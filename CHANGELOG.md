# Changelog

All notable changes to this project during its initial 37-phase development lifecycle will be documented in this file.

## [v0.1.0-alpha] - Initial Release

### Foundation (Phases 1-4)
- **Added:** Scaffolding and multi-module Gradle structure (`app`, `sticker-core`, `keyboard-core`, `transfer`).
- **Added:** Base Jetpack Compose and MVVM + Hilt architecture.
- **Added:** Design System & Theming Tokens (Material 3 baseline).
- **Added:** Local Data Model using Room, with content-addressed file storage.

### Sticker Core & Extraction (Phases 5-11)
- **Added:** Manual Sticker Creation Flow (Crop, background erase, filters, text overlay).
- **Added:** Sticker Editing Suite (Overwrite-based workflow).
- **Added:** Sticker Organization (Categories, Favourites).
- **Added:** Screenshot & Share-Intent Capture Pipeline (via `ACTION_SEND`).
- **Added:** On-Device Segmentation Integration using ML Kit Subject Segmentation.
- **Added:** Gallery/Photo Picker Import Flow using Android's native Photo Picker.

### Video & GIF Pipeline (Phases 12-15)
- **Added:** Video Import & Trim UI (limiting clips to 10 seconds).
- **Added:** Animated WebP and GIF Conversion Pipeline (using `MediaCodec` and native Android encoders to avoid massive FFmpeg sizes).
- **Added:** Size Optimization tools (palette reduction, resolution ceilings).
- **Security:** Verified format/size compatibility across WhatsApp, Telegram, and Signal.

### Keyboard Engine (Phases 16-26)
- **Added:** Minimal IME Shell wired to the Commit Content API for pasting image stickers.
- **Added:** Full Typing Keyboard Core in English (using a custom Compose/View hybrid rendering system).
- **Added:** N-gram predictive text engine (100% on-device).
- **Added:** Auto-Capitalize & Auto-Correct Logic with one-tap undo.
- **Added:** Highly flexible JSON-based Keyboard Theming Engine.
- **Added:** Customizable Keyboard Layouts (QWERTY, Dvorak, etc.).
- **Added:** Keyboard Background Customization.
- **Added:** Persistent Clipboard History Manager (cleared only by explicit user action).
- **Added:** Haptics & Vibration feedback using native `VibrationEffect`.
- **Added:** Keyboard & App Settings UI.

### Cross-Device & Transfer (Phases 27-30)
- **Added:** Device Pairing & Trust Establishment via QR Codes and ECDH key exchange.
- **Added:** Secure LAN Device-to-Device Migration (truncating SQLite WAL and packaging in an AES-256 encrypted zip).
- **Added:** Ephemeral Link-Sharing architecture via a stateless WebSocket relay.

### Quality, Release & Documentation (Phases 31-37)
- **Security:** Executed full Privacy & Permissions Audit (zero unauthorized telemetry verified).
- **Optimization:** Assessed App Size Budget against 100 MB target.
- **Accessibility:** Completed Accessibility & Localization Pass.
- **Testing:** Implemented comprehensive Unit Testing (Jacoco 70% threshold) and hybrid UI/Instrumented Testing.
- **Distribution:** Completed F-Droid Packaging preparation (`fastlane` metadata, `NonFreeDep` ML Kit documentation).
- **Documentation:** Authored User Setup guide (`README.md`), Contributor guidelines (`CONTRIBUTING.md`), and Shipped Architecture design (`ARCHITECTURE.md`).
