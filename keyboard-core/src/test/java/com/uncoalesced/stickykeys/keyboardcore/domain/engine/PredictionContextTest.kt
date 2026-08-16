// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardDatabase
import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.PersonalDictionaryDao
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Sentence context re-ranks; it never invents.
 *
 * The tables here are written by the test rather than loaded from bigram_lm.bin, so a
 * regeneration of that asset can never turn a ranking rule into a red test -- and so the
 * premise of each case is visible in the case itself.
 *
 * The regression half matters as much as the feature half: with no previous word every result
 * must be exactly what it was before this file existed, because that is the path every caller
 * that has not been taught about context still takes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class PredictionContextTest {
    private lateinit var database: KeyboardDatabase
    private lateinit var dao: PersonalDictionaryDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, KeyboardDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.personalDictionaryDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun engineWith(table: Map<String, List<Pair<String, Int>>>): PredictionEngine {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val engine = PredictionEngine(context, dao, FakeLanguageModel(table))
        runBlocking { engine.initialize() }
        return engine
    }

    @Test
    fun `context promotes a follower that plain frequency ranks lower`() =
        runBlocking {
            // Pick the target off the *uncontextualised* result so the test states a real
            // reordering rather than asserting whatever happened to be first already.
            val plain = engineWith(emptyMap()).getSuggestions("th")
            assertTrue("dictionary must be loaded", plain.size >= 2)
            val underdog = plain.last()
            assertTrue("underdog must not already lead", plain.first() != underdog)

            val contextual =
                engineWith(mapOf("how" to listOf(underdog to 255)))
                    .getSuggestions("th", previousWord = "how")

            assertEquals(
                "a strongly-following candidate should lead once context is known",
                underdog,
                contextual.first(),
            )
        }

    @Test
    fun `context never adds a candidate that does not match the prefix`() =
        runBlocking {
            // "you" follows "how" with maximum weight and is not a completion of "th".
            val engine = engineWith(mapOf("how" to listOf("you" to 255, "the" to 255)))
            val suggestions = engine.getSuggestions("th", previousWord = "how")

            assertTrue("suggestions must not be empty", suggestions.isNotEmpty())
            assertTrue(
                "context is a re-ranking signal, not a candidate source: $suggestions",
                suggestions.none { !it.startsWith("th") },
            )
        }

    @Test
    fun `no previous word leaves suggestions byte-identical`() =
        runBlocking {
            val loaded = engineWith(mapOf("how" to listOf("this" to 255, "that" to 200)))
            val bare = engineWith(emptyMap())

            for (prefix in listOf("th", "he", "wo", "st", "co")) {
                assertEquals(
                    "prefix '$prefix' changed with no previous word supplied",
                    bare.getSuggestions(prefix),
                    loaded.getSuggestions(prefix),
                )
            }
        }

    @Test
    fun `an unknown previous word leaves suggestions byte-identical`() =
        runBlocking {
            val engine = engineWith(mapOf("how" to listOf("this" to 255)))
            for (prefix in listOf("th", "he", "wo")) {
                assertEquals(
                    "prefix '$prefix' changed on a previous word with no record",
                    engine.getSuggestions(prefix),
                    engine.getSuggestions(prefix, previousWord = "qqqqqzz"),
                )
            }
        }

    @Test
    fun `context decides which correction wins, not whether one fires`() =
        runBlocking {
            // "thw" corrects to "the" on frequency alone. Give a competing real correction
            // maximum contextual weight and it should take over.
            val baseline = engineWith(emptyMap()).getAutoCorrection("thw")
            assertEquals("the", baseline)

            val contextual =
                engineWith(mapOf("saw" to listOf("two" to 255)))
                    .getAutoCorrection("thw", previousWord = "saw")

            assertEquals(
                "a contextually-favoured candidate should win among corrections",
                "two",
                contextual,
            )
        }

    @Test
    fun `context cannot correct a word that is already valid`() =
        runBlocking {
            // The never-correct-a-valid-word rule is a safety rule, and context is not
            // allowed to be the thing that finally breaks it. "that" is a dictionary word;
            // no weight on any competitor may turn it into a correction.
            val engine =
                engineWith(
                    mapOf("saw" to listOf("this" to 255, "than" to 255, "the" to 255)),
                )
            assertEquals(null, engine.getAutoCorrection("that", previousWord = "saw"))
            assertEquals(null, engine.getAutoCorrection("hello", previousWord = "saw"))
        }

    @Test
    fun `no previous word leaves corrections byte-identical`() =
        runBlocking {
            val loaded = engineWith(mapOf("how" to listOf("two" to 255, "then" to 255)))
            val bare = engineWith(emptyMap())

            for (typo in listOf("thw", "teh", "hte", "adn", "recieve", "wrod")) {
                assertEquals(
                    "'$typo' corrected differently with no previous word supplied",
                    bare.getAutoCorrection(typo),
                    loaded.getAutoCorrection(typo),
                )
            }
        }
}
