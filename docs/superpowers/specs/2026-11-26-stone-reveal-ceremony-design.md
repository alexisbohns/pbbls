# The stone reveal ceremony at the end of the record flow — design

**Date:** 2026-11-26 · **Issues:** #1691 (ios), #1692 (android), #1693 (web) · **Surfaces:** ios first, then android and web · **Size:** medium per surface
**Status:** sequence approved by the maintainer 2026-11-26, from the prototype; this document awaits review.
**Builds on:** `2026-11-26-ios-vector-stone-design.md` (the vector stone; this design needs its Part 3 on iOS). Amends record-flow D12 (`2026-11-04-record-flow-redesign-design.md`).

## Intent

Making a pebble is the moment the record flow exists for, and today it does not feel like one. On every surface the success step slides in sideways like any other step, the haptic fires when the write returns (before anything is on screen), and the stone is too big: 240 pt / 240 dp tall (web 224 px), so on a quiet pebble Done falls below the fold. D12 already asked for "the new stone settling on its day"; nothing does it.

The maintainer judged five ceremonies and their combinations on a prototype (private artifact `https://claude.ai/artifact/4Sc8j16e8ooDYrew83KtuY`, version 3, "Set Down → Carve → Its Day, with a pause") and picked a three-act sequence: set the stone down, carve its glyph, hold on Continue, then send the stone to its day and bring the rest in.

## Goals

- The stone arrives as an event you see and feel. Every haptic lands on a visible contact, never before the stone is on screen.
- The finished pebble ends where it lives: on its day in the week. After the ceremony the screen is the "make it richer" page, with nothing pushed below the fold.
- The ceremony is finite and skippable, and the close button works throughout (the pebble is already saved when it starts).
- iOS and Android play the same sequence with the same timings; web plays the motion without haptics.

## Non-goals

- The stone's look (the vector stone design owns it).
- The steps before success, the `complete_pebble` write, the karma and achievement logic, and the richer rows' behaviour. They are unchanged; only their entrance changes.
- Sound.

## Anti-goals

- Confetti, a level-up, a slot-machine reveal, or anything that reads as a game reward.
- A ceremony that blocks: no step longer than the acts below, no forced wait before Continue is tappable.
- A haptic score that buzzes. If it reads busy on a phone, beats are removed, not added.
- A celebration on a heavy moment. The quiet pebble gets the same shape, slower and silent.

## Approaches considered

Five ceremonies were prototyped on the real stone: **Set Down** (lowered onto the paper, a thud, ripples), **Carving** (the glyph cut into a blank stone), **Onto its Day** (the stone flies into the week row), **Held to the Light** (a dark stone brightens) and **Breath** (a slow swell and exhale). Then two combinations were tried: the three-act sequence playing through, in brisk (about 2.7 s) and full (about 3.5 s) timings plus a roomy-week variant, and the same with a pause.

- **Held to the Light** and **Breath** were too subtle on their own.
- **Onto its Day** alone lost the moment of seeing your stone large.
- Playing straight through left the stone alone on screen for about 1.5 s with no text, then took it away before you could look at it.
- **Picked: the three acts with a pause.** Seeing the stone made, holding it as long as you like, then putting it away is one gesture, and the pause gives the carving room to be read.

## The sequence

Times are from the success step's first frame; durations are the prototype's for a medium river pebble.

**Act 1 — set down and carve (about 1.9–2.2 s; 1.4 s with no glyph).**
- **Set down.** The stone, uncarved (body and material, no glyph), is lowered onto the paper at the centre of the content area with nothing else on screen. It settles from slightly above scale 1 with a wide soft shadow that tightens on contact (exact values from the prototype's source, left to the builder's tuning). Lava falls slower.
  - **Contact:** a thud transient, then a weaker echo about 70 ms later. Ripple waves leave the outline at full reach, trimmed to the screen: two waves, one when quiet. The Spine dip plays at the stone's size.
  - **No rings at rest, ever.**
- **Carve.** Once the ripples are mostly gone (about 0.5 s after contact), the glyph's lip stroke is trimmed on, stroke by stroke, with a train of high-sharpness ticks along the path, never closer than 25 ms. Then the ink fills and one closing tap lands.
  - **Lava:** carves slower (1.0–1.1 s) with sparser ticks.
  - **Gem:** carves crisp and gets a spark on closure.
  - **No glyph:** the act is skipped and the pause comes sooner.

**The pause (held until Continue).**
- **Stone.** The stone rests large and centred at rest light: 158 pt wide for a medium pebble, 122 for small, 168 × 200 for large, about 3.6× a Spine stone.
- **Text.** The name, then the karma line (or the quiet sentence) fade in under it.
- **Continue.** A primary **Continue** button fades in at the bottom of the content area, in the exact place Done will sit.
- **Nothing moves, and nothing auto-advances.**

