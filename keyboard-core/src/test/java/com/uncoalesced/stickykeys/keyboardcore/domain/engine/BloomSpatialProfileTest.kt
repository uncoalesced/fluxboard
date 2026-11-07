// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

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
                c = 't',
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
                c = 'p',
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
            c = 'a',
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
            c = 'm',
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
            c = 'k',
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
}
