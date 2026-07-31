// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONObject

data class KeyboardTheme(
    val id: String,
    val name: String,
    val isLight: Boolean,
    val colors: StickyKeysColors,
    val typeScale: TypeScale,
    val backgroundImagePath: String? = null,
    val imageOverlayOpacity: Float = 0.4f,
    /** Per-key presentation overrides. Defaults to the shipped look. */
    val keyStyle: KeyStyle = KeyStyle.Default,
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("name", name)
        json.put("isLight", isLight)
        json.put("typeScale", typeScale.name)
        if (backgroundImagePath != null) {
            json.put("backgroundImagePath", backgroundImagePath)
        }
        json.put("imageOverlayOpacity", imageOverlayOpacity.toDouble())

        val colorsJson = JSONObject()
        colorsJson.put("primary", colorToHex(colors.primary))
        colorsJson.put("primaryVariant", colorToHex(colors.primaryVariant))
        colorsJson.put("secondary", colorToHex(colors.secondary))
        colorsJson.put("background", colorToHex(colors.background))
        colorsJson.put("surface", colorToHex(colors.surface))
        colorsJson.put("surfaceVariant", colorToHex(colors.surfaceVariant))
        colorsJson.put("error", colorToHex(colors.error))
        colorsJson.put("onPrimary", colorToHex(colors.onPrimary))
        colorsJson.put("onSecondary", colorToHex(colors.onSecondary))
        colorsJson.put("onBackground", colorToHex(colors.onBackground))
        colorsJson.put("onSurface", colorToHex(colors.onSurface))
        colorsJson.put("onSurfaceVariant", colorToHex(colors.onSurfaceVariant))
        colorsJson.put("onError", colorToHex(colors.onError))

        json.put("colors", colorsJson)
        json.put("keyStyle", keyStyle.toJson())
        return json
    }

    /**
     * The same theme with any combination that would render an unreadable keyboard removed.
     *
     * This is the last line of defence behind the blank-canvas failure. Every control in the
     * customization screen is individually legitimate -- a user may want translucent keys, or
     * key text tinted to match a background photo -- but some *combinations* of them paint
     * keys that cannot be seen, and once such a theme is saved and made active it is loaded
     * again on every launch. Force-stopping, clearing cache and rebooting all leave it in
     * place, because it lives in `filesDir` and in the active-theme preference; only
     * uninstalling clears those. That is precisely the reported symptom.
     *
     * Only the offending override is dropped, not the whole theme, so a user who made one bad
     * choice keeps every other customization they made.
     */
    fun sanitized(): KeyboardTheme {
        val fill = keyStyle.resolveFill(colors.surface)
        val text = keyStyle.resolveText(colors.onSurface)

        var safe = keyStyle
        // An invisible fill alone is survivable -- the key background shows through -- but an
        // invisible glyph is not, and the two together are a blank panel.
        if (text.alpha < MIN_VISIBLE_ALPHA) {
            safe = safe.copy(textColor = null, textOpacity = 1f)
        }
        if (fill.alpha < MIN_VISIBLE_ALPHA && text.alpha < MIN_VISIBLE_ALPHA) {
            safe = safe.copy(fillColor = null, fillOpacity = 1f)
        }
        // Glyph and key the same colour is the other way to get a blank board, and it is easy
        // to reach by accident with two colour pickers that default to the same swatch.
        if (contrastRatio(safe.resolveText(colors.onSurface), safe.resolveFill(colors.surface)) <
            MIN_KEY_CONTRAST
        ) {
            safe = safe.copy(textColor = null, textOpacity = 1f, fillColor = null, fillOpacity = 1f)
        }

        // The palette itself can express the same mistake without any key override at all.
        val safeColors =
            if (contrastRatio(colors.onSurface, colors.surface) < MIN_KEY_CONTRAST) {
                if (isLight) lightStickyKeysColors() else darkStickyKeysColors()
            } else {
                colors
            }

        // A background image whose file has been deleted leaves the renderer drawing nothing
        // over an overlay that is still applied.
        val safeImage = backgroundImagePath?.takeIf { java.io.File(it).exists() }

        return if (safe == keyStyle && safeColors === colors && safeImage == backgroundImagePath) {
            this
        } else {
            copy(keyStyle = safe, colors = safeColors, backgroundImagePath = safeImage)
        }
    }

    companion object {
        /** Below this a colour is transparent enough to read as absent. */
        private const val MIN_VISIBLE_ALPHA = 0.12f

        /**
         * Minimum glyph-against-key contrast. Deliberately far below the WCAG 4.5:1 text
         * bar: this is not an accessibility gate, it is the line between "low contrast, and
         * the user's own choice" and "nothing is drawn at all".
         */
        private const val MIN_KEY_CONTRAST = 1.15f

        /** WCAG relative-luminance contrast, composited onto the theme's own background. */
        private fun contrastRatio(
            a: Color,
            b: Color,
        ): Float {
            val la = relativeLuminance(a)
            val lb = relativeLuminance(b)
            return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
        }

        private fun relativeLuminance(color: Color): Float {
            fun channel(v: Float): Float =
                if (v <= 0.03928f) {
                    v / 12.92f
                } else {
                    Math
                        .pow(((v + 0.055f) / 1.055f).toDouble(), 2.4)
                        .toFloat()
                }
            // Alpha folded in rather than ignored: a fully transparent glyph has no luminance
            // difference from anything, which is the case this exists to catch.
            val a = color.alpha
            return (
                0.2126f * channel(color.red) +
                    0.7152f * channel(color.green) +
                    0.0722f * channel(color.blue)
            ) * a
        }

        /**
         * The theme used when nothing else can be resolved.
         *
         * Built from the Kotlin colour tokens rather than read from `assets/themes`, because
         * this has to work in exactly the situation where asset or file loading has already
         * failed.
         */
        fun fallback(isLight: Boolean = false): KeyboardTheme =
            KeyboardTheme(
                id = if (isLight) FALLBACK_LIGHT_ID else FALLBACK_DARK_ID,
                name = if (isLight) "Default Light" else "Default Dark",
                isLight = isLight,
                colors = if (isLight) lightStickyKeysColors() else darkStickyKeysColors(),
                typeScale = TypeScale.MEDIUM,
            )

        const val FALLBACK_DARK_ID = "preset_default_dark"
        const val FALLBACK_LIGHT_ID = "preset_default_light"

        fun fromJson(jsonStr: String): KeyboardTheme {
            val json = JSONObject(jsonStr)
            val colorsJson = json.getJSONObject("colors")

            val isLight = json.getBoolean("isLight")

            val colors =
                StickyKeysColors(
                    primary = hexToColor(colorsJson.getString("primary")),
                    primaryVariant = hexToColor(colorsJson.getString("primaryVariant")),
                    secondary = hexToColor(colorsJson.getString("secondary")),
                    background = hexToColor(colorsJson.getString("background")),
                    surface = hexToColor(colorsJson.getString("surface")),
                    surfaceVariant = hexToColor(colorsJson.getString("surfaceVariant")),
                    error = hexToColor(colorsJson.getString("error")),
                    onPrimary = hexToColor(colorsJson.getString("onPrimary")),
                    onSecondary = hexToColor(colorsJson.getString("onSecondary")),
                    onBackground = hexToColor(colorsJson.getString("onBackground")),
                    onSurface = hexToColor(colorsJson.getString("onSurface")),
                    onSurfaceVariant = hexToColor(colorsJson.getString("onSurfaceVariant")),
                    onError = hexToColor(colorsJson.getString("onError")),
                    isLight = isLight,
                )

            val typeScaleStr = json.optString("typeScale", "MEDIUM")
            val typeScale =
                try {
                    TypeScale.valueOf(typeScaleStr)
                } catch (e: Exception) {
                    TypeScale.MEDIUM
                }

            val backgroundImagePath =
                if (json.has(
                        "backgroundImagePath",
                    )
                ) {
                    json.getString("backgroundImagePath")
                } else {
                    null
                }
            val imageOverlayOpacity = json.optDouble("imageOverlayOpacity", 0.4).toFloat()

            return KeyboardTheme(
                id = json.getString("id"),
                name = json.getString("name"),
                isLight = isLight,
                colors = colors,
                typeScale = typeScale,
                backgroundImagePath = backgroundImagePath,
                imageOverlayOpacity = imageOverlayOpacity,
                // Absent in every theme written before per-key styling existed, including
                // the shipped presets in assets/themes. Those must keep loading as the
                // untouched default rather than as a blank keyboard.
                keyStyle = KeyStyle.fromJson(json.optJSONObject("keyStyle")),
            )
        }

        private fun colorToHex(color: Color): String =
            String.format("#%08X", (0xFFFFFFFF and color.toArgb().toLong()))

        private fun hexToColor(hex: String): Color = Color(android.graphics.Color.parseColor(hex))
    }
}
