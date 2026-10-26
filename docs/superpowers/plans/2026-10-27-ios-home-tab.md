# iOS Home tab (stack 2, #1255) — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the Home tab to the iOS app as the fifth tab in first position and the cold-start landing: greeting, three shortcuts, the folded monthly wraps, the achievement-backed first five ending in the blue pebble claim, and the About Pebbles guides.

**Architecture:** Display only. The server already answers everything (`home_wraps`, `reveal_wrap`, `claim_blue_pebble`, `check_achievements`, `guides`, `save_moment`); iOS decodes, maps and draws in the Spine's idiom with system parts (spec D2). One `HomeModel` (`@Observable`) loads three projections stale-while-revalidate; one `HomeService` behind a protocol so tests and the Lab use fakes. Nothing in Home counts, ranks or thresholds.

**Tech Stack:** SwiftUI (iOS 17), `@Observable`, Swift Testing, `ImageRenderer` references, Supabase Swift client, `Localizable.xcstrings` (EN + FR).

**Spec:** `docs/superpowers/specs/2026-10-26-home-tab-and-empty-states-design.md` §1, §6, §7, §8 and D1–D14. **Back-end:** stack #1262 (merged 2026-10-27). **Payload fixtures:** `packages/supabase/scripts/oracles/home/payloads/{home-wraps,people-prompts}.json`.

**Read before starting:** `apps/ios/CLAUDE.md`; `docs/superpowers/plans/2026-10-24-ios-path-wraps.md` §1 (ground rules) and §7 (as built); the memory notes on parallel simulators, the xcodegen stale build and the local reference baseline (they are restated in Task 0).

---

## Deviations from the spec, decided while planning

