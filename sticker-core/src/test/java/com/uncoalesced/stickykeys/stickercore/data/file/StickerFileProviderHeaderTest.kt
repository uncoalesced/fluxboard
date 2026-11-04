// Engineered by uncoalesced
package com.uncoalesced.stickykeys.stickercore.data.file

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StickerFileProviderHeaderTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun bytes(vararg values: Int): ByteArray =
        ByteArray(values.size) { values[it].toByte() }

    private fun padded(
        prefix: ByteArray,
        size: Int = HEADER_BYTES,
    ): ByteArray = prefix.copyOf(size)

    private fun webp(): ByteArray {
        val header = ByteArray(HEADER_BYTES)
        "RIFF".forEachIndexed { i, c -> header[i] = c.code.toByte() }
        "WEBP".forEachIndexed { i, c -> header[8 + i] = c.code.toByte() }
        return header
    }

    private fun png() = padded(bytes(0x89, 'P'.code, 'N'.code, 'G'.code))

    private fun gif() = padded("GIF89a".toByteArray())

    private fun jpeg() = padded(bytes(0xFF, 0xD8, 0xFF, 0xE0))

    private fun file(content: ByteArray): File = folder.newFile().also { it.writeBytes(content) }

    // imageTypeFromHeader

    @Test
    fun `webp header is recognised`() {
        assertEquals("image/webp", imageTypeFromHeader(webp(), HEADER_BYTES))
    }

    @Test
    fun `png header is recognised`() {
        assertEquals("image/png", imageTypeFromHeader(png(), HEADER_BYTES))
    }

    @Test
    fun `gif header is recognised`() {
        assertEquals("image/gif", imageTypeFromHeader(gif(), HEADER_BYTES))
    }

    @Test
    fun `jpeg header is recognised`() {
        assertEquals("image/jpeg", imageTypeFromHeader(jpeg(), HEADER_BYTES))
    }

    @Test
    fun `garbage header is null`() {
        assertNull(imageTypeFromHeader(ByteArray(HEADER_BYTES), HEADER_BYTES))
    }

    @Test
    fun `short read is null even with a valid signature`() {
        assertNull(imageTypeFromHeader(webp(), HEADER_BYTES - 1))
    }

    @Test
    fun `riff without webp is null`() {
        val wave = ByteArray(HEADER_BYTES)
        "RIFF".forEachIndexed { i, c -> wave[i] = c.code.toByte() }
        "WAVE".forEachIndexed { i, c -> wave[8 + i] = c.code.toByte() }
        assertNull(imageTypeFromHeader(wave, HEADER_BYTES))
    }

    // compareMimeType

    @Test
    fun `exact match is accepted`() {
        assertTrue(compareMimeType("image/webp", "image/webp"))
    }

    @Test
    fun `full wildcard is accepted`() {
        assertTrue(compareMimeType("image/webp", "*/*"))
    }

    @Test
    fun `subtype wildcard is accepted`() {
        assertTrue(compareMimeType("image/webp", "image/*"))
    }

    @Test
    fun `different subtype is rejected`() {
        assertFalse(compareMimeType("image/webp", "image/gif"))
    }

    @Test
    fun `filter without a slash is rejected`() {
        assertFalse(compareMimeType("image/webp", "image"))
    }

    @Test
    fun `concrete type without a slash is rejected`() {
        assertFalse(compareMimeType("webp", "image/*"))
    }

    // hasRecognisedImageHeader

    @Test
    fun `real image headers pass`() {
        assertTrue(file(webp()).hasRecognisedImageHeader())
        assertTrue(file(png()).hasRecognisedImageHeader())
        assertTrue(file(gif()).hasRecognisedImageHeader())
        assertTrue(file(jpeg()).hasRecognisedImageHeader())
    }

    @Test
    fun `all-zero file fails`() {
        assertFalse(file(ByteArray(64)).hasRecognisedImageHeader())
    }

    @Test
    fun `file shorter than the header fails`() {
        assertFalse(file(png().copyOf(HEADER_BYTES - 1)).hasRecognisedImageHeader())
    }

    @Test
    fun `missing file fails`() {
        assertFalse(File(folder.root, "does-not-exist").hasRecognisedImageHeader())
    }
}
