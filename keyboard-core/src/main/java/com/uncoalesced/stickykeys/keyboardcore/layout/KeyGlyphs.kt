// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.layout

import android.view.inputmethod.EditorInfo
import androidx.annotation.DrawableRes
import com.uncoalesced.stickykeys.keyboardcore.R

/**
 * How a key draws itself: either a glyph string or a vector.
 *
 * The two cases are separate types rather than a nullable icon plus a fallback string,
 * because every call site has to branch on it anyway to pick `Text` or `Icon`, and a
 * "label that might secretly be an icon" is what let the three surfaces drift apart.
 */
sealed interface KeyGlyph {
    data class Label(
        val text: String,
    ) : KeyGlyph

    data class Icon(
        @DrawableRes val res: Int,
        val description: String,
    ) : KeyGlyph
}

/** Which shift artwork applies. The live keyboard knows its mode; static previews do not. */
enum class ShiftRendering { OFF, LATCHED, LOCKED }

/**
 * The one place that decides what any key looks like.
 *
 * There used to be four of these tables and all four disagreed. The live keyboard drew
 * `SHIFT` as U+21E7, the theme preview did `keyLabel.take(1)` -- which rendered SHIFT, SPACE
 * and SYMBOLS all as the single letter "S" -- and the layout editor had its own "Sh"/"Del"/
 * "Ent" abbreviations. A user comparing a theme preview against the real keyboard was
 * looking at two different keyboards.
 *
 * Icons rather than glyph characters for the special keys: U+232B and U+23CE are not in
 * every system font and fell back to tofu boxes on this project's own test device.
 *
 * [displayLabel] still wins when a custom layout sets one, so user layouts keep control of
 * their own key captions.
 */
fun keyGlyph(
    keyOutput: String,
    displayLabel: String? = null,
    shift: ShiftRendering = ShiftRendering.OFF,
    enterAction: Int = EditorInfo.IME_ACTION_UNSPECIFIED,
): KeyGlyph {
    displayLabel?.let { return KeyGlyph.Label(it) }
    return when (keyOutput) {
        "SHIFT" ->
            when (shift) {
                ShiftRendering.OFF -> KeyGlyph.Icon(R.drawable.ic_key_shift, "Shift")
                ShiftRendering.LATCHED -> KeyGlyph.Icon(R.drawable.ic_key_shift_on, "Shift")
                ShiftRendering.LOCKED -> KeyGlyph.Icon(R.drawable.ic_key_shift_lock, "Caps lock")
            }
        "DEL" -> KeyGlyph.Icon(R.drawable.ic_key_backspace, "Backspace")
        // The return arrow everywhere except a send field.
        //
        // Send is the one action worth drawing differently, because it is the one where the
        // key does something the user cannot take back: the message leaves. Search, Go, Next
        // and Done all previously had their own glyph and no longer do -- five different
        // pictures on one key taught nobody anything, and the return arrow is what people
        // already read as "Enter". Joel's decision, 2026-08-28, and it reverses his own
        // 2026-08-16 call that the Done tick was correct; the tick is now the return arrow
        // like everything else that is not Send.
        //
        // Driven by the action the field declares, never by which app is in front. WhatsApp,
        // Messenger and the AOSP messaging app all set IME_ACTION_SEND, so they get the paper
        // plane without this keyboard knowing their names -- and a package list would be both
        // fragile and against this project's no-app-heuristics position.
        //
        // Deliberately not gated on `enterInsertsNewline` being false: a field may both accept
        // newlines and declare an action, and Send is still the honest icon there. The newline
        // case is exactly IME_ACTION_UNSPECIFIED or IME_ACTION_NONE, which fall to the else
        // branch on their own -- and those two are a trap worth naming, because UNSPECIFIED is
        // 0 while NONE is 1, so the obvious `action != IME_ACTION_NONE` test treats a plain
        // text field as actionable.
        "ENTER" ->
            when (enterAction) {
                EditorInfo.IME_ACTION_SEND -> KeyGlyph.Icon(R.drawable.ic_key_enter_send, "Send")
                else -> KeyGlyph.Icon(R.drawable.ic_key_enter, "Enter")
            }
        // Two doors into the same picker, and they must not look alike: a user who put both on
        // one board could otherwise not tell which key was which. The face is emoji, the grid is
        // the sticker library -- the same two glyphs the quick-access row already uses for them.
        "EMOJI" -> KeyGlyph.Icon(R.drawable.ic_key_emoji, "Emoji")
        "STICKERS" -> KeyGlyph.Icon(R.drawable.ic_quick_grid, "Stickers")
        "CLIPBOARD" -> KeyGlyph.Icon(R.drawable.ic_key_clipboard, "Clipboard history")
        "MIC" -> KeyGlyph.Icon(R.drawable.ic_key_mic, "Voice input")
        "SYMBOLS" -> KeyGlyph.Label("123")
        // Per the supplied symbols-page reference, which labels this key "{&=".
        "SYMBOLS_SHIFT" -> KeyGlyph.Label("{&=")
        "ABC" -> KeyGlyph.Label("ABC")
        "SPACE" -> KeyGlyph.Label(" ")
        else -> KeyGlyph.Label(keyOutput)
    }
}
