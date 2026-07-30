// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import android.content.ClipDescription
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
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
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import com.uncoalesced.stickykeys.keyboardcore.domain.engine.PredictionEngine
import com.uncoalesced.stickykeys.stickercore.data.file.StickerFileManager
import com.uncoalesced.stickykeys.stickercore.domain.model.Sticker
import com.uncoalesced.stickykeys.stickercore.domain.repository.StickerRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class StickyKeysIME :
    InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner,
    KeyboardController {
    @Inject
    lateinit var fileManager: StickerFileManager

    @Inject
    lateinit var repository: StickerRepository

    @Inject
    lateinit var predictionEngine: PredictionEngine

    @Inject
    lateinit var keyboardPreferences: KeyboardPreferences

    @Inject
    lateinit var themeManager: com.uncoalesced.stickykeys.keyboardcore.theme.ThemeManager

    @Inject
    lateinit var layoutManager: com.uncoalesced.stickykeys.keyboardcore.layout.LayoutManager

    @Inject
    lateinit var clipboardHistoryManager:
        com.uncoalesced.stickykeys.keyboardcore.clipboard.ClipboardHistoryManager

    @Inject
    lateinit var clipboardDao: com.uncoalesced.stickykeys.keyboardcore.data.local.dao.ClipboardDao

    @Inject
    lateinit var hapticsManager: com.uncoalesced.stickykeys.keyboardcore.haptics.HapticsManager

    @Inject
    lateinit var incognitoState: IncognitoState

    @Inject
    lateinit var emojiRepository:
        com.uncoalesced.stickykeys.keyboardcore.emoji.EmojiRepository

    @Inject
    lateinit var usageLog: com.uncoalesced.stickykeys.keyboardcore.diagnostics.UsageRecorder

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private val viewModelFactory by lazy {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(StickerIMEViewModel::class.java)) {
                    return StickerIMEViewModel(repository) as T
                } else if (modelClass.isAssignableFrom(TypingViewModel::class.java)) {
                    return TypingViewModel(
                        predictionEngine,
                        keyboardPreferences,
                        themeManager,
                        layoutManager,
                        hapticsManager,
                        incognitoState,
                        usageLog,
                    ) as T
                } else if (modelClass.isAssignableFrom(ClipboardIMEViewModel::class.java)) {
                    return ClipboardIMEViewModel(clipboardDao) as T
                } else if (modelClass.isAssignableFrom(EmojiPickerViewModel::class.java)) {
                    return EmojiPickerViewModel(repository, emojiRepository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        clipboardHistoryManager.startListening()
        requestHighRefreshRate()
    }

    /**
     * Asks the display for its fastest mode while the keyboard is up.
     *
     * A window gets 60Hz unless it says otherwise. The system only raises the refresh rate
     * for windows that ask, so on a 120Hz panel every animation this keyboard runs -- key
     * colour transitions, the mode crossfade, the toolbar reveal -- was being sampled at 60,
     * which is what reads as stutter. Nothing about the animation code was wrong; it was
     * being shown half the frames it produced.
     *
     * `preferredDisplayModeId` rather than `preferredRefreshRate`: the latter is a hint the
     * compositor may ignore, while naming a concrete mode at the *current resolution* is
     * honoured. Filtering by resolution matters -- some devices expose high-refresh modes
     * only at a reduced resolution, and switching those would visibly resize the display.
     */
    private fun requestHighRefreshRate() {
        val win = window?.window ?: return
        val display = win.decorView.display ?: return
        val current = display.mode
        val best =
            display.supportedModes
                .filter {
                    it.physicalWidth == current.physicalWidth &&
                        it.physicalHeight == current.physicalHeight
                }.maxByOrNull { it.refreshRate }
                ?: return
        if (best.modeId != current.modeId) {
            win.attributes =
                win.attributes.apply { preferredDisplayModeId = best.modeId }
        }
    }

    override fun onCreateInputView(): View {
        // The owners go on the IME window's decor view, not on the ComposeView.
        //
        // setInputView() adds whatever this returns into the framework's own decor
        // (android:id/inputArea, inside parentPanel). AbstractComposeView then resolves its
        // window recomposer by walking UP from that decor root, so tags set on our own view
        // are never in the search path. With them only on the ComposeView the process died
        // the moment the keyboard was asked to show:
        //
        //     IllegalStateException: ViewTreeLifecycleOwner not found from
        //         android.widget.LinearLayout{... android:id/parentPanel}
        //         at AbstractComposeView.onAttachedToWindow
        //         at InputMethodService.setInputView
        //
        // Setting them on the decor view puts them above every view Compose will inspect --
        // both this one and anything the composition itself looks up (rememberSaveable
        // resolves LocalSavedStateRegistryOwner the same way).
        window.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeViewModelStoreOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
        }

        val view =
            ComposeView(this).apply {
                setContent {
                    val stickerViewModel =
                        ViewModelProvider(
                            this@StickyKeysIME,
                            viewModelFactory,
                        )[StickerIMEViewModel::class.java]
                    val typingViewModel =
                        ViewModelProvider(
                            this@StickyKeysIME,
                            viewModelFactory,
                        )[TypingViewModel::class.java]
                    val clipboardViewModel =
                        ViewModelProvider(
                            this@StickyKeysIME,
                            viewModelFactory,
                        )[ClipboardIMEViewModel::class.java]
                    val emojiPickerViewModel =
                        ViewModelProvider(
                            this@StickyKeysIME,
                            viewModelFactory,
                        )[EmojiPickerViewModel::class.java]

                    MainIMEView(
                        keyboardController = this@StickyKeysIME,
                        typingViewModel = typingViewModel,
                        stickerIMEViewModel = stickerViewModel,
                        clipboardIMEViewModel = clipboardViewModel,
                        emojiPickerViewModel = emojiPickerViewModel,
                        fileManager = fileManager,
                        onStickerClick = { commitStickerContent(it) },
                    )
                }
            }
        return view
    }

    /**
     * Never take over the window with the extracted-text editor.
     *
     * The default implementation switches to fullscreen whenever the window is short --
     * which is exactly what landscape and split-screen produce. In that mode the IME
     * covers the host app entirely and substitutes its own text field, so in a split-screen
     * pair the user loses sight of the app they are typing into. Refusing fullscreen keeps
     * the keyboard docked to its own strip in every window configuration.
     */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(
        editorInfo: EditorInfo?,
        restarting: Boolean,
    ) {
        super.onStartInputView(editorInfo, restarting)
        updateIncognito(editorInfo)
        // Seed capitalization from the field's declared initial state. Deliberately
        // NOT InputConnection.getCursorCapsMode(): that is a synchronous IPC into the
        // host app's UI thread, and calling it per keystroke freezes this keyboard
        // whenever the host is busy. LatinIME and FlorisBoard seed-and-track for the
        // same reason. From here on the state is tracked locally in TypingViewModel.
        val info = editorInfo ?: currentInputEditorInfo
        typingViewModel().onInputStarted(initialCapsMode = info?.initialCapsMode ?: 0)
    }

    private fun typingViewModel(): TypingViewModel =
        ViewModelProvider(this, viewModelFactory)[TypingViewModel::class.java]

    /**
     * Phase 38: the editor asking not to be learned from is the only trigger for
     * incognito. Deliberately no app/package heuristics -- IME_FLAG_NO_PERSONALIZED_LEARNING
     * is the sanctioned mechanism and the flag alone drives this.
     */
    private fun updateIncognito(editorInfo: EditorInfo?) {
        val info = editorInfo ?: currentInputEditorInfo
        val noLearning =
            info != null &&
                (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        incognitoState.set(noLearning)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        // Scope incognito to the field actually being edited: once this input
        // session ends the flag must not linger over unrelated clipboard copies.
        incognitoState.set(false)
    }

    override fun onFinishInput() {
        super.onFinishInput()
        incognitoState.set(false)
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            candidatesStart,
            candidatesEnd,
        )
        // No getCursorCapsMode() call here on purpose -- see onStartInputView.
    }

    override fun onWindowShown() {
        super.onWindowShown()
        usageLog.onSessionStart()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        // Flushed when the keyboard goes away rather than on a timer: an IME process can be
        // killed at any moment, and a session that only existed in memory would be lost.
        usageLog.onSessionEnd()
        // Keyboard no longer shown -> not on an incognito field any more.
        incognitoState.set(false)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    override fun onDestroy() {
        super.onDestroy()
        clipboardHistoryManager.stopListening()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }

    override fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    override fun replaceTextBeforeCursor(
        charCount: Int,
        replacement: String,
    ) {
        val ic = currentInputConnection ?: return
        // beginBatchEdit keeps the host editor from rendering the intermediate state.
        ic.beginBatchEdit()
        try {
            if (charCount > 0) {
                ic.deleteSurroundingText(charCount, 0)
            }
            ic.commitText(replacement, 1)
        } finally {
            ic.endBatchEdit()
        }
    }

    override fun sendDelete() {
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
    }

    override fun sendEnter() {
        val editorInfo = currentInputEditorInfo ?: return
        if (editorInfo.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) {
            currentInputConnection?.sendKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER),
            )
            currentInputConnection?.sendKeyEvent(
                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER),
            )
        } else {
            handleEditorAction()
        }
    }

    override fun handleEditorAction() {
        val actionId = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        if (actionId != null && actionId != EditorInfo.IME_ACTION_NONE) {
            currentInputConnection?.performEditorAction(actionId)
        } else {
            currentInputConnection?.sendKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER),
            )
            currentInputConnection?.sendKeyEvent(
                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER),
            )
        }
    }

    override fun switchMode(mode: AppMode) {
        // Mode state is mostly handled internally in MainIMEView
    }

    override fun sendEditingKey(
        keyCode: Int,
        shift: Boolean,
        ctrl: Boolean,
    ) {
        val ic = currentInputConnection ?: return
        var meta = 0
        if (shift) meta = meta or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        if (ctrl) meta = meta or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON

        // Built by hand rather than via the two-argument KeyEvent constructor, which has no
        // metaState parameter -- without one, a held Select would move the caret instead of
        // extending the selection.
        val now = android.os.SystemClock.uptimeMillis()
        ic.sendKeyEvent(
            KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta),
        )
        ic.sendKeyEvent(
            KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta),
        )
    }

    override fun performEditAction(actionId: Int) {
        currentInputConnection?.performContextMenuAction(actionId)
    }

    override fun showInputMethodPicker() {
        val imm =
            getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as? android.view.inputmethod.InputMethodManager
        imm?.showInputMethodPicker()
    }

    private fun commitStickerContent(sticker: Sticker) {
        hapticsManager.performStickerSendHaptic()
        val file = fileManager.getStickerFile(sticker.id)
        if (!file.exists()) return

        val uri =
            FileProvider.getUriForFile(
                this,
                "com.uncoalesced.stickykeys.fileprovider",
                file,
            )

        val editorInfo = currentInputEditorInfo ?: return
        val inputConnection = currentInputConnection ?: return

        val mimeType = sticker.mimeType
        val supportedMimeTypes = EditorInfoCompat.getContentMimeTypes(editorInfo)
        val isSupported = supportedMimeTypes.any { ClipDescription.compareMimeTypes(mimeType, it) }
        if (!isSupported) {
            // Target app does not declare support for this MIME type
            return
        }

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
            null,
        )
    }
}
