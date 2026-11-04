// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapLoadingTest {
    @Test
    fun `an image within the limit is not sampled`() {
        assertEquals(1, sampleSizeFor(2048, 1000))
        assertEquals(1, sampleSizeFor(100, 100))
    }

    @Test
    fun `one pixel over the limit samples by two`() {
        assertEquals(2, sampleSizeFor(2049, 1000))
    }

    @Test
    fun `the sample size is the smallest that fits`() {
        assertEquals(2, sampleSizeFor(4096, 3072))
        assertEquals(4, sampleSizeFor(4097, 3072))
        assertEquals(4, sampleSizeFor(8160, 6120))
    }

    @Test
    fun `the taller side counts as well`() {
        assertEquals(4, sampleSizeFor(3072, 8160))
    }

    @Test
    fun `unknown dimensions are not sampled`() {
        assertEquals(1, sampleSizeFor(-1, -1))
    }

    @Test
    fun `an image within the limit keeps its size`() {
        assertEquals(2048 to 1000, scaledSizeFor(2048, 1000))
    }

    @Test
    fun `scaling brings the longest side to the limit and keeps the ratio`() {
        assertEquals(2048 to 1536, scaledSizeFor(4000, 3000))
        assertEquals(1536 to 2048, scaledSizeFor(3000, 4000))
    }

    @Test
    fun `an extreme panorama keeps at least one pixel`() {
        assertEquals(2048 to 1, scaledSizeFor(100_000, 2))
    }
}
