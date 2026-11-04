// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Decoding and encoding against the device's real codecs, which is where EXIF handling and WebP
 * output actually happen. Run with:
 * `.\gradlew.bat :app:connectedDebugAndroidTest
 * -Pandroid.testInstrumentationRunnerArguments.class=<this class>`
 */
@RunWith(AndroidJUnit4::class)
class StickerImagesInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun jpeg(
        width: Int,
        height: Int,
        orientation: Int? = null,
    ): String {
        val file = File(context.cacheDir, "instrumented-${width}x$height-$orientation.jpg")
        file.outputStream().use {
            Bitmap
                .createBitmap(width, height, Bitmap.Config.ARGB_8888)
                .apply { eraseColor(Color.RED) }
                .compress(Bitmap.CompressFormat.JPEG, 90, it)
        }
        if (orientation != null) {
            ExifInterface(file.path).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
        }
        return Uri.fromFile(file).toString()
    }

    @Test
    fun aPortraitPhotoStoredOnItsSideDecodesUpright() {
        val decoded = context.decodeBitmap(jpeg(400, 200, ExifInterface.ORIENTATION_ROTATE_90))

        assertNotNull(decoded)
        assertEquals(200, decoded!!.width)
        assertEquals(400, decoded.height)
    }

    @Test
    fun aLargePhotoIsLimitedOnItsLongestSide() {
        val decoded = context.decodeBitmap(jpeg(3000, 1500))

        assertNotNull(decoded)
        assertTrue("width ${decoded!!.width}", decoded.width <= MAX_IMPORT_SIDE_PX)
        assertTrue("height ${decoded.height}", decoded.height <= MAX_IMPORT_SIDE_PX)
    }

    @Test
    fun aMutableDecodeCanBeDrawnInto() {
        val decoded = context.decodeBitmap(jpeg(16, 16), mutable = true)

        assertTrue(decoded!!.isMutable)
        decoded.setPixel(0, 0, Color.TRANSPARENT)
    }

    @Test
    fun aDamagedFileDecodesToNull() {
        val file = File(context.cacheDir, "damaged.jpg")
        file.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x01))

        assertNull(context.decodeBitmap(Uri.fromFile(file).toString()))
    }

    @Test
    fun aMissingFileDecodesToNull() {
        assertNull(context.decodeBitmap("file:///does/not/exist.png"))
    }

    @Test
    fun theStoredImageAndThumbnailAreWebp() {
        val bitmap = Bitmap.createBitmap(32, 16, Bitmap.Config.ARGB_8888)

        listOf(encodeStickerImage(bitmap), encodeStickerThumbnail(bitmap)).forEach { bytes ->
            assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
            assertEquals("WEBP", String(bytes, 8, 4, Charsets.US_ASCII))
        }
    }
}
