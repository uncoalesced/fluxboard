// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The parts of the stats store that survive a process restart, and the pruning that keeps the
 * rolling window from growing a key per day forever.
 *
 * Active-typing time is not asserted here: it is accrued from `SystemClock.elapsedRealtime`
 * between presses, and driving that would be a test of Robolectric's clock shadow rather than
 * of this class. What it feeds is [TypingStatsStore.wpm], which is pure and is tested.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TypingStatsStoreTest {
    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun store() = TypingStatsStore(context())

    private fun prefs() = context().getSharedPreferences("typing_stats", Context.MODE_PRIVATE)

    @Test
    fun `counters survive a flush and a new instance`() {
        val first = store()
        repeat(10) { first.recordKeystroke() }
        first.recordBackspace()
        first.flush()

        // A second instance reads storage rather than the first one's memory -- the same
        // situation as the keyboard process being killed and Settings opened afterwards.
        val second = store()
        val snapshot = second.snapshot()
        assertEquals(10L, snapshot.keystrokes)
        assertEquals(1L, snapshot.backspaces)
    }

    @Test
    fun `words are counted apart from keystrokes and survive a restart`() {
        // The two are deliberately independent rather than one derived from the other. A
        // glide is several keystrokes and one word; a tapped suggestion is one word and no
        // letter keystrokes at all. Asserting different totals here is what stops the word
        // count quietly becoming keystrokes divided by five.
        val first = store()
        repeat(12) { first.recordKeystroke() }
        repeat(3) { first.recordWord() }
        first.flush()

        val snapshot = store().snapshot()
        assertEquals(12L, snapshot.keystrokes)
        assertEquals(3L, snapshot.words)
    }

    @Test
    fun `a word counted but not yet flushed is still shown`() {
        val store = store()
        store.recordWord()
        assertEquals(1L, store.snapshot().words)
    }

    @Test
    fun `a flush carrying only words is not skipped as empty`() {
        // The flush returns early when nothing has been counted, and the emptiness test has
        // to name every counter. Miss one and a session that produced only that counter is
        // silently discarded -- which for words is a real session: finishing a word by
        // tapping a suggestion records a word and no keystroke.
        val first = store()
        first.recordWord()
        first.flush()

        assertEquals(1L, store().snapshot().words)
    }

    @Test
    fun `reset clears the word count with everything else`() {
        val store = store()
        repeat(4) { store.recordWord() }
        store.flush()
        store.clear()

        assertEquals(0L, store.snapshot().words)
    }

    @Test
    fun `unflushed counters are still shown`() {
        // Opening Settings straight from the keyboard must not show a total that stops short
        // of what was just typed; that reads as the feature being broken, not as a boundary.
        val store = store()
        repeat(5) { store.recordKeystroke() }
        assertEquals(5L, store.snapshot().keystrokes)
    }

    @Test
    fun `the two accuracy readings measure different things`() {
        val store = store()
        repeat(10) { store.recordKeystroke() }
        repeat(2) { store.recordBackspace() }
        store.recordAutocorrectAccepted()
        store.recordAutocorrectAccepted()
        store.recordAutocorrectAccepted()
        store.recordAutocorrectUndone()

        val snapshot = store.snapshot()
        assertEquals(80, snapshot.cleanKeystrokePercent)
        assertEquals(75, snapshot.correctionsKeptPercent)
    }

    @Test
    fun `nothing typed yet reports null rather than a flattering percentage`() {
        val snapshot = store().snapshot()
        // Zero would read as "you delete everything" and 100 as "you never make a mistake".
        assertNull(snapshot.cleanKeystrokePercent)
        assertNull(snapshot.correctionsKeptPercent)
        assertNull(snapshot.wordsPerMinute)
    }

    @Test
    fun `the day window is seven entries ending with today`() {
        val store = store()
        repeat(3) { store.recordKeystroke() }
        store.flush()

        val byDay = store.snapshot().keystrokesByDay
        assertEquals(TypingStatsStore.WINDOW_DAYS.toInt(), byDay.size)
        assertEquals(3L, byDay.last())
        assertTrue("only today should have counts", byDay.dropLast(1).all { it == 0L })
    }

    @Test
    fun `days older than the window are removed on flush`() {
        val today = LocalDate.now().toEpochDay()
        val stale = "day_${today - TypingStatsStore.WINDOW_DAYS}"
        val kept = "day_${today - TypingStatsStore.WINDOW_DAYS + 1}"
        prefs()
            .edit()
            .putLong(stale, 99)
            .putLong(kept, 42)
            .apply()

        val store = store()
        store.recordKeystroke()
        store.flush()

        assertTrue("the stale day should be gone", !prefs().contains(stale))
        assertEquals(42L, prefs().getLong(kept, 0))
        // And the surviving day is still visible in the window it just barely made.
        assertEquals(42L, store.snapshot().keystrokesByDay.first())
    }

    @Test
    fun `words per minute needs enough typing to mean anything`() {
        // One fast burst over a second reads as a superhuman rate, so it is not reported.
        assertNull(TypingStatsStore.wpm(keystrokes = 100, activeMs = 1_000))
        assertNull(TypingStatsStore.wpm(keystrokes = 0, activeMs = 60_000))
        // 600 keystrokes is 120 five-character words; over one minute that is 120 wpm.
        assertEquals(120, TypingStatsStore.wpm(keystrokes = 600, activeMs = 60_000))
    }

    @Test
    fun `clearing wipes storage as well as memory`() {
        val store = store()
        repeat(4) { store.recordKeystroke() }
        store.flush()
        store.recordKeystroke()

        store.clear()

        assertEquals(0L, store.snapshot().keystrokes)
        assertEquals(0L, store().snapshot().keystrokes)
    }
}
