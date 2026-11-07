// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardDatabase
import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.PersonalDictionaryDao
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class BloomSmartKeysSpatialTest {
    private lateinit var database: KeyboardDatabase
    private lateinit var dao: PersonalDictionaryDao
    private lateinit var engine: PredictionEngine

    @Before
    fun setUp() {
        SmartKeyHitbox.resetCentroids()
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
    fun tearDown() {
        database.close()
        SmartKeyHitbox.resetCentroids()
    }

    @Test
    fun `touch leaning towards adjacent key yields cheaper substitution cost than leaning away`() {
        // 'o' is bordered by 'i' on the left and 'p' on the right in QWERTY row 0.
        // Touch leaning right toward 'p'
        val touchRight = TouchPoint('o', normalizedX = 0.45f, normalizedY = 0f)
        val costLeaningTowardsP = SmartKeyHitbox.substitutionCost(touchRight, 'p')

        // Touch leaning left away from 'p' (towards 'i')
        val touchLeft = TouchPoint('o', normalizedX = -0.45f, normalizedY = 0f)
        val costLeaningAwayFromP = SmartKeyHitbox.substitutionCost(touchLeft, 'p')

        assertTrue(
            "Leaning towards candidate should be substantially cheaper than leaning away",
            costLeaningTowardsP < costLeaningAwayFromP,
        )
        assertEquals(5, costLeaningAwayFromP)
        assertTrue(costLeaningTowardsP <= 2)
    }

    @Test
    fun `spatial distance aligns with physical touch points in nearestWord`() {
        // Test word "work": user typed "eork" where 'e' is neighbor of 'w'
        // If touch on 'e' leaned towards 'w' (normalizedX = -0.40f)
        val touchesTowardsW =
            listOf(
                TouchPoint('e', normalizedX = -0.40f, normalizedY = 0f),
                TouchPoint('o', 0f, 0f),
                TouchPoint('r', 0f, 0f),
                TouchPoint('k', 0f, 0f),
            )

        val correction =
            runBlocking {
                engine.getAutoCorrection("eork", touches = touchesTowardsW)
            }
        assertEquals("work", correction)
    }

    @Test
    fun `centroid adaptation shifts physical touch coordinates`() {
        // User consistently types 'w' offset to the right by 0.3 key widths
        val touches = listOf(TouchPoint('w', normalizedX = 0.3f, normalizedY = 0f))
        repeat(15) {
            SmartKeyHitbox.adaptWord(touches)
        }

        val (meanX, _) = SmartKeyHitbox.getCentroid('w')
        assertTrue("Centroid should adapt towards +0.3", meanX > 0.15f)

        // Now a strike at +0.3 is recognized as calibrated key center (d ~ 0)
        val testTouch = TouchPoint('w', normalizedX = 0.3f, normalizedY = 0f)
        val dist = SmartKeyHitbox.euclideanDistance(testTouch, 'w')
        assertTrue("Calibrated distance should be very small", dist < 0.15f)
    }
}