- **D-plan-1. The wrap sheet shows `MonthPage` with an intro context, not a clock-less deck.** `MonthDeck` is drawn only with an `IntroClock` (its doc comment and `MonthPage`'s branch). Spec §1.3 asked for "the deck with its intro off"; a clock-less deck does not exist. The sheet therefore presents `MonthPage(wrap:lookups:isFocused:returnFocus:onWeek:onOpen:intro:)` with `IntroContext(ledger: homeModel.introLedger, haptics: IntroHaptics.shared)`: the deck plays once per month per launch, exactly as on the Path, and settles to the plain page under Reduce Motion or VoiceOver. The Path keeps its own ledger, so a month replays once on each tab.
- **D-plan-2. Milestone taps in the Home wrap sheet do nothing in this stack.** `MonthPage.onOpen` hands a `MomentSummary` that only `WrapLookups.moment` can resolve, and Home holds no `PathModel`. `onWeek` switches to the Path tab. Opening a milestone from Home is filed as a follow-up issue in Part 3.
- **D-plan-3. Cross-tab intents live on `TabNavigation`, not on another tab's path.** The tabs spec forbids pushing into another tab's stack from outside; a one-shot `TabNavigation.intent` (`createSoul`, `createCollection`, `carveGlyph`, `openPath`) is shared state the destination root consumes on appear. No tab's `[Route]` is written by Home.
- **D-plan-4. The New pebble shortcut reuses `CaptureFlowView` with a new `chainsComplete` flag.** The flow already swaps its cover to `CompleteFlowView` on "Make it a pebble now"; the flag makes that the only way out after saving, so the two flows read as one.

---

## Conventions that apply to every task

- **Worktree and branches.** `.worktrees/ios-home` on `feat/1255-ios-home-tab`, forked from an up-to-date `origin/main`; the parts stack on it: `feat/1255-ios-home-shortcuts` → `feat/1255-ios-home-wraps` → `feat/1255-ios-home-checklist` → `feat/1255-ios-home-guides` → `feat/1255-ios-home-lab`. `gh stack init` / `gh stack add` as in the `gh-stack` skill; every command non-interactive.
- **Build and test** only through `apps/ios/build/home-test.sh` (Task 0) on this worktree's own simulator with a private `derivedDataPath`. `--fresh` after adding files.
- **References**: the local baseline rule (Task 0 Step 4). Never record references locally; the `rebaseline-ios-screenshots` label records them on CI. Never iterate a `Set` or `Dictionary` while drawing a figure.
- **Strings**: every user-facing literal goes through `Localizable.xcstrings` with EN and FR. Keys for Home are dotted and explicit (`home.greeting.morning.title`), read through `SpineStrings`-style `String(localized:)` with a `Locale` parameter where a test pins both languages; SwiftUI `Text("…")` keys for simple labels. Brand words use `Text(verbatim:)`. Before each PR: no entry in the `New` or `Stale` state, every row has `en` and `fr`.
- **Colours**: `Color.pebbles.<role>` only. **Type**: `spineType(...)` or `pebblesFont(...)`. **Haptics**: `TapHaptics.play(...)`, which already honours `HapticsPreference`.
- **Errors**: every async failure is logged with `os.Logger(subsystem: "app.pbbls.ios", category: "home.<area>")` or reflected in state. No empty catch.
- **Commits**: conventional, lowercase, `feat(ios): …`, each ending with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- **PRs**: body starts `Part N of #1255` (the last part `Resolves #1255`), labels `feat`, `ui`, `ios`, milestone `M64 · New Path`, a `## Lab Note (EN/FR)` section authored with the `lab-note` skill on the parts that ship something a user sees (1, 2, 3, 4, 5); part 6 is `no-lab-note`. Never a Kritik id in a body.
- **Arkaik**: `V-home` is already `development` with its API edges; each part adds its acceptance (Task list below) with `create_node` + a `covers` edge to `V-home`. If the tools are unavailable, say so and skip.

---

## Task 0: Setup (once)

- [ ] **Step 1: Worktree from main, gitignored config copied**

```bash
cd /Users/alexis/code/pbbls && git fetch -q origin && git worktree add .worktrees/ios-home -b feat/1255-ios-home-tab origin/main
cp apps/ios/Config/Secrets.xcconfig .worktrees/ios-home/apps/ios/Config/Secrets.xcconfig
cd .worktrees/ios-home && git status --short   # expect nothing
```

- [ ] **Step 2: A simulator of its own**

```bash
xcrun simctl create "iPhone 17 home" "iPhone 17"
xcrun simctl list devices available | grep "iPhone 17 home"
```

- [ ] **Step 3: The test helper (gitignored `apps/ios/build/`)**

Create `apps/ios/build/home-test.sh`:
```zsh
#!/bin/zsh
# Runs the iOS tests on this worktree's simulator with a private derivedData.
# Usage: build/home-test.sh [--fresh] [Suite …]
set -e
cd "$(dirname "$0")/.."
xcodegen generate --quiet
if [[ "$1" == "--fresh" ]]; then rm -rf build/Build; shift; fi
args=()
for suite in "$@"; do args+=("-only-testing:PebblesTests/$suite"); done
xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17 home' \
  -derivedDataPath build -skipMacroValidation -quiet CODE_SIGNING_ALLOWED=NO "${args[@]}"
```
`chmod +x apps/ios/build/home-test.sh`.

- [ ] **Step 4: Record the local reference baseline on the untouched branch**

```bash
cd apps/ios && build/home-test.sh --fresh References 2>&1 | tail -40
xcrun xcresulttool get test-results summary --path build/Logs/Test/*.xcresult 2>/dev/null | head -60
mkdir -p build/baseline && cp -R build/reference-diffs build/baseline/ 2>/dev/null || true
```
Write the failing list under "As built" at the bottom of this plan. The gate for every later part: the same failing set, and every PNG those failures render byte-identical to `build/baseline/`.

- [ ] **Step 5: Stack init**

```bash
git config rerere.enabled true && git config remote.pushDefault origin
gh stack init feat/1255-ios-home-tab
```

---

# Part 1 — the fifth tab and the greeting (`feat/1255-ios-home-tab`)

### Task 1: `AppTab.home` and the navigation state

**Files:**
- Modify: `apps/ios/Pebbles/Features/Tabs/AppTab.swift`
- Modify: `apps/ios/Pebbles/Features/Tabs/TabNavigation.swift`
- Modify: `apps/ios/Pebbles/Features/Tabs/TabRoutes.swift`
- Test: `apps/ios/PebblesTests/Features/Tabs/TabNavigationTests.swift` (extend), `TabStringsTests.swift` (extend)

- [ ] **Step 1: Failing tests.** In `TabNavigationTests.swift` add, following the suite's existing style:

```swift
@Test func homeIsFirstInBarOrderAndTheDefaultSelection() {
    #expect(AppTab.allCases.first == .home)
    #expect(TabNavigation().selected == .home)
}

@Test func anOldSnapshotWithoutHomeStillRestores() throws {
    // A snapshot written before the Home tab existed (no `home` path field).
    let old = #"{"selected":"people","path":[],"people":[],"collections":[],"you":[]}"#
    let snapshot = try #require(TabNavigation.Snapshot.decoded(from: Data(old.utf8)))
    let nav = TabNavigation()
    nav.restore(snapshot)
    #expect(nav.selected == .people)
    #expect(nav.home.isEmpty)
}

@Test func homeRouteRoundTrips() throws {
    let nav = TabNavigation()
    nav.select(.home)
    nav.home = [.guide("stones")]
    let restored = try #require(TabNavigation.Snapshot.decoded(from: nav.snapshot.encoded))
    #expect(restored.home == [.guide("stones")])
}

@Test func anIntentIsConsumedOnce() {
    let nav = TabNavigation()
    nav.go(.people, intent: .createSoul)
    #expect(nav.selected == .people)
    #expect(nav.takeIntent(for: .people) == .createSoul)
    #expect(nav.takeIntent(for: .people) == nil)
    #expect(nav.takeIntent(for: .collections) == nil)
}
```
In `TabStringsTests.swift` add `"Home"` → `"Accueil"` to the keys/values arrays.

- [ ] **Step 2: Run** `build/home-test.sh --fresh TabNavigationTests TabStringsTests` → compile failure (no `.home`).

- [ ] **Step 3: Implement.**

`AppTab.swift`: `case home, path, people, collections, you` (home first; the raw value `"home"` is new, nothing renames).

`TabRoutes.swift`: add
```swift
/// The Home tab's pushed destinations: a guide, by slug (the catalog key the
/// server owns; never the row id, so a snapshot survives a reseed).
enum HomeRoute: Codable, Hashable {
    case guide(String)
}

/// What a root does once on arrival when another tab sent the user over
/// (spec §1.4): open its add sheet, or start a carve. Consumed with
/// `TabNavigation.takeIntent(for:)` and never persisted.
enum TabIntent: Equatable {
    case createSoul, createCollection, carveGlyph
}
```

`TabNavigation.swift`:
- `var selected: AppTab = .home`; `var home: [HomeRoute] = []`.
- `popToRoot` and `depth(of:)` gain `case .home`.
- `Snapshot` gains `var home: [HomeRoute]`, decoded tolerantly (`decodeIfPresent … ?? []`), with `selected` still defaulting to `.path` when absent (an old snapshot keeps the user where they were); `snapshot`, `restore`, `reset` carry it.
- Intent:
```swift
@ObservationIgnored private var pendingIntent: (tab: AppTab, intent: TabIntent)?

/// Switches to `tab` and leaves one intent for its root (D-plan-3).
func go(_ tab: AppTab, intent: TabIntent? = nil) {
    if let intent { pendingIntent = (tab, intent) }
    if selected != tab { selected = tab } else { popToRoot(tab) }
}

/// The root of `tab` calls this on appear and acts on the result once.
func takeIntent(for tab: AppTab) -> TabIntent? {
    guard let pending = pendingIntent, pending.tab == tab else { return nil }
    pendingIntent = nil
    return pending.intent
}
```
`Localizable.xcstrings`: `"Home"` with `en` "Home", `fr` "Accueil".

- [ ] **Step 4: Run** the two suites → pass. `ThemeLiteralsTests` and `LocalizationTests` too.
- [ ] **Step 5: Commit** `feat(ios): add the home tab to the navigation state`.

### Task 2: `HomeGreeting`

**Files:**
- Create: `apps/ios/Pebbles/Features/Home/HomeGreeting.swift`
- Test: `apps/ios/PebblesTests/Features/Home/HomeGreetingTests.swift`
- Modify: `apps/ios/Pebbles/Resources/Localizable.xcstrings`

- [ ] **Step 1: Failing tests**

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("Home greeting") struct HomeGreetingTests {
    static let paris = TimeZone(identifier: "Europe/Paris")!
    static func date(_ s: String) -> Date {
        let f = ISO8601DateFormatter(); f.timeZone = paris; return f.date(from: s)!
    }
    static let en = Locale(identifier: "en"), fr = Locale(identifier: "fr")

    @Test func mondayMorningOpensTheWeek() {
        let g = HomeGreeting.lines(at: Self.date("2026-11-02T08:10:00+01:00"), name: "Alexis", zone: Self.paris, locale: Self.en)
        #expect(g.title == "Morning, Alexis")
        #expect(g.line == "Hope you'll have a pleasant week!")
        #expect(g.dateline == "Monday 2 November")
    }
    @Test func sundayEveningAsksAboutTheWeek() {
        let g = HomeGreeting.lines(at: Self.date("2026-11-08T20:45:00+01:00"), name: "Alexis", zone: Self.paris, locale: Self.en)
        #expect(g.title == "Good evening, Alexis")
        #expect(g.line == "How was your week?")
    }
    @Test func theBandsFallBackToTheirDefaults() {
        let afternoon = HomeGreeting.lines(at: Self.date("2026-11-04T15:30:00+01:00"), name: "Alexis", zone: Self.paris, locale: Self.en)
        #expect(afternoon.title == "Good afternoon, Alexis")
        #expect(afternoon.line == "Halfway through the day. Anything worth keeping?")
        let evening = HomeGreeting.lines(at: Self.date("2026-11-04T19:00:00+01:00"), name: "Alexis", zone: Self.paris, locale: Self.en)
        #expect(evening.line == "What stays with you from today?")
        let morning = HomeGreeting.lines(at: Self.date("2026-11-04T11:59:00+01:00"), name: "Alexis", zone: Self.paris, locale: Self.en)
        #expect(morning.line == "A new day to keep something from.")
    }
    @Test func frenchHasEveryLine() {
        let g = HomeGreeting.lines(at: Self.date("2026-11-02T08:10:00+01:00"), name: "Alexis", zone: Self.paris, locale: Self.fr)
        #expect(g.title == "Bonjour, Alexis")
        #expect(g.line == "Belle semaine à toi !")
        #expect(g.dateline == "lundi 2 novembre")
    }
    @Test func anEmptyNameDropsTheComma() {
        let g = HomeGreeting.lines(at: Self.date("2026-11-04T09:00:00+01:00"), name: "", zone: Self.paris, locale: Self.en)
        #expect(g.title == "Morning")
    }
}
```

- [ ] **Step 2: Run** → fails (no `HomeGreeting`).

- [ ] **Step 3: Implement**

```swift
import Foundation

/// The Home tab's greeting (spec §1.1): a title, an italic line and a dateline,
/// from the hour band (morning < 12, afternoon 12–18, evening ≥ 18) with the
/// Monday-morning and Sunday-evening overrides. Pure; the clock, zone and
/// locale are passed in so tests pin every band in both languages.
enum HomeGreeting {
    struct Lines: Equatable {
        let title: String
        let line: String
        let dateline: String
    }

    enum Band: String { case morning, afternoon, evening }
    enum Override: String { case mondayMorning = "monday-morning", sundayEvening = "sunday-evening" }

    static func band(hour: Int) -> Band {
        switch hour { case ..<12: .morning; case 12..<18: .afternoon; default: .evening }
    }

    static func lines(at date: Date, name: String, zone: TimeZone, locale: Locale) -> Lines {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        calendar.locale = locale
        let hour = calendar.component(.hour, from: date)
        let weekday = calendar.component(.weekday, from: date) // 1 = Sunday
        let band = band(hour: hour)
        let override: Override? = switch (weekday, band) {
            case (2, .morning): .mondayMorning
            case (1, .evening): .sundayEvening
            default: nil
        }
        let key = override?.rawValue ?? band.rawValue
        let greeting = HomeStrings.string("home.greeting.\(key).title", locale: locale)
        let title = name.isEmpty ? greeting : "\(greeting), \(name)"
        let line = HomeStrings.string("home.greeting.\(key).line", locale: locale)
        let dateline = date.formatted(.dateTime.weekday(.wide).day().month(.wide).locale(locale).calendar(calendar))
        return Lines(title: title, line: line, dateline: dateline)
    }
}

/// Explicit-key strings for Home, resolvable in a given language (the
/// `SpineStrings` pattern), so tests pin `en` and `fr` without the device locale.
enum HomeStrings {
    static func string(_ key: String, locale: Locale) -> String {
        let language = locale.language.languageCode?.identifier ?? "en"
        let bundle = Bundle.main.path(forResource: language, ofType: "lproj").flatMap(Bundle.init(path:)) ?? .main
        return bundle.localizedString(forKey: key, value: nil, table: nil)
    }
}
```
Check how `SpineStrings` resolves a language-specific bundle and copy that mechanism verbatim instead of the sketch above if it differs (the string catalog is compiled per language).

Catalog entries (manual extraction state), EN / FR:

| key | en | fr |
|---|---|---|
| `home.greeting.morning.title` | Morning | Bonjour |
| `home.greeting.morning.line` | A new day to keep something from. | Une nouvelle journée à garder. |
| `home.greeting.afternoon.title` | Good afternoon | Bon après-midi |
| `home.greeting.afternoon.line` | Halfway through the day. Anything worth keeping? | La journée est à moitié écrite. Quelque chose à garder ? |
| `home.greeting.evening.title` | Good evening | Bonsoir |
| `home.greeting.evening.line` | What stays with you from today? | Qu'est-ce qui te reste de la journée ? |
| `home.greeting.monday-morning.title` | Morning | Bonjour |
| `home.greeting.monday-morning.line` | Hope you'll have a pleasant week! | Belle semaine à toi ! |
| `home.greeting.sunday-evening.title` | Good evening | Bonsoir |
| `home.greeting.sunday-evening.line` | How was your week? | Comment s'est passée ta semaine ? |

The dateline format must match: in EN `Monday 2 November` (weekday wide, day, month wide, no year); in FR `lundi 2 novembre`. If `.formatted` orders the fields differently for `en`, build it from the components (`weekday wide`, then `"\(day) \(month)"`).

- [ ] **Step 4: Run** → pass. **Step 5: Commit** `feat(ios): compute the home greeting from the hour and the weekday`.

### Task 3: `HomeView` root, the tab item and the loader

**Files:**
- Create: `apps/ios/Pebbles/Features/Home/HomeView.swift`, `HomeModel.swift`, `HomeService.swift`
- Modify: `apps/ios/Pebbles/Features/Tabs/PebblesTabView.swift`, `apps/ios/Pebbles/RootView.swift`

- [ ] **Step 1: `HomeService` (the seam)**

```swift
import Foundation
import Supabase

/// Home's server reads, behind a protocol so the Lab and tests use fakes.
/// Nothing here aggregates: every number comes from the RPC (spec D9).
protocol HomeServicing: Sendable {
    func wraps(zone: TimeZone) async throws -> HomeWraps
    func reveal(month: CalendarMonth, zone: TimeZone) async throws -> HomeReveal
    func guides() async throws -> [Guide]
}

struct HomeService: HomeServicing {
    let client: SupabaseClient

    func wraps(zone: TimeZone) async throws -> HomeWraps {
        let data: Data = try await client.rpc("home_wraps", params: ["p_tz": zone.identifier]).execute().data
        return try WrapDecoding.decode(HomeWrapsPayload.self, from: data).model
    }
    func reveal(month: CalendarMonth, zone: TimeZone) async throws -> HomeReveal {
        let data: Data = try await client.rpc("reveal_wrap", params: ["p_month": month.firstDay.iso, "p_tz": zone.identifier]).execute().data
        return try WrapDecoding.decode(HomeRevealPayload.self, from: data).model
    }
    func guides() async throws -> [Guide] {
        try await client.from("guides").select().eq("published", value: true).order("sort_order").execute().value
    }
}
```
Part 1 ships this file with the three signatures and the payload types stubbed in Part 3 and Part 5 (so Part 1 compiles alone, define `HomeWraps`, `HomeReveal` and `Guide` here as empty-but-decodable placeholders only if the plan's later parts have not landed; otherwise see Tasks 7 and 12. Prefer: in Part 1 the protocol declares only what Part 1 uses — nothing — and each later part adds its method. Keep `HomeServicing` with no requirements in Part 1 and a `struct HomeService: HomeServicing { let client: SupabaseClient }`.)

- [ ] **Step 2: `HomeModel`**

```swift
import Foundation
import os

/// The Home tab's state. Static parts (greeting, shortcuts, About header)
/// render at once; the server projections load in one task, each section
/// keeping its last value while it reloads (spec §1.6).
@Observable @MainActor final class HomeModel {
    var name: String = ""
    var firstLoadSettled = false
    @ObservationIgnored var service: (any HomeServicing)?
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "home.model")

    /// Later parts add their sections here (wraps, checklist, guides).
    func load() async {
        defer { firstLoadSettled = true }
        // Part 1: nothing to fetch yet.
    }
}
```

- [ ] **Step 3: `HomeView`**

```swift
import SwiftUI

