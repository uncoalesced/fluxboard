// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

/**
 * How long after a shift tap a second tap still counts as a double tap.
 *
 * The platform double-tap timeout is 300ms, which is right for a control the user is aiming
 * at twice deliberately. Caps lock is not that: the second tap is a decision made *after*
 * seeing the first one take effect, so it arrives later than a double click on a link would.
 * Long enough to be reachable without hurrying, short enough that a tap the user thinks of as
 * "turn shift back off" is never mistaken for it.
 */
const val CAPS_LOCK_WINDOW_MS = 1_000L

/**
 * What the shift key does next.
 *
 * The old behaviour was a three-way cycle -- lower, upper, caps lock -- which put caps lock
 * one tap away from lower case and made it easy to latch by accident. Worse, escaping it took
 * a *third* tap, so a user who overshot had to cycle all the way round rather than press the
 * key again to undo what they had just done.
 *
 * The replacement uses timing rather than position in a cycle, which is what lets one key mean
 * two different things without either being hidden:
 *
 *  - From lower case, a tap arms shift for the next letter.
 *  - A second tap **soon after** latches caps lock. It reads as a double tap, which is the
 *    gesture every platform uses for exactly this.
 *  - A second tap **later** turns shift back off, because by then it is a separate decision
 *    about a shift the user can see is already on.
 *  - From caps lock, a tap always returns to lower case, whenever it comes. Escaping a latch
 *    must never depend on timing -- that is the one case where getting it wrong strands the
 *    user in capitals.
 *
 * Pure, because the interesting part is entirely about which side of the window a tap fell on
 * and that is miserable to reproduce by hand on a device.
 *
 * @param elapsedSinceLastShiftTapMs time since the previous shift tap. Pass [Long.MAX_VALUE]
 *   when there has not been one, so the first tap of a session can never read as a double.
 */
fun nextShiftMode(
    current: KeyboardMode,
    elapsedSinceLastShiftTapMs: Long,
    windowMs: Long = CAPS_LOCK_WINDOW_MS,
): KeyboardMode =
    when (current) {
        KeyboardMode.LETTERS_LOWER -> KeyboardMode.LETTERS_UPPER
        KeyboardMode.LETTERS_UPPER ->
            if (elapsedSinceLastShiftTapMs <= windowMs) {
                KeyboardMode.LETTERS_CAPS_LOCK
            } else {
                KeyboardMode.LETTERS_LOWER
            }
        // Always escapable, regardless of timing.
        KeyboardMode.LETTERS_CAPS_LOCK -> KeyboardMode.LETTERS_LOWER
        // Shift pressed from a symbols page or the PIN pad returns to letters, which is the
        // only sensible reading of it there.
        else -> KeyboardMode.LETTERS_LOWER
    }

/**
 * The mode a printable key leaves behind once it has consumed one-shot shift.
 *
 * Null means "leave the mode alone" -- caps lock is not consumed by typing, and a board already
 * in lower case has nothing to release.
 *
 * The [autoCapitalize] half is why this is a function rather than an `if`. A key that ends a
 * sentence *re-arms* shift at the same moment it consumes it, and the auto-capitalize effect
 * that would otherwise put the board back into upper case only re-runs when the flag *changes*.
 * Type "Hello.", backspace the stop and retype it and the flag is already true both times, so
 * nothing fires: the unconditional downgrade dropped the board to lower case at a sentence start
 * with nothing to correct it until the next word. Enter and the space bar had always read the
 * flag at this point; the printable-key path had not.
 */
fun modeAfterPrintableKey(
    current: KeyboardMode,
    autoCapitalize: Boolean,
): KeyboardMode? =
    when {
        current != KeyboardMode.LETTERS_UPPER -> null
        autoCapitalize -> null
        else -> KeyboardMode.LETTERS_LOWER
    }
