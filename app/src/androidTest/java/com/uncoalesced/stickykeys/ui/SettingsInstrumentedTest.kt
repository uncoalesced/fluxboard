// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uncoalesced.stickykeys.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented coverage for the Phase 35 sticker and keyboard UI flows.
 *
 * The bottom navigation exposes four tabs: "Styles" (sticker library),
 * "Keyboard" (keyboard settings), "Transfer", and "Settings" (app settings).
 * Keyboard settings live under the "Keyboard" tab directly -- not under
 * "Settings" -- which the previous version of this test got wrong.
 *
 * Live IME typing is intentionally out of scope here: instrumented tests cannot
 * drive the system IME-switch dialog or the InputConnection IPC boundary. That
 * path is covered by docs/testing/manual/ime-typing-flow.md instead.
 */
@RunWith(AndroidJUnit4::class)
class SettingsInstrumentedTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    // --- Sticker flow ---

    @Test
    fun stickerLibrary_launchesAndSwitchesTabs() {
        // The app starts on the "Styles" sticker library (start destination).
        composeTestRule.onNodeWithText("All").assertIsDisplayed()
        composeTestRule.onNodeWithText("Favourites", substring = true).assertIsDisplayed()

        // Switching to the Favourites tab shows the empty-state message
        // (no stickers are seeded in a fresh instrumented run).
        composeTestRule.onNodeWithText("Favourites", substring = true).performClick()
        composeTestRule.onNodeWithText("No stickers in this view.").assertIsDisplayed()

        // And back to All.
        composeTestRule.onNodeWithText("All").performClick()
    }

    // --- Keyboard flow (correct tab: "Keyboard", not "Settings") ---

    @Test
    fun keyboardSettings_togglesAreInteractive() {
        composeTestRule.onNodeWithText("Keyboard").performClick()

        composeTestRule.onNodeWithText("Keyboard Settings").assertIsDisplayed()

        val autoCap = composeTestRule.onNodeWithText("Auto-Capitalization")
        autoCap.assertIsDisplayed()
        autoCap.performClick()

        val autoCorrect = composeTestRule.onNodeWithText("Auto-Correction")
        autoCorrect.assertIsDisplayed()
        autoCorrect.performClick()

        val haptics = composeTestRule.onNodeWithText("Vibration Feedback")
        haptics.performScrollTo().assertIsDisplayed()
        haptics.performClick()
    }

    // --- App settings flow (the "Settings" tab) ---

    @Test
    fun appSettings_exportFormatToggle() {
        composeTestRule.onNodeWithText("Settings").performClick()

        composeTestRule.onNodeWithText("App Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Default Export Format").assertIsDisplayed()

        // Open the export-format dropdown and switch WebP -> GIF.
        composeTestRule.onNodeWithText("Animated WebP").performClick()
        composeTestRule.onNodeWithText("Standard GIF").performClick()
        composeTestRule.onNodeWithText("Standard GIF").assertIsDisplayed()
    }
}
