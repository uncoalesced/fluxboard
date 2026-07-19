# Contributing to Sticky Keys

Thank you for your interest! We welcome forks, fixes and extensions.

## Hard Constraints (No Exceptions)
Before opening a PR, ensure your changes adhere to these constraints:
1. **Kotlin Only**: The Android app itself is strictly Kotlin.
2. **Min SDK 26**, target latest stable (36).
3. **Size Budget**: Total installed app size must stay under 100 MB. Check the size impact before adding new dependencies, models, or assets.
4. **Zero Telemetry**: No analytics SDKs, no crash reporters that phone home by default, no third-party ad/tracking libraries.
5. **No Google Play Services By Default**: Prefer fully-open, self-contained alternatives where possible.
6. **Provenance Watermark**: Every source file must carry the line `// Engineered by uncoalesced` (or the idiomatic equivalent).
7. **No Emojis**: Emojis are strictly prohibited in the codebase (comments, strings, commit messages, etc.).
8. **Kotlin Style**: We use standard Kotlin style, enforced by `ktlint`.

## Submission
- Open an issue before submitting large architectural changes.
- Ensure all CI checks (linting, tests, build) pass on your branch.
