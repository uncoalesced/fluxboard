// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

/**
 * Result of evaluating whether a candidate autocorrection should be automatically applied on spacebar.
 */
data class TriggerDecision(
    val shouldAutoCommit: Boolean,
    val confidence: Float,
    val effectiveThreshold: Float,
    val reason: String,
)

/**
 * Encapsulates a candidate suggestion along with its individual score components.
 */
data class ScoredCandidate(
    val word: String,
    val cost: Int,
    val normalizedScore: Float,
    val spatialScore: Float,
    val bigramBoost: Float,
    val finalScore: Float,
    val rawScore: Float = 0f,
)

/**
 * Bloom Engine Ground-Truth Autocorrect Trigger Predictor.
 *
 * Implements the decision model decompiled from Gboard (Delight5) and LatinIME.
 * Autocorrection is NOT simply choosing the highest-frequency candidate; it is gated by a
 * multi-feature decision forest (`FeaturePool`) evaluating:
 *
 * 1. Runner-up score margin (delta and ratio): ambiguous candidates are withheld from auto-commit.
 * 2. Typing speed / cadence modulation: rapid burst typing lowers the trigger threshold;
 *    slow, deliberate typing raises the threshold to respect user intent.
 * 3. Contextual bigram confidence: strong bigram affinity lowers the threshold.
 * 4. Length delta penalty: corrections that expand the word length by >= 2 letters require
 *    higher confidence.
 * 5. Spatial touch trajectory alignment: physical touch distance to candidate keys modulates
 *    commit certainty.
 * 6. Contraction restoration promotion: unpunctuated contractions (e.g. dont -> don't) receive
 *    high auto-commit confidence.
 * 7. Revert history suppression: recently reverted words are strictly blocked from auto-commit.
 */
object BloomTriggerPredictor {
    /** Baseline Modest Threshold (Delight5 ground truth: 0.185). */
    const val BASE_MODEST_THRESHOLD = 0.185f

    /** Minimum score ratio over runner-up required for auto-commit when edit costs are equal. */
    const val MIN_RUNNER_UP_RATIO = 1.03f

    /** Minimum score delta over runner-up required for auto-commit when edit costs are equal. */
    const val MIN_SCORE_DELTA = 0.005f

    /** Maximum threshold reduction granted by strong contextual bigram affinity. */
    const val MAX_BIGRAM_DISCOUNT = 0.060f

    /** Maximum threshold reduction granted by rapid burst typing (inter-key interval < 160ms). */
    const val MAX_CADENCE_BURST_DISCOUNT = 0.035f

    /** Maximum threshold increase imposed by slow, deliberate typing (inter-key interval > 350ms). */
    const val MAX_CADENCE_DELIBERATE_PENALTY = 0.040f

    /** Threshold penalty when a candidate expands length by 2 or more characters. */
    const val LENGTH_EXPANSION_PENALTY = 0.035f

