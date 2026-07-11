# Android read-only Path timeline — implementation plan (M37 sub-project D)

> Issue: **#531** ([Feat] Android read-only Path timeline) · Blocked by #528/#529/#530 (all done) · Completes the milestone.
> Umbrella design: `docs/superpowers/specs/2026-07-10-android-bootstrap-design.md` (decision D10).
> Status: **ready to implement.**

This plan inlines the exact values pulled from the iOS reference (`apps/ios/Pebbles/Features/Path/`) and the DB layer, so it executes without re-exploration. iOS is the reference implementation; where behavior is described, the named iOS file is the source of truth.

## Context

A (scaffold+CI), B (design system), C (entry funnel) are merged; `RootScreen`'s authenticated branch lands on a placeholder `PathScreen`. D replaces it with the real read-only timeline: pebbles from `path_pebbles()`, grouped by ISO week, rendered with server-composed SVGs tinted by emotion palettes. No create/edit/delete/detail/stats — read-only. CI's `app-debug` artifact (config baked from repo Actions secrets) is the maintainer's install channel.

### Data contracts (verified against migrations + generated types)

- `path_pebbles()` (no params, `security invoker`, `auth.uid()` filter, ordered `happened_at desc`): `id` uuid, `name` text, `happened_at`/`created_at` timestamptz, `intensity` 1..3, `positiveness` −1..1, `render_svg` text?, `emotion` jsonb `{id, slug, name}`?, `first_snap_path` text? — a storage **prefix** `{user_id}/{snap_id}`; consumers append `/thumb.jpg` / `/original.jpg`. One fetch, no data pagination (matches iOS).
- `v_emotions_with_palette`: `primary/secondary/light/surface_color` are 8-digit `#RRGGBBAA` strings (may carry stray whitespace — trim at the model boundary), plus id/slug/name/emoji/category fields.
- Storage: private bucket `pebbles-media`; signed-URL TTL 3600 s (web/iOS convention), cache expiry −60 s safety margin.
- `render_svg`: `<svg viewBox>` + `<g id="layer:shape|fossil|glyph">` with all strokes `stroke="currentColor"`, `fill="none"`; the shape layer may carry `<defs><clipPath>` + `clip-path="url(#…)"` + `fill-rule`/`clip-rule`. Canvases: small 250×200, medium 260×260, large 260×310.

## Scope decisions (deviations flagged for maintainer veto in the PR)

1. **Valence-picker PDFs are not ported.** The iOS Path *row* never uses them (create-flow picker assets). The row is mirrored with the 9 outline silhouette SVGs already in-repo (`apps/ios/Pebbles/Resources/Outlines/{size}-{polarity}.svg`, one `fill="#FF00FF"` sentinel each) → `res/raw/outline_{size}_{polarity}.svg`. Dissolves umbrella risk 6 for D.
2. **Localized emotion name appended to the row meta line** ("MONDAY · 3:42 PM · JOY") via `ReferenceStrings.referenceName` — deviation from iOS (emotion is color-only there) so the "emotion names localized in fr" acceptance criterion is visible on device.
3. **Week roll = static hand-drawn cairn vector** (`res/drawable/cairn_static.xml`), accent-tinted when focused, week number below. The iOS Rive state machine (`pbbls-cairn-states.riv`, iOS-Resources-only asset) is a fast-follow — "static cairn imagery acceptable in v1" per the issue.
4. **Snap thumbnails ship in this PR** (Coil 3 + storage-kt) as the final, cleanly slice-able commit.
5. **iOS reveal cascade + bottom fade mask skipped** — both choreograph the create button, which doesn't exist read-only; also keeps screenshot renders deterministic.

## Architecture

New code under `app/src/main/kotlin/app/pbbls/android/`. Leaf composables take `palette`/data as **parameters** (not services) so screenshot tests need no secret-requiring constructions; `LocalSnapURLCache` defaults to null for previews.

