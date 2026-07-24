# Manual Test Script: Keyboard Typing Flow

This script outlines the end-to-end journey for switching to the StickyKeys IME, typing, receiving predictions, and committing text to a target app. Full automation is impractical here because standard instrumented tests cannot seamlessly interact with system-level IME switching dialogs or simulate the IPC InputConnection boundary faithfully without root access.

## Prerequisites
1. Open the Android System Settings > System > Languages & Input > On-screen keyboard.
2. Enable **StickyKeys** and accept the system warning.
3. Open any app with a standard text field (e.g., Messages, Keep Notes, or a browser).

## Execution Steps

1. **Activate the IME**
   - Tap into the text field to bring up the current keyboard.
   - Use the navigation bar keyboard switcher icon (or long-press spacebar on some ROMs) to switch the active keyboard to **StickyKeys**.
   - *Expected:* The StickyKeys custom UI appears.

2. **Basic Typing**
   - Type the characters: `h`, `e`, `l`, `l`, `o`.
   - *Expected:* The characters are committed to the text field instantly.

3. **Predictive Text / Suggestions**
   - Type the characters: `s`, `t`, `i`, `c`.
   - Look at the top suggestion bar of the keyboard.
   - *Expected:* Relevant dictionary words (e.g., "stick", "sticky") appear in the suggestion slots.

4. **Suggestion Commit**
   - Tap one of the suggested words from the top bar.
   - *Expected:* The remaining characters of the word are automatically committed to the text field, and a space is appended.

5. **Auto-Capitalization**
   - Ensure Auto-Capitalization is enabled in StickyKeys Settings.
   - Type a period (`.`) followed by a space.
   - Type the character `a`.
   - *Expected:* The character is automatically committed as uppercase `A`.

6. **Clipboard History Access**
   - Copy a piece of text from another app.
   - Tap the Clipboard icon on the StickyKeys toolbar.
   - *Expected:* The copied text appears in the history list (unless it was marked with the sensitive content flag, like a password).
   - Tap the clipboard entry.
   - *Expected:* The text is committed to the active text field.
