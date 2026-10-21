// Engineered by uncoalesced
package com.uncoalesced.stickykeys.baselineprofile

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
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

/**
 * Records what the app and the keyboard run on first use: launching the app, focusing a
 * text field so the keyboard comes up, and typing a few words through real key taps.
 *
 * The keyboard is driven by tapping its keys, never with `input text`: that bypasses the IME
 * entirely, so the typing path would never appear in the profile. Keys are found by their
 * content description, which for a letter is the letter itself, in the input-method window's
 * accessibility tree: UiAutomator's own search looks only at the focused app window, and the
 * keyboard is a separate window.
 *
 * Whatever keyboard was selected before the run is selected again afterwards, because this
 * runs on a real phone that somebody types on.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    private val targetPackage: String =
        InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: error("targetAppId instrumentation argument not passed")

    private val imeId = "$targetPackage/$IME_CLASS"

    private var previousIme: String = ""

    @Before
    fun bindKeyboard() {
        val automation = instrumentation.uiAutomation
        automation.serviceInfo =
            automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
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
            // The rule kills the app between runs, and the system answers a killed keyboard by
            // switching to another one, so the binding from @Before does not survive. Bound
            // again here, inside the measured block, every time.
            device.executeShellCommand("ime enable $imeId")
            device.executeShellCommand("ime set $imeId")

            val field = device.wait(Until.findObject(By.text("Search settings")), WAIT_MS)
            if (field != null) {
                field.click()
                if (waitForKey("h")) {
                    for (word in listOf("hello", "the", "keyboard")) {
                        word.forEach { tapKey(it.toString()) }
                        tapKey("Space")
                    }
                }
            }
            device.pressBack()
            device.pressBack()
        }

    /** The keyboard window's key labelled [label], ignoring case, or null if it is not up. */
    private fun findKey(label: String): AccessibilityNodeInfo? {
        val ime =
            instrumentation.uiAutomation.windows
                .firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                ?: return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        ime.root?.let(queue::add)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.contentDescription?.toString()?.equals(label, ignoreCase = true) == true) {
                return node
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return null
    }

    private fun waitForKey(label: String): Boolean {
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            if (findKey(label) != null) return true
            Thread.sleep(POLL_MS)
        }
        return false
    }

    private fun tapKey(label: String) {
        val node = findKey(label) ?: return
        val bounds = Rect().also(node::getBoundsInScreen)
        device.click(bounds.centerX(), bounds.centerY())
        device.waitForIdle()
    }

    private companion object {
        const val IME_CLASS = "com.uncoalesced.stickykeys.keyboardcore.ime.StickyKeysIME"
        const val WAIT_MS = 5_000L
        const val POLL_MS = 200L
    }
}
