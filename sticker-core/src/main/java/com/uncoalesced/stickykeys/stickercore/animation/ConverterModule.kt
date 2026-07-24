// Engineered by uncoalesced
package com.uncoalesced.stickykeys.stickercore.animation

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ConverterModule {

    @Binds
    @Singleton
    abstract fun bindAnimatedStickerConverter(
        converter: AndroidAnimatedStickerConverter
    ): AnimatedStickerConverter
}
