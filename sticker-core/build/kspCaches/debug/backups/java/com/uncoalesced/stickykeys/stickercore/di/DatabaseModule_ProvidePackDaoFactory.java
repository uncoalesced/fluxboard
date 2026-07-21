package com.uncoalesced.stickykeys.stickercore.di;

import com.uncoalesced.stickykeys.stickercore.data.local.StickyKeysDatabase;
import com.uncoalesced.stickykeys.stickercore.data.local.dao.PackDao;
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
public final class DatabaseModule_ProvidePackDaoFactory implements Factory<PackDao> {
  private final Provider<StickyKeysDatabase> databaseProvider;

  private DatabaseModule_ProvidePackDaoFactory(Provider<StickyKeysDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public PackDao get() {
    return providePackDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvidePackDaoFactory create(
      Provider<StickyKeysDatabase> databaseProvider) {
    return new DatabaseModule_ProvidePackDaoFactory(databaseProvider);
  }

  public static PackDao providePackDao(StickyKeysDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.providePackDao(database));
  }
}
