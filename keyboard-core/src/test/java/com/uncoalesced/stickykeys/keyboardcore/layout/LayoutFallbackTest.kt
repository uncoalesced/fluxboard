// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The layout half of the dangling-pointer defence.
 *
 * `ThemeFallbackTest` pins the same property for themes, and this exists because
 * `LayoutManager` carried the identical defect for longer: both observers were
 * `if (layout != null) set it`, so an `active_layout_id` matching nothing was silently
 * dropped. The active layout id lives in SharedPreferences and the layouts themselves in
 * `filesDir`, so a pointer to a layout that was deleted or quarantined survives force-stop,
 * cache clearing and reboot -- the user's chosen layout stays un-applied on every launch and
 * nothing anywhere says why.
 *
 * Plain JUnit rather than Robolectric: unlike themes, nothing here parses a colour.
 */
class LayoutFallbackTest {
    private fun layout(id: String) =
        KeyboardLayoutConfig(
            id = id,
            name = id,
            rows = LayoutManager.buildDefaultLayout().rows,
        )

    private val preset = LayoutManager.buildDefaultLayout()

    @Test
    fun `an id that resolves is returned untouched and repairs nothing`() {
        val custom = layout("custom_abc")
        val resolved = resolveLayout(listOf(preset, custom), "custom_abc")!!

        assertSame(custom, resolved.layout)
        // The repair must be null, not "the same id again": a non-null value here would write
        // to SharedPreferences on every emission of the layout list.
        assertNull(resolved.repairedId)
    }

    @Test
    fun `an id matching nothing falls back to the built in preset`() {
        // The regression itself. Before the fix this case published nothing at all and left
        // whatever was previously in place, which is how a deleted layout stayed "active".
        val resolved = resolveLayout(listOf(preset, layout("custom_abc")), "custom_deleted")!!

        assertEquals(LayoutManager.DEFAULT_LAYOUT_ID, resolved.layout.id)
    }

    @Test
    fun `an unresolvable id also repairs the stored preference`() {
        // Falling back without repairing would mean every launch rediscovers the same broken
        // pointer. This is the half that stops the fault from being permanent.
        val resolved = resolveLayout(listOf(preset), "custom_deleted")!!

        assertEquals(LayoutManager.DEFAULT_LAYOUT_ID, resolved.repairedId)
    }

    @Test
    fun `the built in preset is preferred over merely the first entry`() {
        // Order matters: the built-in is the one layout that cannot be missing or malformed,
        // because loadLayouts adds it before it touches the disk.
        val available = listOf(layout("custom_first"), preset, layout("custom_last"))
        val resolved = resolveLayout(available, "custom_deleted")!!

        assertEquals(LayoutManager.DEFAULT_LAYOUT_ID, resolved.layout.id)
    }

    @Test
    fun `a list without the preset still resolves rather than publishing nothing`() {
        // Defence in depth. loadLayouts should never produce this, but "should never" is what
        // the original bug relied on too, and publishing nothing is the failure being avoided.
        val only = layout("custom_only")
        val resolved = resolveLayout(listOf(only), "custom_deleted")!!

        assertSame(only, resolved.layout)
        assertEquals("custom_only", resolved.repairedId)
    }

    @Test
    fun `nothing loaded yet is not treated as a failure`() {
        // Null means "ask again once there is something to resolve against", not "fall back".
        // Falling back here would fight the real answer arriving microseconds later and would
        // overwrite a perfectly good stored id with the preset on every cold start.
        assertNull(resolveLayout(emptyList(), "custom_abc"))
    }

    @Test
    fun `the built in layout is itself valid`() {
        // The fallback target has to survive the validator, or the recovery path leads
        // somewhere that loadLayouts would quarantine.
        assertEquals(
            LayoutValidationResult.Valid,
            LayoutValidator.validate(preset),
        )
    }
}
