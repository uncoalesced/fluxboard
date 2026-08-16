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
import kotlin.system.measureTimeMillis

/**
 * The properties the beam search has and the unbounded recursion it replaced did not.
 *
 * The old walk followed every branch the path admitted, to whatever depth it admitted. That is
 * fine for a glide a person actually draws and unbounded for one they do not: a long looping
 * path crosses most of the board, and every crossing multiplies the branches alive at the next
 * letter. There was no test for it because there was no bound to test.
 *
 * Run against the real dictionary rather than a fixture, because the property under test is
 * about the size of the search, and a toy trie has no size to speak of.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class GlideBeamTest {
    private lateinit var database: KeyboardDatabase
    private lateinit var engine: PredictionEngine

    /** The h-e-l-o path used throughout GlideStrokeTest, so results are comparable to it. */
    private val helloStroke = GlideStroke("hgfdertyuiklo".toList(), setOf(0, 4, 11, 12))

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, KeyboardDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        engine =
            PredictionEngine(context, database.personalDictionaryDao(), FakeLanguageModel())
        runBlocking { engine.initialize() }
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `an ordinary glide still decodes`() =
        runBlocking {
            // The bound must not have cut the answer off along with the pathology.
            val results = engine.decodeGlide(helloStroke)
            assertTrue("the h-e-l-o path should decode to something", results.isNotEmpty())
            assertTrue("expected 'hello' among $results", results.contains("hello"))
        }

    @Test
    fun `a pathological stroke returns in bounded time instead of exploding`() =
        runBlocking {
            // Every letter of the alphabet, twice round, with a corner every few keys: a path
            // no finger draws, and precisely the shape that makes an unbounded walk enumerate
            // the dictionary. The assertion is that it finishes at all -- the wall-clock number
            // is a generous ceiling, not a performance target.
            val keys = ("abcdefghijklmnopqrstuvwxyz" + "abcdefghijklmnopqrstuvwxyz").toList()
            val stroke = GlideStroke(keys, keys.indices.step(3).toSet())

            var results: List<String> = emptyList()
            val elapsed = measureTimeMillis { results = engine.decodeGlide(stroke) }

            assertTrue("pathological decode took ${elapsed}ms", elapsed < 5_000)
            assertTrue("results must still be capped", results.size <= 5)
        }

    @Test
    fun `a very long stroke does not overflow the stack`() =
        runBlocking {
            // The old walk recursed once per letter. Iterating instead means depth is a loop
            // counter rather than a call frame, and this is the case that told them apart.
            val keys = (0 until 400).map { ('a' + (it % 26)) }
            val stroke = GlideStroke(keys, setOf(0, keys.lastIndex))
            // Returning nothing is a fine answer here; throwing is not.
            assertTrue(engine.decodeGlide(stroke).size <= 5)
        }

    @Test
    fun `concurrent decodeGlide matches the sequential baseline`() =
        runBlocking {
            // Same reasoning as the suggestion and correction concurrency tests: the beam loop
            // reads through reader()'s private cursor, never the shared MappedByteBuffer. This
            // is the test that would catch an edit that reached for the shared one.
            val strokes =
                listOf(
                    helloStroke,
                    GlideStroke("hgfdertyuio".toList(), setOf(0, 4, 10)),
                    GlideStroke("wertyu".toList(), setOf(0, 5)),
                    GlideStroke("qwertyuiop".toList(), setOf(0, 4, 9)),
                )
            val baseline = strokes.map { engine.decodeGlide(it) }

            repeat(10) {
                val concurrent =
                    withContext(Dispatchers.Default) {
                        coroutineScope {
                            strokes.map { s -> async { engine.decodeGlide(s) } }.awaitAll()
                        }
                    }
                assertEquals(
                    "glide decoding produced cross-contaminated results under concurrency",
                    baseline,
                    concurrent,
                )
            }
        }

    private fun engineFavouring(
        previous: String,
        word: String,
    ): PredictionEngine =
        PredictionEngine(
            ApplicationProvider.getApplicationContext(),
            database.personalDictionaryDao(),
            FakeLanguageModel(mapOf(previous to listOf(word to 255))),
        ).also { runBlocking { it.initialize() } }

    @Test
    fun `context decides a reading the path genuinely cannot`() =
        runBlocking {
            // The case the decoder's own doc comment names: gliding t-o and t-o-o draw the
            // identical path, because a finger cannot show a doubled letter. Their glide costs
            // are equal by construction, so frequency picks "to" and nothing about the gesture
            // disagrees. This is exactly where a sentence should get a vote.
            val stroke = GlideStroke("tyuio".toList(), setOf(0, 4))
            assertEquals(
                "the two readings must genuinely tie for this test to mean anything",
                scoreGlideCandidate("to", stroke),
                scoreGlideCandidate("too", stroke),
            )

            val plain = engine.decodeGlide(stroke)
            assertTrue("expected both readings in $plain", plain.containsAll(listOf("to", "too")))
            assertEquals("frequency alone should pick 'to'", "to", plain.first())

            val contextual =
                engineFavouring("far", "too").decodeGlide(stroke, previousWord = "far")
            assertEquals("context should pick 'too' after 'far'", "too", contextual.first())
        }

    @Test
    fun `context cannot overrule the path itself`() =
        runBlocking {
            val plain = engine.decodeGlide(helloStroke)
            assertTrue("need at least two readings", plain.size >= 2)

            // Take the worst-fitting reading the path admits and favour it as hard as the
            // language model is able to. Context is capped below the cost of a single
            // unexplained corner precisely so this cannot work -- a strongly-expected word is
            // not evidence about what the finger drew.
            val worst = plain.last()
            val gap =
                scoreGlideCandidate(worst, helloStroke)!! -
                    scoreGlideCandidate(plain.first(), helloStroke)!!
            assertTrue(
                "this stroke no longer has a decisive best reading (gap $gap); pick another",
                gap > 4,
            )

            val contextual =
                engineFavouring("said", worst).decodeGlide(helloStroke, previousWord = "said")

            assertEquals(
                "a decisively better path fit must survive any amount of context",
                plain.first(),
                contextual.first(),
            )
            assertTrue(
                "context must not add a reading the path does not admit: $contextual",
                plain.containsAll(contextual),
            )
        }

    @Test
    fun `no previous word leaves glide results byte-identical`() =
        runBlocking {
            val loaded =
                PredictionEngine(
                    ApplicationProvider.getApplicationContext(),
                    database.personalDictionaryDao(),
                    FakeLanguageModel(mapOf("said" to listOf("hello" to 255))),
                ).also { runBlocking { it.initialize() } }

            assertEquals(engine.decodeGlide(helloStroke), loaded.decodeGlide(helloStroke))
        }
}
