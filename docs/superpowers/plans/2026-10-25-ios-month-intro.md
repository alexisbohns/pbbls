# iOS month wrap: the animated intro — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The iOS month page enters instead of appearing. The month's name comes first, then the garland drawn day by day, then the texts. Each section then plays as it scrolls into view. A haptic in step with each slot, row, facet, dot and stone. Once per month per launch.

**Architecture:**
- **The score:** a pure `IntroScore` turns each sequence (the cover, each section) into beats: a time, a key, a duration and an optional tick.
- **The clock:** one `IntroClock` per month page plays the sequences, one voice at a time in page order, once per launch (`IntroLedger` on `PathModel`).
- **The views:** each one reads a key's progress through `IntroReader` / `.introReveal`, and every `Canvas` figure takes a reveal that defaults to settled.
- **The haptics:** the same beats compile into one Core Haptics pattern per sequence (`IntroHaptics`).

**Tech Stack:** SwiftUI (iOS 17), Observation, Core Haptics, Swift Charts (the tone figure, as built), Swift Testing, the `ImageRenderer` reference harness.

**Spec:** `docs/superpowers/specs/2026-10-25-ios-month-intro-design.md` (amended in this plan's commit: sine ease-in-out, beat durations, the builders' final signatures). **Issue:** #1241. **Android mirror:** #1242 (not in this plan).

---

## Ground rules (read once, apply everywhere)

- **The settled render is today's render.** No reference PNG that exists today is re-recorded. Every existing `References` test must pass unchanged after each task that touches a figure. A failing existing reference is a bug in the task: fix the code, never the PNG. "No clock in the environment" and `IntroReveal.settled` must draw exactly what the code drew before.
- **iOS 17 APIs only**, `@Observable`, `os.Logger` on every error path, `Color.pebbles.*` only (`ThemeLiteralsTests`), Swift Testing only (never XCTest). No new user-facing strings (the Lab uses `Text(verbatim:)`).
- **References are rendered on CI only.** A new reference fails locally with "no reference … add the rebaseline-ios-screenshots label". That failure is expected for the three new ones (`intro-garland-half`, `intro-persons-ring`, `intro-gem-second-family`) and for nothing else.
- **Commits:** conventional, lowercase, no period, ending with the trailer:

  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  ```
- **Never write a Kritik finding id (`F-…`) in a PR body.**
- **Paths.**
  - The worktree is `/Users/alexis/code/pbbls/.claude/worktrees/ios-month-intro`, and the paths below are relative to it.
  - Run every command from `apps/ios` unless a step says otherwise.
  - Never `cd` to the main checkout. It sits on another branch, and other sessions work there.

## File map

Created (all under `apps/ios/Pebbles/Features/Path/Wrap/Intro/`):

| File | Part | Responsibility |
|---|---|---|
| `IntroBeat.swift` | 1 | `IntroSequence`, `IntroKey`, `IntroTick`, `IntroBeat`, `IntroReveal`: the vocabulary |
| `IntroMotion.swift` | 1 | `IntroMotion` (durations, eases, pop curve), `IntroCurve.times` |
| `IntroScore.swift` | 1 | `IntroScore` constants for the cover, `duration`, `cover(…)` |
| `IntroScore+Sections.swift` | 2 | section head, `rows`, `persons`, `dotPlace` |
| `IntroScore+Gem.swift` | 3 | `IntroGemRun`, `gem`, `gemRuns`, `sweep` |
| `IntroScore+Insights.swift` | 4 | `tone`, `hours`, `ranks` |
| `IntroLedger.swift` | 1 | what has played this launch |
| `IntroHaptics.swift` | 1 | `IntroHapticPlaying`, `IntroHapticEvent`, `IntroHaptics` (Core Haptics, `events(for:)`) |
| `IntroClock.swift` | 1 | `IntroContext`, `IntroViewport`, `IntroPolicy`, `IntroClock` |
| `IntroViews.swift` | 1 | environment values, `IntroSpace`, `IntroReader`, `.introReveal`, `.introSequence` |

Modified:

| File | Part | Change |
|---|---|---|
| `Wrap/Month/Garland.swift` | 1 | `reveal` parameter: slots, seals, course, leaf, numerals |
| `Wrap/Month/MonthCover.swift` | 1 | the cover's reveals, its host, `introBeats` |
| `Wrap/Month/MonthPage.swift` | 1 | builds the clock, coordinate space, tap-to-skip, activity; `MonthPageSections` hosts tone/hours (4) |
| `Spine/SpinePager.swift`, `PathView.swift`, `PathModel.swift` | 1 | pass `IntroContext` down; the ledger |
| `Spine/Debug/SpineLabView.swift` | 1 | *Replay intro* |
| `Wrap/Atoms/MarkRow.swift` | 2 | `MarkRowReveal` |
| `Wrap/Atoms/DaySetMarks.swift` | 2 | `introRow`: ring trim, dot pops |
| `Wrap/Month/MonthSections.swift` | 2 | weeks, milestones, persons, domains reveals and hosts |
| `Wrap/Gem/GemFigure.swift`, `Wrap/Gem/EmotionGem.swift` | 3 | facet lighting, labels, count, host |
| `Wrap/Month/ToneFigure.swift`, `Wrap/Month/HoursFigure.swift` | 4 | sweep mask, pulses, dial reveal, `introBeats` |
| `docs/decisions/log.md` | 1 | one entry |

Tests (under `apps/ios/PebblesTests/`): `Wrap/IntroMotionTests.swift`, `Wrap/IntroScoreCoverTests.swift`, `Wrap/IntroClockTests.swift`, `Wrap/IntroRulesTests.swift`, `Wrap/IntroHapticsTests.swift` (Part 1), `Wrap/IntroScoreSectionsTests.swift` (2), `Wrap/IntroScoreGemTests.swift` (3), `Wrap/IntroScoreInsightsTests.swift` (4), `References/WrapIntroReferences.swift` (1, extended in 2 and 3).

---

## Task 0: Setup (once)

- [ ] **Step 1: Copy the gitignored config into the worktree**

```bash
cd /Users/alexis/code/pbbls/.claude/worktrees/ios-month-intro
cp /Users/alexis/code/pbbls/apps/ios/Config/Secrets.xcconfig apps/ios/Config/Secrets.xcconfig
git status --short   # expect nothing: Secrets.xcconfig is gitignored
```

- [ ] **Step 2: Create this worktree's own simulator**

Another stack's "iPhone 17 wraps" may be booted; never share it.

```bash
xcrun simctl create "iPhone 17 intro" "iPhone 17"
xcrun simctl list devices available | grep "iPhone 17 intro"
```
Expected: one line with a new UDID.

- [ ] **Step 3: Write the test helper into the gitignored `build/`**

Create `apps/ios/build/intro-test.sh`:

```zsh
#!/bin/zsh
# Runs the iOS tests on this worktree's simulator with a private derivedData.
# Usage: build/intro-test.sh [--fresh] [Suite …]   (Suite = e.g. IntroMotionTests, References/WrapIntroReferences)
# --fresh clears the build products: use it after adding files (xcodegen can leave stale objects).
set -e
cd "$(dirname "$0")/.."
xcodegen generate --quiet
if [[ "$1" == "--fresh" ]]; then rm -rf build/Build; shift; fi
args=()
for suite in "$@"; do args+=("-only-testing:PebblesTests/$suite"); done
xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17 intro' \
  -derivedDataPath build -skipMacroValidation -quiet CODE_SIGNING_ALLOWED=NO "${args[@]}"
```

```bash
cd apps/ios && chmod +x build/intro-test.sh
```

- [ ] **Step 4: Record the references' local baseline before any change**

```bash
cd apps/ios && build/intro-test.sh --fresh References 2>&1 | tail -40
```
Expected: the References suite passes. If any reference fails here, on the untouched branch, write its name under "As built" at the bottom of this plan. Later runs are judged against that list, not against zero.

- [ ] **Step 5: Mark the work started in Arkaik**

Read the `arkaik` skill's "Hosted map rules for pbbls" first. Load the tools:

```
ToolSearch: select:mcp__arkaik-mcp__list_nodes,mcp__arkaik-mcp__get_node,mcp__arkaik-mcp__create_node,mcp__arkaik-mcp__add_edge
```

1. Find the iOS month page view node with `list_nodes` (search "month").
2. Create one acceptance: "The month's intro plays once per launch, ticking each slot", platform `ios`, status `development`.
3. Add an edge from the view to the acceptance, as the existing month page acceptances do (read one with `get_node` and copy its shape).

If the `arkaik-mcp` tools are unavailable, say so and stop this step; never edit `docs/arkaik/bundle.json`. Memory note: `ARKAIK_TOKEN` must be exported for writes.

- [ ] **Step 6: Read the `gh-stack` skill**

Read it before the first push. Part 1 is the current branch `feat/1241-ios-month-intro`, and Parts 2–4 stack on it: `feat/1241-ios-intro-sections`, `feat/1241-ios-intro-gem`, `feat/1241-ios-intro-insights`. Also read the memory note on `gh stack` and the rebaseline bot before pushing:
- fetch and fast-forward first
- add `rebaseline-ios-screenshots` alone
- approve the held runs
- the upper branch wins PNG conflicts

---

# Part 1 — the engine and the cover (`feat/1241-ios-month-intro`)

### Task 1: Motion and the slow-fast-slow curve

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Wrap/Intro/IntroMotion.swift`
- Test: `apps/ios/PebblesTests/Wrap/IntroMotionTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

/// #1241 §Choreography: the eases, the pop and the garland's spacing.
@Suite("Intro motion")
struct IntroMotionTests {
    @Test("the eases run from 0 to 1, and the inverse undoes the in-out")
    func eases() {
        #expect(IntroMotion.easeOut(0) == 0)
        #expect(IntroMotion.easeOut(1) == 1)
        #expect(IntroMotion.easeInOut(0) == 0)
        #expect(IntroMotion.easeInOut(1) == 1)
        for x in stride(from: 0.0, through: 1.0, by: 0.05) {
            #expect(abs(IntroMotion.easeInOutInverse(IntroMotion.easeInOut(x)) - x) < 1e-9)
        }
    }

    @Test("a pop grows from 0.4 to 1 past about 1.08, opaque after its first 120 ms")
    func pop() {
        #expect(abs(IntroMotion.popScale(0) - 0.4) < 1e-12)
        #expect(IntroMotion.popScale(1) == 1)
        #expect(abs(IntroMotion.popScale(5.0 / 9.0) - 1.079) < 0.001)
        #expect(IntroMotion.popOpacity(0) == 0)
        #expect(IntroMotion.popOpacity(0.12 / IntroMotion.pop) == 1)
    }

    @Test("a run's beats go slow, fast, slow, from 0 to its duration")
    func curve() throws {
        #expect(IntroCurve.times(count: 0, duration: 1.5).isEmpty)
        #expect(IntroCurve.times(count: 1, duration: 1.5) == [0])
        let times = IntroCurve.times(count: 31, duration: 1.5)
        #expect(times.count == 31)
        #expect(times.first == 0)
        let last = try #require(times.last)
        #expect(abs(last - 1.5) < 1e-12)
        let gaps = zip(times.dropFirst(), times).map { $0 - $1 }
        #expect(gaps.allSatisfy { $0 > 0 })
        let first = try #require(gaps.first)
        let end = try #require(gaps.last)
        let middle = gaps[gaps.count / 2]
        #expect(middle < first)
        #expect(middle < end)
        // The fastest stretch still clears the haptics' 25 ms floor (`IntroHaptics.floor`).
        let fastest = try #require(gaps.min())
        #expect(fastest > 0.025)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd apps/ios && build/intro-test.sh --fresh IntroMotionTests 2>&1 | tail -20`
Expected: build failure, `cannot find 'IntroMotion' in scope`.

- [ ] **Step 3: Write the implementation**

`apps/ios/Pebbles/Features/Path/Wrap/Intro/IntroMotion.swift`:

```swift
import CoreGraphics
import Foundation

/// How a key lands in the month's intro (#1241 §Choreography), from its
/// linear progress 0…1. The durations are the beats'; the views apply the
/// curves. Starting values, tuned on the device.
enum IntroMotion {
    /// A SwiftUI part: fades in and rises `riseOffset`.
    static let rise: TimeInterval = 0.28
    /// A mark in a Canvas: grows from 0.4, overshooting once.
    static let pop: TimeInterval = 0.22
    /// A gem facet: its colour over the blank paper.
    static let light: TimeInterval = 0.18
    static let riseOffset: CGFloat = 6
    /// A pop is opaque after its first 120 ms.
    private static let popFade = 0.12 / pop
    /// Ease-out-back's constant: the pop's scale peaks near 1.08.
    private static let back = 2.0

    static func clamp(_ t: Double) -> Double {
        min(max(t, 0), 1)
    }

    /// Cubic ease-out.
    static func easeOut(_ t: Double) -> Double {
        let u = 1 - clamp(t)
        return 1 - u * u * u
    }

    /// Sine ease-in-out: the slow-fast-slow of the garland, the rings and the
    /// sweeps. Gentler than the cubic, whose middle would put a 31-day
    /// garland's ticks 17 ms apart, under the haptics' floor.
    static func easeInOut(_ t: Double) -> Double {
        (1 - cos(Double.pi * clamp(t))) / 2
    }

    /// When, 0…1, `easeInOut` reaches `y`.
    static func easeInOutInverse(_ y: Double) -> Double {
        acos(1 - 2 * clamp(y)) / Double.pi
    }

    static func popOpacity(_ t: Double) -> Double {
        clamp(t / popFade)
    }

    /// 0.4 to 1, overshooting once (ease-out-back).
    static func popScale(_ t: Double) -> Double {
        let u = clamp(t) - 1
        return 0.4 + 0.6 * (1 + (back + 1) * u * u * u + back * u * u)
    }
}

/// The slow-fast-slow spacing of a run of beats (#1241): beat `i` of `n`
/// lands when the run's eased progress reaches `i / (n − 1)`.
enum IntroCurve {
    static func times(count: Int, duration: TimeInterval) -> [TimeInterval] {
        guard count > 1 else { return count == 1 ? [0] : [] }
        return (0..<count).map { duration * IntroMotion.easeInOutInverse(Double($0) / Double(count - 1)) }
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `cd apps/ios && build/intro-test.sh --fresh IntroMotionTests 2>&1 | tail -20`
Expected: PASS (`** TEST SUCCEEDED **` or no failures with `-quiet`).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Wrap/Intro/IntroMotion.swift apps/ios/PebblesTests/Wrap/IntroMotionTests.swift
git commit -m "feat(ios): add the month intro's eases and spacing

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 2: The vocabulary and the cover's score

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Wrap/Intro/IntroBeat.swift`
- Create: `apps/ios/Pebbles/Features/Path/Wrap/Intro/IntroScore.swift`
- Test: `apps/ios/PebblesTests/Wrap/IntroScoreCoverTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

/// #1241 §Choreography, the cover.
@Suite("Intro score: the cover")
struct IntroScoreCoverTests {
    private func pebble(_ intensity: Int) -> CoverPebble {
        CoverPebble(momentId: "m", emotionId: nil, intensity: intensity, polarity: .highlight)
    }

    @Test("one beat per day from the 1st: an empty day faint, a seal by its intensity, a stacked seal a step later")
    func days() throws {
        let days = [CoverDay(day: 0, pebbles: [pebble(3)]), CoverDay(day: 4, pebbles: [pebble(1), pebble(2)])]
        let beats = IntroScore.cover(days: days, daysInMonth: 31, numbered: [0, 4, 30], souls: 0, lines: [])
        let slots = (0..<31).compactMap { day in beats.first { $0.key == .slot(day) } }
        #expect(slots.count == 31)
        let times = slots.map(\.at)
        #expect(zip(times.dropFirst(), times).allSatisfy { $0 > $1 })
        #expect(times.first == IntroScore.garlandAt)
        #expect(slots[0].tick == .seal(3))
        #expect(slots[1].tick == .faint)
        #expect(slots[4].tick == .seal(1))
        let stacked = try #require(beats.first { $0.key == .seal(4, 1) })
        #expect(abs(stacked.at - slots[4].at - IntroScore.stackGap) < 1e-12)
        #expect(stacked.tick == .seal(2))
        let numerals = beats.compactMap { beat -> Int? in
            if case let .numeral(day) = beat.key { return day }
            return nil
        }
        #expect(numerals == [0, 4, 30])
        #expect(beats.first { $0.key == .numeral(4) }?.at == slots[4].at)
    }

    @Test("the name before the garland; the leaf ties it; then the souls and the lines, in order")
    func order() throws {
        let beats = IntroScore.cover(days: [], daysInMonth: 30, numbered: [], souls: 2, lines: [.summary, .colophon])
        let name = try #require(beats.first { $0.key == .monthName })
        #expect(name.tick == .light)
        #expect(name.at < IntroScore.garlandAt)
        let leaf = try #require(beats.first { $0.key == .leaf })
        let lastSlot = try #require(beats.first { $0.key == .slot(29) })
        #expect(leaf.tick == .knot)
        #expect(leaf.at > lastSlot.at)
        #expect(beats.filter { $0.at > leaf.at }.map(\.key) == [.soul(0), .soul(1), .summary, .colophon])
        #expect(!beats.contains { $0.key == .names })
        let last = try #require(beats.last)
        #expect(IntroScore.duration(beats) == last.at + IntroMotion.rise)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd apps/ios && build/intro-test.sh --fresh IntroScoreCoverTests 2>&1 | tail -20`
Expected: build failure, `cannot find 'IntroScore' in scope`.

- [ ] **Step 3: Write `IntroBeat.swift`**

