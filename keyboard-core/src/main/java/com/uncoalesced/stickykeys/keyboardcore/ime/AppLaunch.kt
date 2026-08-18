// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import android.content.Context
import android.content.Intent

/**
 * Which screen the app should open on when the keyboard launches it.
 *
 * A string extra rather than a deep-link URI: the app module's activity is not reachable as a
 * type from here (`app` depends on `keyboard-core`, never the other way round), and a `VIEW`
 * intent on a custom scheme would have to be declared in the manifest and would then be
 * launchable by anything on the device. The launcher intent plus an extra is the narrow version
 * of the same thing -- only a caller that already knows the package can produce it.
 */
const val EXTRA_INITIAL_ROUTE = "com.uncoalesced.stickykeys.INITIAL_ROUTE"

/** The nav route for the app's Keyboard tab, as [EXTRA_INITIAL_ROUTE] expects it. */
const val ROUTE_KEYBOARD_SETTINGS = "keyboard"

/**
 * Opens the app on [route] from inside the IME.
 *
 * `NEW_TASK` is mandatory: an `InputMethodService` is a Service, and starting an Activity from
 * one without it throws rather than doing nothing, so the keyboard would take the host app down
 * with it. The launcher intent is resolved through the package manager rather than named
 * directly for the module-dependency reason above; a device with the app somehow absent simply
 * gets nothing instead of a crash.
 */
fun openAppAt(
    context: Context,
    route: String,
) {
    val intent =
        context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    intent.putExtra(EXTRA_INITIAL_ROUTE, route)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
