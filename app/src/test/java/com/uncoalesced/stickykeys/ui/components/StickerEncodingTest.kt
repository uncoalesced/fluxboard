// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class StickerEncodingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun solid(
        width: Int,
        height: Int,
        color: Int = Color.RED,
    ) = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    @Test
    fun `attempt returns a failure for an exception`() {
        val result = attempt<Unit> { error("boom") }
        assertTrue(result.isFailure)
    }

    @Test
    fun `attempt returns a failure for running out of memory`() {
        val result = attempt<Unit> { throw OutOfMemoryError("large") }
        assertTrue(result.isFailure)
    }

    @Test(expected = CancellationException::class)
    fun `attempt lets cancellation through`() {
        attempt<Unit> { throw CancellationException("left the screen") }
    }

    @Test
    fun `a wide image is letterboxed in its thumbnail rather than stretched`() {
        val thumbnail = renderStickerThumbnail(solid(200, 100))

        assertEquals(256, thumbnail.width)
        assertEquals(256, thumbnail.height)
        assertEquals(0, Color.alpha(thumbnail.getPixel(128, 10)))
        assertEquals(0, Color.alpha(thumbnail.getPixel(128, 245)))
        assertEquals(Color.RED, thumbnail.getPixel(128, 128))
        assertEquals(Color.RED, thumbnail.getPixel(5, 128))
    }

    @Test
    fun `a square image fills its thumbnail`() {
        val thumbnail = renderStickerThumbnail(solid(64, 64))

        assertEquals(Color.RED, thumbnail.getPixel(2, 2))
        assertEquals(Color.RED, thumbnail.getPixel(253, 253))
    }

    @Test
    fun `the stored image is webp`() {
        val bytes = encodeStickerImage(solid(16, 16))

        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals("WEBP", String(bytes, 8, 4, Charsets.US_ASCII))
    }

    @Test
    fun `the cached copy keeps transparency`() {
        val source = solid(16, 16, Color.TRANSPARENT)

        val uri = writeBitmapToCache(context.cacheDir, "test", source)

        val decoded = BitmapFactory.decodeFile(Uri.parse(uri).path)
        assertEquals(0, Color.alpha(decoded.getPixel(8, 8)))
    }

    @Test
    fun `a missing file decodes to null`() {
        assertNull(context.decodeBitmap("file:///does/not/exist.png"))
    }

    @Test
    fun `a file that is not an image decodes to null`() {
        val file = File(context.cacheDir, "not-an-image.png").apply { writeText("plain text") }

        assertNull(context.decodeBitmap(Uri.fromFile(file).toString()))
    }

    @Test
    fun `a malformed uri decodes to null`() {
        assertNull(context.decodeBitmap("not a uri"))
    }

    private fun Matrix.point(
        x: Float,
        y: Float,
    ): Pair<Float, Float> {
        val points = floatArrayOf(x, y)
        mapPoints(points)
        return points[0] to points[1]
    }

    @Test
    fun `an upright or unknown orientation needs no transform`() {
        assertNull(exifTransform(ExifInterface.ORIENTATION_NORMAL))
        assertNull(exifTransform(ExifInterface.ORIENTATION_UNDEFINED))
    }

    @Test
    fun `rotation tags rotate clockwise by their angle`() {
        assertEquals(0f to 1f, exifTransform(ExifInterface.ORIENTATION_ROTATE_90)!!.point(1f, 0f))
        assertEquals(-1f to 0f, exifTransform(ExifInterface.ORIENTATION_ROTATE_180)!!.point(1f, 0f))
        assertEquals(0f to -1f, exifTransform(ExifInterface.ORIENTATION_ROTATE_270)!!.point(1f, 0f))
    }

    @Test
    fun `flip tags mirror`() {
        assertEquals(
            -1f to 2f,
            exifTransform(ExifInterface.ORIENTATION_FLIP_HORIZONTAL)!!.point(1f, 2f),
        )
        assertEquals(
            1f to -2f,
            exifTransform(ExifInterface.ORIENTATION_FLIP_VERTICAL)!!.point(1f, 2f),
        )
    }
}
