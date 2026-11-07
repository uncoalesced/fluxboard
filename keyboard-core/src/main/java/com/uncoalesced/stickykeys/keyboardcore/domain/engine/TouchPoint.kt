// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

/**
 * A physical key touch event with 2D spatial coordinates normalized relative to key center.
 *
 * [normalizedX] and [normalizedY] represent the touch offset from the key's center in units of key dimensions:
 * - (0.0, 0.0) is the exact center of the key.
 * - [-0.5..0.5] spans the width/height of the key.
 * - Negative [normalizedX] is to the left; positive is to the right.
 * - Negative [normalizedY] is towards the top row; positive is towards the bottom row.
 */
data class TouchPoint(
    val char: Char,
    val normalizedX: Float = 0f,
    val normalizedY: Float = 0f,
    val timestamp: Long = 0L,
)
