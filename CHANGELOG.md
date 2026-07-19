# Changelog

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
