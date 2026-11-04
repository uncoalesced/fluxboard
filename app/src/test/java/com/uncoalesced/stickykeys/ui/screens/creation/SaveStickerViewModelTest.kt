// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens.creation

import android.graphics.Bitmap
import com.uncoalesced.stickykeys.stickercore.domain.repository.StickerRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
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

/**
 * Saving used to complete on the IO dispatcher, and the completion navigates, so every saved
 * sticker crashed the app. Real threads are used on purpose: a single-threaded test dispatcher
 * could not tell the two apart.
 */
@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SaveStickerViewModelTest {
    private lateinit var main: ExecutorCoroutineDispatcher
    private lateinit var io: ExecutorCoroutineDispatcher
    private val repository = mockk<StickerRepository>(relaxed = true)
    private val bitmap = Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888)

    @Before
    fun setUp() {
        main = newSingleThreadContext(MAIN_THREAD)
        io = newSingleThreadContext(IO_THREAD)
        Dispatchers.setMain(main)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        main.close()
        io.close()
    }

    /** Without the " @coroutine#N" that debug mode appends to the name. */
    private fun threadName() = Thread.currentThread().name.substringBefore(" @")

    private fun await(result: CompletableDeferred<String>): String =
        runBlocking { withTimeout(10_000) { result.await() } }

    @Test
    fun `completion runs on the main thread`() {
        val finished = CompletableDeferred<String>()

        SaveStickerViewModel(repository, io).saveSticker(
            bitmap,
            onComplete = { finished.complete(threadName()) },
            onFailure = { finished.complete("failed") },
        )

        assertEquals(MAIN_THREAD, await(finished))
        coVerify(exactly = 1) { repository.saveSticker(any(), any(), any()) }
    }

    @Test
    fun `the write runs off the main thread`() {
        val writeThread = CompletableDeferred<String>()
        coEvery { repository.saveSticker(any(), any(), any()) } coAnswers {
            writeThread.complete(threadName())
            Unit
        }

        SaveStickerViewModel(repository, io).saveSticker(bitmap, {}, {})

        assertEquals(IO_THREAD, await(writeThread))
    }

    @Test
    fun `a failed write reports failure on the main thread instead of crashing`() {
        val finished = CompletableDeferred<String>()
        coEvery { repository.saveSticker(any(), any(), any()) } throws IllegalStateException("full")

        SaveStickerViewModel(repository, io).saveSticker(
            bitmap,
            onComplete = { finished.complete("completed") },
            onFailure = { finished.complete(threadName()) },
        )

        assertEquals(MAIN_THREAD, await(finished))
    }

    private companion object {
        const val MAIN_THREAD = "test-main"
        const val IO_THREAD = "test-io"
    }
}
