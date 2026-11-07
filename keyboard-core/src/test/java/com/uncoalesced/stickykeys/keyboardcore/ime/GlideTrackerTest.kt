// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlideTrackerTest {
    @Test
    fun `registers keys and reports normalized centroids`() {
        val tracker = GlideTracker()
        tracker.register('h', Rect(100f, 100f, 200f, 200f))
        tracker.register('e', Rect(200f, 100f, 300f, 200f))

        val centroidH = tracker.keyCentroid('h')
        val centroidE = tracker.keyCentroid('e')

        assertNotNull(centroidH)
        assertNotNull(centroidE)

        // Width is 100px, center of H is (150, 150) -> normalized (1.5, 1.5)
        assertEquals(1.5f, centroidH!!.first, 0.01f)
        assertEquals(1.5f, centroidH.second, 0.01f)

        // Center of E is (250, 150) -> normalized (2.5, 1.5)
        assertEquals(2.5f, centroidE!!.first, 0.01f)
        assertEquals(1.5f, centroidE.second, 0.01f)
    }

    @Test
    fun `finish produces stroke with BloomTrajectory and key centroids`() {
        val tracker = GlideTracker()
        tracker.register('h', Rect(0f, 0f, 100f, 100f))
        tracker.register('e', Rect(100f, 0f, 200f, 100f))
        tracker.register('l', Rect(200f, 0f, 300f, 100f))
        tracker.register('o', Rect(300f, 0f, 400f, 100f))

        tracker.begin(Offset(50f, 50f))
        tracker.move(Offset(150f, 50f))
        tracker.move(Offset(250f, 50f))
        tracker.move(Offset(350f, 50f))

        val stroke = tracker.finish()
        assertNotNull(stroke)
        assertNotNull(stroke!!.bloomTrajectory)
        assertTrue(stroke.bloomTrajectory!!.isUsable)
        assertEquals(48, stroke.bloomTrajectory!!.count)
        assertTrue(stroke.keyCentroids.containsKey('h'))
        assertTrue(stroke.keyCentroids.containsKey('o'))
    }

    @Test
    fun `corner cutting across key gaps produces usable BloomTrajectory`() {
        val tracker = GlideTracker()
        // Register two keys spaced apart
        tracker.register('a', Rect(0f, 0f, 100f, 100f))
        tracker.register('z', Rect(200f, 200f, 300f, 300f))

        // Sweep diagonally across the gap
        tracker.begin(Offset(50f, 50f))
        tracker.move(Offset(120f, 120f)) // in gap
        tracker.move(Offset(180f, 180f)) // in gap
        tracker.move(Offset(250f, 250f)) // in z

        val stroke = tracker.finish()
        assertNotNull(stroke)
        assertTrue("Stroke with usable Bloom trajectory must be usable", stroke!!.isUsable)
        assertNotNull(stroke.bloomTrajectory)
    }

    @Test
    fun `finishBloom provides direct trajectory extraction`() {
        val tracker = GlideTracker()
        tracker.register('a', Rect(0f, 0f, 100f, 100f))
        tracker.begin(Offset(10f, 10f))
        tracker.move(Offset(50f, 50f))
        tracker.move(Offset(90f, 90f))

        val bloom = tracker.finishBloom()
        assertNotNull(bloom)
        assertTrue(bloom!!.isUsable)
        assertEquals(48, bloom.count)
    }
}
