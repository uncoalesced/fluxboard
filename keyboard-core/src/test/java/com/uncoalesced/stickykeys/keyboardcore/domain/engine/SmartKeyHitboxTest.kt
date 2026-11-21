// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
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

class SmartKeyHitboxTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        SmartKeyHitbox.resetCentroids()
    }

    @Test
    fun `exact center hit has zero distance to itself`() {
        val touch = TouchPoint('o', 0f, 0f)
        val distSelf = SmartKeyHitbox.euclideanDistance(touch, 'o')
        assertEquals(0f, distSelf, 0.001f)

        val cost = SmartKeyHitbox.substitutionCost(touch, 'o')
        assertEquals(0, cost)
    }

    @Test
    fun `right edge hit on O leans towards P`() {
        // 'o' is at x=8, 'p' is at x=9
        // Striking 'o' with normalizedX = +0.4f puts touch at x = 8.4
        val touchRight = TouchPoint('o', 0.4f, 0f)

        val distToO = SmartKeyHitbox.euclideanDistance(touchRight, 'o')
        val distToP = SmartKeyHitbox.euclideanDistance(touchRight, 'p')

        assertEquals(0.4f, distToO, 0.001f)
        assertEquals(0.6f, distToP, 0.001f)

        // Struck close to P border -> low substitution cost
        val costToP = SmartKeyHitbox.substitutionCost(touchRight, 'p')
        assertTrue("Cost to P should be low adjacent cost", costToP <= 2)

        // Conversely, striking left edge on O (normalizedX = -0.4f) puts touch at x = 7.6, far from P
        val touchLeft = TouchPoint('o', -0.4f, 0f)
        val distLeftToP = SmartKeyHitbox.euclideanDistance(touchLeft, 'p')
        assertEquals(1.4f, distLeftToP, 0.001f)
        val costLeftToP = SmartKeyHitbox.substitutionCost(touchLeft, 'p')
        assertEquals(5, costLeftToP)
    }

    @Test
    fun `spatial cost follows gboard sweet spot formula`() {
        // Inner sweet spot: d <= 0.25 -> 0.96 * d
        val touchSweet = TouchPoint('g', 0.10f, 0f)
        val costSweet = SmartKeyHitbox.spatialCostFloat(touchSweet, 'g', isInitialChar = false)
        val expectedSweet =
            SmartKeyHitbox.BASE_PROXIMITY_INTERIOR +
                (0.96f * 0.10f) * SmartKeyHitbox.DISTANCE_WEIGHT_LENGTH
        assertEquals(expectedSweet, costSweet, 0.001f)

        // Initial char has higher base proximity cost
        val costInitial = SmartKeyHitbox.spatialCostFloat(touchSweet, 'g', isInitialChar = true)
        val expectedInitial =
            SmartKeyHitbox.BASE_PROXIMITY_INITIAL +
                (0.96f * 0.10f) * SmartKeyHitbox.DISTANCE_WEIGHT_LENGTH
        assertEquals(expectedInitial, costInitial, 0.001f)
    }

    @Test
    fun `centroid adaptation shifts effective key center`() {
        // Repeatedly typing 'p' with an offset of -0.2f (left of center)
        repeat(20) {
            SmartKeyHitbox.adaptCentroid('p', -0.2f, 0f)
        }

        val (meanX, meanY) = SmartKeyHitbox.getCentroid('p')
        assertTrue("Centroid meanX should have adapted towards -0.2f", meanX < -0.1f)
        assertEquals(0f, meanY, 0.001f)

        // Now, if a user strikes 'p' at -0.2f, after adaptation it's calibrated as a center hit (d ~ 0)
        val touch = TouchPoint('p', -0.2f, 0f)
        val dist = SmartKeyHitbox.euclideanDistance(touch, 'p')
        assertTrue("Calibrated distance should be very close to 0", dist < 0.08f)
    }

    @After
    fun tearDown() {
        SmartKeyHitbox.resetCentroids()
    }

    @Test
    fun `a NaN payload with a valid digest is rejected and leaves centroids untouched`() {
        repeat(20) { SmartKeyHitbox.adaptCentroid('p', -0.2f, 0f) }
        val before = SmartKeyHitbox.snapshot()
        val file = tempFolder.newFile("bloom_tap_profile.bin")
        writeRawProfile(file, MAGIC_BTAP, FloatArray(52).also { it[3] = Float.NaN })

        assertFalse(SmartKeyHitbox.load(file))
        assertArrayEquals(before, SmartKeyHitbox.snapshot(), 0f)
    }

    @Test
    fun `an infinite payload with a valid digest is rejected`() {
        val file = tempFolder.newFile("bloom_tap_profile.bin")
        writeRawProfile(file, MAGIC_BTAP, FloatArray(52).also { it[0] = Float.NEGATIVE_INFINITY })

        assertFalse(SmartKeyHitbox.load(file))
        assertTrue(SmartKeyHitbox.snapshot().all { it == 0f })
    }

    @Test
    fun `restore refuses non-finite data`() {
        assertFalse(SmartKeyHitbox.restore(FloatArray(52).also { it[10] = Float.NaN }))
        assertTrue(SmartKeyHitbox.restore(FloatArray(52).also { it[10] = 0.1f }))
        assertEquals(0.1f, SmartKeyHitbox.snapshot()[10], 0f)
    }

    @Test
    fun `a non-finite touch offset does not move the centroid`() {
        SmartKeyHitbox.adaptCentroid('p', Float.NaN, 0f)
        SmartKeyHitbox.adaptCentroid('p', 0f, Float.POSITIVE_INFINITY)

        assertEquals(0f to 0f, SmartKeyHitbox.getCentroid('p'))
    }

    @Test
    fun `saved centroids load back`() {
        repeat(20) { SmartKeyHitbox.adaptCentroid('q', 0.2f, -0.1f) }
        val saved = SmartKeyHitbox.snapshot()
        val file = File(tempFolder.root, "bloom_tap_profile.bin")
        SmartKeyHitbox.save(file)
        SmartKeyHitbox.resetCentroids()

        assertTrue(SmartKeyHitbox.load(file))
        assertArrayEquals(saved, SmartKeyHitbox.snapshot(), 0f)
        assertFalse(File(tempFolder.root, "bloom_tap_profile.bin.tmp").exists())
    }

    @Test
    fun `concurrent saves and loads never tear the tap profile`() {
        repeat(20) { SmartKeyHitbox.adaptCentroid('q', 0.2f, -0.1f) }
        val file = File(tempFolder.root, "bloom_tap_profile.bin")
        SmartKeyHitbox.save(file)

        val failures =
            hammer(
                threads = 16,
                rounds = 40,
                save = { SmartKeyHitbox.save(file) },
                load = { SmartKeyHitbox.load(file) },
            )

        assertTrue("race observed: ${failures.take(5)}", failures.isEmpty())
        assertTrue(SmartKeyHitbox.load(file))
    }

    @Test
    fun `delete removes the tap profile`() {
        val file = File(tempFolder.root, "bloom_tap_profile.bin")
        SmartKeyHitbox.save(file)
        SmartKeyHitbox.delete(file)

        assertFalse(file.exists())
    }

    private companion object {
        const val MAGIC_BTAP = 0x42544150
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
