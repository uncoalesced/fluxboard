// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

/**
 * SmartKeys 2D Spatial Touch Calibration and Gaussian Hitbox Engine.
 *
 * Evaluates continuous 2D Euclidean and anisotropic Mahalanobis distance between physical
 * finger touch coordinates and keyboard key centers, modulating dynamic programming edit costs.
 */
internal object SmartKeyHitbox {
    /** Running centroid and variance exponential moving average decay factor. */
    const val ALPHA: Float = 0.95f

    const val MAX_SPATIAL_DISTANCE_CAP: Float = 1.20f
    const val SWEET_SPOT_RADIUS: Float = 0.25f
    const val SWEET_SPOT_MULTIPLIER: Float = 0.96f // 4.0 * 0.24

    const val BASE_PROXIMITY_INTERIOR: Float = 0.0694f
    const val BASE_PROXIMITY_INITIAL: Float = 0.0779f
    const val DISTANCE_WEIGHT_LENGTH: Float = 0.1524f

    const val DEFAULT_VAR: Float = 0.25f
    const val MIN_VAR: Float = 0.09f
    const val MAX_VAR: Float = 0.64f
    private const val BASE_STD: Float = 0.50f
    private const val MIN_SCALE: Float = 0.65f
    private const val MAX_SCALE: Float = 1.35f
    private const val MAX_RETARGET_DISTANCE: Float = 1.35f

    private const val MAGIC = 0x42544150 // "BTAP"
    private const val VERSION = 1
    private const val NUM_FLOATS = 52
    private const val PAYLOAD_BYTES = NUM_FLOATS * 4
    private const val TOTAL_FILE_SIZE = 4 + 4 + PAYLOAD_BYTES + 32
    private const val MAX_DRIFT_KEY_WIDTH = 0.35f

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

    /** Running anisotropic touch variances per key: char -> (varX, varY). */
    private val runningVariances = mutableMapOf<Char, Pair<Float, Float>>()

    private var lastCentroidsSnapshot: Map<Char, Pair<Float, Float>>? = null
    private var lastVariancesSnapshot: Map<Char, Pair<Float, Float>>? = null

    /**
     * Serialises file I/O. Deliberately not the object monitor that guards [runningCentroids]:
     * holding that across a disk write would stall every key press waiting on [getCentroid].
     */
    private val ioLock = Any()

    @Synchronized
    fun resetCentroids() {
        runningCentroids.clear()
        runningVariances.clear()
        lastCentroidsSnapshot = null
        lastVariancesSnapshot = null
    }

    @Synchronized
    fun getCentroid(char: Char): Pair<Float, Float> =
        runningCentroids[char.lowercaseChar()] ?: (0f to 0f)

    @Synchronized
    fun getVariance(char: Char): Pair<Float, Float> =
        runningVariances[char.lowercaseChar()] ?: (DEFAULT_VAR to DEFAULT_VAR)

    /** Captures a rollback snapshot of current learned centroids and variances. */
    @Synchronized
    fun saveSnapshot() {
        lastCentroidsSnapshot = HashMap(runningCentroids)
        lastVariancesSnapshot = HashMap(runningVariances)
    }

    /** Restores the most recent snapshot captured before adaptation, returning true if present. */
    @Synchronized
    fun rollbackLastAdaptation(): Boolean {
        val centroidsSnap = lastCentroidsSnapshot
        val variancesSnap = lastVariancesSnapshot
        if (centroidsSnap == null && variancesSnap == null) return false
        runningCentroids.clear()
        centroidsSnap?.let { runningCentroids.putAll(it) }
        runningVariances.clear()
        variancesSnap?.let { runningVariances.putAll(it) }
        lastCentroidsSnapshot = null
        lastVariancesSnapshot = null
        return true
    }

