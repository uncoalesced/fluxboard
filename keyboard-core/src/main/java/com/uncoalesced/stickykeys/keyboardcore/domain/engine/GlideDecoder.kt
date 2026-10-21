// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import java.nio.ByteBuffer

/**
 * How many glide readings to keep.
 *
 * More than the three the suggestion strip shows, because the strip is where a wrong first
 * guess gets corrected without retyping, and a glide is wrong more often than a tap.
 */
private const val MAX_GLIDE_CANDIDATES = 5

/**
 * Longest word a glide will produce.
 *
 * A guard on the walk rather than a real limit on English. Without it a path that loops back
 * over itself lets a branch keep finding its next letter behind it, and the recursion follows
 * arbitrarily long words down a trie that has no reason to stop.
 */
private const val MAX_GLIDE_WORD = 24

/**
 * How many partial readings survive each level of the glide walk.
 *
 * The bound the old recursion never had. It searched every branch the path admitted to
 * whatever depth it admitted, which is fine for the ordinary case and unbounded for the
 * pathological one -- a long looping glide crosses most of the board, and every crossing
 * multiplies the branches alive at the next letter. Kept deliberately wide: pruning is
 * insurance against a gesture nobody makes on purpose, not a ranking mechanism, and a narrow
 * beam would start cutting real words whose cost arrives late.
 *
 * Widened from 40. "A narrow beam cuts real words whose cost arrives late" is the exact
 * failure mode of a long glide: the evidence separating a long reading from a short one sits
 * at the end of the path, so the right reading can stay mid-table for most of the walk and
 * only pull ahead once the last letters land. The wider the beam, the further a late-arriving
 * winner can be carried, and widening can only ever keep candidates a narrower beam would
 * have cut -- the ranking that follows is unchanged.
 *
 * Measured rather than assumed, against the real dictionary: at 40, at 120 and at this value
 * the pathological stroke in GlideBeamTest -- the whole alphabet twice round with a corner
 * every three keys, which no finger draws -- decodes in 35-37ms against that test's 5000ms
 * ceiling, and an ordinary glide in about 30ms. The width stops being the binding constraint
 * well before here, so the extra is insurance for the paths that do saturate it rather than
 * work the common case pays for.
 */
private const val MAX_GLIDE_BEAM = 320

/**
 * Most a maximally-likely follower can take off a glide's cost.
 *
 * Sentence context and path cost are on unrelated scales -- the language model answers on the
 * dictionary's 0-255 frequency scale, while a glide cost counts unexplained corners at
 * [COST_UNEXPLAINED_PIVOT] apiece. Subtracting one from the other directly would let context
 * outweigh every structural fact about the drawn path by an order of magnitude, so a known
 * follower would win no matter what the finger did.
 *
 * Capped below the cost of a single unexplained corner instead. Context then settles the case
 * it should settle -- two readings the path genuinely cannot distinguish, like "too" and "to"
 * -- and can never overrule the path itself.
 */
private const val MAX_GLIDE_CONTEXT_CREDIT = 4f

/**
 * One partial reading of a glide, alive at the current level of the beam search.
 *
 * The first four fields are exactly the arguments the recursion used to carry: where in the
 * trie the reading has reached, the letters spelled so far, how far along the drawn path they
 * consumed, and what they cost. Making them a value rather than a call frame is the whole
 * transformation -- a list of partial readings can be sorted and cut, a stack of call frames
 * cannot.
 *
 * [pivotCredit] is the field the recursion had no use for and the beam cannot work without.
 * Ranking partial readings by [cost] alone does not rank them at all: a glide crosses its keys
 * exactly, so almost every branch sits at cost 0 and a stable sort then keeps whichever
 * branches the trie happens to list first. Measured on the h-e-l-o path, that dropped "hello"
 * outright in favour of "ho", "go" and "hi" -- alphabetically earlier subtries, nothing more.
 */
