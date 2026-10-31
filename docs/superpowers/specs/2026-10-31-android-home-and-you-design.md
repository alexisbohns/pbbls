# Android Home and You — design

**Date:** 2026-10-31
**Issue:** #1383 (Android, the complete implementation). Siblings: #1382 iOS (redesign), #1384 web, #1385 blog post. Absorbs #1376 (Android quick actions, M64), see D6.
**Milestone:** M67 · Home & You
**Approved design:** direction "Cut Stones, carded" (c3) of the 2026-10-31 Type Bench exploration; hub https://claude.ai/artifact/7AjH6xYjeupn4SnFQEZV9u (private). The mock is the source of truth for look; iOS (`apps/ios/Pebbles/Features/Home/`, `Features/Profile/`) is the source of truth for behaviour, states, copy and server calls.
**Prior designs read:** `2026-10-26-home-tab-and-empty-states-design.md` (iOS Home, D1–D14, the server it shipped), `2026-10-26-android-month-intro-design.md` (D17, the week columns), `2026-10-30-android-icon-migration-design.md` (icon sizes), `2026-07-16-android-profile-design.md`.
**PR kind:** `feat`, user-facing: each UI part carries a Lab Note unless the maintainer covers it in Arkaik (`no-lab-note`). Arkaik: new view nodes (Home, the wrap sheet, the guide page) and a changed one (You); the build parts update the hosted map, this docs PR does not.
**Inventory:** read on branch `docs/1383-home-you-spec-android` (`origin/main` at `05cf4926`). Paths in tables are relative to `apps/android/app/src/main/kotlin/app/pbbls/android/`.

## Summary

Android has no Home. Its bar is Path · People · Collections · You (decision log 2026-09-21), while iOS has had a Home tab since #1255 and is now redesigning it (#1382). This spec builds the whole of Home on Android in the approved c3 look, and rebuilds You the same way: a fifth tab in first position; the current month as the wrap's week columns; the greeting; the quick actions as one split stone (taking over #1376); Your first five in one box with a cut-stone stepper; Monthly wraps as cards with their sheet and the unfold; About Pebbles and its guide pages. On You: the glyph avatar in a squircle pentagon, the name in the hand, Ripples on one line, the three counters as one split stone, the Glyphs list, Achievements as cards, and a Lab section with the latest announcement, a three-entry changelog and the WhatsApp callout. Underneath: a port of the iOS stone geometry, a page type set at sizes that fix "too small", and the Inclusive Sans files aligned with iOS and web. One stack of fifteen parts, plus one small back-end part that all three surfaces share.

## Goal

After the stack merges:

- the app opens on **Home**, first of five tabs, and Home shows, top to bottom, the month grid, the dateline, greeting and line, the quick actions, Your first five (until the blue pebble is claimed), Monthly wraps and About Pebbles, each doing what its iOS twin does;
- **You** shows, top to bottom, the settings button, the pentagon avatar, the name, "Member since …", "Ripples Level N", the counters, Glyphs, Achievements and Lab, as in the mock;
- every text on both pages is either Ysabeau (the editorial voice: greeting, heads, figures, dates) or Inclusive Sans (rows, labels, actions) at the sizes of D3, and none is below 12 sp;
- Android's `inclusive_sans*.ttf` are byte-identical to the files iOS and web bundle;
- a stone drawn from a given seed is the same stone on Android and iOS;
- nothing that was reachable from You before is unreachable after (D14).

## Non-goals

