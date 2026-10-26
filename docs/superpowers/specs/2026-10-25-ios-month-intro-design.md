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
- Every existing wrap reference passes unchanged, with no re-baseline. The settled render is today's render.
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

**Revision after the first device pass (2026-10-26).** The maintainer felt Parts 1–4 on the iPhone 15. The wheel was a little fast, and the sections read like lazy loading rather than a reveal. The decisions below supersede the trigger, tempo and list rows described further down, wherever they differ. They ship as Parts 5 and 6.

**D7. The month page reads chapter by chapter.** Each chapter is the cover or one section.
- **One swipe, one chapter:** a swipe moves exactly one chapter in its direction, and the scroll snaps that chapter's top to the top of the page. The next chapter's header peeks below.
- **Tall chapters:** a chapter taller than the page scrolls freely inside itself until its bottom meets the page's bottom. The next swipe then moves on. Swiping back lands on the previous chapter's top, or on its last screen when it is tall.
- **The landing rule:** a pure `ChapterPaging.target(from:proposed:tops:viewport:maxOffset:)` decides where each swipe lands, and a custom iOS 17 `ScrollTargetBehavior` applies it.
- **Measuring:** chapters report their tops in a content coordinate space through a preference.
- **Bottom space:** the page pads its bottom by one viewport, so the last chapter can reach the top.
- **When paging applies:** only while the page has a clock, which already excludes Reduce Motion and VoiceOver. Without one, scrolling is free, as before.
- *Considered and rejected:* `.viewAligned(limitBehavior: .always)`. It aligns to view edges, so the bottom of a chapter taller than the page (large text sizes) could not be reached.

**D8. Arrival, then reveal.**
- **Headers are always drawn:** a section's header, and the insights head over tone or hours, are part of the page, not the reveal, so you see where you're going.
- **The arrive tick:** a chapter enters when its top reaches the top of the page, within 2 pt, or when it covers the top of the page. The 85 % line is gone. Its first beat is a single rounded *arrive* tick (`IntroTick.arrive`), with no visual.
- **Then the reveal:** its elements begin after a pause (`rowsAt` × the tempo, about 0.8 s).
- **Leaving:** swiping on lands it at once, as before.

**D9. Tempo.**
- **One knob per kind:** `IntroTempo` scales a sequence's beats when the clock starts it. The cover uses ×1.45, which takes the garland from 1.5 s to 2.2 s; the sections use ×2.5. Every time and every motion duration is scaled.
- **Haptics:** the 25 ms floor applies to the scaled times, so slower means fewer dropped ticks.
- **References:** frozen clocks (the references) use their beats unscaled.
- **Live tuning:** the Spine Lab's Month tab has two sliders, *Garland* (seconds) and *Sections ×*, applied on *Replay intro*. The values the maintainer settles on become `IntroTempo.standard`.

