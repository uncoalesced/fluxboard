// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

// What a long press offers: the LongPress model, the alternates tables, and the strip state
// that tracks which cell is under the finger.

/**
 * What a key does when it is held rather than tapped.
 *
 * One type for every hold behaviour on the board, because they are the same gesture with
 * different payloads: the press/threshold/release state machine is identical and only what
 * happens after the threshold differs. Three separate per-key handlers would each need their
 * own copy of the timing, their own cancellation handling, and their own interaction with the
 * tap path -- which is how "held backspace sometimes types a character" bugs happen.
 */
internal sealed interface LongPress {
    /** Tap-only. */
    data object None : LongPress

    /** Fire the key's own output over and over, faster the longer it is held. Backspace. */
    data object Repeat : LongPress

    /**
     * Show a pick-one strip above the key. Punctuation alternates, top-row digits.
     *
     * [defaultIndex] is the cell selected when the strip opens, and therefore what a release
     * without any sideways drag commits. It is 0 almost everywhere; the currency key is the
     * exception, because the character its own face shows sits in the middle of the run of
     * currencies rather than at the start.
     */
    data class Alternates(
        val options: List<String>,
        val defaultIndex: Int = 0,
    ) : LongPress

    /** Drag sideways to walk the caret instead of typing. The space bar. */
    data object Scrub : LongPress
}

/**
 * How far the finger must travel before it starts choosing cells in an alternates strip.
 *
 * Well below one cell, because this is not a selection threshold -- it only has to absorb the
 * jitter of a stationary finger so that a hold-and-release keeps the strip's default cell.
 * Past it, every cell including the first stays reachable by dragging back.
 */
internal const val ALTERNATE_DRAG_SLOP_DP = 6f

/**
 * Punctuation reachable by holding, so common marks do not cost a trip to the symbols page.
 *
 * Ordered by how often they are actually wanted -- the first entry is what a TalkBack
 * long-press commits, and what the finger is already over when the strip appears.
 */
private val PUNCTUATION_ALTERNATES =
    mapOf(
        "." to listOf(",", "?", "!", ";", ":", "'"),
        "," to listOf(".", "?", "!"),
    )

/**
 * Every currency the board offers, and the cell selected when the strip opens.
 *
 * One list, reached from two keys -- the symbols page's `$` and digit 4 on the number row --
 * because a user who found `€` under one of them and not the other would reasonably conclude
 * the keyboard had lost it. Still attached to each [KeyDefinition] individually rather than
 * looked up by output: both keys type `$`, and an output-keyed table is exactly the shape that
 * gave the symbols page digit 4's fraction strip.
 *
 * `$` sits at index 2 because the list reads in a conventional order rather than starting with
 * the key's own face -- so the default cell has to be named rather than assumed to be first.
 */
internal val CURRENCY_ALTERNATES = listOf("€", "¥", "$", "¢", "₹")
internal const val CURRENCY_DEFAULT_INDEX = 2

/**
 * What holding a digit on the number row offers.
 *
 * **The shifted symbol is deliberately not here**, with one named exception below. It used to
 * lead every strip, on the reasoning that the corner hint promised it. But the same character
 * is already directly reachable by arming shift, which swaps the whole row to real
 * shifted-symbol keys, so the cell was a second route to something one tap away and it pushed
 * the content the strip exists for down the row. The corner hint stays as information -- it
 * still tells the user what shift will produce -- it simply is no longer what the hold commits.
 *
 * A release without any sideways drag therefore commits the superscript, which is index 0.
 *
 * Ordering is superscript, then the vulgar fractions with that digit as numerator ascending by
 * value, then any superscript letter that belongs with it. There is no length ceiling: the
 * strip sizes its own cells to fit (see [alternateCellWidthPx]).
 *
 * **Digit 4 is the exception, by decision rather than oversight.** It carries the currencies
 * instead of `⁴ ⅘`, and keeps `$` among them even though `$` is its own shifted symbol. The
 * general rule exists because a duplicated cell crowds out content the strip is *for*; here
 * the currencies are the content, `$` belongs beside them to read as a set rather than as four
 * strays, and it is the cell a straight hold should commit. So the trade the rule was written
 * to prevent does not arise: the superscript and fraction were dropped for the currencies, not
 * squeezed alongside them. Six cells is the practical ceiling on a narrow phone and this is
 * five. See [DIGIT_ALTERNATES_DEFAULT_INDEX] for the cell it opens on.
 *
 * `1` and `5` are Joel's confirmed spec, quoted rather than derived -- note that `1` is a
 * *curated* subset of the nine numerator-1 fractions, so it cannot be generated. The remaining
 * digits were proposed rather than inferred from it, and are confirmed as shipped.
 *
 * Attached to the keys themselves rather than consulted here -- see [KeyDefinition.alternates]
 * for why an output-keyed table was the wrong shape.
 */
internal val DIGIT_ALTERNATES =
    mapOf(
        "0" to listOf("⁰"),
        "1" to listOf("¹", "⅛", "¼", "⅓", "½", "ⁱ"),
        "2" to listOf("²", "⅔", "⅖"),
        "3" to listOf("³", "¾", "⅗", "⅜"),
        "4" to CURRENCY_ALTERNATES,
        "5" to listOf("⁵", "⅝", "⅚", "ⁿ"),
        "6" to listOf("⁶"),
        "7" to listOf("⁷", "⅞"),
        "8" to listOf("⁸"),
        "9" to listOf("⁹"),
    )

