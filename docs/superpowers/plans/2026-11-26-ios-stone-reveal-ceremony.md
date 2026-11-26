# iOS stone reveal ceremony Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The record flow's success step plays the approved three-act ceremony (set down and carve, a pause on Continue, then the stone travels to its day), with its Core Haptics score, then rests on the "make it richer" page with nothing below the fold.

**Architecture:** A pure timeline (`StoneCeremonyTimeline`) holds every time, size and table value and samples a `StoneCeremonyFrame` at any instant. A value-type state machine (`StoneCeremony`) owns the phases and the taps, and a small `@Observable` driver maps its transitions onto a Core Haptics player. The views only read frames. The carve is the vector stone's own glyph lines, trimmed, painted over the cached uncarved stone. Each end of the flight is a native paint from `LitStoneCache`, and the two are cross-faded in flight.

**Tech Stack:** SwiftUI (`TimelineView`, `Canvas`/`GraphicsContext`, anchor preferences), Core Haptics, Swift Testing, xcodegen.

**Spec:** `docs/superpowers/specs/2026-11-26-stone-reveal-ceremony-design.md` (issue #1691). It builds on `docs/superpowers/plans/2026-11-26-ios-vector-stone.md`, Parts 1–4. The prototype the maintainer approved is the private artifact `https://claude.ai/artifact/4Sc8j16e8ooDYrew83KtuY`, version 3, ceremony `combo-pause`. Its functions `pT`, `makePause` and `carveTicks` are the source of every number below that the spec leaves "to the prototype".

## Global Constraints

- **Platform.** iOS 17 APIs only, `@Observable`, Swift Testing (`@Suite`/`@Test`/`#expect`), never XCTest. `os.Logger`, never `print`.
- **Where the code lives.**
  - Ceremony logic and views: `apps/ios/Pebbles/Features/Complete/Ceremony/`. These use `Color.pebbles.<role>` only.
  - Code that paints stone pixels (the carve and the lift shadow): `apps/ios/Pebbles/Features/Path/Stone/Lit/`, the one folder `ThemeLiteralsTests` exempts.
  - Tests: `apps/ios/PebblesTests/Features/Complete/Ceremony/` and `apps/ios/PebblesTests/Stone/Lit/`.
- **One home per number.** Times, waves and dip depths live in `StoneCeremonyTimeline`. Sizes live in `StoneCeremonyLayout`. Haptic values live in `StoneCeremonyScore`. Views read them and never restate them. Android #1692 and web #1693 transcribe these three files.
- **Sizes (spec).**
  - Hero widths: 122 pt (small), 158 pt (medium), 168 pt (large). The height follows the outline, so large is 168 × 200.
  - Landing widths: 24 / 28 / 34 pt, which is `SealSize.milestone`.
- **Quiet (`Valence.quietSuccess`).** Every time is × 1.25. There are no haptics at all, the Continue tick included, and no spark or sweep. The Act 1 ripple has one wave and the landing ripple has none.
- **No rings at rest, ever.** The ceremony never draws `StoneRings` or the halo. It draws only the ripple waves (`StoneRipple`) and the glint (`StoneGlint`).
- **Battery contract.** Every animation is finite and ends on a still frame. The `TimelineView` is paused during the pause and after the ceremony settles. Nothing runs during the pause.
- **Haptics.**
  - Each act is one Core Haptics pattern on the ceremony's own engine, scheduled against the engine clock, after `IntroHaptics`.
  - Two events closer than 25 ms are not played (`IntroHaptics.floor`).
  - No haptic plays before the stone's first frame.
  - `HapticsPreference` off means no pattern is played at all.
- **Redraw, never rescale, at rest.** Every resting stone, at the hero size and in the cell, is a native paint from `LitStoneCache`. A bitmap is scaled only while it is moving.
- **Determinism.** References render frozen frames (`frozen: .pause` / `.settled`), never a clock. Never iterate a `Set` or a `Dictionary` while drawing.
- **Copy.** Every string the ceremony shows already exists: Continue, Done, Get support, Record another and the calm sentence. If a new one is added, it gets `en` and `fr` in `Localizable.xcstrings`, with no `New` or `Stale` rows.
- **iOS test runs.**
  - Shorthand: **TEST(S)** means `cd apps/ios && xcodebuild test -scheme Pebbles -destination 'id=4F1E8A2C-E08A-468E-8A89-6ECC011C12D2' -derivedDataPath $SCRATCH/dd-stone -only-testing:PebblesTests/S`, where `$SCRATCH` is the executor's scratchpad.
  - Run `xcodegen generate` after adding files, then delete `$SCRATCH/dd-stone/Build` before trusting a result.
  - Filter at suite level only. Reference suites are nested, so pass `References/<Suite>`.
- **Reference renders.** About 27 fail locally on untouched main. The local gate is the same failing set, with byte-identical PNGs against a baseline run; CI is the real gate. Never re-record locally. Re-baseline with the `rebaseline-ios-screenshots` label alone, once CI's References job has run red.
- **Branches and PRs.**
  - Each part has its own `feat/1691-…-ios` branch, stacked with `gh stack`; Part 1 branches from `main` (the vector stone, #1690, is merged).
  - PR bodies start with `Part of #1691` (Parts 1–2) or `Resolves #1691` (Part 3) and end with `Data safety: no change`.
  - Labels `feat`, `ui`, `ios`; milestone `M70 · Record flow`.
  - The controller commits this plan file before Part 1.
- **Out of scope.** Android #1692 and web #1693.

## Review Focus

1. **Continue tapped during the carve, then tapped again at once.** The first tap completes the carve on that frame, stops Act 1's pattern and plays Act 2. The second tap lands the layout. It never fires Done. Test in Task 3 (`doubleTap`) and Task 4 (`continueMidCarve`).
2. **Rows, Done and Record another, invisible but still in the tree.** Before the ceremony settles, they must take no taps and be hidden from VoiceOver, not just transparent. Test in Task 3 (`richerInputOnlySettled`). Task 7 binds `.allowsHitTesting` and `.accessibilityHidden` to that property.
3. **A moment with no known day (`happenedAt == nil`, e.g. the held card before its detail loads).** There is no week row to land in. The stone lands in a landing-size slot above the name, with the same flight, landing tap and press. Reference `richerNoDay` in Task 7.
4. **Close tapped mid-ceremony.** The cover closes at once with no confirmation, and the haptic pattern stops, so no tick plays on the screen underneath. Test in Task 4 (`closeStops`).
5. **Accessibility text sizes.** At the pause, the name and the karma line under the hero stone must not run under Continue. After the ceremony the page scrolls, and the bottom bar keeps Done in Continue's place. Reference `richerPauseAx2` in Task 7.

## Decisions the spec left to the builder

- **D1 — State machine.** A pure timeline, a value-type `StoneCeremony` (phases `setDown → paused → toItsDay → settled`, wall-clock `Date`s) and a thin `@Observable` driver for the haptics. Every rule (gating, skipping, the early Continue) is then a unit test, with no view and no device. A wall clock also means a backgrounded app resumes on the right frame.
- **D2 — Carve.** The cached uncarved stone (`spec.uncarved`, with no glyph) is drawn under a live `Canvas`. The Canvas strokes the vector stone's own `LitGlyphLine` lines, trimmed cumulatively in stroke order: the lip at `lip`, the ink 70 ms behind it at opacity 0.6. Then the ink fades to 1 over 200 ms, and the view swaps to the cached carved image. Both use the painter's own transforms, clip, tones and night matrix, so the swap shows no change.
- **D3 — End sizes.** The hero image and the landing image are each painted natively from `LitStoneCache`. In flight both are drawn at the interpolated rect, scaled, and cross-faded over the last 40% of the flight. No resting frame is a scaled bitmap, so the carve keeps its 1.25 pt weight at both ends.
- **D4 — Flight.** The move is a hand-made matched-geometry move. The centre slot and the day cell each publish an `Anchor<CGRect>`, and the flying stone is placed by `StoneCeremonyLayout.flightRect` from the timeline's eased progress. `matchedGeometryEffect` was not used because its timing belongs to SwiftUI, and the landing tap must hit the contact frame.
- **D5 — Haptic patterns as data.** `StoneCeremonyScore` returns `[IntroHapticEvent]` for each act, so every value and the 25 ms floor are tested without a device. It also takes `hapticsOn`, so quiet and Haptics-off are both the empty score. The starting values are the spec's and the prototype's, transcribed in Task 4.
- **D6 — Continue becomes Done by a morph.**
  - One primary capsule sits in the bottom bar from the pause to the end.
  - After the tap it keeps reading "Continue", and a further tap only lands the layout.
  - At `actionsIn` its label cross-fades to "Done" over 300 ms, and Record another fades in below it.
  - The spot is never empty, and the thumb never moves.
- **D7 — Modes.**
  - **Low Power** wins over **Reduce Motion**. Both are read once, when the ceremony starts.
  - **Reduce Motion:**
    - The carved stone fades in over 250 ms, with a soft tap (0.50 / 0.40) at the fade's end. Continue fades in from 400 ms over 300 ms.
    - There is no text at the pause, as in the prototype.
    - On Continue there is the tick, then a 250 ms cross-fade: the stone fades out at the centre while the cell stone and the whole final layout fade in.
  - **Low Power:**
    - A flat stone; `LitStoneImage` already reads Low Power. It fades in over 200 ms, with a tap (0.50 / 0.40) at 200 ms. Continue fades in from 200 ms over 200 ms.
    - On Continue there is the tick, then a 200 ms cross-fade.
    - There is no carve, ripple, glint or spark.
  - **Quiet:** see Global Constraints.
  - **Haptics off:** the same visuals and an empty score.
- **D8 — Reference determinism.** `CompleteRicherStep(…, frozen: StoneCeremonyFreeze?)` builds a frozen ceremony that never starts a clock or a haptic. References render `.pause` and `.settled`. Every wave, sweep and spark is finished by the pause (Task 2 asserts this), so the frozen pause equals a live one.
- **D9 — Close on the success step.** `RecordFlowChrome` is now shown on the success step too, with no dots and no back button, so ✕ works throughout. Today the success step has no chrome at all.
- **D10 — Week row slots.** All seven slots take the landed stone's height: small 19.2 pt, medium 28 pt, large 40.6 pt. Nothing shifts when the stone lands. The 24 / 28 / 34 landing widths are kept, pending the device pass.
- **D11 — Lift.** The baked shadow stays. During the fall and the flight, a second far shadow is drawn under the image: offset × (1 + lift), blur × (1 + 1.2 · lift), at alpha 0.5 · lift. At lift 0 the stone is exactly its cached image.
  - Act 1: lift = 1 − fall.
  - Act 2: lift = 0.7 · bump, with scale × (1 + 0.04 · bump).
- **D12 — No known day.** The spec is silent on a moment with no known day. With no week row, the stone lands in a landing-size slot above the name, with the same flight, landing tap and press. The pebble still ends visible, and nothing is pushed below the fold.

---

## Part 1 — timeline, state machine, haptic score

Branch `feat/1691-ceremony-timeline-ios`, stacked on `chore/1690-metal-stone-removal-ios`. No UI changes. One PR.

### Task 1: Timeline and layout

**Files:**
- Create: `apps/ios/Pebbles/Features/Complete/Ceremony/StoneCeremonyTimeline.swift`
- Create: `apps/ios/Pebbles/Features/Complete/Ceremony/StoneCeremonyLayout.swift`
- Test: `apps/ios/PebblesTests/Features/Complete/Ceremony/StoneCeremonyTimelineTests.swift`

**Interfaces:**
- Produces:
  - `enum StoneCeremonyMode: Equatable { case full, reducedMotion, lowPower }`, with `static func resolve(reduceMotion: Bool, lowPower: Bool) -> StoneCeremonyMode`. Low Power wins.
  - `struct StoneCeremonyTimeline: Equatable`, with:
    - fields `size: ValenceSizeGroup`, `polarity: ValencePolarity`, `carves: Bool`, `quiet: Bool`, `mode: StoneCeremonyMode`;
    - two inits: `init(size:polarity:carves:quiet:mode:)`, written out, and `init(valence: Valence, carves: Bool, mode: StoneCeremonyMode)`, where `quiet = valence.quietSuccess`.
  - The timeline's beats are all `Double` seconds and all × `tempo` (1.25 when quiet, else 1).
    - **Act 1**, from the first frame:
      - `contact`: the full contact, or the fade end in the other modes.
      - `carveStart`, `carveDuration` (0 when `!carves`), `closure`.
      - `nameIn`, `karmaIn`, `continueIn`, `continueFade`, `pause`.
    - `tempo: Double`: 1.25 when quiet, else 1.
    - **Act 2**, from Continue:
      - `flight`, and `landing`, which is the same instant;
      - `func dayIn(_ offset: Int) -> Double`, where `offset` is the distance from the marked day and is at least 1;
      - `func rowIn(_ index: Int) -> Double`, where index 0 is the "MAKE IT RICHER" header;
      - `actionsIn`, `settled` (the layout and Done are in; richer input is accepted from here);
      - `still`: when the last landing wave, sweep or spark has ended, so the clock stops on a still frame. `still ≥ settled`.
  - Tables: `heroWaveCount: Int`, `landingWaveCount: Int`, `dipDepth: Double`, `sparksOnClosure: Bool`.
  - `enum StoneCeremonyLayout`, with:
    - `static func heroSize(_: ValenceSizeGroup) -> CGSize`
    - `static func landingSize(_: ValenceSizeGroup) -> CGSize`
    - `static func slotHeight(_: ValenceSizeGroup?) -> CGFloat`
    - `static func heroWaveReach(screenWidth: CGFloat, heroWidth: CGFloat, wave k: Int) -> Double`
- **Values, from the prototype's `pT`, at tempo 1:**

  | Value | Lava | River | Gem |
  |---|---|---|---|
  | `contact` | 0.42 | 0.32 | 0.28 |
  | `carveDuration` | 1.05 | 0.56 | 0.50 |

  - Act 1, full mode:
    - `carveStart = contact + 0.52`
    - `closure = carveStart + carveDuration`
    - `nameIn = closure + 0.10`, `karmaIn = closure + 0.19`, `continueIn = closure + 0.26`
    - `continueFade = 0.30`, `pause = closure + 0.56`, or `closure + StoneMotion.Spark.element.duration · tempo` (0.76) when `sparksOnClosure`, whichever is later, so the spark ends before the pause
  - Act 1, Reduce Motion: `contact = 0.25`, `continueIn = 0.40`, `continueFade = 0.30`, `pause = 0.70`.
  - Act 1, Low Power: `contact = 0.20`, `continueIn = 0.20`, `continueFade = 0.20`, `pause = 0.40`.
  - Act 2, full mode:
    - `flight = 0.38`
    - `dayIn(o) = landing + 0.05 + 0.06 · (o − 1)`
    - `rowIn(i) = landing + 0.20 + 0.06 · i`
    - `actionsIn = landing + 0.48`, `settled = actionsIn + 0.30`
    - `still = max(settled, end of each landing wave, end of the sweep, end of the spark)`, with the ends from Task 2's Act 2 press (wave k ends at `landing + (0.12 + 0.23k)·0.5 + (1.65 + 0.17k)·0.5`; the sweep at `landing + 0.14 + 0.9`; a gem's spark at `landing + 0.30 + 0.76`); river medium gives 1.465
  - Act 2, Reduce Motion: `flight = 0.25`. Low Power: `flight = 0.20`. In both modes `dayIn`, `rowIn` and `actionsIn` are 0, and `settled = still = flight`.
  - Waves: `heroWaveCount` is 2 (1 when quiet). `landingWaveCount` is 2 (0 when quiet). Both are 0 unless the mode is full.
  - `dipDepth`: [0.95, 0.94, 0.92] by size, minus 0.01 for lava. When quiet it is `1 − (1 − d) / 2`.
  - `sparksOnClosure`: gem, `carves`, not quiet, full mode. The spec names the gem only; the prototype also sparked the river.
  - Hero size: the width is [122, 158, 168]; the height is width × outline height / outline width.
  - `landingSize`: the width is `SealSize.milestone.width(size)`.
  - `slotHeight`: `max(10, landingSize.height)`, and 10 for nil.
  - `heroWaveReach`: `max(2, screenWidth / heroWidth × 0.96) × (1 − 0.06 · k)`, in stone widths.

- [ ] **Step 0:** Arkaik: move the richer-step view and its acceptances to `development` (`update_node`). No view is added. If the `arkaik-mcp` tools are unavailable, say so and stop that step; never touch `docs/arkaik/bundle.json`.
- [ ] **Step 1: Write the failing tests**

```swift
@Test func riverMedium() {
    let t = StoneCeremonyTimeline(valence: .neutralMedium, carves: true, mode: .full)
    #expect(close(t.contact, 0.32)); #expect(close(t.carveStart, 0.84)); #expect(close(t.closure, 1.40))
    #expect(close(t.nameIn, 1.50)); #expect(close(t.karmaIn, 1.59)); #expect(close(t.continueIn, 1.66))
    #expect(close(t.pause, 1.96))                                  // spec: Act 1 about 1.9–2.2 s
    #expect(close(t.landing, 0.38)); #expect(close(t.dayIn(1), 0.43)); #expect(close(t.dayIn(3), 0.55))
    #expect(close(t.rowIn(0), 0.58)); #expect(close(t.rowIn(3), 0.76))
    #expect(close(t.actionsIn, 0.86)); #expect(close(t.settled, 1.16)) // spec: Act 2 about 1.2 s
    #expect(close(t.still, 1.465)); #expect(t.tempo == 1)
}
@Test func lavaAndGem() { /* lava: contact 0.42, closure 1.99, pause 2.55 (spec: lava carves 1.0–1.1 s); gem: 0.28, 1.30, pause 2.06 (its spark ends at closure + 0.76) */ }
@Test func noGlyph() { /* river, carves false: carveDuration 0, closure 0.84, pause 1.40 (spec: 1.4 s) */ }
@Test func quietIsSlower() {
    let q = StoneCeremonyTimeline(valence: .lowlightLarge, carves: true, mode: .full)
    let n = StoneCeremonyTimeline(size: .large, polarity: .lowlight, carves: true, quiet: false, mode: .full)
    // every beat × 1.25: contact, carveStart, closure, nameIn, karmaIn, continueIn, pause, landing, actionsIn, settled
    #expect(close(q.pause, n.pause * 1.25)); #expect(close(q.settled, n.settled * 1.25))
    #expect(q.heroWaveCount == 1); #expect(q.landingWaveCount == 0); #expect(!q.sparksOnClosure)
    #expect(close(q.dipDepth, 1 - (1 - 0.91) / 2))
}
@Test func modes() {
    #expect(StoneCeremonyMode.resolve(reduceMotion: true, lowPower: true) == .lowPower)
    // reducedMotion: contact 0.25, continueIn 0.40, pause 0.70, settled 0.25, heroWaveCount 0
    // lowPower: contact 0.20, continueIn 0.20, continueFade 0.20, pause 0.40, settled 0.20
}
@Test func sizes() {
    #expect(StoneCeremonyLayout.heroSize(.small).width == 122)
    #expect(StoneCeremonyLayout.heroSize(.medium) == CGSize(width: 158, height: 158))
    #expect(StoneCeremonyLayout.heroSize(.large).width == 168)
    #expect(abs(StoneCeremonyLayout.heroSize(.large).height - 200) < 1)
    #expect(ValenceSizeGroup.allCases.map { StoneCeremonyLayout.landingSize($0).width } == [24, 28, 34])
    #expect(abs(StoneCeremonyLayout.slotHeight(.small) - 19.23) < 0.01)
    #expect(StoneCeremonyLayout.slotHeight(.medium) == 28)
    #expect(abs(StoneCeremonyLayout.slotHeight(.large) - 40.6) < 0.05)
    #expect(StoneCeremonyLayout.slotHeight(nil) == 10)
    #expect(abs(StoneCeremonyLayout.heroWaveReach(screenWidth: 393, heroWidth: 158, wave: 0) - 2.388) < 0.001)
    #expect(abs(StoneCeremonyLayout.heroWaveReach(screenWidth: 393, heroWidth: 158, wave: 1) - 2.245) < 0.001)
}
```
`close(a, b)` is `abs(a − b) < 0.0005`.

- [ ] **Step 2:** `cd apps/ios && xcodegen generate`, delete `$SCRATCH/dd-stone/Build`, then **TEST(StoneCeremonyTimelineTests)**. Expected: a build failure on the missing types.
- [ ] **Step 3:** Implement both files with the values above.
- [ ] **Step 4:** **TEST(StoneCeremonyTimelineTests)**. Expected: PASS.
- [ ] **Step 5:** Commit: `feat(ios): time the stone reveal ceremony`.

### Task 2: Frames

**Files:**
- Create: `apps/ios/Pebbles/Features/Complete/Ceremony/StoneCeremonyFrame.swift`
- Test: `apps/ios/PebblesTests/Features/Complete/Ceremony/StoneCeremonyFrameTests.swift`

**Interfaces:**
- Consumes: Task 1, plus `StoneMotion` (`Element`, `piecewise`, `Frame`, `WaveSample`, the `Sweep`, `Spark` and `Wave` tables).
- Produces: `struct StoneCeremonyFrame: Equatable`, with these fields.
  - The stone, while it is the hero or in flight:
    - `stoneOpacity`, `stoneScale`, `lift` (0…1, D11);
    - `lipTrim`, `inkTrim`, `inkOpacity`;
    - `press: StoneMotion.Frame`: only `sweep`, the spark and `waves` are read.
  - The flight:
    - `flight: Double`: 0 at the centre, 1 at the cell, eased.
    - `cellStoneOpacity`: the week cell's own stone.
  - The text:
    - `nameOpacity`, `karmaOpacity` (the karma line or the calm sentence);
    - `textDrop`: 1 puts the text under the hero, 0 at its rest.
  - The bar:
    - `buttonOpacity`: the one capsule;
    - `buttonLabel`: 0 reads "Continue", 1 reads "Done";
    - `supportUnderContinue`: the quiet link at the pause;
    - `actionsOpacity`: Record another, and the quiet link above Done.
  - The cascade: `dayOpacity: [Double]` (7), `rowOpacity: [Double]` (4), `rowRise: [CGFloat]` (4).
- Produces, on `StoneCeremonyTimeline`:
  - `func act1(at t: Double, heroReach: [Double]) -> StoneCeremonyFrame`
  - `func act2(at u: Double, markedDay: Int) -> StoneCeremonyFrame`
  - `func settledFrame(markedDay: Int) -> StoneCeremonyFrame`
- **Curves (prototype `E`):**
  - fall: bezier(.5, 0, .9, .6)
  - out: bezier(.2, .7, .2, 1)
  - inout: bezier(.37, 0, .63, 1)
  - The dip, the waves, the sweep and the spark use `StoneMotion`'s own curves.
- **Act 1, full mode:**
  - The stone:
    - `stoneOpacity = clamp(t / (0.6 · contact))`.
    - `stoneScale = lerp(1.12, 1, fall(t / contact)) × dip`. The dip runs on `StoneMotion.Dip.element` from `contact`, over the frames `[(0, 1), (0.26, dipDepth), (0.6, 1.03), (1, 1)]`.
    - `lift = 1 − fall`.
  - The ripple:
    - Wave k starts at `contact + 0.08 + 0.11 · k` and runs `(0.70 + 0.07 · k) · tempo`.
    - It uses `StoneMotion.Wave`'s curve and alpha frames, with scale frames `[(0, 1.15), (0.07, 1.45), (1, heroReach[k])]`.
  - The carve:
    - `lipTrim = inout(progress(t, carveStart, carveDuration))`.
    - `inkTrim` is the same at `t − 0.07`.
    - `inkOpacity = lerp(0.6, 1, progress(t, closure, 0.2 · tempo))`.
    - With no glyph, all three are 1.
  - The text:
    - `nameOpacity` and `karmaOpacity` fade in with out-easing over 0.3 · tempo, from `nameIn` and `karmaIn`.
    - `textDrop = 1`.
  - The bar:
    - `buttonOpacity = out(progress(t, continueIn, continueFade))`.
    - When quiet, `supportUnderContinue` equals it.
  - The cascade, the label and the actions are all 0.
  - The spark is `StoneMotion.Spark` from `closure`, when `sparksOnClosure`.
- **Act 1, Reduce Motion and Low Power:**
  - `stoneOpacity = out(t / contact)`.
  - Every trim is 1, the scale is 1 and the lift is 0.
  - There are no waves and no text.
  - The button behaves as above.
- **Act 2, full mode:**
  - The flight:
    - `flight = out(progress(u, 0, flight))`.
    - `bump = progress(u, 0, 0.14) × (1 − progress(u, landing − 0.1, 0.1))`.
    - `stoneScale = (1 + 0.04 · bump) × dip(u − landing, depth 0.94)`.
    - `lift = 0.7 · bump`.
  - The landing press:
    - Waves k < `landingWaveCount` start at `landing + (0.12 + 0.23 · k) · 0.5`, last `(1.65 + 0.17 · k) · 0.5`, and reach `StoneMotion.Wave.reach[size] − 0.5 · k`.
    - The sweep starts at `landing + 0.14`, unless quiet.
    - The spark starts at `landing + 0.30`, for a gem that is not quiet.
  - The text: `textDrop = 1 − out(progress(u, 0, 1.2 · flight))`, and both text opacities stay 1.
  - The stone in the cell:
    - The marked day's `dayOpacity` is 1 from `landing`.
    - `stoneOpacity` drops to 0, and `cellStoneOpacity` rises to 1, at `still`. Until then the flying stone owns the landing press.
  - The cascade:
    - Day `i` fades in over 0.3 · tempo from `dayIn(abs(i − markedDay))`.
    - Row `i` fades in over the same time from `rowIn(i)`, with `rowRise` going from 6 to 0.
  - The bar:
    - `buttonOpacity` is 1 throughout.
    - `buttonLabel` and `actionsOpacity` fade in over 0.3 · tempo from `actionsIn`.
    - `supportUnderContinue = 1 − progress(u, 0, 0.2)`.
- **Act 2, Reduce Motion and Low Power:** with `a = out(u / flight)`:
  - `stoneOpacity = 1 − a`, and `cellStoneOpacity` equals `a`;
  - every cascade, text and action opacity equals `a`;
  - `flight` stays 0 and `textDrop` is 0.

- [ ] **Step 1: Write the failing tests** (river medium full; `act2` and `settledFrame` take `markedDay: 2`; `reach` is [2.388, 2.245], unless named):

```swift
@Test func setDown() {
    let f0 = t.act1(at: 0, heroReach: reach)
    #expect(f0.stoneOpacity == 0); #expect(close(f0.stoneScale, 1.12)); #expect(f0.lift == 1)
    #expect(f0.lipTrim == 0); #expect(close(f0.inkOpacity, 0.6)); #expect(f0.buttonOpacity == 0)
    let fc = t.act1(at: 0.32, heroReach: reach)
    #expect(fc.stoneOpacity == 1); #expect(close(fc.stoneScale, 1)); #expect(fc.lift == 0)
    let lowest = stride(from: 0.32, through: 0.84, by: 0.005).map { t.act1(at: $0, heroReach: reach).stoneScale }.min()!
    #expect(abs(lowest - 0.94) < 0.005)
}
@Test func ripple() { /* at contact + 0.08 + 0.049: waves[0].alpha ≈ 0.85; waves.count == 2; quiet: 1; lowPower: 0 */ }
@Test func carve() {
    // lipTrim(0.84) == 0, lipTrim(1.40) == 1, inkTrim(1.40) < 1, inkTrim(1.47) == 1
    // inkOpacity(1.40) ≈ 0.6, inkOpacity(1.60) == 1; carves false: all three 1 at t = 0
}
@Test func spark() { /* gem: press.sparkAlpha > 0 at closure + 0.266; river and quiet: 0 at every sample */ }
@Test func pauseIsStill() {
    let p = t.act1(at: t.pause, heroReach: reach)
    #expect(p.lipTrim == 1 && p.inkTrim == 1 && p.inkOpacity == 1)
    #expect(p.nameOpacity == 1 && p.karmaOpacity == 1 && p.buttonOpacity == 1 && p.textDrop == 1)
    #expect(p.press.waves.allSatisfy { $0.alpha == 0 }); #expect(p.press.sweep == nil); #expect(p.press.sparkAlpha == 0)
    #expect(p.dayOpacity == Array(repeating: 0, count: 7)); #expect(p.rowOpacity == [0, 0, 0, 0]); #expect(p.actionsOpacity == 0)
    #expect(t.act1(at: 60, heroReach: reach) == p)   // a backgrounded app resumes on the pause
}
@Test func toItsDay() {
    // flight(0) == 0, flight(0.38) == 1; max stoneScale in 0..<0.38 ≈ 1.04 ±0.005; max lift ≤ 0.7
    // textDrop(0) == 1, textDrop(0.456) == 0; buttonOpacity == 1 at every sample 0...1.16
    // buttonLabel(0.86) == 0, buttonLabel(1.16) == 1; supportUnderContinue(0.2) == 0
    // dayOpacity[2] == 1 at 0.38; dayOpacity[1] == 0 at 0.43, == 1 at 0.73; dayOpacity[6] starts at 0.61
    // rowOpacity[0] == 0, rowRise[0] == 6 at 0.58; rowOpacity[0] == 1, rowRise[0] == 0 at 0.88
    // landing waves: count 2, waves[0] starts at 0.44; quiet: none; sweep starts at 0.52 unless quiet
    #expect(t.act2(at: t.still, markedDay: 2) == t.settledFrame(markedDay: 2))
}
@Test func settled() { /* stoneOpacity 0, cellStoneOpacity 1, flight 1, textDrop 0, every opacity 1, rowRise all 0, buttonLabel 1 */ }
@Test func reducedMotionAndLowPower() {
    // RM act1(0.25): stoneOpacity 1, trims 1, scale 1, lift 0, no waves, nameOpacity 0; act1(0.70) buttonOpacity 1
    // RM act2(u < 0.25): flight 0, scale 1; act2(0.25) == settledFrame. LP: same at 0.20, press.sweep nil, sparkAlpha 0
}
```

- [ ] **Step 2:** **TEST(StoneCeremonyFrameTests)**. Expected: FAIL (missing members).
- [ ] **Step 3:** Implement. Define `progress(x, start, duration)` and `lerp` as private helpers. Reuse `StoneMotion.piecewise` and `StoneMotion.Element` rather than reimplementing them.
- [ ] **Step 4:** **TEST(StoneCeremonyFrameTests)** and **TEST(StoneCeremonyTimelineTests)**. Expected: PASS.
- [ ] **Step 5:** Commit: `feat(ios): sample the stone ceremony's frames`.

### Task 3: State machine

**Files:**
- Create: `apps/ios/Pebbles/Features/Complete/Ceremony/StoneCeremony.swift`
- Test: `apps/ios/PebblesTests/Features/Complete/Ceremony/StoneCeremonyTests.swift`

**Interfaces:**
- Consumes: Tasks 1–2.
- Produces:
  - `enum StoneCeremonyPhase: Equatable { case setDown(since: Date), paused, toItsDay(since: Date), settled }`
  - `enum StoneCeremonyFreeze { case pause, settled }`
  - `struct StoneCeremony: Equatable`, with:
    - fields `timeline`, `markedDay: Int`, `heroReach: [Double]`, and `private(set) var phase`;
    - `init(timeline:markedDay:heroReach:start: Date)`
    - `init(timeline:markedDay:heroReach:frozen: StoneCeremonyFreeze)`
    - `func frame(at now: Date) -> StoneCeremonyFrame`: `.paused` is `act1(at: pause)`, and `.settled` is `settledFrame`.
    - `var isRunning: Bool`: `setDown` or `toItsDay`.
    - `func acceptsRicherInput(at now: Date) -> Bool`: in `.settled`, and in `.toItsDay` once `now − since ≥ timeline.settled`.
    - `func endsAt() -> Date?`: when the running phase ends; nil when the ceremony is still.
    - `mutating func tick(at: Date)`: `setDown` past `pause` becomes `.paused`; `toItsDay` past `still` becomes `.settled`.
    - `mutating func tapOutside(at: Date)`: `setDown` becomes `.paused`; `toItsDay` becomes `.settled`; otherwise nothing happens.
    - `mutating func tapContinue(at: Date) -> Bool`: true only when Act 2 starts now.
      - From `setDown` at or after `contact`, and from `.paused`, it moves to `.toItsDay(since: now)`.
      - Before `contact`, it behaves as `tapOutside`.
      - During `toItsDay`, it behaves as `tapOutside`.

- [ ] **Step 1: Write the failing tests** (river medium, `t0`):
  - `continueGate`: `tapContinue(at: t0 + 0.1)` returns false, and the phase becomes `.paused`.
  - `skipToPause`: `tapOutside(at: t0 + 0.5)` gives `.paused`, and `frame(at: t0 + 0.5) == timeline.act1(at: timeline.pause, …)`.
  - `earlyContinue`: `tapContinue(at: t0 + 1.0)` returns true, the phase becomes `.toItsDay(since: t0 + 1.0)`, and `frame(at: t0 + 1.0).lipTrim == 1 && inkOpacity == 1`.
  - `doubleTap`: after `tapContinue(at: t0 + 2)`, `tapContinue(at: t0 + 2.1)` returns false and gives `.settled`.
  - `ticks`:
    - `tick(at: t0 + 1.9)` stays in `setDown`;
    - `tick(at: t0 + 1.97)` gives `.paused`;
    - in `toItsDay(since: t1)`, `tick(at: t1 + 1.17)` stays in `toItsDay` and `tick(at: t1 + 1.47)` gives `.settled`;
    - in `.paused`, `tick(at: .distantFuture)` stays `.paused` (nothing auto-advances).
  - `richerInputOnlySettled`: `acceptsRicherInput(at:)` is false in `setDown`, `paused` and in `toItsDay` before `settled` (t1 + 1.0), true in `toItsDay` at t1 + 1.2, and true in `settled`.
  - `isRunning` holds only in `setDown` and `toItsDay`.
  - `endsAt` is `t0 + 1.96` in `setDown`, `t1 + 1.465` in `toItsDay(since: t1)`, and nil in `.paused` and `.settled`.
  - `frozen`: `.pause` is `.paused` and `.settled` is `.settled`, and neither is running.
- [ ] **Step 2:** **TEST(StoneCeremonyTests)**. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** **TEST(StoneCeremonyTests)**. Expected: PASS.
- [ ] **Step 5:** Commit: `feat(ios): gate and skip the stone ceremony`.

### Task 4: Haptic score, player and driver

**Files:**
- Create: `apps/ios/Pebbles/Features/Complete/Ceremony/StoneCeremonyScore.swift`
- Create: `apps/ios/Pebbles/Features/Complete/Ceremony/StoneCeremonyHaptics.swift`
- Create: `apps/ios/Pebbles/Features/Complete/Ceremony/StoneCeremonyDriver.swift`
- Test: `apps/ios/PebblesTests/Features/Complete/Ceremony/StoneCeremonyScoreTests.swift`
- Test: `apps/ios/PebblesTests/Features/Complete/Ceremony/StoneCeremonyDriverTests.swift`

**Interfaces:**
- Consumes: Tasks 1–3; `IntroHapticEvent`, `IntroTick` and `IntroHaptics.floor` (existing).
- Produces:
  - `enum StoneCeremonyScore`, with:
    - `static func act1(_ t: StoneCeremonyTimeline, carveLength: CGFloat, hapticsOn: Bool) -> [IntroHapticEvent]`
    - `static func act2(_ t: StoneCeremonyTimeline, hapticsOn: Bool) -> [IntroHapticEvent]`
    - `static func carveTicks(start: Double, duration: Double, length: CGFloat) -> [IntroHapticEvent]`
  - `@MainActor protocol StoneCeremonyHapticPlaying: AnyObject`, with:
    - `func prepare()`
    - `func play(_ events: [IntroHapticEvent], startingAt begin: Date)`
    - `func stop()`
  - `@MainActor final class StoneCeremonyHaptics: StoneCeremonyHapticPlaying`:
    - `static let shared`;
    - its own `CHHapticEngine`, with the auto-shutdown and the reset handler copied from `IntroHaptics`;
    - one advanced player at a time;
    - errors logged under category `ceremony`;
    - `nonisolated static func pattern(_ events: [IntroHapticEvent]) throws -> CHHapticPattern`.
  - `@Observable @MainActor final class StoneCeremonyDriver`, with:
    - `private(set) var ceremony: StoneCeremony`
    - `init(ceremony: StoneCeremony, carveLength: CGFloat, hapticsOn: Bool, haptics: StoneCeremonyHapticPlaying = StoneCeremonyHaptics.shared)`
    - `func start(at: Date)`: called on the hero's first appearance; it plays act 1 at that instant. A frozen ceremony plays nothing.
    - `func tick(at: Date)`
    - `func tapOutside(at: Date)`: stops act 1, silently.
    - `func tapContinue(at: Date)`: when Act 2 starts, it stops act 1 and plays act 2 at `now`.
    - `func close()`: stops.
    - It never calls `play` with an empty score.
- **Act 1 values, full mode:**
  - **Thud** at `contact`: intensity [0.55, 0.75, 0.95] by size; sharpness 0.25 (lava), 0.45 (river), 0.80 (gem).
  - **Echo** at `contact + 0.070`: the thud's intensity × 0.35 and its sharpness − 0.10.
  - **Carve ticks** (when carving):
    - n = `max(8, min(40, round(L / max(12, L / 36))))`, where L is `carveLength` in points.
    - Tick j has p = (j + 0.5) / n.
    - Its time is `carveStart + x · carveDuration`, where x solves `inout(x) = p` (bisect 20 times).
    - Intensity `lerp(0.18, 0.30, p)`, sharpness 0.85.
  - **Closing tap** at `closure`: intensity 0.60 / 0.70 / 0.80 and sharpness 0.35 / 0.60 / 0.80, for lava / river / gem.
  - **The floor:** walk the merged events in time order and drop any tick within 25 ms of the last kept event. The thud, the echo and the closing tap are never dropped. A tick within 25 ms before the closing tap is dropped.
- **Act 2 values, full mode:**
  - The Continue tick at 0: 0.30 / 0.45.
  - The landing tap at `landing`: intensity [0.45, 0.55, 0.65] by size, sharpness 0.50.
- **Reduce Motion and Low Power:** act 1 is one tap (0.50 / 0.40) at `contact`; act 2 is the Continue tick only.
- **Quiet, or `hapticsOn == false`:** both acts are `[]`.

- [ ] **Step 1: Write the failing score tests:**
  - `thudEchoClose`: river medium, L = 300.
    - `[0]` is (0.32, 0.75, 0.45) and `[1]` is (0.39, 0.2625, 0.35).
    - The last event is (1.40, 0.70, 0.60).
    - Every event between them has sharpness 0.85 and intensity in [0.18, 0.30], non-decreasing.
    - The tick count is 25 minus the floored ones.
  - `byValence`:
    - The thud is 0.55 for small and 0.95 for large; its sharpness is 0.25 for lava and 0.80 for gem.
    - The closing tap is lava (0.60, 0.35) and gem (0.80, 0.80).
  - `floor`: a gem with L = 2000 (n = 36 in 0.5 s): every adjacent pair is ≥ 0.025 apart, and the thud, the echo and the closing tap are present.
  - `noGlyph`: act 1 is exactly the thud and the echo.
  - `neverBeforeContact`: for every valence and mode whose act 1 is not empty, `act1.first!.at >= timeline.contact`.
  - `act2`: medium is `[(0, 0.30, 0.45), (0.38, 0.55, 0.50)]`; the landing intensity is 0.45 for small and 0.65 for large.
  - `silent`:
    - quiet (`lowlightLarge`) with `hapticsOn: true` gives `[]` for both acts;
    - `hapticsOn: false` gives `[]` for a river.
  - `calmModes`:
    - Reduce Motion: act 1 is `[(0.25, 0.50, 0.40)]` and act 2 is `[(0, 0.30, 0.45)]`.
    - Low Power: act 1 is `[(0.20, 0.50, 0.40)]`.
  - `pattern`: `StoneCeremonyHaptics.pattern(act1)` does not throw, and its `duration` is within 0.05 of the last event's time.
- [ ] **Step 2: Write the failing driver tests** with a spy (`plays: [([IntroHapticEvent], Date)]`, `stops: Int`):
  - `startPlaysAct1`: one play, at `t0`.
  - `skipIsSilent`: `tapOutside` mid-Act 1 gives `stops == 1` and no new play.
  - `continueMidCarve`: `tapContinue(t0 + 1)` stops, then plays act 2 at `t0 + 1`.
  - A second `tapContinue` plays nothing.
  - `closeStops`: `close()` during Act 2 increments `stops`.
  - `quietPlaysNothing`: no play, ever.
  - `frozenPlaysNothing`: `start` on a frozen ceremony gives no play.
- [ ] **Step 3:** **TEST(StoneCeremonyScoreTests)** and **TEST(StoneCeremonyDriverTests)**. Expected: FAIL.
- [ ] **Step 4:** Implement the three files.
- [ ] **Step 5:** Run both suites again, plus **TEST(IntroHapticsTests)** (unchanged). Expected: PASS.
- [ ] **Step 6:** Commit: `feat(ios): score the stone ceremony's haptics`.
- [ ] **Step 7:** Push and open the Part 1 PR.
  - **Title:** `feat(ios): time the stone reveal ceremony and its haptics`
  - **Body:** start with `Part of #1691`. Add a table of the timeline and haptic values, noting that #1692 and #1693 transcribe it. End with `Data safety: no change`.

## Part 2 — carve and week-row stone

Branch `feat/1691-ceremony-carve-ios`, stacked on Part 1. Nothing that ships changes.

### Task 5: Carve and lift shadow

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Stone/Lit/LitStonePainter.swift`.
  - Extract `drawGlyph`'s line building into a private helper, so that `drawGlyph` and `paintCarve` share it.
  - `paint`'s output must not change.
- Create: `apps/ios/Pebbles/Features/Path/Stone/Lit/LitStoneCarve.swift`
- Create: `apps/ios/Pebbles/Features/Path/Stone/Lit/LitStoneLiftShadow.swift`
- Test: `apps/ios/PebblesTests/Stone/Lit/LitStoneCarveTests.swift`

**Interfaces:**
- Consumes: `LitStoneSpec`, `LitStoneScene`, `LitGlyphLine.line(d:side:)`, `LitStoneCache.padding(_:box:scale:)`.
- Produces:
  - `extension LitStoneSpec { var uncarved: LitStoneSpec }`: the same spec with `glyph: nil`.
  - On `LitStonePainter`:
    - `static func paintCarve(_ spec: LitStoneSpec, in context: inout GraphicsContext, box: CGSize, scale: CGFloat, lip: Double, ink: Double, inkOpacity: Double)`.
      - It clips to the body and applies the night matrix when `spec.night`.
      - It trims the strokes cumulatively by length, in glyph order (`Path.trimmedPath`).
      - A flat spec paints the ink only.
    - `static func carveLength(_ spec: LitStoneSpec, box: CGSize, scale: CGFloat) -> CGFloat`: the total on-screen length of the ink lines, in points; 0 without a glyph.
    - `static func paintLiftShadow(_ spec: LitStoneSpec, in context: inout GraphicsContext, box: CGSize, lift: Double)`: the far shadow at offset × (1 + lift), blur × (1 + 1.2 · lift) and alpha × 0.5 · lift. It paints nothing at lift 0 or for a flat spec.
  - `struct LitStoneCarve: View`, with `init(spec: LitStoneSpec, lip: Double, ink: Double, inkOpacity: Double)`.
  - `struct LitStoneLiftShadow: View`, with `init(spec: LitStoneSpec, lift: Double)`.
  - Both views lay out exactly like `LitStoneImage`: the outline's aspect, unclipped, offset by the same padding, reading the scheme into `night`. Each is a live `Canvas`.

- [ ] **Step 1: Write the failing tests.** Paint at scale 3 into a 158 × 158 box, with river medium and `LitStonePainterTests`' fixture glyph.
  - `carveCompletesTheStone`: the uncarved image with `paintCarve(lip: 1, ink: 1, inkOpacity: 1)` drawn over it equals `paint(spec)` within ±2 per channel, on paper and at night.
  - `zeroTrimPaintsNothing`: every pixel has alpha 0.
  - `halfTrimPaintsLess`: the count of inked pixels at `ink: 0.5` is strictly between the counts at 0 and at 1.
  - `carveLengthScales`:
    - `carveLength` at 158 divided by `carveLength` at 44 is within 1% of 158 / 44;
    - it is 0 with no glyph.
  - `liftShadow`:
    - lift 0 paints nothing;
    - lift 1 paints pixels below and right of the body's frame;
    - a flat spec paints nothing.
- [ ] **Step 2:** **TEST(LitStoneCarveTests)**. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the suites below. Expected: PASS, with the same References failing set and byte-identical PNGs.
  - **TEST(LitStoneCarveTests)**
  - **TEST(LitStonePainterTests)**
  - **TEST(LitStoneCacheTests)**
  - **TEST(ThemeLiteralsTests)**
  - **TEST(References/SpineStoneReferences)**
- [ ] **Step 5:** Commit: `feat(ios): carve the vector stone's glyph line by line`.

### Task 6: The week row draws the stone

**Files:**
- Modify: `apps/ios/Pebbles/Features/Complete/Steps/CompleteRicherStep.swift` (`RicherWeekRow` only).
- Create: `apps/ios/Pebbles/Features/Complete/Ceremony/StoneCeremonyAnchors.swift`
- Test: `apps/ios/PebblesTests/Features/Complete/RicherWeekRowTests.swift` (extend it)

**Interfaces:**
- Consumes: `StoneCeremonyLayout.landingSize` and `slotHeight` (Task 1), `LitStoneImage`.
- Produces:
  - Two preference keys, `struct StoneCeremonyCentreKey: PreferenceKey` and `struct StoneCeremonyCellKey: PreferenceKey`. Each has `Value = Anchor<CGRect>?`, and `reduce` keeps the first non-nil.
  - `RicherWeekRow(date: Date, stone: LitStoneSpec? = nil, dayOpacity: [Double]? = nil, stoneOpacity: Double = 1)`:
    - With a stone, every slot is `slotHeight(stone.size)` tall. The marked slot draws `LitStoneImage` at `landingSize`, faded by `stoneOpacity`, and publishes `StoneCeremonyCellKey` from its bounds.
    - With no stone, it draws today's 10 pt dot, so nothing that renders today changes.
  - `static func markedIndex(around: Date, calendar: Calendar) -> Int?`.

- [ ] **Step 1:** Extend the tests:
  - `markedIndex` for Wednesday 4 November 2026 in Paris, Monday first, is 2;
  - for a Sunday it is 6.
- [ ] **Step 2:** **TEST(RicherWeekRowTests)**. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** **TEST(RicherWeekRowTests)** passes. **TEST(References/RecordFlowReferences)** gives byte-identical PNGs.
- [ ] **Step 5:** Commit: `feat(ios): let the richer week row hold the stone`.
- [ ] **Step 6:** Push and open the Part 2 PR.
  - **Title:** `feat(ios): carve the stone glyph and seat the stone in the week row`
  - **Body:** `Part of #1691`, …, `Data safety: no change`.

## Part 3 — the ceremony on the success step

Branch `feat/1691-ceremony-step-ios`, stacked on Part 2.

### Task 7: The stage and the richer layout

**Files:**
- Create: `apps/ios/Pebbles/Features/Complete/Ceremony/StoneCeremonyStage.swift`
- Modify: `apps/ios/Pebbles/Features/Complete/Ceremony/StoneCeremonyLayout.swift`, adding the flight helpers.
- Modify: `apps/ios/Pebbles/Features/Complete/Steps/CompleteRicherStep.swift`.
- Test: `apps/ios/PebblesTests/Features/Complete/Ceremony/StoneCeremonyTimelineTests.swift` (the flight cases)
- Test: `apps/ios/PebblesTests/References/RecordFlowReferences.swift`

**Interfaces:**
- Consumes: Tasks 1–6, `StoneRipple`, `StoneGlint`, `StoneRingGeometry.forStone(_:)`, `StoneGlintInk.strength(_:)`.
- Produces:
  - `static func flightRect(from: CGRect, to: CGRect, progress: Double) -> CGRect`: a linear interpolation of the origin and the size.
  - `static func flightCrossfade(_ progress: Double) -> Double`: 0 up to 0.6, then smoothstep to 1 at 1.
  - `struct StoneCeremonyStage: View`, with `init(spec: LitStoneSpec, valence: Valence, frame: StoneCeremonyFrame, centre: CGRect, cell: CGRect, bounds: CGSize)`. Bottom to top, it draws:
    - the hero waves, around `centre`, clipped to `bounds` (trimmed to the screen);
    - `LitStoneLiftShadow`;
    - the stone. While a trim or `inkOpacity` is below 1, that is `LitStoneImage(spec.uncarved)` with `LitStoneCarve` over it; after that it is `LitStoneImage(spec)`. In flight, the hero-size and landing-size images are drawn at `flightRect` and cross-faded (D3).
    - `StoneGlint`;
    - the landing waves, around `cell`.
    - It is scaled by `stoneScale`, faded by `stoneOpacity`, `allowsHitTesting(false)` and `accessibilityHidden(true)`.
  - `CompleteRicherStep` gains a trailing init parameter, `frozen: StoneCeremonyFreeze? = nil`, and changes as follows.
    - The 240 pt `PebbleStoneView` goes. The final layout is a `ScrollView` holding the name, the karma line or the calm sentence, the week row, and the MAKE IT RICHER header and rows.
    - The bottom bar is a `.safeAreaInset(edge: .bottom)` holding:
      - the quiet Get support above, at `actionsOpacity`;
      - one `PebblesPrimaryButtonStyle` capsule. Its label cross-fades Continue → Done by `buttonLabel`. Its action is `tapContinue` until settled, then `onExit`.
      - Record another, at `actionsOpacity`, sharing its slot with the quiet pause link (`supportUnderContinue`).
    - The ceremony:
      - `@State` holds the `StoneCeremonyDriver?`. It is built in `.onAppear`, from the reduce-motion environment, `isLowPowerModeEnabled`, `HapticsPreference.isOn()`, `markedIndex`, and `carveLength` at `heroSize`. `start(at: .now)` is called from the stage's first `.onAppear`. A frozen ceremony is built in `init`.
      - A `TimelineView(.animation(paused: !ceremony.isRunning))` samples `frame(at:)`.
      - `.task(id: phase)` sleeps until `endsAt()`, then calls `tick`.
      - A `contentShape` tap on the step calls `tapOutside`.
      - `onDisappear` calls `close()`.
    - The centre slot is a clear `heroSize` frame at the centre of the step's bounds, publishing `StoneCeremonyCentreKey`.
    - The name and the karma line are offset by `textDrop × (centre.maxY + 26 − nameRestY)`.
    - Each visible row takes the next `rowOpacity`/`rowRise` index.
    - The rows and the bottom bar's Done/Record another use `.allowsHitTesting(accepts)` and `.accessibilityHidden(!accepts)`, where `accepts = ceremony.acceptsRicherInput(at: now)`. During the ceremony, VoiceOver reads the stone's name and Continue.
    - The week row gets `stone: spec`, `dayOpacity: frame.dayOpacity` and `stoneOpacity: frame.cellStoneOpacity`.
    - With `happenedAt == nil`, a `landingSize` slot above the name publishes `StoneCeremonyCellKey` instead of the week row (D12), at `cellStoneOpacity`.
    - `heroReach` is `heroWaveReach(screenWidth: bounds.width, heroWidth: heroSize.width, wave: k)`, so the driver is built inside the step's `GeometryReader`.

- [ ] **Step 1: Baseline.** On the untouched Part 2 head, run **TEST(References)**. Save the failing list, and copy `build/reference-diffs/` to `$SCRATCH/ref-baseline/`.
- [ ] **Step 2: Write the failing tests.**
  - Flight cases:
    - `flightRect` at 0 is `from`, and at 1 is `to`;
    - at 0.5 its midX and its width are the midpoints;
    - `flightCrossfade(0.6) == 0` and `flightCrossfade(1) == 1`.
  - References in `RecordFlowReferences`, using `richer(frozen:valence:happenedAt:)`:
    - `richerPause(scheme:)`: both schemes, `.pause`, `highlightMedium` with a glyph;
    - `richerPauseQuiet()`: Paper, `lowlightLarge`, the calm sentence and Get support under Continue;
    - `richerPauseAx2()`: Paper at `.accessibility2`;
    - `richer(scheme:)`: now `.settled`;
    - `richerNoDay()`: Paper, `.settled`, `happenedAt: nil`.
- [ ] **Step 3:** **TEST(StoneCeremonyTimelineTests)**. Expected: FAIL.
- [ ] **Step 4:** Implement.
- [ ] **Step 5:** Run **TEST(StoneCeremonyTimelineTests)** and the whole unit suite without References. Expected: PASS.
  - Run **TEST(References)**. Expected: `richer` has moved and the new references have no baseline; everything else matches the Step 1 set byte for byte. List them in the PR.
  - Visual check. In a temporary `ImageRenderer` test, render frames 0, `contact`, `carveStart + 0.3`, the pause, Act 2 at 0.2 and the settled frame to PNGs on the host. Compare them with the prototype's focus view, then delete the test.
- [ ] **Step 6:** Commit: `feat(ios): stage the stone ceremony on the richer step`.

### Task 8: The flow plays it; records

**Files:**
- Modify: `apps/ios/Pebbles/Features/Complete/CompleteFlowModel.swift`. `succeed(with:quiet:)` no longer fires a haptic. Its doc comment says the ceremony plays it.
- Modify: `apps/ios/Pebbles/Features/Complete/CompleteFlowView.swift`, with three changes:
  - The success step enters, and the life step leaves into it, with `.opacity`, not `.move`.
  - `RecordFlowChrome(dotCount: 0, dotIndex: nil, canGoBack: false, onBack: {}, onClose: close)` is shown on `.success` too.
  - `StoneCeremonyHaptics.shared.prepare()` is called right after `model.beginCompleting()`.
- Modify: `apps/ios/PebblesTests/Features/Complete/CompleteFlowModelTests.swift`
- Modify: `docs/decisions/log.md`

**Interfaces:**
- Consumes: Task 7's `CompleteRicherStep` and the `StoneCeremonyHaptics.shared` player.

- [ ] **Step 1:** Update the test "success goes to the success step …":
  - after `fail` then `succeed`, `recorder.taps.suffix(1) == [.warning]` and `!recorder.taps.contains(.success)`;
  - rename it to say the ceremony plays the haptic.
  - Keep "quiet success does not buzz".
- [ ] **Step 2:** **TEST(CompleteFlowModelTests)**. Expected: FAIL.
- [ ] **Step 3:** Implement the three changes.
- [ ] **Step 4:** Run **TEST(CompleteFlowModelTests)**, then the whole unit suite without References, then **TEST(References)** against the Task 7 set. Expected: PASS, and the same set.
- [ ] **Step 5: Simulator pass**, side by side with the prototype artifact (frames as in Task 7 Step 5, plus Reduce Motion and the no-day case). No haptic value is tuned. The PR lists the device pass below as **owed by the maintainer**, unchecked; it tunes only the values in `StoneCeremonyScore` and `StoneCeremonyTimeline`.
  - Every valence, with and without a glyph, then quiet.
  - Reduce Motion, Low Power and Haptics off.
  - ✕ during Act 1 and during Act 2.
  - Continue mid-carve, then a double tap.
  - A held-card Complete whose detail has not loaded (no day).
  - The large stone in its week cell. Widen the cell only if it overflows, and record it.
- [ ] **Step 6: Records.**
  - **Decision log.** Append `## <ship date> — the success step is a three-act ceremony with a pause; the haptic moves to the view (#1691)`. It amends record-flow D12 and names D1–D11 above in one line each.
  - **Arkaik.** Confirm that the richer-step view and its acceptances are at `development` (Task 1 Step 0). The GitHub App moves them to `releasing` at merge; never set `live`.
  - **Data safety:** no change.
  - **`Localizable.xcstrings`:** no `New` or `Stale` rows.
- [ ] **Step 7:** Commit: `feat(ios): play the stone reveal ceremony at the end of the record flow`.
- [ ] **Step 8:** Push and open the Part 3 PR.
  - **Body:** `Resolves #1691`, the device-pass notes and tuned values, the moved and new references, the decision entry, and `Data safety: no change`.
  - Once CI's References job has run red, add the `rebaseline-ios-screenshots` label alone.
- [ ] **Step 9:** The controller drops one build-journal pebble for the whole #1691 iOS work: `--size medium --valence highlight --collection iOS`, with a pause-frame screenshot.
