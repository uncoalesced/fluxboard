// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import android.content.Context
import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.PersonalDictionaryDao
import com.uncoalesced.stickykeys.keyboardcore.data.local.entity.PersonalWordEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

data class Suggestion(val word: String, val score: Float)

@Singleton
class PredictionEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val personalDao: PersonalDictionaryDao
) {
    private var buffer: MappedByteBuffer? = null
    private val PERSONAL_WEIGHT = 5f // personal usage carries high weight
    private val BASE_WEIGHT = 1f
    private val MAX_SUGGESTIONS = 3
    
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (buffer != null) return@withContext
        try {
            val dictFile = File(context.cacheDir, "base_dict.bin")
            if (!dictFile.exists()) {
                context.assets.open("base_dict.bin").use { inputStream ->
                    dictFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
            
            val randomAccessFile = RandomAccessFile(dictFile, "r")
            val channel = randomAccessFile.channel
            buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
            
            // Check Magic Header 'FLCT'
            buffer?.position(0)
            val b1 = buffer?.get()?.toInt()?.toChar()
            val b2 = buffer?.get()?.toInt()?.toChar()
            val b3 = buffer?.get()?.toInt()?.toChar()
            val b4 = buffer?.get()?.toInt()?.toChar()
            
            if ("$b1$b2$b3$b4" != "FLCT") {
                buffer = null // Invalid format
            }
        } catch (e: Exception) {
            e.printStackTrace()
            buffer = null
        }
    }

    suspend fun learnWord(word: String) = withContext(Dispatchers.IO) {
        if (word.isBlank() || word.length > 30) return@withContext
        val normalized = word.lowercase().trim()
        
        val existing = personalDao.getWord(normalized)
        val now = System.currentTimeMillis()
        
        if (existing != null) {
            val daysSince = (now - existing.lastUsedTimestamp) / (1000 * 60 * 60 * 24)
            var newFreq = existing.frequency
            
            // Decay older words
            if (daysSince > 7) {
                newFreq /= 2
            }
            
            newFreq = (newFreq + 1).coerceAtMost(255)
            personalDao.insertOrUpdate(existing.copy(frequency = newFreq, lastUsedTimestamp = now))
        } else {
            personalDao.insertOrUpdate(PersonalWordEntity(normalized, 1, now))
        }
    }

    suspend fun getSuggestions(prefix: String): List<String> = withContext(Dispatchers.IO) {
        val normalized = prefix.lowercase().trim()
        if (normalized.isEmpty()) return@withContext emptyList()
        
        // 1. Get from Personal Dictionary. Two-strike rule: a word only counts as
        // "personal" once it has been used at least twice, so one-off typos that
        // were learned in passing never outrank the base dictionary.
        val personalSuggestions = personalDao.getSuggestionsForPrefix(normalized, 5)
            .filter { it.frequency >= 2 }
            .map { Suggestion(it.word, it.frequency * PERSONAL_WEIGHT) }
            
        // 2. Get from Base Flictionary
        val baseSuggestions = getBaseSuggestions(normalized, 10)
            .map { Suggestion(it.word, it.score * BASE_WEIGHT) }
            
        // 3. Merge & Sort
        val merged = mutableMapOf<String, Float>()
        for (s in baseSuggestions) {
            merged[s.word] = s.score
        }
        for (s in personalSuggestions) {
            merged[s.word] = (merged[s.word] ?: 0f) + s.score
        }
        
        return@withContext merged.entries
            .sortedByDescending { it.value }
            .take(MAX_SUGGESTIONS)
            .map { it.key }
    }
    
    private fun getBaseSuggestions(prefix: String, limit: Int): List<Suggestion> {
        val buf = buffer ?: return emptyList()
        var currentOffset = 4 // Start after FLCT
        
        // Traverse trie for prefix
        for (char in prefix) {
            var found = false
            buf.position(currentOffset)
            val freq = buf.get().toInt() and 0xFF
            val isTerminal = buf.get().toInt() and 0xFF
            val childCount = buf.get().toInt() and 0xFF
            
            for (i in 0 until childCount) {
                val c1 = buf.get().toInt() and 0xFF
                val c2 = buf.get().toInt() and 0xFF
                val childChar = ((c1 shl 8) or c2).toChar()
                val offset = buf.getInt()
                
                if (childChar == char) {
                    currentOffset = offset
                    found = true
                    break
                }
            }
            
            if (!found) return emptyList()
        }
        
        // Now currentOffset is the node matching the prefix.
        // We must do a DFS/BFS to find the top `limit` completions.
        val results = mutableListOf<Suggestion>()
        val queue = mutableListOf<Pair<Int, String>>()
        queue.add(Pair(currentOffset, prefix))
        
        while (queue.isNotEmpty() && results.size < limit * 3) { // gather more to sort
            val (offset, currentWord) = queue.removeAt(0)
            
            buf.position(offset)
            val freq = buf.get().toInt() and 0xFF
            val isTerminal = buf.get().toInt() and 0xFF
            val childCount = buf.get().toInt() and 0xFF
            
            if (isTerminal == 1) {
                results.add(Suggestion(currentWord, freq.toFloat()))
            }
            
            for (i in 0 until childCount) {
                val c1 = buf.get().toInt() and 0xFF
                val c2 = buf.get().toInt() and 0xFF
                val childChar = ((c1 shl 8) or c2).toChar()
                val nextOffset = buf.getInt()
                queue.add(Pair(nextOffset, currentWord + childChar))
            }
        }
        
        return results.sortedByDescending { it.score }.take(limit)
    }

    suspend fun getAutoCorrection(typedWord: String): String? = withContext(Dispatchers.IO) {
        val normalized = typedWord.lowercase().trim()
        if (normalized.length < 3) return@withContext null // Too short to safely autocorrect

        // Words the user has typed at least twice are treated as deliberate and are
        // never corrected. A single occurrence is not enough -- otherwise every
        // one-off typo would permanently disable its own correction.
        val personalEntry = personalDao.getWord(normalized)
        if (personalEntry != null && personalEntry.frequency >= 2) return@withContext null

        // Search the base trie for candidates within maxErrors edits. The typed word
        // itself competes as its own distance-0 candidate, so a correction only fires
        // when a nearby word beats what the user actually typed under the
        // distance-penalized score. This deliberately replaces an absolute
        // is-in-dictionary veto, which let junk dictionary entries (e.g. a terminal
        // "thw") suppress obvious corrections like "thw" to "the".
        val candidates = mutableListOf<Pair<Suggestion, Int>>()
        val buf = buffer ?: return@withContext null

        val initialRow = IntArray(normalized.length + 1) { it }
        dfsEditDistance(buf, 4, "", normalized, initialRow, 2, candidates)

        val best = candidates
            .filter { it.first.score > 10f } // minimum frequency threshold
            .maxByOrNull { it.first.score / (it.second + 1) } // distance-penalized score
            ?: return@withContext null

        // The typed word won: it is credible enough as-is, leave it alone.
        if (best.first.word == normalized) return@withContext null
        // Correction must itself clear a minimum credibility bar.
        if (best.first.score / (best.second + 1) <= 20f) return@withContext null
        return@withContext best.first.word
    }

    private fun dfsEditDistance(
        buf: MappedByteBuffer,
        offset: Int,
        currentWord: String,
        targetWord: String,
        currentRow: IntArray,
        maxErrors: Int,
        results: MutableList<Pair<Suggestion, Int>>
    ) {
        buf.position(offset)
        val freq = buf.get().toInt() and 0xFF
        val isTerminal = buf.get().toInt() and 0xFF
        val childCount = buf.get().toInt() and 0xFF

        val children = mutableListOf<Pair<Char, Int>>()
        for (i in 0 until childCount) {
            val c1 = buf.get().toInt() and 0xFF
            val c2 = buf.get().toInt() and 0xFF
            val childChar = ((c1 shl 8) or c2).toChar()
            val childOffset = buf.getInt()
            children.add(Pair(childChar, childOffset))
        }

        val distance = currentRow.last()
        if (isTerminal == 1 && distance <= maxErrors) {
            results.add(Pair(Suggestion(currentWord, freq.toFloat()), distance))
        }

        val minInRow = currentRow.minOrNull() ?: 0
        if (minInRow > maxErrors) return // prune branch entirely

        for ((childChar, childOffset) in children) {
            val nextRow = IntArray(targetWord.length + 1)
            nextRow[0] = currentRow[0] + 1
            for (i in 1..targetWord.length) {
                val insertCost = nextRow[i - 1] + 1
                val deleteCost = currentRow[i] + 1
                val subCost = currentRow[i - 1] + if (targetWord[i - 1] == childChar) 0 else 1
                nextRow[i] = minOf(insertCost, deleteCost, subCost)
            }
            dfsEditDistance(buf, childOffset, currentWord + childChar, targetWord, nextRow, maxErrors, results)
        }
    }
}
