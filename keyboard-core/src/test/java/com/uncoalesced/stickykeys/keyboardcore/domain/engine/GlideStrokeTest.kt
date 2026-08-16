// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The glide decoder, tested where it can actually be wrong.
 *
 * A glide is not a spelling. Every key between the letters the user meant is crossed too, so
 * the path for "hello" also contains "hell", "ho", "hey" and a dozen other real words as
 * subsequences. That is the whole difficulty of the feature: the question is never "is this
 * word spelled like the path" but "of the many words the path could be, which one was drawn".
 *
 * So these tests are mostly about what must be *rejected* and how ties must be *ordered*,
 * because a decoder that only accepts is one that returns the commonest short word every time.
 */
class GlideStrokeTest {
    /**
     * Keys a finger crosses gliding h -> e -> l -> o on QWERTY, with corners at those letters.
     *
     * Written out rather than simulated because the sequence is the point: leftwards along the
     * home row to 'e', back across the top row and down to 'l', then up to 'o'. Everything
     * between the corners is crossed incidentally, which is exactly what the decoder has to
     * see through.
     */
    private fun helloStroke() =
        GlideStroke(
            keys = "hgfdertyuiklo".toList(),
            pivots = setOf(0, 4, 11, 12),
        )

    private fun stroke(
        keys: String,
        vararg pivots: Int,
    ) = GlideStroke(keys.toList(), pivots.toSet())

    // --- what must be rejected ---------------------------------------------------------

    @Test
    fun `a word not anchored at the first key is not a reading of this path`() {
        // The start point is where the user put their finger down. A word starting elsewhere
        // is a different word that happens to share the middle of the path.
        assertNull(scoreGlideCandidate("ello", helloStroke()))
    }

    @Test
    fun `a word ending far from the lift-off point is rejected`() {
        // 'a' is nowhere near 'o', so no tolerance should admit it as the final key.
        assertNull(scoreGlideCandidate("hella", helloStroke()))
    }

    @Test
    fun `a prefix that stops short is outranked rather than rejected`() {
        // "hell" is a genuine subsequence of the hello path, and its last letter sits next to
        // the lift-off key, so it is admissible -- lifting off near 'l' really could mean
        // "hell". It has to *lose* rather than be excluded, because the thing that separates
        // them is the corner at 'o' that "hell" leaves unexplained. Rejecting it outright
        // would be the wrong mechanism: the same reasoning would throw away real words
        // whenever a glide overshoots slightly.
        val s = helloStroke()
        val hello = scoreGlideCandidate("hello", s)
        val hell = scoreGlideCandidate("hell", s)
        assertNotNull(hello)
        assertNotNull(hell)
        assertTrue("hello ($hello) must outrank hell ($hell)", hello!! < hell!!)
    }

    @Test
    fun `letters out of order are rejected`() {
        // "hole" uses the same keys as "hello" but the path visits 'l' after 'o' would need to
        // come first. Order is the one thing a path is unambiguous about.
        assertNull(scoreGlideCandidate("hoel", stroke("hgfdertyuiol", 0, 4, 10)))
    }

    @Test
    fun `a letter the path never approached is rejected`() {
        // 'z' is nowhere near this path, so no amount of tolerance should admit it.
        assertNull(scoreGlideCandidate("hzo", helloStroke()))
    }

    @Test
    fun `a single-character word is never a glide`() {
        assertNull(scoreGlideCandidate("a", helloStroke()))
    }

    // --- doubled letters, which the path physically cannot show -------------------------

    @Test
    fun `a doubled letter matches without the path visiting the key twice`() {
        // The case that decides whether this feature works at all. A finger cannot cross 'l'
        // twice in succession, so if a doubled letter had to consume two path positions then
        // "hello", "all", "coffee", "letter" and most of English would be unglideable -- which
        // reads as the feature being broken rather than imperfect.
        assertNotNull(scoreGlideCandidate("hello", helloStroke()))
    }

    @Test
    fun `the doubled letter still has to be on the path`() {
        // Tolerating doubles must not become tolerating anything. "hetto" doubles a 't' that
        // the path does cross, but 'tt' cannot rescue a word whose other letters do not fit.
        assertNull(scoreGlideCandidate("hettz", helloStroke()))
    }

    // --- ranking, which is where a decoder is actually judged ---------------------------

    @Test
    fun `the word explaining every corner beats one that ignores a corner`() {
        // Both are valid subsequences of the same path. "hello" turns at 'e' and ends at 'o';
        // "ho" explains the endpoints and leaves the corner at 'e' unaccounted for.
        val s = helloStroke()
        val hello = scoreGlideCandidate("hello", s)
        val ho = scoreGlideCandidate("ho", s)
        assertNotNull(hello)
        assertNotNull(ho)
        assertTrue(
            "hello ($hello) should score better than ho ($ho)",
            hello!! < ho!!,
        )
    }

