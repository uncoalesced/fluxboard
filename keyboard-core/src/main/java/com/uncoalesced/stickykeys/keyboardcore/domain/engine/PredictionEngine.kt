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
import java.io.File
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
                if (buffer != null) return@withContext
                initMutex.withLock {
                    if (buffer != null) return@withLock
                    mapDictionary()
                }
            }

        private fun mapDictionary() {
            try {
                // noBackupFilesDir, not cacheDir: the OS may reclaim cacheDir at any time
                // without warning, and it is excluded from auto-backup so the 7 MB payload
                // never eats the user's backup quota for something reproducible from assets.
                val dictFile = File(context.noBackupFilesDir, "base_dict.bin")
                if (!dictFile.exists()) {
                    // Extract via a temp file and rename, so a process death mid-copy cannot
                    // leave a truncated file that exists() would then accept forever.
                    val tmp = File(context.noBackupFilesDir, "base_dict.bin.tmp")
                    context.assets.open("base_dict.bin").use { inputStream ->
                        tmp.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    if (!tmp.renameTo(dictFile)) {
                        tmp.delete()
                        buffer = null
                        return
                    }
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

        suspend fun getSuggestions(prefix: String): List<String> =
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

                return@withContext merged.entries
                    .sortedByDescending { it.value }
                    .take(MAX_SUGGESTIONS)
                    .map { it.key }
            }

        private fun getBaseSuggestions(
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

            // Now currentOffset is the node matching the prefix.
            // We must do a DFS/BFS to find the top `limit` completions.
            val results = mutableListOf<Suggestion>()
            val queue = mutableListOf<Pair<Int, String>>()
            queue.add(Pair(currentOffset, prefix))

            while (queue.isNotEmpty() && results.size < limit * 3) { // gather more to sort
                val (offset, currentWord) = queue.removeAt(0)

                buf.position(offset)
                val freq = buf.get().toInt() and 0xFF
                val isTerminal = buf.get().toInt() and 0xFF
                val childCount = buf.get().toInt() and 0xFF

                if (isTerminal == 1) {
                    results.add(Suggestion(currentWord, freq.toFloat()))
                }

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

        suspend fun getAutoCorrection(typedWord: String): String? =
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
                    splitOnMispressedSpace(normalized)
                        ?: nearestWord(normalized)
                        ?: return@withContext null

                // Restore the shape the user typed. Auto-capitalize means the first word of
                // every message arrives with a capital, so returning the dictionary's lowercase
                // form would silently un-capitalize the start of most sentences it fixed.
                return@withContext matchCase(typedWord.trim(), corrected)
            }

        /** The best single-word correction, or null when nothing beats what was typed. */
        private fun nearestWord(normalized: String): String? {
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

            val best =
                candidates
                    .filter { it.first.score > 10f } // minimum frequency threshold
                    .maxByOrNull { it.first.score / penaltyFor(it.second) }
                    ?: return null

            // The typed word won: it is credible enough as-is, leave it alone.
            if (best.first.word == normalized) return null
            // Correction must itself clear a minimum credibility bar.
            if (best.first.score / penaltyFor(best.second) <= MIN_CORRECTION_SCORE) return null
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
