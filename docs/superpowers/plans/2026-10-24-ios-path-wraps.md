# iOS: the Path wraps (strip, week wrap, month page) — stack plan

**Issue:** #1226 (M64 · New Path) · **Spec:** `docs/superpowers/specs/2026-10-23-path-wraps-server-and-android-design.md` · **Android reference:** stack #1213, parts 6–13 (#1217 → #1225) · **Status:** draft for the maintainer's review, no code yet

The server already computes every value. This stack is display only: decode the three RPCs, map them to display models, draw them the iOS way, and say them in the same words as Android.

---

## 1. Ground rules

- **Same data, same words, native idiom.** Numbers, memberships, orders, thresholds and sentences are identical to Android: they come from the payload and from Android's strings, ported word for word. Where Android used a Material idiom (bottom sheet, tab row, segmented buttons, DataStore), iOS uses the platform's own, per the HIG. §2 lists each choice.
- **No client aggregation.** No iOS code counts, ranks, thresholds or allocates. A test that re-derives a server number is a bug.
- **Decode the harness's payloads verbatim.** Swift Testing reads `packages/supabase/scripts/oracles/path-wrap/payloads/*.json` in place through `#filePath`, as the reference PNGs and Spine photos already are. There is one copy, and the harness owns it. `ios.yml` already runs on `packages/supabase/**`, so a payload change re-runs the iOS decoding tests.
- **Tolerant decoding.** An unknown enum becomes `.unknown`, and an unknown tile, section or strip kind is dropped. A missing section is hidden. Date-only fields (`YYYY-MM-DD`) decode into a calendar-day value, never through a `Date` strategy (cross-surface timestamp rule). A malformed payload fails that one page, never the Path.
- **Copy lives in one place:** `WrapCopy`, over `Localizable.xcstrings` through the existing `SpineStrings` loader (explicit dotted keys, language passed in, so tests pin `en`/`fr`). Plurals, number words and French lowercase months mirror Android's `WrapCopy`. **Android's `WrapCopyTest` and `WrapCopyFrenchTest` assertions are ported verbatim** as the parity proof.
- **iOS 17 APIs only**, `@Observable`, `os.Logger` on every error path, `Color.pebbles.*` only (`ThemeLiteralsTests`), Swift Testing only.

---

## 2. Native mapping (Android → iOS)

