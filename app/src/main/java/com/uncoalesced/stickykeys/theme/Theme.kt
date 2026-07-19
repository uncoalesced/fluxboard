package com.uncoalesced.stickykeys.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun StickyKeysTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Theme wrapper stub. To be fully implemented in Phase 3 with tokens.
    MaterialTheme(
        content = content
    )
}