    @Test
    fun `an exact key beats a neighbour even when the neighbour appears first`() {
        // Scanning forward, 'r' is adjacent to 'e' and comes earlier than the real 'e'. Taking
        // the near miss because it was seen first would quietly degrade every glide that
        // passes near its own targets, which is all of them.
        val exact = scoreGlideCandidate("hero", stroke("hgfdertyuio", 0, 4, 10))
        val viaNeighbour = scoreGlideCandidate("hdro", stroke("hgfdertyuio", 0, 4, 10))
        assertNotNull(exact)
        assertNotNull(viaNeighbour)
        assertTrue(
            "exact ($exact) should beat neighbour ($viaNeighbour)",
            exact!! <= viaNeighbour!!,
        )
    }

    @Test
    fun `a clipped corner is tolerated at a cost`() {
        // At speed the finger rounds the turn and lands next door. Accepting this is what
        // stops fast glides failing; charging for it is what stops it outranking a clean read.
        val clipped = scoreGlideCandidate("hwllo", helloStroke())
        assertNotNull("a clipped corner should still decode", clipped)
        assertTrue(clipped!! > scoreGlideCandidate("hello", helloStroke())!!)
    }

    // --- reducing a raw path -------------------------------------------------------------

    @Test
    fun `dwelling on a key does not make it look more intended`() {
        // Thirty samples on one key and two on another must produce two keys, not a weighting.
        val points =
            List(30) { GlidePoint(0f, 0f, 'h') } +
                List(2) { GlidePoint(1f, 0f, 'j') }
        val s = buildGlideStroke(points)
        assertEquals(listOf('h', 'j'), s.keys)
    }

    @Test
    fun `points off any key are ignored rather than breaking the sequence`() {
        val points =
            listOf(
                GlidePoint(0f, 0f, 'h'),
                GlidePoint(0.5f, 0.5f, null),
                GlidePoint(1f, 0f, 'j'),
            )
        assertEquals(listOf('h', 'j'), buildGlideStroke(points).keys)
    }

    @Test
    fun `both endpoints are always pivots`() {
        val points = (0..10).map { GlidePoint(it.toFloat(), 0f, ('a' + it)) }
        val s = buildGlideStroke(points)
        assertTrue(s.pivots.contains(0))
        assertTrue(s.pivots.contains(s.keys.lastIndex))
    }

    @Test
    fun `a straight drag has no interior pivots`() {
        // A straight line across the board turns nowhere, so nothing between the ends was
        // aimed at. Inventing pivots here would promote whichever word sits under the wobble.
        val points = (0..10).map { GlidePoint(it.toFloat(), 0f, ('a' + it)) }
        val s = buildGlideStroke(points)
        assertEquals(setOf(0, s.keys.lastIndex), s.pivots)
    }

    @Test
    fun `a sharp reversal registers as a pivot`() {
        // Out along x, then straight back: the turn is the strongest corner there is.
        val out = (0..5).map { GlidePoint(it.toFloat(), 0f, ('a' + it)) }
        val back = (1..5).map { GlidePoint(5f - it, 1f, ('a' + (5 + it))) }
        val s = buildGlideStroke(out + back)
        assertTrue("the reversal should be a pivot", s.pivots.size > 2)
    }

    // --- corners are graded, not counted --------------------------------------------------

    @Test
    fun `a marginal corner costs less to ignore than a certain one`() {
        // The old scoring had one price for every corner, so a 58-degree turn either cost a
        // candidate nothing at all or cost it the same as a hard reversal, depending on which
        // side of a threshold the finger happened to land. Both answers are wrong about the
        // same gesture. Strength is what makes the middle representable.
        val keys = "hgfdertyuiklo".toList()
        val certain =
            GlideStroke(keys, setOf(0, 4, 12), pivotStrength = mapOf(0 to 1f, 4 to 1f, 12 to 1f))
        val marginal =
            GlideStroke(keys, setOf(0, 4, 12), pivotStrength = mapOf(0 to 1f, 4 to 0.55f, 12 to 1f))
        val noCorner = GlideStroke(keys, setOf(0, 12), pivotStrength = mapOf(0 to 1f, 12 to 1f))

        // "ho" explains both endpoints and leaves the turn at 'e' (index 4) unaccounted for.
        val ignoringCertain = scoreGlideCandidate("ho", certain)!!
        val ignoringMarginal = scoreGlideCandidate("ho", marginal)!!
        val nothingToIgnore = scoreGlideCandidate("ho", noCorner)!!

        assertTrue(
            "a marginal corner ($ignoringMarginal) must cost more to miss than no corner " +
                "($nothingToIgnore)",
            nothingToIgnore < ignoringMarginal,
        )
        assertTrue(
            "a marginal corner ($ignoringMarginal) must cost less to miss than a certain one " +
                "($ignoringCertain)",
            ignoringMarginal < ignoringCertain,
        )
    }

