// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.diagnostics

import android.net.Uri

/**
 * Where usage counters go. Which implementation exists is decided by the build variant.
 *
 * ## Why this is an interface and not a boolean
 *
 * The first attempt gated a single `UsageLog` class behind `BuildConfig.USAGE_LOGGING`. That
 * does not do what the requirement asks. The class is constructor-injected into
 * `TypingViewModel` and field-injected into the IME, so Hilt's generated factories reference
 * it unconditionally and R8 keeps it -- flag or no flag. The build-time check caught exactly
 * that: with the flag set to `false`, `UsageLog` was still present in the release mapping.
 *
 * A runtime boolean cannot make a class stop existing. Source sets can.
 *
 * - `src/debug`  supplies the real recorder, which writes the file.
 * - `src/release` supplies [NoOpUsageRecorder], which holds no state and touches no disk.
 *
 * A stable or F-Droid build is a release build, so the recording implementation is never
 * compiled into it at all. `verifyNoUsageLoggingInRelease` asserts that against R8's own
 * mapping output, and has been checked to fail when the guarantee is broken rather than
 * being assumed to work.
 */
interface UsageRecorder {
    /** False in release. Callers use it to hide tester-only UI, not to gate recording. */
    val enabled: Boolean

    fun onSessionStart()

    fun onSessionEnd()

    fun onKeystroke()

    fun onBackspace()

    fun onAutocorrectAccepted()

    fun onAutocorrectUndone()

    fun onModeSwitch()

    fun onScrubGesture()

    /** A content URI for the log, or null when there is nothing to share. */
    fun shareIntentFile(): Uri?

    fun clear()
}
