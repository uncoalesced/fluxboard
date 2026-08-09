// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.clipboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.ClipboardDao
import com.uncoalesced.stickykeys.keyboardcore.data.local.entity.ClipboardEntryEntity
import com.uncoalesced.stickykeys.keyboardcore.ime.IncognitoState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardHistoryManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val clipboardDao: ClipboardDao,
        private val incognitoState: IncognitoState,
    ) {
        private val clipboardManager: ClipboardManager =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private val clipChangedListener =
            ClipboardManager.OnPrimaryClipChangedListener {
                handleClipboardChange()
            }

        /**
         * The last clip written to history, and when -- so one copy is not stored twice.
         *
         * `OnPrimaryClipChangedListener` is not once-per-copy. The platform fires it again when
         * the clip's description settles, and several OEM builds fire it twice outright --
         * measured here as two identical rows 253ms apart on one copy and 21ms apart on the
         * next, from a single Select-all-then-Copy. Nothing was wrong with the write path; it
         * was called twice and had no reason to refuse the second.
         *
         * Held in memory rather than checked against the database, because the two callbacks
         * arrive faster than a round-trip: both would read "not present" before either wrote.
         * The listener is delivered on the thread that registered it -- the IME's main thread,
         * from `onCreate` -- so this compare-and-set is already serialized and needs no lock.
         *
         * **The timestamp is load-bearing, and leaving it out was a real bug.** Suppressing on
         * text alone also swallowed a *deliberate* re-copy: copying the same passage again a
         * few seconds later vanished, with the entry left sitting at its original position in
         * the history. That is worse than the duplicate it was fixing, because the user watches
         * a copy silently not happen. The window below only has to cover a redelivery of one
         * copy, which is milliseconds; a person cannot invoke copy twice inside it.
         */
        private var lastCapturedText: String? = null
        private var lastCapturedAt = 0L

        fun startListening() {
            clipboardManager.addPrimaryClipChangedListener(clipChangedListener)
        }

        fun stopListening() {
            clipboardManager.removePrimaryClipChangedListener(clipChangedListener)
            // Dropped with the listener. Keeping it would mean that after the keyboard is
            // dismissed and shown again, re-copying the same text is silently ignored -- the
            // guard exists to absorb a duplicate callback, not to deduplicate across sessions.
            lastCapturedText = null
            lastCapturedAt = 0L
        }

        private companion object {
            /**
             * How long one copy may keep arriving for.
             *
             * Sized against what was measured on device -- redeliveries 21ms and 253ms after
             * the original -- with room to spare, and kept far below the time it physically
             * takes a person to invoke copy a second time. Widening this starts eating real
             * copies; that is the failure it already caused once when it was effectively
             * infinite.
             */
            const val REDELIVERY_WINDOW_MS = 1_000L
        }

        private fun handleClipboardChange() {
            if (!clipboardManager.hasPrimaryClip()) return
            val clipData = clipboardManager.primaryClip ?: return
            val description = clipboardManager.primaryClipDescription ?: return

            // Phase 38: additional layer on top of (never instead of) the
            // EXTRA_IS_SENSITIVE check below. While the keyboard is shown on a field
            // that asked not to be learned from, copies are not persisted either.
            // Same shared session state that gates prediction learning and drives
            // the on-keyboard indicator, so there is no second flag to keep in sync.
            if (incognitoState.active.value) return

            // Privacy rule: Respect EXTRA_IS_SENSITIVE flag
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val isSensitive =
                    description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) ?: false
                if (isSensitive) {
                    return // Do not persist sensitive content
                }
            } else {
                // Check fallback for older versions if developers manually added the extra
                val isSensitive =
                    description.extras?.getBoolean("android.content.extra.IS_SENSITIVE") ?: false
                if (isSensitive) {
                    return
                }
            }

            if (clipData.itemCount > 0) {
                val item = clipData.getItemAt(0)
                val text = item.text?.toString()
                if (!text.isNullOrBlank()) {
                    // Checked and claimed synchronously, before the write is dispatched: the
                    // duplicate callback arrives long before an IO coroutine could finish.
                    val now = System.currentTimeMillis()
                    if (text == lastCapturedText &&
                        now - lastCapturedAt < REDELIVERY_WINDOW_MS
                    ) {
                        return
                    }
                    lastCapturedText = text
                    lastCapturedAt = now
                    scope.launch {
                        val entry =
                            ClipboardEntryEntity(
                                text = text,
                                timestamp = System.currentTimeMillis(),
                            )
                        clipboardDao.insert(entry)
                    }
                }
            }
        }
    }
