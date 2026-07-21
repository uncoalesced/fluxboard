package com.uncoalesced.stickykeys;

import android.app.Activity;
import android.app.Service;
import android.view.View;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.uncoalesced.stickykeys.keyboardcore.ime.StickerIMEViewModel;
import com.uncoalesced.stickykeys.keyboardcore.ime.StickerIMEViewModel_HiltModules;
import com.uncoalesced.stickykeys.keyboardcore.ime.StickerIMEViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.uncoalesced.stickykeys.keyboardcore.ime.StickerIMEViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.uncoalesced.stickykeys.keyboardcore.ime.StickyKeysIME;
import com.uncoalesced.stickykeys.keyboardcore.ime.StickyKeysIME_MembersInjector;
import com.uncoalesced.stickykeys.stickercore.data.file.StickerFileManager;
import com.uncoalesced.stickykeys.stickercore.data.local.StickyKeysDatabase;
import com.uncoalesced.stickykeys.stickercore.data.local.dao.CategoryDao;
import com.uncoalesced.stickykeys.stickercore.data.local.dao.PackDao;
import com.uncoalesced.stickykeys.stickercore.data.local.dao.StickerDao;
import com.uncoalesced.stickykeys.stickercore.data.repository.StickerRepositoryImpl;
import com.uncoalesced.stickykeys.stickercore.di.DatabaseModule_ProvideCategoryDaoFactory;
import com.uncoalesced.stickykeys.stickercore.di.DatabaseModule_ProvidePackDaoFactory;
import com.uncoalesced.stickykeys.stickercore.di.DatabaseModule_ProvideStickerDaoFactory;
import com.uncoalesced.stickykeys.stickercore.di.DatabaseModule_ProvideStickyKeysDatabaseFactory;
import com.uncoalesced.stickykeys.ui.screens.AppSettingsViewModel;
import com.uncoalesced.stickykeys.ui.screens.AppSettingsViewModel_HiltModules;
import com.uncoalesced.stickykeys.ui.screens.AppSettingsViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.uncoalesced.stickykeys.ui.screens.AppSettingsViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.uncoalesced.stickykeys.ui.screens.KeyboardSettingsViewModel;
import com.uncoalesced.stickykeys.ui.screens.KeyboardSettingsViewModel_HiltModules;
import com.uncoalesced.stickykeys.ui.screens.KeyboardSettingsViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.uncoalesced.stickykeys.ui.screens.KeyboardSettingsViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.uncoalesced.stickykeys.ui.screens.StickersViewModel;
import com.uncoalesced.stickykeys.ui.screens.StickersViewModel_HiltModules;
import com.uncoalesced.stickykeys.ui.screens.StickersViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.uncoalesced.stickykeys.ui.screens.StickersViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.uncoalesced.stickykeys.ui.screens.TransferShareViewModel;
import com.uncoalesced.stickykeys.ui.screens.TransferShareViewModel_HiltModules;
import com.uncoalesced.stickykeys.ui.screens.TransferShareViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.uncoalesced.stickykeys.ui.screens.TransferShareViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.uncoalesced.stickykeys.ui.screens.creation.SaveStickerViewModel;
import com.uncoalesced.stickykeys.ui.screens.creation.SaveStickerViewModel_HiltModules;
import com.uncoalesced.stickykeys.ui.screens.creation.SaveStickerViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.uncoalesced.stickykeys.ui.screens.creation.SaveStickerViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.uncoalesced.stickykeys.ui.screens.edit.EditStickerViewModel;
import com.uncoalesced.stickykeys.ui.screens.edit.EditStickerViewModel_HiltModules;
import com.uncoalesced.stickykeys.ui.screens.edit.EditStickerViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.uncoalesced.stickykeys.ui.screens.edit.EditStickerViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.uncoalesced.stickykeys.ui.screens.video.VideoConvertViewModel;
import com.uncoalesced.stickykeys.ui.screens.video.VideoConvertViewModel_HiltModules;
import com.uncoalesced.stickykeys.ui.screens.video.VideoConvertViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.uncoalesced.stickykeys.ui.screens.video.VideoConvertViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import dagger.hilt.android.ActivityRetainedLifecycle;
import dagger.hilt.android.ViewModelLifecycle;
import dagger.hilt.android.internal.builders.ActivityComponentBuilder;
import dagger.hilt.android.internal.builders.ActivityRetainedComponentBuilder;
import dagger.hilt.android.internal.builders.FragmentComponentBuilder;
import dagger.hilt.android.internal.builders.ServiceComponentBuilder;
import dagger.hilt.android.internal.builders.ViewComponentBuilder;
import dagger.hilt.android.internal.builders.ViewModelComponentBuilder;
import dagger.hilt.android.internal.builders.ViewWithFragmentComponentBuilder;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories_InternalFactoryFactory_Factory;
import dagger.hilt.android.internal.managers.ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory;
import dagger.hilt.android.internal.managers.SavedStateHandleHolder;
import dagger.hilt.android.internal.modules.ApplicationContextModule;
import dagger.hilt.android.internal.modules.ApplicationContextModule_ProvideContextFactory;
import dagger.internal.DaggerGenerated;
import dagger.internal.DoubleCheck;
import dagger.internal.LazyClassKeyMap;
import dagger.internal.MapBuilder;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

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
public final class DaggerStickyKeysApplication_HiltComponents_SingletonC {
  private DaggerStickyKeysApplication_HiltComponents_SingletonC() {
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private ApplicationContextModule applicationContextModule;

    private Builder() {
    }

    public Builder applicationContextModule(ApplicationContextModule applicationContextModule) {
      this.applicationContextModule = Preconditions.checkNotNull(applicationContextModule);
      return this;
    }

    public StickyKeysApplication_HiltComponents.SingletonC build() {
      Preconditions.checkBuilderRequirement(applicationContextModule, ApplicationContextModule.class);
      return new SingletonCImpl(applicationContextModule);
    }
  }

