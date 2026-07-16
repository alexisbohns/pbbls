# Android souls management — list, detail, create/edit with glyph row

> Sub-project **D** of the Android Profile milestone (M41 on GitHub, drafted "M40"; issue #568, blocked by #567). Umbrella design: `docs/superpowers/specs/2026-07-16-android-profile-design.md` §D, decisions D1, D5–D9, D11–D13.

## Approach

Souls get the full management loop iOS ships: a shortcuts-row entry on Profile → grid list → pushed detail (the soul's pebbles) → full create/edit form carrying the glyph row that M39 D12 explicitly parked. Data access lives in a new `SoulsService` (direct RLS single-table calls — D6; `souls_glyph_usable` owns glyph-ownership enforcement server-side); every mutation also refreshes `ReferenceDataService.souls` so the pebble-form picker never goes stale. The picker's private tiles are replaced by the shared 4-case `SoulItem` (#459 contract), so list and picker render one cell.

## Deliverables

- **`features/glyph/models/SystemGlyph.kt`** — the seeded default-glyph UUID (matches `souls.glyph_id`'s column default), so a form starting on "no choice" agrees with the server.
- **`services/SoulsService.kt`** — `list()` (name-asc, glyph embed + `pebbles_count:pebble_souls(count)` aggregate), `loadSoul`, `loadPebbles` (`pebble_souls!inner` embedded filter, `happened_at` desc), `create`/`update`/`delete` (direct single-table writes; delete cascades only the links), `loadGlyph` (form-default thumbnail). New CompositionLocal, wired in `PebblesApp`/`MainActivity`.
- **`features/shared/SoulItem.kt`** — the shared cell (SELECTED/UNSELECTED/DEFAULT/CREATE per the #459 spec table): `GlyphView` + hand-face name (selection carried by color, never weight) + fossil-shell count; long-press hook for D7. `SoulPickerSheet`'s private `SoulTile`/`CreateSoulTile` deleted in its favor (case mapping: in-selection → SELECTED, empty selection → DEFAULT, else UNSELECTED).
- **`features/profile/components/ProfileShortcutsRow.kt`** — `SurfaceTile`-based tiles in iOS order (Collections · Souls · Glyphs); handlers optional and tiles render only when wired (D11) — souls now, collections with E, glyphs with its milestone.
- **`features/profile/SoulsListScreen.kt`** — NavHost push (D1): adaptive-96 grid, "+" top-bar create, tap → detail route, long-press `DropdownMenu` + confirm dialog ("linked pebbles stay" copy — D7 unifies over iOS's context menu); loading/error/Retry (D13) + empty state; fetches its own rows like iOS and refreshes the refs cache after create/delete.
- **`features/profile/SoulDetailScreen.kt`** — 56dp glyph header + name + live count (derived from the fetched pebbles, like iOS), shared `PebbleRow` list in `pebblesListRow` chrome; tap → `EditPebbleScreen` cover, long-press → `delete_pebble` path with confirm; Edit top-bar button → the form as a cover (D9 surface swap). Receives only the route's `soulId` and fetches the soul itself (iOS hands the row over from the list — named deviation).
- **`features/profile/SoulFormScreen.kt`** — one full-screen surface merging iOS `CreateSoulSheet`/`EditSoulSheet` (they differ only in initial state + write call): name field, Glyph row (32dp thumbnail or dashed placeholder → existing `GlyphPickerSheet`), inline save error. Pure `soulFormCanSave` gate (JVM-tested). Named deviation: the picker returns the full `Glyph`, so iOS's post-pick refetch is dropped.
- **`features/profile/components/`** — `DeleteDialogs.kt` (parameterized confirm + error, the M39 D8 chrome shared by both souls screens), `ProfileEmptyState.kt` (the `ContentUnavailableView` analog, text-only).
- **Navigation (D1)** — `souls` + `souls/{soulId}` routes; `ProfileScreen(onOpenSouls)` renders the shortcuts row between banner and stats.
- **Strings** — 12 en/fr pairs; `profile_collection_count` plurals renamed to the shared `pebbles_count` (collection cards + soul detail header); `create_soul_add`/`create_soul_title`/`create_soul_name_placeholder`/`create_glyph_header`/`pebble_delete*` reused.
- **Arkaik** — `V-souls-list`/`V-soul-detail`/`V-soul-create`/`V-soul-edit` + `F-manage-souls` android status; `V-profile` note gains the shortcuts row.

## Verification

CI green; `SoulFormLogicTest` + existing suites (LocalizationParityTest covers the new pairs); `SoulsScreenshots` gallery (SoulItem ×4 cases + shortcuts row) — light/dark; on device (milestone exit): souls grid with live counts → detail lists the right pebbles → edit a soul's name + glyph → delete a soul ("linked pebbles stay" verified) → inline-create still works from the pebble form; fr pass.

## Lessons learned

- (fill at review)
