// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import java.io.File

/**
 * The three binary stores that hold learned typing state outside Room.
 *
 * Deleting them lives here rather than only in [PredictionEngine] because "Clear learned data"
 * can run in a process where the prediction engine was never created. If the files survive a
 * clear, [BloomMemoryVault.reconcileAndSync] writes the cleared words back into Room the next
 * time the keyboard starts.
 */
internal object BloomStateFiles {
    const val GLIDE_PROFILE = "bloom_profile.bin"
    const val TAP_PROFILE = "bloom_tap_profile.bin"
    const val USER_MEMORY = "bloom_user_memory.bin"

    /** Deletes all three stores in [dir], each under its own I/O lock. */
    fun deleteAll(dir: File) {
        BloomSpatialProfile.deleteFile(File(dir, GLIDE_PROFILE))
        SmartKeyHitbox.delete(File(dir, TAP_PROFILE))
        BloomMemoryVault.delete(File(dir, USER_MEMORY))
    }
}
