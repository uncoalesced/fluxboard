// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens.edit

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.uncoalesced.stickykeys.ui.components.ImageFit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The size slider assigned the paint's own field to itself, and the saved text used screen
 * coordinates on bitmap pixels, so it landed away from where the preview showed it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TextOverlayRenderTest {
    private val source = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)

    // The bitmap shown at half size, filling a 500px view.
    private val fit = ImageFit(scale = 0.5f, left = 0f, top = 0f)

    private fun paintedBounds(bitmap: Bitmap): Rect {
        val bounds = Rect(bitmap.width, bitmap.height, 0, 0)
        for (y in 0 until bitmap.height step 4) {
            for (x in 0 until bitmap.width step 4) {
                if (Color.alpha(bitmap.getPixel(x, y)) != 0) {
                    bounds.union(x, y)
                }
            }
        }
        return bounds
    }

    private fun render(
        viewSize: Float,
        viewX: Float = 100f,
        viewY: Float = 200f,
    ) = renderTextOverlay(source, "HHHH", Color.WHITE, fit, viewX, viewY, viewSize)

    @Test
    fun `the paint takes the requested size`() {
        assertEquals(90f, textOverlayPaint(Color.WHITE, 90f).textSize, 0f)
    }

    @Test
    fun `text lands where the preview shows it`() {
        val bounds = paintedBounds(render(viewSize = 40f))

        // Starts at view x 100 and sits on view y 200, which are bitmap 200 and 400.
        assertTrue("left ${bounds.left}", bounds.left in 196..240)
        assertTrue("bottom ${bounds.bottom}", bounds.bottom in 380..408)
    }

    @Test
    fun `a larger size draws larger text`() {
        val small = paintedBounds(render(viewSize = 20f)).height()
        val large = paintedBounds(render(viewSize = 80f)).height()

        assertTrue("small $small large $large", large > small * 2)
    }

    @Test
    fun `the source stays intact`() {
        render(viewSize = 40f)

        assertEquals(0, Color.alpha(source.getPixel(220, 390)))
    }
}