/// The Home tab root (spec §1): large-title greeting, then the sections the
/// later parts add. Built from system parts in the Spine's type and paper (D2).
struct HomeView: View {
    var onFirstLoad: () -> Void = {}
    @Environment(SupabaseService.self) private var supabase
    @Environment(TabNavigation.self) private var nav
    @State private var model = HomeModel()
    @State private var now = Date()

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.xl) {
                    greeting
                        .id(TabNavigation.scrollTopAnchor)
                    // Part 2: HomeShortcuts · Part 3: HomeWrapsRow · Part 4: HomeChecklist · Part 5: GuidesList
                }
                .padding(.horizontal, Spacing.lg)
                .padding(.top, Spacing.md)
                .padding(.bottom, MonthLayout.pageBottom)
            }
            .onChange(of: nav.scrollToTop[.home, default: 0]) {
                withAnimation { proxy.scrollTo(TabNavigation.scrollTopAnchor, anchor: .top) }
            }
        }
        .pebblesScreen()
        .navigationDestination(for: HomeRoute.self) { route in
            switch route {
            case .guide(let slug): Text(verbatim: slug) // Part 5 replaces this with GuidePage
            }
        }
        .task {
            model.service = HomeService(client: supabase.client)
            model.name = supabase.displayName ?? ""   // read the profile's display_name the way ProfileView does; add a cached accessor on SupabaseService if none exists
            await model.load()
            onFirstLoad()
        }
        .onReceive(Timer.publish(every: 60, on: .main, in: .common).autoconnect()) { now = $0 }
    }

    private var greeting: some View {
        let lines = HomeGreeting.lines(at: now, name: model.name, zone: SpineCalendar.current.timeZone, locale: SpineStrings.appLocale)
        return VStack(alignment: .leading, spacing: 4) {
            Text(verbatim: lines.dateline).spineType(.kicker).foregroundStyle(Color.pebbles.outline)
            Text(verbatim: lines.title).font(.largeTitle.weight(.regular)).foregroundStyle(Color.pebbles.onSurface)
            Text(verbatim: lines.line).spineType(.summary).foregroundStyle(Color.pebbles.onSurfaceVariant)
        }
        .accessibilityElement(children: .combine)
    }
}
```
Check how `ProfileView` reads `display_name` (it decodes a `ProfileRow`); if `SupabaseService` has no cached profile name, add `profileDisplayName()` to `HomeService` (a single-row select on `profiles` for the signed-in user) and load it in `HomeModel.load()`, defaulting to "" on failure with a logged warning.

- [ ] **Step 4: The tab.** In `PebblesTabView.swift`, before Path:
```swift
NavigationStack(path: $nav.home) { HomeView(onFirstLoad: onFirstLoad) }
    .modifier(TabChrome())
    .tabItem { Label("Home", systemImage: "house") }
    .tag(AppTab.home)
