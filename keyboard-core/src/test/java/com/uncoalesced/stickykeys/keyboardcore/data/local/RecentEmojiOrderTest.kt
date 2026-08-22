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

    @Test
    fun `reopening the picker settles the order instead of deferring it to the next tap`() {
        // Issue #28. The picker's view model lives as long as the IME service, so it carries
        // the last order it published across a keyboard dismiss. The freeze lapses on a clock
        // that nothing reads, so without settling on open the grid comes back showing the
        // pre-promotion order and then jumps the moment the user taps -- once per session,
        // which is exactly the reported "shuffles after the first use but not after that".
        val p = prefs()
        var now = 1_000L
        p.clock = { now }

        p.recordEmojiUse(grinning)
        now += KeyboardPreferences.EMOJI_PROMOTION_DELAY_MS
        p.recordEmojiUse(beaming)
        assertEquals("held while the burst is in flight", listOf(grinning), p.recentEmoji())

        // The keyboard closes and comes back. No time has to pass for this to matter: what
        // makes the grid stale is the held order, not the clock.
        p.settleRecentEmoji()
        assertEquals(
            "reopening must show the promotion, not defer it to the next tap",
            listOf(beaming, grinning),
            p.recentEmoji(),
        )
    }

    @Test
    fun `settling does not disturb a burst that has not been interrupted`() {
        // The freeze still has to do its job. Settling is tied to the picker opening, so a
        // run of taps with no reopen between them must behave exactly as it did before.
        val p = prefs()
        var now = 1_000L
        p.clock = { now }

        p.recordEmojiUse(grinning)
        now += KeyboardPreferences.EMOJI_PROMOTION_DELAY_MS
        assertEquals(listOf(grinning), p.recentEmoji())

        p.recordEmojiUse(beaming)
        now += 500
        p.recordEmojiUse(laughing)
        assertEquals("still held mid-burst", listOf(grinning), p.recentEmoji())
    }

    /** A multi-code-point glyph has to survive storage whole rather than as its halves. */
    @Test
    fun `a non-BMP glyph round-trips`() {
        val p = prefs()
        p.recordEmojiUse(grinning)
        assertEquals(listOf(grinning), prefs().recentEmoji())
    }
}
