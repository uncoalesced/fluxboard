// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.layout

import androidx.test.core.app.ApplicationProvider
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

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
 * Uses Robolectric so [LayoutManager] deletion and active-layout fallback tests run
 * against real `filesDir` and `KeyboardPreferences` alongside the pure `resolveLayout` checks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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

    private suspend fun freshLayoutManager(
        context: android.content.Context,
        prefs: KeyboardPreferences,
    ): LayoutManager =
        LayoutManager(context, prefs).also { manager ->
            withTimeout(5_000L) {
                manager.availableLayouts.first { it.isNotEmpty() }
            }
        }

    @Test
    fun `deleteCustomLayout removes layout json and keeps preset qwerty`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val layoutsDir = File(context.filesDir, "layouts").apply { deleteRecursively() }
            val prefs = KeyboardPreferences(context)
            prefs.setActiveLayoutId(LayoutManager.DEFAULT_LAYOUT_ID)

            val manager = freshLayoutManager(context, prefs)
            val custom =
                LayoutManager.buildDefaultLayout().copy(
                    id = "custom_delete_layout",
                    name = "Delete Layout",
                )
            assertEquals(LayoutValidationResult.Valid, manager.saveCustomLayout(custom))
            val jsonFile = File(layoutsDir, "custom_delete_layout.json")
            assertTrue(jsonFile.exists())
            withTimeout(5_000L) {
                manager.availableLayouts.first { list ->
                    list.any { it.id == "custom_delete_layout" }
                }
            }

            manager.deleteCustomLayout("custom_delete_layout")

            withTimeout(5_000L) {
                manager.availableLayouts.first { list ->
                    list.none { it.id == "custom_delete_layout" }
                }
            }
            assertFalse(jsonFile.exists())
            assertTrue(
                manager.availableLayouts.value.any { it.id == LayoutManager.DEFAULT_LAYOUT_ID },
            )
        }

    @Test
    fun `deleteCustomLayout falls back to preset qwerty when active custom layout is deleted`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            File(context.filesDir, "layouts").deleteRecursively()
            val prefs = KeyboardPreferences(context)
            prefs.setActiveLayoutId(LayoutManager.DEFAULT_LAYOUT_ID)

            val manager = freshLayoutManager(context, prefs)
            val custom =
                LayoutManager.buildDefaultLayout().copy(
                    id = "custom_active_layout",
                    name = "Active Custom Layout",
                )
            assertEquals(LayoutValidationResult.Valid, manager.saveCustomLayout(custom))
            withTimeout(5_000L) {
                manager.availableLayouts.first { list ->
                    list.any { it.id == "custom_active_layout" }
                }
            }
            prefs.setActiveLayoutId("custom_active_layout")
            withTimeout(5_000L) {
                manager.activeLayout.first { it.id == "custom_active_layout" }
            }

            manager.deleteCustomLayout("custom_active_layout")

            withTimeout(5_000L) {
                manager.activeLayout.first { it.id == LayoutManager.DEFAULT_LAYOUT_ID }
            }
            assertEquals(LayoutManager.DEFAULT_LAYOUT_ID, prefs.activeLayoutId.value)
            assertEquals(LayoutManager.DEFAULT_LAYOUT_ID, manager.activeLayout.value.id)
        }

    @Test
    fun `deleteCustomLayout ignores built in preset id`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            File(context.filesDir, "layouts").deleteRecursively()
            val prefs = KeyboardPreferences(context)
            val manager = freshLayoutManager(context, prefs)

            manager.deleteCustomLayout(LayoutManager.DEFAULT_LAYOUT_ID)

            assertTrue(
                manager.availableLayouts.value.any { it.id == LayoutManager.DEFAULT_LAYOUT_ID },
            )
        }
}
