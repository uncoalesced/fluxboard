package com.uncoalesced.stickykeys.ui.screens.edit;

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
public final class EditStickerViewModel_Factory implements Factory<EditStickerViewModel> {
  private final Provider<StickerRepository> repositoryProvider;

  private EditStickerViewModel_Factory(Provider<StickerRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public EditStickerViewModel get() {
    return newInstance(repositoryProvider.get());
  }

  public static EditStickerViewModel_Factory create(
      Provider<StickerRepository> repositoryProvider) {
    return new EditStickerViewModel_Factory(repositoryProvider);
  }

  public static EditStickerViewModel newInstance(StickerRepository repository) {
    return new EditStickerViewModel(repository);
  }
}
