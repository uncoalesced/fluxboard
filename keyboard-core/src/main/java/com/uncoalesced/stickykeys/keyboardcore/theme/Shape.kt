// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Plain Kotlin data class for StickyKeys corner radius scale.
 */
data class StickyKeysShapes(
    val small: Shape = RoundedCornerShape(4.dp),
    val medium: Shape = RoundedCornerShape(8.dp),
    val large: Shape = RoundedCornerShape(16.dp),
    val extraLarge: Shape = RoundedCornerShape(24.dp),
    val pill: Shape = RoundedCornerShape(50)
)

val defaultStickyKeysShapes = StickyKeysShapes()
