// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.PersonalDictionaryDao
import com.uncoalesced.stickykeys.keyboardcore.data.local.entity.PersonalBigramEntity
import com.uncoalesced.stickykeys.keyboardcore.data.local.entity.PersonalWordEntity
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BloomMemoryVaultTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var vaultFile: File

    @Before
    fun setUp() {
        vaultFile = File(tempFolder.root, "bloom_user_memory.bin")
        BloomMemoryVault.forgetCachedState()
        SmartKeyHitbox.resetCentroids()
    }

    @After
    fun tearDown() {
        SmartKeyHitbox.resetCentroids()
        BloomMemoryVault.forgetCachedState()
    }

    @Test
    fun `a saved snapshot loads back intact`() {
        val glide = FloatArray(52) { it * 0.001f }
        val tap = FloatArray(52) { -it * 0.002f }
        BloomMemoryVault.save(
            vaultFile,
            glide,
            tap,
            listOf(PersonalWordEntity("zixor", 3, 1_000L)),
            listOf(PersonalBigramEntity("order", "shawarma", 2, 2_000L)),
        )

        val loaded = BloomMemoryVault.load(vaultFile)
        assertNotNull(loaded)
        assertArrayEquals(glide, loaded!!.glideOffsets, 0f)
        assertArrayEquals(tap, loaded.tapOffsets, 0f)
        assertEquals(listOf(PersonalWordEntity("zixor", 3, 1_000L)), loaded.unigrams)
        assertEquals(listOf(PersonalBigramEntity("order", "shawarma", 2, 2_000L)), loaded.bigrams)
        assertFalse("no temp file left behind", tmpFile().exists())
    }

    /**
     * Every save writes the same `${name}.tmp`, and saves run on a background scope. Without
     * the I/O lock two saves truncate each other's temp file and a reader in between sees a
     * torn or missing vault. Every load here runs against a file that always holds a valid
     * snapshot, so a single null is the race.
     */
    @Test
    fun `concurrent saves and loads never tear the vault`() {
        saveWords(vaultFile, count = 50, freq = 1)

        val threads = 16
        val rounds = 40
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val failures = Collections.synchronizedList(mutableListOf<String>())

        repeat(threads) { t ->
            pool.execute {
                try {
                    start.await()
                    repeat(rounds) { r ->
                        if ((t + r) % 2 == 0) {
                            saveWords(vaultFile, count = 50 + t, freq = r + 1)
                        } else if (BloomMemoryVault.load(vaultFile) == null) {
                            failures.add("thread $t round $r loaded null")
                        }
                    }
                } catch (e: Throwable) {
                    failures.add("thread $t threw $e")
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        assertTrue("workers finished", done.await(60, TimeUnit.SECONDS))
        pool.shutdownNow()

        assertTrue("race observed: ${failures.take(5)}", failures.isEmpty())
        assertNotNull(BloomMemoryVault.load(vaultFile))
        assertFalse("no temp file left behind", tmpFile().exists())
    }

    @Test
    fun `a flipped payload byte fails the digest and loads nothing`() {
        saveWords(vaultFile, count = 3, freq = 2)
        val bytes = vaultFile.readBytes()
        bytes[8 + 4] = (bytes[8 + 4].toInt() xor 0x01).toByte()
        vaultFile.writeBytes(bytes)

        assertNull(BloomMemoryVault.load(vaultFile))
    }

    @Test
    fun `a truncated vault loads nothing`() {
        saveWords(vaultFile, count = 3, freq = 2)
        val bytes = vaultFile.readBytes()
        vaultFile.writeBytes(bytes.copyOf(bytes.size - 10))

        assertNull(BloomMemoryVault.load(vaultFile))
    }

    @Test
    fun `a wrong magic number loads nothing`() {
        saveWords(vaultFile, count = 3, freq = 2)
        val bytes = vaultFile.readBytes()
        bytes[0] = 0
        vaultFile.writeBytes(bytes)

        assertNull(BloomMemoryVault.load(vaultFile))
    }

    @Test
    fun `non-finite spatial floats are zeroed but learned words survive`() {
        val glide = FloatArray(52).also { it[4] = Float.NaN }
        val tap = FloatArray(52).also { it[7] = Float.POSITIVE_INFINITY }
        writeRawVault(
            vaultFile,
            glide,
            tap,
            listOf(PersonalWordEntity("zixor", 4, 5_000L)),
        )

        val loaded = BloomMemoryVault.load(vaultFile)
        assertNotNull(loaded)
        assertTrue(loaded!!.glideOffsets.all { it == 0f })
        assertTrue(loaded.tapOffsets.all { it == 0f })
        assertEquals(listOf(PersonalWordEntity("zixor", 4, 5_000L)), loaded.unigrams)
    }

    @Test
    fun `saveCurrentState keeps the timestamp of a word whose count did not change`() {
        val engine = PersonalLearningEngine(personalDao = noOpDao())
        engine.lexicon.recordOccurrence("zixor", 3)
        engine.bigramCache.recordTransition("order", "shawarma", 2)

        val old = System.currentTimeMillis() - 10L * 24 * 60 * 60 * 1000
        BloomMemoryVault.save(
            vaultFile,
            FloatArray(52),
            FloatArray(52),
            listOf(PersonalWordEntity("zixor", 3, old)),
            listOf(PersonalBigramEntity("order", "shawarma", 2, old)),
        )
        // Simulate a fresh process: the first save must seed from disk, not stamp everything now.
        BloomMemoryVault.forgetCachedState()

        engine.lexicon.recordOccurrence("newword", 1)
        val before = System.currentTimeMillis()
        BloomMemoryVault.saveCurrentState(vaultFile, engine, BloomSpatialProfile())

        val loaded = BloomMemoryVault.load(vaultFile)
        assertNotNull("saveCurrentState wrote a loadable vault", loaded)
        val words = loaded!!.unigrams.associateBy { it.word }
        assertEquals(old, words.getValue("zixor").lastUsedTimestamp)
        assertTrue(words.getValue("newword").lastUsedTimestamp >= before)
        assertEquals(old, loaded.bigrams.single().lastUsedTimestamp)

        // A word used again gets a fresh timestamp.
        engine.lexicon.recordOccurrence("zixor", 1)
        BloomMemoryVault.saveCurrentState(vaultFile, engine, BloomSpatialProfile())
        val reloaded = BloomMemoryVault.load(vaultFile)!!.unigrams.associateBy { it.word }
        assertEquals(4, reloaded.getValue("zixor").frequency)
        assertTrue(reloaded.getValue("zixor").lastUsedTimestamp >= before)
    }

    @Test
    fun `delete removes the vault and a later load finds nothing`() {
        saveWords(vaultFile, count = 2, freq = 1)
        BloomMemoryVault.delete(vaultFile)

        assertFalse(vaultFile.exists())
        assertNull(BloomMemoryVault.load(vaultFile))
    }

    @Test
    fun `v2 vault round-trips tap variances and adaptive threshold state`() {
        val glide = FloatArray(52) { it * 0.001f }
        val tap = FloatArray(52) { -it * 0.001f }
        val variances = FloatArray(52) { if (it < 2) 0.45f else 0f }
        BloomMemoryVault.save(
            file = vaultFile,
            glideOffsets = glide,
            tapOffsets = tap,
            unigrams = listOf(PersonalWordEntity("zixor", 3, 1_000L)),
            bigrams = emptyList(),
            tapVariances = variances,
            actAcceptCount = 14,
            actRevertCount = 2,
            adaptiveThresholdOffset = 0.012f,
        )

        val loaded = BloomMemoryVault.load(vaultFile)
        assertNotNull(loaded)
        assertArrayEquals(variances, loaded!!.tapVariances, 0f)
        assertEquals(14, loaded.actAcceptCount)
        assertEquals(2, loaded.actRevertCount)
        assertEquals(0.012f, loaded.adaptiveThresholdOffset, 1e-6f)
    }

    @Test
    fun `legacy v1 vault loads cleanly with default v2 fields`() {
        val glide = FloatArray(52) { 0.05f }
        val tap = FloatArray(52) { -0.05f }
        writeRawVault(
            vaultFile,
            glide,
            tap,
            listOf(PersonalWordEntity("legacyword", 3, 5_000L)),
        )

        val loaded = BloomMemoryVault.load(vaultFile)
        assertNotNull(loaded)
        assertArrayEquals(glide, loaded!!.glideOffsets, 0f)
        assertArrayEquals(tap, loaded.tapOffsets, 0f)
        assertTrue(loaded.tapVariances.all { it == 0f })
        assertEquals(0, loaded.actAcceptCount)
        assertEquals(0, loaded.actRevertCount)
        assertEquals(0f, loaded.adaptiveThresholdOffset, 0f)
        assertEquals(listOf(PersonalWordEntity("legacyword", 3, 5_000L)), loaded.unigrams)
    }

    @Test
    fun `saveCurrentState persists and reconciles adaptive threshold and tap variances`() {
        val engine = PersonalLearningEngine(personalDao = noOpDao())
        val threshold = BloomAdaptiveThreshold()
        threshold.recordRevert()
        threshold.recordAccept()
        SmartKeyHitbox.restoreVariances(
            FloatArray(52).apply {
                this[0] = 0.51f
                this[1] = 0.14f
            },
        )

        BloomMemoryVault.saveCurrentState(
            vaultFile = vaultFile,
            learningEngine = engine,
            glideProfile = BloomSpatialProfile(),
            adaptiveThreshold = threshold,
        )

        SmartKeyHitbox.resetCentroids()
        val restoredThreshold = BloomAdaptiveThreshold()
        BloomMemoryVault.reconcileAndSync(
            vaultFile = vaultFile,
            glideFile = null,
            tapFile = null,
            personalDao = noOpDao(),
            personalBigramDao = null,
            learningEngine = engine,
            glideProfile = BloomSpatialProfile(),
            adaptiveThreshold = restoredThreshold,
        )

        assertEquals(0.51f to 0.14f, SmartKeyHitbox.getVariance('a'))
        assertEquals(threshold.snapshot().first, restoredThreshold.snapshot().first)
        assertEquals(threshold.snapshot().second, restoredThreshold.snapshot().second)
        assertEquals(threshold.currentOffset(), restoredThreshold.currentOffset(), 1e-6f)
    }

    private fun tmpFile() = File(vaultFile.parentFile, "${vaultFile.name}.tmp")

    private fun saveWords(
        file: File,
        count: Int,
        freq: Int,
    ) {
        BloomMemoryVault.save(
            file,
            FloatArray(52),
            FloatArray(52),
            (0 until count).map { PersonalWordEntity("word$it", freq, 1_000L + it) },
            emptyList(),
        )
    }

    /** Writes a well-formed vault (valid digest) whose floats are whatever the caller passes. */
    private fun writeRawVault(
        file: File,
        glide: FloatArray,
        tap: FloatArray,
        unigrams: List<PersonalWordEntity>,
    ) {
        val payload = ByteArrayOutputStream()
        val out = DataOutputStream(payload)
        glide.forEach { out.writeInt(java.lang.Float.floatToRawIntBits(it)) }
        tap.forEach { out.writeInt(java.lang.Float.floatToRawIntBits(it)) }
        out.writeInt(unigrams.size)
        unigrams.forEach {
            val b = it.word.toByteArray(Charsets.UTF_8)
            out.writeShort(b.size)
            out.write(b)
            out.writeInt(it.frequency)
            out.writeLong(it.lastUsedTimestamp)
        }
        out.writeInt(0)
        out.flush()
        val bytes = payload.toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val buf = ByteBuffer.allocate(8 + bytes.size + 32).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(0x424D454D)
        buf.putInt(1)
        buf.put(bytes)
        buf.put(digest)
        file.writeBytes(buf.array())
    }

    /** saveCurrentState never touches the DAO; a proxy keeps this test free of Room. */
    private fun noOpDao(): PersonalDictionaryDao =
        Proxy.newProxyInstance(
            PersonalDictionaryDao::class.java.classLoader,
            arrayOf(PersonalDictionaryDao::class.java),
        ) { _, _, _ -> null } as PersonalDictionaryDao
}
