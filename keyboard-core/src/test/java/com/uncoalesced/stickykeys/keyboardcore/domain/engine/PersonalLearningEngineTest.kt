// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.PersonalBigramDao
import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.PersonalDictionaryDao
import com.uncoalesced.stickykeys.keyboardcore.data.local.entity.PersonalBigramEntity
import com.uncoalesced.stickykeys.keyboardcore.data.local.entity.PersonalWordEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PersonalLearningEngineTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var fakePersonalDao: FakePersonalDictionaryDao
    private lateinit var fakeBigramDao: FakePersonalBigramDao
    private lateinit var engine: PersonalLearningEngine

    @Before
    fun setUp() {
        fakePersonalDao = FakePersonalDictionaryDao()
        fakeBigramDao = FakePersonalBigramDao()
        engine =
            PersonalLearningEngine(
                personalDao = fakePersonalDao,
                personalBigramDao = fakeBigramDao,
                lexicon = BloomPersonalLexicon(),
                bigramCache = BloomPersonalBigramCache(),
                scope = testScope,
            )
    }

    @Test
    fun twoStrikeActivationRule_shieldsOnSecondCommit() =
        testScope.runTest {
            // First commit: strike 1 (count = 1)
            engine.onWordCommitted(null, "zixor")
            advanceUntilIdle()

            assertEquals(1, engine.lexicon.countOf("zixor"))
            assertFalse(engine.lexicon.isShielded("zixor"))
            assertEquals(1, fakePersonalDao.getWord("zixor")?.frequency)

            // Second commit: strike 2 (count = 2) -> Joins Inviolable Shield
            engine.onWordCommitted(null, "zixor")
            advanceUntilIdle()

            assertEquals(2, engine.lexicon.countOf("zixor"))
            assertTrue(engine.lexicon.isShielded("zixor"))
            assertEquals(2, fakePersonalDao.getWord("zixor")?.frequency)
        }

    @Test
    fun bigramTransitionLearning_boostsFollowers() =
        testScope.runTest {
            engine.onWordCommitted("order", "shawarma")
            advanceUntilIdle()

            assertEquals(1, engine.bigramCache.countFor("order", "shawarma"))
            assertTrue(engine.bigramCache.boostFor("order", "shawarma") > 0f)
            val followers = engine.bigramCache.followersOf("order")
            assertEquals(1, followers.size)
            assertEquals("shawarma", followers[0].first)

            // Check L2 persist
            val l2Followers = fakeBigramDao.getFollowers("order", 8)
            assertEquals(1, l2Followers.size)
            assertEquals("shawarma", l2Followers[0].nextWord)
        }

    @Test
    fun backspaceRevert_rollsBackUnigramAndBigram() =
        testScope.runTest {
            engine.onWordCommitted("meet", "rahul")
            engine.onWordCommitted("meet", "rahul")
            advanceUntilIdle()

            assertEquals(2, engine.lexicon.countOf("rahul"))
            assertTrue(engine.lexicon.isShielded("rahul"))
            assertEquals(2, engine.bigramCache.countFor("meet", "rahul"))

            // User hits backspace to undo
            engine.onWordReverted("meet", "rahul")
            advanceUntilIdle()

            assertEquals(1, engine.lexicon.countOf("rahul"))
            assertFalse(engine.lexicon.isShielded("rahul"))
            assertEquals(1, engine.bigramCache.countFor("meet", "rahul"))
        }

    @Test
    fun onWordKept_elevatesImmediatelyToTwoStrikes() =
        testScope.runTest {
            // Novel word typed and autocorrect was undone by user
            engine.onWordKept("zorbly")
            advanceUntilIdle()

            assertEquals(2, engine.lexicon.countOf("zorbly"))
            assertTrue(engine.lexicon.isShielded("zorbly"))
            assertEquals(2, fakePersonalDao.getWord("zorbly")?.frequency)
        }

    @Test
    fun sensitiveFieldAndIncognitoShield_bypassesAllLearning() =
        testScope.runTest {
            engine.isIncognitoOrPassword = true

            engine.onWordCommitted("password", "secret123")
            engine.onWordCommitted(null, "bankpin")
            engine.onWordKept("sensitive")
            advanceUntilIdle()

            // L1 caches remain pristine
            assertEquals(0, engine.lexicon.size)
            assertEquals(0, engine.bigramCache.totalTransitionsCount())

            // L2 database remains empty
            assertEquals(0, fakePersonalDao.getAll(10).size)
            assertEquals(0, fakeBigramDao.getAllRecent(10).size)
        }

    @Test
    fun disabledEngine_bypassesLearning() =
        testScope.runTest {
            engine.isEnabled = false

            engine.onWordCommitted(null, "ignoredWord")
            advanceUntilIdle()

            assertEquals(0, engine.lexicon.size)
            assertEquals(0, fakePersonalDao.getAll(10).size)
        }

    @Test
    fun digitsAndWhitespace_areFilteredOut() =
        testScope.runTest {
            engine.onWordCommitted(null, "word123")
            engine.onWordCommitted(null, "two words")
            engine.onWordCommitted(null, "a") // too short
            advanceUntilIdle()

            assertEquals(0, engine.lexicon.size)
            assertEquals(0, fakePersonalDao.getAll(10).size)
        }

    @Test
    fun prewarm_loadsFromDatabase() =
        testScope.runTest {
            fakePersonalDao.insertOrUpdate(
                PersonalWordEntity("prewarmed", 5, System.currentTimeMillis()),
            )
            fakeBigramDao.upsertBigram(
                PersonalBigramEntity("nice", "weather", 3, System.currentTimeMillis()),
            )

            engine.prewarm()
            advanceUntilIdle()

            assertEquals(5, engine.lexicon.countOf("prewarmed"))
            assertTrue(engine.lexicon.isShielded("prewarmed"))
            assertEquals(3, engine.bigramCache.countFor("nice", "weather"))
        }

    @Test
    fun clearAll_purgesL1AndL2() =
        testScope.runTest {
            engine.onWordCommitted("hello", "world")
            advanceUntilIdle()

            assertEquals(1, engine.lexicon.size)
            assertEquals(1, engine.bigramCache.totalTransitionsCount())

            engine.clearAll()
            advanceUntilIdle()

            assertEquals(0, engine.lexicon.size)
            assertEquals(0, engine.bigramCache.totalTransitionsCount())
            assertEquals(0, fakePersonalDao.getAll(10).size)
            assertEquals(0, fakeBigramDao.getAllRecent(10).size)
        }
}