    /**
     * Adapts running key centroid and anisotropic variance with a valid typed touch.
     */
    @Synchronized
    fun adaptCentroid(
        char: Char,
        offsetX: Float,
        offsetY: Float,
    ) {
        val lower = char.lowercaseChar()
        if (lower !in nominalPositions) return
        // NaN passes coerceIn untouched and would then poison every distance for this key.
        if (!offsetX.isFinite() || !offsetY.isFinite()) return
        val current = runningCentroids[lower] ?: (0f to 0f)
        val dx = offsetX - current.first
        val dy = offsetY - current.second
        val newX =
            (ALPHA * current.first + (1f - ALPHA) * offsetX)
                .coerceIn(-MAX_DRIFT_KEY_WIDTH, MAX_DRIFT_KEY_WIDTH)
        val newY =
            (ALPHA * current.second + (1f - ALPHA) * offsetY)
                .coerceIn(-MAX_DRIFT_KEY_WIDTH, MAX_DRIFT_KEY_WIDTH)
        runningCentroids[lower] = (newX to newY)

        val curVar = runningVariances[lower] ?: (DEFAULT_VAR to DEFAULT_VAR)
        val newVarX =
            (ALPHA * curVar.first + (1f - ALPHA) * dx * dx).coerceIn(MIN_VAR, MAX_VAR)
        val newVarY =
            (ALPHA * curVar.second + (1f - ALPHA) * dy * dy).coerceIn(MIN_VAR, MAX_VAR)
        runningVariances[lower] = (newVarX to newVarY)
    }

    /**
     * Adapts a sequence of touch points for an accepted word.
     */
    @Synchronized
    fun adaptWord(touches: List<TouchPoint>) {
        saveSnapshot()
        for (tp in touches) {
            adaptCentroid(tp.char, tp.normalizedX, tp.normalizedY)
        }
    }

    /**
     * Adapts centroids and anisotropic variances against an accepted autocorrection target,
     * aligning adjacent transpositions so touches update the intended target keys.
     */
    @Synchronized
    fun adaptCorrectedWord(
        touches: List<TouchPoint>,
        targetWord: String,
    ) {
        saveSnapshot()
        if (touches.isEmpty() || touches.size != targetWord.length) return
        var i = 0
        while (i < touches.size) {
            val tCur = touches[i].char.lowercaseChar()
            val wCur = targetWord[i].lowercaseChar()
            if (i + 1 < touches.size &&
                tCur != wCur &&
                tCur == targetWord[i + 1].lowercaseChar() &&
                touches[i + 1].char.lowercaseChar() == wCur
            ) {
                adaptTouchToTarget(touches[i], targetWord[i + 1])
                adaptTouchToTarget(touches[i + 1], targetWord[i])
                i += 2
            } else {
                adaptTouchToTarget(touches[i], targetWord[i])
                i += 1
            }
        }
    }

    private fun adaptTouchToTarget(
        touch: TouchPoint,
        targetChar: Char,
    ) {
        val typedLower = touch.char.lowercaseChar()
        val targetLower = targetChar.lowercaseChar()
        val typedPos = nominalPositions[typedLower] ?: return
        val candPos = nominalPositions[targetLower] ?: return
        if (!touch.normalizedX.isFinite() || !touch.normalizedY.isFinite()) return

        val offsetX: Float
        val offsetY: Float
        if (typedLower == targetLower) {
            offsetX = touch.normalizedX
            offsetY = touch.normalizedY
        } else {
            val (meanXTyped, meanYTyped) = getCentroid(typedLower)
            val touchX = typedPos.x + (touch.normalizedX - meanXTyped)
            val touchY = typedPos.y + (touch.normalizedY - meanYTyped)
            offsetX = touchX - candPos.x
            offsetY = touchY - candPos.y
        }
        if (hypot(offsetX, offsetY) <= MAX_RETARGET_DISTANCE) {
            adaptCentroid(targetLower, offsetX, offsetY)
        }
    }

    /** Returns a 52-float snapshot (deltaX, deltaY for 'a'..'z') of learned tap centroids. */
    @Synchronized
    fun snapshot(): FloatArray {
        val out = FloatArray(NUM_FLOATS)
        for (i in 0 until 26) {
            val ch = ('a'.code + i).toChar()
            val pair = runningCentroids[ch] ?: continue
            out[i * 2] = pair.first
            out[i * 2 + 1] = pair.second
        }
        return out
    }

    /** Returns a 52-float snapshot (varX, varY for 'a'..'z') of learned tap variances. */
    @Synchronized
    fun varianceSnapshot(): FloatArray {
        val out = FloatArray(NUM_FLOATS)
        for (i in 0 until 26) {
            val ch = ('a'.code + i).toChar()
            val pair = runningVariances[ch] ?: continue
            out[i * 2] = pair.first
            out[i * 2 + 1] = pair.second
        }
        return out
    }

