// Engineered by uncoalesced
package com.uncoalesced.stickykeys.transfer.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MigrationPackager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Packages all relevant application data into a temporary ZIP file.
     * @param includeClipboard Whether to include clipboard history. If false, the DB is sanitized.
     * @return The temporary ZIP file.
     */
    suspend fun packageDataToTempFile(includeClipboard: Boolean): File = withContext(Dispatchers.IO) {
        val tempZip = File(context.cacheDir, "migration_payload.zip")
        if (tempZip.exists()) tempZip.delete()
        
        val zos = ZipOutputStream(tempZip.outputStream())
        
        try {
            // 1. Pack StickyKeys DB
            packDatabase(zos, "stickykeys_db")
            
            // 2. Pack Keyboard DB
            if (includeClipboard) {
                packDatabase(zos, "keyboard_database")
            } else {
                packSanitizedKeyboardDatabase(zos)
            }
            
            // 3. Pack DataStore preferences
            val datastoreDir = File(context.filesDir, "datastore")
            if (datastoreDir.exists()) {
                packDirectory(zos, datastoreDir, "datastore")
            }
            
            // 4. Pack Stickers
            val stickersDir = File(context.filesDir, "stickers")
            if (stickersDir.exists()) {
                packDirectory(zos, stickersDir, "stickers")
            }
            
            // 5. Pack Sticker Thumbnails
            val thumbnailsDir = File(context.filesDir, "stickers_thumbnails")
            if (thumbnailsDir.exists()) {
                packDirectory(zos, thumbnailsDir, "stickers_thumbnails")
            }
            
        } finally {
            zos.finish()
            zos.flush()
            zos.close()
        }
        
        return@withContext tempZip
    }

    private fun packDatabase(zos: ZipOutputStream, dbName: String) {
        val dbFile = context.getDatabasePath(dbName)
        if (dbFile.exists()) {
            writeFileToZip(zos, dbFile, "databases/$dbName")
        }
        val shmFile = File(dbFile.parentFile, "$dbName-shm")
        if (shmFile.exists()) {
            writeFileToZip(zos, shmFile, "databases/$dbName-shm")
        }
        val walFile = File(dbFile.parentFile, "$dbName-wal")
        if (walFile.exists()) {
            writeFileToZip(zos, walFile, "databases/$dbName-wal")
        }
    }

    private fun packSanitizedKeyboardDatabase(zos: ZipOutputStream) {
        val originalDb = context.getDatabasePath("keyboard_database")
        if (!originalDb.exists()) return

        // Create a temporary copy to sanitize
        val tempDb = File(context.cacheDir, "temp_keyboard_database")
        if (tempDb.exists()) tempDb.delete()
        
        // We must ensure the original DB is fully checkpointed, but since we are just reading it, 
        // copying the main file might miss WAL data.
        // For a robust migration, the Room DB should be checkpointed. 
        // However, assuming standard SQLite, we can just open it.
        
        // Actually, safest way to copy a SQLite DB that might have WAL:
        // Open the original directly, ATTACH a new temp DB, and backup. But SQLite Android API has no backup.
        // Let's just force a checkpoint by opening and executing PRAGMA wal_checkpoint(TRUNCATE).
        try {
            val db = SQLiteDatabase.openDatabase(originalDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            db.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
            db.close()
        } catch (e: Exception) {
            // Ignore if locked or unable to checkpoint
        }

        originalDb.copyTo(tempDb, overwrite = true)
        
        // Sanitize
        val sanitizedDb = SQLiteDatabase.openDatabase(tempDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            sanitizedDb.execSQL("DELETE FROM clipboard_entries")
            sanitizedDb.execSQL("VACUUM")
        } finally {
            sanitizedDb.close()
        }

        writeFileToZip(zos, tempDb, "databases/keyboard_database")
        tempDb.delete() // Cleanup
    }

    private fun packDirectory(zos: ZipOutputStream, dir: File, zipPrefix: String) {
        dir.listFiles()?.forEach { file ->
            if (file.isFile) {
                writeFileToZip(zos, file, "$zipPrefix/${file.name}")
            }
        }
    }

    private fun writeFileToZip(zos: ZipOutputStream, file: File, zipEntryName: String) {
        zos.putNextEntry(ZipEntry(zipEntryName))
        file.inputStream().use { input ->
            input.copyTo(zos)
        }
        zos.closeEntry()
    }

    /**
     * Extracts a ZIP stream into the application's data directories.
     *
     * Each entry is resolved against a fixed base directory and rejected unless
     * its canonical path stays inside that base -- this defends against zip-slip,
     * where a crafted entry name such as "stickers/../../databases/x" would
     * otherwise escape the intended directory and overwrite arbitrary files.
     */
    suspend fun extractDataFromStream(input: InputStream) = withContext(Dispatchers.IO) {
        val zis = ZipInputStream(input)
        var entry = zis.nextEntry

        while (entry != null) {
            if (!entry.isDirectory) {
                val name = entry.name
                val targetFile = when {
                    name.startsWith("databases/") ->
                        resolveWithinBase(context.getDatabasePath("dummy").parentFile, name.removePrefix("databases/"))
                    name.startsWith("datastore/") ->
                        resolveWithinBase(File(context.filesDir, "datastore"), name.removePrefix("datastore/"))
                    name.startsWith("stickers/") ->
                        resolveWithinBase(File(context.filesDir, "stickers"), name.removePrefix("stickers/"))
                    name.startsWith("stickers_thumbnails/") ->
                        resolveWithinBase(File(context.filesDir, "stickers_thumbnails"), name.removePrefix("stickers_thumbnails/"))
                    else -> null
                }

                targetFile?.let { file ->
                    file.parentFile?.mkdirs()
                    file.outputStream().use { out ->
                        zis.copyTo(out)
                    }
                }
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
    }

    /**
     * Resolves [relativePath] under [baseDir], returning the target file only if
     * its canonical path is strictly inside [baseDir]. Returns null (entry skipped)
     * for anything that would escape -- the zip-slip guard.
     */
    internal fun resolveWithinBase(baseDir: File?, relativePath: String): File? {
        if (baseDir == null) return null
        baseDir.mkdirs()
        val baseCanonical = baseDir.canonicalFile
        val candidate = File(baseCanonical, relativePath).canonicalFile
        val basePrefix = baseCanonical.path + File.separator
        return if (candidate.path.startsWith(basePrefix)) candidate else null
    }
}
