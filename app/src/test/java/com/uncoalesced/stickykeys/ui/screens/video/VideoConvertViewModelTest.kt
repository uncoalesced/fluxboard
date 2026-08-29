// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens.video

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.uncoalesced.stickykeys.data.local.AppPreferences
import com.uncoalesced.stickykeys.stickercore.animation.AnimatedStickerConverter
import com.uncoalesced.stickykeys.stickercore.animation.ConversionQuality
import com.uncoalesced.stickykeys.stickercore.domain.model.Category
import com.uncoalesced.stickykeys.stickercore.domain.model.Pack
import com.uncoalesced.stickykeys.stickercore.domain.model.Sticker
import com.uncoalesced.stickykeys.stickercore.domain.repository.StickerRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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

/**
 * Covers the conversion state machine that used to live inside the composable, where a
 * rotation cancelled it silently. The rotation case is the reason this class exists.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoConvertViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var repository: RecordingRepository
    private lateinit var preferences: AppPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        repository = RecordingRepository()
        preferences = AppPreferences(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(converter: AnimatedStickerConverter) =
        VideoConvertViewModel(repository, converter, preferences, dispatcher)

    @Test
    fun `starts idle at HIGH quality`() {
        val state = viewModel(SucceedingConverter()).uiState.value
        assertEquals(VideoConvertUiState.Idle(ConversionQuality.HIGH), state)
    }

    @Test
    fun `selectQuality updates the picker`() {
        val vm = viewModel(SucceedingConverter())
        vm.selectQuality(ConversionQuality.LOW)
        assertEquals(
            ConversionQuality.LOW,
            (vm.uiState.value as VideoConvertUiState.Idle).quality,
        )
    }

    @Test
    fun `conversion reports progress then saves the sticker`() =
        runTest(dispatcher) {
            val converter = SucceedingConverter(progressSteps = listOf(0.25f, 0.5f, 1f))
            val vm = viewModel(converter)

            vm.startConversion(context, "content://video/1", 0L, 1_000L)
            advanceUntilIdle()

            assertEquals(VideoConvertUiState.Done, vm.uiState.value)
            assertEquals(1, repository.saved.size)
            // Real `AppPreferences`, untouched, so this is the shipped default arriving on
            // the saved file rather than a value the test chose. It is the only place that
            // proves the export-format preference reaches the conversion at all -- the
            // setting is otherwise a picker writing to a key nothing visibly reads.
            assertEquals(
                "image/gif",
                repository.saved
                    .single()
                    .first.mimeType,
            )
        }

    @Test
    fun `the selected quality reaches the converter`() =
        runTest(dispatcher) {
            val converter = SucceedingConverter()
            val vm = viewModel(converter)
            vm.selectQuality(ConversionQuality.LOW)

            vm.startConversion(context, "content://video/1", 0L, 1_000L)
            advanceUntilIdle()

            assertEquals(ConversionQuality.LOW, converter.seenQuality)
        }

    @Test
    fun `a converter failure surfaces as Error rather than a silent stall`() =
        runTest(dispatcher) {
            val vm = viewModel(FailingConverter("encoder exploded"))

            vm.startConversion(context, "content://video/1", 0L, 1_000L)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue("expected Error, got $state", state is VideoConvertUiState.Error)
            assertEquals("encoder exploded", (state as VideoConvertUiState.Error).message)
            assertTrue("nothing should be saved on failure", repository.saved.isEmpty())
        }

    @Test
    fun `retry returns to the picker after a failure`() =
        runTest(dispatcher) {
            val vm = viewModel(FailingConverter("boom"))
            vm.startConversion(context, "content://video/1", 0L, 1_000L)
            advanceUntilIdle()

            vm.retry()

            assertTrue(vm.uiState.value is VideoConvertUiState.Idle)
        }

    @Test
    fun `a second start cannot run over an in-flight conversion`() =
        runTest(dispatcher) {
            // The protection is the state machine, not a job handle: once the state leaves
            // Idle, startConversion has nothing to read a quality from and returns.
            val converter = BlockingConverter()
            val vm = viewModel(converter)

            vm.startConversion(context, "content://video/1", 0L, 1_000L)
            advanceUntilIdle()
            vm.startConversion(context, "content://video/1", 0L, 1_000L)
            advanceUntilIdle()

            assertEquals(1, converter.invocations)

            converter.gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, repository.saved.size)
        }

    @Test
    fun `conversion survives the composable going away, as a rotation would`() =
        runTest(dispatcher) {
            // The regression this guards: the encode used to live in a LaunchedEffect, so a
            // configuration change cancelled it mid-progress and reset the screen to Idle.
            // Nothing here touches the ViewModel between start and finish -- exactly what a
            // rotation does, since the ViewModel is retained while the composition is not.
            val converter = BlockingConverter()
            val vm = viewModel(converter)

            vm.startConversion(context, "content://video/1", 0L, 1_000L)
            advanceUntilIdle()
            assertTrue(vm.uiState.value is VideoConvertUiState.Converting)

            converter.gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(VideoConvertUiState.Done, vm.uiState.value)
            assertEquals(1, repository.saved.size)
        }
}

private class SucceedingConverter(
    private val progressSteps: List<Float> = listOf(1f),
) : AnimatedStickerConverter {
    var seenQuality: ConversionQuality? = null

    override suspend fun convertVideoToAnimatedSticker(
        context: Context,
        videoUri: Uri,
        startMs: Long,
        endMs: Long,
        targetFormat: String,
        quality: ConversionQuality,
        onProgress: (Float) -> Unit,
    ): Result<ByteArray> {
        seenQuality = quality
        progressSteps.forEach(onProgress)
        return Result.success(byteArrayOf(1, 2, 3))
    }
}

private class FailingConverter(
    private val message: String,
) : AnimatedStickerConverter {
    override suspend fun convertVideoToAnimatedSticker(
        context: Context,
        videoUri: Uri,
        startMs: Long,
        endMs: Long,
        targetFormat: String,
        quality: ConversionQuality,
        onProgress: (Float) -> Unit,
    ): Result<ByteArray> = Result.failure(IllegalStateException(message))
}

/** Holds the conversion open until [gate] completes, standing in for a slow encode. */
private class BlockingConverter : AnimatedStickerConverter {
    val gate = CompletableDeferred<Unit>()
    var invocations = 0

