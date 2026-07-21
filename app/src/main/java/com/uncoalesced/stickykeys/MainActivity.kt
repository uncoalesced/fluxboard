package com.uncoalesced.stickykeys

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.uncoalesced.stickykeys.navigation.AppNavGraph
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StickyKeysTheme {
                AppNavGraph()
            }
        }
    }
}
