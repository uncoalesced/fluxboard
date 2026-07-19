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
public final class AppSettingsViewModel_Factory implements Factory<AppSettingsViewModel> {
  @Override
  public AppSettingsViewModel get() {
    return newInstance();
  }

  public static AppSettingsViewModel_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static AppSettingsViewModel newInstance() {
    return new AppSettingsViewModel();
  }

  private static final class InstanceHolder {
    static final AppSettingsViewModel_Factory INSTANCE = new AppSettingsViewModel_Factory();
  }
}