```
Rename the parameter `onPathFirstLoad` to `onFirstLoad` and pass it to both Home and Path (whichever root is shown first settles the loader). In `RootView.swift`, `restoreTabs()`: the loader is held only for the restored tab's first load; adjust the `if snapshot.selected != .path` line to `if ![.home, .path].contains(snapshot.selected)`. `nav.reset()` already resets `selected` to the default (`.home`).

- [ ] **Step 5: Build, run on the simulator, screenshot.** `build/home-test.sh --fresh TabNavigationTests` then boot the app on "iPhone 17 home" (`xcrun simctl boot`, `xcodebuild build`, `xcrun simctl install`, `launch`) and confirm the five tabs with Home first and the greeting in both languages (`-AppleLanguages '(fr)'` as a launch argument). Save a screenshot to the scratchpad and look at it.

- [ ] **Step 6: References.** Add `apps/ios/PebblesTests/References/HomeReferences.swift` with `greeting` (paper+night, en+fr) rendering the greeting block alone at 393 pt. Run `References` and apply the baseline gate.

- [ ] **Step 7: Commit** `feat(ios): land the home tab with its greeting`.

### Task 4: Ship Part 1

- [ ] Arkaik: acceptance "The greeting adapts to the hour and the weekday" (ios, development, gherkin, values `sensory-appeal`, `informs`), `covers` → `V-home`.
- [ ] Lab Note via the `lab-note` skill. `gh stack submit --auto --open`; `gh pr edit` title `feat(ios): add the home tab with its greeting`, body `Part 1 of #1255`, labels `feat`, `ui`, `ios`, milestone M64, `rebaseline-ios-screenshots` added **alone** afterwards (memory: add the label alone, approve the held run).

---

# Part 2 — the shortcuts (`feat/1255-ios-home-shortcuts`)

### Task 5: `HomeShortcuts` and the quick moment sheet

**Files:**
- Create: `Features/Home/Shortcuts/HomeShortcuts.swift`, `QuickMomentSheet.swift`
- Modify: `Features/Home/HomeView.swift`
- Test: `PebblesTests/Features/Home/QuickMomentDraftTests.swift`

- [ ] **Step 1: Failing test** for the pure draft rules:
```swift
@Suite("Quick moment draft") struct QuickMomentDraftTests {
    @Test func aNameIsTrimmedAndCapped() {
        var d = QuickMomentDraft(now: Date(timeIntervalSince1970: 0))
        d.setName("  " + String(repeating: "a", count: 60))
        #expect(d.name.count == CaptureFlowModel.nameLimit)
        #expect(d.canSave)
    }
    @Test func anEmptyNameCannotSave() {
        let d = QuickMomentDraft(now: .now)
        #expect(!d.canSave)
    }
    @Test func thePayloadCarriesTheDateAtWholeSeconds() {
        var d = QuickMomentDraft(now: Date(timeIntervalSince1970: 1_700_000_000.7))
        d.setName("Lunch")
        let p = d.payload
        #expect(p.name == "Lunch")
        #expect(p.happenedAt == Date(timeIntervalSince1970: 1_700_000_000))
    }
}
```
- [ ] **Step 2: Implement** `QuickMomentDraft` (in `QuickMomentSheet.swift` or its own file): `happenedAt`, `name`, `setName` (trim + `CaptureFlowModel.nameLimit`), `canSave`, `payload: SaveMomentPayload` (`name`, `happenedAt` truncated to the second; no snaps).

`QuickMomentSheet(onSaved: (UUID, QuickMomentExit) -> Void)` with `enum QuickMomentExit { case open, close }`: a medium detent, a `TextField` focused on appear (`@FocusState`), a `DatePicker` (compact, date and time, prefilled `now`), two buttons stacked: "Save & open" (`.borderedProminent`) and "Save & close" (`.bordered`), both disabled until `canSave`; saving through `MomentsService(client:).saveMoment(payload)`; `achievements.fireCheck()` after a successful save (so `first-moment` ticks); error line in `Color.pebbles.error` on failure, logged. Strings: `home.quick.title` "Quick moment"/"Moment rapide", `home.quick.name` "What happened?"/"Que s'est-il passé ?", `home.quick.saveOpen` "Save & open"/"Enregistrer et ouvrir", `home.quick.saveClose` "Save & close"/"Enregistrer et fermer".

`HomeShortcuts(onCamera:onQuick:onPebble:)`: three equal `Button`s in an `HStack` (`.frame(maxWidth: .infinity, minHeight: 44)`, `.buttonStyle(.bordered)`, continuous 14 pt corners), each a `VStack` of an SF Symbol (`camera`, `clock`, `plus.circle`) in `Color.pebbles.onSurfaceVariant` and a label in `.pebblesFont(.callout)`: `home.shortcut.camera` "Camera"/"Appareil photo", `home.shortcut.quick` "Quick moment"/"Moment rapide", `home.shortcut.pebble` "New pebble"/"Nouveau galet". Order fixed (D3).