- **Models** (`features/path/models/`, hand-written `@Serializable`): `Pebble` (+ `EmotionRef`), `Valence` (9 cases, `fromOrDefault` → NEUTRAL_MEDIUM, log-free — logging lives in `PathService`; `ValenceSizeGroup`/`ValencePolarity`), `EmotionPalette` (4 Compose Colors + 4 trimmed hexes; 6/8-digit `#RRGGBBAA` parser; `pebbleFrameColors(intensity)`: 3 → stroke=light/fill=primary/opacity=α(primary), 1–2 → stroke=secondary/fill=surface/opacity=α(surface); `rgbHex` 9→7 truncation), `EmotionWithPalette` (row → nullable mapping; service logs+skips bad rows), `WeekRollEntry(weekStart: LocalDate, pebbles)`. Timestamps: `OffsetDateTime` via a custom serializer — `OffsetDateTime.parse` accepts `+00:00`/`Z`/fractions; `java.time.Instant.parse` does not.
- **Pure logic**: `WeekRollBuilder` (ISO Monday via `TemporalAdjusters.previousOrSame(MONDAY)`, zone injected, union with current week, past weeks ascending / current+future descending, `previous`/`next` by index), `WeekHeaderFormatting.formatRange` (month-day per locale, `IsoFields.WEEK_BASED_YEAR` suffix only when ≠ today's), `PathRowFormatting.weekdayTime` (`EEEE` + localized short time), `SvgColors` (`injectStrokeColor` replaces `currentColor`; `injectOutlineFill` replaces `#FF00FF`).
- **Rendering** (`features/path/render/`): `PebbleSvg` (D10 — substitute → `SVG.getFromString` → `renderToPicture`, `remember(svg, strokeHex)`; parse failure → single logged error, renders nothing, the backdrop still gives a silhouette), `PebbleOutlineGeometry` (outlines 337×270 / 350×350 / 335×400; `pebbleScale = canvasW/outlineW`; `aspectRatio`), `PebbleOutlineBackdrop` (explicit 9-entry res map, raw read + remember, `alpha(fillOpacity)`), `PebbleThumbnail` (backdrop + scaled overlay; 56 dp small/medium, 96 dp large).
- **Components**: `PathPebbleRow` (row heights 100/60/71/68; name color dark→palette.light / light→palette.primary / no-palette→system.foreground; frame-color fallback accent `#C07A7A`; photo 64 dp, radius 8, 4 dp white border, shadow, rotation −7°/+4° by index parity), `PathSnapThumb` (sign via nullable `LocalSnapURLCache`, Coil `AsyncImage`), `WeekHeader` (40 dp pill, radius 17, 1 dp muted border, accent chevrons 0.3-alpha disabled), `WeekRoll` (LazyRow, 72 dp cells, centered focus), `WeekPebbleList` (empty state "Fresh week"/"Collect a new pebble", no create button).
- **Screen**: `PathScreen` — loading spinner / localized error / content; **single source of truth `focusedWeekStart: LocalDate?`**; `HorizontalPager` created after load (`initialPage`, `key = weekStart.toEpochDay()`, `pageCount = { entries.size }`); swipe → `snapshotFlow(currentPage)` updates focus; chevron/cairn tap → focus change → `animateScrollToPage`; on-load refocus = current week, else closest by epoch-day distance. Temporary sign-out `TextButton` retained.
- **Services**: `PathService` (`rpc("path_pebbles").decodeList<Pebble>()` + out-of-range valence logging), `EmotionPaletteService` (`byEmotionId` map + `hasLoaded` via `mutableStateOf`; load never throws — logs and recovers next launch; `palette(emotionId)`), `PebbleSnapRepository` (`SignedUrlProviding`; one `createSignedUrls` call for original+thumb; URL normalized against `AppEnvironment.supabaseUrl` if relative), `SnapURLCache` (Mutex + inflight `Deferred` map coalescing, injectable clock, TTL 3600−60 s, `invalidateAll`, log-free/JVM-tested).
- **Wiring**: `SupabaseService` gains `install(Storage)`; `PebblesApp` constructs palettes/path/snapUrls + implements Coil `SingletonImageLoader.Factory` (explicit `OkHttpNetworkFetcherFactory`); `MainActivity` provides the three new locals; `RootScreen` adds `LaunchedEffect { palettes.load() }` (splash-concurrent, mirrors iOS RootView `.task`) and snap-cache invalidation on the session→null transition.
- **Deps** (catalog): `com.caverock:androidsvg-aar` 1.4, Coil 3 (`coil-compose` + `coil-network-okhttp`), `storage-kt` (BOM-managed).

## SVG fidelity spike (front-loaded)

Commit 2 ships `PebbleSvg` + fixtures + a screenshot grid, pushed immediately so CI's `ui-screenshots` artifact gives the maintainer an early side-by-side against iOS. Fixtures are assembled per `compose.ts`'s wrapper logic from the checked-in engine shape sources, deliberately covering `medium-neutral` (defs/clipPath), `large-lowlight` (fill-rule/clip-rule), a small shape + fossil + glyph transform, plus the iOS test fixture. Fallback ladder if AndroidSVG mangles output: (a) server-side SVG tweak (decision-log entry — affects three surfaces), (b) scoped custom renderer (iOS `PebbleSVGModel` port). If layoutlib can't run AndroidSVG: try direct `renderToCanvas`; else inspection-mode placeholder + device-only fidelity verification.

## Tests (JVM, JUnit4)

Week grouping incl. ISO-year boundaries (2025-12-29 → week 1 of WBY 2026; week 53 of 2026; Sunday → prior Monday; zone sensitivity); valence 9 cases + fallback; palette hex parse (6/8-digit ARGB reorder, trim, invalid) + `pebbleFrameColors` both branches (fillOpacity ≈ 0x1A/255); realistic RPC JSON decode (nested emotion, absent optionals, timestamp offset variants); `currentColor`/sentinel substitution; header formatting en/fr + week-based-year suffix; row weekday·time formatting; `SnapURLCache` coalescing/TTL/failure-retry/invalidate with virtual clock; `LocalizationParityTest` extended to full en/fr key-set equality.

Screenshot previews (CI `ui-screenshots`): fidelity grid, row variants (sizes/photo/fallback/parity), header (same/cross-year), week roll, week list (empty+populated), screen states — light + dark.

## Commit sequence

1. `docs: add android path timeline implementation plan`
2. `feat(android): add PebbleSvg renderer and composed-SVG fidelity screenshots` ← the spike, pushed immediately
3. `feat(android): add Path models and ISO-week grouping`
4. `feat(android): add PathService and EmotionPaletteService, wire the service graph`
5. `feat(android): render pebble rows with outline backdrops`
6. `feat(android): build the read-only Path timeline screen`
7. `feat(android): add snap thumbnails via signed URLs and Coil`
8. `docs(android): record the Path timeline in CLAUDE.md and Arkaik` (+ `fix(docs): drop duplicate DM-bounce arkaik node` — the bundle validator already fails on main because of it)

## Verification

- CI green: `ktlintCheck testDebugUnitTest assembleDebug` + `updateDebugScreenshotTest`; review `ui-screenshots`.
- Maintainer on device (debug APK, real account): pebbles grouped by ISO week; SVGs vs iOS side-by-side (spike exit criterion); emotion names in fr via per-app language; palette fallback (pebble without emotion renders accent); sign-out returns to Welcome.
- PR: `Resolves #531`, spike verdict, veto-able decisions above, milestone-level bilingual Lab Note proposal (per the umbrella spec's deferred-Lab-Note rule), labels `feat`/`ui`/`core`/`android`, milestone M38.

## Lessons learned

_(filled at completion)_
