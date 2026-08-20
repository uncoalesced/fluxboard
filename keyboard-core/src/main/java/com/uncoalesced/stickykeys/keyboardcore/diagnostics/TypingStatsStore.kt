// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.diagnostics

import android.content.Context
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Typing statistics for the user themselves, kept on this device and nowhere else.
 *
 * ## Not the same thing as [UsageRecorder]
 *
 * That one is a tester diagnostic which physically does not compile into a release build --
 * its debug/release split is two source sets, not a flag. This is the opposite by design: it
 * ships, it is always on, and what it holds is meant to be *shown to the person typing*.
 * They are separate classes because they answer to different audiences, not because the code
 * would not have merged.
 *
 * What it does share is the hard rule: counters and durations only. No typed text, no words,
 * no clipboard contents, no app or field identifiers. A statistic that quotes what somebody
 * typed would be a worse privacy problem than having no statistics at all. And there is no
 * network code here or anywhere downstream -- nothing uploads, queues, syncs or phones home.
 *
 * ## Why nothing is written on a keystroke
 *
 * Counters live in memory and reach SharedPreferences on [flush], called when the keyboard is
 * hidden -- the same shape and the same reason as `UsageLog`: an `apply()` per key press
 * would put a map copy and a background disk write on the exact path this release is trying
 * to make faster.
 *
 * ponytail: rolling window pruned lazily on flush rather than by a scheduled job. Seven
 * integers do not justify a WorkManager dependency.
 */