| Concern | Android | iOS (this plan) | Why |
|---|---|---|---|
| Tile → lists | `ModalBottomSheet` (partial), `SecondaryScrollableTabRow`, `HorizontalPager` | `.sheet` with `.presentationDetents([.medium, .large])`, drag indicator, Spine paper background. A `NavigationStack` inside, with **Done** as the confirmation action. The pages are a paging `ScrollView`, the Spine pager's pattern (D4c). **List switcher: see Decision A.** | Fixed detents give "opens at half the screen, drags to the top, never resizes on swipe" for free. Done is the standard way out of a sheet. |
| Circle / calendar switch | `SingleChoiceSegmentedButtonRow` | `Picker(.segmented)` with SF Symbols (`circle.dashed` / `calendar`) and accessibility labels | The HIG's control for two closely related views |
| Day-view preference | DataStore migration (part 10) + `DisplayPreferences` | `@AppStorage("path.dayView")`. **No migration part:** `UserDefaults` is already the iOS settings store, and its required-reason API is already declared in `PrivacyInfo.xcprivacy`. | Android's part 10 exists to leave SharedPreferences, and iOS has nothing to leave. |
| Disk cache | `noBackupFilesDir/path-wraps/…` | An `actor WrapDiskCache` under `Library/Caches/path-wraps/v{schema}/{user}/{zone}/{page}.json`. Writes are atomic (`.atomic`). Default file protection applies. Wiped in `SignedOutPurge`. | Caches is where iOS keeps re-creatable data. It is never backed up, which `CompleteDraftStore` already relies on. A purge by the system is a cache miss, nothing worse. |
| Service seam | `PathWrapServicing` + `FakePathWrapService` | The same: a `PathWrapServicing` protocol, `PathWrapService` over `SupabaseClient` (like `MomentsService`), and a fake in tests | The issue asks for it. `apps/ios/CLAUDE.md`'s "extract a protocol when a test needs it" is met, since `PathModelTests` needs it. |
| Ring layout | `RadialLayout` (math) | `RingGeometry` (pure, tested) for Canvas work, plus a SwiftUI `Layout` adopter (`RadialLayout`) where children are views | `Layout` is the native way to place views on a ring |
| Strip | `LazyRow`, centred by its initial index | A horizontal `ScrollView` with `.scrollPosition(id:anchor: .center)`, seeded so the first frame is already centred. Each item is a `Button` with `.isSelected`, and spacers are static text. `TapHaptics.play(.selection)` fires on focus. `accessibilityShowsLargeContentViewer` is on, because the strip is a fixed-height bar like a tab bar. Reduce Motion snaps. | HIG: a fixed-size bar offers the large content viewer. The selection haptic matches the Spine's stones. |
| Tone chart | Canvas | **Decision B:** Swift Charts (`BarMark` up/down, `PointMark` for even and still days) or a Canvas port | Charts brings Audio Graphs, Dynamic Type axes and RTL, but it is a new framework in the app (CLAUDE.md: a new pattern needs a discussion). |
| Hours dial, gem, garland, cairns | Canvas | `Canvas` + `GraphicsContext.drawSeal` | Same reason as Android: many marks in one view |
| Chart accessibility | a content description | The same sentence, plus `accessibilityChartDescriptor` on the gem, tone and hours, so VoiceOver offers Audio Graphs and the data table | The native, richer form of the same description |
| Targets | 48 dp | 44 pt minimum | HIG |
| Corners | `MaterialTheme.shapes` | `RoundedRectangle(cornerRadius:style: .continuous)` | Native continuous corners |
| Milestone tap | opens the pebble | `HeldCardHostModel.open`, the one way every iOS caller opens a pebble | `apps/ios/CLAUDE.md` |
| Page dots | `PageDots` (onboarding moves onto it) | **Not ported.** The sheet has none (decided on Android), and iOS onboarding keeps its own. | No iOS caller |
| Part 5 (pure move) | `SoulMark` → `core/ui`, tokens → `core/designsystem` | **Not needed.** iOS has no `core`/`features` boundary. The new atoms go in `Features/Path/Wrap/Atoms/`, and nothing existing moves. | Avoids a move that buys nothing |

**Folder layout** (all new code, one folder per concern):

```
Pebbles/Features/Path/Wrap/
  Data/      StripPayload, WeekWrapPayload, MonthWrapPayload, display models, EmotionFamily,
             PathWrapService(+ing), WrapDiskCache, WrapState
  Copy/      WrapCopy
  Atoms/     DotMark, RingGeometry, RadialLayout, SpineSectionHeader, MoreLink, TileCard,
             ProportionBar, DaySetMarks, DayHeader, MarkRow
  Sheet/     WrapSheet (detents + switcher + paging pages)
  Strip/     PathStrip, StripItemView, CairnMark, DolmenMark, CairnGeometry, PathPage, PathPages
  Week/      WeekLine, WeekTiles, WeekSheetPages
  Month/     MonthPage, MonthCover, Garland, WeeksSummary, MilestonesSection, PersonsSection,
             DomainsSection, MonthSheetPages
  Gem/       GemGeometry, GemColors, GemCanvas, EmotionGem
  Insights/  ToneFigure, HoursDial, HoursLayout
```

`StoneSeal` stays in `Spine/Stone/` and gains its sizes in place. `WeekContents` and `PersonsOfWeekView` stay where they are and are rebased onto the atoms.

---

## 3. The stack

