package com.uncoalesced.stickykeys.stickercore.data.repository;

import com.uncoalesced.stickykeys.stickercore.data.file.StickerFileManager;
import com.uncoalesced.stickykeys.stickercore.data.local.dao.CategoryDao;
import com.uncoalesced.stickykeys.stickercore.data.local.dao.PackDao;
import com.uncoalesced.stickykeys.stickercore.data.local.dao.StickerDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class StickerRepositoryImpl_Factory implements Factory<StickerRepositoryImpl> {
  private final Provider<StickerDao> stickerDaoProvider;

  private final Provider<PackDao> packDaoProvider;

  private final Provider<CategoryDao> categoryDaoProvider;

  private final Provider<StickerFileManager> fileManagerProvider;

  private StickerRepositoryImpl_Factory(Provider<StickerDao> stickerDaoProvider,
      Provider<PackDao> packDaoProvider, Provider<CategoryDao> categoryDaoProvider,
      Provider<StickerFileManager> fileManagerProvider) {
    this.stickerDaoProvider = stickerDaoProvider;
    this.packDaoProvider = packDaoProvider;
    this.categoryDaoProvider = categoryDaoProvider;
    this.fileManagerProvider = fileManagerProvider;
  }

  @Override
  public StickerRepositoryImpl get() {
    return newInstance(stickerDaoProvider.get(), packDaoProvider.get(), categoryDaoProvider.get(), fileManagerProvider.get());
  }

  public static StickerRepositoryImpl_Factory create(Provider<StickerDao> stickerDaoProvider,
      Provider<PackDao> packDaoProvider, Provider<CategoryDao> categoryDaoProvider,
      Provider<StickerFileManager> fileManagerProvider) {
    return new StickerRepositoryImpl_Factory(stickerDaoProvider, packDaoProvider, categoryDaoProvider, fileManagerProvider);
  }

  public static StickerRepositoryImpl newInstance(StickerDao stickerDao, PackDao packDao,
      CategoryDao categoryDao, StickerFileManager fileManager) {
    return new StickerRepositoryImpl(stickerDao, packDao, categoryDao, fileManager);
  }
}
