// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/**
 * The per-key appearance layer: fill, text, border and haze.
 *
 * A fifth token type alongside colours, typography, spacing and shapes rather than an
 * extension of [StickyKeysColors]. The palette is semantic -- `surface`, `onSurface` -- and
 * is shared with the sticker and clipboard panels; these are presentational overrides that
 * apply to keys only. Folding them into the palette would mean every consumer of a colour
 * token had to know whether it had been overridden for keys.
 *
 * **Every colour is nullable and every override defaults to off.** Null means "derive it from
 * the palette the way the keyboard always has", which is what keeps the defaults good-looking
 * and makes an untouched theme byte-for-byte the same keyboard as before this existed. A user
 * who never opens the customization screen sees no change; a user who does gets each control
 * layered onto a sane base rather than starting from nothing.
 *
 * `@Immutable` is load-bearing, not decoration. This is passed down to every key, and an
 * unstable type here would make the entire grid non-skippable and silently undo the
 * recomposition work `KeyboardRecompositionTest` measures.
 */
@Immutable
data class KeyStyle(
    /** Key fill. Null derives from `surface` / `surfaceVariant` as before. */
    val fillColor: Color? = null,
    val fillOpacity: Float = 1f,
    /** Key glyph and label. Null derives from `onSurface` / `onSurfaceVariant`. */
    val textColor: Color? = null,
    val textOpacity: Float = 1f,
    /** Outline around each key. Off by default: the reference layout has borderless keys. */
    val borderEnabled: Boolean = false,
    val borderColor: Color? = null,
    val borderOpacity: Float = 1f,
    val borderWidth: Dp = 1.dp,
    /** Glow / drop shadow behind each key. Zero opacity is off, and is the default. */
    val hazeColor: Color? = null,
    val hazeOpacity: Float = 0f,
    val hazeRadius: Dp = 6.dp,
    /**
     * Opacity of the keyboard background image itself.
     *
     * Distinct from `KeyboardTheme.imageOverlayOpacity`, which darkens *over* the image for
     * legibility. This fades the image itself, so a user can soften a busy photo without
     * also blackening the keyboard.
     */
    val backgroundImageOpacity: Float = 1f,
) {
    /** The fill actually drawn, given the palette value this key would otherwise have used. */
    fun resolveFill(fallback: Color): Color =
        (fillColor ?: fallback).copy(alpha = clamp(fillOpacity))

    /** The glyph colour actually drawn. */
    fun resolveText(fallback: Color): Color =
        (textColor ?: fallback).copy(alpha = clamp(textOpacity))

    /** The border colour, or null when borders are off. */
    fun resolveBorder(fallback: Color): Color? =
        if (!borderEnabled) null else (borderColor ?: fallback).copy(alpha = clamp(borderOpacity))

    /** The haze colour, or null when the effect is off. */
    fun resolveHaze(fallback: Color): Color? =
        if (hazeOpacity <= 0f) null else (hazeColor ?: fallback).copy(alpha = clamp(hazeOpacity))

    fun toJson(): JSONObject =
        JSONObject().apply {
            fillColor?.let { put("fillColor", colorToHex(it)) }
            put("fillOpacity", fillOpacity.toDouble())
            textColor?.let { put("textColor", colorToHex(it)) }
            put("textOpacity", textOpacity.toDouble())
            put("borderEnabled", borderEnabled)
            borderColor?.let { put("borderColor", colorToHex(it)) }
            put("borderOpacity", borderOpacity.toDouble())
            put("borderWidth", borderWidth.value.toDouble())
            hazeColor?.let { put("hazeColor", colorToHex(it)) }
            put("hazeOpacity", hazeOpacity.toDouble())
            put("hazeRadius", hazeRadius.value.toDouble())
            put("backgroundImageOpacity", backgroundImageOpacity.toDouble())
        }

    companion object {
        /** The look shipped by default. Every override off. */
        val Default = KeyStyle()

        fun fromJson(json: JSONObject?): KeyStyle {
            // A theme saved before this existed has no keyStyle object at all, and must load
            // as the untouched default rather than failing or producing a blank keyboard.
            if (json == null) return Default
            return KeyStyle(
                fillColor = json.optColor("fillColor"),
                fillOpacity = json.optDouble("fillOpacity", 1.0).toFloat(),
                textColor = json.optColor("textColor"),
                textOpacity = json.optDouble("textOpacity", 1.0).toFloat(),
                borderEnabled = json.optBoolean("borderEnabled", false),
                borderColor = json.optColor("borderColor"),
                borderOpacity = json.optDouble("borderOpacity", 1.0).toFloat(),
                borderWidth = json.optDouble("borderWidth", 1.0).toFloat().dp,
                hazeColor = json.optColor("hazeColor"),
                hazeOpacity = json.optDouble("hazeOpacity", 0.0).toFloat(),
                hazeRadius = json.optDouble("hazeRadius", 6.0).toFloat().dp,
                backgroundImageOpacity =
                    json.optDouble("backgroundImageOpacity", 1.0).toFloat(),
            )
        }

        private fun JSONObject.optColor(key: String): Color? =
            if (has(key)) Color(android.graphics.Color.parseColor(getString(key))) else null

        private fun colorToHex(color: Color): String =
            String.format("#%08X", (0xFFFFFFFF and color.toArgb().toLong()))
    }
}

private fun clamp(value: Float): Float = value.coerceIn(0f, 1f)