- iOS (#1382), web (#1384), the blog post (#1385).
- People and Collections empty states and the People prompts (iOS #1256, Android follow-up #1301). Home's "add your first soul" task opens People's create sheet, nothing more.
- Changing the global M3 type scale (D3 explains why it stays; a follow-up issue asks the global question).
- A slimmer `path_month_wrap` (D9 explains why not now).
- Redrawing any icon. The `whatsapp` icon comes from the icon package (D16).
- Notifications, widgets, an App Shortcut for the quick actions.

## What exists, what must be built

| Area | Android today | To build |
|---|---|---|
| Home tab, key, entry, start route | nothing (`PebblesKey.tabs` = Path, People, Collections, You) | `PebblesKey.Home`, fifth tab first, start route, `HomeScreen` + `HomeViewModel` (D1) |
| Greeting, dateline, line | nothing | pure `HomeGreeting` port + tests (iOS `HomeGreeting.swift`, `HomeGreetingTests`) |
| Month grid | `features/path/wrap/month/WeeksColumns.kt` (internal, needs `WrapLookups`, `WrapCopy`, intro reveal) | a public `HomeMonthGrid` in `features/path`, handed to Home as a slot (D8) |
| Month data | `core/data/PathWrapService.monthWrap(month, zone)` + `WrapDiskCache`, models in `core/model/wrap/` | reuse as is (D9) |
| Week tap → Path | Path focuses a week only from inside (`PathScreen.kt:402`) | `TabIntents` service and Path consuming `FocusWeek` (D10) |
| Quick actions | nothing; `ShortcutStones` exists on iOS only | stone port (D5), `HomeShortcuts` row/bento × stones/plain, Settings → Home (D6) |
| Camera moment | no `TakePicture`, no `FileProvider` | system camera, upload, `save_moment`, a snackbar with "Name it" (D7) |
| Quick moment sheet | nothing | `ModalBottomSheet` port of `QuickMomentSheet` (D7) |
| New pebble | `Capture` → optional "make it a pebble now" → `CompletePebble` | `Capture(chainsComplete = true)` from Home (D7) |
| First five | achievements catalog + unlocks + `check_achievements` in `AchievementsService`; no `claim_blue_pebble` | checklist state port, box, stepper, rows, claim, task sheet, illustrations (D11) |
| Monthly wraps | `MonthPage` (public, `features/path`); no `home_wraps`, no `reveal_wrap` | `HomeService`, row, card, promise card, wrap sheet (D12) |
| About Pebbles | no `guides` read; markdown renderer is `features/lab/components/LabMarkdownBody.kt` | `HomeService.guides()`, list, guide page, illustrations, renderer moved to `core/ui` (D13) |
| You header | `ProfileBanner` (`GlyphBanner` + `largeTitleHand` 41 sp + since), `LargeFlexibleTopAppBar` with back and settings | pentagon, hand name 60 sp, since, Ripples line, no top bar (D14) |
| Ripples | `core/ui/RippleBadge.kt`, fixed 44 dp, digit inside | a `size` parameter (D15) |
| Counters | `ProfileStatsCard` → `RipplesRow` + `AssiduityGrid` + `ProfileCountersRow` of `DataTile`s | one split stone of three (D14) |
| Glyphs section | `ProfileShortcutsRow` (Souls, Glyphs, Connections tiles); `PebblesKey.Glyphs` opens on Mine only | three rows, `PebblesKey.Glyphs(tab)`, counts from the server (D14, D17) |
| Achievements | `ProfileAchievementsCard` (shelf from `get_achievements`) | badge cards, "N earned" (D14) |
| Lab | `ProfileLabCard` → Lab; `LogsService` in `core/data`; `FeaturedCommunityCard` in `features/lab` | announcement card, three-entry timeline, "All updates", callout (D16) |
| Collections card, log out, connections tile | on You | leave You; log out to Settings, Connections to People (D14) |
| Type | M3 baseline sizes (`bodyLarge` 16, `bodyMedium` 14, `titleMedium` 16); `SpineTypography`; `PebblesHandTypography` | `PageTypography` + one hand rank (D3) |
| Fonts | `res/font/inclusive_sans.ttf` 112 280 B, `inclusive_sans_italic.ttf` 114 612 B | replaced by the maintainer's files (D4) |

## Decisions

**D1. Home is a fifth tab, first: Home · Path · People · Collections · You; the app opens on it.** *Mirrors iOS #1255 D1.* `PebblesKey.Home : TopLevelKey` joins `PebblesKey.tabs` first; `rememberNavigationState(startRoute = PebblesKey.Home)`; `RootScreen`'s signed-in branch roots at `Home` (`wasAlreadyAuthed = navigator.rootKey == PebblesKey.Home`), and onboarding is pushed over Home as it was over Path. Icon `home` / `home.active` (both exist in `PbblsIcons.kt`), label `tab_home` "Home" / "Accueil". The FAB stays a Path-only control. M3 allows five destinations; at 200 % font the labels already ellipsize (`PebblesNavigationSuite.kt:91–96`), which the five-tab bar keeps. This supersedes the "four-tab" half of the 2026-09-21 decision and narrows the IA divergence row of `apps/android/CLAUDE.md` (Android has a bar, iOS a floating tab bar; both now have the same five tabs). The part that lands it appends a decisions-log entry and edits that one table row, since the row would otherwise be false. *Rejected:* Home as a page above Path, or as a header in Path; neither is iOS's Home, and the bar is where Android users look for a destination.

**D2. The page ground is `surface`, colours only through M3 roles, and three surface kinds.** Home and You draw on `colorScheme.surface` (the Spine paper). **Stones** for actions and side-by-side tiles (quick actions, counters, chips, the settings button, the callout); **cards** for content you browse (month wraps, achievement badges, the announcement); **one box** for the list (Your first five). Secondary tiles are flat: no border, no shadow. Role map, from the mock's tokens:

| Mock token | Android |
|---|---|
| `--surface`, `--on-surface`, `--on-surface-variant`, `--outline`, `--outline-variant` | `colorScheme.surface`, `onSurface`, `onSurfaceVariant`, `outline`, `outlineVariant` |
| `--primary`, `--primary-container`, `--on-primary-container` | `primary`, `primaryContainer`, `onPrimaryContainer` |
| `--tertiary-container`, `--on-tertiary-container` | `tertiaryContainer`, `onTertiaryContainer` |
| `--surface-container`, `-high`, `-highest` | `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest` |
| `--stock` | `PebblesTheme.spine.colors.stock` (`surfaceContainerLowest` Paper, `surfaceContainerLow` Night) |
| `--rule` | `PebblesTheme.spine.colors.rule` |

**D3. A page type set in `core/designsystem`, M3's scale untouched.** `PageTypography` (`core/designsystem/PageTypography.kt`, read through `PebblesTheme.page`) holds every rank of the two pages, built like `SpineTypography`: Ysabeau on `ysabeauAt(weight)`, Inclusive Sans on `InclusiveSansFamily` at its 400/500/600 cuts. Sizes are the mock's, in sp, so all of them follow font scale:

| Rank | Face | Size / weight / tracking | Figures | Use |
|---|---|---|---|---|
| `dateline` | Ysabeau | 17 / 540 / 0.005em | old-style | Home's date, `primary` |
| `greeting` | Ysabeau | 40 / 270 / −0.024em, lh 1.04 | lining | Home's title (D18 for one line) |
| `lead` | Ysabeau italic | 21 / 360, lh 1.25 | | the greeting's line, `onSurfaceVariant` |
| `sectionHead` | Ysabeau | 30 / 300 / −0.018em, lh 1.05 | | every section head, sentence case, no caps |
| `sectionFigure` | Ysabeau | 30 / 300 / −0.01em | lining | "2 of 5", "3", "12 earned", `primary` |
| `figureWord` | Ysabeau italic | 19 / 380 | | the "of" / "earned" inside a figure, `onSurfaceVariant` |
| `rowTitle` | Inclusive | 17 / 600, lh 1.25 | | rows (tasks, guides, glyph rows) |
| `rowTitleDone` | Inclusive | 17 / 500 | | a done task, `onSurfaceVariant` |
| `rowLine` | Inclusive | 15 / 400, lh 1.4 | | a row's second line |
| `actionLabel` | Inclusive | 15 / 600, lh 1.15 | | quick actions, "All updates" |
| `counterFigure` | Ysabeau | 36 / 280 / −0.025em | lining | counters, `onSurface` |
| `counterLabel` | Inclusive | 15 / 400 | | counter labels |
| `wrapFigure` | Ysabeau | 34 / 280 / −0.02em | lining | the count inside a wrap ring |
| `wrapMonth` / `wrapYear` | Ysabeau / italic | 23 / 400 · 16 / 400 | · old-style | a wrap card's month and year |
| `badgeLabel` | Inclusive | 12.5 / 500, lh 1.2 | | an achievement card's label |
| `since` | Ysabeau italic | 19 / 380 | old-style | "Member since March 2026" |
| `rippleLabel` | Inclusive | 15 / 600 | | "Ripples Level 3" |
| `postKicker` | Ysabeau italic | 15 / 400 | old-style | "Lab notes · 31 October" |
| `postTitle` | Ysabeau | 26 / 340 / −0.015em, lh 1.1 | | the announcement's title |
| `postLine` | Inclusive | 15 / 400, lh 1.4 | | its line |
| `logDate` | Ysabeau | 15 / 500 | old-style | "31 Oct" |
| `logTitle` / `logLine` | Inclusive | 16 / 600 · 14 / 400 | | a changelog entry and its area |
| `calloutTitle` / `calloutLine` | Ysabeau · Inclusive | 22 / 420 · 15 / 400 | | the community callout |

Plus one hand rank, `PebblesTheme.hand.profileName`: Reenie Beanie 60 sp, −0.075em, lh 0.9. The month grid keeps the Spine's own ranks (`dayNumeral` 25/330, `dayLetter` 9.5/600 small caps), which is what the mock specifies. *Why not change the M3 scale:* `bodyLarge` 16 → 17 and `bodyMedium` 14 → 15 would move every screen and every reference in the app, which is a theme decision (#853) that this milestone did not take; and the two pages need ranks M3 has no slot for (a 40 sp light greeting, a 36 sp figure). The bench's "too small" verdict was about these pages; the follow-up issue the last part files asks whether the M3 scale should follow. `ThemeLiteralsTest` already keeps sizes out of `features/` and `core/ui`, so the set must live in `core/designsystem` anyway.

**D4. Align `res/font/inclusive_sans*.ttf` on the maintainer's files.** Read on 2026-10-31: both pairs are Inclusive Sans **2.004**, the same `wght` 300–700 axis and the same table set; `glyf`, `gvar`, `hmtx`, `cmap` are byte-identical, and the differences are in `GPOS` (−68 B, kerning/marks), `GDEF`, `GSUB` content (same size) and `name` (the Google Fonts build vs the release build). So the outlines do not change; some kerning pairs may. Replace both files with `InclusiveSans-VariableFont_wght.ttf` → `inclusive_sans.ttf` and `InclusiveSans-Italic-VariableFont_wght.ttf` → `inclusive_sans_italic.ttf`, byte-for-byte, and add `OFL.txt` beside the iOS copy only (Android credits the licence through the existing open-source notice; check it lists Inclusive Sans). The gain: iOS (which now bundles these files, #1382) and web (`next/font/local`, #1384) and Android render the same font program, which the cross-surface rule wants for anything a user compares side by side. Done in its own bottom part so its screenshot churn (every Inclusive text, by a kerning pair at most) is reviewed alone. A `FontFilesTest` pins the two SHA-1s (`7c3e44eb…`, `c4fdd08a…`) so a later Theme Builder re-export cannot silently swap them back.

**D5. Stones are a port of the iOS geometry into `core/designsystem/stone/`.** The geometry is `ShortcutStones.swift`, generalised the way the mock's `stones.js` generalises it (n pieces, `corner`, `bow`, `gutter`, `fillet`, `lean` options, single stones). Files:

- `StoneRandom.kt` — the seeded generator, bit for bit (UTF-16 murmur seed, three-step mixer, `/ 2^32`).
- `StoneGeometry.kt` — `rockOutline(w, h, random, options)`, `splitRow(size, n, seed, options)`, `splitBento(size, leadWidth, order)`, `resample`, the closed Catmull-Rom `path`; pure Kotlin over `Offset`/`Size`, memoised per (size × 4, seed, options) with the iOS cap of 24 entries.
- `StoneShape.kt` — `StoneShape(seed, corner = 34.dp, bow = 1f)`: a Compose `Shape` whose outline is the seeded rock at the measured size. Used with `background`, `clip` and the click ripple.
- `SplitStone.kt` — a layout composable `SplitStone(n, seed, fills, options) { cell -> … }`: measures the row, draws piece *k* behind cell *k*, clips cell *k*'s ripple to its piece. Heights are minimums, so a cell's text may grow it (D18).
- `SquirclePentagonShape.kt` — the mock's `pentagon()`: five corners on a 64-unit radius in a 128 × 122 box (centre y 66), each corner a cubic over 28 % of the side with handles at 4 %, each side bowed outward by 2 units.

Seeds are part of the cross-surface contract, so the same stone draws everywhere: quick actions `camera|fixed|rock|q11` (row) and `<shortcut>|fixed|q12split` (bento), as shipped on iOS; counters `days|pebbles|karma`; stepper `first-five` with `corner 3, gutter 5, fillet 2, lean 0.9`; chips `chip-0…2` (About) and `chip-g0…2` (Glyphs), corner 16; settings button `settings`, corner 16; callout `whatsapp`, corner 30; announcement band `splitRow(n = 3, corner 22, fillet 14, gutter 6)` on the iOS row seed. Tests copy the iOS test's values verbatim (`HomeShortcutsTests.swift:21–28`: the two seeds' first three draws; row pieces inside their cells ±gutter with gutters open at 359 × 84, 359 × 98, 300 × 120; bento pieces inside their cells ±0.5 with > 24 points at 359 × 162; determinism). For the options iOS does not have yet, the oracle is the iOS generalisation #1382 lands; if Android builds first, the values come from `stones.js` (the mock kit), pasted into the test with a comment naming the source, and the later iOS test must reproduce them. *Why `core/designsystem`:* pure geometry with no Pebbles domain type, and Home and You (two features) both use it.

**D6. Quick actions: #1376 folds into this stack, in `primaryContainer`.** Part 7 builds all of #1376 (row and bento, stones and plain tiles, Settings → Home with a Row/Bento choice and a "Pebble shapes" switch, the bento square going to the last-used shortcut, a selection haptic per tap) and its PR says `Resolves #1376`. One change from #1376's text, which the brief settles: the fill is `primaryContainer` and the ink `onPrimaryContainer` (iOS's local fix moves off "primary at 13 / 20 %" to the same pair), opaque roles that follow contrast levels. Labels `actionLabel` (Inclusive 15/600), max two lines; icons `camera`, `pebble-flat`, `plus-circle` at `TILE`; row height ≥ 100 dp (mock) and bento square = 45 % of the width, grown past square when the type needs it (iOS `ShortcutsBentoLayout`). Preferences: three keys in the existing DataStore through a `HomeShortcutsPreference` wrapper (`home.shortcuts.layout` = `row|bento`, default `row`; `home.shortcuts.stones` default `true`; `home.shortcuts.lastUsed` = `camera|quick|pebble`, default `camera`), the raw values iOS persists. While a camera moment saves, the camera piece is disabled at 0.6 alpha.

**D7. The three shortcut flows mirror iOS; two Android readings.**
- *New pebble* opens `PebblesKey.Capture(chainsComplete = true)`: after `save_moment`, Capture hands the id to `CompletePebble` without asking (iOS `CaptureFlowView(chainsComplete:)`). `Capture` becomes a data class with that default-false field; every other caller is unchanged.
- *Quick moment* is a `ModalBottomSheet` (iOS medium detent): name field (autofocus, clamped to Capture's name limit, trimmed), date and time defaulting to now (the stock `DatePicker`/`TimePicker` dialogs from a `WhenPill`, as Capture does), "Save & open" (filled) and "Save & close" (outlined), both disabled while the name is blank or a save runs. `save_moment {name, happened_at}` (whole seconds), then `fireCheck()`, a success haptic, dismiss; "Save & open" then navigates to `CompletePebble(id)`.
- *Camera moment* uses `ActivityResultContracts.TakePicture` into a `FileProvider` URI under `cache/camera/` (manifest `<provider>` + `res/xml/camera_paths.xml`; no `CAMERA` permission, the system camera holds it). No camera app (`resolveActivity` null or no `FEATURE_CAMERA_ANY`) → an alert "No camera yet". The shutter time is read when the result returns, floored to whole seconds. Upload through `SnapUploadCoordinator` (the photo pipeline Capture uses), then `save_moment {happened_at, snaps:[…]}` with no name; a failed upload saves nothing ("The photo didn't upload. Nothing was saved."). *Differs:* iOS's custom toast becomes a stock M3 `Snackbar` (divergence #854) with "Moment saved · 14:32" and the action "Name it" → `PebblesKey.EditMoment(id)`; duration `Long`, which the platform extends under TalkBack. No thumbnail: a snackbar has no slot for one, and the moment is a tap away.
- After any of the three: `revision` bump (the Path re-reads), checklist reload.

**D8. The month grid is the wrap's own composable, handed to Home as a slot.** `WeeksColumns` depends on eight `features/path` internals (`WeekColumn`, `WrapLookups`, `WrapCopy`, `rememberWrapCopy`, `valenceOf`, the intro reveal, `SECTION_PAD_X`, `CairnMark` usage), so moving it to `core/` is a large move for one caller. Instead, following `GlyphPickerSlot`: `core/ui/HomeSlots.kt` declares `typealias HomeMonthSlot = @Composable (wrap: MonthWrap, onWeek: (LocalDate) -> Unit, modifier: Modifier) -> Unit` and `typealias HomeWrapSlot` (D12); `features/path/wrap/month/HomeMonthGrid.kt` (public) builds the `WrapLookups` from the ambient locals and calls `WeeksColumns` with no intro host (so every reveal reads settled) and `room` chosen so a day cell is 28 dp (mock: 28 pt rows); `PebblesEntryProvider` fills the slot. The pip follows the existing rule (`min(cell × 0.5, …, 16)` = 14 dp, the mock's 15 rounded by the same rule iOS uses), sockets 6.8 dp, seals 0.85 / 1 / 1.15 by intensity, outside days blank, the cairn at the foot (dotted for an empty week): all already what `WeeksColumns` draws. Konsist holds: Home imports `core/ui`, `features/path` imports `core`, only `navigation/` sees both.

**D9. The grid reads `path_month_wrap` for the current month; no slim projection now.** `HomeViewModel` calls `PathWrapServicing.cachedMonthWrap` then `monthWrap(YearMonth.now(zone), zone)`: the disk cache paints first, and the Path tab loads the same month, so the two share one cache entry. The RPC also computes sections Home ignores; a slimmer projection is worth a back-end issue only if the device pass measures a slow first paint (recommended threshold: the network read past 600 ms p50 on the maintainer's Pixel 8). Never aggregate client-side. A failed read with no cache hides the grid (the greeting stays the page's first line); there is no error row for a decorative header.

**D10. A tap on a week column opens the Path on that week.** *Recommendation for the brief's open question:* yes, as the wrap does (iOS `HomeWrapSheet` week tap → `nav.go(.path)`; Android's deck `onWeek` focuses the week). On Home the grid is the only place a past week is one tap away, a column already carries `Role.Button` and a label ("5–11 October, 3 pebbles"), and a decorative-only grid would leave that semantics lying. Mechanism: `core/data/TabIntents.kt`, a `@Singleton` holding one pending `TabIntent` (`FocusWeek(weekStart)`, `CreateCollection`, `CreateSoul`, `CarveGlyph`) as a `StateFlow`, consumed exactly once by the target screen's ViewModel on resume (`take()`); a service owning a cross-screen notification is what `apps/android/CLAUDE.md` allows. Home calls `tabIntents.post(FocusWeek(d))` then `navigate(PebblesKey.Path)`; `PathViewModel` focuses `PathPage.Week(d)`. The same service carries the first-five CTAs (D11), iOS's `nav.go(_, intent:)`. iOS's #1382 should answer the same way on Home (flagged to the iOS spec).

