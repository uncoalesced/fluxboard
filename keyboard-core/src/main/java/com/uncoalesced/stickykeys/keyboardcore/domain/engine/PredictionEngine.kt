// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import android.content.Context
import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.PersonalDictionaryDao
import com.uncoalesced.stickykeys.keyboardcore.data.local.entity.PersonalWordEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/** Personal usage carries high weight relative to the base dictionary. */
private const val PERSONAL_WEIGHT = 5f
private const val BASE_WEIGHT = 1f
private const val MAX_SUGGESTIONS = 3

/**
 * Trie nodes a single prefix completion walk may visit.
 *
 * A bound on work, not on answers. The walk used to stop after a fixed number of *terminals*,
 * which during a breadth-first search means stopping near the top of the subtree -- so the
 * candidate pool was chosen by word length instead of by frequency.
 *
 * Set above the largest single-letter subtree in the shipped dictionary ("s", 15,716 nodes),
 * so every prefix a user actually types is walked in full, and a dictionary that grows later
 * degrades to breadth-first order rather than to unbounded work.
 */
private const val MAX_SUGGESTION_NODES = 20_000

/** Longest completion worth offering; past this the strip cannot show it anyway. */
private const val MAX_SUGGESTION_WORD = 24

/**
 * Edit costs, on a scale where one ordinary substitution is [COST_SUBSTITUTE].
 *
 * Uniform costs make every one-edit candidate a tie, which hands the decision to raw
 * dictionary frequency and reliably picks the commoner word over the one the user's finger
 * actually explains. Ordering these by how often each mistake really happens is what makes
 * "vall" resolve to "call" rather than "all", and "gine" to "gone" rather than "line".
 *
 * A dropped or doubled letter sits between the two substitution costs on purpose: it is more
 * likely than hitting a key on the other side of the board, and less likely than catching the
 * neighbour of the key aimed at.
 */
private const val COST_ADJACENT_SUBSTITUTE = 2
private const val COST_GAP = 3
private const val COST_TRANSPOSE = 3
private const val COST_SUBSTITUTE = 4

/** Two ordinary edits. Beyond this the candidate is not a plausible reading of the input. */
private const val MAX_EDIT_COST = COST_SUBSTITUTE * 2

/** The trie root sits immediately after the 4-byte "FLCT" magic. */
private const val TRIE_ROOT_OFFSET = 4

/**
 * How many glide readings to keep.
 *
 * More than the three the suggestion strip shows, because the strip is where a wrong first
 * guess gets corrected without retyping, and a glide is wrong more often than a tap.
 */
private const val MAX_GLIDE_CANDIDATES = 5

/**
 * Longest word a glide will produce.
 *
 * A guard on the walk rather than a real limit on English. Without it a path that loops back
 * over itself lets a branch keep finding its next letter behind it, and the recursion follows
 * arbitrarily long words down a trie that has no reason to stop.
 */
private const val MAX_GLIDE_WORD = 24

/**
 * How many partial readings survive each level of the glide walk.
 *
 * The bound the old recursion never had. It searched every branch the path admitted to
 * whatever depth it admitted, which is fine for the ordinary case and unbounded for the
 * pathological one -- a long looping glide crosses most of the board, and every crossing
 * multiplies the branches alive at the next letter. Kept deliberately wide: pruning is
 * insurance against a gesture nobody makes on purpose, not a ranking mechanism, and a narrow
 * beam would start cutting real words whose cost arrives late.
 */
private const val MAX_GLIDE_BEAM = 40

/**
 * Most a maximally-likely follower can take off a glide's cost.
 *
 * Sentence context and path cost are on unrelated scales -- the language model answers on the
 * dictionary's 0-255 frequency scale, while a glide cost counts unexplained corners at
 * [COST_UNEXPLAINED_PIVOT] apiece. Subtracting one from the other directly would let context
 * outweigh every structural fact about the drawn path by an order of magnitude, so a known
 * follower would win no matter what the finger did.
 *
 * Capped below the cost of a single unexplained corner instead. Context then settles the case
 * it should settle -- two readings the path genuinely cannot distinguish, like "too" and "to"
 * -- and can never overrule the path itself.
 */
private const val MAX_GLIDE_CONTEXT_CREDIT = 4f

/** A correction has to be at least this credible after its edit penalty to be applied. */
private const val MIN_CORRECTION_SCORE = 20f

/** Shortest run-on worth trying to split: two three-letter words plus the junk key. */
private const val MIN_SPLIT_LENGTH = 7

