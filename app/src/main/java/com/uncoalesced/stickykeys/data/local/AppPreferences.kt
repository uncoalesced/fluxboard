// Engineered by uncoalesced
package com.uncoalesced.stickykeys.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "app_preferences",
        Context.MODE_PRIVATE
    )

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "default_export_format" -> _defaultExportFormat.value = prefs.getString("default_export_format", "image/webp") ?: "image/webp"
        }
    }

    private val _defaultExportFormat = MutableStateFlow(prefs.getString("default_export_format", "image/webp") ?: "image/webp")
    val defaultExportFormat: StateFlow<String> = _defaultExportFormat.asStateFlow()

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun setDefaultExportFormat(format: String) {
        prefs.edit().putString("default_export_format", format).apply()
    }
}
