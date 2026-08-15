// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.data.local

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Recents grid must hold still while a burst of taps is in flight.
 *
 * Promoting on every use reshuffled the whole strip after each tap, so reacting with three
 * emoji in a row meant the second and third taps landed on a grid that had just moved under
 * the finger.
 *
 * The half of this worth a test is the half that is easy to get wrong: the *write* must not
 * be delayed along with the display order. An IME is a service the system kills freely and
 * the usual emoji flow is tap-then-send, which closes the keyboard within a second -- so
 * holding the write until the window elapsed would have quietly dropped most emoji out of
 * Recents. [writeIsNotDeferred] is the case that fails if anyone reintroduces that.
 *
 * Glyphs are built from code points rather than pasted, because
 * `scripts/check-source-rules.sh` bans emoji literals in source and does not exempt tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecentEmojiOrderTest {
    private fun glyph(codePoint: Int) = String(Character.toChars(codePoint))

    private val grinning = glyph(0x1F600)
    private val beaming = glyph(0x1F601)
    private val laughing = glyph(0x1F602)

    private fun prefs() = KeyboardPreferences(ApplicationProvider.getApplicationContext())

    @Test
    fun `order is held for the whole burst and settles once after it`() {
        val p = prefs()
        var now = 1_000L
        p.clock = { now }

        p.recordEmojiUse(grinning)
        now += KeyboardPreferences.EMOJI_PROMOTION_DELAY_MS
        assertEquals("first use settles", listOf(grinning), p.recentEmoji())

        // Two further taps inside the window. The strip must report the order it had
        // before the burst started, not after each tap.
        p.recordEmojiUse(beaming)
        assertEquals("held after tap 2", listOf(grinning), p.recentEmoji())

        now += 500
        p.recordEmojiUse(laughing)
        assertEquals("held after tap 3", listOf(grinning), p.recentEmoji())

        // Each tap restarts the window, so the strip settles once the burst ends.
        now += KeyboardPreferences.EMOJI_PROMOTION_DELAY_MS
        assertEquals(listOf(laughing, beaming, grinning), p.recentEmoji())
    }

    @Test
    fun writeIsNotDeferred() {
        val p = prefs()
        var now = 1_000L
        p.clock = { now }

        p.recordEmojiUse(grinning)

        // A second reader of the same SharedPreferences stands in for the next process:
        // it has no frozen order of its own, so it sees exactly what was persisted. If the
        // promotion were held in memory this would be empty.
        assertEquals(listOf(grinning), prefs().recentEmoji())
    }

    /** A multi-code-point glyph has to survive storage whole rather than as its halves. */
    @Test
    fun `a non-BMP glyph round-trips`() {
        val p = prefs()
        p.recordEmojiUse(grinning)
        assertEquals(listOf(grinning), prefs().recentEmoji())
    }
}
