// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * Resampled continuous gesture trajectory for BloomType.
 *
 * Uses a flat FloatArray [resampledPoints] holding alternating x, y coordinates
 * (length = count * 2) to eliminate object allocations during gesture processing and beam search.
 */
class BloomTrajectory(
    val resampledPoints: FloatArray,
    val count: Int,
    val totalLength: Float,
    val inflections: IntArray,
    val inflectionWeights: FloatArray,
) {
    inline fun getX(index: Int): Float = resampledPoints[index * 2]
    inline fun getY(index: Int): Float = resampledPoints[index * 2 + 1]

    val isUsable: Boolean get() = count >= 2 && totalLength > 0.1f
}

/**
 * Normalizes and resamples raw touch points into [targetPoints] equidistant points
 * along the gesture's arc-length.
 */
fun buildBloomTrajectory(
    rawX: FloatArray,
    rawY: FloatArray,
    count: Int,
    targetPoints: Int = 48,
): BloomTrajectory {
    if (count < 2) {
        val pts = FloatArray(targetPoints * 2)
        val x = if (count > 0) rawX[0] else 0f
        val y = if (count > 0) rawY[0] else 0f
        for (i in 0 until targetPoints) {
            pts[i * 2] = x
            pts[i * 2 + 1] = y
        }
        return BloomTrajectory(pts, targetPoints, 0f, intArrayOf(0), floatArrayOf(1f))
    }

    // 1. Calculate cumulative arc length
    val cumDist = FloatArray(count)
    cumDist[0] = 0f
    for (i in 1 until count) {
        val dx = rawX[i] - rawX[i - 1]
        val dy = rawY[i] - rawY[i - 1]
        cumDist[i] = cumDist[i - 1] + hypot(dx, dy)
    }

    val totalLength = cumDist[count - 1]
    val resampled = FloatArray(targetPoints * 2)

    if (totalLength <= 0.0001f) {
        for (i in 0 until targetPoints) {
            resampled[i * 2] = rawX[0]
            resampled[i * 2 + 1] = rawY[0]
        }
        return BloomTrajectory(resampled, targetPoints, 0f, intArrayOf(0), floatArrayOf(1f))
    }

    // 2. Resample at uniform arc-length intervals
    val step = totalLength / (targetPoints - 1)
    var currSegment = 0

    resampled[0] = rawX[0]
    resampled[1] = rawY[0]

    for (k in 1 until targetPoints - 1) {
        val targetDist = k * step
        while (currSegment < count - 2 && cumDist[currSegment + 1] < targetDist) {
            currSegment++
        }
        val segStart = cumDist[currSegment]
        val segEnd = cumDist[currSegment + 1]
        val segLen = segEnd - segStart
        val fraction = if (segLen > 0.00001f) ((targetDist - segStart) / segLen).coerceIn(0f, 1f) else 0f

        resampled[k * 2] = rawX[currSegment] + fraction * (rawX[currSegment + 1] - rawX[currSegment])
        resampled[k * 2 + 1] = rawY[currSegment] + fraction * (rawY[currSegment + 1] - rawY[currSegment])
    }

    resampled[(targetPoints - 1) * 2] = rawX[count - 1]
    resampled[(targetPoints - 1) * 2 + 1] = rawY[count - 1]

    // 3. Detect inflection points (curvature extrema and sharp turns)
    val inflList = mutableListOf<Int>()
    val inflWeights = mutableListOf<Float>()

    // Endpoints are always primary anchors
    inflList.add(0)
    inflWeights.add(1.0f)

    val angles = FloatArray(targetPoints)
    for (k in 1 until targetPoints - 1) {
        val inDx = resampled[k * 2] - resampled[(k - 1) * 2]
        val inDy = resampled[k * 2 + 1] - resampled[(k - 1) * 2 + 1]
        val outDx = resampled[(k + 1) * 2] - resampled[k * 2]
        val outDy = resampled[(k + 1) * 2 + 1] - resampled[k * 2 + 1]

        val inAngle = atan2(inDy, inDx)
        val outAngle = atan2(outDy, outDx)
        angles[k] = angleDelta(inAngle, outAngle)
    }

    // Identify local maxima of turning angle >= 0.45 radians (~25 degrees)
    for (k in 2 until targetPoints - 2) {
        val a = angles[k]
        if (a >= 0.45f && a >= angles[k - 1] && a >= angles[k + 1]) {
            val weight = (a / 1.57f).coerceIn(0.5f, 1.0f)
            inflList.add(k)
            inflWeights.add(weight)
        }
    }

    inflList.add(targetPoints - 1)
    inflWeights.add(1.0f)

    return BloomTrajectory(
        resampledPoints = resampled,
        count = targetPoints,
        totalLength = totalLength,
        inflections = inflList.toIntArray(),
        inflectionWeights = inflWeights.toFloatArray(),
    )
}

/** Smallest difference between two angles in radians [0, PI]. */
private fun angleDelta(a: Float, b: Float): Float {
    var diff = abs(a - b)
    while (diff > Math.PI * 2) diff -= (Math.PI * 2).toFloat()
    return min(diff, (Math.PI * 2).toFloat() - diff)
}

/**
 * Calculates continuous Gaussian prior cost for touch-down and lift-off endpoints.
 * Cost = distance^2 / (2 * sigma^2). Returns 0 when exact, smooth continuous penalty otherwise.
 */
fun gaussianEndpointCost(
    sampleX: Float,
    sampleY: Float,
    keyX: Float,
    keyY: Float,
    sigma: Float = 0.45f,
): Float {
    val dx = sampleX - keyX
    val dy = sampleY - keyY
    val distSq = dx * dx + dy * dy
    return distSq / (2f * sigma * sigma)
}
