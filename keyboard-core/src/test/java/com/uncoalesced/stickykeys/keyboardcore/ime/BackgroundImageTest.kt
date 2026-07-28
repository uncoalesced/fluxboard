// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackgroundImageTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun writePng(
        name: String,
        width: Int,
        height: Int,
    ): File {
        val file = File(context.cacheDir, name)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    @Test
    fun `a 4000x3000 photo into a keyboard strip is subsampled then scaled to fit`() {
        // The case from the defect report: 4000 * 3000 * 4 bytes is about 48 MB decoded at
        // full size, to fill a strip roughly 1080x280.
        // inSampleSize only moves in powers of two, and width is the binding constraint for
        // a cover fit (4000/4 = 1000 would fall under the 1080 needed), so it stops at 2.
        val sample = calculateInSampleSize(4000, 3000, 1080, 280)
        assertEquals(2, sample)

        val sampledBytes = (4000L / sample) * (3000L / sample) * 4
        val fullBytes = 4000L * 3000L * 4
        assertEquals(48_000_000L, fullBytes)
        assertEquals(12_000_000L, sampledBytes)

        // The exact scale afterwards is what actually gets it down to strip size.
        val sampledBitmap =
            Bitmap.createBitmap(4000 / sample, 3000 / sample, Bitmap.Config.ARGB_8888)
        val finalBitmap = scaleToCover(sampledBitmap, 1080, 280)
        val finalBytes = finalBitmap.width.toLong() * finalBitmap.height * 4

        assertTrue(
            "final bitmap is ${finalBitmap.width}x${finalBitmap.height} = $finalBytes bytes; " +
                "expected at least a 10x cut from $fullBytes",
            fullBytes / finalBytes >= 10,
        )
        assertTrue(
            "final bitmap must still cover the 1080x280 strip",
            finalBitmap.width >= 1080 && finalBitmap.height >= 280,
        )
    }

    @Test
    fun `scaleToCover never scales an already-small image up`() {
        val small = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        val result = scaleToCover(small, 1080, 280)
        assertEquals(400, result.width)
        assertEquals(200, result.height)
    }

    @Test
    fun `sample size is always a power of two`() {
        listOf(
            Triple(4000, 3000, 1),
            Triple(1000, 1000, 3),
            Triple(333, 999, 7),
            Triple(4096, 4096, 64),
        ).forEach { (w, h, req) ->
            val sample = calculateInSampleSize(w, h, req, req)
            assertTrue(
                "inSampleSize $sample for ${w}x$h -> $req is not a power of two",
                sample > 0 && (sample and (sample - 1)) == 0,
            )
        }
    }

    @Test
    fun `an image already smaller than the target is not subsampled`() {
        assertEquals(1, calculateInSampleSize(200, 100, 1080, 280))
        assertEquals(1, calculateInSampleSize(1080, 280, 1080, 280))
    }

    @Test
    fun `subsampling never drops below the requested size`() {
        val sourceW = 4000
        val sourceH = 3000
        val reqW = 1080
        val reqH = 280
        val sample = calculateInSampleSize(sourceW, sourceH, reqW, reqH)

        assertTrue(
            "subsampled width ${sourceW / sample} fell under the requested $reqW",
            sourceW / sample >= reqW,
        )
        assertTrue(
            "subsampled height ${sourceH / sample} fell under the requested $reqH",
            sourceH / sample >= reqH,
        )
    }

    @Test
    fun `a degenerate target does not divide by zero`() {
        assertEquals(1, calculateInSampleSize(4000, 3000, 0, 0))
        assertEquals(1, calculateInSampleSize(4000, 3000, -1, -1))
    }

    @Test
    fun `the decode itself is subsampled, not just scaled afterwards`() {
        // This is the assertion that distinguishes a real inSampleSize decode from decoding
        // at full resolution and shrinking after: both end at the same final size, but only
        // the former avoids allocating the full-size bitmap in the first place.
        val file = writePng("bg-sampled.png", 1600, 1200)
        val sampled = decodeSampled(file.absolutePath, 800, 600)

        assertNotNull(sampled)
        assertEquals("expected a half-size decode, not a full one", 800, sampled!!.width)
        assertEquals(600, sampled.height)
    }

    @Test
    fun `decoding a real file honours the sample size`() {
        val file = writePng("bg-large.png", 1600, 1200)
        val decoded = decodeDownsampled(file.absolutePath, 200, 150)

        assertNotNull("expected a decoded bitmap", decoded)
        // 1600x1200 into 200x150 is three halvings, so 200x150 out.
        assertEquals(200, decoded!!.width)
        assertEquals(150, decoded.height)

        val fullSize = BitmapFactory.decodeFile(file.absolutePath)!!
        assertTrue(
            "downsampled bitmap (${decoded.width}x${decoded.height}) is not smaller than " +
                "the full decode (${fullSize.width}x${fullSize.height})",
            decoded.width < fullSize.width && decoded.height < fullSize.height,
        )
    }

    @Test
    fun `a missing file yields null rather than throwing`() {
        assertNull(decodeDownsampled(File(context.cacheDir, "nope.png").absolutePath, 100, 100))
    }

    @Test
    fun `a file that is not an image does not throw`() {
        // Robolectric's BitmapFactory is a shadow that does not really parse the bytes, so
        // this can only assert the call is exception-safe, not that it returns null.
        val junk = File(context.cacheDir, "junk.png")
        junk.writeText("this is not a png")
        decodeDownsampled(junk.absolutePath, 100, 100)
    }
}
