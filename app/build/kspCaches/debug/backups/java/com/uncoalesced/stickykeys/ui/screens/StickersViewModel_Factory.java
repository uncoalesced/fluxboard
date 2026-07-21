package com.uncoalesced.stickykeys.ui.screens;

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
public final class StickersViewModel_Factory implements Factory<StickersViewModel> {
  private final Provider<StickerRepository> repositoryProvider;

  private StickersViewModel_Factory(Provider<StickerRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public StickersViewModel get() {
    return newInstance(repositoryProvider.get());
  }

  public static StickersViewModel_Factory create(Provider<StickerRepository> repositoryProvider) {
    return new StickersViewModel_Factory(repositoryProvider);
  }

  public static StickersViewModel newInstance(StickerRepository repository) {
    return new StickersViewModel(repository);
  }
}
