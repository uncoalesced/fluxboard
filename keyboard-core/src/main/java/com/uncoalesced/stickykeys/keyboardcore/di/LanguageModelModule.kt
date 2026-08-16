// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.di

import com.uncoalesced.stickykeys.keyboardcore.domain.engine.BigramLanguageModel
import com.uncoalesced.stickykeys.keyboardcore.domain.engine.LanguageModel
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The seam a future on-device model slots into.
 *
 * [PredictionEngine][com.uncoalesced.stickykeys.keyboardcore.domain.engine.PredictionEngine]
 * depends on the [LanguageModel] interface rather than the corpus-derived
 * [BigramLanguageModel] that implements it today, so replacing the context signal is a change
 * to the line below and nothing else. No model is bundled and none is planned for this
 * release -- a real neural one needs a vetted, license-clean weights file, which is a
 * research deliverable rather than a coding one.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LanguageModelModule {
    @Binds
    abstract fun bindLanguageModel(impl: BigramLanguageModel): LanguageModel
}
