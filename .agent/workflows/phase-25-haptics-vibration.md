---
description: "Phase 25: Haptics & Vibration Feedback"
---

# Phase 25 — Haptics & Vibration Feedback

**Owner:** Rahul
**Type:** Implementation (light — wiring feedback into existing actions)
**Depends on:** Phase 17, Phase 16

## Goal

Haptic feedback throughout the keyboard and sticker-sending actions —
wiring, not new architecture, since the actions it attaches to already
exist by this point.

## Tasks

1. Key-press haptics: `View.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)`
   for the simple case, or `VibrationEffect.createOneShot(duration, amplitude)`
   (API 26+, matching this project's min SDK exactly — no external library
   needed) where finer control over intensity is wanted.
2. A distinct, lighter haptic on sticker send from the tray built in
   Phase 16/17.
3. A settings toggle for haptics on/off, and an intensity control if using
   the amplitude-capable API.
4. Add the `VIBRATE` permission to the manifest (normal permission, no
   runtime request needed) and note it in the permission list Phase 29
   audits against.

## Definition of done

- [ ] Key presses and sticker sends each produce a distinct haptic pulse
      on a real device.
- [ ] Haptics can be fully disabled from settings, and the toggle takes
      effect immediately.
