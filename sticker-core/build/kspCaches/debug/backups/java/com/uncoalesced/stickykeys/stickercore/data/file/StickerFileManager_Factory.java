package com.uncoalesced.stickykeys.stickercore.data.file;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class StickerFileManager_Factory implements Factory<StickerFileManager> {
  private final Provider<Context> contextProvider;

  private StickerFileManager_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public StickerFileManager get() {
    return newInstance(contextProvider.get());
  }

  public static StickerFileManager_Factory create(Provider<Context> contextProvider) {
    return new StickerFileManager_Factory(contextProvider);
  }

  public static StickerFileManager newInstance(Context context) {
    return new StickerFileManager(context);
  }
}