  private static final class ActivityRetainedCBuilder implements StickyKeysApplication_HiltComponents.ActivityRetainedC.Builder {
    private final SingletonCImpl singletonCImpl;

    private SavedStateHandleHolder savedStateHandleHolder;

    private ActivityRetainedCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ActivityRetainedCBuilder savedStateHandleHolder(
        SavedStateHandleHolder savedStateHandleHolder) {
      this.savedStateHandleHolder = Preconditions.checkNotNull(savedStateHandleHolder);
      return this;
    }

    @Override
    public StickyKeysApplication_HiltComponents.ActivityRetainedC build() {
      Preconditions.checkBuilderRequirement(savedStateHandleHolder, SavedStateHandleHolder.class);
      return new ActivityRetainedCImpl(singletonCImpl, savedStateHandleHolder);
    }
  }

  private static final class ActivityCBuilder implements StickyKeysApplication_HiltComponents.ActivityC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private Activity activity;

    private ActivityCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ActivityCBuilder activity(Activity activity) {
      this.activity = Preconditions.checkNotNull(activity);
      return this;
    }

    @Override
    public StickyKeysApplication_HiltComponents.ActivityC build() {
      Preconditions.checkBuilderRequirement(activity, Activity.class);
      return new ActivityCImpl(singletonCImpl, activityRetainedCImpl, activity);
    }
  }

  private static final class FragmentCBuilder implements StickyKeysApplication_HiltComponents.FragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private Fragment fragment;

    private FragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public FragmentCBuilder fragment(Fragment fragment) {
      this.fragment = Preconditions.checkNotNull(fragment);
      return this;
    }

    @Override
    public StickyKeysApplication_HiltComponents.FragmentC build() {
      Preconditions.checkBuilderRequirement(fragment, Fragment.class);
      return new FragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragment);
    }
  }

  private static final class ViewWithFragmentCBuilder implements StickyKeysApplication_HiltComponents.ViewWithFragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private View view;

    private ViewWithFragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;
    }

    @Override
    public ViewWithFragmentCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public StickyKeysApplication_HiltComponents.ViewWithFragmentC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewWithFragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl, view);
    }
  }

  private static final class ViewCBuilder implements StickyKeysApplication_HiltComponents.ViewC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private View view;

    private ViewCBuilder(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public ViewCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public StickyKeysApplication_HiltComponents.ViewC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, view);
    }
  }

  private static final class ViewModelCBuilder implements StickyKeysApplication_HiltComponents.ViewModelC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private SavedStateHandle savedStateHandle;

    private ViewModelLifecycle viewModelLifecycle;

    private ViewModelCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ViewModelCBuilder savedStateHandle(SavedStateHandle handle) {
      this.savedStateHandle = Preconditions.checkNotNull(handle);
      return this;
    }

    @Override
    public ViewModelCBuilder viewModelLifecycle(ViewModelLifecycle viewModelLifecycle) {
      this.viewModelLifecycle = Preconditions.checkNotNull(viewModelLifecycle);
      return this;
    }

    @Override
    public StickyKeysApplication_HiltComponents.ViewModelC build() {
      Preconditions.checkBuilderRequirement(savedStateHandle, SavedStateHandle.class);
      Preconditions.checkBuilderRequirement(viewModelLifecycle, ViewModelLifecycle.class);
      return new ViewModelCImpl(singletonCImpl, activityRetainedCImpl, savedStateHandle, viewModelLifecycle);
    }
  }

  private static final class ServiceCBuilder implements StickyKeysApplication_HiltComponents.ServiceC.Builder {
    private final SingletonCImpl singletonCImpl;

    private Service service;

    private ServiceCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ServiceCBuilder service(Service service) {
      this.service = Preconditions.checkNotNull(service);
      return this;
    }

    @Override
    public StickyKeysApplication_HiltComponents.ServiceC build() {
      Preconditions.checkBuilderRequirement(service, Service.class);
      return new ServiceCImpl(singletonCImpl, service);
    }
  }

  private static final class ViewWithFragmentCImpl extends StickyKeysApplication_HiltComponents.ViewWithFragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private final ViewWithFragmentCImpl viewWithFragmentCImpl = this;

    ViewWithFragmentCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;


    }
  }

  private static final class FragmentCImpl extends StickyKeysApplication_HiltComponents.FragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl = this;

    FragmentCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl, Fragment fragmentParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return activityCImpl.getHiltInternalFactoryFactory();
    }

    @Override
    public ViewWithFragmentComponentBuilder viewWithFragmentComponentBuilder() {
      return new ViewWithFragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl);
    }
  }

  private static final class ViewCImpl extends StickyKeysApplication_HiltComponents.ViewC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final ViewCImpl viewCImpl = this;

    ViewCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }
  }

  private static final class ActivityCImpl extends StickyKeysApplication_HiltComponents.ActivityC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl = this;

    ActivityCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        Activity activityParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;


    }

    Map keySetMapOfClassOfObjectAndBooleanBuilder() {
      MapBuilder mapBuilder = MapBuilder.<String, Boolean>newMapBuilder(8);
      mapBuilder.put(AppSettingsViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, AppSettingsViewModel_HiltModules.KeyModule.provide());
      mapBuilder.put(EditStickerViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, EditStickerViewModel_HiltModules.KeyModule.provide());
      mapBuilder.put(KeyboardSettingsViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, KeyboardSettingsViewModel_HiltModules.KeyModule.provide());
      mapBuilder.put(SaveStickerViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, SaveStickerViewModel_HiltModules.KeyModule.provide());
      mapBuilder.put(StickerIMEViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, StickerIMEViewModel_HiltModules.KeyModule.provide());
      mapBuilder.put(StickersViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, StickersViewModel_HiltModules.KeyModule.provide());
      mapBuilder.put(TransferShareViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, TransferShareViewModel_HiltModules.KeyModule.provide());
      mapBuilder.put(VideoConvertViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, VideoConvertViewModel_HiltModules.KeyModule.provide());
      return mapBuilder.build();
    }

    @Override
    public void injectMainActivity(MainActivity arg0) {
    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return DefaultViewModelFactories_InternalFactoryFactory_Factory.newInstance(getViewModelKeys(), new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl));
    }

    @Override
    public Map<Class<?>, Boolean> getViewModelKeys() {
      return LazyClassKeyMap.<Boolean>of(keySetMapOfClassOfObjectAndBooleanBuilder());
    }

    @Override
    public ViewModelComponentBuilder getViewModelComponentBuilder() {
      return new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public FragmentComponentBuilder fragmentComponentBuilder() {
      return new FragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    @Override
    public ViewComponentBuilder viewComponentBuilder() {
      return new ViewCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }
  }

  private static final class ViewModelCImpl extends StickyKeysApplication_HiltComponents.ViewModelC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ViewModelCImpl viewModelCImpl = this;

    Provider<AppSettingsViewModel> appSettingsViewModelProvider;

    Provider<EditStickerViewModel> editStickerViewModelProvider;

    Provider<KeyboardSettingsViewModel> keyboardSettingsViewModelProvider;

    Provider<SaveStickerViewModel> saveStickerViewModelProvider;

    Provider<StickerIMEViewModel> stickerIMEViewModelProvider;

    Provider<StickersViewModel> stickersViewModelProvider;

    Provider<TransferShareViewModel> transferShareViewModelProvider;

    Provider<VideoConvertViewModel> videoConvertViewModelProvider;

    ViewModelCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        SavedStateHandle savedStateHandleParam, ViewModelLifecycle viewModelLifecycleParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;

      initialize(savedStateHandleParam, viewModelLifecycleParam);

    }

    Map hiltViewModelMapMapOfClassOfObjectAndProviderOfViewModelBuilder() {
      MapBuilder mapBuilder = MapBuilder.<String, javax.inject.Provider<ViewModel>>newMapBuilder(8);
      mapBuilder.put(AppSettingsViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (appSettingsViewModelProvider)));
      mapBuilder.put(EditStickerViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (editStickerViewModelProvider)));
      mapBuilder.put(KeyboardSettingsViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (keyboardSettingsViewModelProvider)));
      mapBuilder.put(SaveStickerViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (saveStickerViewModelProvider)));
      mapBuilder.put(StickerIMEViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (stickerIMEViewModelProvider)));
      mapBuilder.put(StickersViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (stickersViewModelProvider)));
      mapBuilder.put(TransferShareViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (transferShareViewModelProvider)));
      mapBuilder.put(VideoConvertViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (videoConvertViewModelProvider)));
      return mapBuilder.build();
    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandle savedStateHandleParam,
        final ViewModelLifecycle viewModelLifecycleParam) {
      this.appSettingsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 0);
      this.editStickerViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 1);
      this.keyboardSettingsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 2);
      this.saveStickerViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 3);
      this.stickerIMEViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 4);
      this.stickersViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 5);
      this.transferShareViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 6);
      this.videoConvertViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 7);
    }

    @Override
    public Map<Class<?>, javax.inject.Provider<ViewModel>> getHiltViewModelMap() {
      return LazyClassKeyMap.<javax.inject.Provider<ViewModel>>of(hiltViewModelMapMapOfClassOfObjectAndProviderOfViewModelBuilder());
    }

    @Override
    public Map<Class<?>, Object> getHiltViewModelAssistedMap() {
      return Collections.<Class<?>, Object>emptyMap();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final ViewModelCImpl viewModelCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          ViewModelCImpl viewModelCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.viewModelCImpl = viewModelCImpl;
        this.id = id;
      }

      @Override
      @SuppressWarnings("unchecked")
      public T get() {
        switch (id) {
          case 0: // com.uncoalesced.stickykeys.ui.screens.AppSettingsViewModel
          return (T) new AppSettingsViewModel();

          case 1: // com.uncoalesced.stickykeys.ui.screens.edit.EditStickerViewModel
          return (T) new EditStickerViewModel(singletonCImpl.stickerRepositoryImplProvider.get());

          case 2: // com.uncoalesced.stickykeys.ui.screens.KeyboardSettingsViewModel
          return (T) new KeyboardSettingsViewModel();

          case 3: // com.uncoalesced.stickykeys.ui.screens.creation.SaveStickerViewModel
          return (T) new SaveStickerViewModel(singletonCImpl.stickerRepositoryImplProvider.get());

          case 4: // com.uncoalesced.stickykeys.keyboardcore.ime.StickerIMEViewModel
          return (T) new StickerIMEViewModel(singletonCImpl.stickerRepositoryImplProvider.get());

          case 5: // com.uncoalesced.stickykeys.ui.screens.StickersViewModel
          return (T) new StickersViewModel(singletonCImpl.stickerRepositoryImplProvider.get());

          case 6: // com.uncoalesced.stickykeys.ui.screens.TransferShareViewModel
          return (T) new TransferShareViewModel();

          case 7: // com.uncoalesced.stickykeys.ui.screens.video.VideoConvertViewModel
          return (T) new VideoConvertViewModel(singletonCImpl.stickerRepositoryImplProvider.get());

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ActivityRetainedCImpl extends StickyKeysApplication_HiltComponents.ActivityRetainedC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl = this;

    Provider<ActivityRetainedLifecycle> provideActivityRetainedLifecycleProvider;

    ActivityRetainedCImpl(SingletonCImpl singletonCImpl,
        SavedStateHandleHolder savedStateHandleHolderParam) {
      this.singletonCImpl = singletonCImpl;

      initialize(savedStateHandleHolderParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandleHolder savedStateHandleHolderParam) {
      this.provideActivityRetainedLifecycleProvider = DoubleCheck.provider(new SwitchingProvider<ActivityRetainedLifecycle>(singletonCImpl, activityRetainedCImpl, 0));
    }

    @Override
    public ActivityComponentBuilder activityComponentBuilder() {
      return new ActivityCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public ActivityRetainedLifecycle getActivityRetainedLifecycle() {
      return provideActivityRetainedLifecycleProvider.get();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.id = id;
      }

      @Override
      @SuppressWarnings("unchecked")
      public T get() {
        switch (id) {
          case 0: // dagger.hilt.android.ActivityRetainedLifecycle
          return (T) ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory.provideActivityRetainedLifecycle();

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ServiceCImpl extends StickyKeysApplication_HiltComponents.ServiceC {
    private final SingletonCImpl singletonCImpl;

    private final ServiceCImpl serviceCImpl = this;

    ServiceCImpl(SingletonCImpl singletonCImpl, Service serviceParam) {
      this.singletonCImpl = singletonCImpl;


    }

    @Override
    public void injectStickyKeysIME(StickyKeysIME stickyKeysIME) {
      injectStickyKeysIME2(stickyKeysIME);
    }

    private StickyKeysIME injectStickyKeysIME2(StickyKeysIME instance) {
      StickyKeysIME_MembersInjector.injectFileManager(instance, singletonCImpl.stickerFileManagerProvider.get());
      StickyKeysIME_MembersInjector.injectRepository(instance, singletonCImpl.stickerRepositoryImplProvider.get());
      return instance;
    }
  }

  private static final class SingletonCImpl extends StickyKeysApplication_HiltComponents.SingletonC {
    private final ApplicationContextModule applicationContextModule;

    private final SingletonCImpl singletonCImpl = this;

    Provider<StickyKeysDatabase> provideStickyKeysDatabaseProvider;

    Provider<StickerFileManager> stickerFileManagerProvider;

    Provider<StickerRepositoryImpl> stickerRepositoryImplProvider;

    SingletonCImpl(ApplicationContextModule applicationContextModuleParam) {
      this.applicationContextModule = applicationContextModuleParam;
      initialize(applicationContextModuleParam);

    }

    StickerDao stickerDao() {
      return DatabaseModule_ProvideStickerDaoFactory.provideStickerDao(provideStickyKeysDatabaseProvider.get());
    }

    PackDao packDao() {
      return DatabaseModule_ProvidePackDaoFactory.providePackDao(provideStickyKeysDatabaseProvider.get());
    }

    CategoryDao categoryDao() {
      return DatabaseModule_ProvideCategoryDaoFactory.provideCategoryDao(provideStickyKeysDatabaseProvider.get());
    }

    @SuppressWarnings("unchecked")
    private void initialize(final ApplicationContextModule applicationContextModuleParam) {
      this.provideStickyKeysDatabaseProvider = DoubleCheck.provider(new SwitchingProvider<StickyKeysDatabase>(singletonCImpl, 1));
      this.stickerFileManagerProvider = DoubleCheck.provider(new SwitchingProvider<StickerFileManager>(singletonCImpl, 2));
      this.stickerRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<StickerRepositoryImpl>(singletonCImpl, 0));
    }

    @Override
    public void injectStickyKeysApplication(StickyKeysApplication arg0) {
    }

    @Override
    public Set<Boolean> getDisableFragmentGetContextFix() {
      return Collections.<Boolean>emptySet();
    }

    @Override
    public ActivityRetainedComponentBuilder retainedComponentBuilder() {
      return new ActivityRetainedCBuilder(singletonCImpl);
    }

    @Override
    public ServiceComponentBuilder serviceComponentBuilder() {
      return new ServiceCBuilder(singletonCImpl);
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.id = id;
      }

      @Override
      @SuppressWarnings("unchecked")
      public T get() {
        switch (id) {
          case 0: // com.uncoalesced.stickykeys.stickercore.data.repository.StickerRepositoryImpl
          return (T) new StickerRepositoryImpl(singletonCImpl.stickerDao(), singletonCImpl.packDao(), singletonCImpl.categoryDao(), singletonCImpl.stickerFileManagerProvider.get());

          case 1: // com.uncoalesced.stickykeys.stickercore.data.local.StickyKeysDatabase
          return (T) DatabaseModule_ProvideStickyKeysDatabaseFactory.provideStickyKeysDatabase(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 2: // com.uncoalesced.stickykeys.stickercore.data.file.StickerFileManager
          return (T) new StickerFileManager(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          default: throw new AssertionError(id);
        }
      }
    }
  }
}
