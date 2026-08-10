# FluxBoard

![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)
![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84.svg)
![Language](https://img.shields.io/badge/language-Kotlin-7F52FF.svg)
![Telemetry](https://img.shields.io/badge/telemetry-zero-success.svg)
![Status](https://img.shields.io/badge/status-beta-orange.svg)

A customisable Android keyboard with a sticker library attached, built so that
nothing you type leaves your phone.

The keyboard is the main thing. You can re-theme it, re-layout it, resize it,
glide-type on it, and it learns your words in a dictionary that lives on your
device and nowhere else. The sticker side lets you cut stickers out of
screenshots and photos, keep them as editable objects rather than one-off
exports, and send them from the keyboard.

It is free, MIT-licensed, and has no accounts, no ads, and no analytics.

## Current state: beta

`v0.1.5-BETA`, versionCode 6. It works and it is being used daily, but it is a
beta and it will have rough edges. If something looks wrong, please
[open an issue](https://github.com/uncoalesced/fluxboard/issues/new/choose) —
small reports are welcome and often the useful ones.

Download the APK from the
[latest release](https://github.com/uncoalesced/fluxboard/releases/latest).

## What works

**The keyboard**

- Glide typing. Swipe across the letters instead of tapping each one. On by
  default, and you can turn it off.
- On-device predictive text and autocorrect, with a personal dictionary that
  learns the words you actually use.
- Themes you can edit: colours, key shapes, fonts, background images, and how
  much of a haze sits behind the keys.
- Layouts you can remap, plus an optional number row and corner symbols on every
  key.
- Three independent size sliders: overall height, key size within its cell, and
  the gap below the bottom row.
- A clipboard history that is only ever cleared by you.
- Caps lock by double-tapping shift, double-space for a full stop, a text-editing
  panel with a proper cursor pad, and media controls for whatever is playing.
- Haptics, with a strength slider that actually maps to something sensible across
  different phones.

**Privacy behaviour that is part of the keyboard rather than a setting**

- Password and PIN fields are detected from the field's own input type, not from
  a flag the host app has to remember to set. In those fields the keyboard stops
  learning, stops suggesting, stops autocorrecting, stops decoding glides, and
  stops capturing clipboard content. A numeric PIN field gets a digits-only pad.
- Auto-capitalize is suppressed on credential fields too, because a password
  silently starting with a capital is worse in a masked field than in a visible
  one.
- There is also a manual privacy switch on the keyboard for fields Android gives
  us no signal about. It stays on until you turn it off.

**Stickers**

- Import from a screenshot, a photo, or the system photo picker, then crop and
  erase the background by hand.
- Edit a sticker again later instead of rebuilding it.
- Categories and a favourites tab.
- Turn a trimmed video clip into a GIF or animated WebP.
- Send stickers straight from the keyboard's emoji and sticker picker.

## What does not work yet

Listing this because a README that only describes the good parts is not much use
to someone deciding whether to install it.

- **Automatic subject cutout is not in this version.** Extraction is crop plus
  the manual eraser. The earlier automatic path depended on ML Kit, which pulls
  Google Play Services and a telemetry transport with it, so it was removed
  rather than reconfigured. It will only come back behind a genuinely open
  on-device model shipped in this repo.
- **Sharing a sticker by link only works on your own network.** Device-to-device
  migration over Wi-Fi with QR pairing works. The relay that would let a link
  reach someone off your network exists as code and is deployed nowhere, so
  treat that feature as absent.
- **Voice input, translate, and grammar check are placeholders.** They are
  visible in the keyboard's toolbar and say so when tapped.
- **Only English, one QWERTY-family layout.** You can remap the letters, but
  there is no second language.
- **Sticker delivery to WhatsApp and Discord is unconfirmed.** The bug that
  caused it (a null MIME type on the `.sticker` extension) is fixed and verified
  at the provider level, but the phone this project is tested on runs LineageOS
  with neither app installed. If you have them, that report would genuinely help.

## Privacy

The short version: the app has no analytics, no crash reporting, no ad SDK, and
no Google Play Services dependency. Your dictionary, clipboard history, stickers,
and themes stay in the app's private storage.

The longer version is in [PRIVACY.md](PRIVACY.md), including which permissions
exist and why, and what is deliberately kept out of cloud backup.

Two of those claims are checked by CI on every build rather than asserted:

- The release APK is unpacked and both its DEX files and R8's own mapping are
  scanned. Any Play Services, ML Kit, Firebase, or `datatransport` class fails
  the build. The check carries a positive control, so it fails loudly if the
  scan itself breaks rather than passing silently.
- The tester usage log is proven absent from release builds as a class, not
  merely switched off by a flag.

## Building it

You need JDK 17 or newer and the Android SDK for API 36. Then:

```bash
git clone https://github.com/uncoalesced/fluxboard.git
cd fluxboard
./gradlew assembleDebug
```

`./gradlew build` runs the same gate CI does: ktlint, the full unit suite across
all four modules, lint, and the release build.

Release builds are signed from a `keystore.properties` at the repo root that
points at a keystore kept outside it. Neither is in the repository. If the file
is absent the release build degrades to unsigned instead of failing, so a fresh
clone and CI both work.

Min SDK 26 (Android 8.0), target SDK 36. Kotlin only inside the app; Python is
used for the offline dictionary, emoji, and icon tooling.

## How it is put together

| Layer | Choice |
|---|---|
| UI | Jetpack Compose, including inside the keyboard itself |
| Architecture | MVVM with unidirectional data flow |
| DI | Hilt, except the IME's own ViewModels, which are built by hand |
| Persistence | Room for anything queryable, files on disk for sticker bytes |
| Concurrency | Coroutines and Flow |
| Install size | Under 100 MB, enforced in CI (currently about 8 MB) |

Four Gradle modules. `app` is the settings and sticker-management UI.
`keyboard-core` is the IME, prediction, theming, and clipboard. `sticker-core` is
the data layer and the image pipeline. `transfer` is device-to-device migration.
`app` depends on the other three, and `keyboard-core` also depends on
`sticker-core` because the keyboard renders the sticker library.

[ARCHITECTURE.md](ARCHITECTURE.md) covers the conventions in more detail.

## Contributing

Please do. [CONTRIBUTING.md](CONTRIBUTING.md) covers the build, the house style,
and the few rules that are not negotiable — chiefly that nothing may add
telemetry or a Play Services dependency, and that every source file carries the
provenance line.

Bug reports from real use are worth more to this project right now than patches.
There are issue templates for bugs, keyboard-specific problems, and feature
ideas.

## Built on other people's work

- [FlorisBoard](https://github.com/florisboard/florisboard) — the main reference
  for how to build an Android IME at all, and for its theming and dictionary
  approach.
- [EweSticker](https://github.com/FredHappyface/Android.EweSticker) — prior art
  for a sticker keyboard and for handling several sticker formats.
- [LocalSend](https://github.com/localsend/localsend) — the model for serverless
  transfer between devices on a network.
- [ZXing](https://github.com/journeyapps/zxing-android-embedded) and
  [qrcode-kotlin](https://github.com/g0dkar/qrcode-kotlin) — QR scanning and
  generation for device pairing, both Apache-2.0 and neither needing Play
  Services.
- SCOWL, for the word lists that filter the dictionary corpus. Without that
  filter the raw corpus ships misspellings as high-frequency words, and
  autocorrect then refuses to fix the most common typos.

## Licence

[MIT](LICENSE). Copyright 2026 uncoalesced and ZapCannonYT. See
[AUTHORS.md](AUTHORS.md).

Use of this app is also covered by [TERMS.md](TERMS.md), which mostly says what
the MIT licence already says: there is no warranty, and you are responsible for
what you type.
