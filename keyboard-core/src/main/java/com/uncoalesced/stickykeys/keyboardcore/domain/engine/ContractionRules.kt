// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

/**
 * Contractions whose bare form is itself a dictionary word, decided one word at a time.
 *
 * The general apostrophe repair (`SpellingCorrector.restoreApostrophe`) refuses any word that
 * is already in the dictionary, and for most of these that refusal is right: "were", "well",
 * "shell", "its", "hell", "wed", "shed", "lets" and "id" are ordinary words and usually meant
 * exactly as typed. Rewriting them is real-word correction. Those stay offered in the strip
 * and are never applied.
 *
 * Four are different. "im" is not a word anyone types on purpose. "cant" and "wont" are
 * real but rare enough ("religious cant", "as was his wont") that the contraction is what
 * the user meant nearly every time. "ill" is common in both readings, so it is applied only
 * where "ill" the adjective cannot follow: at the start of a sentence, after a comma or other
 * clause break ("Okay, ill call"), or after a word that introduces a clause ("and ill").
 * "I am ill" and "she felt ill" keep their meaning.
 *
 * This amends roadmap decision 4O.15, which offered every one of these and applied none,
 * per word as roadmap 4R.3 asks.
 */
private val ALWAYS =
    mapOf(
        "im" to "I'm",
        "cant" to "can't",
        "wont" to "won't",
    )

/** Words after which "ill" starts a new clause rather than describing someone. */
private val ILL_CLAUSE_OPENERS =
    setOf(
        "and",
        "but",
        "so",
        "then",
        "think",
        "guess",
        "hope",
        "maybe",
        "yes",
        "yeah",
        "ok",
        "okay",
        "sure",
        "fine",
    )

/**
 * The contraction for [typed], or null to leave it alone.
 *
 * [previousWord] is the word before it in the sentence, null at the start of one. [typed]
 * keeps its case because auto-capitalize marks a sentence start with a capital. [startsClause]
 * is read from the text itself (`startsClause` in the ime package): the previous word is not
 * reset by a comma or a full stop, so it cannot say where a clause begins.
 */
internal fun contractionFor(
    typed: String,
    previousWord: String?,
    startsClause: Boolean = false,
): String? {
    val normalized = typed.lowercase()
    ALWAYS[normalized]?.let { return it }
    if (normalized != "ill") return null
    val opensClause = previousWord == null || startsClause || typed.first().isUpperCase()
    return if (opensClause || previousWord?.lowercase() in ILL_CLAUSE_OPENERS) "I'll" else null
}
