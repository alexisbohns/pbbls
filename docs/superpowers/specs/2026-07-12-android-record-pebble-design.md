# Android Record Pebble — create, detail, edit, delete

> Milestone-level design drafted as **"M39 · Android Record Pebble"** — GitHub milestone numbering may differ, as it did last time (the bootstrap was drafted "M37", created as **M38 · Android App**; docs referring to "M39" mean this milestone). Five PRs: one `[Fix]` DB pre-flight plus four sub-projects **A–D**, each its own issue + spec + plan + PR, per the M36/M38 precedent. Foundation: the M38 bootstrap (`docs/superpowers/specs/2026-07-10-android-bootstrap-design.md`, all shipped) and the decision-log entry **2026-07-10 — Android app: native Kotlin + Jetpack Compose, mirroring the iOS architecture**, whose consequence — *"keep sibling RPCs symmetric" now spans three surfaces* — this milestone both exercises and repairs.

## Goal

Give the Android app its first write path: record a pebble (name, when, description, emotion, domain, valence, souls, collection, existing glyph), see the "+N karma" flash, get revealed the freshly composed pebble, then edit and delete it — mirroring the iOS single-form flow (`PebbleFormView` + modal sub-pickers, not a stepped wizard) 1:1 in Compose. The milestone ends with the full on-device round-trip working against production: **create → karma flash → reveal → edit → delete**, with karma deltas verifiable in the wallet.

The finished iOS app is the reference implementation. Where this design says "mirror X", the implementer reads the named iOS file under `apps/ios/Pebbles/` and ports its structure, not just its behavior (`apps/android/CLAUDE.md` rule).

## Non-goals

- **Photo attachments (snaps).** The iOS form's Photo section (`SnapUploadCoordinator`, `PhotoPickerView`, `FormSnap`, eager `delete_pebble_media`) does not port. The Android form simply has no Photo section — `PebbleFormView` already gates it behind `showsPhotoSection` for exactly this kind of caller. Payloads omit `snaps` on create; edit handling per risk 5.
- **Glyph carving.** `GlyphCarveSheet` and the carve stack stay iOS-only. Selecting an *existing* glyph (own, system, or marketplace-entitled — whatever the `glyphs` RLS SELECT exposes) **is** in scope.
- **Cards/thoughts.** Neither iOS nor the web quick editor sends `cards`; out of scope.
- **`new_glyph` / `new_collections` payload keys.** iOS sends neither; Android won't either. (`new_souls` is also not sent — inline soul creation uses a direct insert, matching iOS; see D12.)
- **Karma sound + waveform haptics.** iOS's ceramic sound and CHHaptics amplitude-matched pattern don't translate; Android v1 is pastille + one system haptic (D9).
- **Path stats bar** (`PathStatsService` / `PathBottomBar`). The pastille amount comes from the compose response, never a stats diff (per the M36 flash design D1). Wallet verification happens on web.
- **Swipe-to-delete, drafts surviving process death, tablet layouts, Play distribution.**

## Background: what exists today

### The write contract (all surfaces share it)