    /**
     * Restores learned tap variances from a 52-float array.
     * Unadapted `0f` pairs remain absent and fall back to [DEFAULT_VAR].
     */
    @Synchronized
    fun restoreVariances(data: FloatArray): Boolean {
        if (data.size != NUM_FLOATS) return false
        if (!data.all { it.isFinite() }) return false
        runningVariances.clear()
        for (i in 0 until 26) {
            val rawX = data[i * 2]
            val rawY = data[i * 2 + 1]
            if (rawX > 0f || rawY > 0f) {
                val vx = if (rawX > 0f) rawX.coerceIn(MIN_VAR, MAX_VAR) else DEFAULT_VAR
                val vy = if (rawY > 0f) rawY.coerceIn(MIN_VAR, MAX_VAR) else DEFAULT_VAR
                val ch = ('a'.code + i).toChar()
                runningVariances[ch] = vx to vy
            }
        }
        return true
    }

    /**
     * Restores learned tap centroids from a 52-float array.
     *
     * Rejected whole, leaving the current centroids untouched, if any value is non-finite:
     * NaN survives `coerceIn` because every comparison with it is false.
     */
    @Synchronized
    fun restore(data: FloatArray): Boolean {
        if (data.size != NUM_FLOATS) return false
        if (!data.all { it.isFinite() }) return false
        runningCentroids.clear()
        for (i in 0 until 26) {
            val dx = data[i * 2].coerceIn(-MAX_DRIFT_KEY_WIDTH, MAX_DRIFT_KEY_WIDTH)
            val dy = data[i * 2 + 1].coerceIn(-MAX_DRIFT_KEY_WIDTH, MAX_DRIFT_KEY_WIDTH)
            if (dx != 0f || dy != 0f) {
                val ch = ('a'.code + i).toChar()
                runningCentroids[ch] = dx to dy
            }
        }
        return true
    }

