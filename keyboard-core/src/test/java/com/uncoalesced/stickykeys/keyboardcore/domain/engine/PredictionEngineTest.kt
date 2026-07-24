// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardDatabase
import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.PersonalDictionaryDao
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class PredictionEngineTest {

    private lateinit var database: KeyboardDatabase
    private lateinit var dao: PersonalDictionaryDao
    private lateinit var engine: PredictionEngine

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            KeyboardDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.personalDictionaryDao()
        engine = PredictionEngine(context, dao)
        
        runBlocking {
            engine.initialize()
        }
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testDictionaryLoadedAndReturnsSuggestions() = runBlocking {
        // "th" should return common words like "the", "that", "this"
        val suggestions = engine.getSuggestions("th")
        assertTrue("Should return suggestions for 'th'", suggestions.isNotEmpty())
        assertTrue("Suggestions should contain 'the'", suggestions.contains("the"))
    }

    @Test
    fun testPersonalDictionaryLearning() = runBlocking {
        // Two-strike rule: a word must be used at least twice before it is
        // trusted as a personal word, so one-off typos are never suggested.
        val uniqueWord = "stickykeysapp"
        engine.learnWord(uniqueWord)

        val afterOneUse = engine.getSuggestions("stickykeys")
        assertTrue("A word used only once should not be suggested yet", !afterOneUse.contains(uniqueWord))

        engine.learnWord(uniqueWord)

        // Ensure it appears in suggestions by querying a prefix unique enough that base dict won't drown it out
        val suggestions = engine.getSuggestions("stickykeys")

        assertTrue("Learned word should be in suggestions", suggestions.contains(uniqueWord))

        // Ensure it is ranked highly
        assertEquals("Learned word should be top suggestion", uniqueWord, suggestions.first())
    }

    @Test
    fun testAutoCorrection() = runBlocking {
        // "thw" is a common typo for "the". "the" is a very high frequency base dictionary word.
        val correction = engine.getAutoCorrection("thw")
        assertNotNull("Should find a correction for 'thw'", correction)
        assertEquals("Correction should be 'the'", "the", correction)

        // Valid word should return null (no correction needed)
        val validWord = engine.getAutoCorrection("that")
        assertNull("Valid base dictionary word should not be corrected", validWord)

        // Learn a personal word (twice, per the two-strike rule) and make sure
        // it doesn't get corrected
        engine.learnWord("stickykeysapp")
        engine.learnWord("stickykeysapp")
        val personalWord = engine.getAutoCorrection("stickykeysapp")
        assertNull("Valid personal dictionary word should not be corrected", personalWord)

        // Note: typos of personal-dictionary words are not corrected by design --
        // getAutoCorrection searches the base trie only.
    }
}
