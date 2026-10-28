// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import java.nio.ByteBuffer

/**
 * Edit costs, on a scale where one ordinary substitution is [COST_SUBSTITUTE].
 *
 * Uniform costs make every one-edit candidate a tie, which hands the decision to raw
 * dictionary frequency and reliably picks the commoner word over the one the user's finger
 * actually explains. Ordering these by how often each mistake really happens is what makes
 * "vall" resolve to "call" rather than "all", and "gine" to "gone" rather than "line".
 *
 * A dropped or doubled letter sits between the two substitution costs on purpose: it is more
 * likely than hitting a key on the other side of the board, and less likely than catching the
 * neighbour of the key aimed at.
 */
private const val COST_ADJACENT_SUBSTITUTE = 2
private const val COST_GAP = 3
private const val COST_TRANSPOSE = 3
private const val COST_SUBSTITUTE = 4

/**
 * Three ordinary edits. Beyond this the candidate is not a plausible reading of the input.
 *
 * Raised from two deliberately, trading search width for accuracy. On the weighted scale
 * above, two ordinary edits also bought only four adjacent slips -- and four adjacent slips is
 * an ordinary outcome for a thumb moving quickly, so longer words were falling outside the
 * budget for mistakes that are individually very likely. A third edit is what puts them back
 * in reach.
 *
 * This widens the *pool*, not the willingness to correct: [MIN_CORRECTION_SCORE] still decides
 * whether anything is applied, and a candidate three edits away carries three edits' worth of
 * penalty into that decision. The cost is a wider walk on every word-ending keystroke, which
 * is the trade this was asked for.
 */
private const val MAX_EDIT_COST = COST_SUBSTITUTE * 3

/** A correction has to be at least this credible after its edit penalty to be applied. */
private const val MIN_CORRECTION_SCORE = 20f

/**
 * The furthest a correction may be from what was typed, in cost units per typed letter.
 *
 * One unit per letter is half an adjacent slip per letter, so a short word gets about one
 * slip and a long one proportionally more. Measured on the shipped dictionary: every
 * correction the test suite pins sits at or under this ("definately" costs 4 over 10
 * letters, the worst ratio is "teh"/"adn" at 3 over 3), while "zorbly" to "only" costs 8
 * over 6 letters.
 */
private const val MAX_COST_PER_LETTER = 1

/**
 * How close a dropped-repeated-letter reading has to score to the winner to make a word
 * ambiguous. "hello" scores 0.73 of "help" for "helo"; no correction the test suite pins
 * has a doubled reading at all, so the margin on that side is not yet measured.
 */
private const val DOUBLED_READING_RATIO = 0.6f

/** Shortest run-on worth trying to split: two three-letter words plus the junk key. */
private const val MIN_SPLIT_LENGTH = 7

/** Neither half of a split may be shorter than this, or "a" and "I" match everywhere. */
private const val MIN_SPLIT_PART = 3

/** Both halves of a split must be this common, so a split never invents a rare pairing. */
private const val MIN_SPLIT_FREQUENCY = 40

/**
 * Spelling correction against the dictionary trie: weighted edit distance with adjacent
 * transposition, the missing-apostrophe repair, and the run-on split.
 *
 * Plain Kotlin with no Android, no coroutine and no database, like [GlideDecoder]. The
 * personal-dictionary rules that decide whether to correct at all stay in
 * [PredictionEngine.getAutoCorrection]; this class only answers what the correction would be.
 *
 * [dictionary] hands out a fresh cursor on every call -- see `PredictionEngine.reader`.
 */
