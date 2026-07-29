// Engineered by uncoalesced
package com.uncoalesced.stickykeys.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardDatabase
import com.uncoalesced.stickykeys.stickercore.data.local.StickyKeysDatabase
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the startup path that crashed the first signed release: both Room databases
 * being constructed and every DAO pulled off them, which is what Hilt does while building
 * the singleton graph in `Application.onCreate`.
 *
 * **What this does not catch, stated plainly.** The release crash was
 * `NoSuchMethodException: StickyKeysDatabase_Impl.<init> []` -- R8 removed a constructor
 * that only Room's reflective lookup reaches. Unit tests run against un-minified classes,
 * so that constructor is always present here and this test would have passed while the
 * release APK died on launch. The guard for *that* specific failure is the
 * `verifyRoomKeepRules` Gradle task, which reads R8's own usage.txt after a release build.
 *
 * What this test does cover is the other way the same startup path can break: a schema,
 * entity or DAO change that stops a database from being built or a DAO from being
 * obtained. Those are invisible to the R8 check and visible here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseStartupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the sticker database builds and every DAO is obtainable`() {
        val db =
            Room
                .inMemoryDatabaseBuilder(context, StickyKeysDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        try {
            assertNotNull("stickerDao", db.stickerDao())
            assertNotNull("packDao", db.packDao())
            assertNotNull("categoryDao", db.categoryDao())
            assertTrue("database should open", db.openHelper.writableDatabase.isOpen)
        } finally {
            db.close()
        }
    }

    @Test
    fun `the keyboard database builds and every DAO is obtainable`() {
        val db =
            Room
                .inMemoryDatabaseBuilder(context, KeyboardDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        try {
            assertNotNull("personalDictionaryDao", db.personalDictionaryDao())
            assertNotNull("clipboardDao", db.clipboardDao())
            assertTrue("database should open", db.openHelper.writableDatabase.isOpen)
        } finally {
            db.close()
        }
    }

    @Test
    fun `both generated implementations resolve by the name Room looks them up under`() {
        // Room does Class.forName(<Database>.canonicalName + "_Impl"). If a database class
        // is ever renamed or moved without the generated implementation following, this
        // fails here rather than at runtime on a device.
        listOf(
            StickyKeysDatabase::class.java,
            KeyboardDatabase::class.java,
        ).forEach { dbClass ->
            val implName = dbClass.canonicalName + "_Impl"
            val impl = Class.forName(implName)
            assertNotNull("$implName should exist", impl)
            assertNotNull(
                "$implName must expose a no-arg constructor for Room to instantiate",
                impl.getDeclaredConstructor(),
            )
        }
    }
}
