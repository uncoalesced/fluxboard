// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * One sampled point of a glide, already resolved to the key under it.
 *
 * The key is resolved by the view against the real laid-out rectangles rather than recomputed
 * here from a model of the grid. That matters: the grid is user-remappable and can carry a
 * number row, so any geometry duplicated in this file would be wrong for exactly the users who
 * customised their layout. [x] and [y] stay in the path only so direction changes can be
 * measured; nothing here needs to know where a key *is*.
 */
data class GlidePoint(
    val x: Float,
    val y: Float,
    val key: Char?,
)

/**
 * A finished glide, reduced to what a decoder can actually use.
 *
 * @property keys the keys crossed, in order, with consecutive repeats collapsed
 * @property pivots indices into [keys] where the path changed direction sharply, or was an
 *   endpoint. These are the letters the user aimed at; everything else was crossed on the way.
 */
data class GlideStroke(
    val keys: List<Char>,
    val pivots: Set<Int>,
) {
    val isUsable: Boolean get() = keys.size >= MIN_KEYS

    companion object {
        /**
         * Below this a glide is indistinguishable from a slightly sloppy tap, and treating it
         * as a word would replace a deliberate single character with a guess.
         */
        const val MIN_KEYS = 3
    }
}

/**
 * Angle, in radians, below which a direction change does not count as aiming at a key.
 *
 * A glide is a curve, not a polyline: the finger is always turning slightly. Only a genuine
 * corner marks a letter the user meant, and this is the threshold between the two. Set wide
 * (about 60 degrees) because a false pivot is worse than a missed one -- pivots add confidence
 * to a candidate, so inventing them promotes whichever word happens to sit under a wobble.
 */
private const val PIVOT_ANGLE = 1.05f

/** Points closer together than this contribute no reliable direction and are skipped. */
private const val MIN_SEGMENT = 0.35f

/**
 * Reduces a raw sampled path to the sequence of keys it crossed and the corners it turned.
 *
 * Consecutive duplicates collapse, which is what makes the result a *path* through the board
 * rather than a sample count -- dwelling on a key produces dozens of samples and must not make
 * that key look more intended than one crossed quickly.
 *
 * Coordinates are expected in key widths, so [MIN_SEGMENT] means a fraction of a key rather
 * than a number of pixels. The view divides by key width before calling.
 */
fun buildGlideStroke(points: List<GlidePoint>): GlideStroke {
    val onKeys = points.filter { it.key != null }
    if (onKeys.isEmpty()) return GlideStroke(emptyList(), emptySet())

    // Collapse to the key sequence, remembering where in the raw path each key began so
    // pivots can be attributed back to a key rather than to a sample index.
    val keys = mutableListOf<Char>()
    val firstSampleOfKey = mutableListOf<Int>()
    onKeys.forEachIndexed { index, p ->
        val c = p.key ?: return@forEachIndexed
        if (keys.isEmpty() || keys.last() != c) {
            keys.add(c)
            firstSampleOfKey.add(index)
        }
    }

    val pivots = mutableSetOf<Int>()
    // The endpoints are always deliberate: a glide starts and ends where the user put it down
    // and lifted off, which is the strongest signal in the whole path.
    pivots.add(0)
    pivots.add(keys.lastIndex)

    for (k in 1 until keys.lastIndex) {
        val at = firstSampleOfKey[k]
        val before = seekBack(onKeys, at)
        val after = seekForward(onKeys, at)
        if (before == null || after == null) continue
        val incoming = atan2(onKeys[at].y - before.y, onKeys[at].x - before.x)
        val outgoing = atan2(after.y - onKeys[at].y, after.x - onKeys[at].x)
        if (angleBetween(incoming, outgoing) >= PIVOT_ANGLE) pivots.add(k)
    }

    return GlideStroke(keys, pivots)
}

private fun seekBack(
    points: List<GlidePoint>,
    from: Int,
): GlidePoint? {
    for (i in from - 1 downTo 0) {
        if (hypot(points[from].x - points[i].x, points[from].y - points[i].y) >= MIN_SEGMENT) {
            return points[i]
        }
    }
    return null
}

private fun seekForward(
    points: List<GlidePoint>,
    from: Int,
): GlidePoint? {
    for (i in from + 1 until points.size) {
        if (hypot(points[i].x - points[from].x, points[i].y - points[from].y) >= MIN_SEGMENT) {
            return points[i]
        }
    }
    return null
}

/** Smallest angle between two headings, in radians, always in 0..PI. */
private fun angleBetween(
    a: Float,
    b: Float,
): Float {
    var d = abs(a - b)
    while (d > 2 * Math.PI) d -= (2 * Math.PI).toFloat()
    return min(d, (2 * Math.PI).toFloat() - d)
}

