// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class SymbolsPageMemoryTest {
    @Test
    fun `opens on the first symbols page until the user has chosen another`() {
        assertEquals(KeyboardMode.SYMBOLS, SymbolsPageMemory().page)
    }

    @Test
    fun `remembers the second plane across a reset to letters`() {
        val memory = SymbolsPageMemory()
        memory.remember(KeyboardMode.SYMBOLS_SHIFTED)
        memory.remember(KeyboardMode.LETTERS_LOWER)
        memory.remember(KeyboardMode.LETTERS_UPPER)
        assertEquals(KeyboardMode.SYMBOLS_SHIFTED, memory.page)
    }

    @Test
    fun `going back to the first page replaces the remembered one`() {
        val memory = SymbolsPageMemory()
        memory.remember(KeyboardMode.SYMBOLS_SHIFTED)
        memory.remember(KeyboardMode.SYMBOLS)
        assertEquals(KeyboardMode.SYMBOLS, memory.page)
    }

    @Test
    fun `non symbol modes never overwrite the choice`() {
        val memory = SymbolsPageMemory()
        memory.remember(KeyboardMode.SYMBOLS_SHIFTED)
        memory.remember(KeyboardMode.PIN)
        memory.remember(KeyboardMode.LETTERS_CAPS_LOCK)
        assertEquals(KeyboardMode.SYMBOLS_SHIFTED, memory.page)
    }
}
