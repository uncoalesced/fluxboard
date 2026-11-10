// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory L1 cache for dynamic user bigram transitions.
 * Provides 0ms lookups during active typing and scales contextual boosting
 * according to the formula:
 * personalBoost = min(255, 180 + count * 15) / 255.0f
 */
class BloomPersonalBigramCache(
    private val maxCapacity: Int = MAX_BIGRAMS,
) {
    // previousWord -> Map<nextWord, count>
    private val transitions = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()

    /**
     * Records a transition from [prev] to [next].
     * Increments the transition count and returns the updated count.
     */
    fun recordTransition(
        prev: String,
        next: String,
        increment: Int = 1,
    ): Int {
        val p = prev.lowercase().trim()
        val n = next.lowercase().trim()
        if (p.isEmpty() || n.isEmpty()) return 0

        if (totalTransitionsCount() >= maxCapacity) {
            prune(maxCapacity - maxOf(1, maxCapacity / 20)) // prune lowest 5%
        }

        val nextMap = transitions.computeIfAbsent(p) { ConcurrentHashMap() }
        return nextMap.compute(n) { _, count ->
            val updated = (count ?: 0) + increment
            minOf(255, updated)
        } ?: increment
    }

    /**
     * Rolls back a learned transition (e.g. after backspace or undo).
     * Returns remaining count (0 if removed).
     */
    fun rollbackTransition(
        prev: String,
        next: String,
    ): Int {
        val p = prev.lowercase().trim()
        val n = next.lowercase().trim()
        if (p.isEmpty() || n.isEmpty()) return 0

        val nextMap = transitions[p] ?: return 0
        var remaining = 0
        nextMap.compute(n) { _, count ->
            val updated = (count ?: 1) - 1
            if (updated <= 0) {
                remaining = 0
                null
            } else {
                remaining = updated
                updated
            }
        }
        if (nextMap.isEmpty()) {
            transitions.remove(p)
        }
        return remaining
    }

    /** Returns the count of times [next] was seen following [prev]. */
    fun countFor(
        prev: String,
        next: String,
    ): Int = transitions[prev.lowercase().trim()]?.get(next.lowercase().trim()) ?: 0

    /**
     * Returns a normalized boost in [0.0, 1.0] for candidate [next] following [prev].
     * Scaled personal bigram weight formula:
     * boost = min(255, 180 + count * 15) / 255.0f
     */
    fun boostFor(
        prev: String,
        next: String,
    ): Float {
        val count = countFor(prev, next)
        if (count <= 0) return 0f
        val scaled = minOf(255, 180 + (count * 15))
        return scaled / 255.0f
    }

    /**
     * Returns the recorded followers of [prev], sorted by frequency descending.
     */
    fun followersOf(
        prev: String,
        limit: Int = 8,
    ): List<Pair<String, Int>> {
        val nextMap = transitions[prev.lowercase().trim()] ?: return emptyList()
        if (limit <= 0) return emptyList()

        return nextMap.entries
            .asSequence()
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }
            .toList()
    }

    /** Decays all transition counts by [amount], purging those reaching <= 0. */
    fun decayAll(amount: Int = 1) {
        if (amount <= 0) return
        for ((prev, nextMap) in transitions) {
            for ((next, _) in nextMap) {
                nextMap.compute(next) { _, current ->
                    val updated = (current ?: 0) - amount
                    if (updated <= 0) null else updated
                }
            }
            if (nextMap.isEmpty()) {
                transitions.remove(prev)
            }
        }
    }

    /** Total count of individual bigram transition pairs across all previous words. */
    fun totalTransitionsCount(): Int = transitions.values.sumOf { it.size }

    /**
     * Prunes lowest-frequency transitions if total count exceeds [maxEntries].
     */
    fun prune(maxEntries: Int = maxCapacity) {
        val currentTotal = totalTransitionsCount()
        if (currentTotal <= maxEntries) return
        val excess = currentTotal - maxEntries

        // Find the lowest transitions
        val allEntries = mutableListOf<Triple<String, String, Int>>()
        for ((prev, nextMap) in transitions) {
            for ((next, count) in nextMap) {
                allEntries.add(Triple(prev, next, count))
            }
        }
        allEntries.sortBy { it.third }
        for (i in 0 until minOf(excess, allEntries.size)) {
            val (prev, next, _) = allEntries[i]
            val nextMap = transitions[prev]
            if (nextMap != null) {
                nextMap.remove(next)
                if (nextMap.isEmpty()) {
                    transitions.remove(prev)
                }
            }
        }
    }

    /** Drops all cached bigrams. */
    fun clear() {
        transitions.clear()
    }

    /** Returns an immutable snapshot copy of current transitions. */
    fun snapshot(): Map<String, Map<String, Int>> = transitions.mapValues { HashMap(it.value) }

    companion object {
        const val MAX_BIGRAMS = 10000
    }
}