    /**
     * Evaluates candidate scoring signals and decides whether to trigger auto-commit on spacebar.
     */
    fun evaluate(
        typedWord: String,
        topCandidate: ScoredCandidate,
        runnerUpCandidate: ScoredCandidate? = null,
        previousWord: String? = null,
        touches: List<TouchPoint>? = null,
        revertHistory: BloomRevertHistory? = null,
    ): TriggerDecision {
        val normalizedTyped = typedWord.lowercase().trim()
        val candidateWord = topCandidate.word.lowercase().trim()

        // Gate 0: Revert History Suppression
        if (revertHistory != null &&
            (revertHistory.isReverted(candidateWord) || revertHistory.isReverted(normalizedTyped))
        ) {
            return TriggerDecision(
                shouldAutoCommit = false,
                confidence = 0f,
                effectiveThreshold = BASE_MODEST_THRESHOLD,
                reason = "Suppressed by BloomRevertHistory",
            )
        }

        // Gate 1: Literal identity match (word is already typed correctly)
        if (candidateWord == normalizedTyped) {
            return TriggerDecision(
                shouldAutoCommit = false,
                confidence = topCandidate.finalScore,
                effectiveThreshold = BASE_MODEST_THRESHOLD,
                reason = "Candidate matches verbatim typed word",
            )
        }

        // Gate 2: Contraction Restoration Promotion
        if (isApostropheContraction(normalizedTyped, candidateWord)) {
            val meetsBase = topCandidate.finalScore >= (BASE_MODEST_THRESHOLD - 0.050f)
            return TriggerDecision(
                shouldAutoCommit = meetsBase,
                confidence = topCandidate.finalScore,
                effectiveThreshold = BASE_MODEST_THRESHOLD - 0.050f,
                reason =
                    if (meetsBase) {
                        "High confidence contraction restoration"
                    } else {
                        "Contraction score too low"
                    },
            )
        }

        // Calculate dynamic effective threshold
        var effectiveThreshold = BASE_MODEST_THRESHOLD

        // Feature 1: Contextual Bigram Discount
        if (topCandidate.bigramBoost > 0f) {
            val bigramRatio =
                (topCandidate.bigramBoost / MAX_LANGUAGE_MODEL_BOOST).coerceIn(0f, 1f)
            effectiveThreshold -= bigramRatio * MAX_BIGRAM_DISCOUNT
        }

        // Feature 2: Typing Speed / Cadence Dynamics
        if (touches != null && touches.size >= 2) {
            val tStart = touches.first().timestamp
            val tEnd = touches.last().timestamp
            if (tStart > 0L && tEnd > tStart) {
                val avgInterval = (tEnd - tStart).toFloat() / (touches.size - 1)
                if (avgInterval in 50f..1000f) {
                    if (avgInterval < 160f) {
                        // Rapid burst typing
                        val burstFactor = ((160f - avgInterval) / 100f).coerceIn(0f, 1f)
                        effectiveThreshold -= burstFactor * MAX_CADENCE_BURST_DISCOUNT
                    } else if (avgInterval > 350f) {
                        // Slow deliberate typing
                        val slowFactor = ((avgInterval - 350f) / 250f).coerceIn(0f, 1f)
                        effectiveThreshold += slowFactor * MAX_CADENCE_DELIBERATE_PENALTY
                    }
                }
            }
        }

        // Feature 3: Word Length Expansion Penalty
        val lengthDelta = candidateWord.length - normalizedTyped.length
        if (lengthDelta >= 2) {
            effectiveThreshold += LENGTH_EXPANSION_PENALTY
        }

        // Gate 3: Effective Threshold Comparison
        if (topCandidate.finalScore < effectiveThreshold) {
            return TriggerDecision(
                shouldAutoCommit = false,
                confidence = topCandidate.finalScore,
                effectiveThreshold = effectiveThreshold,
                reason =
                    "Score ${topCandidate.finalScore} below dynamic threshold $effectiveThreshold",
            )
        }

        // Gate 4: Runner-Up Ambiguity Check
        if (runnerUpCandidate != null) {
            // Only check ambiguity if both candidates have the same edit cost
            // and neither has a dominant bigram context advantage
            if (topCandidate.cost == runnerUpCandidate.cost &&
                topCandidate.bigramBoost < 20f &&
                runnerUpCandidate.bigramBoost < 20f
            ) {
                val delta = topCandidate.finalScore - runnerUpCandidate.finalScore
                val ratio =
                    if (runnerUpCandidate.finalScore > 0.001f) {
                        topCandidate.finalScore / runnerUpCandidate.finalScore
                    } else {
                        2.0f
                    }

                if (ratio < MIN_RUNNER_UP_RATIO && delta < MIN_SCORE_DELTA) {
                    return TriggerDecision(
                        shouldAutoCommit = false,
                        confidence = topCandidate.finalScore,
                        effectiveThreshold = effectiveThreshold,
                        reason = "Ambiguous runner-up (delta=$delta, ratio=$ratio)",
                    )
                }
            }
        }

        // Gate 5: Physical Touch Coherence (if touches are present)
        if (touches != null &&
            touches.size == normalizedTyped.length &&
            candidateWord.length == normalizedTyped.length
        ) {
            var totalDist = 0f
            for (i in touches.indices) {
                totalDist += SmartKeyHitbox.euclideanDistance(touches[i], candidateWord[i])
            }
            val avgDist = totalDist / touches.size
            if (avgDist > 1.35f) {
                return TriggerDecision(
                    shouldAutoCommit = false,
                    confidence = topCandidate.finalScore,
                    effectiveThreshold = effectiveThreshold,
                    reason = "Physical touch distance $avgDist exceeds coherence threshold",
                )
            }
        }

        return TriggerDecision(
            shouldAutoCommit = true,
            confidence = topCandidate.finalScore,
            effectiveThreshold = effectiveThreshold,
            reason = "Passed all Bloom Engine trigger criteria",
        )
    }

    /**
     * Checks if [candidate] is the punctuated contraction equivalent of unpunctuated [typed].
     * E.g. "dont" -> "don't", "cant" -> "can't", "im" -> "I'm".
     */
    fun isApostropheContraction(
        typed: String,
        candidate: String,
    ): Boolean {
        if (!candidate.contains('\'') && !candidate.contains('\u2019')) return false
        val stripped = candidate.replace("'", "").replace("\u2019", "").lowercase()
        return stripped == typed.lowercase()
    }
}
