// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import android.content.ClipDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.uncoalesced.stickykeys.keyboardcore.R
import com.uncoalesced.stickykeys.stickercore.data.file.StickerFileManager
import com.uncoalesced.stickykeys.stickercore.domain.model.Sticker
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The FileProvider authority for [packageName].
 *
 * The manifest declares `${applicationId}.fileprovider`, so a hardcoded id names a provider that
 * does not exist under the benchmark build's ".benchmark" suffix.
 */
internal fun stickerProviderAuthority(packageName: String): String = "$packageName.fileprovider"

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
        val delivery =
            try {
                deliver(service, sticker)
            } catch (e: Exception) {
                // The file provider and the host app can both throw from here, and an exception
                // on this path would take the keyboard down in the middle of the user's typing.
                e.printStackTrace()
                StickerDelivery.Failed
            }
        val message = delivery.messageRes ?: return
        hapticsManager.performKeyPressHaptic()
        Toast.makeText(service, message, Toast.LENGTH_SHORT).show()
    }

    private fun deliver(
        service: InputMethodService,
        sticker: Sticker,
    ): StickerDelivery {
        val file = fileManager.getStickerFile(sticker.id)
        if (!file.exists()) return StickerDelivery.Failed

        var uri =
            FileProvider.getUriForFile(
                service,
                stickerProviderAuthority(service.packageName),
                file,
            )

        val editorInfo = service.currentInputEditorInfo ?: return StickerDelivery.Failed
        val inputConnection = service.currentInputConnection ?: return StickerDelivery.Failed

        // The type the *content* actually is, from the resolver -- which now answers, because
        // the provider reads the file header. The value recorded in Room is used only as a
        // fallback: a sticker imported before a conversion, or edited outside the app, can
        // disagree with what is on disk, and the receiving app will believe the bytes.
        var mimeType =
            service.contentResolver.getType(uri) ?: sticker.mimeType
        val declared = EditorInfoCompat.getContentMimeTypes(editorInfo)

        // Some fields take PNG and nothing else. A still sticker is re-encoded for them rather
        // than refused; an animated one is not, because a PNG would silently freeze it on its
        // first frame and the user would not know why.
        if (shouldTranscodeToPng(mimeType, declared, isAnimatedImage(file))) {
            transcodeToPng(service.cacheDir, file, sticker.id)?.let { png ->
                uri =
                    FileProvider.getUriForFile(
                        service,
                        stickerProviderAuthority(service.packageName),
                        png,
                    )
                mimeType = PNG
            }
        }

        // A field that does not take this content gets a message: a tap with no visible response
        // is indistinguishable from the keyboard being broken, which is how this reported as
        // "stickers do not work".
        if (!fieldAcceptsMimeType(mimeType, declared)) {
            return StickerDelivery.NotAccepted
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

        // False means the editor declared the type and then refused the content anyway. Nothing
        // more can be done through the InputConnection.
        val committed =
            InputConnectionCompat.commitContent(
                inputConnection,
                editorInfo,
                contentInfo,
                flags,
                null,
            )
        return if (committed) StickerDelivery.Delivered else StickerDelivery.NotAccepted
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

/** How a sticker tap ended, and what the user is told about it. */
internal enum class StickerDelivery(
    @StringRes val messageRes: Int?,
) {
    Delivered(null),
    NotAccepted(R.string.sticker_not_accepted),
    Failed(R.string.sticker_send_failed),
}

/** Whether a field declaring [declared] content types takes [mimeType]. */
internal fun fieldAcceptsMimeType(
    mimeType: String,
    declared: Array<String>,
): Boolean = declared.any { ClipDescription.compareMimeTypes(mimeType, it) }

private const val PNG = "image/png"

/** Folder under `cacheDir` holding PNG copies; must match `file_paths.xml`. */
internal const val TRANSCODE_DIR = "sticker_transcode"

/** Longest side of a transcoded sticker. The sticker pipeline itself caps at 512. */
internal const val MAX_TRANSCODE_SIDE_PX = 512

internal const val MAX_TRANSCODE_FILES = 25
internal const val MAX_TRANSCODE_BYTES = 15L * 1024 * 1024

/** Types that can be re-encoded to PNG without losing anything a still image has. */
private val TRANSCODABLE_TYPES = setOf("image/webp", "image/jpeg")

/**
 * Whether a sticker of [mimeType] should be re-encoded to PNG for a field declaring [declared].
 *
 * Only when the field refuses the real type, takes PNG, and the image is still. GIFs are never
 * transcoded, even single-frame ones: the header check does not count frames.
 */
internal fun shouldTranscodeToPng(
    mimeType: String,
    declared: Array<String>,
    isAnimated: Boolean,
): Boolean =
    !isAnimated &&
        mimeType in TRANSCODABLE_TYPES &&
        !fieldAcceptsMimeType(mimeType, declared) &&
        fieldAcceptsMimeType(PNG, declared)

/** Whether [file] is a GIF or an animated WebP, read from its header. Unreadable counts as no. */
internal fun isAnimatedImage(file: File): Boolean =
    try {
        file.inputStream().use { stream ->
            val header = ByteArray(WEBP_VP8X_FLAGS_OFFSET + 1)
            isAnimatedHeader(header, stream.read(header))
        }
    } catch (e: Exception) {
        false
    }

/**
 * Header check behind [isAnimatedImage], pure so it can be tested on bytes.
 *
 * Any GIF counts as animated. An animated WebP is always the extended format: a `VP8X` chunk
 * first, whose flags byte carries the animation bit.
 */
internal fun isAnimatedHeader(
    header: ByteArray,
    length: Int,
): Boolean {
    fun ascii(
        offset: Int,
        text: String,
    ): Boolean =
        length >= offset + text.length &&
            text.indices.all { header[offset + it] == text[it].code.toByte() }

    if (ascii(0, "GIF8")) return true
    if (!ascii(0, "RIFF") || !ascii(8, "WEBP") || !ascii(12, "VP8X")) return false
    if (length <= WEBP_VP8X_FLAGS_OFFSET) return false
    return header[WEBP_VP8X_FLAGS_OFFSET].toInt() and WEBP_ANIMATION_FLAG != 0
}

/** RIFF(4) size(4) WEBP(4) VP8X(4) chunk size(4), then the flags byte. */
private const val WEBP_VP8X_FLAGS_OFFSET = 20
private const val WEBP_ANIMATION_FLAG = 0x02

/**
 * A PNG copy of [source] under [cacheRoot]/[TRANSCODE_DIR], or null if it cannot be made.
 *
 * Reused while it is newer than the source, so editing a sticker yields a fresh copy. Written
 * to a temp file and moved into place, so a crash cannot leave a truncated PNG that later sends
 * reuse. Runs inline on the commit path, which is why the decode is capped small.
 */
internal fun transcodeToPng(
    cacheRoot: File,
    source: File,
    stickerId: String,
): File? {
    val dir = File(cacheRoot, TRANSCODE_DIR)
    if (!dir.isDirectory && !dir.mkdirs()) return null
    val safeId = stickerId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
    if (safeId.isEmpty()) return null
    val target = File(dir, "$safeId.png")

    if (target.length() > 0 && target.lastModified() >= source.lastModified()) {
        target.setLastModified(System.currentTimeMillis())
        return target
    }

    val bitmap = decodeBounded(source, MAX_TRANSCODE_SIDE_PX) ?: return null
    val tmp = File(dir, "$safeId.png.tmp")
    try {
        val written =
            FileOutputStream(tmp).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        if (!written || tmp.length() == 0L) return null
        try {
            Files.move(
                tmp.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } catch (e: Exception) {
        return null
    } finally {
        bitmap.recycle()
        if (tmp.exists()) tmp.delete()
    }

    pruneTranscodeCache(dir, MAX_TRANSCODE_FILES, MAX_TRANSCODE_BYTES, keep = target)
    return target
}

/**
 * Decodes [source] with its longest side at most [maxSide], or null if it cannot be decoded.
 *
 * Bounds are read first so an oversized file is subsampled during decode instead of being
 * allocated at full size; an out-of-memory error is caught rather than taking the keyboard
 * down mid-typing.
 */
private fun decodeBounded(
    source: File,
    maxSide: Int,
): Bitmap? {
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null

        var sample = 1
        while (longest / (sample * 2) >= maxSide) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(source.absolutePath, options) ?: return null

        val side = maxOf(decoded.width, decoded.height)
        if (side <= maxSide) return decoded
        val scale = maxSide.toFloat() / side
        val scaled =
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    } catch (e: OutOfMemoryError) {
        return null
    } catch (e: Exception) {
        return null
    }
}

/**
 * Keeps [dir] within [maxFiles] files and [maxBytes] bytes, deleting the least recently used
 * first. [keep] is never deleted. Leftover `.tmp` files are always removed: the commit path is
 * single-threaded, so none can belong to a write still in progress.
 */
internal fun pruneTranscodeCache(
    dir: File,
    maxFiles: Int,
    maxBytes: Long,
    keep: File?,
) {
    val files = dir.listFiles()?.filter { it.isFile } ?: return
    files.filter { it.name.endsWith(".tmp") }.forEach { it.delete() }

    var count = 0
    var bytes = 0L
    files
        .filter { !it.name.endsWith(".tmp") }
        .sortedWith(compareByDescending<File> { it == keep }.thenByDescending { it.lastModified() })
        .forEach { file ->
            val size = file.length()
            if (file != keep && (count + 1 > maxFiles || bytes + size > maxBytes)) {
                file.delete()
            } else {
                count++
                bytes += size
            }
        }
}
