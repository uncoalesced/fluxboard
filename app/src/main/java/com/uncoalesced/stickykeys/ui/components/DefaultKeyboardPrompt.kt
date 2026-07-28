// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.components

import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.uncoalesced.stickykeys.R
import com.uncoalesced.stickykeys.data.local.AppPreferences

/**
 * Decides whether the one-time "make FluxBoard your default" prompt is due.
 *
 * Pure so the rule is testable without a dialog. All four conditions must hold:
 * the keyboard is enabled (it is in the system IME list, which the setup card
 * establishes), it is not already the default (or there is nothing to ask), the
 * user has visited a customiser at least once, and the prompt has never been shown.
 */
internal fun shouldShowDefaultKeyboardPrompt(
    keyboardEnabled: Boolean,
    alreadyDefault: Boolean,
    visitedCustomizer: Boolean,
    alreadyShown: Boolean,
): Boolean = keyboardEnabled && !alreadyDefault && visitedCustomizer && !alreadyShown

/**
 * One-time nudge to select FluxBoard as the system keyboard.
 *
 * There is no API for an app to make itself the default IME -- deliberately, since that
 * would let any app silently intercept typing. Accepting opens the system keyboard picker
 * via [InputMethodManager.showInputMethodPicker], exactly as the setup card does; the user
 * still makes the choice there.
 *
 * "Shown" is persisted when the dialog appears rather than when it is accepted, so
 * declining does not leave it queued to ask again.
 */
@Composable
fun DefaultKeyboardPrompt(appPreferences: AppPreferences) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var due by remember {
        mutableStateOf(
            shouldShowDefaultKeyboardPrompt(
                keyboardEnabled = isImeEnabled(context),
                alreadyDefault = isImeDefault(context),
                visitedCustomizer = appPreferences.hasVisitedCustomizer,
                alreadyShown = appPreferences.hasShownDefaultKeyboardPrompt,
            ),
        )
    }

    // Re-evaluated on resume, so returning from the theme editor or the picker is picked up
    // without needing the screen to be recreated.
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    due =
                        shouldShowDefaultKeyboardPrompt(
                            keyboardEnabled = isImeEnabled(context),
                            alreadyDefault = isImeDefault(context),
                            visitedCustomizer = appPreferences.hasVisitedCustomizer,
                            alreadyShown = appPreferences.hasShownDefaultKeyboardPrompt,
                        )
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!due) return

    fun dismissForGood() {
        appPreferences.markDefaultKeyboardPromptShown()
        due = false
    }

    AlertDialog(
        onDismissRequest = { dismissForGood() },
        title = { Text(stringResource(R.string.text_set_default_keyboard_title)) },
        text = { Text(stringResource(R.string.text_set_default_keyboard_body)) },
        confirmButton = {
            Button(onClick = {
                dismissForGood()
                val imm =
                    context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }) {
                Text(stringResource(R.string.text_set_default_keyboard_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { dismissForGood() }) {
                Text(stringResource(R.string.text_not_now))
            }
        },
    )
}

private fun isImeEnabled(context: Context): Boolean {
    val imm =
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return false
    return imm.enabledInputMethodList.any { it.packageName == context.packageName }
}

private fun isImeDefault(context: Context): Boolean {
    val current =
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD,
        ) ?: return false
    return current.startsWith(context.packageName)
}
