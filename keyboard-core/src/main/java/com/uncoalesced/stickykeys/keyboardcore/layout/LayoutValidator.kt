// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.layout

/** Result of validating a keyboard layout configuration. */
sealed interface LayoutValidationResult {
    data object Valid : LayoutValidationResult
    data class Invalid(val reasons: List<String>) : LayoutValidationResult
}

/** Validates a KeyboardLayoutConfig against structural and usability rules. */
object LayoutValidator {

    private const val MAX_ROWS = 5
    private const val MAX_KEYS_PER_ROW = 14

    private val requiredOutputs = listOf("SPACE", "DEL", "ENTER")

    fun validate(config: KeyboardLayoutConfig): LayoutValidationResult {
        val errors = mutableListOf<String>()

        // Row count
        if (config.rows.isEmpty()) {
            errors.add("Layout must have at least one row.")
        }
        if (config.rows.size > MAX_ROWS) {
            errors.add("Layout has ${config.rows.size} rows, maximum is $MAX_ROWS.")
        }

        // Per-row checks
        config.rows.forEachIndexed { index, row ->
            if (row.isEmpty()) {
                errors.add("Row ${index + 1} is empty.")
            }
            if (row.size > MAX_KEYS_PER_ROW) {
                errors.add("Row ${index + 1} has ${row.size} keys, maximum is $MAX_KEYS_PER_ROW.")
            }
        }

        // Weight check
        val allKeys = config.rows.flatten()
        allKeys.forEach { key ->
            if (key.weight <= 0f) {
                errors.add("Key '${key.id}' has non-positive weight ${key.weight}.")
            }
        }

        // Duplicate IDs
        val ids = allKeys.map { it.id }
        val duplicates = ids.groupBy { it }.filter { it.value.size > 1 }.keys
        if (duplicates.isNotEmpty()) {
            errors.add("Duplicate key IDs: ${duplicates.joinToString(", ")}.")
        }

        // Required keys
        val outputs = allKeys.map { it.output }.toSet()
        for (req in requiredOutputs) {
            if (req !in outputs) {
                errors.add("Layout is missing required key: $req.")
            }
        }

        return if (errors.isEmpty()) {
            LayoutValidationResult.Valid
        } else {
            LayoutValidationResult.Invalid(errors)
        }
    }
}
