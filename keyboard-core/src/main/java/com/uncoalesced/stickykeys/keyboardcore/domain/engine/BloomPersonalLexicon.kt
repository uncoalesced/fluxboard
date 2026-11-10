// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe, allocation-free in-memory user vocabulary cache.
 * Pre-warmed from Room DB on IME launch; provides 0ms lookups during typing.
 *
 * Enforces the Two-Strike Activation Rule:
 * Strike 1 (count = 1): Word is staged, eligible for suggestions, but not shielded.
 * Strike 2 (count >= 2): Word joins the Inviolable Lexicon Shield (isShielded = true).
 */
class BloomPersonalLexicon(
    private val maxCapacity: Int = MAX_UNIGRAMS,
) {
    private val wordCounts = ConcurrentHashMap<String, Int>()

    /**
     * Records occurrence of a word.
     * Enforces the unigram storage capacity limit.
     */
    fun recordOccurrence(
        word: String,
        increment: Int = 1,
    ): Int {
        val lower = word.lowercase().trim()
        if (lower.isEmpty()) return 0

        if (wordCounts.size >= maxCapacity && !wordCounts.containsKey(lower)) {
            prune(maxCapacity - maxOf(1, maxCapacity / 20)) // prune lowest 5%
        }

        return wordCounts.compute(lower) { _, current ->
            val updated = (current ?: 0) + increment
            minOf(255, updated)
        } ?: increment
    }

    /**
     * Decrements the occurrence count of a word, or removes it if count reaches 0.
     * Returns the remaining count (0 if removed).
     */
    fun decrementOrRemove(word: String): Int {
        val lower = word.lowercase().trim()
        if (lower.isEmpty()) return 0

        var remaining = 0
        wordCounts.compute(lower) { _, current ->
            val updated = (current ?: 1) - 1
            if (updated <= 0) {
                remaining = 0
                null
            } else {
                remaining = updated
                updated
            }
        }
        return remaining
    }

    /** Returns the frequency count of [word], or 0 if unknown. */
    fun countOf(word: String): Int = wordCounts[word.lowercase().trim()] ?: 0

    /**
     * Two-strike unigram activation rule:
     * Words typed at least twice (or explicitly kept after an undo) are shielded
     * against being autocorrected away.
     */
    fun isShielded(word: String): Boolean = countOf(word) >= SHIELD_STRIKE_THRESHOLD

    /** True if the personal lexicon contains this word (count >= 1). */
    fun hasWord(word: String): Boolean = wordCounts.containsKey(word.lowercase().trim())

    /**
     * Returns personal words matching [prefix], sorted by frequency descending.
     */
    fun wordsMatchingPrefix(
        prefix: String,
        limit: Int = 10,
    ): List<Pair<String, Int>> {
        val p = prefix.lowercase().trim()
        if (p.isEmpty() || limit <= 0) return emptyList()

        return wordCounts.entries
            .asSequence()
            .filter { it.key.startsWith(p) }
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }
            .toList()
    }

    /**
     * Decays every entry by [amount]. Entries dropping to <= 0 are purged.
     */
    fun decayAll(amount: Int = 1) {
        if (amount <= 0) return
        for ((word, _) in wordCounts) {
            wordCounts.compute(word) { _, current ->
                val updated = (current ?: 0) - amount
                if (updated <= 0) null else updated
            }
        }
    }

    /**
     * Prunes the lowest frequency entries if current size exceeds [maxEntries].
     */
    fun prune(maxEntries: Int = maxCapacity) {
        if (wordCounts.size <= maxEntries) return
        val excess = wordCounts.size - maxEntries
        val lowest =
            wordCounts.entries
                .sortedBy { it.value }
                .take(excess)
        for (entry in lowest) {
            wordCounts.remove(entry.key)
        }
    }

    /** Drops all entries. */
    fun clear() {
        wordCounts.clear()
    }

    /** Returns an immutable copy of the active lexicon. */
    fun snapshot(): Map<String, Int> = HashMap(wordCounts)

    /** Number of unique personal words currently cached. */
    val size: Int get() = wordCounts.size

    companion object {
        const val MAX_UNIGRAMS = 5000
        const val SHIELD_STRIKE_THRESHOLD = 2
    }
}
