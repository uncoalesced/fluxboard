// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.runtime.staticCompositionLocalOf
import com.uncoalesced.stickykeys.keyboardcore.diagnostics.UsageRecorder

/**
 * The usage recorder, for the composables that observe presses.
 *
 * Provided once by [StickyKeysIME] at the root of its view. Null everywhere else -- the theme
 * preview and the layout editor draw the same keys without a recorder -- and in practice inert
 * in release, where the recorder is a no-op whose `enabled` is false. Static because it never
 * changes for the life of the view, so reading it costs the key grid nothing.
 */
internal val LocalUsageRecorder = staticCompositionLocalOf<UsageRecorder?> { null }
