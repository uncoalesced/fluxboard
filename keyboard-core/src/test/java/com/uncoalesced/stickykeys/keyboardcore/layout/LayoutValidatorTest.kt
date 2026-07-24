// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.layout

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class LayoutValidatorTest {

    private fun buildValidLayout(): KeyboardLayoutConfig {
        return KeyboardLayoutConfig(
            id = "test",
            name = "Test",
            rows = listOf(
                listOf(
                    KeyDefinition("k1", "q"),
                    KeyDefinition("k2", "w"),
                    KeyDefinition("k3", "e")
                ),
                listOf(
                    KeyDefinition("k4", "SHIFT", weight = 1.5f),
                    KeyDefinition("k5", "a"),
                    KeyDefinition("k6", "DEL", weight = 1.5f)
                ),
                listOf(
                    KeyDefinition("k7", "SPACE", weight = 4f),
                    KeyDefinition("k8", "ENTER", weight = 1.5f)
                )
            )
        )
    }

    @Test
    fun `valid layout passes validation`() {
        val result = LayoutValidator.validate(buildValidLayout())
        assertTrue(result is LayoutValidationResult.Valid)
    }

    @Test
    fun `missing SPACE key fails`() {
        val layout = buildValidLayout().let { config ->
            config.copy(rows = config.rows.map { row ->
                row.filter { it.output != "SPACE" }
            })
        }
        val result = LayoutValidator.validate(layout)
        assertTrue(result is LayoutValidationResult.Invalid)
        val errors = (result as LayoutValidationResult.Invalid).reasons
        assertTrue(errors.any { "SPACE" in it })
    }

    @Test
    fun `missing DEL key fails`() {
        val layout = buildValidLayout().let { config ->
            config.copy(rows = config.rows.map { row ->
                row.filter { it.output != "DEL" }
            })
        }
        val result = LayoutValidator.validate(layout)
        assertTrue(result is LayoutValidationResult.Invalid)
        val errors = (result as LayoutValidationResult.Invalid).reasons
        assertTrue(errors.any { "DEL" in it })
    }

    @Test
    fun `missing ENTER key fails`() {
        val layout = buildValidLayout().let { config ->
            config.copy(rows = config.rows.map { row ->
                row.filter { it.output != "ENTER" }
            })
        }
        val result = LayoutValidator.validate(layout)
        assertTrue(result is LayoutValidationResult.Invalid)
        val errors = (result as LayoutValidationResult.Invalid).reasons
        assertTrue(errors.any { "ENTER" in it })
    }

    @Test
    fun `empty layout fails`() {
        val layout = KeyboardLayoutConfig("test", "Empty", emptyList())
        val result = LayoutValidator.validate(layout)
        assertTrue(result is LayoutValidationResult.Invalid)
    }

    @Test
    fun `too many rows fails`() {
        val valid = buildValidLayout()
        val manyRows = (1..6).map { listOf(KeyDefinition("extra_$it", "x")) }
        val layout = valid.copy(rows = manyRows)
        val result = LayoutValidator.validate(layout)
        assertTrue(result is LayoutValidationResult.Invalid)
        val errors = (result as LayoutValidationResult.Invalid).reasons
        assertTrue(errors.any { "rows" in it.lowercase() })
    }

    @Test
    fun `too many keys in one row fails`() {
        val valid = buildValidLayout()
        val bigRow = (1..15).map { KeyDefinition("big_$it", "x") }
        val layout = valid.copy(rows = valid.rows + listOf(bigRow))
        val result = LayoutValidator.validate(layout)
        assertTrue(result is LayoutValidationResult.Invalid)
        val errors = (result as LayoutValidationResult.Invalid).reasons
        assertTrue(errors.any { "keys" in it.lowercase() || "maximum" in it.lowercase() })
    }

    @Test
    fun `zero weight key fails`() {
        val valid = buildValidLayout()
        val modifiedRows = valid.rows.toMutableList()
        val firstRow = modifiedRows[0].toMutableList()
        firstRow[0] = firstRow[0].copy(weight = 0f)
        modifiedRows[0] = firstRow
        val layout = valid.copy(rows = modifiedRows)
        val result = LayoutValidator.validate(layout)
        assertTrue(result is LayoutValidationResult.Invalid)
        val errors = (result as LayoutValidationResult.Invalid).reasons
        assertTrue(errors.any { "weight" in it.lowercase() })
    }

    @Test
    fun `duplicate IDs fail`() {
        val layout = KeyboardLayoutConfig(
            id = "test",
            name = "Dup",
            rows = listOf(
                listOf(
                    KeyDefinition("same_id", "q"),
                    KeyDefinition("same_id", "w")
                ),
                listOf(
                    KeyDefinition("k1", "SPACE", weight = 4f),
                    KeyDefinition("k2", "DEL", weight = 1.5f),
                    KeyDefinition("k3", "ENTER", weight = 1.5f)
                )
            )
        )
        val result = LayoutValidator.validate(layout)
        assertTrue(result is LayoutValidationResult.Invalid)
        val errors = (result as LayoutValidationResult.Invalid).reasons
        assertTrue(errors.any { "Duplicate" in it })
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyboardLayoutConfigTest {

    @Test
    fun `json round trip preserves data`() {
        val original = KeyboardLayoutConfig(
            id = "test_layout",
            name = "Test Layout",
            rows = listOf(
                listOf(
                    KeyDefinition("k1", "q", null, 1.0f),
                    KeyDefinition("k2", "w", null, 1.0f)
                ),
                listOf(
                    KeyDefinition("k3", "SPACE", "___", 4.0f),
                    KeyDefinition("k4", "DEL", null, 1.5f),
                    KeyDefinition("k5", "ENTER", null, 1.5f)
                )
            )
        )

        val json = original.toJson().toString(2)
        val restored = KeyboardLayoutConfig.fromJson(json)

        assertEquals(original.id, restored.id)
        assertEquals(original.name, restored.name)
        assertEquals(original.rows.size, restored.rows.size)

        original.rows.forEachIndexed { rowIdx, row ->
            row.forEachIndexed { keyIdx, key ->
                val restoredKey = restored.rows[rowIdx][keyIdx]
                assertEquals(key.id, restoredKey.id)
                assertEquals(key.output, restoredKey.output)
                assertEquals(key.displayLabel, restoredKey.displayLabel)
                assertEquals(key.weight, restoredKey.weight, 0.001f)
            }
        }
    }

    @Test
    fun `fromLegacyLayout converts correctly`() {
        val legacy = listOf(
            listOf("q", "w", "e"),
            listOf("SHIFT", "a", "DEL"),
            listOf("SYMBOLS", "SPACE", "ENTER")
        )

        val config = KeyboardLayoutConfig.fromLegacyLayout("test", "Test", legacy)

        assertEquals(3, config.rows.size)
        assertEquals(3, config.rows[0].size)
        assertEquals("q", config.rows[0][0].output)
        assertEquals(1.0f, config.rows[0][0].weight, 0.001f)
        assertEquals("SHIFT", config.rows[1][0].output)
        assertEquals(1.5f, config.rows[1][0].weight, 0.001f)
        assertEquals("SPACE", config.rows[2][1].output)
        assertEquals(4.0f, config.rows[2][1].weight, 0.001f)
    }
}
