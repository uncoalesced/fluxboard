// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardDatabase
import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.PersonalDictionaryDao
import com.uncoalesced.stickykeys.keyboardcore.data.local.entity.PersonalWordEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The 2026-10-26 Gboard comparison (roadmap 4R.3), one test per row.
 *
 * The same words were typed into both keyboards on one phone. FluxBoard's misses fall into four
 * groups, and each group below is one: corrections that change the meaning of the sentence, new
 * words destroyed before they can be learned, contractions whose bare form is itself a word, and
 * a standalone lower-case `i`. The "keep" rows are what FluxBoard already did better than Gboard;
 * they are here so that holding back does not quietly cost them.
 *
 * Runs against the shipped `base_dict.bin`, because every row is a claim about the real
 * dictionary. Rows typed "in a sentence" use the shipped bigram table as well, since that is the
 * thing that turned `thier` into `the`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class AutocorrectParityTest {
    private lateinit var database: KeyboardDatabase
    private lateinit var dao: PersonalDictionaryDao
    private lateinit var engine: PredictionEngine
    private lateinit var contextEngine: PredictionEngine

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
        contextEngine = PredictionEngine(context, dao, BigramLanguageModel(context))
        runBlocking {
            engine.initialize()
            contextEngine.initialize()
        }
    }

    @After
    fun teardown() {
        database.close()
    }

    // --- Restraint: a wrong correction costs a rewrite, a missed one costs a typo ------------

    @Test
    fun `alot is left alone rather than becoming slot`() =
        runBlocking {
            assertNull(engine.getAutoCorrection("alot"))
        }

    @Test
    fun `alot in a sentence is left alone rather than becoming not`() =
        runBlocking {
            assertNull(contextEngine.getAutoCorrection("alot", previousWord = "thanks"))
        }

    @Test
    fun `helo is left alone because help and hello are equally plausible`() =
        runBlocking {
            assertNull(engine.getAutoCorrection("helo"))
        }

    @Test
    fun `thier in a sentence becomes their, not the`() =
        runBlocking {
            assertEquals("their", contextEngine.getAutoCorrection("thier", previousWord = "at"))
        }

    // --- New words: leave them alone so they can be learned ----------------------------------

    @Test
    fun `an unknown word with no close candidate is left alone`() =
        runBlocking {
            assertNull(engine.getAutoCorrection("zorbly"))
        }

    @Test
    fun `a near miss of a learned word corrects to the learned word`() =
        runBlocking {
            dao.insertOrUpdate(PersonalWordEntity("zorbly", 2, System.currentTimeMillis()))
            assertEquals("zorbly", engine.getAutoCorrection("zorbli"))
        }

    // --- Contractions whose bare form is a word ----------------------------------------------

    @Test
    fun `im becomes I'm`() =
        runBlocking {
            assertEquals("I'm", engine.getAutoCorrection("im"))
        }

    @Test
    fun `cant becomes can't`() =
        runBlocking {
            assertEquals("can't", engine.getAutoCorrection("cant"))
        }

    @Test
    fun `wont becomes won't`() =
        runBlocking {
            assertEquals("won't", engine.getAutoCorrection("wont"))
        }

    @Test
    fun `ill at the start of a sentence becomes I'll`() =
        runBlocking {
            assertEquals("I'll", engine.getAutoCorrection("ill"))
        }

    @Test
    fun `ill after a linking verb stays ill`() =
        runBlocking {
            assertNull(engine.getAutoCorrection("ill", previousWord = "am"))
            assertNull(engine.getAutoCorrection("ill", previousWord = "feel"))
        }

    @Test
    fun `contraction homographs that are usually meant as typed are left alone`() =
        runBlocking {
            for (word in listOf("well", "were", "shell", "its", "hell")) {
                assertNull("$word must not be rewritten", engine.getAutoCorrection(word))
            }
        }

    @Test
    fun `capitalization of a contraction follows the pronoun, not the typed case`() =
        runBlocking {
            assertEquals("Can't", engine.getAutoCorrection("Cant"))
            assertEquals("I'm", engine.getAutoCorrection("Im"))
        }

    // --- Standalone i -------------------------------------------------------------------------

    @Test
    fun `a standalone i becomes I`() =
        runBlocking {
            assertEquals("I", engine.getAutoCorrection("i"))
            assertEquals("I", engine.getAutoCorrection("i", previousWord = "and"))
        }

    @Test
    fun `other single letters are still never corrected`() =
        runBlocking {
            for (word in listOf("a", "u", "o")) {
                assertNull(engine.getAutoCorrection(word))
            }
        }

    // --- Offered rather than applied, and kept once undone ----------------------------------

    @Test
    fun `a declined correction is offered instead`() =
        runBlocking {
            val helo = engine.getCorrectionOffers("helo")
            assertTrue(
                "expected help and hello, got $helo",
                helo.containsAll(listOf("help", "hello")),
            )
            assertEquals("a lot", engine.getCorrectionOffers("alot").first())
        }

    @Test
    fun `nothing is offered for a real word, and nothing far away for a new one`() =
        runBlocking {
            assertTrue(engine.getCorrectionOffers("the").isEmpty())
            // "sorely" is close enough to offer and that is harmless: an offer is never applied.
            // "only" is the distant word autocorrect used to apply, and must not appear even here.
            val zorbly = engine.getCorrectionOffers("zorbly")
            assertTrue("expected no distant offers for zorbly, got $zorbly", "only" !in zorbly)
        }

    @Test
    fun `a kept word is not corrected again`() =
        runBlocking {
            assertEquals("The", engine.getAutoCorrection("Teh"))
            engine.keepWord("teh")
            assertNull(engine.getAutoCorrection("teh"))
        }

    // --- Keep: what FluxBoard already did better ----------------------------------------------

    @Test
    fun `the corrections Gboard missed still fire`() =
        runBlocking {
            mapOf(
                "teh" to "the",
                "thw" to "the",
                "dont" to "don't",
                "itll" to "it'll",
                "recieve" to "receive",
                "hellonworld" to "hello world",
                "vall" to "call",
            ).forEach { (typed, expected) ->
                assertEquals("'$typed'", expected, engine.getAutoCorrection(typed))
            }
        }

    @Test
    fun `thier alone still becomes their`() =
        runBlocking {
            assertEquals("their", engine.getAutoCorrection("thier"))
        }
}
