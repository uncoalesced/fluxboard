// Engineered by uncoalesced
package com.uncoalesced.stickykeys.transfer.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class MigrationPackagerTest {

    private lateinit var context: Context
    private lateinit var packager: MigrationPackager
    private lateinit var dbPath: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        packager = MigrationPackager(context)
        
        // Setup a dummy keyboard_database
        dbPath = context.getDatabasePath("keyboard_database")
        dbPath.parentFile?.mkdirs()
        
        val db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
        db.execSQL("CREATE TABLE clipboard_entries (id INTEGER PRIMARY KEY, content TEXT)")
        db.execSQL("INSERT INTO clipboard_entries (content) VALUES ('secret password')")
        db.close()
    }

    @After
    fun teardown() {
        context.deleteDatabase("keyboard_database")
        File(context.cacheDir, "migration_payload.zip").delete()
        File(context.filesDir, "stickers").deleteRecursively()
        File(context.getDatabasePath("dummy").parentFile, "evil.db").delete()
    }

    @Test
    fun `packager includes clipboard when includeClipboard is true`() {
        runBlocking {
            val zipFile = packager.packageDataToTempFile(includeClipboard = true)
        
        assertTrue(zipFile.exists())
        
        val zf = ZipFile(zipFile)
        val entry = zf.getEntry("databases/keyboard_database")
        assertNotNull("keyboard_database should be in ZIP", entry)
        
        // We can extract and read it
        val extractedDb = File(context.cacheDir, "extracted_db")
        zf.getInputStream(entry).use { input ->
            extractedDb.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        zf.close()
        
        val db = SQLiteDatabase.openDatabase(extractedDb.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        val cursor = db.rawQuery("SELECT COUNT(*) FROM clipboard_entries", null)
        cursor.moveToFirst()
        assertEquals("Clipboard entries should remain intact", 1, cursor.getInt(0))
        cursor.close()
        db.close()
            extractedDb.delete()
        }
    }

    @Test
    fun `packager sanitizes clipboard when includeClipboard is false`() {
        runBlocking {
            val zipFile = packager.packageDataToTempFile(includeClipboard = false)
        
        assertTrue(zipFile.exists())
        
        val zf = ZipFile(zipFile)
        val entry = zf.getEntry("databases/keyboard_database")
        assertNotNull("keyboard_database should be in ZIP", entry)
        
        val extractedDb = File(context.cacheDir, "extracted_db_sanitized")
        zf.getInputStream(entry).use { input ->
            extractedDb.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        zf.close()
        
        val db = SQLiteDatabase.openDatabase(extractedDb.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        val cursor = db.rawQuery("SELECT COUNT(*) FROM clipboard_entries", null)
        cursor.moveToFirst()
        assertEquals("Clipboard entries should be sanitized (0)", 0, cursor.getInt(0))
            cursor.close()
            db.close()
            extractedDb.delete()
        }
    }

    @Test
    fun `resolveWithinBase rejects path traversal`() {
        val base = File(context.filesDir, "stickers")
        // Legitimate entries resolve to a file inside the base directory.
        assertNotNull(packager.resolveWithinBase(base, "good.webp"))
        assertNotNull(packager.resolveWithinBase(base, "a/../b.webp"))
        // Traversal entries that would escape the base are rejected (null).
        assertNull(packager.resolveWithinBase(base, "../../databases/evil.db"))
        assertNull(packager.resolveWithinBase(base, "../sibling.txt"))
    }

    @Test
    fun `extractDataFromStream ignores zip-slip entries`() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("stickers/good.webp"))
            zos.write(byteArrayOf(1, 2, 3))
            zos.closeEntry()
            // Crafted entry that tries to escape into the databases directory.
            zos.putNextEntry(ZipEntry("stickers/../../databases/evil.db"))
            zos.write(byteArrayOf(9, 9, 9))
            zos.closeEntry()
        }

        runBlocking {
            packager.extractDataFromStream(ByteArrayInputStream(baos.toByteArray()))
        }

        // The legitimate entry lands inside the stickers directory.
        assertTrue(File(File(context.filesDir, "stickers"), "good.webp").exists())
        // The escaping entry must NOT have been written to the databases dir.
        val escaped = File(context.getDatabasePath("dummy").parentFile, "evil.db")
        assertFalse("zip-slip entry must not escape the target directory", escaped.exists())
    }
}
