// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

/**
 * In-memory short-term memory buffer for recently reverted autocorrect words.
 *
 * Prevents the frustrating autocorrect loop:
 * When a user types a word, it is autocorrected, and the user hits Backspace to revert it:
 * - The original word is recorded here.
 * - If the user hits Space again, autocorrect is strictly suppressed for this word.
 * - This operates synchronously in-memory (0ms latency, zero SQLite race conditions).
 * - Avoids poisoning the permanent personal dictionary with one-off typos that the user
 *   reverted just to edit a letter.
 */
class BloomRevertHistory(
    private val maxEntries: Int = 5,
) {
    private val revertedWords = LinkedHashSet<String>()
    private val lock = Any()

    /** Records that [word] was reverted by the user. */
    fun recordRevert(word: String) {
        val normalized = word.trim().lowercase()
        if (normalized.isBlank()) return
        synchronized(lock) {
            revertedWords.remove(normalized)
            if (revertedWords.size >= maxEntries) {
                val oldest = revertedWords.iterator().next()
                revertedWords.remove(oldest)
            }
            revertedWords.add(normalized)
        }
    }

    /** Returns true if [word] was recently reverted and should not be autocorrected. */
    fun isReverted(word: String): Boolean {
        val normalized = word.trim().lowercase()
        synchronized(lock) {
            return revertedWords.contains(normalized)
        }
    }

    /** Clears the revert history (e.g., when switching input fields). */
    fun clear() {
        synchronized(lock) {
            revertedWords.clear()
        }
    }
}
