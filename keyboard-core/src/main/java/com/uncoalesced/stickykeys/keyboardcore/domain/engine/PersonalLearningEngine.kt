// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.PersonalBigramDao
import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.PersonalDictionaryDao
import com.uncoalesced.stickykeys.keyboardcore.data.local.entity.PersonalBigramEntity
import com.uncoalesced.stickykeys.keyboardcore.data.local.entity.PersonalWordEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Phase 4 Coordinator: Continuous On-Device N-gram Personalization & Dynamic User Dictionary Learning.
 *
 * Implements the L1/L2 memory hierarchy:
 * - L1: Thread-safe, allocation-free in-memory caches ([BloomPersonalLexicon], [BloomPersonalBigramCache])
 *       providing 0ms latency in active typing, auto-correction, and suggestion paths.
 * - L2: Asynchronous Room database persistence for unigrams and bigrams with 30-day decay and storage caps.
 *
 * Enforces the Two-Strike Activation Rule, backspace undo rollbacks, and sensitive/incognito shields.
 */
class PersonalLearningEngine(
    private val personalDao: PersonalDictionaryDao,
    private val personalBigramDao: PersonalBigramDao? = null,
    val lexicon: BloomPersonalLexicon = BloomPersonalLexicon(),
    val bigramCache: BloomPersonalBigramCache = BloomPersonalBigramCache(),
    private val keyboardPreferences: KeyboardPreferences? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    /**
     * Secondary constructor for unit testing or when [PersonalBigramDao] is not supplied.
     */
    constructor(
        personalDao: PersonalDictionaryDao,
        lexicon: BloomPersonalLexicon = BloomPersonalLexicon(),
        bigramCache: BloomPersonalBigramCache = BloomPersonalBigramCache(),
    ) : this(
        personalDao = personalDao,
        personalBigramDao = null,
        lexicon = lexicon,
        bigramCache = bigramCache,
        keyboardPreferences = null,
    )

    @Volatile
    private var _isEnabled: Boolean = true

    /** Master toggle for personalized learning, wired to user settings. */
    var isEnabled: Boolean
        get() = keyboardPreferences?.personalizedLearningEnabled?.value ?: _isEnabled
        set(value) {
            _isEnabled = value
        }

    /**
     * Strict privacy gate: when true (passwords, PINs, IME_FLAG_NO_PERSONALIZED_LEARNING,
     * or private mode), all learning and caching are completely bypassed.
     */
    @Volatile
    var isIncognitoOrPassword: Boolean = false

    /**
     * Pre-warms in-memory L1 caches from the Room database on launch.
     * Runs asynchronously off the main thread when invoked via [prewarm],
     * or synchronously via [prewarmSync] when already inside a background initialization coroutine.
     */
    fun prewarm(): kotlinx.coroutines.Job =
        scope.launch {
            prewarmSync()
        }

    /**
     * Synchronous prewarm routine; avoids racing against fast teardowns or Room database closures.
     */
    fun prewarmSync() {
        try {
            // Preload unigrams
            personalDao.getAll(BloomPersonalLexicon.MAX_UNIGRAMS).forEach { entity ->
                lexicon.recordOccurrence(entity.word, entity.frequency)
            }
        } catch (e: Exception) {
            // In-memory operation degrades gracefully if DB read fails
        }

        try {
            // Preload bigrams
            personalBigramDao?.getAllRecent(2000)?.forEach { bigram ->
                bigramCache.recordTransition(
                    bigram.previousWord,
                    bigram.nextWord,
                    bigram.count,
                )
            }
        } catch (e: Exception) {
            // Graceful degradation
        }
    }

    /**
     * Learns a single unigram token.
     */
    fun onUnigramCommitted(committedWord: String) {
        if (!isEnabled || isIncognitoOrPassword) return
        val word = committedWord.trim()
        if (word.length < 2 ||
            word.any { it.isDigit() } ||
            word.any { it.isWhitespace() }
        ) {
            return
        }

        val newCount = lexicon.recordOccurrence(word)

        scope.launch {
            try {
                personalDao.insertOrUpdate(
                    PersonalWordEntity(
                        word = word.lowercase(),
                        frequency = newCount,
                        lastUsedTimestamp = System.currentTimeMillis(),
                    ),
                )
            } catch (e: Exception) {
            }
        }
    }

    /**
     * Learns a dynamic bigram transition between [previousWord] and [nextWord].
     */
    fun recordBigramTransition(
        previousWord: String,
        nextWord: String,
    ) {
        if (!isEnabled || isIncognitoOrPassword) return
        val prev = previousWord.trim()
        val next = nextWord.trim()
        if (prev.length < 2 ||
            prev.any { it.isDigit() } ||
            prev.any { it.isWhitespace() }
        ) {
            return
        }
        if (next.length < 2 ||
            next.any { it.isDigit() } ||
            next.any { it.isWhitespace() }
        ) {
            return
        }

        bigramCache.recordTransition(prev, next)

        scope.launch {
            try {
                val now = System.currentTimeMillis()
                val updated =
                    personalBigramDao?.incrementBigram(
                        prev = prev.lowercase(),
                        next = next.lowercase(),
                        timestamp = now,
                    ) ?: 0
                if (updated == 0) {
                    personalBigramDao?.upsertBigram(
                        PersonalBigramEntity(
                            previousWord = prev.lowercase(),
                            nextWord = next.lowercase(),
                            count = 1,
                            lastUsedTimestamp = now,
                        ),
                    )
                }
            } catch (e: Exception) {
            }
        }
    }

    /**
     * Learns a committed word and its preceding bigram transition.
     * Updates L1 in-memory caches instantly and dispatches L2 disk writes asynchronously.
     */
    fun onWordCommitted(
        previousWord: String?,
        committedWord: String,
    ) {
        onUnigramCommitted(committedWord)
        if (!previousWord.isNullOrBlank()) {
            recordBigramTransition(previousWord, committedWord)
        }
    }

    /**
     * Rolls back a learned word and bigram transition (e.g. after backspace or undo).
     */
    fun onWordReverted(
        previousWord: String?,
        revertedWord: String,
    ) {
        if (!isEnabled || isIncognitoOrPassword) return
        val word = revertedWord.trim()
        if (word.isEmpty()) return

        // Decrement in L1 lexicon
        val remaining = lexicon.decrementOrRemove(word)

        // Update L2 database
        scope.launch {
            try {
                if (remaining <= 0) {
                    personalDao.deleteWord(word.lowercase())
                } else {
                    personalDao.insertOrUpdate(
                        PersonalWordEntity(
                            word = word.lowercase(),
                            frequency = remaining,
                            lastUsedTimestamp = System.currentTimeMillis(),
                        ),
                    )
                }
            } catch (e: Exception) {
            }
        }

        // Rollback bigram transition
        if (!previousWord.isNullOrBlank()) {
            val prev = previousWord.trim()
            bigramCache.rollbackTransition(prev, word)
            scope.launch {
                try {
                    personalBigramDao?.deleteBigram(prev.lowercase(), word.lowercase())
                } catch (e: Exception) {
                }
            }
        }
    }

    /**
     * Stronger than standard commit: user explicitly undid a correction to keep [word].
     * Elevates [word] directly to the two-strike Inviolable Shield (count >= 2).
     */
    fun onWordKept(word: String) {
        if (!isEnabled || isIncognitoOrPassword) return
        val trimmed = word.trim()
        if (trimmed.length < 2 ||
            trimmed.any { it.isDigit() } ||
            trimmed.any { it.isWhitespace() }
        ) {
            return
        }

        val current = lexicon.countOf(trimmed)
        val newCount = maxOf(current + 1, BloomPersonalLexicon.SHIELD_STRIKE_THRESHOLD)
        lexicon.recordOccurrence(trimmed, newCount - current)

        scope.launch {
            try {
                personalDao.insertOrUpdate(
                    PersonalWordEntity(
                        word = trimmed.lowercase(),
                        frequency = newCount,
                        lastUsedTimestamp = System.currentTimeMillis(),
                    ),
                )
            } catch (e: Exception) {
            }
        }
    }

    /**
     * 30-Day Activity Decay and Capacity Pruning maintenance routine.
     * Decrements frequency of tokens unused in the last 30 days and removes expired entries.
     */
    fun runDecayMaintenance(): kotlinx.coroutines.Job {
        val cutoffTimestamp = System.currentTimeMillis() - THIRTY_DAYS_MS
        lexicon.decayAll(1)
        bigramCache.decayAll(1)
        lexicon.prune(BloomPersonalLexicon.MAX_UNIGRAMS)
        bigramCache.prune(BloomPersonalBigramCache.MAX_BIGRAMS)

        return scope.launch {
            try {
                personalDao.pruneOldEntries(cutoffTimestamp)
                personalBigramDao?.pruneOldEntries(cutoffTimestamp)
            } catch (e: Exception) {
            }
        }
    }

    /**
     * Synchronous maintenance routine for background initialization paths.
     */
    fun runDecayMaintenanceSync() {
        val cutoffTimestamp = System.currentTimeMillis() - THIRTY_DAYS_MS
        lexicon.decayAll(1)
        bigramCache.decayAll(1)
        lexicon.prune(BloomPersonalLexicon.MAX_UNIGRAMS)
        bigramCache.prune(BloomPersonalBigramCache.MAX_BIGRAMS)

        try {
            personalDao.pruneOldEntries(cutoffTimestamp)
            personalBigramDao?.pruneOldEntries(cutoffTimestamp)
        } catch (e: Exception) {
        }
    }

    /**
     * Purges all learned user data across L1 caches and L2 database tables.
     */
    fun clearAll() {
        lexicon.clear()
        bigramCache.clear()
        scope.launch {
            try {
                personalDao.clear()
                personalBigramDao?.clearAll()
            } catch (e: Exception) {
            }
        }
    }

    companion object {
        private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000
    }
}
