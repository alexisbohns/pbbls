# The Path header, the week wrap and the month page — server aggregates, Android renders

**Date:** 2026-10-23 · **Surfaces:** supabase (the brain), android (first renderer), with iOS and web to follow on the same RPCs · **Status:** draft for review

The redesign was explored over five rounds of hosted boards and approved section by section by the maintainer. Three platforms will show the same consolidated numbers: rankings, thresholds, milestones, gem allocation, tone, hours. So this spec puts **all data logic in Postgres**, behind three read RPCs, and keeps the clients to display and component logic.

On the client side, components are **factored once and composed everywhere**: one stone, one way to draw a set of days, one ring geometry, one row, one section header, one sheet. The week and the month are two compositions of the same parts.

Design sources (private artifact, maintainer-approved): https://claude.ai/artifact/J9LhDgHTB1SNQSQ2xWfXqC
- the **week board** (approved as built)
- the **month board** (rows 1–11), including the faithful gem with uniform seams, and the insights (tone bars and the 24-hour dial)
- the **gem method**: `METHOD.md` and `gem-method.js`. The JS file becomes the harness's oracle (D14).

---

## 1. The split: brain in the back-end, rendering in the front-end

| Lives in Postgres (computed once, served to all three clients) | Lives in the client (per platform) |
|---|---|
| Week and month bucketing in the reader's timezone | Decoding the payload into display models (a pure mapping) |
| Counts, worths (intensity × charge), shares, rankings, day sets | Geometry: ring layout, calendar grid, facet polygons, label placement |
| Thresholds: which tiles and which sections exist | Colour: palette lookup, light tones on facets, theme roles |
| Milestone picking, gem allocation and rotation | Copy: mapping semantic enums and numbers to localised sentences (plurals, number words) |
| The strip: weeks, empty spacers, month placement, cairn day weights | Interaction: focus, sheet open/page, the circle/calendar switch, motion |
| Tone per day and its verdict; hours, waits, the busiest window | Accessibility descriptions assembled from the payload |
| "New since last month", the month's first and last valence | Caching a payload per page and invalidating it after a write |

**Test of the rule:** if two platforms could disagree about a number, a membership or an order, that computation belongs on the server. If they can only disagree about pixels or wording, it belongs on the client.

Copy is the one borderline case. The server returns **semantic values**, never sentences: `verdict: "bright_two_to_one"`, `lead: {order: [...], zero: [...]}`. Each client turns them into localised words. Localisation is display; deciding which sentence applies is data.

---

## 2. Decisions

