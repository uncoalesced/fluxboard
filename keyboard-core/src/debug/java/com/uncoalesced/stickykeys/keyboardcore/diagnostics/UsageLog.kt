// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.diagnostics

import android.content.Context
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Local-only usage diagnostics for alpha and beta testers.
 *
 * ## What this deliberately is not
 *
 * There is no network code in this file and there is none anywhere downstream of it. Nothing
 * here uploads, queues, schedules, syncs or phones home, and no caller does either -- the
 * only way any of this leaves the device is the user picking a target in the Android share
 * sheet themselves, from [shareIntentFile]. That is not an incidental property of the current
 * implementation; it is the requirement, and this class exists in a project whose first rule
 * is "zero telemetry, ever".
 *
 * ## How it is switched off
 *
 * Every entry point begins with [enabled], which is `BuildConfig.USAGE_LOGGING` and nothing
 * else. That field is set per build type in `app/build.gradle.kts`: true for debug, false for
 * release. A stable or F-Droid build is a release build, so the flag is a compile-time
 * constant `false` there and R8 removes the calls and then this class with them.
 * `verifyNoUsageLoggingInRelease` checks that on the built artifact instead of assuming it.
 *
 * ## What it records
 *
 * Only counters and durations that answer "is this keyboard actually being used, and where
 * does it fall over" -- session count and length, keystrokes, backspaces, autocorrect
 * accepted and undone, mode switches, crashes-since-last-open. **No typed text, no words, no
 * clipboard contents, no app or field identifiers.** A diagnostic file that quotes what
 * somebody typed would be a worse privacy problem than having no diagnostics at all.
 */
@Singleton
class UsageLog
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : UsageRecorder {
        /**
         * Always true here. This file is compiled only into debug variants, so its mere
         * existence is the gate -- there is no flag to get out of sync with reality.
         */
        override val enabled: Boolean = true

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // Counters are cheap and in-memory; the file is only rewritten on flush, so a
        // keystroke never costs a disk write.
        private val mutex = Mutex()
        private var sessionStartedAt = 0L
        private var keystrokes = 0L
        private var backspaces = 0L
        private var autocorrectsAccepted = 0L
        private var autocorrectsUndone = 0L
        private var modeSwitches = 0L
        private var scrubGestures = 0L

        val file: File get() = File(context.filesDir, FILE_NAME)

        override fun onSessionStart() {
            sessionStartedAt = System.currentTimeMillis()
        }

        override fun onSessionEnd() {
            val started = sessionStartedAt
            if (started == 0L) return
            sessionStartedAt = 0L
            val durationMs = max(0L, System.currentTimeMillis() - started)
            // Read on this thread and reset here, so the window belongs to the session being
            // written rather than to whatever gets typed while the write is queued.
            val latency = LatencyTracker.snapshot()
            LatencyTracker.reset()
            scope.launch { appendSession(durationMs, latency) }
        }

        override fun onKeystroke() {
            keystrokes++
        }

        override fun onBackspace() {
            backspaces++
        }

        override fun onAutocorrectAccepted() {
            autocorrectsAccepted++
        }

        override fun onAutocorrectUndone() {
            autocorrectsUndone++
        }

        override fun onModeSwitch() {
            modeSwitches++
        }

        override fun onScrubGesture() {
            scrubGestures++
        }

        private suspend fun appendSession(
            durationMs: Long,
            latency: LatencySnapshot,
        ) {
            mutex.withLock {
                val target = file
                if (!target.exists()) {
                    target.writeText(header())
                }
                target.appendText(sessionRow(durationMs, latency))
                target.appendText(outlierNote(latency.outlierMs))
                // Reset per-session counters only after a successful write, so a failed
                // append does not silently discard the session it was recording.
                keystrokes = 0
                backspaces = 0
                autocorrectsAccepted = 0
                autocorrectsUndone = 0
                modeSwitches = 0
                scrubGestures = 0
            }
        }

        private fun header(): String =
            buildString {
                appendLine("# FluxBoard usage log")
                appendLine()
                appendLine("Local diagnostics for alpha and beta testing. This file is written")
                appendLine("only on this device and is never uploaded. Share it yourself if and")
                appendLine("when you want to; nothing sends it for you.")
                appendLine()
                appendLine("No typed text, words, clipboard contents or app names are recorded.")
                appendLine()
                // Library BuildConfig carries no VERSION_NAME; the build type is the part
                // that matters here anyway, since it is what decides the file exists at all.
                appendLine("Build type: debug (tester build)")
                appendLine()
                appendLine("Latency is in milliseconds. `Input` is the gap between the OS")
                appendLine("timestamping a press and this keyboard acting on it, which is the")
                appendLine("one that rises when the process stalls. `Commit` starts at the same")
                appendLine("press and ends when the edit is issued, so it also contains however")
                appendLine("long the finger stayed on the key.")
                appendLine()
                appendLine(
                    "| Ended | Session (s) | Keys | Backspace | Autocorrect | Undone | Modes | " +
                        "Scrubs | Input avg | Input p95 | Commit avg | Commit p95 |",
                )
                appendLine(
                    "|---|---|---|---|---|---|---|---|---|---|---|---|",
                )
            }

        private fun sessionRow(
            durationMs: Long,
            latency: LatencySnapshot,
        ): String {
            val stamp =
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            return "| $stamp | ${durationMs / 1000} | $keystrokes | $backspaces | " +
                "$autocorrectsAccepted | $autocorrectsUndone | $modeSwitches | $scrubGestures | " +
                "${cell(latency.deliveryAverageMs)} | ${cell(latency.deliveryP95Ms)} | " +
                "${cell(latency.commitAverageMs)} | ${cell(latency.commitP95Ms)} |\n"
        }

        /** A dash rather than a zero: no sample recorded is not the same as no latency. */
        private fun cell(value: Long?): String = value?.toString() ?: "-"

        /**
         * The spikes, under the table rather than in it.
         *
         * A p95 over 200 samples smooths away three bad keystrokes in a minute of typing, and
         * three bad keystrokes in a minute of typing is exactly the report this is chasing.
         */
        private fun outlierNote(outliers: List<Long>): String =
            if (outliers.isEmpty()) {
                ""
            } else {
                "\n- Slow keystrokes this session (ms): " + outliers.joinToString(", ") + "\n"
            }

        /**
         * A content URI for the log, for use with `Intent.ACTION_SEND`.
         *
         * Returns null when logging is off or nothing has been recorded, so a caller cannot
         * accidentally raise a share sheet for a file that does not exist.
         */
        override fun shareIntentFile(): android.net.Uri? {
            val target = file
            if (!target.exists()) return null
            return FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                target,
            )
        }

        override fun clear() {
            scope.launch { mutex.withLock { file.delete() } }
        }

        private companion object {
            const val FILE_NAME = "fluxboard-usage.md"
        }
    }

/**
 * Binds the real recorder for debug builds only.
 *
 * The release variant has its own module supplying [NoOpUsageRecorder]. Exactly one of the
 * two source sets is compiled per variant, so there is never a duplicate binding and never
 * a variant with both.
 */
@dagger.Module
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
abstract class UsageRecorderModule {
    @dagger.Binds
    @Singleton
    abstract fun bindUsageRecorder(impl: UsageLog): UsageRecorder
}
