// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardDatabase
import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.PersonalDictionaryDao
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Autocorrect measured across a realistic spread of typos rather than four hand-picked ones.
 *
 * The existing [PredictionEngineTest] asserts a handful of very high frequency targets ("the",
 * "and", "receive"). Those clear any credibility threshold by a wide margin, so they kept
 * passing while ordinary single-letter mistakes on mid-frequency words were silently left
 * alone -- which is what "one wrong letter and it sleeps" describes. This suite fails on that
 * class of miss.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class AutoCorrectCoverageTest {
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

    /**
     * One substituted letter, the single most common typing mistake there is. Each of these
     * is distance 1 from a word any English speaker uses daily.
     */
    private val singleSubstitution =
        mapOf(
            "abput" to "about",
            "becuase" to "because",
            "chikd" to "child",
            "familt" to "family",
            "friebd" to "friend",
            "mornibg" to "morning",
            "peopke" to "people",
            "probkem" to "problem",
            "somethinh" to "something",
            "thibk" to "think",
            "tomorrpw" to "tomorrow",
            "wprk" to "work",
        )

    /** One letter left out -- the second most common mistake, and a distance-1 deletion. */
    private val singleOmission =
        mapOf(
            "shold" to "should",
            "wich" to "which",
            "diferent" to "different",
            "bcause" to "because",
        )

    /** One extra letter, usually a repeated key. */
    private val singleInsertion =
        mapOf(
            "abbout" to "about",
            "peopple" to "people",
            "tthink" to "think",
            "worrk" to "work",
        )

    @Test
    fun `a single substituted letter is corrected`() = assertAllCorrected(singleSubstitution)

    @Test
    fun `a single omitted letter is corrected`() = assertAllCorrected(singleOmission)

    @Test
    fun `a single extra letter is corrected`() = assertAllCorrected(singleInsertion)

    @Test
    fun `a mispressed key next to the intended one is corrected`() {
        // Proximity: every one of these substitutes a physically adjacent key, which is why
        // they happen. A correction engine with no spatial model treats them as no more
        // likely than substituting a letter from the other side of the board, so the tie
        // falls to raw frequency and picks the commoner word -- "vall" became "all".
        //
        // Only cases where the spatial answer is unambiguous are asserted. Where two
        // candidates are *both* one adjacent press away frequency is the right tie-break and
        // there is nothing to fix -- "tuen" is one key from "turn" and one from "then", and
        // "gine" is one from "gone" and one from "fine". Asserting either would be pinning a
        // coin toss.
        //
        // "vall" is the decisive case: "call" is one adjacent press, "all" is a dropped
        // letter to a *more frequent* word. Uniform edit costs pick "all" every time.
        assertAllCorrected(
            mapOf(
                "hime" to "home",
                "namr" to "name",
                "vall" to "call",
            ),
        )
    }

    @Test
    fun `the proximity map matches the shipped key geometry`() {
        // Derived from the layout rather than hand-listed, so this pins the derivation. The
        // diagonals are the point: most mispresses land on the row above or below, and a
        // same-row-only model would miss them entirely.
        assertTrue("v sits under the f/g gap", KeyProximity.areAdjacent('v', 'c'))
        assertTrue("row above", KeyProximity.areAdjacent('v', 'f'))
        assertTrue("row above", KeyProximity.areAdjacent('v', 'g'))
        assertTrue("home row diagonal", KeyProximity.areAdjacent('s', 'w'))
        assertTrue("adjacency is symmetric", KeyProximity.areAdjacent('c', 'v'))

        assertFalse("opposite ends of the board", KeyProximity.areAdjacent('q', 'p'))
        assertFalse("two rows apart", KeyProximity.areAdjacent('q', 'z'))
        assertFalse("a key is not its own neighbour", KeyProximity.areAdjacent('a', 'a'))
    }

    @Test
    fun `real words are still never corrected`() {
        // The counterweight: loosening the threshold must not start rewriting correct input.
        runBlocking {
            listOf(
                "the",
                "and",
                "work",
                "home",
                "name",
                "think",
                "about",
                "people",
                "should",
                "which",
                "really",
                "different",
                "morning",
                "tomorrow",
                "friend",
                "family",
                "child",
                "problem",
                "something",
                "because",
                "turn",
                "call",
                "gone",
                "want",
            ).forEach { word ->
                assertNull(
                    "Real word '$word' must not be corrected",
                    engine.getAutoCorrection(word),
                )
            }
        }
    }

    @Test
    fun `a word typed in sentence case is corrected and keeps its capital`() {
        // Auto-capitalize means the first word of every message is typed with a capital, so
        // this is the single most common shape a real typo arrives in -- and it went through
        // a lookup that lowercases the input but returned a lowercase replacement.
        runBlocking {
            assertEquals("The", engine.getAutoCorrection("Teh"))
            assertEquals("Because", engine.getAutoCorrection("Becuase"))
        }
    }

    @Test
    fun `a key pressed instead of the space bar is split back into two words`() {
        // 'b', 'n' and 'v' sit along the space bar's top edge, so a low thumb produces a
        // run-on with one junk letter where the space belonged. No amount of edit-distance
        // search finds this: the result is not one misspelled word, it is two words joined.
        runBlocking {
            assertEquals("thank you", engine.getAutoCorrection("thankbyou"))
            assertEquals("see you", engine.getAutoCorrection("seenyou"))
            assertEquals("what time", engine.getAutoCorrection("whatvtime"))
        }
    }

    private fun assertAllCorrected(cases: Map<String, String>) =
        runBlocking {
            val missed = mutableListOf<String>()
            val wrong = mutableListOf<String>()
            cases.forEach { (typo, expected) ->
                when (val actual = engine.getAutoCorrection(typo)) {
                    null -> missed += typo
                    expected -> Unit
                    else -> wrong += "$typo -> $actual (wanted $expected)"
                }
            }
            assertTrue(
                "Not corrected at all: $missed; corrected to the wrong word: $wrong",
                missed.isEmpty() && wrong.isEmpty(),
            )
        }
}
