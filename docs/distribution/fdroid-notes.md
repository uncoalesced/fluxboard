# F-Droid Packaging Notes

## Anti-Features Declaration

**None required.** FluxBoard has no proprietary dependencies.

Earlier revisions of this document declared the `NonFreeDep` anti-feature for two
Play-Services-delivered ML Kit libraries. Both have since been removed:

- `com.google.android.gms:play-services-mlkit-subject-segmentation` -- removed.
  Automatic subject segmentation is not in v1; sticker extraction is crop plus the
  manual eraser. It will only return behind a genuinely open on-device model.
- `io.github.g00fy2.quickie` (barcode/QR scanning) -- removed. Both its `-bundled`
  and `-unbundled` variants resolve to `play-services-mlkit-barcode-scanning`.
  QR scanning now uses `com.journeyapps:zxing-android-embedded` (Apache-2.0), and
  QR generation uses `io.github.g0dkar:qrcode-kotlin-android` (Apache-2.0).

Verified on the built artifact, not just the build files: the release merged
manifest contains zero `com.google.*` components, and every `classes*.dex` in the
release APK reports `datatransport=0 mlkit=0 gms=0`. See `docs/privacy-audit.md`
section 2a for the exact commands and output.

The app therefore builds and runs with no Google Play Services on the device,
which also means it works fully on de-Googled ROMs (GrapheneOS, LineageOS without
gapps) with no degraded feature path.

## Play Store Considerations
If a Google Play Store listing is pursued in the future, the following additional steps are strictly required:
1. **Privacy Policy**: A hosted privacy policy URL must be provided in the Play Console because this app requests the `CAMERA` permission.
2. **Data Safety Form**: Must explicitly declare that no user data is collected or shared off-device.
3. **Graphic Assets**: A 1024x500 Feature Graphic and at least 2 screenshots are required.
