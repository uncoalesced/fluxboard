// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BloomTriggerPredictorTest {
    @Test
    fun `exact match is not autocorrected`() {
        val top =
            ScoredCandidate(
                word = "hello",
                cost = 0,
                normalizedScore = 0.95f,
                spatialScore = 0.95f,
                bigramBoost = 0f,
                finalScore = 0.95f,
            )
        val decision = BloomTriggerPredictor.evaluate(typedWord = "hello", topCandidate = top)
        assertFalse(decision.shouldAutoCommit)
        assertEquals("Candidate matches verbatim typed word", decision.reason)
    }

    @Test
    fun `revert history suppresses auto-commit`() {
        val revertHistory = BloomRevertHistory(maxEntries = 5)
        revertHistory.recordRevert("world")

        val top =
            ScoredCandidate(
                word = "world",
                cost = 2,
                normalizedScore = 0.80f,
                spatialScore = 0.80f,
                bigramBoost = 0f,
                finalScore = 0.80f,
            )
        val decision =
            BloomTriggerPredictor.evaluate(
                typedWord = "workd",
                topCandidate = top,
                revertHistory = revertHistory,
            )
        assertFalse(decision.shouldAutoCommit)
        assertEquals("Suppressed by BloomRevertHistory", decision.reason)
    }

    @Test
    fun `contraction restoration receives high confidence auto-commit`() {
        val top =
            ScoredCandidate(
                word = "don't",
                cost = 1,
                normalizedScore = 0.75f,
                spatialScore = 0.75f,
                bigramBoost = 0f,
                finalScore = 0.75f,
            )
        val decision = BloomTriggerPredictor.evaluate(typedWord = "dont", topCandidate = top)
        assertTrue(decision.shouldAutoCommit)
        assertEquals("High confidence contraction restoration", decision.reason)
    }

    @Test
    fun `rapid burst typing lowers effective threshold`() {
        // 4 touches 80ms apart = average interval 80ms (< 160ms burst threshold)
        val touches =
            listOf(
                TouchPoint('w', 0f, 0f, 1000L),
                TouchPoint('o', 0f, 0f, 1080L),
                TouchPoint('r', 0f, 0f, 1160L),
                TouchPoint('k', 0f, 0f, 1240L),
            )
        val top =
            ScoredCandidate(
                word = "word",
                cost = 2,
                // Below base 0.185, but above burst threshold
                // (0.185 - 0.028 = 0.157)
                normalizedScore = 0.165f,
                spatialScore = 0.165f,
                bigramBoost = 0f,
                finalScore = 0.165f,
            )
        val decision =
            BloomTriggerPredictor.evaluate(
                typedWord = "work",
                topCandidate = top,
                touches = touches,
            )
        assertTrue(
            "Burst typing should lower effective threshold and allow commit",
            decision.shouldAutoCommit,
        )
        assertTrue(decision.effectiveThreshold < BloomTriggerPredictor.BASE_MODEST_THRESHOLD)
    }

    @Test
    fun `slow deliberate typing raises effective threshold`() {
        // 4 touches 450ms apart = average interval 450ms (> 350ms deliberate threshold)
        val touches =
            listOf(
                TouchPoint('w', 0f, 0f, 1000L),
                TouchPoint('o', 0f, 0f, 1450L),
                TouchPoint('r', 0f, 0f, 1900L),
                TouchPoint('k', 0f, 0f, 2350L),
            )
        val top =
            ScoredCandidate(
                word = "word",
                cost = 2,
                // Above base 0.185, but below deliberate threshold
                // (0.185 + 0.016 = 0.201)
                normalizedScore = 0.190f,
                spatialScore = 0.190f,
                bigramBoost = 0f,
                finalScore = 0.190f,
            )
        val decision =
            BloomTriggerPredictor.evaluate(
                typedWord = "work",
                topCandidate = top,
                touches = touches,
            )
        assertFalse(
            "Deliberate slow typing should raise threshold and suppress aggressive auto-commit",
            decision.shouldAutoCommit,
        )
        assertTrue(decision.effectiveThreshold > BloomTriggerPredictor.BASE_MODEST_THRESHOLD)
    }

    @Test
    fun `strong bigram boost lowers effective threshold`() {
        val top =
            ScoredCandidate(
                word = "the",
                cost = 2,
                normalizedScore = 0.140f, // Below base 0.185
                spatialScore = 0.140f,
                bigramBoost = 192f, // Max boost
                finalScore = 0.140f + (192f / 255f),
            )
        val decision =
            BloomTriggerPredictor.evaluate(
                typedWord = "thw",
                topCandidate = top,
                previousWord = "saw",
            )
        assertTrue(
            "Strong bigram boost should lower threshold",
            decision.effectiveThreshold < BloomTriggerPredictor.BASE_MODEST_THRESHOLD,
        )
        assertTrue(decision.shouldAutoCommit)
    }

    @Test
    fun `ambiguous runner-up with equal cost suppresses auto-commit`() {
        // Two candidates with same cost (2) and nearly identical score
        val top =
            ScoredCandidate(
                word = "then",
                cost = 2,
                normalizedScore = 0.503f,
                spatialScore = 0.503f,
                bigramBoost = 0f,
                finalScore = 0.503f,
            )
        val runnerUp =
            ScoredCandidate(
                word = "turn",
                cost = 2,
                normalizedScore = 0.501f, // Ratio 1.004 (< 1.03), delta 0.002 (< 0.005)
                spatialScore = 0.501f,
                bigramBoost = 0f,
                finalScore = 0.501f,
            )
        val decision =
            BloomTriggerPredictor.evaluate(
                typedWord = "tuen",
                topCandidate = top,
                runnerUpCandidate = runnerUp,
            )
        assertFalse("Ambiguous tie should withhold auto-commit", decision.shouldAutoCommit)
        assertTrue(decision.reason.startsWith("Ambiguous runner-up"))
    }

    @Test
    fun `runner-up with higher cost does not suppress auto-commit`() {
        // Top has cost 2 (close neighbor), runner-up has cost 5 (distant replacement)
        val top =
            ScoredCandidate(
                word = "call",
                cost = 2,
                normalizedScore = 0.505f,
                spatialScore = 0.505f,
                bigramBoost = 0f,
                finalScore = 0.505f,
            )
        val runnerUp =
            ScoredCandidate(
                word = "ball",
                cost = 5,
                normalizedScore = 0.500f,
                spatialScore = 0.500f,
                bigramBoost = 0f,
                finalScore = 0.500f,
            )
        val decision =
            BloomTriggerPredictor.evaluate(
                typedWord = "vall",
                topCandidate = top,
                runnerUpCandidate = runnerUp,
            )
        assertTrue(
            "Runner up with higher edit cost should not block top candidate",
            decision.shouldAutoCommit,
        )
    }

    @Test
    fun `length expansion penalty suppresses multi-letter insertions without overwhelming score`() {
        // Typed 3 letters, candidate is 6 letters (length delta = 3)
        val top =
            ScoredCandidate(
                word = "action",
                cost = 6,
                normalizedScore = 0.200f, // Above base 0.185, but below (0.185 + 0.035 = 0.220)
                spatialScore = 0.200f,
                bigramBoost = 0f,
                finalScore = 0.200f,
            )
        val decision =
            BloomTriggerPredictor.evaluate(
                typedWord = "act",
                topCandidate = top,
            )
        assertFalse(
            "Large length expansion requires higher confidence",
            decision.shouldAutoCommit,
        )
        assertTrue(decision.effectiveThreshold > BloomTriggerPredictor.BASE_MODEST_THRESHOLD)
    }
}
