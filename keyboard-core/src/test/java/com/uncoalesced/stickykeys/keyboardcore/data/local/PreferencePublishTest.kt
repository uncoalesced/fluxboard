// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.data.local

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every setter must publish to its own flow, synchronously.
 *
 * This used to be done by a `SharedPreferences.OnSharedPreferenceChangeListener`, and in a
 * release build it did nothing at all. SharedPreferences holds its listeners in a WeakHashMap,
 * so the `private val listener` field was the only strong reference to ours -- and R8, seeing a
 * field written once and read once, deleted the field. The listener was collected at the first
 * GC and no preference change reached any flow again for the life of the process.
 *
 * It was invisible in debug (no R8) and invisible in unit tests (no R8), which is exactly why it
 * shipped. So this test cannot reproduce the original failure -- nothing running on the JVM can.
 * What it *can* do is pin the mechanism that replaced it, which is the thing a future edit would
 * break: a setter that writes SharedPreferences and forgets its flow is silently the same bug
 * again, for that one preference.
 *
 * `scripts/check-source-rules.sh` covers the other half by refusing to let a listener come back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreferencePublishTest {
    private fun prefs() = KeyboardPreferences(ApplicationProvider.getApplicationContext())

    @Test
    fun `every boolean setter publishes immediately`() {
        val p = prefs()

        // Each is toggled away from its default and read back off the flow with no dispatcher
        // run in between -- the IME reads these while a finger is still on the key.
        p.setAutoCapitalize(false)
        assertFalse("auto capitalize", p.autoCapitalizeEnabled.value)

        p.setAutoCorrect(false)
        assertFalse("auto correct", p.autoCorrectEnabled.value)

        p.setGlideTyping(false)
        assertFalse("glide typing", p.glideTypingEnabled.value)

        p.setShowNumberRow(false)
        assertFalse("number row", p.showNumberRow.value)

        p.setShowKeyHints(false)
        assertFalse("key hints", p.showKeyHints.value)

        p.setDoubleSpacePeriod(false)
        assertFalse("double space period", p.doubleSpacePeriodEnabled.value)

        p.setHapticsEnabled(false)
        assertFalse("haptics", p.hapticsEnabled.value)
    }

    @Test
    fun `the privacy switch turns off again`() {
        // The direction that mattered. With the listener dead, this switch could be turned on
        // and never off within one process -- and because it suppresses suggestions, autocorrect
        // and glide, that left the keyboard's headline feature dead with no way back.
        val p = prefs()

        p.setPrivateMode(true)
        assertTrue(p.privateModeEnabled.value)

        p.setPrivateMode(false)
        assertFalse(p.privateModeEnabled.value)
    }

    @Test
    fun `string setters publish`() {
        val p = prefs()

        p.setActiveThemeId("custom_something")
        assertEquals("custom_something", p.activeThemeId.value)

        p.setActiveLayoutId("custom_layout")
        assertEquals("custom_layout", p.activeLayoutId.value)
    }

    @Test
    fun `clamped setters publish the clamped value, not the argument`() {
        // The stored number and the published one come from one clamp on purpose. Clamping
        // twice from two expressions is how a slider ends up showing something the keyboard
        // is not using.
        val p = prefs()

        p.setKeyboardHeightPercent(9999)
        assertEquals(
            KeyboardPreferences.MAX_KEYBOARD_HEIGHT_PERCENT,
            p.keyboardHeightPercent.value,
        )

        p.setKeySizePercent(0)
        assertEquals(KeyboardPreferences.MIN_KEY_SIZE_PERCENT, p.keySizePercent.value)

        p.setHapticsIntensity(500)
        assertEquals(100, p.hapticsIntensity.value)

        p.setKeyboardBottomPaddingDp(-5)
        assertEquals(0, p.keyboardBottomPaddingDp.value)
    }
}
