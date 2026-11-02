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
 * Completions carry the case of what was typed.
 *
 * Tapping a strip entry replaces the word under the caret, so the entry's case is what
 * lands in the field. Found on the LineageOS phone (roadmap 4S.1): at the start of a field
 * "hel" was auto-capitalized to "Hel", the strip offered "help", and the tap left "help" --
 * every sentence finished from the strip lost its capital. Correction offers already ran
 * `matchCase`; prefix completions did not.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class SuggestionCaseTest {
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

    @Test
    fun `a capitalised prefix gets capitalised completions`() =
        runBlocking {
            val lower = engine.getSuggestions("hel")
            val upper = engine.getSuggestions("Hel")
            assertTrue("expected completions for \"hel\", got $lower", lower.isNotEmpty())
            assertEquals(lower.map { it.replaceFirstChar { c -> c.uppercase() } }, upper)
        }

    @Test
    fun `an all caps prefix gets all caps completions`() =
        runBlocking {
            val lower = engine.getSuggestions("hel")
            assertEquals(lower.map { it.uppercase() }, engine.getSuggestions("HEL"))
        }

    @Test
    fun `a lowercase prefix is left alone`() =
        runBlocking {
            assertTrue(engine.getSuggestions("hel").all { it == it.lowercase() })
        }

    /** The device case: a word learned in lower case, completed under a capital. */
    @Test
    fun `a learned word is capitalised under a capitalised prefix`() =
        runBlocking {
            engine.learnWord("zorbly")
            engine.learnWord("zorbly")
            assertEquals("Zorbly", engine.getSuggestions("Zorb").first())
        }

    /** A contraction keeps its own capital whatever case the bare form was typed in. */
    @Test
    fun `im still offers I'm`() =
        runBlocking {
            assertTrue(engine.getSuggestions("im").contains("I'm"))
            assertTrue(engine.getSuggestions("Im").contains("I'm"))
        }
}