**D11. The month is a deck of cards** (second device pass, 2026-10-26). It supersedes D7 and D8's trigger. On the phone, the chapter snapping felt awkward, and a section already sitting on the page before its turn felt wrong.
- **The cards:** the cover first, then each section that shows, in the server's order. An empty section deals no card. Tone and hours are separate cards, and the insights head sits on the first of them.
- **The card** (refined the same day, from the deck exploration's galley):
  - **Paper:** the pebble cards' own shell (`SpineCardShell` without an emotion): the stock, the card shadow and the Night ring. Its lines are printed in `cardRule`.
  - **Margin:** ruled off on the left, with the month and year set upright at its top and the stepper down its foot, one segment per card, filled up to this one.
  - **Title band:** the card's first head, with its label on the left, its hint in italics at the far end, and a rule under it. The section leaves that head out (`monthHeadOnCard`), and each section and figure exposes its head once (`MonthHead`).
  - **The cover** has the margin but no title band: the month is its title.
- **The deck:** a vertical `ScrollView` of full-height pages, one card per page, inset from the page's edges. It snaps one card per throw with `.viewAligned(limitBehavior: .always)`; `.paging` let a throw pass a card. `.scrollTransition` makes the paging read as a deck:
  - The top card leaves upward under the finger, lifted and turned a little.
  - The next card waits beneath it. It is counter-offset so it stays in place, slightly smaller, with its foot peeking below the top card, and it grows to full size as it comes on top.
  - A static stack of up to two more card edges below shows how many cards remain.
  - Swiping down brings the previous card back down onto the pile.
- **The Path's pager:** horizontal swipes still move it, because the deck only scrolls vertically.
- **Before its turn,** a card shows its title only (D8's always-drawn heads).
- **Arrival:** a card arrives when it becomes the top card, tracked by `.scrollPosition(id:)`. The clock lands the previous card's sequence and plays this one: the arrive tick, then its content.
- **Revisits:** the month is still a deck, with its cards settled.
- **Content taller than its card** scales down to fit, anchored at its top. This happens only at the larger non-accessibility text sizes or on small phones.
- **When there is no deck:** under Reduce Motion, VoiceOver or an accessibility text size, the month is the vertical page as before, settled, with no clock. `IntroPolicy` gains the text-size condition.
- **The clock no longer measures geometry.** The deck tells it which card is on top (`IntroClock.show(_:)`), and each section's host registers its beats when it appears. `IntroViewport`, `report` and the chapter paging are gone.
- *Considered and rejected:* a custom `ZStack` deck driven by a drag gesture. Its vertical drag fights the pager's horizontal scroll and any scrolling inside a card, and it gives up native momentum and interruption.

**D10. Each card has a signature** (third device pass, 2026-10-26). This replaces the first D10, which was never built. Rows land one at a time, each in its parts, with a haptic per part.
- **Weeks:** the week's cairn builds from the bottom, stone by stone, with a light tick per stone. Then its line and dates rise with a light tick, and its souls pop one by one with a light tick each. Then the next week. An empty week lands its ground line and muted words together on a faint tick.
- **Milestones:** the stone stamps. It comes in at 1.25× and transparent, then presses to size, with a tick at impact that is firmer for a more intense pebble (`IntroTick.seal`). Then the date rises, then the name, then the souls pop one by one, each with a light tick. Then the next milestone.
- **Persons:** unchanged: the glyph, the name, then the ring dot by dot.
- **Gem:** the count comes first. The table's number counts up from 0 to the month's pebbles on the garland's ease-in-out, one tick per pebble, and lands on a firm tick. The 25 ms floor thins the ticks in the fast middle. Then the families' facets light around it, as before.
- **Domains:** for each domain, its name and count rise with a light tick. Then its gauge fills from the left, for longer the fuller it is, with a continuous vibration swelling as it fills (D15), and a light tick as it stops. Then the next domain, with *more* last.

**D12. The month's content changes on every surface** (third device pass).
- **Empty weeks:** every week of the month is listed. An empty one is muted: its mark is a dotted stone where its cairn would stand (the strip's dotted next stone), its line reads "No pebble." ("Aucun galet."), in outline ink, with no souls, and it isn't a button. A month page exists only for a completed month, so an empty week is always past. The weeks' head counts every week. The server already sends empty weeks.
- **Milestones:** a milestone shows its date above its name.
- **Scope:** both changes reach the scrolled page too. Android follows in its own issue.

**D13. Weeks and persons fill their card.** On the deck, their rows share the room under the title band: each row is `room / rows` tall, between the row minimum and a cap. The cairn and the avatar grow with their row, up to their own caps. The card passes its room down in the environment. On the scrolled page, rows keep their size.
- **More at the foot:** on a card, the persons', emotions' and domains' *more* stands at the card's foot. Content that fits is laid out in the whole room (`MonthCardFill`), so a spacer before *more* opens; content past the room keeps its own height and scales down.

**D14. The end card.** After the last section, the deck deals one more card, like the back of the deck:
- **Layout:** the margin and stepper (the stepper counts the end card), no title band, then the cover's whole garland with the month inside it, settled (`MonthCoverGarland`, shared with the cover; no clock reaches it), and a "Replay the month" button.
- **Arrival:** it ticks, and the button rises. `IntroSequence` gains `.end`, which plays last.
- **Replay:** a tap puts the deck back on the cover with a fresh clock, and the ledger forgets the month, so it plays from the start.
- **The scrolled page** has no end card.

**D16. Milestones in their own stones** (the same pass, later). Everywhere, a milestone draws the Spine's own lit stone (`SpineStone`) instead of the flat seal, in a column wide enough for its rings, which draw at 45 % (`SpineStone.rings`, 1 in the Spine). As its stamp meets the paper, it ripples once with the Spine's own press, at the stone's pace whatever the tempo; a card dealt settled does not ripple. On the right, its first photo as a mini polaroid (`MilestoneSnap`): the stock under a rule, the photo in a window of one of three shapes, square, 3:4 or 4:3, the nearest to its own (a 16:9 or 9:16 photo fills a 4:3 or 3:4 window, cropped), then a strip with two faint caption lines, tilted slightly clockwise, keeping within a box as tall as the row allows. Or its souls when it has no photo. In the intro, the polaroid lands as one part where the souls landed one by one. The list's rows are 56 pt, so the rings clear each other. On a card, the rows share the room (up to 104 pt) and the stone grows to the Spine's own size, with the photo's card and the souls growing alongside.

**D17. The weeks card is a calendar of columns** (the same pass, later). It replaces the weeks' rows on a card and their D10 signature; the scrolled page keeps its list, which never plays an intro.
- **Columns:** a column per week, its Monday's number over its month's abbreviation, then its seven days down it, Monday first, level with a margin of weekday letters. A day outside the month stays blank, a day without a pebble is a dotted slot (the garland's socket), and a day with pebbles shows up to three of them as small seals inked as on the garland, from the cover's pebbles. The week's cairn stands at the column's foot, or a dotted stone for an empty week. A tap on a column focuses its week.
- **Intro:** one week at a time. Its date rises with a light tick, then its days go down the column: a slot pops silently, and each pebble pops with a light tick, three at most a day. Then its cairn builds stone by stone, each stone a little firmer, or an empty week's dotted stone pops on a faint tick. Then the next week.

**D15. Continuous haptics.** `IntroBeat` gains an optional `swell` (`IntroSwell`): a continuous Core Haptics event that starts at the beat's time and lasts the swell's duration. Its intensity rises from `from` to `to` along a parameter curve. The 25 ms floor applies to transients only.

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

**`IntroCurve.times(count:duration:)`.** The slow-fast-slow spacing: beat *i* of *n* lands at `duration × easeInOut⁻¹(i / (n − 1))`, with the sine ease-in-out `(1 − cos πt) / 2`. The cubic's middle would space a 31-day garland's ticks 17 ms apart, under the 25 ms floor; the sine's are 32 ms. Progress along the sequence then follows ease-in-out over time. One beat lands at 0.

**The cover (`IntroSequence.cover`)**, times from the sequence's start:

| At | Key | Motion | Tick |
|---|---|---|---|
| 0 | `.kicker` | rise | none |
| 120 ms | `.monthName` | rise | `light` |
| 240 ms | `.year` | rise | none |
| 400 ms + `IntroCurve.times(count: daysInMonth, duration: 1500 ms)`[d] | `.slot(d)`: the socket of an empty day, or the first seal of a day with pebbles | pop | `faint` (socket) or `seal(intensity)` |
| slot time + 35 ms × k | `.seal(d, k)` for k ≥ 1 (a stacked day) | pop | `seal(intensity)`, only if it lands at least 25 ms after the previous tick (its slot, or the previous stacked seal) and at least 25 ms before the next day's slot (the leaf, after the last day); otherwise none, and the day's `.slot(d)` ticks `seal(strongest)`, the strongest intensity among its seals. The 25 ms floor would otherwise drop the stacked tick or the next day's slot. |
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
| gem | `.lead` at 160 ms; at 320 ms `.blank` (the stone as blank paper, its seams and its ticks) rise, none; blank facets fill with the table's paper (`colors.table`), so the surface-coloured seams stay visible; from 520 ms each family in the server's lead order: its facets in sweep order, clockwise, starting after the run's widest gap so a run across the top stays one sweep, 45 ms apart, `.facet(id)` light `facet`; its `.label(slug)` (name and share) rises with its first facet; 220 ms pause between families; `.count` 160 ms after the last facet, rise, `firm`; *more* lands with the count |
| tone | `.header` and the sentence as `.lead`; `.sweep` from 320 ms, span 1200 ms ease-in-out, a mask left to right; each day with pebbles (`up + even + down > 0`) is `.day(d)`, ticking `light` when the sweep reaches it; an even day's circle gets a pulse then (a ring growing and fading over 220 ms, drawn in `chartOverlay` at the proxy's position) |
| hours | `.header` and the sentence as `.lead`; `.dial` (rings, night wash, hour numbers) rise at 320 ms, none; from 520 ms the stones by the time of day they happened, from 00:00, `IntroCurve.times(count:, duration: 1200 ms)`, `.stone(i)` pop `light`, each with its collection dot and its wait spiral; the hour numbers and the busiest window fade in with `.dial`. With the figure empty under the insights head (no tone days, no known moments), only `.insights` plays |

## Score API (`Wrap/Intro/`, Foundation only)

```swift
struct IntroBeat: Hashable, Sendable {
    let key: IntroKey
    let at: TimeInterval          // from the sequence's start
    let duration: TimeInterval    // how long it takes to land: a motion's length, or a sweep's span
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
    static func rows(count: Int, hasLead: Bool, more: Bool) -> [IntroBeat]   // weeks, milestones, domains
    static func persons(rings: [DaySet], hasLead: Bool) -> [IntroBeat]       // a dot at (day + 0.5) / length of its ring
    static func gem(runs: [IntroGemRun]) -> [IntroBeat]                       // lead order
    static func gemRuns(order: [String], facets: [String: String?]) -> [IntroGemRun]  // facets in sweep order
    static func tone(days: [ToneDay], insights: Bool) -> [IntroBeat]
    static func hours(stones: Int, insights: Bool) -> [IntroBeat]            // stone(rank), `ranks(_:)` by time of day
    static func duration(_ beats: [IntroBeat]) -> TimeInterval
}

enum IntroCurve { static func times(count: Int, duration: TimeInterval) -> [TimeInterval] }
```

The builders take what the views already show, after the same `lookups` filtering (souls the device knows, moments it holds). They never take the raw wrap, so a beat never names a row the page doesn't draw. Each view passes its filtered inputs to both its rows and its builder.

## Runtime (`Wrap/Intro/`)

- **`IntroClock`** (`@Observable @MainActor`, one per `MonthPage`):
  - **State:** each sequence is `waiting`, `playing(start)` or `settled`. A waiting sequence in view is the queue.
  - **Answers:** `reveal(sequence, at: Date) -> IntroReveal`, each key's linear progress (0 before the beat, eased to 1 after it, 1 for a key the beats don't name, 1 when settled, 0 while waiting or queued). `isPlaying(sequence)`.
  - **Calls:**
    - `enter(sequence, beats:)`: a sequence came into view; it is queued unless the ledger has it.
    - `leave(sequence)`: it left the viewport.
    - `advance(to:)` (lands the playing one at its end), `settleAll()` (the playing one settles, the queued ones go back to waiting), `skip()` (the playing and queued ones settle and are recorded).
  - It owns the queue (D4) and starts the next sequence when the playing one's last beat plus its motion has passed.
  - Tests and references build a frozen clock: `IntroClock.frozen(month:sequence:beats:elapsed:)`.
- **`IntroLedger`** (on `PathModel`): `contains(month, sequence)`, `record(month, sequence)`, `reset(month)` (the Lab).
- **`IntroHaptics`:**
  - `prepare()` starts the engine when a month page gains focus with something left to play, so the first tick is not late.
  - `play(sequence, of: month, beats, startingAt: Date)` compiles the beats into one player keyed by (month, sequence). The clock's start is `now + 50 ms`. The player starts at `engine.currentTime + max(0, start − now)`, computed after the engine has started and the player is made, so a cold start can't push the ticks behind the frames.
  - `stop(sequence, of: month)` stops at `CHHapticTimeImmediate`. A page only ever stops its own sequences, never another page's.
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
- **Canvas figures**, each with `reveal: IntroReveal = .settled` (`DaySetMarks` takes `introRow: Int?` instead, its keys being its row's):
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
  - *Every existing `Wrap*References` test passes unchanged; none is re-recorded.*
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

Each part builds, lints and passes the iOS tests alone. Every part keeps the existing references passing unchanged.

**Hosting.** Each section hosts its own sequence (`.introSequence`) inside its view, so a section that draws nothing plays nothing. Tone and hours are the exception: the shared insights head lives in `MonthPageSections`, so the page hosts them there, head included, and `ToneFigure` and `HoursFigure` keep their shape where the references lay them out.

**Also:**
- **Arkaik:** one acceptance under the iOS month page view, "the month's intro plays once per launch, ticking each slot", moved to `development` when Part 1 starts.
- **Decision log:** one entry in Part 1, covering D1, D4 and D5.
- **Android:** #1242 references this spec.

**Parts 5 and 6** (the revisions, D7–D15), stacked on Part 4:

| Part | Contents |
|---|---|
| 5 | The deck (D11): `MonthDeck`, the cards, `IntroClock.show`, the beats registered by the hosts, the text-size condition in `IntroPolicy`. Plus `IntroTempo` scaling in the clock, the arrive tick with always-drawn heads (D8), and the Lab's two sliders. The chapter paging (D7) was built, tried on the phone and dropped. |
| 6 | The cards' signatures (D10), the content changes (D12), the filled cards (D13), the end card and its replay (D14), and the swell (D15) |

## New patterns, raised in the PR

- **A choreographed intro driven by a pure score and one clock**, with Core Haptics compiled from the same beats (D1, D5). It is the first of its kind in the app; the launch loader (`HandcraftedLogoView`, 2026-07-17 log entry) is the nearest relative, a timeline-driven draw-on.
- **A figure-level `reveal` parameter defaulting to settled** (D2, D6), the way `Garland` and its siblings stay reference-stable.

## Risks

- **Core Haptics and the System Haptics switch.** I found no authoritative source on whether a `CHHapticEngine` pattern is silenced when Settings → Sounds & Haptics → System Haptics is off, and no public API to read that switch. The device pass decides. If the pattern plays anyway, `IntroHaptics` switches to UIKit generators fired from the clock (D1's hybrid). Those follow the switch, and the score, the clock and the figures stay as they are.
- **Per-frame cost.** The garland redraws up to 31 seals per frame for about 2 s, and the gem 28 facets. Measure on the iPhone 15 with Instruments. If frames drop, the settled marks are drawn once into a `drawingGroup()` layer and only the marks in motion redraw.
- **Haptic density.** The middle of a 31-day garland runs about 25–32 ms apart. The 25 ms floor guards it, but the device pass may raise the garland's duration rather than drop ticks. A person's ring with many days, a busy month's hours (from about 32 stones) and the tone's middle (25.5 ms apart on 31 days) also thin out at the floor. The visual beats stay.
- **Section geometry in a `LazyVStack`.** A section reports its frame only once realised. A section realised already inside the 85 % line enters on its first report, which is the intended behaviour.
- **Engine start latency.** `prepare()` on focus covers the cover's first tick. The player's start is measured after the engine starts, against the clock's start instant.
