// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The ceiling any [LanguageModel] may return from [LanguageModel.boostFor].
 *
 * Part of the interface's contract rather than one implementation's tuning, because a caller
 * working on a different scale has to normalize against something -- the glide decoder counts
 * cost in unexplained corners, not in dictionary frequency, and needs to know what "as strong
 * as this signal gets" is worth before it can convert. Set just below the 255 a base-dictionary
 * frequency reaches, so context is comparable to frequency and never larger than it.
 */
internal const val MAX_LANGUAGE_MODEL_BOOST = 192f

/**
 * How much a candidate's score is boosted when it followed the previous word in the
 * training corpus, per point of stored (1-255) weight.
 *
 * Deliberately below 1: this re-ranks candidates that already passed a real credibility bar
 * (a prefix match, an edit-distance cost, a path consistent with the drawn glide). It is not
 * a source of candidates, and a scale that let it dominate would turn every suggestion into
 * whatever the corpus says usually follows -- which is wrong exactly when the user is writing
 * something the corpus has not seen, i.e. most of the time.
 *
 * The stored weight is normalized per previous-word by dictionary-tools/build_bigrams.py, so
 * 255 means "the commonest thing that follows this word" rather than "common in English".
 */
private const val BIGRAM_WEIGHT_SCALE = MAX_LANGUAGE_MODEL_BOOST / 255f

/** The FLBG header: 4-byte magic plus a u32 record count. */
private const val FLBG_MAGIC = "FLBG"

/**
 * Scores how well a candidate word follows a previous word.
 *
 * One implementation today ([BigramLanguageModel], a corpus-derived table). It exists as an
 * interface because a real on-device model is the intended second one -- when a vetted,
 * size-and-license-checked weights file appears, it becomes another implementation and a
 * one-line change to the binding, with nothing in [PredictionEngine] touched.
 */
interface LanguageModel {
    /**
     * Loads whatever backing data the implementation needs.
     *
     * Called from [PredictionEngine.initialize][
     * com.uncoalesced.stickykeys.keyboardcore.domain.engine.PredictionEngine.initialize] so
     * there is one place the keyboard warms its ranking data, rather than a second init call
     * that a new caller of the engine would have to know to make. Defaulted to a no-op
     * because an implementation with nothing to load should not have to say so.
     */
    suspend fun initialize() = Unit

    /**
     * How much better [candidate] looks for having followed [previousWord].
     *
     * 0f when there is no signal either way -- no previous word, or a pair the model has
     * never seen -- and never above [MAX_LANGUAGE_MODEL_BOOST].
     */
    fun boostFor(
        previousWord: String?,
        candidate: String,
    ): Float

    /**
     * The words most likely to follow [previousWord], best first, at most [limit] of them.
     *
     * The one place context is allowed to *be* a candidate source rather than only re-rank one,
     * and it is deliberately a separate method rather than a mode of [boostFor]. Completion and
     * prediction are different questions: a completion has to match what the user is visibly
     * typing, and letting the corpus contribute there is how a strip starts proposing words the
     * user is plainly not writing. With nothing typed there is no prefix to contradict, and the
     * corpus is the only thing that knows anything at all.
     *
     * Empty when there is no signal -- no previous word, an unknown one, or an implementation
     * with no table behind it. Defaulted so an implementation that cannot answer this does not
     * have to say so, the same way [initialize] is.
     */
    fun followersOf(
        previousWord: String?,
        limit: Int,
    ): List<String> = emptyList()
}

@Singleton
class BigramLanguageModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : LanguageModel {
        /**
         * Held fully parsed in memory rather than mmap'd and re-walked per lookup the way
         * base_dict.bin is. Two reasons: it is a fraction of the dictionary's size, and every
         * lookup is by exact previous-word rather than by prefix, so there is no trie to walk
         * and nothing to gain from staying on disk.
         *
         * Volatile so the reference publishes safely to the IO threads that read it.
         */
        @Volatile
        private var table: Map<String, List<Pair<String, Int>>> = emptyMap()

        /** Guards [initialize] so two concurrent callers cannot parse the asset twice. */
        private val initMutex = Mutex()

        override suspend fun initialize() =
            withContext(Dispatchers.IO) {
                if (table.isNotEmpty()) return@withContext
                initMutex.withLock {
                    if (table.isNotEmpty()) return@withLock
                    table = loadTable()
                }
            }

