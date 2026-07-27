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
}
