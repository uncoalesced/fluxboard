// Engineered by uncoalesced
package com.uncoalesced.stickykeys.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * Records what the app and the keyboard run on first use: launching the app, focusing a
 * text field so the keyboard comes up, and typing a few words through real key taps.
 *
 * The keyboard is driven by tapping its keys, never with `input text`: that bypasses the IME
 * entirely, so the typing path would never appear in the profile. Keys are found by their
 * content description, which for a letter is the letter itself.
 *
 * Whatever keyboard was selected before the run is selected again afterwards, because this
 * runs on a real phone that somebody types on.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private val targetPackage: String =
        InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: error("targetAppId instrumentation argument not passed")

    private val imeId = "$targetPackage/$IME_CLASS"

    private var previousIme: String = ""

    @Before
    fun bindKeyboard() {
        previousIme = device.executeShellCommand("settings get secure default_input_method").trim()
        device.executeShellCommand("ime enable $imeId")
        device.executeShellCommand("ime set $imeId")
    }

    @After
    fun restoreKeyboard() {
        if (previousIme.isNotEmpty() && previousIme != "null") {
            device.executeShellCommand("ime set $previousIme")
        }
    }

    @Test
    fun generate() =
        rule.collect(packageName = targetPackage, includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()

            val field = device.wait(Until.findObject(By.text("Search settings")), WAIT_MS)
            if (field != null) {
                field.click()
                if (device.wait(Until.hasObject(key("h")), WAIT_MS)) {
                    for (word in listOf("hello", "the", "keyboard")) {
                        word.forEach { tapKey(it.toString()) }
                        tapKey("Space")
                    }
                }
            }
            device.pressBack()
            device.pressBack()
        }

    private fun key(label: String) = By.desc(Pattern.compile(Pattern.quote(label), Pattern.CASE_INSENSITIVE))

    private fun tapKey(label: String) {
        device.findObject(key(label))?.click()
        device.waitForIdle()
    }

    private companion object {
        const val IME_CLASS = "com.uncoalesced.stickykeys.keyboardcore.ime.StickyKeysIME"
        const val WAIT_MS = 5_000L
    }
}