private data class GlideBeam(
    val offset: Int,
    val word: String,
    val pathIndex: Int,
    val cost: Int,
    val pivotCredit: Float,
) {
    /**
     * Lower is better, and on the same scale [scoreGlideCandidate] finally reports.
     *
     * A partial reading that has already turned the corners the path turned is worth keeping
     * over one that has walked past them, because the corners are exactly what the final score
     * charges for. Estimating the eventual score rather than the cost so far is what makes the
     * cut a search heuristic instead of a tiebreak on trie order.
     */
    val priority: Float get() = cost - pivotCredit
}

/**
 * The glide decoder: turns a finished stroke into ranked words, against the dictionary trie.
 *
 * Plain Kotlin with no Android, no coroutine and no database, so a JVM test can build one over
 * the shipped `base_dict.bin` and replay recorded strokes into [decode] directly.
 * [PredictionEngine.decodeGlide] is the production caller and runs it off the main thread.
 *
 * [dictionary] hands out a fresh cursor on every call, never a shared one -- see
 * `PredictionEngine.reader` for why that matters.
 */
internal class GlideDecoder(
    private val dictionary: () -> ByteBuffer?,
    private val languageModel: LanguageModel,
) {
    /**
     * Decodes a finished glide into ranked word candidates.
     *
     * Walks the same trie the spelling search uses, but with a different question. Spelling
     * asks how far a typed string is from each word; a glide asks which words the drawn
     * path *could be*, and there are always many -- the path for "hello" crosses enough of
     * the board that "ho", "hell" and "hilo" are all readings of it. Ranking is therefore
     * the whole job, not a tie-break.
     *
     * The walk carries the position reached along the path, so a branch dies the moment its
     * next letter cannot be found ahead of where the previous one matched. That is what
     * keeps this from enumerating the dictionary: the first letter alone restricts the root
     * to the keys under the finger at touch-down, and each subsequent letter prunes again.
     *
     * Level-order rather than depth-first, keeping only the [MAX_GLIDE_BEAM] cheapest
     * partial readings across the *whole* frontier at each letter. Pruning across the
     * frontier rather than per node is what makes it a beam search instead of a fan-out
     * cap: a cheap partial reading survives regardless of which branch produced it, and no
     * single branch can spend the budget the way an ordinary depth limit would let it.
     *
     * Frequency breaks ties, one rung below sentence context and nothing more. It has to be
     * last, or every glide returns the commonest short word whose letters happen to lie
     * along the path.
     */
    fun decode(
        stroke: GlideStroke,
        previousWord: String? = null,
    ): List<String> =
        run {
            if (!stroke.isUsable) return@run emptyList()
            val buf = dictionary() ?: return@run emptyList()
            val results = mutableListOf<Pair<String, Int>>()
            try {
                // 4 bytes in: the header is the ASCII magic "FLCT", and the root follows it.
                var frontier = listOf(GlideBeam(TRIE_ROOT_OFFSET, "", 0, 0, 0f))
                var depth = 0
                while (frontier.isNotEmpty() && depth < MAX_GLIDE_WORD) {
                    val next = mutableListOf<GlideBeam>()
                    for (beam in frontier) {
                        expandBeam(buf, beam, stroke, results, next)
                    }
                    frontier = next.sortedBy { it.priority }.take(MAX_GLIDE_BEAM)
                    depth++
                }
            } catch (e: Exception) {
                // A malformed offset must not escape: this runs in the IME process, where
                // an unhandled throw takes the keyboard down mid-gesture.
                return@run emptyList()
            }
            results
                .asSequence()
                .sortedWith(
                    compareBy<Pair<String, Int>> {
                        it.second - glideContextCredit(previousWord, it.first)
                    }.thenByDescending { frequencyOf(it.first) ?: 0 },
                ).map { it.first }
                .distinct()
                .take(MAX_GLIDE_CANDIDATES)
                .toList()
        }

    /**
     * Reads one trie node, scores it if it completes a word, and queues its viable children.
     *
     * Split out of [decode] only because the loop that calls it is already three deep;
     * the matching rules are unchanged from the recursion this replaced.
     */
    private fun expandBeam(
        buf: ByteBuffer,
        beam: GlideBeam,
        stroke: GlideStroke,
        results: MutableList<Pair<String, Int>>,
        next: MutableList<GlideBeam>,
    ) {
        buf.position(beam.offset)
        buf.get() // frequency, read through the ranking pass instead
        val isTerminal = buf.get().toInt() and 0xFF
        val childCount = buf.get().toInt() and 0xFF

        val children = ArrayList<Pair<Char, Int>>(childCount)
        for (i in 0 until childCount) {
            val c1 = buf.get().toInt() and 0xFF
            val c2 = buf.get().toInt() and 0xFF
            val childChar = ((c1 shl 8) or c2).toChar()
            children.add(Pair(childChar, buf.getInt()))
        }

        // A complete word only counts if the finger actually lifted here. scoreGlideCandidate
        // re-scores from scratch rather than trusting the running cost, so the anchors and
        // the unexplained-corner penalty are applied by one function with one definition.
        if (isTerminal == 1) {
            scoreGlideCandidate(beam.word, stroke)?.let { results.add(beam.word to it) }
        }

        for ((childChar, childOffset) in children) {
            // A doubled letter does not advance along the path: a finger cannot visit the
            // same key twice in succession, so the second 'l' of "hello" has no position
            // of its own to occupy.
            if (beam.word.isNotEmpty() && childChar == beam.word.last()) {
                next.add(
                    GlideBeam(
                        childOffset,
                        beam.word + childChar,
                        beam.pathIndex,
                        // Free, matching scoreGlideCandidate. This charged 1 while the
                        // score charged 0, so a doubled-letter reading was ranked worse
                        // during the search than the score it was being searched for --
                        // enough to drop it from a crowded beam and never be rescored.
                        beam.cost + COST_DOUBLE_LETTER,
                        beam.pivotCredit,
                    ),
                )
                continue
            }
            val match = nextGlideMatch(stroke, beam.pathIndex, childChar) ?: continue
            next.add(
                GlideBeam(
                    childOffset,
                    beam.word + childChar,
                    match.first + 1,
                    beam.cost + match.second,
                    beam.pivotCredit + pivotCreditAt(stroke, match.first),
                ),
            )
        }
    }

    /**
     * What explaining the corner at [index] is worth, or 0f if it is not one.
     *
     * Weighted exactly as [scoreGlideCandidate] charges for missing it, so the search
     * heuristic and the final score cannot disagree about which corners matter.
     */
    private fun pivotCreditAt(
        stroke: GlideStroke,
        index: Int,
    ): Float {
        if (index !in stroke.pivots) return 0f
        return (stroke.pivotStrength[index] ?: 1f) * COST_UNEXPLAINED_PIVOT
    }

    /**
     * The glide's cost reduction for following [previousWord], on the glide cost scale.
     *
     * Normalized rather than subtracted raw -- see [MAX_GLIDE_CONTEXT_CREDIT] for why the
     * two scales cannot simply be mixed.
     */
    private fun glideContextCredit(
        previousWord: String?,
        word: String,
    ): Float {
        if (previousWord == null) return 0f
        val boost = languageModel.boostFor(previousWord, word)
        if (boost <= 0f) return 0f
        return (boost / MAX_LANGUAGE_MODEL_BOOST).coerceAtMost(1f) * MAX_GLIDE_CONTEXT_CREDIT
    }

    /** First position at or after [from] where [letter] is on the path, and what it cost. */
    private fun nextGlideMatch(
        stroke: GlideStroke,
        from: Int,
        letter: Char,
    ): Pair<Int, Int>? {
        var near: Pair<Int, Int>? = null
        for (i in from until stroke.keys.size) {
            val onPath = stroke.keys[i]
            if (onPath == letter) return Pair(i, -CREDIT_KEY_ON_PATH)
            if (near == null && KeyProximity.areAdjacent(onPath, letter)) {
                near = Pair(i, COST_NEIGHBOUR - CREDIT_KEY_ON_PATH)
            }
        }
        return near
    }

    /** Frequency of an exact dictionary word, or null when it is not a terminal node. */
    private fun frequencyOf(word: String): Int? {
        val buf = dictionary() ?: return null
        return trieFrequencyOf(buf, word)
    }
}
