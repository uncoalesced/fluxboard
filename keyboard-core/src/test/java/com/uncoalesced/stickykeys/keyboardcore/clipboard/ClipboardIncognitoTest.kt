// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.ClipboardDao
import com.uncoalesced.stickykeys.keyboardcore.ime.IncognitoState
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Phase 38: copies made while the keyboard is on an incognito field must not be
 * persisted. This layer sits on top of the EXTRA_IS_SENSITIVE check, which is
 * unchanged and covered by ClipboardHistoryManagerTest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ClipboardIncognitoTest {
    private lateinit var context: Context
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var clipboardDao: ClipboardDao
    private lateinit var incognitoState: IncognitoState
    private lateinit var manager: ClipboardHistoryManager

    @Before
    fun setup() {
        context = spyk(ApplicationProvider.getApplicationContext())
        clipboardManager = mockk(relaxed = true)
        clipboardDao = mockk(relaxed = true)
        incognitoState = IncognitoState()

        every { context.getSystemService(Context.CLIPBOARD_SERVICE) } returns clipboardManager

        manager = ClipboardHistoryManager(context, clipboardDao, incognitoState)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /** Wires a plain, non-sensitive clip and returns the captured change listener. */
    private fun startWithNormalClip(text: String): ClipboardManager.OnPrimaryClipChangedListener {
        val clipData = mockk<ClipData>(relaxed = true)
        val description = mockk<ClipDescription>(relaxed = true)
        val item = mockk<ClipData.Item>(relaxed = true)

        every { description.extras } returns null // not sensitive
        every { clipboardManager.hasPrimaryClip() } returns true
        every { clipboardManager.primaryClip } returns clipData
        every { clipboardManager.primaryClipDescription } returns description
        every { clipData.itemCount } returns 1
        every { clipData.getItemAt(0) } returns item
        every { item.text } returns text

        manager.startListening()
        val listenerSlot = slot<ClipboardManager.OnPrimaryClipChangedListener>()
        verify { clipboardManager.addPrimaryClipChangedListener(capture(listenerSlot)) }
        return listenerSlot.captured
    }

    @Test
    fun `copy made while incognito is not persisted`() =
        runTest {
            val listener = startWithNormalClip("incognito secret")
            incognitoState.set(true)

            listener.onPrimaryClipChanged()

            Thread.sleep(100) // manager writes on its own IO scope
            coVerify(exactly = 0) { clipboardDao.insert(any()) }
        }

    @Test
    fun `copy made right after incognito turns off is persisted`() =
        runTest {
            val listener = startWithNormalClip("normal text")

            // On an incognito field: dropped.
            incognitoState.set(true)
            listener.onPrimaryClipChanged()
            Thread.sleep(100)
            coVerify(exactly = 0) { clipboardDao.insert(any()) }

            // Session ended (keyboard hidden / next field allows learning): captured.
            incognitoState.set(false)
            listener.onPrimaryClipChanged()
            Thread.sleep(100)
            coVerify(exactly = 1) { clipboardDao.insert(match { it.text == "normal text" }) }
        }
}
