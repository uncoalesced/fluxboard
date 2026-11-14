// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens

import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import com.uncoalesced.stickykeys.keyboardcore.theme.ThemeManager
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Unit tests for the theme editor's selection and deletion actions. */
@OptIn(ExperimentalCoroutinesApi::class)
class ThemeEditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var themeManager: ThemeManager
    private lateinit var preferences: KeyboardPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        themeManager = mockk(relaxed = true)
        preferences = mockk(relaxed = true)
        every { themeManager.availableThemes } returns MutableStateFlow(emptyList())
        every { themeManager.activeTheme } returns MutableStateFlow(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ThemeEditorViewModel(themeManager, preferences)

    @Test
    fun `deleteCustomTheme delegates deletion to themeManager`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()

            vm.deleteCustomTheme("custom_solarized")
            advanceUntilIdle()

            coVerify { themeManager.deleteCustomTheme("custom_solarized") }
        }

    @Test
    fun `setActiveTheme stores the chosen theme id in preferences`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()

            vm.setActiveTheme("custom_solarized")
            advanceUntilIdle()

            verify { preferences.setActiveThemeId("custom_solarized") }
        }
}
