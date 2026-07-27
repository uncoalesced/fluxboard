# Producing a Tester APK (interim, debug-signed)

This is the interim path for handing a working APK to testers directly, separate
from the F-Droid build recipe. It uses the **debug** build type, which Android
signs automatically with the auto-generated debug keystore -- so there is no
keystore to create or protect yet.

## The one command

```
./gradlew assembleDebug
```

Output APK:

```
app/build/outputs/apk/debug/app-debug.apk
```

Install on a connected device with `adb install -r app/build/outputs/apk/debug/app-debug.apk`,
or just send testers the `.apk` file to sideload.

For the artifact that actually ships to testers, build it clean so nothing stale
is carried over from an incremental run:

```
.\gradlew.bat clean assembleDebug
```

## What a fresh installer needs to know

- **Android 8.0 (API 26) or newer.** Anything below the minSdk 26 floor will
  refuse to install.
- **"Install unknown apps" must be enabled** for whichever app delivers the file
  (browser, Files, Drive, messaging). Android blocks sideloading otherwise. The
  prompt appears on first install attempt; grant it to that specific app.
- **The debug signature is expected.** The APK is signed with the local debug
  keystore, not a release key. It installs fine, but it will not upgrade in place
  over any future release-signed build -- that will need an uninstall first.
- **FluxBoard must be enabled and selected before the keyboard does anything.**
  Open the app, go to the Keyboard tab, and use the setup card at the top: it
  opens system keyboard settings for step 1 and the keyboard picker for step 2,
  and disappears once both are done. Nothing else in the app depends on this;
  stickers and editing work without it.
- **Link sharing is LAN-only right now.** The relay server exists in `relay/` but
  is not deployed anywhere, so two devices can only exchange stickers while on
  the same Wi-Fi network. Off-network sharing will fail to connect. Device-to-
  device migration (QR pairing) is LAN-only by design and is unaffected.
- **No automatic background removal in this build.** Sticker cutouts are crop
  plus the manual eraser. Automatic subject segmentation was removed along with
  ML Kit; see `docs/privacy-audit.md`.
- **Camera permission is only for QR pairing.** It is requested at the point of
  use on the scan screen, never at launch.

If the Gradle launcher picks up an old JDK, prefix with the JDK 17+ path, e.g.
`JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug`.

## Why debug, not release, for now

`app/build.gradle.kts` has a `release { }` build type (minify + resource
shrinking + ProGuard) but **no `signingConfig`**. `./gradlew assembleRelease`
therefore produces an **unsigned** APK that cannot be installed directly -- fine
for the F-Droid pipeline (F-Droid signs its own builds) but not for handing to a
tester.

The debug build is:
- automatically signed with the local debug keystore (`~/.android/debug.keystore`),
- installable on any device with "install unknown apps" enabled,
- functionally complete for testing every feature.

Its only differences from a release build: it is `debuggable`, not minified, and
carries the debug signature -- none of which block feature testing.

## DECISION NEEDED before a real release build

Shipping a signed **release** APK/AAB (for updates testers can upgrade in place,
or for any store) requires a release signing key. **Creating that keystore is a
decision, not an automatic step**: whoever owns it must back it up, because
losing it means no future update can ever be signed with the same identity, which
breaks in-place upgrades for every user. This has intentionally NOT been done
automatically. When you are ready, decide on and create the keystore, then a
`signingConfigs { }` + `release.signingConfig` block gets wired into
`app/build.gradle.kts`.
