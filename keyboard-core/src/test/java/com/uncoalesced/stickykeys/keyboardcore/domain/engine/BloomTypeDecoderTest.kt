// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

class BloomTypeDecoderTest {
    private val dictionary: ByteBuffer =
        ByteBuffer.wrap(File("src/main/assets/base_dict.bin").readBytes())

    private val namesDictionary: ByteBuffer? =
        File("src/main/assets/names_dict.bin").let {
            if (it.exists()) ByteBuffer.wrap(it.readBytes()) else null
        }

    private val qwertyCentroids: Map<Char, Pair<Float, Float>> =
        mapOf(
            'q' to Pair(0.5f, 0.5f),
            'w' to Pair(1.5f, 0.5f),
            'e' to Pair(2.5f, 0.5f),
            'r' to Pair(3.5f, 0.5f),
            't' to Pair(4.5f, 0.5f),
            'y' to Pair(5.5f, 0.5f),
            'u' to Pair(6.5f, 0.5f),
            'i' to Pair(7.5f, 0.5f),
            'o' to Pair(8.5f, 0.5f),
            'p' to Pair(9.5f, 0.5f),
            'a' to Pair(0.8f, 1.5f),
            's' to Pair(1.8f, 1.5f),
            'd' to Pair(2.8f, 1.5f),
            'f' to Pair(3.8f, 1.5f),
            'g' to Pair(4.8f, 1.5f),
            'h' to Pair(5.8f, 1.5f),
            'j' to Pair(6.8f, 1.5f),
            'k' to Pair(7.8f, 1.5f),
            'l' to Pair(8.8f, 1.5f),
            'z' to Pair(1.2f, 2.5f),
            'x' to Pair(2.2f, 2.5f),
            'c' to Pair(3.2f, 2.5f),
            'v' to Pair(4.2f, 2.5f),
            'b' to Pair(5.2f, 2.5f),
            'n' to Pair(6.2f, 2.5f),
            'm' to Pair(7.2f, 2.5f),
        )

    private fun centroidLookup(c: Char): Pair<Float, Float>? = qwertyCentroids[c.lowercaseChar()]

    private val profile = BloomSpatialProfile()
    private val revertHistory = BloomRevertHistory()
    private val languageModel = FakeLanguageModel()

    private val decoder =
        BloomTypeDecoder(
            dictionary = { dictionary.duplicate() },
            namesDictionary = { namesDictionary?.duplicate() },
            languageModel = languageModel,
            revertHistory = revertHistory,
            profile = profile,
        )

    @Test
    fun `decodes hello from smooth curved trajectory on QWERTY`() {
        // H(5.8, 1.5) -> E(2.5, 0.5) -> L(8.8, 1.5) -> O(8.5, 0.5)
        val rawX = floatArrayOf(5.8f, 4.0f, 2.5f, 4.5f, 7.0f, 8.8f, 8.5f)
        val rawY = floatArrayOf(1.5f, 1.0f, 0.5f, 0.9f, 1.2f, 1.5f, 0.5f)
        val trajectory = buildBloomTrajectory(rawX, rawY, rawX.size, 48)

        val candidates = decoder.decode(trajectory, ::centroidLookup)
        assertTrue("Expected 'hello' among candidates: $candidates", candidates.contains("hello"))
    }

    @Test
    fun `reverted word is penalized and superseded by alternative candidate`() {
        // Swipe T -> O
        val rawX = floatArrayOf(4.5f, 5.5f, 6.5f, 7.5f, 8.5f)
        val rawY = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f, 0.5f)
        val trajectory = buildBloomTrajectory(rawX, rawY, rawX.size, 48)

        // Normally "to" wins
        val normalCandidates = decoder.decode(trajectory, ::centroidLookup)
        assertEquals("to", normalCandidates.firstOrNull())

        // User reverts "to"
        revertHistory.recordRevert("to")

        // Now "to" should be penalized so "too" or another candidate wins
        val afterRevert = decoder.decode(trajectory, ::centroidLookup)
        assertTrue(
            "Candidate list should not have 'to' as first: $afterRevert",
            afterRevert.firstOrNull() != "to",
        )
    }

    @Test
    fun `context boost promotes contextual candidate`() {
        // Swipe T -> O
        val rawX = floatArrayOf(4.5f, 5.5f, 6.5f, 7.5f, 8.5f)
        val rawY = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f, 0.5f)
        val trajectory = buildBloomTrajectory(rawX, rawY, rawX.size, 48)

        // FakeLanguageModel boost for "me too"
        val candidates = decoder.decode(trajectory, ::centroidLookup, previousWord = "me")
        assertTrue(
            "Expected 'too' or 'to' among candidates: $candidates",
            candidates.contains("too") || candidates.contains("to"),
        )
    }

    @Test
    fun `decodes custom personal words matching trajectory`() {
        // Glide for custom word "zorb" (Z -> O -> R -> B)
        // Z(1.2, 2.5) -> O(8.5, 0.5) -> R(3.5, 0.5) -> B(5.2, 2.5)
        val rawX = floatArrayOf(1.2f, 4.0f, 8.5f, 6.0f, 3.5f, 4.5f, 5.2f)
        val rawY = floatArrayOf(2.5f, 1.5f, 0.5f, 0.5f, 0.5f, 1.5f, 2.5f)
        val trajectory = buildBloomTrajectory(rawX, rawY, rawX.size, 48)

        val customDecoder =
            BloomTypeDecoder(
                dictionary = { dictionary.duplicate() },
                namesDictionary = { namesDictionary?.duplicate() },
                languageModel = languageModel,
                revertHistory = revertHistory,
                profile = profile,
                personalWords = { listOf("zorb" to 220f) },
            )

        val candidates = customDecoder.decode(trajectory, ::centroidLookup)
        assertTrue(
            "Expected custom word 'zorb' in candidates: $candidates",
            candidates.contains("zorb"),
        )
    }

    @Test
    fun `first letter anchor prunes tail-matching spurious words`() {
        // Swipe going A -> S -> D -> F
        // A(0.8, 1.5) -> S(1.8, 1.5) -> D(2.8, 1.5) -> F(3.8, 1.5)
        val rawX = floatArrayOf(0.8f, 1.8f, 2.8f, 3.8f)
        val rawY = floatArrayOf(1.5f, 1.5f, 1.5f, 1.5f)
        val trajectory = buildBloomTrajectory(rawX, rawY, rawX.size, 48)

        val candidates = decoder.decode(trajectory, ::centroidLookup)
        // Words starting with 'f' should not appear because 'f' is at the end of the stroke
        val spurious = candidates.filter { it.startsWith("f") }
        assertTrue(
            "Should not propose words starting with tail letter 'f': $spurious",
            spurious.isEmpty(),
        )
    }
}
