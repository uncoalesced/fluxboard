package com.uncoalesced.stickykeys.stickercore.di;

import android.content.Context;
import com.uncoalesced.stickykeys.stickercore.data.local.StickyKeysDatabase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class DatabaseModule_ProvideStickyKeysDatabaseFactory implements Factory<StickyKeysDatabase> {
  private final Provider<Context> contextProvider;

  private DatabaseModule_ProvideStickyKeysDatabaseFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public StickyKeysDatabase get() {
    return provideStickyKeysDatabase(contextProvider.get());
  }

  public static DatabaseModule_ProvideStickyKeysDatabaseFactory create(
      Provider<Context> contextProvider) {
    return new DatabaseModule_ProvideStickyKeysDatabaseFactory(contextProvider);
  }

  public static StickyKeysDatabase provideStickyKeysDatabase(Context context) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideStickyKeysDatabase(context));
  }
}
