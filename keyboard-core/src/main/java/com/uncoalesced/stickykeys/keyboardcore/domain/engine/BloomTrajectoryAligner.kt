// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import kotlin.math.hypot
import kotlin.math.min

/**
 * Continuous Dynamic Time Warping (DTW) curve-to-polyline trajectory aligner for BloomType.
 *
 * Evaluates candidate words by computing the monotonic, continuous spatial alignment
 * between the resampled gesture trajectory and the ideal polyline of adapted key centroids.
 *
 * Guaranteed monotonic progression: avoids backwards word matching or letter scrambling.
 * Extracts the exact trajectory sample corresponding to each character for biomechanical learning.
 * Operates with 0 heap allocations during scoring via pre-allocated flat arrays.
 */
class BloomTrajectoryAligner {
    companion object {
        private const val MAX_TRAJECTORY_POINTS = 48
        private const val MAX_VERTICES = 32
        private const val MAX_ENDPOINT_DISTANCE = 2.2f
        private const val INFLECTION_MATCH_DISTANCE = 0.85f
        private const val UNEXPLAINED_PIVOT_PENALTY = 2.0f
        private const val EXPLAINED_PIVOT_CREDIT = 0.6f
        private const val SIGMA_ENDPOINT = 0.55f
        private const val INF_COST = 1e6f
    }

    private val lock = Any()
    private val vx = FloatArray(MAX_VERTICES)
    private val vy = FloatArray(MAX_VERTICES)
    private val vertexChars = CharArray(MAX_VERTICES)

    // Flat DP table: row = trajectory sample (0..47), col = vertex (0..M-1)
    private val dpTable = FloatArray(MAX_TRAJECTORY_POINTS * MAX_VERTICES)
    private val traceTable = ByteArray(MAX_TRAJECTORY_POINTS * MAX_VERTICES)

    /**
     * Scores how well [word] matches [trajectory].
     * Returns a continuous score where **lower is better**, or null if geometrically incompatible.
     */
    fun scoreCandidate(
        trajectory: BloomTrajectory,
        word: String,
        keyCentroids: (Char) -> Pair<Float, Float>?,
        profile: BloomSpatialProfile,
    ): Float? = alignAndScore(trajectory, word, keyCentroids, profile, null)

