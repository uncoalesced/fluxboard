// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.layout

import androidx.test.core.app.ApplicationProvider
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import com.uncoalesced.stickykeys.keyboardcore.ime.KeyboardLayouts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * A symbol-page edit surviving the trip through the manager, which is where issue #30 lives.
 *
 * [SymbolPageLayoutTest] already pins the JSON round-trip, and it passes -- so the format was
 * never the problem. What the format tests cannot see is everything between the Save button and
 * the keyboard reading the layout back: which id the edit is written under, whether the file is
 * still there on the next load, and which of several entries with a claim to that id wins.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SymbolPagePersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    /**
     * A manager whose initial load has actually finished.
     *
     * `LayoutManager` loads on its own IO scope from `init`, so reading `availableLayouts`
     * straight after constructing one reads an empty list rather than the disk.
     */
    private suspend fun freshManager(): LayoutManager =
        LayoutManager(context, KeyboardPreferences(context)).also { manager ->
            withTimeout(LOAD_TIMEOUT_MS) {
                manager.availableLayouts.first { it.isNotEmpty() }
            }
        }

    /** The default layout with one symbol on page 1 replaced, as the editor would leave it. */
    private fun editedDefault(id: String): KeyboardLayoutConfig {
        val base = LayoutManager.buildDefaultLayout()
        val editedRows =
            base.symbolRows.mapIndexed { rowIndex, row ->
                if (rowIndex != 0) {
                    row
                } else {
                    row.mapIndexed { keyIndex, key ->
                        if (keyIndex == 0) key.copy(output = EDIT_MARKER) else key
                    }
                }
            }
        return base.copy(id = id, name = "Custom QWERTY", symbolRows = editedRows)
    }

    private fun firstSymbolOutput(config: KeyboardLayoutConfig): String =
        config.symbolRows
            .first()
            .first()
            .output

    @Test
    fun `a symbol page edit is still there after the manager reloads it`() =
        runBlocking {
            File(context.filesDir, "layouts").deleteRecursively()
            val edited = editedDefault("custom_symboledit")
            assertNotEquals(
                "the fixture must actually differ from the shipped page",
                firstSymbolOutput(LayoutManager.buildDefaultLayout()),
                firstSymbolOutput(edited),
            )

            val saved = freshManager().saveCustomLayout(edited)
            assertEquals(LayoutValidationResult.Valid, saved)

            // A second manager stands in for the next launch: it reads the same directory with
            // none of the first one's state.
            val reloaded = freshManager()
            val onDisk = reloaded.availableLayouts.value.find { it.id == "custom_symboledit" }
            assertEquals(
                "the edited symbol page must survive a reload",
                EDIT_MARKER,
                onDisk?.let { firstSymbolOutput(it) },
            )
        }

    @Test
    fun `an edit saved under a preset id is not shadowed by the shipped copy of that preset`() =
        runBlocking {
            // The failure this test exists for: loadLayouts adds the built-in first and every
            // custom file afterwards with no check for a clash, and resolveLayout takes the
            // first entry matching the id. A layout written under a shipped id would then be
            // read back as the shipped one, so the file on disk would hold the user's symbol
            // edits and the keyboard would show none of them -- with nothing anywhere saying
            // the save had failed, because it had not.
            File(context.filesDir, "layouts").deleteRecursively()
            File(context.filesDir, "layouts").mkdirs()
            File(context.filesDir, "layouts/${LayoutManager.DEFAULT_LAYOUT_ID}.json")
                .writeText(editedDefault(LayoutManager.DEFAULT_LAYOUT_ID).toJson().toString(2))

            val manager = freshManager()
            val resolved =
                resolveLayout(
                    manager.availableLayouts.value,
                    LayoutManager.DEFAULT_LAYOUT_ID,
                )
            assertEquals(
                "the user's saved copy must win over the shipped one it replaces",
                EDIT_MARKER,
                resolved?.layout?.let { firstSymbolOutput(it) },
            )
        }

    @Test
    fun `the shipped symbol pages are what an untouched layout reports`() {
        // Guards the other direction: nothing above should make a clean install start out
        // reporting something other than the pages it ships with.
        assertEquals(
            KeyboardLayouts.symbolsPrimaryRows,
            LayoutManager.buildDefaultLayout().symbolRows,
        )
        assertEquals(
            KeyboardLayouts.symbolsShiftedRows,
            LayoutManager.buildDefaultLayout().symbolShiftedRows,
        )
    }

    companion object {
        /** An output no shipped page uses, so finding it proves the edit is the one read back. */
        private const val EDIT_MARKER = "\u00A7"

        /** Generous: this waits on a disk read, it is not measuring one. */
        private const val LOAD_TIMEOUT_MS = 5_000L
    }
}
