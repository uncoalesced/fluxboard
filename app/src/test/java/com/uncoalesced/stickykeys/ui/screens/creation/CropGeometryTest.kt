// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens.creation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CropGeometryTest {
    @Test
    fun `untouched crop keeps the whole image`() {
        val scale = cropFitScale(1000f, 2000f, 2000, 1000)

        assertEquals(
            CropRect(0, 0, 2000, 1000),
            cropRectFor(1000f, 2000f, 2000, 1000, scale, 0f, 0f),
        )
    }

    @Test
    fun `zooming in crops to the centre of the image`() {
        // Box 800, fit 0.4, zoom 4: the box spans 500 bitmap pixels.
        assertEquals(
            CropRect(750, 750, 500, 500),
            cropRectFor(1000f, 1000f, 2000, 2000, 1.6f, 0f, 0f),
        )
    }

    @Test
    fun `dragging the image right moves the crop left`() {
        assertEquals(
            CropRect(500, 750, 500, 500),
            cropRectFor(1000f, 1000f, 2000, 2000, 1.6f, 400f, 0f),
        )
    }

    @Test
    fun `dragging the image down moves the crop up`() {
        assertEquals(
            CropRect(750, 500, 500, 500),
            cropRectFor(1000f, 1000f, 2000, 2000, 1.6f, 0f, 400f),
        )
    }

    @Test
    fun `a pan clamped to the limit lands on the image corner`() {
        val box = cropBoxSide(1000f, 1000f)
        val limit = clampCropOffset(100_000f, 2000 * 1.6f, box)

        assertEquals(
            CropRect(0, 0, 500, 500),
            cropRectFor(1000f, 1000f, 2000, 2000, 1.6f, limit, limit),
        )
    }

    @Test
    fun `an image smaller than the box cannot be panned`() {
        assertEquals(0f, clampCropOffset(50f, 400f, 800f), 0f)
        assertEquals(0f, clampCropOffset(-50f, 400f, 800f), 0f)
    }

    @Test
    fun `a pan within the limit is left alone`() {
        assertEquals(30f, clampCropOffset(30f, 1000f, 800f), 0f)
        assertEquals(-100f, clampCropOffset(-300f, 1000f, 800f), 0f)
    }

    @Test
    fun `a box overhanging the image is clipped to it`() {
        // Scale 1: the 800 box over a 400x2000 image covers all of its width.
        assertEquals(
            CropRect(0, 600, 400, 800),
            cropRectFor(1000f, 1000f, 400, 2000, 1f, 0f, 0f),
        )
    }

    @Test
    fun `the box and the screen transform agree`() {
        val scale = 1.25f
        val offsetX = -60f
        val offsetY = 35f
        val rect = cropRectFor(1000f, 1600f, 3000, 2000, scale, offsetX, offsetY)!!

        fun screenX(pixel: Int) = 500f + offsetX + scale * (pixel - 1500f)

        fun screenY(pixel: Int) = 800f + offsetY + scale * (pixel - 1000f)

        // The box is 800 wide and centred, so its edges sit at 100 and 900, 400 and 1200.
        assertEquals(100f, screenX(rect.left), 1f)
        assertEquals(900f, screenX(rect.left + rect.width), 1f)
        assertEquals(400f, screenY(rect.top), 1f)
        assertEquals(1200f, screenY(rect.top + rect.height), 1f)
    }

    @Test
    fun `an unmeasured view has no crop`() {
        assertNull(cropRectFor(0f, 0f, 2000, 2000, 1f, 0f, 0f))
    }

    @Test
    fun `a box entirely off the image has no crop`() {
        assertNull(cropRectFor(1000f, 1000f, 100, 100, 1f, 5000f, 0f))
    }
}
