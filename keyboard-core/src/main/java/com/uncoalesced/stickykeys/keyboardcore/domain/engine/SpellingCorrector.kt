// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import java.nio.ByteBuffer

/**
 * FluxBloom Engine Ground-Truth Edit Costs.
 *
 * Calibrated on the 5.48 : 1 Proximity-to-Substitution ratio from the reverse-engineering
 * analysis of modern on-device autocorrect engines.
 *
 * Ordered by natural mistake likelihood:
 * - Adjacent fat-finger slip: COST_ADJACENT_SUBSTITUTE = 2
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
private const val COST_GAP = 3
private const val COST_TRANSPOSE = 3
private const val COST_SUBSTITUTE = 5

/** Max edit budget for branch pruning during trie traversal. */
private const val MAX_EDIT_COST = COST_SUBSTITUTE * 3

/** Size of the FLCT magic header in bytes. */
private const val TRIE_HEADER_SIZE = 4

/**
 * Master Autocorrection Modest Threshold (Bloom Engine ground truth: 0.185).
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
) {
    constructor(
        dictionary: () -> ByteBuffer?,
        languageModel: LanguageModel,
    ) : this(listOf(dictionary), languageModel)

    /**
     * Finds the optimal single-word autocorrection using the FluxBloom Master Normalized Score formula.
     * Returns null when the typed word is already valid, nothing beats the threshold, or the winner
     * is not trustworthy enough to apply.
     */
    internal fun nearestWord(
        normalized: String,
        previousWord: String? = null,
        personalWords: List<Pair<String, Float>> = emptyList(),
        touches: List<TouchPoint>? = null,
        revertHistory: BloomRevertHistory? = null,
    ): String? {
        val inputLen = normalized.length
        if (inputLen == 0) return null

        // Inviolable Lexicon Veto: Never autocorrect a word that already exists in the dictionary
        // or user personal lexicon.
        if (frequencyOf(normalized) != null) return null
        if (personalWords.any { it.first == normalized }) return null

        // "alot" is "a lot" with the space missed, and also one key from "slot". Two
        // readings that far apart in meaning are not a typo to fix silently.
        if (isArticleRunOn(normalized)) return null

        // Retrieve candidates within MAX_EDIT_COST
        val base = candidatesFor(normalized, touches) ?: return null
        val eligible = withPersonalWords(normalized, base, personalWords, touches)
        if (eligible.isEmpty()) return null

        // FluxBloom Invariants Filter
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
                // For words <= 4 letters, only tolerate single localized mistakes (adjacent slip or transposition).
                if (inputLen <= SHORT_WORD_MAX_LENGTH && cost > COST_TRANSPOSE) return@filter false

                // Bloom Invariant 4: Modest Threshold Gating
                val score = calcNormalizedScore(suggestion.score, cost, candidateLen)
                score >= MODEST_THRESHOLD
            }

        if (valid.isEmpty()) return null

        // Spatial alignment boost for candidates matching physical touch points (Gboard SpatialDistanceFeature)
        fun scoreCandidate(
            suggestion: Suggestion,
            cost: Int,
        ): Float {
            var baseScore = calcNormalizedScore(suggestion.score, cost, suggestion.word.length)
            if (touches != null && touches.size == inputLen && suggestion.word.length == inputLen) {
                var totalDist = 0f
                for (idx in 0 until inputLen) {
                    totalDist +=
                        SmartKeyHitbox.euclideanDistance(touches[idx], suggestion.word[idx])
                }
                val avgDist = totalDist / inputLen
                if (avgDist < 0.60f) {
                    baseScore *= (1.0f + (0.60f - avgDist) * 0.25f)
                }
            }
            return baseScore
        }

        // Plain best candidate without contextual boost to establish minimum edit cost
        val plainBest =
            valid.maxByOrNull { (suggestion, cost) ->
                scoreCandidate(suggestion, cost)
            } ?: return null

        // The typed word won: leave it alone
        if (plainBest.first.word == normalized) return null

        // Ambiguity check: dropped repeated letter
        if (hasCompetingDoubledReading(normalized, plainBest)) return null

        // Sentence context re-ranking: only candidates as close as plainBest can compete
        val eligibleForContext =
            valid
                .filter { it.second <= plainBest.second }
                .sortedByDescending { (suggestion, cost) ->
                    val baseScore = scoreCandidate(suggestion, cost)
                    val boost = languageModel.boostFor(previousWord, suggestion.word)
                    baseScore + (boost / MAX_FREQ_SCALE)
                }

        val top = eligibleForContext.firstOrNull() ?: return null
        if (top.first.word == normalized) return null

        val runnerUp = eligibleForContext.getOrNull(1)

        val topScored =
            ScoredCandidate(
                word = top.first.word,
                cost = top.second,
                rawScore = top.first.score,
                normalizedScore =
                    calcNormalizedScore(top.first.score, top.second, top.first.word.length),
                spatialScore = scoreCandidate(top.first, top.second),
                bigramBoost = languageModel.boostFor(previousWord, top.first.word),
                finalScore =
                    scoreCandidate(top.first, top.second) +
                        (languageModel.boostFor(previousWord, top.first.word) / MAX_FREQ_SCALE),
            )

        val runnerUpScored =
            runnerUp?.let {
                ScoredCandidate(
                    word = it.first.word,
                    cost = it.second,
                    rawScore = it.first.score,
                    normalizedScore =
                        calcNormalizedScore(it.first.score, it.second, it.first.word.length),
                    spatialScore = scoreCandidate(it.first, it.second),
                    bigramBoost = languageModel.boostFor(previousWord, it.first.word),
                    finalScore =
                        scoreCandidate(it.first, it.second) +
                            (languageModel.boostFor(previousWord, it.first.word) / MAX_FREQ_SCALE),
                )
            }

        // Bloom Engine Decision Model Gating (FeaturePool trigger evaluation)
        val decision =
            BloomTriggerPredictor.evaluate(
                typedWord = normalized,
                topCandidate = topScored,
                runnerUpCandidate = runnerUpScored,
                previousWord = previousWord,
                touches = touches,
                revertHistory = revertHistory,
            )

        if (!decision.shouldAutoCommit) return null

        return top.first.word
    }

    /**
     * What to offer in the strip for a word autocorrect left alone, best first.
     */
    internal fun offersFor(
        normalized: String,
        personalWords: List<Pair<String, Float>> = emptyList(),
        limit: Int = 3,
    ): List<String> {
        if (frequencyOf(normalized) != null) return emptyList()
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
            candidatesFor(normalized) ?: return (split + doubledOffers).distinct().take(limit)
        val near =
            withPersonalWords(normalized, base, personalWords)
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
     * Every dictionary word within [MAX_EDIT_COST] of [normalized] that is common enough to
     * be worth correcting to, paired with its weighted edit cost across all active lexicons.
     * The typed word itself is among them at cost 0 when it is a dictionary word.
     * Null when no dictionary can be read.
     */
    internal fun candidatesFor(
        normalized: String,
        touches: List<TouchPoint>? = null,
    ): List<Pair<Suggestion, Int>>? {
        val candidates = mutableListOf<Pair<Suggestion, Int>>()
        val initialRow = IntArray(normalized.length + 1) { it * COST_GAP }
        var readAny = false

        for (dictProvider in dictionaries) {
            val buf = dictProvider() ?: continue
            readAny = true
            try {
                dfsEditDistance(
                    buf,
                    TRIE_HEADER_SIZE,
                    "",
                    normalized,
                    initialRow,
                    null,
                    null,
                    MAX_EDIT_COST,
                    candidates,
                    touches,
                )
            } catch (e: Exception) {
                // Safeguard against malformed offsets
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
            val freq = frequencyOf(doubled) ?: continue
            val doubledScore = calcNormalizedScore(freq.toFloat(), COST_GAP, doubled.length)
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

    /**
     * Walks the trie carrying one weighted-edit row per node, with the
     * Damerau/optimal-string-alignment extension for adjacent transpositions.
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
        touches: List<TouchPoint>? = null,
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
                        touches != null &&
                            i - 1 < touches.size &&
                            touches[i - 1].char.lowercaseChar() == typed ->
                            SmartKeyHitbox.substitutionCost(
                                touches[i - 1],
                                childChar,
                                COST_ADJACENT_SUBSTITUTE,
                                COST_SUBSTITUTE,
                            )
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
                touches,
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
        rows[j][0] = j * COST_GAP
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
                    rows[j - 1][i] + COST_GAP,
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