- **Create** — clients invoke edge function **`compose-pebble`** (`packages/supabase/supabase/functions/compose-pebble/index.ts`) with body `{payload}`: auth-forwards the JWT → `create_pebble(payload jsonb)` RPC → composes `render_svg` server-side (shared engine) → `200 {pebble_id, karma_delta, render_svg, render_version}`. **Soft-success contract:** insert succeeded but compose failed → `500` **with `pebble_id` in the body**; both web and iOS advance anyway (iOS `CreatePebbleSheet.softSuccessPebbleId(from:)`).
- **Edit** — edge function **`compose-pebble-update`** with `{pebble_id, payload}` → `update_pebble` RPC → recompose → same response shape; `karma_delta` is the enrichment delta (null/0 when unchanged). `update_pebble` (`20260426000002_pebble_media_edit.sql`) `coalesce`s scalars and does delete-then-reinsert on join tables **gated on key presence** — which is why iOS always sends every scalar and every array (`PebbleUpdatePayload.swift` doc comment). iOS soft-success on edit is any `status >= 500`.
- **Delete** — direct RPC **`delete_pebble(p_pebble_id)`**, void return (`20260411000005_security_hardening.sql`): server sums the pebble's `karma_events` and inserts a negative `pebble_deleted` clawback, then deletes with cascades. No flash — clawbacks are silent.
- **Payload shape** (snake_case): `name`, `description` (nullable), `happened_at` (ISO-8601 string — never epoch numbers, Postgres rejects them), `intensity` 1–3, `positiveness` −1..1, `visibility` (`'private'`, no UI), `emotion_id`, `domain_ids` (single-element array), `soul_ids`, `collection_ids` (0..1), `glyph_id?`, `snaps?`.
- **Karma** — `compute_karma_delta` is pure and capped at 10: base 1, +1 description, +min(cards,4), +1 souls, +1 domains, +1 glyph, +1 snaps. Flash reasons: create → `pebbleCreated`, edit → `pebbleEnriched`.
- **Latent DB bug** — the current `update_pebble` (`20260426000002_pebble_media_edit.sql:57-66`) still inserts `glyphs.shape_id` in its `new_glyph` branch, but `20260701114205_drop_glyph_shape.sql` dropped that column and recreated **only `create_pebble`**. The branch is dead — no client sends `new_glyph` on edit — but any future caller would hit a runtime error, and it violates "keep sibling RPCs symmetric". This milestone fixes it (pre-flight `[Fix]` PR).

### iOS UI shape to mirror

