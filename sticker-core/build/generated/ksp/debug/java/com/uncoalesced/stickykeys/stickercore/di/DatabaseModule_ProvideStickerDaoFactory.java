package com.uncoalesced.stickykeys.stickercore.di;

import com.uncoalesced.stickykeys.stickercore.data.local.StickyKeysDatabase;
import com.uncoalesced.stickykeys.stickercore.data.local.dao.StickerDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
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
public final class DatabaseModule_ProvideStickerDaoFactory implements Factory<StickerDao> {
  private final Provider<StickyKeysDatabase> databaseProvider;

  private DatabaseModule_ProvideStickerDaoFactory(Provider<StickyKeysDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public StickerDao get() {
    return provideStickerDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideStickerDaoFactory create(
      Provider<StickyKeysDatabase> databaseProvider) {
    return new DatabaseModule_ProvideStickerDaoFactory(databaseProvider);
  }

  public static StickerDao provideStickerDao(StickyKeysDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideStickerDao(database));
  }
}
