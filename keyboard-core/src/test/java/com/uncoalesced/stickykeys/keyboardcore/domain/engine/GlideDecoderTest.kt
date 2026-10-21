// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

/**
 * The decoder runs on a plain JVM, with no Robolectric, no Room and no Context.
 *
 * That is the point of [GlideDecoder] being its own class: recorded glide traces can be
 * replayed into it straight from a unit test, against the dictionary that actually ships.
 * If this test ever needs an Android runner, the seam has been lost.
 */
class GlideDecoderTest {
    /** The shipped dictionary. Unit tests run with the module directory as working directory. */
    private val dictionary: ByteBuffer =
        ByteBuffer.wrap(File("src/main/assets/base_dict.bin").readBytes())

    private val decoder = GlideDecoder({ dictionary.duplicate() }, FakeLanguageModel())

    /** The h-e-l-o path from GlideStrokeTest and GlideBeamTest. */
    private val helloStroke = GlideStroke("hgfdertyuiklo".toList(), setOf(0, 4, 11, 12))

    @Test
    fun `decodes the hello path on the JVM against the shipped dictionary`() {
        val results = decoder.decode(helloStroke)
        assertTrue("expected 'hello' among $results", results.contains("hello"))
    }

    @Test
    fun `an unusable stroke decodes to nothing`() {
        assertEquals(emptyList<String>(), decoder.decode(GlideStroke(listOf('h'), emptySet())))
    }

    @Test
    fun `no dictionary decodes to nothing rather than throwing`() {
        val empty = GlideDecoder({ null }, FakeLanguageModel())
        assertEquals(emptyList<String>(), empty.decode(helloStroke))
    }
}
