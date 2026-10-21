// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import android.content.ClipDescription
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.uncoalesced.stickykeys.stickercore.data.file.StickerFileManager
import com.uncoalesced.stickykeys.stickercore.domain.model.Sticker

/**
 * Commits a sticker into the focused editor through `commitContent`, and refuses taps that
 * arrive too close together.
 *
 * Holds the one piece of state the path needs, the time of the last accepted commit, so the
 * service does not have to.
 */
internal class StickerCommit(
    private val fileManager: StickerFileManager,
    private val hapticsManager: com.uncoalesced.stickykeys.keyboardcore.haptics.HapticsManager,
) {
    /**
     * When the last sticker commit was allowed through, on the uptime clock.
     *
     * Held so [commit] can refuse a tap that arrives on top of the previous one.
     * See [STICKER_COMMIT_MIN_INTERVAL_MS] for why that path needs a floor at all.
     */
    private var lastCommitAt = 0L

    fun commit(
        service: InputMethodService,
        sticker: Sticker,
    ) {
        // Refused before anything else happens, including the haptic: a tap that is being
        // dropped should cost nothing at all, and a buzz for a sticker that was never sent
        // says the opposite of what happened.
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastCommitAt < STICKER_COMMIT_MIN_INTERVAL_MS) return
        lastCommitAt = now

        hapticsManager.performStickerSendHaptic()
        val file = fileManager.getStickerFile(sticker.id)
        if (!file.exists()) return

        val uri =
            FileProvider.getUriForFile(
                service,
                "com.uncoalesced.stickykeys.fileprovider",
                file,
            )

        val editorInfo = service.currentInputEditorInfo ?: return
        val inputConnection = service.currentInputConnection ?: return

        // The type the *content* actually is, from the resolver -- which now answers, because
        // the provider reads the file header. The value recorded in Room is used only as a
        // fallback: a sticker imported before a conversion, or edited outside the app, can
        // disagree with what is on disk, and the receiving app will believe the bytes.
        val mimeType =
            service.contentResolver.getType(uri) ?: sticker.mimeType

        val declared = EditorInfoCompat.getContentMimeTypes(editorInfo)
        val accepted = declared.firstOrNull { ClipDescription.compareMimeTypes(mimeType, it) }
        if (accepted == null) {
            // The field genuinely does not take this content. Say so rather than doing
            // nothing: a tap that produces no response at all is indistinguishable from the
            // keyboard being broken, which is how this reported as "stickers do not work".
            hapticsManager.performKeyPressHaptic()
            return
        }

        val clip = ClipDescription("Sticker", arrayOf(mimeType))
        val contentInfo = InputContentInfoCompat(uri, clip, null)

        var flags = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            flags = flags or InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
        } else {
            service.grantUriPermission(
                editorInfo.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }

        // Belt and braces on the permission grant. INPUT_CONTENT_GRANT_READ_URI_PERMISSION is
        // the sanctioned route and is what the flag above asks for, but it grants to the
        // *IME target* as the framework understands it -- and an app whose text field lives in
        // one process while its media import runs in another (both of these do) can end up
        // reading the URI from a component the implicit grant never covered. An explicit grant
        // to the editor's package costs nothing and closes that gap.
        runCatching {
            service.grantUriPermission(
                editorInfo.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }

        val committed =
            InputConnectionCompat.commitContent(
                inputConnection,
                editorInfo,
                contentInfo,
                flags,
                null,
            )
        if (!committed) {
            // The editor declared the type and then refused the content anyway. Nothing more
            // can be done through the InputConnection, and silently swallowing it is what
            // made this look like the sticker feature simply not existing.
            hapticsManager.performKeyPressHaptic()
        }
    }

    private companion object {
        /**
         * Floor between two sticker commits.
         *
         * Every commit is synchronous main-thread work with two blocking parts: disk I/O (the
         * file check, and the header sniff the resolver runs behind `getType`) and binder IPC
         * into the host app, which has to accept or refuse the content before the call
         * returns. One of those is cheap. A dozen queued behind an app that is slow to answer
         * is a stalled main thread, and a stalled IME main thread is an ANR -- which is what
         * "spamming the stickers crashed the keyboard" looks like from the outside
         * (roadmap 4J.3).
         *
         * A floor between events rather than a queue, which is the same shape the scrub's
         * haptic throttle uses in `ScrubGesture.kt` for the same reason: the tap that arrives
         * too soon is dropped, not deferred, because deferring it would still run the work.
         *
         * ponytail: 300ms is a guess, and it is the *only* thing standing between a fast tap
         * run and the main thread. Set the number from a device: if a reproduction still
         * stalls, the real fix is moving the file read off the main thread rather than raising
         * this further -- a debounce large enough to hide a slow host is one a user notices as
         * "the sticker didn't send".
         */
        const val STICKER_COMMIT_MIN_INTERVAL_MS = 300L
    }
}
