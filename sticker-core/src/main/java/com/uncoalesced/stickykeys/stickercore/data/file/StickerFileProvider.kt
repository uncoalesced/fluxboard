// Engineered by uncoalesced
package com.uncoalesced.stickykeys.stickercore.data.file

import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File

/**
 * A FileProvider that can answer what a sticker actually is.
 *
 * Stickers are stored as `<uuid>.sticker`, an extension no `MimeTypeMap` knows, and the stock
 * `FileProvider` derives its MIME type from exactly that lookup. So `getType()` returned null
 * for every sticker this app has ever produced.
 *
 * That is invisible in apps which trust the `ClipDescription` passed to `commitContent` and
 * fatal in apps which do not. WhatsApp resolves the URI's own type before importing, and
 * rejects anything that is not `image/webp` for a sticker or a known image type otherwise;
 * Discord resolves the type to decide how to upload, and a null type falls out of its
 * inline-media path into a generic attachment -- which is also why a GIF sent there opened
 * the attachment editor instead of being posted inline. Neither app is doing anything
 * unusual; both are asking the standard question and getting no answer.
 *
 * The type is read from the file's own header rather than from a database lookup, because a
 * ContentProvider is process-wide and may be queried before, or entirely without, the Room
 * instance being available -- a caller that has been granted a URI can query it after our
 * own UI is gone.
 */
class StickerFileProvider : FileProvider() {
    override fun getType(uri: Uri): String? = sniff(uri) ?: super.getType(uri)

    /**
     * Answered explicitly because `ContentProvider`'s default returns null, and a caller that
     * asks this way (rather than through `getType`) would otherwise conclude the URI offers
     * nothing it can accept.
     */
    override fun getStreamTypes(
        uri: Uri,
        mimeTypeFilter: String,
    ): Array<String>? {
        val type = getType(uri) ?: return null
        return if (compareMimeType(type, mimeTypeFilter)) arrayOf(type) else null
    }

    /**
     * Rewrites the reported file name so its extension matches the real content.
     *
     * The stock implementation reports `<uuid>.sticker`. Apps that name an upload from
     * `DISPLAY_NAME` -- Discord among them -- then send a file whose extension no server-side
     * type sniffer recognises, so a valid image arrives as an unrenderable blob.
     */
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val source = super.query(uri, projection, selection, selectionArgs, sortOrder)
        val extension = extensionFor(getType(uri)) ?: return source

        return try {
            val columns = source.columnNames
            val nameIndex = columns.indexOf(OpenableColumns.DISPLAY_NAME)
            if (nameIndex < 0 || !source.moveToFirst()) return source

            val rewritten = MatrixCursor(columns, source.count)
            do {
                val row =
                    Array<Any?>(columns.size) { i ->
                        when (source.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> null
                            Cursor.FIELD_TYPE_INTEGER -> source.getLong(i)
                            Cursor.FIELD_TYPE_FLOAT -> source.getDouble(i)
                            Cursor.FIELD_TYPE_BLOB -> source.getBlob(i)
                            else -> source.getString(i)
                        }
                    }
                row[nameIndex] =
                    source.getString(nameIndex)?.substringBeforeLast('.')?.plus(".$extension")
                rewritten.addRow(row)
            } while (source.moveToNext())
            rewritten
        } finally {
            source.close()
        }
    }

    /**
     * The real type, from the first few bytes.
     *
     * Magic numbers rather than the stored `Sticker.mimeType`: this runs in a
     * ContentProvider, which the system may instantiate without the rest of the app, and a
     * file's own header is the one source that is always available and always correct.
     *
     * **Opened with [FileProvider.openFile], never `ContentProvider.openFileHelper`.** That is
     * not a preference. `openFileHelper` resolves a URI to a file by *querying its own
     * provider* -- and [query] is overridden here to call [getType], which calls this. Every
     * sticker commit therefore recursed until the stack was gone: a native SIGSEGV, ~512
     * frames of `query -> getType -> sniff -> openFileHelper -> query`, taking the whole IME
     * process with it on the first tap (roadmap 4J.3, device-confirmed 2026-08-20). It
     * presented as "spamming stickers crashed the keyboard" only because nobody had tapped
     * one twice slowly. The `catch` below cannot save it either: a blown stack is an Error.
     * `FileProvider.openFile` maps the path from its own `<files-path>` config and asks the
     * provider nothing, so it cannot re-enter.
     */
    private fun sniff(uri: Uri): String? {
        val header = ByteArray(HEADER_BYTES)
        val read =
            try {
                super
                    .openFile(uri, "r")
                    ?.use { descriptor ->
                        java.io.FileInputStream(descriptor.fileDescriptor).use { it.read(header) }
                    } ?: return null
            } catch (e: Exception) {
                return null
            }
        if (read < HEADER_BYTES) return null

        fun ascii(
            offset: Int,
            text: String,
        ): Boolean = text.indices.all { header[offset + it] == text[it].code.toByte() }

        return when {
            // RIFF....WEBP
            ascii(0, "RIFF") && ascii(8, "WEBP") -> "image/webp"
            header[0] == 0x89.toByte() && ascii(1, "PNG") -> "image/png"
            ascii(0, "GIF8") -> "image/gif"
            header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() -> "image/jpeg"
            else -> null
        }
    }

    private fun extensionFor(mimeType: String?): String? =
        when (mimeType) {
            "image/webp" -> "webp"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/jpeg" -> "jpg"
            else -> null
        }

    private companion object {
        /** Enough for the longest signature checked here: RIFF + size + WEBP. */
        const val HEADER_BYTES = 12
    }
}

/**
 * `ClipDescription.compareMimeTypes` semantics, without the framework dependency.
 *
 * [concrete] is a real type, [filter] may carry wildcards on either side of the slash.
 */
internal fun compareMimeType(
    concrete: String,
    filter: String,
): Boolean {
    if (filter == "*/*" || filter == concrete) return true
    val slash = filter.indexOf('/')
    if (slash < 0) return false
    val filterType = filter.substring(0, slash)
    val filterSubtype = filter.substring(slash + 1)
    val concreteSlash = concrete.indexOf('/')
    if (concreteSlash < 0) return false
    val concreteType = concrete.substring(0, concreteSlash)
    val concreteSubtype = concrete.substring(concreteSlash + 1)
    val typeMatches = filterType == "*" || filterType == concreteType
    val subtypeMatches = filterSubtype == "*" || filterSubtype == concreteSubtype
    return typeMatches && subtypeMatches
}

/** True when [file] holds bytes this provider can name a type for. */
internal fun File.hasRecognisedImageHeader(): Boolean =
    try {
        inputStream().use { stream ->
            val header = ByteArray(12)
            if (stream.read(header) < 12) {
                false
            } else {
                val riff =
                    header[0] == 'R'.code.toByte() &&
                        header[8] == 'W'.code.toByte()
                riff ||
                    header[0] == 0x89.toByte() ||
                    header[0] == 'G'.code.toByte() ||
                    header[0] == 0xFF.toByte()
            }
        }
    } catch (e: Exception) {
        false
    }
