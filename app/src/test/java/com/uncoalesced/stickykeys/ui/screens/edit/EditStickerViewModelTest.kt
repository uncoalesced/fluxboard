// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens.edit

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.uncoalesced.stickykeys.stickercore.domain.model.Sticker
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class EditStickerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<StickerRepository>(relaxed = true)
    private lateinit var context: Context
    private lateinit var stickerFile: File

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        stickerFile = File(context.cacheDir, "edit-test.png")
        stickerFile.outputStream().use {
            Bitmap
                .createBitmap(8, 8, Bitmap.Config.ARGB_8888)
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(BITMAP_LOADING)
        Dispatchers.resetMain()
    }

    private fun sticker(mimeType: String = "image/gif") =
        Sticker(
            id = "id",
            packId = null,
            categoryId = null,
            isFavourite = false,
            createdAt = 0L,
            mimeType = mimeType,
            file = stickerFile,
            thumbnailFile = File(""),
        )

    private fun loadedViewModel(): EditStickerViewModel {
        coEvery { repository.getStickerById("id") } returns sticker()
        return EditStickerViewModel(repository, dispatcher).also { it.loadSticker("id") }
    }

    @Test
    fun `a sticker that cannot be found is an error state`() =
        runTest(dispatcher) {
            coEvery { repository.getStickerById("id") } returns null
            val vm = EditStickerViewModel(repository, dispatcher)

            vm.loadSticker("id")
            advanceUntilIdle()

            assertTrue(vm.uiState.value is EditStickerUiState.Error)
        }

    @Test
    fun `a database failure while loading is an error state`() =
        runTest(dispatcher) {
            coEvery { repository.getStickerById("id") } throws IllegalStateException("closed")
            val vm = EditStickerViewModel(repository, dispatcher)

            vm.loadSticker("id")
            advanceUntilIdle()

            assertTrue(vm.uiState.value is EditStickerUiState.Error)
        }

    @Test
    fun `an unreadable edit reports failure instead of crashing`() =
        runTest(dispatcher) {
            val vm = loadedViewModel()
            advanceUntilIdle()
            vm.updateCurrentUri("file:///does/not/exist.png")
            var failed = false
            var completed = false

            vm.overwriteSticker(context, { completed = true }, { failed = true })
            advanceUntilIdle()

            assertTrue(failed)
            assertEquals(false, completed)
        }

    @Test
    fun `overwriting records the edited image as a static webp`() =
        runTest(dispatcher) {
            mockkStatic(BITMAP_LOADING)
            every { any<Context>().decodeBitmap(any(), any()) } returns
                Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            val vm = loadedViewModel()
            advanceUntilIdle()
            var completed = false

            vm.overwriteSticker(context, { completed = true }, {})
            advanceUntilIdle()

            assertTrue(completed)
            coVerify {
                repository.updateStickerData(
                    match { it.mimeType == "image/webp" },
                    any(),
                    any(),
                )
            }
        }

    private companion object {
        const val BITMAP_LOADING = "com.uncoalesced.stickykeys.ui.components.BitmapLoadingKt"
    }
}
