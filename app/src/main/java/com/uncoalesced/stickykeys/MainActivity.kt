// Engineered by uncoalesced
package com.uncoalesced.stickykeys

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.uncoalesced.stickykeys.data.local.AppPreferences
import com.uncoalesced.stickykeys.data.local.ThemeMode
import com.uncoalesced.stickykeys.keyboardcore.haptics.HapticsManager
import com.uncoalesced.stickykeys.keyboardcore.haptics.ProvideHapticIndication
import com.uncoalesced.stickykeys.keyboardcore.ime.EXTRA_INITIAL_ROUTE
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import com.uncoalesced.stickykeys.navigation.AppNavGraph
import com.uncoalesced.stickykeys.stickercore.capture.ScreenshotObserver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @javax.inject.Inject
    lateinit var appPreferences: AppPreferences

    @javax.inject.Inject
    lateinit var hapticsManager: HapticsManager

    private var sharedImageUri by mutableStateOf<String?>(null)

    /**
     * A route the keyboard asked us to open on, consumed once.
     *
     * Nulled by the nav graph after it navigates, so a configuration change does not send the
     * user back to the Keyboard tab every time they rotate the phone.
     */
    private var initialRoute by mutableStateOf<String?>(null)
    private var screenshotObserver: ScreenshotObserver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        // Phase 9 primary path: a screenshot taken while this activity exists
        // (foreground or backgrounded) lands directly in the creation flow.
        val observer = ScreenshotObserver(applicationContext)
        screenshotObserver = observer
        observer.start()
        lifecycleScope.launch {
            observer.screenshots.collect { uri ->
                sharedImageUri = uri.toString()
            }
        }

        setContent {
            // The app used to call StickyKeysTheme with its default darkTheme = true, so the
            // light palette was unreachable no matter what the user or the OS wanted.
            val themeMode by appPreferences.themeMode.collectAsState()
            val darkTheme =
                when (themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            StickyKeysTheme(darkTheme = darkTheme) {
                // Every button in the app gets the same press haptic as a key, driven by the
                // same enable switch and strength slider. Installed once here rather than at
                // ninety call sites, so screens added later inherit it.
                ProvideHapticIndication(hapticsManager) {
                    AppNavGraph(
                        initialImageUri = sharedImageUri,
                        initialRoute = initialRoute,
                        onInitialRouteHandled = { initialRoute = null },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        screenshotObserver?.stop()
        screenshotObserver = null
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            uri?.let {
                sharedImageUri = it.toString()
            }
        }
        // Also read on onNewIntent, not only on first launch: the app is usually already in the
        // back stack by the time someone reaches for the keyboard's settings shortcut, so the
        // launcher intent is delivered here rather than to a fresh onCreate.
        intent?.getStringExtra(EXTRA_INITIAL_ROUTE)?.let { initialRoute = it }
    }
}