@Singleton
class TypingStatsStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs = context.getSharedPreferences("typing_stats", Context.MODE_PRIVATE)

        private var keystrokes = 0L
        private var backspaces = 0L
        private var autocorrectsAccepted = 0L
        private var autocorrectsUndone = 0L
        private var activeMs = 0L
        private var lastKeystrokeAt = 0L

        /**
         * One key press, and however much typing time it represents.
         *
         * Time is accrued between consecutive presses rather than from a session's wall
         * clock, and only when the gap is short enough to still be typing. A message composed
         * over ten minutes of thinking is not slow typing, and counting the pauses would make
         * every reported speed a measure of how distracted the user was.
         *
         * `elapsedRealtime`, not `uptimeMillis`: a pause that spans the device sleeping is
         * still a pause, and uptime stops counting through it.
         */
        @Synchronized
        fun recordKeystroke() {
            val now = SystemClock.elapsedRealtime()
            val gap = now - lastKeystrokeAt
            if (lastKeystrokeAt != 0L && gap in 0..IDLE_GAP_MS) {
                activeMs += gap
            }
            lastKeystrokeAt = now
            keystrokes++
        }

        @Synchronized
        fun recordBackspace() {
            backspaces++
        }

        @Synchronized
        fun recordAutocorrectAccepted() {
            autocorrectsAccepted++
        }

        @Synchronized
        fun recordAutocorrectUndone() {
            autocorrectsUndone++
        }

        /** Merges everything counted since the last call into storage. */
        @Synchronized
        fun flush() {
            if (keystrokes == 0L &&
                backspaces == 0L &&
                autocorrectsAccepted == 0L &&
                autocorrectsUndone == 0L
            ) {
                return
            }
            val today = LocalDate.now().toEpochDay()
            val edit = prefs.edit()
            edit.putLong(LIFETIME_KEYSTROKES, prefs.getLong(LIFETIME_KEYSTROKES, 0) + keystrokes)
            edit.putLong(LIFETIME_BACKSPACES, prefs.getLong(LIFETIME_BACKSPACES, 0) + backspaces)
            edit.putLong(
                LIFETIME_ACCEPTED,
                prefs.getLong(LIFETIME_ACCEPTED, 0) + autocorrectsAccepted,
            )
            edit.putLong(LIFETIME_UNDONE, prefs.getLong(LIFETIME_UNDONE, 0) + autocorrectsUndone)
            edit.putLong(LIFETIME_ACTIVE_MS, prefs.getLong(LIFETIME_ACTIVE_MS, 0) + activeMs)
            edit.putLong(dayKey(today), prefs.getLong(dayKey(today), 0) + keystrokes)
            // Pruned here rather than on a timer: this is the only moment new day keys appear,
            // so it is the only moment an old one can have fallen out of the window.
            prefs.all.keys
                .filter { it.startsWith(DAY_PREFIX) }
                .forEach {
                    val day = it.removePrefix(DAY_PREFIX).toLongOrNull()
                    if (day == null || day <= today - WINDOW_DAYS) edit.remove(it)
                }
            edit.apply()
            keystrokes = 0
            backspaces = 0
            autocorrectsAccepted = 0
            autocorrectsUndone = 0
            activeMs = 0
            lastKeystrokeAt = 0
        }

        /**
         * Everything the stats screen shows, including anything counted but not yet flushed.
         *
         * Reading storage alone would show a user who just opened Settings from the keyboard
         * a total that stops short of what they have typed this minute, which reads as the
         * feature being broken rather than as a flush boundary.
         */
        @Synchronized
        fun snapshot(): TypingStatsSnapshot {
            val today = LocalDate.now().toEpochDay()
            val totalKeystrokes = prefs.getLong(LIFETIME_KEYSTROKES, 0) + keystrokes
            val totalBackspaces = prefs.getLong(LIFETIME_BACKSPACES, 0) + backspaces
            val accepted = prefs.getLong(LIFETIME_ACCEPTED, 0) + autocorrectsAccepted
            val undone = prefs.getLong(LIFETIME_UNDONE, 0) + autocorrectsUndone
            val totalActiveMs = prefs.getLong(LIFETIME_ACTIVE_MS, 0) + activeMs
            val latency = LatencyTracker.snapshot()
            return TypingStatsSnapshot(
                keystrokes = totalKeystrokes,
                backspaces = totalBackspaces,
                activeMs = totalActiveMs,
                wordsPerMinute = wpm(totalKeystrokes, totalActiveMs),
                cleanKeystrokePercent =
                    if (totalKeystrokes == 0L) {
                        null
                    } else {
                        (100 * (totalKeystrokes - totalBackspaces) / totalKeystrokes)
                            .coerceIn(0, 100)
                            .toInt()
                    },
                correctionsKeptPercent =
                    if (accepted + undone == 0L) {
                        null
                    } else {
                        (100 * accepted / (accepted + undone)).toInt()
                    },
                keystrokesByDay =
                    ((today - WINDOW_DAYS + 1)..today).map { day ->
                        prefs.getLong(dayKey(day), 0) + if (day == today) keystrokes else 0
                    },
                latency = latency,
            )
        }

        @Synchronized
        fun clear() {
            prefs.edit().clear().apply()
            keystrokes = 0
            backspaces = 0
            autocorrectsAccepted = 0
            autocorrectsUndone = 0
            activeMs = 0
            lastKeystrokeAt = 0
            LatencyTracker.reset()
        }

        private fun dayKey(epochDay: Long) = "$DAY_PREFIX$epochDay"

        companion object {
            /**
             * Five characters to a word is the standard WPM convention, and it is a
             * convention rather than a fact -- stating it here is what stops the number being
             * read as a measurement of English.
             */
            fun wpm(
                keystrokes: Long,
                activeMs: Long,
            ): Int? {
                if (activeMs < MIN_ACTIVE_MS_FOR_WPM || keystrokes == 0L) return null
                return ((keystrokes / 5.0) / (activeMs / 60_000.0)).toInt()
            }

            /**
             * A gap longer than this is not typing. Three seconds is a judgement call: long
             * enough to cover looking for a key, short enough that composing a thought does
             * not count against the speed.
             */
            const val IDLE_GAP_MS = 3_000L

            /** Under this, one fast burst reads as a superhuman rate. */
            const val MIN_ACTIVE_MS_FOR_WPM = 10_000L

            const val WINDOW_DAYS = 7L

            private const val LIFETIME_KEYSTROKES = "lifetime_keystrokes"
            private const val LIFETIME_BACKSPACES = "lifetime_backspaces"
            private const val LIFETIME_ACCEPTED = "lifetime_autocorrects_accepted"
            private const val LIFETIME_UNDONE = "lifetime_autocorrects_undone"
            private const val LIFETIME_ACTIVE_MS = "lifetime_active_ms"
            private const val DAY_PREFIX = "day_"
        }
    }

/**
 * One read of the stats, with the two derived percentages kept apart on purpose.
 *
 * "Accuracy" has two defensible readings and they answer different questions, so both are
 * carried and each is labelled for what it actually measures rather than being averaged into
 * one number that means neither. [cleanKeystrokePercent] is how much of the typing was not
 * taken back -- it counts ordinary rewording as error, because from here the two are
 * indistinguishable. [correctionsKeptPercent] is how often autocorrect was left alone once it
 * acted, which is a statement about the engine rather than about the typist.
 *
 * Per-language counters are deliberately absent: there is one dictionary and one layout
 * language in the app today, so the breakdown would be a single row reading 100 percent.
 * See `.notes/plans/language-infra-plan.md` -- when a second language exists, the day keys
 * gain a language suffix and older data simply has no breakdown.
 */
data class TypingStatsSnapshot(
    val keystrokes: Long,
    val backspaces: Long,
    val activeMs: Long,
    val wordsPerMinute: Int?,
    val cleanKeystrokePercent: Int?,
    val correctionsKeptPercent: Int?,
    /** Oldest first, [TypingStatsStore.WINDOW_DAYS] entries, ending with today. */
    val keystrokesByDay: List<Long>,
    val latency: LatencySnapshot,
)
