// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent

/**
 * Play, pause and skip for whatever is playing, without asking for anything.
 *
 * **Why this is not `MediaSessionManager`.** The obvious API for "what is playing" is
 * `MediaSessionManager.getActiveSessions()`, which hands back a `MediaController` per session
 * and with it `PlaybackState`, the track title, the album art and direct transport calls. It is
 * also gated behind `BIND_NOTIFICATION_LISTENER_SERVICE` -- a permission that grants the app
 * the text of every notification on the device, granted through a Settings screen with a
 * warning to match. For three buttons on a keyboard that is a wildly disproportionate ask, and
 * for a keyboard whose entire pitch is that it reads nothing, it is the wrong trade outright.
 * So this is the permission-free path, and the two consequences are stated rather than hidden:
 *
 *  - **No metadata, at all.** No title, no artist, no artwork. That is the scope, not a stub.
 *  - **[isPlaying] is a proxy, not the session's `PlaybackState`.** `AudioManager.isMusicActive`
 *    answers "is audio coming out of this device right now", which is close but not the same
 *    thing: a paused podcast reads false (correct here), and audio from something that is not a
 *    media session at all -- a video in a browser tab, a game -- also reads true. It picks a
 *    glyph. Nothing depends on it being right.
 *
 * The buttons themselves are exact regardless: [dispatch] hands the key to the same
 * `MediaSessionService` routing a hardware media button uses, so it reaches whichever session
 * the platform considers active, including one that is paused. That is what makes resuming
 * work when [isPlaying] says false.
 */
internal object MediaTransport {
    /**
     * The three transport actions, kept as an enum rather than raw key codes at the call site.
     *
     * `NEXT` and `PREVIOUS` are one digit apart in `KeyEvent` (87 and 88) and swapping them
     * produces a keyboard where the skip buttons work perfectly and point the wrong way --
     * which reads as correct in a screenshot and in a code review, and is obvious only to
     * somebody holding the phone. [keyCodeFor] is pure so that stays pinned by a test.
     */
    enum class Action { PLAY_PAUSE, NEXT, PREVIOUS }

    fun keyCodeFor(action: Action): Int =
        when (action) {
            Action.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            Action.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            Action.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        }

    /** True while the device is actually producing audio. See the class note -- it is a proxy. */
    fun isPlaying(context: Context): Boolean = audioManager(context)?.isMusicActive == true

    /**
     * Sends [action] to the active media session.
     *
     * Both halves of the press are sent. A lone ACTION_DOWN is a held button as far as the
     * receiver is concerned, and players that distinguish a tap from a hold -- a hold on
     * NEXT is seek-forward in most of them -- either do the wrong thing with it or wait for an
     * up event that never arrives and do nothing at all.
     */
    fun dispatch(
        context: Context,
        action: Action,
    ) {
        val manager = audioManager(context) ?: return
        val keyCode = keyCodeFor(action)
        // One timestamp for both events. The framework treats downTime as the identity of the
        // gesture, so a fresh clock read for the up event describes a different press.
        val now = SystemClock.uptimeMillis()
        manager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        manager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
    }

    private fun audioManager(context: Context): AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
}
