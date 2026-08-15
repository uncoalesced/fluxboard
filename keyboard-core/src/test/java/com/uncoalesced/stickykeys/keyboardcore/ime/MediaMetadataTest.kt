// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.test.core.app.ApplicationProvider
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The media-metadata opt-in, and the boundary around what it buys.
 *
 * This feature reopens a permission the project refused on its merits: roadmap 3.13/B8 ruled
 * out `BIND_NOTIFICATION_LISTENER_SERVICE` because it grants the text of every notification on
 * the device, and this app's whole pitch is that it reads nothing. Reopening it means the
 * trade stays visible at every layer -- so what is tested here is the *constraints*, not the
 * feature.
 *
 * What cannot be tested here, and is flagged `NEEDS DEVICE` in the roadmap for the same reason
 * 4.1 is: whether a real grant against a real media session actually produces a title. A
 * Robolectric `MediaSessionManager` has no sessions and no listener binding, so a green run
 * here says the gates hold, not that the feature works.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaMetadataTest {
    private fun prefs() = KeyboardPreferences(ApplicationProvider.getApplicationContext())

    /** Off unless asked for. An update must never turn this on for anyone. */
    @Test
    fun `the opt-in is off by default`() {
        assertFalse(prefs().mediaMetadataEnabled.value)
    }

    /** Its own flag, so nothing else can enable it as a side effect. */
    @Test
    fun `the opt-in is independent of every other setting`() {
        val p = prefs()
        p.setGlideTyping(true)
        p.setAutoCorrect(true)
        p.setPrivateMode(false)
        assertFalse("no other setting may enable media metadata", p.mediaMetadataEnabled.value)

        p.setMediaMetadataEnabled(true)
        assertTrue(p.mediaMetadataEnabled.value)
        // And turning it off is immediate, like every other setter here.
        p.setMediaMetadataEnabled(false)
        assertFalse(p.mediaMetadataEnabled.value)
    }

    /**
     * Null when the listener is not bound, which is distinct from "nothing is playing".
     *
     * The reader must never call `getActiveSessions` without the binding: the platform throws
     * a SecurityException there, and an IME that crashes because a Settings toggle moved is
     * worse than one that shows no track.
     */
    @Test
    fun `no track is reported when the listener is not connected`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertFalse(MediaMetadataListenerService.connected)
        assertNull(MediaMetadataReader.currentTrack(context))
    }

    /**
     * The grant is read from the OS, not from our own preference.
     *
     * The switch records what the user asked for; only Settings.Secure says whether the grant
     * exists. A control that reported the app's wish rather than the system's answer would be
     * a fake, and this project has shipped one of those before.
     */
    @Test
    fun `the grant is read from the system, not from the preference`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        prefs().setMediaMetadataEnabled(true)
        assertFalse(
            "turning the preference on must not make the app believe it was granted",
            MediaMetadataReader.listenerGranted(context),
        )
    }
}
