// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import android.service.notification.NotificationListenerService

/**
 * Exists only so the platform will answer `MediaSessionManager.getActiveSessions()`.
 *
 * Android has no permission for "read media metadata". The only key to the active media session
 * is `BIND_NOTIFICATION_LISTENER_SERVICE`, which is a far larger grant than the feature needs:
 * it hands the app the text of every notification on the device. This project refused that trade
 * outright for two releases -- see [MediaTransport], which is the permission-free path and stays
 * the default -- and reopened it only as an explicit, off-by-default opt-in.
 *
 * **This class is the scope boundary, and it is deliberately empty.** The base class calls
 * `onNotificationPosted` and `onNotificationRemoved` for every notification that appears on the
 * device whether or not they are overridden; not overriding them is what makes "FluxBoard never
 * reads a notification" a fact about the code rather than a promise about intent. The two
 * lifecycle hooks below are the entire implementation and neither is given a notification.
 *
 * `scripts/check-source-rules.sh` fails the build if any `onNotification*` override appears in
 * this file, so the boundary cannot be crossed quietly by a later edit -- the same shape as the
 * existing ban on preference-change listeners.
 */
class MediaMetadataListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        connected = false
    }

    companion object {
        /**
         * Whether the platform currently has this service bound.
         *
         * Read by [MediaMetadataReader] before it calls `getActiveSessions`, which throws a
         * `SecurityException` when the listener is not enabled. A user can revoke the grant in
         * Settings at any time and the app is not told beyond this callback.
         */
        @Volatile
        var connected: Boolean = false
            private set
    }
}
