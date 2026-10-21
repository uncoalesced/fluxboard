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

/**
 * Three ordinary edits. Beyond this the candidate is not a plausible reading of the input.
 *
 * Raised from two deliberately, trading search width for accuracy. On the weighted scale
 * above, two ordinary edits also bought only four adjacent slips -- and four adjacent slips is
 * an ordinary outcome for a thumb moving quickly, so longer words were falling outside the
 * budget for mistakes that are individually very likely. A third edit is what puts them back
 * in reach.
 *
 * This widens the *pool*, not the willingness to correct: [MIN_CORRECTION_SCORE] still decides
 * whether anything is applied, and a candidate three edits away carries three edits' worth of
 * penalty into that decision. The cost is a wider walk on every word-ending keystroke, which
 * is the trade this was asked for.
 */
private const val MAX_EDIT_COST = COST_SUBSTITUTE * 3

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

                // 2b. Contractions the prefix walk cannot reach.
                //
                // "i'll" is in the dictionary but is not a completion of "ill" -- the trie
                // branches at the apostrophe, so no amount of prefix walking finds it. The
                // autocorrect path has always repaired the unambiguous cases ("dont",
                // "youre", "itll") and deliberately refuses the rest, because the bare form
                // of "we're", "I'll", "we'll", "she'll" and "can't" is itself an ordinary
                // English word and rewriting it would break "I am ill", "there were three"
                // and "well done".
                //
                // Offering rather than replacing is what makes those reachable without that
                // cost: the typed word stands, and the contraction sits in the strip one tap
                // away. This feeds suggestions only. Autocorrect is a separate path and is
                // deliberately not given these.
                val contractionSuggestions =
                    try {
                        apostropheVariantsOf(normalized).map { (word, freq) ->
                            Suggestion(word, freq * BASE_WEIGHT)
                        }
                    } catch (e: Exception) {
                        emptyList()
                    }

                // 3. Merge & Sort
                val merged = mutableMapOf<String, Float>()
                for (s in contractionSuggestions) {
                    merged[s.word] = s.score
                }
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

                val ranked =
                    merged.entries
                        .sortedByDescending { it.value }
                        .take(MAX_SUGGESTIONS)
                        .map { it.key }

                // 5. One slot is kept for a contraction, when the typed letters are the bare
                // form of one.
                //
                // Ranking alone is not enough, and the case that showed it is "im". The strip
                // holds three, and "image", "important" and "images" are all commoner than
                // any single contraction, so "I'm" was computed, scored and then cut --
                // leaving one of the most frequent words in English unreachable while three
                // completions of a word the user had not finished took every slot. Measured
                // on device; "ill", "cant" and "well" happened to have weaker competition and
                // so looked fine.
                //
                // The trade is deliberate. A completion is a guess about a word still being
                // typed, while the bare form of a contraction is a complete thing the user
                // already finished typing, so it has the better claim on the last slot. Only
                // one is ever promoted, and only when none made it on merit, so the ordinary
                // case is untouched.
                val bestContraction = contractionSuggestions.maxByOrNull { it.score }?.word
                return@withContext when {
                    bestContraction == null -> ranked
                    ranked.contains(bestContraction) -> ranked
                    ranked.size < MAX_SUGGESTIONS -> ranked + bestContraction
                    else -> ranked.dropLast(1) + bestContraction
                }
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

        /** The glide decoder, reading the same mapping through its own cursors. */
        private val glideDecoder = GlideDecoder(::reader, languageModel)

        /**
         * Decodes a finished glide into ranked word candidates. See [GlideDecoder] for how.
         *
         * Off the main thread because the walk can visit thousands of trie nodes, and it runs
         * from a gesture handler in the IME process.
         */
        suspend fun decodeGlide(
            stroke: GlideStroke,
            previousWord: String? = null,
        ): List<String> =
            withContext(Dispatchers.IO) {
                glideDecoder.decode(stroke, previousWord)
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
            return best?.first?.let { displayContraction(it) }
        }

        /**
         * Every contraction reachable by putting one apostrophe into [normalized].
         *
         * The same insertion sweep [restoreApostrophe] does, without the gate that stops it
         * when the typed word is itself a dictionary entry. That gate is right for
         * autocorrect, which replaces, and wrong here, which offers: "ill", "were", "well",
         * "cant", "wont", "shell" and "its" are all real words *and* the bare forms of
         * common contractions, and the only safe way to reach the second reading is to put
         * it in the strip and let the user decide.
         *
         * Returns word and frequency so the caller can rank it against everything else
         * rather than pinning it to a made-up score.
         */
        internal fun apostropheVariantsOf(normalized: String): List<Pair<String, Int>> {
            if (normalized.length < 2 || normalized.contains('\'')) return emptyList()
            return (1 until normalized.length).mapNotNull { i ->
                val candidate = normalized.substring(0, i) + '\'' + normalized.substring(i)
                frequencyOf(candidate)?.let { displayContraction(candidate) to it }
            }
        }

        /**
         * Capitalizes the one English pronoun that is always capitalized.
         *
         * The dictionary is lowercase throughout, which is right for every other word --
         * case is restored from what the user typed. It cannot be for "i'm", "i'll", "i've"
         * and "i'd", where the correct form does not depend on what was typed and a
         * faithfully lowercase "i'll" reads as the feature being broken. Applied to the
         * contraction alone, so nothing else in the dictionary changes shape.
         */
        private fun displayContraction(word: String): String =
            if (word.startsWith("i'")) "I'" + word.substring(2) else word

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
            return trieFrequencyOf(buf, word)
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