`HomeView` presents: `.sheet(isPresented: $showsQuick) { QuickMomentSheet(onSaved:) }`; on `.open`, set `completing = CompleteTarget(momentId:momentName:)` and `.fullScreenCover(item: $completing) { CompleteFlowView(momentId:momentName:onCompleted: { _ in revision.bump(from: .home) }) }` (add the `.home` case to `MomentsRevision`'s source enum if it has one; read it). On `.close`, `revision.bump(from: .home)` so the Path reloads.

- [ ] **Step 3: Run tests, build, click through on the simulator** (quick moment → Save & open lands in Complete; Save & close returns to Home and the Path shows the moment). **Commit** `feat(ios): add the home shortcuts and the quick moment sheet`.

### Task 6: New pebble chaining and the camera moment

**Files:**
- Modify: `Features/Capture/CaptureFlowView.swift` (add `var chainsComplete = false`)
- Create: `Features/Home/Shortcuts/CameraMoment.swift` (`CameraPicker` + `CameraMomentCoordinator`), `MomentToast.swift`
- Modify: `HomeView.swift`

- [ ] **Step 1: Chaining.** In `CaptureFlowView`, read the step after save where "Make it a pebble now" is offered. Add `var chainsComplete = false`; when true, `save(thenComplete:)` is always called with `true` and the choice UI is skipped. Home presents `CaptureFlowView(onMomentChanged: { revision.bump(from: .home) }, chainsComplete: true)` in a `.fullScreenCover`. `PathView`'s call site is unchanged (default false).

- [ ] **Step 2: Camera.** `CameraPicker: UIViewControllerRepresentable` over `UIImagePickerController` with `sourceType = .camera`, `cameraCaptureMode = .photo`; `onCapture: (UIImage, Date) -> Void` with the date read at the delegate callback (the shutter time, D12), `onCancel`. Add `NSCameraUsageDescription` to `Info.plist` through `project.yml` if absent (check `grep -n Camera apps/ios/project.yml apps/ios/Pebbles/Resources/Info.plist`); the string: "Pebbles takes the photo for the moment you are recording." (EN) and its FR in `InfoPlist.xcstrings` if the project localises Info.plist; otherwise EN only, noted in the PR. Guard with `UIImagePickerController.isSourceTypeAvailable(.camera)`: on the simulator the shortcut shows an alert "No camera on this device" rather than crashing.

`CameraMomentCoordinator` (`@Observable @MainActor`): given the image and the time, it uses `SnapUploadCoordinator` exactly as `CaptureFlowView` does (read how a picked photo becomes a `FormSnap`: the `RecordPhotoStep` → coordinator path), waits for the upload, then `MomentsService.saveMoment(SaveMomentPayload(name: nil, happenedAt: shutter, snaps: …))`, then `achievements.fireCheck()`, then publishes `landed: LandedMoment?` (`momentId`, `thumbnail: UIImage`, `at: Date`). On upload failure it keeps the moment unsaved and surfaces the coordinator's existing retry (same copy as Capture); log every path.

`MomentToast(landed:onName:)`: a capsule above the tab bar (`.safeAreaInset(.bottom)`), `.ultraThinMaterial` over the Spine paper, the thumbnail (44 pt, 8 pt continuous corners), the time in `.pebblesFont(.meta)` (`Date.FormatStyle` hour and minute, app locale), one button `home.toast.name` "Name it"/"Nommer"; auto-dismisses after 6 s (`Task.sleep`), cancelled by the tap. "Name it" opens `EditMomentSheet(detail:onSaved:)` after fetching `MomentsService.moment(id:)`.

- [ ] **Step 3: Device check.** The camera needs a real device: build to the iPhone 15 with the device recipe (memory note: ids and the three commands), take a photo, see the toast, name it, see it on the Path. On the simulator, verify the "no camera" alert and that nothing crashes.

- [ ] **Step 4: Tests.** `CameraMomentCoordinatorTests` with a fake `MomentsService`-like seam is overkill; test the pure parts: the payload built from a shutter date (whole seconds, `name == nil`), and `MomentToast`'s auto-dismiss deadline helper if you extract one. **Commit** `feat(ios): record a moment from the camera and chain new pebbles`.

### Task 7: Ship Part 2

- [ ] References: `HomeReferences.shortcuts` (the row) and `toast`. Baseline gate.
- [ ] Arkaik acceptance "A shortcut records in one tap" (`saves-time`, `reduces-effort`). Lab Note. `gh stack add feat/1255-ios-home-shortcuts` was done at the start of Part 2; `gh stack submit --auto --open`, PR `feat(ios): add the home shortcuts`, `Part 2 of #1255`.

---

# Part 3 — the monthly wraps (`feat/1255-ios-home-wraps`)

### Task 8: Payloads and models

**Files:**
- Create: `Features/Home/Wraps/HomeWrapsPayload.swift`
- Test: `PebblesTests/Features/Home/HomePayloads.swift` (fixture loader), `HomeWrapsDecodingTests.swift`

- [ ] **Step 1: Fixture loader** mirroring `Wrap/WrapPayloads.swift` but pointing at `packages/supabase/scripts/oracles/home/payloads`.

- [ ] **Step 2: Failing tests**
```swift
@Suite("Home wraps decoding") struct HomeWrapsDecodingTests {
    @Test func theHarnessPayloadDecodesVerbatim() throws {
        let wraps = try HomePayloads.wraps()          // home-wraps.json
        let sept = try #require(wraps.months.first { $0.month == CalendarMonth(year: 2026, month: 9) })
        #expect(sept.count == 4)
        #expect(sept.revealedAt == nil)
        #expect(sept.cover.days.map(\.day) == [2, 11, 19, 27])
        #expect(sept.cover.soulsInOrder.count == 1)
        #expect(sept.cover.inks.count == 1 && sept.cover.papers.count == 2)
        #expect(wraps.months == wraps.months.sorted { $0.month > $1.month })
    }
    @Test func aRevealedMonthParsesItsWholeSecondTimestamp() throws {
        let json = #"{"months":[{"month":"2026-08-01","count":1,"revealed_at":"2026-10-26T22:24:21Z","cover":{"days":[],"souls_in_order":[],"inks":[],"papers":[]}}]}"#
        let wraps = try WrapDecoding.decode(HomeWrapsPayload.self, from: Data(json.utf8)).model
        #expect(wraps.months[0].revealedAt == ISO8601Flexible.parse("2026-10-26T22:24:21Z"))
    }
    @Test func aMonthBecomesAMonthWrapForTheGarland() throws {
        let sept = try #require(HomePayloads.wraps().months.first)
        let wrap = sept.asMonthWrap
        #expect(wrap.daysInMonth == 30 && wrap.cover?.days.count == 4 && wrap.sections.isEmpty)
    }
    @Test func theRevealPayloadDecodes() throws {
        let json = #"{"month":"2026-09-01","revealed_at":null}"#
        let r = try WrapDecoding.decode(HomeRevealPayload.self, from: Data(json.utf8)).model
        #expect(r.month == CalendarMonth(year: 2026, month: 9) && r.revealedAt == nil)
    }
}
```

- [ ] **Step 3: Implement** `HomeWrapsPayload { months: [HomeMonthPayload] }`, `HomeMonthPayload { month: CalendarDay; count: Int; revealedAt: String?; cover: CoverPayload }` (reuse `CoverPayload` from `MonthWrapPayload.swift`), `HomeRevealPayload { month: CalendarDay; revealedAt: String? }`, with tolerant decoding (`decode(.key, or:)`) and the `.model` extensions building `HomeWraps { months: [HomeMonth] }`, `HomeMonth: Hashable, Identifiable { month: CalendarMonth; count: Int; revealedAt: Date?; cover: MonthCover; var isFolded: Bool { revealedAt == nil } ; var asMonthWrap: MonthWrap }` (`asMonthWrap` fills `daysInMonth` and `firstWeekday` from the calendar, `pebbles: count`, `people: cover.soulsInOrder.count`, `sections: []`, every section nil, `cover`), `HomeReveal { month; revealedAt: Date? }`. Timestamps parse through `ISO8601Flexible.parse` (tolerant, cross-surface rule). Add `wraps(zone:)` and `reveal(month:zone:)` to `HomeServicing`/`HomeService` (Task 3's shape).

- [ ] **Step 4: Run** → pass. **Commit** `feat(ios): decode the home wraps and the reveal`.

### Task 9: `HomeWrapCard`, `HomeWrapsRow`, `HomeWrapSheet`

**Files:**
- Create: `Features/Home/Wraps/HomeWrapCard.swift`, `HomeWrapsRow.swift`, `HomeWrapSheet.swift`, `HomeLookups.swift`
- Modify: `HomeModel.swift`, `HomeView.swift`
- Test: `HomeModelTests.swift` (with `FakeHomeService`), `HomeReferences` (cards row, folded card, sheet closed state)

- [ ] **Step 1: Lookups.** `HomeLookups.make(palettes: EmotionPaletteService, refs: ReferenceDataService) -> WrapLookups`: `family` from `EmotionFamilies.of(palettes.byEmotionId.values)`, `domain` from `refs.domains`, `emotion`/`palette` from `palettes.byEmotionId`, `soulName`/`soulGlyph` from `refs.souls`; `moment` stays nil (D-plan-2). Read `PathView.lookups` and copy its id normalisation (lowercased UUID strings).

- [ ] **Step 2: Card.** `HomeWrapCard(month: HomeMonth, lookups: WrapLookups)`: a 128 pt square `SurfaceTile`-style card (`Color.pebbles.surfaceContainer`, 16 pt continuous corners, hairline `Color.pebbles.cardRule`); revealed: `Garland(wrap: month.asMonthWrap, cover: month.cover, lookups: lookups) { Text(verbatim: "\(month.count)").spineType(.dayNumeral) }` scaled with `.scaleEffect(92/262).frame(width: 92, height: 92)` and the month name under it in `.spineType(.monthName)` with the year in `.spineType(.kicker)`; folded: `home.wrap.folded` "folded"/"plié" in small caps (`.spineType(.sectionLabel)`), a hairline rule where the wheel would be, a turned corner (a `Path` triangle at the top-right filled with `Color.pebbles.surfaceContainerHighest`), the month name. `accessibilityLabel`: "September 2026, 4 pebbles, folded" (copy from `WrapCopy` month name helpers).

`HomeWrapsRow(months: [HomeMonth], lookups:, onOpen: (HomeMonth) -> Void, nextFold: Date?)`: section header `home.wraps.title` "Monthly wraps"/"Bilans du mois" (`SpineSectionHeader` if it takes a plain title, else `.spineType(.sectionLabel)`) with the count on the right; a horizontal `ScrollView` of cards with `Spacing.sm` gaps; when `months.isEmpty`, one dashed card (`strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [4, 4]))`) with an empty ring and `home.wraps.empty` "%@ folds into your first wrap on the %@."/"%@ se pliera en ton premier bilan le %@." where the arguments are the live month name and its last day (formatted with the app locale, day only).

- [ ] **Step 3: Sheet.** `HomeWrapSheet(month: HomeMonth, lookups:, service:, onRevealed: (HomeReveal) -> Void, onWeek: (CalendarDay) -> Void)`: `.presentationDetents([.large])`, drag indicator, a `NavigationStack` with the month name as the inline title and a Done button. Folded: the count line (`WrapCopy.line(pebbles:people:live:hasMoments:locale:)`), one prominent `home.wrap.unfold` "Unfold"/"Déplier" button, dashed outlines of three cards below (decorative, `accessibilityHidden`); on tap `service.reveal(month:zone:)`, then the deck loads in place. Revealed: fetch the full `MonthWrap` through `PathWrapService(client:disk: .standard, owner:)`'s `cachedMonthWrap` then `monthWrap`, then `MonthPage(wrap:lookups:isFocused: true, returnFocus: .constant(nil), onWeek:, onOpen: { _ in }, intro: IntroContext(ledger: model.introLedger, haptics: IntroHaptics.shared))` (D-plan-1, D-plan-2). `onWeek` dismisses and `nav.go(.path)`.

- [ ] **Step 4: Model.** `HomeModel` gains `wraps: HomeWraps?`, `wrapsError: String?`, `let introLedger = IntroLedger()`, `loadWraps()` inside `load()` (stale-while-revalidate: keep the last value, set the error line on failure), `markRevealed(_ reveal: HomeReveal)` updating the month in place. `FakeHomeService` in tests; `HomeModelTests`: a failed wraps load keeps the previous months and sets the error; a reveal flips `isFolded`.

- [ ] **Step 5: Wire** the row into `HomeView` under the shortcuts, with the quiet italic error line `home.wraps.error` "Couldn't load your months."/"Impossible de charger tes mois." and a retry link. The `home_wraps` zone is `SpineCalendar.current.timeZone`.

- [ ] **Step 6: Simulator check with the signed-in maintainer account** (the Pixel/iPhone accounts have July–October data): the row shows the finished months, October folded; Unfold works once; the deck plays. **References**: `cards` (two revealed + one folded from the fixture), `empty` (the promise card), `sheetFolded`. Baseline gate. **Commit** `feat(ios): show the monthly wraps as folded cards and open their deck`.

### Task 10: Ship Part 3

- [ ] File the follow-up issue "iOS Home wrap sheet: opening a milestone from Home" (feat, ui, ios, M64) and reference it by number in the PR body (D-plan-2).
- [ ] Arkaik acceptance "A folded month unfolds once" (`rewards-me`, `nostalgia`). Lab Note. Submit; PR `feat(ios): fold the monthly wraps on home until the user unfolds them`, `Part 3 of #1255`.

---

# Part 4 — your first five and the blue pebble (`feat/1255-ios-home-checklist`)

### Task 11: The three families' copy and `claimBluePebble`

**Files:**
- Modify: `Features/Profile/Models/Achievement+Localized.swift`, `Resources/Localizable.xcstrings`, `Services/AchievementsService.swift`
- Test: extend `AchievementsDecodingTests.swift` / `AchievementMomentTests.swift`

- [ ] **Step 1: Copy.** Arms for `first_moment` (`"First moment"` / `"Record your first moment."`, symbol `clock`), `first_pebble` (`"First pebble"` / `"Complete your first pebble."`, symbol `circle.hexagongrid`), `first_five` (`"The blue pebble"` / `"Claim it once your first five are done."`, symbol `drop.fill`), plus `achievement.group.first_moment` "First moment", `achievement.group.first_pebble` "First pebble", `achievement.group.first_five` "The blue pebble". FR: "Premier moment" / "Enregistre ton premier moment.", "Premier galet" / "Complète ton premier galet.", "Le galet bleu" / "Réclame-le quand tes cinq premières fois sont faites." A test asserts the three titles in EN and FR through the catalog (`LocalizationTests` style).

- [ ] **Step 2: Claim.** In `AchievementsService`:
```swift
/// Claims the blue pebble (#1254, spec D6). An empty result means "not
/// granted" (already claimed, or retired), never success; the caller keeps
/// its row until a non-empty result arrives.
func claimBluePebble() async throws -> [AchievementCheckResult] {
    let results: [AchievementCheckResult] = try await client.rpc("claim_blue_pebble").execute().value
    if !results.isEmpty { presentUnlockMoment(for: results) }
    return results
}
```
(`presentUnlockMoment` stays private; this method is in the same type.) A `not_ready` error surfaces as a thrown error the caller logs and shows.

- [ ] **Step 3: Commit** `feat(ios): name the first-moment, first-pebble and blue-pebble achievements and claim the blue pebble`.

### Task 12: `HomeChecklist` model and views

**Files:**
- Create: `Features/Home/Checklist/HomeChecklist.swift` (pure), `HomeChecklistView.swift`, `ChecklistTaskSheet.swift`, `ChecklistIllustrations.swift`
- Modify: `HomeModel.swift`, `HomeView.swift`, `SoulsListView.swift`, `CollectionsListView.swift`, `ProfileView.swift` (intents)
- Test: `HomeChecklistTests.swift`, `HomeReferences` (checklist 0/5, 3/5, claim row), `ChecklistTaskSheet` references

- [ ] **Step 1: Failing tests** for the pure model:
```swift
@Suite("Home checklist") struct HomeChecklistTests {
    static func catalog(_ slugs: [String]) -> [AchievementRecord] { /* records with family per slug, sortOrder by index */ }
    @Test func rowsFollowTheFixedOrderAndTickFromUnlocks() {
        let c = HomeChecklist.make(catalog: Self.catalog(HomeChecklist.slugs + ["blue-pebble"]),
                                   unlocked: ["first-moment", "first-soul"])
        #expect(c.rows.map(\.task) == [.moment, .pebble, .collection, .soul, .glyph])
        #expect(c.rows.map(\.isDone) == [true, false, false, true, false])
        #expect(c.doneCount == 2 && !c.canClaim && c.state == .inProgress)
    }
    @Test func fiveDoneOffersTheClaim() {
        let c = HomeChecklist.make(catalog: Self.catalog(HomeChecklist.slugs + ["blue-pebble"]), unlocked: Set(HomeChecklist.slugs))
        #expect(c.canClaim && c.state == .claimable)
    }
    @Test func aClaimedBluePebbleRemovesTheSection() {
        let c = HomeChecklist.make(catalog: Self.catalog(HomeChecklist.slugs + ["blue-pebble"]), unlocked: Set(HomeChecklist.slugs + ["blue-pebble"]))
        #expect(c.state == .done)
    }
    @Test func aMissingCatalogRowHidesTheSectionRatherThanLying() {
        let c = HomeChecklist.make(catalog: [], unlocked: [])
        #expect(c.state == .unavailable)
    }
}
```
- [ ] **Step 2: Implement** `HomeChecklist` with `enum Task: CaseIterable { moment, pebble, collection, soul, glyph; var slug: String }` (`first-moment`, `first-pebble`, `first-collection`, `first-soul`, `first-glyph`), `static let slugs`, `static let blueSlug = "blue-pebble"`, `struct Row { task; isDone; unlockedAt: Date? }`, `enum State { unavailable, inProgress, claimable, done }`, `make(catalog:unlocked:)` keyed by slug (an inactive or missing row → `.unavailable`).

- [ ] **Step 3: Views.** `HomeChecklistView(checklist:onTask:onClaim:)`: section header `home.firstFive.title` "Your first five"/"Tes cinq premières fois" with five dots and "n of 5" (`home.firstFive.count` "%lld of 5"/"%lld sur 5"); an inset grouped look (`pebblesList` row chrome, `pebblesRowPosition`) with one row per task: a circle mark (`circle.dashed` or `checkmark.circle.fill`), the task title and an italic second line, a chevron; done rows in `Color.pebbles.outline` with no chevron and no action. Titles/lines (EN/FR):

| task | title | line |
|---|---|---|
| moment | Record your first moment / Enregistre ton premier moment | A time and a name. Nothing more. / Une heure et un nom. Rien de plus. |
| pebble | Complete your first pebble / Complète ton premier galet | Give a moment its weight and colour. / Donne à un moment son poids et sa couleur. |
| collection | Start your first collection / Commence ta première collection | Gather pebbles that belong together. / Rassemble des galets qui vont ensemble. |
| soul | Add your first soul / Ajoute ta première âme | Someone who shares your moments. / Quelqu'un qui partage tes moments. |
| glyph | Carve your first glyph / Grave ton premier glyphe | A mark of your own, for a soul or a collection. / Une marque à toi, pour une âme ou une collection. |

When `state == .claimable`, a sixth filled row `home.firstFive.claim` "Claim the blue pebble"/"Réclamer le galet bleu" with a drawn blue pebble mark (`StoneSeal` with the `peace` family palette, size `.milestone`, or a `drop.fill` tinted `Color.pebbles.primary`). When `.done` or `.unavailable`, the section is not drawn.

`ChecklistTaskSheet(task:onGo:)`: medium detent; illustration (Task 12 Step 4), two sentences (`home.task.<task>.body`), one prominent button (`home.task.<task>.cta`: "Record a moment", "Complete a pebble", "Start a collection", "Add a soul", "Carve a glyph", and FR). The button closes the sheet and runs `onGo`.

Illustrations (`ChecklistIllustrations`), each a small real-component composition at 120 pt tall: moment = a `WeekContents`-style row (day, `StoneSeal(isBlank: true)`, a name, a time) using the fixture's row components; pebble = the same row with a lit `StoneSeal` beside it and an arrow; collection = three `StoneSeal`s and a `CollectionRow`-like line (make `CollectionRow` internal, Part of #1256 also needs it: do the one-line visibility change here); soul = three `SoulMark`s over a row carrying one; glyph = a dashed ring with one `GlyphStroke` path from `SpineFixtureWeek.soulGlyphs`.

- [ ] **Step 4: Navigation.** `onGo` per task: moment → `showsQuick = true`; pebble → the Complete flow on the latest moment without a pebble (fetch through `MomentsService.pathMoments()` filtered to `pebble == nil`, newest first; none → the Capture flow with `chainsComplete: true`); collection → `nav.go(.collections, intent: .createCollection)`; soul → `nav.go(.people, intent: .createSoul)`; glyph → `nav.go(.you, intent: .carveGlyph)`. Roots consume: `SoulsListView` and `CollectionsListView` add `.onAppear { if nav.takeIntent(for: .people) == .createSoul { isPresentingCreate = true } }` (and the collections twin); `ProfileView` pushes `.glyphs` and `GlyphsListView` consumes `.carveGlyph` into its carve cover (read how the carve is presented there and add the same one-shot).

- [ ] **Step 5: Model.** `HomeModel` gains `checklist: HomeChecklist?`, `loadChecklist()` = `achievements.checkIgnoringFailure()` then `loadCatalog()`+`loadUnlocks()` → `HomeChecklist.make`; `claim()` calls `achievements.claimBluePebble()`, and on a non-empty result reloads the checklist (now `.done`, section gone). `HomeView` calls `model.loadChecklist()` on appear and after each shortcut completes (`onMomentChanged`, quick save, camera landed).

- [ ] **Step 6: Simulator** with a fresh test account (sign up in the app): tick the five through the real flows, claim, watch the unlock moment, the section leaves; relaunch, still gone. **References**: `checklistEmpty`, `checklistThree`, `checklistClaim`, `taskSoul`. **Commit** `feat(ios): walk a beginner through the first five and the blue pebble claim`.

### Task 13: Ship Part 4

- [ ] Arkaik acceptance "The first five end in the blue pebble claim" (`motivation`, `rewards-me`). Lab Note. Submit; PR `feat(ios): add the first five checklist to home`, `Part 4 of #1255`; mention #1257 and #1258 by number (the other surfaces' copy).

---

# Part 5 — About Pebbles (`feat/1255-ios-home-guides`)

### Task 14: `Guide`, `GuidesService`, `MarkdownBlocks`

**Files:**
- Create: `Features/Home/Guides/Guide.swift`, `GuidesService.swift` (or the methods on `HomeService`), `Components/MarkdownBlocks.swift`
- Modify: `Features/Lab/Views/AnnouncementDetailView.swift` (use the lifted helper; a pure move)
- Test: `GuideDecodingTests.swift`, `MarkdownBlocksTests.swift`

- [ ] **Step 1: Failing tests**: `Guide` decodes a row with `body_md_fr: null` and `illustration: null`; `guide.title(for: fr)` falls back to EN when `title_fr` is null; `MarkdownBlocks.parse("# A\n\nText\n\n## B")` gives `[.heading(1,"A"), .paragraph("Text"), .heading(2,"B")]`.
- [ ] **Step 2: Implement** `Guide: Decodable, Identifiable, Hashable` (`id`, `slug`, `sortOrder`, `illustration: GuideIllustration?` with `enum GuideIllustration: String, Decodable { pebbles, stones, emotions, valence, domains, collections, souls, glyphs, karma }` decoded tolerantly to nil on an unknown value, `titleEn/Fr`, `summaryEn/Fr`, `bodyMdEn/Fr`, with `title(for:)`, `summary(for:)`, `body(for:)` using `locale.prefersFrench ? (fr ?? en) : en` like `Log`). Lift `Block`, `blocks(from:)`, `attributed(_:)` out of `AnnouncementDetailView` into `enum MarkdownBlocks` + a `MarkdownBlocksView(markdown:)` that renders them with the same fonts; `AnnouncementDetailView` calls it (behaviour unchanged, its reference stays byte-identical). `guides()` on `HomeServicing`.
- [ ] **Step 3: Commit** `feat(ios): decode the guides and share the lab's markdown blocks`.

### Task 15: `GuidesList`, `GuidePage`, `GuideIllustrationView`

**Files:**
- Create: `Features/Home/Guides/GuidesList.swift`, `GuidePage.swift`, `GuideIllustrationView.swift`
- Modify: `HomeModel.swift`, `HomeView.swift`

- [ ] **Step 1:** `GuidesList(guides:)`: section header `home.about.title` "About Pebbles" (`Text(verbatim:)` for the brand word inside a localized sentence: EN "About Pebbles", FR "À propos de Pebbles"), inset rows (`NavigationLink(value: HomeRoute.guide(slug))`) with a drawn mark per illustration (24 pt: `GuideIllustrationView(kind, size: .mark)`), the title in `.pebblesFont(.headline)`, the summary in `.spineType(.summary)` italic, a chevron. `GuidePage(guide:)`: pushed, large title, the summary italic, `GuideIllustrationView(kind, size: .page)` (120 pt tall, optional), then `MarkdownBlocksView(markdown: guide.body(for: locale))`; `.pebblesScreen()`. `GuideIllustrationView` draws each enum with real components: stones = a 3 × 3 grid of `StoneSeal`s (small/medium/large × low/neutral/high); emotions = seven `StoneSeal`s in the families' palettes; valence = three seals (lava, river, gem); domains = three `DotMark`s with domain names; collections = the three mode marks (`CollectionRow`'s pile / line / sockets); souls = three `SoulMark`s; glyphs = a dashed ring with one stroke; karma = a `StoneSeal` with a small `+1`; pebbles = a contents row becoming a seal. Palettes come from `EmotionPaletteService` by family through `HomeLookups`.
- [ ] **Step 2:** `HomeModel.loadGuides()` (keeps the last list; on error the list stays and nothing is drawn under the header, spec §1.6). `HomeView.navigationDestination` resolves `.guide(slug)` from `model.guides` (`RouteResolution`: loading → `ProgressView`, missing → `ContentUnavailableView`).
- [ ] **Step 3: Simulator**: the nine topics, push, back, FR. **References**: `guidesList`, `guidePageStones`. **Commit** `feat(ios): list the guides on home and open them as pages`.

### Task 16: Ship Part 5

- [ ] Arkaik acceptance "About topics open as pages" (`informs`). Lab Note. Submit; PR `feat(ios): read about pebbles from home`, `Part 5 of #1255`.

---

# Part 6 — the Spine Lab's Home tab and the references (`feat/1255-ios-home-lab`)

### Task 17: `HomeFixture`, the Lab tab, the remaining references

**Files:**
- Create: `Features/Path/Spine/Debug/HomeFixture.swift` (`#if DEBUG`), `Features/Home/HomeContent.swift` if the page body needs to be extracted so the Lab can draw it with a fake service
- Modify: `SpineLabView.swift` (a `home` section with a segmented state picker: new user · returning · claim · folded sheet), `SettingsSheet.swift` is unchanged (the Spine Lab already has an entry)
- Test: `HomeReferences` gains `pageNewUser`, `pageReturning` (tall, `width: 393`, under 8,192 px), `pageClaim`

- [ ] **Step 1:** `HomeFixture`: embedded `home-wraps.json` (as `SpineFixtureMonth` embeds the month), a catalog + unlocks for the four states, three guides, `lookups` from `SpineFixtureMonth`. `FakeHomeService` moves from tests to `#if DEBUG` app code if the Lab needs it (or the Lab builds `HomeModel` state directly).
- [ ] **Step 2:** The Lab section draws `HomeContent(model:lookups:…)` for each state with verbatim labels. References render the same three states. Baseline gate; the bot records them on CI.
- [ ] **Step 3: Commit** `feat(ios): add the home states to the spine lab and pin them as references`. Submit; PR `feat(ios): pin the home tab's states`, `Resolves #1255`, labels `feat`, `ios`, `ui`, `no-lab-note`.

---

## Self-review against the spec

- D1 fifth tab first, default selection, snapshot tolerance → Task 1; D2 system parts in Spine type → Tasks 3, 9, 12, 15; D3 fixed shortcuts → Task 5; D4 the promise card → Task 9; D5 folded cards, Unfold once, per-account → Tasks 8–9; D6 achievements and the claim → Tasks 11–12; D7 guides as pushed pages with the Lab's markdown → Tasks 14–15; D8 guides table read directly → Task 14; D9 no client aggregation → every model is a projection; D10 is server-side; D11 is stack 3; D12 camera at shutter time, toast → Task 6; D13 quick moment → Task 5; D14 chaining → Task 6.
- §1.1 greeting catalog → Task 2; §1.2 shortcuts → Task 5; §1.3 wraps and sheet → Task 9 (with D-plan-1/2); §1.4 checklist, task sheets, cross-tab navigation → Task 12 (D-plan-3); §1.5 About → Task 15; §1.6 loading and errors → Tasks 3, 9, 15.
- §6 structure → the file list above; §8 tests → greeting bands EN/FR, checklist rows from fixtures, payload decoding verbatim, references per state; the Lab tab → Task 17.
- Spec §7 names six parts; this plan's six branches match.

## As built

- **Local reference baseline (2026-10-27, iPhone 17 home, iOS 26.3, macOS 26.5.1):** 27 of 61 fail on the untouched tree, the known local set (memory note "iOS references fail locally"): `friday`, `insights`, `insightsAx2`, `largestType`, `midPress`, `midTurn`, `monday`, `mondayAx2`, `nineStones`, `noPhoto`, `photoRecto`, `photoVerso`, `picnic`, `print`, `publicCard`, `ratios`, `saturday`, `seams`, `seamsAx2`, `sheetPage`, `states`, `stressVerso`, `sunday`, `tiles`, `tilesAx2`, `tuesday`, `wednesday`. Their PNGs are in `apps/ios/build/baseline/reference-diffs/`; every later run must fail exactly these and render them byte-identical.
- **Task 1 as built:** `Snapshot()`'s default `selected` is `.home` (so `reset()` lands on Home) while the decode fallback for a missing `selected` stays `.path`; the three existing tests that assumed Path first were updated. No `switch` outside `Features/Tabs` needed a `.home` arm.