private class FakePersonalDictionaryDao : PersonalDictionaryDao {
    val storage = mutableMapOf<String, PersonalWordEntity>()

    override fun getSuggestionsForPrefix(
        prefix: String,
        limit: Int,
    ): List<PersonalWordEntity> =
        storage.values
            .filter { it.word.startsWith(prefix) }
            .sortedByDescending { it.frequency }
            .take(limit)

    override fun getFrequentWords(
        minFrequency: Int,
        limit: Int,
    ): List<PersonalWordEntity> =
        storage.values
            .filter { it.frequency >= minFrequency }
            .sortedByDescending { it.frequency }
            .take(limit)

    override fun getWord(word: String): PersonalWordEntity? = storage[word]

    override fun insertOrUpdate(word: PersonalWordEntity) {
        storage[word.word] = word
    }

    override fun getAll(limit: Int): List<PersonalWordEntity> =
        storage.values.sortedByDescending { it.frequency }.take(limit)

    override fun deleteWord(word: String): Int = if (storage.remove(word) != null) 1 else 0

    override fun pruneOldEntries(cutoffTimestamp: Long): Int {
        val before = storage.size
        storage.values.removeAll { it.lastUsedTimestamp < cutoffTimestamp }
        return before - storage.size
    }

    override fun clear(): Int {
        val count = storage.size
        storage.clear()
        return count
    }
}

private class FakePersonalBigramDao : PersonalBigramDao {
    val storage = mutableMapOf<Pair<String, String>, PersonalBigramEntity>()

    override fun getFollowers(
        previousWord: String,
        limit: Int,
    ): List<PersonalBigramEntity> =
        storage.values
            .filter { it.previousWord == previousWord }
            .sortedByDescending { it.count }
            .take(limit)

    override fun getAllRecent(limit: Int): List<PersonalBigramEntity> =
        storage.values.sortedByDescending { it.lastUsedTimestamp }.take(limit)

    override fun getBigram(
        prev: String,
        next: String,
    ): PersonalBigramEntity? = storage[prev to next]

    override fun upsertBigram(bigram: PersonalBigramEntity) {
        storage[bigram.previousWord to bigram.nextWord] = bigram
    }

    override fun incrementBigram(
        prev: String,
        next: String,
        timestamp: Long,
    ): Int {
        val existing = storage[prev to next] ?: return 0
        storage[prev to next] =
            existing.copy(count = existing.count + 1, lastUsedTimestamp = timestamp)
        return 1
    }

    override fun deleteBigram(
        prev: String,
        next: String,
    ): Int = if (storage.remove(prev to next) != null) 1 else 0

    override fun pruneOldEntries(cutoffTimestamp: Long): Int {
        val before = storage.size
        storage.values.removeAll { it.lastUsedTimestamp < cutoffTimestamp }
        return before - storage.size
    }

    override fun clearAll(): Int {
        val count = storage.size
        storage.clear()
        return count
    }
}
