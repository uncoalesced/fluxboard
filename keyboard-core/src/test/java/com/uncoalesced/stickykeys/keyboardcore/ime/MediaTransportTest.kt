// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one part of the media row that can be wrong while looking entirely right.
 *
 * `KEYCODE_MEDIA_NEXT` is 87 and `KEYCODE_MEDIA_PREVIOUS` is 88. Transposing them produces a
 * feature that works -- both buttons skip, both are wired, nothing throws -- and points the
 * wrong way. That is invisible in a screenshot, invisible in a code review of the composable,
 * and obvious within one second of holding the phone, which is the worst possible place for it
 * to be discovered. Everything else in `MediaTransport` is a framework call with no decision in
 * it; this table is the decision.
 */
class MediaTransportTest {
    @Test
    fun `each action maps to its own media key`() {
        assertEquals(
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            MediaTransport.keyCodeFor(MediaTransport.Action.PLAY_PAUSE),
        )
        assertEquals(
            KeyEvent.KEYCODE_MEDIA_NEXT,
            MediaTransport.keyCodeFor(MediaTransport.Action.NEXT),
        )
        assertEquals(
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            MediaTransport.keyCodeFor(MediaTransport.Action.PREVIOUS),
        )
    }

    @Test
    fun `no two actions share a key code`() {
        val codes = MediaTransport.Action.entries.map { MediaTransport.keyCodeFor(it) }
        assertEquals(codes.size, codes.toSet().size)
    }
}
