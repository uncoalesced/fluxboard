// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardDatabase
import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.PersonalDictionaryDao
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The candidate pool is chosen by frequency, not by word length.
 *
 * Found during the v0.1.5.1 device pass and left unfixed at the time. The completion walk
 * stopped after a fixed number of *terminals*, and because the walk is breadth-first that
 * means it stopped near the top of the subtree -- where the short words are. A common longer
 * word never entered the pool at all.
 *
 * It mattered more than it looked. Sentence context re-ranks this pool and never adds to it,
 * so a word missing here could not be promoted by context no matter how strong the signal --
 * which is why "How m" produced the same suggestions as a bare "m" on device, and why the
 * whole bigram feature looked weaker than it was.
 *
 * Asserted against the shipped dictionary on purpose: a fixture would prove the walk visits
 * what it is given, and the defect was about the real one's shape.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class SuggestionBreadthTest {
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

    /** The exact case measured on device: "many" outranks "map" and "mar" and was missing. */
    @Test
    fun `a common longer word is not crowded out by shorter ones`() =
        runBlocking {
            val pool = engine.getBaseSuggestions("ma", limit = 30).map { it.word }
            assertTrue("expected many in the pool for ma, got $pool", pool.contains("many"))
        }

    /** Same shape, different prefix, so the fix is not a special case for one word. */
    @Test
    fun `longer common words survive other prefixes too`() =
        runBlocking {
            val pool = engine.getBaseSuggestions("th", limit = 30).map { it.word }
            for (word in listOf("that", "them", "there", "think")) {
                assertTrue("expected $word in the pool for th, got $pool", pool.contains(word))
            }
        }

    /** The walk is bounded: the largest single-letter subtree must still return. */
    @Test
    fun `the widest prefix in the dictionary still completes`() =
        runBlocking {
            val pool = engine.getBaseSuggestions("s", limit = 10)
            assertTrue("a one-letter prefix must still produce candidates", pool.isNotEmpty())
        }

    /** The strip itself is unchanged in shape: still the same number of suggestions. */
    @Test
    fun `the visible strip is still short`() =
        runBlocking {
            assertTrue(engine.getSuggestions("th").size <= 3)
        }
}