/**
 * Which cell of a digit's strip opens selected, for the digits where it is not the first.
 *
 * A map with one entry rather than a field on every digit: index 0 is right for a strip that
 * leads with its superscript, and naming the exception is what keeps the rule readable. This
 * is also what a TalkBack long-press commits, so it is behaviour rather than presentation --
 * getting it wrong gives screen-reader users a different character from everyone else.
 */
internal val DIGIT_ALTERNATES_DEFAULT_INDEX = mapOf("4" to CURRENCY_DEFAULT_INDEX)

/**
 * Width of one alternates cell, shrunk when the strip would otherwise run off the screen.
 *
 * The strip used to assume every cell could have its full preferred width, and the only thing
 * standing between that and an unreachable cell was a hard six-entry ceiling asserted in a
 * test. Remove the ceiling without this and a long strip is clipped at the screen edge: the
 * cells past the edge are drawn nowhere and, because selection is computed from the same cell
 * width, they cannot be selected either.
 *
 * Pure so the arithmetic is assertable without a screen -- which matters, because the failure
 * it prevents is invisible until someone tries the last cell of a long strip on a narrow phone.
 */
internal fun alternateCellWidthPx(
    optionCount: Int,
    availableWidthPx: Float,
    preferredWidthPx: Float,
): Float {
    if (optionCount <= 0) return preferredWidthPx
    val fits = availableWidthPx / optionCount
    return minOf(preferredWidthPx, fits).coerceAtLeast(1f)
}

/**
 * The hold behaviour for a key.
 *
 * Driven by the layout's own [hint] rather than a table keyed on letters. A hardcoded map
 * would go stale the moment a custom layout moved a key, and would disagree with the corner
 * glyph the user can see -- the promise the superscript makes is precisely "hold this and
 * you get that", so the two must come from one field.
 *
 * Hints that are not a single character are labels, not outputs: `,!?` advertises that
 * alternates exist, and `MIC` names an icon. Neither is typeable, so both fall through to
 * the explicit tables.
 */
internal fun longPressFor(
    keyOutput: String,
    hint: String? = null,
    alternates: List<String>? = null,
    alternatesDefaultIndex: Int = 0,
): LongPress {
    if (keyOutput == "SPACE") return LongPress.Scrub
    if (keyOutput == "DEL") return LongPress.Repeat
    // The key's own alternates win over every shared table. This is what keeps two keys that
    // type the same character from inheriting each other's hold behaviour -- the digit row and
    // the symbols page both carry "1", and the currency key types the same "$" as digit 4's
    // shifted symbol.
    if (alternates != null && alternates.isNotEmpty()) {
        return LongPress.Alternates(
            alternates,
            alternatesDefaultIndex.coerceIn(0, alternates.lastIndex),
        )
    }
    PUNCTUATION_ALTERNATES[keyOutput]?.let { return LongPress.Alternates(it) }
    if (hint != null && hint.length == 1) return LongPress.Alternates(listOf(hint))
    return LongPress.None
}

/**
 * The alternates strip, hoisted out of the key that opened it.
 *
 * It has to outlive the key's own bounds: the finger routinely slides off the originating key
 * while choosing, and a popup owned by that key would be dismissed by its own pointer-exit.
 * Holding it at the grid level also means exactly one strip can be open at a time, which is
 * what stops a second finger opening a competing one.
 */
@Stable
internal class KeyAlternatesState {
    var options by mutableStateOf<List<String>>(emptyList())
        private set

    /** The originating key's bounds, in root coordinates. Null when nothing is showing. */
    var anchor by mutableStateOf<Rect?>(null)
        private set

    var selectedIndex by mutableIntStateOf(0)
        private set

    /**
     * The width one cell was actually given, which is not always the preferred width.
     *
     * Read by the strip when it draws. Drawing from the shared constant while selecting from
     * this value is how a long strip ends up highlighting a different cell than the one under
     * the finger, so both sides take the same number from here.
     */
    var cellWidthPx by androidx.compose.runtime.mutableFloatStateOf(1f)
        private set

    val visible: Boolean get() = anchor != null

    fun show(
        options: List<String>,
        anchor: Rect,
        cellWidthPx: Float,
        defaultIndex: Int = 0,
    ) {
        this.options = options
        this.anchor = anchor
        this.cellWidthPx = cellWidthPx.coerceAtLeast(1f)
        // Not always zero. A release with no drag commits whatever is selected here, so this
        // is what decides the "just hold it" character.
        this.selectedIndex = defaultIndex.coerceIn(0, maxOf(0, options.lastIndex))
    }

    /** Track the finger across the strip. [x] is in root coordinates. */
    fun moveTo(x: Float) {
        val bounds = anchor ?: return
        if (options.isEmpty()) return
        val offset = ((x - bounds.left) / cellWidthPx).toInt()
        selectedIndex = offset.coerceIn(0, options.lastIndex)
    }

    /** Hide, returning whatever was under the finger. */
    fun consume(): String? {
        val picked = options.getOrNull(selectedIndex)
        hide()
        return picked
    }

    fun hide() {
        anchor = null
        options = emptyList()
        selectedIndex = 0
    }
}
