// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.uncoalesced.stickykeys.stickercore.domain.repository.StickerRepository
import com.uncoalesced.stickykeys.ui.components.decodeBitmap
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class StickersViewModelFailureTest {
    private val repository = mockk<StickerRepository>(relaxed = true)
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        unmockkStatic(BITMAP_LOADING)
        Dispatchers.resetMain()
    }

    @Test
    fun `an unreadable image is counted and the rest of the batch still imports`() {
        mockkStatic(BITMAP_LOADING)
        every { any<Context>().decodeBitmap("file:///readable.png", any()) } returns
            Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        every { any<Context>().decodeBitmap("file:///missing.png", any()) } returns null
        val vm = StickersViewModel(repository)

        vm.batchImportStickers(listOf("file:///missing.png", "file:///readable.png"), context)

        runBlocking { withTimeout(10_000) { vm.batchImportFailures.first { it == 1 } } }
        coVerify(exactly = 1) { repository.saveSticker(any(), any(), any()) }
        assertEquals(false, vm.isBatchImporting.value)
    }

    @Test
    fun `acknowledging clears the failure count`() {
        val vm = StickersViewModel(repository)
        vm.batchImportStickers(listOf("file:///missing.png"), context)
        runBlocking { withTimeout(10_000) { vm.batchImportFailures.first { it == 1 } } }

        vm.clearBatchImportFailures()

        assertEquals(0, vm.batchImportFailures.value)
    }

    @Test
    fun `a failed database write does not crash`() {
        coEvery { repository.deleteSticker(any()) } throws IllegalStateException("closed")
        val vm = StickersViewModel(repository)

        vm.deleteSticker("id")

        coVerify(exactly = 1) { repository.deleteSticker("id") }
    }

    private companion object {
        const val BITMAP_LOADING = "com.uncoalesced.stickykeys.ui.components.BitmapLoadingKt"
    }
}
