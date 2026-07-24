// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.clipboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.ClipboardDao
import com.uncoalesced.stickykeys.keyboardcore.data.local.entity.ClipboardEntryEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardHistoryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clipboardDao: ClipboardDao
) {
    private val clipboardManager: ClipboardManager = 
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val clipChangedListener = ClipboardManager.OnPrimaryClipChangedListener {
        handleClipboardChange()
    }

    fun startListening() {
        clipboardManager.addPrimaryClipChangedListener(clipChangedListener)
    }

    fun stopListening() {
        clipboardManager.removePrimaryClipChangedListener(clipChangedListener)
    }

    private fun handleClipboardChange() {
        if (!clipboardManager.hasPrimaryClip()) return
        val clipData = clipboardManager.primaryClip ?: return
        val description = clipboardManager.primaryClipDescription ?: return

        // Privacy rule: Respect EXTRA_IS_SENSITIVE flag
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isSensitive = description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) ?: false
            if (isSensitive) {
                return // Do not persist sensitive content
            }
        } else {
            // Check fallback for older versions if developers manually added the extra
            val isSensitive = description.extras?.getBoolean("android.content.extra.IS_SENSITIVE") ?: false
            if (isSensitive) {
                return
            }
        }

        if (clipData.itemCount > 0) {
            val item = clipData.getItemAt(0)
            val text = item.text?.toString()
            if (!text.isNullOrBlank()) {
                scope.launch {
                    val entry = ClipboardEntryEntity(
                        text = text,
                        timestamp = System.currentTimeMillis()
                    )
                    clipboardDao.insert(entry)
                }
            }
        }
    }
}