/**
 * Whether [word] could have produced [stroke], and how well it fits.
 *
 * Returns null when the word is not a possible reading of the path at all; otherwise a score
 * where **lower is better**, to match the cost convention the edit-distance search already
 * uses.
 *
 * The rule is that the word's letters must appear along the path **in order**. Skipping a key
 * the path crossed is free, because a glide crosses everything between the letters it meant --
 * that is what makes this different from spell-checking, where every character is evidence.
 * Skipping a letter of the *word* is not free, and is only allowed at all because a fast glide
 * can cut a corner and miss the key it was aiming at.
 *
 * Two cases carry the whole design:
 *
 *  - **Doubled letters.** "hello" cannot cross `l` twice; a finger has no way to visit one key
 *    twice in a row without a loop nobody makes. A doubled letter therefore matches the same
 *    position as its predecessor rather than advancing. Without this, every word with a double
 *    letter is unreachable by gliding -- and they are common enough that the feature would feel
 *    broken rather than imperfect.
 *  - **Clipped corners.** At speed the finger rounds the turn and lands on a neighbour instead
 *    of the target, so a letter is allowed to match a key adjacent to it at a cost. Without
 *    this, glides get faster and stop working, which reads as the feature being unreliable
 *    rather than as the user being sloppy.
 */
fun scoreGlideCandidate(
    word: String,
    stroke: GlideStroke,
): Int? {
    if (word.length < 2 || stroke.keys.isEmpty()) return null

    // The endpoints anchor everything. They are the two points the user certainly chose, so a
    // candidate that starts or ends somewhere else is not a reading of this path -- it is a
    // different word that happens to share the middle.
    if (!matchesKey(word.first(), stroke.keys.first())) return null
    if (!matchesKey(word.last(), stroke.keys.last())) return null

    var cost = 0
    var pathIndex = 0
    var lastMatchedIndex = -1
    var pivotsHit = 0

    for ((letterIndex, letter) in word.withIndex()) {
        // A doubled letter re-uses the position its predecessor matched, because the path
        // physically cannot have visited that key twice in succession.
        if (letterIndex > 0 && letter == word[letterIndex - 1] && lastMatchedIndex >= 0) {
            cost += COST_DOUBLE_LETTER
            continue
        }

        var found = -1
        var foundCost = 0
        var scan = pathIndex
        while (scan < stroke.keys.size) {
            val onPath = stroke.keys[scan]
            if (onPath == letter) {
                found = scan
                foundCost = 0
                break
            }
            if (KeyProximity.areAdjacent(onPath, letter)) {
                // Remember the first near miss but keep looking for an exact hit further
                // along; an exact match is always the better reading.
                if (found < 0) {
                    found = scan
                    foundCost = COST_NEIGHBOUR
                }
            }
            scan++
        }
        if (found < 0) return null

        cost += foundCost
        // Every key the path crossed between the previous letter and this one was incidental.
        // Free, deliberately: penalising them would rank short words above long ones purely
        // because a long word's path crosses more of the board.
        if (stroke.pivots.contains(found)) pivotsHit++
        lastMatchedIndex = found
        pathIndex = found + 1
    }

    // A pivot the word did not account for is a corner the user turned for some other word.
    val unexplainedPivots = (stroke.pivots.size - pivotsHit).coerceAtLeast(0)
    cost += unexplainedPivots * COST_UNEXPLAINED_PIVOT
    return cost
}

private fun matchesKey(
    letter: Char,
    key: Char,
): Boolean = letter == key || KeyProximity.areAdjacent(key, letter)

/**
 * Free, and it has to be.
 *
 * This was 1 -- a nudge, on the reasoning that a doubled letter is slightly unusual. On a
 * device that turned out to decide real words wrongly: "good" and "god" are both readings of
 * the same path, and the doubled one lost by exactly this point every time. So did "too"
 * against "to", "been" against "ben", "week" against "wek".
 *
 * The reasoning was wrong rather than mistuned. A glide cannot show a doubled letter at all --
 * the finger has no way to visit one key twice in succession -- so the path carries *no
 * evidence either way*. Charging for it does not express uncertainty, it invents a preference
 * against every doubled word in English. With it at zero the two readings tie on structure and
 * frequency decides, which is the only signal that actually distinguishes them.
 */
private const val COST_DOUBLE_LETTER = 0

/** Landing on a neighbour instead of the target is what fast gliding does. */
private const val COST_NEIGHBOUR = 3

/**
 * A corner the candidate does not explain.
 *
 * Weighted heavily because it is the main thing separating candidates that are all valid
 * subsequences of the same path. Gliding "hello" crosses enough of the board that "ho", "hell"
 * and "hollow" are all readings of it; the corners are what say which one was drawn.
 */
private const val COST_UNEXPLAINED_PIVOT = 6
