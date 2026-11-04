// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageFitTest {
    @Test
    fun `a wide image fits the width and centres vertically`() {
        assertEquals(ImageFit(0.5f, 0f, 250f), ImageFit.of(1000f, 1000f, 2000, 1000))
    }

    @Test
    fun `a tall image fits the height and centres horizontally`() {
        assertEquals(ImageFit(0.5f, 250f, 0f), ImageFit.of(1000f, 1000f, 1000, 2000))
    }

    @Test
    fun `screen positions map back to bitmap pixels`() {
        val fit = ImageFit.of(1000f, 1000f, 2000, 1000)

        assertEquals(0f, fit.toBitmapX(0f), 0f)
        assertEquals(2000f, fit.toBitmapX(1000f), 0f)
        assertEquals(0f, fit.toBitmapY(250f), 0f)
        assertEquals(1000f, fit.toBitmapY(750f), 0f)
    }

    @Test
    fun `screen lengths scale to bitmap lengths`() {
        assertEquals(100f, ImageFit(0.5f, 0f, 0f).toBitmapLength(50f), 0f)
    }
}
