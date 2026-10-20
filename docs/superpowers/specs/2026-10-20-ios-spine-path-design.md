# iOS Path: the Spine — design

- **Issue:** [#1130 — The iOS Path becomes the Spine](https://github.com/alexisbohns/pbbls/issues/1130) (ios, ui; milestone M64 · New Path)
- **Date:** 2026-10-20
- **Scope:** `apps/ios` only: the Path tab's week view, its week roll, the margin stone's press, strings EN/FR. No schema change, no RPC change.
- **Size:** Large. Stack D, seven parts (D1, D2a, D2b, D3, D4, D5a, D5b), mirroring Android's stack #1125 minus its migration (Part 1, #1123, is already live).
- **Depends on:** stack A (M3 roles as `Color.pebbles.*`, Spine brand, paper on `surface`, cool Night), stack B (`SpineType` rank table on variable Ysabeau with Dynamic Type; B2's `ImageRenderer` reference harness; B1 takes #1101), stack C (the Path is a tab of a `TabView` with its own `NavigationStack`).
- **Feeds:** stack E (`2026-10-20-ios-pebble-card-design.md`: the held card, an overlay above the `TabView` that flies from the slot). This spec gives E `CardFrameRegistry`, `CardFace` and `SpineOpen` in the shapes of E's §Registry; until E lands a press opens today's `PebbleDetailSheet`.
- **Lineage:**
  - Contract: `2026-10-11-web-path-spine-design.md` (#1052) §The rule set (lines 130–277) and §Accessibility. "The rule set" below means that section.
  - Sibling: `2026-10-18-android-spine-path-design.md` (#1122) and the Lessons learned of `docs/superpowers/plans/2026-10-18-android-spine-path.md`. Behaviour reference: `apps/android/.../features/path/spine/`. It is a reference, not code to transliterate.
  - The lit stone on iOS: #975 and `2026-09-27-ios-stone-material-lab-design.md`.

## Summary

Each page of the iOS Path stops being a list of rows. A week opens like a gathering: a title in words, the persons of the week, each with a seven-dot strip of the days they appear on, and a contents list. Below the opening, one line runs down the left margin over raked paper. Each pebble's existing lit stone (`PebbleStoneView`) sits on that line. A pebble with a photo hangs a Deck card off its stone, at the photo's own ratio. A pebble without one is set as type. A bare moment shows a blank stone. Each day starts at a hairline seam with its date in the margin. Pressing a stone dips it, ripples the ground and glints. The press then asks to open the pebble, on the verso for a stone press and on the recto for a card press.

## Goal

- Parity with Android's Spine (`SpineWeekScreenshots`, `SpineFeedScreenshots`) for the fixture week, Monday 5 to Sunday 11 October 2026, in Paper and Night, EN and FR.
- The logic ports pass the web Vitest vectors verbatim under Swift Testing.
- The three Arkaik acceptances move for iOS.

## Non-goals

- A new stone renderer. The tilt light on the feed and the sweep on appearance are out too: the feed stays at rest light, as today.
- The spread (wide ≥ 900 pt; iPhone only), the page grain, a dock, stored snap dimensions, several photos per card.
- The held card itself and its flight (stack E). Colour roles (A), the type ranks and the reference harness (B), and the tab shell, the `+` and the bottom bar (C).
- Any change to Capture, Complete, Edit, the delete flow, `PebbleDetailSheet`, or the soul and collection lists (`Components/PebbleRow.swift` stays).
- Swift 6 strict concurrency. iOS 17 stays the floor, with no `#available` guard.

## Decisions

**D1. The margin stone is the existing `PebbleStoneView(valence:glyph:palette:followsTilt:)`, or `.blank()` for a bare moment, at rest light.** It renders at the rule set's phone widths, 34 / 44 / 54 pt. Its outline aspect gives the heights, 27.2 / 44 / 64.4. The rings, halo, ripple and glint are new SwiftUI layers over it. The stone takes one additive parameter, `seed: UInt32?`, defaulting to today's per-valence seed. The Spine passes `StableHash.seed(pebble.id)`, so each pebble's crust varies as it does on web and Android. Other callers are unchanged.
*Considered and rejected:* porting the web SVG construction (Android D1). iOS has the stone, and #1130 says no new renderer. Snapshotting stones to bitmaps up front: measure first (Risks).

**D2. Souls come from `path_moments`.** `MomentSummary.PebbleSummary` gains `souls: [PathSoul]` (`id`, `name`, `glyph_id`), decoded with `decodeIfPresent` and defaulting to `[]`. Glyph strokes come from `ReferenceDataService.souls`, which is already loaded app-wide, so there is no new fetch. A soul missing there draws the dashed mark.
*Considered and rejected:* `v_moments_full` per pebble (N reads for a timeline); a dedicated souls fetch in the Path (the reference cache already holds them).

**D3. The photo ratio is probed on device.** `SnapAspectCache` signs the thumb through `SnapURLCache`, downloads it and reads `kCGImagePropertyPixelWidth/Height` with `CGImageSourceCopyPropertiesAtIndex`, which does not decode. It caches by storage path for the session, times out after 4 s with a `Logger.warning`, and does not retry within the session. The card starts square, `CARD_PENDING_RATIO`, and eases to the ratio over 240 ms ease-out, snapping under Reduce Motion. The model prefetches the ratios of the focused week and its pager neighbours. Neither `path_moments` nor `MomentDetail.snaps` carries dimensions.
*Considered and rejected:* `BannerAspect.nearest` buckets, which crop to three shapes and break `AC-a-photo-keeps-its-own-shape`; adding stored dimensions now, which is a separate backend issue for every client.

**D4. Each week page scrolls in a `ScrollView` holding a `LazyVStack(spacing: 0)`, not a `List`.**
- `List` brings cell clipping, separators, insets and per-row backgrounds. The stone overhangs the top of its row by up to 13.6 pt and must stay pressable, and the ground must sit behind the rows, not in them.
- SwiftUI does not clip a `LazyVStack` child, so hit-testing follows each leaf's `contentShape`. A later row draws above an earlier one, so an overhanging stone is on top.
- No view in a day column may take `.clipped()` or `.clipShape`. The card clips its own photo only.
- B2's harness cannot render a `ScrollView`, so every row is a standalone view: `SpineWeekRows` emits the rows, and the scroll host (`SpineWeekView`) and the reference stack (`SpineWeekStack`, a plain `VStack`) both lay them out.
*Considered and rejected:* `List` with `.listRowInsets(.init())` and a clear background (it still clips cells and fights the overhang); a non-lazy `VStack` (20 stone layers per week up front).

**D5. The week pager is a horizontal `ScrollView` with a `LazyHStack`, `.scrollTargetLayout()`, `.scrollTargetBehavior(.paging)` and `.scrollPosition(id:)`.** All of these are iOS 17 APIs.
- It is one SwiftUI hierarchy, so global frames for stack E are plain SwiftUI geometry.
- Only the visible page and its neighbour during a swipe are realised, and a settled pager holds one week of stones.
- It replaces today's `TabView(.page)`, whose `UIPageViewController` hosts each page in its own controller and keeps ±1 pages live: three weeks of shader layers.
*Considered and rejected:* keeping `TabView(.page)` (it stays the documented fallback, see Risks); one continuous feed across weeks (it breaks the opening-per-week model and the roll).

**D6. The rake ground is drawn once, behind the days block, in content coordinates.**
- The days are the lower block of the `LazyVStack`. `SpineGround` is a `.background(alignment: .top)` of the scroll content, starting at the first seam's recorded content y. The first seam is always realised at the top of the page, before any scroll.
- The ground is a tiled `Image` rendered from the 36 × 240 rake tile, once per scheme and display scale, masked by a horizontal `LinearGradient` (full to `colX − 6`, 22 % at `colX + 110`).
- The phase is the content's own y, so there is no height bookkeeping, and Android's re-phasing after a far jump cannot happen.
*Considered and rejected:* a per-row ground with summed heights (Android); a `Canvas` the height of the week, which redraws whole on any change.

**D7. Android's form choices hold on iOS too (mirror rule):**
- one column at the phone geometry;
- no spread, no grain (Android D6) and no dock;
- the date header goes, because the opening is the header;
- the masthead and the mouth carry the week links;
- the `+` and the bottom chrome are stack C's.

**D8. A press request is state, not an event.**
- A contents tap scrolls first and presses later. The stone it targets may not exist until the scroll realises it.
- `StonePressRequests` is an `@Observable` holding one `pending: PressRequest?`. A stone that appears after the request still finds it in `.task(id:)`, where a closure or a notification would have fired into nothing.
*Considered and rejected:* `NotificationCenter`; a `PassthroughSubject`; a closure registry keyed by id (all three drop a request sent before the stone exists).

**D9. The week links name the pager's neighbour, not the calendar's** (Android lesson, Part 6).
- The roll and pager hold the current week plus the weeks with moments (`WeekRollBuilder`, unchanged). The masthead's and the mouth's "week N" therefore name `entries[i ∓ 1]`.
- Only the mouth's "the Nth begins on Monday the D" stays calendar-based, because it is about time.

**D10. The logic is pure Swift, and its time and language are explicit.**
- Every function takes `calendar: Calendar`, with its `timeZone` set, and `locale: Locale`. None reads `.current`, so the tests pin Europe/Paris and `en` / `fr` as Vitest does.
- The functions are generic over a protocol `SpineMoment` whose ids are `String`, so the web's ids (`"tue-late"`, `"s0"`) copy verbatim.
- `MomentSummary` conforms with `spineId = id.uuidString.lowercased()`. `UUID.uuidString` is uppercase, and web and Android hash and tie-break on lowercase.

**D11. Dynamic Type scales the text, not the geometry.**
- The `SpineType` ranks scale (stack B), and so do the anchors derived from them: an entry's anchor is `0.6 ×` the resolved name size, so the stone stays centred on the first line.
- The line, the column, the stones, the seams, the dots and the card chrome stay in points.
- Card text is `k × u` under stack B's `SpineType` policy `Scaling.card(cap: 1.3)`: it scales with Dynamic Type up to ×1.3 and no further. D defines no scaling factor of its own. A longer title grows the card past the formula's height, the drift the rule set accepts.
*Considered and rejected:* scaling `u` itself (the photo would shrink as text grows); fixing the card text (it fails the largest sizes).

**D12. The reading day comes from a `PreferenceKey`, not `onScrollGeometryChange`, which needs iOS 18.**
- Each realised seam reports its `minY` in the page's named coordinate space.
- The reading day is the last seam at or above 35 % of the viewport. The model writes it only when the index changes.

## The rule set, as the iOS build must satisfy it

The rule numbers below are this spec's; the sections are the web spec's. The values stay in the web spec and in `SpineLayout`.

| # | Web §, rule | iOS note |
|---|---|---|
| R1 | Page geometry: spine x 40, column x 82, right pad 18, day pads 58 / 34, gap 38, 1 pt line | Phone column only; column = width − 100 (293 at the 393 pt B2 pin; 290 on the web's 390 mock) |
| R2 | Stone sizes: 34 / 44 / 54 wide; centre on the line at the anchor (31 for a card, 0.6 × name size for an entry); hit area stone + 10 | Hit area grows to ≥ 44 pt invisibly (D-a11y) |
| R3 | Rings 1 / 2 / 3 at 4.8 / 4.4 / 4.2 pt steps, halo 0.78 step out, glyph carve constant | Rings and halo from the flat outline, scaled about its centroid |
| R4 | Card sizing: `u = min(w/70, 4.4)`, `W = min(column, (cap − 123) × ratio + 95)`, floor 180, caps 330 / 370 / 400 | `CardSize`, vectors from `card-size.test.ts` |
| R5 | Card anatomy: border inset 1.6u, radius 1.3u, padding 4.6u 9u 6.2u; index; rail; tinted field with the valence pattern; uncropped photo in a 1.8u mat; title 4.4u by valence cut; souls strip; radius 11; painted edge 2 / 3.5 / 5.5 in the edge colour; leader at the anchor | Edge, Paper: high `palette.primary`, neutral `palette.secondary`, low `palette.dark`. Night: `palette.secondary`, `palette.primary`, `palette.shaded`. Field tint: `palette.primary` mixed into `Color.pebbles.stock` |
| R6 | No-photo entry: name 19 / 24 / 31, cut 600 / 460 / 340-italic, line 1.12, max 22 ch; meta 8 below; people 6 below; no "alone" | `entryType`, phone rows |
| R7 | Many people: 0 none · 1 A · 2 A & B · 3 A, B & C · n ≥ 4 A, B & *n − 2 in words* others, ≤ 5 marks, `+k`; persons-of-week order | `peopleLine`, `namedInFull: 3` (E passes 8) |
| R8 | Seam: 8 pt, hairline + teeth (1 on 7, 3.5 tall, offset 2) + second period (1 on 19, 6.5 tall, offset 4) | `DaySeam` is 22 pt with the hairline at 14 (Android lesson) |
| R9 | Day label: 46 pt box centred on the line at −14, numeral 25 at 330, weekday small caps 10, radius 0 0 10 10 | Background is the ground colour (no grain) |
| R10 | Empty day: seam, label, italic *still* in ink-3, no padding | A still day is ≥ 48 pt with 14 pt top, so it grows with its label |
| R11 | Ground: six grooves of the 36 × 240 tile, shadow 0.75 + lit 0.9 offset, mask to 22 % | D6. Grain dropped (D7) |
| R12 | Press: stone 1 → .9 (26 %) → 1.03 (60 %) → 1, 520 ms; rings 1 → 1.08 (35 %), 1150 / 110; waves k = 0…2 to `REACH − .5k`, `1650 + 170k` / `120 + 230k`; sweep 900 / 140; spark 760 / 300; glint strength 1 / .3 / .34 (web `stone-tones.ts`, which supersedes the rule set's .62) | One easing over the whole run, linear between keyframes (Android lesson) |
| R13 | Reduced motion: no waves, no sweep; rings in ink-2; sheen .9 for 900 ms | Also: open at once, instant scroll, ratio snaps |
| R14 | The ripple is mounted on press and gone after its last frame | `StoneRipple` lives in the tree only while a press is live |
| R15 | Persons of the week: rows in people-line order, glyph 24, name 15 small caps, seven 7 pt dots with 5 pt gaps; narrow-weekday header, the reading day's in ink; cap 8 + "and *N* others" | `personsOfWeek`, `split` |
| R16 | Contents: columns 40 / 36 / fill / auto, min 42; day on first row only, lowercase, period stripped; seal 20 / 24 / 27; name 16.5 by intensity; time 12.5 tabular ink-3; reading-day dot 5 at −12 | Tap = scroll to centre, then press (no open) |
| R17 | Seal recipe: relief / line / engraving; spot `palette.primary` Paper, `palette.secondary` Night; deep `palette.shaded` Paper, `palette.secondary` mixed 80 % toward `Color.pebbles.surface` Night | New `StoneSeal` (iOS has none) |
| R18 | Opening type: kicker 12 / 600, title 40 / 300 balanced, summary 18 italic 380, section labels 11.5; gaps 46 / 40 / 40 | `SpineType` (B); balance via `.multilineTextAlignment` + `lineLimit(nil)` |
| A1–A8 | §Accessibility: headings; stone button and its name; card or entry button; contents actions; day dots label; polite week announcement; colour never alone; contrast | §Accessibility below |

## Data flow

`PathModel` is a new `@Observable @MainActor final class` in `Features/Path/PathModel.swift`. `PathView` creates it with `@State` and runs `load()` in `.task`. It takes over today's inline `load()`, the refocus rule and the delete path, unchanged in behaviour.

```swift
enum Phase: Equatable { case loading, failed, content }
private(set) var phase: Phase
private(set) var entries: [WeekRollEntry]          // WeekRollBuilder.build(moments:calendar:today:)
var focusedWeekStart: Date                          // the one source of truth for roll, pager, links
private(set) var readingDay: [Date: Int]            // weekStart -> reading-day index (D12)
let pressRequests: StonePressRequests               // D8; cancel() on page change
var returnFocus: UUID?                              // the slot VoiceOver returns to; stack E sets it on close
func page(for weekStart: Date) -> SpinePage         // memoised per load
func focus(_ weekStart: Date)                       // roll tap, links; no-op if equal
```

- **Registry.** The model does not own `CardFrameRegistry`. `RootView` creates it, and the views read it from the environment (§Frame registry).
- **Reload.** `PathView` runs `.onChange(of: revision.value) { Task { await model.load() } }` on stack C's `MomentsRevision`, so an edit, completion or delete made from another tab or a sheet reloads the Path. It skips the reload when the Path itself caused the bump, as stack C specifies.

- **Source.** `MomentsService.pathMoments()` → `[MomentSummary]`, newest first. Each row carries `id`, `name?`, `happened_at` (decoded flexibly), `created_at`, `first_snap_path?`, and `pebble?`. The pebble holds `id`, `intensity`, `positiveness`, `visibility`, `emotion?`, `glyph?` and `souls: [{id, name, glyph_id}]`. `pebble == nil` is a bare moment.
- **Palettes.** `EmotionPaletteService.palette(for:)` returns nil for no emotion, which draws the brand stone (D1).
- **Photos.** `SnapURLCache.signedURLs(storagePath:)` gives the original, with the thumb as placeholder. The ratio comes from `SnapAspectCache` (D3).

`SpinePage` is a value: `weekStart`, `week: SpineWeek<MomentSummary>`, `words: WeekWords`, `persons: [PersonOfWeek]`, `contents: [MomentSummary]` (`readingOrder`), `previous: WeekNeighbour?`, `next: WeekNeighbour?` (D9; `WeekNeighbour { number: Int; weekStart: Date }`), `isCurrent: Bool`, `soulGlyphs: [String: [GlyphStroke]]`.

The views hold only UI state: the scroll proxy, the in-flight press per stone, and the card's ratio animation. A soul-cache miss logs nothing and draws a dashed mark. A failed load logs and shows `phase = .failed`, as today.

## Logic API (part D1)

The logic lives under `apps/ios/Pebbles/Features/Path/Spine/Logic/`: pure Swift, `Foundation` only, no SwiftUI.

```swift
protocol SpineMoment { var spineId: String { get }; var happenedAt: Date { get }; var isPebble: Bool { get }; var soulIds: [String] { get } }
struct SpineDay<M: SpineMoment> { let date: Date; let index: Int; let moments: [M]; let isStill: Bool; let isToday: Bool }
struct SpineWeek<M: SpineMoment> { let weekStart: Date; let days: [SpineDay<M>]; var pebbleCount: Int }
enum SpineWeekBuilder {
  static func spineWeek<M>(weekStart: Date, moments: [M], today: Date, calendar: Calendar) -> SpineWeek<M>
  static func readingOrder<M>(_ week: SpineWeek<M>) -> [M] }
enum LocalDays { static func dayIndexInWeek(weekStart: Date, _ date: Date, calendar: Calendar) -> Int }
struct PersonOfWeek: Equatable { let soulId: String; let name: String; let days: [Bool]; let dayCount: Int; let pebbleCount: Int }
enum PersonsOfWeek { static let cap = 8
  static func personsOfWeek<M>(_ moments: [M], names: [String: String], weekStart: Date, calendar: Calendar, locale: Locale) -> [PersonOfWeek]
  static func split(_ rows: [PersonOfWeek], cap: Int = cap) -> (shown: [PersonOfWeek], folded: [PersonOfWeek]) }
struct PeopleLine: Equatable { let named: [String]; let others: Int; let othersWords: String; let marks: [String]; let overflow: Int }
enum PeopleLines { static let marks = 5; static let namedOnCard = 8   // stack E: namedInFull: PeopleLines.namedOnCard
  static func peopleLine(_ soulIds: [String], order: [String], locale: Locale, namedInFull: Int = 3) -> PeopleLine }
// SpineType.Cut is stack B's: { size: CGFloat; weight: Int; italic: Bool; trackingEm: CGFloat }. D defines no type struct of its own.
struct EntryType: Equatable { let name: SpineType.Cut; let lineHeight: CGFloat; let anchor: CGFloat }
enum EntryTypes { static func entryType(intensity: Int, valence: Int) -> EntryType
  static func contentsNameType(intensity: Int, valence: Int) -> SpineType.Cut
  static func cardTitleCut(valence: Int) -> SpineType.Cut   // size is 4.4 (in u); weight and italic by valence }
struct CardSize: Equatable { let width: CGFloat; let height: CGFloat; let cap: CGFloat }
enum CardSizes { static let chromeVertical: CGFloat = 123, chromeHorizontal: CGFloat = 95, minWidth: CGFloat = 180, pendingRatio: CGFloat = 1
  static func cap(_ intensity: Int) -> CGFloat; static func edge(_ intensity: Int) -> CGFloat
  static func cardSize(intensity: Int, ratio: CGFloat?, column: CGFloat) -> CardSize }
enum WeekTitle: Equatable { case sameMonth(from: String, to: String, month: String); case crossMonth(from: String, fromMonth: String, to: String, toMonth: String) }
struct WeekWords: Equatable { let number: Int; let year: Int; let ordinal: String; let title: WeekTitle; let previousNumber: Int; let nextNumber: Int; let nextOrdinal: String; let nextMonday: String }
enum WeekOpening {
  static func weekWords(weekStart: Date, calendar: Calendar, locale: Locale) -> WeekWords
  static func summaryWords(pebbles: Int, people: Int, locale: Locale) -> (pebblesWords: String, peopleWords: String)
  static func sentenceCase(_ text: String, locale: Locale) -> String
  static func weekdayLetters(weekStart: Date, calendar: Calendar, locale: Locale) -> [String]
  static func dotDays(_ days: [Bool], weekStart: Date, calendar: Calendar, locale: Locale) -> (count: Int, days: String) }
enum SpineDates { static func shortWeekday(_ d: Date, calendar: Calendar, locale: Locale) -> String
  static func longDay(_ d: Date, calendar: Calendar, locale: Locale) -> String
  static func dayRange(_ from: Date, _ to: Date, calendar: Calendar, locale: Locale) -> String }
enum NumberWords { enum Gender { case masculine, feminine }
  static func cardinal(_ n: Int, locale: Locale, gender: Gender = .masculine) -> String
  static func ordinal(_ n: Int, locale: Locale, gender: Gender = .masculine) -> String
  static func dayOfMonth(_ n: Int, locale: Locale) -> String }
enum StableHash { static func fnv1a(_ s: String) -> UInt32; static func seed(_ id: UUID) -> UInt32 /* fnv1a(id.uuidString.lowercased()) */ }
```

The rules these functions encode:
- **Days.** They run oldest first in every week, including the current one. Moments sort by `(happenedAt, spineId)`. A past empty day is still. A future day is dropped unless it holds a moment. A bare moment makes its day not still.
- **People.** Only pebbles name people, and a soul counts once per pebble. The order is days desc, then pebbles desc, then name by `locale` collation (`compare(_:options:range:locale:)`), then id.
- **Number words.** They are a hand port of `number-words.ts`: `NumberFormatter.spellOut` cannot give "quarante et unième" or "premier".
- **Day lists.** `dotDays` joins by hand with the serial comma outside French, matching the vector "…, and Saturday 10". `ListFormatter` follows the region, so it is not used.
- **Not ported.** `stone-recipe`, `stone-carvings` and `stone-geometry` stay unported (D1).

**Vector mapping.** The tests live under `apps/ios/PebblesTests/Spine/`. Each file opens with the web file's path and its commit, and copies the inputs and expected outputs verbatim:

| Swift Testing file | Web vector file | Skipped cases |
|---|---|---|
| `SpineWeekBuilderTests.swift` | `apps/web/lib/utils/spine/spine-week.test.ts` | none; the `row(json)` case decodes the same JSON through `MomentSummary` |
| `LocalDaysTests.swift` | `persons-of-week.test.ts` (DST week, Sunday 23:59) | `local-day.ts` has no test of its own |
| `PersonsOfWeekTests.swift` | `apps/web/lib/utils/spine/persons-of-week.test.ts` | none |
| `PeopleLineTests.swift` | `apps/web/lib/utils/spine/people-line.test.ts` | none |
| `EntryTypeTests.swift` | `apps/web/lib/utils/spine/entry-type.test.ts` | the `wide: true` rows |
| `CardSizeTests.swift` | `apps/web/lib/utils/spine/card-size.test.ts` | the wide column and caps |
| `WeekOpeningTests.swift` | `apps/web/lib/utils/spine/week-opening.test.ts` | none |
| `SpineDatesTests.swift` | `apps/web/lib/utils/spine/spine-dates.test.ts` | none; `dayRange` matched with the same `\s` tolerance |
| `NumberWordsTests.swift` | `apps/web/lib/i18n/number-words.test.ts` | none |
| `StableHashTests.swift` | `apps/web/lib/utils/spine/stone-recipe.test.ts` seeds; Android `UuidHashTest` | expects 3118864542 and 3303075303, and the same value from a `UUID` parsed from the uppercase string |
| `PathMomentDecodingTests.swift` | verbatim `path_moments` rows (foreign-payload rule) | `souls` present, absent and empty; `glyph: null`; `name: null`; whole-second and microsecond timestamps; `pebble: null` |

**Strings (D1).**
- **Where.** `Localizable.xcstrings` gains the web's `path.spine.*` set (`apps/web/lib/i18n/messages/{en,fr}.json`) under explicit dotted keys, read as `String(localized: "path.spine.kicker")` with arguments.
- **How.** The entries are inserted as text at an alphabetical anchor and never rewritten with a JSON dump.
- **Plurals.** They are xcstrings plural variations. French carries `one`, `many` and `other`, with `many` equal to `other`.
- **ICU.** The web's ICU `=0` branches become an explicit zero check in `WeekOpeningText`.
- **Collision check.** The catalogue holds no `path.spine` key today. The lowercase "week %lld" link is namespaced as `path.spine.weekLink`, so it cannot collide with the existing `Week %lld` and `Week %lld, %lld pebbles` keys. `LocalizationTests` guards EN/FR parity.

| Key | EN | FR |
|---|---|---|
| `path.spine.kicker` | the %1$@ week · %2$lld | la %1$@ semaine · %2$lld |
| `path.spine.titleSameMonth` | The %1$@ to the %2$@ of %3$@ | Du %1$@ au %2$@ %3$@ |
| `path.spine.titleCrossMonth` | The %1$@ of %2$@ to the %3$@ of %4$@ | Du %1$@ %2$@ au %3$@ %4$@ |
| `path.spine.summaryPebbles` (plural on n) | zero: no pebbles · one: %@ pebble · other: %@ pebbles | zero: aucun galet · one: %@ galet · many/other: %@ galets |
| `path.spine.summaryPeople` (plural on n) | one: , %@ person · other: , %@ people | one: , %@ personne · many/other: , %@ personnes |
| `path.spine.persons` | persons of the week | les personnes de la semaine |
| `path.spine.personsMore` (plural) | one: and %@ other · other: and %@ others | one: et %@ autre · many/other: et %@ autres |
| `path.spine.contents` | contents | sommaire |
| `path.spine.with` | with | avec |
| `path.spine.withNames` | With %@. | Avec %@. |
| `path.spine.others` (plural) | one: & %@ other · other: & %@ others | one: & %@ autre · many/other: & %@ autres |
| `path.spine.still` | still | calme |
| `path.spine.untitled` | untitled | sans titre |
| `path.spine.photoUnavailable` | photo unavailable | photo indisponible |
| `path.spine.weekEnds` | the %@ week ends here | la %@ semaine s'arrête ici |
| `path.spine.nextBegins` | The %1$@ begins on Monday the %2$@. | La %1$@ commence le lundi %2$@. |
| `path.spine.weekLink` | week %lld | semaine %lld |
| `path.spine.dayDots` (plural) | one: On one day: %2$@ · other: On %1$lld days: %2$@ | one: Un jour : %2$@ · many/other: %1$lld jours : %2$@ |
| `path.spine.stoneA11y` | %1$@. %2$@, %3$@. %4$@, %5$@. %6$@ Opens the pebble. | %1$@. %2$@, %3$@. %4$@, %5$@. %6$@ Ouvre le galet. |
| `path.spine.blankA11y` | %1$@. %2$@, %3$@. Not a pebble yet. %4$@ Opens the moment. | %1$@. %2$@, %3$@. Pas encore un galet. %4$@ Ouvre le moment. |
| `path.spine.contentsHint` | Scrolls to and presses the stone | Fait défiler jusqu'au galet et le presse |
| `path.spine.feedA11y` | The week, %@ | La semaine, %@ |
| `path.spine.glanceA11y` | The week at a glance | La semaine en un coup d'œil |
| `path.spine.stone.small.lowlight` / `.neutral` / `.highlight` | Small lava stone / Small river pebble / Small gem | Petite pierre de lave / Petit galet de rivière / Petite gemme |
| `path.spine.stone.medium.*` | Medium lava stone / Medium river pebble / Medium gem | Pierre de lave moyenne / Galet de rivière moyen / Gemme moyenne |
| `path.spine.stone.large.*` | Large lava stone / Large river pebble / Large gem | Grande pierre de lave / Grand galet de rivière / Grande gemme |

`cardAria` folds into `stoneA11y`, because the texts are identical on web; Android made the same call. `prevWeek` and `nextWeek` fold into `weekLink`. `carve` is not ported, because the mouth has no carve link (D7). `contentsHint` is new on iOS (A4), worded in the web's register ("galet").

## View tree

All paths are under `apps/ios/Pebbles/Features/Path/`.

- `PathView.swift`: the Path tab's root. It owns `PathModel`, the roll, the pager, today's sheets and the delete dialog, plus the `onOpen(SpineOpen)` hand-off (until E: `selectedMomentId = open.momentId`).
- `Spine/Roll/SpineWeekRoll.swift`: a horizontal `ScrollView` + `LazyHStack` of ISO numerals in cells 56 × 44, ink-2. The focused cell is ink with a 1 pt `onSurface` underline. It keeps today's cell accessibility label.
- `Spine/SpinePager.swift`: the paging `ScrollView` of D5, bound to `focusedWeekStart`.
- `Spine/SpineWeekView.swift`: one page. A `ScrollViewReader` › `ScrollView` › `LazyVStack` of `SpineWeekRows` with the ground behind. It owns the reading-day preference and contents → press.
- `Spine/SpineWeekRows.swift`: the one place the row order lives: masthead, opening, persons (if any), contents (if any), then per day a seam followed by its pebbles, then the mouth (or the empty week).
- `Spine/SpineWeekStack.swift`: the same rows in a `VStack`, for B2 references and the debug lab.
- `Spine/Opening/WeekMasthead.swift`: "week N" back link (44 pt target) on the leading side; the ISO number in ink on the trailing side.
- `Spine/Opening/WeekOpening.swift` and `WeekOpeningText.swift`: kicker, title in words, summary.
- `Spine/Opening/PersonsOfWeekView.swift` (rows, weekday-letter header, the fold) and `DayDots.swift` (the seven dots).
- `Spine/Opening/WeekContents.swift`: rows of day, seal, name and time, with the reading-day dot.
- `Spine/Opening/WeekMouth.swift`: the line's last 26 pt, a three-stone outline cairn (14 / 19 / 25), "the Nth week ends here", "The N+1th begins on Monday the D.", and the "week N" link.
- `Spine/Opening/SpineEmptyWeek.swift`: the current week with nothing in it, with its `onCreate`.
- `Spine/Feed/SpineGround.swift` + `SpineRakeTile.swift`: the ground of D6.
- `Spine/Feed/DaySeam.swift`: the seam and the margin label, or *still*.
- `Spine/Feed/SpinePebble.swift`: one moment. The stone on the line, the leader, then the card or the entry. It carries the press choreography, the claim of a press request and the context-menu delete.
- `Spine/Feed/SpineEntry.swift`: name, meta and people line, as a full-width button.
- `Spine/Feed/SpineCard.swift`: the Deck card. It reports its frame to `CardFrameRegistry`.
- `Spine/Feed/PeopleLineText.swift` + `SoulMark.swift`: a `Text` concatenation with inline glyph `Image`s rendered from strokes; a dashed square for a missing glyph.
- `Spine/Stone/SpineStone.swift`: halo and rings, then `PebbleStoneView`, then `StoneGlint`, with `StoneRipple` mounted only during a press.
- `Spine/Stone/StoneSeal.swift`, `StoneRipple.swift`, `StoneGlint.swift`, `StoneMotion.swift` (timings, `piecewise` progress tables, `openDelay 0.36 s`, `goPressDelay 0.62 s`).
- `Spine/SpineLayout.swift`: every point constant.
- `Spine/SpineColors.swift`: only the blends with the per-emotion palette: `spot(palette:)`, `deep(palette:)`, `edge(palette:intensity:)`, the field tint (`tintK .09 / .13`) and pattern (`patK .34 / .40`).
- The scheme-derived constants are stack A's generated `Color.pebbles.*`, never redefined here: `stock`, `rule`, `cardRule`, `line`, `seam`, and likewise `teeth`, `ring`, `ripple` and `halo`.
- `Spine/SnapAspectCache.swift`, `StonePressRequests.swift`, `CardFrameRegistry.swift`.
- `Spine/Debug/SpineFixtureWeek.swift` (`#if DEBUG`; the port of `apps/web/lib/seed/spine-fixture-week.ts`, zone Europe/Paris, today Sunday 11 October) and `SpineLabView.swift`, reached from Settings → Developer beside the stone lab.

## Geometry (points)

- **Feed.** Feed top 48, bottom 96, plus the safe-area bottom, which clears stack C's bar.
- **Line.** 1 pt at x 40, running the height of each day.
- **Column.** It starts at 82 and runs to width − 18.
- **Days.** A day with moments has 58 top and 34 bottom. The seam row carries the 58. The label's 14 pt overhang comes off the previous day's bottom pad, never the day's own top. A still day is ≥ 48 with a 14 top.
- **Stone row.** The centre is at (40, anchor). The anchor is 31 for a card and `0.6 × resolved name size` for an entry, which gives 11.4, 14.4 and 18.6 at the default size. The row's minimum height is `anchor + stoneH/2 + 14`. The hit shape is the stone + 10, grown to 44 × 44 about its centre. The leader is 1 pt from x 40 to the column at the anchor's y.
- **Card.** `u = min(cardWidth / 70, 4.4)`. At the 393 pt fixture width (column 293): 16:9 → 293 × about 237 (the photo is 293 − 21.6u ≈ 203 wide, so H = 123 + 203 / 1.78); 2:3 → 233 × 330 / 260 × 370 / 280 × 400 by intensity (below the column, so as on the web). The photo sits in a 1.8u mat and is `scaledToFit`, never cropped. The field tint, pattern, index, rail and title follow R5. The shadow is the rule set's `--spine-card-shadow` as two `.shadow` modifiers on a `RoundedRectangle(cornerRadius: 11)` background, so text gets no shadow.
- **Seam and label.** The seam view is 22 tall, with the hairline at 14 and the teeth below it as two `Path` periods. The label is 46 wide, centred on x 40, at top −14 of the hairline.
- **Persons.** Rows are ≥ 44 tall: glyph column 30 (glyph 24), a 10 gap, the name, then the dots, 7 pt each with 5 pt gaps, 79 wide. A filled dot is ink; an empty dot is a 1 pt inset ring in ink-3 at .7. The header letters are right-aligned over the dots.
- **Contents.** Rows ≥ 42 tall in columns 40 / 36 / fill / auto. Seals are 20 / 24 / 27.
- **Dynamic Type.** Per D11. At accessibility sizes the persons and contents rows grow in height. The 40 / 36 columns stay; the day label and the contents' day column wrap; the entry name has no line limit.

## Pager semantics

- **Pages.** One page per `WeekRollEntry`. The page id is its `weekStart`.
- **One source of truth.** `focusedWeekStart` lives in `PathModel`. The pager's `scrollPosition(id:)` binding reads it. A settled swipe writes through `model.focus(_:)`. A roll tap, a link or a Capture refocus calls `model.focus(_:)` inside `withAnimation`, or without one under Reduce Motion. `focus` ignores an equal value, so there is no loop and no Android-style steering counter.
- **Links.** The masthead link exists only when `previous` exists, and the mouth link only when `next` exists, each naming `WeekWords.number` of that neighbour (D9). The mouth's "begins on Monday the D" uses `WeekWords.nextOrdinal` / `nextMonday` (the calendar).
- **Load.** The refocus rule is today's `PathView.load()`: keep the focused week if it is still there, else the current week, else the closest. After Capture, `focusWeek(containing:)` as today.
- **Reselect.** Stack C's Path-tab reselect calls `model.focus(currentWeek)` and sets `scrollToTop` on the page, which scrolls it to the masthead.
- **Page change.** A page change cancels any pending press request. The new page starts at its top (masthead), and VoiceOver announces the title (A6).

## Press-request state

```swift
@Observable @MainActor final class StonePressRequests {
  struct PressRequest: Equatable { let momentId: UUID; let seq: Int }
  private(set) var pending: PressRequest?
  func request(_ id: UUID)          // pending = PressRequest(id, seq += 1)
  func claim(_ r: PressRequest)     // if pending == r { pending = nil }
  func cancel() }
enum CardFace: String, Codable { case recto, verso }  // defined here; stack E consumes it
```

- **Stone press.** `TapHaptics.play(.selection)` and the press choreography. After 360 ms (at once under Reduce Motion), `onOpen` with `.verso` for a pebble or `.recto` for a bare moment.
- **Card or entry press.** `onOpen` with `.recto` at once, and the stone plays its press.
- **Contents press** (`go`):
  1. `proxy.scrollTo(rowId, anchor: .center)`, which realises the row;
  2. on the next frame, `proxy.scrollTo("stone-\(id)", anchor: .center)`, both animated unless Reduce Motion is on;
  3. after 620 ms, `pressRequests.request(id)`.
  The stone's `.task(id: pressRequests.pending)` claims a matching request and plays the press **without opening**.

The press is per stone `@State var pressSeq: Int?`, which mounts the ripple. A `TimelineView(.animation)` drives one progress 0 → 1 per element through `StoneMotion`'s piecewise tables (WAAPI semantics: one easing over the run, linear between keyframes), and unmounts at the last element's end.

## Frame registry for stack E

```swift
@Observable @MainActor final class CardFrameRegistry {             // the shape of the held-card spec §Registry
  @ObservationIgnored private(set) var frames: [UUID: CGRect] = [:] // global rects; per-frame writes do not invalidate views
  var hidden: UUID?                                                 // observed: that slot hides while E flies
  func report(_ momentId: UUID, frame: CGRect); func forget(_ momentId: UUID) }
struct SpineOpen { let momentId: UUID; let face: CardFace; let summary: MomentSummary; let soulOrder: [String] }  // same lowercase-string ids as the logic
```

This block is canonical: stack E consumes these shapes as written here.
- **Reporting.** `SpineCard` reports with `onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { registry.report(id, frame: $0) }`. The 17.0 target already uses that API (`Features/Path/Components/WeekRollView.swift:56`). The card forgets in `.onDisappear`, so a scrolled-out card has no frame and E falls back to its fade.
- **Entries.** `SpineEntry` reports too, because E opens a bare moment or an entry plainly but still needs a focus-return target.
- **Why `frames` is unobserved.** `frames` is `@ObservationIgnored`. E reads it imperatively at open and close, and an observed dictionary written on every scroll frame would invalidate every reader.
- **Ownership.** `RootView` creates the registry and injects it with `.environment`. The Spine reads it from the environment and calls `onOpen(SpineOpen)`. `soulOrder` is the page's persons-of-the-week order, so the card names people in the Spine's order.
- **Focus return (D3).** Each `SpineEntry` and `SpineCard` binds `.accessibilityFocused($slotFocus, equals: momentId)`. Here `slotFocus` is an `@AccessibilityFocusState var slotFocus: UUID?` held by `SpineWeekView`, and it mirrors `PathModel.returnFocus: UUID?`. Stack E sets `returnFocus` on close, and the page moves VoiceOver focus to that slot.

## Removal (D5b)

These go, with their replacements:
- `Components/WeekPathView.swift` → `SpineWeekView`;
- `Components/PathPebbleRow.swift` → `SpinePebble`;
- `Components/PathPebbleSnapThumb.swift` → the card's photo;
- `Components/WeekHeaderView.swift` → the opening and the masthead;
- `Components/WeekRollView.swift` and `Components/WeekRollCairnCell.swift` → `SpineWeekRoll`;
- `Resources/pbbls-cairn-states.riv` (the cairn cell is its only user; `packages/rive` keeps its copy);
- `GroupPebblesByISOWeek.swift`, already orphaned (only its test uses it).

Tests that go with them: `PathPebbleRowGeometryTests`, `PathPebbleRowNameColorTests`, `WeekHeaderFormatTests`, `WeekRollViewMarginTests` and `GroupPebblesByISOWeekTests`. The string keys that only those views used go too ("Fresh week" moves to `SpineEmptyWeek`). `apps/ios/CLAUDE.md`'s Path line is updated.

These stay: `WeekRollBuilder`, `PebbleStoneView`, `Components/PebbleRow.swift`, `GroupPebblesByMonth`, `PebbleDetailSheet` and the `Read/` family (E's to remove).

## Accessibility

- **A1, headings.** The title is `.isHeader`. Each seam carries a header "Monday 5 October" (`longDay`). The label's numeral and weekday are hidden behind it.
- **A2, the stone.** A `Button` named by `path.spine.stoneA11y`: name, date, time, stone kind, emotion, people, then "opens the pebble". Its hit target is ≥ 44 pt.
- **A3, card or entry.** One `Button` with `.accessibilityElement(children: .ignore)` and the same name. Index and rail are `.accessibilityHidden(true)`. Delete is offered as `.accessibilityAction(named:)` beside the context menu.
- **A4, contents.** Rows are buttons named by the pebble, with the hint `path.spine.contentsHint`.
- **Focus return (D3).** Each entry and card carries `.accessibilityFocused($slotFocus, equals: momentId)`, mirrored from `PathModel.returnFocus`, so stack E's close lands VoiceOver on the slot (§Frame registry).
- **A5, day dots.** One element labelled from `dotDays` ("On four days: Monday 5, …"). The letter header is hidden.
- **A6, the week.** On a focus change, `AccessibilityNotification.Announcement(title).post()`.
- **A7, colour never alone.** Valence is also the name's cut, and intensity also the size and rank.
- **A8, contrast.** Ink-3 appears only for the time and the empty dots, both repeated in text.
- **Order.** Masthead, opening, persons, contents, then per day the seam heading followed by each pebble (stone, then card or entry), then the mouth. It is pinned with `.accessibilitySortPriority` inside each row, because the stone is drawn leading but laid out in an overlay.
- **Reduce Motion.** R13, plus instant scroll and paging, the ratio snapping, and no sheen sweep.

## Testing

| Part | Proof |
|---|---|
| D1 | The vector files above; `PathMomentDecodingTests`; `LocalizationTests` parity |
| D2a | `StoneMotion` table tests (keyframe values at their stops, total durations); `StonePressRequestsTests` (request, claim of a stale seq, cancel); B2 references of the nine stones with rings, halo and seal, Paper and Night |
| D2b | `SnapAspectCacheTests` (caches by path; a timeout yields nil once with no retry; uses the existing `SignedURLProviding` fake); `CardFrameRegistryTests` (report, forget, `hidden`); B2 references of four cards (1:1, 3:2, 16:9, 2:3) at 393 pt |
| D3 | B2 references of the fixture days in Paper and Night: one entry per intensity × valence, the 14-soul picnic, the glyph-less stone, the bare and untitled moments, the still Friday |
| D4 | `ReadingDayTests` (pure: seams' minY and viewport → index); `WeekNeighbourTests` (pager neighbours skip empty weeks: week 35 ↔ 41); B2 references of the opening (`VersoLight` / `VersoDark` parity), the mouth, the empty week |
| D5a | B2 references of the whole fixture week at 393 pt, Paper and Night × EN and FR, at `.large`, plus `-ax2` (`.accessibility2`, B2's cap) in EN Paper |
| D5b | The suite stays green after the removals; `grep` finds no reference to a removed type |

The fixture week's photos are the web's five `public/sandbox/*.jpg` files, copied into the test bundle. Their ratios are 1:1, 3:2, 3:2, 16:9 and 2:3, primed into `SnapAspectCache`. A `SpineSnapSource` environment value hands the references decoded `UIImage`s, because `AsyncImage` does not render in `ImageRenderer`.

**Device checklist (iPhone 15, D5a).**
- Paper and Night.
- EN and FR.
- A real week with photos of three ratios, and a 14-soul pebble.
- A press on the stone, the card and a contents row, with and without Reduce Motion.
- The largest Dynamic Type on device (the references stop at `.accessibility2`).
- VoiceOver over the stone, the card, the contents and a day-dot row.
- Paging by swipe, by the roll and by both links, checking that the links name the right week.
- Low Power Mode (the flat stone).
- Scrolling a 20-pebble week, watched for dropped frames in Instruments.

## Parts (`gh stack`; labels `feat`, `ui`, `ios`, except D5b `chore`; all M64 · New Path, all `no-lab-note`)

| # | Branch | Contents | Done when |
|---|---|---|---|
| D1 | `feat/1130-spine-logic` | `Spine/Logic/*`, `PathSoul` + `souls` decoding, the `path.spine.*` strings, the fixture week; Arkaik moves at pickup | Every vector file passes on the CI simulator; the decode tests pass on verbatim rows; no view changes; the four nodes read `ios: development` |
| D2a | `feat/1130-spine-stone` | `SpineLayout`, `SpineColors`, `SpineStone`, `StoneSeal`, `StoneRipple`, `StoneGlint`, `StoneMotion`, `StonePressRequests`, the `seed:` parameter, the lab gallery | Stone references baked; a press plays to spec on device; no visible change in the app |
| D2b | `feat/1130-spine-card` | `SpineCard`, `SnapAspectCache`, `CardFrameRegistry`, `CardFace`, `SpineOpen`, the card references | Card references baked at four ratios; the ratio eases from square on device |
| D3 | `feat/1130-spine-feed` | `SpineGround`, `DaySeam`, `SpinePebble`, `SpineEntry` (with the slot focus binding), `PeopleLineText`, `SoulMark`, `SpineWeekRows` (days only), `SpineWeekStack` | The day references match Android's `SpineFeedScreenshots` states; the 20-pebble scroll budget holds |
| D4 | `feat/1130-spine-opening` | `WeekMasthead`, `WeekOpening`, `PersonsOfWeekView`, `DayDots`, `WeekContents`, `WeekMouth`, `SpineEmptyWeek`, `SpineWeekView`, `SpinePager`, `SpineWeekRoll`, reading day, contents → press | The fixture week scrolls and pages in the lab; the links name the pager's neighbour; a contents tap centres and presses without opening |
| D5a | `feat/1130-path-becomes-spine` | `PathModel` (with the `MomentsRevision` reload); `PathView` renders the pager; full references; device checklist; decision log; `apps/ios/CLAUDE.md` | `Resolves #1130`; the checklist is ticked |
| D5b | `chore/1130-remove-row-path` | The removal list, the `.riv`, the five tests, the orphaned strings | Lint, build and tests green; no reference to a removed type remains |

- Each part lints (`swiftlint`), builds and passes `npm run test --workspace=@pbbls/ios` alone.
- D1–D4 show only in the debug lab and the references.
- Push the stack only after a fetch and a fast-forward.

**Arkaik (D1, at pickup; hosted, MCP only; if `arkaik-mcp` is unavailable, stop and say so).**
- `V-timeline`'s iOS platform status, `AC-the-week-opens-with-who-was-there-and-what-happened`, `AC-path-week-roll` and `AC-a-photo-keeps-its-own-shape` go to `ios: development`.
- `update_node` replaces metadata wholesale, so the gherkin, the values and every `platformStatuses` go back together.
- The App marks `releasing` at merge. Never set `live`.
- Every part is labelled `no-lab-note`, since Arkaik covers the change (standing rule).

**Decision log (D5a).** "2026-10-DD, dated at merge — The iOS Path becomes the Spine: the existing lit stone in the margin, a SwiftUI paging pager, souls from `path_moments`". It extends 2026-10-18 (#1122) and records D1, D4, D5, D6 and D11.

## New patterns, raised in the PR

None of these exist in `apps/ios` today. Each is named in its part's PR body for review, per root `CLAUDE.md` ("new patterns require discussion"):
- **`SnapAspectCache` (D2b).** A session cache that reads image headers without decoding them, beside `SnapURLCache`.
- **`StonePressRequests` (D2a).** Observable state consumed as a request: a stone claims it in `.task(id:)`, which replaces a callback or a notification (D8).
- **`SpineSnapSource` (D2b).** An environment value that hands decoded `UIImage`s to the card in references and the lab, with the signed-URL loader in the app.
- **`PathModel` (D5a).** The Path's first `@Observable` view model. It replaces `PathView`'s inline `@State` and `load()`.
- **The SwiftUI paging `ScrollView` (D4).** `scrollTargetBehavior(.paging)` with `scrollPosition(id:)` replaces `TabView(.page)` as the week pager (D5).

## Risks

- **Paged `ScrollView` vs `TabView(.page)`.**
  - The risks: on iOS 17, `scrollPosition(id:)` over a `LazyHStack` can report `nil` on first layout, and can drift when the entries change under it (a deletion, a reload). The nested vertical scroll can also steal diagonal swipes.
  - Mitigation: seed the position in `.onAppear`, and re-assert it after each `entries` change.
  - Spike in D4 on device before building the opening on it. If it fails, fall back to `TabView(.page)` bound to `focusedWeekStart`, as today. The registry still works, because its frames are global, but ±1 pages stay live and the stone budget below triples.
- **`ImageRenderer` limits.**
  - It renders neither `AsyncImage` (hence `SpineSnapSource`) nor a `ScrollView` (hence `SpineWeekStack`).
  - It may not run `layerEffect` shaders. That fallback is B2's D6 (its offscreen-window capture), not this stack's; D2a checks the gallery through it first.
  - `TimelineView` renders its first frame only, which is the rest state.
- **The shader stone in a lazy list.**
  - Each `PebbleStoneView` is several `layerEffect` passes. A `LazyVStack` drops off-screen rows and re-renders them on return.
  - Measure a 20-pebble week on the iPhone 15 in D3. The budget is 60 fps on scroll and the first page within 2 frames.
  - If it misses: first `.drawingGroup()` on the stone stack. Failing that, a snapshot cache of rendered stones keyed like Android's (size, polarity, palette hex, glyph id, seed, scheme, scale; 64 entries) behind `SpineStone`.
- **Frames for stack E while paging.** A frame read during a swipe is mid-flight. E must read at the press, when the pager has settled.
- **Small caps and the variable Ysabeau.** These are stack B's, but D3 depends on them. If `smcp` falls back to full caps, B's fallback applies, not a local one.
- **Reading-day preference churn.** A preference fires on every scroll frame. The model writes only on an index change, and the preference reduces to the minimal value (one `CGFloat` per realised seam).
