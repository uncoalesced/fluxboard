// Engineered by uncoalesced
package com.uncoalesced.stickykeys.stickercore.di

import com.uncoalesced.stickykeys.stickercore.data.repository.StickerRepositoryImpl
import com.uncoalesced.stickykeys.stickercore.domain.repository.StickerRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindStickerRepository(
        impl: StickerRepositoryImpl
    ): StickerRepository
}
