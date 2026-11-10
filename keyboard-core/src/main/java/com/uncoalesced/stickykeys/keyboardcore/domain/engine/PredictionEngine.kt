// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import android.content.Context
import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.PersonalDictionaryDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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

/** Score a learned word starts from as a correction target; see personalCorrectionTargets. */
private const val PERSONAL_TARGET_BASE = 128f

/** The top of the base dictionary's frequency scale. */
private const val MAX_FREQUENCY = 255f

/**
 * Most personal words scored per correction. Each costs one small edit-distance table, and
 * past this many the commonest words already decide the outcome.
 */
private const val MAX_PERSONAL_TARGETS = 500

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
        val personalLearningEngine: PersonalLearningEngine,
    ) {
        constructor(
            context: Context,
            personalDao: PersonalDictionaryDao,
            languageModel: LanguageModel,
        ) : this(
            context = context,
            personalDao = personalDao,
            languageModel = languageModel,
            personalLearningEngine = PersonalLearningEngine(personalDao),
        )

        init {
            if (languageModel is BigramLanguageModel) {
                languageModel.personalBigramCache = personalLearningEngine.bigramCache
            }
        }

        /**
         * The mapped base dictionary. Volatile so the reference publishes safely across the
         * IO threads that read it. Never navigated directly -- see [reader]: a
         * MappedByteBuffer carries its own position, so concurrent lookups sharing one
         * instance would interleave position() calls and read each other's bytes.
         */
        @Volatile
        private var buffer: MappedByteBuffer? = null

        /** Mapped names, Indian vocabulary, and cultural terms dictionary. */
        @Volatile
        private var namesBuffer: MappedByteBuffer? = null

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val bloomTrajectoryAligner = BloomTrajectoryAligner()

        /** Bloom in-memory revert memory for immediate undo suppression. */
        private val revertHistory = BloomRevertHistory()

        val bloomSpatialProfile = BloomSpatialProfile()

        fun recordRevert(word: String) {
            revertHistory.recordRevert(word)
        }

        fun clearReverts() {
            revertHistory.clear()
        }

        fun isReverted(word: String): Boolean = revertHistory.isReverted(word)

        /** Guards [initialize] so concurrent callers synchronize safely and initialize once. */
        private val initMutex = Mutex()

        @Volatile
        private var isInitialized = false

        /**
         * A private cursor over the shared mapping. duplicate() copies position/limit
         * and shares the underlying bytes, so each lookup gets independent navigation
         * with no extra memory and no copy of the 7 MB payload.
         */
        private fun reader(): ByteBuffer? = buffer?.duplicate()

        /** A private cursor over the names and cultural dictionary mapping. */
        private fun namesReader(): ByteBuffer? = namesBuffer?.duplicate()

        suspend fun initialize() =
            withContext(Dispatchers.IO) {
                if (isInitialized) return@withContext
                initMutex.withLock {
                    if (isInitialized) return@withLock
                    if (languageModel is BigramLanguageModel) {
                        languageModel.personalBigramCache = personalLearningEngine.bigramCache
                    }
                    languageModel.initialize()
                    loadBloomSpatialProfile()
                    personalLearningEngine.prewarmSync()
                    personalLearningEngine.runDecayMaintenanceSync()
                    if (buffer == null) {
                        mapDictionary()
                    }
                    isInitialized = true
                }
            }

        private fun mapDictionary() {
            buffer = mapAssetFile("base_dict.bin")
            namesBuffer = mapAssetFile("names_dict.bin")
        }

        private fun loadBloomSpatialProfile() {
            try {
                val file = File(context.filesDir, "bloom_profile.bin")
                if (file.exists()) {
                    bloomSpatialProfile.load(file)
                }
            } catch (e: Exception) {
            }
        }

        fun saveBloomSpatialProfile() {
            try {
                val file = File(context.filesDir, "bloom_profile.bin")
                bloomSpatialProfile.save(file)
            } catch (e: Exception) {
            }
        }

        private fun saveBloomSpatialProfileAsync() {
            scope.launch {
                saveBloomSpatialProfile()
            }
        }

        fun resetBloomSpatialProfile() {
            bloomSpatialProfile.reset()
            try {
                val file = File(context.filesDir, "bloom_profile.bin")
                if (file.exists()) file.delete()
            } catch (e: Exception) {
            }
        }

        private fun mapAssetFile(assetName: String): MappedByteBuffer? {
            return try {
                // Re-extracted whenever the app version changes, not only when the file is
                // missing -- see assetBackedFile.
                val dictFile = assetBackedFile(context, assetName) ?: return null
                val mapped =
                    RandomAccessFile(dictFile, "r").use { raf ->
                        raf.channel.use { channel ->
                            channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
                        }
                    }

                val header = mapped.duplicate()
                header.position(0)
                val magic =
                    buildString {
                        repeat(4) { append((header.get().toInt() and 0xFF).toChar()) }
                    }
                if (magic == "FLCT") mapped else null
            } catch (e: Exception) {
                null
            }
        }

        suspend fun learnWord(word: String) =
            withContext(Dispatchers.IO) {
                if (word.isBlank() || word.length > 30) return@withContext
                val normalized = word.lowercase().trim()
                // A split correction ("thank you") is two words, and storing it as one would
                // put a phrase into the prefix trie that no prefix lookup can ever match.
                if (normalized.any { it.isWhitespace() }) return@withContext
                personalLearningEngine.onUnigramCommitted(normalized)
            }

        suspend fun learnBigram(
            previousWord: String,
            nextWord: String,
        ) = withContext(Dispatchers.IO) {
            personalLearningEngine.recordBigramTransition(previousWord, nextWord)
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
                val l1Personal =
                    personalLearningEngine.lexicon
                        .wordsMatchingPrefix(normalized, 5)
                        .filter { it.second >= BloomPersonalLexicon.SHIELD_STRIKE_THRESHOLD }
                        .map { Suggestion(it.first, it.second * PERSONAL_WEIGHT) }
                val dbPersonal =
                    try {
                        personalDao
                            .getSuggestionsForPrefix(normalized, 5)
                            .filter { it.frequency >= BloomPersonalLexicon.SHIELD_STRIKE_THRESHOLD }
                            .map { Suggestion(it.word, it.frequency * PERSONAL_WEIGHT) }
                    } catch (e: Exception) {
                        emptyList()
                    }
                val personalSuggestions = (l1Personal + dbPersonal).distinctBy { it.word }

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

                // 2a. Names and Cultural Vocabulary Suggestions
                val namesSuggestions =
                    try {
                        getNamesSuggestions(normalized, 10).map {
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
                for (s in namesSuggestions) {
                    merged[s.word] = maxOf(merged[s.word] ?: 0f, s.score)
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
                val strip =
                    when {
                        bestContraction == null -> ranked
                        ranked.contains(bestContraction) -> ranked
                        ranked.size < MAX_SUGGESTIONS -> ranked + bestContraction
                        else -> ranked.dropLast(1) + bestContraction
                    }

                // 6. Give every entry the typed word's case. Tapping a strip entry replaces
                // the word, so a lowercase entry under "Hel" un-capitalised the sentence:
                // auto-capitalize made the capital and the tap threw it away. The
                // correction offers have always done this; the completions never did.
                val typed = prefix.trim()
                return@withContext strip.map { spellingCorrector.matchCase(typed, it) }
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

        /** Spelling correction, reading base and names mappings through their own cursors. */
        private val spellingCorrector =
            SpellingCorrector(
                listOf(::reader, ::namesReader),
                languageModel,
                personalLearningEngine.lexicon,
            )

        /**
         * Every contraction reachable by putting one apostrophe into [normalized]. See
         * [SpellingCorrector.apostropheVariantsOf].
         */
        internal fun apostropheVariantsOf(normalized: String): List<Pair<String, Int>> =
            spellingCorrector.apostropheVariantsOf(normalized)

        /** The glide decoder, reading the same mapping through its own cursors. */
        private val glideDecoder = GlideDecoder(::reader, languageModel)

        /** GBoard-adjacent continuous trajectory decoder. */
        private val bloomTypeDecoder =
            BloomTypeDecoder(
                dictionary = ::reader,
                namesDictionary = ::namesReader,
                languageModel = languageModel,
                revertHistory = revertHistory,
                profile = bloomSpatialProfile,
                personalWords = { personalCorrectionTargets() },
            )

        /**
         * Decodes a finished glide into ranked word candidates. See [GlideDecoder] and [BloomTypeDecoder].
         *
         * Off the main thread because the walk can visit thousands of trie nodes, and it runs
         * from a gesture handler in the IME process.
         */
        suspend fun decodeGlide(
            stroke: GlideStroke,
            previousWord: String? = null,
        ): List<String> =
            withContext(Dispatchers.IO) {
                val trajectory = stroke.bloomTrajectory
                if (trajectory != null && stroke.keyCentroids.isNotEmpty()) {
                    val bloomCandidates =
                        bloomTypeDecoder.decode(
                            trajectory,
                            { c -> stroke.keyCentroids[c.lowercaseChar()] },
                            previousWord,
                        )
                    if (bloomCandidates.isNotEmpty()) {
                        return@withContext bloomCandidates
                    }
                }
                glideDecoder.decode(stroke, previousWord)
            }

        suspend fun decodeGlideBloom(
            trajectory: BloomTrajectory,
            keyCentroids: (Char) -> Pair<Float, Float>?,
            previousWord: String? = null,
        ): List<String> =
            withContext(Dispatchers.IO) {
                bloomTypeDecoder.decode(trajectory, keyCentroids, previousWord)
            }

        fun learnGlideWord(
            word: String,
            trajectory: BloomTrajectory,
            keyCentroids: (Char) -> Pair<Float, Float>?,
            isSensitive: Boolean = false,
        ) {
            if (isSensitive) return
            val normalized = word.lowercase().trim()
            if (normalized.length < 2 || !trajectory.isUsable) return

            val sampleIndices = IntArray(normalized.length)
            val score =
                bloomTrajectoryAligner.alignAndScore(
                    trajectory = trajectory,
                    word = normalized,
                    keyCentroids = keyCentroids,
                    profile = bloomSpatialProfile,
                    outSampleIndices = sampleIndices,
                ) ?: return

            // Save snapshot before learning for instant rollback on backspace or alternative selection
            bloomSpatialProfile.saveSnapshot()

            for (k in normalized.indices) {
                val char = normalized[k]
                val nominal = keyCentroids(char) ?: continue
                val sampleIdx = sampleIndices[k]
                if (sampleIdx in 0 until trajectory.count) {
                    val observedX = trajectory.getX(sampleIdx)
                    val observedY = trajectory.getY(sampleIdx)
                    bloomSpatialProfile.learnObservation(
                        char = char,
                        nominalX = nominal.first,
                        nominalY = nominal.second,
                        observedX = observedX,
                        observedY = observedY,
                        isSensitive = false,
                    )
                }
            }
            saveBloomSpatialProfileAsync()
        }

        fun rollbackLastGlideLearning(): Boolean {
            val restored = bloomSpatialProfile.rollbackLastLearning()
            if (restored) {
                saveBloomSpatialProfileAsync()
            }
            return restored
        }

        fun correctGlideLearning(
            wrongWord: String,
            chosenWord: String,
            trajectory: BloomTrajectory,
            keyCentroids: (Char) -> Pair<Float, Float>?,
            isSensitive: Boolean = false,
        ) {
            if (isSensitive || !trajectory.isUsable) return
            bloomSpatialProfile.rollbackLastLearning()
            recordRevert(wrongWord)
            learnGlideWord(chosenWord, trajectory, keyCentroids, isSensitive)
        }

        fun revertGlideWord(word: String) {
            recordRevert(word)
            rollbackLastGlideLearning()
        }

        internal fun getBaseSuggestions(
            prefix: String,
            limit: Int,
        ): List<Suggestion> = getTrieSuggestions(reader(), prefix, limit)

        internal fun getNamesSuggestions(
            prefix: String,
            limit: Int,
        ): List<Suggestion> = getTrieSuggestions(namesReader(), prefix, limit)

        private fun getTrieSuggestions(
            buf: ByteBuffer?,
            prefix: String,
            limit: Int,
        ): List<Suggestion> {
            if (buf == null) return emptyList()
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

            val results = mutableListOf<Suggestion>()
            val queue = ArrayDeque<Pair<Int, String>>()
            queue.add(Pair(currentOffset, prefix))
            var visited = 0

            while (queue.isNotEmpty() && visited < MAX_SUGGESTION_NODES) {
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

        /**
         * The correction to apply to [typedWord], or null to leave it alone. [startsClause] is
         * true when the word follows a comma, a sentence end or similar; it only affects the
         * contractions whose bare form is also a word (see ContractionRules).
         */
        suspend fun getAutoCorrection(
            typedWord: String,
            previousWord: String? = null,
            startsClause: Boolean = false,
            touches: List<TouchPoint>? = null,
        ): String? =
            withContext(Dispatchers.IO) {
                val trimmed = typedWord.trim()
                val normalized = trimmed.lowercase()

                // 0. Bloom Revert Memory: if this word was recently reverted, suppress re-correction.
                if (revertHistory.isReverted(normalized)) return@withContext null

                // The pronoun is the one word whose correct form never depends on what was
                // typed, so it is settled before every rule below -- including the length
                // floor, which it would otherwise never get past.
                // An "I" that is already right returns null, not itself: a non-null answer is
                // applied and counted as a correction, with an undo chip for nothing.
                if (normalized == "i") return@withContext if (trimmed == "I") null else "I"

                // Ahead of the personal-dictionary veto on purpose. Every bare form here has
                // been accepted and learned on every space press since the keyboard shipped,
                // so nearly every existing user already holds "im" and "ill" at frequency 2
                // or more, and the veto would make this pass unreachable for exactly the
                // people who type them. The rules are narrow enough to be applied regardless;
                // see ContractionRules.
                contractionFor(trimmed, previousWord, startsClause)?.let {
                    return@withContext spellingCorrector.matchCase(trimmed, it)
                }

                // Too short to safely autocorrect.
                if (normalized.length < 3) return@withContext null

                // Words the user has typed at least twice are treated as deliberate and are
                // never corrected. A single occurrence is not enough -- otherwise every
                // one-off typo would permanently disable its own correction.
                if (personalLearningEngine.lexicon.isShielded(normalized)) return@withContext null
                val personalEntry = personalDao.getWord(normalized)
                if (personalEntry != null &&
                    personalEntry.frequency >= BloomPersonalLexicon.SHIELD_STRIKE_THRESHOLD
                ) {
                    return@withContext null
                }

                val corrected =
                    spellingCorrector.restoreApostrophe(normalized)
                        ?: spellingCorrector.splitOnMispressedSpace(normalized)
                        ?: spellingCorrector.nearestWord(
                            normalized,
                            previousWord,
                            personalCorrectionTargets(),
                            touches,
                            revertHistory,
                        )
                        ?: return@withContext null

                // Restore the shape the user typed. Auto-capitalize means the first word of
                // every message arrives with a capital, so returning the dictionary's lowercase
                // form would silently un-capitalize the start of most sentences it fixed.
                return@withContext spellingCorrector.matchCase(trimmed, corrected)
            }

        /**
         * Corrections to offer in the strip for a word [getAutoCorrection] left alone.
         *
         * Empty wherever autocorrect would have nothing to say anyway: short words, words the
         * user has made their own, and anything that is already a dictionary word. Case
         * follows what was typed, as it does for an applied correction.
         */
        suspend fun getCorrectionOffers(typedWord: String): List<String> =
            withContext(Dispatchers.IO) {
                val trimmed = typedWord.trim()
                val normalized = trimmed.lowercase()
                if (normalized.length < 3) return@withContext emptyList()
                if (personalLearningEngine.lexicon.isShielded(
                        normalized,
                    )
                ) {
                    return@withContext emptyList()
                }
                val personalEntry = personalDao.getWord(normalized)
                if (personalEntry != null &&
                    personalEntry.frequency >= BloomPersonalLexicon.SHIELD_STRIKE_THRESHOLD
                ) {
                    return@withContext emptyList()
                }
                try {
                    spellingCorrector
                        .offersFor(normalized, personalCorrectionTargets())
                        .map { spellingCorrector.matchCase(trimmed, it) }
                } catch (e: Exception) {
                    emptyList()
                }
            }

        /**
         * Records that the user deliberately kept [word] by undoing a correction of it.
         *
         * Stronger than [learnWord]: it lifts the word straight to the two-strike threshold,
         * so the same correction is not applied again the next time it is typed. Undoing is
         * the clearest signal a keyboard gets that a word is real, and making the user undo
         * the same correction twice before it stops would teach them the undo does nothing.
         */
        suspend fun keepWord(word: String) =
            withContext(Dispatchers.IO) {
                personalLearningEngine.onWordKept(word)
            }

        /**
         * The user's own words as correction targets, scored on the base dictionary's scale.
         *
         * Only words past the two-strike rule, the same bar [getSuggestions] uses, so a typo
         * learned once never becomes something other typos correct to. A word used twice is
         * scored as a mid-frequency dictionary word and climbs with use: the base scale says
         * nothing about a word it does not contain, and the user has already said twice that
         * this one is real.
         */
        private fun personalCorrectionTargets(): List<Pair<String, Float>> {
            val fromDb =
                try {
                    personalDao
                        .getFrequentWords(
                            minFrequency = BloomPersonalLexicon.SHIELD_STRIKE_THRESHOLD,
                            limit = MAX_PERSONAL_TARGETS,
                        ).map {
                            it.word to
                                (PERSONAL_TARGET_BASE + it.frequency * PERSONAL_WEIGHT)
                                    .coerceAtMost(MAX_FREQUENCY)
                        }
                } catch (e: Exception) {
                    emptyList()
                }
            val fromLexicon =
                personalLearningEngine.lexicon
                    .snapshot()
                    .filter { it.value >= BloomPersonalLexicon.SHIELD_STRIKE_THRESHOLD }
                    .map { (w, count) ->
                        w to
                            (PERSONAL_TARGET_BASE + count * PERSONAL_WEIGHT).coerceAtMost(
                                MAX_FREQUENCY,
                            )
                    }
            return (fromLexicon + fromDb).distinctBy { it.first }.take(MAX_PERSONAL_TARGETS)
        }
    }
