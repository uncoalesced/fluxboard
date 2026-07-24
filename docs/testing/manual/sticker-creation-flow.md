# Manual Test Script: Sticker Creation Flow

This script outlines the end-to-end journey for creating a sticker and ensuring it appears in the sticker library. Full automation is impractical here because the system Photo Picker runs in an out-of-process system UI, and the camera feed cannot be reliably mocked across diverse emulator setups.

## Prerequisites
1. Open the StickyKeys app on a device or emulator running Android 8.0 (API 26) or higher.
2. Have at least one photo with a clear subject (e.g., a person or pet) saved in the device Gallery.

## Execution Steps

1. **Launch Picker**
   - Tap the **"+" (Create Sticker)** Floating Action Button on the main Sticker Library screen.
   - *Expected:* The system Photo Picker or Android file chooser opens.

2. **Select Image**
   - Select the pre-saved photo from the gallery.
   - *Expected:* The photo picker closes, and the StickyKeys crop/segmentation UI appears, showing the selected image.

3. **Segmentation (ML Kit)**
   - Allow the ML Kit Segmentation Engine to process the image (should take ~0.5s - 2s depending on device).
   - *Expected:* The background of the image is automatically removed, leaving only the primary subject.

4. **Touch-Up / Save**
   - (Optional) Tap any areas to restore/remove if the touch-up tool is available.
   - Tap the **Save** button.
   - *Expected:* The screen navigates back to the main Sticker Library.

5. **Verify Library**
   - Scroll through the Sticker Library grid.
   - *Expected:* The newly segmented sticker appears in the grid.

6. **Verify Persistence**
   - Force close the app and reopen it.
   - *Expected:* The newly created sticker is still visible in the library grid.
