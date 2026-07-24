// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.ClipboardDao
import com.uncoalesced.stickykeys.keyboardcore.data.local.entity.ClipboardEntryEntity
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ClipboardHistoryManagerTest {

    private lateinit var context: Context
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var clipboardDao: ClipboardDao
    private lateinit var clipboardHistoryManager: ClipboardHistoryManager

    @Before
    fun setup() {
        context = spyk(ApplicationProvider.getApplicationContext())
        clipboardManager = mockk(relaxed = true)
        clipboardDao = mockk(relaxed = true)

        every { context.getSystemService(Context.CLIPBOARD_SERVICE) } returns clipboardManager
        
        clipboardHistoryManager = ClipboardHistoryManager(context, clipboardDao)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `sensitive content is not persisted`() = runTest {
        // Arrange
        val clipData = mockk<ClipData>(relaxed = true)
        val description = mockk<ClipDescription>(relaxed = true)
        val extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
        
        every { description.extras } returns extras
        every { clipboardManager.hasPrimaryClip() } returns true
        every { clipboardManager.primaryClip } returns clipData
        every { clipboardManager.primaryClipDescription } returns description

        clipboardHistoryManager.startListening()

        // Capture the listener
        val listenerSlot = slot<ClipboardManager.OnPrimaryClipChangedListener>()
        verify { clipboardManager.addPrimaryClipChangedListener(capture(listenerSlot)) }

        // Act
        listenerSlot.captured.onPrimaryClipChanged()

        // Assert
        coVerify(exactly = 0) { clipboardDao.insert(any()) }
    }

    @Test
    fun `normal content is persisted`() = runTest {
        // Arrange
        val clipData = mockk<ClipData>(relaxed = true)
        val description = mockk<ClipDescription>(relaxed = true)
        val item = mockk<ClipData.Item>(relaxed = true)
        
        every { description.extras } returns null
        every { clipboardManager.hasPrimaryClip() } returns true
        every { clipboardManager.primaryClip } returns clipData
        every { clipboardManager.primaryClipDescription } returns description
        every { clipData.itemCount } returns 1
        every { clipData.getItemAt(0) } returns item
        every { item.text } returns "Hello World"

        clipboardHistoryManager.startListening()

        // Capture the listener
        val listenerSlot = slot<ClipboardManager.OnPrimaryClipChangedListener>()
        verify { clipboardManager.addPrimaryClipChangedListener(capture(listenerSlot)) }

        // Act
        listenerSlot.captured.onPrimaryClipChanged()

        // Assert
        // We use CoroutineScope(Dispatchers.IO) inside the manager, so we might need a small delay or test dispatcher
        // For standard Robolectric tests, we might just sleep briefly or use advanceUntilIdle() if using test dispatcher
        Thread.sleep(100) // Simple workaround for IO dispatcher in the actual class
        coVerify(exactly = 1) { 
            clipboardDao.insert(match { it.text == "Hello World" }) 
        }
    }
}
