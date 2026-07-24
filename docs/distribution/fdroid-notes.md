# F-Droid Packaging Notes

## Anti-Features Declaration
To comply with F-Droid's inclusion criteria while retaining our core functionality, this app requires the `NonFreeDep` anti-feature declaration during its F-Droid build recipe creation. There are **two** Play-Services-delivered ML Kit dependencies, both of which trigger the flag:

### 1. Subject segmentation (sticker extraction)
**Dependency:** `com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1`
**Used by:** `sticker-core` -- `MlKitSegmentationEngine`, the on-device automatic sticker cutout (Phase 10).
**Reason:** Proprietary, closed-source Google library; the segmentation model is delivered by Google Play Services.

### 2. Barcode/QR scanning (device pairing)
**Dependency:** `io.github.g00fy2.quickie:quickie-unbundled:1.12.0`
**Used by:** `transfer` / `DevicePairingScreen` -- scanning the pairing QR code (Phase 27).
**Reason:** The `-unbundled` Quickie variant depends transitively on
`com.google.android.gms:play-services-mlkit-barcode-scanning`, whose barcode
model is delivered by Google Play Services. It is therefore a non-free
dependency in the same category as the segmentation model above.

**De-Googled-device impact:** On devices without Play Services (GrapheneOS,
LineageOS without gapps) neither feature works out of the box. Segmentation
already degrades gracefully to the manual eraser. QR *generation* (sender side,
`qrcode-kotlin`, fully open) still works; only QR *scanning* (receiver side) is
affected -- a future option is to swap `quickie-unbundled` for `quickie-bundled`
(bundles the model, no Play Services, larger APK -- weigh against the 100 MB
budget) or add a manual pairing-code entry fallback.

*Note for F-Droid maintainers: We are targeting repos that accept NonFreeDep apps, such as IzzyOnDroid.*

## Play Store Considerations
If a Google Play Store listing is pursued in the future, the following additional steps are strictly required:
1. **Privacy Policy**: A hosted privacy policy URL must be provided in the Play Console because this app requests the `CAMERA` permission.
2. **Data Safety Form**: Must explicitly declare that no user data is collected or shared off-device.
3. **Graphic Assets**: A 1024x500 Feature Graphic and at least 2 screenshots are required.