/** Neither half of a split may be shorter than this, or "a" and "I" match everywhere. */
private const val MIN_SPLIT_PART = 3

/** Both halves of a split must be this common, so a split never invents a rare pairing. */
private const val MIN_SPLIT_FREQUENCY = 40

data class Suggestion(
    val word: String,
    val score: Float,
)

/**
 * One partial reading of a glide, alive at the current level of the beam search.
 *
 * The first four fields are exactly the arguments the recursion used to carry: where in the
 * trie the reading has reached, the letters spelled so far, how far along the drawn path they
 * consumed, and what they cost. Making them a value rather than a call frame is the whole
 * transformation -- a list of partial readings can be sorted and cut, a stack of call frames
 * cannot.
 *
 * [pivotCredit] is the field the recursion had no use for and the beam cannot work without.
 * Ranking partial readings by [cost] alone does not rank them at all: a glide crosses its keys
 * exactly, so almost every branch sits at cost 0 and a stable sort then keeps whichever
 * branches the trie happens to list first. Measured on the h-e-l-o path, that dropped "hello"
 * outright in favour of "ho", "go" and "hi" -- alphabetically earlier subtries, nothing more.
 */
private data class GlideBeam(
    val offset: Int,
    val word: String,
    val pathIndex: Int,
    val cost: Int,
    val pivotCredit: Float,
) {
    /**
     * Lower is better, and on the same scale [scoreGlideCandidate] finally reports.
     *
     * A partial reading that has already turned the corners the path turned is worth keeping
     * over one that has walked past them, because the corners are exactly what the final score
     * charges for. Estimating the eventual score rather than the cost so far is what makes the
     * cut a search heuristic instead of a tiebreak on trie order.
     */
    val priority: Float get() = cost - pivotCredit
}

