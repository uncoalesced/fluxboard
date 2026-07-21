// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import android.content.ClipDescription
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.uncoalesced.stickykeys.stickercore.data.file.StickerFileManager
import com.uncoalesced.stickykeys.stickercore.domain.model.Sticker
import com.uncoalesced.stickykeys.stickercore.domain.repository.StickerRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo

@AndroidEntryPoint
class StickyKeysIME : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, KeyboardController {

    @Inject
    lateinit var fileManager: StickerFileManager

    @Inject
    lateinit var repository: StickerRepository

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onCreateInputView(): View {
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@StickyKeysIME)
            setViewTreeViewModelStoreOwner(this@StickyKeysIME)
            setViewTreeSavedStateRegistryOwner(this@StickyKeysIME)
            setContent {
                val factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return StickerIMEViewModel(repository) as T
                    }
                }
                val viewModel = ViewModelProvider(this@StickyKeysIME, factory)[StickerIMEViewModel::class.java]
                MainIMEView(
                    keyboardController = this@StickyKeysIME,
                    stickerIMEViewModel = viewModel,
                    fileManager = fileManager,
                    onStickerClick = { commitStickerContent(it) }
                )
            }
        }
        return view
    }

    override fun onWindowShown() {
        super.onWindowShown()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }

    override fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    override fun sendDelete() {
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
    }

    override fun sendEnter() {
        val editorInfo = currentInputEditorInfo ?: return
        if (editorInfo.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) {
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        } else {
            handleEditorAction()
        }
    }

    override fun handleEditorAction() {
        val actionId = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        if (actionId != null && actionId != EditorInfo.IME_ACTION_NONE) {
            currentInputConnection?.performEditorAction(actionId)
        } else {
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    override fun switchMode(mode: AppMode) {
        // Mode state is mostly handled internally in MainIMEView
    }

    private fun commitStickerContent(sticker: Sticker) {
        val file = fileManager.getStickerFile(sticker.id)
        if (!file.exists()) return

        val uri = FileProvider.getUriForFile(
            this,
            "com.uncoalesced.stickykeys.fileprovider",
            file
        )

        val editorInfo = currentInputEditorInfo ?: return
        val inputConnection = currentInputConnection ?: return

        // Default to image/webp
        val mimeType = "image/webp"

        val clip = ClipDescription("Sticker", arrayOf(mimeType))
        val contentInfo = InputContentInfoCompat(uri, clip, null)

        var flags = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            flags = flags or InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
        } else {
            grantUriPermission(editorInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        InputConnectionCompat.commitContent(
            inputConnection,
            editorInfo,
            contentInfo,
            flags,
            null
        )
    }
}
