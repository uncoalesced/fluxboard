// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.diagnostics

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-press outcomes, for finding taps that do not register. Debug builds only.
 *
 * Two kinds of row. `down` is written by an observer on the whole key grid the moment a finger
 * lands, before any key reacts. `press` is written by the key that handled the touch, once its
 * gesture has resolved. Lining the two up over a fixed-sentence typing run is the measurement:
 * a `down` with no `press` after it is a touch that never reached a key (a gap, a second finger
 * swallowed by a gesture in progress), and a `press` that resolved to `NOTHING` is one that
 * reached a key and typed nothing.
 *
 * Like [UsageLog], this never records which key: only its kind (letter, space, delete, other),
 * the outcome, how long the finger was down, and how many fingers were on the grid. The typed
 * sentence is compared against the text field by the person running the test, not from here.
 *
 * Read it from a debug build with
 * `adb shell run-as com.uncoalesced.stickykeys cat files/fluxboard-presses.csv`.
 * Rows are buffered in memory and appended in batches, so a press never waits on the disk.
 */
@Singleton
class PressLog
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val mutex = Mutex()
        private val pending = ArrayList<String>()

        val file: File get() = File(context.filesDir, FILE_NAME)

        fun onTouchDown(
            uptimeMillis: Long,
            pointersDown: Int,
        ) = add(downRow(uptimeMillis, pointersDown))

        fun onPressResolved(
            kind: KeyKind,
            outcome: PressOutcome,
            heldMillis: Long,
            uptimeMillis: Long,
        ) = add(pressRow(uptimeMillis, kind, outcome, heldMillis))

        /** Writes whatever is buffered. Called at the end of each input session. */
        fun flush() {
            val rows =
                synchronized(pending) {
                    if (pending.isEmpty()) return
                    pending.toList().also { pending.clear() }
                }
            scope.launch {
                mutex.withLock {
                    val target = file
                    if (!target.exists()) target.writeText(HEADER + "\n")
                    target.appendText(rows.joinToString(separator = "\n", postfix = "\n"))
                }
            }
        }

        fun clear() {
            synchronized(pending) { pending.clear() }
            scope.launch { mutex.withLock { file.delete() } }
        }

        private fun add(row: String) {
            val full =
                synchronized(pending) {
                    pending.add(row)
                    pending.size >= FLUSH_EVERY
                }
            if (full) flush()
        }

        companion object {
            const val FILE_NAME = "fluxboard-presses.csv"
            const val HEADER = "uptime_ms,event,kind,outcome,held_ms,pointers"

            /** Big enough that a typing burst costs one write, small enough to lose little. */
            private const val FLUSH_EVERY = 64

            fun downRow(
                uptimeMillis: Long,
                pointersDown: Int,
            ): String = "$uptimeMillis,down,,,,$pointersDown"

            fun pressRow(
                uptimeMillis: Long,
                kind: KeyKind,
                outcome: PressOutcome,
                heldMillis: Long,
            ): String = "$uptimeMillis,press,${kind.name},${outcome.name},$heldMillis,"
        }
    }
