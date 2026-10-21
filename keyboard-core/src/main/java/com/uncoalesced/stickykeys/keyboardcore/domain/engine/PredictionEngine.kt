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

        /** Spelling correction, reading the same mapping through its own cursors. */
        private val spellingCorrector = SpellingCorrector(::reader, languageModel)

        /**
         * Every contraction reachable by putting one apostrophe into [normalized]. See
         * [SpellingCorrector.apostropheVariantsOf].
         */
        internal fun apostropheVariantsOf(normalized: String): List<Pair<String, Int>> =
            spellingCorrector.apostropheVariantsOf(normalized)

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
                    spellingCorrector.restoreApostrophe(normalized)
                        ?: spellingCorrector.splitOnMispressedSpace(normalized)
                        ?: spellingCorrector.nearestWord(normalized, previousWord)
                        ?: return@withContext null

                // Restore the shape the user typed. Auto-capitalize means the first word of
                // every message arrives with a capital, so returning the dictionary's lowercase
                // form would silently un-capitalize the start of most sentences it fixed.
                return@withContext spellingCorrector.matchCase(typedWord.trim(), corrected)
            }
    }
