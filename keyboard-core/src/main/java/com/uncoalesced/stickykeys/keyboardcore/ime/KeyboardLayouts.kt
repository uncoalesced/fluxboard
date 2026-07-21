// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

object KeyboardLayouts {

    val qwertyLettersLower = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("SHIFT", "z", "x", "c", "v", "b", "n", "m", "DEL"),
        listOf("SYMBOLS", "STICKERS", "SPACE", ".", "ENTER")
    )

    val qwertyLettersUpper = listOf(
        listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
        listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
        listOf("SHIFT", "Z", "X", "C", "V", "B", "N", "M", "DEL"),
        listOf("SYMBOLS", "STICKERS", "SPACE", ".", "ENTER")
    )

    val symbolsPrimary = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("@", "#", "$", "%", "&", "-", "+", "(", ")"),
        listOf("SYMBOLS_SHIFT", "*", "\"", "'", ":", ";", "!", "?", "DEL"),
        listOf("ABC", "STICKERS", "SPACE", ",", "ENTER")
    )

    val symbolsShifted = listOf(
        listOf("~", "`", "|", "•", "√", "π", "÷", "×", "{", "}"),
        listOf("\t", "£", "¢", "€", "º", "^", "_", "=", "[", "]"),
        listOf("SYMBOLS_SHIFT", "™", "®", "©", "¶", "\\", "<", ">", "DEL"),
        listOf("ABC", "STICKERS", "SPACE", ",", "ENTER")
    )

    fun getLayoutForMode(mode: KeyboardMode): List<List<String>> {
        return when (mode) {
            KeyboardMode.LETTERS_LOWER -> qwertyLettersLower
            KeyboardMode.LETTERS_UPPER, KeyboardMode.LETTERS_CAPS_LOCK -> qwertyLettersUpper
            KeyboardMode.SYMBOLS -> symbolsPrimary
            KeyboardMode.SYMBOLS_SHIFTED -> symbolsShifted
        }
    }
}
