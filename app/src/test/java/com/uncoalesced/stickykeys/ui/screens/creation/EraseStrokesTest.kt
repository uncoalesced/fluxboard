// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens.creation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/**
 * The eraser used to collect strokes, draw them as an overlay and then save the original image
 * untouched. These assert on the pixels of the result.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class EraseStrokesTest {
    private val source =
        Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }

    private fun stroke(vararg points: Pair<Float, Float>) =
        EraseStroke(points.map { EraseStrokePoint(it.first, it.second) }, width = 10f)

    @Test
    fun `a stroke cuts through to transparency`() {
        val result = applyEraseStrokes(source, listOf(stroke(10f to 50f, 90f to 50f)))

        assertEquals(0, Color.alpha(result.getPixel(50, 50)))
        assertEquals(0, Color.alpha(result.getPixel(50, 53)))
    }

    @Test
    fun `an opaque photo keeps its transparency through a save`() {
        val opaque =
            Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.RED)
                setHasAlpha(false)
            }

        val result = applyEraseStrokes(opaque, listOf(stroke(10f to 50f, 90f to 50f)))

        val bytes = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        val reloaded = BitmapFactory.decodeByteArray(bytes.toByteArray(), 0, bytes.size())
        assertEquals(0, Color.alpha(reloaded.getPixel(50, 50)))
        assertEquals(Color.RED, reloaded.getPixel(50, 20))
    }

    @Test
    fun `pixels away from the stroke are untouched`() {
        val result = applyEraseStrokes(source, listOf(stroke(10f to 50f, 90f to 50f)))

        assertEquals(Color.RED, result.getPixel(50, 20))
        assertEquals(Color.RED, result.getPixel(50, 80))
    }

    @Test
    fun `a single point erases a dot`() {
        val result = applyEraseStrokes(source, listOf(stroke(50f to 50f)))

        assertEquals(0, Color.alpha(result.getPixel(50, 50)))
        assertEquals(Color.RED, result.getPixel(50, 70))
    }

    @Test
    fun `every stroke is applied`() {
        val result =
            applyEraseStrokes(
                source,
                listOf(stroke(10f to 20f, 90f to 20f), stroke(10f to 80f, 90f to 80f)),
            )

        assertEquals(0, Color.alpha(result.getPixel(50, 20)))
        assertEquals(0, Color.alpha(result.getPixel(50, 80)))
    }

    @Test
    fun `the source stays intact`() {
        applyEraseStrokes(source, listOf(stroke(10f to 50f, 90f to 50f)))

        assertEquals(Color.RED, source.getPixel(50, 50))
    }

    @Test
    fun `no strokes leaves the image whole`() {
        assertEquals(Color.RED, applyEraseStrokes(source, emptyList()).getPixel(50, 50))
    }
}
