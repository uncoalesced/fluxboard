// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * On-device biomechanical learner for BloomType.
 *
 * Maintains a 26-key adaptive touch offset table (deltaX, deltaY per letter 'a'..'z'),
 * learning the user's personal thumb drift and swipe tendencies without transmitting any data.
 *
 * All operations use flat FloatArrays for 0ms lookup latency during typing.
 */
class BloomSpatialProfile {
    /** 26 letters * 2 coordinates (x, y) = 52 floats. */
    private val offsets = FloatArray(52)
    private val backupOffsets = FloatArray(52)
    private var hasBackup = false
    private val lock = Any()

    companion object {
        private const val MAGIC = 0x424C4F4D // "BLOM"
        private const val VERSION = 1
        private const val NUM_FLOATS = 52
        private const val PAYLOAD_BYTES = NUM_FLOATS * 4 // 208 bytes
        private const val TOTAL_FILE_SIZE = 4 + 4 + PAYLOAD_BYTES + 32 // 248 bytes

        private const val MAX_DRIFT_KEY_WIDTH = 0.35f
        private const val LEARNING_RATE = 0.05f
    }

    /** Returns the learned (deltaX, deltaY) offset in key widths for [char]. */
    fun getOffset(char: Char): Pair<Float, Float> {
        val idx = charToIndex(char) ?: return Pair(0f, 0f)
        synchronized(lock) {
            return Pair(offsets[idx], offsets[idx + 1])
        }
    }

    /** Allocation-free adapted X coordinate. */
    fun getAdaptedX(
        char: Char,
        nominalX: Float,
    ): Float {
        val idx = charToIndex(char) ?: return nominalX
        return nominalX + synchronized(lock) { offsets[idx] }
    }

    /** Allocation-free adapted Y coordinate. */
    fun getAdaptedY(
        char: Char,
        nominalY: Float,
    ): Float {
        val idx = charToIndex(char) ?: return nominalY
        return nominalY + synchronized(lock) { offsets[idx + 1] }
    }

    /** Returns the adapted key center (nominalX + deltaX, nominalY + deltaY). */
    fun getAdaptedCenter(
        char: Char,
        nominalX: Float,
        nominalY: Float,
    ): Pair<Float, Float> {
        val idx = charToIndex(char) ?: return Pair(nominalX, nominalY)
        synchronized(lock) {
            return Pair(nominalX + offsets[idx], nominalY + offsets[idx + 1])
        }
    }

    /**
     * Saves an in-memory backup of the current offsets before applying a new word learning pass.
     * Can be rolled back via [rollbackLastLearning] if the word is undone or an alternative is picked.
     */
    fun saveSnapshot() {
        synchronized(lock) {
            System.arraycopy(offsets, 0, backupOffsets, 0, NUM_FLOATS)
            hasBackup = true
        }
    }

    /**
     * Restores the offsets before the most recent learning pass.
     * Returns true if a snapshot was available and restored.
     */
    fun rollbackLastLearning(): Boolean {
        synchronized(lock) {
            if (!hasBackup) return false
            System.arraycopy(backupOffsets, 0, offsets, 0, NUM_FLOATS)
            hasBackup = false
            return true
        }
    }

    /** Returns a standalone copy of the current 52-float offsets array. */
    fun snapshot(): FloatArray {
        synchronized(lock) {
            return offsets.clone()
        }
    }

    /** Restores offsets from [data]. Must have size 52. */
    fun restore(data: FloatArray): Boolean {
        if (data.size != NUM_FLOATS) return false
        synchronized(lock) {
            System.arraycopy(data, 0, offsets, 0, NUM_FLOATS)
        }
        return true
    }

    /**
     * Updates the learned centroid for [char] based on an accepted gesture stroke.
     * Strictly ignored when [isSensitive] is true (passwords, PINs, incognito fields).
     */
    fun learnObservation(
        char: Char,
        nominalX: Float,
        nominalY: Float,
        observedX: Float,
        observedY: Float,
        isSensitive: Boolean,
    ) {
        if (isSensitive) return
        val idx = charToIndex(char) ?: return

        val rawDx = observedX - nominalX
        val rawDy = observedY - nominalY
        val observedDx = rawDx.coerceIn(-MAX_DRIFT_KEY_WIDTH * 1.5f, MAX_DRIFT_KEY_WIDTH * 1.5f)
        val observedDy = rawDy.coerceIn(-MAX_DRIFT_KEY_WIDTH * 1.5f, MAX_DRIFT_KEY_WIDTH * 1.5f)

        synchronized(lock) {
            val currDx = offsets[idx]
            val currDy = offsets[idx + 1]

            val newDx = (1f - LEARNING_RATE) * currDx + LEARNING_RATE * observedDx
            offsets[idx] = newDx.coerceIn(-MAX_DRIFT_KEY_WIDTH, MAX_DRIFT_KEY_WIDTH)

            val newDy = (1f - LEARNING_RATE) * currDy + LEARNING_RATE * observedDy
            offsets[idx + 1] = newDy.coerceIn(-MAX_DRIFT_KEY_WIDTH, MAX_DRIFT_KEY_WIDTH)
        }
    }

    /** Saves the profile to [file] with a magic header, version, and SHA-256 checksum. */
    fun save(file: File) {
        val buffer = ByteBuffer.allocate(TOTAL_FILE_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(MAGIC)
        buffer.putInt(VERSION)

        val payloadBytes = ByteArray(PAYLOAD_BYTES)
        val payloadBuf = ByteBuffer.wrap(payloadBytes).order(ByteOrder.LITTLE_ENDIAN)
        synchronized(lock) {
            for (f in offsets) {
                payloadBuf.putFloat(f)
            }
        }
        buffer.put(payloadBytes)

        val digest = MessageDigest.getInstance("SHA-256").digest(payloadBytes)
        buffer.put(digest)

        file.parentFile?.mkdirs()
        FileOutputStream(file).use { it.write(buffer.array()) }
    }

    /** Loads and verifies the profile from [file]. Returns true on success, false if invalid. */
    fun load(file: File): Boolean {
        if (!file.exists() || file.length() != TOTAL_FILE_SIZE.toLong()) return false

        try {
            val bytes = FileInputStream(file).use { it.readBytes() }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            val magic = buffer.int
            if (magic != MAGIC) return false

            val version = buffer.int
            if (version != VERSION) return false

            val payloadBytes = ByteArray(PAYLOAD_BYTES)
            buffer.get(payloadBytes)

            val expectedDigest = ByteArray(32)
            buffer.get(expectedDigest)

            val actualDigest = MessageDigest.getInstance("SHA-256").digest(payloadBytes)
            if (!actualDigest.contentEquals(expectedDigest)) return false

            val payloadBuf = ByteBuffer.wrap(payloadBytes).order(ByteOrder.LITTLE_ENDIAN)
            synchronized(lock) {
                for (i in 0 until NUM_FLOATS) {
                    offsets[i] = payloadBuf.float
                }
            }
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /** Clears all learned offsets back to factory neutral (0, 0). */
    fun reset() {
        synchronized(lock) {
            offsets.fill(0f)
            backupOffsets.fill(0f)
            hasBackup = false
        }
    }

    private fun charToIndex(char: Char): Int? {
        val c = char.lowercaseChar()
        if (c !in 'a'..'z') return null
        return (c - 'a') * 2
    }
}
