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

    @Test
    fun `too few keys is not a glide`() {
        assertTrue(buildGlideStroke(listOf(GlidePoint(0f, 0f, 'h'))).isUsable.not())
        val three = listOf('h', 'j', 'k').map { GlidePoint(0f, 0f, it) }
        assertTrue(buildGlideStroke(three).isUsable)
    }
}
