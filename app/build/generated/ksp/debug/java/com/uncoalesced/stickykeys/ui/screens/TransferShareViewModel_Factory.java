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
public final class TransferShareViewModel_Factory implements Factory<TransferShareViewModel> {
  @Override
  public TransferShareViewModel get() {
    return newInstance();
  }

  public static TransferShareViewModel_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static TransferShareViewModel newInstance() {
    return new TransferShareViewModel();
  }

  private static final class InstanceHolder {
    static final TransferShareViewModel_Factory INSTANCE = new TransferShareViewModel_Factory();
  }
}
