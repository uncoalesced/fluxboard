// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardDatabase
import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.PersonalDictionaryDao
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
 * Tests for Bloom Engine (FluxBloom Core) Multi-Lexicon Federation and Bloom Revert Memory.
 *
 * Validates:
 * 1. Inviolable Lexicon Veto for diverse vocabularies (Western names, Indian surnames/names,
 *    cities, cultural terms, and Hinglish conversational words).
 * 2. Autocorrect candidate generation and prefix completions across federated lexicons.
 * 3. Bloom Revert Memory (0ms synchronous suppression of backspaced autocorrects).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class BloomEngineMultiLexiconTest {
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
        runBlocking {
            engine.initialize()
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `inviolable lexicon veto protects indian surnames from autocorrect`() =
        runBlocking {
            // Surnames from names_dict.bin must never be corrected to English words
            assertNull(engine.getAutoCorrection("Sharma"))
            assertNull(engine.getAutoCorrection("sharma"))
            assertNull(engine.getAutoCorrection("Patel"))
            assertNull(engine.getAutoCorrection("patel"))
            assertNull(engine.getAutoCorrection("Iyer"))
            assertNull(engine.getAutoCorrection("iyer"))
            assertNull(engine.getAutoCorrection("Reddy"))
            assertNull(engine.getAutoCorrection("reddy"))
            assertNull(engine.getAutoCorrection("Mukherjee"))
            assertNull(engine.getAutoCorrection("mukherjee"))
        }

    @Test
    fun `inviolable lexicon veto protects indian first names and project authors`() =
        runBlocking {
            assertNull(engine.getAutoCorrection("Rahul"))
            assertNull(engine.getAutoCorrection("rahul"))
            assertNull(engine.getAutoCorrection("Joel"))
            assertNull(engine.getAutoCorrection("joel"))
            assertNull(engine.getAutoCorrection("Anthony"))
            assertNull(engine.getAutoCorrection("anthony"))
            assertNull(engine.getAutoCorrection("Priya"))
            assertNull(engine.getAutoCorrection("priya"))
            assertNull(engine.getAutoCorrection("Sneha"))
            assertNull(engine.getAutoCorrection("sneha"))
            assertNull(engine.getAutoCorrection("Amit"))
            assertNull(engine.getAutoCorrection("amit"))
        }

    @Test
    fun `inviolable lexicon veto protects indian food and cultural vocabulary`() =
        runBlocking {
            assertNull(engine.getAutoCorrection("biryani"))
            assertNull(engine.getAutoCorrection("dosa"))
            assertNull(engine.getAutoCorrection("samosa"))
            assertNull(engine.getAutoCorrection("masala"))
            assertNull(engine.getAutoCorrection("paneer"))
            assertNull(engine.getAutoCorrection("roti"))
            assertNull(engine.getAutoCorrection("chai"))
        }

    @Test
    fun `inviolable lexicon veto protects hinglish conversational words`() =
        runBlocking {
            assertNull(engine.getAutoCorrection("kaise"))
            assertNull(engine.getAutoCorrection("kyun"))
            assertNull(engine.getAutoCorrection("theek"))
            assertNull(engine.getAutoCorrection("bhai"))
            assertNull(engine.getAutoCorrection("yaar"))
            assertNull(engine.getAutoCorrection("accha"))
        }

    @Test
    fun `bloom revert memory suppresses immediate re-correction on space`() =
        runBlocking {
            // "teh" normally corrects to "the"
            val initialCorrection = engine.getAutoCorrection("teh")
            assertEquals("the", initialCorrection)

            // User hits backspace, which records a revert in BloomRevertHistory
            engine.recordRevert("teh")
            assertTrue(engine.isReverted("teh"))

            // User hits space again immediately: autocorrect must be suppressed!
            val suppressed = engine.getAutoCorrection("teh")
            assertNull("Bloom Revert Memory must suppress re-correction on space", suppressed)

            // When cleared (e.g. switching fields), normal autocorrect resumes
            engine.clearReverts()
            assertEquals("the", engine.getAutoCorrection("teh"))
        }

    @Test
    fun `names dictionary feeds prefix suggestions`() =
        runBlocking {
            val namesCandidates = engine.getNamesSuggestions("shar", 5)
            val hasSharmaInNames =
                namesCandidates.any {
                    it.word.equals("sharma", ignoreCase = true)
                }
            assertTrue("Expected 'sharma' in names suggestions for 'shar'", hasSharmaInNames)

            val suggestions = engine.getSuggestions("sharm")
            val hasSharma =
                suggestions.any {
                    it.equals("sharma", ignoreCase = true)
                }
            assertTrue("Expected 'sharma' in suggestions for 'sharm'", hasSharma)

            val foodSuggestions = engine.getSuggestions("biry")
            val hasBiryani =
                foodSuggestions.any {
                    it.equals("biryani", ignoreCase = true)
                }
            assertTrue("Expected 'biryani' in suggestions for 'biry'", hasBiryani)
        }
}
