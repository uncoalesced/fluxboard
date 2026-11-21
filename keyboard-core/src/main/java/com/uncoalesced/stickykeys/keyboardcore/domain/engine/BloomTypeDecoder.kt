// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import java.nio.ByteBuffer
import kotlin.math.hypot

private const val MAX_BLOOM_CANDIDATES = 5
private const val MAX_BLOOM_WORD_LEN = 24
private const val MAX_BLOOM_BEAM = 240
private const val REVERT_PENALTY_COST = 20.0f
private const val MAX_LM_BOOST_CREDIT = 3.0f

private data class BloomBeam(
    val offset: Int,
    val word: String,
    val pathSampleIndex: Int,
    val spatialEstimate: Float,
)

/**
 * Continuous spatial trajectory decoder for BloomType.
 *
 * Replaces discrete-key subsequence scanning with a continuous curve-to-polyline
 * trajectory aligner over multi-lexicon binary prefix tries and personal dictionaries.
 */
class BloomTypeDecoder(
    private val dictionary: () -> ByteBuffer?,
    namesDictionary: () -> ByteBuffer? = { null },
    private val languageModel: LanguageModel,
    private val revertHistory: BloomRevertHistory,
    private val profile: BloomSpatialProfile,
    private val personalWords: () -> List<Pair<String, Float>> = { emptyList() },
) {
    private val namesDictionaryProvider = namesDictionary
    private val aligner = BloomTrajectoryAligner()

    /**
     * Decodes [trajectory] into ranked candidate words using continuous spatial alignment,
     * language model context priors, and revert feedback.
     */
    fun decode(
        trajectory: BloomTrajectory,
        keyCentroids: (Char) -> Pair<Float, Float>?,
        previousWord: String? = null,
    ): List<String> {
        if (!trajectory.isUsable) return emptyList()

        val bestScores = mutableMapOf<String, Float>()

        // 1. Search personal and custom dictionary words
        val pWords = personalWords()
        if (pWords.isNotEmpty()) {
            val startX = trajectory.getX(0)
            val startY = trajectory.getY(0)
            for ((pWord, pScore) in pWords) {
                if (pWord.length < 2 || pWord.length > MAX_BLOOM_WORD_LEN) continue
                val firstChar = pWord[0]
                val nominal = keyCentroids(firstChar) ?: continue
                val adaptedX = profile.getAdaptedX(firstChar, nominal.first)
                val adaptedY = profile.getAdaptedY(firstChar, nominal.second)
                val startDist = hypot(startX - adaptedX, startY - adaptedY)
                if (startDist > 1.85f) continue

                val spatialCost = aligner.scoreCandidate(trajectory, pWord, keyCentroids, profile)
                if (spatialCost != null) {
                    val contextCredit = contextBoost(previousWord, pWord)
                    val freqScore = (pScore / 255.0f) * 1.5f
                    val revertPenalty =
                        if (revertHistory.isReverted(pWord)) REVERT_PENALTY_COST else 0f
                    val finalCost = spatialCost - contextCredit - freqScore + revertPenalty
                    val existing = bestScores[pWord]
                    if (existing == null || finalCost < existing) {
                        bestScores[pWord] = finalCost
                    }
                }
            }
        }

        // 2. Search base dictionary trie
        dictionary()?.let { buf ->
            searchTrie(
                buf = buf,
                trajectory = trajectory,
                keyCentroids = keyCentroids,
                previousWord = previousWord,
                bestScores = bestScores,
                isSupplemental = false,
            )
        }

        // 3. Search names / cultural dictionary trie if present
        namesDictionaryProvider()?.let { buf ->
            searchTrie(
                buf = buf,
                trajectory = trajectory,
                keyCentroids = keyCentroids,
                previousWord = previousWord,
                bestScores = bestScores,
                isSupplemental = true,
            )
        }

        return bestScores.entries
            .asSequence()
            .sortedBy { it.value }
            .take(MAX_BLOOM_CANDIDATES)
            .map { it.key }
            .toList()
    }

    private fun searchTrie(
        buf: ByteBuffer,
        trajectory: BloomTrajectory,
        keyCentroids: (Char) -> Pair<Float, Float>?,
        previousWord: String?,
        bestScores: MutableMap<String, Float>,
        isSupplemental: Boolean = false,
    ) {
        try {
            var frontier = listOf(BloomBeam(TRIE_ROOT_OFFSET, "", 0, 0f))
            var depth = 0
            val next = mutableListOf<BloomBeam>()

            while (frontier.isNotEmpty() && depth < MAX_BLOOM_WORD_LEN) {
                next.clear()
                for (beam in frontier) {
                    expandBeam(
                        buf = buf,
                        beam = beam,
                        trajectory = trajectory,
                        keyCentroids = keyCentroids,
                        previousWord = previousWord,
                        bestScores = bestScores,
                        next = next,
                        isSupplemental = isSupplemental,
                    )
                }
                if (next.isEmpty()) break
                frontier =
                    if (next.size > MAX_BLOOM_BEAM) {
                        next.sortedBy { it.spatialEstimate }.take(MAX_BLOOM_BEAM)
                    } else {
                        ArrayList(next)
                    }
                depth++
            }
        } catch (e: Exception) {
            // Buffer safety: IME process must not crash on malformed offset
        }
    }

    private fun expandBeam(
        buf: ByteBuffer,
        beam: BloomBeam,
        trajectory: BloomTrajectory,
        keyCentroids: (Char) -> Pair<Float, Float>?,
        previousWord: String?,
        bestScores: MutableMap<String, Float>,
        next: MutableList<BloomBeam>,
        isSupplemental: Boolean = false,
    ) {
        buf.position(beam.offset)
        val frequency = buf.get().toInt() and 0xFF
        val isTerminal = buf.get().toInt() and 0xFF
        val childCount = buf.get().toInt() and 0xFF

        // 1. Evaluate completed terminal word
        val eligibleTerminal =
            isTerminal == 1 &&
                beam.word.length >= 2 &&
                (!isSupplemental || beam.word.length >= 4 || frequency >= 170)
        if (eligibleTerminal) {
            val spatialCost = aligner.scoreCandidate(trajectory, beam.word, keyCentroids, profile)
            if (spatialCost != null) {
                val contextCredit = contextBoost(previousWord, beam.word)
                val freqScale = if (isSupplemental) 0.95f else 1.5f
                val sourcePenalty = if (isSupplemental) 0.55f else 0f
                val freqScore = (frequency / 255.0f) * freqScale
                val revertPenalty =
                    if (revertHistory.isReverted(beam.word)) {
                        REVERT_PENALTY_COST
                    } else {
                        0f
                    }

                val finalCost =
                    spatialCost - contextCredit - freqScore + sourcePenalty + revertPenalty
                val existing = bestScores[beam.word]
                if (existing == null || finalCost < existing) {
                    bestScores[beam.word] = finalCost
                }
            }
        }

        if (childCount == 0) return

        // 2. Expand children along the trajectory without intermediate allocations
        val isRoot = beam.word.isEmpty()
        for (i in 0 until childCount) {
            val c1 = buf.get().toInt() and 0xFF
            val c2 = buf.get().toInt() and 0xFF
            val childChar = ((c1 shl 8) or c2).toChar()
            val childOffset = buf.getInt()

            val nominal = keyCentroids(childChar) ?: continue
            val adaptedX = profile.getAdaptedX(childChar, nominal.first)
            val adaptedY = profile.getAdaptedY(childChar, nominal.second)

            if (isRoot) {
                // First letter must anchor to the initial segment of the trajectory (s <= 3)
                var minDist = Float.MAX_VALUE
                var bestIdx = -1
                val maxStartSample = minOf(3, trajectory.count - 1)
                for (s in 0..maxStartSample) {
                    val dx = trajectory.getX(s) - adaptedX
                    val dy = trajectory.getY(s) - adaptedY
                    val d = hypot(dx, dy)
                    if (d < minDist) {
                        minDist = d
                        bestIdx = s
                    }
                }

                if (minDist <= 1.85f && bestIdx >= 0) {
                    next.add(
                        BloomBeam(
                            offset = childOffset,
                            word = childChar.toString(),
                            pathSampleIndex = bestIdx,
                            spatialEstimate = minDist * 1.5f,
                        ),
                    )
                }
            } else {
                // Doubled letter stays on the same stroke point
                if (childChar == beam.word.last()) {
                    next.add(
                        BloomBeam(
                            offset = childOffset,
                            word = beam.word + childChar,
                            pathSampleIndex = beam.pathSampleIndex,
                            spatialEstimate = beam.spatialEstimate + 0.1f,
                        ),
                    )
                    continue
                }

                // Forward search along the trajectory (allow 1 sample rewind for corner jitter)
                var minDist = Float.MAX_VALUE
                var bestIdx = -1
                val searchStart = (beam.pathSampleIndex - 1).coerceAtLeast(0)

                for (s in searchStart until trajectory.count) {
                    val dx = trajectory.getX(s) - adaptedX
                    val dy = trajectory.getY(s) - adaptedY
                    val d = hypot(dx, dy)
                    if (d < minDist) {
                        minDist = d
                        bestIdx = s
                    }
                }

                // Pruning: stroke must pass near the key and progress forward
                if (minDist <= 2.2f && bestIdx >= (beam.pathSampleIndex - 1).coerceAtLeast(0)) {
                    next.add(
                        BloomBeam(
                            offset = childOffset,
                            word = beam.word + childChar,
                            pathSampleIndex = bestIdx,
                            spatialEstimate = beam.spatialEstimate + minDist,
                        ),
                    )
                }
            }
        }
    }

    private fun contextBoost(
        previousWord: String?,
        word: String,
    ): Float {
        if (previousWord == null) return 0f
        val boost = languageModel.boostFor(previousWord, word)
        if (boost <= 0f) return 0f
        return (boost / MAX_LANGUAGE_MODEL_BOOST).coerceAtMost(1f) * MAX_LM_BOOST_CREDIT
    }
}
