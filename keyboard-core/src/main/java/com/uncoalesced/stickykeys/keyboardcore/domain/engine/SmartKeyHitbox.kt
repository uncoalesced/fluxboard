// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import kotlin.math.min
import kotlin.math.sqrt

/**
 * SmartKeys 2D Spatial Touch Calibration and Gaussian Hitbox Engine.
 *
 * Re-engineered from libjni_latinime.so (sub_0xe5ba4) and Delight5 binary disassembly:
 * Evaluates continuous 2D Euclidean distance and Gaussian spatial probability between physical
 * finger touch coordinates and keyboard key centers, modulating dynamic programming edit costs.
 */
internal object SmartKeyHitbox {
    /** Running centroid exponential moving average decay factor (Delight5: alpha = 0.95). */
    const val ALPHA: Float = 0.95f

    const val MAX_SPATIAL_DISTANCE_CAP: Float = 1.20f
    const val SWEET_SPOT_RADIUS: Float = 0.25f
    const val SWEET_SPOT_MULTIPLIER: Float = 0.96f // 4.0 * 0.24

    const val BASE_PROXIMITY_INTERIOR: Float = 0.0694f
    const val BASE_PROXIMITY_INITIAL: Float = 0.0779f
    const val DISTANCE_WEIGHT_LENGTH: Float = 0.1524f

    private val ROWS =
        listOf(
            "qwertyuiop",
            "asdfghjkl",
            "zxcvbnm",
        )

    private val ROW_OFFSET = listOf(0f, 0.25f, 0.75f)

    data class KeyPos(
        val x: Float,
        val y: Float,
    )

    private val nominalPositions: Map<Char, KeyPos> =
        buildMap {
            ROWS.forEachIndexed { rowIndex, row ->
                row.forEachIndexed { colIndex, char ->
                    put(char, KeyPos(colIndex + ROW_OFFSET[rowIndex], rowIndex.toFloat()))
                }
            }
        }

    /** Running touch offset centroids per key: char -> (meanOffsetX, meanOffsetY). */
    private val runningCentroids = mutableMapOf<Char, Pair<Float, Float>>()

    @Synchronized
    fun resetCentroids() {
        runningCentroids.clear()
    }

    @Synchronized
    fun getCentroid(char: Char): Pair<Float, Float> =
        runningCentroids[char.lowercaseChar()] ?: (0f to 0f)

    /**
     * Adapts running key centroid with a valid typed touch:
     * \mu_{k}^{(n)} = \alpha \cdot \mu_{k}^{(n-1)} + (1 - \alpha) \cdot \mathbf{x}_{\text{touch}}
     */
    @Synchronized
    fun adaptCentroid(
        char: Char,
        offsetX: Float,
        offsetY: Float,
    ) {
        val lower = char.lowercaseChar()
        if (lower !in nominalPositions) return
        val current = runningCentroids[lower] ?: (0f to 0f)
        val newX = ALPHA * current.first + (1f - ALPHA) * offsetX
        val newY = ALPHA * current.second + (1f - ALPHA) * offsetY
        runningCentroids[lower] = (newX to newY)
    }

    /**
     * Adapts a sequence of touch points for an accepted word.
     */
    fun adaptWord(touches: List<TouchPoint>) {
        for (tp in touches) {
            adaptCentroid(tp.char, tp.normalizedX, tp.normalizedY)
        }
    }

    /**
     * Returns the physical 2D Euclidean distance from the touch point to candidate character [candidateChar]
     * in units of key width.
     */
    fun euclideanDistance(
        touch: TouchPoint,
        candidateChar: Char,
    ): Float {
        val typedLower = touch.char.lowercaseChar()
        val candLower = candidateChar.lowercaseChar()

        val typedPos = nominalPositions[typedLower] ?: return Float.MAX_VALUE
        val candPos = nominalPositions[candLower] ?: return Float.MAX_VALUE

        val (meanX, meanY) = getCentroid(typedLower)
        val touchX = typedPos.x + (touch.normalizedX - meanX)
        val touchY = typedPos.y + (touch.normalizedY - meanY)

        val dx = touchX - candPos.x
        val dy = touchY - candPos.y
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Ground-Truth Gboard Spatial Cost (from sub_0xe5ba4 in libjni_latinime.so).
     */
    fun spatialCostFloat(
        touch: TouchPoint,
        candidateChar: Char,
        isInitialChar: Boolean = false,
    ): Float {
        val d = euclideanDistance(touch, candidateChar)
        val fd =
            if (d <= SWEET_SPOT_RADIUS) {
                SWEET_SPOT_MULTIPLIER * d
            } else {
                min(d, MAX_SPATIAL_DISTANCE_CAP)
            }
        val base = if (isInitialChar) BASE_PROXIMITY_INITIAL else BASE_PROXIMITY_INTERIOR
        return base + fd * DISTANCE_WEIGHT_LENGTH
    }

    /**
     * Discrete integer edit cost for Bloom DP trie traversal and Levenshtein alignment.
     * Maps continuous Euclidean distance into calibrated edit costs:
     * - Direct hit on same char: 0
     * - Near-center hit on adjacent char (d <= 0.60): 1 or 2
     * - Outer edge adjacent (0.60 < d <= 1.10): defaultAdjacentCost (2)
     * - Diagonal adjacent (1.10 < d <= 1.35): 3
     * - Non-adjacent (d > 1.35): defaultSubstituteCost (5)
     */
    fun substitutionCost(
        touch: TouchPoint,
        candidateChar: Char,
        defaultAdjacentCost: Int = 2,
        defaultSubstituteCost: Int = 5,
    ): Int {
        val typedLower = touch.char.lowercaseChar()
        val candLower = candidateChar.lowercaseChar()
        if (typedLower == candLower) return 0

        val d = euclideanDistance(touch, candLower)
        return when {
            d <= 0.60f -> 1 // Extremely close to candidate key border
            d <= 1.10f -> defaultAdjacentCost // Adjacent key body
            d <= 1.35f -> 3 // Outer diagonal neighbour
            else -> defaultSubstituteCost
        }
    }
}
