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
 * [dwellProfile] contains normalized dwell density (0f..2f) at each resampled point.
 */
class BloomTrajectory(
    val resampledPoints: FloatArray,
    val count: Int,
    val totalLength: Float,
    val inflections: IntArray,
    val inflectionWeights: FloatArray,
    val dwellProfile: FloatArray = FloatArray(count) { 1.0f },
    val pointWeights: FloatArray = FloatArray(count) { 1.0f },
) {
    inline fun getX(index: Int): Float = resampledPoints[index * 2]

    inline fun getY(index: Int): Float = resampledPoints[index * 2 + 1]

    val isUsable: Boolean get() = count >= 2 && totalLength > 0.1f
}

/**
 * Normalizes and resamples raw touch points into [targetPoints] equidistant points
 * along the gesture's arc-length. Computes continuous curvature and raw touch density.
 */
fun buildBloomTrajectory(
    rawX: FloatArray,
    rawY: FloatArray,
    count: Int,
    targetPoints: Int = 48,
    rawTimestamps: LongArray? = null,
): BloomTrajectory {
    if (count < 2) {
        val pts = FloatArray(targetPoints * 2)
        val x = if (count > 0) rawX[0] else 0f
        val y = if (count > 0) rawY[0] else 0f
        for (i in 0 until targetPoints) {
            pts[i * 2] = x
            pts[i * 2 + 1] = y
        }
        return BloomTrajectory(
            resampledPoints = pts,
            count = targetPoints,
            totalLength = 0f,
            inflections = intArrayOf(0),
            inflectionWeights = floatArrayOf(1f),
            dwellProfile = FloatArray(targetPoints) { 1.0f },
        )
    }

    // 1. Calculate cumulative arc length, filtering identical consecutive points
    val cumDist = FloatArray(count)
    cumDist[0] = 0f
    for (i in 1 until count) {
        val dx = rawX[i] - rawX[i - 1]
        val dy = rawY[i] - rawY[i - 1]
        cumDist[i] = cumDist[i - 1] + hypot(dx, dy)
    }

    val totalLength = cumDist[count - 1]
    val resampled = FloatArray(targetPoints * 2)
    val dwell = FloatArray(targetPoints)

    if (totalLength <= 0.0001f) {
        for (i in 0 until targetPoints) {
            resampled[i * 2] = rawX[0]
            resampled[i * 2 + 1] = rawY[0]
            dwell[i] = 1.0f
        }
        return BloomTrajectory(
            resampledPoints = resampled,
            count = targetPoints,
            totalLength = 0f,
            inflections = intArrayOf(0),
            inflectionWeights = floatArrayOf(1f),
            dwellProfile = dwell,
        )
    }

    // 2. Resample at uniform arc-length intervals and estimate local dwell density
    val step = totalLength / (targetPoints - 1)
    var currSegment = 0

    resampled[0] = rawX[0]
    resampled[1] = rawY[0]
    dwell[0] = 1.0f

    val window = step * 0.75f
    for (k in 1 until targetPoints - 1) {
        val targetDist = k * step
        while (currSegment < count - 2 && cumDist[currSegment + 1] < targetDist) {
            currSegment++
        }
        val segStart = cumDist[currSegment]
        val segEnd = cumDist[currSegment + 1]
        val segLen = segEnd - segStart
        val fraction =
            if (segLen > 0.00001f) {
                ((targetDist - segStart) / segLen).coerceIn(0f, 1f)
            } else {
                0f
            }

        resampled[k * 2] =
            rawX[currSegment] + fraction * (rawX[currSegment + 1] - rawX[currSegment])
        resampled[k * 2 + 1] =
            rawY[currSegment] + fraction * (rawY[currSegment + 1] - rawY[currSegment])

        // Measure raw point density around targetDist
        var rawCountNear = 0
        for (r in 0 until count) {
            if (abs(cumDist[r] - targetDist) <= window) {
                rawCountNear++
            }
        }
        val expectedPerStep = (count.toFloat() / targetPoints).coerceAtLeast(1f)
        dwell[k] = (rawCountNear / expectedPerStep).coerceIn(0.2f, 3.0f)
    }

    resampled[(targetPoints - 1) * 2] = rawX[count - 1]
    resampled[(targetPoints - 1) * 2 + 1] = rawY[count - 1]
    dwell[targetPoints - 1] = 1.0f

    // 3. Detect inflection points (curvature extrema, sharp turns, and dwell peaks)
    val inflList = mutableListOf<Int>()
    val inflWeights = mutableListOf<Float>()

    inflList.add(0)
    inflWeights.add(1.0f)

    val angles = FloatArray(targetPoints)
    val pointWeights = FloatArray(targetPoints) { 1.0f }
    val gamma = 0.45f
    for (k in 1 until targetPoints - 1) {
        val inDx = resampled[k * 2] - resampled[(k - 1) * 2]
        val inDy = resampled[k * 2 + 1] - resampled[(k - 1) * 2 + 1]
        val outDx = resampled[(k + 1) * 2] - resampled[k * 2]
        val outDy = resampled[(k + 1) * 2 + 1] - resampled[k * 2 + 1]

        val inAngle = atan2(inDy, inDx)
        val outAngle = atan2(outDy, outDx)
        val a = angleDelta(inAngle, outAngle)
        angles[k] = a
        val kappa = (a / 1.57f).coerceIn(0f, 2f)
        val dwellBoost = (dwell[k] / 1.5f).coerceIn(1.0f, 1.3f)
        pointWeights[k] = (1.0f + gamma * kappa * dwellBoost).coerceIn(1.0f, 2.0f)
    }

    // Curvature maxima >= 0.45 rad (~25 deg) or curvature >= 0.35 with high dwell (pause)
    for (k in 2 until targetPoints - 2) {
        val a = angles[k]
        val isAnglePeak = a >= 0.45f && a >= angles[k - 1] && a >= angles[k + 1]
        val isDwellCorner = a >= 0.35f && dwell[k] >= 1.5f

        if (isAnglePeak || isDwellCorner) {
            val baseWeight = (a / 1.57f).coerceIn(0.5f, 1.0f)
            val dwellFactor = (dwell[k] / 1.5f).coerceIn(1.0f, 1.4f)
            val weight = (baseWeight * dwellFactor).coerceAtMost(1.0f)
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
        dwellProfile = dwell,
        pointWeights = pointWeights,
    )
}

/** Smallest difference between two angles in radians [0, PI]. */
private fun angleDelta(
    a: Float,
    b: Float,
): Float {
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
