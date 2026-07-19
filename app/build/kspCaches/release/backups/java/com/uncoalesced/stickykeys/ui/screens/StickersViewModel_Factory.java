package com.uncoalesced.stickykeys.ui.screens;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
  @Override
  public StickersViewModel get() {
    return newInstance();
  }

  public static StickersViewModel_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static StickersViewModel newInstance() {
    return new StickersViewModel();
  }

  private static final class InstanceHolder {
    static final StickersViewModel_Factory INSTANCE = new StickersViewModel_Factory();
  }
}
