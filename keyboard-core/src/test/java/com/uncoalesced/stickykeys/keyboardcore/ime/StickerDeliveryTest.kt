// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import com.uncoalesced.stickykeys.keyboardcore.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StickerDeliveryTest {
    @Test
    fun `an exact type is accepted`() {
        assertTrue(fieldAcceptsMimeType("image/webp", arrayOf("image/png", "image/webp")))
    }

    @Test
    fun `a wildcard type is accepted`() {
        assertTrue(fieldAcceptsMimeType("image/gif", arrayOf("image/*")))
    }

    @Test
    fun `a different type is refused`() {
        assertFalse(fieldAcceptsMimeType("image/webp", arrayOf("image/png", "image/gif")))
    }

    @Test
    fun `a field that declares nothing refuses`() {
        assertFalse(fieldAcceptsMimeType("image/webp", emptyArray()))
    }

    @Test
    fun `only a delivered sticker goes unannounced`() {
        assertNull(StickerDelivery.Delivered.messageRes)
        assertEquals(R.string.sticker_not_accepted, StickerDelivery.NotAccepted.messageRes)
        assertEquals(R.string.sticker_send_failed, StickerDelivery.Failed.messageRes)
    }
}
