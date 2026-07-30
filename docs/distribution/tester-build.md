# Producing a Tester APK

Testers now get a **signed release** APK. The release signing config exists and
works, so builds upgrade in place instead of forcing an uninstall between
versions. The debug path below is still useful for development, but is no longer
what you hand to a tester.

## The one command

```
.\gradlew.bat clean :app:packageReleaseArtifact
```

Output:

```
app/build/outputs/distributable/FluxBoard-v0.1.1-ALPHA-release.apk
```

`packageReleaseArtifact` wraps `assembleRelease` and copies the APK out under a
name that says what it is -- `app-release.apk` tells a tester nothing. Build it
`clean`: incremental runs cache `compileDebugKotlin` as UP-TO-DATE and have
hidden a broken build here before.

Two checks run automatically on every release build and fail it if they trip:

- `verifyRoomKeepRules` -- catches the R8 stripping that killed the very first
  signed release on launch.
- `verifyNoUsageLoggingInRelease` -- proves the tester usage log is genuinely
  absent from a stable build (see below).

## What's new in v0.1.1-ALPHA

Written for testers -- what you would actually notice, not a changelog.

- **The keyboard opens.** In v0.1.0 selecting FluxBoard did nothing at all. That
  is fixed; this is the headline change.
- **Emoji.** Tapping the smiley key opens a combined picker: your stickers first,
  then the full emoji set in the usual categories. Emoji are drawn by your
  phone's own emoji font, so they look like they do everywhere else.
- **Haptics actually fire**, and the vibration slider is now a sensible 0-100%
  rather than a raw motor value. Zero means off. Every button in the app buzzes,
  not just the keys.
- **Hold the space bar and slide** left or right to move the cursor. It speeds up
  the longer you hold the drag.
- **Hold a key for its corner symbol** -- `q` gives `%`, `a` gives `@`, and so
  on. Hold backspace to delete repeatedly.
- **A number row** above the letters, on by default. Turn it off in Keyboard
  Settings if you would rather have the space.
- **The arrow at the top-left** opens a row of shortcuts: stickers/emoji,
  clipboard, a text-editing panel (cursor keys, select, copy, paste), and a
  keyboard switcher. Translate, grammar check and the mic are placeholders and
  say so when tapped. Opening this row makes the keyboard taller rather than
  squashing the keys.
- **Much deeper theming.** Key fill, text, borders and a glow effect all take
  their own colour and opacity, and there is a live preview with a text box so
  you can feel the result before committing to it.
- **The dark theme is properly black now**, which also helps on OLED screens.
- **The keyboard no longer overlaps the gesture bar** at the bottom of the
  screen.

### About the usage log

This tester build keeps a small local file of how much you have used the
keyboard: session lengths, key counts, that sort of thing. **No typed text,
words, clipboard contents or app names are recorded**, and nothing is ever sent
anywhere. If you want to share it, there is a "Share usage log" button in
Keyboard Settings that opens the normal Android share sheet -- you choose where
it goes, every time.

This exists **only in tester builds**. A real stable or F-Droid release does not
contain the code at all, which the build verifies rather than assumes.

## What a fresh installer needs to know

- **Android 8.0 (API 26) or newer.** Below the minSdk 26 floor it will refuse to
  install.
- **"Install unknown apps" must be enabled** for whichever app delivers the file
  (browser, Files, Drive, messaging). Android blocks sideloading otherwise. The
  prompt appears on the first install attempt; grant it to that specific app.
- **Upgrading from v0.1.0 works in place** -- same signing key. But a device
  running an old *debug* build must uninstall first: debug and release are signed
  with different keys and Android will refuse the swap.
- **FluxBoard must be enabled and selected before the keyboard does anything.**
  Open the app, go to the Keyboard tab, and use the setup card at the top: it
  opens system keyboard settings for step 1 and the keyboard picker for step 2,
  and disappears once both are done. Stickers and editing work without it.
- **Link sharing is LAN-only right now.** The relay server exists in `relay/` but
  is not deployed anywhere, so two devices can only exchange stickers on the same
  Wi-Fi network. Off-network sharing will fail to connect. Device-to-device
  migration (QR pairing) is LAN-only by design and is unaffected.
- **No automatic background removal in this build.** Sticker cutouts are crop
  plus the manual eraser. Automatic subject segmentation was removed along with
  ML Kit; see `docs/privacy-audit.md`.
- **Camera permission is only for QR pairing.** It is requested at the point of
  use on the scan screen, never at launch.

## The debug build, for development

```
.\gradlew.bat clean assembleDebug
```

```
app/build/outputs/apk/debug/app-debug.apk
```

Debug-signed, not minified, `debuggable`, and the only variant that contains the
usage-logging code. Fine for development; use the release artifact for testers.

If the Gradle launcher picks up an old JDK, prefix with the JDK 17+ path, e.g.
`JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug`.

## Signing

`app/build.gradle.kts` reads `keystore.properties` from the repo root, which is
**gitignored** and points at a keystore stored **outside the repo**. When that
file is absent the release build degrades to unsigned rather than failing, so
fresh clones, CI and F-Droid still work -- F-Droid signs its own builds anyway.

Never commit the keystore or its password. Losing either permanently breaks
in-place updates for everyone already on a release build.
