// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import com.uncoalesced.stickykeys.keyboardcore.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StickerDeliveryTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `an exact type is accepted`() {
        assertTrue(fieldAcceptsMimeType("image/webp", arrayOf("image/png", "image/webp")))
    }

    @Test
    fun `a wildcard type is accepted`() {
        assertTrue(fieldAcceptsMimeType("image/gif", arrayOf("image/*")))
    }

    @Test
    fun `a different type is refused`() {
        assertFalse(fieldAcceptsMimeType("image/webp", arrayOf("image/png", "image/gif")))
    }

    @Test
    fun `a field that declares nothing refuses`() {
        assertFalse(fieldAcceptsMimeType("image/webp", emptyArray()))
    }

    @Test
    fun `only a delivered sticker goes unannounced`() {
        assertNull(StickerDelivery.Delivered.messageRes)
        assertEquals(R.string.sticker_not_accepted, StickerDelivery.NotAccepted.messageRes)
        assertEquals(R.string.sticker_send_failed, StickerDelivery.Failed.messageRes)
    }

    @Test
    fun `a field that accepts anything takes webp as is`() {
        assertTrue(fieldAcceptsMimeType("image/webp", arrayOf("*/*")))
        assertFalse(shouldTranscodeToPng("image/webp", arrayOf("*/*"), isAnimated = false))
    }

    @Test
    fun `a still webp goes to png for a png-only field`() {
        assertTrue(shouldTranscodeToPng("image/webp", arrayOf("image/png"), isAnimated = false))
    }

    @Test
    fun `a field that takes webp gets no transcode`() {
        val declared = arrayOf("image/png", "image/webp")
        assertFalse(shouldTranscodeToPng("image/webp", declared, isAnimated = false))
    }

    @Test
    fun `a field that takes neither gets no transcode`() {
        assertFalse(shouldTranscodeToPng("image/webp", arrayOf("image/gif"), isAnimated = false))
    }

    @Test
    fun `an animated sticker is never transcoded`() {
        assertFalse(shouldTranscodeToPng("image/webp", arrayOf("image/png"), isAnimated = true))
        assertFalse(shouldTranscodeToPng("image/gif", arrayOf("image/png"), isAnimated = false))
    }

    @Test
    fun `gif and animated webp headers read as animated`() {
        val gif = "GIF89a".toByteArray().copyOf(21)
        assertTrue(isAnimatedHeader(gif, 21))

        val animated = webpHeader(chunk = "VP8X", flags = 0x02)
        assertTrue(isAnimatedHeader(animated, animated.size))
    }

    @Test
    fun `still webp headers read as still`() {
        val extendedStill = webpHeader(chunk = "VP8X", flags = 0x10)
        assertFalse(isAnimatedHeader(extendedStill, extendedStill.size))

        val simple = webpHeader(chunk = "VP8 ", flags = 0x02)
        assertFalse(isAnimatedHeader(simple, simple.size))
    }

    @Test
    fun `a short or unknown header is not animated`() {
        assertFalse(isAnimatedHeader(ByteArray(21), 21))
        val truncated = webpHeader(chunk = "VP8X", flags = 0x02)
        assertFalse(isAnimatedHeader(truncated, 20))
    }

    @Test
    fun `the cache keeps at most the file cap, newest first`() {
        val dir = tempFolder.newFolder(TRANSCODE_DIR)
        val files = (0 until 30).map { cacheFile(dir, "s$it.png", 10, age = 30 - it) }

        pruneTranscodeCache(dir, maxFiles = 25, maxBytes = Long.MAX_VALUE, keep = null)

        assertEquals(25, dir.listFiles()!!.size)
        files.take(5).forEach { assertFalse("${it.name} is oldest", it.exists()) }
        files.drop(5).forEach { assertTrue("${it.name} is recent", it.exists()) }
    }

    @Test
    fun `the cache keeps under the byte cap`() {
        val dir = tempFolder.newFolder(TRANSCODE_DIR)
        val old = cacheFile(dir, "old.png", 600, age = 3)
        val mid = cacheFile(dir, "mid.png", 600, age = 2)
        val new = cacheFile(dir, "new.png", 600, age = 1)

        pruneTranscodeCache(dir, maxFiles = 25, maxBytes = 1_300, keep = null)

        assertFalse(old.exists())
        assertTrue(mid.exists())
        assertTrue(new.exists())
    }

    @Test
    fun `the file just written survives even when it is the oldest`() {
        val dir = tempFolder.newFolder(TRANSCODE_DIR)
        val kept = cacheFile(dir, "kept.png", 600, age = 10)
        val other = cacheFile(dir, "other.png", 600, age = 1)

        pruneTranscodeCache(dir, maxFiles = 1, maxBytes = Long.MAX_VALUE, keep = kept)

        assertTrue(kept.exists())
        assertFalse(other.exists())
    }

    @Test
    fun `leftover temp files are swept`() {
        val dir = tempFolder.newFolder(TRANSCODE_DIR)
        val tmp = cacheFile(dir, "s1.png.tmp", 10, age = 0)

        pruneTranscodeCache(dir, maxFiles = 25, maxBytes = Long.MAX_VALUE, keep = null)

        assertFalse(tmp.exists())
    }

    private fun webpHeader(
        chunk: String,
        flags: Int,
    ): ByteArray {
        val bytes = ByteArray(21)
        "RIFF".toByteArray().copyInto(bytes, 0)
        "WEBP".toByteArray().copyInto(bytes, 8)
        chunk.toByteArray().copyInto(bytes, 12)
        bytes[20] = flags.toByte()
        return bytes
    }

    private fun cacheFile(
        dir: File,
        name: String,
        size: Int,
        age: Int,
    ): File =
        File(dir, name).apply {
            writeBytes(ByteArray(size))
            setLastModified(1_700_000_000_000L - age * 60_000L)
        }
}
