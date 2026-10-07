<!-- Engineered by uncoalesced -->

# FluxBoard

<div align="center">
  <img src="assets/logos/flux_logo_beta_transparent.png" alt="FluxBoard" width="400">

  <h3>The keyboard that keeps what you type on your phone.</h3>

  <p>
    Glide typing, autocorrect that learns you, themes you can actually edit,<br>
    and a sticker studio built right in. Zero telemetry. Zero accounts. Zero cost.
  </p>

  <a href="https://github.com/uncoalesced/fluxboard/releases/latest"><strong>Download the latest APK</strong></a>
  &nbsp;·&nbsp;
  <a href="https://github.com/uncoalesced/fluxboard/issues/new/choose">Report a bug</a>
  &nbsp;·&nbsp;
  <a href="docs/PRIVACY.md">Read the privacy policy</a>
</div>

<br>

![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)
![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84.svg)
![Language](https://img.shields.io/badge/language-Kotlin-7F52FF.svg)
![Telemetry](https://img.shields.io/badge/telemetry-zero-success.svg)
![Status](https://img.shields.io/badge/status-beta-orange.svg)

---

Your keyboard sees everything. Every message, every search, every half-typed
thought you deleted before sending. Most keyboards ship that somewhere.

FluxBoard doesn't. It has no analytics, no crash reporter, no ad SDK and no
Google Play Services. The dictionary it builds from your typing lives in the
app's private storage and goes nowhere else. And it's still a keyboard you'll
want to use: fast, good-looking, and yours to reshape.

## Why people switch

**It learns you, and only you.** Predictions and autocorrect run entirely on the
device. Corrections are weighted by where the keys actually sit, so "vall" becomes
"call" (the key your thumb clipped) rather than whatever word is most common.

**Glide typing that copes with real thumbs.** Swipe through the letters. Doubled
letters, rounded corners and fast swipes are all handled, and when two words
share a path (to/too) the other reading is one tap away in the suggestion strip.

**It's yours to design.** Edit every colour, the key shapes, fonts, a background
image, and how much haze sits behind the keys. Remap the layout. Add a number row.
Set the height, key size and bottom gap on three separate sliders.

**It protects passwords without being asked.** FluxBoard reads the field type
itself instead of trusting the app to flag it. In a password or PIN field it stops
learning, suggesting, autocorrecting, glide-decoding and clipboard capture, and a
numeric PIN field gets a clean digits-only pad. For everything else there's a
privacy switch on the keyboard that stays on until you turn it off.

**Stickers, made from your own stuff.** Cut a sticker out of a screenshot or
photo, edit it again later, sort it into categories, and send it straight from the
keyboard. Trim a video clip and it becomes a GIF or animated WebP.

## Everything else in the box

- Clipboard history that only you can clear
- Double-tap shift for caps lock, double-space for a full stop
- Space-bar cursor scrubbing and a text-editing panel with a proper cursor pad
- Hold backspace and swipe left to delete whole words
- Long-press any key for its symbols, with currency on `4` and superscripts on
  the digits
- Emoji picker with search, recents and an emoticons tab
- Media controls for whatever's playing
- Haptics with a strength slider that feels the same on every phone
- Local typing stats (keys and words typed), kept on the device
- Phone-to-phone migration over Wi-Fi, paired with a QR code

## Privacy you can verify

Plenty of apps promise privacy. FluxBoard's two biggest claims are enforced by the
build, so a regression can't ship quietly:

- Every release APK gets unpacked, and both its DEX files and R8's mapping are
  scanned. Any Play Services, ML Kit, Firebase or `datatransport` class fails the
  build. The check includes a positive control, so if the scan itself breaks it
  fails loudly instead of passing.
- The usage log that tester builds keep is proven to be *absent from release
  builds as a class*, not just switched off by a flag.

Your personal dictionary and clipboard history are also kept out of Android's
cloud backup on purpose. The full picture, including every permission and why it
exists, is in [PRIVACY.md](docs/PRIVACY.md).

## Status: beta, and honest about it

`v0.1.7.5-BETA`. It's in daily use and it holds up, but it's a beta. Here's what
isn't there yet, so you know before you install:

- **No automatic background removal.** Stickers are cut out with crop and a
  manual eraser. The automatic version needed ML Kit, which drags in Play
  Services and a telemetry transport, so it was taken out. It'll come back only
  with an open, on-device model that ships in this repo.
- **Link sharing only works on your own network.** The relay that would carry a
  link further is written but not deployed anywhere.
- **Voice input is a placeholder** and says so when you tap it.
- **English only**, on one QWERTY-family layout (fully remappable).
- **WhatsApp and Discord sticker delivery is unconfirmed.** The underlying bug is
  fixed and verified at the provider level, but the test phone runs LineageOS
  without either app. If you have them, a report would help a lot.

Found something rough? [Open an issue](https://github.com/uncoalesced/fluxboard/issues/new/choose).
Small reports are often the most useful ones.

## Build it yourself

You need JDK 17+ and the Android SDK for API 36.

```bash
git clone https://github.com/uncoalesced/fluxboard.git
cd fluxboard
./gradlew assembleDebug
```

`./gradlew build` runs the same gate as CI: ktlint, the full unit suite across all
four modules, lint, and the release build with its privacy checks.

Release signing reads a `keystore.properties` at the repo root that points at a
keystore kept outside it. Neither is committed. Without the file, the release
build comes out unsigned instead of failing, so fresh clones and CI just work.

Min SDK 26 (Android 8.0), target SDK 36. The app is pure Kotlin; Python is used
only for the offline dictionary, emoji and icon tooling.

## Under the hood

| Layer | Choice |
|---|---|
| UI | Jetpack Compose, including inside the keyboard itself |
| Architecture | MVVM with unidirectional data flow |
| DI | Hilt (the IME's own ViewModels are built by hand) |
| Persistence | Room for anything queryable, files on disk for sticker bytes |
| Concurrency | Coroutines and Flow |
| Size | About 8 MB installed, with a 100 MB ceiling enforced in CI |

Four Gradle modules: `app` (settings and sticker management), `keyboard-core`
(the IME, prediction, theming, clipboard), `sticker-core` (data layer and image
pipeline) and `transfer` (device-to-device migration). `keyboard-core` depends on
`sticker-core` because the keyboard renders your sticker library.

More in [ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Contributing

Yes, please. [CONTRIBUTING.md](docs/CONTRIBUTING.md) covers the build, the house
style and the few non-negotiables. The big one: nothing may add telemetry or a
Play Services dependency.

Right now, bug reports from real use are worth more than patches. There are
issue templates for general bugs, keyboard problems and feature ideas.

## Standing on shoulders

- [FlorisBoard](https://github.com/florisboard/florisboard), the main reference
  for building an Android IME, and for its theming and dictionary approach.
- [EweSticker](https://github.com/FredHappyface/Android.EweSticker), prior art
  for a sticker keyboard and multi-format sticker handling.
- [LocalSend](https://github.com/localsend/localsend), the model for serverless
  transfer between devices on a network.
- [ZXing](https://github.com/journeyapps/zxing-android-embedded) and
  [qrcode-kotlin](https://github.com/g0dkar/qrcode-kotlin) for QR pairing. Both
  are Apache-2.0 and neither needs Play Services.
- SCOWL, whose word lists filter the dictionary corpus. Without them the raw
  corpus ships misspellings as common words, and autocorrect refuses to fix the
  typos you make most.

## Licence

[MIT](LICENSE). Copyright 2026 uncoalesced and ZapCannonYT. See
[AUTHORS.md](docs/AUTHORS.md).

Using the app is also covered by [TERMS.md](docs/TERMS.md), which mostly repeats
the MIT licence: no warranty, and you're responsible for what you type.
