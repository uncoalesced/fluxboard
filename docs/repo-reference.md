# Repo & Library Reference

Everything below was found by researching what already exists for each
subsystem of this app. For each entry: what it is, its license, and how
it's meant to be used here — as a direct dependency, a fork base, or just a
study reference. Phase files link back to this doc instead of repeating it.

This list is a starting point, not the final word — several phases assign
follow-up research (deeper compatibility checks, license re-verification
before shipping, checking for newer alternatives) because libraries and
repos change after this doc was written.

## Keyboard / IME

- **FlorisBoard** (`florisboard/florisboard`, Apache-2.0, Kotlin) — the
  single most relevant reference in this entire project. A mature,
  privacy-respecting, fully open-source Android IME: working
  `InputMethodService` core, multiple layouts, a custom theme engine
  ("Snygg", JSON or Kotlin DSL) for full color/font/size theming, and —
  critically — its own NLP/prediction core (`FlorisLanguageModel`, a binary
  dictionary format called "Flictionary" supporting n-grams up to order 8
  with edit-distance fuzzy matching) plus a companion tool
  (`florisboard/dictionary-tools`) for turning raw word-frequency corpora
  into that binary format. Use this as primary study material for Phases
  16–23, and seriously consider adapting its NLP core and dictionary
  pipeline directly rather than rebuilding from scratch — Phase 18 covers
  the licensing/adaptation evaluation. Worth knowing: FlorisBoard's own
  first attempt at the NLP core was pure Kotlin/JVM and hit real
  performance and memory problems before being reworked — don't repeat that
  mistake blind.
- **AnySoftKeyboard** and **OpenBoard** — older, Java-based open-source
  Android keyboards. Lower relevance than FlorisBoard given this project is
  Kotlin-only, but worth a skim in Phase 18/8 for how they structured
  dictionary/autocorrect if FlorisBoard's approach doesn't fit.
- **EweSticker** (fork of `woosticker`, itself inspired by `uSticker`; MIT,
  Kotlin, F-Droid distributed) — an existing open-source sticker-keyboard
  app. Directly relevant prior art for the sticker-sending side of the
  keyboard: it's a working example of an IME whose primary content is
  stickers/media rather than typing, including multi-format handling
  (`image/gif`, `image/webp`, `image/png`, several video MIME types) with a
  static-image fallback for apps that don't support the native format.
  Review before Phase 16.

## Sticker extraction / segmentation

- **ML Kit Subject Segmentation** (Google, proprietary API surface but
  free to call, on-device) — isolates a subject from its background
  specifically for use cases like sticker creation. The model is delivered
  as an unbundled library via Google Play services rather than shipped in
  the APK, so it barely affects the size budget — but it requires Play
  Services on the device. Evaluate in Phase 8.
- **U2NetP** (part of the U-2-Net project, open source) — a lightweight
  (~4.5 MB) salient-object-detection model, TFLite-convertible, runs fully
  offline with zero Google dependency. The fully-open alternative to ML Kit
  for this feature. Evaluate alongside ML Kit in Phase 8; Phase 30 will
  need whichever is chosen accounted for in the size budget either way.

## Video → GIF / WebP