The branches are `feat/1226-ios-*`, cut from an up-to-date `main` in one worktree. The PR titles are conventional; each PR is labelled `feat` + `ui` + `ios` + `no-lab-note`, milestone M64 (inherited from #1226, to confirm).

| # | Branch | Mirrors | One line | Est. size* |
|---|---|---|---|---|
| W1 | `feat/1226-ios-wrap-data` | 6 | Decode the three payloads, the display models, `EmotionFamily`, the service | ~1,000 |
| W2 | `feat/1226-ios-wrap-atoms` | 7 | The shared atoms and seal sizes; persons and contents rebased with identical pixels | ~900 |
| W3 | `feat/1226-ios-strip` | 8 | Cairns and dolmens replace the week roll; months become pages (placeholder) | ~1,100 |
| W4 | `feat/1226-ios-week-wrap` | 9 | The week line, tiles and the tabbed sheet; `WrapCopy` (week); session stale-while-revalidate | ~1,700 |
| W4b | `feat/1226-ios-wrap-cache` | 9b | The kept copy per user and zone: a cold start draws the last wraps at once | ~450 |
| W5 | `feat/1226-ios-month-page` | 10 + 11 | The month page and month sheet, with the day-view switch | ~1,700 |
| W6 | `feat/1226-ios-gem` | 12 | `GemGeometry`, `GemCanvas`, `EmotionGem` | ~800 |
| W7 | `feat/1226-ios-insights` | 13 | The tone figure and the 24-hour dial | ~600 |

\* Lines excluding reference PNGs. Each part builds, lints and passes its tests alone.

### W1 · data (mirrors part 6)

- **Payloads** (`Wrap/Data/`): `Decodable` structs with explicit `CodingKeys`. Every optional field uses `decodeIfPresent`, and every array defaults to empty.
- **Display models:** `Sendable`, `Equatable` values with the same names as Android, so a reviewer can read the two side by side:
  - `DaySet`, `DayWeight`
  - `PersonStat`, `FamilyStat`, `Lead`, `DomainStat`, `HourMark`
  - `Strip` / `StripItem`
  - `WeekWrap` with `WeekTile`, `WeightGrid` and `Kept`
  - `MonthWrap` with `MonthSection`, `MonthCover`, `MonthWeek`, `Gem`, `Tone` / `ToneVerdict` and `MonthHours`

  Each aggregate owns its mapping. Polarities map to the existing `ValencePolarity`.
- **`EmotionFamily` / `EmotionFamilies.of(_:)`:** families built from `EmotionPaletteService` rows (`categorySlug`). They come in the gem's wheel order, joy · pride · anger · fear · shame · sadness · peace, with unknown families last.
- **`PathWrapServicing` + `PathWrapService`:**
  - `strip(zone:)`, `weekWrap(_:zone:)` and `monthWrap(_:zone:)`.
  - `p_tz` is `TimeZone.current.identifier`, which is the zone `SpineCalendar` buckets the feed in.
  - The raw body is decoded off the main actor.
- **Tests:**
  - `WrapPayloadDecodingTests` covers all eight payloads with Android's expected values, ported.
  - `WrapToleranceTests` covers unknown tiles, sections, kinds and keys; explicit nulls; and an unknown verdict.
  - `EmotionFamiliesTests`.
- No UI and no Arkaik change.

### W2 · atoms (mirrors part 7)

- **`StoneSeal` + `SealSize`:**
  - `contents` keeps today's 20 / 24 / 27 pt.
  - `milestone`.
  - `tiny` has no glyph and keeps a fixed 2.4 pt hatch, so a small heavy stone never fills in solid.
  - `pip` is the bare silhouette.
  - `GraphicsContext.drawSeal(…)` lets one Canvas print many seals.
- **Atoms:**
  - `DotMark`: filled, ring, socket, quiet.
  - `RingGeometry` + `RadialLayout`: slots with a centred gap, `continuous(24, top: 12)`, fractional slots for the gem.
  - `SpineSectionHeader`: label, optional hint, optional trailing, `.isHeader`.
  - `MoreLink`: a `Button`, `chevron.forward`, 44 pt.
  - `TileCard` / `TileHeader`: 128 × 112 scaled with `@ScaledMetric`, solid or dashed.
  - `ProportionBar`.
  - `DaySetMarks`: row, calendar or circle; one accessibility element.
  - `DayHeader`.
  - `MarkRow`: 30 · 1fr · auto, 44 pt minimum, rule below, one element, optional button.
- **Rebased with identical pixels:**
  - `PersonsOfWeekView`: header, rows and `DayDots` move onto `SpineSectionHeader`, `MarkRow` and `DaySetMarks(.row)`.
  - `WeekContents`: header and seal size.
  - **Proof:** no existing reference PNG changes on CI.
- **Tests:** `RingGeometryTests` and `DaySetGeometryTests` (the 79 pt week row, calendar weeks, the contents seal widths).
- **References:** each atom in Paper and Night, plus ax2 and French where it holds text.

### W3 · strip (mirrors part 8)

- **`CairnGeometry`** (pure): width `9 + 4.2·min(c,3) + 2.4·(m−1)` pt, the stack capped at 42 pt. A busy week flattens its stones instead of narrowing them.
- **Marks:** `CairnMark` (focused, unfocused, live with the dotted next stone) and `DolmenMark`, both on the medium outline.
- **`PathStrip`** replaces `SpineWeekRoll` (§2 strip row):
  - cairns, dolmens, dated ground lines, and *now*
  - the focus in full ink, underlined
  - item sentences: "Week of 5 October, 4 moments"; "September, the month"
- **`PathPage`** (`.week(Date)` / `.month(CalendarMonth)`) and **`PathPages`** (a port of Android's):
  - The pages are the strip's focusable items.
  - A week the feed holds and the strip lacks joins in date order with a blank cairn.
  - The refocus rule works over pages.
  - A week link skips past a month to the nearest week.
- **`PathModel`:**
  - `path_strip` loads beside `path_moments` (`async let`). A strip failure never fails the timeline, and a failed refresh keeps the last strip.
  - `focusedWeekStart` becomes `focus: PathPage`.
  - The pager pages over `pages`.
  - The month page is a placeholder (its name and a "coming" line) until W5.
- **Deleted:** `SpineWeekRoll`. `WeekRollBuilder` stays as the feed's grouping.
- **Tests:** `PathPagesTests` (Android's 7 cases), `CairnGeometryTests`, and two strip cases in `PathModelTests` (via the fake).
- **References:** the strip on the fixture, and the cairn states plus the dolmen.
- **Arkaik:** `AC-a-finished-month-stands-in-the-strip` and `AC-path-week-roll` move to iOS `development`.

### W4 · week wrap (mirrors part 9)

- **Strings:** the week's `path.wrap.*`, `path.strip.*` and `path.week.*` keys, EN + FR, plurals included, from Android's `strings.xml` / `values-fr`. French uses `many` where Android does.
- **`WrapCopy` (week):** `line`, `soFar`, tile names and sentences, the emotions / domains / weight / hours leads, `invitation`, photos and cards.
- **`WeekLine`:** "WEEK 41  Eleven pebbles, five people."
  - The live week adds *so far*.
  - An empty week reads *Nothing yet.* with a Collect link that opens Capture.
  - Before the first load, the line shows the number alone.
- **`WeekTiles`:** a horizontal row of the tiles the server listed, in its order:
  - contents (weekday pips)
  - persons (glyphs, or the invitation)
  - emotions (bar and lead)
  - domains (top three)
  - weight (seals and counts)
  - hours (24 h track)
  - kept

  The live week's tiles are dashed and read *so far*. Each tile is one button that says its list.
- **`WrapSheet`** (§2, Decision A) with the week's pages:
  - contents is `WeekContents` (a row closes the sheet and runs the D8 go-and-press)
  - persons is `PersonsOfWeekView`, now on the server's ranked persons
  - emotions and domains use `MarkRow` + `DaySetMarks`
  - weight is the 3 × 3 sieve
  - hours and kept are lists

  The open tile is `@SceneStorage`/`@State` in the page, never in the model.
- **`WrapLookups`:** resolves moment, soul, family, emotion palette and domain from the feed, `ReferenceDataService` and `EmotionPaletteService`.
- **`WrapState` in `PathModel`:**
  - `neverLoaded | ready(payload, stale) | failed(lastGood)`, one per page.
  - The focused page and its neighbours are fetched once each.
  - A write marks the wraps stale and refetches behind them; the content is never replaced by a spinner.
  - A payload that equals the cached one is not reassigned, so Observation invalidates nothing.
- **Deleted:**
  - `WeekMasthead`, `WeekOpeningView` / `WeekOpeningText`
  - `WeekOpening`'s summary helpers
  - `SpineEmptyWeek`
  - the client ranking in `PersonsOfWeek` (the fold stays)
  - the masthead and opening rows in `SpineWeekRows`

  `SpineLayout.feedTop` drops as Android's `FEED_TOP` did (48 → 24).
- **Tests:**
  - `WrapCopyTests` + `WrapCopyFrenchTests`, Android's assertions verbatim
  - `PathModelTests`: load once with prefetch; stale and refetch after a write; a failure keeps the last good wrap and the next focus retries
  - `SpineWeekRowsTests` updated
- **References:** the line (past, live, empty), each tile body, and the sheet's pages. The Spine week references move as intended (`rebaseline-ios-screenshots`).
- **Arkaik:** `AC-a-week-opens-on-its-line-and-tiles` moves to iOS `development`.

### W4b · kept copy (mirrors part 9b)

- **`actor WrapDiskCache`** behind `WrapDiskCaching`:
  - The path is §2's: user, zone and schema version. Path parts are sanitised.
  - Writes are atomic.
  - An IO failure is logged and read as a miss.
  - Bumping `schemaVersion` drops the old tree.
- **`PathWrapService`:**
  - It keeps every body that decodes, for the signed-in user.
  - `cachedStrip()`, `cachedWeekWrap(_:)` and `cachedMonthWrap(_:)` read the bodies back.
  - A kept body this build can't decode is a miss.
- **`PathModel`:**
  - A page not yet shown this session opens on its kept copy as `ready(stale: true)` while it fetches.
  - Offline, the kept strip stands in.
- **Privacy:** `SignedOutPurge.run()` wipes the tree. That covers sign-out, account deletion and a lost session.
- **Tests:**
  - `WrapDiskCacheTests`: owner and zone isolation, a format bump, a wipe, no path escape
  - a `PathModelTests` kept-copy case
  - `SignedOutPurgeTests` extended
- **Device:** a force-quit and relaunch shows the live week's line and tiles before the network answers.

### W5 · month page (mirrors parts 10 + 11)

- **`MonthCover` + `Garland`:**
  - A Canvas over `RingGeometry(32)`: a dotted course, and each pebble printed with `drawSeal`, turned along the ring (two on one day step in and out).
  - Sockets mark empty days. The 1st, each Monday and the last day are numbered. A leaf ties the ring at the foot.
  - Inside the ring: "part the tenth", the name, the year and the souls' glyphs.
  - Below the ring: the summary, the persons in order of appearance, and the colophon in the families' inks.
- **`MonthPage`:** a `ScrollView` › `LazyVStack` over `sections`, in payload order:
  - `WeeksSummary`: a tap focuses that week
  - `MilestonesSection`: a tap goes through `HeldCardHostModel.open`
  - `PersonsSection` and `DomainsSection`: five rows + *more*
- **`MonthSheet`** on `WrapSheet`: persons · emotions · domains.
  - The emotions tab lists the fine emotions under their families, in gem order, with their shares.
  - Every list shows its days through `DaySetMarks` (a 40 pt ring), switched by `Picker(.segmented)` and remembered with `@AppStorage`.
- **Strings + `WrapCopy` (month):** French months lowercase inside a sentence, capitalised when they stand alone.
- **Spine Lab (Debug):** a Month tab on a hand-worked October fixture, as Android's.
- **Tests:** month copy (EN/FR, Android's assertions) and a `PathModelTests` month case.
- **References:** sliced per section to stay under `ImageRenderer`'s 8,192 px. The slices are the cover, weeks, milestones, persons + domains, and each sheet page in circle and calendar, in Paper, Night, French and ax2.
- **Arkaik:** `AC-a-month-opens-on-its-garland` moves to iOS `development`.

### W6 · gem (mirrors part 12)

- **`GemGeometry`:** a Swift port of `gem-method.js`'s geometry:
  - the table, 7 stars, 7 kites and 14 girdle facets, with girdle arcs in 24 eased steps
  - the light as a per-facet tone: crown 34.5°, light 38° up, azimuth −135°
  - shared edges, the rim radius and the run angle

  Display only: who owns a facet comes from `gem.facets`.
- **`GemCanvas`:**
  - each facet in its family's four-stop OKLab ramp at the facet's tone, per theme
  - uniform seams
  - the table as a paper window with the count
  - rim labels with shares, nudged apart and tied to their run by a hairline
  - the wheel rotated so `main` sits on the crown
- **`EmotionGem`:** the header, the lead line over `gem.lead` ("level" on a tie, "only"), the canvas, and *more* to the sheet's emotions tab.
- **Tests:** `GemGeometryTests` asserts the nominal areas to 1e-9 (kite 1034.8127177953763, girdle 516.2394149640708, star 325.98255305140214), 29 facets with the 28 ids, tones in range and adjacency. Gem copy is covered in EN/FR.
- **References:** the October gem in Paper and Night, joy on the crown.
- **Arkaik:** `AC-a-month-s-emotions-read-as-a-gem` moves to iOS `development`.

### W7 · insights (mirrors part 13)

- **One *insights* head** ("experimental") over whichever of tone and hours comes first.
- **`ToneFigure`** (Decision B):
  - per day, a bar up for bright and a bar down for heavy, each as tall as its summed intensities
  - a circle on the line for even
  - a quiet dot on a still day
  - the 1st, Mondays and the last day numbered
  - in a heavy month, the unit shrinks

  The sentence comes from the verdict plus the first and last polarities.
- **`HoursDial`:** a Canvas on `RingGeometry.continuous(24, top: 12)`:
  - tiny seals at the hour each pebble happened
  - dots inside at the hour it was collected
  - the wait as a spiral between them
  - the night wash from 18:00 to 06:00
  - the busiest window in the centre

  Times use `SpineStrings.dateLocale`, so the hour cycle is respected.
- **`HoursLayout`** (pure): the clockwise wait across midnight, and swarm lanes.
- **Tests:** `HoursLayoutTests` (2), plus tone and hours copy in EN/FR.
- **References:** the October insights in Paper and Night.
- **Arkaik:** `AC-a-month-shows-its-tone-and-its-hours` moves to iOS `development`.

---

## 4. How each part is built and checked

- **Setup, once:**
  - Fast-forward the main checkout's `main`.
  - Create a worktree at `.claude/worktrees/ios-wraps`. Copy `apps/ios/Config/Secrets.xcconfig` into it.
  - Create a dedicated simulator, "iPhone 17 wraps", with a private `-derivedDataPath`.
  - Run `gh stack init` from there.
- **Per part:**
  1. Write the part's task list.
  2. Implement test-first where there is logic: decoding, geometry, copy, model.
  3. Run `npm run lint --workspace=@pbbls/ios` (SwiftLint) and the full suite on the dedicated simulator. Clear the private `Build/` after any `xcodegen`.
  4. Confirm `Localizable.xcstrings` has no New or Stale row and every row has `en` and `fr`.
  5. Do one independent review of the part's diff, then fix its findings.
  6. Open a draft PR (`gh stack submit --auto`), then `rebaseline-ios-screenshots` as a label of its own when references move. Never commit a local render.
  7. Device check on the iPhone 15 for W3–W7 (a read-only display over the maintainer's live data).
- **Arkaik:** acceptances move to iOS `development` when a part starts. The App handles `releasing`, and nothing sets `live`. Never write a finding id in a PR body.
- **Decision log:** one entry at W4 if Decision A diverges from Android's tab row. It is a deliberate cross-surface divergence that a future agent could wrongly "fix".

---

## 5. Risks

- **`ImageRenderer` limits:** it blanks above 8,192 px and over `ScrollView` / `containerRelativeFrame`. The month references are sliced and drawn in eager `VStack` harnesses (the `SpineWeekStack` pattern).
- **Paging inside a sheet:** a horizontal paging `ScrollView` inside a `.medium` sheet must not steal the vertical drag that expands it. W4 starts with a device spike on the iPhone 15. The fallback is `TabView(.page)` with the index display hidden.
- **The strip's first frame:** `.scrollPosition(id:)` can report nil on the first layout (the pager's known issue). It is seeded and re-asserted after each load, as `SpinePager` does.
- **Number words beyond 60:** Android kept 0–60 and falls back to digits, despite spec §8. iOS mirrors Android, so the two apps read the same, and the gap is noted on #1226.
- **Swift Charts under `ImageRenderer`:** if Decision B picks Charts, W7's first task proves a Charts reference renders deterministically on CI.

---

## 6. Decisions (maintainer, 2026-10-24)

- **A. The list switcher in the sheet: A1.** Weather's pattern. The title is a pull-down `Menu` naming the open list, a horizontal swipe turns to the next list, and the month sheet uses the same component. Rejected:
  - A2, Android's scrolling text tabs (custom on iOS).
  - A3, a segmented control for the month and A1 for the week (two patterns for one component).

  The week sheet can hold seven lists, and the HIG caps a segmented control at about five on iPhone. W4 logs the divergence in `docs/decisions/log.md`.
- **B. The tone figure: B1, Swift Charts, "if it fits".** W7's first task proves that Charts can draw the board's figure (up and down bars, the even circle on the line, quiet still days, numbered days) and render deterministically under `ImageRenderer`. If it can't, W7 falls back to a Canvas port (B2) and says why in its PR.
- **C. PRs:** labels `feat`, `ui`, `ios` and `no-lab-note`, milestone M64.