| # | Decision | Why |
|---|---|---|
| D1 | **The strip replaces the week roll.** Each week is a cairn: one stone per day that holds a moment (max 7), widest at the base, sized from the day's count and strongest intensity, in grey ink with the focused item in full ink. A month is a **dolmen**, placed after the week that holds the month's last day. Empty past weeks are dated ground lines. The current week reads *now*, with a dotted outline for the next stone. | Round 2 pick. One week number on the fold. |
| D2 | **Focusable strip items and pager pages are 1:1.** Focusable items: weeks with moments, the current week, and completed months with at least one pebble. Empty past weeks are non-focusable spacers. | Keeps the pager contract (#1122) and adds the month as a page. |
| D3 | **The week opening becomes one line:** `WEEK 41  Eleven pebbles, five people.` The masthead, kicker, title in words and stacked summary are deleted. | Round 1 brief. |
| D4 | **The week wrap is a row of tiles.** A tap opens one half sheet, and inside it the reader swipes between the tiles' lists. The contents and persons pages are today's `WeekContents` and `PersonsOfWeek` composables, rebased; the persons page now reads the server's persons. | Maintainer: reuse the existing lists. |
| D5 | **The thresholds are server logic.** The week payload lists the tiles that exist; the month payload lists the sections that exist. Clients never test a count. The rules are in §5. | Same answer on every platform. |
| D6 | **The month page:** cover → weeks summary → five milestones → five persons + *more* → emotion gem + *more* → five domains + *more* → insights (tone, hours). Each *more* opens the month sheet (persons · emotions · domains). | Maintainer's order (round 3). |
| D7 | **The cover:** a leaf with a garland of the month's days on a dotted course. A day with pebbles shows a seal; an empty day shows a dashed socket. The souls' glyphs sit inside the ring, then come the summary, the persons line and the colophon. | Rounds 2–3. |
| D8 | **Milestones:** large stones first (bright → even → heavy, then by date), then medium stones if needed, never small, and no section without a large or medium stone. Picked on the server. | Maintainer's rule. |
| D9 | **Every dot view of days offers a circle (default) and a calendar,** switched once for the month sheet and remembered. The week's seven dots are the same component with a fixed mode. | Round 3. |
| D10 | **The emotion gem:** a fixed faithful brilliant with uniform seams. Its colours come from the allocation computed on the server (D14). Worth is intensity × charge (bright 1, heavy 1, even ½). Families share 28 facets in proportion, in the clockwise order joy · pride · anger · fear · shame · sadness · peaceful. The wheel rotates so the main family sits on the crown. | Rounds 4–5. |
| D11 | **The tone:** for each day, a bar up for bright, a bar down for heavy and a circle on the line for even, all scaled by intensity. The server sends the per-day values and a verdict. | Round 4. |
| D12 | **The hours:** a 24-hour dial with noon on top: the stone at `happened_at`, a dot at `created_at`, a wait line between them, and the busiest four-hour window in the centre. The server sends local hours and the window. | Round 4. |
| D13 | **Three read RPCs:** `path_strip(p_tz)`, `path_week_wrap(p_week_start, p_tz)` and `path_month_wrap(p_month, p_tz)`. All are `security invoker` and scoped to `auth.uid()`. `p_tz` is the device's IANA zone; no timezone is stored on the profile today, and clients already bucket days in the device zone. `path_moments` stays the feed and is unchanged. | One aggregation for three clients. The feed and the wraps load independently. |
| D14 | **The gem allocation is a SQL function,** `gem_allocate(p_worths jsonb) returns jsonb`: a plpgsql port of `gem-method.js`, called by `path_month_wrap`. The JS reference and `METHOD.md` are committed as the **oracle** under `packages/supabase/scripts/oracles/path-wrap/`. The harness computes the expected allocation with the oracle and compares it facet by facet. Rejected alternative: an edge function running the JS directly. It needs no port, but adds a second network hop and a cold start to a page read, and the house pattern for reads is a single RPC. | One implementation serves every client; the oracle keeps the port honest. |
| D15 | **The dot-view preference triggers the DataStore migration** that `apps/android/CLAUDE.md` defers to "the next preference". `OnboardingPreferences` and `AppearancePreferences` move with it, in their own part. | The written trigger fires. |

**Out of scope** (open questions in §11):
- the month's own path and its running head
- the year tray
- the web history table (board 03, shelved)
- the iOS and web renderers, which reuse these RPCs

---

## 3. Server: the RPCs

All three RPCs:
- **Ownership:** read only the caller's rows (`auth.uid()`), counting only completed pebbles where a pebble is needed. Uncompleted moments count toward dates and totals, and carry no family.
- **Time:** bucket days and weeks with `happened_at at time zone p_tz` (ISO weeks, Monday start). Local hours are decimal hours in `p_tz`. An invalid zone raises `invalid_tz`, and clients map it to an error state.
- **Identifiers:** return ids, not names, wherever the client already holds the entity. Moment titles come from `path_moments`, souls and domains from the reference data, and emotions and families from `v_emotions_with_palette`. Payloads stay small and never duplicate the feed.
- **Format:** keys are explicit `snake_case`; arrays are ordered as they should render; dates are ISO `YYYY-MM-DD`; and **days are 0-based indices into the span** (week 0..6, month 0..n−1).

### 3.1 `path_strip(p_tz text) returns jsonb`
```json
{ "items": [
  { "kind": "week", "week_start": "2026-08-31", "iso_week": 36, "iso_year": 2026,
    "count": 0, "focusable": false, "live": false, "days": [[0,0],[0,0],[0,0],[0,0],[0,0],[0,0],[0,0]] },
  { "kind": "month", "month": "2026-08-01", "count": 15, "focusable": true },
  { "kind": "week", "week_start": "2026-11-02", "iso_week": 45, "iso_year": 2026,
    "count": 3, "focusable": true, "live": true, "days": [[1,2],[1,1],[1,2],[0,0],[0,0],[0,0],[0,0]] }
]}
```
- **Weeks:** every week from the reader's first moment to the current week, oldest first. Each `days` entry is `[count, max_intensity]`.
- **Months:** each month item sits right after the week that holds its last day. Only completed months with at least one pebble are included.

### 3.2 `path_week_wrap(p_week_start date, p_tz text) returns jsonb`
```json
{ "week_start": "2026-10-05", "iso_week": 41, "live": false, "count": 11, "people_count": 5,
  "tiles": ["contents","persons","emotions","domains","weight","hours","kept"],
  "persons":  [{ "soul_id": "…", "count": 4, "days": [0,1,3,5] }],
  "invite_moment_ids": ["…","…","…"],
  "families": [{ "slug": "joy", "count": 3, "worth": 7, "share": 0.33, "days": [0,3,5] }],
  "domains":  [{ "domain_id": "…", "count": 4, "days": [0,1,6] }],
  "weight":   { "high": [0,2,3], "neutral": [2,1,0], "low": [1,2,0] },
  "hours":    [{ "moment_id": "…", "happened": 10.25, "collected": 21.4 }],
  "kept":     { "photo_moment_ids": ["…"], "cards": [{ "moment_id": "…", "count": 2 }] },
  "lead":     { "order": ["joy","peace","pride","anger","sadness","fear"], "zero": ["shame"] } }
```
- **`tiles`** is the threshold's verdict, in render order; the live week stops after persons. Absent stats are omitted, not nulled.
- **`invite_moment_ids`** holds the week's first three moments, for the "was someone there for…" invitation. It is present only when `persons` is empty.
- **`weight`** is indexed by intensity 1..3.

### 3.3 `path_month_wrap(p_month date, p_tz text) returns jsonb`
```json
{ "month": "2026-10-01", "days_in_month": 31, "first_weekday": 3, "count": 24, "people_count": 6,
  "sections": ["weeks","milestones","persons","gem","domains","tone","hours"],
  "cover":   { "days": [{ "day": 0, "pebbles": [{ "moment_id": "…", "emotion_id": "…", "intensity": 3, "polarity": -1 }] }],
               "souls_in_order": ["…"], "inks": ["joy","peace","pride","anger","sadness","fear"], "papers": ["…","…","…"] },
  "weeks":   [{ "week_start": "2026-09-28", "from": 0, "to": 3, "count": 1, "soul_ids": [], "days": [[0,0],[0,0],[0,0],[1,3],[0,0],[0,0],[0,0]] }],
  "milestones": ["…","…","…","…","…"],
  "persons": [{ "soul_id": "…", "count": 5, "days": [4,5,9,30], "new_since_previous": false }],
  "previous_month": { "count": 15, "soul_ids": ["…","…","…"] },
  "gem":     { "worths": { "joy": 14, "peace": 8 }, "shares": { "joy": 0.341 }, "main": "joy", "rotation": 0,
               "facets": { "kite_0": "joy", "star_3": null }, "lead": { "order": ["joy","peace"], "zero": ["shame"] } },
  "fine_emotions": [{ "family": "joy", "emotion_id": "…", "count": 2, "days": [4,18] }],
  "domains": [{ "domain_id": "…", "count": 7, "days": [0,4,5] }],
  "tone":    { "days": [[0,0,3],[0,0,0]], "verdict": "bright_two_to_one", "first": -1, "last": 1 },
  "hours":   { "items": [{ "moment_id": "…", "happened": 10.25, "collected": 21.4 }], "busiest": { "start": 20, "count": 19 } } }
```
- **`sections`** is the threshold's verdict, in render order. Absent sections are omitted.
- **`tone.days[i]`** is `[up, even, down]`: the summed intensities of bright, even and heavy pebbles that day.
- **`verdict`** ∈ `bright_more_than_two_to_one`, `bright_two_to_one`, `balance`, `heavy_outweighed`, `nothing_heavy`, `nothing_bright`.
- **`gem.facets`** maps all 28 facet ids (`star_0..6`, `kite_0..6`, `girdle_0..13`); the table is not data.
- **`first_weekday`** is 0 = Monday.

### 3.4 `gem_allocate(p_worths jsonb) returns jsonb` (immutable)
A port of `GEM.allocate` from `gem-method.js`, step for step:
- proportional wedges on the facet cycle, in the fixed order, with the main family centred on the crown
- each facet goes to the wedge covering most of it, with ties going counter-clockwise
- a present family that is left empty gets its smallest touching facet
- a settle pass on run ends
- `main` is the largest worth, ties broken by more pebbles and then by order; `rotation` is the index of `main`

The facet table (ids, kinds, nominal areas, cycle positions) is a constant inside the function, copied from the oracle.

### 3.5 Thresholds (inside the RPCs; the one source)
| Where | Shown when | Stops at |
|---|---|---|
| Week tiles: contents, persons | ≥ 3 pebbles (persons becomes an invitation with no souls) | — |
| Week tiles: emotions, domains, weight, hours, kept | ≥ 5 pebbles, week not live, and ≥ 2 distinct values for that stat (kept: at least one photo or card) | — |
| Month: cover, weeks | ≥ 1 pebble | every week of the month |
| Month: milestones | ≥ 3 pebbles and one large or medium stone | 5 |
| Month: persons | ≥ 3 pebbles (an empty array means the client shows the invitation) | full list; the client shows 5, then *more* |
| Month: gem | ≥ 3 pebbles in ≥ 2 families | — |
| Month: domains | ≥ 3 pebbles in ≥ 2 domains | full list; the client shows 5 |
| Month: tone | ≥ 5 pebbles on ≥ 3 days | — |
| Month: hours | ≥ 5 pebbles | — |

"Shows 5, then *more*" is display: the payload carries the full ranked list once, and both the section and the sheet read it.

### 3.6 Supabase deliverables
- **Migrations:**
  - `…_gem_allocate.sql`
  - `…_path_strip.sql`
  - `…_path_week_wrap.sql`
  - `…_path_month_wrap.sql`

  Each comes with a `comment on function` and regenerated types.
- **Indexes and budget:** check `moments (user_id, happened_at)` and the join paths with `explain analyze`, on the maintainer's real data and on a seeded heavy account. The budget is server execution time at p95: under **20 ms** for `path_month_wrap` on a 200-pebble month, and under **10 ms** for `path_strip` over 2,000 pebbles. The expected cost for a typical 40-pebble month is a few milliseconds; the user's wait is dominated by the network round trip, which §7 hides. The harness records the timings, so a regression is visible.
- **Harness:** `scripts/verify-path-wraps.ts` (anon, throwaway users, like `verify-moments.ts`) seeds the round-2 fixture (24 October pebbles and the thin, quiet and heavy subsets) and checks:
  - the thresholds
  - rankings and their tie-breaks
  - milestones
  - the month placement and empty spacers
  - a timezone boundary case (23:30 UTC on the 31st, in `Europe/Paris` versus `America/New_York`)
  - tone days and the verdict, and hours with the busiest window
  - `new_since_previous`
  - **`gem` against the oracle**, facet by facet

  It also writes the verbatim payloads to `scripts/oracles/path-wrap/payloads/*.json` for the clients' decoding tests. It joins the CI harness gate.
- **Oracle:** `scripts/oracles/path-wrap/{METHOD.md, gem-method.js, test.js}`, committed unchanged from the method round.

### 3.7 Freshness: computed on read
- **Nothing is stored.** Each call reads the current `moments`, `pebbles`, `pebble_souls`, `pebble_domains`, `pebble_cards` and `snaps`, and builds the answer then. A write (`save_moment`, `complete_pebble`, an edit, a delete) is visible on the very next read; no refresh job, trigger or queue exists that could lag.
- **Not a view.** The answers depend on parameters (the page asked for, `p_tz`), which a view cannot take. Day boundaries depend on the reader's zone, so one stored answer per month would also be wrong for a reader who travels.
- **Not a stored aggregate table.** A `month_wraps` table kept by triggers would watch six tables, need a stored timezone the profile does not have, and join `purge_account` along with its harness assertion. That is a lot of machinery to save milliseconds (§3.6).
- **The upgrade path,** if a budget is ever missed: a cache behind the same function signatures, keyed by user, zone and a per-user data version bumped by the write RPCs. No client changes.
- **Other devices** see a change on their next read (load, refresh, foreground), as with the feed today. Live push (Supabase Realtime) is out of scope and not blocked.

---

## 4. Client factoring rule (Android)

> **One concept, one component, at the lowest layer that can own it.** A screen composes; it never re-draws a stone, a dot, a ring, a row or a header, and it never aggregates.

| Concept | Today (inventory) | After this spec |
|---|---|---|
| Simplified stone | `StoneSeal`, plus a private `cairnStone`, `drawGhostStone`, `ValenceStone` and the `PebbleOutlineBackdrop` pipeline | **`StoneSeal`** with size presets and a tiny print; the new code adds no other renderer |
| A set of days as dots | a private `DayDots` | **`DaySetMarks`**: Week7, Calendar or Circle |
| Ring geometry | none | **`RadialLayout`**: circle dots, garland, mini garland, dial, gem rotation |
| Mark · name · count · trailing row | private `PersonRow` | **`MarkRow`**: every list except contents, which keeps its grid and shares `MarkRow`'s tokens |
| Section label + hint | ad hoc | **`SpineSectionHeader`** |
| Weekday labels and calendar display | `shortWeekday()`, `weekdayLetters()` | **`CalendarLabels`**: display only (the server owns week bucketing and ISO numbers) |
| Page dots | inline in onboarding | **`PageDots`** |
| Tabbed sheet with pager | none | **`TabbedSheetPager`**: week sheet and month sheet |
| Aggregation (`PersonsOfWeek.of`, summaries, thresholds) | client logic in `features/path/spine/logic` | **deleted** where the payload now carries it; what stays in `logic/` is display grouping of the feed (`SpineWeekBuilder`, entry types) |

---

## 5. Client layers and packages (Android)

The rules (`ArchitectureBoundaryTest`, `ThemeLiteralsTest`) are unchanged.

```
core/model/
  wrap/StripDto.kt, WeekWrapDto.kt, MonthWrapDto.kt   @Serializable payloads (snake_case), tolerant decoding
  wrap/WrapModels.kt                                   display models: DaySet, DayWeight, Verdict, FacetMap …
  EmotionFamily.kt                                     slug, name and palette, from v_emotions_with_palette rows

core/data/
  PathWrapService.kt                                   strip(), weekWrap(start), monthWrap(month): the three RPC calls, DataError boundary

core/designsystem/
  RadialLayout.kt · DotMark.kt · SpineSectionHeader.kt · MoreLink.kt · TileCard.kt
  ProportionBar.kt · PageDots.kt · TabbedSheetPager.kt · SpineLayoutTokens.kt · CalendarLabels.kt

core/ui/
  render/stone/StoneSeal.kt (sizes)  ·  SoulMark.kt (moved)  ·  DaySetMarks.kt  ·  DayHeader.kt
  marks/CairnMark.kt  ·  marks/DolmenMark.kt  ·  MarkRow.kt  ·  gem/GemGeometry.kt (facet polygons + light tones, constant)

features/path/
  strip/          PathStrip, StripItem
  wrap/copy/      WrapCopy: enums and numbers to strings (plurals, NumberWords), the only place sentences are built
  wrap/week/      WeekLine, WeekTiles (tile bodies), WeekSheetPages
  wrap/month/     MonthCover (Garland), WeeksSummary, MilestonesSection, PersonsSection, EmotionGem,
                  DomainsSection, Insights (ToneBars, HoursDial), MonthSheetPages, MonthPage
  spine/          SpineWeek (opening rows replaced), WeekContents + PersonsOfWeek (rebased, sheet pages)
```

`gem/GemGeometry.kt` is display: polygons, the fixed outline and per-facet light tones. **It never decides which family owns a facet**; that comes from `gem.facets`.

---

## 6. Components (atomic design)

All components are stateless and read `PebblesTheme.spine` for colours and type. Only leaves read `LocalEmotionPaletteService`.

### Atoms — `core/designsystem`
| Atom | Sketch | Notes |
|---|---|---|
| `DotMark` | `DrawScope.dotMark(center, d, style, color)` + `@Composable DotMark(...)` | `DotStyle { Filled, Ring, Socket, Quiet }`. The single primitive behind every dot. |
| `RadialLayout` | `RadialLayout(slots, startDeg, gapSlots, clockwise)`, plus `continuous(24f)` | Month ring (31 slots, 1.5 gap), garland (32 slots, tie at the foot), dial (noon on top), gem (7 positions, plus `rotation`). |
| `SpineSectionHeader` | `(label, hint?, trailing?)` | Small-caps label and italic hint; heading semantics. |
| `MoreLink` | `(text, onClick)` | Chevron, 48 dp. |
| `TileCard` + `TileHeader` | `(dashed, onClick, content)` | 128×112, shape from `MaterialTheme.shapes`. |
| `ProportionBar` | `(segments, height)` | Emotions tile, domain rows. |
| `PageDots` | `(count, current)` | Onboarding moves onto it. |
| `TabbedSheetPager` | `(tabs, initialPage, onDismiss, header?, page)` | Stock `ModalBottomSheet` with partial expansion, a stock `SecondaryScrollableTabRow`, a `HorizontalPager` and `PageDots`. |
| `CalendarLabels` | weekday letters and short names (EN/FR) | Display only. |

### Atoms — `core/ui`
| Atom | Sketch | Notes |
|---|---|---|
| `StoneSeal` | `(valence, palette, glyph?, knockout, size: SealSize)` + `DrawScope.drawSeal(...)` | `SealSize { Tiny, Contents, Milestone, Pip }`. Tiny keeps a fixed 2.4 dp hatch spacing and drops the glyph. `drawSeal` serves the Canvas organisms. |
| `SoulMark` | moved, unchanged | |
| `DaySetMarks` | `(days: DaySet, mode: DayMode, tint?)` | Week7, Calendar or Circle, drawn with `DotMark`; one semantics node. |
| `DayHeader` | `(mode, span)` | Letters, or the hint "from the 1st, clockwise". |
| `CairnMark` | `(days: List<DayWeight>, state, color)` | Stones from the `StoneSeal.Pip` outline. Width `9 + 4.2·min(c,3) + 2.4·(m−1)`; the stack is capped at 42 dp. These are display formulas over server counts. |
| `DolmenMark` | `(color)` | |
| `MarkRow` | `(leading, title, count?, subtitle?, trailing?, onClick?)` | 30 dp · 1fr · auto, min 44 dp, rule below. |

### Molecules
- **Strip and week:**
  - `StripItem`: a cairn or dolmen, a label and a focus underline.
  - `WeekLine`.
  - The tile bodies (`Contents`, `Persons`, `Emotions`, `Domains`, `Weight`, `Hours`, `Kept`).
- **Rows built on `MarkRow`:**
  - `PersonRow`: `SoulMark` + name + role + count + `DaySetMarks`.
  - `FamilyRow` and `FineEmotionRow`: a tinted pip + name + count + tinted `DaySetMarks`.
  - `DomainRow`: count + name + dots, or a bar.
  - `MilestoneRow`: a milestone seal + title (weight by intensity, italic if heavy) + dateline + glyphs.
  - `WeekSummaryRow`: cairn + range + summary + glyphs.
- **Other:**
  - `ViewModeSwitch`: a stock `SingleChoiceSegmentedButtonRow`.
  - `LeadLine`.

### Organisms
| Organism | Composition |
|---|---|
| `PathStrip` | A `LazyRow` of `StripItem`, focused item centred; replaces `WeekRoll`. |
| `WeekWrap` | `WeekLine` + the tiles listed in `tiles`. |
| `WrapSheet` | `TabbedSheetPager` over `List<WrapPage>`, which the week and the month each supply. |
| `MonthCover` | Leaf + `Garland` (a Canvas over `RadialLayout(32)`: the course, `drawSeal` per pebble in `cover.days`, `DotMark.Socket` on empty days, Monday numerals, the tie) + the souls row + lines. |
| `WeeksSummary`, `MilestonesSection`, `PersonsSection`, `DomainsSection` | `SpineSectionHeader` + rows + `MoreLink`. |
| `EmotionGem` | Header + `LeadLine(gem.lead)` + `GemCanvas` (`GemGeometry` polygons, colours = palette of `gem.facets[id]` × the facet's light tone, uniform seams) + rim labels + `MoreLink`. |
| `Insights` | Header + `ToneBars(tone.days)` + `HoursDial(hours)` on `RadialLayout.continuous`. |

### Templates and pages
- **Week page** (`SpineWeek`): `Line` and `Tiles` rows replace the masthead, opening, persons and contents rows, and `SpineEmptyWeek` is deleted. Days, seams, pebbles and the mouth are unchanged.
- **Month page** (`MonthPage`): a `LazyColumn` over `sections`, in payload order.
- **Path screen:** `PathStrip` above the pager, whose pages are `PathPage = Week(start) | Month(month)` derived from the focusable strip items.

---

## 7. State, data flow, caching (Android)

- **ViewModel:** `PathViewModel` loads `path_moments` (the feed) and `path_strip` in parallel. On focus, it loads the focused page's wrap through `PathWrapService` and keeps it in `Content.wraps: Map<PathPage, WrapState>`.
- **`WrapState`** is `NeverLoaded` (the only state that may show a placeholder, and only for a page never fetched on this device), `Ready(payload, stale: Boolean)` or `Failed(lastGood: payload?)`. A failed refresh keeps showing the last good payload, with a quiet retry; an error state appears only when no payload exists.
- **Neighbours:** the next and previous pages are prefetched.

### 7.1 Caching: stale-while-revalidate, no blink
- **Show, then check.** A page with a cached payload renders it immediately and revalidates in the background. If the fresh payload equals the cached one, which is the usual case, nothing recomposes. If it differs, only the changed sections update in place. Content is never replaced by a spinner.
- **After a write** (create, edit, delete, completing a moment), the cached wraps are **marked stale, never cleared**. The focused page and the strip refetch at once; the page keeps showing the old payload until the new one lands, about one round trip later.
- **Memory cache:** `PathWrapService` holds the payloads for the session (strip, weeks, months).
- **Disk cache,** so a cold start opens instantly: one JSON file per page under `noBackupFilesDir/path-wraps/`, written after each successful fetch and read at launch before the network answers.
  - **Keys:** user id, `p_tz` and a payload schema version. A change of user, zone or format never shows wrong data; a version bump drops the old files.
  - **Privacy:** wiped on sign-out and on account deletion (the same path that ends the session). It is private journal content at rest, never backed up.
  - **Order:** the disk cache is display-only. The background revalidation always runs, and the server stays the truth.

### 7.2 Progressive reveal (animation in a later step)
The month payload arrives in one call, organised by section; it is never split into one request per section, which would pay a network round trip per section to save milliseconds of server work. Progressive display is a device concern:
- **Off the main thread:** each section maps to its own display model on the default dispatcher: gem facet colours and label placement, the dial and garland paths, tone bars. Each section renders as soon as its model is ready, in page order, so the cover appears first while heavier sections finish.
- **The later animation** only staggers the reveal of models that are already present (from the network or the cache). It never waits on I/O and never shows a placeholder for a section the payload left out.
- **Rules:**
  - Stagger only on a page's first reveal in a session.
  - A revisit or background refresh shows everything at once.
  - After a write, only the changed section animates.
  - `rememberReduceMotion()` disables the stagger entirely.
- **If one section ever proves slow,** `path_month_wrap` can take an optional `p_sections text[]` and the client can request it separately. The keyed payload makes that a server-side change with no new client shape.
- **`PathUiState.Content` changes:**
  - It gains `strip`, `focus: PathFocus` (replacing `focusedWeekStart`) and `wraps`.
  - The pages are a lazy getter.
  - `onFocusWeek` becomes `onFocus(PathFocus)`.
- **Timezone:** `p_tz` is `ZoneId.systemDefault().id`, the zone the feed already uses for its days.
- **UI state:**
  - The sheet's open state and page index are `rememberSaveable` in the page; the sheet is never in the ViewModel and never a Nav3 key.
  - The dot view mode comes from `DisplayPreferences` (DataStore, D15), through the ViewModel.
- **Decoding:** DTOs decode tolerantly (`ignoreUnknownKeys`; absent optional sections become `null`) and map to display models in one place (`WrapModels`). A malformed payload becomes an `Error` for that page only, never for the whole Path.

---

## 8. Accessibility, i18n

- **Semantics:**
  - Strip items: buttons with a description ("Week of 5 October, eleven pebbles"; "October, the month") and a selected state.
  - Spacers: plain text.
  - Tiles: buttons naming their page.
  - `DaySetMarks`: one node.
  - The gem: a description listing families and shares, main first.
  - Charts: a description sentence.
  - Sheet tabs: the tab role.
  - Targets: at least 48 dp.
- **Motion:** `rememberReduceMotion()` makes the strip's centring and the pager's page changes snap.
- **Strings:**
  - All sentences live in `WrapCopy`, built from `strings.xml` and `values-fr` plurals (French needs `quantity="many"`) and `NumberWords`. Its cardinal table must cover month counts beyond 60.
  - Family, emotion, domain and soul names come from data.

---

## 9. Tests

- **Server (the logic):**
  - `verify-path-wraps.ts` as in §3.6, including the gem oracle comparison and the timezone boundary.
  - It runs in the CI harness gate.
- **Android (decoding and display):**
  - **Decoding tests replay the harness's verbatim payloads** (`scripts/oracles/path-wrap/payloads/*.json`, copied into test resources by a Gradle `sourceSets` entry), including absent sections and explicit nulls, per the root cross-surface rule.
  - `WrapModelsTest` covers the mapping, `WrapCopyTest` the sentences in EN and FR, and `RadialLayoutTest` and `GemGeometryTest` the geometry.
  - No Android test re-derives a number the server computes.
- **Screenshot tests:** every atom and molecule (Light, Dark, `@PreviewLargeFont`, `@PreviewFrench`):
  - `DaySetMarks` in its three modes
  - `StoneSeal` in its four sizes and three polarities
  - `CairnMark` in its three states, plus `DolmenMark`
  - `MarkRow`
  - each tile body
  - the organisms on the October, thin and quiet payloads

  Coupled counts such as `VERSO_ROWS` become semantics tags.

---

## 10. Parts (one branch and PR each, `gh stack`)

Server first: the clients cannot render what the server does not compute.

1. **db:** `gem_allocate`, with the oracle committed and its own harness section.
2. **db:** `path_strip`.
3. **db:** `path_week_wrap`, with thresholds, rankings and the invitation.
4. **db:** `path_month_wrap`, with the cover, milestones, gem, tone, hours and `new_since_previous`. The harness writes the payload fixtures.
5. **android, move:** `SoulMark` to `core/ui`; `Modifier.rule`, the shared sizes and `CalendarLabels` to `core/designsystem`. No behaviour change.
6. **android, model and data:** the DTOs, `WrapModels`, `EmotionFamily` and `PathWrapService`, with decoding tests on the payload fixtures.
7. **android, atoms:** `DotMark`, `RadialLayout`, `SpineSectionHeader`, `MoreLink`, `TileCard`, `ProportionBar`, `PageDots` (onboarding moves onto it), the `StoneSeal` sizes, `DaySetMarks` and `MarkRow`. `PersonsOfWeek` and `WeekContents` are rebased with identical pixels.
8. **android, strip:** `CairnMark`, `DolmenMark`, `PathStrip` and `PathPage`, with the month page as a placeholder. `WeekRoll` is deleted.
9. **android, week wrap:** `WeekLine`, the tiles, `TabbedSheetPager` and `WrapCopy` (week). The opening rows and client aggregation are deleted.
10. **android, DataStore:** the preference migration and `DisplayPreferences`.
11. **android, month page:** the cover, weeks, milestones, persons, domains and the month sheet with `ViewModeSwitch`.
12. **android, gem:** `GemGeometry`, `GemCanvas` and `EmotionGem`.
13. **android, insights:** `ToneBars` and `HoursDial`.

**Every part:**
- **Arkaik:** update the hosted map when a view or endpoint changes (four new RPCs, the strip, the wrap, the sheet, the month page) and move acceptances to `development`. This needs the `arkaik-mcp` tools; if they are missing, stop and say so.
- **Release notes:** user-facing parts carry a Lab Note, or `no-lab-note` where Arkaik covers them.
- **Pending migrations:** before applying db parts, diff any function body that another pending migration also re-emits.

---

## 11. Open questions and known debt

- **Even pebbles:** ½ or ⅔. Changing it touches one constant in `gem_allocate`, the oracle and the harness expectations.
- **Gem order:** shame before sadness, so sadness sits next to peaceful. Recommended by the method, not yet confirmed.
- **Timezone:** clients send the device zone. A stored profile zone would make server-side features (notifications, digests) consistent, but none exists today.
- **The month's own path and running head:** not in this spec.
- **Existing flat-stone duplicates and `Glyph` vs `PebbleGlyph`:** a later quality issue.
- **iOS and web:** one issue each. They consume the same three RPCs and replay the same payload fixtures; their work is display only.
- **The board-03 almanac table:** shelved for a web history table; no issue yet.
