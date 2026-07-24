// Engineered by uncoalesced
package com.uncoalesced.stickykeys.stickercore.animation

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import java.io.ByteArrayOutputStream

class AndroidAnimatedStickerConverterTest {

    @Test
    fun `converter handles missing file gracefully`() = runBlocking {
        val converter = AndroidAnimatedStickerConverter()
        val context = mock(Context::class.java)
        val uri = mock(Uri::class.java)

        val result = converter.convertVideoToAnimatedSticker(
            context = context,
            videoUri = uri,
            startMs = 0,
            endMs = 1000,
            targetFormat = "image/gif",
            quality = ConversionQuality.HIGH,
            onProgress = {}
        )

        assertTrue(result.isFailure)
    }

    // --- Animated WebP structural validation ---

    private fun chunk(id: String, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(id.toByteArray(Charsets.US_ASCII))
        val size = payload.size
        out.write(size and 0xFF)
        out.write((size shr 8) and 0xFF)
        out.write((size shr 16) and 0xFF)
        out.write((size shr 24) and 0xFF)
        out.write(payload)
        if (size % 2 == 1) out.write(0) // RIFF even-size padding
        return out.toByteArray()
    }

    private fun riffWebP(vararg chunks: ByteArray): ByteArray {
        val body = ByteArrayOutputStream()
        body.write("WEBP".toByteArray(Charsets.US_ASCII))
        chunks.forEach { body.write(it) }
        val bodyBytes = body.toByteArray()

        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        val size = bodyBytes.size
        out.write(size and 0xFF)
        out.write((size shr 8) and 0xFF)
        out.write((size shr 16) and 0xFF)
        out.write((size shr 24) and 0xFF)
        out.write(bodyBytes)
        return out.toByteArray()
    }

    /** VP8X payload: 1 flags byte + 3 reserved + 3-byte width-1 + 3-byte height-1. */
    private fun vp8xPayload(animationFlag: Boolean): ByteArray {
        val flags = if (animationFlag) 0x02 else 0x00
        return byteArrayOf(flags.toByte(), 0, 0, 0, 15, 0, 0, 15, 0, 0)
    }

    @Test
    fun `animated webp with anim flag and two frames validates`() {
        val bytes = riffWebP(
            chunk("VP8X", vp8xPayload(animationFlag = true)),
            chunk("ANIM", ByteArray(6)),
            chunk("ANMF", ByteArray(24)),
            chunk("ANMF", ByteArray(24))
        )
        assertTrue(AndroidAnimatedStickerConverter.isValidAnimatedWebP(bytes))
    }

    @Test
    fun `static webp fails validation`() {
        // A static lossy WebP has a single VP8 chunk and no VP8X animation flag.
        val bytes = riffWebP(chunk("VP8 ", ByteArray(32)))
        assertFalse(AndroidAnimatedStickerConverter.isValidAnimatedWebP(bytes))
    }

    @Test
    fun `single frame with anim flag fails validation`() {
        val bytes = riffWebP(
            chunk("VP8X", vp8xPayload(animationFlag = true)),
            chunk("ANIM", ByteArray(6)),
            chunk("ANMF", ByteArray(24))
        )
        assertFalse(AndroidAnimatedStickerConverter.isValidAnimatedWebP(bytes))
    }

    @Test
    fun `frames without anim flag fail validation`() {
        val bytes = riffWebP(
            chunk("VP8X", vp8xPayload(animationFlag = false)),
            chunk("ANMF", ByteArray(24)),
            chunk("ANMF", ByteArray(24))
        )
        assertFalse(AndroidAnimatedStickerConverter.isValidAnimatedWebP(bytes))
    }

    @Test
    fun `garbage bytes fail validation`() {
        assertFalse(AndroidAnimatedStickerConverter.isValidAnimatedWebP(ByteArray(0)))
        assertFalse(AndroidAnimatedStickerConverter.isValidAnimatedWebP(ByteArray(64) { 0x41 }))
    }
}
