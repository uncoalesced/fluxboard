// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * A bundled asset must be re-extracted when the app updates, not only when it is missing.
 *
 * Both engines used to guard the copy with `if (!file.exists())`. Correct on a first launch,
 * wrong on every update after one: the previous version's copy survives and a new asset
 * shipped in the APK is never read.
 *
 * Found on a phone, and findable nowhere else -- an in-place install of v0.1.6 over v0.1.5.1
 * kept typing "font" for "dont", because the rebuilt dictionary with contractions in it was
 * sitting unread in the APK while the old copy was still mapped. A unit test gets a clean app
 * directory every run, so the stale branch simply cannot occur; what is asserted here instead
 * is the mechanism that replaced it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class AssetCacheTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun cached(name: String) = File(context.noBackupFilesDir, name)

    private fun stamp(name: String) = File(context.noBackupFilesDir, "$name.version")

    @Test
    fun `the asset is extracted and stamped on first use`() {
        val file = assetBackedFile(context, "base_dict.bin")
        assertNotNull(file)
        assertTrue(cached("base_dict.bin").exists())
        assertTrue("a stamp must be written", stamp("base_dict.bin").exists())
        assertTrue(stamp("base_dict.bin").readText().isNotEmpty())
    }

    /** An ordinary launch must not re-copy several megabytes. */
    @Test
    fun `an unchanged version reuses the extracted copy`() {
        assetBackedFile(context, "base_dict.bin")
        val marker = "sentinel".toByteArray()
        cached("base_dict.bin").writeBytes(marker)

        assetBackedFile(context, "base_dict.bin")

        assertEquals(
            "the file must not be re-extracted while the version is unchanged",
            marker.size.toLong(),
            cached("base_dict.bin").length(),
        )
    }

    /** The regression itself: a version change has to replace the cached copy. */
    @Test
    fun `a version change re-extracts the asset`() {
        assetBackedFile(context, "base_dict.bin")
        val realSize = cached("base_dict.bin").length()

        // Stand in for "the user installed a new APK": the stamp no longer matches. The value
        // has to be one the platform cannot actually report -- Robolectric's own version code
        // is 0, so using that here would collide and the copy would look current.
        cached("base_dict.bin").writeBytes("stale".toByteArray())
        stamp("base_dict.bin").writeText("a-previous-version")

        val file = assetBackedFile(context, "base_dict.bin")

        assertNotNull(file)
        assertEquals("the stale copy must be replaced", realSize, cached("base_dict.bin").length())
    }

    /** A cached file with no stamp at all is what every pre-v0.1.6 install looks like. */
    @Test
    fun `a copy left by an older build with no stamp is replaced`() {
        assetBackedFile(context, "base_dict.bin")
        val realSize = cached("base_dict.bin").length()

        cached("base_dict.bin").writeBytes("old".toByteArray())
        stamp("base_dict.bin").delete()

        assetBackedFile(context, "base_dict.bin")

        assertEquals(realSize, cached("base_dict.bin").length())
    }

    /** Both shipped assets go through the same path, so neither can drift from the other. */
    @Test
    fun `the bigram table is cached the same way`() {
        assertNotNull(assetBackedFile(context, "bigram_lm.bin"))
        assertTrue(stamp("bigram_lm.bin").exists())
    }
}
