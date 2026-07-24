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
    val imageOverlayOpacity: Float = 0.4f
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
        return json
    }

    companion object {
        fun fromJson(jsonStr: String): KeyboardTheme {
            val json = JSONObject(jsonStr)
            val colorsJson = json.getJSONObject("colors")
            
            val isLight = json.getBoolean("isLight")
            
            val colors = StickyKeysColors(
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
                isLight = isLight
            )

            val typeScaleStr = json.optString("typeScale", "MEDIUM")
            val typeScale = try {
                TypeScale.valueOf(typeScaleStr)
            } catch (e: Exception) {
                TypeScale.MEDIUM
            }

            val backgroundImagePath = if (json.has("backgroundImagePath")) json.getString("backgroundImagePath") else null
            val imageOverlayOpacity = json.optDouble("imageOverlayOpacity", 0.4).toFloat()

            return KeyboardTheme(
                id = json.getString("id"),
                name = json.getString("name"),
                isLight = isLight,
                colors = colors,
                typeScale = typeScale,
                backgroundImagePath = backgroundImagePath,
                imageOverlayOpacity = imageOverlayOpacity
            )
        }

        private fun colorToHex(color: Color): String {
            return String.format("#%08X", (0xFFFFFFFF and color.toArgb().toLong()))
        }

        private fun hexToColor(hex: String): Color {
            return Color(android.graphics.Color.parseColor(hex))
        }
    }
}
