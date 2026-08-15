// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.provider.Settings

/**
 * What is playing: three fields, and nothing else escapes.
 *
 * The type is the scope. `MediaSessionManager.getActiveSessions` hands back a `MediaController`
 * per session, and through it the full `MediaMetadata`, the `PlaybackState`, the queue, and the
 * ability to send transport commands. Returning any of those from here would make "FluxBoard
 * only reads the track title" a claim that depends on every future caller behaving; returning
 * [NowPlaying] makes it a property the compiler holds.
 */
data class NowPlaying(
    val title: String?,
    val artist: String?,
    val artwork: Bitmap?,
)

/**
 * Reads the current track, if and only if the user granted the listener.
 *
 * Parallel to [MediaTransport] rather than part of it. Transport works with no permission at
 * all and must keep working exactly as it does for a user who declines this, so the two are
 * separate objects with separate requirements instead of one object with a degraded mode.
 *
 * Nothing here is stored. [currentTrack] is read fresh each time the toolbar opens, the same way
 * `MediaRow` already re-reads whether audio is playing, and no track information is written to
 * preferences, to Room, or to a file. There is no history, no recently-played, and no scrobbling
 * -- the permission being granted is not a licence to keep what it exposes.
 */
object MediaMetadataReader {
    /**
     * The current track, or null when there is nothing to report.
     *
     * Null covers three different situations on purpose, because a caller can do nothing useful
     * to tell them apart: the listener is not enabled, it is enabled but not yet bound, or
     * nothing is playing. In all three the row falls back to the icons it has always shown.
     */
    fun currentTrack(context: Context): NowPlaying? {
        if (!MediaMetadataListenerService.connected) return null
        return try {
            val manager =
                context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                    ?: return null
            val component = ComponentName(context, MediaMetadataListenerService::class.java)
            // Throws SecurityException if the grant was revoked between the connected flag
            // being set and this call. Treated as "nothing playing" rather than propagated:
            // a keyboard must not crash because a Settings toggle moved.
            val sessions = manager.getActiveSessions(component)
            val metadata =
                sessions.firstNotNullOfOrNull { it.metadata } ?: return null
            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            val artist =
                metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            if (title.isNullOrBlank() && artist.isNullOrBlank()) return null
            NowPlaying(
                title = title?.takeIf { it.isNotBlank() },
                artist = artist?.takeIf { it.isNotBlank() },
                artwork =
                    metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                        ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART),
            )
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Whether the user has actually granted the listener, according to the OS.
     *
     * Read from `Settings.Secure` rather than trusted from our own preference. The in-app switch
     * records an intent; only this says whether the grant exists, and the user can withdraw it
     * from system Settings without the app being told. A switch that reported the app's wish
     * rather than the system's answer would be a fake control.
     */
    fun listenerGranted(context: Context): Boolean {
        val enabled =
            Settings.Secure.getString(
                context.contentResolver,
                ENABLED_NOTIFICATION_LISTENERS,
            ) ?: return false
        val us = ComponentName(context, MediaMetadataListenerService::class.java)
        return enabled
            .split(":")
            .any { ComponentName.unflattenFromString(it) == us }
    }

    /** Not a public constant on `Settings.Secure`, though the value is long-standing. */
    private const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"
}
