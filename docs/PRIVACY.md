<!-- Engineered by uncoalesced -->

# Privacy policy

Last updated 21 November 2026. Applies to FluxBoard `v1.0.0-testing` and later.

A keyboard sees everything you type. Passwords, one-time codes, messages you
would not repeat to anyone. That is the whole reason this app is built the way it
is, and it is why this document is specific about mechanisms rather than
reassuring about intentions.

## The short answer

FluxBoard collects nothing. There is no analytics SDK, no crash reporter, no
advertising library, no Google Play Services dependency, and no account. Nobody
who works on this project can see what you type, because there is nowhere for it
to go.

## What is stored, and where

All of it lives in the app's private storage on your device. Other apps cannot
read it, and it is deleted when you uninstall.

| What | Where | Notes |
|---|---|---|
| Personal dictionary and bigrams | `keyboard_database` (Room v3: `PersonalWordEntity`, `PersonalBigramEntity`) | Words and word pairs you have typed, and how often, subject to 14-day smooth exponential decay and OOV typo quarantine. Built as you type. |
| BloomType spatial glide profile | `files/bloom_profile.bin` | On-device key centroid offsets adapted from your glide strokes, checked with a SHA-256 digest. |
| Adaptive tap hitbox profile | `files/bloom_tap_profile.bin` | On-device `SmartKeyHitbox` 2D tap centroids and anisotropic variances, checked with a SHA-256 digest. |
| Unified learned memory vault | `files/bloom_user_memory.bin` | On-device `BloomMemoryVault` unified snapshot storing learned unigrams, bigrams, glide offsets, tap centroids and anisotropic variances, and local adaptive autocorrect calibration (`BloomAdaptiveThreshold`), checked with a SHA-256 digest. |
| Clipboard history | `keyboard_database` | Only cleared when you clear it. See below for what never gets captured. |
| Stickers | `files/stickers/` | The image bytes. |
| Sticker index, categories, favourites | `stickykeys_db` | Metadata only; the bytes stay on disk. |
| Themes, layouts, background images | `files/themes/`, `files/layouts/`, `files/theme_backgrounds/` | Including any you make. |
| Settings | `shared_prefs/` | Sizes, toggles, which theme and layout are active. |
| Recently used emoji | `shared_prefs/` | A short ordered list, rewritten as you use it. |

None of this is transmitted anywhere.

## Controlling or clearing what is learned

Under **Keyboard Settings > Typing**:

- **BloomType adaptive learning** turns on-device spatial key target adaptation on or
  off. Turning it off stops updating your glide geometry offsets and tap hitbox
  calibration.
- **Personalized learning** stops recording new words and phrase pairs while
  typing.

Under **Keyboard Settings > Privacy**:

- **Private mode** switches on the manual privacy lock (also reachable from the
  keyboard toolbar), disabling learning, bigram recording, hitbox calibration,
  adaptive threshold updates, context-weighted hit-testing, and clipboard capture.
- **Clear learned data** purges learned words and bigrams immediately from both the
  in-memory prediction caches and `keyboard_database` (`PersonalWordEntity` and
  `PersonalBigramEntity`), atomically deletes all three `.bin` stores
  (`files/bloom_profile.bin`, `files/bloom_tap_profile.bin`, and
  `files/bloom_user_memory.bin`) via `BloomStateFiles` even if `PredictionEngine`
  has not started in that process, and resets local adaptive calibration
  (`BloomAdaptiveThreshold`, `BloomSpatialProfile`, and `SmartKeyHitbox`).
  Uninstalling the app removes all files in private storage.

When you immediately tap backspace or an undo chip to revert an autocorrection or
glide commit, `BloomSpatialProfile`, `BloomAdaptiveThreshold`, and
`PersonalLearningEngine` roll back the spatial offset, adaptive calibration, and
learned word/bigram updates from that committed word.

## When the keyboard stops learning

The dictionary only gets better if it learns. There are cases where learning
would be a liability, so it stops. These are behaviours in the code, not
preferences you have to find:

- **Password, PIN, and credential fields, detected from the field's own input
  type.** Not from `IME_FLAG_NO_PERSONALIZED_LEARNING`, which the app you are
  typing into has to set and which Android's own text fields do not set for
  passwords. All three text password variations are covered, including
  visible-password fields, plus numeric PINs and credential inputs.
- **Any field where the host app does set `IME_FLAG_NO_PERSONALIZED_LEARNING`**,
  such as a private browsing tab.
- **While you have manual Private Mode on**, from the keyboard's toolbar or
  Keyboard Settings, which stays on until you turn it off.

In all three states, the keyboard strictly disables learning, bigram recording,
hitbox calibration, adaptive threshold updates, context-weighted hit-testing, and
clipboard capture. In password, PIN, and credential fields and in manual Private
Mode, it also stops suggesting words, stops autocorrecting, and stops decoding
glide strokes. Suppressing suggestions matters as much as suppressing learning: the
suggestion strip draws completions of the word you are typing above the keyboard,
where anyone looking at your screen can read them.

