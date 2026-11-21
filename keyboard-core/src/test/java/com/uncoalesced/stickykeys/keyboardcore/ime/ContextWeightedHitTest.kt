// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardDatabase
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import com.uncoalesced.stickykeys.keyboardcore.diagnostics.TypingStatsStore
import com.uncoalesced.stickykeys.keyboardcore.diagnostics.UsageRecorder
import com.uncoalesced.stickykeys.keyboardcore.domain.engine.FakeLanguageModel
import com.uncoalesced.stickykeys.keyboardcore.domain.engine.PredictionEngine
import com.uncoalesced.stickykeys.keyboardcore.domain.engine.SmartKeyHitbox
import com.uncoalesced.stickykeys.keyboardcore.domain.engine.TouchPoint
import com.uncoalesced.stickykeys.keyboardcore.haptics.HapticsManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies context-weighted spatial hit-testing inside the live IME key-press pipeline.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class ContextWeightedHitTest {
    private val dispatcher = StandardTestDispatcher()

    private lateinit var database: KeyboardDatabase
    private lateinit var engine: PredictionEngine
    private lateinit var autoCorrectFlow: MutableStateFlow<Boolean>
    private lateinit var viewModel: TypingViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        SmartKeyHitbox.resetCentroids()
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, KeyboardDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        engine =
            PredictionEngine(
                context = context,
                personalDao = database.personalDictionaryDao(),
                languageModel = FakeLanguageModel(),
            )
        runBlocking { engine.initialize() }

        autoCorrectFlow = MutableStateFlow(true)
        val prefs = mockk<KeyboardPreferences>(relaxed = true)
        every { prefs.autoCapitalizeEnabled } returns MutableStateFlow(false)
        every { prefs.autoCorrectEnabled } returns autoCorrectFlow
        every { prefs.privateModeEnabled } returns MutableStateFlow(false)
        every { prefs.bloomTypeBiomechanicalLearning } returns MutableStateFlow(true)

        viewModel =
            TypingViewModel(
                predictionEngine = engine,
                keyboardPreferences = prefs,
                themeManager = mockk(relaxed = true),
                layoutManager = mockk(relaxed = true),
                hapticsManager = mockk<HapticsManager>(relaxed = true),
                incognitoState = IncognitoState(),
                usageLog = mockk<UsageRecorder>(relaxed = true),
                typingStats = mockk<TypingStatsStore>(relaxed = true),
            )
        viewModel.onInputStarted(initialCapsMode = 0, fieldKind = FieldKind.NORMAL)
    }

    @After
    fun tearDown() {
        SmartKeyHitbox.resetCentroids()
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `tapping d near shared e-d border after th flips to e`() =
        runTest(dispatcher) {
            val controller = HitTestController()
            pressLetter("t", TouchPoint('t', 0f, 0f), controller, this)
            pressLetter("h", TouchPoint('h', 0f, 0f), controller, this)
            pressLetter("d", TouchPoint('d', -0.25f, -0.45f), controller, this)
            advanceUntilIdle()

            assertEquals("the", controller.text)
            assertEquals("the", viewModel.getCurrentWord())
        }

    @Test
    fun `tapping e near shared e-d border after an flips to d`() =
        runTest(dispatcher) {
            val controller = HitTestController()
            pressLetter("a", TouchPoint('a', 0f, 0f), controller, this)
            pressLetter("n", TouchPoint('n', 0f, 0f), controller, this)
            pressLetter("e", TouchPoint('e', 0.25f, 0.48f), controller, this)
            advanceUntilIdle()

            assertEquals("and", controller.text)
            assertEquals("and", viewModel.getCurrentWord())
        }

    @Test
    fun `center tap on d after th does not flip`() =
        runTest(dispatcher) {
            val controller = HitTestController()
            pressLetter("t", TouchPoint('t', 0f, 0f), controller, this)
            pressLetter("h", TouchPoint('h', 0f, 0f), controller, this)
            pressLetter("d", TouchPoint('d', 0f, 0f), controller, this)
            advanceUntilIdle()

            assertEquals("thd", controller.text)
            assertEquals("thd", viewModel.getCurrentWord())
        }

    @Test
    fun `close competing continuations do not flip on border taps`() =
        runTest(dispatcher) {
            // "th" + 'r' ("three") competes closely with "th" + 'e' ("the").
            val thController = HitTestController()
            pressLetter("t", TouchPoint('t', 0f, 0f), thController, this)
            pressLetter("h", TouchPoint('h', 0f, 0f), thController, this)
            pressLetter("r", TouchPoint('r', -0.45f, 0f), thController, this)
            advanceUntilIdle()

            assertEquals("thr", thController.text)
            assertEquals("thr", viewModel.getCurrentWord())

            // "an" + 's' ("answer") competes closely with "an" + 'd' ("and").
            viewModel.onInputStarted(initialCapsMode = 0, fieldKind = FieldKind.NORMAL)
            val anController = HitTestController()
            pressLetter("a", TouchPoint('a', 0f, 0f), anController, this)
            pressLetter("n", TouchPoint('n', 0f, 0f), anController, this)
            pressLetter("s", TouchPoint('s', 0.45f, 0f), anController, this)
            advanceUntilIdle()

            assertEquals("ans", anController.text)
            assertEquals("ans", viewModel.getCurrentWord())
        }

    @Test
    fun `sensitive fields never flip border taps`() =
        runTest(dispatcher) {
            listOf(FieldKind.PASSWORD, FieldKind.PIN, FieldKind.PRIVATE).forEach { kind ->
                viewModel.onInputStarted(initialCapsMode = 0, fieldKind = kind)
                val controller = HitTestController()
                pressLetter("t", TouchPoint('t', 0f, 0f), controller, this)
                pressLetter("h", TouchPoint('h', 0f, 0f), controller, this)
                pressLetter("d", TouchPoint('d', -0.25f, -0.45f), controller, this)
                advanceUntilIdle()

                assertEquals("Expected no flip for $kind", "thd", controller.text)
                assertEquals("thd", viewModel.getCurrentWord())
            }
        }

    @Test
    fun `disabling auto-correct suppresses context-weighted hit flipping`() =
        runTest(dispatcher) {
            autoCorrectFlow.value = false
            val controller = HitTestController()
            pressLetter("t", TouchPoint('t', 0f, 0f), controller, this)
            pressLetter("h", TouchPoint('h', 0f, 0f), controller, this)
            pressLetter("d", TouchPoint('d', -0.25f, -0.45f), controller, this)
            advanceUntilIdle()

            assertEquals("thd", controller.text)
            assertEquals("thd", viewModel.getCurrentWord())
        }

    @Test
    fun `flipped key preserves uppercase and reprojects touch into target frame`() =
        runTest(dispatcher) {
            val controller = HitTestController()
            pressLetter("t", TouchPoint('t', 0f, 0f), controller, this)
            pressLetter("h", TouchPoint('h', 0f, 0f), controller, this)

            val (resolvedLabel, resolvedTouch) =
                viewModel.resolveContextWeightedKey(
                    "D",
                    TouchPoint('D', -0.25f, -0.45f),
                )

            assertEquals("E", resolvedLabel)
            assertNotNull(resolvedTouch)
            assertEquals('E', resolvedTouch!!.char)
            assertEquals(0f, resolvedTouch.normalizedX, 0.001f)
            assertEquals(0.5f, resolvedTouch.normalizedY, 0.001f)
        }

    private fun pressLetter(
        label: String,
        touch: TouchPoint,
        controller: HitTestController,
        scope: kotlinx.coroutines.CoroutineScope,
    ) {
        handleKeyPress(
            keyLabel = label,
            controller = controller,
            currentMode = KeyboardMode.LETTERS_LOWER,
            viewModel = viewModel,
            coroutineScope = scope,
            touchPoint = touch,
        ) {}
    }

    private class HitTestController : KeyboardController {
        private val buffer = StringBuilder()

        val text: String
            get() = buffer.toString()

        override fun commitText(text: String) {
            buffer.append(text)
        }

        override fun textBeforeCursor(maxChars: Int): String = buffer.toString().takeLast(maxChars)

        override fun replaceTextBeforeCursor(
            charCount: Int,
            replacement: String,
        ) {
            val deleteCount = charCount.coerceAtMost(buffer.length)
            buffer.delete(buffer.length - deleteCount, buffer.length)
            buffer.append(replacement)
        }

        override fun sendDelete() {
            if (buffer.isNotEmpty()) {
                buffer.deleteCharAt(buffer.length - 1)
            }
        }

        override fun deleteBefore(charCount: Int) {
            val deleteCount = charCount.coerceAtMost(buffer.length)
            buffer.delete(buffer.length - deleteCount, buffer.length)
        }

        override fun sendEnter() {
            buffer.append('\n')
        }

        override fun handleEditorAction() = Unit

        override fun switchMode(mode: AppMode) = Unit

        override fun moveCursor(
            move: CursorMove,
            extend: Boolean,
        ) = Unit

        override fun performEditAction(actionId: Int) = Unit

        override fun showInputMethodPicker() = Unit
    }
}
