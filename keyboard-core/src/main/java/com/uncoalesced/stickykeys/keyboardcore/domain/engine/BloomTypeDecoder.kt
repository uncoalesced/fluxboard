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
 * GBoard-adjacent continuous spatial trajectory decoder for BloomType.
 *
 * Replaces discrete-key subsequence scanning with a continuous curve-to-polyline
 * trajectory aligner over the multi-lexicon binary prefix tries.
 */
class BloomTypeDecoder(
    private val dictionary: () -> ByteBuffer?,
    private val namesDictionary: () -> ByteBuffer? = { null },
    private val languageModel: LanguageModel,
    private val revertHistory: BloomRevertHistory,
    private val profile: BloomSpatialProfile,
) {
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

        val results = mutableListOf<Pair<String, Float>>()

        // Search base dictionary
        dictionary()?.let { buf ->
            searchTrie(buf, trajectory, keyCentroids, previousWord, results)
        }

        // Search names / cultural dictionary if present
        namesDictionary()?.let { buf ->
            searchTrie(buf, trajectory, keyCentroids, previousWord, results)
        }

        return results
            .asSequence()
            .sortedBy { it.second }
            .map { it.first }
            .distinct()
            .take(MAX_BLOOM_CANDIDATES)
            .toList()
    }

    private fun searchTrie(
        buf: ByteBuffer,
        trajectory: BloomTrajectory,
        keyCentroids: (Char) -> Pair<Float, Float>?,
        previousWord: String?,
        results: MutableList<Pair<String, Float>>,
    ) {
        try {
            var frontier = listOf(BloomBeam(TRIE_ROOT_OFFSET, "", 0, 0f))
            var depth = 0

            while (frontier.isNotEmpty() && depth < MAX_BLOOM_WORD_LEN) {
                val next = mutableListOf<BloomBeam>()
                for (beam in frontier) {
                    expandBeam(buf, beam, trajectory, keyCentroids, previousWord, results, next)
                }
                frontier = next.sortedBy { it.spatialEstimate }.take(MAX_BLOOM_BEAM)
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
        results: MutableList<Pair<String, Float>>,
        next: MutableList<BloomBeam>,
    ) {
        buf.position(beam.offset)
        val frequency = buf.get().toInt() and 0xFF
        val isTerminal = buf.get().toInt() and 0xFF
        val childCount = buf.get().toInt() and 0xFF

        val children = ArrayList<Pair<Char, Int>>(childCount)
        for (i in 0 until childCount) {
            val c1 = buf.get().toInt() and 0xFF
            val c2 = buf.get().toInt() and 0xFF
            val childChar = ((c1 shl 8) or c2).toChar()
            val childOffset = buf.getInt()
            children.add(Pair(childChar, childOffset))
        }

        // 1. Evaluate completed terminal word
        if (isTerminal == 1 && beam.word.length >= 2) {
            val spatialCost = aligner.scoreCandidate(trajectory, beam.word, keyCentroids, profile)
            if (spatialCost != null) {
                val contextCredit = contextBoost(previousWord, beam.word)
                val freqScore = (frequency / 255.0f) * 1.5f
                val revertPenalty =
                    if (revertHistory.isReverted(
                            beam.word,
                        )
                    ) {
                        REVERT_PENALTY_COST
                    } else {
                        0f
                    }

                val finalCost = spatialCost - contextCredit - freqScore + revertPenalty
                results.add(beam.word to finalCost)
            }
        }

        // 2. Expand children along the trajectory
        for ((childChar, childOffset) in children) {
            val nominal = keyCentroids(childChar) ?: continue
            val (adaptedX, adaptedY) =
                profile.getAdaptedCenter(
                    childChar,
                    nominal.first,
                    nominal.second,
                )

            // Doubled letter stays on the same stroke point
            if (beam.word.isNotEmpty() && childChar == beam.word.last()) {
                next.add(
                    BloomBeam(
                        offset = childOffset,
                        word = beam.word + childChar,
                        pathSampleIndex = beam.pathSampleIndex,
                        spatialEstimate = beam.spatialEstimate,
                    ),
                )
                continue
            }

            // Find closest stroke point at or after current index
            var minDist = Float.MAX_VALUE
            var bestIdx = -1
            val searchStart = (beam.pathSampleIndex - 2).coerceAtLeast(0)

            for (s in searchStart until trajectory.count) {
                val d = hypot(trajectory.getX(s) - adaptedX, trajectory.getY(s) - adaptedY)
                if (d < minDist) {
                    minDist = d
                    bestIdx = s
                }
            }

            // Pruning: if the stroke never came within 2.2 key widths of this child key, prune this branch
            if (minDist <= 2.2f && bestIdx >= 0) {
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