Auto-capitalize is switched off on password and PIN fields as well. In a masked
field you cannot see that the first character is not what you pressed.

There was a real defect here before v0.1.4: an alphanumeric password went down
the ordinary letter path, ran a dictionary lookup on every keystroke, and the
first space afterwards wrote it to the on-disk dictionary. It is fixed, and the
history is in [CHANGELOG.md](CHANGELOG.md) rather than quietly omitted.

## The clipboard

Clipboard history persists until you clear it. That is a deliberate choice, not
an oversight, because a history that expires on a timer is not much of a history.

It is also a real exposure if you lose your phone, so two things never enter it:
anything Android has marked as sensitive content, and anything copied while the
keyboard is in one of the privacy states above.

## Network access

The app declares `INTERNET` and `ACCESS_NETWORK_STATE`. Nothing about typing uses
them.

They exist for device-to-device transfer: moving your library to another phone of
yours over the same Wi-Fi network, paired with a QR code, with no server in the
middle.

There is also code for an off-network relay for sharing a single sticker by link.
It is not deployed anywhere and the feature does not work in this build. When it
does, it will get its own section here describing exactly what the relay can and
cannot see, and it will stay opt-in per share.

Nothing else in the app opens a socket. Not the keyboard, not prediction, not
theming, not sticker creation, not GIF conversion.

## Permissions, and why each one exists

| Permission | Why | When it is asked for |
|---|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Device-to-device transfer on your own network | Not a prompted permission |
| `VIBRATE` | Keyboard haptics | Not a prompted permission |
| `READ_MEDIA_IMAGES` (Android 13+) | Reading a screenshot or photo you chose to import | When you tap Extract Screenshot or import |
| `READ_MEDIA_VISUAL_USER_SELECTED` (Android 14+) | Lets you grant access to specific photos instead of all of them | Same prompt |
| `READ_EXTERNAL_STORAGE` (Android 12 and below only, capped at API 32) | The older equivalent of the above | When you tap Extract Screenshot or import |
| `CAMERA` | Scanning the pairing QR code during device transfer. Declared by the ZXing scanning library rather than by this app directly. | When you start a device pairing |
| `com.uncoalesced.stickykeys.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Internal signature-level permission generated by AndroidX Core so runtime broadcast receivers stay private to this app | Not a prompted permission |

No other `<uses-permission>` entries appear in the merged application manifest.

Media access is requested at the button, never at launch. Granting access to only
some photos is treated as access, not as a refusal.

Video import uses Android's photo picker, which needs no permission at all.

## Backup

Android's automatic backup is enabled, but scoped. The scoping is the mechanism,
so it is worth being precise: both rule files are include-only, and once a scope
contains any include, everything not listed is already excluded.

Neither `keyboard_database` nor the three `.bin` learning stores appear in
either list. That is what keeps your personal dictionary, bigrams, spatial and
threshold calibration, and clipboard history off Google's servers.

Sticker bytes and the sticker index are left out of cloud backup together. The
25 MB cloud quota does not degrade gracefully, and an index restored without its
bytes would present as a library of broken thumbnails. They travel on
device-to-device transfer instead.

Cloud backup is also refused outright on a device with no lock screen, where the
payload would reach Google without device-side encryption. There is no equivalent
setting before Android 12, so on those versions the omissions above are the only
protection, which makes the list matter more there rather than less.

## How you can check any of this

You do not have to take our word for it, and you should not have to.

- The source is all here, MIT-licensed.
- CI unpacks every release APK and fails the build if any Play Services, ML Kit,
  Firebase, or `com.google.android.datatransport` class appears in a DEX file or
  in R8's mapping. The check includes a positive control so that a broken scan
  fails instead of passing quietly.
- CI also proves the tester usage log is absent from release builds as a class,
  not just disabled by a flag. A runtime boolean cannot make a class stop
  existing, so this is checked against the built artifact.
- One part has not been done, and is named here rather than left to be found: no
  runtime traffic capture on a physical device. Everything above was verified by
  reading the source and the dependency tree or by checking the built artifact,
  and none of that is a substitute for watching the running app's network.

## Children

Not directed at children and it collects nothing from anyone, so there is nothing
to delete on request.

## Changes

Material changes will be noted in [CHANGELOG.md](CHANGELOG.md) alongside the
release that makes them, with the date at the top of this file updated. If a
future version ever collects anything, that will be stated plainly and it will be
something you turn on rather than something you discover.

## Contact

Use the [issue tracker](https://github.com/uncoalesced/fluxboard/issues). For
anything that would expose typed input, use GitHub's private vulnerability
reporting instead. [SECURITY.md](SECURITY.md) explains why a public issue about a
keyboard input leak is a working exploit.
