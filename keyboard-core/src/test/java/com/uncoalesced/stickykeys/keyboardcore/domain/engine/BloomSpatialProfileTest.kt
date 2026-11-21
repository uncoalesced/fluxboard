// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BloomSpatialProfileTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `initial profile has zero offsets and returns nominal centroids`() {
        val profile = BloomSpatialProfile()
        val (dx, dy) = profile.getOffset('a')
        assertEquals(0f, dx, 0.0001f)
        assertEquals(0f, dy, 0.0001f)

        val (adaptedX, adaptedY) = profile.getAdaptedCenter('a', 10f, 20f)
        assertEquals(10f, adaptedX, 0.0001f)
        assertEquals(20f, adaptedY, 0.0001f)
    }

    @Test
    fun `learnObservation updates offset toward observed point via EMA`() {
        val profile = BloomSpatialProfile()
        // Nominal at (10, 20), user repeatedly swipes slightly right (+0.2) and down (+0.3)
        for (i in 0 until 5) {
            profile.learnObservation(
                char = 't',
                nominalX = 10f,
                nominalY = 20f,
                observedX = 10.2f,
                observedY = 20.3f,
                isSensitive = false,
            )
        }

        val (dx, dy) = profile.getOffset('t')
        assertTrue("dx should move positive towards 0.2", dx > 0.01f)
        assertTrue("dy should move positive towards 0.3", dy > 0.01f)

        val (adaptedX, adaptedY) = profile.getAdaptedCenter('t', 10f, 20f)
        assertEquals(10f + dx, adaptedX, 0.0001f)
        assertEquals(20f + dy, adaptedY, 0.0001f)
    }

    @Test
    fun `extreme touch drifts are strictly clamped to 0_35 key widths`() {
        val profile = BloomSpatialProfile()
        // Extreme outlier: user hit 3.0 key widths away
        for (i in 0 until 100) {
            profile.learnObservation(
                char = 'p',
                nominalX = 5f,
                nominalY = 5f,
                observedX = 8f,
                observedY = 1f,
                isSensitive = false,
            )
        }

        val (dx, dy) = profile.getOffset('p')
        assertEquals(0.35f, dx, 0.01f)
        assertEquals(-0.35f, dy, 0.01f)
    }

    @Test
    fun `sensitive fields strictly bypass learning`() {
        val profile = BloomSpatialProfile()
        // In sensitive mode, observations must be ignored
        profile.learnObservation(
            char = 'a',
            nominalX = 1f,
            nominalY = 1f,
            observedX = 1.3f,
            observedY = 1.3f,
            isSensitive = true,
        )

        val (dx, dy) = profile.getOffset('a')
        assertEquals(0f, dx, 0.0001f)
        assertEquals(0f, dy, 0.0001f)
    }

    @Test
    fun `binary serialization and deserialization preserves learned offsets`() {
        val profile = BloomSpatialProfile()
        profile.learnObservation(
            char = 'm',
            nominalX = 7f,
            nominalY = 3f,
            observedX = 7.15f,
            observedY = 3.25f,
            isSensitive = false,
        )
        val (expectedDx, expectedDy) = profile.getOffset('m')

        val file = File(tempFolder.root, "bloom_profile.bin")
        profile.save(file)
        assertTrue("File should be created", file.exists() && file.length() > 0)

        val loadedProfile = BloomSpatialProfile()
        val success = loadedProfile.load(file)
        assertTrue("Load should succeed with valid magic and checksum", success)

        val (actualDx, actualDy) = loadedProfile.getOffset('m')
        assertEquals(expectedDx, actualDx, 0.0001f)
        assertEquals(expectedDy, actualDy, 0.0001f)
    }

    @Test
    fun `tampered or corrupt file fails load and remains clean`() {
        val file = File(tempFolder.root, "corrupted_profile.bin")
        file.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04))

        val profile = BloomSpatialProfile()
        val success = profile.load(file)
        assertFalse("Corrupted file load must fail safely", success)

        val (dx, dy) = profile.getOffset('a')
        assertEquals(0f, dx, 0.0001f)
    }

    @Test
    fun `reset clears all learned offsets back to zero`() {
        val profile = BloomSpatialProfile()
        profile.learnObservation(
            char = 'k',
            nominalX = 5f,
            nominalY = 5f,
            observedX = 5.2f,
            observedY = 5.2f,
            isSensitive = false,
        )
        assertTrue(profile.getOffset('k').first > 0f)

        profile.reset()
        assertEquals(0f, profile.getOffset('k').first, 0.0001f)
        assertEquals(0f, profile.getOffset('k').second, 0.0001f)
    }

    @Test
    fun `saveSnapshot and rollbackLastLearning restores offsets accurately`() {
        val profile = BloomSpatialProfile()
        profile.learnObservation('a', 1f, 1f, 1.1f, 1.1f, false)
        val originalDx = profile.getOffset('a').first
        assertTrue(originalDx > 0f)

        // Take snapshot before a bad drift
        profile.saveSnapshot()

        // Apply bad drift
        profile.learnObservation('a', 1f, 1f, 1.35f, 1.35f, false)
        val driftedDx = profile.getOffset('a').first
        assertTrue(driftedDx > originalDx)

        // Roll back
        val rolledBack = profile.rollbackLastLearning()
        assertTrue("Rollback should succeed", rolledBack)
        assertEquals(originalDx, profile.getOffset('a').first, 0.00001f)

        // Second rollback without new snapshot should return false
        assertFalse("Second rollback should fail", profile.rollbackLastLearning())
    }

    @Test
    fun `getAdaptedX and getAdaptedY match getAdaptedCenter without allocation`() {
        val profile = BloomSpatialProfile()
        profile.learnObservation('b', 2f, 3f, 2.2f, 3.1f, false)

        val pair = profile.getAdaptedCenter('b', 2f, 3f)
        val adaptedX = profile.getAdaptedX('b', 2f)
        val adaptedY = profile.getAdaptedY('b', 3f)

        assertEquals(pair.first, adaptedX, 0.00001f)
        assertEquals(pair.second, adaptedY, 0.00001f)
    }

    @Test
    fun `a NaN payload with a valid digest is rejected and leaves offsets untouched`() {
        val profile = BloomSpatialProfile()
        repeat(5) { profile.learnObservation('t', 10f, 20f, 10.2f, 20.3f, isSensitive = false) }
        val before = profile.snapshot()
        val file = tempFolder.newFile("bloom_profile.bin")
        writeRawProfile(file, MAGIC_BLOM, FloatArray(52).also { it[3] = Float.NaN })

        assertFalse(profile.load(file))
        assertArrayEquals(before, profile.snapshot(), 0f)
    }

    @Test
    fun `an infinite payload with a valid digest is rejected`() {
        val profile = BloomSpatialProfile()
        val file = tempFolder.newFile("bloom_profile.bin")
        writeRawProfile(file, MAGIC_BLOM, FloatArray(52).also { it[51] = Float.POSITIVE_INFINITY })

        assertFalse(profile.load(file))
        assertTrue(profile.snapshot().all { it == 0f })
    }

    @Test
    fun `restore refuses non-finite data and clamps finite data`() {
        val profile = BloomSpatialProfile()
        assertFalse(profile.restore(FloatArray(52).also { it[0] = Float.NaN }))
        assertTrue(profile.restore(FloatArray(52).also { it[0] = 5f }))
        assertEquals(0.35f, profile.snapshot()[0], 0.0001f)
    }

    @Test
    fun `a non-finite observation is ignored`() {
        val profile = BloomSpatialProfile()
        profile.learnObservation('t', 10f, 20f, Float.NaN, 20.3f, isSensitive = false)
        val inf = Float.POSITIVE_INFINITY
        profile.learnObservation('t', 10f, inf, 10.2f, 20.3f, isSensitive = false)

        assertEquals(0f to 0f, profile.getOffset('t'))
    }

    @Test
    fun `concurrent saves and loads never tear the glide profile`() {
        val profile = BloomSpatialProfile()
        repeat(5) { profile.learnObservation('t', 10f, 20f, 10.2f, 20.3f, isSensitive = false) }
        val file = File(tempFolder.root, "bloom_profile.bin")
        profile.save(file)
        // A second instance writing the same path: the lock has to be per file, not per object.
        val other = BloomSpatialProfile().also { it.restore(profile.snapshot()) }
        var flip = false

        val failures =
            hammer(
                threads = 16,
                rounds = 40,
                save = {
                    flip = !flip
                    (if (flip) profile else other).save(file)
                },
                load = { BloomSpatialProfile().load(file) },
            )

        assertTrue("race observed: ${failures.take(5)}", failures.isEmpty())
        assertFalse(File(tempFolder.root, "bloom_profile.bin.tmp").exists())
    }

    @Test
    fun `delete removes the glide profile`() {
        val profile = BloomSpatialProfile()
        val file = File(tempFolder.root, "bloom_profile.bin")
        profile.save(file)
        profile.delete(file)

        assertFalse(file.exists())
    }

    private companion object {
        const val MAGIC_BLOM = 0x424C4F4D
    }

    /** A well-formed file (valid digest) carrying whatever floats the caller passes. */
    private fun writeRawProfile(
        file: File,
        magic: Int,
        floats: FloatArray,
    ) {
        val payload = ByteArray(floats.size * 4)
        val payloadBuf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        floats.forEach { payloadBuf.putFloat(it) }
        val digest = MessageDigest.getInstance("SHA-256").digest(payload)
        val buf = ByteBuffer.allocate(8 + payload.size + 32).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(magic)
        buf.putInt(1)
        buf.put(payload)
        buf.put(digest)
        file.writeBytes(buf.array())
    }

    /** Runs [threads] workers that each alternate save and load [rounds] times. */
    private fun hammer(
        threads: Int,
        rounds: Int,
        save: () -> Unit,
        load: () -> Boolean,
    ): List<String> {
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
                            save()
                        } else if (!load()) {
                            failures.add("thread $t round $r failed to load")
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
        return failures
    }
}
