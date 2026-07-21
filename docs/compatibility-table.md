# Platform Compatibility Table

This document outlines the exact formatting and sizing requirements for exporting stickers to major messaging platforms. It informs the size optimization targets (Phase 14) and the export/sharing architecture.

## Platform Requirements

| Platform | Format (Static) | Format (Animated) | Dimensions | Max File Size | Pack Size | Integration Method |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **WhatsApp** | WebP | WebP | 512x512 px | 100 KB (Static)<br>500 KB (Animated) | 3–30 | Content Provider (preferred) or general Commit Content / Share Intent. |
| **Telegram** | WebP | TGS (Lottie)<br>WebM (VP9) | 512x(≤512) px | 512 KB (Static)<br>64 KB (TGS)<br>256 KB (WebM) | ~50-120+ | Telegram Bot API (`@Stickers` for sets). Fallback: Commit Content for standard attachments. |
| **Signal** | PNG / WebP | APNG | 512x512 px | 300 KiB | up to 200 | Encrypted Protobuf upload to Signal CDN (for packs). Fallback: Commit Content for standard attachments. |

## Notes for Exporters

### WhatsApp
- **Strict WebP Only:** Both static and animated stickers must use the WebP format.
- **Margins:** A 16px transparent margin and 8px white stroke are recommended for visibility on dark/light backgrounds.
- **Pack Requirements:** Needs a 96x96 px (≤50 KB) tray icon. Packs cannot mix static and animated stickers.

### Telegram
- **Animated Format Mismatch:** Telegram does **not** support Animated WebP or GIF natively for sticker packs. It strictly requires `.tgs` (Bodymovin/Lottie JSON) or `.webm` (VP9 encoded, no audio, max 30fps).
- **Workaround:** For our generated GIFs/WebPs, we must rely on Android's generic `Commit Content` (IME pasting) or Share Intent. Telegram will send these as standard image/video attachments rather than native "stickers" unless we build a WebM/TGS exporter.

### Signal
- **Animated Format Mismatch:** Signal strictly requires **APNG** for animated stickers. GIFs and Animated WebPs are **not supported** for animated sticker packs.
- **Integration Complexity:** Creating a Signal sticker pack requires generating a 64-character encryption key and uploading an encrypted Protocol Buffers manifest to their CDN. For local/offline use, we rely on `Commit Content` to paste as an attachment.

## Implications for Phase 14 (Size Optimization)
- **Target Size Ceiling:** To support all platforms natively, static stickers must stay under **100 KB** (WhatsApp's limit is the strictest). Animated stickers should target **< 250 KB** (Telegram WebM limit) or at least **< 500 KB** (WhatsApp limit).
- **Target Resolution:** **512x512 px** is the universal standard.
- **Target Format:** Since Telegram needs WebM/TGS and Signal needs APNG, native sticker pack integration for animated stickers across *all* platforms is not possible with just WebP or GIF. Our primary export format will be **GIF / Animated WebP** for general IME pasting, and we'll target WhatsApp's WebP spec for native packs.
