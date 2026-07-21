# Changelog

## Phase 13: Video-to-GIF / Animated WebP Conversion Pipeline
- Created `AnimatedStickerConverter` interface in `sticker-core` for zero-FFmpeg video animation conversion.
- Implemented `AndroidAnimatedStickerConverter` using native `MediaMetadataRetriever` frame extraction and downsampling (15 FPS, max 512px).
- Defaulted to **Animated WebP** as internal storage format (`image/webp`) for superior compression and alpha channel support.
- Built `VideoConvertScreen.kt` displaying real-time progress indicator during conversion.

## Phase 12: Video Import & Trim UI
- Added "Import Video" action in `StickersLibraryScreen` leveraging privacy-friendly `PickVisualMedia(VideoOnly)` contract.
- Built `VideoTrimScreen.kt` featuring a timeline scrubber, live keyframe frame preview, and a strict **10-second (10,000ms) trim duration ceiling**.
- Registered `trim_video/{uri}` route in `AppNavGraph` for forwarding video sub-ranges to the Phase 13 conversion pipeline.

## Phase 11: Gallery/Photo Picker Import Flow
- Upgraded Photo Picker launcher in `StickersLibraryScreen` to privacy-friendly `PickMultipleVisualMedia(maxItems = 10)` contract.
- Added `batchImportStickers` to `StickersViewModel` for parallel background segmentation and library persistence.
- Added import options dialog giving users a choice between **⚡ Auto-Segment (Batch)** and **✏️ Custom Edit**.

## Phase 10: On-Device Segmentation Integration & Touch-Up UI
- Created `SegmentationEngine` interface to decouple auto-segmentation execution.
- Implemented `MlKitSegmentationEngine` with Play Services availability check and graceful fallback.
- Created `TouchUpScreen.kt` with interactive **Erase** and **Restore** brush modes for refining automatic subject cutouts.
- Connected `CropScreen` to run auto-segmentation on "Next" and pass both original and segmented bitmaps into `TouchUpScreen`.

## Phase 9: Screenshot & Share-Intent Capture Pipeline
- Registered `ACTION_SEND` intent filter in `AndroidManifest.xml` for `image/*` MIME types.
- Extended `MainActivity` to process shared image URIs from external apps (`Intent.EXTRA_STREAM`) and auto-navigate to the creation flow.
- Created `ScreenshotHelper` to query `MediaStore` for recent screenshot files.
- Added "Extract Screenshot" button to `StickersLibraryScreen` allowing one-tap extraction from the latest device screenshot.

## Phase 8: Segmentation Approach Research & Library Evaluation
- Conducted evaluation comparing Google ML Kit Subject Segmentation vs. U2NetP TFLite across a 20-image test dataset.
- Re-verified U2NetP license directly from upstream source repository (`xuebinqin/U-2-Net`): **Apache License 2.0**.
- Published detailed trade-off report in `docs/segmentation-research.md` recommending ML Kit for primary extraction with manual eraser fallback for de-Googled ROMs.

## Phase 7: Sticker Organization — Categories & Favourites
- Updated `StickersViewModel` to reactively filter stickers across "All", "Favourites", and dynamic custom categories using `flatMapLatest`.
- Added scrollable `ScrollableTabRow` to `StickersLibraryScreen` featuring first-class Favourites tab and dynamic Category tabs.
- Added Category creation dialog (`AddCategoryDialog`).
- Added favouriting star gesture and a long-press context menu (`StickerContextMenuDialog`) for assigning categories or deleting stickers directly from the library grid.

## Phase 6: Sticker Editing Suite
- Added `updateStickerData` to `StickerRepository` and `StickerRepositoryImpl` to support overwriting sticker files on disk and updating DB entities without re-inserting.
- Created `FilterScreen.kt` using `android.graphics.ColorMatrix` for real-time Brightness, Contrast, and Saturation adjustments.
- Created `TextOverlayScreen.kt` for typing, sizing, coloring, and positioning text over sticker images.
- Created `EditStickerScreen.kt` and `EditStickerViewModel.kt` uniting Crop, Erase, Filter, and Text tools with explicit Overwrite vs. Save As New options.
- Connected `StickersLibraryScreen` grid items to `edit/{stickerId}` navigation route.
- Implemented `CropScreen` with standard pan/zoom gestures over a fixed crop mask.
- Implemented `EraseScreen` using Compose `Canvas` and `PorterDuff.Mode.CLEAR` to allow manual background erasing.
- Implemented `SaveStickerScreen` to preview and compress final assets into `.webp` format (lossless for full size, lossy for 256x256 thumbnails).
- Integrated `ActivityResultContracts.PickVisualMedia` as the creation entry point in `StickersLibraryScreen`.
- Created `SaveStickerViewModel` to coordinate with `StickerRepository`.

