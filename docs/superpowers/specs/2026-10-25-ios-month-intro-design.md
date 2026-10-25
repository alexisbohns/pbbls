# iOS month wrap: the animated intro — design

- **Issue:** [#1241 — Animated intro for the month wrap (iOS)](https://github.com/alexisbohns/pbbls/issues/1241) (feat, ui, ios; milestone M64 · New Path)
- **Date:** 2026-10-25
- **Scope:** `apps/ios` only: the month page of the Path (`Features/Path/Wrap/`), its figures, the Spine Lab's Month tab. No schema change, no RPC change, no copy change.
- **Size:** Large. One stack of four parts (§Parts).
- **Depends on:** the iOS Path wraps (#1226, stack #1231, merged 2026-10-24). Read `docs/superpowers/plans/2026-10-24-ios-path-wraps.md` §7 "As built" before touching a figure.
- **Feeds:** the Android mirror, [#1242](https://github.com/alexisbohns/pbbls/issues/1242). Android ports the score (§Score) and the lifecycle (§Lifecycle) as-is; its haptic backend is its own.

## Summary

The month page enters instead of appearing all at once. The month's name rises in first. Then the garland draws itself from the 1st to the last day, one slot at a time. The spacing eases in and out, and each slot ticks: faint on an empty day, firmer on a seal. The leaf ties the ring last. The cover's texts follow. Below the cover, each section plays the first time it scrolls into view: its header, then its rows one by one, a light tick each. The emotions gem lights family by family, one tick per facet, with a pause between families. A person's row lands glyph, then name, then its ring of days dot by dot. The tone chart and the hours dial draw progressively. Every section plays once per month per launch. A tap skips, leaving settles, and Reduce Motion or VoiceOver turns the intro off.

## Goal

- The choreography in §Choreography plays on an iPhone 15. Its haptics are in step with the visuals and no tick is dropped in the garland's fast middle.
- Every existing wrap reference PNG stays byte-identical, with no re-baseline. The settled render is today's render.
- Three new references pin mid-intro frames at fixed clock times.

## Non-goals

- The week page, the week and month sheets, the strip, the Spine weeks. Only the month page plays an intro.
- Sound. The intro is visual and haptic.
- Persisting what has played across launches.
- A user setting to turn the intro off. The system settings (Reduce Motion, VoiceOver and, depending on the device pass, System Haptics) are the switches (Risks).
- The web, which does not render the month page.
- Any change to a figure's layout, data, words or accessibility labels.

## Decisions

**D1. One score, one clock.** A pure `IntroScore` turns each sequence into beats: a time, an `IntroKey` (what appears) and an optional `IntroTick` (how firmly it ticks). One `IntroClock` per month page answers how far in a key is at a given time, as 0…1 already eased. The visuals read that value. The same beats compile into one `CHHapticPattern`, started at the same instant as the sequence. Visuals and haptics therefore share one timeline and cannot drift.
*Considered and rejected:*
- **SwiftUI-native.** A task steps a counter with `Task.sleep` and `.sensoryFeedback(trigger:)` ticks on each change. The `Canvas` figures (garland, gem, rings, dial) would snap instead of easing, because a transition cannot reach inside a `Canvas`. The sleeps drift, and UIKit's generators merge or drop taps about 30 ms apart, which smears the ta-ta-ta.
- **Hybrid** (the clock for visuals, UIKit generators fired when the clock crosses a beat). It keeps the rate limit and ties every tick to frame timing. It remains the fallback backend if Core Haptics turns out to ignore the System Haptics switch (Risks). Only `IntroHaptics`' internals would change.

**D2. The settled render is the default, everywhere.** No `IntroClock` in the environment means every key reads 1. The references, the sheets and any other caller of a figure render exactly as today without opting out. An unknown key also reads 1, so data that arrives mid-play (the cache, then the network) simply draws settled.

**D3. Once per month per launch, per section.** `IntroLedger` is an in-memory set of `(CalendarMonth, IntroSequence)` held on `PathModel`, so it lives as long as the process. A sequence is recorded when it starts. Leaving October before reaching the gem keeps the cover from replaying, but the gem still plays the first time it is seen. Relaunching the app starts fresh.
*Considered and rejected:* replaying on every focus (31 ticks per swipe wears out fast); once per month ever, persisted (it removes the moment from every later visit).

**D4. One voice at a time.** Sequences play in a queue, in page order, with the cover first. A section joins the queue when it enters the viewport. When the playing sequence ends, the next one that is still in the viewport starts. A queued section that has left the viewport by its turn goes back to waiting, unrecorded. A playing section that leaves the viewport entirely settles. The page's haptics are never two patterns layered, and fast scrolling plays only what is looked at.
*This tightens the brainstorm's Part 2, which allowed the cover and a section to play together.* The weeks section is usually on screen under the cover at load. Playing both together would layer two tick patterns, and settling the cover when a section entered would cut the garland short.

**D5. Core Haptics, behind its own type.** `IntroHaptics` owns one `CHHapticEngine` for the intro and plays each sequence as one `CHHapticAdvancedPatternPlayer` of transients. It is not `HapticsService` (karma, the glyph slider) and not `TapHaptics` (UI taps), as both types' doc comments ask. It is reached through the environment and is nil by default, so the references, the tests and the sheets are silent.

**D6. Canvas figures take a reveal, SwiftUI parts take a modifier.** Each `Canvas` figure gains one parameter, `reveal: IntroReveal = .settled`, a value type that maps a key to 0…1. SwiftUI parts take `.introReveal(_ key:)`, which reads the clock and the sequence from the environment. No figure's layout changes. Hidden elements keep their frames, so nothing jumps when they land.

## Choreography

Every time and strength here is a starting value, kept as a `static let` in `IntroScore` and `IntroTick` and tuned on the device.

**Shared motions**

| Motion | Definition |
|---|---|
| **rise** (SwiftUI parts) | 280 ms ease-out: opacity 0 → 1, offset y 6 pt → 0 |
| **pop** (Canvas marks: seals, sockets, dots, stones, glyphs) | 220 ms: scale 0.4 → 1 with a 1.08 overshoot, opacity 0 → 1 over its first 120 ms |
| **light** (gem facets) | 180 ms ease-out: the facet's fill from the page's paper to its colour |
| **span** (sweeps: the ring line, the tone mask, the garland course) | linear progress over the span's duration, eased by the span's own curve |

**Tick presets (`IntroTick`)**, intensity / sharpness:

| Preset | Values | Used by |
|---|---|---|
| `faint` | 0.22 / 0.25 | an empty day on the garland |
| `light` | 0.40 / 0.45 | headers, rows, texts, soul glyphs, ring dots, tone days, hour stones |
| `seal(i)` | 0.50 · 0.68 · 0.85 (intensity 1 · 2 · 3) / 0.60 | a seal on the garland |
| `facet` | 0.60 / 0.55 | a gem facet |
| `knot` | 0.75 / 0.35 | the garland's leaf |
| `firm` | 0.90 / 0.70 | the gem's count |

Within one voice, a transient closer than **25 ms** to the previous one is dropped from the pattern. The visual beat stays.

**`IntroCurve.times(count:duration:)`.** The slow-fast-slow spacing: beat *i* of *n* lands at `duration × easeInOut⁻¹(i / (n − 1))`, with the cubic ease-in-out. Progress along the sequence then follows ease-in-out over time. One beat lands at 0.

**The cover (`IntroSequence.cover`)**, times from the sequence's start:

| At | Key | Motion | Tick |
|---|---|---|---|
| 0 | `.kicker` | rise | none |
| 120 ms | `.monthName` | rise | `light` |
| 240 ms | `.year` | rise | none |
| 400 ms + `IntroCurve.times(count: daysInMonth, duration: 1500 ms)`[d] | `.slot(d)`: the socket of an empty day, or the first seal of a day with pebbles | pop | `faint` (socket) or `seal(intensity)` |
| slot time + 35 ms × k | `.seal(d, k)` for k ≥ 1 (a stacked day) | pop | `seal(intensity)` |
| with `.slot(d)` | `.numeral(d)` (the 1st, Mondays, the last) | rise without the offset | none |
| span over the slots | the dotted course | trimmed to the newest slot | none |
| last slot + 120 ms | `.leaf` | pop | `knot` |
| leaf + 200 ms, 60 ms apart | `.soul(i)`: the glyphs inside the ring | pop | `light` |
| then 140 ms apart | `.summary`, `.names` (with its label), `.colophon` | rise | `light` |

**A section** (`IntroSequence.section(MonthSection)`): `.header` at 0 (rise, `light`), its lead line if any at 160 ms (rise, none), then rows from 320 ms, 70 ms apart. *more* lands with the last row and stays silent. For tone and hours, whichever comes first also carries the shared insights head as `.insights` at 0 (rise, `light`), and every time after it shifts by 160 ms.

| Section | Rows |
|---|---|
| weeks, milestones | `.row(i)`: the leading mark pops, the text column rises, `light` |
| domains | `.row(i)`: the row rises and its bar grows from 0 to its share over 280 ms ease-out, `light` |
| persons | per row, strictly in sequence: `.glyph(i)` pop `light` → 90 ms → `.name(i)` (name and count) rise `light` → 90 ms → `.ring(i)` span 450 ms ease-in-out (the line draws clockwise from the 1st) → each `.dot(i, day)` pops as the line reaches it, `light`. The next row starts 60 ms after the ring closes. In calendar mode the dots pop in reading order over the same 450 ms. |
| gem | `.lead` at 160 ms; at 320 ms `.blank` (the stone as blank paper, its seams and its ticks) rise, none; from 520 ms each family in the server's lead order: its facets in angle order, clockwise from 12 o'clock, 45 ms apart, `.facet(id)` light `facet`; its `.label(slug)` (name and share) rises with its first facet; 220 ms pause between families; `.count` 160 ms after the last facet, rise, `firm` |
| tone | `.header` and the sentence as `.lead`; `.sweep` from 320 ms, span 1200 ms ease-in-out, a mask left to right; each day with pebbles (`up + even + down > 0`) is `.day(d)`, ticking `light` when the sweep reaches it; an even day's circle gets a pulse then (a ring growing and fading over 220 ms, drawn in `chartOverlay` at the proxy's position) |
| hours | `.header` and the sentence as `.lead`; `.dial` (rings, night wash, hour numbers) rise at 320 ms, none; from 520 ms the stones by the time of day they happened, from 00:00, `IntroCurve.times(count:, duration: 1200 ms)`, `.stone(i)` pop `light`, each with its collection dot and its wait spiral |

## Score API (`Wrap/Intro/`, Foundation only)

```swift
struct IntroBeat: Hashable, Sendable {
    let key: IntroKey
    let at: TimeInterval          // from the sequence's start
    var span: TimeInterval = 0    // > 0 for a sweep
    var tick: IntroTick?
}

enum IntroSequence: Hashable, Sendable { case cover, section(MonthSection) }

enum IntroKey: Hashable, Sendable {
    case kicker, monthName, year, slot(Int), seal(Int, Int), numeral(Int), course, leaf, soul(Int)
    case summary, names, colophon
    case header, lead, row(Int), more
    case glyph(Int), name(Int), ring(Int), dot(Int, Int)
    case blank, facet(String), label(String), count
    case insights, sweep, day(Int), dial, stone(Int)
}

struct IntroTick: Hashable, Sendable { let intensity: Float; let sharpness: Float }  // + the presets

enum IntroScore {
    static func cover(days: [CoverDay], daysInMonth: Int, numbered: [Int], souls: Int,
                      lines: [IntroKey]) -> [IntroBeat]                    // the texts drawn: ⊆ summary, names, colophon
    static func rows(count: Int, hasLead: Bool) -> [IntroBeat]               // weeks, milestones, domains
    static func persons(rings: [DaySet], mode: DayMode) -> [IntroBeat]
    static func gem(runs: [(slug: String, facets: [String])]) -> [IntroBeat]  // lead order, facets already in angle order
    static func tone(days: [ToneDay], hasInsightsHead: Bool) -> [IntroBeat]
    static func hours(stones: Int, hasInsightsHead: Bool) -> [IntroBeat]     // stones already in time-of-day order
}

enum IntroCurve { static func times(count: Int, duration: TimeInterval) -> [TimeInterval] }
```

The builders take what the views already show, after the same `lookups` filtering (souls the device knows, moments it holds). They never take the raw wrap, so a beat never names a row the page doesn't draw. Each view passes its filtered inputs to both its rows and its builder.

## Runtime (`Wrap/Intro/`)

- **`IntroClock`** (`@Observable @MainActor`, one per `MonthPage`):
  - **State:** each sequence is `waiting`, `queued`, `playing(start: Date, beats:)` or `settled`.
  - **Answers:** `progress(_ key, in sequence, at: Date) -> Double` (0 before the beat, eased to 1 after it, 1 for a key the beats don't name, 1 when settled, 0 while waiting or queued). `isPlaying(sequence)`.
  - **Calls:**
    - `enter(sequence, beats:)`: a sequence came into view; it is queued unless the ledger has it.
    - `leave(sequence)`: it left the viewport.
    - `settle(sequence)`, `settleAll()` (the playing one settles, the queued ones go back to waiting), `skip()` (the playing and queued ones settle and are recorded).
  - It owns the queue (D4) and starts the next sequence when the playing one's last beat plus its motion has passed.
  - Tests and references build a frozen clock: `IntroClock.frozen(_ sequence:, beats:, at: TimeInterval)`.
- **`IntroLedger`** (on `PathModel`): `contains(month, sequence)`, `record(month, sequence)`, `reset(month)` (the Lab).
- **`IntroHaptics`:**
  - `prepare()` starts the engine when a month page gains focus, so the first tick is not late.
  - `play(sequence, beats, at: Date)` compiles the beats and starts the player with the clock's start instant. The clock's start is `now + 50 ms`, and the player starts at `engine.currentTime + 0.05`.
  - `stop(sequence)` and `stopAll()` stop at `CHHapticTimeImmediate`.
  - The compile step, `IntroHaptics.events(for: [IntroBeat]) -> [(at: TimeInterval, tick: IntroTick)]`, applies the 25 ms floor and is a pure, tested function.
  - The engine's reset and stopped handlers restart it lazily, as `HapticsService` does.
  - There is no hardware, or the engine fails to start: it logs once with `Logger(subsystem: "app.pbbls.ios", category: "intro")` and plays nothing. The visuals still play.
- **`IntroPolicy.plays(reduceMotion:voiceOver:) -> Bool`.** False makes the page build no clock, so everything is settled (D2) and nothing is recorded.

## View hooks

- **`MonthPage`:**
  - Builds the clock when `IntroPolicy` allows it and gives the ScrollView a named coordinate space.
  - Measures its viewport with `onGeometryChange`, as `SpineWeekView` does.
  - Puts the clock in the environment, along with `IntroHaptics` from `PathView`.
  - Settles everything on focus loss and on `scenePhase` leaving `.active`.
  - Skips on any tap: a `simultaneousGesture(TapGesture())` calls `clock.skip()`, which settles and records the playing and queued sequences and stops their haptics (§Lifecycle). The tap still does its job (a milestone opens its held card, *more* opens the sheet).
  - Takes `ledger: IntroLedger?` (nil: no intro).
- **The section host** (`.introSequence(_ sequence:, beats:)`, on the cover and on each section root):
  - Sets the sequence in the environment.
  - Reports its frame in the page's space. It enters when its top passes 85 % of the viewport's height and its bottom is below the top, and leaves when it is fully out. `onAppear` is not used, because `LazyVStack` builds rows before they are on screen.
  - Wraps its content in `TimelineView(.animation(paused: !clock.isPlaying(sequence)))`, so only the playing sequence redraws per frame, and only while it plays.
- **`.introReveal(_ key:, motion: .rise)`:** the opacity and the offset, from the environment's clock, sequence and timeline date.
- **`MarkRow`** gains `reveal: MarkRowReveal?`, one key each for the leading mark, the text column and the trailing view, so a person's glyph, name and ring land apart. Nil by default.
- **Canvas figures**, each with `reveal: IntroReveal = .settled`:
  - **`Garland`:** the per-slot pop, the course trimmed to the newest slot, the leaf. Its numerals use `.introReveal(.numeral(d))`.
  - **`DaySetMarks`:** the ring's line trimmed by `.ring(i)`, each dot's pop by `.dot(i, day)`, and the calendar's dots likewise.
  - **`GemStone`:** each facet's fill, interpolated from the page's paper (`Color.pebbles.surface`) to its colour by `.facet(id)`.
  - **`HoursDial`:** each stone with its dot and wait spiral, the rings by `.dial`.
  - **`ToneChart`** is not rebuilt per frame. Only its mask, a leading-aligned rectangle, reads the clock (`.sweep`), and so does the `chartOverlay` drawing an even day's pulse at `.day(d)`.
- **The Spine Lab's Month tab:** a *Replay intro* button calls `ledger.reset(october)` and rebuilds the page's clock. This is how the feel is tuned on the device without relaunching.

## Lifecycle

- **The cover starts** when the month page is the focused page *and* its wrap is loaded. Focus moves when a swipe settles, a strip tap lands or a week link lands. A page showing `MonthPagePlaceholder` starts when the wrap arrives.
- **Before its turn**, a sequence draws its before state: blank paper where its elements will be, the layout already in place. A month page sliding in mid-swipe is empty paper, and its name rises once the swipe settles. A page that slid in drawn and then blanked to replay would flash.
- **Data changing mid-play:**
  - A key the running beats don't name draws settled (D2).
  - A key that has gone simply disappears.
  - Nothing restarts.
- **Settling:** the page losing focus, the app leaving `.active`, and a playing section leaving the viewport. Each settles the playing sequence at once and stops its haptics at once. Queued sequences go back to waiting, unrecorded.
- **Skipping:** a tap on the page settles the playing sequence *and* every queued one, and records them, because the reader chose to skip what is on screen. Sections entering later still play.
- **Scrolling never skips:** it is how sections enter.

## Accessibility

- **No intro** under Reduce Motion or while VoiceOver is running. The page builds no clock and renders settled from its first frame. Hidden and moving elements would fight VoiceOver's focus, and `SpinePager` already announces the month.
- **Labels never change.** A row's accessibility label is the same before, during and after its reveal; the intro only draws.
- **Dynamic Type** changes nothing here: the reveals act on frames that are already laid out.

## Testing (Swift Testing; never XCTest)

- **`IntroCurve`:**
  - The first beat is at 0 and the last at the full duration, and the times strictly increase.
  - The middle gaps are shorter than the end gaps.
  - One beat lands at 0; zero beats give `[]`.
- **`IntroScore`:**
  - **Cover:** one `.slot` beat per day, one `.seal` beat per stacked seal 35 ms after its slot, and the leaf last. Seal strengths scale with intensity, and the souls and texts come after the leaf.
  - **Rows:** 70 ms apart, *more* with the last row.
  - **Persons:** 2 beats, plus a ring span, plus one dot per day present for each row. Row n + 1's glyph comes after row n's ring closes.
  - **Gem:** each family gets as many facet beats as the server gave it facets, in the lead order and in angle order, with the 220 ms pause between families and the count last.
  - **Tone:** a tick only on days with pebbles.
  - **Hours:** one stone beat per stone, in time-of-day order.
- **`IntroClock`:**
  - A key reads 0 before its beat, eases to 1 after it, and reads 1 when the beats don't name it.
  - `settle`, `settleAll` and `skip` work as specified, including what each does to the queue and the ledger.
  - The queue plays in page order, skips a section that has left the viewport, and settles a playing section that leaves.
- **`IntroLedger`:** once per (month, sequence); `reset` clears one month only.
- **`IntroHaptics.events`:** beats with ticks become events at the same times; the 25 ms floor drops the later of two close transients.
- **`IntroPolicy`:** Reduce Motion or VoiceOver turns the intro off.
- **References:**
  - *Every existing `Wrap*References` PNG stays byte-identical; none is re-recorded.*
  - New references with a frozen clock:
    - `intro-garland-half` (Part 1): the cover at 1150 ms.
    - `intro-persons-ring` (Part 2): the persons section with row 0's ring half drawn.
    - `intro-gem-second-family` (Part 3): the gem midway through the second family.
  - Paper, EN.
- **Device pass on the iPhone 15**, recorded in the plan's "As built":
  - The feel and the timings.
  - The System Haptics switch (Risks).
  - No tick dropped in the garland's middle on a 31-day month.
  - Swiping away, backgrounding and tapping cut the buzz at once.
  - VoiceOver and Reduce Motion show the page settled.
  - Smooth frames during the garland and the gem (Instruments, Risks).

## Parts (`gh stack`; labels `feat`, `ui`, `ios`, `no-lab-note`; all M64 · New Path)

| Part | Contents | Rough size |
|---|---|---|
| 1 | The engine: `IntroCurve`, `IntroScore.cover`, `IntroClock`, `IntroLedger`, `IntroHaptics`, `IntroPolicy`, `.introReveal`, the section host. **The cover**: the name, the garland, the texts. `MonthPage` and `SpinePager` wiring, the Lab's replay button, the decision log entry, `intro-garland-half`. | ~550 LOC |
| 2 | **Weeks, milestones, domains, persons**: `IntroScore.rows` and `.persons`, `MarkRow`'s reveal, `DaySetMarks`' reveal, `intro-persons-ring`. | ~350 |
| 3 | **The gem**: `IntroScore.gem`, `GemStone`'s reveal, the labels and the count, `intro-gem-second-family`. | ~200 |
| 4 | **Tone and hours**: `IntroScore.tone` and `.hours`, `ToneChart`'s mask, `HoursDial`'s reveal, the shared insights head. | ~250 |

Each part builds, lints and passes the iOS tests alone. Every part keeps the existing references byte-identical.

**Also:**
- **Arkaik:** one acceptance under the iOS month page view, "the month's intro plays once per launch, ticking each slot", moved to `development` when Part 1 starts.
- **Decision log:** one entry in Part 1, covering D1, D4 and D5.
- **Android:** #1242 references this spec.

## New patterns, raised in the PR

- **A choreographed intro driven by a pure score and one clock**, with Core Haptics compiled from the same beats (D1, D5). It is the first of its kind in the app; the launch loader (`HandcraftedLogoView`, 2026-07-17 log entry) is the nearest relative, a timeline-driven draw-on.
- **A figure-level `reveal` parameter defaulting to settled** (D2, D6), the way `Garland` and its siblings stay reference-stable.

## Risks

- **Core Haptics and the System Haptics switch.** I found no authoritative source on whether a `CHHapticEngine` pattern is silenced when Settings → Sounds & Haptics → System Haptics is off, and no public API to read that switch. The device pass decides. If the pattern plays anyway, `IntroHaptics` switches to UIKit generators fired from the clock (D1's hybrid). Those follow the switch, and the score, the clock and the figures stay as they are.
- **Per-frame cost.** The garland redraws up to 31 seals per frame for about 2 s, and the gem 28 facets. Measure on the iPhone 15 with Instruments. If frames drop, the settled marks are drawn once into a `drawingGroup()` layer and only the marks in motion redraw.
- **Haptic density.** The middle of a 31-day garland runs about 25–32 ms apart. The 25 ms floor guards it, but the device pass may raise the garland's duration rather than drop ticks.
- **Section geometry in a `LazyVStack`.** A section reports its frame only once realised. A section realised already inside the 85 % line enters on its first report, which is the intended behaviour.
- **Engine start latency.** `prepare()` on focus covers the cover's first tick. The 50 ms lead absorbs the player's start.
