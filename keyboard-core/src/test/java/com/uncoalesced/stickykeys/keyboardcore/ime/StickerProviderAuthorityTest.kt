// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class StickerProviderAuthorityTest {
    @Test
    fun `authority follows the application id`() {
        assertEquals(
            "com.uncoalesced.stickykeys.fileprovider",
            stickerProviderAuthority("com.uncoalesced.stickykeys"),
        )
    }

    @Test
    fun `benchmark suffix is carried into the authority`() {
        assertEquals(
            "com.uncoalesced.stickykeys.benchmark.fileprovider",
            stickerProviderAuthority("com.uncoalesced.stickykeys.benchmark"),
        )
    }
}