    /** Saves the learned tap centroids to [file] with magic header, version, and SHA-256 digest. */
    fun save(file: File) {
        val floats = snapshot()
        val buffer = ByteBuffer.allocate(TOTAL_FILE_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(MAGIC)
        buffer.putInt(VERSION)

        val payloadBytes = ByteArray(PAYLOAD_BYTES)
        val payloadBuf = ByteBuffer.wrap(payloadBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (f in floats) {
            payloadBuf.putFloat(f)
        }
        buffer.put(payloadBytes)

        val digest = MessageDigest.getInstance("SHA-256").digest(payloadBytes)
        buffer.put(digest)

        synchronized(ioLock) {
            writeFileAtomically(file, buffer.array())
        }
    }

    /** Loads and verifies the learned tap centroids from [file]. Forward-compatible across versions. */
    fun load(file: File): Boolean = synchronized(ioLock) { loadLocked(file) }

    /** Deletes the saved centroids under the I/O lock, so an in-flight save cannot resurrect them. */
    fun delete(file: File) {
        synchronized(ioLock) {
            if (file.exists()) file.delete()
            File(file.parentFile, "${file.name}.tmp").delete()
        }
    }

    private fun loadLocked(file: File): Boolean {
        if (!file.exists() || file.length() < TOTAL_FILE_SIZE.toLong()) return false
        return try {
            val bytes = FileInputStream(file).use { it.readBytes() }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (buffer.int != MAGIC) return false
            val version = buffer.int
            if (version < 1) return false

            val payloadLen = bytes.size - 8 - 32
            if (payloadLen < PAYLOAD_BYTES) return false
            val payloadBytes = ByteArray(payloadLen)
            buffer.get(payloadBytes)

            val expectedDigest = ByteArray(32)
            buffer.get(expectedDigest)
            val actualDigest = MessageDigest.getInstance("SHA-256").digest(payloadBytes)
            if (!actualDigest.contentEquals(expectedDigest)) return false

            val payloadBuf = ByteBuffer.wrap(payloadBytes).order(ByteOrder.LITTLE_ENDIAN)
            val floats = FloatArray(NUM_FLOATS)
            for (i in 0 until NUM_FLOATS) {
                floats[i] = payloadBuf.float
            }
            restore(floats)
        } catch (e: Exception) {
            false
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
     * Returns the anisotropic Mahalanobis distance between [touch] and [candidateChar],
     * scaling horizontal and vertical residuals by each key's learned spread. When
     * unadapted (`varX = varY = 0.25f`), this matches [euclideanDistance].
     */
    fun mahalanobisDistance(
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
        val (varX, varY) = getVariance(candLower)
        val scaleX = (sqrt(varX) / BASE_STD).coerceIn(MIN_SCALE, MAX_SCALE)
        val scaleY = (sqrt(varY) / BASE_STD).coerceIn(MIN_SCALE, MAX_SCALE)
        val normDx = dx / scaleX
        val normDy = dy / scaleY
        return sqrt(normDx * normDx + normDy * normDy)
    }

    /**
     * Returns the closest QWERTY neighbor key in the direction of [touch] within [maxDist],
     * or null when no adjacent key is closer than the cutoff.
     */
    fun nearestNeighborInDirection(
        typedChar: Char,
        touch: TouchPoint,
        maxDist: Float = 0.85f,
    ): Pair<Char, Float>? {
        if (!touch.normalizedX.isFinite() || !touch.normalizedY.isFinite()) return null
        val typedLower = typedChar.lowercaseChar()
        val typedPos = nominalPositions[typedLower] ?: return null
        val (meanX, meanY) = getCentroid(typedLower)
        val touchX = typedPos.x + (touch.normalizedX - meanX)
        val touchY = typedPos.y + (touch.normalizedY - meanY)

        var bestChar: Char? = null
        var bestDist = Float.MAX_VALUE
        for ((cand, pos) in nominalPositions) {
            if (cand == typedLower) continue
            val dist = hypot(touchX - pos.x, touchY - pos.y)
            if (dist < bestDist) {
                bestDist = dist
                bestChar = cand
            }
        }
        return if (bestChar != null && bestDist <= maxDist) {
            bestChar to bestDist
        } else {
            null
        }
    }

    /**
     * Reprojects [touch] from `touch.char` into the local coordinate frame of [resolvedChar],
     * clamped to `[-0.5f, 0.5f]`.
     */
    fun reprojectTouchOffset(
        touch: TouchPoint,
        resolvedChar: Char,
    ): Pair<Float, Float> {
        if (!touch.normalizedX.isFinite() || !touch.normalizedY.isFinite()) return 0f to 0f
        val typedLower = touch.char.lowercaseChar()
        val resolvedLower = resolvedChar.lowercaseChar()
        val typedPos = nominalPositions[typedLower]
        val resolvedPos = nominalPositions[resolvedLower]
        if (typedPos == null || resolvedPos == null) {
            return (
                touch.normalizedX.coerceIn(-0.5f, 0.5f) to
                    touch.normalizedY.coerceIn(-0.5f, 0.5f)
            )
        }
        val (meanX, meanY) = getCentroid(typedLower)
        val (resolvedMeanX, resolvedMeanY) = getCentroid(resolvedLower)
        val physX = typedPos.x + (touch.normalizedX - meanX)
        val physY = typedPos.y + (touch.normalizedY - meanY)
        val outX = (physX - resolvedPos.x + resolvedMeanX).coerceIn(-0.5f, 0.5f)
        val outY = (physY - resolvedPos.y + resolvedMeanY).coerceIn(-0.5f, 0.5f)
        return outX to outY
    }

    /**
     * Calibrated SmartKeys Spatial Cost with inner sweet-spot damping.
     */
    fun spatialCostFloat(
        touch: TouchPoint,
        candidateChar: Char,
        isInitialChar: Boolean = false,
    ): Float {
        val d = mahalanobisDistance(touch, candidateChar)
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
     * Maps continuous Mahalanobis distance into calibrated edit costs:
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

        val d = mahalanobisDistance(touch, candLower)
        return when {
            d <= 0.60f -> 1 // Extremely close to candidate key border
            d <= 1.10f -> defaultAdjacentCost // Adjacent key body
            d <= 1.35f -> 3 // Outer diagonal neighbour
            else -> defaultSubstituteCost
        }
    }
}
