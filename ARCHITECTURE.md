# StickyKeys Architecture

This document is a living summary of the actual implementation of StickyKeys, reflecting the technical decisions, module structures, and boundaries established across the 37 phases of development. It replaces the early planning phases and represents the shipped architecture.

## 1. Core Principles
- **100% Local-First**: The core application logic does not require internet access. Predictive text and segmentation operate strictly on-device.
- **Zero Telemetry**: We employ no analytics, ad frameworks, or crash reporters.
- **Unidirectional Data Flow**: State is managed via `StateFlow` in ViewModels, heavily leaning on modern Jetpack Compose paradigms for UI.

## 2. Tech Stack
- **Language**: Kotlin 2.0+
- **UI Framework**: Jetpack Compose (Material 3)
- **Dependency Injection**: Dagger Hilt
- **Persistence**: Room (SQLite) + DataStore for Preferences
- **Concurrency**: Kotlin Coroutines & Flow
- **Networking**: OkHttp & Retrofit (Strictly limited to the LAN/Relay module)
- **Media Processing**: `webp-android`, `gifkt-jvm`, ML Kit Subject Segmentation

## 3. Module Structure

StickyKeys uses a multi-module architecture to strictly enforce domain boundaries and ensure the `keyboard-core` doesn't accidentally depend on UI-heavy `sticker-core` features.

### `:app`
The entry point. Holds the `StickyKeysApplication` (annotated with `@HiltAndroidApp`), main navigation graphs, Settings UI screens (Compose), and the F-Droid packaging metadata.

### `:keyboard-core`
The flagship component. 
- **IME Service**: Subclasses `InputMethodService` and uses a hybrid View/Compose architecture using `AbstractComposeView` to render the keyboard UI within the strict Android window limits.
- **Prediction Engine**: Loads N-gram Tries (unigram and bigram) from binary assets and provides sub-millisecond autocomplete suggestions as the user types.
- **Theming & Layout Engine**: Parses JSON files to dynamically render layout schemas (e.g., QWERTY) and color mappings. Fully reactive; changing a theme updates the active keyboard instantly.
- **Clipboard History**: Intercepts clipboard events (respecting the sensitive content flag `EXTRA_IS_SENSITIVE`) and persists them to a local Room database for quick recall.

### `:sticker-core`
Handles all media and image manipulation.
- **Segmentation**: Uses Google's ML Kit Subject Segmentation to extract subjects from bitmaps.
- **GIF/WebP Pipeline**: Uses `quickie-unbundled` for fast decoding, handles video frame extraction, and encodes to `.webp` or `.gif` using FOSS encoders (`webp-android` and `gifkt-jvm`).
- **Database**: Stores the local sticker library metadata (tags, file paths, favorites) via Room, providing reactive `Flow<List<Sticker>>` streams to the UI.

### `:transfer`
A completely isolated module responsible for device-to-device migration and ephemeral sharing.
- **LAN Discovery**: Uses UDP broadcasting to discover peer devices on the same Wi-Fi network.
- **Pairing & Crypto**: Implements ECDH key exchange to generate symmetric AES-256 keys. No data is transmitted over LAN unencrypted.
- **Relay Fallback**: Connects to our lightweight Python WebSocket relay for one-off sticker sharing via deep links (`https://stickykeys.app/s#...`). This is the *only* component allowed to make outbound Internet calls.

## 4. Notable Architectural Decisions & Trade-offs

1. **ML Kit vs. U-2-Net**: 
   We evaluated both open-source models (U-2-Net) and Google's ML Kit for subject segmentation. We selected ML Kit for its vastly superior performance and smaller on-device footprint. However, because it relies on proprietary Google Play Services binaries, this triggers the `NonFreeDep` flag for strict F-Droid builds.

2. **Compose in `InputMethodService`**:
   Rendering Compose directly in an IME window has historically been difficult due to lifecycle and saved-state registry missing from the IME context. We worked around this by building a custom `ViewTreeLifecycleOwner` and attaching it to the `ComposeView` window token, allowing us to build the entire keyboard in Compose.

3. **ZIP-Based Migration**:
   To transfer the user's data across devices, the `:transfer` module truncates the SQLite Write-Ahead Log (WAL), copies the Room databases and the local sticker image files into a single `.zip`, and encrypts the entire payload using the ECDH-derived AES key before transmitting over socket.

4. **Testing Strategy**:
   The `transfer`, `sticker-core`, and `keyboard-core` modules heavily utilize Robolectric for unit testing Android-dependent logic (like DBs and Crypto). Full UI end-to-end paths (like Settings Toggles) are automated via standard Android instrumented Compose tests. Highly complex, sandbox-restricted paths (like IME switching and Camera intents) are covered by strict manual test scripts (`docs/testing/manual/`).