**D11. Your first five: iOS's state and copy in the c3 box.** State is iOS's `HomeChecklist` (unavailable / in progress / claimable / done from the six slugs), ported pure with `HomeChecklistTests`' matrix. It shows only in progress or claimable, **before** Monthly wraps. One box: `surfaceContainer`, 26 dp corners (`PageShapes.box`), padding 24/16/10/18. Head: "Your first five" (`sectionHead`) and "2 of 5" (`sectionFigure` with "of" in `figureWord`). Under it the stepper: `SplitStone(n = 5, seed = "first-five", …)` 6 dp tall, done pieces `primary`, the rest `surfaceContainerHighest`, decorative (`clearAndSetSemantics {}`, the figure speaks). Rows without separators: a 26 dp blob mark (outline `outline` 1.5 dp; done = `primary` fill with an `onPrimary` check), title `rowTitle` (`rowTitleDone` when done), line `rowLine`, a chevron only on open tasks. A done row is not clickable and says "Done" as its state description. Claimable adds iOS's filled row "Claim the blue pebble" (`claim_blue_pebble()`; an empty result is *not granted*, "Couldn't claim it yet. Try again."); a grant plays the usual unlock overlay (`AchievementNotificationService`) and the box leaves Home for good. A tap on an open task opens the task sheet (`ModalBottomSheet`: illustration 120 dp, body, one filled CTA). CTAs: moment → the quick moment sheet; pebble → `path_moments`, the newest uncompleted moment → `CompletePebble`, else `Capture(chainsComplete = true)`; collection → `TabIntent.CreateCollection` + Collections tab; soul → `CreateSoul` + People tab; glyph → `CarveGlyph` + `Glyphs(tab = MINE)` on the You tab. Illustrations port `ChecklistIllustrations.swift` from existing `core/ui` parts (seals, `SoulMark`, `GlyphView`, `DashedPlaceholder`). Home fires `checkIgnoringFailure()` on every resume before reading the catalog and unlocks, with iOS's stale-read generation guard.

