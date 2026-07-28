// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.components

import androidx.test.core.app.ApplicationProvider
import com.uncoalesced.stickykeys.data.local.AppPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The four-condition rule behind the one-time default-keyboard prompt. */
class DefaultKeyboardPromptRuleTest {
    private fun due(
        enabled: Boolean = true,
        alreadyDefault: Boolean = false,
        visited: Boolean = true,
        shown: Boolean = false,
    ) = shouldShowDefaultKeyboardPrompt(enabled, alreadyDefault, visited, shown)

    @Test
    fun `due once enabled, customised, not default and never shown`() {
        assertTrue(due())
    }

    @Test
    fun `not due before the keyboard is even enabled`() {
        // Asking someone to make a keyboard their default before it is switched on at all
        // sends them to a picker that cannot offer it.
        assertFalse(due(enabled = false))
    }

    @Test
    fun `not due when FluxBoard is already the default`() {
        assertFalse(due(alreadyDefault = true))
    }

    @Test
    fun `not due before the user has customised anything`() {
        // The trigger: the prompt waits until the user has opened the theme or layout
        // editor, so it lands after they have a reason to say yes.
        assertFalse(due(visited = false))
    }

    @Test
    fun `never due a second time, whatever the first answer was`() {
        assertFalse(due(shown = true))
    }

    @Test
    fun `every condition is load-bearing`() {
        // Flipping any single input away from the due case must suppress the prompt.
        assertTrue(due())
        listOf(
            due(enabled = false),
            due(alreadyDefault = true),
            due(visited = false),
            due(shown = true),
        ).forEachIndexed { i, result ->
            assertFalse("condition $i did not suppress the prompt", result)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DefaultKeyboardPromptPersistenceTest {
    private fun prefs() = AppPreferences(ApplicationProvider.getApplicationContext())

    @Test
    fun `both flags start false on a fresh install`() {
        val p = prefs()
        assertFalse(p.hasVisitedCustomizer)
        assertFalse(p.hasShownDefaultKeyboardPrompt)
    }

    @Test
    fun `visiting a customizer is persisted across instances`() {
        prefs().markCustomizerVisited()
        assertTrue(prefs().hasVisitedCustomizer)
    }

    @Test
    fun `showing the prompt is persisted across instances`() {
        // Recorded when shown, not when accepted -- declining must not leave it queued.
        prefs().markDefaultKeyboardPromptShown()
        assertTrue(prefs().hasShownDefaultKeyboardPrompt)
    }

    @Test
    fun `once shown, the rule never fires again even with everything else still true`() {
        val p = prefs()
        p.markCustomizerVisited()
        assertTrue(
            shouldShowDefaultKeyboardPrompt(
                keyboardEnabled = true,
                alreadyDefault = false,
                visitedCustomizer = p.hasVisitedCustomizer,
                alreadyShown = p.hasShownDefaultKeyboardPrompt,
            ),
        )

        p.markDefaultKeyboardPromptShown()

        assertFalse(
            shouldShowDefaultKeyboardPrompt(
                keyboardEnabled = true,
                alreadyDefault = false,
                visitedCustomizer = p.hasVisitedCustomizer,
                alreadyShown = p.hasShownDefaultKeyboardPrompt,
            ),
        )
    }
}
