// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * The FLBG format has a writer in Python and a reader in Kotlin, and nothing else checks that
 * they agree. Pure JUnit rather than Robolectric on purpose: this is a byte layout, it has no
 * Android in it, and it must be assertable without the real 474 KB asset.
 *
 * Malformed input is asserted to produce an empty map rather than an exception, because the
 * runtime contract is that missing context data degrades ranking and never breaks it -- a
 * truncated file has to behave like an absent one.
 */
class ParseFlbgTest {
    /** Builds an FLBG payload the same way dictionary-tools/build_bigrams.py does. */
    private fun encode(
        table: List<Pair<String, List<Pair<String, Int>>>>,
        magic: String = "FLBG",
        declaredCount: Int = table.size,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(magic.toByteArray(Charsets.UTF_8))
        out.write(declaredCount ushr 24)
        out.write(declaredCount ushr 16)
        out.write(declaredCount ushr 8)
        out.write(declaredCount)
        for ((prev, followers) in table) {
            val prevBytes = prev.toByteArray(Charsets.UTF_8)
            out.write(prevBytes.size)
            out.write(prevBytes)
            out.write(followers.size)
            for ((next, weight) in followers) {
                val nextBytes = next.toByteArray(Charsets.UTF_8)
                out.write(nextBytes.size)
                out.write(nextBytes)
                out.write(weight)
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `parses records and preserves follower order`() {
        val bytes =
            encode(
                listOf(
                    "how" to listOf("to" to 255, "do" to 234, "many" to 228),
                    "thank" to listOf("you" to 255),
                ),
            )

        val table = parseFlbg(bytes)

        assertEquals(2, table.size)
        assertEquals(listOf("to" to 255, "do" to 234, "many" to 228), table["how"])
        assertEquals(listOf("you" to 255), table["thank"])
    }

    @Test
    fun `a weight of 255 survives the unsigned byte read`() {
        // A signed read turns 255 into -1, which would silently invert the strongest
        // follower in every record into the weakest.
        val table = parseFlbg(encode(listOf("i" to listOf("have" to 255))))
        assertEquals(255, table.getValue("i").single().second)
    }

    @Test
    fun `wrong magic yields an empty map rather than garbage`() {
        val bytes = encode(listOf("how" to listOf("to" to 255)), magic = "FLCT")
        assertTrue(parseFlbg(bytes).isEmpty())
    }

    @Test
    fun `a truncated payload yields an empty map rather than throwing`() {
        val full = encode(listOf("how" to listOf("to" to 255, "do" to 234)))
        // Cut mid-record: the header still promises a record that is no longer all there.
        val truncated = full.copyOf(full.size - 4)
        assertTrue(parseFlbg(truncated).isEmpty())
    }

    @Test
    fun `a record count larger than the payload yields an empty map`() {
        val bytes = encode(listOf("how" to listOf("to" to 255)), declaredCount = 500)
        assertTrue(parseFlbg(bytes).isEmpty())
    }

    @Test
    fun `an empty or header-only payload yields an empty map`() {
        assertTrue(parseFlbg(ByteArray(0)).isEmpty())
        assertTrue(parseFlbg(encode(emptyList())).isEmpty())
    }
}
