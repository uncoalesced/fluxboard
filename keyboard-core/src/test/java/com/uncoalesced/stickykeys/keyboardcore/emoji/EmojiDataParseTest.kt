// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.emoji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The picker's contents come out of this parse, and until now it was verified only by looking
 * at a phone.
 *
 * No emoji literals anywhere below. `scripts/check-source-rules.sh` bans them across all source
 * including tests, and it does not distinguish fixture data from UI text -- so astral-plane
 * characters are built from their codepoints, which is also the only way to be sure what the
 * fixture actually contains.
 */
class EmojiDataParseTest {
    private val grin = String(Character.toChars(0x1F600))
    private val joy = String(Character.toChars(0x1F602))
    private val apple = String(Character.toChars(0x1F34E))

    private fun parse(
        text: String,
        isSupported: (String) -> Boolean = { true },
    ) = parseEmojiData(text.lineSequence(), isSupported)

    @Test
    fun `groups keep their order and their records`() {
        val groups =
            parse(
                """
                #Smileys
                $grin	0.6	grinning face
                $joy	0.6	face with tears of joy
                #Food
                $apple	0.6	red apple
                """.trimIndent(),
            )

        assertEquals(listOf("Smileys", "Food"), groups.map { it.name })
        assertEquals(2, groups[0].emoji.size)
        assertEquals(Emoji(grin, "0.6", "grinning face"), groups[0].emoji[0])
        assertEquals(listOf(apple), groups[1].emoji.map { it.glyph })
    }

    @Test
    fun `an unsupported glyph is dropped rather than drawn as tofu`() {
        val groups =
            parse(
                """
                #Smileys
                $grin	0.6	grinning face
                $joy	15.1	face with tears of joy
                """.trimIndent(),
            ) { it == grin }

        assertEquals(listOf(grin), groups.single().emoji.map { it.glyph })
    }

    @Test
    fun `a group the font cannot draw at all is dropped, not shown empty`() {
        // The failure this prevents is a tab that opens on nothing, which reads as the picker
        // being broken rather than as the device lacking those glyphs.
        val groups =
            parse(
                """
                #Smileys
                $grin	0.6	grinning face
                #Unsupported
                $joy	15.1	face with tears of joy
                """.trimIndent(),
            ) { it != joy }

        assertEquals(listOf("Smileys"), groups.map { it.name })
    }

    @Test
    fun `a malformed line is skipped and does not end the group`() {
        val groups =
            parse(
                """
                #Smileys
                $grin	0.6	grinning face
                not-a-record
                $joy	0.6	face with tears of joy
                """.trimIndent(),
            )

        assertEquals(2, groups.single().emoji.size)
    }

    @Test
    fun `a name containing a tab keeps only its first field`() {
        // split('\t') with no limit: everything after the third field is discarded rather than
        // silently appended to the name, which is what a keyword list in the source data would
        // otherwise do to the search text.
        val groups =
            parse(
                """
                #Smileys
                $grin	0.6	grinning face	extra
                """.trimIndent(),
            )
        val record = groups.single().emoji.single()
        assertEquals("grinning face", record.name)
    }

    @Test
    fun `records before any group header are discarded`() {
        // There is no group to put them in, and inventing one would show the user a tab with no
        // name. The generator always writes a header first, so this is defence against a
        // truncated asset rather than a shape the pipeline produces.
        val groups = parse("$grin	0.6	grinning face")
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `blank lines are ignored`() {
        val groups =
            parse(
                """
                |
                |#Smileys
                |
                |$grin	0.6	grinning face
                |
                """.trimMargin(),
            )
        assertEquals(1, groups.single().emoji.size)
    }
}
