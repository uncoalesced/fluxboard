// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.emoji

import android.content.Context
import android.graphics.Paint
import androidx.compose.runtime.Immutable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** One pickable emoji. [name] doubles as the search text. */
@Immutable
data class Emoji(
    val glyph: String,
    val version: String,
    val name: String,
)

/** A Unicode emoji group, in the order emoji-test.txt lists them. */
@Immutable
data class EmojiGroup(
    val name: String,
    val emoji: List<Emoji>,
)

/**
 * The emoji catalogue, loaded from a generated asset.
 *
 * No emoji artwork ships with the app -- Android draws the glyphs from its own system font.
 * What ships is the *metadata*, because Android exposes no API for it: nothing lets a
 * third-party app ask which codepoints are "Food & Drink" or what a codepoint is called.
 * `assets/emoji_data.txt` is generated offline from Unicode's emoji-test.txt by
 * `emoji-tools/build_emoji_data.py`; see that script for what is filtered and why.
 */
@Singleton
class EmojiRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val mutex = Mutex()
        private var cached: List<EmojiGroup>? = null

        /**
         * Groups the device can actually draw, loaded once and cached.
         *
         * Safe to call repeatedly; the parse and the font probe happen on the first call
         * only, and never on the main thread.
         */
        suspend fun groups(): List<EmojiGroup> {
            cached?.let { return it }
            return mutex.withLock {
                cached ?: load().also { cached = it }
            }
        }

        private suspend fun load(): List<EmojiGroup> =
            withContext(Dispatchers.IO) {
                val paint = Paint()
                context.assets.open(ASSET).bufferedReader().useLines { lines ->
                    // Ask the font, do not guess from the API level.
                    //
                    // Emoji support tracks the system font, which OEMs update on their own
                    // schedule -- an SDK-to-Unicode-version table is an approximation that is
                    // wrong on exactly the devices that matter. hasGlyph answers for a whole
                    // sequence, so a ZWJ emoji the font lacks is rejected rather than rendering
                    // as its separate parts. The alternative is a grid of tofu.
                    parseEmojiData(lines) { paint.hasGlyph(it) }
                }
            }

        private companion object {
            const val ASSET = "emoji_data.txt"
        }
    }

/**
 * The generated asset, as groups.
 *
 * Pure, and separated from [EmojiRepository] for the reason every other pure helper in this
 * codebase is: the parse decides what the picker contains, and until now the only way to
 * exercise it was to look at a phone. It takes the font probe as a parameter rather than
 * calling `Paint` itself, which is also the only part of the old version that needed Android.
 *
 * Format is one record per line, `<glyph>TAB<version>TAB<name>`, with group headers as
 * `#<name>`. A group whose every emoji the font rejects is dropped rather than shown empty --
 * a tab that opens on nothing reads as the picker being broken.
 */
internal fun parseEmojiData(
    lines: Sequence<String>,
    isSupported: (String) -> Boolean,
): List<EmojiGroup> {
    val groups = mutableListOf<EmojiGroup>()
    var currentName: String? = null
    var current = mutableListOf<Emoji>()

    fun flush() {
        val name = currentName ?: return
        if (current.isNotEmpty()) groups += EmojiGroup(name, current)
    }

    lines.forEach { line ->
        if (line.isEmpty()) return@forEach
        if (line[0] == '#') {
            flush()
            currentName = line.substring(1)
            current = mutableListOf()
            return@forEach
        }
        val parts = line.split('\t')
        if (parts.size < 3) return@forEach
        val glyph = parts[0]
        if (!isSupported(glyph)) return@forEach
        current += Emoji(glyph = glyph, version = parts[1], name = parts[2])
    }
    flush()
    return groups
}
