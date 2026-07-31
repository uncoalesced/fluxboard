// Engineered by uncoalesced
package com.uncoalesced.stickykeys.capture

import android.Manifest
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The permission mapping, pinned per API level.
 *
 * Getting this wrong fails silently rather than loudly, which is precisely how the original
 * defect survived: requesting a permission that does not exist on the running version is never
 * granted, and the MediaStore query it was meant to enable then returns an empty cursor rather
 * than an error. The caller reads that as "there are no screenshots" and the button appears to
 * do nothing. These assertions exist so a future SDK bump cannot quietly reintroduce that.
 */
class MediaImageAccessTest {
    @Test
    fun `modern platforms ask for the images-only permission`() {
        val perms = MediaImageAccess.requiredPermissions(Build.VERSION_CODES.TIRAMISU)
        assertEquals(listOf(Manifest.permission.READ_MEDIA_IMAGES), perms.toList())
    }

    @Test
    fun `pre-Tiramisu falls back to the storage permission`() {
        // READ_MEDIA_IMAGES does not exist before API 33, and requesting it there is silently
        // never granted -- so the old spelling is not optional, it is the only thing that works.
        listOf(Build.VERSION_CODES.O, Build.VERSION_CODES.S_V2).forEach { sdk ->
            assertEquals(
                "API $sdk must use the legacy permission",
                listOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                MediaImageAccess.requiredPermissions(sdk).toList(),
            )
        }
    }

    @Test
    fun `Android 14 also offers the narrower user-selected permission`() {
        // Requested alongside the full one, not instead of it: that pairing is what makes
        // "Select photos" appear next to "Allow all", so the feature stays usable without
        // handing over the whole library.
        val perms =
            MediaImageAccess.requiredPermissions(Build.VERSION_CODES.UPSIDE_DOWN_CAKE).toList()
        assertTrue(perms.contains(Manifest.permission.READ_MEDIA_IMAGES))
        assertTrue(perms.contains(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
        assertEquals(2, perms.size)
    }

    @Test
    fun `the legacy permission is never requested where the granular ones exist`() {
        // It is capped with maxSdkVersion in the manifest, so requesting it on a modern
        // release would ask for something the app does not declare -- an automatic denial.
        (Build.VERSION_CODES.TIRAMISU..Build.VERSION_CODES.VANILLA_ICE_CREAM).forEach { sdk ->
            assertFalse(
                "API $sdk must not request READ_EXTERNAL_STORAGE",
                MediaImageAccess
                    .requiredPermissions(sdk)
                    .contains(Manifest.permission.READ_EXTERNAL_STORAGE),
            )
        }
    }

    @Test
    fun `every platform asks for something`() {
        // An empty request array launches a permission dialog that resolves instantly with no
        // results, which the caller would read as a denial it can do nothing about.
        (Build.VERSION_CODES.O..Build.VERSION_CODES.VANILLA_ICE_CREAM).forEach { sdk ->
            assertTrue(
                "API $sdk requested nothing",
                MediaImageAccess.requiredPermissions(sdk).isNotEmpty(),
            )
        }
    }
}