    /**
     * Continuous DTW alignment and scoring. Optionally populates [outSampleIndices] with
     * the best-matched trajectory sample index (0..47) for each character in [word].
     */
    fun alignAndScore(
        trajectory: BloomTrajectory,
        word: String,
        keyCentroids: (Char) -> Pair<Float, Float>?,
        profile: BloomSpatialProfile,
        outSampleIndices: IntArray? = null,
    ): Float? {
        if (!trajectory.isUsable || word.length < 2 || word.length >= MAX_VERTICES) return null

        val wordLen = word.length
        val tCount = trajectory.count.coerceAtMost(MAX_TRAJECTORY_POINTS)

        synchronized(lock) {
            // 1. Build polyline vertices
            var vertexCount = 0
            for (i in 0 until wordLen) {
                val c = word[i]
                if (i > 0 && c == word[i - 1]) {
                    // Doubled letter collapses into the same vertex
                    continue
                }
                val nominal = keyCentroids(c) ?: return null
                val adaptedX = profile.getAdaptedX(c, nominal.first)
                val adaptedY = profile.getAdaptedY(c, nominal.second)
                vx[vertexCount] = adaptedX
                vy[vertexCount] = adaptedY
                vertexChars[vertexCount] = c
                vertexCount++
            }

            if (vertexCount < 2) return null

            val startX = trajectory.getX(0)
            val startY = trajectory.getY(0)
            val endX = trajectory.getX(tCount - 1)
            val endY = trajectory.getY(tCount - 1)

            // 2. Endpoint constraints with Gaussian priors
            val startDist = hypot(startX - vx[0], startY - vy[0])
            if (startDist > MAX_ENDPOINT_DISTANCE) return null

            val lastV = vertexCount - 1
            val endDist = hypot(endX - vx[lastV], endY - vy[lastV])
            if (endDist > MAX_ENDPOINT_DISTANCE) return null

            val startCost = gaussianEndpointCost(startX, startY, vx[0], vy[0], SIGMA_ENDPOINT)
            val endCost = gaussianEndpointCost(endX, endY, vx[lastV], vy[lastV], SIGMA_ENDPOINT)

            // 3. Monotonic DTW Matrix Initialization
            val numCols = vertexCount
            for (i in 0 until tCount * numCols) {
                dpTable[i] = INF_COST
                traceTable[i] = 0
            }

            // Base cell (sample 0, vertex 0)
            dpTable[0] = startDist * 0.5f

            // 4. Dynamic Programming forward pass
            for (s in 1 until tCount) {
                val px = trajectory.getX(s)
                val py = trajectory.getY(s)
                val rowOffset = s * numCols
                val prevRowOffset = (s - 1) * numCols

                for (v in 0 until numCols) {
                    val vertexDist = hypot(px - vx[v], py - vy[v])
                    val localDist =
                        if (v == 0) {
                            vertexDist
                        } else {
                            val segDistSq =
                                distToSegmentSq(px, py, vx[v - 1], vy[v - 1], vx[v], vy[v])
                            min(vertexDist, kotlin.math.sqrt(segDistSq))
                        }

                    val pointWeight = trajectory.pointWeights.getOrElse(s) { 1.0f }
                    val weightedDist = localDist * pointWeight

                    // Candidate 1: Dwell (from s-1, v)
                    var bestPrev = dpTable[prevRowOffset + v] + (weightedDist * 0.85f)
                    var bestAction: Byte = 1

                    // Candidate 2: Advance from previous vertex (from s-1, v-1)
                    if (v > 0) {
                        val advCost = dpTable[prevRowOffset + (v - 1)] + weightedDist
                        if (advCost < bestPrev) {
                            bestPrev = advCost
                            bestAction = 2
                        }
                    }

                    // Candidate 3: Corner-cutting skip (from s-1, v-2)
                    if (v > 1) {
                        val skipDistSq =
                            distToSegmentSq(
                                vx[v - 1],
                                vy[v - 1],
                                vx[v - 2],
                                vy[v - 2],
                                vx[v],
                                vy[v],
                            )
                        val skipDist = kotlin.math.sqrt(skipDistSq)
                        val skipPenalty = skipDist * 1.25f
                        val skipCost = dpTable[prevRowOffset + (v - 2)] + weightedDist + skipPenalty
                        if (skipCost < bestPrev) {
                            bestPrev = skipCost
                            bestAction = 3
                        }
                    }

                    dpTable[rowOffset + v] = bestPrev
                    traceTable[rowOffset + v] = bestAction
                }
            }

            val finalDtwCost = dpTable[(tCount - 1) * numCols + lastV]
            if (finalDtwCost >= INF_COST / 2f) return null

            val normalizedDtwCost = finalDtwCost / tCount

            // 5. Backtracking to extract best sample index per vertex
            if (outSampleIndices != null) {
                var currS = tCount - 1
                var currV = lastV
                val vertexBestSample = IntArray(vertexCount) { tCount - 1 }

                while (currS > 0 && currV >= 0) {
                    vertexBestSample[currV] = currS
                    val action = traceTable[currS * numCols + currV].toInt()
                    when (action) {
                        1 -> currS--
                        2 -> {
                            currS--
                            currV--
                        }
                        3 -> {
                            currS--
                            currV -= 2
                        }
                        else -> currS--
                    }
                }
                vertexBestSample[0] = 0

                // Map collapsed vertices back to original characters
                var vIdx = 0
                for (w in 0 until wordLen) {
                    if (w > 0 && word[w] == word[w - 1]) {
                        outSampleIndices[w] = vertexBestSample[vIdx - 1]
                    } else {
                        outSampleIndices[w] = vertexBestSample[vIdx]
                        vIdx++
                    }
                }
            }

            // 6. Inflection & pivot matching
            var pivotDelta = 0f
            for (i in 0 until trajectory.inflections.size) {
                val inflIdx = trajectory.inflections[i]
                val inflWeight = trajectory.inflectionWeights[i]
                val ix = trajectory.getX(inflIdx)
                val iy = trajectory.getY(inflIdx)

                var closestDist = Float.MAX_VALUE
                for (v in 0 until vertexCount) {
                    val d = hypot(ix - vx[v], iy - vy[v])
                    if (d < closestDist) closestDist = d
                }

                if (closestDist <= INFLECTION_MATCH_DISTANCE) {
                    pivotDelta -= EXPLAINED_PIVOT_CREDIT * inflWeight
                } else if (closestDist > 1.35f) {
                    pivotDelta += UNEXPLAINED_PIVOT_PENALTY * inflWeight
                }
            }

            // 7. Doubled-letter dwell credit: doubled letter collapses onto same vertex with neutral cost
            val doubledCredit = 0f

            val totalScore =
                startCost + endCost + (normalizedDtwCost * 2.2f) + pivotDelta - doubledCredit
            return totalScore
        }
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
