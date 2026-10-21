// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import java.nio.ByteBuffer

// The on-disk dictionary format, "Flictionary": the ASCII magic "FLCT", then a trie. Each node
// is a frequency byte, a terminal flag byte, a child count byte, and per child a two-byte
// character and a four-byte offset to the child node. Built offline by
// dictionary-tools/build_flictionary.py.
//
// Readers never share a cursor: every caller passes its own duplicate() of the mapped buffer.
// See PredictionEngine.reader for why.

/** The trie root sits immediately after the 4-byte "FLCT" magic. */
internal const val TRIE_ROOT_OFFSET = 4

/**
 * Frequency of an exact dictionary word in [buf], or null when it is not a terminal node or
 * the walk reads a malformed offset.
 */
internal fun trieFrequencyOf(
    buf: ByteBuffer,
    word: String,
): Int? {
    return try {
        var offset = 4 // after the FLCT magic
        for (char in word) {
            buf.position(offset)
            buf.get() // frequency
            buf.get() // terminal flag
            val childCount = buf.get().toInt() and 0xFF
            var next = -1
            for (i in 0 until childCount) {
                val c1 = buf.get().toInt() and 0xFF
                val c2 = buf.get().toInt() and 0xFF
                val childChar = ((c1 shl 8) or c2).toChar()
                val childOffset = buf.getInt()
                if (childChar == char) {
                    next = childOffset
                    break
                }
            }
            if (next < 0) return null
            offset = next
        }
        buf.position(offset)
        val freq = buf.get().toInt() and 0xFF
        val isTerminal = buf.get().toInt() and 0xFF
        if (isTerminal == 1) freq else null
    } catch (e: Exception) {
        null
    }
}
