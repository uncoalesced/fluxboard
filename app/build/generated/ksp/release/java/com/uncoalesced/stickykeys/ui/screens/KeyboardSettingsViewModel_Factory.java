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
public final class KeyboardSettingsViewModel_Factory implements Factory<KeyboardSettingsViewModel> {
  @Override
  public KeyboardSettingsViewModel get() {
    return newInstance();
  }

  public static KeyboardSettingsViewModel_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static KeyboardSettingsViewModel newInstance() {
    return new KeyboardSettingsViewModel();
  }

  private static final class InstanceHolder {
    static final KeyboardSettingsViewModel_Factory INSTANCE = new KeyboardSettingsViewModel_Factory();
  }
}
