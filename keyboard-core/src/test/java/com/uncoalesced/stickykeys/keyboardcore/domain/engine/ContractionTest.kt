// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardDatabase
import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.PersonalDictionaryDao
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contractions have to be in the dictionary, and this pins that they are.
 *
 * They were not, for every release up to v0.1.5.1, and nothing caught it. The corpus that
 * builds `base_dict.bin` carries no punctuation at all, so "you're" was never in it -- and
 * the stripped form the corpus *did* count, "youre", was then dropped by the reference
 * intersection because "youre" is not a word either. Both halves of the pipeline behaved
 * exactly as designed and the result was that not one contraction reached the dictionary:
 * "don't", "it's", "can't", "i'm" were all absent. That is roughly one word in twenty of
 * running English typed with no suggestion behind it, and an open invitation for
 * autocorrect to reach for something else.
 *
 * The word-boundary code was never the problem -- `wordUnderCaret` already keeps an
 * apostrophe inside a word, and `getSuggestions`/`getAutoCorrection` only lowercase and
 * trim. So these assertions are about the shipped asset, which is why they run against the
 * real one rather than a fixture.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class ContractionTest {
    private lateinit var database: KeyboardDatabase
    private lateinit var dao: PersonalDictionaryDao
    private lateinit var engine: PredictionEngine

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, KeyboardDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.personalDictionaryDao()
        engine = PredictionEngine(context, dao, FakeLanguageModel())
        runBlocking { engine.initialize() }
    }

    @After
    fun teardown() {
        database.close()
    }

    /** The regression itself: a correctly typed contraction must not be corrected away. */
    @Test
    fun `a typed contraction is left alone`() =
        runBlocking {
            for (word in listOf("you're", "don't", "it's", "i'm", "that's", "can't")) {
                assertNull(
                    "$word is a real word and must never be corrected",
                    engine.getAutoCorrection(word),
                )
            }
        }

    /** Typing up to the apostrophe must still offer the contraction. */
    @Test
    fun `a prefix through the apostrophe suggests the contraction`() =
        runBlocking {
            val suggestions = engine.getSuggestions("you'")
            assertTrue(
                "expected you're among suggestions for \"you'\", got $suggestions",
                suggestions.contains("you're"),
            )
        }

    /**
     * The apostrophe-less spelling is what a hurried thumb produces, and it is the case the
     * corpus counted, so it has to resolve to the contraction rather than to a near-miss of
     * some unrelated word.
     */
    @Test
    fun `the apostrophe-less spelling corrects to the contraction`() =
        runBlocking {
            assertEquals("you're", engine.getAutoCorrection("youre"))
            assertEquals("don't", engine.getAutoCorrection("dont"))
        }

    /**
     * Words whose stripped form is a word in its own right keep both entries.
     *
     * "its" and "it's" are both correct and mean different things, so neither may be
     * corrected into the other -- this is the same never-correct-a-valid-word rule the
     * engine applies everywhere, checked here because the contraction work is exactly what
     * could have broken it.
     */
    @Test
    fun `a contraction and its homograph both survive`() =
        runBlocking {
            for (word in listOf("its", "it's", "were", "we're", "cant", "can't")) {
                assertNull("$word must stand on its own", engine.getAutoCorrection(word))
            }
        }

    /** Ordinary words are unaffected: this must add entries, never move existing ones. */
    @Test
    fun `plain words are unchanged`() =
        runBlocking {
            assertNotNull("teh should still correct", engine.getAutoCorrection("teh"))
            assertNull("the should still be left alone", engine.getAutoCorrection("the"))
            assertTrue(engine.getSuggestions("th").contains("the"))
        }

    /**
     * A contraction whose bare form is an ordinary word is offered, never substituted.
     *
     * "i'll" is in the dictionary but is not a completion of "ill" -- the trie branches at
     * the apostrophe, so the prefix walk can never reach it however far it searches. These
     * were the contractions with no route to the user at all: the autocorrect path refuses
     * them on purpose, because rewriting them would break "I am ill" and "there were
     * three", and the suggestion path simply could not see them.
     */
    @Test
    fun `an ambiguous contraction reaches the suggestion strip`() =
        runBlocking {
            val expected =
                mapOf(
                    "ill" to "I'll",
                    "cant" to "can't",
                    "wont" to "won't",
                    "well" to "we'll",
                    "shell" to "she'll",
                )
            for (typed in expected.keys) {
                val offered = expected.getValue(typed)
                assertTrue(
                    "typing \"$typed\" should offer \"$offered\" in the strip",
                    engine.apostropheVariantsOf(typed).any { it.first == offered },
                )
            }
        }

    /**
     * The safety half, and the reason offering was chosen over replacing.
     *
     * Each of these is a word somebody meant to type. If any of them ever starts returning
     * a correction, "I am ill" becomes "I am I'll" and the feature has done more damage
     * than the gap it filled.
     */
    @Test
    fun `an ambiguous contraction is never auto-applied`() =
        runBlocking {
            for (word in listOf("ill", "well", "shell", "wont", "hell", "id")) {
                assertNull(
                    "$word is a real word and must never be corrected to a contraction",
                    engine.getAutoCorrection(word),
                )
            }
        }

    /** A bare form that is not a word still corrects outright, as it always has. */
    @Test
    fun `an unambiguous contraction still corrects without a tap`() =
        runBlocking {
            assertEquals("it'll", engine.getAutoCorrection("itll"))
            assertEquals("you've", engine.getAutoCorrection("youve"))
        }

    /**
     * The one pronoun that is always capitalized.
     *
     * The dictionary is lowercase throughout and case is otherwise restored from what the
     * user typed, which is right for every word except this handful: a faithfully lowercase
     * "i'll" is wrong however it was typed, and reads as the feature being broken.
     */
    @Test
    fun `first person contractions are capitalized`() =
        runBlocking {
            assertEquals("I've", engine.getAutoCorrection("ive"))
            assertTrue(engine.apostropheVariantsOf("ill").any { it.first == "I'll" })
        }

    /**
     * A contraction keeps a slot in the strip even when three completions outrank it.
     *
     * Found on a device, not in review. "image", "important" and "images" are each commoner
     * than any single contraction, so for "im" the strip filled with completions of a word
     * the user had not finished and "I'm" was computed, scored and then cut. Ranking alone
     * cannot fix that without over-weighting contractions everywhere else, so exactly one is
     * promoted, and only when none reached the strip on merit.
     */
    @Test
    fun `a contraction is not ranked out of the strip by commoner completions`() =
        runBlocking {
            val suggestions = engine.getSuggestions("im")
            assertTrue(
                "expected I'm among suggestions for \"im\", got $suggestions",
                suggestions.contains("I'm"),
            )
        }

    /** Promotion takes one slot at most and leaves the rest of the ranking alone. */
    @Test
    fun `promoting a contraction does not empty the strip of completions`() =
        runBlocking {
            val suggestions = engine.getSuggestions("im")
            assertTrue(
                "expected ordinary completions alongside it, got $suggestions",
                suggestions.any { !it.contains("'") },
            )
        }

    /**
     * "im" cannot autocorrect, and the strip is the only route to "I'm".
     *
     * Autocorrect refuses anything under three characters outright, which is a deliberate
     * and much older rule: at two letters almost every word is one edit from several
     * others, so correcting them confidently is how a keyboard rewrites what somebody
     * meant. The offer path has no such floor, because offering costs the user nothing.
     */
    @Test
    fun `two letter bare forms are offered even though they cannot be corrected`() =
        runBlocking {
            assertNull(
                "autocorrect must not touch a two-letter word",
                engine.getAutoCorrection("im"),
            )
            assertTrue(
                "typing \"im\" should still offer \"I'm\"",
                engine.apostropheVariantsOf("im").any { it.first == "I'm" },
            )
        }
}
