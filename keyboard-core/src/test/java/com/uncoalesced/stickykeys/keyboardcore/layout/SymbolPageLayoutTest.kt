// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.layout

import com.uncoalesced.stickykeys.keyboardcore.ime.KeyboardLayouts
import com.uncoalesced.stickykeys.keyboardcore.ime.KeyboardMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The symbol pages as part of the layout, and the compatibility promise that comes with it.
 *
 * The pages used to be hardcoded `List<List<String>>` outside `KeyboardLayoutConfig`, converted
 * at render time. That is the single reason they could not be remapped, weighted, or carry the
 * corner hints and long-press alternates the letter pages have always had.
 *
 * Moving them into the config changes a *persisted* format, which is the risk worth testing:
 * a layout file written before this existed has no symbol rows in it, and if that loaded as an
 * empty symbols plane every tester with a custom layout would get a keyboard with no digits and
 * no punctuation -- the same class of failure as the blank-canvas bug.
 *
 * Robolectric because the JSON round-trip goes through `org.json`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SymbolPageLayoutTest {
    /** Exactly what a layout file looked like before symbol pages were part of one. */
    private val legacyLayoutJson =
        """
        {
          "id": "custom_legacy",
          "name": "Custom QWERTY",
          "rows": [
            [
              {"id": "key_a", "output": "a", "weight": 1.0},
              {"id": "key_b", "output": "b", "weight": 1.0}
            ],
            [
              {"id": "key_space", "output": "SPACE", "weight": 4.0},
              {"id": "key_del", "output": "DEL", "weight": 1.5},
              {"id": "key_enter", "output": "ENTER", "weight": 1.5},
              {"id": "key_symbols", "output": "SYMBOLS", "weight": 1.5}
            ]
          ]
        }
        """.trimIndent()

    @Test
    fun `a layout saved before symbol pages existed still has working symbol pages`() {
        // The compatibility promise, stated as an assertion. Silently loading empty pages here
        // is the specific failure this whole change had to avoid.
        val restored = KeyboardLayoutConfig.fromJson(legacyLayoutJson)

        assertEquals(KeyboardLayouts.symbolsPrimaryRows, restored.symbolRows)
        assertEquals(KeyboardLayouts.symbolsShiftedRows, restored.symbolShiftedRows)
    }

    @Test
    fun `an untouched layout does not write symbol pages into its file`() {
        // Writing them unconditionally would freeze today's pages into every custom layout, so
        // a user who reordered two letters would never receive a shipped symbol-page fix again.
        val config =
            KeyboardLayoutConfig(
                id = "custom_x",
                name = "X",
                rows = LayoutManager.buildDefaultLayout().rows,
            )
        val json = config.toJson()

        assertFalse(json.has("symbolRows"))
        assertFalse(json.has("symbolShiftedRows"))
    }

    @Test
    fun `edited symbol pages survive a round trip`() {
        val edited =
            KeyboardLayouts.symbolsPrimaryRows.mapIndexed { index, row ->
                if (index == 1) row.map { it.copy(output = "Z", id = "key_z_$index") } else row
            }
        val config =
            KeyboardLayoutConfig(
                id = "custom_y",
                name = "Y",
                rows = LayoutManager.buildDefaultLayout().rows,
                symbolRows = edited,
            )

        val restored = KeyboardLayoutConfig.fromJson(config.toJson().toString())

        assertEquals(edited, restored.symbolRows)
        // The page that was not edited still defaults rather than being written out.
        assertEquals(KeyboardLayouts.symbolsShiftedRows, restored.symbolShiftedRows)
    }

    @Test
    fun `the shipped symbol pages both validate`() {
        // The pages now go through the validator, so a mistake in them would quarantine the
        // user's whole layout on load rather than showing up as a wrong key.
        assertEquals(
            LayoutValidationResult.Valid,
            LayoutValidator.validate(LayoutManager.buildDefaultLayout()),
        )
    }

    @Test
    fun `a symbol page with no way back to the letters is rejected`() {
        // The mirror of the SYMBOLS rule on the letters page: without ABC the user is stranded
        // on a page with no alphabet and no control that reaches one.
        val stranded =
            KeyboardLayouts.symbolsPrimaryRows.map { row -> row.filterNot { it.output == "ABC" } }
        val result =
            LayoutValidator.validate(
                LayoutManager.buildDefaultLayout().copy(symbolRows = stranded),
            )

        assertTrue(result is LayoutValidationResult.Invalid)
        assertTrue(
            (result as LayoutValidationResult.Invalid).reasons.any { it.contains("ABC") },
        )
    }

    @Test
    fun `a comma on the letters page and one on a symbol page are not a duplicate id`() {
        // Pages are validated independently. Flattening them together would reject every
        // layout this project ships, since both planes carry a comma and a full stop.
        assertEquals(
            LayoutValidationResult.Valid,
            LayoutValidator.validate(LayoutManager.buildDefaultLayout()),
        )
    }

    // --- content, from the supplied reference ---------------------------------------------

    private fun outputsOn(mode: KeyboardMode): Set<String> =
        KeyboardLayouts
            .symbolRowsForMode(mode, LayoutManager.buildDefaultLayout())
            .flatten()
            .map { it.output }
            .toSet()

    @Test
    fun `the forward slash is reachable from the symbols page`() {
        // It previously existed nowhere on either symbol page and could only be produced by
        // holding the "m" key, which is why it was reported as missing outright.
        assertTrue("/" in outputsOn(KeyboardMode.SYMBOLS))
    }

    @Test
    fun `the symbols page matches the reference row for row`() {
        // Pinned against the supplied reference image rather than described loosely, so a
        // later edit that quietly drops a key fails here instead of on a device.
        val rows =
            KeyboardLayouts.symbolsPrimaryRows.map { row -> row.map { it.output } }

        assertEquals(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"), rows[0])
        // The reference draws a currency slot in position 3. It carries "$" rather than the
        // reference's "£": the other currencies are reachable by holding it, so committing the
        // page to one of them was the avoidable choice. "£" is not carried into the
        // alternates -- it is dropped outright.
        assertEquals(listOf("@", "#", "$", "&", "_", "-", "(", ")", "=", "%"), rows[1])
        assertEquals(
            listOf("SYMBOLS_SHIFT", "\"", "*", "'", ":", "/", "!", "?", "+", "DEL"),
            rows[2],
        )
        // EMOJI is a deliberate deviation from the reference, which draws no emoji key here.
        // Keeping it preserves one-tap emoji access from the symbols page. The token was
        // "STICKERS" until the two picker doors were separated; the key and its behaviour are
        // unchanged, only the name now says which tab it lands on.
        assertEquals(listOf("ABC", "EMOJI", ",", "SPACE", ".", "ENTER"), rows[3])
    }

    @Test
    fun `the currency key opens on the dollar sign it already shows`() {
        val currency =
            KeyboardLayouts.symbolsPrimaryRows.flatten().first { it.output == "$" }
        val held =
            com.uncoalesced.stickykeys.keyboardcore.ime.longPressFor(
                currency.output,
                currency.hint,
                currency.alternates,
                currency.alternatesDefaultIndex,
            ) as com.uncoalesced.stickykeys.keyboardcore.ime.LongPress.Alternates

        assertEquals(listOf("€", "¥", "$", "¢", "₹"), held.options)
        // Holding without moving must give back the character already printed on the key.
        // Committing the first cell instead would turn every accidental hold into a euro.
        assertEquals("$", held.options[held.defaultIndex])
        assertFalse("the pound sign was dropped, not moved", "£" in held.options)
    }

    @Test
    fun `strips are attached per key, never inherited from a matching output`() {
        // The number row's digit 4 and the symbols page's `$` now deliberately carry the same
        // currency list, so this can no longer be shown by their strips differing. The property
        // was never about those two lists disagreeing anyway -- it is that a strip belongs to a
        // *key*, and the symbols page's own digit row is where an output-keyed table showed
        // itself: those keys type the same characters as the number row and must stay bare.
        // That is the F1 finding, and it is the reason alternates live on KeyDefinition.
        val symbolsPageDigits =
            KeyboardLayouts.symbolsPrimaryRows
                .flatten()
                .filter { it.output.singleOrNull()?.isDigit() == true }

        assertEquals(
            "the symbols page should still carry a full digit row",
            10,
            symbolsPageDigits.size,
        )
        symbolsPageDigits.forEach { key ->
            assertNull(
                "symbols-page digit ${key.output} must not inherit the number row's strip",
                key.alternates,
            )
        }

        // And the number row's own 4 does carry one, so the assertion above is not vacuous.
        assertEquals(
            listOf("€", "¥", "$", "¢", "₹"),
            KeyboardLayouts.numberRow.first { it.output == "4" }.alternates,
        )
    }

    @Test
    fun `symbols page two is unchanged, since no reference has ever covered it`() {
        val rows = KeyboardLayouts.symbolsShiftedRows.map { row -> row.map { it.output } }

        assertEquals(listOf("~", "`", "|", "•", "√", "π", "÷", "×", "{", "}"), rows[0])
        assertEquals(listOf("£", "¢", "€", "º", "^", "_", "=", "[", "]"), rows[1])
    }
}