**Act 2 — to its day (about 1.2 s from Continue).**
- **Tap.** A light selection tick on Continue.
- **Travel.** The stone lifts slightly and travels while shrinking into its day cell in the week row, landing at the cell-fitting size (seal widths 24 / 28 / 34). The Spine press plays at that size: dip, glint, landing ripples at half speed. A landing tap sits on the contact.
- **Name and karma** slide up with the content to their resting place.
- **Cascade, after the landing:** the other six days open outward from the stone's day, then the "make it richer" rows one by one (fade plus a 6 pt rise), then Done and Record another. Done takes Continue's place, so the thumb does not move.
- **No haptic in the cascade.**

**Haptic score:** Act 1 is thud, echo, carve ticks, closing tap. Act 2 is the Continue tick and the landing tap. The lift tick and a karma tick were tried and dropped: one read as a double tap, the other crowded the landing.

## Gating and skipping

- A tap anywhere but Continue during Act 1 skips silently to the pause frame.
- Continue is tappable from the moment the stone has landed, during the carve too. An early tap completes the carve instantly, then plays Act 2.
- The close button closes at any time, with no confirmation: the pebble is saved before the ceremony starts.
- The rows and Done are not reachable before Continue. That is a deliberate change from today. The ceremony is one tap long, and closing is always available.

## States

- **Quiet (high distress, `Valence.quietSuccess`):** the same acts, 25% slower, with **no haptic at all**, the Continue tick included, and no spark. The pause shows the calm sentence, with **Get support** as a text link under Continue from the start. After Continue it rests above Done, as today.
- **Reduce Motion:** no travel, scale or rise. The stone fades in already carved at the centre, then Continue appears. On Continue the stone cross-fades into its day cell while the final layout fades in. One soft tap when the first fade ends: haptics are not motion.
- **Low Power Mode:** a flat stone, no carve animation, no ripples, glint or spark. The stone and Continue fade in, and on Continue the final layout fades in over 200 ms with one transient.
- **Haptics off** (`HapticsPreference`): the same visuals, no haptics.
- **Battery contract:** every animation is finite and ends on a still frame. Nothing runs during the pause.

## Implementation notes (iOS)

- The success haptic leaves `CompleteFlowModel.succeed`. The view plays the Act 1 pattern once the stone's first frame is committed, as one Core Haptics pattern scheduled against the engine's clock, following `IntroHaptics` (`Features/Path/Wrap/Intro/IntroHaptics.swift`: transient pattern, 25 ms floor, start tied to the first frame). Act 2 is its own short pattern.
- The success step enters by cross-fade, not the sideways `.move` every other step uses.
- The motion reuses the Spine's tables (`StoneMotion`: dip, sweep, spark, ripple waves) at the hero and landing sizes. The flight is a matched-geometry move from the centre slot to the day cell in `RicherWeekRow`. The stone is redrawn at each end size from the vector stone's cache, never scaled as a bitmap, so the carve line keeps its weight (the prototype's CSS scale thinned it to a pixel).
- The carve trims the glyph's lip line (the vector stone's wobbled glyph polyline) from 0 to 1, then fades in the ink.
- `RicherWeekRow` draws the new pebble's day as the stone, not the 10 pt dot.

## Mirrors

- **Android:** the same sequence, with the same timings, sizes and states. `IntroVibrator`'s ladder: a composition (`PRIMITIVE_THUD`, `PRIMITIVE_TICK` train, `PRIMITIVE_CLICK`), then waveform, then `performHapticFeedback(CONFIRM)`. Low Power reads `PowerManager.isPowerSaveMode`. One issue, on Android's own record flow (`RecordRicherStep`).
- **Web:** the same motion through WAAPI on `LitStone` (the success step moves off `PebbleFramed` onto the lit stone). No haptics; at most `navigator.vibrate(6)` at contact. One issue.

## Left to the builder

- **Haptic values:** the final intensities and sharpnesses, tuned on a phone. Starting values are the prototype's: thud 0.55 / 0.75 / 0.95 by size; landing 0.45 / 0.55 / 0.65; Continue tick 0.30 / 0.45; carve ticks at sharpness 0.85, intensity 0.18 → 0.30.
- **Continue into Done:** whether Continue morphs into Done or cross-fades. The prototype leaves the spot empty for 0.7 s; avoid that.
- **Lift:** how the lift reads (scale, shadow).
- **Week cell:** whether the week cell needs a touch more room for the large stone (the prototype's roomy row cost about 40 pt and still overflowed 6 pt). Keep the 24 / 28 / 34 landing sizes unless the device pass says otherwise.

## Records

- Decision log: one entry when iOS ships ("the success step is a three-act ceremony with a pause; the haptic moves to the view"), amending D12.
- Arkaik: the richer-step view and its acceptances move to `development` when the iOS work starts; no view is added.
- Data safety: no change.
