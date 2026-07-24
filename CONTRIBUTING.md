# Contributing to StickyKeys

Thank you for your interest in improving StickyKeys! Since this project is built entirely by the community, your contributions to the codebase, themes, layouts, and documentation are what keep it alive.

## 1. Development Setup

To build and test the project locally:
1. Ensure you have JDK 17 and the Android SDK (Platform 36) installed.
2. Clone the repository and open it in Android Studio.
3. The project uses the standard Android Gradle Plugin (AGP). Build the debug variant via `./gradlew assembleDebug`.
4. Please ensure your code adheres to standard Kotlin style guidelines. We enforce `ktlint`; run `./gradlew ktlintCheck` before opening a Pull Request.

## 2. Adding a New Keyboard Theme

Themes in StickyKeys are defined in JSON format. The Theme Engine reads these files and maps them to Jetpack Compose colors at runtime.

To propose a new theme:
1. Locate the default themes in `keyboard-core/src/main/assets/themes/`.
2. Create a new file (e.g., `theme_dracula.json`).
3. Follow the schema:
```json
{
  "id": "theme_dracula",
  "name": "Dracula Dark",
  "isDark": true,
  "colors": {
    "background": "#282a36",
    "keyBackground": "#44475a",
    "keyText": "#f8f8f2",
    "accent": "#bd93f9",
    "suggestionBackground": "#282a36",
    "suggestionText": "#50fa7b"
  }
}
```
4. Update the `ThemeManager` default asset lists if necessary, and open a PR with screenshots of the theme in action!

## 3. Adding a New Keyboard Layout

Layouts dictate the arrangement of keys and are completely decoupled from the rendering logic.

To propose a new layout (e.g., a specific language or Dvorak):
1. Navigate to `keyboard-core/src/main/assets/layouts/`.
2. Create your layout file (e.g., `layout_dvorak.json`).
3. Follow the schema to define the rows and keys:
```json
{
  "id": "layout_dvorak",
  "name": "English (US) - Dvorak",
  "languageCode": "en",
  "rows": [
    {
      "keys": [
        { "code": 39, "label": "'", "shiftedLabel": "\"" },
        { "code": 44, "label": ",", "shiftedLabel": "<" },
        // ... additional keys ...
      ]
    }
  ]
}
```
4. Ensure the `code` maps correctly to standard Android KeyEvents or Unicode points. Open a PR for review.

## 4. Expanding the Predictive Dictionary

Our predictive text engine uses compressed N-gram tries. 
- The unigram and bigram data files are generated offline via Python scripts.
- To improve predictions, do **not** edit the binary files directly. Instead, propose changes to the offline corpus or generation scripts located in `keyboard-core/tools/dictionary_pipeline/`.

## 5. Architectural Changes & Large Features

If you are planning a massive refactor or a brand new feature (e.g., adding an internet-reliant API):
- **Stop and read `ARCHITECTURE.md` first.**
- Open an Issue to discuss your idea before writing any code. StickyKeys has strict constraints on network usage and privacy (Zero Telemetry, 100% On-Device by default). We will reject features that violate these core principles.
