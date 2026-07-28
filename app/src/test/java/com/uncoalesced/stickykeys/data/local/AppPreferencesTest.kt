// Engineered by uncoalesced
package com.uncoalesced.stickykeys.data.local

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppPreferencesTest {
    private fun prefs() = AppPreferences(ApplicationProvider.getApplicationContext())

    @Test
    fun `defaults to webp when nothing has been chosen`() {
        assertEquals("image/webp", prefs().defaultExportFormat.value)
    }

    @Test
    fun `a write is reflected in the exposed flow`() {
        // The flow is driven by an OnSharedPreferenceChangeListener rather than re-read on
        // access, so a listener that stops firing would silently freeze the setting.
        val preferences = prefs()
        preferences.setDefaultExportFormat("image/gif")
        assertEquals("image/gif", preferences.defaultExportFormat.value)
    }

    @Test
    fun `the choice survives a new instance reading the same store`() {
        prefs().setDefaultExportFormat("image/gif")
        assertEquals("image/gif", prefs().defaultExportFormat.value)
    }

    @Test
    fun `theme mode defaults to dark, matching what the app shipped as`() {
        assertEquals(ThemeMode.DARK, prefs().themeMode.value)
    }

    @Test
    fun `every theme mode can be selected and read back`() {
        val preferences = prefs()
        ThemeMode.entries.forEach { mode ->
            preferences.setThemeMode(mode)
            assertEquals(mode, preferences.themeMode.value)
        }
    }

    @Test
    fun `the theme choice survives a new instance, so it is really persisted`() {
        // The app was hardcoded dark with nothing stored; picking light has to outlive the
        // process, not just the composition.
        prefs().setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, prefs().themeMode.value)
    }

    @Test
    fun `an unrecognised stored value falls back to dark rather than crashing`() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromStored("PUCE"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStored(null))
    }
}