        private fun loadTable(): Map<String, List<Pair<String, Int>>> =
            try {
                // Same placement and the same reasoning as base_dict.bin: noBackupFilesDir is
                // not reclaimable mid-session the way cacheDir is, and is excluded from backup
                // because the payload is reproducible from the APK's own assets.
                // Re-extracted when the app version changes, not merely when absent: see
                // assetBackedFile for the staleness bug that guarding on absence alone caused.
                val dictFile = assetBackedFile(context, "bigram_lm.bin")
                if (dictFile != null) parseFlbg(dictFile.readBytes()) else emptyMap()
            } catch (e: Exception) {
                // Context is an enhancement to ranking, never a precondition for it. A missing
                // or corrupt table must leave autocorrect and glide working exactly as they did
                // before this file existed, not take the keyboard down.
                emptyMap()
            }

        /**
         * The stored followers of [previousWord], or null when the table has never seen it.
         *
         * A contraction whose stripped form is also a real word -- "it's"/"its",
         * "can't"/"cant", "we're"/"were" -- keeps its record under the plain spelling,
         * because in the corpus that count belongs to both senses and rewriting it
         * would have cost the plain word its context entirely. Retrying without the
         * apostrophe is what reaches it. Contractions whose stripped form is not a word
         * ("don't", "i'm", "that's") are stored under the real spelling and hit first.
         */
        private fun followerRecords(previousWord: String?): List<Pair<String, Int>>? {
            if (previousWord == null) return null
            return table[previousWord] ?: table[previousWord.replace("'", "")]
        }

        override fun boostFor(
            previousWord: String?,
            candidate: String,
        ): Float {
            val followers = followerRecords(previousWord) ?: return 0f
            val weight = followers.firstOrNull { it.first == candidate }?.second ?: return 0f
            return weight * BIGRAM_WEIGHT_SCALE
        }

        override fun followersOf(
            previousWord: String?,
            limit: Int,
        ): List<String> {
            if (limit <= 0) return emptyList()
            val followers = followerRecords(previousWord) ?: return emptyList()
            // Sorted here rather than trusted from the file. The weights are what rank these,
            // and nothing in the FLBG format promises an order -- a writer that changed its
            // grouping would silently reorder every prediction with no test able to see it.
            return followers
                .sortedByDescending { it.second }
                .take(limit)
                .map { it.first }
        }
    }

/**
 * Parses the FLBG table written by dictionary-tools/build_bigrams.py.
 *
 * Pure and top-level so the binary format has a test that does not need Robolectric, an
 * Android context, or the real 474 KB asset -- the same reason the format is documented in
 * one place and mirrored 1:1 by the writer.
 *
 * Returns an empty map for anything that is not a well-formed FLBG payload rather than
 * throwing: the caller's contract is that absent context data degrades ranking, never breaks
 * it, and a truncated file is indistinguishable from an absent one as far as that goes.
 */
internal fun parseFlbg(bytes: ByteArray): Map<String, List<Pair<String, Int>>> {
    if (bytes.size < 8) return emptyMap()
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
    val magic =
        buildString {
            repeat(FLBG_MAGIC.length) { append((buf.get().toInt() and 0xFF).toChar()) }
        }
    if (magic != FLBG_MAGIC) return emptyMap()

    val recordCount = buf.getInt()
    if (recordCount <= 0) return emptyMap()

    return try {
        val table = HashMap<String, List<Pair<String, Int>>>(recordCount)
        repeat(recordCount) {
            val prevWord = readShortString(buf)
            val followerCount = buf.get().toInt() and 0xFF
            val followers = ArrayList<Pair<String, Int>>(followerCount)
            repeat(followerCount) {
                val nextWord = readShortString(buf)
                val weight = buf.get().toInt() and 0xFF
                followers.add(nextWord to weight)
            }
            table[prevWord] = followers
        }
        table
    } catch (e: Exception) {
        emptyMap()
    }
}

/** A length-prefixed UTF-8 string: one byte of length, then that many bytes. */
private fun readShortString(buf: ByteBuffer): String {
    val length = buf.get().toInt() and 0xFF
    val raw = ByteArray(length)
    buf.get(raw)
    return String(raw, Charsets.UTF_8)
}
