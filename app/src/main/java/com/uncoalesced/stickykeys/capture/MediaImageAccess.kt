// Engineered by uncoalesced
package com.uncoalesced.stickykeys.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Which gallery-read permission to ask for, and whether it has been given.
 *
 * The platform has spelled this three different ways across the versions this app supports,
 * and asking for the wrong one is silent rather than loud: a request for a permission that
 * does not exist on the running version is simply never granted, and the query it was meant to
 * enable then returns an empty cursor. That is exactly how "Extract Screenshot" came to do
 * nothing at all -- the query ran without any permission behind it, found no rows, and the
 * caller treated that as "no screenshots exist".
 *
 * Split out as pure functions of the SDK level so the mapping is assertable without a device
 * on each API level.
 */
object MediaImageAccess {
    /** The full-library permission for the running platform version. */
    private fun fullAccessPermission(sdkInt: Int): String =
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    /**
     * Everything worth requesting on this version.
     *
     * On API 34+ the user-selected permission is requested *alongside* the full one rather
     * than instead of it. That is what puts "Select photos" in the system dialog next to
     * "Allow all", so someone who does not want to hand over their whole library can still
     * use the feature by picking the one screenshot.
     */
    fun requiredPermissions(sdkInt: Int = Build.VERSION.SDK_INT): Array<String> =
        if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
        } else {
            arrayOf(fullAccessPermission(sdkInt))
        }

    /**
     * True when a MediaStore image query will return something.
     *
     * Partial access counts. With only [Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED]
     * the query still succeeds, it just sees the subset the user chose -- and if their latest
     * screenshot is in that subset the feature works exactly as intended. Treating partial
     * access as "denied" would send a user who deliberately granted the narrower option down
     * the failure path.
     */
    fun hasAccess(
        context: Context,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): Boolean =
        requiredPermissions(sdkInt).any { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        }

    /** True when the user chose "Select photos" rather than granting the whole library. */
    fun isPartialAccess(
        context: Context,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): Boolean {
        if (sdkInt < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        val full =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) ==
                PackageManager.PERMISSION_GRANTED
        val selected =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            ) == PackageManager.PERMISSION_GRANTED
        return !full && selected
    }
}
