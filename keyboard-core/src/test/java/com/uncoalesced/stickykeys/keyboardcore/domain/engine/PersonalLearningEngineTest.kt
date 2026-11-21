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
import java.io.File
import java.nio.file.Files

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
            assertEquals(1, fakeBigramDao.getBigram("meet", "rahul")?.count)
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

    /**
     * "Clear learned data" can run before PredictionEngine exists in the process, so the
     * onMemoryCleared hook is unset. The .bin stores must still go, or the vault restores
     * every cleared word into Room on the next keyboard start.
     */
    @Test
    fun clearAll_deletesBloomStateFiles_withoutPredictionEngine() =
        testScope.runTest {
            val dir = Files.createTempDirectory("clear-learned").toFile()
            try {
                val stores =
                    listOf(
                        BloomStateFiles.GLIDE_PROFILE,
                        BloomStateFiles.TAP_PROFILE,
                        BloomStateFiles.USER_MEMORY,
                    ).map { File(dir, it) }
                val tmp = File(dir, "${BloomStateFiles.USER_MEMORY}.tmp")
                (stores + tmp).forEach { it.writeBytes(byteArrayOf(1, 2, 3)) }
                val withDir =
                    PersonalLearningEngine(
                        personalDao = fakePersonalDao,
                        personalBigramDao = fakeBigramDao,
                        scope = testScope,
                        stateDir = dir,
                    )

                withDir.clearAll()
                advanceUntilIdle()

                (stores + tmp).forEach { assertFalse("${it.name} survived", it.exists()) }
            } finally {
                dir.deleteRecursively()
            }
        }

    @Test
    fun startupPrewarmAndMaintenance_preservesLearnedCountsAcrossRestarts() =
        testScope.runTest {
            val now = System.currentTimeMillis()
            fakePersonalDao.insertOrUpdate(PersonalWordEntity("fluxboard", 2, now))
            fakePersonalDao.insertOrUpdate(PersonalWordEntity("newterm", 1, now))
            fakeBigramDao.upsertBigram(PersonalBigramEntity("open", "fluxboard", 1, now))

            // Simulate repeated version upgrades / process startups
            repeat(3) {
                engine.prewarmSync()
                engine.runDecayMaintenanceSync()
            }

            assertEquals(2, engine.lexicon.countOf("fluxboard"))
            assertTrue(engine.lexicon.isShielded("fluxboard"))
            assertEquals(1, engine.lexicon.countOf("newterm"))
            assertEquals(1, engine.bigramCache.countFor("open", "fluxboard"))
        }

    @Test
    fun bloomMemoryVault_carriesOverAllMemoryAcrossVersionUpdate() =
        testScope.runTest {
            val tempDir = Files.createTempDirectory("bloom_vault_test").toFile()
            try {
                val vaultFile = File(tempDir, "bloom_user_memory.bin")
                val glideFile = File(tempDir, "bloom_profile.bin")
                val tapFile = File(tempDir, "bloom_tap_profile.bin")

                // 1. User learns unigrams, bigrams, glide offsets, and tap offsets in v1
                engine.onWordCommitted("hey", "joel")
                engine.onWordCommitted("hey", "joel")
                advanceUntilIdle()

                val v1GlideProfile = BloomSpatialProfile()
                v1GlideProfile.learnObservation(
                    char = 'w',
                    nominalX = 100f,
                    nominalY = 200f,
                    observedX = 112f,
                    observedY = 194f,
                    isSensitive = false,
                )
                SmartKeyHitbox.resetCentroids()
                SmartKeyHitbox.adaptCentroid('a', 0.20f, -0.15f)
                val expectedTapA = SmartKeyHitbox.getCentroid('a')
                val expectedGlideW = v1GlideProfile.getOffset('w')

                BloomMemoryVault.reconcileAndSync(
                    vaultFile = vaultFile,
                    glideFile = glideFile,
                    tapFile = tapFile,
                    personalDao = fakePersonalDao,
                    personalBigramDao = fakeBigramDao,
                    learningEngine = engine,
                    glideProfile = v1GlideProfile,
                )

                // 2. Simulate version upgrade where in-memory caches and DB were reset
                SmartKeyHitbox.resetCentroids()
                val v2PersonalDao = FakePersonalDictionaryDao()
                val v2BigramDao = FakePersonalBigramDao()
                val v2Engine =
                    PersonalLearningEngine(
                        personalDao = v2PersonalDao,
                        personalBigramDao = v2BigramDao,
                        lexicon = BloomPersonalLexicon(),
                        bigramCache = BloomPersonalBigramCache(),
                        scope = testScope,
                    )
                val v2GlideProfile = BloomSpatialProfile()

                BloomMemoryVault.reconcileAndSync(
                    vaultFile = vaultFile,
                    glideFile = glideFile,
                    tapFile = tapFile,
                    personalDao = v2PersonalDao,
                    personalBigramDao = v2BigramDao,
                    learningEngine = v2Engine,
                    glideProfile = v2GlideProfile,
                )

                // Verify all 4 memory stores carried over intact into v2
                assertEquals(2, v2Engine.lexicon.countOf("joel"))
                assertTrue(v2Engine.lexicon.isShielded("joel"))
                assertEquals(2, v2PersonalDao.getWord("joel")?.frequency)
                assertEquals(2, v2Engine.bigramCache.countFor("hey", "joel"))
                assertEquals(2, v2BigramDao.getBigram("hey", "joel")?.count)
                assertEquals(expectedGlideW.first, v2GlideProfile.getOffset('w').first, 1e-4f)
                assertEquals(expectedGlideW.second, v2GlideProfile.getOffset('w').second, 1e-4f)
                assertEquals(expectedTapA.first, SmartKeyHitbox.getCentroid('a').first, 1e-4f)
                assertEquals(expectedTapA.second, SmartKeyHitbox.getCentroid('a').second, 1e-4f)
            } finally {
                SmartKeyHitbox.resetCentroids()
                tempDir.deleteRecursively()
            }
        }

    @Test
    fun quarantineRule_requiresThreeStrikesForCoreTypoWhileNovelWordsTakeTwo() =
        testScope.runTest {
            engine.isLikelyCoreTypo = { it == "teh" }

            // Novel word still activates normally (1 -> staged, 2 -> shielded)
            engine.onWordCommitted(null, "zixor")
            advanceUntilIdle()
            assertEquals(1, engine.lexicon.countOf("zixor"))
            assertFalse(engine.lexicon.isShielded("zixor"))
            engine.onWordCommitted(null, "zixor")
            advanceUntilIdle()
            assertEquals(2, engine.lexicon.countOf("zixor"))
            assertTrue(engine.lexicon.isShielded("zixor"))

            // Core typo "teh" is quarantined for strikes 1 and 2
            engine.onWordCommitted(null, "teh")
            engine.onWordCommitted(null, "teh")
            advanceUntilIdle()
            assertEquals(0, engine.lexicon.countOf("teh"))
            assertFalse(engine.lexicon.isShielded("teh"))
            assertEquals(null, fakePersonalDao.getWord("teh"))

            // Strike 3 promotes "teh" with frequency 3
            engine.onWordCommitted(null, "teh")
            advanceUntilIdle()
            assertEquals(3, engine.lexicon.countOf("teh"))
            assertTrue(engine.lexicon.isShielded("teh"))
            assertEquals(3, fakePersonalDao.getWord("teh")?.frequency)
        }

    @Test
    fun onWordKept_bypassesCoreTypoQuarantineImmediately() =
        testScope.runTest {
            engine.isLikelyCoreTypo = { it == "teh" }

            engine.onWordCommitted(null, "teh")
            advanceUntilIdle()
            assertEquals(0, engine.lexicon.countOf("teh"))

            engine.onWordKept("teh")
            advanceUntilIdle()
            assertEquals(2, engine.lexicon.countOf("teh"))
            assertTrue(engine.lexicon.isShielded("teh"))
            assertEquals(2, fakePersonalDao.getWord("teh")?.frequency)
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
