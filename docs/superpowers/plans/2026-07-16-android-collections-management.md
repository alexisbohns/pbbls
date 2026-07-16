# Android collections management — list, detail, create/edit

> Sub-project **E** of the Android Profile milestone (M41 on GitHub, drafted "M40"; issue #569, blocked by #567, independent of D). Umbrella design: `docs/superpowers/specs/2026-07-16-android-profile-design.md` §E, decisions D1, D5–D7, D9–D11, D13–D14.

## Approach

Collections get the full management loop iOS ships: the Profile carousel wires up (header → list, card → detail, empty tile → create), a pushed list with mode badges and pull-to-refresh, a detail grouping the collection's pebbles by calendar month, and one create/edit form whose mode picker encodes "None" as an **explicit JSON null** so clearing the mode actually clears the column (PostgREST only touches present keys). Data access lives in a new `CollectionsService` (direct RLS single-table calls — D6); every mutation refreshes `ReferenceDataService.collections` so the pebble-form picker stays in sync.

## Deliverables

- **`features/profile/models/CollectionPayloads.kt`** — pure insert/update payload builders with the explicit-null mode key; `wireName` routed through the enum's own serializer so encode can't drift from the `@SerialName` decode mapping. Exact-keyset JVM tests mirroring the iOS encoding suites.
- **`services/CollectionsService.kt`** — `list()` (name-asc, `pebble_count:collection_pebbles(count)` aggregate — note the junction's word order, opposite of `pebble_souls`), `loadCollection`, `loadPebbles` (`collection_pebbles!inner` embedded filter, `happened_at` desc), `create`/`update`/`delete`. New CompositionLocal, wired in `PebblesApp`/`MainActivity`.
- **`features/profile/components/CollectionModeBadge.kt`** — the iOS capsule badge (emoji + label, null renders nothing); Stack/Pack/Track labels are string resources pending the maintainer's FR vocabulary (never machine-translated).
- **`features/profile/GroupPebblesByMonth.kt`** — `groupPebblesByMonth(pebbles, zone)` on `java.time.YearMonth` (descending months, input order preserved within a group; zone injectable). JVM test mirrors `GroupPebblesByMonthTests` + a zone-vs-offset case.
- **`features/profile/CollectionsListScreen.kt`** — NavHost push (D1): bordered two-line rows (name / badge · count with "No pebbles" zero copy), "+" top-bar create, pull-to-refresh (`PullToRefreshBox`), long-press `DropdownMenu` + confirm ("linked pebbles stay" copy — D7 unifies over iOS's swipe action); D13 loading/error/Retry + empty state; fetches its own rows (D10).
- **`features/profile/CollectionDetailScreen.kt`** — subheader row (badge + live count), month-grouped `PebbleRow` sections with locale-formatted `MMMM yyyy` headers (D14); tap → `EditPebbleScreen` cover, long-press → `delete_pebble` path with confirm; Edit → the form as a cover (D9). Fetches its own row from the route id (same named deviation as the soul detail).
- **`features/profile/CollectionFormScreen.kt`** — one full-screen surface merging iOS `Create/EditCollectionSheet`: name field + `CollectionModePicker` (None/Stack/Pack/Track as Pebbles-styled capsule toggles — named deviation from Material's segmented buttons, same reason as the app-wide no-Material-roles rule). Pure `collectionFormCanSave` gate (JVM-tested, includes the clear-to-null case).
- **Profile wiring** — `ProfileCollectionsCard`'s dormant callbacks connect (`onOpenList`/`onOpenCollection`/`onCreate`); NavHost destination disposal means returning to Profile re-runs its load, keeping the carousel fresh after edits in pushed screens.
- **Navigation (D1)** — `collections` + `collections/{collectionId}` routes.
- **Strings** — 15 en/fr pairs; `profile_collections_header`/`profile_collection_new`/`settings_name_label`/`soul_detail_*`/`pebble_delete*`/`pebbles_count` reused.
- **Arkaik** — `V-collections-list`/`V-collection-detail`/`V-collection-create`/`V-collection-edit` + `V-profile` android notes.

## Verification

CI green; `CollectionPayloadsTest` + `GroupPebblesByMonthTest` + `CollectionFormLogicTest` + existing suites; `CollectionsScreenshots` gallery (badges ×3 + mode picker states) — light/dark; on device (milestone exit): collections list with mode badges → detail groups by month with localized headers → create with mode → edit clearing the mode (verify the column is NULL in the dashboard) → delete → carousel on Profile reflects changes; fr pass.

## Lessons learned

- (fill at review)
