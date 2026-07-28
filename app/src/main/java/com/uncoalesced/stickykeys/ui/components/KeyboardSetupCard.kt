// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.components

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Setup card for the two things a custom IME cannot do for itself: being enabled
 * in system settings, and being selected as the active keyboard. Both steps are
 * re-checked whenever the screen resumes, so the card reflects reality after the
 * user comes back from Settings, and disappears entirely once setup is complete.
 */
@Composable
fun KeyboardSetupCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var enabled by remember { mutableStateOf(isKeyboardEnabled(context)) }
    var active by remember { mutableStateOf(isKeyboardActive(context)) }

    // Re-check on resume: the user leaves for system settings and comes back.
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    enabled = isKeyboardEnabled(context)
                    active = isKeyboardActive(context)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (enabled && active) return

    Card(
        modifier = modifier.fillMaxWidth().padding(bottom = 16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Finish setting up FluxBoard", style = MaterialTheme.typography.titleMedium)

            if (!enabled) {
                Text(
                    "Step 1: turn FluxBoard on. This opens the system keyboard list -- " +
                        "find FluxBoard and switch it on, then come back here.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open keyboard settings")
                }
            } else {
                Text("Step 1: FluxBoard is enabled.", style = MaterialTheme.typography.bodyMedium)
            }

            Text(
                if (active) {
                    "Step 2: FluxBoard is your active keyboard."
                } else {
                    "Step 2: switch to it. This opens the keyboard picker -- choose FluxBoard."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!active) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            val imm =
                                context.getSystemService(Context.INPUT_METHOD_SERVICE)
                                    as InputMethodManager
                            imm.showInputMethodPicker()
                        },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Choose FluxBoard")
                    }
                }
            }
        }
    }
}

/** True when this app's IME appears in the system's enabled input-method list. */
private fun isKeyboardEnabled(context: Context): Boolean {
    val imm =
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return false
    return imm.enabledInputMethodList.any { it.packageName == context.packageName }
}

/** True when this app's IME is the one currently selected system-wide. */
private fun isKeyboardActive(context: Context): Boolean {
    val current =
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD,
        ) ?: return false
    return current.startsWith(context.packageName)
}
