// Engineered by uncoalesced
package com.uncoalesced.stickykeys

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import com.uncoalesced.stickykeys.navigation.AppNavGraph
import com.uncoalesced.stickykeys.stickercore.capture.ScreenshotObserver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var sharedImageUri by mutableStateOf<String?>(null)
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
            StickyKeysTheme {
                AppNavGraph(initialImageUri = sharedImageUri)
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
    }
}
