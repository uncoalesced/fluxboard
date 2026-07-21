package com.uncoalesced.stickykeys.ui.screens.creation;

import com.uncoalesced.stickykeys.stickercore.domain.repository.StickerRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class SaveStickerViewModel_Factory implements Factory<SaveStickerViewModel> {
  private final Provider<StickerRepository> repositoryProvider;

  private SaveStickerViewModel_Factory(Provider<StickerRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public SaveStickerViewModel get() {
    return newInstance(repositoryProvider.get());
  }

  public static SaveStickerViewModel_Factory create(
      Provider<StickerRepository> repositoryProvider) {
    return new SaveStickerViewModel_Factory(repositoryProvider);
  }

  public static SaveStickerViewModel newInstance(StickerRepository repository) {
    return new SaveStickerViewModel(repository);
  }
}
