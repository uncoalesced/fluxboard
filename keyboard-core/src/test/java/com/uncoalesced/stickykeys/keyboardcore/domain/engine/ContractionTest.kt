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
}