**D12. Monthly wraps: iOS's behaviour on c3 cards.** `HomeService.wraps(zone)` = `home_wraps(p_tz)`, lossy per month, newest first. Section head "Monthly wraps" + the count (`sectionFigure`), not boxed. A horizontal `LazyRow` of cards (`PebblesCard`, D19) 152 dp wide, 12 dp apart, snapping. A revealed card draws the mock's ring: one dot per day of the month on a 116 dp circle, a filled `primary` dot (r 3.6) for a day with a pebble from `cover.days`, an `outlineVariant` ring dot (r 1.9) otherwise, the count inside in `wrapFigure`; then the month (`wrapMonth`) and the year (`wrapYear`, hidden above 1.5 × font scale as iOS hides it at accessibility sizes). A folded card keeps iOS's treatment on the same card: the word "folded" over a hairline and an 18 dp turned corner. No month yet → the promise card (two cards wide, dashed, "Your first wrap folds on 30 November."). A tap opens `PebblesKey.HomeWrap(month)`, a bottom-sheet scene on phones (`BottomSheetSceneStrategy`, as the glyph detail) and a pane on large screens: folded → summary line, "Unfold" (`reveal_wrap(p_month, p_tz)`; a null `revealed_at` is a failure), three dashed placeholders; revealed → the Path's `MonthPage` through `HomeWrapSlot`, the week tap closing the sheet and focusing Path (D10), milestones inert. The ring replaces iOS's scaled garland because the mock draws it; flagged so iOS #1382 draws the same card.

