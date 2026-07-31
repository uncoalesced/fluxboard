// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

/**
 * Which keys a finger can plausibly hit instead of the one it was aiming for.
 *
 * Autocorrect without this treats every substitution as equally likely, so a candidate is
 * chosen on raw dictionary frequency alone. Measured against real typos that produces
 * "vall" -> "all" (deleting a letter) rather than "call" (the key physically under 'v'), and
 * "gine" -> "line" rather than "gone". Both wrong answers are the *more frequent* word, which
 * is exactly what a frequency-only tie-break will always pick.
 *
 * Derived from the shipped QWERTY geometry rather than hand-listed per letter: the rows are
 * offset by the same stagger the layout draws, and neighbours are every key whose centre is
 * within a key and a half. Hand-listing would drift the moment a row changed, and gets the
 * diagonals wrong -- which is where most mispresses land.
 */
internal object KeyProximity {
    private val ROWS =
        listOf(
            "qwertyuiop",
            "asdfghjkl",
            "zxcvbnm",
        )

    /**
     * Horizontal offset of each row, in key widths. These are the real proportions of the
     * layout this keyboard draws: the home row is inset by a quarter key and the bottom row
     * by three quarters, which is what makes 'v' sit under the gap between 'f' and 'g'.
     */
    private val ROW_OFFSET = listOf(0f, 0.25f, 0.75f)

    /** Centre-to-centre distance, in key widths, at which two keys count as neighbours. */
    private const val NEIGHBOUR_RADIUS = 1.3f

    private val neighbours: Map<Char, Set<Char>> = buildNeighbours()

    private fun buildNeighbours(): Map<Char, Set<Char>> {
        data class Pos(
            val c: Char,
            val x: Float,
            val y: Float,
        )

        val positions =
            ROWS.flatMapIndexed { rowIndex, row ->
                row.mapIndexed { column, c ->
                    Pos(c, column + ROW_OFFSET[rowIndex], rowIndex.toFloat())
                }
            }
        return positions.associate { key ->
            key.c to
                positions
                    .filter { other ->
                        other.c != key.c &&
                            run {
                                val dx = other.x - key.x
                                val dy = other.y - key.y
                                dx * dx + dy * dy <= NEIGHBOUR_RADIUS * NEIGHBOUR_RADIUS
                            }
                    }.map { it.c }
                    .toSet()
        }
    }

    /** True when [typed] and [intended] are close enough that a mispress explains the swap. */
    fun areAdjacent(
        typed: Char,
        intended: Char,
    ): Boolean = neighbours[typed]?.contains(intended) == true

    /**
     * Letters sitting along the space bar's top edge.
     *
     * A thumb that lands low on the board hits one of these instead of the space, producing a
     * run-on with one junk letter where the gap belonged ("thank" + "b" + "you"). No amount of
     * edit-distance search finds that: the result is not a misspelled word, it is two correct
     * words joined by a character.
     */
    val spaceNeighbours: Set<Char> = setOf('b', 'n', 'v', 'c', 'm')
}
