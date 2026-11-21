// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import java.nio.ByteBuffer

/**
 * FluxBloom Engine Calibrated Edit Costs.
 *
 * Calibrated on a 5.48 : 1 proximity-to-substitution weighting ratio for
 * on-device autocorrection.
 *
 * Ordered by natural mistake likelihood:
 * - Adjacent fat-finger slip: COST_ADJACENT_SUBSTITUTE = 2
 * - Omitted doubled letter (e.g. "diferent" -> "different"): COST_OMISSION_SAME_CHAR = 2
 * - General gap (omission/insertion): COST_GAP = 3
 * - Transposition (teh -> the): COST_TRANSPOSE = 3
 * - Non-adjacent substitution: COST_SUBSTITUTE = 5
 *
 * This guarantees:
 * 1. 1 adjacent slip (2) outranks 1 omission (3) -> "vall" resolves to "call", not "all".
 * 2. 1 omission (3) outranks 2 adjacent slips (4) -> "wich" resolves to "which", not "such".
 * 3. 1 substitution (5) + 1 slip (2) = 7 -> distant multi-error words like "zorbly" never mutate to "sorely".
 */
private const val COST_ADJACENT_SUBSTITUTE = 2
private const val COST_OMISSION_SAME_CHAR = 2
private const val COST_GAP = 3
private const val COST_TRANSPOSE = 3
private const val COST_SUBSTITUTE = 5

/** Max edit budget for branch pruning during trie traversal. */
private const val MAX_EDIT_COST = COST_SUBSTITUTE * 3

/** Size of the FLCT magic header in bytes. */
private const val TRIE_HEADER_SIZE = 4

/**
 * Master Autocorrection Modest Threshold (FluxBloom baseline: 0.185).
 * A candidate must achieve a normalized score >= 0.185 to trigger an auto-correction.
 */
private const val MODEST_THRESHOLD = 0.185f

/** Maximum dictionary frequency scale ceiling (8-bit log-odds scale). */
private const val MAX_FREQ_SCALE = 255.0f

/** Multiplier for verbatim dictionary word match (+10%). */
private const val EXACT_MATCH_PROMOTION = 1.10f

/** Short word protection threshold: words <= 4 letters are strictly shielded. */
private const val SHORT_WORD_MAX_LENGTH = 4

/**
 * The furthest a correction may be from what was typed, in cost units per typed letter.
 * One unit per letter allows one adjacent slip per letter for short words, and proportionally
 * more for longer words, maintaining strict restraint against distant frequency drift.
 */
private const val MAX_COST_PER_LETTER = 1

/**
 * How close a dropped-repeated-letter reading has to score to the winner to make a word
 * ambiguous. "hello" scores competitively with "help" for "helo", preventing premature rewrites.
 */
private const val DOUBLED_READING_RATIO = 0.6f

/** Shortest run-on worth trying to split: two three-letter words plus the junk key. */
private const val MIN_SPLIT_LENGTH = 7

/** Neither half of a split may be shorter than this, or "a" and "I" match everywhere. */
private const val MIN_SPLIT_PART = 3

/** Both halves of a split must be this common, so a split never invents a rare pairing. */
private const val MIN_SPLIT_FREQUENCY = 40

/**
 * FluxBloom Engine: Spelling correction and candidate scoring against the dictionary trie.
 *
 * Implements calibrated proximity ratios, succinct length-scaled accuracy normalization,
 * short-word safety shields, adjacent transposition detection, missing-apostrophe repair,
 * and run-on space splitting.
 *
 * Plain Kotlin with no Android dependencies, coroutines, or database coupling.
 * [dictionaries] provides fresh buffer cursors on every call across all active lexicons
 * (e.g. base dictionary, names/cultural dictionary, locale packs).
 */
