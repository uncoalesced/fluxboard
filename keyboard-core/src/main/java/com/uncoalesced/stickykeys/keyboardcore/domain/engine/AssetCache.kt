// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

/**
 * Unpacks a bundled data asset to private storage, and re-unpacks it when the app updates.
 *
 * The dictionary cannot be read straight from `assets`: it is memory-mapped, and an asset
 * inside the APK has no file to map. So it is copied out once and mapped from there.
 *
 * "Once" was the bug. Both engines guarded the copy with `if (!file.exists())`, which is
 * correct for a first launch and wrong for every update after it -- the cached copy from the
 * previous version stays, and a new asset shipped in the APK is never read. Found on device,
 * and only findable there: a unit test gets a fresh app directory every run, so the stale
 * branch cannot happen. It meant a dictionary improvement reached new installs only, and
 * silently did nothing for everyone who already had the app.
 *
 * The stamp is the app's own version code. It changes exactly when a new APK is installed,
 * which is exactly when a bundled asset can have changed, so the copy happens once per update
 * and never on an ordinary launch.
 */
internal fun assetBackedFile(
    context: Context,
    name: String,
): File? {
    // noBackupFilesDir, not cacheDir: the OS may reclaim cacheDir at any time without warning,
    // and it is excluded from auto-backup so a multi-megabyte payload never eats the user's
    // backup quota for something reproducible from the APK.
    val dir = context.noBackupFilesDir
    val target = File(dir, name)
    val stamp = File(dir, "$name.version")
    val version = appVersion(context)

    val stamped = runCatching { stamp.readText() }.getOrNull()
    if (target.exists() && stamped == version) return target

    return try {
        // Extract via a temp file and rename, so a process death mid-copy cannot leave a
        // truncated file that a later existence check would then accept forever.
        val tmp = File(dir, "$name.tmp")
        context.assets.open(name).use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            tmp.delete()
            return null
        }
        // Written only after the rename succeeded. A stamp ahead of the file would make a
        // failed copy look current for the rest of the version's life.
        stamp.writeText(version)
        target
    } catch (e: Exception) {
        null
    }
}

/**
 * The installed version code, as a string, or a constant when it cannot be read.
 *
 * A failure here degrades to re-copying on every launch, which is wasteful but correct. The
 * opposite default -- treating an unreadable version as current -- would reintroduce exactly
 * the staleness this exists to fix.
 */
private fun appVersion(context: Context): String =
    try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        // longVersionCode is API 28 and this module ships to 26, so the deprecated int is
        // the only thing available on Android 8. The two never disagree below the point
        // where the high bits are used, which this project does not use.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toString()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toString()
        }
    } catch (e: PackageManager.NameNotFoundException) {
        "unknown"
    }
