package com.uncoalesced.stickykeys

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import com.uncoalesced.stickykeys.navigation.AppNavGraph
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var sharedImageUri by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        setContent {
            StickyKeysTheme {
                AppNavGraph(initialImageUri = sharedImageUri)
            }
        }
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
