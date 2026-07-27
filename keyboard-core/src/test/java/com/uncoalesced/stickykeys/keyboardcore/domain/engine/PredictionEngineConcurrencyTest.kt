// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The engine reads one shared MappedByteBuffer. Navigating it with position()/get()
 * is stateful, so concurrent lookups on a single shared cursor interleave and read
 * each other's bytes -- producing garbage suggestions, or a bad offset that throws
 * and kills the IME process. Reads now take a private duplicate() cursor.
 *
 * These tests hammer one engine instance from many threads and assert both
 * properties: no exception escapes, and every concurrent result is identical to the
 * sequential baseline (no cross-contamination).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class PredictionEngineConcurrencyTest {
    private lateinit var database: KeyboardDatabase
    private lateinit var engine: PredictionEngine

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, KeyboardDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        engine = PredictionEngine(context, database.personalDictionaryDao())
        runBlocking { engine.initialize() }
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `concurrent getSuggestions returns the same results as sequential`() =
        runBlocking {
            val prefixes = listOf("th", "he", "wo", "st", "co", "in", "an", "re", "de", "pr")

            // Sequential baseline, one lookup at a time.
            val baseline = prefixes.associateWith { engine.getSuggestions(it) }
            assertTrue(
                "dictionary must be loaded for this test to mean anything",
                baseline.getValue("th").isNotEmpty(),
            )

            // Same lookups, 20 rounds, all in flight at once on a multi-threaded pool.
            repeat(20) {
                val concurrent =
                    withContext(Dispatchers.Default) {
                        coroutineScope {
                            prefixes.map { p -> async { p to engine.getSuggestions(p) } }.awaitAll()
                        }
                    }
                for ((prefix, result) in concurrent) {
                    assertEquals(
                        "prefix '$prefix' produced cross-contaminated results under concurrency",
                        baseline.getValue(prefix),
                        result,
                    )
                }
            }
        }

    @Test
    fun `concurrent getAutoCorrection stays consistent and never throws`() =
        runBlocking {
            val typos = listOf("thw", "wrng", "hlelo", "yaer", "abou")

            val baseline = typos.associateWith { engine.getAutoCorrection(it) }

            repeat(10) {
                val concurrent =
                    withContext(Dispatchers.Default) {
                        coroutineScope {
                            typos.map { w -> async { w to engine.getAutoCorrection(w) } }.awaitAll()
                        }
                    }
                for ((word, result) in concurrent) {
                    assertEquals(
                        "word '$word' corrected inconsistently under concurrency",
                        baseline.getValue(word),
                        result,
                    )
                }
            }
        }

    @Test
    fun `mixed suggestion and correction traffic does not corrupt either path`() =
        runBlocking {
            val expectedSuggestions = engine.getSuggestions("th")
            val expectedCorrection = engine.getAutoCorrection("thw")

            // Interleave both read paths across threads -- the realistic keyboard load,
            // where a keystroke fires getSuggestions while space fires getAutoCorrection.
            withContext(Dispatchers.Default) {
                coroutineScope {
                    val jobs =
                        (0 until 40).map { i ->
                            async {
                                if (i % 2 == 0) {
                                    engine.getSuggestions("th") to null
                                } else {
                                    null to engine.getAutoCorrection("thw")
                                }
                            }
                        }
                    for ((suggestions, correction) in jobs.awaitAll()) {
                        if (suggestions != null) {
                            assertEquals(expectedSuggestions, suggestions)
                        } else {
                            assertEquals(expectedCorrection, correction)
                        }
                    }
                }
            }
        }

    @Test
    fun `concurrent initialize maps the dictionary once and stays usable`() =
        runBlocking {
            // Second engine, initialized from many threads at once.
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val other = PredictionEngine(context, database.personalDictionaryDao())

            withContext(Dispatchers.Default) {
                coroutineScope {
                    (0 until 16).map { async { other.initialize() } }.awaitAll()
                }
            }

            assertEquals(engine.getSuggestions("th"), other.getSuggestions("th"))
        }
}
