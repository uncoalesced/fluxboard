// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import kotlin.math.hypot

/**
 * Continuous curve-to-polyline trajectory matcher for BloomType.
 *
 * Evaluates candidate words by computing the continuous spatial distance between
 * the resampled gesture trajectory and the ideal polyline of adapted key centroids.
 *
 * Operates without allocating objects during scoring.
 */
class BloomTrajectoryAligner {
    companion object {
        private const val MAX_ENDPOINT_DISTANCE = 2.2f
        private const val MAX_VERTEX_MISS_DISTANCE = 1.6f
        private const val INFLECTION_MATCH_DISTANCE = 0.85f
        private const val UNEXPLAINED_PIVOT_PENALTY = 2.0f
        private const val EXPLAINED_PIVOT_CREDIT = 0.6f
        private const val SIGMA_ENDPOINT = 0.55f
    }

    /**
     * Scores how well [word] matches [trajectory].
     * Returns a continuous score where **lower is better**, or null if geometrically incompatible.
     */
    fun scoreCandidate(
        trajectory: BloomTrajectory,
        word: String,
        keyCentroids: (Char) -> Pair<Float, Float>?,
        profile: BloomSpatialProfile,
    ): Float? {
        if (!trajectory.isUsable || word.isEmpty()) return null

        val wordLen = word.length
        val vx = FloatArray(wordLen)
        val vy = FloatArray(wordLen)
        var vertexCount = 0

        // 1. Build polyline vertices, collapsing adjacent duplicate letters
        for (i in 0 until wordLen) {
            val c = word[i]
            if (i > 0 && c == word[i - 1]) {
                // Doubled letter occupies the same vertex; zero additional travel
                continue
            }
            val nominal = keyCentroids(c) ?: return null
            val (adaptedX, adaptedY) = profile.getAdaptedCenter(c, nominal.first, nominal.second)
            vx[vertexCount] = adaptedX
            vy[vertexCount] = adaptedY
            vertexCount++
        }

        if (vertexCount == 0) return null

        val startX = trajectory.getX(0)
        val startY = trajectory.getY(0)
        val endX = trajectory.getX(trajectory.count - 1)
        val endY = trajectory.getY(trajectory.count - 1)

        // 2. Soft Gaussian endpoint priors with outer cutoff
        val startDist = hypot(startX - vx[0], startY - vy[0])
        if (startDist > MAX_ENDPOINT_DISTANCE) return null

        val lastV = vertexCount - 1
        val endDist = hypot(endX - vx[lastV], endY - vy[lastV])
        if (endDist > MAX_ENDPOINT_DISTANCE) return null

        val startCost = gaussianEndpointCost(startX, startY, vx[0], vy[0], SIGMA_ENDPOINT)
        val endCost = gaussianEndpointCost(endX, endY, vx[lastV], vy[lastV], SIGMA_ENDPOINT)

        // 3. Vertex satisfaction & monotonic temporal progression
        var lastMatchedSample = 0
        var vertexDistanceSum = 0f

        for (v in 0 until vertexCount) {
            val targetX = vx[v]
            val targetY = vy[v]

            var minDist = Float.MAX_VALUE
            var bestIdx = -1

            // Search stroke points at or ahead of last matched sample (with small backward tolerance)
            val searchStart = (lastMatchedSample - 3).coerceAtLeast(0)
            for (s in searchStart until trajectory.count) {
                val d = hypot(trajectory.getX(s) - targetX, trajectory.getY(s) - targetY)
                if (d < minDist) {
                    minDist = d
                    bestIdx = s
                }
            }

            // If a vertex was missed completely (even with generous corner cutting), reject word
            if (minDist > MAX_VERTEX_MISS_DISTANCE || bestIdx < 0) return null

            vertexDistanceSum += minDist
            lastMatchedSample = bestIdx
        }

        // 4. Point-to-segment continuous projection across the entire trajectory
        var polylineDistSum = 0f
        for (s in 0 until trajectory.count) {
            val px = trajectory.getX(s)
            val py = trajectory.getY(s)

            var minSegDistSq = Float.MAX_VALUE
            for (v in 0 until vertexCount - 1) {
                val dSq = distToSegmentSq(px, py, vx[v], vy[v], vx[v + 1], vy[v + 1])
                if (dSq < minSegDistSq) minSegDistSq = dSq
            }
            if (vertexCount == 1) {
                val dx = px - vx[0]
                val dy = py - vy[0]
                minSegDistSq = dx * dx + dy * dy
            }
            polylineDistSum += minSegDistSq
        }
        val avgPolylineDist = polylineDistSum / trajectory.count

        // 5. Inflection point & pivot alignment
        var pivotScoreDelta = 0f
        for (i in 0 until trajectory.inflections.size) {
            val inflIdx = trajectory.inflections[i]
            val inflWeight = trajectory.inflectionWeights[i]
            val ix = trajectory.getX(inflIdx)
            val iy = trajectory.getY(inflIdx)

            // Find closest vertex to this inflection
            var closestVertexDist = Float.MAX_VALUE
            for (v in 0 until vertexCount) {
                val d = hypot(ix - vx[v], iy - vy[v])
                if (d < closestVertexDist) closestVertexDist = d
            }

            if (closestVertexDist <= INFLECTION_MATCH_DISTANCE) {
                // Explained inflection corner
                pivotScoreDelta -= EXPLAINED_PIVOT_CREDIT * inflWeight
            } else if (closestVertexDist > 1.25f) {
                // Unexplained sharp turn away from the word's keys
                pivotScoreDelta += UNEXPLAINED_PIVOT_PENALTY * inflWeight
            }
        }

        val baseScore = startCost + endCost + (avgPolylineDist * 1.5f)
        return baseScore + (vertexDistanceSum * 0.8f) + pivotScoreDelta
    }

    /** Minimum squared distance from point (px, py) to line segment (x1, y1) -> (x2, y2). */
    private fun distToSegmentSq(
        px: Float,
        py: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
    ): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq < 0.0001f) {
            val dpx = px - x1
            val dpy = py - y1
            return dpx * dpx + dpy * dpy
        }

        val t = (((px - x1) * dx + (py - y1) * dy) / lenSq).coerceIn(0f, 1f)
        val projX = x1 + t * dx
        val projY = y1 + t * dy
        val ex = px - projX
        val ey = py - projY
        return ex * ex + ey * ey
    }
}
