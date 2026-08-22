// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.emoji

/**
 * Text emoticons, which are a different thing from emoji and belong on their own tab.
 *
 * An emoji is one glyph the system font draws, catalogued by Unicode and filtered by
 * [EmojiRepository] against what the device can actually render. An emoticon is ordinary text
 * that happens to look like a face -- it needs no font support, no categorisation and no
 * capability check, because every character in it is one the keyboard could already type.
 * Mixing them into the emoji groups would mean a category the search index cannot describe and
 * a `hasGlyph` filter that is meaningless for it.
 *
 * Hard-coded rather than generated: there is no upstream registry of emoticons the way
 * `emoji-test.txt` is the registry for emoji, so a generator would only be reading a list
 * somebody wrote by hand anyway.
 *
 * Every character here is deliberately outside the ranges `scripts/check-source-rules.sh`
 * bans, which is why there are no hearts, stars or flowers in the kaomoji -- those live in the
 * Miscellaneous Symbols and Dingbats blocks and are emoji by Unicode's own property, so the
 * gate would reject this file. Box-drawing, geometric shapes, kana and Latin punctuation are
 * all fine and are what the shapes below are built from.
 */
val EMOTICONS: List<String> =
    listOf(
        // The plain ASCII set first: these are what most people mean by "emoticon", and they
        // are the ones that survive being pasted anywhere at all.
        ":)",
        ":(",
        ";)",
        ":D",
        ":P",
        ":O",
        ":|",
        ":/",
        ":'(",
        ":*",
        "XD",
        "<3",
        "</3",
        ">:(",
        "^_^",
        ">_<",
        "T_T",
        "o_O",
        "-_-",
        "._.",
        "=)",
        ":3",
        "\\o/",
        "(y)",
        // Kaomoji, which are the reason this tab is worth having: they are long enough that
        // typing one by hand is genuinely tedious.
        "\u00AF\\_(\u30C4)_/\u00AF",
        "(\u0CA0_\u0CA0)",
        "(\u3065\uFF61\u25D5\u203F\u25D5\uFF61)\u3065",
        "( \u035C\u00B0 \u035C\u0296 \u035C\u00B0)",
        "(\u2310\u25A0_\u25A0)",
        "\u30FD(\u30C4)\u30CE",
        "(\u256F\u00B0\u25A1\u00B0)\u256F\uFE35 \u253B\u2501\u253B",
        "\u252C\u2500\u252C\u30CE( \u00BA _ \u00BA\u30CE)",
        "(\u30CE\u0CA0\u76CA\u0CA0)\u30CE\u5F61\u253B\u2501\u253B",
        "(\u00AC_\u00AC)",
        "(\u2022_\u2022)",
        "(\u0298\u203F\u0298)",
        "\u1555( \u141B )\u1557",
        "(\u3063\u02D8\u03C9\u02D8\u3055)",
        "(\u0CA5_\u0CA5)",
        "\u10DA(\u0CA0\u76CA\u0CA0\u10DA)",
        "(\u00B0\u25CB\u00B0)",
        "(\uFF61\u2022\u0301\u203F\u2022\u0300\uFF61)",
        "\\(^o^)/",
        "(~_~)",
    )
