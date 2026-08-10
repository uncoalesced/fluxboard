# Privacy policy

Last updated 10 August 2026. Applies to FluxBoard `v0.1.5-BETA` and later.

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
| Personal dictionary | `keyboard_database` | Words you have typed, and how often. Built as you type. |
| Clipboard history | `keyboard_database` | Only cleared when you clear it. See below for what never gets captured. |
| Stickers | `files/stickers/` | The image bytes. |
| Sticker index, categories, favourites | `stickykeys_db` | Metadata only; the bytes stay on disk. |
| Themes, layouts, background images | `files/themes/`, `files/layouts/`, `files/theme_backgrounds/` | Including any you make. |
| Settings | `shared_prefs/` | Sizes, toggles, which theme is active. |
| Recently used emoji | `shared_prefs/` | A short ordered list, rewritten as you use it. |

None of this is transmitted anywhere.

## When the keyboard stops learning

The dictionary only gets better if it learns. There are cases where learning
would be a liability, so it stops. These are behaviours in the code, not
preferences you have to find:

- **Password fields, detected from the field's own input type.** Not from
  `IME_FLAG_NO_PERSONALIZED_LEARNING`, which the app you are typing into has to
  set and which Android's own text fields do not set for passwords. All three
  text password variations are covered, including visible-password fields, plus
  numeric PINs.
- **Any field where the host app does set that flag**, such as a private browsing
  tab.
- **While you have the manual privacy switch on**, which is on the keyboard's
  toolbar and stays on until you turn it off.

In those cases the keyboard also stops suggesting words, stops autocorrecting,
stops decoding glide strokes, and stops capturing anything you copy. Suppressing
suggestions matters as much as suppressing learning: the suggestion strip draws
completions of the word you are typing above the keyboard, where anyone looking
at your screen can read them.

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
| `READ_MEDIA_IMAGES` (Android 13+) | Reading a screenshot or photo you chose to import | When you tap the import button |
| `READ_MEDIA_VISUAL_USER_SELECTED` (Android 14+) | Lets you grant access to specific photos instead of all of them | Same prompt |
| `READ_EXTERNAL_STORAGE` (Android 12 and below only) | The older equivalent of the above | When you tap the import button |
| `CAMERA` | Scanning the pairing QR code during device transfer. Declared by the ZXing scanning library rather than by this app directly. | When you start a device pairing |

Media access is requested at the button, never at launch. Granting access to only
some photos is treated as access, not as a refusal.

Video import uses Android's photo picker, which needs no permission at all.

## Backup

Android's automatic backup is enabled, but scoped. The scoping is the mechanism,
so it is worth being precise: both rule files are include-only, and once a scope
contains any include, everything not listed is already excluded.

`keyboard_database` appears in neither list. That is what keeps your personal
dictionary and clipboard history off Google's servers.

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
- [docs/privacy-audit.md](docs/privacy-audit.md) records what was verified by
  reading the source and the dependency tree, and is honest about the one part
  that has not been done: a runtime traffic capture on a physical device. The
  static and artifact-level checks are not a substitute for it, and it is not
  claimed as complete.

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
