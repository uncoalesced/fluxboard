// Engineered by uncoalesced
package com.uncoalesced.stickykeys.stickercore.segmentation

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class MlKitSegmentationEngineTest {

    @Test
    fun `segmentSubject handles mock context and bitmap gracefully`() = runBlocking {
        val engine = MlKitSegmentationEngine()
        val context = mock(Context::class.java)
        val bitmap = mock(Bitmap::class.java)

        // Without play services initialization and real bitmaps, it should fail gracefully
        val result = engine.segmentSubject(context, bitmap)
        assertTrue(result.isFailure)
    }
}
