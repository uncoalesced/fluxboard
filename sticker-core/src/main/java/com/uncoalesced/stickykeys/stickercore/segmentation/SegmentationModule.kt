// Engineered by uncoalesced
package com.uncoalesced.stickykeys.stickercore.segmentation

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SegmentationModule {

    @Binds
    @Singleton
    abstract fun bindSegmentationEngine(
        engine: MlKitSegmentationEngine
    ): SegmentationEngine
}
