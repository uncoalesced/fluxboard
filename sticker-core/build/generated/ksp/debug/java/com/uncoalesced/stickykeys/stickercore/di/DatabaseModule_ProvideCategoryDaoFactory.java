package com.uncoalesced.stickykeys.stickercore.di;

import com.uncoalesced.stickykeys.stickercore.data.local.StickyKeysDatabase;
import com.uncoalesced.stickykeys.stickercore.data.local.dao.CategoryDao;
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
public final class DatabaseModule_ProvideCategoryDaoFactory implements Factory<CategoryDao> {
  private final Provider<StickyKeysDatabase> databaseProvider;

  private DatabaseModule_ProvideCategoryDaoFactory(Provider<StickyKeysDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public CategoryDao get() {
    return provideCategoryDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideCategoryDaoFactory create(
      Provider<StickyKeysDatabase> databaseProvider) {
    return new DatabaseModule_ProvideCategoryDaoFactory(databaseProvider);
  }

  public static CategoryDao provideCategoryDao(StickyKeysDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideCategoryDao(database));
  }
}