- **gif.kt** (`shaksternano/gifkt`, Maven Central) — a pure Kotlin
  Multiplatform GIF decode/encode library: memory-efficient decoding,
  lossy and lossless encoding, handles minimum frame duration correctly.
  Preferred over pulling in FFmpeg for this feature — FFmpeg's native
  binaries are commonly tens of MB per ABI, which threatens the 100 MB
  budget on their own. Use Android's built-in `MediaCodec`/
  `MediaMetadataRetriever` for frame extraction from the source video, and
  gif.kt (or Android's native animated WebP encoding path) for the output
  encode. Covered in Phase 13.
- **Android's native animated WebP support** — no external library needed;
  Android has supported WebP natively since 4.0, with animated support in
  later versions. Roughly 60%+ smaller than GIF at comparable quality. Use
  as the default internal/storage format, exporting to GIF only where a
  target app or platform requires it. Decision + fallback logic in
  Phase 13/14.

## Sticker/GIF platform compatibility

- **`WhatsApp/stickers`** (official WhatsApp repo, BSD) — the authoritative
  source for WhatsApp's actual sticker requirements: 512×512 px, WebP only,
  transparent background, ≤100 KB per static sticker / ≤500 KB per animated
  sticker, 3–30 stickers per pack, delivered via WhatsApp's own
  ContentProvider integration (distinct from the generic Commit Content /
  IME route — supporting both is worth doing, since the ContentProvider
  route gets the polished native "sticker" treatment specifically inside
  WhatsApp). Use as the reference implementation for Phase 15's
  WhatsApp-specific research; Telegram (Bot API sticker/`.tgs` spec) and
  Signal (sticker-pack manifest format) need the equivalent research done
  and documented in that phase, since specifics weren't pinned down here.

## Device pairing & transfer

- **LocalSend** (Apache-2.0) — the direct model for LAN-based
  device-to-device migration: discovers devices on the local network and
  transfers directly between them over an encrypted connection, no cloud
  relay, no account, no internet round-trip. Reference for Phase 26.
- **Magic Wormhole** (`magic-wormhole`, MIT, Python reference
  implementation — the *protocol*, not the Kotlin code, is what's
  reusable here) — the right model for ephemeral, link-based sharing with
  someone who isn't on your LAN: a lightweight rendezvous/"mailbox" server
  brokers a key exchange (PAKE/SPAKE2) and connection setup only; the two
  clients then attempt a direct connection, falling back to a dumb "Transit
  Relay" that just glues two encrypted TCP streams together and never sees
  the actual content. One-time codes, nothing persisted, nothing logged.
  This is the pattern for Phase 27/28 — the relay we'd need to run is
  intentionally as dumb and blind as Magic Wormhole's, which is what keeps
  it compatible with zero telemetry despite being real infrastructure.
- **ZXing / `zxing-android-embedded`** (Apache-2.0) — QR generation and
  scanning for the device-pairing trust flow (Phase 25), following the same
  pattern already used for pairing in this team's other project (Impart).
  Note: `zxing-android-embedded` is in maintenance mode (no further
  updates) — still fine to use, just don't expect upstream fixes; confirm
  in Phase 25 whether that's still acceptable or whether a maintained fork
  exists by then.

## Clipboard & haptics

- **Android `ClipboardManager` + a Room-backed history table** — no strong
  single open-source reference for this specific combination; the pattern
  is straightforward enough (listen for clipboard changes, persist each
  entry via the same Room approach as Phase 4) that it's lower research
  priority than the other subsystems here. Worth a look at Gboard's and
  FlorisBoard's clipboard-history UX (not their code, both are effectively
  closed or not yet feature-complete here) purely for interaction-design
  reference — pin, delete-one, clear-all.
- **`ClipDescription.EXTRA_IS_SENSITIVE`** and Android's newer OS-level
  sensitive-content detection in the clipboard preview — check current
  behavior across the min-SDK-26-to-latest range in Phase 24, since this
  API surface has changed across Android versions and the oldest supported
  devices won't have the newest detection.
- **`VibrationEffect`** (API 26+, matching this project's min SDK exactly)
  — `createOneShot(duration, amplitude)` gives amplitude control without
  needing a third-party haptics library. `HapticFeedbackConstants` (e.g.
  `KEYBOARD_TAP`) is the simpler View-level alternative for basic key-press
  feedback. No external dependency needed for Phase 25.

## Distribution

- **F-Droid** — the natural distribution channel for a zero-telemetry,
  fully open-source app; several of the repos above (FlorisBoard, EweSticker)
  are already F-Droid-distributed and can serve as templates for the
  metadata/build reproducibility requirements. Covered in Phase 34.