- Entry: `NewPebbleButton` pinned in `PathView`'s bottom `safeAreaInset` (+ empty-week "+") → **`CreatePebbleSheet`** (shell: NavigationStack, Cancel/Save, owns the `PebbleDraft`) → **`PebbleFormView`** (shared create/edit, pure UI): When/Name/Description → Mood (emotion row → `EmotionPickerSheet`; domain inline menu; valence row → `ValencePickerSheet` 3×3) → Glyph (`GlyphPickerSheet`) → Souls (`SelectedSoulsRow` → `SoulPickerSheet`, multi-select + inline `CreateSoulSheet`) → Collection inline menu → inline `saveError`.
- Validity (`PebbleDraft.isValid`): trimmed name + `emotionId` + `domainId` + `valence` all required; everything else optional; `happenedAt` defaults to now, no min/max.
- `EmotionPickerSheet`: category → emotion chip grid; category order from **`EmotionCategoryOrdering`** (static 9-key table keyed on the draft's valence).
- Edit: row tap → **`PebbleDetailSheet`** (read sheet, Edit in toolbar) → **`EditPebbleSheet`**: loads detail via a direct PostgREST embedded select (NOT an RPC), prefills `PebbleDraft(from: detail)`, renders the current `render_svg` at the top of the form, saves via `compose-pebble-update`.
- Delete: long-press `.contextMenu` on `PathPebbleRow` → `PathView.confirmationDialog` → `delete_pebble` → reload. No swipe-to-delete.
- After create: karma flash (`KarmaNotificationService` pastille) and `onCreated(pebbleId)` opens **`PebbleDetailSheet` as the reveal** + timeline reload. No celebration screen.
- Size: ~20–22 files, ~2,500–2,900 LOC Swift across the sheets, pickers, read views and karma flash (before models/services).

### Android base (post-M38)

Exists: `SupabaseService` (Auth + Postgrest + Storage — **no Functions plugin**), `PathService`, `EmotionPaletteService` + palette models, `Valence`, `Pebble`, the `PebbleSvg`/outline render stack, `SnapURLCache`/`PebbleSnapRepository` (read), theme/typography/components, `ReferenceSlugs`/`ReferenceStrings`, screenshot-preview CI (`ui-screenshots`), config-baked debug APK. `PathScreen` rows have **no click handlers** yet.

Missing (this milestone's inventory): Functions plugin; a write service; `Domain`/`SoulWithGlyph`/`PebbleCollection`/`Glyph` models; a `ReferenceDataService` analog; `PebbleDraft` + payloads + `ComposePebbleResponse` + `PebbleDetail`; a `KarmaNotificationService` analog; all create/edit/delete/detail/picker UI; the valence-picker imagery (deferred by `apps/android/CLAUDE.md` "until the create flow lands" — that's now).

## Milestone-level settled choices

- **Scope:** pebble create + edit + delete + the detail read sheet both flows hinge on. Snaps and carving deferred. Single shared form, modal sub-pickers — iOS parity, not a wizard.
- **iOS is the reference; deviations are named decisions** (D2, D5, D9, D11, D15); everything else ports structurally.
- **DB symmetry repair rides along:** `update_pebble` loses its `shape_id` reference via migration before any Android write code lands.

## Sub-project decomposition

Five PRs, strictly sequenced **Fix → A → B → C → D**. Each sub-project gets its own spec + plan per superpowers conventions.

### Fix (pre-flight) — `update_pebble` still references the dropped `glyphs.shape_id`

Migration recreating `public.update_pebble` identical to `20260426000002` except the `new_glyph` branch inserts `(user_id, name, strokes, view_box)` — mirroring what `20260701114205_drop_glyph_shape.sql` did to `create_pebble` (keep the branch; only the column goes). Regenerate + commit `types/database.ts`. Not folded into A: it's a `fix`-species change to `packages/supabase` with its own path-filters, and the repo applies one species label per issue.

Issue title: `[Fix] update_pebble still inserts glyphs.shape_id in its dead new_glyph branch` — labels: `fix`, `db`.

### A — Write plumbing: Functions client, reference data, form models

Everything non-visual that B–D compose against, CI-green and unit-tested — the payload contract, the soft-success contract, and the cached reference lists.

- **Functions plugin** added to `SupabaseService` (`install(Functions)`, catalog addition via the existing supabase-kt BOM).
- **`PebbleWriteService`** (D2): `create(draft)`, `update(pebbleId, draft)`, `delete(pebbleId)` — sealed result type; soft-success body parsing extracted as a pure, unit-tested function. **Front-load the risk-1 spike:** prove the 5xx body is readable through supabase-kt, or fall back to raw Ktor, before any UI exists.
- **Models** (hand-written `@Serializable`, mirroring the iOS files): `Domain`, `SoulWithGlyph`, `PebbleCollection`, `Glyph` + `GlyphStroke`, `PebbleDraft` (+ `isValid`), `PebbleCreatePayload`/`PebbleUpdatePayload` (D3), `ComposePebbleResponse`, `PebbleDetail` (D7 — lands here so B consumes it), `KarmaReason`.
- **`ReferenceDataService`** (D11): domains/souls/collections cached at splash, `refreshSouls()`/`refreshCollections()`; new CompositionLocal.
- **`EmotionCategoryOrdering`** port (D14) — pure table + pure function.
- Unit tests: payload key-set + explicit-null encoding, ISO-8601 `happened_at`, draft validity, soft-success `pebble_id` extraction, category-ordering parity with the iOS table, `SoulWithGlyph` decoding.

Issue title: `[Feat] Android pebble write plumbing: edge-function client, reference data, form models` — labels: `feat`, `android`, `api`, `core`.

### B — Pebble detail sheet + delete

The read surface — which the timeline needs anyway (rows currently do nothing on tap) and which C's post-create reveal targets. Sequencing B before C means create ships with the *real* iOS reveal on day one instead of a throwaway interim (D1).

- Row tap → **`PebbleDetailScreen`** (D5): loads via the direct PostgREST embedded select mirroring `PebbleDetailSheet.load()`, renders the read view (banner with `render_svg` via existing `PebbleSvg`, title, meta pills, privacy badge — `apps/ios/Pebbles/Features/Path/Read/*`). Edit slot exists from day one (stub until D).
- **Delete** (D8): long-press on `PathPebbleRow` (`combinedClickable`) → `DropdownMenu` destructive Delete → `AlertDialog` confirm ("This can't be undone.") → `delete_pebble` RPC → timeline reload. No flash.
- Screenshot previews: detail variants (with/without glyph, description, souls), delete dialog.

Issue title: `[Feat] Android pebble detail sheet + long-press delete` — labels: `feat`, `android`, `ui`, `core`.

### C — Create funnel: form, pickers, karma flash, reveal

The milestone's centerpiece — `NewPebbleButton` → full-screen form → save → pastille → detail reveal.

- `NewPebbleButton` pinned at `PathScreen`'s bottom + empty-week "+" hook.
- **`CreatePebbleScreen`** (shell: top bar Cancel/Save + saving spinner, owns the draft) hosting the shared **`PebbleForm`** (D6): When row (D15), Name, Description, Mood section (emotion row, domain dropdown, valence row), Glyph section (D13), Souls section (D12 inline creation), Collection dropdown, inline `saveError`.
- **Pickers as `ModalBottomSheet`s** (D5): `EmotionPickerSheet` (D14 ordering), `ValencePickerSheet` (3×3 grid — port the 9 valence-picker images from the iOS asset catalog now), `SoulPickerSheet`, `GlyphPickerSheet` (selection-only).
- **Karma flash v1** (D9/D10): `KarmaNotificationService` analog + `KarmaEarnedCapsule` pastille overlaid in `RootScreen`; save path mirrors `CreatePebbleSheet.save()` including soft-success (skip the flash, still reveal).
- Reveal: `onCreated(pebbleId)` → open B's detail screen + timeline reload.
- Unit tests: draft validity edges, error-message mapping (D16); screenshot previews: form states, each picker, pastille.

If the PR balloons, the pre-agreed slice is *form + save + reveal* first, *karma flash* second — but aim for one PR.

Issue title: `[Feat] Android create pebble: form, pickers, karma flash, post-create reveal` — labels: `feat`, `android`, `ui`, `core`.

### D — Edit pebble

The same form, prefilled, with the live render on top.

- **`EditPebbleScreen`**: loads `PebbleDetail` (reuses B's fetch), prefills `PebbleDraft(from = detail)` (first domain, first collection, valence from `(positiveness, intensity)`), seeds `selectedGlyph`, renders `render_svg` at the top at `sizeGroup`-derived height with the palette stroke color; load-error + Retry state.
- Save via `PebbleWriteService.update` → `compose-pebble-update`; response `render_svg` swaps in; `pebbleEnriched` flash (delta > 0 only); edit soft-success = any 5xx → advance (iOS parity).
- Wire the detail screen's Edit button; `onSaved` reloads detail + timeline.
- Screenshot previews: edit form with render header at all three heights.

Issue title: `[Feat] Android edit pebble` — labels: `feat`, `android`, `ui`.

**Dependencies:** Fix blocks nothing technically (the branch is dead) but lands first as contract hygiene; A blocks B (models) and C/D (write service, refs); B blocks C (reveal target) and D (detail load + Edit slot); C blocks D (the shared form).

## Core design decisions (settled)

- **D1 — Detail ships before create.** iOS reveals a new pebble by opening `PebbleDetailSheet`; building C first would force an interim reveal thrown away one PR later, and the timeline needs tap-to-detail regardless. Rejected: minimal-detail-inside-C (splits the read view across PRs); simpler-reveal-then-upgrade (throwaway work).
- **D2 — All writes go through one `PebbleWriteService`; UI never touches `functions.invoke`.** Deliberate structural deviation from iOS (sheets call the client directly there): the bootstrap convention keeps supabase-kt out of previewable composables, and the soft-success transport question (risk 1) must be swappable without touching UI. Sealed results — `ComposeResult.Success(response)` / `SoftSuccess(pebbleId)` / `Failure(userMessage)` — so screens branch on semantics, not exception classes. Soft-success rules mirror iOS exactly: create = 5xx body decodes a `pebble_id`; edit = any status ≥ 500. Transport: supabase-kt Functions plugin first; if its exception shape doesn't expose the 5xx body, drop to a raw Ktor POST against `{supabaseUrl}/functions/v1/compose-pebble` via the client's own `httpClient` with the session bearer — one code path inside the service either way.
- **D3 — Always send every scalar and every array key; explicit nulls.** The `update_pebble` coalesce/key-presence contract makes omitted keys mean "keep"; iOS's answer — send everything, always — is proven on two surfaces and simpler than dirty-tracking. Kotlin mechanics: payload classes declare **no default values** and the write service's `Json` sets `explicitNulls = true`, so `description: null` / `glyph_id: null` are emitted as literal nulls (edit must be able to clear both). `happened_at` encodes as ISO-8601 UTC string. Create omits `snaps`; edit snap handling per risk 5. A unit test asserts the **exact encoded key set** of both payloads — the Android half of the cross-surface contract.
- **D4 — `PebbleDraft` is a plain data class in `remember { mutableStateOf(...) }` at the screen shell**, mutated by copy. No `rememberSaveable` in v1: the draft holds UUIDs/Instant (needs a custom Saver), the activity is portrait-locked, and iOS loses in-progress drafts on process death too — parity, accepted (risk 7).
- **D5 — Navigation: form/detail/edit are full-screen conditionally-composed surfaces; sub-pickers are `ModalBottomSheet`s; never stack modal bottom sheets.** iOS stacks sheets three deep; Compose's `ModalBottomSheet` is a separate window — stacking fights scrim ordering, back handling, and IME. The honest mapping: first level (create/detail/edit) = full-screen surface conditionally composed over `PathScreen` (the established `fullScreenCover` analog, `BackHandler` = Cancel); second level (pickers) = exactly one `ModalBottomSheet`; third level (inline soul create) = a plain dialog over the sheet (well-behaved). Detail → Edit is a surface swap, not a stack.
- **D6 — One shared `PebbleForm` composable for create + edit, pure UI.** Takes draft + reference lists + `selectedGlyph` + optional `renderSvg`/`strokeColor`/`renderHeight` + `saveError` + callbacks; knows nothing about Supabase or its host shell. Stateless above scroll state → screenshot-previewable in every state.
- **D7 — Detail loads via the direct PostgREST embedded select, hand-written `PebbleDetail`.** Mirror the iOS query string verbatim (including `glyph:glyphs(id, name, strokes, view_box)` for the edit prefill). Decode junction wrappers with intermediate row types flattened into the clean model (the established `EmotionWithPaletteRow` pattern). `snaps` decodes but drives no UI this milestone. Valence derives via the existing `Valence.fromOrDefault`.
- **D8 — Delete = long-press context menu on the row, iOS parity over Android convention.** `combinedClickable` + `DropdownMenu` → confirm `AlertDialog` → RPC → reload. Swipe-to-delete rejected (iOS doesn't have it; destructive swipe with no undo is worse than a confirmed menu). A detail-sheet Delete button was considered as more discoverable — deferred, not chosen; revisit if on-device testing shows the long-press is undiscoverable.
- **D9 — Karma flash v1: themed pastille + countdown ring + one system haptic; no sound; overlay in `RootScreen`.** Port `KarmaNotificationService` as a plain class (`mutableStateOf` capsule content, 2.5 s auto-dismiss, tap to dismiss, `amount > 0` guard) and `KarmaEarnedCapsule` as a bottom-center capsule `Surface` (tonal elevation standing in for liquid glass; countdown ring = a `Canvas` arc). Haptic: `HapticFeedbackConstants.CONFIRM` — honest, zero-dependency; the waveform pattern and ceramic sound are explicitly *not* approximated. Host: a `Box` overlay atop `RootScreen`'s authed branch — because D5 keeps form surfaces in-composition, the pastille floats above them with zero window trickery (simpler than iOS's pass-through window). Known limit: cannot float above an open `ModalBottomSheet` — irrelevant today since flashes fire post-save when sheets are closed. Lands inside C: ~250 LOC and unverifiable without a create to trigger it.
- **D10 — Flash semantics: create → `PEBBLE_CREATED`, edit → `PEBBLE_ENRICHED` (delta > 0 only), delete → silent.** `KarmaReason` enum with en/fr string-resource labels (`LocalizationParityTest` coverage). Amounts come exclusively from the edge-function response's `karma_delta` — never a client-side stats diff (settled by the M36 flash design D1).
- **D11 — `ReferenceDataService` loads at splash, mirrors iOS, plus one deliberate improvement.** Domains/souls/collections in parallel, session-cached, no retry (empty pickers on offline launch, recovers next launch); `load()` kicked from `RootScreen` alongside `palettes.load()`. Improvement: after an inline soul insert succeeds, `refreshSouls()` fire-and-forget — iOS's picker appends only to its local list while `SelectedSoulsRow` filters against stale `refs.souls`, so a freshly created + selected soul silently renders no pill. Android closes that gap; a companion iOS `[Fix]` issue is filed rather than porting the bug (risk 4).
- **D12 — Inline soul creation: in scope, name-only, direct insert.** iOS `CreateSoulSheet` does a plain `public.souls` insert (not the `new_souls` payload key), and `souls.glyph_id` has a system-glyph default, so name-only insert is valid. Android v1: a dialog (D5 third level) with one `PebblesTextInput` + Save → insert `{name}` → select back the `SoulWithGlyph` shape → auto-select + D11 refresh. The iOS glyph-swap row is deferred (soul glyph management belongs to a Profile milestone).
- **D13 — Glyph picking is selection-only; thumbnails render strokes through the existing AndroidSVG pipeline.** List via the iOS `GlyphService.list()` select (RLS exposes own + system + entitled); **no "Carve new glyph" row at all** in v1 (a disabled entry advertises a feature that doesn't exist). Long-press "Remove glyph" on the form's glyph row uses the D8 `DropdownMenu` idiom. Rendering: build a minimal SVG string from `GlyphStroke.d` + `width` + `view_box` (stroke `currentColor`, fill none) and feed the existing `PebbleSvg` path with case-appropriate tint. One composable (`GlyphImage`), reused by picker grid, form row, and soul pills.
- **D14 — `EmotionCategoryOrdering` ports as a pure table.** `Map<Pair<ValenceSizeGroup, ValencePolarity>, List<String>>` + medium-neutral default, consumed by grouping the cached palette rows by `categorySlug` (emotions sorted by localized name). Staged selection with Done/Cancel and tap-again-to-clear, like iOS. Unit test asserts the table equals the iOS source (9 keys × 7 slugs).
- **D15 — When row: Material3 `DatePickerDialog` then `TimePicker` dialog, two taps.** Compose has no inline combined date+time control. The row shows the locale-formatted date+time; tap opens the date picker, confirm opens the time picker, both prefilled. Divergence from iOS accepted and documented — fighting Material's pickers with a custom widget is worse. Default now, no bounds.
- **D16 — Error surfacing mirrors iOS `userMessageForPebbleSaveError`, minus photo cases.** One shared mapping in `PebbleWriteService`: `media_quota_exceeded`/`P0001` → quota message (defensive); everything else → "Couldn't save your pebble. Please try again." Inline in the form's error section; en/fr.
- **D17 — Visibility stays `'private'` with no UI.** Draft carries it, payloads always send it, detail renders the badge read-only. Same as both other surfaces.

## Risks and open questions

1. **supabase-kt error-body access for soft-success (highest uncertainty).** The create contract hinges on reading `pebble_id` out of a **5xx body**. supabase-kt maps non-2xx to `RestException` subtypes whose retained payload shape must be verified against a real `compose-pebble` 500 — not assumed. Mitigation: A's first task is the spike; the D2 seam makes the raw-Ktor fallback a service-internal swap. Exit criterion: a unit-tested `softSuccessPebbleId(...)` working against a captured real error body.
2. **Edit-form `render_svg` header.** Rendering in a scrolling column at `sizeGroup`-derived heights is new for `PebbleSvg` (previously fixed-size rows). Low risk — D's plan includes a screenshot preview at all three heights before wiring.
3. **Date/time UX divergence** (D15): two dialogs vs. iOS inline. Accepted; flag in C's device pass in case the two-step feels heavy enough to justify a custom row later.
4. **Reference staleness after inline soul creation.** Android fixes its side (D11); the latent iOS gap (`SelectedSoulsRow` drops ids missing from stale `refs.souls`) gets a companion iOS `[Fix]` issue so the surfaces don't silently diverge.
5. **Cross-surface edit clobbering a photo.** An always-send `snaps: []` on edit would delete an iOS-attached snap the user can't even see on Android. Options: round-trip (`echo PebbleDetail.snaps` into the update payload, ~10 lines) vs. accept the loss. **Decide in A's spec; leaning round-trip** — silent data loss the user can't see is the worse failure.
6. **`ModalBottomSheet` + IME.** Only the soul-create dialog takes text over a sheet; the form's fields live on the full-screen surface (D5). `imePadding()` on the form; verify description multi-line growth on device.
7. **Draft loss on process death / config change** (D4): accepted for v1; `rememberSaveable` + custom Saver is the known upgrade.
8. **Milestone naming/numbering:** drafted **M39 · Android Record Pebble**; GitHub numbering may differ (bootstrap precedent). Docs referring to "M39" mean this milestone.
9. **Maintainer device dependency** (carried from M38): agents get to CI green + screenshot artifacts; only the maintainer's device gets to "verified". The config-baked debug APK is the delivery channel.

## Verification strategy

Each PR proves itself in three rungs: **CI green → unit tests → maintainer on device**, with the `ui-screenshots` artifact as the agent-side UI review channel.

- **Fix:** migration applies cleanly; a smoke call of `update_pebble` with a `new_glyph` payload (the previously-dead branch) succeeds; `db:types` regenerated with a clean committed diff; grep confirms no client sends `new_glyph`.
- **A:** CI green; payload key-set/null-encoding, ISO-8601, validity, ordering-parity, soft-success-parse, and decode tests pass; the risk-1 spike's captured-body test committed. No device checkpoint (no UI).
- **B:** CI green; screenshot previews reviewed; on device with a real account: tap a pebble → detail renders SVG/pills/badge matching iOS side-by-side; long-press → delete → confirm → row gone, and the **negative `pebble_deleted` event appears in the wallet on web** (full clawback); no flash fired.
- **C:** CI green; validity + error-mapping tests; screenshot previews; on device: full create with every field (emotion via valence-ordered picker, domain, valence, glyph, 2 souls incl. one created inline, collection) → **pastille shows the server's `karma_delta`** (cross-check against `compute_karma_delta` and the wallet on web) → detail reveal opens → timeline reloaded; a minimal create (name+emotion+domain+valence) flashes the formula-expected amount; Save disabled until valid; cancel discards; fr locale pass.
- **D:** CI green; on device: edit the C-created pebble — prefill correct (incl. valence cell and glyph thumbnail), render at top, change name+description+glyph → save → recomposed SVG swaps in, `pebbleEnriched` flash **only when the delta is positive** (an enrichment-neutral edit stays silent), detail + timeline reflect changes; then the **milestone exit round-trip: create → flash → reveal → edit → delete on one pebble, wallet net delta zero after the delete**.

## Arkaik

No new nodes — the record flow's live shape is `F-record-pebble → V-quick-pebble-editor` + `V-pebble-detail` (the stepped-wizard nodes are `archived`). As B/C/D land, update `docs/arkaik/bundle.json` descriptions/status for `V-quick-pebble-editor`, `V-pebble-detail`, and `V-soul-create` (Android now implements them), and add `"android"` to **`V-karma-flash`** (currently `platforms: ["ios"]`); run `node .claude/skills/arkaik/scripts/validate-bundle.js`. Lab Notes remain deferred per the M38 rule — fold into the single milestone-level bilingual note when a distributable build exists.

## Open questions deferred (not blocking)

- Snap attach/edit on Android (own milestone; risk 5's round-trip rule is the only snap handling here).
- Glyph carving on Android (canvas capture + simplification stack).
- Karma sound + rich haptics; pastille-above-bottom-sheet hosting if a flash source inside a sheet ever appears.
- `PathStatsService`/`PathBottomBar` port (karma number + ripple on the timeline).
- Swipe or detail-button delete affordance revisit after discoverability feedback (D8).
- `rememberSaveable` draft persistence (D4).