internal class SpellingCorrector(
    private val dictionary: () -> ByteBuffer?,
    private val languageModel: LanguageModel,
) {
    /**
     * The best single-word correction, or null when nothing beats what was typed or the
     * winner is not trustworthy enough to apply.
     *
     * [personalWords] are words the user has made their own, each with a score on the same
     * scale as a base-dictionary frequency. They compete as candidates alongside the base
     * dictionary, which is what lets a near miss of a learned word ("zorbli") reach it.
     */
    internal fun nearestWord(
        normalized: String,
        previousWord: String? = null,
        personalWords: List<Pair<String, Float>> = emptyList(),
    ): String? {
        // "alot" is "a lot" with the space missed, and also one key from "slot". Two
        // readings that far apart in meaning are not a typo to fix silently.
        if (isArticleRunOn(normalized)) return null

        // Search the base trie for candidates within [MAX_EDIT_COST]. The typed word
        // itself competes as its own zero-cost candidate, so a correction only fires
        // when a nearby word beats what the user actually typed under the
        // cost-penalized score. This deliberately replaces an absolute
        // is-in-dictionary veto, which let junk dictionary entries (e.g. a terminal
        // "thw") suppress obvious corrections like "thw" to "the".
        val base = candidatesFor(normalized) ?: return null
        val eligible = withPersonalWords(normalized, base, personalWords)

        // Whether a correction fires at all is settled first, and without context.
        //
        // This ordering is the whole safety argument. The typed word competes as its own
        // zero-cost candidate, and "it won, so leave it alone" is what stops a correctly
        // spelled word being rewritten. Fold context into that decision and a strong
        // enough follower can outscore a word the user typed perfectly -- which is
        // real-word correction, the one thing this engine has always refused to do.
        val plainBest =
            eligible.maxByOrNull { it.first.score / penaltyFor(it.second) } ?: return null
        // The typed word won: it is credible enough as-is, leave it alone.
        if (plainBest.first.word == normalized) return null
        // Correction must itself clear a minimum credibility bar.
        if (plainBest.first.score / penaltyFor(plainBest.second) <= MIN_CORRECTION_SCORE) {
            return null
        }

        // Restraint (roadmap 4R.3). A wrong correction costs the user a rewrite, a missed one
        // costs a typo, so a winner that only *beat* the alternatives is not enough.
        //
        // First, the winner has to be close. Frequency can carry a candidate several edits
        // away past the bar above -- "zorbly" became "only" -- and a word that far from
        // anything is far more likely a word the dictionary does not know than a typo of a
        // common one. Leaving it alone is also what lets it be learned.
        if (plainBest.second > normalized.length * MAX_COST_PER_LETTER) return null
        // Second, a dropped repeated letter is as ordinary a slip as a mispressed key, so
        // when that reading is real and competitive the word is ambiguous: "helo" is one key
        // from "help" and one tap short of "hello".
        if (hasCompetingDoubledReading(normalized, plainBest)) return null

        // Only now does the sentence get a say, and only over *which* correction wins.
        // Every candidate here clears the same bar the unconditional winner just cleared,
        // so context reorders real corrections and can promote nothing else. With no
        // previous word every boost is zero and this necessarily re-elects [plainBest],
        // which is what keeps the contextless path byte-identical.
        //
        // Context also may not reach for a candidate that needs more editing than the plain
        // winner. That is how "at thier" became "at the": two deletions outranked one
        // transposition because "the" follows "at" so often.
        val best =
            eligible
                .filter { it.second <= plainBest.second }
                .filter { it.first.score / penaltyFor(it.second) > MIN_CORRECTION_SCORE }
                .maxByOrNull {
                    (it.first.score + languageModel.boostFor(previousWord, it.first.word)) /
                        penaltyFor(it.second)
                }
                ?: return null
        // Context defended the typed word against a correction that would otherwise have
        // fired. Fewer corrections is always the safe direction, so this is allowed to.
        if (best.first.word == normalized) return null
        return best.first.word
    }

    /**
     * What to offer in the strip for a word autocorrect left alone, best first.
     *
     * The other half of restraint: a correction that is not certain enough to apply is still
     * worth one tap. Empty for a dictionary word, which needs no offer, and the same closeness
     * limit as [nearestWord] applies, so a word nothing is near gets nothing. A run-on with
     * "a" or "i" offers the split itself first, since that is the likeliest reading.
     */
    internal fun offersFor(
        normalized: String,
        personalWords: List<Pair<String, Float>> = emptyList(),
        limit: Int = 3,
    ): List<String> {
        if (frequencyOf(normalized) != null) return emptyList()
        val split =
            if (isArticleRunOn(normalized)) {
                listOf(normalized.take(1) + " " + normalized.substring(1))
            } else {
                emptyList()
            }
        val base = candidatesFor(normalized) ?: return split
        val near =
            withPersonalWords(normalized, base, personalWords)
                .filter { it.first.word != normalized }
                .filter { it.second <= normalized.length * MAX_COST_PER_LETTER }
                .sortedByDescending { it.first.score / penaltyFor(it.second) }
                .map { it.first.word }
        return (split + near).distinct().take(limit)
    }

    /**
     * Every base-dictionary word within [MAX_EDIT_COST] of [normalized] that is common enough to
     * be worth correcting to, paired with its weighted edit cost. The typed word itself is among
     * them at cost 0 when it is a dictionary word. Null when the dictionary cannot be read.
     */
    internal fun candidatesFor(normalized: String): List<Pair<Suggestion, Int>>? {
        val candidates = mutableListOf<Pair<Suggestion, Int>>()
        val buf = dictionary() ?: return null

        // Same reasoning as getSuggestions: never let a bad offset kill the process.
        try {
            val initialRow = IntArray(normalized.length + 1) { it * COST_GAP }
            dfsEditDistance(
                buf,
                4,
                "",
                normalized,
                initialRow,
                null,
                null,
                MAX_EDIT_COST,
                candidates,
            )
        } catch (e: Exception) {
            return null
        }
        return candidates.filter { it.first.score > 10f } // minimum frequency
    }

    /**
     * [base] plus every personal word within [MAX_EDIT_COST] of [normalized]. A word in both
     * keeps the higher of its two scores rather than appearing twice.
     */
    private fun withPersonalWords(
        normalized: String,
        base: List<Pair<Suggestion, Int>>,
        personalWords: List<Pair<String, Float>>,
    ): List<Pair<Suggestion, Int>> {
        if (personalWords.isEmpty()) return base
        val merged = base.associateBy { it.first.word }.toMutableMap()
        for ((word, score) in personalWords) {
            val cost = weightedEditCost(normalized, word)
            if (cost > MAX_EDIT_COST) continue
            val existing = merged[word]
            if (existing == null || existing.first.score < score) {
                merged[word] = Suggestion(word, score) to cost
            }
        }
        return merged.values.toList()
    }

    /**
     * Whether [normalized] reads as "a" or "i" run into a common word: "alot", "ilike".
     *
     * Only for a word that is not itself in the dictionary, so "atone", "aside" and "item"
     * are untouched. A missed space and a misspelling are both plausible here and they point
     * at words with different meanings, which is the case autocorrect should stay out of.
     */
    private fun isArticleRunOn(normalized: String): Boolean {
        if (normalized.length < 3) return false
        if (normalized[0] != 'a' && normalized[0] != 'i') return false
        if (frequencyOf(normalized) != null) return false
        val rest = frequencyOf(normalized.substring(1)) ?: return false
        return rest >= MIN_SPLIT_FREQUENCY
    }

    /**
     * Whether doubling one letter of [normalized] gives a real word other than [winner] that
     * scores within [DOUBLED_READING_RATIO] of it, as a dropped repeated letter would cost.
     */
    private fun hasCompetingDoubledReading(
        normalized: String,
        winner: Pair<Suggestion, Int>,
    ): Boolean {
        val winnerScore = winner.first.score / penaltyFor(winner.second)
        for (i in normalized.indices) {
            val head = normalized.substring(0, i + 1)
            val doubled = head + normalized[i] + normalized.substring(i + 1)
            if (doubled == winner.first.word) continue
            val freq = frequencyOf(doubled) ?: continue
            if (freq / penaltyFor(COST_GAP) >= winnerScore * DOUBLED_READING_RATIO) return true
        }
        return false
    }

    /**
     * Divisor applied to a candidate's frequency, so a further-away word has to be
     * proportionally more common to win.
     *
     * Expressed in whole edits rather than raw cost units, which keeps this the same curve
     * it was before edit costs became weighted -- one edit still halves a candidate's
     * score. Only the *ordering within* a given number of edits changed.
     */
    private fun penaltyFor(cost: Int): Float = 1f + cost.toFloat() / COST_SUBSTITUTE

    /**
     * Puts back an apostrophe the user left out: "youre" to "you're", "dont" to "don't".
     *
     * A targeted repair rather than a tweak to the edit costs, and tried ahead of the
     * general search for the same reason [splitOnMispressedSpace] is: the edit-distance
     * walk cannot get this right at any cost setting. Inserting an apostrophe and
     * deleting a letter are both one gap, so "youre" reaches "you're" and "your" for the
     * identical price and the tie falls to raw frequency -- which "your" wins by three
     * orders of magnitude, because the corpus counts it as one of the commonest words in
     * English. Lowering the cost of an apostrophe until "you're" won would have made
     * every apostrophe near-free, and at that point "were" reaches "we're" for nothing.
     *
     * The apostrophe is worth this on this keyboard specifically: it is not on the letter
     * plane at all, so reaching it costs a long press or a page switch and dropping it is
     * the single most common thing a hurried thumb does.
     *
     * Only fires for a word that is not already in the dictionary, which is what keeps
     * "its", "were", "cant", "hell" and every other contraction homograph untouched --
     * rewriting one of those would be real-word correction, the one thing this engine
     * refuses to do. That check also makes this cheap: it runs only for a word that was
     * going to be corrected regardless.
     */
    internal fun restoreApostrophe(normalized: String): String? {
        if (normalized.length < 2 || normalized.contains('\'')) return null
        if (frequencyOf(normalized) != null) return null

        var best: Pair<String, Int>? = null
        for (i in 1 until normalized.length) {
            val candidate =
                normalized.substring(0, i) + '\'' + normalized.substring(i)
            val freq = frequencyOf(candidate) ?: continue
            if (freq > (best?.second ?: 0)) best = candidate to freq
        }
        return best?.first?.let { displayContraction(it) }
    }

    /**
     * Every contraction reachable by putting one apostrophe into [normalized].
     *
     * The same insertion sweep [restoreApostrophe] does, without the gate that stops it
     * when the typed word is itself a dictionary entry. That gate is right for
     * autocorrect, which replaces, and wrong here, which offers: "ill", "were", "well",
     * "cant", "wont", "shell" and "its" are all real words *and* the bare forms of
     * common contractions, and the only safe way to reach the second reading is to put
     * it in the strip and let the user decide.
     *
     * Returns word and frequency so the caller can rank it against everything else
     * rather than pinning it to a made-up score.
     */
    internal fun apostropheVariantsOf(normalized: String): List<Pair<String, Int>> {
        if (normalized.length < 2 || normalized.contains('\'')) return emptyList()
        return (1 until normalized.length).mapNotNull { i ->
            val candidate = normalized.substring(0, i) + '\'' + normalized.substring(i)
            frequencyOf(candidate)?.let { displayContraction(candidate) to it }
        }
    }

    /**
     * Capitalizes the one English pronoun that is always capitalized.
     *
     * The dictionary is lowercase throughout, which is right for every other word --
     * case is restored from what the user typed. It cannot be for "i'm", "i'll", "i've"
     * and "i'd", where the correct form does not depend on what was typed and a
     * faithfully lowercase "i'll" reads as the feature being broken. Applied to the
     * contraction alone, so nothing else in the dictionary changes shape.
     */
    private fun displayContraction(word: String): String =
        if (word.startsWith("i'")) "I'" + word.substring(2) else word

    /**
     * Splits a run-on caused by hitting a key beside the space bar instead of the space.
     *
     * Tried before the edit-distance search because the two answer different questions: a
     * trie walk looks for one word close to what was typed, and there is none -- "thankbyou"
     * is not a near-miss of any single word. Only both halves being real, common words is
     * evidence enough to act on, which is why the frequency bar is applied to each.
     */
    internal fun splitOnMispressedSpace(normalized: String): String? {
        if (normalized.length < MIN_SPLIT_LENGTH) return null
        if (normalized.any { !it.isLetter() }) return null

        var best: Pair<String, Float>? = null
        for (i in MIN_SPLIT_PART until normalized.length - MIN_SPLIT_PART) {
            if (normalized[i] !in KeyProximity.spaceNeighbours) continue
            val left = normalized.substring(0, i)
            val right = normalized.substring(i + 1)
            val leftFreq = frequencyOf(left) ?: continue
            val rightFreq = frequencyOf(right) ?: continue
            if (leftFreq < MIN_SPLIT_FREQUENCY || rightFreq < MIN_SPLIT_FREQUENCY) continue
            // Rank by the weaker half: a split is only as believable as its least
            // convincing side, and scoring on the sum lets one very common word carry a
            // fragment that happens to be a rare dictionary entry.
            val score = minOf(leftFreq, rightFreq).toFloat()
            if (best == null || score > best!!.second) {
                best = "$left $right" to score
            }
        }
        // The whole string being a real word outranks any split of it: "carbon" must not
        // become "car on".
        if (best != null && frequencyOf(normalized) != null) return null
        return best?.first
    }

    /**
     * Gives [replacement] the capitalization of [original].
     *
     * Only two shapes are carried over, because only two are ever deliberate: an initial
     * capital (sentence start, which auto-capitalize produces on its own) and all caps.
     * Anything else is treated as lower case rather than guessed at.
     */
    internal fun matchCase(
        original: String,
        replacement: String,
    ): String =
        when {
            original.length > 1 && original.all { !it.isLetter() || it.isUpperCase() } ->
                replacement.uppercase()
            original.firstOrNull()?.isUpperCase() == true ->
                replacement.replaceFirstChar { it.uppercase() }
            else -> replacement
        }

    /**
     * Walks the trie carrying one weighted-edit row per node, with the
     * Damerau/optimal-string-alignment extension for adjacent transpositions.
     *
     * Transposition has to cost one edit or the whole feature misses the most
     * common class of keyboard typo: "teh", "hte", "adn" and "recieve" are all two
     * letters in the wrong order. Under plain Levenshtein each of those is two edits
     * from its target but one from some unrelated word, so "teh" corrected to
     * "ten". Scoring that edit as one needs the row from two levels up ([prevRow]) and
     * the character that produced [currentRow] ([prevChar]); both are null at the root.
     *
     * Costs are weighted rather than uniform -- see [COST_ADJACENT_SUBSTITUTE]. The row
     * arrays stay integers: the weights are small whole numbers on a scale where one
     * ordinary edit is [COST_SUBSTITUTE], so the dynamic programming is unchanged and
     * only the constants differ.
     */
    private fun dfsEditDistance(
        buf: ByteBuffer,
        offset: Int,
        currentWord: String,
        targetWord: String,
        currentRow: IntArray,
        prevRow: IntArray?,
        prevChar: Char?,
        maxErrors: Int,
        results: MutableList<Pair<Suggestion, Int>>,
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
            nextRow[0] = currentRow[0] + COST_GAP
            for (i in 1..targetWord.length) {
                val typed = targetWord[i - 1]
                val insertCost = nextRow[i - 1] + COST_GAP
                val deleteCost = currentRow[i] + COST_GAP
                val substitution =
                    when {
                        typed == childChar -> 0
                        // A key physically under the intended one is the likeliest
                        // mistake there is, and must outrank both dropping a letter and
                        // hitting something across the board. Without this the choice
                        // between two same-distance candidates falls back to raw
                        // frequency, which picks the commoner word every time -- "vall"
                        // became "all" rather than "call".
                        KeyProximity.areAdjacent(typed, childChar) ->
                            COST_ADJACENT_SUBSTITUTE
                        else -> COST_SUBSTITUTE
                    }
                var cost =
                    minOf(insertCost, deleteCost, currentRow[i - 1] + substitution)

                // Adjacent transposition: the candidate ends "prevChar, childChar"
                // where the target has those two the other way round.
                if (i >= 2 &&
                    prevRow != null &&
                    prevChar != null &&
                    targetWord[i - 1] == prevChar &&
                    targetWord[i - 2] == childChar
                ) {
                    cost = minOf(cost, prevRow[i - 2] + COST_TRANSPOSE)
                }

                nextRow[i] = cost
            }
            dfsEditDistance(
                buf,
                childOffset,
                currentWord + childChar,
                targetWord,
                nextRow,
                currentRow,
                childChar,
                maxErrors,
                results,
            )
        }
    }

    /** Frequency of an exact dictionary word, or null when it is not a terminal node. */
    private fun frequencyOf(word: String): Int? {
        val buf = dictionary() ?: return null
        return trieFrequencyOf(buf, word)
    }
}

