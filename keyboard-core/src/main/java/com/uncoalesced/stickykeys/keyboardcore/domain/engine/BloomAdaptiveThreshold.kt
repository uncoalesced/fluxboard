// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

/**
 * Adaptive autocorrection trigger threshold controller.
 *
 * Tracks user accept and revert signals and adjusts the trigger confidence offset
 * within bounded limits so repeated accepts gently relax confirmation while reverts
 * quickly tighten restraint.
 */
class BloomAdaptiveThreshold {
    private val lock = Any()
    private var acceptCount: Int = 0
    private var revertCount: Int = 0
    private var thresholdOffset: Float = 0f

    /** Records an accepted autocorrection, lowering the trigger threshold slightly. */
    fun recordAccept() {
        synchronized(lock) {
            acceptCount++
            thresholdOffset =
                (thresholdOffset + ACCEPT_STEP).coerceIn(MIN_OFFSET, MAX_OFFSET)
        }
    }

    /** Records a reverted autocorrection, raising the trigger threshold to increase restraint. */
    fun recordRevert() {
        synchronized(lock) {
            revertCount++
            thresholdOffset =
                (thresholdOffset + REVERT_STEP).coerceIn(MIN_OFFSET, MAX_OFFSET)
        }
    }

    /** Returns the current bounded threshold offset. */
    fun currentOffset(): Float = synchronized(lock) { thresholdOffset }

    /** Returns a consistent `(acceptCount, revertCount, thresholdOffset)` snapshot. */
    fun snapshot(): Triple<Int, Int, Float> =
        synchronized(lock) {
            Triple(acceptCount, revertCount, thresholdOffset)
        }

    /**
     * Restores threshold state from persisted values, rejecting non-finite offsets
     * and clamping counts and offset to valid bounds.
     */
    fun restore(
        accepts: Int,
        reverts: Int,
        offset: Float,
    ) {
        if (!offset.isFinite()) return
        synchronized(lock) {
            acceptCount = accepts.coerceAtLeast(0)
            revertCount = reverts.coerceAtLeast(0)
            thresholdOffset = offset.coerceIn(MIN_OFFSET, MAX_OFFSET)
        }
    }

    /** Resets counters and threshold offset back to baseline. */
    fun reset() {
        synchronized(lock) {
            acceptCount = 0
            revertCount = 0
            thresholdOffset = 0f
        }
    }

    companion object {
        const val MIN_OFFSET = -0.030f
        const val MAX_OFFSET = 0.060f
        const val ACCEPT_STEP = -0.002f
        const val REVERT_STEP = 0.010f
    }
}
