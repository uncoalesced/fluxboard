// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Plain Kotlin data class for StickyKeys corner radius scale.
 *
 * Two radii, as the brand kit sets them: 4dp for small things (chips, tags) and 10dp for
 * anything that contains (cards, panels, sheets). [large] and [extraLarge] keep their names so
 * existing call sites compile, and both resolve to the medium radius.
 */
data class StickyKeysShapes(
    val small: Shape = RoundedCornerShape(4.dp),
    val medium: Shape = RoundedCornerShape(10.dp),
    val large: Shape = RoundedCornerShape(10.dp),
    val extraLarge: Shape = RoundedCornerShape(10.dp),
    val pill: Shape = RoundedCornerShape(50),
)

val defaultStickyKeysShapes = StickyKeysShapes()