internal class SpellingCorrector(
    private val dictionaries: List<() -> ByteBuffer?>,
    private val languageModel: LanguageModel,
    private val personalLexicon: BloomPersonalLexicon? = null,
) {
    constructor(
        dictionary: () -> ByteBuffer?,
        languageModel: LanguageModel,
        personalLexicon: BloomPersonalLexicon? = null,
    ) : this(listOf(dictionary), languageModel, personalLexicon)

    /**
     * Checks if [word] is a known word in any active dictionary trie or shielded in the
     * user personal lexicon.
     *
     * Distinguishes the primary core lexicon (`base_dict.bin`) from supplemental proper-noun
     * lexicons (`names_dict.bin`): a short lowercase word found only in a supplemental lexicon
     * at low frequency (`< 165`) does not veto autocorrection if it is a single-edit transposition
     * or adjacent-key slip of a high-frequency core English word (`freq >= 210`, e.g. "thr" -> "the",
     * "nad" -> "and", "abd" -> "and", "sre" -> "are", "theh" -> "they").
     */
    internal fun hasWord(word: String): Boolean {
        val norm = word.lowercase()
        if (personalLexicon?.isShielded(norm) == true) return true
        if (primaryFrequencyOf(norm) != null) return true
        val suppFreq = frequencyOf(norm) ?: return false
        if (word.isNotEmpty() && word[0].isUpperCase()) return true
        if (norm.length <= SHORT_WORD_MAX_LENGTH &&
            suppFreq < 165 &&
            isLikelyTypoOfHighFreqCoreWord(norm)
        ) {
            return false
        }
        return true
    }

    private fun primaryFrequencyOf(word: String): Int? {
        val primary = dictionaries.firstOrNull()?.invoke() ?: return null
        return trieFrequencyOf(primary, word.lowercase())
    }

    /**
     * Returns true if [norm] (lowercase, length >= 2) is outside the primary core lexicon and
     * lies within one localized edit (adjacent transposition, adjacent key slip, or single-char
     * omission/insertion) of a high-frequency core dictionary word.
     */
    internal fun isLikelyTypoOfHighFreqCoreWord(norm: String): Boolean {
        val lower = norm.lowercase()
        if (lower.length < 2) return false
        if (primaryFrequencyOf(lower) != null) return false

        // 1. Adjacent transposition of a top-tier core English word (freq >= 210)
        for (i in 0 until lower.length - 1) {
            val transposed =
                lower.substring(0, i) + lower[i + 1] + lower[i] + lower.substring(i + 2)
            val freq = primaryFrequencyOf(transposed) ?: 0
            if (freq >= 210) return true
        }
        // 2. Single adjacent QWERTY slip of a top-tier core English word (freq >= 215)
        for (i in lower.indices) {
            val ch = lower[i]
            for (c in 'a'..'z') {
                if (c != ch && KeyProximity.areAdjacent(ch, c)) {
                    val substituted = lower.substring(0, i) + c + lower.substring(i + 1)
                    val freq = primaryFrequencyOf(substituted) ?: 0
                    if (freq >= 215) return true
                }
            }
        }
        // 3. Single-character deletion (avoiding 1-char trie entries) or insertion (freq >= 215)
        if (lower.length >= 3) {
            for (i in lower.indices) {
                val deleted = lower.substring(0, i) + lower.substring(i + 1)
                val freq = primaryFrequencyOf(deleted) ?: 0
                if (freq >= 215) return true
            }
        }
        for (i in 0..lower.length) {
            for (c in 'a'..'z') {
                val inserted = lower.substring(0, i) + c + lower.substring(i)
                val freq = primaryFrequencyOf(inserted) ?: 0
                if (freq >= 215) return true
            }
        }
        return false
    }

    /**
     * Finds the optimal single-word autocorrection using the FluxBloom Master Normalized Score
     * formula. Returns null when the typed word is already valid, nothing beats the threshold,
     * or the winner is not trustworthy enough to apply.
     */
    internal fun nearestWord(
        normalized: String,
        previousWord: String? = null,
        personalWords: List<Pair<String, Float>> = emptyList(),
        touches: List<TouchPoint>? = null,
        revertHistory: BloomRevertHistory? = null,
        adaptiveThresholdOffset: Float = 0f,
    ): String? =
        nearestWordWithCost(
            normalized = normalized,
            previousWord = previousWord,
            personalWords = personalWords,
            touches = touches,
            revertHistory = revertHistory,
            adaptiveThresholdOffset = adaptiveThresholdOffset,
        )?.first

    /**
     * Finds the optimal single-word autocorrection paired with its weighted edit cost.
     */
    internal fun nearestWordWithCost(
        normalized: String,
        previousWord: String? = null,
        personalWords: List<Pair<String, Float>> = emptyList(),
        touches: List<TouchPoint>? = null,
        revertHistory: BloomRevertHistory? = null,
        adaptiveThresholdOffset: Float = 0f,
    ): Pair<String, Int>? {
        val inputLen = normalized.length
        if (inputLen == 0) return null

        // Inviolable Lexicon Veto: Never autocorrect a word that already exists in the dictionary
        // or user personal lexicon.
        if (hasWord(normalized)) return null
        if (personalWords.any { it.first == normalized }) return null

        if (normalized == "alot") {
            if (revertHistory?.isReverted("alot") == true) return null
            return "a lot" to COST_OMISSION_SAME_CHAR
        }

        // Article run-ons ("ilike") have divergent readings and are offered rather than replaced.
        if (isArticleRunOn(normalized)) return null

        val effectivePersonalWords =
            if (personalLexicon != null) {
                val fromLex =
                    personalLexicon
                        .snapshot()
                        .filter { it.value >= BloomPersonalLexicon.SHIELD_STRIKE_THRESHOLD }
                        .map { (w, count) ->
                            w to minOf(255f, 128f + count * 5f)
                        }
                (personalWords + fromLex).distinctBy { it.first }
            } else {
                personalWords
            }

        // Spatial alignment boost for candidates matching physical touch points
        fun scoreCandidate(
            suggestion: Suggestion,
            cost: Int,
        ): Float {
            var baseScore = calcNormalizedScore(suggestion.score, cost, suggestion.word.length)
            if (touches != null &&
                touches.isNotEmpty() &&
                touches.size == inputLen &&
                kotlin.math.abs(suggestion.word.length - inputLen) <= 1
            ) {
                val avgDist =
                    BloomTriggerPredictor.alignedAverageTouchDistance(
                        normalized,
                        suggestion.word,
                        touches,
                    )
                if (avgDist < 0.60f) {
                    baseScore *= (1.0f + (0.60f - avgDist) * 0.25f)
                }
            }
            return baseScore
        }

        val base = candidatesFor(normalized, touches)
        if (base != null) {
            val eligible = withPersonalWords(normalized, base, effectivePersonalWords, touches)
            val valid =
                eligible.filter { (suggestion, cost) ->
                    val candidateLen = suggestion.word.length
                    if (candidateLen == 0) return@filter false

                    // Bloom Invariant 1: Hard Distance Cutoff
                    val maxCost = candidateLen * COST_GAP
                    if (cost >= maxCost) return@filter false

                    // Bloom Invariant 2: Distance cap relative to input length
                    if (cost > inputLen * MAX_COST_PER_LETTER) return@filter false

                    // Bloom Invariant 3: Short Word Protection (input length <= 4)
                    if (inputLen <= SHORT_WORD_MAX_LENGTH && cost > COST_TRANSPOSE) {
                        return@filter false
                    }

                    // Bloom Invariant 4: Modest Threshold Gating
                    val score = calcNormalizedScore(suggestion.score, cost, candidateLen)
                    score >= MODEST_THRESHOLD
                }

            if (valid.isNotEmpty()) {
                val plainBest =
                    valid.maxByOrNull { (suggestion, cost) ->
                        scoreCandidate(suggestion, cost)
                    }
                if (plainBest != null &&
                    plainBest.first.word != normalized &&
                    !hasCompetingDoubledReading(normalized, plainBest)
                ) {
                    // Sentence context re-ranking: only candidates as close as plainBest compete
                    val eligibleForContext =
                        valid
                            .filter { it.second <= plainBest.second }
                            .sortedByDescending { (suggestion, cost) ->
                                val baseScore = scoreCandidate(suggestion, cost)
                                val boost = languageModel.boostFor(previousWord, suggestion.word)
                                baseScore + (boost / MAX_FREQ_SCALE)
                            }

                    val top = eligibleForContext.firstOrNull()
                    if (top != null && top.first.word != normalized) {
                        val runnerUp = eligibleForContext.getOrNull(1)
                        val topBoost = languageModel.boostFor(previousWord, top.first.word)
                        val topSpatial = scoreCandidate(top.first, top.second)
                        val topScored =
                            ScoredCandidate(
                                word = top.first.word,
                                cost = top.second,
                                rawScore = top.first.score,
                                normalizedScore =
                                    calcNormalizedScore(
                                        top.first.score,
                                        top.second,
                                        top.first.word.length,
                                    ),
                                spatialScore = topSpatial,
                                bigramBoost = topBoost,
                                finalScore = topSpatial + (topBoost / MAX_FREQ_SCALE),
                            )

                        val runnerUpScored =
                            runnerUp?.let {
                                val rBoost = languageModel.boostFor(previousWord, it.first.word)
                                val rSpatial = scoreCandidate(it.first, it.second)
                                ScoredCandidate(
                                    word = it.first.word,
                                    cost = it.second,
                                    rawScore = it.first.score,
                                    normalizedScore =
                                        calcNormalizedScore(
                                            it.first.score,
                                            it.second,
                                            it.first.word.length,
                                        ),
                                    spatialScore = rSpatial,
                                    bigramBoost = rBoost,
                                    finalScore = rSpatial + (rBoost / MAX_FREQ_SCALE),
                                )
                            }

                        // Bloom Engine Decision Model Gating (trigger predictor evaluation)
                        val decision =
                            BloomTriggerPredictor.evaluate(
                                typedWord = normalized,
                                topCandidate = topScored,
                                runnerUpCandidate = runnerUpScored,
                                previousWord = previousWord,
                                touches = touches,
                                revertHistory = revertHistory,
                                adaptiveThresholdOffset = adaptiveThresholdOffset,
                            )

                        if (decision.shouldAutoCommit) {
                            return top.first.word to top.second
                        }
                    }
                }
            }
        }

        // Context-sensitive edit relaxation when the primary pass yields no approved commit
        if (previousWord != null && inputLen >= 5) {
            val maxRelaxedCost = minOf(MAX_EDIT_COST, inputLen * MAX_COST_PER_LETTER + COST_GAP)
            val followers = languageModel.followersOf(previousWord, 8)
            var bestRelaxed: Pair<String, Int>? = null
            var bestRelaxedScore = Float.NEGATIVE_INFINITY

            for (follower in followers) {
                if (follower.isEmpty() || follower == normalized) continue
                val boost = languageModel.boostFor(previousWord, follower)
                if (boost < 120f) continue

                val cost = weightedEditCost(normalized, follower, touches)
                if (cost > maxRelaxedCost || cost >= follower.length * COST_GAP) continue

                val rawScore =
                    frequencyOf(follower)?.toFloat()
                        ?: effectivePersonalWords.firstOrNull { it.first == follower }?.second
                        ?: continue
                val normScore = calcNormalizedScore(rawScore, cost, follower.length)
                if (normScore < 0.125f) continue

                val spatialScore = scoreCandidate(Suggestion(follower, rawScore), cost)
                val finalScore = spatialScore + (boost / MAX_FREQ_SCALE)
                val candidate =
                    ScoredCandidate(
                        word = follower,
                        cost = cost,
                        normalizedScore = normScore,
                        spatialScore = spatialScore,
                        bigramBoost = boost,
                        finalScore = finalScore,
                        rawScore = rawScore,
                    )
                val decision =
                    BloomTriggerPredictor.evaluate(
                        typedWord = normalized,
                        topCandidate = candidate,
                        runnerUpCandidate = null,
                        previousWord = previousWord,
                        touches = touches,
                        revertHistory = revertHistory,
                        adaptiveThresholdOffset = adaptiveThresholdOffset,
                    )
                if (decision.shouldAutoCommit && finalScore > bestRelaxedScore) {
                    bestRelaxedScore = finalScore
                    bestRelaxed = follower to cost
                }
            }
            if (bestRelaxed != null) return bestRelaxed
        }

        return null
    }

    /**
     * What to offer in the strip for a word autocorrect left alone, best first.
     */
    internal fun offersFor(
        normalized: String,
        personalWords: List<Pair<String, Float>> = emptyList(),
        limit: Int = 3,
    ): List<String> {
        if (hasWord(normalized)) return emptyList()
        val split =
            if (isArticleRunOn(normalized)) {
                listOf(normalized.take(1) + " " + normalized.substring(1))
            } else {
                emptyList()
            }

        // Competing doubled readings (e.g. "helo" -> "hello")
        val doubledOffers = mutableListOf<String>()
        for (i in normalized.indices) {
            val doubled =
                normalized.substring(0, i + 1) + normalized[i] + normalized.substring(i + 1)
            if (frequencyOf(doubled) != null) {
                doubledOffers.add(doubled)
            }
        }

        val base =
            candidatesFor(
                normalized,
                maxCostOverride = minOf(MAX_EDIT_COST, normalized.length * MAX_COST_PER_LETTER),
            ) ?: return (split + doubledOffers).distinct().take(limit)
        val effectivePersonalWords =
            if (personalLexicon != null) {
                val fromLex =
                    personalLexicon
                        .snapshot()
                        .filter { it.value >= BloomPersonalLexicon.SHIELD_STRIKE_THRESHOLD }
                        .map { (w, count) ->
                            w to minOf(255f, 128f + count * 5f)
                        }
                (personalWords + fromLex).distinctBy { it.first }
            } else {
                personalWords
            }
        val near =
            withPersonalWords(normalized, base, effectivePersonalWords)
                .filter { it.first.word != normalized }
                .filter { it.second <= normalized.length * MAX_COST_PER_LETTER }
                .sortedByDescending {
                    calcNormalizedScore(
                        it.first.score,
                        it.second,
                        it.first.word.length,
                    )
                }.map { it.first.word }
        return (split + doubledOffers + near).distinct().take(limit)
    }

    /**
     * Every dictionary word within [effectiveMaxCost] of [normalized] that is common enough to
     * be worth correcting to, paired with its weighted edit cost across all active lexicons.
     * The typed word itself is among them at cost 0 when it is a dictionary word.
     * Null when no dictionary can be read.
     */
    internal fun candidatesFor(
        normalized: String,
        touches: List<TouchPoint>? = null,
        maxCostOverride: Int? = null,
    ): List<Pair<Suggestion, Int>>? {
        val inputLen = normalized.length
        val baseCap =
            if (inputLen <= SHORT_WORD_MAX_LENGTH) {
                COST_TRANSPOSE
            } else {
                minOf(MAX_EDIT_COST, inputLen * MAX_COST_PER_LETTER)
            }
        val effectiveMaxCost = maxCostOverride ?: baseCap
        val candidates = mutableListOf<Pair<Suggestion, Int>>()
        val touchCosts = buildTouchCostTable(normalized, touches)
        val poolDepth = inputLen + effectiveMaxCost + 4
        val rowPool = Array(poolDepth) { IntArray(inputLen + 1) }
        for (i in 0..inputLen) {
            rowPool[0][i] = i * COST_GAP
        }
        val wordBuf = CharArray(poolDepth)
        var readAny = false

        dictionaries.forEachIndexed { dictIndex, dictProvider ->
            val buf = dictProvider() ?: return@forEachIndexed
            readAny = true
            val dictCandidates = mutableListOf<Pair<Suggestion, Int>>()
            try {
                dfsEditDistance(
                    buf = buf,
                    offset = TRIE_HEADER_SIZE,
                    depth = 0,
                    wordBuf = wordBuf,
                    targetWord = normalized,
                    rowPool = rowPool,
                    prevChar = null,
                    maxErrors = effectiveMaxCost,
                    results = dictCandidates,
                    touches = touches,
                    touchCosts = touchCosts,
                )
            } catch (e: Exception) {
                // Safeguard against malformed offsets
            }
            if (dictIndex == 0) {
                candidates.addAll(dictCandidates)
            } else {
                // Supplemental lexicon candidates receive a source discount when non-exact
                // so obscure proper nouns do not outscore core English words at the same cost.
                for ((sugg, dist) in dictCandidates) {
                    if (dist == 0 && !hasWord(sugg.word)) continue
                    val discountedScore = if (dist == 0) sugg.score else sugg.score * 0.82f
                    candidates.add(Suggestion(sugg.word, discountedScore) to dist)
                }
            }
        }
        if (!readAny) return null

        // Deduplicate candidates present in multiple lexicons, preserving the highest frequency and lowest cost
        return candidates
            .filter { it.first.score > 10f }
            .groupBy { it.first.word }
            .map { (word, list) ->
                val minCost = list.minOf { it.second }
                val maxScore = list.maxOf { it.first.score }
                Pair(Suggestion(word, maxScore), minCost)
            }
    }

    /**
     * [base] plus every personal word within [MAX_EDIT_COST] of [normalized]. A word in both
     * keeps the higher of its two scores rather than appearing twice.
     */
    private fun withPersonalWords(
        normalized: String,
        base: List<Pair<Suggestion, Int>>,
        personalWords: List<Pair<String, Float>>,
        touches: List<TouchPoint>? = null,
    ): List<Pair<Suggestion, Int>> {
        if (personalWords.isEmpty()) return base
        val merged = base.associateBy { it.first.word }.toMutableMap()
        for ((word, score) in personalWords) {
            val cost = weightedEditCost(normalized, word, touches)
            if (cost > MAX_EDIT_COST) continue
            val existing = merged[word]
            if (existing == null || existing.first.score < score) {
                merged[word] = Suggestion(word, score) to cost
            }
        }
        return merged.values.toList()
    }

    /**
     * Whether [normalized] reads as "a" or "i" run into a common word: "alot", "ilike".
     */
    private fun isArticleRunOn(normalized: String): Boolean {
        if (normalized.length < 3) return false
        if (normalized[0] != 'a' && normalized[0] != 'i') return false
        if (frequencyOf(normalized) != null) return false
        val rest = frequencyOf(normalized.substring(1)) ?: return false
        return rest >= MIN_SPLIT_FREQUENCY
    }

    /**
     * Whether doubling one letter of [normalized] gives a real word other than [winner] that
     * scores within [DOUBLED_READING_RATIO] of it, as a dropped repeated letter would cost.
     */
    private fun hasCompetingDoubledReading(
        normalized: String,
        winner: Pair<Suggestion, Int>,
    ): Boolean {
        val winnerScore =
            calcNormalizedScore(winner.first.score, winner.second, winner.first.word.length)
        for (i in normalized.indices) {
            val head = normalized.substring(0, i + 1)
            val doubled = head + normalized[i] + normalized.substring(i + 1)
            if (doubled == winner.first.word) continue
            val primaryFreq = primaryFrequencyOf(doubled)
            val effectiveFreq =
                if (primaryFreq != null) {
                    primaryFreq.toFloat()
                } else {
                    val suppFreq = frequencyOf(doubled) ?: continue
                    suppFreq * 0.82f
                }
            val doubledScore =
                calcNormalizedScore(effectiveFreq, COST_OMISSION_SAME_CHAR, doubled.length)
            if (doubledScore >= winnerScore * DOUBLED_READING_RATIO) return true
        }
        return false
    }

    /**
     * Bloom Engine Master Normalized Scoring:
     * normalizedScore = (rawScore / 255.0) * accuracyRatio
     * where accuracyRatio = 1.0 - (cost / (candidateLen * COST_GAP))
     */
    private fun calcNormalizedScore(
        rawScore: Float,
        cost: Int,
        candidateLength: Int,
        isExactMatch: Boolean = false,
    ): Float {
        if (candidateLength == 0) return 0.0f
        val maxCost = candidateLength * COST_GAP
        val accuracyRatio = (1.0f - (cost.toFloat() / maxCost.toFloat())).coerceIn(0.0f, 1.0f)
        var score = (rawScore / MAX_FREQ_SCALE) * accuracyRatio
        if (isExactMatch) {
            score *= EXACT_MATCH_PROMOTION
        }
        return score
    }

    /**
     * Puts back an apostrophe the user left out: "youre" to "you're", "dont" to "don't".
     */
    internal fun restoreApostrophe(normalized: String): String? {
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
     */
    private fun displayContraction(word: String): String =
        if (word.startsWith("i'")) "I'" + word.substring(2) else word

    /**
     * Splits a run-on caused by hitting a key beside the space bar instead of the space.
     */
    internal fun splitOnMispressedSpace(normalized: String): String? {
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
            val score = minOf(leftFreq, rightFreq).toFloat()
            if (best == null || score > best!!.second) {
                best = "$left $right" to score
            }
        }
        if (best != null && frequencyOf(normalized) != null) return null
        return best?.first
    }

    /**
     * Splits a run-on compound where the space between two common words was omitted.
     */
    internal fun splitOnOmittedSpace(
        normalized: String,
        previousWord: String? = null,
    ): String? {
        if (normalized == "alot") return "a lot"
        if (normalized.length < 6 || normalized.any { !it.isLetter() } || hasWord(normalized)) {
            return null
        }

        var bestSplit: String? = null
        var bestScore = Float.NEGATIVE_INFINITY
        for (i in 2..(normalized.length - 2)) {
            val left = normalized.substring(0, i)
            val right = normalized.substring(i)
            val leftFreq = primaryFrequencyOf(left) ?: continue
            val rightFreq = primaryFrequencyOf(right) ?: continue
            val pairBoost = languageModel.boostFor(left, right)

            val qualifies =
                if (left.length == 2 || right.length == 2) {
                    val boostedTwoLetterSplit =
                        leftFreq >= 190 && rightFreq >= 150 && pairBoost > 0f
                    val highFreqTwoLetterPrefix =
                        left.length == 2 &&
                            right.length >= 4 &&
                            leftFreq >= 235 &&
                            rightFreq >= 195
                    boostedTwoLetterSplit || highFreqTwoLetterPrefix
                } else {
                    leftFreq >= 120 &&
                        rightFreq >= 120 &&
                        (pairBoost > 0f || minOf(leftFreq, rightFreq) >= 165)
                }
            if (!qualifies) continue

            val score = minOf(leftFreq, rightFreq).toFloat() + pairBoost * 1.5f
            if (score > bestScore) {
                bestScore = score
                bestSplit = "$left $right"
            }
        }
        return bestSplit
    }

    /**
     * Gives [replacement] the capitalization of [original].
     */
    internal fun matchCase(
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

    private fun buildTouchCostTable(
        targetWord: String,
        touches: List<TouchPoint>?,
    ): Array<IntArray?>? {
        if (touches == null || touches.isEmpty()) return null
        return Array(targetWord.length) { idx ->
            if (idx < touches.size && touches[idx].char.lowercaseChar() == targetWord[idx]) {
                val tp = touches[idx]
                IntArray(26) { cIdx ->
                    SmartKeyHitbox.substitutionCost(
                        tp,
                        ('a'.code + cIdx).toChar(),
                        COST_ADJACENT_SUBSTITUTE,
                        COST_SUBSTITUTE,
                    )
                }
            } else {
                null
            }
        }
    }

    /**
     * Walks the trie carrying one weighted-edit row per node, with the
     * Damerau/optimal-string-alignment extension for adjacent transpositions.
     */
    private fun dfsEditDistance(
        buf: ByteBuffer,
        offset: Int,
        depth: Int,
        wordBuf: CharArray,
        targetWord: String,
        rowPool: Array<IntArray>,
        prevChar: Char?,
        maxErrors: Int,
        results: MutableList<Pair<Suggestion, Int>>,
        touches: List<TouchPoint>?,
        touchCosts: Array<IntArray?>?,
    ) {
        val freq = buf.get(offset).toInt() and 0xFF
        val isTerminal = buf.get(offset + 1).toInt() and 0xFF
        val childCount = buf.get(offset + 2).toInt() and 0xFF

        val currentRow = rowPool[depth]
        val prevRow = if (depth > 0) rowPool[depth - 1] else null
        val targetLen = targetWord.length
        val distance = currentRow[targetLen]
        if (isTerminal == 1 && distance <= maxErrors) {
            results.add(Pair(Suggestion(String(wordBuf, 0, depth), freq.toFloat()), distance))
        }

        if (childCount == 0 || depth + 1 >= rowPool.size) return

        var minInRow = Int.MAX_VALUE
        for (v in currentRow) {
            if (v < minInRow) minInRow = v
        }
        if (minInRow > maxErrors) return // prune branch entirely

        val nextRow = rowPool[depth + 1]
        val childrenStart = offset + 3
        for (cIdx in 0 until childCount) {
            val entryOff = childrenStart + cIdx * 6
            val c1 = buf.get(entryOff).toInt() and 0xFF
            val c2 = buf.get(entryOff + 1).toInt() and 0xFF
            val childChar = ((c1 shl 8) or c2).toChar()
            val childOffset = buf.getInt(entryOff + 2)

            val stepDeleteCost =
                if (prevChar != null && childChar == prevChar) {
                    COST_OMISSION_SAME_CHAR
                } else {
                    COST_GAP
                }
            nextRow[0] = currentRow[0] + stepDeleteCost
            val childAlphaIdx = childChar.code - 'a'.code
            val isAsciiLower = childAlphaIdx in 0..25

            for (i in 1..targetLen) {
                val typed = targetWord[i - 1]
                val insertCost = nextRow[i - 1] + COST_GAP
                val deleteCost = currentRow[i] + stepDeleteCost
                val substitution =
                    when {
                        typed == childChar -> 0
                        touchCosts != null && touchCosts[i - 1] != null -> {
                            if (isAsciiLower) {
                                touchCosts[i - 1]!![childAlphaIdx]
                            } else {
                                SmartKeyHitbox.substitutionCost(
                                    touches!![i - 1],
                                    childChar,
                                    COST_ADJACENT_SUBSTITUTE,
                                    COST_SUBSTITUTE,
                                )
                            }
                        }
                        KeyProximity.areAdjacent(typed, childChar) -> COST_ADJACENT_SUBSTITUTE
                        else -> COST_SUBSTITUTE
                    }
                var cost = minOf(insertCost, deleteCost, currentRow[i - 1] + substitution)

                // Adjacent transposition
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
            wordBuf[depth] = childChar
            dfsEditDistance(
                buf = buf,
                offset = childOffset,
                depth = depth + 1,
                wordBuf = wordBuf,
                targetWord = targetWord,
                rowPool = rowPool,
                prevChar = childChar,
                maxErrors = maxErrors,
                results = results,
                touches = touches,
                touchCosts = touchCosts,
            )
        }
    }

    /** Frequency of an exact dictionary word across all active lexicons, or null when not found. */
    internal fun frequencyOf(word: String): Int? {
        val normalized = word.lowercase()
        var maxFreq: Int? = null
        for (dictProvider in dictionaries) {
            val buf = dictProvider() ?: continue
            val f = trieFrequencyOf(buf, normalized)
            if (f != null) {
                if (maxFreq == null || f > maxFreq) {
                    maxFreq = f
                }
            }
        }
        return maxFreq
    }

    companion object {
        internal const val SINGLE_EDIT_SPLIT_PRECEDENCE_COST = COST_TRANSPOSE
    }
}

/**
 * The weighted edit cost from [typed] to [candidate], on the same scale and with the same
 * transposition rule the trie walk uses.
 */
internal fun weightedEditCost(
    typed: String,
    candidate: String,
    touches: List<TouchPoint>? = null,
): Int {
    val rows = Array(candidate.length + 1) { IntArray(typed.length + 1) }
    for (i in 0..typed.length) rows[0][i] = i * COST_GAP
    for (j in 1..candidate.length) {
        val c = candidate[j - 1]
        val stepDeleteCost =
            if (j >= 2 && c == candidate[j - 2]) COST_OMISSION_SAME_CHAR else COST_GAP
        rows[j][0] = rows[j - 1][0] + stepDeleteCost
        for (i in 1..typed.length) {
            val t = typed[i - 1]
            val substitution =
                when {
                    t == c -> 0
                    touches != null &&
                        i - 1 < touches.size &&
                        touches[i - 1].char.lowercaseChar() == t ->
                        SmartKeyHitbox.substitutionCost(
                            touches[i - 1],
                            c,
                            COST_ADJACENT_SUBSTITUTE,
                            COST_SUBSTITUTE,
                        )
                    KeyProximity.areAdjacent(t, c) -> COST_ADJACENT_SUBSTITUTE
                    else -> COST_SUBSTITUTE
                }
            var cost =
                minOf(
                    rows[j][i - 1] + COST_GAP,
                    rows[j - 1][i] + stepDeleteCost,
                    rows[j - 1][i - 1] + substitution,
                )
            if (i >= 2 && j >= 2 && typed[i - 1] == candidate[j - 2] && typed[i - 2] == c) {
                cost = minOf(cost, rows[j - 2][i - 2] + COST_TRANSPOSE)
            }
            rows[j][i] = cost
        }
    }
    return rows[candidate.length][typed.length]
}
