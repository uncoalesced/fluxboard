// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.layout

/** Result of validating a keyboard layout configuration. */
sealed interface LayoutValidationResult {
    data object Valid : LayoutValidationResult

    data class Invalid(
        val reasons: List<String>,
    ) : LayoutValidationResult
}

/** Validates a KeyboardLayoutConfig against structural and usability rules. */
object LayoutValidator {
    private const val MAX_ROWS = 5
    private const val MAX_KEYS_PER_ROW = 14

    /**
     * Outputs a layout cannot be saved without. The bar is reachability: losing any of
     * these makes a whole class of input unreachable with no way back from the keyboard
     * itself. SYMBOLS is on the list because the symbol/numeric planes are only ever
     * entered through it -- a letters-only layout that drops it soft-bricks every digit
     * and punctuation mark. SHIFT is deliberately not on the list: without it text is
     * still fully typeable, just always lowercase.
     */
    private val requiredOutputs = listOf("SPACE", "DEL", "ENTER", "SYMBOLS")

    /**
     * Outputs a symbol page cannot be saved without.
     *
     * A shorter list than [requiredOutputs] because the two planes are reached differently.
     * `ABC` is what gets the user back to the letters, and losing it strands them on a page
     * with no alphabet -- the mirror image of losing `SYMBOLS`. `SYMBOLS` itself must *not* be
     * required here: the symbol pages are where the user already is.
     */
    private val requiredSymbolOutputs = listOf("SPACE", "DEL", "ENTER", "ABC")

    fun validate(config: KeyboardLayoutConfig): LayoutValidationResult {
        val errors = mutableListOf<String>()

        // Each page is checked on its own terms. Flattening all three together would be wrong
        // twice over: a comma on the letters page and a comma on a symbol page are separate
        // keys that legitimately share an id, and each page needs its own reachability rules.
        errors += validatePage(config.rows, "Letters", requiredOutputs)
        errors += validatePage(config.symbolRows, "Symbols", requiredSymbolOutputs)
        errors += validatePage(config.symbolShiftedRows, "Symbols 2", requiredSymbolOutputs)

        return if (errors.isEmpty()) {
            LayoutValidationResult.Valid
        } else {
            LayoutValidationResult.Invalid(errors)
        }
    }

    private fun validatePage(
        rows: List<List<KeyDefinition>>,
        page: String,
        required: List<String>,
    ): List<String> {
        val errors = mutableListOf<String>()

        // Row count
        if (rows.isEmpty()) {
            errors.add("$page must have at least one row.")
        }
        if (rows.size > MAX_ROWS) {
            errors.add("$page has ${rows.size} rows, maximum is $MAX_ROWS.")
        }

        // Per-row checks
        rows.forEachIndexed { index, row ->
            if (row.isEmpty()) {
                errors.add("$page row ${index + 1} is empty.")
            }
            if (row.size > MAX_KEYS_PER_ROW) {
                errors.add(
                    "$page row ${index + 1} has ${row.size} keys, maximum is $MAX_KEYS_PER_ROW.",
                )
            }
        }

        // Weight check
        val allKeys = rows.flatten()
        allKeys.forEach { key ->
            if (key.weight <= 0f) {
                errors.add("Key '${key.id}' has non-positive weight ${key.weight}.")
            }
        }

        // Duplicate IDs
        val ids = allKeys.map { it.id }
        val duplicates = ids.groupBy { it }.filter { it.value.size > 1 }.keys
        if (duplicates.isNotEmpty()) {
            errors.add("Duplicate key IDs on $page: ${duplicates.joinToString(", ")}.")
        }

        // Required keys
        val outputs = allKeys.map { it.output }.toSet()
        for (req in required) {
            if (req !in outputs) {
                errors.add("$page is missing required key: $req.")
            }
        }

        return errors
    }
}