```swift
import Foundation

/// What enters in a month's intro (#1241): the cover, or one of its sections.
enum IntroSequence: Hashable, Sendable {
    case cover
    case section(MonthSection)
}

/// One element of a sequence, named by what it is; the figures ask for these.
enum IntroKey: Hashable, Sendable {
    /// The cover: the month's name, the garland's dotted course and leaf, the souls' glyphs, the texts; a day's
    /// slot (its socket or its first seal), a stacked seal (day, index), a day's numeral.
    case kicker, monthName, year, course, leaf, summary, names, colophon
    case slot(Int), seal(Int, Int), numeral(Int), soul(Int)
    /// Any section: the insights head (over tone or hours), the head, its lead line, *more*, a row.
    case insights, header, lead, more, row(Int)
    /// A person's row: its glyph, its name, its ring's line, a day on its ring (row, day).
    case glyph(Int), name(Int), ring(Int), dot(Int, Int)
    /// The gem: the blank stone, the count in its table, a facet, a family's name and share.
    case blank, count, facet(String), label(String)
    /// Tone and hours: the sweep, the dial's face, a day of the chart, a stone by its rank in the day.
    case sweep, dial, day(Int), stone(Int)
}

/// How firmly a beat ticks: Core Haptics' intensity and sharpness, 0…1 (#1241 §Choreography).
struct IntroTick: Hashable, Sendable {
    let intensity: Float
    let sharpness: Float

    /// An empty day on the garland.
    static let faint = IntroTick(intensity: 0.22, sharpness: 0.25)
    /// Heads, rows, texts, glyphs, ring dots, tone days, hour stones.
    static let light = IntroTick(intensity: 0.40, sharpness: 0.45)
    static let facet = IntroTick(intensity: 0.60, sharpness: 0.55)
    /// The garland's leaf, tying the ring.
    static let knot = IntroTick(intensity: 0.75, sharpness: 0.35)
    /// The gem's count.
    static let firm = IntroTick(intensity: 0.90, sharpness: 0.70)

    /// A seal on the garland, firmer with its pebble's intensity, 1…3.
    static func seal(_ intensity: Int) -> IntroTick {
        IntroTick(intensity: [0.50, 0.68, 0.85][min(max(intensity - 1, 0), 2)], sharpness: 0.60)
    }
}

/// One beat: `key` starts landing `at` seconds into its sequence, takes
/// `duration` to land, and ticks `tick` as it starts.
struct IntroBeat: Hashable, Sendable {
    let key: IntroKey
    let at: TimeInterval
    let duration: TimeInterval
    var tick: IntroTick?
}

/// Each key's linear progress, 0…1, for a figure to draw (#1241 D6). The
/// default everywhere is `.settled`: every key landed, today's render.
struct IntroReveal: Sendable {
    static let settled = IntroReveal { _ in 1 }
    static let hidden = IntroReveal { _ in 0 }

    private let progress: @Sendable (IntroKey) -> Double

    init(_ progress: @escaping @Sendable (IntroKey) -> Double) {
        self.progress = progress
    }

    func callAsFunction(_ key: IntroKey) -> Double {
        progress(key)
    }
}
```

- [ ] **Step 4: Write `IntroScore.swift`**

```swift
import Foundation

/// The beats of each sequence of a month's intro (#1241 §Choreography). Pure,
/// over what the views already show after their own `lookups` filtering, so a
/// beat never names a row the page doesn't draw. The times are starting
/// values, tuned on the device (this plan's "As built"). The sections' builders
/// live in the extensions beside this file.
enum IntroScore {
    static let nameAt: TimeInterval = 0.12
    static let yearAt: TimeInterval = 0.24
    static let garlandAt: TimeInterval = 0.4
    static let garlandSpan: TimeInterval = 1.5
    static let stackGap: TimeInterval = 0.035
    static let leafAfter: TimeInterval = 0.12
    static let soulsAfter: TimeInterval = 0.2
    static let soulGap: TimeInterval = 0.06
    static let lineGap: TimeInterval = 0.14

    /// When a sequence has landed: its latest beat's end.
    static func duration(_ beats: [IntroBeat]) -> TimeInterval {
        beats.map { $0.at + $0.duration }.max() ?? 0
    }

    /// The cover: the month's name; the garland's days from the 1st (an empty
    /// day's socket, or its seals, a stacked one a step after the first) with
    /// their numerals, under the course drawing round; the leaf; then the
    /// souls' glyphs drawn and the `lines` shown (⊆ summary, names, colophon).
    static func cover(days: [CoverDay], daysInMonth: Int, numbered: [Int], souls: Int,
                      lines: [IntroKey]) -> [IntroBeat] {
        var beats = [
            IntroBeat(key: .kicker, at: 0, duration: IntroMotion.rise),
            IntroBeat(key: .monthName, at: nameAt, duration: IntroMotion.rise, tick: .light),
            IntroBeat(key: .year, at: yearAt, duration: IntroMotion.rise),
            IntroBeat(key: .course, at: garlandAt, duration: garlandSpan)
        ]
        let seals = Dictionary(days.map { ($0.day, $0.pebbles) }, uniquingKeysWith: { first, _ in first })
        let numerals = Set(numbered)
        for (day, time) in IntroCurve.times(count: daysInMonth, duration: garlandSpan).enumerated() {
            let at = garlandAt + time
            let pebbles = seals[day] ?? []
            beats.append(IntroBeat(key: .slot(day), at: at, duration: IntroMotion.pop,
                                   tick: pebbles.first.map { .seal($0.intensity) } ?? .faint))
            for (index, pebble) in pebbles.enumerated().dropFirst() {
                beats.append(IntroBeat(key: .seal(day, index), at: at + stackGap * Double(index),
                                       duration: IntroMotion.pop, tick: .seal(pebble.intensity)))
            }
            if numerals.contains(day) {
                beats.append(IntroBeat(key: .numeral(day), at: at, duration: IntroMotion.rise))
            }
        }
        var at = garlandAt + garlandSpan + leafAfter
        beats.append(IntroBeat(key: .leaf, at: at, duration: IntroMotion.pop, tick: .knot))
        at += soulsAfter
        for soul in 0..<souls {
            beats.append(IntroBeat(key: .soul(soul), at: at, duration: IntroMotion.pop, tick: .light))
            at += soulGap
        }
        for line in lines {
            beats.append(IntroBeat(key: line, at: at, duration: IntroMotion.rise, tick: .light))
            at += lineGap
        }
        return beats
    }
}
```

- [ ] **Step 5: Run it to verify it passes**

Run: `cd apps/ios && build/intro-test.sh --fresh IntroScoreCoverTests 2>&1 | tail -20`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Wrap/Intro/IntroBeat.swift apps/ios/Pebbles/Features/Path/Wrap/Intro/IntroScore.swift apps/ios/PebblesTests/Wrap/IntroScoreCoverTests.swift
git commit -m "feat(ios): score the month cover's intro

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 3: The haptics

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Wrap/Intro/IntroHaptics.swift`
- Test: `apps/ios/PebblesTests/Wrap/IntroHapticsTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