@Singleton
class PredictionEngine
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val personalDao: PersonalDictionaryDao,
        private val languageModel: LanguageModel,
    ) {
        /**
         * The mapped dictionary. Volatile so the reference publishes safely across the
         * IO threads that read it. Never navigated directly -- see [reader]: a
         * MappedByteBuffer carries its own position, so concurrent lookups sharing one
         * instance would interleave position() calls and read each other's bytes.
         */
        @Volatile
        private var buffer: MappedByteBuffer? = null

        /** Guards [initialize] so two concurrent callers cannot map the file twice. */
        private val initMutex = Mutex()

        /**
         * A private cursor over the shared mapping. duplicate() copies position/limit
         * and shares the underlying bytes, so each lookup gets independent navigation
         * with no extra memory and no copy of the 7 MB payload.
         */
        private fun reader(): ByteBuffer? = buffer?.duplicate()

        suspend fun initialize() =
            withContext(Dispatchers.IO) {
                // The ranking data warms alongside the dictionary rather than from a second
                // call the next caller of this engine would have to know to make. It carries
                // its own double-checked guard, so calling it on an already-loaded model is
                // free.
                languageModel.initialize()
                if (buffer != null) return@withContext
                initMutex.withLock {
                    if (buffer != null) return@withLock
                    mapDictionary()
                }
            }

        private fun mapDictionary() {
            try {
                // Re-extracted whenever the app version changes, not only when the file is
                // missing -- see assetBackedFile. Guarding on absence alone meant an updated
                // dictionary never reached anyone who already had the app installed.
                val dictFile =
                    assetBackedFile(context, "base_dict.bin") ?: run {
                        buffer = null
                        return
                    }

                val mapped =
                    RandomAccessFile(dictFile, "r").use { raf ->
                        raf.channel.use { channel ->
                            channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
                        }
                    }

                // Check Magic Header 'FLCT' on a private cursor, then publish.
                val header = mapped.duplicate()
                header.position(0)
                val magic =
                    buildString {
                        repeat(4) { append((header.get().toInt() and 0xFF).toChar()) }
                    }
                buffer = if (magic == "FLCT") mapped else null
            } catch (e: Exception) {
                e.printStackTrace()
                buffer = null
            }
        }

        suspend fun learnWord(word: String) =
            withContext(Dispatchers.IO) {
                if (word.isBlank() || word.length > 30) return@withContext
                val normalized = word.lowercase().trim()
                // A split correction ("thank you") is two words, and storing it as one would
                // put a phrase into the prefix trie that no prefix lookup can ever match.
                if (normalized.any { it.isWhitespace() }) return@withContext

                val existing = personalDao.getWord(normalized)
                val now = System.currentTimeMillis()

                if (existing != null) {
                    val daysSince = (now - existing.lastUsedTimestamp) / (1000 * 60 * 60 * 24)
                    var newFreq = existing.frequency

                    // Decay older words
                    if (daysSince > 7) {
                        newFreq /= 2
                    }

                    newFreq = (newFreq + 1).coerceAtMost(255)
                    personalDao.insertOrUpdate(
                        existing.copy(frequency = newFreq, lastUsedTimestamp = now),
                    )
                } else {
                    personalDao.insertOrUpdate(PersonalWordEntity(normalized, 1, now))
                }
            }

        /**
         * Completions for [prefix], ranked.
         *
         * [previousWord] is the word the user just finished, and it only ever *re-ranks*: the
         * bigram table is never asked for candidates of its own. A word that does not match
         * the prefix is not a completion of it however often the corpus says it follows, and
         * treating context as a candidate source is how a suggestion strip starts proposing
         * words the user is visibly not typing.
         */
        suspend fun getSuggestions(
            prefix: String,
            previousWord: String? = null,
        ): List<String> =
            withContext(Dispatchers.IO) {
                val normalized = prefix.lowercase().trim()
                if (normalized.isEmpty()) return@withContext emptyList()

                // 1. Get from Personal Dictionary. Two-strike rule: a word only counts as
                // "personal" once it has been used at least twice, so one-off typos that
                // were learned in passing never outrank the base dictionary.
                val personalSuggestions =
                    personalDao
                        .getSuggestionsForPrefix(normalized, 5)
                        .filter { it.frequency >= 2 }
                        .map { Suggestion(it.word, it.frequency * PERSONAL_WEIGHT) }

                // 2. Get from Base Flictionary. A malformed offset must never escape into an
                // uncaught exception: this runs on a coroutine in the IME process, where an
                // unhandled throw would take the whole keyboard down mid-sentence.
                val baseSuggestions =
                    try {
                        getBaseSuggestions(normalized, 10).map {
                            Suggestion(
                                it.word,
                                it.score * BASE_WEIGHT,
                            )
                        }
                    } catch (e: Exception) {
                        emptyList()
                    }

                // 3. Merge & Sort
                val merged = mutableMapOf<String, Float>()
                for (s in baseSuggestions) {
                    merged[s.word] = s.score
                }
                for (s in personalSuggestions) {
                    merged[s.word] = (merged[s.word] ?: 0f) + s.score
                }

                // 4. Re-rank on sentence context. Applied to what is already in `merged` and
                // nothing else, so with no previous word -- or an unknown one -- the order is
                // byte-identical to what it was before context existed.
                if (previousWord != null) {
                    for (key in merged.keys.toList()) {
                        merged[key] =
                            merged.getValue(key) + languageModel.boostFor(previousWord, key)
                    }
                }

                return@withContext merged.entries
                    .sortedByDescending { it.value }
                    .take(MAX_SUGGESTIONS)
                    .map { it.key }
            }

        /**
         * What is likely to come *next*, with nothing typed yet.
         *
         * The deliberate counterpart to [getSuggestions] rather than a case inside it, and the
         * split is the point. [getSuggestions] refuses to let context contribute candidates
         * because a word that does not match the prefix is not a completion of it -- that rule
         * is unchanged and this does not weaken it. Here there is no prefix at all: the user
         * has finished a word and pressed space, so nothing on screen can be contradicted, and
         * the corpus is the only thing with an opinion to offer.
         *
         * Empty for an unknown or absent previous word, which is the previous behaviour and
         * still the common case -- so a strip that has nothing to say stays blank rather than
         * filling with whatever is frequent in English.
         *
         * The privacy gates live at the caller, in `TypingViewModel`, alongside the ones the
         * completion path already answers to. Nothing about this read is safe that was not
         * already safe: [previousWord] is only ever set by `learn`, which incognito stops.
         */
        suspend fun getNextWordSuggestions(previousWord: String?): List<String> =
            withContext(Dispatchers.IO) {
                if (previousWord.isNullOrBlank()) return@withContext emptyList()
                languageModel.followersOf(previousWord.lowercase(), MAX_SUGGESTIONS)
            }

        /**
         * Decodes a finished glide into ranked word candidates.
         *
         * Walks the same trie the spelling search uses, but with a different question. Spelling
         * asks how far a typed string is from each word; a glide asks which words the drawn
         * path *could be*, and there are always many -- the path for "hello" crosses enough of
         * the board that "ho", "hell" and "hilo" are all readings of it. Ranking is therefore
         * the whole job, not a tie-break.
         *
         * The walk carries the position reached along the path, so a branch dies the moment its
         * next letter cannot be found ahead of where the previous one matched. That is what
         * keeps this from enumerating the dictionary: the first letter alone restricts the root
         * to the keys under the finger at touch-down, and each subsequent letter prunes again.
         *
         * Level-order rather than depth-first, keeping only the [MAX_GLIDE_BEAM] cheapest
         * partial readings across the *whole* frontier at each letter. Pruning across the
         * frontier rather than per node is what makes it a beam search instead of a fan-out
         * cap: a cheap partial reading survives regardless of which branch produced it, and no
         * single branch can spend the budget the way an ordinary depth limit would let it.
         *
         * Frequency breaks ties, one rung below sentence context and nothing more. It has to be
         * last, or every glide returns the commonest short word whose letters happen to lie
         * along the path.
         */
        suspend fun decodeGlide(
            stroke: GlideStroke,
            previousWord: String? = null,
        ): List<String> =
            withContext(Dispatchers.IO) {
                if (!stroke.isUsable) return@withContext emptyList()
                val buf = reader() ?: return@withContext emptyList()
                val results = mutableListOf<Pair<String, Int>>()
                try {
                    // 4 bytes in: the header is the ASCII magic "FLCT", and the root follows it.
                    var frontier = listOf(GlideBeam(TRIE_ROOT_OFFSET, "", 0, 0, 0f))
                    var depth = 0
                    while (frontier.isNotEmpty() && depth < MAX_GLIDE_WORD) {
                        val next = mutableListOf<GlideBeam>()
                        for (beam in frontier) {
                            expandGlideBeam(buf, beam, stroke, results, next)
                        }
                        frontier = next.sortedBy { it.priority }.take(MAX_GLIDE_BEAM)
                        depth++
                    }
                } catch (e: Exception) {
                    // A malformed offset must not escape: this runs in the IME process, where
                    // an unhandled throw takes the keyboard down mid-gesture.
                    return@withContext emptyList()
                }
                results
                    .asSequence()
                    .sortedWith(
                        compareBy<Pair<String, Int>> {
                            it.second - glideContextCredit(previousWord, it.first)
                        }.thenByDescending { frequencyOf(it.first) ?: 0 },
                    ).map { it.first }
                    .distinct()
                    .take(MAX_GLIDE_CANDIDATES)
                    .toList()
            }

        /**
         * Reads one trie node, scores it if it completes a word, and queues its viable children.
         *
         * Split out of [decodeGlide] only because the loop that calls it is already three deep;
         * the matching rules are unchanged from the recursion this replaced.
         */
        private fun expandGlideBeam(
            buf: ByteBuffer,
            beam: GlideBeam,
            stroke: GlideStroke,
            results: MutableList<Pair<String, Int>>,
            next: MutableList<GlideBeam>,
        ) {
            buf.position(beam.offset)
            buf.get() // frequency, read through the ranking pass instead
            val isTerminal = buf.get().toInt() and 0xFF
            val childCount = buf.get().toInt() and 0xFF

            val children = ArrayList<Pair<Char, Int>>(childCount)
            for (i in 0 until childCount) {
                val c1 = buf.get().toInt() and 0xFF
                val c2 = buf.get().toInt() and 0xFF
                val childChar = ((c1 shl 8) or c2).toChar()
                children.add(Pair(childChar, buf.getInt()))
            }

            // A complete word only counts if the finger actually lifted here. scoreGlideCandidate
            // re-scores from scratch rather than trusting the running cost, so the anchors and
            // the unexplained-corner penalty are applied by one function with one definition.
            if (isTerminal == 1) {
                scoreGlideCandidate(beam.word, stroke)?.let { results.add(beam.word to it) }
            }

            for ((childChar, childOffset) in children) {
                // A doubled letter does not advance along the path: a finger cannot visit the
                // same key twice in succession, so the second 'l' of "hello" has no position
                // of its own to occupy.
                if (beam.word.isNotEmpty() && childChar == beam.word.last()) {
                    next.add(
                        GlideBeam(
                            childOffset,
                            beam.word + childChar,
                            beam.pathIndex,
                            beam.cost + 1,
                            beam.pivotCredit,
                        ),
                    )
                    continue
                }
                val match = nextGlideMatch(stroke, beam.pathIndex, childChar) ?: continue
                next.add(
                    GlideBeam(
                        childOffset,
                        beam.word + childChar,
                        match.first + 1,
                        beam.cost + match.second,
                        beam.pivotCredit + pivotCreditAt(stroke, match.first),
                    ),
                )
            }
        }

        /**
         * What explaining the corner at [index] is worth, or 0f if it is not one.
         *
         * Weighted exactly as [scoreGlideCandidate] charges for missing it, so the search
         * heuristic and the final score cannot disagree about which corners matter.
         */
        private fun pivotCreditAt(
            stroke: GlideStroke,
            index: Int,
        ): Float {
            if (index !in stroke.pivots) return 0f
            return (stroke.pivotStrength[index] ?: 1f) * COST_UNEXPLAINED_PIVOT
        }

        /**
         * The glide's cost reduction for following [previousWord], on the glide cost scale.
         *
         * Normalized rather than subtracted raw -- see [MAX_GLIDE_CONTEXT_CREDIT] for why the
         * two scales cannot simply be mixed.
         */
        private fun glideContextCredit(
            previousWord: String?,
            word: String,
        ): Float {
            if (previousWord == null) return 0f
            val boost = languageModel.boostFor(previousWord, word)
            if (boost <= 0f) return 0f
            return (boost / MAX_LANGUAGE_MODEL_BOOST).coerceAtMost(1f) * MAX_GLIDE_CONTEXT_CREDIT
        }

        /** First position at or after [from] where [letter] is on the path, and what it cost. */
        private fun nextGlideMatch(
            stroke: GlideStroke,
            from: Int,
            letter: Char,
        ): Pair<Int, Int>? {
            var near: Pair<Int, Int>? = null
            for (i in from until stroke.keys.size) {
                val onPath = stroke.keys[i]
                if (onPath == letter) return Pair(i, 0)
                if (near == null && KeyProximity.areAdjacent(onPath, letter)) {
                    near = Pair(i, 3)
                }
            }
            return near
        }

        internal fun getBaseSuggestions(
            prefix: String,
            limit: Int,
        ): List<Suggestion> {
            val buf = reader() ?: return emptyList()
            var currentOffset = 4 // Start after FLCT

            // Traverse trie for prefix
            for (char in prefix) {
                var found = false
                buf.position(currentOffset)
                val freq = buf.get().toInt() and 0xFF
                val isTerminal = buf.get().toInt() and 0xFF
                val childCount = buf.get().toInt() and 0xFF

                for (i in 0 until childCount) {
                    val c1 = buf.get().toInt() and 0xFF
                    val c2 = buf.get().toInt() and 0xFF
                    val childChar = ((c1 shl 8) or c2).toChar()
                    val offset = buf.getInt()

                    if (childChar == char) {
                        currentOffset = offset
                        found = true
                        break
                    }
                }

                if (!found) return emptyList()
            }

            // The subtree under the prefix holds every completion; the walk collects them and
            // the sort at the end picks the best.
            //
            // The budget counts *nodes visited*, not terminals found, and that distinction is
            // the whole fix. Stopping after 30 terminals during a breadth-first walk stops
            // near the top of the subtree, where the short words are -- so the pool was
            // decided by word length rather than by frequency, and a common longer word never
            // entered it at all. Measured against the shipped dictionary: prefix "ma" offered
            // "map" and "mar" while "many" was missing, despite "many" outranking both. That
            // also capped what sentence context could ever do, because context re-ranks this
            // pool and never adds to it.
            //
            // Sized from the real dictionary rather than guessed: the largest single-letter
            // subtree is "s" at 15,716 nodes, so this walks every realistic prefix in full and
            // still cannot run away if the dictionary grows.
            val results = mutableListOf<Suggestion>()
            val queue = ArrayDeque<Pair<Int, String>>()
            queue.add(Pair(currentOffset, prefix))
            var visited = 0

            while (queue.isNotEmpty() && visited < MAX_SUGGESTION_NODES) {
                // ArrayDeque, not removeAt(0) on a list. The old queue never grew past a few
                // dozen entries because the walk stopped so early; at this budget a list would
                // shift thousands of elements per pop and make the walk quadratic.
                val (offset, currentWord) = queue.removeFirst()
                visited++

                buf.position(offset)
                val freq = buf.get().toInt() and 0xFF
                val isTerminal = buf.get().toInt() and 0xFF
                val childCount = buf.get().toInt() and 0xFF

                if (isTerminal == 1) {
                    results.add(Suggestion(currentWord, freq.toFloat()))
                }

                if (currentWord.length >= MAX_SUGGESTION_WORD) continue
                for (i in 0 until childCount) {
                    val c1 = buf.get().toInt() and 0xFF
                    val c2 = buf.get().toInt() and 0xFF
                    val childChar = ((c1 shl 8) or c2).toChar()
                    val nextOffset = buf.getInt()
                    queue.add(Pair(nextOffset, currentWord + childChar))
                }
            }

            return results.sortedByDescending { it.score }.take(limit)
        }

        suspend fun getAutoCorrection(
            typedWord: String,
            previousWord: String? = null,
        ): String? =
            withContext(Dispatchers.IO) {
                val normalized = typedWord.lowercase().trim()
                // Too short to safely autocorrect.
                if (normalized.length < 3) return@withContext null

                // Words the user has typed at least twice are treated as deliberate and are
                // never corrected. A single occurrence is not enough -- otherwise every
                // one-off typo would permanently disable its own correction.
                val personalEntry = personalDao.getWord(normalized)
                if (personalEntry != null && personalEntry.frequency >= 2) return@withContext null

                val corrected =
                    restoreApostrophe(normalized)
                        ?: splitOnMispressedSpace(normalized)
                        ?: nearestWord(normalized, previousWord)
                        ?: return@withContext null

                // Restore the shape the user typed. Auto-capitalize means the first word of
                // every message arrives with a capital, so returning the dictionary's lowercase
                // form would silently un-capitalize the start of most sentences it fixed.
                return@withContext matchCase(typedWord.trim(), corrected)
            }

        /** The best single-word correction, or null when nothing beats what was typed. */
        private fun nearestWord(
            normalized: String,
            previousWord: String? = null,
        ): String? {
            // Search the base trie for candidates within [MAX_EDIT_COST]. The typed word
            // itself competes as its own zero-cost candidate, so a correction only fires
            // when a nearby word beats what the user actually typed under the
            // cost-penalized score. This deliberately replaces an absolute
            // is-in-dictionary veto, which let junk dictionary entries (e.g. a terminal
            // "thw") suppress obvious corrections like "thw" to "the".
            val candidates = mutableListOf<Pair<Suggestion, Int>>()
            val buf = reader() ?: return null

            // Same reasoning as getSuggestions: never let a bad offset kill the process.
            try {
                val initialRow = IntArray(normalized.length + 1) { it * COST_GAP }
                dfsEditDistance(
                    buf,
                    4,
                    "",
                    normalized,
                    initialRow,
                    null,
                    null,
                    MAX_EDIT_COST,
                    candidates,
                )
            } catch (e: Exception) {
                return null
            }

            val eligible = candidates.filter { it.first.score > 10f } // minimum frequency

            // Whether a correction fires at all is settled first, and without context.
            //
            // This ordering is the whole safety argument. The typed word competes as its own
            // zero-cost candidate, and "it won, so leave it alone" is what stops a correctly
            // spelled word being rewritten. Fold context into that decision and a strong
            // enough follower can outscore a word the user typed perfectly -- which is
            // real-word correction, the one thing this engine has always refused to do.
            val plainBest =
                eligible.maxByOrNull { it.first.score / penaltyFor(it.second) } ?: return null
            // The typed word won: it is credible enough as-is, leave it alone.
            if (plainBest.first.word == normalized) return null
            // Correction must itself clear a minimum credibility bar.
            if (plainBest.first.score / penaltyFor(plainBest.second) <= MIN_CORRECTION_SCORE) {
                return null
            }

            // Only now does the sentence get a say, and only over *which* correction wins.
            // Every candidate here clears the same bar the unconditional winner just cleared,
            // so context reorders real corrections and can promote nothing else. With no
            // previous word every boost is zero and this necessarily re-elects [plainBest],
            // which is what keeps the contextless path byte-identical.
            val best =
                eligible
                    .filter { it.first.score / penaltyFor(it.second) > MIN_CORRECTION_SCORE }
                    .maxByOrNull {
                        (it.first.score + languageModel.boostFor(previousWord, it.first.word)) /
                            penaltyFor(it.second)
                    }
                    ?: return null
            // Context defended the typed word against a correction that would otherwise have
            // fired. Fewer corrections is always the safe direction, so this is allowed to.
            if (best.first.word == normalized) return null
            return best.first.word
        }

        /**
         * Divisor applied to a candidate's frequency, so a further-away word has to be
         * proportionally more common to win.
         *
         * Expressed in whole edits rather than raw cost units, which keeps this the same curve
         * it was before edit costs became weighted -- one edit still halves a candidate's
         * score. Only the *ordering within* a given number of edits changed.
         */
        private fun penaltyFor(cost: Int): Float = 1f + cost.toFloat() / COST_SUBSTITUTE

        /**
         * Puts back an apostrophe the user left out: "youre" to "you're", "dont" to "don't".
         *
         * A targeted repair rather than a tweak to the edit costs, and tried ahead of the
         * general search for the same reason [splitOnMispressedSpace] is: the edit-distance
         * walk cannot get this right at any cost setting. Inserting an apostrophe and
         * deleting a letter are both one gap, so "youre" reaches "you're" and "your" for the
         * identical price and the tie falls to raw frequency -- which "your" wins by three
         * orders of magnitude, because the corpus counts it as one of the commonest words in
         * English. Lowering the cost of an apostrophe until "you're" won would have made
         * every apostrophe near-free, and at that point "were" reaches "we're" for nothing.
         *
         * The apostrophe is worth this on this keyboard specifically: it is not on the letter
         * plane at all, so reaching it costs a long press or a page switch and dropping it is
         * the single most common thing a hurried thumb does.
         *
         * Only fires for a word that is not already in the dictionary, which is what keeps
         * "its", "were", "cant", "hell" and every other contraction homograph untouched --
         * rewriting one of those would be real-word correction, the one thing this engine
         * refuses to do. That check also makes this cheap: it runs only for a word that was
         * going to be corrected regardless.
         */
        private fun restoreApostrophe(normalized: String): String? {
            if (normalized.length < 2 || normalized.contains('\'')) return null
            if (frequencyOf(normalized) != null) return null

            var best: Pair<String, Int>? = null
            for (i in 1 until normalized.length) {
                val candidate =
                    normalized.substring(0, i) + '\'' + normalized.substring(i)
                val freq = frequencyOf(candidate) ?: continue
                if (freq > (best?.second ?: 0)) best = candidate to freq
            }
            return best?.first
        }

        /**
         * Splits a run-on caused by hitting a key beside the space bar instead of the space.
         *
         * Tried before the edit-distance search because the two answer different questions: a
         * trie walk looks for one word close to what was typed, and there is none -- "thankbyou"
         * is not a near-miss of any single word. Only both halves being real, common words is
         * evidence enough to act on, which is why the frequency bar is applied to each.
         */
        private fun splitOnMispressedSpace(normalized: String): String? {
            if (normalized.length < MIN_SPLIT_LENGTH) return null
            if (normalized.any { !it.isLetter() }) return null

            var best: Pair<String, Float>? = null
            for (i in MIN_SPLIT_PART until normalized.length - MIN_SPLIT_PART) {
                if (normalized[i] !in KeyProximity.spaceNeighbours) continue
                val left = normalized.substring(0, i)
                val right = normalized.substring(i + 1)
                val leftFreq = frequencyOf(left) ?: continue
                val rightFreq = frequencyOf(right) ?: continue
                if (leftFreq < MIN_SPLIT_FREQUENCY || rightFreq < MIN_SPLIT_FREQUENCY) continue
                // Rank by the weaker half: a split is only as believable as its least
                // convincing side, and scoring on the sum lets one very common word carry a
                // fragment that happens to be a rare dictionary entry.
                val score = minOf(leftFreq, rightFreq).toFloat()
                if (best == null || score > best!!.second) {
                    best = "$left $right" to score
                }
            }
            // The whole string being a real word outranks any split of it: "carbon" must not
            // become "car on".
            if (best != null && frequencyOf(normalized) != null) return null
            return best?.first
        }

        /** Frequency of an exact dictionary word, or null when it is not a terminal node. */
        private fun frequencyOf(word: String): Int? {
            val buf = reader() ?: return null
            return try {
                var offset = 4 // after the FLCT magic
                for (char in word) {
                    buf.position(offset)
                    buf.get() // frequency
                    buf.get() // terminal flag
                    val childCount = buf.get().toInt() and 0xFF
                    var next = -1
                    for (i in 0 until childCount) {
                        val c1 = buf.get().toInt() and 0xFF
                        val c2 = buf.get().toInt() and 0xFF
                        val childChar = ((c1 shl 8) or c2).toChar()
                        val childOffset = buf.getInt()
                        if (childChar == char) {
                            next = childOffset
                            break
                        }
                    }
                    if (next < 0) return null
                    offset = next
                }
                buf.position(offset)
                val freq = buf.get().toInt() and 0xFF
                val isTerminal = buf.get().toInt() and 0xFF
                if (isTerminal == 1) freq else null
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Gives [replacement] the capitalization of [original].
         *
         * Only two shapes are carried over, because only two are ever deliberate: an initial
         * capital (sentence start, which auto-capitalize produces on its own) and all caps.
         * Anything else is treated as lower case rather than guessed at.
         */
        private fun matchCase(
            original: String,
            replacement: String,
        ): String =
            when {
                original.length > 1 && original.all { !it.isLetter() || it.isUpperCase() } ->
                    replacement.uppercase()
                original.firstOrNull()?.isUpperCase() == true ->
                    replacement.replaceFirstChar { it.uppercase() }
                else -> replacement
            }

        /**
         * Walks the trie carrying one weighted-edit row per node, with the
         * Damerau/optimal-string-alignment extension for adjacent transpositions.
         *
         * Transposition has to cost one edit or the whole feature misses the most
         * common class of keyboard typo: "teh", "hte", "adn" and "recieve" are all two
         * letters in the wrong order. Under plain Levenshtein each of those is two edits
         * from its target but one from some unrelated word, so "teh" corrected to
         * "ten". Scoring that edit as one needs the row from two levels up ([prevRow]) and
         * the character that produced [currentRow] ([prevChar]); both are null at the root.
         *
         * Costs are weighted rather than uniform -- see [COST_ADJACENT_SUBSTITUTE]. The row
         * arrays stay integers: the weights are small whole numbers on a scale where one
         * ordinary edit is [COST_SUBSTITUTE], so the dynamic programming is unchanged and
         * only the constants differ.
         */
        private fun dfsEditDistance(
            buf: ByteBuffer,
            offset: Int,
            currentWord: String,
            targetWord: String,
            currentRow: IntArray,
            prevRow: IntArray?,
            prevChar: Char?,
            maxErrors: Int,
            results: MutableList<Pair<Suggestion, Int>>,
        ) {
            buf.position(offset)
            val freq = buf.get().toInt() and 0xFF
            val isTerminal = buf.get().toInt() and 0xFF
            val childCount = buf.get().toInt() and 0xFF

            val children = mutableListOf<Pair<Char, Int>>()
            for (i in 0 until childCount) {
                val c1 = buf.get().toInt() and 0xFF
                val c2 = buf.get().toInt() and 0xFF
                val childChar = ((c1 shl 8) or c2).toChar()
                val childOffset = buf.getInt()
                children.add(Pair(childChar, childOffset))
            }

            val distance = currentRow.last()
            if (isTerminal == 1 && distance <= maxErrors) {
                results.add(Pair(Suggestion(currentWord, freq.toFloat()), distance))
            }

            val minInRow = currentRow.minOrNull() ?: 0
            if (minInRow > maxErrors) return // prune branch entirely

            for ((childChar, childOffset) in children) {
                val nextRow = IntArray(targetWord.length + 1)
                nextRow[0] = currentRow[0] + COST_GAP
                for (i in 1..targetWord.length) {
                    val typed = targetWord[i - 1]
                    val insertCost = nextRow[i - 1] + COST_GAP
                    val deleteCost = currentRow[i] + COST_GAP
                    val substitution =
                        when {
                            typed == childChar -> 0
                            // A key physically under the intended one is the likeliest
                            // mistake there is, and must outrank both dropping a letter and
                            // hitting something across the board. Without this the choice
                            // between two same-distance candidates falls back to raw
                            // frequency, which picks the commoner word every time -- "vall"
                            // became "all" rather than "call".
                            KeyProximity.areAdjacent(typed, childChar) ->
                                COST_ADJACENT_SUBSTITUTE
                            else -> COST_SUBSTITUTE
                        }
                    var cost =
                        minOf(insertCost, deleteCost, currentRow[i - 1] + substitution)

                    // Adjacent transposition: the candidate ends "prevChar, childChar"
                    // where the target has those two the other way round.
                    if (i >= 2 &&
                        prevRow != null &&
                        prevChar != null &&
                        targetWord[i - 1] == prevChar &&
                        targetWord[i - 2] == childChar
                    ) {
                        cost = minOf(cost, prevRow[i - 2] + COST_TRANSPOSE)
                    }

                    nextRow[i] = cost
                }
                dfsEditDistance(
                    buf,
                    childOffset,
                    currentWord + childChar,
                    targetWord,
                    nextRow,
                    currentRow,
                    childChar,
                    maxErrors,
                    results,
                )
            }
        }
    }
