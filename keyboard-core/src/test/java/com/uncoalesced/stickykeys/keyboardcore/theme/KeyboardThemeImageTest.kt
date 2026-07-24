// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyboardThemeImageTest {

    @Test
    fun `keyboard theme json round trip with image background properties`() {
        val theme = KeyboardTheme(
            id = "custom_image_theme",
            name = "Image Theme",
            isLight = false,
            colors = darkStickyKeysColors(),
            typeScale = TypeScale.MEDIUM,
            backgroundImagePath = "/data/user/0/com.uncoalesced.stickykeys/files/theme_backgrounds/custom_image_theme.png",
            imageOverlayOpacity = 0.55f
        )

        val jsonString = theme.toJson().toString(2)
        val restored = KeyboardTheme.fromJson(jsonString)

        assertEquals(theme.id, restored.id)
        assertEquals(theme.name, restored.name)
        assertEquals(theme.isLight, restored.isLight)
        assertEquals(theme.backgroundImagePath, restored.backgroundImagePath)
        assertEquals(theme.imageOverlayOpacity, restored.imageOverlayOpacity, 0.01f)
    }

    @Test
    fun `keyboard theme json round trip without image background defaults`() {
        val theme = KeyboardTheme(
            id = "default_theme",
            name = "Default Theme",
            isLight = true,
            colors = lightStickyKeysColors(),
            typeScale = TypeScale.SMALL
        )

        val jsonString = theme.toJson().toString(2)
        val restored = KeyboardTheme.fromJson(jsonString)

        assertNull(restored.backgroundImagePath)
        assertEquals(0.4f, restored.imageOverlayOpacity, 0.01f)
    }
}
