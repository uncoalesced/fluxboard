// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import android.content.ClipDescription
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.KeyCharacterMap
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

    /**
     * Where the caret is, mirrored from [onUpdateSelection].
     *
     * Held rather than queried so [moveCursor] costs no binder round-trip in the common case;
     * re-seeded from `EditorInfo` at the start of every input session, because a new field
     * never reports its initial selection through onUpdateSelection.
     */
    private var selStart = 0
    private var selEnd = 0

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
                if (modelClass.isAssignableFrom(TypingViewModel::class.java)) {
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
                    return EmojiPickerViewModel(
                        repository,
                        emojiRepository,
                        keyboardPreferences,
                    ) as T
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
        // Seed capitalization from the field's declared initial state. Deliberately
        // NOT InputConnection.getCursorCapsMode(): that is a synchronous IPC into the
        // host app's UI thread, and calling it per keystroke freezes this keyboard
        // whenever the host is busy. LatinIME and FlorisBoard seed-and-track for the
        // same reason. From here on the state is tracked locally in TypingViewModel.
        val info = editorInfo ?: currentInputEditorInfo
        // A fresh field reports its caret here and nowhere else -- onUpdateSelection only
        // fires on subsequent moves -- so without this the first cursor move of every session
        // would be computed against a stale position from the previous field.
        selStart = (info?.initialSelStart ?: 0).coerceAtLeast(0)
        selEnd = (info?.initialSelEnd ?: 0).coerceAtLeast(selStart)
        typingViewModel().onInputStarted(
            initialCapsMode = info?.initialCapsMode ?: 0,
            fieldKind = fieldKindFor(info?.inputType ?: 0),
            noPersonalizedLearning = noPersonalizedLearning(info),
            enterIsNewline =
                info != null && enterInsertsNewline(info.inputType, info.imeOptions),
        )
        // A field can be focused with the caret already inside a word -- editing an existing
        // draft, or a search box being corrected. onInputStarted clears the word tracker, so
        // without this the first suggestion tap in such a field would size its replacement
        // against an empty word. One read per session, not per keystroke.
        typingViewModel().onEditorContextChanged(textBeforeCursor(WORD_CONTEXT_CHARS))
    }

    private fun typingViewModel(): TypingViewModel =
        ViewModelProvider(this, viewModelFactory)[TypingViewModel::class.java]

    /**
     * Whether the host asked not to be learned from.
     *
     * The sanctioned mechanism, and deliberately not joined by any app/package heuristic. It
     * used to be the *only* trigger for incognito, which was the hole the password fix closed:
     * the flag has to be set by the host app and Android's own `TextView` does not set it for
     * password fields. The field's own type and the user's manual switch are now folded in
     * beside it, in `TypingViewModel.publishPrivacy` -- one combination, in one place, rather
     * than a second copy of the decision here.
     */
    private fun noPersonalizedLearning(info: EditorInfo?): Boolean =
        info != null &&
            (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        // Scope the host's flag and the field's type to the field actually being edited: once
        // this input session ends they must not linger over unrelated clipboard copies. The
        // user's manual switch deliberately survives -- see TypingViewModel.onInputFinished.
        typingViewModel().onInputFinished()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        typingViewModel().onInputFinished()
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
        // The framework tells us where the caret is whenever it moves, including moves the
        // user made by tapping in the host app. Tracking it here is what lets moveCursor
        // compute a target without an IPC round-trip per step.
        //
        // It also answers a second question the typing model needs: was this move ours?
        // TypingViewModel keeps a running copy of the word being typed, appended to on each
        // key and shortened on each backspace, because reading the field per keystroke would
        // be a blocking IPC into the host. That copy is only correct while this keyboard is
        // the sole editor. Tapping into the middle of a word, pasting, selecting and retyping,
        // or an autofill write all leave it describing text that is no longer there -- and the
        // suggestion strip then deletes `word.length` characters of whatever *is* there.
        //
        // Every edit made here predicts its own caret, so a mismatch means something else
        // moved it. Only then is the field re-read, which keeps this off the keystroke path.
        val external = newSelStart != selStart || newSelEnd != selEnd
        selStart = newSelStart
        selEnd = newSelEnd
        if (external) {
            typingViewModel().onEditorContextChanged(textBeforeCursor(WORD_CONTEXT_CHARS))
        }
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
        // Keyboard no longer shown -> no field-driven incognito any more. A manual private
        // mode stays on: clipboard capture keeps running while the keyboard is hidden, and
        // that is precisely the window a user who threw the switch wants covered.
        typingViewModel().onInputFinished()
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
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)
        // A commit replaces any selection, so the caret lands at the selection's start plus
        // what was inserted. Predicting it here is what lets onUpdateSelection tell this
        // keyboard's own edits apart from the user's -- see there for why that matters.
        predictCaret(minOf(selStart, selEnd) + text.length)
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
        val caret = minOf(selStart, selEnd)
        predictCaret((caret - charCount).coerceAtLeast(0) + replacement.length)
    }

    override fun sendDelete() {
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        // Backspace over a selection deletes the selection; otherwise one character back.
        predictCaret(
            if (selStart != selEnd) minOf(selStart, selEnd) else (selEnd - 1).coerceAtLeast(0),
        )
    }

    override fun textBeforeCursor(maxChars: Int): String =
        currentInputConnection?.getTextBeforeCursor(maxChars, 0)?.toString() ?: ""

    /**
     * Records where this keyboard's own edit should have left the caret.
     *
     * Not an optimization -- it is the discriminator. `onUpdateSelection` fires for every
     * change including the ones made here, so without a prediction to compare against, either
     * every keystroke looks like an external edit (and re-reading the field on each one is the
     * blocking-IPC-per-key that this codebase forbids) or none of them do (and a caret the
     * user moved by hand is never noticed at all).
     */
    private fun predictCaret(position: Int) {
        selStart = position
        selEnd = position
    }

    /**
     * What the Enter key does, decided from the field rather than assumed.
     *
     * Four defects lived in the previous version, and between them they account for Enter
     * behaving differently in every app:
     *
     * 1. `currentInputEditorInfo ?: return` meant Enter did **nothing at all** in any field
     *    that had not published an EditorInfo yet -- a real state during the first frames of
     *    a newly focused field, and permanent in a few hosts.
     * 2. `IME_ACTION_UNSPECIFIED` is 0 and `IME_ACTION_NONE` is 1, so the old
     *    `actionId != IME_ACTION_NONE` test treated *unspecified* as a real action and called
     *    `performEditorAction(0)`. Most editors ignore that, so Enter silently did nothing;
     *    a few treat it as Done and dismissed the keyboard. Unspecified is the default for
     *    plain text fields, which is why this was so widespread.
     * 3. Multi-line fields were never checked. A note or a message composer that also carries
     *    an action would send instead of inserting a line break.
     * 4. `performEditorAction` returns whether the editor handled it, and that was discarded,
     *    so a refusal left the keypress with no effect rather than falling back.
     */
    override fun sendEnter() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo

        if (info == null) {
            sendRawEnter(ic)
            return
        }

        // A field that accepts line breaks always gets one. Its action, if it declares any,
        // belongs to a button in the host's own UI -- not to the return key.
        //
        // The test lives in `enterInsertsNewline` rather than here because the typing model
        // needs the same answer to decide whether Enter may learn the word it just ended, and
        // two copies of this arithmetic would eventually disagree about the same field.
        if (enterInsertsNewline(info.inputType, info.imeOptions)) {
            sendRawEnter(ic)
            // A newline is this keyboard's own edit and has to say so, exactly like commitText.
            //
            // Without this, onUpdateSelection cannot match the caret to anything predicted and
            // treats it as the user having edited elsewhere, so it re-reads the field and
            // bumps the generation token -- a blocking IPC on every Enter, which is the thing
            // this codebase avoids everywhere else. It also made the token stale immediately,
            // which is how the fact that Enter had *ended* a word arrived after the model had
            // already been told the text moved.
            //
            // If a host does something other than insert one character the prediction simply
            // misses and the existing resync path handles it, which is the behaviour this
            // replaces rather than a new risk.
            predictCaret(minOf(selStart, selEnd) + 1)
            return
        }
        performActionOrEnter(ic, info)
    }

    /**
     * Runs the field's declared action, ignoring multi-line and the no-action flag.
     *
     * Separate from [sendEnter] because the two callers want different things: the return key
     * has to respect a field that wants a literal newline, while a Send/Search affordance is
     * an explicit request for the action itself.
     */
    override fun handleEditorAction() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        if (info == null) {
            sendRawEnter(ic)
            return
        }
        performActionOrEnter(ic, info)
    }

    private fun performActionOrEnter(
        ic: android.view.inputmethod.InputConnection,
        info: EditorInfo,
    ) {
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        val actionable =
            action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED
        // The return value matters: an editor that declines the action leaves the keypress
        // with no effect unless something else happens, which reads as a dead Enter key.
        if (!actionable || !ic.performEditorAction(action)) {
            sendRawEnter(ic)
        }
    }

    /**
     * A literal Enter keypress.
     *
     * Built with real timestamps and the soft-keyboard flags rather than the two-argument
     * `KeyEvent` constructor, which leaves `downTime`/`eventTime` at zero. Several editors
     * treat a zero-timestamp event as stale and drop it, and `FLAG_SOFT_KEYBOARD` is how a
     * host distinguishes an on-screen keyboard from a physical one -- some suppress their
     * hardware-keyboard shortcut handling on the strength of it.
     */
    private fun sendRawEnter(ic: android.view.inputmethod.InputConnection) {
        val now = android.os.SystemClock.uptimeMillis()
        val flags = KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
        ic.sendKeyEvent(
            KeyEvent(
                now,
                now,
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_ENTER,
                0,
                0,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                flags,
            ),
        )
        ic.sendKeyEvent(
            KeyEvent(
                now,
                now,
                KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_ENTER,
                0,
                0,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                flags,
            ),
        )
    }

    override fun switchMode(mode: AppMode) {
        // Mode state is mostly handled internally in MainIMEView
    }

    /**
     * Moves the caret entirely inside the InputConnection.
     *
     * This replaces `sendKeyEvent(KEYCODE_DPAD_*)`, which was the focus-escape bug. Android
     * routes a DPAD key event to the focused view first and, when that view does not consume
     * it, to the window's focus search -- so an arrow sent while the caret is already at the
     * end of the text stops being a caret movement and becomes "move focus to the next view",
     * landing somewhere else in the host app. A scrub that ran off the end of a message
     * therefore ended with the text field no longer focused. Nothing about scoping the gesture
     * to this view hierarchy could have prevented that: the event is delivered to the host
     * process on purpose, and only never producing one fixes it.
     *
     * Positions come from [selStart]/[selEnd], which the framework keeps current through
     * [onUpdateSelection], so the common case costs no IPC at all. The surrounding text is
     * requested only where the target genuinely depends on it, and always with a bounded
     * length -- an unbounded `getExtractedText` per scrub step would pull the whole field
     * across a binder transaction dozens of times a second.
     */
    override fun moveCursor(
        move: CursorMove,
        extend: Boolean,
    ) {
        val ic = currentInputConnection ?: return
        val caret = if (extend) selEnd else maxOf(selStart, selEnd)
        val anchor = if (extend) selStart else caret

        // One character either side is enough for the two moves that make up a scrub, which is
        // the only path that runs at speed. The wider read is taken only for moves that
        // genuinely depend on where the line boundaries are.
        val scan = if (move == CursorMove.LEFT || move == CursorMove.RIGHT) 1 else MAX_SCAN
        val before = ic.getTextBeforeCursor(scan, 0)?.toString() ?: return
        val after = ic.getTextAfterCursor(scan, 0)?.toString() ?: return

        val target = cursorTargetFor(before, after, caret, move)?.coerceAtLeast(0) ?: return

        if (extend) {
            ic.setSelection(anchor, target)
            selStart = anchor
            selEnd = target
        } else {
            ic.setSelection(target, target)
            selStart = target
            selEnd = target
        }
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

        // The type the *content* actually is, from the resolver -- which now answers, because
        // the provider reads the file header. The value recorded in Room is used only as a
        // fallback: a sticker imported before a conversion, or edited outside the app, can
        // disagree with what is on disk, and the receiving app will believe the bytes.
        val mimeType =
            contentResolver.getType(uri) ?: sticker.mimeType

        val declared = EditorInfoCompat.getContentMimeTypes(editorInfo)
        val accepted = declared.firstOrNull { ClipDescription.compareMimeTypes(mimeType, it) }
        if (accepted == null) {
            // The field genuinely does not take this content. Say so rather than doing
            // nothing: a tap that produces no response at all is indistinguishable from the
            // keyboard being broken, which is how this reported as "stickers do not work".
            hapticsManager.performKeyPressHaptic()
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

        // Belt and braces on the permission grant. INPUT_CONTENT_GRANT_READ_URI_PERMISSION is
        // the sanctioned route and is what the flag above asks for, but it grants to the
        // *IME target* as the framework understands it -- and an app whose text field lives in
        // one process while its media import runs in another (both of these do) can end up
        // reading the URI from a component the implicit grant never covered. An explicit grant
        // to the editor's package costs nothing and closes that gap.
        runCatching {
            grantUriPermission(editorInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val committed =
            InputConnectionCompat.commitContent(
                inputConnection,
                editorInfo,
                contentInfo,
                flags,
                null,
            )
        if (!committed) {
            // The editor declared the type and then refused the content anyway. Nothing more
            // can be done through the InputConnection, and silently swallowing it is what
            // made this look like the sticker feature simply not existing.
            hapticsManager.performKeyPressHaptic()
        }
    }

    private companion object {
        /**
         * Upper bound on how much surrounding text a single cursor move will pull across IPC.
         *
         * Line-relative moves genuinely need the text, but an unbounded request would copy
         * the whole field through a binder transaction -- and a large one throws
         * `TransactionTooLargeException` rather than merely being slow. Two thousand
         * characters covers any realistic line while staying far inside that limit.
         */
        const val MAX_SCAN = 2000
    }
}