**D13. About Pebbles: a plain list, guide pages pushed.** `HomeService.guides()` = `guides` where `published`, by `sort_order`, lossy, FR falling back to EN field by field. Head "About Pebbles", no box. Rows: a 48 × 46 dp stone chip (`surfaceContainer`, seed `chip-<index>`, corner 16) holding a `TILE` icon in `primary`, title, line, chevron; 10 dp vertical padding; no separators. The chip icon comes from the guide's `illustration` enum: pebbles `pebble-round`, stones `stones-aligned-bottom`, emotions `stones-track`, valence `scale`, domains `abstract-landscape`, collections `collection`, souls `people`, glyphs `pebble-dotted-glyph`, karma `karma-dot`, none `pebble-round` (the mock fixes three; the rest are proposed and must match iOS). A tap pushes `PebblesKey.Guide(slug)`: large title, italic summary, the 120 dp illustration (port of `GuideIllustrationView` page size), the markdown body. The body renders through `LabMarkdownBody`, moved as-is to `core/ui/MarkdownBody.kt` in its own part (move separate from rewrite). Unknown slug → "This reading isn't available".

**D14. You is rebuilt top to bottom; nothing it reached is orphaned.** No top app bar (iOS hides the bar on both pages). In order:
1. The settings button, top end: a 46 × 44 dp `surfaceContainerHigh` stone (seed `settings`), `slider` at `TOOLBAR`, 48 dp target, label "Settings".
2. The avatar: `SquirclePentagonShape` 128 × 122 dp in `primaryContainer`, the profile glyph inside (`GlyphView`, 58 dp, `onPrimaryContainer`), `pebble-dotted-glyph` when there is none.
3. The name in `hand.profileName`, one line (D18), heading semantics; "Member since March 2026" in `since`; then "Ripples Level 3" (`rippleLabel`) beside the Ripples mark (D15). No subline: the progress line and the assiduity grid leave.
4. The counters: `SplitStone(n = 3, seed = "days|pebbles|karma")` in `surfaceContainer`, 88 dp min height; each piece the figure (`counterFigure`, `onSurface`, grouped with the locale's separator) over icon (`INLINE`, `primary`) + label on one line; content vertically centred (no empty room at the top). From `PathStatsService` (Days = `days_practiced`, Pebbles, Karma). Not clickable. No "Stats" title.
5. Glyphs (head, no box): three chip rows: My glyphs ("The 12 marks you carved.", `draw-dotted`), Owned glyphs ("The 5 you collected from others.", `pouch`), Community ("Glyphs other pebblers share.", `petroglyph`), each opening `PebblesKey.Glyphs(tab)` on its tab (D17). Plurals for the counts; while counts load, the lines read without a number ("The marks you carved.").
6. Achievements: head + "12 earned" (`sectionFigure`, "earned" in `figureWord`); a four-column grid of badge cards (`PebblesCard`, 10 dp gaps), each the family icon (`achievementFamilyIcon`, `TILE`, `primary`) over its label (`badgeLabel`); a tap opens Achievements. Data: the existing shelf (`ProfileShelf`, `get_achievements`, 15-minute freshness).
7. Lab (D16).

What leaves You and where it goes: **Souls** and **Collections** tiles/card (their tabs exist); **Connections** gets a top-bar action on the People tab (`SoulsListScreen`, `people` icon, label "Circle"), which is where iOS has it (`SoulsListView.swift:40–46`) and the Android drift this fixes; **Log out** becomes the last row of Settings (above "Sign out everywhere"), flagged so iOS and web place it the same way; the **assiduity grid** and the ripples progress line are dropped by the design. These moves land in a part *before* the You rebuild, so no build of the stack orphans a screen.

**D15. Ripples: the existing `RippleBadge`, given a size.** `RippleBadge(level, activeToday, modifier, size: Dp = 44.dp, showsLevel: Boolean = true)`: the rings already scale from their 44-unit viewBox; below 32 dp the digit cannot be read, so the You line passes `size = 24.dp, showsLevel = false` (the label beside it says the level). Its existing a11y label ("Ripples level 3, active today") becomes the whole line's, the text node cleared. Defaults keep every current call site and `RippleBadgeScreenshots` unchanged. Not the mock's `wave` icon, which was a stand-in.

**D16. Lab on You: an announcement card, three entries, "All updates", the callout.** Data through `LogsServicing` (`core/data`): `announcements(limit = 1)` and `changelog(limit = 3)` (species `feature`, released), in parallel, each failing alone. (a) The latest announcement as a `PebblesCard` with `clip`: a cover band (the log's cover image through `coverImageUrl`, Coil, 108 dp tall, cropped; with no cover, `splitRow(n = 3)` pieces in `primaryContainer` on a band of `primaryContainer` 35 % over stock, as the mock), then "Lab notes · 31 October" (`postKicker`), the title (`postTitle`), one line (`postLine`, max 2 lines); a tap opens `LabAnnouncement(logId)`. None published → no card. (b) The three entries as a dated timeline: date column 46 dp right-aligned (`logDate`, short day-month in the locale), a 9 dp dot (`primary` on the newest, `surface` ring in `outline` for the others) on a 1 dp `rule` line, title (`logTitle`) and area (`logLine`). Not clickable rows. (c) "All updates": an outlined pill (`outlineVariant` border, `primary` label in `actionLabel`, chevron), 48 dp target, opening **the Lab screen** (`PebblesKey.Lab`), not the changelog list, because the Lab screen is the only way to the roadmap, the backlog and reactions once `ProfileLabCard` goes. (d) The community callout: a `tertiaryContainer` stone (seed `whatsapp`, corner 30), `whatsapp` icon (32 dp, `onTertiaryContainer`), "Join the community" (`calloutTitle`), "Talk with other pebblers on WhatsApp." (`calloutLine`), chevron; `ACTION_VIEW` on `LabConfig.WHATSAPP_INVITE_URL`. It is new code in `features/profile` (`FeaturedCommunityCard` is Lab's and stays there). The `whatsapp` icon is not in the package yet (`feat/icon-whatsapp` has no commit); the part that draws the callout waits for it (R4).

**D17. Glyph counts come from `get_profile_engagement`; Glyphs opens on a tab.** You needs "12 carved" and "5 owned" on all three surfaces, so the numbers are a server projection, not two client counts (brain in the back end; the brief points at this RPC). One back-end part, shared by #1382/#1383/#1384 and filed as its own issue: `get_profile_engagement(p_tz)` gains `glyphs_carved int` (rows in `glyphs` with `user_id = auth.uid()`) and `glyphs_owned int` (rows in `glyph_entitlements` for the caller). The return type changes, so the migration drops and recreates the function (re-granting `execute` to `authenticated`), and a new harness `packages/supabase/scripts/verify-profile-engagement.ts` (no harness covers the RPC today; model it on `verify-home.ts`) seeds a carved glyph and an entitlement for a throwaway user and asserts both counts plus the two existing columns. Nothing else depends on the function (`get_public_profile` recomputes engagement itself). `ProfileEngagement` decodes both as optional (an older server still parses), and `PathStatsService` exposes them. `PebblesKey.Glyphs` becomes `data class Glyphs(val tab: GlyphShelf = GlyphShelf.MINE)` with `GlyphShelf` in `core/model` (a navigation key may not name a `features/` enum), and `GlyphsListViewModel.start(tab)` selects it; the store's labels stay Mine / Owned / Commu. *Rejected:* client `count=exact` head reads: single-table and legal under `AGENTS.md`, but three surfaces would each define "carved" (are glyphs made for souls counted?), which is exactly how numbers drift.

**D18. Accessibility.**
- *Font scale.* Every size is sp. The greeting is one line up to 1.3 ×: `BasicText` with `TextAutoSize.StepBased(min = 28.sp, max = greeting.fontSize)` and `maxLines = 1` (the app already uses `TextAutoSize`, `YearSheet.kt:157`); above 1.3 × it wraps to two lines at full size, because shrinking a 200 % greeting back to 28 sp would undo the user's setting. A test renders "Bonsoir, Alexandrine" at 360 dp and 1 × and asserts one line. The profile name gets the same rule (min 40 sp). Stones and boxes are drawn behind measured content, so text grows them; split stones keep their pieces side by side up to 1.5 × and stack the three counters and the three quick actions vertically above (`LocalDensity.current.fontScale > 1.5`), as iOS's bento grows past square. The month grid's column dates keep the existing font-scale cap of 1 (`WeeksColumns`), the mock's 9.5 sp caps included, since a column cannot widen; the grid's information is in each column's spoken label.
- *TalkBack, the month grid.* The weekday legend is cleared; each column is one button: "5 to 11 October, 3 pebbles, 2 people" (the existing `WrapCopy` range and line), double-tap opens the Path on that week (D10). Columns read left to right; the grid as a whole has no extra label. Pebbles inside a day are not separate nodes (iOS parity).
- *Other nodes.* Section heads carry `heading()`. A task row merges into one button with its state ("Done") and no chevron node. The stepper and the wrap ring are decorative; the figure and the card label speak ("October 2026, 23 pebbles" / "…, folded", plurals). Counters read "112 days", one node each. The Ripples line is one node (D15). The callout is one link-role button. Every target ≥ 48 dp (`minimumInteractiveComponentSize` on the pill and the settings stone).
- *Contrast.* All ink is on-role (`onPrimaryContainer` on `primaryContainer`, `onTertiaryContainer` on `tertiaryContainer`, `onSurfaceVariant` for second lines), so the high-contrast schemes carry it.
- *Motion.* No new animation beyond stock sheets and ripples; the grid draws without its intro.

**D19. The card is one `core/designsystem` component.** `PebblesCard(modifier, onClick?, content)`: `stock` fill, `PageShapes.card` 18 dp corners; on Paper a soft shadow (`Modifier.dropShadow` with the mock's two layers: 0/1/2 dp at 5 %, 0/8/22 dp at 7 % black); on Night a 1 dp `rule` border and a 0/8/22 dp shadow at 35 %. `PageShapes` holds `card = 18.dp`, `box = 26.dp` so no feature writes a radius (`ThemeLiteralsTest`). Wrap cards, badge cards and the announcement use it; nothing else in the app changes.

**D20. French copy is iOS's.** Every Home string is ported with its iOS key's EN and FR values from `Localizable.xcstrings` (greetings, dateline format `EEEE d MMMM`, shortcuts, quick moment, camera, toast, first five, tasks and their bodies and samples, wraps, guides). New strings with no iOS key yet (the You lines, "All updates", "Lab notes", the callout, the glyph rows, "Log out" in Settings, "Circle") take the mock's EN and these FR, to be matched by #1382: "Mes glyphes" / "Les %d marques que tu as gravées." · "Glyphes acquis" / "Les %d que tu as reçus d'autres." · "Communauté" / "Les glyphes que partagent d'autres pebblers." · "Toutes les nouveautés" · "Notes du Lab" · "Rejoins la communauté" / "Parle avec d'autres pebblers sur WhatsApp." · "%d obtenus" · "Cercle". French plurals use `one`/`many`/`other` (the Spine stack's lesson). `LocalizationParityTest` guards parity; strings that leave (`profile_stats_header`, `profile_ripples_progress`, `profile_ripples_max`, `profile_ripples_loading`, `profile_assiduity_a11y`, `profile_collections_*`, `profile_log_out`, the Lab card's) are deleted in the part that orphans them, or Lint's `UnusedResources` fails it.

**D21. Screenshots: new references per part, recorded on CI only.** New classes: `StoneScreenshots` (the generator's shapes, Paper/Night), `PageKitScreenshots`, `HomeScreenshots` (Paper, Night, French, 2 × font, wide; content states: first-run with the box, mid-month, claimable, no wraps, no grid), `HomeWrapScreenshots`, `GuideScreenshots`; re-recorded: `NavigationSuiteScreenshots` (8, five tabs), `ProfileScreenshots` (5, rebuilt), `SettingsScreenshots` (Home section, log out), `ListDetailScreenshots` (rail), `LabScreenshots` untouched. Each content layer (`HomeContent`, `ProfileContent`) is stateless over its `UiState`, so a preview needs no service. Re-baseline by the `rebaseline-screenshots` label after the part's last commit, one part at a time bottom-up, approve the held runs, never re-run the failed run, never commit a local render, never widen the threshold (`android-screenshot-gate` skill). Part 1's font swap re-records most references in the app; it is labelled alone and reviewed by scanning for anything other than a sub-pixel kerning shift.

**D22. Lint baseline.** `app/lint-baseline.xml` has one entry on a deleted file: `NonObservableLocale` in `features/profile/components/ProfileBanner.kt`; deleting the file drops it, which is fine (a baseline entry with no file is ignored, then cleaned by `updateLintBaseline` in that part, diffed by issue id). `features/lab/components/LabMarkdownBody.kt` carries none, so its move un-filters nothing; check anyway by grepping the baseline for its path before the move. New code must not add a `NonObservableLocale`: dates (dateline, since, log dates, wrap months) format through the app locale read in composition (`LocalConfiguration.current.locales[0]`), the pattern the Spine uses. No part should need a new baseline entry.

**D23. Konsist boundaries.** New `features/home/` (Home, its ViewModel, checklist, wraps, guides, shortcuts, flows). It imports `core/*` only: stones and page kit from `core/designsystem`; `HomeService`, `TabIntents`, `PathWrapService`, `AchievementsService`, `MomentWriteService`, `SnapUploadCoordinator` from `core/data`; slots, seals, `SoulMark`, `GlyphView`, `MarkdownBody`, `AchievementCopy` from `core/ui`. The month grid and the month page arrive as slots from `features/path` (D8); Capture and Complete as keys. `features/profile` imports nothing from `features/lab` or `features/glyph` (keys only). `HomeShortcutsPreference` lives in `core/data` because Settings (in `features/profile`) writes it and Home reads it. `ArchitectureBoundaryTest` is the gate; no exception is added.

## Data

| Read / write | Where | Server |
|---|---|---|
| display name | `HomeService.displayName()` | `profiles.select("display_name").single()` |
| month grid | `PathWrapService.monthWrap` | `path_month_wrap(p_month, p_tz)` |
| wraps, unfold | `HomeService.wraps`, `.reveal` | `home_wraps(p_tz)`, `reveal_wrap(p_month, p_tz)` |
| guides | `HomeService.guides` | `guides` (published, by `sort_order`) |
| checklist | `AchievementsService.checkIgnoringFailure`, `loadCatalog`, new `loadUnlocks`, new `claimBluePebble` | `check_achievements`, `achievements`, `achievement_unlocks`, `claim_blue_pebble()` |
| first pebble task | `MomentReadService` | `path_moments` (newest uncompleted) |
| quick / camera moment | `MomentWriteService.saveMoment` | `save_moment {payload}` |
| You profile, glyph strokes | `ProfileService` | `profiles`, `glyphs` (as today) |
| counters, ripples, glyph counts | `PathStatsService` | `v_karma_summary`, `v_ripple`, `get_profile_engagement(p_tz)` (+ D17 columns) |
| achievements shelf | `AchievementsService.page` | `get_achievements(p_tz)` |
| Lab | `LogsService.announcements(1)`, `.changelog(3)` | `v_logs_with_counts` |

`HomeService` is new (`core/data/HomeService.kt`, `@Singleton`, a `HomeServicing` seam because `HomeViewModelTest` needs a fake). Decoding follows the root rule for shared shapes: the tests decode the server's own payload (`packages/supabase/scripts/oracles/home/payloads/home-wraps.json`, dumped by `verify-home.ts`, added to the test resources in `app/build.gradle.kts` as the path-wrap and achievements oracles already are) and the payloads iOS tests decode (`PebblesTests/Features/Home/HomePayloads.swift`, `HomeWrapsDecodingTests`) verbatim, including a `revealed_at` with fractional seconds and with none, an explicit `null`, and a malformed month that must be dropped. Writes send `happened_at` at whole seconds.

## Parts (one stack, `feat/1383-…-android`)

Branch names carry `-android` so Vercel skips them. Each part passes `npm run lint --workspace=@pbbls/android` and `./gradlew lint testDebugUnitTest assembleDebug :app:compileDebugScreenshotTestKotlin` alone, then its re-baselined references.

| # | Branch | Content | Depends on |
|---|---|---|---|
| B | `feat/<issue>-glyph-counts` (back-end, own issue, not in this stack) | D17's two columns on `get_profile_engagement`, harness, `database.ts` | — |
| 1 | `feat/1383-inclusive-sans-android` | D4 font files + `FontFilesTest` | — |
| 2 | `feat/1383-stones-android` | D5 geometry, shapes, pentagon, tests, gallery previews | — |
| 3 | `feat/1383-page-kit-android` | D3 `PageTypography`, hand rank, D19 `PageShapes` + `PebblesCard`, `SectionHead`, `ChipRow`, D15 `RippleBadge(size)` | 2 |
| 4 | `feat/1383-home-data-android` | `HomeService`, `AchievementsService.loadUnlocks/claimBluePebble`, `TabIntents`, `HomeShortcutsPreference`, models, decoding tests | — |
| 5 | `feat/1383-home-tab-android` | D1 key, bar, start route, `HomeScreen` shell, greeting + dateline, decisions log, CLAUDE.md row | 3, 4 |
| 6 | `feat/1383-home-month-android` | D8 slot + `HomeMonthGrid`, D9 read, D10 Path focus | 5 |
| 7 | `feat/1383-quick-actions-android` (`Resolves #1376`) | D6 shortcuts and Settings → Home | 5 |
| 8 | `feat/1383-shortcut-flows-android` | D7 new pebble chain, quick moment sheet, camera moment, snackbar | 7 |
| 9 | `feat/1383-first-five-android` | D11 | 8 |
| 10 | `feat/1383-home-wraps-android` | D12 | 6 |
| 11 | `feat/1383-markdown-move-android` | `LabMarkdownBody` → `core/ui/MarkdownBody` (pure move) | — |
| 12 | `feat/1383-guides-android` | D13 | 10, 11 |
| 13 | `feat/1383-you-prep-android` | Connections on People, Log out in Settings, `Glyphs(tab)` | — |
| 14 | `feat/1383-you-header-android` | D14 items 1–5 | 3, 13, B |
| 15 | `feat/1383-you-sections-android` (`Resolves #1383`) | D14 item 6, D16, deletions, decisions log, follow-up issue | 14, the `whatsapp` icon |

The stack is ordered as listed (parts with no dependency sit where their story puts them). Parts 13–15 depend only on 1–3 and B: if Home's review lags, they can be split into a second stack on top of Part 3 without changing their content. The detailed tasks are in `docs/superpowers/plans/2026-10-31-android-home-and-you.md`.

## How this lines up with #1376

#1376 is the Android half of #1373 and predates the c3 design. Part 7 is #1376 in full, with the c3 colour (D6), and closes it; nothing of #1376 is built separately. If #1376 is picked up before this stack starts, build it as Part 7's content on its own branch and drop Part 7 here; the stones port (Part 2) is then its first commit and moves down with it.

## Testing

- **Pure JVM:** `StoneRandomTest`, `StoneGeometryTest` (iOS vectors, D5), `SquirclePentagonShapeTest` (five corners inside the box, symmetric about the vertical axis), `HomeGreetingTest` (iOS `HomeGreetingTests` cases: bands at 11:59/12:00/17:59/18:00, Monday morning, Sunday evening, blank name), `HomeChecklistTest` (iOS matrix), `HomeShortcutsTest` (bento order, persisted raw values), `HomeWrapsDecodingTest` + `GuidesDecodingTest` (iOS payloads verbatim), `TabIntentsTest` (post, take once), `FontFilesTest` (SHA-1s), `PebblesKeysTest` and `PebblesNavigationIconTest` updated for five tabs.
- **ViewModels:** `HomeViewModelTest` (four reads in parallel, each failing alone, stale checklist read dropped, claim granted / empty / thrown, reload after a save), `ProfileViewModelTest` updated, `QuickMomentViewModelTest`, `CameraMomentTest` (upload failure saves nothing).
- **Robolectric journeys:** `HomeJourneyTest` (launch lands on Home; a week column opens Path on that week; "Add your first soul" opens People's create sheet; quick moment "Save & open" lands on Complete), `RootGateTest` and `NavigatorRootAtTest` re-pointed at Home, `YouJourneyTest` (Glyphs rows open the right tab; Circle reachable from People; Log out from Settings signs out).
- **Architecture:** `ArchitectureBoundaryTest`, `ThemeLiteralsTest`, `LocalizationParityTest` stay green unchanged.
- **Screenshots:** D21.
- **Back-end:** the harness of Part B against the linked project, `npm run db:verify --workspace=packages/supabase`.

## Device pass (the maintainer's Pixel 8)

Per merged part, on the Play internal build; the account is real, so write only ordinary moments. Eyeball: the five-tab bar and the cold launch on Home; the grid against the Path's month; a week tap; the three quick-action styles in Paper, Night and wallpaper colour; a camera moment and "Name it"; a quick moment both ways; the first five with a done task and the claim on a test account; a wrap unfold on a test account; a guide page; You in both schemes; the pentagon at 2 × density; "Bonsoir, Alexandrine" in French; 200 % font on both pages; TalkBack across the grid, the box and the counters.

## Risks

- **R1. Five tabs crowd the bar at 200 % font in French** ("Collections", "Accueil"). The labels already ellipsize; the fallback, if they read badly on device, is M3's `NavigationSuiteType` labels-on-selected-only, recorded as a decisions-log supersede, not a fourth-tab cut.
- **R2. Stone parity rests on a generator ported three times.** Mitigated by pinned draws and outline points from one oracle (D5). A shared fixture file all three test suites read would be stronger; worth an issue once the three ports exist.
- **R3. Part 1 re-records most references.** Kerning-only by construction (identical outlines); anything else in the bot's PNGs is a stop.
- **R4. The `whatsapp` icon is not merged.** Part 15 is last for this reason. If the icon is late and the rest is ready, Part 15 ships with `people` in the callout and a one-line follow-up, never a hand-traced WhatsApp mark.
- **R5. Part B is shared by three surfaces.** Whichever surface reaches its You header first lands it; the other two consume it. Until it lands, Part 14's glyph lines render without a number (D14.5), which is correct copy, so Part 14 is not blocked from review, only from merge.
- **R6. A sheet over a deck.** The wrap sheet hosts a horizontal pager inside a vertically draggable sheet; `BottomSheetSceneStrategy` already serves the glyph detail, but a deck is denser. If the drag fights the pager on device, the wrap opens as a pushed page (a `BarKey`), a one-line change in the scene strategy.