    override suspend fun convertVideoToAnimatedSticker(
        context: Context,
        videoUri: Uri,
        startMs: Long,
        endMs: Long,
        targetFormat: String,
        quality: ConversionQuality,
        onProgress: (Float) -> Unit,
    ): Result<ByteArray> {
        invocations++
        onProgress(0.5f)
        gate.await()
        return Result.success(byteArrayOf(1))
    }
}

private class RecordingRepository : StickerRepository {
    val saved = mutableListOf<Triple<Sticker, ByteArray, ByteArray>>()

    override suspend fun saveSticker(
        sticker: Sticker,
        bytes: ByteArray,
        thumbnailBytes: ByteArray,
    ) {
        saved.add(Triple(sticker, bytes, thumbnailBytes))
    }

    override fun getAllStickers(): Flow<List<Sticker>> = flowOf(emptyList())

    override fun getStickersByPack(packId: String): Flow<List<Sticker>> = flowOf(emptyList())

    override fun getStickersByCategory(categoryId: String): Flow<List<Sticker>> =
        flowOf(emptyList())

    override fun getFavouriteStickers(): Flow<List<Sticker>> = flowOf(emptyList())

    override suspend fun getStickerById(id: String): Sticker? = null

    override suspend fun updateStickerData(
        sticker: Sticker,
        bytes: ByteArray,
        thumbnailBytes: ByteArray,
    ) = Unit

    override suspend fun updateStickerMetadata(sticker: Sticker) = Unit

    override suspend fun deleteSticker(id: String) = Unit

    override suspend fun toggleFavourite(id: String) = Unit

    override fun getAllPacks(): Flow<List<Pack>> = flowOf(emptyList())

    override suspend fun savePack(pack: Pack) = Unit

    override suspend fun deletePack(id: String) = Unit

    override fun getAllCategories(): Flow<List<Category>> = flowOf(emptyList())

    override suspend fun saveCategory(category: Category) = Unit

    override suspend fun deleteCategory(id: String) = Unit
}