## Phase 4: Local Data Model & Room Schema
- Defined `StickerEntity`, `PackEntity`, and `CategoryEntity` in `sticker-core` using Room.
- Hand-rolled `StickerFileManager` to decouple blob storage from the database.
- Implemented DAOs with non-blocking Flow queries and `Dispatchers.IO` suspend functions to bypass KSP Kotlin 2.0 limitations.
- Built unified `StickerRepository` to coordinate Room DB and file system operations cleanly.

## Phase 3: Design System & Theming Tokens
- Created `keyboard-core` base theme primitives (`Color`, `Typography`, `Spacing`, `Shape`) using plain Kotlin data classes.
- Enabled Compose in `keyboard-core` to utilize Compose `Color`, `Dp`, and `TextStyle`.
- Implemented `StickyKeysTheme` with `ProvidableCompositionLocal` wrappers for seamless Compose integration.
- Designed a vibrant Indigo (`#6366F1`) and Teal (`#2DD4BF`) palette.
- Developed `StyleSheetScreen` in `app` to visually render and test tokens.

## Phase 2: Architecture Foundation
- Successfully resolved build failures related to AGP 9.3.0 and Kotlin 2.0.21.
- Updated Kotlin to 2.0.21 and KSP to 2.0.21-1.0.28 to support built-in Kotlin compilation.
- Migrated from deprecated `kotlinOptions` to the top-level `kotlin { compilerOptions }` block.
- Applied official `org.jetbrains.kotlin.plugin.compose` Compose Compiler plugin.
- Added `local.properties` to specify the Android SDK path.
- Increased Gradle daemon JVM heap size to 2GB in `gradle.properties`.
- Fixed KtLint formatting and minor Lint property file issues.
- Integrated `androidx.hilt:hilt-navigation-compose:1.2.0` to resolve `hiltViewModel()` calls across Compose screens.

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [v0.1.0-ALPHA]

### Added (Phase 1: Project Scaffolding)
- Established multi-module Gradle project structure (`app`, `sticker-core`, `keyboard-core`, `transfer`).
- Created version catalog (`gradle/libs.versions.toml`) targeting Android Gradle Plugin 9.3.0, Kotlin 1.9.23, and Compose.
- Set `compileSdk` and `targetSdk` to 36, and `minSdk` to 26 across all modules.
- Set application ID and base namespace to `com.uncoalesced.stickykeys`.
- Configured `.editorconfig` and added the `ktlint` plugin to enforce standard Kotlin style rules.
- Set up GitHub Actions CI (`ci.yml`) to enforce build, linting, and tests on JDK 17.
- Added `gradle.properties` to easily configure `org.gradle.java.home` for the local Gradle daemon.
- Created `README.md`, `CONTRIBUTING.md`, GitHub issue templates, and included the MIT `LICENSE`.

### Added (Phase 2: Architecture Foundation)
- Upgraded Dagger Hilt to 2.60.1, Compose BOM to 2026.06.00, and added Navigation Compose 2.9.8 per version verification.
- Implemented `StickyKeysApplication` with `@HiltAndroidApp` and updated `AndroidManifest.xml`.
- Created `ARCHITECTURE.md` to formally document MVVM / Unidirectional Data Flow conventions.
- Added base stub for `StickyKeysTheme`.
- Introduced `SharedStates.kt` for shared loading/error UI components.
- Scaffolded 4 top-level MVVM screens (StickersLibrary, KeyboardSettings, TransferShare, AppSettings) with Hilt ViewModels and distinct sealed `UiState` interfaces.
- Established primary Compose Navigation graph (`AppNavGraph`) in `MainActivity` with a bottom navigation bar routing between the top-level screens.
