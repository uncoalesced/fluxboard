// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens

import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import com.uncoalesced.stickykeys.keyboardcore.layout.KeyboardLayoutConfig
import com.uncoalesced.stickykeys.keyboardcore.layout.LayoutManager
import com.uncoalesced.stickykeys.keyboardcore.layout.LayoutValidationResult
import com.uncoalesced.stickykeys.keyboardcore.layout.LayoutValidator
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Unit tests for the layout editor's mutation and save logic. */
@OptIn(ExperimentalCoroutinesApi::class)
class LayoutEditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var layoutManager: LayoutManager
    private lateinit var preferences: KeyboardPreferences
    private val activeLayout = MutableStateFlow(LayoutManager.buildDefaultLayout())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        layoutManager = mockk(relaxed = true)
        preferences = mockk(relaxed = true)
        every { layoutManager.activeLayout } returns activeLayout
        every { layoutManager.availableLayouts } returns MutableStateFlow(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = LayoutEditorViewModel(layoutManager, preferences)

    private fun KeyboardLayoutConfig.key(id: String) = rows.flatten().first { it.id == id }

    @Test
    fun `swapKeys exchanges two keys in a row`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            val before = vm.editingLayout.value.rows[0]

            vm.swapKeys(rowIndex = 0, fromIndex = 0, toIndex = 1)

            val after = vm.editingLayout.value.rows[0]
            assertEquals(before[1].id, after[0].id)
            assertEquals(before[0].id, after[1].id)
        }

    @Test
    fun `swapKeys ignores an out-of-range index instead of crashing`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            val before = vm.editingLayout.value

            vm.swapKeys(rowIndex = 0, fromIndex = 0, toIndex = 99)

            assertEquals(before, vm.editingLayout.value)
        }

    @Test
    fun `adjustWeight clamps to the allowed range in both directions`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            val id =
                vm.editingLayout.value.rows[0][0]
                    .id

            repeat(20) { vm.adjustWeight(id, -1f) }
            assertEquals(
                0.5f,
                vm.editingLayout.value
                    .key(id)
                    .weight,
                0.001f,
            )

            repeat(40) { vm.adjustWeight(id, 1f) }
            assertEquals(
                6.0f,
                vm.editingLayout.value
                    .key(id)
                    .weight,
                0.001f,
            )
        }

    @Test
    fun `remapKey changes only the target key and clears the selection`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            val target =
                vm.editingLayout.value.rows[0][0]
                    .id
            val untouched = vm.editingLayout.value.rows[0][1]
            vm.selectKey(target)

            vm.remapKey(target, "Z")

            assertEquals(
                "Z",
                vm.editingLayout.value
                    .key(target)
                    .output,
            )
            assertEquals(
                untouched.output,
                vm.editingLayout.value
                    .key(untouched.id)
                    .output,
            )
            assertNull(vm.selectedKeyId.value)
        }

    @Test
    fun `saving an edited preset forks it into a new custom layout`() =
        runTest(dispatcher) {
            // Presets must stay pristine: editing one and saving has to produce a copy,
            // not overwrite the built-in layout every other install relies on.
            val saved = slot<KeyboardLayoutConfig>()
            coEvery { layoutManager.saveCustomLayout(capture(saved)) } returns
                LayoutValidationResult.Valid
            val vm = viewModel()
            advanceUntilIdle()

            vm.saveLayout()
            advanceUntilIdle()

            assertTrue(
                "expected a custom id, got ${saved.captured.id}",
                saved.captured.id.startsWith("custom_"),
            )
            assertTrue(saved.captured.name.startsWith("Custom "))
            assertTrue(vm.saveSuccess.value)
            assertTrue(vm.validationErrors.value.isEmpty())
        }

    @Test
    fun `validation failure surfaces the reasons and does not report success`() =
        runTest(dispatcher) {
            coEvery { layoutManager.saveCustomLayout(any()) } returns
                LayoutValidationResult.Invalid(listOf("Layout is missing required key: SYMBOLS."))
            val vm = viewModel()
            advanceUntilIdle()

            vm.saveLayout()
            advanceUntilIdle()

            assertFalse(vm.saveSuccess.value)
            assertEquals(1, vm.validationErrors.value.size)
            assertTrue(
                vm.validationErrors.value
                    .single()
                    .contains("SYMBOLS"),
            )
        }

    @Test
    fun `a layout stripped of its SYMBOLS key is rejected by the validator`() =
        runTest(dispatcher) {
            // End-to-end on the rule itself: the editor can remove keys, and dropping
            // SYMBOLS leaves no way to reach digits or punctuation.
            val vm = viewModel()
            advanceUntilIdle()
            val stripped =
                vm.editingLayout.value.let { config ->
                    config.copy(
                        rows =
                            config.rows.map { row ->
                                row.filter { it.output != "SYMBOLS" }
                            },
                    )
                }

            val result = LayoutValidator.validate(stripped)

            assertTrue(result is LayoutValidationResult.Invalid)
            assertTrue(
                (result as LayoutValidationResult.Invalid).reasons.any { "SYMBOLS" in it },
            )
        }

    @Test
    fun `resetToDefault restores the built-in layout and clears prior errors`() =
        runTest(dispatcher) {
            coEvery { layoutManager.saveCustomLayout(any()) } returns
                LayoutValidationResult.Invalid(listOf("boom"))
            val vm = viewModel()
            advanceUntilIdle()
            vm.saveLayout()
            advanceUntilIdle()
            vm.remapKey(
                vm.editingLayout.value.rows[0][0]
                    .id,
                "Z",
            )

            vm.resetToDefault()

            assertEquals(LayoutManager.buildDefaultLayout(), vm.editingLayout.value)
            assertTrue(vm.validationErrors.value.isEmpty())
            assertFalse(vm.saveSuccess.value)
        }
}
