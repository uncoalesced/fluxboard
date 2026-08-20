// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.diagnostics

import android.os.SystemClock

/**
 * How long a key press takes to become an edit, measured on this device.
 *
 * ## Two spans, because one of them cannot answer the question on its own
 *
 * The obvious measurement -- finger down to this keyboard issuing the edit -- is what the
 * user feels, and it is [commitSamples]. But this keyboard commits an ordinary letter on
 * *release*, not on press, so most of that number is the user's own dwell on the key. A
 * 90ms average there says nothing about whether the process stalled.
 *
 * So the down event's own timestamp is used for a second span: `now - MotionEvent time` at
 * the moment this process observes the press. That is dwell-free by construction and is
 * exactly what a main-thread stall, a GC pause or a slow recomposition inflates. If the
 * intermittent lag is this keyboard's fault, [deliverySamples] is where it shows up; if
 * delivery is flat and only commit is slow, the finger was on the key that long and the
 * commit-on-release design is the thing to question.
 *
 * `SystemClock.uptimeMillis()`, not `elapsedRealtimeNanos` or `currentTimeMillis`: it is the
 * clock `MotionEvent.getEventTime()` is expressed in, and the whole delivery measurement is
 * a subtraction between the two. Nanoseconds would be spurious precision on a number whose
 * inputs are millisecond-quantised anyway.
 *
 * ## What it records
 *
 * Durations. No characters, no key identities, no words -- the same rule [UsageRecorder]
 * follows, and this class is compiled into release builds where that one deliberately is
 * not.
 *
 * ponytail: a plain object rather than a Hilt @Singleton. It has no dependencies, and the
 * two call sites are a Compose Modifier and an InputMethodService -- injecting it would mean
 * threading a parameter through the key composables or adding a CompositionLocal for one
 * subtraction. Promote it to @Singleton if a second consumer ever needs its own instance.
 */
object LatencyTracker {
    /** Time from the OS timestamping the press to this process acting on it. */
    private val deliverySamples = LatencySamples()

    /** Time from the press landing here to this keyboard issuing the edit. */
    private val commitSamples = LatencySamples()

    @Volatile private var pressStartedAtMs = 0L

    /**
     * A key press, at the earliest point this process can see one.
     *
     * [eventUptimeMs] is the pointer change's own `uptimeMillis`, i.e. when the OS stamped
     * the touch, which is earlier than now by however long the input queue and this process
     * took to get to it.
     */
    fun markDown(eventUptimeMs: Long) {
        val now = SystemClock.uptimeMillis()
        pressStartedAtMs = now
        deliverySamples.add(now - eventUptimeMs)
    }

    /**
     * This keyboard's own work for a press is done and the edit has been handed to the host.
     *
     * Deliberately silent when no press is outstanding: a suggestion tap, a paste and an
     * autocorrect follow-up all reach the same edit primitives without a key press behind
     * them, and timing those against a stale press start would invent latency that never
     * happened.
     */
    fun markCommitted() {
        val started = pressStartedAtMs
        if (started == 0L) return
        pressStartedAtMs = 0L
        commitSamples.add(SystemClock.uptimeMillis() - started)
    }

    fun snapshot(): LatencySnapshot =
        LatencySnapshot(
            deliveryAverageMs = deliverySamples.averageMs(),
            deliveryP95Ms = deliverySamples.p95Ms(),
            commitAverageMs = commitSamples.averageMs(),
            commitP95Ms = commitSamples.p95Ms(),
            outlierMs = deliverySamples.outliers() + commitSamples.outliers(),
        )

    fun reset() {
        pressStartedAtMs = 0L
        deliverySamples.clear()
        commitSamples.clear()
    }
}

/**
 * One rolling window of durations, and the only part of this file worth testing.
 *
 * A mean hides the shape of the report this exists to chase -- "sometimes, not always" -- so
 * a p95 and a separate list of individual spikes are kept alongside it. The spike list is
 * what survives a 200-wide p95 smoothing away three bad keystrokes in a minute of typing.
 *
 * Fixed arrays rather than a growing collection: this is written from the touch path, and an
 * allocation per keystroke in the thing measuring keystroke cost would be self-defeating.
 */
internal class LatencySamples {
    private val ring = LongArray(MAX_SAMPLES)
    private var count = 0
    private var next = 0
    private var sum = 0L

    private val outlierRing = LongArray(MAX_OUTLIERS)
    private var outlierCount = 0
    private var outlierNext = 0

    /**
     * Records one duration in milliseconds, ignoring implausible ones.
     *
     * Negative is a clock or ordering fault. Anything past [MAX_PLAUSIBLE_MS] is a gesture
     * that was never a tap -- a hold that opened the alternates strip, a glide, a press
     * abandoned when the app went away -- and averaging those in would report the user's
     * behaviour as this keyboard's latency.
     */
    @Synchronized
    fun add(ms: Long) {
        if (ms < 0 || ms > MAX_PLAUSIBLE_MS) return
        if (count == MAX_SAMPLES) sum -= ring[next] else count++
        ring[next] = ms
        sum += ms
        next = (next + 1) % MAX_SAMPLES
        if (ms >= OUTLIER_THRESHOLD_MS) {
            outlierRing[outlierNext] = ms
            outlierNext = (outlierNext + 1) % MAX_OUTLIERS
            if (outlierCount < MAX_OUTLIERS) outlierCount++
        }
    }

    /** Null rather than zero with nothing recorded: no data is not the same as no latency. */
    @Synchronized
    fun averageMs(): Long? = if (count == 0) null else sum / count

    @Synchronized
    fun p95Ms(): Long? {
        if (count == 0) return null
        val sorted = ring.copyOf(count).sortedArray()
        return sorted[((count - 1) * 95) / 100]
    }

    /** Individual spikes, oldest first. */
    @Synchronized
    fun outliers(): List<Long> {
        // Once the ring has wrapped the oldest entry is the one about to be overwritten.
        val start = if (outlierCount == MAX_OUTLIERS) outlierNext else 0
        return (0 until outlierCount).map { outlierRing[(start + it) % MAX_OUTLIERS] }
    }

    @Synchronized
    fun clear() {
        count = 0
        next = 0
        sum = 0
        outlierCount = 0
        outlierNext = 0
    }

    internal companion object {
        const val MAX_SAMPLES = 200
        const val MAX_OUTLIERS = 20
        const val MAX_PLAUSIBLE_MS = 1_000L

        /**
         * Five frames at 60Hz. A guess at "obviously felt", not a measurement -- the first
         * device pass should look at the raw distribution before this number is trusted,
         * because a lag that sits consistently at 40ms would not trip it even once.
         */
        const val OUTLIER_THRESHOLD_MS = 80L
    }
}

/** Everything the diagnostics export and the stats screen read, in one value. */
data class LatencySnapshot(
    val deliveryAverageMs: Long?,
    val deliveryP95Ms: Long?,
    val commitAverageMs: Long?,
    val commitP95Ms: Long?,
    val outlierMs: List<Long>,
)