    @Test
    fun `a stroke with no strengths scores exactly as it always did`() {
        // The compatibility guarantee the rest of this file depends on: absent strengths read
        // as full, which reproduces the old flat per-corner penalty. If this drifts, every
        // hand-built stroke above is silently testing different arithmetic than it was written
        // against.
        val keys = "hgfdertyuiklo".toList()
        val bare = GlideStroke(keys, setOf(0, 4, 12))
        val explicitlyFull =
            GlideStroke(keys, setOf(0, 4, 12), pivotStrength = mapOf(0 to 1f, 4 to 1f, 12 to 1f))
        assertEquals(scoreGlideCandidate("ho", explicitlyFull), scoreGlideCandidate("ho", bare))
    }

    // --- dwell, which is the signal a fixed angle cutoff cannot see -----------------------

    /**
     * A path that runs east, turns by [MODERATE_TURN] radians at index 3, and runs on.
     *
     * [samplesAtCorner] repeats the corner sample without moving it, so the geometry -- and
     * therefore the measured angle -- is identical between calls and dwell is the only
     * difference. That is what makes the pair below a controlled comparison rather than two
     * different gestures.
     */
    private fun turningPath(samplesAtCorner: Int): GlideStroke {
        val letters = "asdfghj"
        val points = mutableListOf<GlidePoint>()
        for (i in 0..2) points.add(GlidePoint(i.toFloat(), 0f, letters[i]))
        repeat(samplesAtCorner) { points.add(GlidePoint(3f, 0f, letters[3])) }
        val dx = kotlin.math.cos(MODERATE_TURN)
        val dy = kotlin.math.sin(MODERATE_TURN)
        for (i in 1..3) {
            points.add(GlidePoint(3f + i * dx, i * dy, letters[3 + i]))
        }
        return buildGlideStroke(points)
    }

    @Test
    fun `lingering on a corner makes it a stronger pivot than rushing through it`() {
        val rushed = turningPath(samplesAtCorner = 1)
        val lingered = turningPath(samplesAtCorner = 6)

        assertEquals("the two paths must cross the same keys", rushed.keys, lingered.keys)
        assertTrue("the corner must register at all", rushed.pivots.contains(CORNER_INDEX))
        assertTrue(lingered.pivots.contains(CORNER_INDEX))
        assertTrue(
            "a lingered corner (${lingered.pivotStrength[CORNER_INDEX]}) should read as more " +
                "deliberate than a rushed one (${rushed.pivotStrength[CORNER_INDEX]})",
            lingered.pivotStrength.getValue(CORNER_INDEX) >
                rushed.pivotStrength.getValue(CORNER_INDEX),
        )
    }

    @Test
    fun `ignoring a lingered corner costs a candidate more than ignoring a rushed one`() {
        // The point of carrying dwell at all: it has to reach scoring, not just the pivot set.
        // "adj" skips the corner key entirely, so it pays for it in both strokes -- and should
        // pay more where the finger visibly slowed down.
        val rushed = scoreGlideCandidate("adj", turningPath(samplesAtCorner = 1))!!
        val lingered = scoreGlideCandidate("adj", turningPath(samplesAtCorner = 6))!!
        assertTrue(
            "lingered ($lingered) should penalise a reading that skips the corner more than " +
                "rushed ($rushed)",
            lingered > rushed,
        )
    }

    @Test
    fun `dwell is recorded per key and aligned with the key sequence`() {
        val s = turningPath(samplesAtCorner = 6)
        assertEquals(s.keys.size, s.dwell.size)
        assertEquals(6, s.dwell[CORNER_INDEX])
    }

    @Test
    fun `too few keys is not a glide`() {
        assertTrue(buildGlideStroke(listOf(GlidePoint(0f, 0f, 'h'))).isUsable.not())
        val three = listOf('h', 'j', 'k').map { GlidePoint(0f, 0f, it) }
        assertTrue(buildGlideStroke(three).isUsable)
    }

    companion object {
        /**
         * A turn just short of the old 60-degree cutoff, in radians.
         *
         * Chosen there on purpose: it is exactly the case the boolean threshold got wrong, and
         * the one where dwell has to be able to decide.
         */
        private const val MODERATE_TURN = 0.89f

        /** Where [turningPath] puts its corner. */
        private const val CORNER_INDEX = 3
    }
}