/**
 * The weighted edit cost from [typed] to [candidate], on the same scale and with the same
 * transposition rule the trie walk uses.
 *
 * The trie walk computes this one row per node so it can prune; personal words are not in
 * the trie, and there are few enough of them to score each one directly. Keeping both on one
 * set of constants is what lets a learned word and a dictionary word compete fairly.
 */
internal fun weightedEditCost(
    typed: String,
    candidate: String,
): Int {
    val rows = Array(candidate.length + 1) { IntArray(typed.length + 1) }
    for (i in 0..typed.length) rows[0][i] = i * COST_GAP
    for (j in 1..candidate.length) {
        rows[j][0] = j * COST_GAP
        val c = candidate[j - 1]
        for (i in 1..typed.length) {
            val t = typed[i - 1]
            val substitution =
                when {
                    t == c -> 0
                    KeyProximity.areAdjacent(t, c) -> COST_ADJACENT_SUBSTITUTE
                    else -> COST_SUBSTITUTE
                }
            var cost =
                minOf(
                    rows[j][i - 1] + COST_GAP,
                    rows[j - 1][i] + COST_GAP,
                    rows[j - 1][i - 1] + substitution,
                )
            if (i >= 2 && j >= 2 && typed[i - 1] == candidate[j - 2] && typed[i - 2] == c) {
                cost = minOf(cost, rows[j - 2][i - 2] + COST_TRANSPOSE)
            }
            rows[j][i] = cost
        }
    }
    return rows[candidate.length][typed.length]
}