/// #1241 D5: what the intro asks of the Taptic Engine.
@Suite("Intro haptics")
struct IntroHapticsTests {
    @Test("each ticking beat plays at its time; one closer than 25 ms to the last kept is dropped")
    func floor() {
        let beats = [
            IntroBeat(key: .row(1), at: 0.05, duration: 0.28, tick: .facet),
            IntroBeat(key: .header, at: 0, duration: 0.28, tick: .light),
            IntroBeat(key: .lead, at: 0.01, duration: 0.28),
            IntroBeat(key: .row(0), at: 0.02, duration: 0.28, tick: .light)
        ]
        #expect(IntroHaptics.events(for: beats) == [IntroHapticEvent(at: 0, tick: .light),
                                                    IntroHapticEvent(at: 0.05, tick: .facet)])
    }

    @Test("a 31-day garland keeps its name, every day and its leaf")
    func garland() {
        let beats = IntroScore.cover(days: [], daysInMonth: 31, numbered: [], souls: 0, lines: [])
        #expect(IntroHaptics.events(for: beats).count == 33)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd apps/ios && build/intro-test.sh --fresh IntroHapticsTests 2>&1 | tail -20`
Expected: build failure, `cannot find 'IntroHaptics' in scope`.

- [ ] **Step 3: Write `IntroHaptics.swift`**

```swift
import CoreHaptics
import Foundation
import os

/// What the intro's clock asks of the haptics (#1241 D5); the tests stand in a spy.
@MainActor
protocol IntroHapticPlaying: AnyObject {
    /// Starts the engine early, so a sequence's first tick is not late.
    func prepare()
    /// Plays `beats`' ticks as one pattern, starting `lead` seconds from now.
    func play(_ sequence: IntroSequence, beats: [IntroBeat], after lead: TimeInterval)
    func stop(_ sequence: IntroSequence)
    func stopAll()
}

/// One tick of a sequence's pattern.
struct IntroHapticEvent: Hashable, Sendable {
    let at: TimeInterval
    let tick: IntroTick
}

/// The month intro's haptics (#1241 D5): each sequence as one Core Haptics
/// pattern of transients, so the ticks keep their exact times, which UIKit's
/// generators would merge in the garland's fast middle. Its own engine: not
/// `HapticsService` (karma, the glyph slider) nor `TapHaptics` (UI taps). No
/// hardware, or an engine that fails, plays nothing; the visuals still play.
@MainActor
final class IntroHaptics: IntroHapticPlaying {
    static let shared = IntroHaptics()

    /// Two ticks closer than this blur into one buzz: the later one is dropped (its visual beat stays).
    nonisolated static let floor: TimeInterval = 0.025

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "intro")
    private var engine: CHHapticEngine?
    private var players: [IntroSequence: CHHapticAdvancedPatternPlayer] = [:]

    init() {
        guard CHHapticEngine.capabilitiesForHardware().supportsHaptics else { return }
        do {
            let engine = try CHHapticEngine()
            engine.isAutoShutdownEnabled = true
            // The engine stops on backgrounding and route changes; restart it lazily, as `HapticsService` does.
            engine.resetHandler = { [weak engine] in try? engine?.start() }
            self.engine = engine
        } catch {
            logger.error("intro haptic engine init failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    func prepare() {
        guard let engine else { return }
        do {
            try engine.start()
        } catch {
            logger.error("intro haptic engine start failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    func play(_ sequence: IntroSequence, beats: [IntroBeat], after lead: TimeInterval) {
        guard let engine else { return }
        let events = Self.events(for: beats).map { event in
            CHHapticEvent(eventType: .hapticTransient, parameters: [
                CHHapticEventParameter(parameterID: .hapticIntensity, value: event.tick.intensity),
                CHHapticEventParameter(parameterID: .hapticSharpness, value: event.tick.sharpness)
            ], relativeTime: event.at)
        }
        guard !events.isEmpty else { return }
        stop(sequence)
        do {
            try engine.start()
            let player = try engine.makeAdvancedPlayer(with: CHHapticPattern(events: events, parameters: []))
            players[sequence] = player
            try player.start(atTime: engine.currentTime + lead)
        } catch {
            logger.error("intro haptic play failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    func stop(_ sequence: IntroSequence) {
        guard let player = players.removeValue(forKey: sequence) else { return }
        do {
            try player.stop(atTime: CHHapticTimeImmediate)
        } catch {
            logger.warning("intro haptic stop failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    func stopAll() {
        for sequence in Array(players.keys) {
            stop(sequence)
        }
    }

    /// The ticks to play: each ticking beat at its time, in time order,
    /// dropping one closer than `floor` to the last tick kept.
    nonisolated static func events(for beats: [IntroBeat]) -> [IntroHapticEvent] {
        var kept: [IntroHapticEvent] = []
        for beat in beats.sorted(by: { $0.at < $1.at }) {
            guard let tick = beat.tick else { continue }
            if let last = kept.last, beat.at - last.at < floor { continue }
            kept.append(IntroHapticEvent(at: beat.at, tick: tick))
        }
        return kept
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `cd apps/ios && build/intro-test.sh --fresh IntroHapticsTests 2>&1 | tail -20`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Wrap/Intro/IntroHaptics.swift apps/ios/PebblesTests/Wrap/IntroHapticsTests.swift
git commit -m "feat(ios): play the month intro's ticks through core haptics

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 4: The ledger, the viewport rule and the policy

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Wrap/Intro/IntroLedger.swift`
- Create: `apps/ios/Pebbles/Features/Path/Wrap/Intro/IntroClock.swift` (this task writes only its top three types; Task 5 adds the clock)
- Test: `apps/ios/PebblesTests/Wrap/IntroRulesTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

/// #1241 D3, §Lifecycle, §Accessibility.
@Suite("Intro rules")
@MainActor
struct IntroRulesTests {
    @Test("a sequence enters once its top passes 85% of the viewport, and leaves once wholly off it")
    func viewport() {
        func place(_ y: CGFloat) -> IntroViewport.Place {
            IntroViewport.place(CGRect(x: 0, y: y, width: 10, height: 100), viewport: 800)
        }
        #expect(place(600) == .entered)
        #expect(place(-50) == .entered)
        #expect(place(700) == .between)
        #expect(place(800) == .out)
        #expect(place(-100) == .out)
    }

    @Test("no intro under Reduce Motion or while VoiceOver runs")
    func policy() {
        #expect(IntroPolicy.plays(reduceMotion: false, voiceOver: false))
        #expect(!IntroPolicy.plays(reduceMotion: true, voiceOver: false))
        #expect(!IntroPolicy.plays(reduceMotion: false, voiceOver: true))
    }

    @Test("the ledger holds a month's sequence once; a reset forgets that month only")
    func ledger() {
        let ledger = IntroLedger()
        let october = CalendarMonth(year: 2026, month: 10)
        let september = CalendarMonth(year: 2026, month: 9)
        ledger.record(october, .cover)
        ledger.record(september, .section(.gem))
        #expect(ledger.contains(october, .cover))
        #expect(!ledger.contains(october, .section(.gem)))
        ledger.reset(october)
        #expect(!ledger.contains(october, .cover))
        #expect(ledger.contains(september, .section(.gem)))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd apps/ios && build/intro-test.sh --fresh IntroRulesTests 2>&1 | tail -20`
Expected: build failure, `cannot find 'IntroViewport' in scope`.

- [ ] **Step 3: Write `IntroLedger.swift`**

```swift
import Foundation

/// The sequences that have played this launch (#1241 D3): a month's sequence
/// plays once per process. `PathModel` holds the app's; the Lab keeps its own.
@MainActor
final class IntroLedger {
    private struct Entry: Hashable {
        let month: CalendarMonth
        let sequence: IntroSequence
    }

    private var played: Set<Entry> = []

    func contains(_ month: CalendarMonth, _ sequence: IntroSequence) -> Bool {
        played.contains(Entry(month: month, sequence: sequence))
    }

    func record(_ month: CalendarMonth, _ sequence: IntroSequence) {
        played.insert(Entry(month: month, sequence: sequence))
    }

    /// Forgets one month, so its intro plays again (the Lab's replay).
    func reset(_ month: CalendarMonth) {
        played = played.filter { $0.month != month }
    }
}
```

- [ ] **Step 4: Write the start of `IntroClock.swift`**

```swift
import CoreGraphics
import Foundation
import Observation

/// What a month page needs to play its intro: the launch's ledger and the haptics (nil: silent).
struct IntroContext {
    let ledger: IntroLedger
    let haptics: IntroHapticPlaying?
}

/// Where a sequence sits in its page's viewport (#1241 §Lifecycle).
enum IntroViewport {
    enum Place: Equatable {
        case entered, between, out
    }

    /// A sequence enters once its top passes this share of the viewport's height.
    static let entryLine: CGFloat = 0.85

    static func place(_ frame: CGRect, viewport: CGFloat) -> Place {
        if frame.maxY <= 0 || frame.minY >= viewport { return .out }
        return frame.minY < viewport * entryLine ? .entered : .between
    }
}

/// Whether the intro plays at all (#1241 §Accessibility): not under Reduce
/// Motion, not while VoiceOver runs, where hidden and moving parts would fight
/// its focus.
enum IntroPolicy {
    static func plays(reduceMotion: Bool, voiceOver: Bool) -> Bool {
        !reduceMotion && !voiceOver
    }
}
```

- [ ] **Step 5: Run it to verify it passes**

Run: `cd apps/ios && build/intro-test.sh --fresh IntroRulesTests 2>&1 | tail -20`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Wrap/Intro/IntroLedger.swift apps/ios/Pebbles/Features/Path/Wrap/Intro/IntroClock.swift apps/ios/PebblesTests/Wrap/IntroRulesTests.swift
git commit -m "feat(ios): add the month intro's ledger, viewport rule and policy

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 5: The clock

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Wrap/Intro/IntroClock.swift` (append)
- Test: `apps/ios/PebblesTests/Wrap/IntroClockTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

/// A stand-in that records what the clock asked of the haptics.
@MainActor
private final class HapticsSpy: IntroHapticPlaying {
    var prepared = 0
    var played: [IntroSequence] = []
    var stopped: [IntroSequence] = []
    var stoppedAll = 0

    func prepare() { prepared += 1 }
    func play(_ sequence: IntroSequence, beats: [IntroBeat], after lead: TimeInterval) { played.append(sequence) }
    func stop(_ sequence: IntroSequence) { stopped.append(sequence) }
    func stopAll() { stoppedAll += 1 }
}

/// #1241 D3, D4, §Lifecycle.
@Suite("Intro clock")
@MainActor
struct IntroClockTests {
    private let october = CalendarMonth(year: 2026, month: 10)
    private let base = Date(timeIntervalSinceReferenceDate: 1_000)
    private let weeks = IntroSequence.section(.weeks)
    /// Lands at 0.6 s (0.32 + 0.28).
    private let beats = [IntroBeat(key: .header, at: 0, duration: 0.28, tick: .light),
                         IntroBeat(key: .row(0), at: 0.32, duration: 0.28, tick: .light)]

    private func clock(_ ledger: IntroLedger = IntroLedger(), haptics: HapticsSpy? = nil) -> IntroClock {
        let now = base
        return IntroClock(month: october, order: [.cover, weeks, .section(.persons)], ledger: ledger,
                          haptics: haptics, now: { now }, schedules: false)
    }

    @Test("a sequence waits hidden, plays once the page is active, and lands at its end")
    func plays() {
        let ledger = IntroLedger()
        let spy = HapticsSpy()
        let clock = clock(ledger, haptics: spy)
        clock.enter(.cover, beats: beats)
        #expect(clock.phase(.cover) == .waiting)
        #expect(clock.reveal(.cover, at: base)(.header) == 0)
        clock.isActive = true
        let start = base.addingTimeInterval(IntroClock.lead)
        #expect(clock.phase(.cover) == .playing(start))
        #expect(spy.prepared == 1)
        #expect(spy.played == [.cover])
        #expect(ledger.contains(october, .cover))
        let reveal = clock.reveal(.cover, at: start.addingTimeInterval(0.46))
        #expect(reveal(.header) == 1)
        #expect(abs(reveal(.row(0)) - 0.5) < 1e-9)
        // A key the beats don't name has landed.
        #expect(reveal(.row(1)) == 1)
        clock.advance(to: start.addingTimeInterval(0.5))
        #expect(clock.isPlaying(.cover))
        clock.advance(to: start.addingTimeInterval(0.7))
        #expect(clock.phase(.cover) == .settled)
        #expect(spy.stopped == [.cover])
    }

    @Test("one voice at a time, in page order, whatever order they enter in")
    func queue() {
        let clock = clock()
        clock.enter(weeks, beats: beats)
        clock.enter(.cover, beats: beats)
        clock.isActive = true
        #expect(clock.isPlaying(.cover))
        #expect(clock.phase(weeks) == .waiting)
        clock.advance(to: base.addingTimeInterval(1))
        #expect(clock.phase(.cover) == .settled)
        #expect(clock.isPlaying(weeks))
    }

    @Test("a waiting sequence that left the viewport is passed over; a playing one that leaves lands")
    func leaving() {
        let spy = HapticsSpy()
        let clock = clock(haptics: spy)
        clock.enter(.cover, beats: beats)
        clock.enter(weeks, beats: beats)
        clock.isActive = true
        clock.leave(weeks)
        clock.advance(to: base.addingTimeInterval(1))
        #expect(clock.phase(weeks) == .waiting)
        #expect(spy.played == [.cover])
        clock.enter(weeks, beats: beats)
        #expect(clock.isPlaying(weeks))
        clock.leave(weeks)
        #expect(clock.phase(weeks) == .settled)
        #expect(spy.stopped == [.cover, weeks])
    }

    @Test("a tap lands what plays and what waits in view, and counts them as played")
    func skip() {
        let ledger = IntroLedger()
        let spy = HapticsSpy()
        let clock = clock(ledger, haptics: spy)
        clock.enter(.cover, beats: beats)
        clock.enter(weeks, beats: beats)
        clock.isActive = true
        clock.skip()
        #expect(clock.phase(.cover) == .settled)
        #expect(clock.phase(weeks) == .settled)
        #expect(ledger.contains(october, weeks))
        #expect(spy.stoppedAll == 1)
        #expect(spy.played == [.cover])
    }

    @Test("losing focus lands what plays; what waits stays unrecorded and plays on the next focus")
    func focusLoss() {
        let ledger = IntroLedger()
        let clock = clock(ledger)
        clock.enter(.cover, beats: beats)
        clock.enter(weeks, beats: beats)
        clock.isActive = true
        clock.isActive = false
        #expect(clock.phase(.cover) == .settled)
        #expect(clock.phase(weeks) == .waiting)
        #expect(!ledger.contains(october, weeks))
        clock.isActive = true
        #expect(clock.isPlaying(weeks))
    }

    @Test("a sequence played earlier this launch draws settled and never plays again")
    func ledgerHolds() {
        let ledger = IntroLedger()
        ledger.record(october, .cover)
        let spy = HapticsSpy()
        let clock = clock(ledger, haptics: spy)
        clock.enter(.cover, beats: beats)
        clock.isActive = true
        #expect(spy.played.isEmpty)
        #expect(clock.reveal(.cover, at: base)(.header) == 1)
    }

    @Test("a frozen clock draws its sequence at its instant and ignores reports")
    func frozen() {
        let clock = IntroClock.frozen(month: october, sequence: .cover, beats: beats, elapsed: 0.46)
        let reveal = clock.reveal(.cover, at: clock.frozenAt ?? .distantPast)
        #expect(abs(reveal(.row(0)) - 0.5) < 1e-9)
        clock.report(.cover, beats: [], frame: CGRect(x: 0, y: -500, width: 10, height: 10), viewport: 800)
        #expect(clock.isPlaying(.cover))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd apps/ios && build/intro-test.sh --fresh IntroClockTests 2>&1 | tail -20`
Expected: build failure, `cannot find 'IntroClock' in scope`.

- [ ] **Step 3: Append the clock to `IntroClock.swift`**

```swift
/// A month page's intro (#1241 D1, D3, D4): which sequence plays, which waits,
/// which has landed. One voice at a time: a sequence in view and still waiting
/// plays when nothing else does, in page order, and only while the page is
/// active. A sequence plays once per launch (the ledger records it as it
/// starts). Views read `reveal(_:at:)`; the page drives `isActive` and taps
/// `skip()`; each sequence reports where it is.
@Observable @MainActor
final class IntroClock {
    enum Phase: Equatable {
        case waiting
        case playing(Date)
        case settled
    }

    /// A sequence starts this far ahead, so its haptic player and its frames begin together.
    static let lead: TimeInterval = 0.05

    let month: CalendarMonth
    /// A reference's instant: every playing sequence is drawn at it, and nothing moves.
    @ObservationIgnored let frozenAt: Date?

    private var phases: [IntroSequence: Phase] = [:]
    @ObservationIgnored private var keyed: [IntroSequence: [IntroKey: IntroBeat]] = [:]
    @ObservationIgnored private var latest: [IntroSequence: [IntroBeat]] = [:]
    @ObservationIgnored private var ends: [IntroSequence: Date] = [:]
    @ObservationIgnored private var inView: Set<IntroSequence> = []
    @ObservationIgnored private let order: [IntroSequence]
    @ObservationIgnored private let ledger: IntroLedger
    @ObservationIgnored private let haptics: IntroHapticPlaying?
    @ObservationIgnored private let now: () -> Date
    @ObservationIgnored private let schedules: Bool
    @ObservationIgnored private var ending: Task<Void, Never>?

    /// The page is focused, the app active, and the intro allowed (`IntroPolicy`). Off lands what plays.
    var isActive = false {
        didSet {
            guard isActive != oldValue else { return }
            if isActive {
                haptics?.prepare()
                startNext()
            } else {
                settleAll()
            }
        }
    }

    /// `schedules: false` leaves the landing to `advance(to:)`, for the tests.
    convenience init(month: CalendarMonth, order: [IntroSequence], ledger: IntroLedger,
                     haptics: IntroHapticPlaying?, now: @escaping () -> Date = Date.init, schedules: Bool = true) {
        self.init(month: month, order: order, ledger: ledger, haptics: haptics, now: now, schedules: schedules,
                  frozenAt: nil)
    }

    private init(month: CalendarMonth, order: [IntroSequence], ledger: IntroLedger, haptics: IntroHapticPlaying?,
                 now: @escaping () -> Date, schedules: Bool, frozenAt: Date?) {
        self.month = month
        self.order = order
        self.ledger = ledger
        self.haptics = haptics
        self.now = now
        self.schedules = schedules
        self.frozenAt = frozenAt
    }

    /// The cover, then the sections in the server's order.
    static func order(_ wrap: MonthWrap) -> [IntroSequence] {
        [.cover] + wrap.sections.map { .section($0) }
    }

    /// A reference's clock: `sequence` playing, drawn `elapsed` seconds in.
    static func frozen(month: CalendarMonth, sequence: IntroSequence, beats: [IntroBeat],
                       elapsed: TimeInterval) -> IntroClock {
        let start = Date(timeIntervalSinceReferenceDate: 0)
        let clock = IntroClock(month: month, order: [sequence], ledger: IntroLedger(), haptics: nil,
                               now: { start }, schedules: false, frozenAt: start.addingTimeInterval(elapsed))
        clock.keyed[sequence] = index(beats)
        clock.phases[sequence] = .playing(start)
        return clock
    }

    func phase(_ sequence: IntroSequence) -> Phase {
        phases[sequence] ?? (ledger.contains(month, sequence) ? .settled : .waiting)
    }

    func isPlaying(_ sequence: IntroSequence) -> Bool {
        if case .playing = phase(sequence) { return true }
        return false
    }

    /// Each key's linear progress in `sequence` at `date`: nothing before the
    /// sequence's turn; while it plays, its beat's progress (a key the beats
    /// don't name has landed); everything once it has landed.
    func reveal(_ sequence: IntroSequence, at date: Date) -> IntroReveal {
        switch phase(sequence) {
        case .settled:
            return .settled
        case .waiting:
            return .hidden
        case let .playing(start):
            let elapsed = date.timeIntervalSince(start)
            let beats = keyed[sequence] ?? [:]
            return IntroReveal { key in
                guard let beat = beats[key] else { return 1 }
                guard beat.duration > 0 else { return elapsed >= beat.at ? 1 : 0 }
                return IntroMotion.clamp((elapsed - beat.at) / beat.duration)
            }
        }
    }

    /// A sequence's frame in its page: in view, it waits its turn; wholly out, it leaves.
    func report(_ sequence: IntroSequence, beats: [IntroBeat], frame: CGRect, viewport: CGFloat) {
        guard frozenAt == nil, viewport > 0 else { return }
        switch IntroViewport.place(frame, viewport: viewport) {
        case .entered: enter(sequence, beats: beats)
        case .out: leave(sequence)
        case .between: break
        }
    }

    func enter(_ sequence: IntroSequence, beats: [IntroBeat]) {
        latest[sequence] = beats
        guard inView.insert(sequence).inserted else { return }
        startNext()
    }

    func leave(_ sequence: IntroSequence) {
        guard inView.remove(sequence) != nil else { return }
        if isPlaying(sequence) { land(sequence) }
    }

    /// Lands the playing sequence once `date` is past its last beat, and starts the next.
    func advance(to date: Date) {
        for (sequence, end) in ends where date >= end {
            land(sequence)
        }
    }

    /// A tap: what plays and what waits in view land now, and count as played.
    func skip() {
        for sequence in inView where phase(sequence) == .waiting {
            ledger.record(month, sequence)
            phases[sequence] = .settled
        }
        settleAll()
    }

    /// Focus lost or the app inactive: what plays lands; what waits keeps waiting, unrecorded.
    func settleAll() {
        ending?.cancel()
        ending = nil
        haptics?.stopAll()
        for sequence in ends.keys {
            phases[sequence] = .settled
        }
        ends = [:]
    }

    /// The playing sequence has landed (its time came, or it left the viewport): the next may start.
    private func land(_ sequence: IntroSequence) {
        guard ends.removeValue(forKey: sequence) != nil else { return }
        ending?.cancel()
        ending = nil
        haptics?.stop(sequence)
        phases[sequence] = .settled
        startNext()
    }

    /// Starts the first sequence in view and still waiting, in page order, when the page may play and nothing does.
    private func startNext() {
        guard isActive, ends.isEmpty else { return }
        let rank = { (sequence: IntroSequence) in self.order.firstIndex(of: sequence) ?? self.order.count }
        guard let next = inView.filter({ phase($0) == .waiting }).min(by: { rank($0) < rank($1) }) else { return }
        start(next, beats: latest[next] ?? [])
    }

    private func start(_ sequence: IntroSequence, beats: [IntroBeat]) {
        let begin = now().addingTimeInterval(Self.lead)
        let end = begin.addingTimeInterval(IntroScore.duration(beats))
        keyed[sequence] = Self.index(beats)
        ends[sequence] = end
        phases[sequence] = .playing(begin)
        ledger.record(month, sequence)
        haptics?.play(sequence, beats: beats, after: Self.lead)
        guard schedules else { return }
        let wait = end.timeIntervalSince(now())
        ending = Task { [weak self] in
            do {
                try await Task.sleep(for: .seconds(wait))
            } catch {
                return // Cancelled: the sequence landed some other way.
            }
            self?.advance(to: end)
        }
    }

    private static func index(_ beats: [IntroBeat]) -> [IntroKey: IntroBeat] {
        Dictionary(beats.map { ($0.key, $0) }, uniquingKeysWith: { first, _ in first })
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `cd apps/ios && build/intro-test.sh IntroClockTests IntroRulesTests 2>&1 | tail -20`
Expected: PASS. If the compiler rejects `didSet` on an `@Observable` property, move the body into `func setActive(_:)` and make `isActive` `private(set)`. Then change every `isActive =` in the tests and in Task 8 to `setActive(…)`, and record the change under "As built".

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Wrap/Intro/IntroClock.swift apps/ios/PebblesTests/Wrap/IntroClockTests.swift
git commit -m "feat(ios): add the month intro's clock, one voice at a time

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 6: The view hooks

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Wrap/Intro/IntroViews.swift`

No unit test: these are views. Tasks 7 and 9 prove them, through the existing references (settled) and the new one (frozen).

- [ ] **Step 1: Write `IntroViews.swift`**

```swift
import SwiftUI

/// The month page's coordinate space: each sequence measures its frame in it (#1241).
enum IntroSpace {
    static let name = "intro.page"
}

private struct IntroClockKey: EnvironmentKey {
    static let defaultValue: IntroClock? = nil
}

private struct IntroSequenceKey: EnvironmentKey {
    static let defaultValue: IntroSequence? = nil
}

private struct IntroNowKey: EnvironmentKey {
    static let defaultValue = Date.distantPast
}

extension EnvironmentValues {
    /// The month page's intro; nil, the default, draws every figure settled.
    var introClock: IntroClock? {
        get { self[IntroClockKey.self] }
        set { self[IntroClockKey.self] = newValue }
    }

    /// The sequence a view belongs to, set by its host.
    var introSequence: IntroSequence? {
        get { self[IntroSequenceKey.self] }
        set { self[IntroSequenceKey.self] = newValue }
    }

    /// The playing sequence's frame date, set by its host's timeline.
    var introNow: Date {
        get { self[IntroNowKey.self] }
        set { self[IntroNowKey.self] = newValue }
    }
}

/// Hands its content the reveal of the sequence around it at the frame's
/// date: settled with no clock, outside a sequence, under Reduce Motion, or
/// while VoiceOver runs.
struct IntroReader<Content: View>: View {
    private let content: (IntroReveal) -> Content

    @Environment(\.introClock) private var clock
    @Environment(\.introSequence) private var sequence
    @Environment(\.introNow) private var now
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver

    init(@ViewBuilder content: @escaping (IntroReveal) -> Content) {
        self.content = content
    }

    var body: some View {
        content(reveal)
    }

    private var reveal: IntroReveal {
        guard let clock, let sequence, IntroPolicy.plays(reduceMotion: reduceMotion, voiceOver: voiceOver) else {
            return .settled
        }
        return clock.reveal(sequence, at: clock.frozenAt ?? now)
    }
}

/// How a SwiftUI part lands (#1241 §Choreography).
enum IntroLanding {
    /// Fades in, rising 6 pt.
    case rise
    /// Fades in where it is.
    case fade
    /// Grows from 0.4, overshooting once.
    case pop
}

extension View {
    /// Lands this view on `key`'s beat. A nil key, or no intro around it, leaves it as it is.
    func introReveal(_ key: IntroKey?, _ landing: IntroLanding = .rise) -> some View {
        modifier(IntroRevealModifier(key: key, landing: landing))
    }

    /// Makes this view a sequence of the intro: it reports its place in the
    /// page, and redraws on each frame while it plays. `beats` is asked for only
    /// when a clock listens.
    func introSequence(_ sequence: IntroSequence, beats: @escaping () -> [IntroBeat]) -> some View {
        modifier(IntroSequenceHost(sequence: sequence, beats: beats))
    }
}

private struct IntroRevealModifier: ViewModifier {
    let key: IntroKey?
    let landing: IntroLanding

    func body(content: Content) -> some View {
        if let key {
            IntroReader { reveal in
                IntroLanded(content: content, progress: reveal(key), landing: landing)
            }
        } else {
            content
        }
    }
}

/// A part at its progress: landed (1), it draws exactly as it would without the intro.
private struct IntroLanded<Content: View>: View {
    let content: Content
    let progress: Double
    let landing: IntroLanding

    var body: some View {
        switch landing {
        case .rise:
            let eased = IntroMotion.easeOut(progress)
            content
                .opacity(eased)
                .offset(y: IntroMotion.riseOffset * CGFloat(1 - eased))
        case .fade:
            content.opacity(IntroMotion.easeOut(progress))
        case .pop:
            content
                .opacity(IntroMotion.popOpacity(progress))
                .scaleEffect(CGFloat(IntroMotion.popScale(progress)))
        }
    }
}

/// A sequence's frame, and its page's viewport height.
private struct IntroPlacement: Equatable {
    let frame: CGRect
    let viewport: CGFloat
}

private struct IntroSequenceHost: ViewModifier {
    let sequence: IntroSequence
    let beats: () -> [IntroBeat]

    @Environment(\.introClock) private var clock

    func body(content: Content) -> some View {
        if let clock {
            Group {
                if clock.frozenAt != nil {
                    content
                } else {
                    // Only the playing sequence redraws per frame, and only while it plays.
                    TimelineView(.animation(paused: !clock.isPlaying(sequence))) { timeline in
                        content.environment(\.introNow, timeline.date)
                    }
                }
            }
            .environment(\.introSequence, sequence)
            .onGeometryChange(for: IntroPlacement.self) { proxy in
                IntroPlacement(frame: proxy.frame(in: .named(IntroSpace.name)),
                               viewport: proxy.bounds(of: .named(IntroSpace.name))?.height ?? 0)
            } action: { placement in
                clock.report(sequence, beats: beats(), frame: placement.frame, viewport: placement.viewport)
            }
        } else {
            content
        }
    }
}
```

- [ ] **Step 2: Build**

Run: `cd apps/ios && build/intro-test.sh --fresh IntroClockTests 2>&1 | tail -20`
Expected: builds and passes. The point is that `IntroViews.swift` compiles.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Wrap/Intro/IntroViews.swift
git commit -m "feat(ios): add the month intro's reader, reveal and sequence host

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 7: The cover plays

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Wrap/Month/Garland.swift` (whole file below)
- Modify: `apps/ios/Pebbles/Features/Path/Wrap/Month/MonthCover.swift` (`body` and new statics below)

- [ ] **Step 1: Replace `Garland.swift` with this**

```swift
import SwiftUI

/// The cover's garland (#1226, D7): the month's days around a ring from the
/// foot, clockwise, on a dotted course. A day with pebbles shows each as a seal
/// turned along the ring (two on one day step in and out); an empty day shows a
/// socket. The 1st, each Monday and the last day are numbered, and a leaf ties
/// the ring at the foot. `inner` sits in its middle. Decorative, hidden from
/// VoiceOver: the cover says the month in words below it. In the month's intro
/// (#1241) `reveal` lands each day in turn and draws the course to the newest;
/// settled, it is the garland as ever. Android: `Garland`.
struct Garland<Inner: View>: View {
    let wrap: MonthWrap
    let cover: MonthCover
    let lookups: WrapLookups
    let reveal: IntroReveal
    let inner: Inner

    @Environment(\.colorScheme) private var scheme

    private static var side: CGFloat { 262 }
    private static var radius: CGFloat { 103 }
    /// 32 slots from the foot, clockwise: day n sits in slot n + 1, and the tie fills slot 0.
    private static var ring: RingGeometry { RingGeometry(slots: 32, startDegrees: 90) }

    init(wrap: MonthWrap, cover: MonthCover, lookups: WrapLookups, reveal: IntroReveal = .settled,
         @ViewBuilder inner: () -> Inner) {
        self.wrap = wrap
        self.cover = cover
        self.lookups = lookups
        self.reveal = reveal
        self.inner = inner()
    }

    var body: some View {
        let placed = cover.days.flatMap { day in
            day.pebbles.enumerated().map { index, pebble in
                PlacedPebble(day: day.day, index: index, count: day.pebbles.count, pebble: pebble)
            }
        }
        let prints = placed.map { place in
            // An emotion the device does not know prints blank, as on Android.
            let palette = place.pebble.emotionId.flatMap { lookups.emotion($0)?.palette }
            return SealPrint(valence: .of(ValenceSizeGroup(intensity: place.pebble.intensity), place.pebble.polarity),
                             glyph: nil, palette: SpineColors.palette(palette, isBlank: palette == nil),
                             isBlank: palette == nil, size: .contents, scheme: scheme)
        }
        let pebbled = Set(cover.days.map(\.day))
        ZStack {
            // The ring turns clockwise whatever the language: its numbers stay on their days.
            ZStack {
                Canvas { context, size in
                    draw(in: context, size: size, placed: placed, prints: prints, pebbled: pebbled)
                }
                numerals
            }
            .environment(\.layoutDirection, .leftToRight)
            .accessibilityHidden(true)
            inner
        }
        .frame(width: Self.side, height: Self.side)
    }

    private func draw(in context: GraphicsContext, size: CGSize, placed: [PlacedPebble], prints: [SealPrint],
                      pebbled: Set<Int>) {
        let ring = Self.ring
        let center = CGPoint(x: size.width / 2, y: size.height / 2)
        let radius = Self.radius
        drawCourse(in: context, center: center)
        for day in 0..<wrap.daysInMonth where !pebbled.contains(day) {
            let shown = reveal(.slot(day))
            guard shown > 0 else { continue }
            var socket = context
            var diameter: CGFloat = 6.8
            if shown < 1 {
                socket.opacity = IntroMotion.popOpacity(shown)
                diameter *= CGFloat(IntroMotion.popScale(shown))
            }
            socket.dotMark(center: ring.point(Double(day + 1), center: center, radius: radius), diameter: diameter,
                           style: .socket, color: Color.pebbles.outline)
        }
        for (place, print) in zip(placed, prints) {
            // A day's first seal lands on its slot's beat, a stacked one on its own.
            let shown = reveal(place.index == 0 ? .slot(place.day) : .seal(place.day, place.index))
            guard shown > 0 else { continue }
            let slot = Double(place.day + 1)
            let step = (CGFloat(place.index) - CGFloat(place.count - 1) / 2) * -Self.stackOffset
            let at = ring.point(slot, center: center, radius: radius + step)
            let width = [14.0, 17.0, 20.0][min(max(place.pebble.intensity - 1, 0), 2)]
            let height = width * print.frame.height / print.frame.width
            var seal = context
            seal.translateBy(x: at.x, y: at.y)
            seal.rotate(by: .degrees(ring.degrees(slot) + 90))
            if shown < 1 {
                seal.opacity = IntroMotion.popOpacity(shown)
                let scale = CGFloat(IntroMotion.popScale(shown))
                seal.scaleBy(x: scale, y: scale)
            }
            seal.drawSeal(print, in: CGRect(x: -width / 2, y: -height / 2, width: width, height: height))
        }
        drawTie(in: context, center: center)
    }

    /// The dotted course, stopping short of the tie on each side; in the intro,
    /// only as far as half a slot past the newest day.
    private func drawCourse(in context: GraphicsContext, center: CGPoint) {
        let drawn = reveal(.course)
        guard drawn > 0 else { return }
        let ring = Self.ring
        let inset = 0.55
        let start = ring.degrees(inset)
        let full = ring.degrees(32 - inset) - start
        let newest = 1.5 + IntroMotion.easeInOut(drawn) * Double(max(wrap.daysInMonth - 1, 0))
        let reach = drawn >= 1 ? full : min(full, ring.degrees(newest) - start)
        var course = Path()
        course.addRelativeArc(center: center, radius: Self.radius, startAngle: .degrees(start), delta: .degrees(reach))
        context.stroke(course, with: .color(Color.pebbles.rule), style: StrokeStyle(lineWidth: 1, dash: [2, 3]))
    }

    /// The tie: a leaf at the foot, where the course parts.
    private func drawTie(in context: GraphicsContext, center: CGPoint) {
        let tied = reveal(.leaf)
        guard tied > 0 else { return }
        let foot = Self.ring.point(0, center: center, radius: Self.radius)
        var tie = context
        if tied < 1 {
            tie.opacity = IntroMotion.popOpacity(tied)
            let scale = CGFloat(IntroMotion.popScale(tied))
            tie.translateBy(x: foot.x, y: foot.y)
            tie.scaleBy(x: scale, y: scale)
            tie.translateBy(x: -foot.x, y: -foot.y)
        }
        let leaf: CGFloat = 12
        var path = Path()
        path.move(to: CGPoint(x: foot.x, y: foot.y - leaf / 2))
        path.addQuadCurve(to: CGPoint(x: foot.x, y: foot.y + leaf / 2),
                          control: CGPoint(x: foot.x + leaf / 2, y: foot.y))
        path.addQuadCurve(to: CGPoint(x: foot.x, y: foot.y - leaf / 2),
                          control: CGPoint(x: foot.x - leaf / 2, y: foot.y))
        tie.fill(path, with: .color(Color.pebbles.onSurfaceVariant))
        var vein = Path()
        vein.move(to: CGPoint(x: foot.x, y: foot.y - leaf / 2.6))
        vein.addLine(to: CGPoint(x: foot.x, y: foot.y + leaf / 2.6))
        tie.stroke(vein, with: .color(Color.pebbles.surface), lineWidth: 1)
    }

    private static var stackOffset: CGFloat { 10.5 }

    /// The 1st, each Monday and the last day, outside the ring; a stacked day's number steps out with it.
    private var numerals: some View {
        let ring = Self.ring
        let center = CGPoint(x: Self.side / 2, y: Self.side / 2)
        return ForEach(wrap.numberedDays, id: \.self) { day in
            let stacked = (cover.days.first { $0.day == day }?.pebbles.count ?? 0) > 1
            let at = ring.point(Double(day + 1), center: center,
                                radius: Self.radius + 20 + (stacked ? Self.stackOffset / 2 : 0))
            Text(verbatim: "\(day + 1)")
                .spineType(.dayLetter)
                .dynamicTypeSize(...DynamicTypeSize.xLarge)
                .foregroundStyle(Color.pebbles.outline)
                .fixedSize()
                .introReveal(.numeral(day), .fade)
                .position(at)
        }
    }
}

/// A pebble on the garland: its day, its place among the day's pebbles, and the pebble.
private struct PlacedPebble {
    let day: Int
    let index: Int
    let count: Int
    let pebble: CoverPebble
}
```

- [ ] **Step 2: Change `MonthCoverView` in `MonthCover.swift`**

Replace the whole `var body: some View { … }` of `MonthCoverView` with the version below. Then add the three statics after it, before `colophon(_:)`.

```swift
    var body: some View {
        if let cover = wrap.cover {
            let souls = Self.souls(cover, lookups: lookups)
            VStack(spacing: 10) {
                IntroReader { reveal in
                    Garland(wrap: wrap, cover: cover, lookups: lookups, reveal: reveal) {
                        VStack(spacing: 0) {
                            Text(verbatim: WrapCopy.monthKicker(wrap.month, locale: locale))
                                .spineType(.sectionLabel)
                                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                                .introReveal(.kicker)
                            Text(verbatim: WrapCopy.monthName(wrap.month, locale: locale))
                                .spineType(.monthName)
                                .foregroundStyle(Color.pebbles.onSurface)
                                .introReveal(.monthName)
                            Text(verbatim: String(wrap.month.year))
                                .spineType(.contentsTime)
                                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                                .introReveal(.year)
                            if !souls.isEmpty {
                                HStack(spacing: 2) {
                                    ForEach(Array(souls.prefix(Self.soulMarks).enumerated()), id: \.element) { index, id in
                                        SoulMark(strokes: lookups.soulGlyph(id), side: 15)
                                            .introReveal(.soul(index), .pop)
                                    }
                                }
                                .padding(.top, 6)
                                .accessibilityHidden(true)
                            }
                        }
                        .dynamicTypeSize(...DynamicTypeSize.large)
                        .multilineTextAlignment(.center)
                        // One stop for VoiceOver: "part the tenth, October, 2026", the page's header.
                        .accessibilityElement(children: .combine)
                        .accessibilityAddTraits(.isHeader)
                    }
                }
                Text(verbatim: WrapCopy.line(pebbles: wrap.pebbles, people: wrap.people, live: false, hasMoments: true,
                                             locale: locale))
                    .spineType(.summary)
                    .foregroundStyle(Color.pebbles.onSurface)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .introReveal(.summary)
                if !souls.isEmpty {
                    VStack(spacing: 2) {
                        Text(verbatim: WrapCopy.personsInOrderLabel(locale: locale))
                            .spineType(.sectionHint)
                            .foregroundStyle(Color.pebbles.outline)
                        Text(verbatim: souls.compactMap(lookups.soulName).joined(separator: "\u{00A0}· "))
                            .spineType(.soulName)
                            .foregroundStyle(Color.pebbles.onSurface)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .multilineTextAlignment(.center)
                    .accessibilityElement(children: .combine)
                    .introReveal(.names)
                }
                colophon(cover)
                    .introReveal(.colophon)
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, MonthLayout.sectionPadX)
            .padding(.top, 20)
            .introSequence(.cover) { Self.introBeats(wrap: wrap, lookups: lookups) }
        }
    }

    /// At most this many souls' glyphs inside the ring.
    private static let soulMarks = 8

    /// The souls the device can name, in the cover's order.
    private static func souls(_ cover: MonthCover, lookups: WrapLookups) -> [String] {
        cover.soulsInOrder.filter { lookups.soulName($0) != nil }
    }

    /// The cover's intro (#1241): its days, the glyphs it draws and the lines it shows.
    static func introBeats(wrap: MonthWrap, lookups: WrapLookups) -> [IntroBeat] {
        guard let cover = wrap.cover else { return [] }
        let souls = souls(cover, lookups: lookups)
        let papers = cover.papers.compactMap { lookups.domain($0) }
        let lines: [IntroKey] = [.summary] + (souls.isEmpty ? [] : [.names])
            + (cover.inks.isEmpty && papers.isEmpty ? [] : [.colophon])
        return IntroScore.cover(days: cover.days, daysInMonth: wrap.daysInMonth, numbered: wrap.numberedDays,
                                souls: min(souls.count, soulMarks), lines: lines)
    }
```

Also add one sentence to the end of `MonthCoverView`'s doc comment, before "Android: `MonthCover`.": "In the month's intro (#1241) it is the first sequence: the name, the garland, then the words."

- [ ] **Step 3: Prove the settled cover is unchanged**

Run: `cd apps/ios && build/intro-test.sh References/WrapMonthReferences References/WrapAtomsReferences 2>&1 | tail -30`
Expected: PASS, every reference the same as in Task 0's baseline.
- If a cover reference differs, the settled path drew something new. Diff the actual PNG the harness writes against the reference (its path is in the failure).
- The suspects are an `opacity` or `scaleBy` applied while `shown == 1`, and the course's `full` versus the old delta.
- Fix the code. Never re-record.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Wrap/Month/Garland.swift apps/ios/Pebbles/Features/Path/Wrap/Month/MonthCover.swift
git commit -m "feat(ios): draw the month's garland day by day in its intro

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 8: The page builds the clock; the Path and the Lab pass it down

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Wrap/Month/MonthPage.swift` (the `MonthPage` struct)
- Modify: `apps/ios/Pebbles/Features/Path/PathModel.swift` (one property)
- Modify: `apps/ios/Pebbles/Features/Path/Spine/SpinePager.swift` (one property, one call)
- Modify: `apps/ios/Pebbles/Features/Path/PathView.swift` (one argument)
- Modify: `apps/ios/Pebbles/Features/Path/Spine/Debug/SpineLabView.swift` (the month tab)

- [ ] **Step 1: Replace the `MonthPage` struct (leave `MonthPageSections` as it is)**

```swift
/// A month's page (#1226, D6): the cover, then the sections the server listed,
/// in its order. Each *more* opens the month's sheet on its list; the day view
/// (circle or calendar) is one setting for every month. When a milestone's
/// held card closes, VoiceOver comes back to its row (the focused page only,
/// so a realised neighbour never takes it). With an intro context, the page
/// plays its intro (#1241) while focused and the app active, skipped by a tap.
/// Android: `MonthPage`.
struct MonthPage: View {
    let wrap: MonthWrap
    let lookups: WrapLookups
    let isFocused: Bool
    /// The model's mirror of the held card's closing; cleared once used.
    @Binding var returnFocus: UUID?
    let onWeek: (CalendarDay) -> Void
    let onOpen: (MomentSummary) -> Void

    @AppStorage(DayView.storageKey) private var dayView: DayView = .circle
    /// The list the sheet opened on; the page's own state, never the model's.
    @State private var sheet: MonthSection?
    @AccessibilityFocusState private var milestoneFocus: UUID?
    /// The page's intro; nil without a context, and the page draws settled.
    @State private var intro: IntroClock?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
    @Environment(\.scenePhase) private var scenePhase

    init(wrap: MonthWrap, lookups: WrapLookups, isFocused: Bool, returnFocus: Binding<UUID?>,
         onWeek: @escaping (CalendarDay) -> Void, onOpen: @escaping (MomentSummary) -> Void,
         intro context: IntroContext? = nil) {
        self.wrap = wrap
        self.lookups = lookups
        self.isFocused = isFocused
        _returnFocus = returnFocus
        self.onWeek = onWeek
        self.onOpen = onOpen
        // Built with the page, not on appear: a first frame without it would draw settled, then blank.
        _intro = State(initialValue: context.map {
            IntroClock(month: wrap.month, order: IntroClock.order(wrap), ledger: $0.ledger, haptics: $0.haptics)
        })
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                MonthPageSections(wrap: wrap, lookups: lookups, dayView: dayView, focus: $milestoneFocus,
                                  onWeek: onWeek, onOpen: onOpen, onMore: { sheet = $0 })
            }
            .padding(.bottom, MonthLayout.pageBottom)
        }
        .coordinateSpace(name: IntroSpace.name)
        .environment(\.introClock, intro)
        // A tap anywhere skips the intro, and still does its job.
        .simultaneousGesture(TapGesture().onEnded { intro?.skip() })
        .onChange(of: playsIntro, initial: true) { _, plays in intro?.isActive = plays }
        .onChange(of: returnFocus) { _, id in
            guard isFocused, let id else { return }
            milestoneFocus = id
            returnFocus = nil
        }
        .sheet(item: $sheet) { tab in
            MonthSheet(wrap: wrap, lookups: lookups, initial: tab)
        }
    }

    /// The page may play: focused, the app active, neither Reduce Motion nor VoiceOver on.
    private var playsIntro: Bool {
        isFocused && scenePhase == .active && IntroPolicy.plays(reduceMotion: reduceMotion, voiceOver: voiceOver)
    }
}
```

- [ ] **Step 2: Give `PathModel` the ledger**

In `PathModel.swift`, directly after `var returnFocus: UUID?` (line 54), add:

```swift
    /// What the month pages' intros have played this launch (#1241 D3).
    @ObservationIgnored let introLedger = IntroLedger()
```

- [ ] **Step 3: Pass the context through `SpinePager`**

In `SpinePager.swift`, after `var scrollToTopToken = 0`, add:

```swift
    /// The month pages' intro (#1241): the launch's ledger and the haptics; nil draws them settled.
    var intro: IntroContext?
```

and change the `MonthPage(…)` call to end with `onWeek: focusWeek, onOpen: openMilestone, intro: intro)`.

- [ ] **Step 4: Hand it in from `PathView`**

In `PathView.swift`, in the `SpinePager(` call, after `scrollToTopToken: nav.scrollToTop[.path, default: 0]`, add the argument:

```swift
                    scrollToTopToken: nav.scrollToTop[.path, default: 0],
                    intro: IntroContext(ledger: model.introLedger, haptics: IntroHaptics.shared)
```

- [ ] **Step 5: The Lab's replay**

In `SpineLabView.swift`, add two state properties after `@State private var lastOpen = ""`:

```swift
    /// The Month tab's intro: its own ledger, so *Replay intro* forgets October without touching the Path's.
    @State private var introLedger = IntroLedger()
    @State private var introRun = 0
```

and replace the `month` property with:

```swift
    /// The harness's October as its page: the cover, the sections, the sheets.
    /// Week taps and opens are logged on screen; *Replay intro* plays the
    /// intro again (#1241), for tuning on a device.
    @ViewBuilder
    private var month: some View {
        if let october = SpineFixtureMonth.october {
            VStack(spacing: 0) {
                HStack {
                    Text(verbatim: lastOpen).font(.caption.monospaced()).foregroundStyle(Color.pebbles.outline)
                    Spacer(minLength: 8)
                    Button {
                        introLedger.reset(october.month)
                        introRun += 1
                    } label: {
                        Text(verbatim: "Replay intro").font(.caption)
                    }
                }
                .padding(.horizontal, 16)
                MonthPage(wrap: october, lookups: SpineFixtureMonth.lookups(october), isFocused: true,
                          returnFocus: .constant(nil),
                          onWeek: { lastOpen = "week of \($0.iso)" },
                          onOpen: { lastOpen = "open \($0.name ?? "untitled")" },
                          intro: IntroContext(ledger: introLedger, haptics: IntroHaptics.shared))
                    .id(introRun)
            }
        }
    }
```

- [ ] **Step 6: Build and run the whole suite**

Run: `cd apps/ios && build/intro-test.sh 2>&1 | tail -40`
Expected: every test passes, and every reference matches Task 0's baseline.

- [ ] **Step 7: Watch it once on the simulator**

1. Install and launch on "iPhone 17 intro" with the `run` skill, or with `xcrun simctl install` / `launch` from `build/Build/Products/Debug-iphonesimulator/Pebbles.app`.
2. Open Settings → Developer → Spine Lab → `month`.

Expected:
- the name rises
- the garland's slots pop in from the foot, slow, fast, slow, while the course follows
- the leaf lands, then the texts
- the sections below stay blank until Part 2 (their parts have no keys yet, so they draw settled)
- *Replay intro* plays it again
- a tap during the garland lands it at once
- with Settings → Accessibility → Motion → Reduce Motion on, the page appears settled

The simulator has no haptics.

- [ ] **Step 8: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Wrap/Month/MonthPage.swift apps/ios/Pebbles/Features/Path/PathModel.swift apps/ios/Pebbles/Features/Path/Spine/SpinePager.swift apps/ios/Pebbles/Features/Path/PathView.swift apps/ios/Pebbles/Features/Path/Spine/Debug/SpineLabView.swift
git commit -m "feat(ios): play the month's intro on its page, once per launch

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 9: The cover's mid-intro reference

**Files:**
- Create: `apps/ios/PebblesTests/References/WrapIntroReferences.swift`

- [ ] **Step 1: Write the reference test**

```swift
import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1241: the month's intro frozen mid-way, on the harness's October. The
    /// settled render is the other wrap references' job; these pin the
    /// in-between frames, which a clock at a fixed instant makes deterministic.
    @Suite("Wrap intro references")
    @MainActor
    struct WrapIntroReferences {
        /// The cover 1.15 s in: the name up, the garland about half drawn.
        @Test
        func garlandHalf() throws {
            let wrap = try MonthPayloadFixture.october()
            let lookups = MonthPayloadFixture.lookups(wrap)
            let clock = IntroClock.frozen(month: wrap.month, sequence: .cover,
                                          beats: MonthCoverView.introBeats(wrap: wrap, lookups: lookups),
                                          elapsed: 1.15)
            let view = MonthCoverView(wrap: wrap, lookups: lookups)
                .padding(.bottom, 24)
                .background(Color.pebbles.surface)
                .environment(\.introClock, clock)
            ReferenceImage.assertMatches(view, named: "intro-garland-half", config: ReferenceConfig(scheme: .paper))
        }
    }
}
```

- [ ] **Step 2: Run it and read the failure**

Run: `cd apps/ios && build/intro-test.sh --fresh References/WrapIntroReferences 2>&1 | tail -20`
Expected: FAIL with "intro-garland-half: no reference at … add the rebaseline-ios-screenshots label".
1. Open the actual PNG the message names.
2. Check it shows: the name, the year and the kicker up; about the first half of the days popped, the course reaching them; no leaf, no summary.
3. If it shows the full garland, the frozen clock isn't reaching the views: check that `MonthCoverView`'s host sets `introSequence`. If it shows nothing, check `frozenAt` in `IntroReader`.
4. Don't commit the PNG: CI records it.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/PebblesTests/References/WrapIntroReferences.swift
git commit -m "test(ios): pin the month cover half way through its intro

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 10: The decision log

**Files:**
- Modify: `docs/decisions/log.md` (append at the end)

- [ ] **Step 1: Append the entry**

```markdown

## 2026-10-25 — The month's intro on iOS is one score on one clock, its haptics Core Haptics (#1241)

- **Status:** taken
- **Scope:** ios
- **Context:**
  - #1241 makes the iOS month page enter instead of appearing. The garland draws day by day, and each section plays as it scrolls in, with a haptic on every slot, row, facet and stone.
  - The garland, the gem, the persons' rings and the hours dial are `Canvas` drawings, which no SwiftUI transition reaches into.
  - A 31-day garland asks for 31 ticks in a second and a half.
- **Decision:**
  - **The score:** a pure `IntroScore` turns each sequence into beats: a time, a key, a duration and an optional tick.
  - **The clock:** one `IntroClock` per month page plays them, one sequence at a time in page order, once per launch (`IntroLedger` on `PathModel`).
  - **The views:** they read each key's progress from the clock, and every figure's reveal defaults to settled, so a figure with no clock around it draws as before.
  - **The haptics:** the same beats compile into one Core Haptics pattern per sequence (`IntroHaptics`, its own engine). A tick closer than 25 ms to the last is dropped.
- **Why:**
  - One clock keeps each tick on the frame it belongs to.
  - **Rejected:** `Task.sleep` steps with `.sensoryFeedback`. The Canvas figures would snap, the sleeps drift, and UIKit's generators merge taps about 30 ms apart.
  - **Rejected:** UIKit generators fired from the clock, because they keep that limit.
  - **One voice at a time:** the weeks section is on screen under the cover at load, and two tick patterns at once blur.
  - **Sine, not cubic, ease-in-out:** the cubic's middle would space a 31-day garland's ticks 17 ms apart, under the floor.
- **Consequences:**
  - **System Haptics, unknown at build:** whether Core Haptics honours Settings → Sounds & Haptics → System Haptics wasn't known. The device pass records it. If it doesn't, `IntroHaptics` fires UIKit generators from the clock instead, and the score and the figures stay as they are.
  - No intro under Reduce Motion or while VoiceOver runs.
  - A new figure on the month page takes a reveal that defaults to settled, and gets its beats from `IntroScore`.
  - Android (#1242) ports the score and the lifecycle, with its own haptic backend.
- **Supersedes / Superseded-by:** none.
- **Refs:** #1241, #1242, `docs/superpowers/specs/2026-10-25-ios-month-intro-design.md`, `apps/ios/Pebbles/Features/Path/Wrap/Intro/`.
```

- [ ] **Step 2: Commit**

```bash
git add docs/decisions/log.md
git commit -m "docs(ios): log the month intro's score, clock and haptics

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 11: Ship Part 1

- [ ] **Step 1: Lint**

Run: `cd apps/ios && swiftlint 2>&1 | tail -20`
Expected: no errors. Fix any new warning in the files this part touched; leave the others alone.

- [ ] **Step 2: The full suite, then the French references**

```bash
cd apps/ios && build/intro-test.sh 2>&1 | tail -30
xcodebuild test-without-building -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17 intro' \
  -derivedDataPath build -testLanguage fr -testRegion FR -only-testing:PebblesTests/References -quiet \
  CODE_SIGNING_ALLOWED=NO 2>&1 | tail -20
```
Expected: everything passes except `intro-garland-half` ("no reference"), plus Task 0's baseline list if it had any.

- [ ] **Step 3: One independent review of the part's diff**

Use superpowers:requesting-code-review on `git diff origin/main...HEAD -- apps/ios`. Fix confirmed findings in new commits.

- [ ] **Step 4: Open the PR**

Follow the `gh-stack` skill to push Part 1 as the stack's base and open its draft PR:
- **Title:** `feat(ios): play the month wrap's intro, its cover first`
- **Labels:** `feat`, `ui`, `ios`, `no-lab-note`
- **Milestone:** `M64 · New Path`
- **Body:**
  - "Part 1 of 4 for #1241" (not "Resolves"; the last part resolves the issue)
  - the key files
  - the new pattern, in one paragraph pointing at the decision log entry
  - the line "New reference: `intro-garland-half` (recorded by CI)"
  - ends with `🤖 Generated with [Claude Code](https://claude.com/claude-code)`
  - no finding ids

Then add the `rebaseline-ios-screenshots` label **on its own**, approve the held run, and pull the bot's commit before stacking the next part.

---

# Part 2 — the lists and the persons (`feat/1241-ios-intro-sections`)

Create the branch on top of Part 1 with the `gh-stack` skill before the first commit.

### Task 12: The sections' score

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Wrap/Intro/IntroScore+Sections.swift`
- Test: `apps/ios/PebblesTests/Wrap/IntroScoreSectionsTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

/// #1241 §Choreography, the sections.
@Suite("Intro score: the sections")
struct IntroScoreSectionsTests {
    @Test("a list: the head ticks, then each row 70 ms apart, and more with the last")
    func rows() throws {
        let beats = IntroScore.rows(count: 3, hasLead: false, more: true)
        let header = try #require(beats.first { $0.key == .header })
        #expect(header.at == 0)
        #expect(header.tick == .light)
        #expect(!beats.contains { $0.key == .lead })
        let rows = (0..<3).compactMap { row in beats.first { $0.key == .row(row) } }
        #expect(rows.map(\.at) == [IntroScore.rowsAt, IntroScore.rowsAt + 0.07, IntroScore.rowsAt + 0.14])
        #expect(rows.allSatisfy { $0.tick == .light })
        let more = try #require(beats.first { $0.key == .more })
        #expect(more.at == rows[2].at)
        #expect(more.tick == nil)
        #expect(!IntroScore.rows(count: 2, hasLead: false, more: false).contains { $0.key == .more })
    }

    @Test("a person: glyph, name, then the ring, a dot as the line reaches each day; the next row after")
    func persons() throws {
        let rings = [DaySet(length: 31, days: [0, 15, 30]), DaySet(length: 31, days: [3])]
        let beats = IntroScore.persons(rings: rings, hasLead: false)
        let glyph = try #require(beats.first { $0.key == .glyph(0) })
        let name = try #require(beats.first { $0.key == .name(0) })
        let ring = try #require(beats.first { $0.key == .ring(0) })
        #expect(glyph.at == IntroScore.rowsAt)
        #expect(name.at > glyph.at)
        #expect(ring.at > name.at)
        #expect(ring.duration == IntroScore.ringSpan)
        #expect(ring.tick == nil)
        let dots = [0, 15, 30].compactMap { day in beats.first { $0.key == .dot(0, day) } }
        #expect(dots.count == 3)
        #expect(zip(dots.dropFirst(), dots).allSatisfy { $0.at > $1.at })
        // The middle day sits at the ring's middle: the eased line reaches it half way through.
        #expect(abs(dots[1].at - (ring.at + IntroScore.ringSpan / 2)) < 1e-9)
        #expect(dots.allSatisfy { $0.tick == .light })
        let next = try #require(beats.first { $0.key == .glyph(1) })
        #expect(next.at > ring.at + ring.duration)
        let more = try #require(beats.first { $0.key == .more })
        let lastRing = try #require(beats.first { $0.key == .ring(1) })
        #expect(abs(more.at - (lastRing.at + lastRing.duration)) < 1e-12)
    }

    @Test("nobody named: the head and the invitation, nothing else")
    func nobody() {
        let beats = IntroScore.persons(rings: [], hasLead: true)
        #expect(beats.map(\.key) == [.header, .lead])
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd apps/ios && build/intro-test.sh --fresh IntroScoreSectionsTests 2>&1 | tail -20`
Expected: build failure, `type 'IntroScore' has no member 'rows'`.

- [ ] **Step 3: Write `IntroScore+Sections.swift`**

```swift
import Foundation

/// The sections' beats (#1241 §Choreography): a head, then rows; a person's
/// row in three parts. The gem, tone and hours build on `head`.
extension IntroScore {
    static let leadAt: TimeInterval = 0.16
    static let rowsAt: TimeInterval = 0.32
    static let rowGap: TimeInterval = 0.07
    /// When the insights head leads tone or hours, the rest comes this much later.
    static let insightsShift: TimeInterval = 0.16
    static let partGap: TimeInterval = 0.09
    static let ringSpan: TimeInterval = 0.45
    static let nextRow: TimeInterval = 0.06

    /// A list section (weeks, milestones, domains): its head, its lead line if
    /// any, each row in turn, and *more* with the last when it has one.
    static func rows(count: Int, hasLead: Bool, more: Bool) -> [IntroBeat] {
        var beats = head(hasLead: hasLead, insights: false)
        let times = (0..<count).map { rowsAt + rowGap * Double($0) }
        for (row, at) in times.enumerated() {
            beats.append(IntroBeat(key: .row(row), at: at, duration: IntroMotion.rise, tick: .light))
        }
        if more, let last = times.last {
            beats.append(IntroBeat(key: .more, at: last, duration: IntroMotion.rise))
        }
        return beats
    }

    /// The persons: per row its glyph, its name, then its ring's line drawn
    /// from the 1st, a beat on each day present as the line reaches it; the
    /// next row once the ring has closed; *more* as the last ring closes. With
    /// nobody named, the head and the invitation (`hasLead`).
    static func persons(rings: [DaySet], hasLead: Bool) -> [IntroBeat] {
        var beats = head(hasLead: hasLead, insights: false)
        var at = rowsAt
        for (row, days) in rings.enumerated() {
            beats.append(IntroBeat(key: .glyph(row), at: at, duration: IntroMotion.pop, tick: .light))
            beats.append(IntroBeat(key: .name(row), at: at + partGap, duration: IntroMotion.rise, tick: .light))
            let ring = at + 2 * partGap
            beats.append(IntroBeat(key: .ring(row), at: ring, duration: ringSpan))
            for day in days.days.sorted() {
                let reached = ringSpan * IntroMotion.easeInOutInverse(dotPlace(day, length: days.length))
                beats.append(IntroBeat(key: .dot(row, day), at: ring + reached, duration: IntroMotion.pop,
                                       tick: .light))
            }
            if row == rings.count - 1 {
                beats.append(IntroBeat(key: .more, at: ring + ringSpan, duration: IntroMotion.rise))
            }
            at = ring + ringSpan + nextRow
        }
        return beats
    }

    /// How far round its ring a day sits, 0…1: the middle of its slot, the ring opening before the 1st.
    static func dotPlace(_ day: Int, length: Int) -> Double {
        length > 0 ? (Double(day) + 0.5) / Double(length) : 0
    }

    /// A section's head: the insights head first when it leads tone or hours,
    /// then the head, then the lead line if any.
    static func head(hasLead: Bool, insights: Bool) -> [IntroBeat] {
        var beats: [IntroBeat] = []
        if insights {
            beats.append(IntroBeat(key: .insights, at: 0, duration: IntroMotion.rise, tick: .light))
        }
        let shift = shift(insights)
        beats.append(IntroBeat(key: .header, at: shift, duration: IntroMotion.rise, tick: .light))
        if hasLead {
            beats.append(IntroBeat(key: .lead, at: shift + leadAt, duration: IntroMotion.rise))
        }
        return beats
    }

    static func shift(_ insights: Bool) -> TimeInterval {
        insights ? insightsShift : 0
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `cd apps/ios && build/intro-test.sh --fresh IntroScoreSectionsTests 2>&1 | tail -20`
Expected: PASS. `rows.map(\.at)` compares floats exactly. If 0.32 + 0.14 fails by an ulp, compare each with `abs(… ) < 1e-12` instead.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Wrap/Intro/IntroScore+Sections.swift apps/ios/PebblesTests/Wrap/IntroScoreSectionsTests.swift
git commit -m "feat(ios): score the month sections' intro

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 13: Rows and rings take a reveal

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Wrap/Atoms/MarkRow.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Wrap/Atoms/DaySetMarks.swift`

- [ ] **Step 1: `MarkRow`: add the reveal**

1. Add the stored property after `var actionHint: String?`:

   ```swift
       /// Its columns' keys in the month's intro (#1241); nil, it draws as ever.
       var reveal: MarkRowReveal?
   ```
2. In the main `init`, add `reveal: MarkRowReveal? = nil,` after `actionHint: String? = nil,` and `self.reveal = reveal` after `self.actionHint = actionHint`.
3. In the `extension MarkRow where Trailing == EmptyView` init, add the same `reveal: MarkRowReveal? = nil,` parameter after `actionHint`, and pass `reveal: reveal` through to `self.init`.
4. Replace `row` with:

```swift
    private var row: some View {
        HStack(spacing: SpineLayout.rowGap) {
            leading
                .frame(width: SpineLayout.markColumn)
                .introReveal(reveal?.leading, .pop)
            VStack(alignment: .leading, spacing: 0) {
                Text(verbatim: title)
                    .spineType(titleType)
                    .foregroundStyle(titleColor)
                if let subtitle {
                    Text(verbatim: subtitle)
                        .spineType(.contentsTime)
                        .foregroundStyle(Color.pebbles.outline)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .introReveal(reveal?.text)
            if let count {
                Text(verbatim: "\(count)")
                    .spineType(.contentsTime)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .introReveal(reveal?.text)
            }
            trailing
                .introReveal(reveal?.trailing)
        }
        .frame(minHeight: SpineLayout.rowMin)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Color.pebbles.rule).frame(height: SpineLayout.rule)
                .introReveal(reveal?.text, .fade)
        }
        .contentShape(Rectangle())
    }
```

5. Add at the end of the file:

```swift
/// Which intro keys land a row's columns (#1241): the mark pops; the words,
/// the count and the rule land with `text`; a nil `trailing` lands itself (a
/// person's ring, a domain's bar).
struct MarkRowReveal {
    let leading: IntroKey
    let text: IntroKey
    var trailing: IntroKey?

    /// A list row landing whole on `.row(index)`.
    static func row(_ index: Int) -> MarkRowReveal {
        MarkRowReveal(leading: .row(index), text: .row(index), trailing: .row(index))
    }
}
```

- [ ] **Step 2: `DaySetMarks`: add `introRow`**

1. Add the property after `var ringSize: CGFloat = SpineLayout.dayCircle`:

   ```swift
       /// Its row in the month's persons, for the intro (#1241): the ring draws on `.ring(row)`, each day on
       /// `.dot(row, day)`. Nil, it draws as ever.
       var introRow: Int?
   ```
2. Replace `body`, `draw(in:size:)` and `drawCircle(in:size:)` with:

```swift
    var body: some View {
        let box = DaySetGeometry.size(length: days.length, mode: mode, ringSize: ringSize)
        IntroReader { reveal in
            Canvas { context, size in
                draw(in: context, size: size, reveal: introRow == nil ? .settled : reveal)
            }
        }
        .frame(width: box.width, height: box.height)
        // A week reads in the layout's direction; a ring turns clockwise whatever the language.
        .flipsForRightToLeftLayoutDirection(mode != .circle)
        .accessibilityElement()
        .accessibilityLabel(Text(verbatim: label ?? ""))
        .accessibilityHidden(label == nil)
    }

    private func draw(in context: GraphicsContext, size: CGSize, reveal: IntroReveal) {
        switch mode {
        case .row, .calendar:
            let dot = SpineLayout.dot
            let step = dot + SpineLayout.dotGap
            let leading: Int
            let cells: Int
            if case let .calendar(first) = mode {
                leading = first
                cells = DaySetGeometry.rows(length: days.length, leading: first) * 7
            } else {
                leading = 0
                cells = days.length
            }
            let person = introRow ?? 0
            // The grid fades in over the ring's span; each day present pops as it is reached.
            let grid = reveal(.ring(person))
            for cell in 0..<cells {
                let day = cell - leading
                let isCalendar = mode != .row
                let column = isCalendar ? cell % 7 : cell
                let row = isCalendar ? cell / 7 : 0
                let style: DotStyle = !(0..<days.length).contains(day) ? .quiet : days.contains(day) ? .filled : .ring
                let shown = style == .filled ? reveal(.dot(person, day)) : grid
                guard shown > 0 else { continue }
                // A calendar's padding is the empty quiet: a ring's colour, not the ink.
                let color = style == .filled ? ink : ring
                let center = CGPoint(x: CGFloat(column) * step + dot / 2, y: CGFloat(row) * step + dot / 2)
                var mark = context
                var diameter = dot
                if shown < 1 {
                    mark.opacity = style == .filled ? IntroMotion.popOpacity(shown) : IntroMotion.easeOut(shown)
                    if style == .filled { diameter *= CGFloat(IntroMotion.popScale(shown)) }
                }
                mark.dotMark(center: center, diameter: diameter, style: style, color: color)
            }
        case .circle:
            drawCircle(in: context, size: size, reveal: reveal)
        }
    }

    /// The board's ring: one thin line round the span, open across the 1st with
    /// a tick there, and a dot on each day present only, so the eye reads the
    /// days, not the grid. In the intro the line draws from the 1st and each
    /// dot pops as it is reached.
    private func drawCircle(in context: GraphicsContext, size: CGSize, reveal: IntroReveal) {
        guard days.length > 0 else { return }
        let person = introRow ?? 0
        let dot = max(SpineLayout.dayCircleDot * ringSize / SpineLayout.dayCircle, 3)
        let geometry = RingGeometry(slots: days.length, gapSlots: SpineLayout.dayCircleGapSlots)
        let center = CGPoint(x: size.width / 2, y: size.height / 2)
        let radius = min(size.width, size.height) / 2 - dot / 2
        let drawn = reveal(.ring(person))
        if drawn > 0 {
            let start = geometry.degrees(-0.5)
            let end = geometry.degrees(Double(days.length) - 0.5)
            let sweep = drawn >= 1 ? end - start : (end - start) * IntroMotion.easeInOut(drawn)
            var arc = Path()
            arc.addRelativeArc(center: center, radius: radius, startAngle: .degrees(start), delta: .degrees(sweep))
            context.stroke(arc, with: .color(ring.opacity(0.7)), lineWidth: SpineLayout.dotRing)
            var tick = Path()
            tick.move(to: CGPoint(x: center.x, y: center.y - radius - dot / 2))
            tick.addLine(to: CGPoint(x: center.x, y: center.y - radius + dot / 2))
            context.stroke(tick, with: .color(ring), lineWidth: SpineLayout.dotRing)
        }
        for day in days.days {
            let shown = reveal(.dot(person, day))
            guard shown > 0 else { continue }
            var mark = context
            var diameter = dot
            if shown < 1 {
                mark.opacity = IntroMotion.popOpacity(shown)
                diameter *= CGFloat(IntroMotion.popScale(shown))
            }
            mark.dotMark(center: geometry.point(Double(day), center: center, radius: radius), diameter: diameter,
                         style: .filled, color: ink)
        }
    }
```

- [ ] **Step 3: Prove the settled rows and rings are unchanged**

Run: `cd apps/ios && build/intro-test.sh References 2>&1 | tail -30`
Expected: every reference matches Task 0's baseline (Part 1's `intro-garland-half` is recorded by now; it passes too). `DaySetMarks` and `MarkRow` draw across the week sheet, the month sheet and the Spine, so this is the wide check.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Wrap/Atoms/MarkRow.swift apps/ios/Pebbles/Features/Path/Wrap/Atoms/DaySetMarks.swift
git commit -m "feat(ios): let the wrap rows and day rings land in the intro

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 14: Weeks, milestones and domains play

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Wrap/Month/MonthSections.swift` (`WeeksSummary`, `MilestonesSection`, `DomainsSection`)

- [ ] **Step 1: Replace `WeeksSummary`'s `body` and add its statics**

```swift
    var body: some View {
        let weeks = Self.weeks(wrap)
        if !weeks.isEmpty {
            VStack(alignment: .leading, spacing: 0) {
                MonthSectionHead(label: WrapCopy.weeksHeader(weeks.count, locale: locale),
                                 hint: WrapCopy.weeksHint(wrap.month, locale: locale))
                    .introReveal(.header)
                VStack(spacing: 0) {
                    ForEach(Array(weeks.enumerated()), id: \.element.weekStart) { index, week in
                        let range = WrapCopy.range(wrap.month, from: week.from, to: week.to, locale: locale)
                        let line = WrapCopy.line(pebbles: week.pebbles, people: week.soulIds.count, live: false,
                                                 hasMoments: true, locale: locale)
                        let focus = { onWeek(week.weekStart) }
                        MarkRow(line, accessibilityText: "\(range), \(line)", titleType: .mouthLine, subtitle: range,
                                action: focus, reveal: .row(index)) {
                            CairnMark(days: week.days, state: .settled, color: Color.pebbles.onSurfaceVariant)
                        } trailing: {
                            SoulMarks(ids: week.soulIds, lookups: lookups, limit: 5)
                        }
                    }
                }
                .padding(.horizontal, MonthLayout.sectionPadX)
            }
            .introSequence(.section(.weeks)) { Self.introBeats(wrap) }
        }
    }

    private static func weeks(_ wrap: MonthWrap) -> [MonthWeek] {
        (wrap.weeks ?? []).filter { $0.pebbles > 0 }
    }

    /// The weeks' intro (#1241): the head, then each week.
    static func introBeats(_ wrap: MonthWrap) -> [IntroBeat] {
        IntroScore.rows(count: weeks(wrap).count, hasLead: false, more: false)
    }
```

- [ ] **Step 2: Replace `MilestonesSection`'s `body` and `row`, and add its statics**

```swift
    var body: some View {
        let moments = Self.moments(wrap, lookups: lookups)
        if !moments.isEmpty {
            VStack(alignment: .leading, spacing: 0) {
                MonthSectionHead(label: WrapCopy.milestonesHeader(moments.count, locale: locale),
                                 hint: WrapCopy.milestonesHint(locale: locale))
                    .introReveal(.header)
                VStack(spacing: 0) {
                    ForEach(Array(moments.enumerated()), id: \.element.id) { index, moment in
                        row(moment, index: index)
                    }
                }
                .padding(.horizontal, MonthLayout.sectionPadX)
            }
            .introSequence(.section(.milestones)) { Self.introBeats(wrap: wrap, lookups: lookups) }
        }
    }

    private static func moments(_ wrap: MonthWrap, lookups: WrapLookups) -> [MomentSummary] {
        (wrap.milestones ?? []).compactMap(lookups.moment)
    }

    /// The milestones' intro (#1241): the head, then each stone.
    static func introBeats(wrap: MonthWrap, lookups: WrapLookups) -> [IntroBeat] {
        IntroScore.rows(count: moments(wrap, lookups: lookups).count, hasLead: false, more: false)
    }

    private func row(_ moment: MomentSummary, index: Int) -> some View {
        let name = moment.name.flatMap { $0.trimmingCharacters(in: .whitespaces).isEmpty ? nil : $0 }
            ?? SpineCopy.untitled(locale: locale)
        let cut = EntryTypes.contentsNameType(intensity: moment.pebble?.intensity ?? 2,
                                              valence: moment.pebble?.valence.positiveness ?? 0)
        let date = WrapCopy.dateline(CalendarDay(moment.happenedAt, in: SpineCalendar.current.timeZone), locale: locale)
        let open = { onOpen(moment) }
        return MarkRow(name, accessibilityText: "\(name), \(date)", titleType: .contentsName(cut), subtitle: date,
                       action: open, reveal: .row(index)) {
            StoneSeal(moment: moment,
                      palette: SpineColors.palette(lookups.palette(moment), isBlank: moment.pebble == nil),
                      size: .milestone)
        } trailing: {
            SoulMarks(ids: (moment.pebble?.souls ?? []).map { $0.id.uuidString.lowercased() }, lookups: lookups,
                      limit: 4)
        }
        .modifier(ReturnFocus(focus: focus, id: moment.id))
    }
```

- [ ] **Step 3: Replace `DomainsSection`'s `body` and add its statics**

```swift
    var body: some View {
        let domains = Self.rows(wrap, lookups: lookups)
        if let top = domains.map(\.stat.count).max(), top > 0 {
            VStack(alignment: .leading, spacing: 0) {
                MonthSectionHead(label: WrapCopy.tile(.domains, locale: locale))
                    .introReveal(.header)
                VStack(spacing: 0) {
                    ForEach(Array(domains.prefix(WrapCopy.shown).enumerated()), id: \.element.id) { index, row in
                        let share = Double(row.stat.count) / Double(top)
                        MarkRow(row.name, accessibilityText: "\(row.name), \(row.stat.count)", count: row.stat.count,
                                reveal: .row(index)) {
                            EmptyView()
                        } trailing: {
                            // The bar grows to its share as its row lands.
                            IntroReader { reveal in
                                let grown = share * IntroMotion.easeOut(reveal(.row(index)))
                                ProportionBar(segments: [BarSegment(weight: grown,
                                                                    color: Color.pebbles.onSurfaceVariant),
                                                         BarSegment(weight: 1 - grown, color: Color.pebbles.rule)])
                                    .frame(width: 96)
                            }
                        }
                    }
                }
                .padding(.horizontal, MonthLayout.sectionPadX)
                MoreLink(text: WrapCopy.moreDomains(domains.count, locale: locale), action: onMore)
                    .accessibilityHint(Text(verbatim: WrapCopy.tileHint(locale: locale)))
                    .padding(.horizontal, MonthLayout.sectionPadX)
                    .introReveal(.more)
            }
            .introSequence(.section(.domains)) { Self.introBeats(wrap: wrap, lookups: lookups) }
        }
    }

    /// The domains the device knows, with their names, in the server's order.
    private static func rows(_ wrap: MonthWrap, lookups: WrapLookups) -> [MonthDomainRow] {
        (wrap.domains ?? []).compactMap { stat in
            lookups.domain(stat.domainId).map { MonthDomainRow(stat: stat, name: $0.localizedName) }
        }
    }

    /// The domains' intro (#1241): the head, the five rows, *more*.
    static func introBeats(wrap: MonthWrap, lookups: WrapLookups) -> [IntroBeat] {
        IntroScore.rows(count: min(rows(wrap, lookups: lookups).count, WrapCopy.shown), hasLead: false, more: true)
    }
```

- [ ] **Step 4: Prove the settled sections are unchanged**

Run: `cd apps/ios && build/intro-test.sh References/WrapMonthReferences 2>&1 | tail -30`
Expected: PASS (`weeks-milestones`, `persons-domains`, the sheets).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Wrap/Month/MonthSections.swift
git commit -m "feat(ios): land the month's weeks, milestones and domains row by row

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 15: The persons play

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Wrap/Month/MonthSections.swift` (`PersonsSection`, `MonthPersonRow`)
- Modify: `apps/ios/PebblesTests/References/WrapIntroReferences.swift`

- [ ] **Step 1: Replace `PersonsSection`'s `body` and add its statics**

```swift
    var body: some View {
        if wrap.persons != nil {
            let rows = Self.rows(wrap, lookups: lookups)
            VStack(alignment: .leading, spacing: 0) {
                let clockwise = dayView == .circle && !rows.isEmpty
                MonthSectionHead(label: WrapCopy.tile(.persons, locale: locale),
                                 hint: clockwise ? WrapCopy.clockwiseHint(locale: locale) : nil)
                    .introReveal(.header)
                if rows.isEmpty {
                    SheetLead(WrapCopy.nobody(locale: locale), bottom: 0)
                        .padding(.horizontal, MonthLayout.sectionPadX)
                        .introReveal(.lead)
                } else {
                    VStack(spacing: 0) {
                        ForEach(Array(rows.prefix(WrapCopy.shown).enumerated()), id: \.element.soulId) { index, person in
                            MonthPersonRow(person: person, wrap: wrap, lookups: lookups, dayView: dayView,
                                           introRow: index)
                        }
                    }
                    .padding(.horizontal, MonthLayout.sectionPadX)
                    MoreLink(text: WrapCopy.morePersons(rows.count, locale: locale), action: onMore)
                        .accessibilityHint(Text(verbatim: WrapCopy.tileHint(locale: locale)))
                        .padding(.horizontal, MonthLayout.sectionPadX)
                        .introReveal(.more)
                }
            }
            .introSequence(.section(.persons)) { Self.introBeats(wrap: wrap, lookups: lookups) }
        }
    }

    /// The souls the device can name, in the server's order.
    private static func rows(_ wrap: MonthWrap, lookups: WrapLookups) -> [PersonStat] {
        (wrap.persons ?? []).filter { lookups.soulName($0.soulId) != nil }
    }

    /// The persons' intro (#1241): the head, then each of the five in turn, or the invitation.
    static func introBeats(wrap: MonthWrap, lookups: WrapLookups) -> [IntroBeat] {
        let rows = rows(wrap, lookups: lookups)
        return IntroScore.persons(rings: rows.prefix(WrapCopy.shown).map(\.days), hasLead: rows.isEmpty)
    }
```

- [ ] **Step 2: `MonthPersonRow` takes its row**

1. Add `var introRow: Int?` after `let dayView: DayView`, with the doc comment `/// Its place in the month page's persons, for the intro (#1241); the sheet leaves it nil.`
2. Change the `MarkRow(…)` call to pass `reveal: introRow.map { MarkRowReveal(leading: .glyph($0), text: .name($0)) }` after `count: person.count`.
3. Change the trailing `DaySetMarks(…)` to `DaySetMarks(days: person.days, mode: wrap.dayMode(dayView), ringSize: MonthLayout.rowRing, introRow: introRow)`.

- [ ] **Step 3: Add the persons' reference to `WrapIntroReferences`**

Add inside `struct WrapIntroReferences`:

```swift
        /// The persons with the first ring half drawn: the first glyph and name up, the rest to come.
        @Test
        func personsRing() throws {
            let wrap = try MonthPayloadFixture.october()
            let lookups = MonthPayloadFixture.lookups(wrap)
            let beats = PersonsSection.introBeats(wrap: wrap, lookups: lookups)
            let ring = try #require(beats.first { $0.key == .ring(0) })
            let clock = IntroClock.frozen(month: wrap.month, sequence: .section(.persons), beats: beats,
                                          elapsed: ring.at + ring.duration / 2)
            let view = PersonsSection(wrap: wrap, lookups: lookups, dayView: .circle, onMore: {})
                .padding(.bottom, 24)
                .background(Color.pebbles.surface)
                .environment(\.introClock, clock)
            ReferenceImage.assertMatches(view, named: "intro-persons-ring", config: ReferenceConfig(scheme: .paper))
        }
```

- [ ] **Step 4: Run the references**

Run: `cd apps/ios && build/intro-test.sh References 2>&1 | tail -30`
Expected: everything passes except `intro-persons-ring` ("no reference"). Open its actual PNG and check:
- the head is up
- row 0's glyph and name are up and its ring is about half drawn, with the dots on that half
- rows 1–4 and *more* are blank

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Wrap/Month/MonthSections.swift apps/ios/PebblesTests/References/WrapIntroReferences.swift
git commit -m "feat(ios): land each person of the month, glyph, name and ring

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 16: Ship Part 2

- [ ] **Step 1:** Lint, the full suite and the French references, exactly as in Task 11 steps 1–2. Expected: only `intro-persons-ring` lacks a reference.
- [ ] **Step 2:** Watch it in the Lab on the simulator. After the cover, the weeks play, then the milestones, then the persons row by row, then the domains with their bars growing. Scrolling fast to the bottom plays only what you stop on.
- [ ] **Step 3:** One independent review of the part's diff (superpowers:requesting-code-review on `git diff <part-1-branch>...HEAD -- apps/ios`). Fix confirmed findings.
- [ ] **Step 4:** Push Part 2 with the `gh-stack` skill. PR title `feat(ios): land the month's lists and persons in its intro`. Body "Part 2 of 4 for #1241", same labels and milestone, "New reference: `intro-persons-ring`", no finding ids. Add `rebaseline-ios-screenshots` alone, approve the held run, then pull the bot's commit.

---

# Part 3 — the gem (`feat/1241-ios-intro-gem`)

### Task 17: The gem's score

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Wrap/Intro/IntroScore+Gem.swift`
- Test: `apps/ios/PebblesTests/Wrap/IntroScoreGemTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

/// #1241 §Choreography, the gem: ta-ta-ta-ta-ta · ta-ta-ta-ta · ta-ta-ta.
@Suite("Intro score: the gem")
struct IntroScoreGemTests {
    @Test("a run that crosses the top sweeps clockwise through it, from after its widest gap")
    func sweep() {
        let facets: [(id: String, theta: Double)] = [("kite_0", 0), ("girdle_1", 0.25), ("star_6", 6.5),
                                                     ("girdle_0", -0.25)]
        #expect(IntroScore.sweep(facets) == ["star_6", "girdle_0", "kite_0", "girdle_1"])
        #expect(IntroScore.sweep([("kite_3", 3)]) == ["kite_3"])
    }

    @Test("the families that own facets, in the lead order, each with exactly its facets")
    func runs() {
        let facets: [String: String?] = ["kite_0": "joy", "star_0": "joy", "kite_3": "calm", "star_3": nil]
        let runs = IntroScore.gemRuns(order: ["calm", "anger", "joy"], facets: facets)
        #expect(runs.map(\.slug) == ["calm", "joy"])
        #expect(runs[0].facets == ["kite_3"])
        #expect(Set(runs[1].facets) == ["kite_0", "star_0"])
    }

    @Test("facets 45 ms apart, a pause between families, each name with its first facet, the count last")
    func beats() throws {
        let runs = [IntroGemRun(slug: "joy", facets: ["a", "b", "c"]), IntroGemRun(slug: "calm", facets: ["d", "e"])]
        let beats = IntroScore.gem(runs: runs)
        func at(_ key: IntroKey) throws -> TimeInterval { try #require(beats.first { $0.key == key }).at }
        #expect(try at(.lead) > at(.header))
        #expect(try at(.blank) < at(.facet("a")))
        #expect(abs(try at(.facet("b")) - at(.facet("a")) - IntroScore.facetGap) < 1e-12)
        #expect(abs(try at(.facet("d")) - at(.facet("c")) - IntroScore.facetGap - IntroScore.familyPause) < 1e-12)
        #expect(try at(.label("joy")) == at(.facet("a")))
        #expect(try at(.label("calm")) == at(.facet("d")))
        let count = try #require(beats.first { $0.key == .count })
        #expect(count.tick == .firm)
        #expect(abs(try count.at - at(.facet("e")) - IntroScore.countAfter) < 1e-12)
        #expect(beats.filter { if case .facet = $0.key { return true } else { return false } }
            .allSatisfy { $0.tick == .facet })
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd apps/ios && build/intro-test.sh --fresh IntroScoreGemTests 2>&1 | tail -20`
Expected: build failure, `cannot find 'IntroGemRun' in scope`.

- [ ] **Step 3: Write `IntroScore+Gem.swift`**

```swift
import Foundation

/// A family of the gem in the intro: its slug and its facets in sweep order.
struct IntroGemRun: Hashable, Sendable {
    let slug: String
    let facets: [String]
}

/// The gem's beats (#1241 §Choreography): the stone lights family by family.
extension IntroScore {
    static let blankAt: TimeInterval = 0.32
    static let facetsAt: TimeInterval = 0.52
    static let facetGap: TimeInterval = 0.045
    static let familyPause: TimeInterval = 0.22
    static let countAfter: TimeInterval = 0.16

    /// The gem: its head and lead, the blank stone, then each family's facets
    /// in turn (its name and share with its first facet), a pause between
    /// families, and the count in the table last, with *more*.
    static func gem(runs: [IntroGemRun]) -> [IntroBeat] {
        var beats = head(hasLead: true, insights: false)
        beats.append(IntroBeat(key: .blank, at: blankAt, duration: IntroMotion.rise))
        var at = facetsAt
        var last = facetsAt
        for (index, run) in runs.enumerated() {
            if index > 0 { at += familyPause }
            for (place, facet) in run.facets.enumerated() {
                if place == 0 {
                    beats.append(IntroBeat(key: .label(run.slug), at: at, duration: IntroMotion.rise))
                }
                beats.append(IntroBeat(key: .facet(facet), at: at, duration: IntroMotion.light, tick: .facet))
                last = at
                at += facetGap
            }
        }
        beats.append(IntroBeat(key: .count, at: last + countAfter, duration: IntroMotion.rise, tick: .firm))
        beats.append(IntroBeat(key: .more, at: last + countAfter, duration: IntroMotion.rise))
        return beats
    }

    /// The families in `order` that own facets in the server's allocation, each with its facets in sweep order.
    static func gemRuns(order: [String], facets: [String: String?]) -> [IntroGemRun] {
        order.compactMap { slug in
            let owned = GemGeometry.allocated.compactMap { id -> (id: String, theta: Double)? in
                guard (facets[id] ?? nil) == slug, let theta = GemGeometry.byId[id]?.theta else { return nil }
                return (id, theta)
            }
            return owned.isEmpty ? nil : IntroGemRun(slug: slug, facets: sweep(owned))
        }
    }

    /// A run's facets clockwise from the top, starting after the widest gap
    /// between them, so a run that crosses the top still lights as one sweep.
    static func sweep(_ facets: [(id: String, theta: Double)]) -> [String] {
        let turn = Double(GemGeometry.sides)
        let around = facets
            .map { (id: $0.id, theta: ($0.theta.truncatingRemainder(dividingBy: turn) + turn)
                .truncatingRemainder(dividingBy: turn)) }
            .sorted { $0.theta < $1.theta }
        guard around.count > 1 else { return around.map(\.id) }
        let gaps = around.indices.map { index in
            index == 0 ? around[0].theta + turn - around[around.count - 1].theta
                : around[index].theta - around[index - 1].theta
        }
        let start = gaps.indices.max { gaps[$0] < gaps[$1] } ?? 0
        return around.indices.map { around[(start + $0) % around.count].id }
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `cd apps/ios && build/intro-test.sh --fresh IntroScoreGemTests 2>&1 | tail -20`
Expected: PASS. If `#expect(try at(…) > at(…))` is rejected ("try cannot appear to the right"), hoist each `try at(…)` into a `let` first.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Wrap/Intro/IntroScore+Gem.swift apps/ios/PebblesTests/Wrap/IntroScoreGemTests.swift
git commit -m "feat(ios): score the gem's intro family by family

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 18: The gem lights

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Wrap/Gem/GemFigure.swift` (`GemFigure.body`, `GemStone`)
- Modify: `apps/ios/Pebbles/Features/Path/Wrap/Gem/EmotionGem.swift` (`EmotionGem.body` + a static)
- Modify: `apps/ios/PebblesTests/References/WrapIntroReferences.swift`

- [ ] **Step 1: `GemFigure.body`: the stone reads the reveal; the words land**

Replace the `GemFigureLayout(anchors:) { … }` block with:

```swift
        GemFigureLayout(anchors: runs.map(\.anchor)) {
            IntroReader { reveal in
                GemStone(colors: colors, ticks: runs.map(\.anchor), tick: Color.pebbles.outline, reveal: reveal)
            }
            .layoutValue(key: GemPart.self, value: .stone)
            Text(verbatim: count)
                .spineType(.dayNumeral)
                .foregroundStyle(Color.pebbles.onSurface)
                .dynamicTypeSize(...DynamicTypeSize.large)
                .introReveal(.count)
                .layoutValue(key: GemPart.self, value: .count)
            Text(verbatim: countLabel)
                .spineType(.dayWeekday)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                .dynamicTypeSize(...DynamicTypeSize.large)
                .introReveal(.count)
                .layoutValue(key: GemPart.self, value: .countLabel)
            ForEach(Array(runs.enumerated()), id: \.element.label.slug) { index, run in
                Text(verbatim: run.label.name)
                    .spineType(.soulName)
                    .foregroundStyle(Color.pebbles.onSurface)
                    .lineLimit(1)
                    .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                    .introReveal(.label(run.label.slug))
                    .layoutValue(key: GemPart.self, value: .name(index))
                Text(run.label.percent, format: .percent.locale(SpineStrings.appLocale))
                    .spineType(.contentsTime)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .lineLimit(1)
                    .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                    .introReveal(.label(run.label.slug))
                    .layoutValue(key: GemPart.self, value: .share(index))
            }
        }
```

`.introReveal` sits before `.layoutValue` on purpose: the layout reads its values from the outermost modifier.

- [ ] **Step 2: `GemStone`: blank paper, then each facet's colour**

Add `var reveal: IntroReveal = .settled` after `let tick: Color`. Then replace the first `for facet in GemGeometry.facets …` loop with the block below, and draw everything after it (the seams, the table, the ticks) with `stone` instead of `context`:

```swift
            // In the intro (#1241) the stone lands blank, then each facet takes its colour; settled, as ever.
            let blank = reveal(.blank)
            guard blank > 0 else { return }
            var stone = context
            if blank < 1 { stone.opacity = IntroMotion.easeOut(blank) }
            for facet in GemGeometry.facets where facet.kind != .table {
                let shape = path(facet.points)
                let fill = colors.fills[facet.id] ?? colors.seam
                // A facet no family owns lands with the stone.
                let lit = colors.fills[facet.id] == nil ? 1 : reveal(.facet(facet.id))
                if lit < 1 {
                    stone.fill(shape, with: .color(colors.table))
                    stone.stroke(shape, with: .color(colors.table),
                                 style: StrokeStyle(lineWidth: GemMetrics.edge, lineJoin: .round))
                }
                guard lit > 0 else { continue }
                var ink = stone
                if lit < 1 { ink.opacity = stone.opacity * IntroMotion.easeOut(lit) }
                ink.fill(shape, with: .color(fill))
                ink.stroke(shape, with: .color(fill), style: StrokeStyle(lineWidth: GemMetrics.edge, lineJoin: .round))
            }
```

Add a sentence to `GemStone`'s doc comment: "In the month's intro (#1241) `reveal` lands the stone blank, then lights each facet."

- [ ] **Step 3: `EmotionGem`: reveals and the host**

Replace `EmotionGem`'s `body` and add the static:

```swift
    var body: some View {
        if let gem = wrap.gem {
            let words = GemWords(gem: gem, lookups: lookups, locale: locale)
            VStack(alignment: .leading, spacing: 0) {
                MonthSectionHead(label: WrapCopy.tile(.emotions, locale: locale),
                                 hint: WrapCopy.gemHint(locale: locale))
                    .introReveal(.header)
                SheetLead(words.lead)
                    .padding(.horizontal, MonthLayout.sectionPadX)
                    .introReveal(.lead)
                GemFigure(facets: gem.facets, palette: { lookups.family($0)?.palette }, labels: words.labels,
                          count: "\(wrap.pebbles)", countLabel: WrapCopy.gemPebbles(wrap.pebbles, locale: locale),
                          description: words.description)
                MoreLink(text: WrapCopy.moreEmotions(wrap.fineEmotions?.count ?? 0, locale: locale), action: onMore)
                    .accessibilityHint(Text(verbatim: WrapCopy.tileHint(locale: locale)))
                    .padding(.horizontal, MonthLayout.sectionPadX)
                    .introReveal(.more)
            }
            .introSequence(.section(.gem)) { Self.introBeats(wrap: wrap, lookups: lookups) }
        }
    }

    /// The gem's intro (#1241): the families in the lead's order, each with the facets the server gave it.
    static func introBeats(wrap: MonthWrap, lookups: WrapLookups) -> [IntroBeat] {
        guard let gem = wrap.gem else { return [] }
        let words = GemWords(gem: gem, lookups: lookups, locale: SpineStrings.appLocale)
        return IntroScore.gem(runs: IntroScore.gemRuns(order: words.labels.map(\.slug), facets: gem.facets))
    }
```

- [ ] **Step 4: Prove the settled gem is unchanged**

Run: `cd apps/ios && build/intro-test.sh References/WrapGemReferences References/WrapMonthReferences 2>&1 | tail -30`
Expected: PASS. A gem diff means a settled facet was drawn through the new path differently: `stone` must equal `context` and `ink` must equal `stone` when everything is 1. Fix the code.

- [ ] **Step 5: The gem's mid-intro reference**

Add inside `struct WrapIntroReferences`:

```swift
        /// The gem half way through its second family: the first lit, the second lighting, the rest blank.
        @Test
        func gemSecondFamily() throws {
            let wrap = try MonthPayloadFixture.october()
            let lookups = MonthPayloadFixture.lookups(wrap)
            let gem = try #require(wrap.gem)
            let words = GemWords(gem: gem, lookups: lookups, locale: SpineStrings.appLocale)
            let runs = IntroScore.gemRuns(order: words.labels.map(\.slug), facets: gem.facets)
            try #require(runs.count > 1)
            let beats = EmotionGem.introBeats(wrap: wrap, lookups: lookups)
            let second = runs[1].facets
            let middle = try #require(beats.first { $0.key == .facet(second[second.count / 2]) })
            let clock = IntroClock.frozen(month: wrap.month, sequence: .section(.gem), beats: beats,
                                          elapsed: middle.at)
            let view = EmotionGem(wrap: wrap, lookups: lookups, onMore: {})
                .padding(.bottom, 24)
                .background(Color.pebbles.surface)
                .environment(\.introClock, clock)
            ReferenceImage.assertMatches(view, named: "intro-gem-second-family",
                                         config: ReferenceConfig(scheme: .paper))
        }
```

Run: `cd apps/ios && build/intro-test.sh References/WrapIntroReferences 2>&1 | tail -20`
Expected: only `intro-gem-second-family` fails, with "no reference". Its actual PNG should show:
- the head, the lead and the stone up
- the first family's facets coloured and its name up
- the second family part coloured and its name up
- the rest of the facets blank paper, and no count

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Wrap/Gem/GemFigure.swift apps/ios/Pebbles/Features/Path/Wrap/Gem/EmotionGem.swift apps/ios/PebblesTests/References/WrapIntroReferences.swift
git commit -m "feat(ios): light the month's gem facet by facet in its intro

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 19: Ship Part 3

- [ ] **Step 1:** Lint, the full suite and the French references, as in Task 11. Expected: only `intro-gem-second-family` lacks a reference.
- [ ] **Step 2:** In the Lab, the gem lights its families in the lead's order, with the pauses between them; the count lands last.
- [ ] **Step 3:** One independent review of the part's diff; fix confirmed findings.
- [ ] **Step 4:** Push Part 3. PR title `feat(ios): light the month's gem in its intro`. Body "Part 3 of 4 for #1241" and "New reference: `intro-gem-second-family`". Then the same labels, milestone and rebaseline routine as before.

---

# Part 4 — tone and hours (`feat/1241-ios-intro-insights`)

### Task 20: The insights' score

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Wrap/Intro/IntroScore+Insights.swift`
- Test: `apps/ios/PebblesTests/Wrap/IntroScoreInsightsTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

/// #1241 §Choreography, tone and hours.
@Suite("Intro score: tone and hours")
struct IntroScoreInsightsTests {
    @Test("the tone sweeps once, ticking only on days with pebbles, as the sweep reaches them")
    func tone() throws {
        let days = [ToneDay(up: 1, even: 0, down: 0), ToneDay(up: 0, even: 0, down: 0),
                    ToneDay(up: 0, even: 2, down: 0)]
        let beats = IntroScore.tone(days: days, insights: false)
        let sweep = try #require(beats.first { $0.key == .sweep })
        #expect(sweep.at == IntroScore.sweepAt)
        #expect(sweep.duration == IntroScore.sweepSpan)
        #expect(beats.first { $0.key == .day(0) }?.at == sweep.at)
        #expect(!beats.contains { $0.key == .day(1) })
        let last = try #require(beats.first { $0.key == .day(2) })
        #expect(abs(last.at - (sweep.at + sweep.duration)) < 1e-12)
        #expect(last.tick == .light)
    }

    @Test("leading the insights, the head comes first and the rest shifts")
    func insights() throws {
        let beats = IntroScore.tone(days: [ToneDay(up: 1, even: 0, down: 0)], insights: true)
        #expect(beats.first?.key == .insights)
        let header = try #require(beats.first { $0.key == .header })
        #expect(header.at == IntroScore.insightsShift)
        let sweep = try #require(beats.first { $0.key == .sweep })
        #expect(abs(sweep.at - IntroScore.insightsShift - IntroScore.sweepAt) < 1e-12)
    }

    @Test("the hours: the dial, then one stone per mark, slow-fast-slow")
    func hours() {
        let beats = IntroScore.hours(stones: 4, insights: false)
        let stones = (0..<4).compactMap { rank in beats.first { $0.key == .stone(rank) } }
        #expect(stones.count == 4)
        #expect(stones.first?.at == IntroScore.stonesAt)
        #expect(zip(stones.dropFirst(), stones).allSatisfy { $0.at > $1.at })
        #expect(beats.contains { $0.key == .dial })
    }

    @Test("a stone's rank is its time of day from midnight; ties keep their order")
    func ranks() {
        #expect(IntroScore.ranks([23.5, 0.25, 12, 0.25]) == [3, 0, 2, 1])
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd apps/ios && build/intro-test.sh --fresh IntroScoreInsightsTests 2>&1 | tail -20`
Expected: build failure, `type 'IntroScore' has no member 'tone'`.

- [ ] **Step 3: Write `IntroScore+Insights.swift`**

```swift
import Foundation

/// Tone's and hours' beats (#1241 §Choreography): figures drawn progressively.
extension IntroScore {
    static let sweepAt: TimeInterval = 0.32
    static let sweepSpan: TimeInterval = 1.2
    static let dialAt: TimeInterval = 0.32
    static let stonesAt: TimeInterval = 0.52
    static let stonesSpan: TimeInterval = 1.2

    /// The tone: its head (after the insights head when it leads them) and its
    /// sentence, then the sweep left to right, a beat on each day with pebbles
    /// as the sweep reaches it.
    static func tone(days: [ToneDay], insights: Bool) -> [IntroBeat] {
        var beats = head(hasLead: true, insights: insights)
        let start = shift(insights) + sweepAt
        beats.append(IntroBeat(key: .sweep, at: start, duration: sweepSpan))
        for (index, day) in days.enumerated() where day.up + day.even + day.down > 0 {
            let place = days.count > 1 ? Double(index) / Double(days.count - 1) : 0
            beats.append(IntroBeat(key: .day(index), at: start + sweepSpan * IntroMotion.easeInOutInverse(place),
                                   duration: IntroMotion.pop, tick: .light))
        }
        return beats
    }

    /// The hours: its head and sentence, the dial's face, then `stones` stones
    /// in the order of the time of day they happened (`ranks`).
    static func hours(stones: Int, insights: Bool) -> [IntroBeat] {
        var beats = head(hasLead: true, insights: insights)
        let shift = shift(insights)
        beats.append(IntroBeat(key: .dial, at: shift + dialAt, duration: IntroMotion.rise))
        for (rank, time) in IntroCurve.times(count: stones, duration: stonesSpan).enumerated() {
            beats.append(IntroBeat(key: .stone(rank), at: shift + stonesAt + time, duration: IntroMotion.pop,
                                   tick: .light))
        }
        return beats
    }

    /// Each stone's rank by the time of day it happened, from 00:00; ties keep their order.
    static func ranks(_ hours: [Double]) -> [Int] {
        let order = hours.indices.sorted { hours[$0] != hours[$1] ? hours[$0] < hours[$1] : $0 < $1 }
        var ranks = Array(repeating: 0, count: hours.count)
        for (rank, index) in order.enumerated() {
            ranks[index] = rank
        }
        return ranks
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `cd apps/ios && build/intro-test.sh --fresh IntroScoreInsightsTests 2>&1 | tail -20`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Wrap/Intro/IntroScore+Insights.swift apps/ios/PebblesTests/Wrap/IntroScoreInsightsTests.swift
git commit -m "feat(ios): score the tone's and the hours' intro

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 21: The tone sweeps

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Wrap/Month/ToneFigure.swift`

- [ ] **Step 1: `ToneFigure.body`: reveals and the sweep**

In `ToneFigure.body`:
1. Add `.introReveal(.header)` after `SpineSectionHeader(…)`.
2. Add `.introReveal(.lead)` after the sentence's `.padding(.bottom, 8)`.
3. Replace `ToneChart(wrap: wrap, tone: tone, summary: line)` with:

```swift
                IntroSweep { ToneChart(wrap: wrap, tone: tone, summary: line) }
```

Add after `ToneFigure`:

```swift
extension ToneFigure {
    /// The tone's intro (#1241), with the insights head's when it leads them; none when nothing shows.
    static func introBeats(_ wrap: MonthWrap, insights: Bool) -> [IntroBeat] {
        let days = wrap.tone?.days ?? []
        guard insights || !days.isEmpty else { return [] }
        return IntroScore.tone(days: days, insights: insights)
    }
}

/// The tone chart under the intro's sweep (#1241): hidden before it, masked
/// from the leading edge while it runs, untouched once it has passed. The
/// chart is a stored value, so only the mask follows the frames.
private struct IntroSweep<Content: View>: View {
    let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        IntroReader { reveal in
            let sweep = reveal(.sweep)
            if sweep >= 1 {
                content
            } else if sweep <= 0 {
                content.hidden()
            } else {
                content.mask(alignment: .leading) {
                    GeometryReader { geometry in
                        Rectangle()
                            .frame(width: ToneMetrics.sweepWidth(IntroMotion.easeInOut(sweep),
                                                                 width: geometry.size.width))
                    }
                }
            }
        }
    }
}
```

and, after `struct ToneMetrics { … }`:

```swift
extension ToneMetrics {
    /// How much of the chart the intro's sweep shows at eased progress
    /// `eased`: the days run from `inset` to `width − inset`, and the sweep
    /// leads the day it reaches by half a bar.
    static func sweepWidth(_ eased: Double, width: CGFloat) -> CGFloat {
        inset + CGFloat(eased) * (width - 2 * inset) + bar / 2
    }
}
```

- [ ] **Step 2: `ToneChart`: the even days' pulse**

In `ToneChart.body`'s `.chartOverlay { proxy in GeometryReader { geometry in … } }`, add a line after `words(proxy: proxy, plot: plot, metrics: metrics)`:

```swift
                pulses(proxy: proxy, plot: plot)
```

and add the method to `ToneChart`:

```swift
    /// The intro's pulse on each even day as the sweep reaches it (#1241): a
    /// ring growing out of the circle and fading. Nothing when settled.
    private func pulses(proxy: ChartProxy, plot: CGRect) -> some View {
        IntroReader { reveal in
            ZStack(alignment: .topLeading) {
                Color.clear
                ForEach(tone.days.indices.filter { tone.days[$0].even > 0 }, id: \.self) { index in
                    let t = reveal(.day(index))
                    if t > 0 && t < 1 {
                        let radius = ToneMetrics.evenRadius(tone.days[index].even)
                        Circle()
                            .stroke(Color.pebbles.onSurfaceVariant, lineWidth: 1)
                            .frame(width: radius * 2, height: radius * 2)
                            .scaleEffect(CGFloat(1 + t))
                            .opacity(1 - t)
                            .position(x: plot.minX + (proxy.position(forX: index) ?? 0),
                                      y: plot.minY + (proxy.position(forY: 0.0) ?? 0))
                    }
                }
            }
        }
    }
```

- [ ] **Step 3: Prove the settled tone is unchanged**

Run: `cd apps/ios && build/intro-test.sh References/WrapInsightsReferences 2>&1 | tail -30`
Expected: PASS. Settled, `IntroSweep` returns the chart itself, and the pulses draw nothing over a clear `ZStack`. If the reference moves, the extra overlay child is the suspect. Move `pulses` into its own `.overlay` on the `Chart`, outside the `chartOverlay`, and run again.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Wrap/Month/ToneFigure.swift
git commit -m "feat(ios): sweep the month's tone in its intro

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 22: The hours land stone by stone

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Wrap/Month/HoursFigure.swift`

- [ ] **Step 1: `HoursFigure`: reveals, the dial reads the reveal, the beats**

1. In `HoursFigure.body`, add `.introReveal(.header)` after `.padding(.top, 28)` on the `SpineSectionHeader`, and `.introReveal(.lead)` after the sentence's `.padding(.top, 6)`.
2. Replace `HoursDial(items: items, busiest: hours.busiest, lookups: lookups)` with:

```swift
                    IntroReader { reveal in
                        HoursDial(items: items, busiest: hours.busiest, lookups: lookups, reveal: reveal)
                    }
```

Then add after `HoursFigure`:

```swift
extension HoursFigure {
    /// The hours' intro (#1241), with the insights head's when it leads them; none when nothing shows.
    static func introBeats(wrap: MonthWrap, lookups: WrapLookups, insights: Bool) -> [IntroBeat] {
        let stones = wrap.hours?.items.filter { lookups.moment($0.momentId) != nil }.count ?? 0
        guard insights || stones > 0 else { return [] }
        return IntroScore.hours(stones: stones, insights: insights)
    }
}
```

- [ ] **Step 2: `HoursDial`: the face, then each stone with its wait and dot**

1. Add `var reveal: IntroReveal = .settled` after `let lookups: WrapLookups`.
2. In `body`, add `let ranks = IntroScore.ranks(items.map(\.mark.happened))` after `let centre = …`, and make the Canvas call `draw(in: context, centre: centre, prints: prints, ranks: ranks)`.
3. Add `.introReveal(.dial, .fade)` to the hour numbers' `Text`, before `.position(…)`, and to the busiest window's `VStack`, after `.multilineTextAlignment(.center)`.
4. Replace `draw`, `drawWaits`, `drawDots` and `drawStones` with:

```swift
    private func draw(in context: GraphicsContext, centre: CGPoint, prints: [SealPrint], ranks: [Int]) {
        // In the intro (#1241) the face lands first, then each stone by the time of day, with its wait and dot.
        let face = reveal(.dial)
        if face > 0 {
            var dial = context
            if face < 1 { dial.opacity = IntroMotion.easeOut(face) }
            drawFace(in: dial, centre: centre)
        }
        let shown = ranks.map { reveal(.stone($0)) }
        drawWaits(in: context, centre: centre, shown: shown)
        drawDots(in: context, centre: centre, shown: shown)
        drawStones(in: context, centre: centre, prints: prints, shown: shown)
    }

    /// The waits: a spiral from the stone's hour, clockwise, to the collection's.
    private func drawWaits(in context: GraphicsContext, centre: CGPoint, shown: [Double]) {
        let outer = Metric.outer
        let inner = Metric.inner
        for (index, item) in items.enumerated() where shown[index] > 0 {
            let lag = HoursLayout.forward(item.mark.happened, item.mark.collected)
            let steps = max(2, Int((lag * 3).rounded(.up)))
            let from = outer - 1.5
            let to = inner + 1.5
            var spiral = Path()
            for step in 0...steps {
                let t = Double(step) / Double(steps)
                let point = at(item.mark.happened + lag * t, from + (to - from) * CGFloat(t), centre)
                if step == 0 { spiral.move(to: point) } else { spiral.addLine(to: point) }
            }
            var wait = context
            if shown[index] < 1 { wait.opacity = IntroMotion.popOpacity(shown[index]) }
            wait.stroke(spiral, with: .color(Color.pebbles.outline.opacity(Metric.waitAlpha)),
                        style: StrokeStyle(lineWidth: Metric.wait, lineCap: .round))
        }
    }

    /// Inside, a dot on the hour each pebble was collected, in lanes so none touch.
    private func drawDots(in context: GraphicsContext, centre: CGPoint, shown: [Double]) {
        let inner = Metric.inner
        let dot = Metric.dot
        let inLanes = HoursLayout.lanes(items.map(\.mark.collected), sizes: items.map { _ in dot * 2 + 0.6 },
                                        limit: Metric.innerLanes) { lane in
            2 * .pi * (inner - Metric.dotOff - CGFloat(lane) * Metric.laneIn) / 24
        }
        for (index, (item, lane)) in zip(items, inLanes).enumerated() where shown[index] > 0 {
            let point = at(item.mark.collected, inner - Metric.dotOff - CGFloat(lane) * Metric.laneIn, centre)
            var mark = context
            if shown[index] < 1 { mark.opacity = IntroMotion.popOpacity(shown[index]) }
            mark.fill(Path(ellipseIn: CGRect(x: point.x - dot, y: point.y - dot, width: dot * 2, height: dot * 2)),
                      with: .color(Color.pebbles.onSurfaceVariant))
        }
    }

    /// Outside, each stone on the hour it happened, in lanes, turned along the ring.
    private func drawStones(in context: GraphicsContext, centre: CGPoint, prints: [SealPrint], shown: [Double]) {
        let outer = Metric.outer
        let widths = items.map { SealSize.tiny.width($0.moment.pebble?.valence.sizeGroup ?? .medium) }
        let outLanes = HoursLayout.lanes(items.map(\.mark.happened), sizes: widths.map { $0 + 1 },
                                         limit: Metric.outerLanes) { lane in
            2 * .pi * (outer + Metric.stoneOff + CGFloat(lane) * Metric.laneOut) / 24
        }
        for index in items.indices where shown[index] > 0 {
            let hour = items[index].mark.happened
            let point = at(hour, outer + Metric.stoneOff + CGFloat(outLanes[index]) * Metric.laneOut, centre)
            let print = prints[index]
            let width = widths[index]
            let height = width * print.frame.height / print.frame.width
            var seal = context
            seal.translateBy(x: point.x, y: point.y)
            seal.rotate(by: .degrees((hour - 12) / 24 * 360))
            if shown[index] < 1 {
                seal.opacity = IntroMotion.popOpacity(shown[index])
                let scale = CGFloat(IntroMotion.popScale(shown[index]))
                seal.scaleBy(x: scale, y: scale)
            }
            seal.drawSeal(print, in: CGRect(x: -width / 2, y: -height / 2, width: width, height: height))
        }
    }
```

- [ ] **Step 3: Prove the settled hours are unchanged**

Run: `cd apps/ios && build/intro-test.sh References/WrapInsightsReferences 2>&1 | tail -30`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Wrap/Month/HoursFigure.swift
git commit -m "feat(ios): land the month's hours stone by stone in its intro

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 23: The page hosts tone and hours with their shared head

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Wrap/Month/MonthPage.swift` (`MonthPageSections`'s `.tone, .hours` case)

The insights head lives in `MonthPageSections`, so tone and hours are hosted there, head included. That leaves `ToneFigure` and `HoursFigure` unchanged where `WrapInsightsReferences` lays them out.

- [ ] **Step 1: Replace the `case .tone, .hours:` branch**

```swift
            case .tone, .hours:
                // One head for both figures, over whichever comes first; it plays with that figure (#1241).
                let leads = section == wrap.sections.first(where: { $0 == .tone || $0 == .hours })
                VStack(alignment: .leading, spacing: 0) {
                    if leads {
                        MonthSectionHead(label: WrapCopy.insights(locale: SpineStrings.appLocale),
                                         hint: WrapCopy.insightsHint(locale: SpineStrings.appLocale))
                            .introReveal(.insights)
                    }
                    if section == .tone {
                        ToneFigure(wrap: wrap)
                    } else {
                        HoursFigure(wrap: wrap, lookups: lookups)
                    }
                }
                .introSequence(.section(section)) {
                    section == .tone
                        ? ToneFigure.introBeats(wrap, insights: leads)
                        : HoursFigure.introBeats(wrap: wrap, lookups: lookups, insights: leads)
                }
```

- [ ] **Step 2: The whole suite**

Run: `cd apps/ios && build/intro-test.sh 2>&1 | tail -30`
Expected: every test and every reference passes (Parts 1–3's references are recorded by now).

- [ ] **Step 3: Watch it in the Lab**

At the foot of the page:
- the insights head rises
- the tone sweeps left to right, with each even day pulsing
- then the hours' face fades in and its stones land round the dial from midnight, clockwise

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Wrap/Month/MonthPage.swift
git commit -m "feat(ios): play the month's tone and hours with their head

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 24: Ship Part 4

- [ ] **Step 1:** Lint, the full suite and the French references, as in Task 11. Expected: everything passes; there is no new reference in this part.
- [ ] **Step 2:** One independent review of the part's diff; fix confirmed findings.
- [ ] **Step 3:** Push Part 4.
  - **PR title:** `feat(ios): draw the month's tone and hours in its intro`
  - **PR body:** starts with `Resolves #1241`, then "Part 4 of 4"
  - **Labels and milestone:** the same as Parts 1–3
  - **Rebaseline:** no `rebaseline-ios-screenshots` label unless a reference moved, and none should have.

---

## Task 25: The device pass (iPhone 15), before the stack merges

The memory note "iOS device deploy recipe" holds the build, install and launch commands and the device ids. The maintainer's phone holds their real account. The intro is read-only, so no write happens, but ask before installing.

- [ ] **Feel:** open October in the Lab, then a real month on the Path. Tune the constants in `IntroScore`, `IntroMotion` and `IntroTick` until the feel is right. Use *Replay intro* in the Lab for each try. Commit the tuned values on the part that owns them, with the `gh-stack` skill's flow for amending a lower part, and record the final values below.
- [ ] **Haptics:**
  - Count the garland's ticks on a 31-day month. None should be lost in the fast middle.
  - The stacked days tap twice.
  - The gem goes ta-ta-ta-ta-ta · ta-ta-ta-ta · ta-ta-ta.
- [ ] **System Haptics:** turn off Settings → Sounds & Haptics → System Haptics and replay.
  - If the intro still buzzes, Core Haptics ignores the switch. Switch `IntroHaptics.play` to UIKit generators fired from the clock (D1's hybrid), in a follow-up commit on Part 1, and log it as a new decision-log entry that supersedes the System Haptics consequence.
  - Record what you found below either way.
- [ ] **Interruptions:**
  - Swiping away mid-garland stops the buzz at once.
  - Backgrounding the app mid-garland settles it.
  - Tapping a milestone mid-section opens its held card with no buzz under it.
  - Opening *more* mid-section shows the sheet, silent.
- [ ] **Once per launch:** swiping back to October shows it settled. A section never reached plays when first reached. After a relaunch, October plays again.
- [ ] **Accessibility:** with VoiceOver on, the month page is settled and the page announcement is unchanged. With Reduce Motion on, it's settled and silent.
- [ ] **Smoothness:** run Instruments (SwiftUI and Animation Hitches) during the garland and the gem. If frames drop, apply the spec's Risks remedy: draw the landed marks into a `drawingGroup()` layer and redraw only the moving ones. Record the result below.

## As built

- **Local reference baseline (Task 0, 2026-10-25):** 27 existing reference tests fail locally on the untouched branch. This Mac's renders differ from CI's: fonts and sizes, macOS 26.5.1 SDK, iOS 26.3 simulator. Among them:
  - `WrapInsightsReferences` (both)
  - `WrapWeekReferences` (`tiles`, `tilesAx2`, `sheetPage`)
  - `SpineFeedReferences`, `SpineSeamReferences`, `SpineCardReferences`, `HeldCard*References`, `StoneReferences`, `SpineStoneReferences`

  The list is in `apps/ios/build/baseline-failures.txt`, and their rendered PNGs are in `apps/ios/build/baseline-diffs/` (gitignored, this worktree only). The local gate is therefore `build/intro-compare.sh`, run after `build/intro-test.sh … References`. It checks two things:
  - no failure outside the baseline (bar the new `intro-*` ones)
  - every PNG a baseline failure renders is byte-identical to the baseline's

  `WrapMonthReferences`, `WrapGemReferences`, `WrapAtomsReferences` and `WrapStripReferences` pass locally and stay strict gates. CI remains the real gate.

Fill in as the parts land. List each deviation from this plan, and why:
- the local reference baseline from Task 0
- tuned constants
- the System Haptics finding
- the Instruments result
