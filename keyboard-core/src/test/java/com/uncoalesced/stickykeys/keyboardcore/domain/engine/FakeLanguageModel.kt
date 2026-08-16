// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

/**
 * A [LanguageModel] whose table is written by the test rather than loaded from an asset.
 *
 * Two jobs. Constructed empty it is the no-context baseline every pre-existing engine test
 * runs against, so those tests keep asserting exactly what they asserted before sentence
 * context existed. Constructed with a table it lets a context test state its premise in two
 * lines instead of depending on what Norvig's corpus happens to say about a real word pair --
 * which would make a data regeneration look like a logic regression.
 *
 * The stored weight is returned as the boost directly, with no scale factor. The production
 * scale is a tuning constant; a test that multiplied by it would be asserting the constant
 * rather than the ranking.
 */
internal class FakeLanguageModel(
    private val table: Map<String, List<Pair<String, Int>>> = emptyMap(),
) : LanguageModel {
    override fun boostFor(
        previousWord: String?,
        candidate: String,
    ): Float {
        if (previousWord == null) return 0f
        val followers = table[previousWord] ?: return 0f
        return followers.firstOrNull { it.first == candidate }?.second?.toFloat() ?: 0f
    }
}
