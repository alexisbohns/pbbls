# Android moments composer (#1048) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move `apps/android` off every compatibility object of the moments/pebbles split (`path_pebbles`, the `pebbles` columns that moved, `pebble_drafts`, `delete_pebble_media`, `compose-pebble[-update]`, `render_svg`) and replace the eleven-step composer with a three-step **Capture** flow (`save_moment`) and a six-step **Complete** flow (`complete_pebble`), drawing every pebble's derived glyph from its strokes. Mirrors the iOS plan for #1047 one to one.

**Architecture:** Two new seams in `core/data` own every moment read (`MomentReadService`: `path_moments`, `v_moments_full`, `resolve_pebble_glyph`) and write (`MomentWriteService`: `save_moment`, `complete_pebble`, `delete_pebble`, `delete_moment`, the karma read-back). The stone is composed natively from the nine bundled shape seeds plus the glyph's `strokes`/`view_box` into the existing `PebbleSvgModel`, and traced by the existing `PebbleStaticRender` Canvas code. Capture and Complete are two new `@HiltViewModel` flows on the existing step scaffold and step bodies, each with a pure state machine that fires a haptic on every interaction (M58 D4).

**Tech stack:** Kotlin, Jetpack Compose (material3 `1.5.0-alpha27`), Hilt, Navigation 3, supabase-kt 3.8.0 (`postgrest`, `storage`), kotlinx.serialization 1.11.0, JUnit 4 + Robolectric + Compose Preview Screenshot Testing, ktlint, Android Lint.

**Read first (the SQL is the contract):** `docs/superpowers/specs/2026-10-11-moments-and-pebbles-split-design.md` §1, §3, §4, §6; `packages/supabase/supabase/migrations/20261012090100_moments_split_writers.sql` and `20261012090200_moments_split_readers.sql`; `apps/android/CLAUDE.md`; `docs/superpowers/specs/2026-08-23-ios-record-flow-design.md` (the flow being replaced). Skills: `android-skills:android-dev`, `android-skills:compose`, `android-skills:android-testing`, `navigation-3`, `android-skills:modularization`, `gh-stack`, `android-screenshot-gate`.

---

## 0. Ground rules for every part

**Commands** (always from `apps/android`, with the SDK exported first, or `scripts/gradle-if-sdk.sh` silently no-ops):

```bash
cd apps/android
export ANDROID_HOME=$HOME/Library/Android/sdk
./gradlew testDebugUnitTest --tests 'app.pbbls.android.core.model.PathMomentDecodingTest'   # one class
./gradlew ktlintCheck testDebugUnitTest assembleDebug                                     # the CI unit gate
./gradlew compileDebugScreenshotTestKotlin                                                # previews compile
./gradlew lint                                                                            # Android Lint (warningsAsErrors)
```

`./gradlew validateDebugScreenshotTest` is a **CI-only** gate: never commit references rendered locally, never widen the threshold. Every part that changes UI turns the screenshot check red by design; re-baseline by adding the `rebaseline-screenshots` label to that part's PR and approving the held runs (the `android-screenshot-gate` skill). New previews are listed per part; no part re-baselines locally.

**Baseline recorded on untouched `main` (8a6b6023), 2026-10-13:** `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew -p apps/android assembleDebug testDebugUnitTest --console=plain` → `BUILD SUCCESSFUL in 1m 20s`, 1061 unit tests in 147 classes, 0 failures.

**Stack.** One part = one branch = one PR, chained with `gh stack` (read `.claude/skills/gh-stack/SKILL.md`; fetch and fast-forward before stack pushes because the rebaseline bot commits to branches). Every part passes `ktlintCheck testDebugUnitTest assembleDebug lint` on its own and ships a working app: `main` auto-publishes to Play internal testing on every push.

| # | Branch | Part | One line |
|---|---|---|---|
| 1 | `feat/1048-android-moments-data` | Moment models and services | New read/write seams, models, payloads, whole-second timestamps, error mapping, verbatim decoder tests. Additive only. |
| 2 | `feat/1048-android-moments-readers` | Readers and editor on moments | Path, read page, soul/collection detail, edit, delete and share move onto the new seams; the installed app's reads are unbroken. |
| 3 | `feat/1048-android-moments-stone` | The glyph drawn from strokes | Stone composed from shape seeds plus `glyph.strokes`; every `render_svg` surface switches; blank-stone renderer. |
| 4 | `feat/1048-android-moments-path` | Bare moments on Path and the read page | Blank stones for uncompleted moments, the bare-moment read page, the two-choice delete UX. |
| 5 | `feat/1048-android-moments-complete` | Complete flow | Six-step Complete flow, per-moment autosave, success screen, the read page's call to action. |
| 6 | `feat/1048-android-moments-capture` | Capture flow | Three-step Capture flow, the "make it a pebble now" chain, New pebble opens Capture. |
| 7 | `feat/1048-android-moments-retire` | Collection glyph and retirement | Collection glyph picker; the eleven-step flow, the all-at-once composer, drafts and `compose-pebble` removed; docs and Arkaik. |

Parts 5 and 6 are in the reverse of the brief's suggested order on purpose: Capture's secondary action chains into Complete, so Complete is the lower layer. Between Part 2 and Part 6 the old composers keep creating pebbles through `compose-pebble` (a compat object that still works and returns `render_svg: null`); Part 6 stops every call to it and Part 7 deletes the code.

PR conventions: conventional title `feat(android): …`, body starts `Part of #1048` (`Resolves #1048` on Part 7), labels and milestone inherited from #1048 after confirming with the maintainer, commits end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Lab Note: the user-facing parts (4, 5, 6, 7) are covered by Arkaik acceptances, so label them `no-lab-note` (the maintainer's standing preference); if no acceptance covers a part, author the note with the repo `lab-note` skill. Never write a Kritik finding id in a PR body.

---

## 1. Supabase touchpoint inventory

Every call site that reaches Supabase on `main`, found with
`grep -rnE '\.from\(|\.rpc\(|functions|storage|render_svg|render_version|"glyph_id"|happened_at|pebble_drafts|delete_pebble|path_pebbles|compose-pebble|snaps\(|pebble_souls|collection_pebbles' app/src/main/kotlin`.
Paths are relative to `app/src/main/kotlin/app/pbbls/android/`. "P2" means "changed in Part 2".

### 1a. Touchpoints that change

| # | file:line | Today | Becomes |
|---|---|---|---|
| 1 | `core/data/PathService.kt:36` | `rpc("path_pebbles")` (deprecated web compat) | `MomentReadService.loadPath()` → `rpc("path_moments")` (P1 adds, P2 switches; `PathService` deleted P2) |
| 2 | `core/model/Pebble.kt:18,28` | `path_pebbles` row: `happened_at`, `render_svg` | `PathMoment` + `PathPebble` (P1); `Pebble` deleted P2 |
| 3 | `core/data/PebbleDetailService.kt:32` | `from("pebbles")` | `MomentReadService.loadMoment(id)` → `from("v_moments_full")` (P1/P2); file deleted P2 |
| 4 | `core/data/PebbleDetailService.kt:44-51` | select `name, description, happened_at, render_svg, render_version, glyph_id, glyph:glyphs(...), emotion:emotions(...), pebble_domains(...), pebble_souls(...), collection_pebbles(...), snaps(...)` (dropped or moved columns) | select `id, name, description, happened_at, created_at, snaps, pebble` on `v_moments_full` (P1) |
| 5 | `core/model/PebbleDetail.kt:28-48` | `happened_at`, `render_svg`, `render_version`, `glyph_id`, junction wrappers | `MomentDetail` + `MomentPebble` + `MomentSnap` + `SoulRef` (P1); `PebbleDetail` deleted P2 |
| 6 | `core/data/SoulsService.kt:88-96` | `from("pebbles")` select `name, happened_at, render_svg` + `pebble_souls!inner`, `order("happened_at")` | `MomentReadService.loadMomentsWithSoul(soulId)` → `v_moments_full` with `pebble=cs.{"souls":[{"id":…}]}`, `order("happened_at" desc)` (P1); `loadPebbles` deleted P2 |
| 7 | `core/data/CollectionsService.kt:81-89` | same shape with `collection_pebbles!inner` | `MomentReadService.loadMomentsInCollection(id)` → `pebble=cs.{"collections":[{"id":…}]}` (P1); `loadPebbles` deleted P2 |
| 8 | `core/data/PebbleWriteService.kt:135` | `rpc("delete_pebble")` with the row id (a moment id since the backfill; deletes only the pebble and strands the moment) | P2: Path, soul and collection rows call `MomentWriteService.deleteMoment` (`rpc("delete_moment")` + Storage cleanup of the returned paths). P4: the "keep the moment" choice calls `MomentWriteService.deletePebble(pebble.id)`. |
| 9 | `core/data/PebbleWriteService.kt:159` (`compose-pebble`) | create through the deprecated edge function | P5/P6: `complete_pebble` (Complete) and `save_moment` (Capture). The old composers keep calling it until P6 unhooks them and P7 deletes `PebbleWriteService`. |
| 10 | `core/data/PebbleWriteService.kt:159` (`compose-pebble-update`) | edit through the deprecated edge function | P2: `MomentWriteService.saveMoment`, then `completePebble` when the moment has your pebble |
| 11 | `core/data/PebbleWriteService.kt:179` + `core/model/ComposePebbleResponse.kt:17-24` | `pebble_id`, `render_svg`, `render_version`, `karma_delta` from the edge function | karma: `MomentWriteService.karmaDelta(pebbleId, reason)` → `from("karma_events").select("delta")`, latest row by `ref_id` + `reason` (P1, mirrors `functions/_shared/karma-delta.ts`); glyph: `MomentReadService.resolveGlyph(pebbleId)` → `rpc("resolve_pebble_glyph")` (P1). `ComposePebbleResponse` deleted P7. |
| 12 | `core/model/PebbleCreatePayload.kt:25,39` | `happened_at`, `glyph_id` keys | `MomentSavePayload` + `PebbleCompletePayload` (P1); file deleted P7 |
| 13 | `core/model/PebbleUpdatePayload.kt:32,46` | `happened_at`, `glyph_id` keys | same; class deleted P2 (edit is its only user) |
| 14 | `core/model/PebbleUpdatePayload.kt:12-18` | `PebbleSnapPayload` `{id, storage_path, sort_order}` | moved unchanged to `core/model/PebbleSnapPayload.kt` (P1); it is exactly `save_moment`'s `snaps[]` element |
| 15 | `core/data/PebbleDraftsService.kt:80,87,109,116,129` | `from("pebble_drafts")` list/load/upsert/delete/count (table dropped) | deleted P7, with Path's draft badge and the drafts list |
| 16 | `core/data/PebbleDraftsService.kt:146` | `rpc("can_use_glyph")` for a resumed draft's glyph | deleted P7 (pebbles carry no glyph) |
| 17 | `core/model/PebbleDraftPayload.kt:31,45` | `pebble_drafts.payload` keys incl. `happened_at`, `glyph_id` | deleted P7 |
| 18 | `core/data/PebbleSnapRepository.kt:113` | `rpc("delete_pebble_media")` (dropped) | removed P2. Photo removal rides `save_moment`'s whole-set `snaps`; `delete_moment_media` is not called (PD-15). |
| 19 | `core/data/SnapUploadCoordinator.kt:136` | `removeExisting()` → `deletePebbleMedia` | `detachExisting()` clears the form and returns the snap; the caller deletes its files after a successful save (P2) |
| 20 | `features/path/EditPebbleViewModel.kt:142,200-252` | `PebbleDraft.from(PebbleDetail)`, eager `delete_pebble_media`, update payload | rewritten on `MomentDetail` + `saveMoment`/`completePebble` (P2) |
| 21 | `features/path/record/RecordFlowViewModel.kt:362,467,520` | `PebbleDraftPayload`, `drafts.saveAsDraft`, create payload | deleted P7 |
| 22 | `features/path/create/CreatePebbleViewModel.kt:242,284,316` | same | deleted P7 |
| 23 | `features/path/DraftsViewModel.kt`, `DraftsScreen.kt` | `pebble_drafts` list | deleted P7 |
| 24 | `features/path/PathViewModel.kt:223` and its `drafts.count()` | `path_pebbles` load, draft count | `loadPath()` (P2); draft count removed (P7) |
| 25 | `core/model/SharedPebbleLink.kt:9` via `features/path/PebbleDetailScreen.kt:83` | `/p/<id>` built from the detail key's id | built from `MomentPebble.id` (P2): a moment id is not its pebble id for anything created after the split |
| 26 | `navigation/PebblesEntryProvider.kt:278-283` (`CreatePebble.onCreated`) | opens the detail with the returned `pebble_id` | P2: pops only (the detail key now carries a moment id, and the compat wrapper returns a pebble id) |
| 27 | `features/profile/GroupPebblesByMonth.kt:22` | `Pebble.happenedAt` | `PathMoment.happenedAt` (P2) |
| 28 | `core/data/CollectionsService.kt:59,68` | `from("collections")` select `id, name, mode, pebble_count:collection_pebbles(count)` | unchanged P1–P6; P7 adds `glyph_id, glyph:glyphs(id, name, strokes, view_box)` |
| 29 | `core/data/CollectionsService.kt:101` | insert `collections` | P7 adds the `glyph_id` key |
| 30 | `core/data/CollectionsService.kt:112` | update `collections` | P7 adds the `glyph_id` key (explicit null clears) |
| 31 | `di/SupabaseModule.kt:50` | `Functions` plugin; comment names compose-pebble | P7 rewrites the comment; the plugin stays for `delete-account` |
| 32 | `core/ui/render/PebbleStaticRender.kt:48` and callers `PebbleThumbnail.kt:56`, `PebbleReadPetroglyph.kt:61`, `PebbleForm.kt:116` | parse and trace `render_svg` | P3: trace a `PebbleSvgModel` composed from a shape seed + `glyph.strokes` |

### 1b. Touchpoints verified unchanged

| # | file:line | Object | Why it is safe |
|---|---|---|---|
| 33 | `core/data/PebbleSnapRepository.kt:57,75,102` | Storage bucket `pebbles-media` sign/upload/delete | bucket, RLS and `{user_id}/{snap_id}` prefix unchanged (spec D9) |
| 34 | `core/data/SoulsService.kt:63,74` | `souls` + `glyphs(...)` + `pebbles_count:pebble_souls(count)` | `souls`, `pebble_souls` untouched |
| 35 | `core/data/SoulsService.kt:108,132,149` | `souls` insert/update/delete | untouched |
| 36 | `core/data/SoulsService.kt:158` | `glyphs` by id | untouched |
| 37 | `core/data/CollectionsService.kt:124` | `collections` delete | `collection_pebbles` still cascades |
| 38 | `core/data/ReferenceDataService.kt:118,155` | `souls` insert/select | untouched |
| 39 | `core/data/ReferenceDataService.kt:145` | `v_domains_with_glyph` | untouched |
| 40 | `core/data/ReferenceDataService.kt:165` | `collections` `id, name` | untouched (the picker needs no glyph) |
| 41 | `core/data/ProfileService.kt:62,138` | `profiles` | untouched |
| 42 | `core/data/ProfileService.kt:69` | `glyphs` strokes | untouched |
| 43 | `core/data/ProfileService.kt:81` | `collections` with `collection_pebbles(count)` | untouched |
| 44 | `core/data/ProfileService.kt:99,122` | `update_profile`, `set_handle` | untouched |
| 45 | `core/data/ProfileService.kt:156` | edge function `delete-account` | still deployed (`packages/supabase/supabase/functions/delete-account`) |
| 46 | `core/data/PathStatsService.kt:124` | `v_karma_summary` | survived the column drop (Postgres refuses to drop a column a view reads) |
| 47 | `core/data/PathStatsService.kt:133` | `v_ripple` | same |
| 48 | `core/data/PathStatsService.kt:142` | `get_profile_engagement` | its body reads only `pebbles.created_at` (`20260516104231`) |
| 49 | `core/data/AchievementsService.kt:128,136` | `achievements`, `achievement_unlocks` | untouched |
| 50 | `core/data/AchievementsService.kt:146` | `check_achievements` | kept; fired after `complete_pebble` (PD-9) |
| 51 | `core/data/GlyphService.kt:66,78,101,120` | `glyphs`, `glyph_entitlements` | untouched |
| 52 | `core/data/GlyphMarketService.kt:87,110,136,165` | `glyphs`, `glyph_entitlements`, `v_glyph_market`, swap RPC | untouched |
| 53 | `core/data/EmotionPaletteService.kt:46` | `v_emotions_with_palette` | untouched |
| 54 | `core/data/ConsentService.kt:45,55,74` | `user_consents`, consent RPC | untouched |
| 55 | `core/data/ConnectionsService.kt:51,61,72,84,97` | connection RPCs | untouched |
| 56 | `core/data/LogsService.kt:85,141,158,171,192` | `v_logs_with_counts`, `log_reactions` | untouched |
| 57 | `core/data/SupabaseService.kt:302,309` | `profiles` | untouched |
| 58 | `core/model/LabConfig.kt:24` | public assets bucket URL | untouched |

### 1c. New touchpoints

| Object | Called from | Part |
|---|---|---|
| `rpc("path_moments")` | `MomentReadService.loadPath` | P1 |
| `from("v_moments_full")` by id, by soul, by collection | `MomentReadService` | P1 |
| `rpc("resolve_pebble_glyph", {p_pebble_id})` | `MomentReadService.resolveGlyph` | P1 |
| `rpc("save_moment", {payload})` | `MomentWriteService.saveMoment` | P1 |
| `rpc("complete_pebble", {payload})` | `MomentWriteService.completePebble` | P1 |
| `rpc("delete_moment", {p_moment_id})` | `MomentWriteService.deleteMoment` | P1 |
| `rpc("delete_pebble", {p_pebble_id})`, pebble id only | `MomentWriteService.deletePebble` | P1 |
| `from("karma_events").select("delta")` | `MomentWriteService.karmaDelta` | P1 |

Deliberately **not** called: `path_pebbles`, `v_pebbles_full`, `create_pebble`, `update_pebble`, `compose-pebble`, `compose-pebble-update` (all after P6), `delete_moment_media` (PD-15), `set_collection_glyph` (PD-19), `get_shared_pebble` (Android only builds the `/p/` URL; the web page reads it), `report_content` (Android has no report UI: `grep -rn report_content app/src` is empty).

**Count:** 32 touchpoints change, 26 verified unchanged, 8 new. Nothing is unmapped.

### 1d. Every composable that shows a pebble today

| Composable | Where | Renders today | After |
|---|---|---|---|
| `PathPebbleRow` → `PebbleThumbnail` | `features/path/components/PathPebbleRow.kt:109` | outline backdrop + `PebbleStaticRender(render_svg)` | P2 backdrop only; P3 composed stone; P4 blank stone for uncompleted moments |
| `PebbleRow` → `PebbleThumbnail` | `core/ui/PebbleRow.kt:82` (soul and collection detail) | same | P2/P3 as above (these rows always carry a pebble) |
| `PebbleReadBanner` → `PebbleReadPetroglyph` | `features/path/read/PebbleReadBanner.kt:90` | backdrop + `render_svg` | P3 composed stone; P4 blank stone |
| `PebbleSnapFrame` → `PebbleReadPetroglyph` | `features/path/read/PebbleSnapFrame.kt:71` | same, over the photo | same |
| `RecordSuccessStep` → `PebbleReadPetroglyph` | `features/path/record/steps/RecordSuccessStep.kt:69` | `render_svg` (null since the split) | P3 takes a glyph; P5 reuses it as Complete's success screen |
| `PebbleForm` header (edit) | `features/path/create/PebbleForm.kt:116` | `PebbleStaticRender(render_svg)` | P2 header slot; P3 composed stone |
| `DraftsScreen` rows | `features/path/DraftsScreen.kt:173` | valence art only | deleted P7 |
| Share | `features/path/PebbleDetailScreen.kt:83` | a URL, no render | pebble id (P2) |

Every server-SVG path goes through `PebbleStaticRender` (fallback `PebbleSvg`). After P3 nothing parses a server SVG; `PebbleSvg` stays for `GlyphImage` and the outline backdrop.

### 1e. Stone port scope

Draw the derived glyph on the existing stone surfaces, nothing more. The Android stone today is the outline silhouette (`res/raw/outline_*`) filled with the palette, plus the server's composed SVG (the engine's shape strokes and the glyph in a slot) traced at one weight by `PebbleStaticRender`. The engine is deleted, so Part 3 ports exactly its composition, natively: the nine shape seeds in `docs/seeds/shape-seeds/` (byte-identical to the deleted `functions/_shared/engine/shapes/*.ts`, checked with `cmp`) are copied to `res/raw/shape_*`, and the glyph slot table from the deleted `functions/_shared/engine/layout.ts` (readable at git `13b5ae3b^`) becomes a Kotlin table. The result is a `PebbleSvgModel`, the type `PebbleStaticRender` already traces, so the wobble experiment, the uniform stroke weight and the fit logic are reused unchanged. **The iOS lit-stone material port (PR #975's tone tables and shading) is out of scope:** it is a new rendering technique with its own lab, not part of moving off `render_svg`, and the brief limits this issue to drawing the derived glyph. It stays the follow-up the spec names. One deliberate difference from the engine: no bounding-box normalization of the glyph (the engine's `createGlyphArtwork` re-centred strokes to fill 80 % of the box); the glyph's own `view_box` maps into the slot, which is how every other Android glyph surface (`GlyphView`, `GlyphImage`) already draws a glyph (PD-20).

---

## Part 1 — Moment models and services (`feat/1048-android-moments-data`)

**Story:** the app gains a typed, tested view of the new contract without any screen using it yet. Additive only, apart from three pure moves (commit them alone first) and one rule added to the shared error boundary.

**File map**

| Action | Path (under `app/src/main/kotlin/app/pbbls/android/` unless noted) | Responsibility |
|---|---|---|
| Move | `core/model/EmotionRef.kt` (from `Pebble.kt:48-53`) | `{id, slug, name}` emotion ref, shared by path and detail |
| Move | `core/model/DomainRef.kt` (from `PebbleDetail.kt:7-13`) | `{id, slug, name}` domain ref |
| Move | `core/model/PebbleSnapPayload.kt` (from `PebbleUpdatePayload.kt:7-18`) | `{id, storage_path, sort_order}` |
| Create | `core/model/WholeSecondTimestampSerializer.kt` | emit `yyyy-MM-ddTHH:mm:ssZ`, parse tolerantly |
| Create | `core/model/PebbleGlyph.kt` | derived glyph `{id, name, strokes, view_box, source}` |
| Create | `core/model/PathMoment.kt` | `path_moments` row + nested `PathPebble` |
| Create | `core/model/MomentDetail.kt` | `v_moments_full` row + `MomentPebble`, `MomentSnap`, `SoulRef` |
| Create | `core/model/PebbleAnswers.kt` | the pebble half of the answers, shared by Complete and Edit |
| Create | `core/model/MomentPayloads.kt` | `MomentSavePayload`, `PebbleCompletePayload`, `MomentWireJson` |
| Create | `core/data/MomentReadService.kt` | `MomentReadServicing` + live implementation |
| Create | `core/data/MomentWriteService.kt` | `MomentWriteServicing` + live implementation |
| Modify | `core/data/DataError.kt` | `P0002` → `NotFound`; `soul_not_owned`/`collection_not_owned` keep their name |
| Create | `core/ui/MomentErrorMessage.kt` | `DataError` → `@StringRes` for moment and pebble writes |
| Modify | `di/ServiceBindings.kt` | `@Binds` for the two new seams |
| Modify | `app/src/main/res/values/strings.xml`, `values-fr/strings.xml` | four error strings |
| Create | `app/src/test/.../testing/FakeMomentReadService.kt`, `FakeMomentWriteService.kt` | fakes |
| Modify | `app/src/test/.../testing/FakeServicesModule.kt`, `UiFixtures.kt` | bind fakes, moment fixtures |
| Create | tests listed per task | |

### Task 1.1: Pure moves

**Files:** the three "Move" rows above; `core/model/Pebble.kt`, `core/model/PebbleDetail.kt`, `core/model/PebbleUpdatePayload.kt`.

- [ ] **Step 1:** Cut `data class EmotionRef` (with its KDoc) out of `Pebble.kt` into `core/model/EmotionRef.kt`, `DomainRef` out of `PebbleDetail.kt` into `core/model/DomainRef.kt`, and `PebbleSnapPayload` out of `PebbleUpdatePayload.kt` into `core/model/PebbleSnapPayload.kt`. Same package, same declarations, so no import changes anywhere.
- [ ] **Step 2:** `./gradlew ktlintCheck testDebugUnitTest assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 3:** Commit: `git commit -m "quality(android): give the shared pebble refs their own files"` (with the trailer).

### Task 1.2: Whole-second timestamps

**Files:** Create `core/model/WholeSecondTimestampSerializer.kt`; Test `app/src/test/kotlin/app/pbbls/android/core/model/WholeSecondTimestampSerializerTest.kt`.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.pbbls.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.OffsetDateTime

class WholeSecondTimestampSerializerTest {
    @Test
    fun `emits UTC at whole seconds, dropping the fraction`() {
        val local = OffsetDateTime.parse("2026-10-12T08:40:00.987654+02:00")
        assertEquals("2026-10-12T06:40:00Z", WholeSecondTimestampSerializer.encode(local))
    }

    @Test
    fun `emits seconds even when they are zero`() {
        assertEquals("2026-10-12T06:40:00Z", WholeSecondTimestampSerializer.encode(OffsetDateTime.parse("2026-10-12T06:40Z")))
    }

    @Test
    fun `parses every precision the other surfaces emit`() {
        val instant = OffsetDateTime.parse("2026-10-12T06:40:00Z").toInstant()
        listOf(
            "2026-10-12T06:40:00Z",
            "2026-10-12T06:40:00+00:00",
            "2026-10-12T08:40:00+02:00",
        ).forEach { assertEquals(it, instant, WholeSecondTimestampSerializer.parse(it).toInstant()) }
        assertEquals(
            OffsetDateTime.parse("2026-10-12T06:40:00.123456+00:00").toInstant(),
            WholeSecondTimestampSerializer.parse("2026-10-12T06:40:00.123456+00:00").toInstant(),
        )
    }
}
```

- [ ] **Step 2:** `./gradlew testDebugUnitTest --tests 'app.pbbls.android.core.model.WholeSecondTimestampSerializerTest'` → FAIL (unresolved reference).
- [ ] **Step 3: Implement**

```kotlin
package app.pbbls.android.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * A timestamp that crosses a surface boundary: emitted in UTC at whole seconds,
 * the narrowest precision every reader accepts (root CLAUDE.md), and parsed
 * tolerantly (`Z`, `+00:00`, any fraction). `save_moment` truncates to seconds
 * too; emitting them already truncated keeps a client round-trip exact.
 *
 * An explicit pattern rather than `ISO_OFFSET_DATE_TIME`, whose output drops
 * zero seconds and keeps a non-zero fraction.
 */
object WholeSecondTimestampSerializer : KSerializer<OffsetDateTime> {
    private val format: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("app.pbbls.WholeSecondTimestamp", PrimitiveKind.STRING)

    fun encode(value: OffsetDateTime): String =
        value.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).format(format)

    fun parse(raw: String): OffsetDateTime = OffsetDateTime.parse(raw)

    override fun serialize(
        encoder: Encoder,
        value: OffsetDateTime,
    ) = encoder.encodeString(encode(value))

    override fun deserialize(decoder: Decoder): OffsetDateTime = parse(decoder.decodeString())
}
```

- [ ] **Step 4:** Re-run the test → PASS.
- [ ] **Step 5:** Commit `feat(android): emit moment timestamps at whole seconds`.

### Task 1.3: Read models and verbatim decoder tests

**Files:** Create `core/model/PebbleGlyph.kt`, `core/model/PathMoment.kt`, `core/model/MomentDetail.kt`; Test `core/model/PathMomentDecodingTest.kt`, `core/model/MomentDetailDecodingTest.kt`.

The fixtures are verbatim shapes of what `path_moments()` and `v_moments_full` return (read the SQL in `20261012090200_moments_split_readers.sql`): top-level `timestamptz` columns come back from PostgREST as `+00:00` offsets, timestamps nested inside `jsonb` keep Postgres' microseconds, `pebble` is SQL null for an uncompleted moment, `name` is an explicit null on a photo-only moment, and `v_moments_full` carries keys the client does not model (`user_id`, `hidden_at`, `updated_at`, `emotion_id`, `cards`, `emotion.color`, `domains[].label`, `collections[].glyph_id`). The tests use `Json { ignoreUnknownKeys = true }`, which is supabase-kt 3.8.0's default serializer (`SupabaseClientBuilder.defaultSerializer`).

- [ ] **Step 1: Write the failing tests**

`app/src/test/kotlin/app/pbbls/android/core/model/PathMomentDecodingTest.kt`:

```kotlin
package app.pbbls.android.core.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class PathMomentDecodingTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val rows =
        """
        [
          {
            "id": "7d1f6a2e-0c4b-4e8a-9b51-2f3c4d5e6f70",
            "name": "Swim at dawn",
            "happened_at": "2026-10-12T06:40:00+00:00",
            "created_at": "2026-10-12T07:02:13.481516+00:00",
            "first_snap_path": "3f0a9c1e-5b7d-4e21-8c44-0a1b2c3d4e5f/9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d",
            "pebble": {
              "id": "c2b1a090-8f7e-4d6c-b5a4-938271605f4e",
              "intensity": 3,
              "positiveness": 1,
              "visibility": "private",
              "emotion": {"id": "e1000000-0000-4000-8000-000000000001", "slug": "joy", "name": "Joy"},
              "glyph": {
                "id": "91c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e",
                "name": "Wave",
                "strokes": [{"d": "M 20 100 Q 100 20 180 100", "width": 6}, {"d": "M 50 150 L 150 150", "width": 6.5}],
                "view_box": "0 0 200 200",
                "source": "collection"
              }
            }
          },
          {
            "id": "0b9e8d7c-6b5a-4f3e-9d2c-1b0a9f8e7d6c",
            "name": null,
            "happened_at": "2026-10-11T18:05:27.5+00:00",
            "created_at": "2026-10-11T18:06:00+00:00",
            "first_snap_path": "3f0a9c1e-5b7d-4e21-8c44-0a1b2c3d4e5f/11111111-2222-4333-8444-555555555555",
            "pebble": null
          },
          {
            "id": "5a4b3c2d-1e0f-4a9b-8c7d-6e5f4a3b2c1d",
            "name": "Coffee with Ana",
            "happened_at": "2026-10-10T09:00:00Z",
            "created_at": "2026-10-10T09:15:00+00:00",
            "first_snap_path": null,
            "pebble": {
              "id": "d4c3b2a1-0f9e-4d8c-b7a6-5f4e3d2c1b0a",
              "intensity": 1,
              "positiveness": -1,
              "visibility": "secret",
              "emotion": null,
              "glyph": null
            }
          }
        ]
        """.trimIndent()

    private val decoded by lazy { json.decodeFromString<List<PathMoment>>(rows) }

    @Test
    fun `a completed moment carries its pebble and derived glyph`() {
        val moment = decoded[0]
        assertTrue(moment.isCompleted)
        assertEquals("Swim at dawn", moment.name)
        assertEquals(Valence.HIGHLIGHT_LARGE, moment.valence)
        assertEquals(Visibility.PRIVATE, moment.pebble?.visibility)
        assertEquals("joy", moment.pebble?.emotion?.slug)
        val glyph = moment.pebble?.glyph!!
        assertEquals("0 0 200 200", glyph.viewBox)
        assertEquals(2, glyph.strokes.size)
        assertEquals(6.5, glyph.strokes[1].width, 0.0)
        assertEquals("collection", glyph.source)
    }

    @Test
    fun `a photo-only moment has a null name and no pebble`() {
        val moment = decoded[1]
        assertNull(moment.name)
        assertNull(moment.pebble)
        assertFalse(moment.isCompleted)
        assertEquals(Valence.NEUTRAL_MEDIUM, moment.valence)
        assertEquals("3f0a9c1e-5b7d-4e21-8c44-0a1b2c3d4e5f/11111111-2222-4333-8444-555555555555", moment.firstSnapPath)
        assertEquals(OffsetDateTime.parse("2026-10-11T18:05:27.5Z").toInstant(), moment.happenedAt.toInstant())
    }

    @Test
    fun `a pebble may come without emotion or glyph`() {
        val pebble = decoded[2].pebble!!
        assertNull(pebble.emotion)
        assertNull(pebble.glyph)
        assertEquals(Valence.LOWLIGHT_SMALL, pebble.valence)
        assertNull(decoded[2].firstSnapPath)
    }

    @Test
    fun `absent optional keys decode like explicit nulls`() {
        val minimal = """{"id":"x","happened_at":"2026-10-12T06:40:00Z","created_at":"2026-10-12T06:40:00Z"}"""
        val moment = json.decodeFromString<PathMoment>(minimal)
        assertNull(moment.name)
        assertNull(moment.firstSnapPath)
        assertNull(moment.pebble)
    }
}
```

`app/src/test/kotlin/app/pbbls/android/core/model/MomentDetailDecodingTest.kt`:

```kotlin
package app.pbbls.android.core.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentDetailDecodingTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val ownCompleted =
        """
        {
          "id": "7d1f6a2e-0c4b-4e8a-9b51-2f3c4d5e6f70",
          "user_id": "3f0a9c1e-5b7d-4e21-8c44-0a1b2c3d4e5f",
          "name": "Swim at dawn",
          "description": "Cold, then not.",
          "happened_at": "2026-10-12T06:40:00+00:00",
          "hidden_at": null,
          "created_at": "2026-10-12T07:02:13.481516+00:00",
          "updated_at": "2026-10-12T07:02:13.481516+00:00",
          "snaps": [
            {"id": "aa000000-0000-4000-8000-000000000002", "storage_path": "3f0a9c1e-5b7d-4e21-8c44-0a1b2c3d4e5f/aa000000-0000-4000-8000-000000000002", "sort_order": 1},
            {"id": "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d", "storage_path": "3f0a9c1e-5b7d-4e21-8c44-0a1b2c3d4e5f/9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d", "sort_order": 0}
          ],
          "pebble": {
            "id": "c2b1a090-8f7e-4d6c-b5a4-938271605f4e",
            "emotion_id": "e1000000-0000-4000-8000-000000000001",
            "intensity": 3,
            "positiveness": 1,
            "visibility": "public",
            "created_at": "2026-10-12T07:02:13.481516+00:00",
            "updated_at": "2026-10-12T07:02:13.481516+00:00",
            "emotion": {"id": "e1000000-0000-4000-8000-000000000001", "slug": "joy", "name": "Joy", "color": "#F6C445FF"},
            "glyph": {"id": "b2000000-0000-4000-8000-000000000003", "name": null, "strokes": [{"d": "M 10 10 L 190 190", "width": 6}], "view_box": "0 0 200 200", "source": "domain"},
            "cards": [],
            "souls": [{"id": "50000000-0000-4000-8000-000000000004", "name": "Ana"}],
            "domains": [{"id": "d0000000-0000-4000-8000-000000000005", "slug": "health", "name": "Health", "label": "Body, sleep, movement"}],
            "collections": [{"id": "c0000000-0000-4000-8000-000000000006", "name": "Sea days", "glyph_id": "91c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e"}]
          }
        }
        """.trimIndent()

    private val ownPhotoOnly =
        """
        {
          "id": "0b9e8d7c-6b5a-4f3e-9d2c-1b0a9f8e7d6c",
          "user_id": "3f0a9c1e-5b7d-4e21-8c44-0a1b2c3d4e5f",
          "name": null,
          "description": null,
          "happened_at": "2026-10-11T18:05:27+00:00",
          "hidden_at": null,
          "created_at": "2026-10-11T18:06:00.000001+00:00",
          "updated_at": "2026-10-11T18:06:00.000001+00:00",
          "snaps": [{"id": "11111111-2222-4333-8444-555555555555", "storage_path": "3f0a9c1e-5b7d-4e21-8c44-0a1b2c3d4e5f/11111111-2222-4333-8444-555555555555", "sort_order": 0}],
          "pebble": null
        }
        """.trimIndent()

    private val connectionsMoment =
        """
        {
          "id": "f0000000-0000-4000-8000-000000000007",
          "user_id": "99999999-0000-4000-8000-000000000008",
          "name": "Their trip",
          "description": null,
          "happened_at": "2026-10-09T12:00:00+00:00",
          "hidden_at": null,
          "created_at": "2026-10-09T12:30:00+00:00",
          "updated_at": "2026-10-09T12:30:00+00:00",
          "snaps": [],
          "pebble": null
        }
        """.trimIndent()

    @Test
    fun `an own completed moment decodes every nested relation`() {
        val moment = json.decodeFromString<MomentDetail>(ownCompleted)
        val pebble = moment.pebble!!
        assertEquals("Cold, then not.", moment.description)
        assertEquals(Visibility.PUBLIC, pebble.visibility)
        assertEquals("joy", pebble.emotion.slug)
        assertEquals(listOf("Ana"), pebble.souls.map { it.name })
        assertEquals(listOf("health"), pebble.domains.map { it.slug })
        assertEquals(listOf("Sea days"), pebble.collections.map { it.name })
        assertEquals("domain", pebble.glyph?.source)
        assertNull(pebble.glyph?.name)
        assertEquals(Valence.HIGHLIGHT_LARGE, pebble.valence)
    }

    @Test
    fun `snaps sort by sort_order and the first one is the path photo`() {
        val moment = json.decodeFromString<MomentDetail>(ownCompleted)
        assertEquals(listOf(0, 1), moment.sortedSnaps.map { it.sortOrder })
        assertEquals("3f0a9c1e-5b7d-4e21-8c44-0a1b2c3d4e5f/9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d", moment.toPathMoment().firstSnapPath)
    }

    @Test
    fun `an own photo-only moment has explicit nulls and no pebble`() {
        val moment = json.decodeFromString<MomentDetail>(ownPhotoOnly)
        assertNull(moment.name)
        assertNull(moment.description)
        assertNull(moment.pebble)
        assertEquals(1, moment.snaps.size)
    }

    @Test
    fun `a connection's moment has no snaps and no pebble of yours`() {
        val moment = json.decodeFromString<MomentDetail>(connectionsMoment)
        assertTrue(moment.snaps.isEmpty())
        assertNull(moment.pebble)
    }

    @Test
    fun `toPathMoment keeps what a row needs`() {
        val row = json.decodeFromString<MomentDetail>(ownCompleted).toPathMoment()
        assertEquals("7d1f6a2e-0c4b-4e8a-9b51-2f3c4d5e6f70", row.id)
        assertEquals("c2b1a090-8f7e-4d6c-b5a4-938271605f4e", row.pebble?.id)
        assertEquals("joy", row.pebble?.emotion?.slug)
        assertEquals("b2000000-0000-4000-8000-000000000003", row.pebble?.glyph?.id)
    }
}
```

- [ ] **Step 2:** Run both classes → FAIL (unresolved `PathMoment`, `MomentDetail`).
- [ ] **Step 3: Implement**

`core/model/PebbleGlyph.kt`:

```kotlin
package app.pbbls.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A pebble's derived glyph (spec D5): its oldest collection's glyph, else its
 * first domain's default, as resolved server-side by `resolve_pebble_glyph`.
 * Pebbles carry no glyph of their own any more; clients draw these strokes.
 *
 * [source] stays a string (`"collection"` or `"domain"`): an enum would make a
 * new server value a decode failure for the whole timeline.
 */
@Serializable
data class PebbleGlyph(
    val id: String,
    val name: String? = null,
    val strokes: List<GlyphStroke>,
    @SerialName("view_box")
    val viewBox: String,
    val source: String? = null,
)
```

`core/model/PathMoment.kt`:

```kotlin
package app.pbbls.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

/**
 * One row of `path_moments()`: the caller's moments, newest `happened_at`
 * first. A null [pebble] is an uncompleted moment, drawn as a blank stone.
 * [name] is null on a photo-only moment (spec D3: a name or a photo).
 */
@Serializable
data class PathMoment(
    val id: String,
    val name: String? = null,
    @SerialName("happened_at")
    @Serializable(with = WholeSecondTimestampSerializer::class)
    val happenedAt: OffsetDateTime,
    @SerialName("created_at")
    @Serializable(with = WholeSecondTimestampSerializer::class)
    val createdAt: OffsetDateTime,
    /** Storage prefix `{user_id}/{snap_id}`; append `/thumb.jpg` or `/original.jpg` before signing. */
    @SerialName("first_snap_path")
    val firstSnapPath: String? = null,
    val pebble: PathPebble? = null,
) {
    val isCompleted: Boolean get() = pebble != null

    /** The stone's shape. An uncompleted moment uses the neutral medium stone (PD-11). */
    val valence: Valence get() = pebble?.valence ?: Valence.NEUTRAL_MEDIUM
}

/** The caller's pebble on a Path row. Palette colours come from `EmotionPaletteService` by [emotion] id. */
@Serializable
data class PathPebble(
    val id: String,
    val intensity: Int,
    val positiveness: Int,
    val visibility: Visibility = Visibility.SECRET,
    val emotion: EmotionRef? = null,
    val glyph: PebbleGlyph? = null,
) {
    val valence: Valence get() = Valence.fromOrDefault(positiveness, intensity)
}
```

`core/model/MomentDetail.kt`:

```kotlin
package app.pbbls.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

/**
 * One row of `v_moments_full`: the moment, its snaps, and the CALLER's pebble
 * on it (null when uncompleted). Used by the read page, the edit form and the
 * soul and collection lists.
 */
@Serializable
data class MomentDetail(
    val id: String,
    val name: String? = null,
    val description: String? = null,
    @SerialName("happened_at")
    @Serializable(with = WholeSecondTimestampSerializer::class)
    val happenedAt: OffsetDateTime,
    @SerialName("created_at")
    @Serializable(with = WholeSecondTimestampSerializer::class)
    val createdAt: OffsetDateTime,
    val snaps: List<MomentSnap> = emptyList(),
    val pebble: MomentPebble? = null,
) {
    val sortedSnaps: List<MomentSnap> get() = snaps.sortedBy { it.sortOrder }

    fun toPathMoment(): PathMoment =
        PathMoment(
            id = id,
            name = name,
            happenedAt = happenedAt,
            createdAt = createdAt,
            firstSnapPath = sortedSnaps.firstOrNull()?.storagePath,
            pebble =
                pebble?.let {
                    PathPebble(
                        id = it.id,
                        intensity = it.intensity,
                        positiveness = it.positiveness,
                        visibility = it.visibility,
                        emotion = it.emotion,
                        glyph = it.glyph,
                    )
                },
        )
}

@Serializable
data class MomentSnap(
    val id: String,
    @SerialName("storage_path")
    val storagePath: String,
    @SerialName("sort_order")
    val sortOrder: Int = 0,
)

@Serializable
data class MomentPebble(
    val id: String,
    val intensity: Int,
    val positiveness: Int,
    val visibility: Visibility,
    val emotion: EmotionRef,
    val glyph: PebbleGlyph? = null,
    val souls: List<SoulRef> = emptyList(),
    val domains: List<DomainRef> = emptyList(),
    val collections: List<PebbleCollection> = emptyList(),
) {
    val valence: Valence get() = Valence.fromOrDefault(positiveness, intensity)
}

/** The view projects souls as `{id, name}`; glyphs come from the souls cache (PD-26). */
@Serializable
data class SoulRef(
    val id: String,
    val name: String,
)
```

- [ ] **Step 4:** Run both classes → PASS.
- [ ] **Step 5:** Commit `feat(android): decode path_moments and v_moments_full`.

### Task 1.4: Write payloads

**Files:** Create `core/model/PebbleAnswers.kt`, `core/model/MomentPayloads.kt`; Test `core/model/MomentPayloadEncodingTest.kt`.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.pbbls.android.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.OffsetDateTime

class MomentPayloadEncodingTest {
    private val snap =
        PebbleSnapPayload(
            id = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d",
            storagePath = "3f0a9c1e-5b7d-4e21-8c44-0a1b2c3d4e5f/9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d",
            sortOrder = 0,
        )
    private val at = OffsetDateTime.parse("2026-10-12T08:40:00.987+02:00")

    private fun encoded(payload: MomentSavePayload) = MomentWireJson.encodeToJsonElement(MomentSavePayload.serializer(), payload)

    @Test
    fun `capture sends name, whole-second date and the one snap`() {
        val expected =
            """
            {"name":"Swim at dawn","happened_at":"2026-10-12T06:40:00Z",
             "snaps":[{"id":"9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d","storage_path":"3f0a9c1e-5b7d-4e21-8c44-0a1b2c3d4e5f/9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d","sort_order":0}]}
            """.trimIndent()
        assertEquals(Json.parseToJsonElement(expected), encoded(MomentSavePayload.capture("  Swim at dawn ", at, snap)))
    }

    @Test
    fun `a photo-only capture leaves the name out entirely`() {
        val expected =
            """{"happened_at":"2026-10-12T06:40:00Z","snaps":[{"id":"9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d","storage_path":"3f0a9c1e-5b7d-4e21-8c44-0a1b2c3d4e5f/9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d","sort_order":0}]}"""
        assertEquals(Json.parseToJsonElement(expected), encoded(MomentSavePayload.capture("   ", at, snap)))
    }

    @Test
    fun `a name-only capture leaves snaps out`() {
        assertEquals(
            Json.parseToJsonElement("""{"name":"Walk","happened_at":"2026-10-12T06:40:00Z"}"""),
            encoded(MomentSavePayload.capture("Walk", at, null)),
        )
    }

    @Test
    fun `an edit sends every moment key, empty strings to clear, and the whole snap set`() {
        val expected =
            """{"id":"m-1","name":"","description":"","happened_at":"2026-10-12T06:40:00Z","snaps":[]}"""
        assertEquals(
            Json.parseToJsonElement(expected),
            encoded(MomentSavePayload.edit(id = "m-1", name = " ", description = "\n", happenedAt = at, snaps = emptyList())),
        )
    }

    @Test
    fun `complete sends every pebble key and never a glyph, card or inline creation key`() {
        val answers =
            PebbleAnswers(
                valence = Valence.LOWLIGHT_LARGE,
                emotionId = "e-1",
                domainId = "d-1",
                soulIds = listOf("s-1", "s-2"),
                collectionId = null,
                visibility = Visibility.PRIVATE,
            )
        val expected =
            """
            {"moment_id":"m-1","emotion_id":"e-1","intensity":3,"positiveness":-1,"visibility":"private",
             "domain_ids":["d-1"],"soul_ids":["s-1","s-2"],"collection_ids":[]}
            """.trimIndent()
        assertEquals(
            Json.parseToJsonElement(expected),
            MomentWireJson.encodeToJsonElement(PebbleCompletePayload.serializer(), PebbleCompletePayload.from("m-1", answers)),
        )
    }
}
```

- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3: Implement**

`core/model/PebbleAnswers.kt`:

```kotlin
package app.pbbls.android.core.model

/**
 * The pebble half of a moment, as the Complete flow and the edit form hold it.
 * Complete (spec D2) = valence + emotion + one domain; the rest is optional.
 */
data class PebbleAnswers(
    val valence: Valence? = null,
    val emotionId: String? = null,
    val domainId: String? = null,
    val soulIds: List<String> = emptyList(),
    val collectionId: String? = null,
    val visibility: Visibility = Visibility.SECRET,
) {
    val isComplete: Boolean get() = valence != null && emotionId != null && domainId != null
}
```

`core/model/MomentPayloads.kt`:

```kotlin
package app.pbbls.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime

/**
 * The encoder for both writers. Absent optional keys are left out rather than
 * sent as JSON null (PD-27): the RPCs read null as absent anyway
 * (`private.jsonb_has`), and leaving them out matches what Swift's synthesized
 * encoder sends, so the two surfaces put identical bytes on the wire.
 */
internal val MomentWireJson = Json { explicitNulls = false }

/** `save_moment(payload)`: create without [id], update your own with it. */
@Serializable
data class MomentSavePayload(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    @SerialName("happened_at")
    @Serializable(with = WholeSecondTimestampSerializer::class)
    val happenedAt: OffsetDateTime? = null,
    val snaps: List<PebbleSnapPayload>? = null,
) {
    companion object {
        /** Capture creates. A blank name is left out: a photo-only moment is valid (D3). */
        fun capture(
            name: String,
            happenedAt: OffsetDateTime,
            snap: PebbleSnapPayload?,
        ): MomentSavePayload =
            MomentSavePayload(
                name = name.trim().ifEmpty { null },
                happenedAt = happenedAt,
                snaps = snap?.let { listOf(it) },
            )

        /**
         * Edit sends every moment key so the row ends up exactly as the form
         * shows it: `""` clears a text field (null would mean "keep"), and
         * `snaps` is the whole set, `[]` when the photo was removed (PD-14, PD-15).
         */
        fun edit(
            id: String,
            name: String,
            description: String,
            happenedAt: OffsetDateTime,
            snaps: List<PebbleSnapPayload>,
        ): MomentSavePayload =
            MomentSavePayload(
                id = id,
                name = name.trim(),
                description = description.trim(),
                happenedAt = happenedAt,
                snaps = snaps,
            )
    }
}

/**
 * `complete_pebble(payload)`: inserts or updates the caller's one pebble on
 * [momentId]. Every key is always sent, so an update rewrites the join tables
 * to exactly the answers (an empty list clears). `new_souls`,
 * `new_collections` and `cards` are never sent (PD-8).
 */
@Serializable
data class PebbleCompletePayload(
    @SerialName("moment_id")
    val momentId: String,
    @SerialName("emotion_id")
    val emotionId: String,
    val intensity: Int,
    val positiveness: Int,
    val visibility: Visibility,
    @SerialName("domain_ids")
    val domainIds: List<String>,
    @SerialName("soul_ids")
    val soulIds: List<String>,
    @SerialName("collection_ids")
    val collectionIds: List<String>,
) {
    companion object {
        fun from(
            momentId: String,
            answers: PebbleAnswers,
        ): PebbleCompletePayload {
            val valence = requireNotNull(answers.valence) { "complete without a valence" }
            return PebbleCompletePayload(
                momentId = momentId,
                emotionId = requireNotNull(answers.emotionId) { "complete without an emotion" },
                intensity = valence.intensity,
                positiveness = valence.positiveness,
                visibility = answers.visibility,
                domainIds = listOf(requireNotNull(answers.domainId) { "complete without a domain" }),
                soulIds = answers.soulIds,
                collectionIds = listOfNotNull(answers.collectionId),
            )
        }
    }
}
```

- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat(android): encode save_moment and complete_pebble payloads`.

### Task 1.5: Keep the moment conditions through the error boundary

`complete_pebble` raises `soul_not_owned` / `collection_not_owned` with SQLSTATE `42501`, which `dataErrorOf` turns into `Unauthorized` today, and the writers raise `moment_not_found` with `P0002`, which PostgREST answers with a 5xx and `dataErrorOf` turns into `Unknown`. Fix it in the layer that owns it.

**Files:** Modify `core/data/DataError.kt:76-90`; Test `app/src/test/.../core/data/DataErrorTest.kt` (append).

- [ ] **Step 1: Append failing tests**

```kotlin
    @Test
    fun `the two ownership conditions keep their name despite 42501`() {
        assertEquals(DataError.Conflict("soul_not_owned"), dataErrorOf(403, "soul_not_owned", "42501"))
        assertEquals(DataError.Conflict("collection_not_owned"), dataErrorOf(403, "collection_not_owned", "42501"))
    }

    @Test
    fun `any other 42501 is still a refusal`() {
        assertEquals(DataError.Unauthorized, dataErrorOf(403, "not_authenticated", "42501"))
        assertEquals(DataError.Unauthorized, dataErrorOf(403, "insufficient_privilege", "42501"))
    }

    @Test
    fun `P0002 is not found whatever the status`() {
        assertEquals(DataError.NotFound, dataErrorOf(500, "moment_not_found", "P0002"))
        assertEquals(DataError.NotFound, dataErrorOf(400, "collection_not_found", "P0002"))
    }
```

- [ ] **Step 2:** `./gradlew testDebugUnitTest --tests 'app.pbbls.android.core.data.DataErrorTest'` → the three new tests FAIL.
- [ ] **Step 3: Implement** — replace the `when` in `dataErrorOf`:

```kotlin
/** 42501 conditions that name a missing reference, not a dead session (`complete_pebble`). */
private val OWNERSHIP_CONDITIONS = setOf("soul_not_owned", "collection_not_owned")

internal fun dataErrorOf(
    statusCode: Int,
    error: String,
    sqlState: String?,
): DataError =
    when {
        sqlState == "42501" && error in OWNERSHIP_CONDITIONS -> DataError.Conflict(error)
        statusCode == 401 || statusCode == 403 || sqlState == "42501" -> DataError.Unauthorized
        // no_data_found: PostgREST answers P0002 with a 5xx, so the code is the only signal.
        statusCode == 404 || sqlState == "P0002" -> DataError.NotFound
        error == MEDIA_QUOTA_EXCEEDED -> DataError.Quota
        error.isNotBlank() && (sqlState == "P0001" || statusCode in 400..499) -> DataError.Conflict(error)
        else -> DataError.Unknown(null)
    }
```

- [ ] **Step 4:** Run `DataErrorTest` → all PASS (the existing `Glyph not usable by user: abc` case stays `Unauthorized`).
- [ ] **Step 5:** Commit `fix(android): keep moment write conditions through the error boundary`.

### Task 1.6: Error copy

**Files:** Create `core/ui/MomentErrorMessage.kt`; Modify `res/values/strings.xml`, `res/values-fr/strings.xml` (append after `pebble_save_error_photo_failed`); Test `app/src/test/.../core/ui/MomentErrorMessageTest.kt`.

Strings (EN / FR), in both files:

```xml
<string name="moment_save_error_generic">Couldn\'t save this moment. Please try again.</string>
<string name="moment_error_empty">A moment needs a name or a photo.</string>
<string name="moment_error_not_found">This moment no longer exists.</string>
<string name="pebble_error_reference_gone">A soul or collection you picked no longer exists.</string>
```

```xml
<string name="moment_save_error_generic">Impossible d\'enregistrer ce moment. Réessaie.</string>
<string name="moment_error_empty">Un moment a besoin d\'un nom ou d\'une photo.</string>
<string name="moment_error_not_found">Ce moment n\'existe plus.</string>
<string name="pebble_error_reference_gone">Une âme ou une collection choisie n\'existe plus.</string>
```

- [ ] **Step 1: Write the failing test**

```kotlin
package app.pbbls.android.core.ui

import app.pbbls.android.R
import app.pbbls.android.core.data.DataError
import org.junit.Assert.assertEquals
import org.junit.Test

class MomentErrorMessageTest {
    private val fallback = R.string.moment_save_error_generic

    @Test
    fun `named conditions map to their copy`() {
        assertEquals(R.string.moment_error_empty, momentWriteErrorRes(DataError.Conflict("moment_empty"), fallback))
        assertEquals(R.string.moment_error_not_found, momentWriteErrorRes(DataError.Conflict("moment_not_found"), fallback))
        assertEquals(R.string.moment_error_not_found, momentWriteErrorRes(DataError.NotFound, fallback))
        assertEquals(R.string.pebble_error_reference_gone, momentWriteErrorRes(DataError.Conflict("soul_not_owned"), fallback))
        assertEquals(R.string.pebble_error_reference_gone, momentWriteErrorRes(DataError.Conflict("collection_not_owned"), fallback))
        assertEquals(R.string.pebble_save_error_media_quota, momentWriteErrorRes(DataError.Quota, fallback))
        assertEquals(R.string.record_signed_out_error, momentWriteErrorRes(DataError.Unauthorized, fallback))
    }

    @Test
    fun `everything else is the caller's generic line`() {
        listOf(
            DataError.Conflict("pebble_needs_domain"),
            DataError.Conflict("invalid_payload"),
            DataError.Network,
            DataError.Unknown(null),
        ).forEach { assertEquals(it.toString(), fallback, momentWriteErrorRes(it, fallback)) }
        assertEquals(
            R.string.pebble_save_error_generic,
            momentWriteErrorRes(DataError.Network, R.string.pebble_save_error_generic),
        )
    }
}
```

- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3: Implement** `core/ui/MomentErrorMessage.kt`:

```kotlin
package app.pbbls.android.core.ui

import androidx.annotation.StringRes
import app.pbbls.android.R
import app.pbbls.android.core.data.DataError

/**
 * The one mapping from a moment or pebble write failure to copy (PD-18), shared
 * by Capture, Complete, the edit form and the delete paths. [fallback] is the
 * caller's generic line: `moment_save_error_generic` for `save_moment`,
 * `pebble_save_error_generic` for `complete_pebble`. Conditions the client
 * prevents before calling (`pebble_needs_*`, `invalid_*`) fall back too.
 */
@StringRes
fun momentWriteErrorRes(
    error: DataError,
    @StringRes fallback: Int,
): Int =
    when (error) {
        DataError.Quota -> R.string.pebble_save_error_media_quota
        DataError.Unauthorized -> R.string.record_signed_out_error
        DataError.NotFound -> R.string.moment_error_not_found
        is DataError.Conflict ->
            when (error.code) {
                "moment_empty" -> R.string.moment_error_empty
                "moment_not_found" -> R.string.moment_error_not_found
                "soul_not_owned", "collection_not_owned" -> R.string.pebble_error_reference_gone
                else -> fallback
            }
        DataError.Network, is DataError.Unknown -> fallback
    }
```

- [ ] **Step 4:** Run `MomentErrorMessageTest` and `LocalizationParityTest` → PASS.
- [ ] **Step 5:** Commit `feat(android): map moment write failures to copy`.

### Task 1.7: The read seam

**Files:** Create `core/data/MomentReadService.kt`; Test `app/src/test/.../core/data/MomentReadServiceTest.kt`; Modify `di/ServiceBindings.kt`.

- [ ] **Step 1: Write the failing test** (the containment filters are the part with judgement in it)

```kotlin
package app.pbbls.android.core.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class MomentReadServiceTest {
    @Test
    fun `the soul filter is a jsonb containment on the caller's pebble`() {
        assertEquals(
            Json.parseToJsonElement("""{"souls":[{"id":"50000000-0000-4000-8000-000000000004"}]}"""),
            Json.parseToJsonElement(MomentReadService.withSoul("50000000-0000-4000-8000-000000000004")),
        )
    }

    @Test
    fun `the collection filter is a jsonb containment on the caller's pebble`() {
        assertEquals(
            Json.parseToJsonElement("""{"collections":[{"id":"c-1"}]}"""),
            Json.parseToJsonElement(MomentReadService.inCollection("c-1")),
        )
    }

    @Test
    fun `the view is read through an explicit column list`() {
        assertEquals("id, name, description, happened_at, created_at, snaps, pebble", MomentReadService.COLUMNS)
    }
}
```

- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3: Implement**

```kotlin
package app.pbbls.android.core.data

import android.util.Log
import app.pbbls.android.core.model.MomentDetail
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.model.PebbleGlyph
import app.pbbls.android.core.model.Valence
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import javax.inject.Inject
import javax.inject.Singleton

/** Every moment read (#1048). Extracted because the Path, detail, edit and list ViewModels have tests. */
interface MomentReadServicing {
    suspend fun loadPath(): List<PathMoment>

    suspend fun loadMoment(momentId: String): MomentDetail

    suspend fun loadMomentsWithSoul(soulId: String): List<MomentDetail>

    suspend fun loadMomentsInCollection(collectionId: String): List<MomentDetail>

    suspend fun resolveGlyph(pebbleId: String): PebbleGlyph?
}

/**
 * `path_moments()` for the timeline and `v_moments_full` for everything that
 * needs a whole moment. Single-statement reads, so direct calls (root AGENTS.md).
 * Errors propagate; callers own their loading and error state.
 */
@Singleton
class MomentReadService
    @Inject
    constructor(
        private val supabase: SupabaseService,
    ) : MomentReadServicing {
        override suspend fun loadPath(): List<PathMoment> {
            val moments = supabase.client.postgrest.rpc("path_moments").decodeList<PathMoment>()
            // Valence.fromOrDefault is pure and log-free; decode drift is surfaced here, once per load.
            moments
                .mapNotNull { it.pebble }
                .filter { p -> Valence.entries.none { it.positiveness == p.positiveness && it.intensity == p.intensity } }
                .forEach { Log.e(TAG, "unexpected valence (${it.positiveness}, ${it.intensity}) on pebble ${it.id}") }
            return moments
        }

        override suspend fun loadMoment(momentId: String): MomentDetail =
            supabase.client
                .from(VIEW)
                .select(Columns.raw(COLUMNS)) { filter { eq("id", momentId) } }
                .decodeSingle()

        override suspend fun loadMomentsWithSoul(soulId: String): List<MomentDetail> = listContaining(withSoul(soulId))

        override suspend fun loadMomentsInCollection(collectionId: String): List<MomentDetail> =
            listContaining(inCollection(collectionId))

        override suspend fun resolveGlyph(pebbleId: String): PebbleGlyph? =
            supabase.client.postgrest
                .rpc("resolve_pebble_glyph", buildJsonObject { put("p_pebble_id", pebbleId) })
                .decodeAs<PebbleGlyph?>()

        /**
         * `pebble @> {...}`: only the caller's own pebble is non-null in the
         * view, so the containment also restricts to the caller's moments.
         */
        private suspend fun listContaining(containment: String): List<MomentDetail> =
            supabase.client
                .from(VIEW)
                .select(Columns.raw(COLUMNS)) {
                    filter { filter("pebble", FilterOperator.CS, containment) }
                    order("happened_at", Order.DESCENDING)
                }.decodeList()

        companion object {
            private const val TAG = "moments"
            private const val VIEW = "v_moments_full"
            internal const val COLUMNS = "id, name, description, happened_at, created_at, snaps, pebble"

            internal fun withSoul(soulId: String): String =
                buildJsonObject { putJsonArray("souls") { addJsonObject { put("id", soulId) } } }.toString()

            internal fun inCollection(collectionId: String): String =
                buildJsonObject { putJsonArray("collections") { addJsonObject { put("id", collectionId) } } }.toString()
        }
    }
```

Add to the `ServiceBindings` interface (`di/ServiceBindings.kt`), beside the other `@Binds`:

```kotlin
    @Binds
    @Singleton
    fun bindMomentReadServicing(impl: MomentReadService): MomentReadServicing
```

- [ ] **Step 4:** Run the test → PASS; `./gradlew assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 5:** Commit `feat(android): read moments from path_moments and v_moments_full`.

### Task 1.8: The write seam

**Files:** Create `core/data/MomentWriteService.kt`; Test `app/src/test/.../core/data/MomentWriteServiceTest.kt`; Modify `di/ServiceBindings.kt`.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.pbbls.android.core.data

import app.pbbls.android.core.data.MomentWriteService.Companion.wire
import app.pbbls.android.core.model.KarmaReason
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class MomentWriteServiceTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `delete_moment answers with the files the client removes`() {
        val result =
            json.decodeFromString<DeleteMomentResult>(
                """{"storage_paths":["3f0a9c1e-5b7d-4e21-8c44-0a1b2c3d4e5f/9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d"]}""",
            )
        assertEquals(listOf("3f0a9c1e-5b7d-4e21-8c44-0a1b2c3d4e5f/9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d"), result.storagePaths)
        assertEquals(emptyList<String>(), json.decodeFromString<DeleteMomentResult>("""{"storage_paths":[]}""").storagePaths)
    }

    @Test
    fun `karma reasons use the karma_events wire names`() {
        assertEquals("pebble_created", KarmaReason.PEBBLE_CREATED.wire())
        assertEquals("pebble_enriched", KarmaReason.PEBBLE_ENRICHED.wire())
    }
}
```

- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3: Implement**

```kotlin
package app.pbbls.android.core.data

import android.util.Log
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.model.KarmaReason
import app.pbbls.android.core.model.MomentSavePayload
import app.pbbls.android.core.model.MomentWireJson
import app.pbbls.android.core.model.PebbleCompletePayload
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** Every moment and pebble write (#1048). Throws; callers map with `toDataError()` + `momentWriteErrorRes`. */
interface MomentWriteServicing {
    /** `save_moment`; returns the moment id. */
    suspend fun saveMoment(payload: MomentSavePayload): String

    /** `complete_pebble`; returns the pebble id. Does not check achievements (PD-9). */
    suspend fun completePebble(payload: PebbleCompletePayload): String

    /** `delete_pebble`: the pebble only, the moment stays. Takes a PEBBLE id. */
    suspend fun deletePebble(pebbleId: String)

    /** `delete_moment`, then a best-effort delete of the returned snap files. */
    suspend fun deleteMoment(momentId: String)

    /** The delta the last write recorded for [pebbleId], or null. Never throws. */
    suspend fun karmaDelta(
        pebbleId: String,
        reason: KarmaReason,
    ): Int?
}

@Singleton
class MomentWriteService
    @Inject
    constructor(
        private val supabase: SupabaseService,
        private val snapFiles: SnapWriteRepositing,
    ) : MomentWriteServicing {
        override suspend fun saveMoment(payload: MomentSavePayload): String =
            supabase.client.postgrest
                .rpc(
                    "save_moment",
                    buildJsonObject { put("payload", MomentWireJson.encodeToJsonElement(MomentSavePayload.serializer(), payload)) },
                ).decodeAs<String>()

        override suspend fun completePebble(payload: PebbleCompletePayload): String =
            supabase.client.postgrest
                .rpc(
                    "complete_pebble",
                    buildJsonObject { put("payload", MomentWireJson.encodeToJsonElement(PebbleCompletePayload.serializer(), payload)) },
                ).decodeAs<String>()

        override suspend fun deletePebble(pebbleId: String) {
            supabase.client.postgrest.rpc("delete_pebble", buildJsonObject { put("p_pebble_id", pebbleId) })
        }

        override suspend fun deleteMoment(momentId: String) {
            val result =
                supabase.client.postgrest
                    .rpc("delete_moment", buildJsonObject { put("p_moment_id", momentId) })
                    .decodeAs<DeleteMomentResult>()
            // The rows are gone server-side; the files must follow even if the
            // caller leaves. Best-effort: the orphan sweep is the backstop.
            withContext(NonCancellable) {
                result.storagePaths.forEach { snapFiles.deleteFiles(storagePrefix = it) }
            }
        }

        /** Mirrors `functions/_shared/karma-delta.ts`: newest row for (ref_id, reason). */
        override suspend fun karmaDelta(
            pebbleId: String,
            reason: KarmaReason,
        ): Int? =
            runCatchingCancellable {
                supabase.client
                    .from("karma_events")
                    .select(Columns.raw("delta")) {
                        filter {
                            eq("ref_id", pebbleId)
                            eq("reason", reason.wire())
                        }
                        order("created_at", Order.DESCENDING)
                        limit(1)
                    }.decodeList<KarmaDeltaRow>()
                    .firstOrNull()
                    ?.delta
            }.onFailure { Log.w(TAG, "karma read-back failed for $pebbleId", it) }
                .getOrNull()

        companion object {
            private const val TAG = "moment-write"

            internal fun KarmaReason.wire(): String =
                when (this) {
                    KarmaReason.PEBBLE_CREATED -> "pebble_created"
                    KarmaReason.PEBBLE_ENRICHED -> "pebble_enriched"
                }
        }
    }

@Serializable
internal data class DeleteMomentResult(
    @SerialName("storage_paths")
    val storagePaths: List<String> = emptyList(),
)

@Serializable
private data class KarmaDeltaRow(
    val delta: Int,
)
```

Add to the `ServiceBindings` interface:

```kotlin
    @Binds
    @Singleton
    fun bindMomentWriteServicing(impl: MomentWriteService): MomentWriteServicing
```

- [ ] **Step 4:** Run the test → PASS; `./gradlew assembleDebug` → BUILD SUCCESSFUL (Hilt resolves `SnapWriteRepositing` through `ServiceModule.provideSnapWriteRepositing`).
- [ ] **Step 5:** Commit `feat(android): write moments through save_moment and complete_pebble`.

### Task 1.9: Fakes and fixtures

**Files:** Create `app/src/test/kotlin/app/pbbls/android/testing/FakeMomentReadService.kt`, `FakeMomentWriteService.kt`; Modify `testing/FakeServicesModule.kt`, `testing/UiFixtures.kt`, `testing/ServiceFakesTest.kt`.

- [ ] **Step 1:** Write the fakes, on the `FakePathService` pattern:

```kotlin
package app.pbbls.android.testing

import app.pbbls.android.core.data.MomentReadServicing
import app.pbbls.android.core.model.MomentDetail
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.model.PebbleGlyph

class FakeMomentReadService(
    var path: List<PathMoment> = emptyList(),
    var moments: Map<String, MomentDetail> = emptyMap(),
    var withSoul: List<MomentDetail> = emptyList(),
    var inCollection: List<MomentDetail> = emptyList(),
    var glyph: PebbleGlyph? = null,
) : MomentReadServicing {
    var loadPathCount = 0
        private set
    val loadedMomentIds = mutableListOf<String>()
    val resolvedGlyphFor = mutableListOf<String>()
    private val armed = ArmedFailure()

    var failNext: Exception?
        get() = armed.next
        set(value) {
            armed.next = value
        }

    override suspend fun loadPath(): List<PathMoment> {
        loadPathCount += 1
        armed.fire()
        return path
    }

    override suspend fun loadMoment(momentId: String): MomentDetail {
        loadedMomentIds += momentId
        armed.fire()
        return moments[momentId] ?: throw NoSuchElementException(momentId)
    }

    override suspend fun loadMomentsWithSoul(soulId: String): List<MomentDetail> {
        armed.fire()
        return withSoul
    }

    override suspend fun loadMomentsInCollection(collectionId: String): List<MomentDetail> {
        armed.fire()
        return inCollection
    }

    override suspend fun resolveGlyph(pebbleId: String): PebbleGlyph? {
        resolvedGlyphFor += pebbleId
        armed.fire()
        return glyph
    }
}
```

```kotlin
package app.pbbls.android.testing

import app.pbbls.android.core.data.MomentWriteServicing
import app.pbbls.android.core.model.KarmaReason
import app.pbbls.android.core.model.MomentSavePayload
import app.pbbls.android.core.model.PebbleCompletePayload
import kotlinx.coroutines.CompletableDeferred

class FakeMomentWriteService : MomentWriteServicing {
    val saved = mutableListOf<MomentSavePayload>()
    val completed = mutableListOf<PebbleCompletePayload>()
    val deletedPebbles = mutableListOf<String>()
    val deletedMoments = mutableListOf<String>()
    val karmaReads = mutableListOf<Pair<String, KarmaReason>>()
    var nextMomentId = "moment-new"
    var nextPebbleId = "pebble-new"
    var karma: Int? = 4

    /** Holds a write in flight until completed, for back-while-saving tests. */
    var gate: CompletableDeferred<Unit>? = null
    private val armed = ArmedFailure()

    var failNext: Exception?
        get() = armed.next
        set(value) {
            armed.next = value
        }

    override suspend fun saveMoment(payload: MomentSavePayload): String {
        gate?.await()
        armed.fire()
        saved += payload
        return payload.id ?: nextMomentId
    }

    /** Thrown by the next `completePebble` only: the edit form's "moment saved, pebble refused" case. */
    var failOnComplete: Exception? = null

    override suspend fun completePebble(payload: PebbleCompletePayload): String {
        gate?.await()
        armed.fire()
        failOnComplete?.let {
            failOnComplete = null
            throw it
        }
        completed += payload
        return nextPebbleId
    }

    override suspend fun deletePebble(pebbleId: String) {
        armed.fire()
        deletedPebbles += pebbleId
    }

    override suspend fun deleteMoment(momentId: String) {
        armed.fire()
        deletedMoments += momentId
    }

    override suspend fun karmaDelta(
        pebbleId: String,
        reason: KarmaReason,
    ): Int? {
        karmaReads += pebbleId to reason
        return karma
    }
}
```

- [ ] **Step 2:** In `FakeServicesModule.kt`, beside the existing pairs:

```kotlin
    @Provides @Singleton
    fun fakeMomentRead() = FakeMomentReadService()

    @Provides @Singleton
    fun fakeMomentWrite() = FakeMomentWriteService()

    @Provides
    fun momentRead(fake: FakeMomentReadService): MomentReadServicing = fake

    @Provides
    fun momentWrite(fake: FakeMomentWriteService): MomentWriteServicing = fake
```

- [ ] **Step 3:** In `UiFixtures.kt`, add moment builders used by every later part:

```kotlin
    fun pebbleGlyph(
        id: String = "glyph-1",
        source: String = "domain",
    ) = PebbleGlyph(
        id = id,
        name = null,
        strokes = listOf(GlyphStroke(d = "M 20 100 Q 100 20 180 100", width = 6.0)),
        viewBox = "0 0 200 200",
        source = source,
    )

    fun pathMoment(
        id: String = "moment-1",
        name: String? = "Morning walk",
        happenedAt: OffsetDateTime = OffsetDateTime.parse("2026-10-12T06:40:00Z"),
        firstSnapPath: String? = null,
        pebble: PathPebble? = pathPebble(),
    ) = PathMoment(id, name, happenedAt, happenedAt, firstSnapPath, pebble)

    fun pathPebble(
        id: String = "pebble-1",
        valence: Valence = Valence.HIGHLIGHT_MEDIUM,
        visibility: Visibility = Visibility.SECRET,
        glyph: PebbleGlyph? = pebbleGlyph(),
    ) = PathPebble(id, valence.intensity, valence.positiveness, visibility, joy, glyph)

    fun momentDetail(
        id: String = "moment-1",
        name: String? = "Morning walk",
        description: String? = null,
        snaps: List<MomentSnap> = emptyList(),
        pebble: MomentPebble? = momentPebble(),
    ) = MomentDetail(
        id = id,
        name = name,
        description = description,
        happenedAt = OffsetDateTime.parse("2026-10-12T06:40:00Z"),
        createdAt = OffsetDateTime.parse("2026-10-12T07:00:00Z"),
        snaps = snaps,
        pebble = pebble,
    )

    fun momentPebble(
        id: String = "pebble-1",
        valence: Valence = Valence.HIGHLIGHT_MEDIUM,
        visibility: Visibility = Visibility.SECRET,
        souls: List<SoulRef> = emptyList(),
        collections: List<PebbleCollection> = emptyList(),
    ) = MomentPebble(
        id = id,
        intensity = valence.intensity,
        positiveness = valence.positiveness,
        visibility = visibility,
        emotion = joy,
        glyph = pebbleGlyph(),
        souls = souls,
        domains = listOf(DomainRef(id = "domain-1", slug = "health", name = "Health")),
        collections = collections,
    )
```

- [ ] **Step 4:** In `ServiceFakesTest.kt`, add one armed-failure case per new fake mirroring `an armed failure throws once, then clears so the retry succeeds`.
- [ ] **Step 5:** `./gradlew ktlintCheck testDebugUnitTest assembleDebug lint` → BUILD SUCCESSFUL. Commit `test(android): fake the moment seams`.

**Part 1 done when:** the full gate is green, no screen changed, `git grep -n "MomentReadServicing\|MomentWriteServicing" app/src/main` shows only the services and `ServiceBindings`. No preview changes, so the screenshot check stays green.

---

## Part 2 — Readers and editor on moments (`feat/1048-android-moments-readers`)

**Story:** every screen that reads or edits a pebble moves onto the Part 1 seams, so the installed build stops reading dropped columns. Behaviour is unchanged except what the split forces: a row's "Delete" deletes the moment (as it did before the split), photo-only moments show "Untitled moment", the edit form loses its glyph row, share uses the pebble id. The old composers (eleven-step flow, long-press composer) still create through `compose-pebble` and are untouched apart from compile fixes.

**File map** (paths under `app/src/main/kotlin/app/pbbls/android/` unless noted)

| Action | Path | What changes |
|---|---|---|
| Modify | `navigation/PebblesKeys.kt:92-95,113-115` | `PebbleDetail` and `EditPebble` carry `momentId`, serialized as `pebbleId` |
| Modify | `navigation/PebblesEntryProvider.kt:62-71,112-134,233-254,273-287` | pass moment ids; `CreatePebble.onCreated` only pops; `EditPebble` loses `glyphPicker` |
| Modify | `core/model/WeekRollEntry.kt` | `moments: List<PathMoment>` |
| Modify | `features/path/WeekRollBuilder.kt:32-60` | builds from `PathMoment` |
| Modify | `features/path/PathViewModel.kt` | `MomentReadServicing` + `MomentWriteServicing`; delete = `deleteMoment` |
| Modify | `features/path/PathScreen.kt:165-177` | dialog takes a `PathMoment` |
| Modify | `features/path/components/WeekPebbleList.kt`, `PathPebbleRow.kt` | render a `PathMoment` |
| Modify | `core/ui/render/PebbleThumbnail.kt` | takes `valence` + `palette` (backdrop only until Part 3) |
| Modify | `core/ui/PebbleRow.kt` | renders a `PathMoment` |
| Modify | `features/profile/SoulDetailViewModel.kt`, `SoulDetailScreen.kt`, `CollectionDetailViewModel.kt`, `CollectionDetailScreen.kt`, `GroupPebblesByMonth.kt` | lists of `PathMoment`; delete = `deleteMoment` |
| Modify | `core/data/SoulsService.kt:80-97`, `core/data/CollectionsService.kt:73-90` | drop `loadPebbles` |
| Modify | `features/path/PebbleDetailViewModel.kt`, `PebbleDetailScreen.kt` | `MomentDetail`; share from the pebble id |
| Modify | `features/path/read/PebbleReadView.kt` | `MomentDetail`; pebble sections only when a pebble exists; soul glyphs from the souls cache |
| Modify | `core/model/PebbleDraft.kt` | `from(MomentDetail)`, `answers` |
| Modify | `core/data/SnapUploadCoordinator.kt:129-140`, `core/data/PebbleSnapRepository.kt:20-39,111-116` | `detachExisting()`; `deletePebbleMedia` removed |
| Modify | `features/path/EditPebbleViewModel.kt`, `EditPebbleScreen.kt` | `save_moment` then `complete_pebble` |
| Modify | `features/path/create/PebbleForm.kt` | glyph row removed; `header` slot; `showPebbleFields` |
| Modify | `features/path/create/CreatePebbleScreen.kt` | compile against the new `PebbleForm` |
| Modify | `core/data/PebbleWriteService.kt` | drop `update` and `delete` (create stays until Part 7) |
| Delete | `core/data/PathService.kt`, `core/data/PebbleDetailService.kt`, `core/model/Pebble.kt`, `core/model/PebbleDetail.kt`, `core/model/PebbleUpdatePayload.kt` | replaced |
| Modify | `di/ServiceBindings.kt` | drop `PathServicing`, `PebbleDetailServicing` bindings |
| Modify | `res/values/strings.xml`, `values-fr/strings.xml` | `moment_untitled`; strings lint reports unused |
| Tests | see Task 2.9 | |

### Task 2.1: Keys carry moment ids

- [ ] **Step 1:** In `PebblesKeysTest.kt`, change the fixtures to `PebblesKey.PebbleDetail(momentId = "m-1")` and `PebblesKey.EditPebble(momentId = "m-1")`, and add:

```kotlin
    @Test
    fun `a back stack saved before the split still decodes`() {
        val saved = """{"type":"app.pbbls.android.navigation.PebblesKey.PebbleDetail","pebbleId":"m-1"}"""
        val key = Json.decodeFromString(PebblesKey.serializer(), saved)
        assertEquals(PebblesKey.PebbleDetail(momentId = "m-1"), key)
    }
```

(If the polymorphic discriminator in the existing round-trip test differs from `type`, copy the exact JSON the existing `every key round-trips` test prints for `PebbleDetail` today, before editing the key.)

- [ ] **Step 2:** Run `PebblesKeysTest` → FAIL.
- [ ] **Step 3:** In `PebblesKeys.kt`:

```kotlin
    @Serializable
    data class PebbleDetail(
        /** A moment id. Serialized under its pre-split name so a restored back stack still decodes. */
        @SerialName("pebbleId")
        val momentId: String,
    ) : PebblesKey,
        BarKey

    @Serializable
    data class EditPebble(
        /** A moment id; see [PebbleDetail]. */
        @SerialName("pebbleId")
        val momentId: String,
    ) : PebblesKey
```

Update every `key.pebbleId` in `PebblesEntryProvider.kt` to `key.momentId`.
- [ ] **Step 4:** Run `PebblesKeysTest` → PASS. Commit with the rest of Task 2.2 (the provider does not compile alone).

### Task 2.2: Path on `path_moments`

**Files:** `core/model/WeekRollEntry.kt`, `features/path/WeekRollBuilder.kt`, `features/path/PathViewModel.kt`, `features/path/PathScreen.kt`, `features/path/components/WeekPebbleList.kt`, `features/path/components/PathPebbleRow.kt`, `core/ui/render/PebbleThumbnail.kt`; strings.

- [ ] **Step 1: Update the failing tests first.** In `PathViewModelTest.kt` replace `FakePathService` with `FakeMomentReadService` (`read.path = listOf(UiFixtures.pathMoment(...))`) and `FakePebbleWriteService` with `FakeMomentWriteService`. Change the delete test to assert `write.deletedMoments == listOf("moment-1")`, and add:

```kotlin
    @Test
    fun `an uncompleted moment is on the timeline`() =
        runTest {
            val h = Harness(backgroundScope)
            h.read.path = listOf(UiFixtures.pathMoment(id = "bare", name = null, pebble = null, firstSnapPath = "u/s"))
            h.viewModel.retry()
            advanceUntilIdle()
            val content = h.viewModel.uiState.value as PathUiState.Content
            assertEquals(listOf("bare"), content.entries.flatMap { it.moments }.map { it.id })
        }

    @Test
    fun `ripple counts today only for a completed moment created today`() =
        runTest {
            val today = OffsetDateTime.now()
            val h = Harness(backgroundScope)
            h.stats.ripple = RippleSummary(rippleLevel = 1, pebbles28d = 1, activeToday = false)
            h.read.path = listOf(UiFixtures.pathMoment(happenedAt = today, pebble = null))
            h.viewModel.retry()
            advanceUntilIdle()
            assertEquals(false, (h.viewModel.uiState.value as PathUiState.Content).ripple?.activeToday)
        }
```

(`Harness` gains `read: FakeMomentReadService` and `write: FakeMomentWriteService`; keep its other fakes. Match `RippleSummary`'s constructor to `core/model/RippleSummary.kt`.) In `WeekRollBuilderTest.kt` swap the `Pebble(...)` fixtures for `UiFixtures.pathMoment(...)` and `entry.pebbles` for `entry.moments`.

- [ ] **Step 2:** Run `PathViewModelTest`, `WeekRollBuilderTest` → FAIL (compile).
- [ ] **Step 3: Implement.**

`core/model/WeekRollEntry.kt`: `data class WeekRollEntry(val weekStart: LocalDate, val moments: List<PathMoment>)`.

`WeekRollBuilder.build(moments: List<PathMoment>, zone: ZoneId, today: LocalDate)`: same body, `pebble.happenedAt` → `moment.happenedAt`, `pebbles = …` → `moments = …`.

`PathViewModel`: constructor `private val moments: MomentReadServicing, private val stats: PathStatsServicing, private val drafts: PebbleDraftsServicing, private val writes: MomentWriteServicing`; `fetch()` calls `WeekRollBuilder.build(moments.loadPath(), ZoneId.systemDefault(), today)` and logs `"path_moments load failed"`; `PathCovers.pendingDeletion: PathMoment?`; `requestDelete(moment: PathMoment)`; `confirmDelete()` calls `writes.deleteMoment(target.id)`; the ripple getter becomes:

```kotlin
        val ripple: RippleSummary? by lazy {
            serverRipple?.let { server ->
                val zone = ZoneId.systemDefault()
                val activeToday =
                    entries.any { entry ->
                        entry.moments.any { it.isCompleted && it.createdAt.atZoneSameInstant(zone).toLocalDate() == today }
                    }
                server.copy(activeToday = activeToday)
            }
        }
```

`PebbleThumbnail` (backdrop only until Part 3):

```kotlin
@Composable
fun PebbleThumbnail(
    valence: Valence,
    palette: EmotionPalette?,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val accentHex = remember(primary) { primary.toRgbHex() }
    val frame =
        palette?.pebbleFrameColors(valence.intensity)
            ?: PebbleFrameColors(strokeHex = accentHex, fillHex = accentHex, fillOpacity = 1f)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        PebbleOutlineBackdrop(
            sizeGroup = valence.sizeGroup,
            polarity = valence.polarity,
            fillHex = frame.fillHex,
            fillOpacity = frame.fillOpacity,
            modifier = Modifier.aspectRatio(PebbleOutlineGeometry.aspectRatio(valence.sizeGroup)),
        )
    }
}
```

`PathPebbleRow(moment: PathMoment, positionIndex: Int, palette: EmotionPalette?, …)`: `isLarge = moment.valence.intensity >= 3`; `hasPhoto = moment.firstSnapPath != null`; the title is `moment.name ?: stringResource(R.string.moment_untitled)`; `metaLine` reads `moment.pebble?.emotion`; `PebbleThumbnail(valence = moment.valence, palette = palette, …)`; `PathPebbleRowMetrics.rowHeightDp(moment.valence.intensity, hasPhoto, positionIndex)`.

`WeekPebbleList`: iterate `entry.moments`; the palette is `moment.pebble?.emotion?.id?.let { palettes.palette(it) }`.

`PathScreen`: `DeleteConfirmDialog(pebbleName = target.name ?: stringResource(R.string.moment_untitled), …)`.

Strings: `<string name="moment_untitled">Untitled moment</string>` / `<string name="moment_untitled">Moment sans nom</string>`.

- [ ] **Step 4:** Run both test classes → PASS.

### Task 2.3: Soul and collection detail on `v_moments_full`

- [ ] **Step 1:** In `SoulDetailViewModelTest.kt` and `CollectionDetailViewModelTest.kt`, feed `FakeMomentReadService.withSoul` / `.inCollection` with `UiFixtures.momentDetail(...)`, assert the content list is `fixtures.map { it.toPathMoment() }`, and assert delete records `FakeMomentWriteService.deletedMoments`. Update `GroupPebblesByMonthTest` to `PathMoment` fixtures.
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Both ViewModels take `moments: MomentReadServicing` and `writes: MomentWriteServicing` in place of `writeService: PebbleWriteServicing`; their list field and `pendingDeletion` become `PathMoment`; `fetch` reads `soulsService.loadSoul(id) to moments.loadMomentsWithSoul(id).map { it.toPathMoment() }` (collections: `loadMomentsInCollection`); `confirmDelete` calls `writes.deleteMoment(target.id)`. Delete `loadPebbles` from `SoulsServicing`/`SoulsService`, `CollectionsServicing`/`CollectionsService` and their fakes. `GroupPebblesByMonth` takes `List<PathMoment>`. `PebbleRow(moment: PathMoment, palette, onTap, onDelete)` renders `moment.name ?: stringResource(R.string.moment_untitled)` and `PebbleThumbnail(valence = moment.valence, palette = palette, modifier = Modifier.size(40.dp))`. The screens' delete dialogs show the same fallback name.
- [ ] **Step 4:** Run → PASS.

### Task 2.4: The read page on `MomentDetail`

- [ ] **Step 1:** In `PebbleDetailViewModelTest.kt` swap `FakePebbleDetailService` for `FakeMomentReadService` (`moments = mapOf("moment-1" to UiFixtures.momentDetail())`), assert `Content.detail` is that `MomentDetail`, and add a case loading `momentDetail(pebble = null, name = null)`.
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3: Implement.**

`PebbleDetailViewModel`: constructor `private val moments: MomentReadServicing`; `PebbleDetailUiState.Content(val detail: MomentDetail)`; `fetch(id)` calls `moments.loadMoment(id)`; the log says `"moment load failed"`.

`PebbleDetailScreen(momentId: String, …)`: `val pebble = detail?.pebble`; `palette = pebble?.emotion?.id?.let(palettes::palette)`; share only `if (pebble?.visibility == Visibility.PUBLIC)` with `SharedPebbleLink.url(pebble.id)` (PD-17); `DetailTopBar(visibility = pebble?.visibility, …)`.

`PebbleReadView(detail: MomentDetail, palette: EmotionPalette?, modifier)`:

```kotlin
    val pebble = detail.pebble
    // … same Column as today …
        PebbleReadBanner(
            renderSvg = null,
            valence = pebble?.valence ?: Valence.NEUTRAL_MEDIUM,
            palette = palette,
            modifier = Modifier.fillMaxWidth(),
            snapStoragePath = detail.sortedSnaps.firstOrNull()?.storagePath,
        )
        PebbleReadTitle(
            name = detail.name ?: stringResource(R.string.moment_untitled),
            happenedAt = detail.happenedAt,
        )
        if (pebble != null) PebbleReadMeta(pebble = pebble)
        detail.description?.takeIf { it.isNotEmpty() }?.let { desc ->
            Text(desc, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface, modifier = Modifier.fillMaxWidth())
        }
        if (pebble != null && pebble.souls.isNotEmpty()) PebbleReadSouls(souls = pebble.souls)
```

`PebbleReadMeta(pebble: MomentPebble)` reads `pebble.emotion`, `pebble.domains`, `pebble.collections` exactly as it read the old detail. `PebbleReadSouls(souls: List<SoulRef>)` resolves glyphs from the ambient cache (PD-26), a leaf reading reference data where it renders:

```kotlin
@Composable
private fun PebbleReadSouls(souls: List<SoulRef>) {
    val cached = LocalReferenceDataService.current.souls.associateBy { it.id }
    // … same chunked Rows …
    DetailSoulCell(name = soul.name, glyph = cached[soul.id]?.glyph, modifier = Modifier.weight(1f))
}

@Composable
private fun DetailSoulCell(
    name: String,
    glyph: Glyph?,
    modifier: Modifier = Modifier,
) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GlyphImage(
            strokes = glyph?.strokes.orEmpty(),
            viewBox = glyph?.viewBox ?: "0 0 200 200",
            strokeColor = onSurfaceVariant,
            modifier = Modifier.size(72.dp),
        )
        Text(name, style = PebblesTheme.hand.bodyLeadHand, color = onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
```

(Check `SoulWithGlyph.glyph`'s exact type in `core/model/SoulWithGlyph.kt` and adapt the two property reads; if it is non-null, `cached[soul.id]?.glyph` is already nullable.)

- [ ] **Step 4:** Run `PebbleDetailViewModelTest` → PASS.

### Task 2.5: Photo removal rides `save_moment`

- [ ] **Step 1:** In `SnapUploadCoordinatorTest.kt` replace the `removeExisting` tests with:

```kotlin
    @Test
    fun `detaching a saved photo writes nothing and hands the snap back`() =
        runTest {
            val repo = FakeSnapWriteRepository()
            val coordinator = SnapUploadCoordinator(repo = repo, initialSnap = FormSnap.Existing("snap-1", "user-1/snap-1"))
            assertEquals(FormSnap.Existing("snap-1", "user-1/snap-1"), coordinator.detachExisting())
            assertNull(coordinator.formSnap)
            assertTrue(repo.deletedPrefixes.isEmpty())
            assertTrue(repo.deletedSnapIds.isEmpty())
        }

    @Test
    fun `detaching with nothing saved is a no-op`() {
        assertNull(SnapUploadCoordinator(repo = FakeSnapWriteRepository()).detachExisting())
    }
```

- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** In `SnapUploadCoordinator`, replace `removeExisting()` with:

```kotlin
    /**
     * Detach the saved photo from the form. Nothing is written here: the
     * edit's `save_moment` drops the row through its whole-set `snaps`, which
     * is also where "a moment needs a name or a photo" is enforced, and the
     * caller deletes the returned snap's files once that save succeeds (PD-15).
     */
    fun detachExisting(): FormSnap.Existing? {
        val existing = formSnap as? FormSnap.Existing ?: return null
        formSnap = null
        return existing
    }
```

Remove `deletePebbleMedia` from `SnapWriteRepositing`, `PebbleSnapRepository` and `FakeSnapWriteRepository`. Fix the KDoc of `FormSnap.Existing` ("triggers the eager `delete_pebble_media` RPC" → "detached in the form; the save drops it").
- [ ] **Step 4:** Run → PASS.

### Task 2.6: The edit form on two writers

- [ ] **Step 1: Rewrite the failing tests** in `EditPebbleViewModelTest.kt`. `Harness` takes `read: FakeMomentReadService`, `writes: FakeMomentWriteService`, keeps the others, and builds `EditPebbleViewModel(moments = read, writes = writes, karma = KarmaNotificationService(scope), achievements, supabase, snapRepo, media)`. Fixtures from `UiFixtures.momentDetail` (which has a domain, so no `makeValid` workaround). Cases:

```kotlin
    @Test
    fun `saving a completed moment writes the moment, then the pebble, then checks achievements`() =
        runTest {
            val h = Harness(backgroundScope)
            h.supabase.session = testSession("user-1")
            h.read.moments = mapOf("moment-1" to UiFixtures.momentDetail(description = "Old"))
            h.viewModel.start("moment-1")
            advanceUntilIdle()
            val state = h.viewModel.uiState.value as EditPebbleUiState.Content
            h.viewModel.onDraftChange(state.draft.copy(description = ""))
            h.viewModel.save()
            advanceUntilIdle()

            assertEquals(
                MomentSavePayload(
                    id = "moment-1",
                    name = "Morning walk",
                    description = "",
                    happenedAt = OffsetDateTime.parse("2026-10-12T06:40:00Z"),
                    snaps = emptyList(),
                ),
                h.writes.saved.single(),
            )
            assertEquals("moment-1", h.writes.completed.single().momentId)
            assertEquals(listOf("domain-1"), h.writes.completed.single().domainIds)
            assertEquals(listOf("pebble-new" to KarmaReason.PEBBLE_ENRICHED), h.writes.karmaReads)
            assertEquals(1, h.achievements.fireCheckCount)
        }

    @Test
    fun `an uncompleted moment saves the moment only`() =
        runTest {
            val h = Harness(backgroundScope)
            h.read.moments = mapOf("moment-1" to UiFixtures.momentDetail(pebble = null))
            h.viewModel.start("moment-1")
            advanceUntilIdle()
            assertFalse((h.viewModel.uiState.value as EditPebbleUiState.Content).hasPebble)
            h.viewModel.save()
            advanceUntilIdle()
            assertEquals(1, h.writes.saved.size)
            assertTrue(h.writes.completed.isEmpty())
            assertEquals(0, h.achievements.fireCheckCount)
        }

    @Test
    fun `a blank name without a photo cannot be saved`() =
        runTest {
            val h = Harness(backgroundScope)
            h.read.moments = mapOf("moment-1" to UiFixtures.momentDetail(pebble = null))
            h.viewModel.start("moment-1")
            advanceUntilIdle()
            val state = h.viewModel.uiState.value as EditPebbleUiState.Content
            h.viewModel.onDraftChange(state.draft.copy(name = "  "))
            assertFalse((h.viewModel.uiState.value as EditPebbleUiState.Content).canSave)
            h.viewModel.save()
            advanceUntilIdle()
            assertTrue(h.writes.saved.isEmpty())
        }

    @Test
    fun `removing the saved photo sends an empty snap set and deletes the files only after the save`() =
        runTest {
            val h = Harness(backgroundScope)
            h.supabase.session = testSession("user-1")
            h.read.moments =
                mapOf("moment-1" to UiFixtures.momentDetail(snaps = listOf(MomentSnap("snap-1", "user-1/snap-1", 0))))
            h.viewModel.start("moment-1")
            advanceUntilIdle()
            h.viewModel.onRemoveExistingSnap()
            assertTrue(h.snapRepo.deletedPrefixes.isEmpty())
            h.viewModel.save()
            advanceUntilIdle()
            assertEquals(emptyList<PebbleSnapPayload>(), h.writes.saved.single().snaps)
            assertEquals(listOf("user-1/snap-1"), h.snapRepo.deletedPrefixes)
        }

    @Test
    fun `a moment_empty refusal shows its copy and keeps the files`() =
        runTest {
            val h = Harness(backgroundScope)
            h.read.moments =
                mapOf("moment-1" to UiFixtures.momentDetail(name = "x", snaps = listOf(MomentSnap("snap-1", "user-1/snap-1", 0))))
            h.viewModel.start("moment-1")
            advanceUntilIdle()
            h.viewModel.onRemoveExistingSnap()
            h.writes.failNext = postgrestException("moment_empty")
            h.viewModel.save()
            advanceUntilIdle()
            val state = h.viewModel.uiState.value as EditPebbleUiState.Content
            assertEquals(R.string.moment_error_empty, state.saveErrorRes)
            assertFalse(state.isSaving)
            assertTrue(h.snapRepo.deletedPrefixes.isEmpty())
        }

    @Test
    fun `a pebble refusal after the moment saved shows the pebble copy`() =
        runTest {
            val h = Harness(backgroundScope)
            h.read.moments = mapOf("moment-1" to UiFixtures.momentDetail())
            h.viewModel.start("moment-1")
            advanceUntilIdle()
            h.writes.failOnComplete = postgrestException("soul_not_owned", sqlState = "42501", status = HttpStatusCode.Forbidden)
            h.viewModel.save()
            advanceUntilIdle()
            assertEquals(1, h.writes.saved.size)
            assertEquals(R.string.pebble_error_reference_gone, (h.viewModel.uiState.value as EditPebbleUiState.Content).saveErrorRes)
        }
```

- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3: Implement.**

`PebbleDraft` gains:

```kotlin
    /** The pebble half, for `complete_pebble`. */
    val answers: PebbleAnswers
        get() = PebbleAnswers(valence, emotionId, domainId, soulIds, collectionId, visibility)

    companion object {
        fun from(moment: MomentDetail): PebbleDraft {
            val pebble = moment.pebble
            return PebbleDraft(
                happenedAt = moment.happenedAt,
                name = moment.name.orEmpty(),
                description = moment.description.orEmpty(),
                emotionId = pebble?.emotion?.id,
                domainId = pebble?.domains?.firstOrNull()?.id,
                valence = pebble?.valence,
                soulIds = pebble?.souls?.map { it.id }.orEmpty(),
                collectionId = pebble?.collections?.firstOrNull()?.id,
                visibility = pebble?.visibility ?: Visibility.SECRET,
            )
        }
    }
```

(and delete `from(detail: PebbleDetail)`).

`EditPebbleUiState.Content`:

```kotlin
    data class Content(
        val draft: PebbleDraft,
        /** Whether the moment has your pebble: the pebble section and `complete_pebble` depend on it. */
        val hasPebble: Boolean,
        val snap: FormSnap? = null,
        val isSaving: Boolean = false,
        @StringRes val saveErrorRes: Int? = null,
    ) : EditPebbleUiState {
        /** A name or a photo (D3), and a complete pebble when there is one (D2). */
        val canSave: Boolean by lazy {
            !isSaving && (draft.name.isNotBlank() || snap != null) && (!hasPebble || draft.answers.isComplete)
        }
    }
```

`EditPebbleViewModel`: constructor `moments: MomentReadServicing, writes: MomentWriteServicing, karma, achievements, supabase, private val snapFiles: SnapWriteRepositing, media`; `loadedMomentId`; `private var detachedSnap: FormSnap.Existing? = null`; `load` builds `Content(draft = PebbleDraft.from(detail), hasPebble = detail.pebble != null)` and seeds the first sorted snap as today; `onRemoveExistingSnap()` becomes `detachedSnap = snaps.detachExisting() ?: detachedSnap`; `onDraftChange` drops the glyph bookkeeping; `onGlyphPicked` is deleted; `save()` becomes:

```kotlin
        fun save() {
            val state = content ?: return
            if (!state.canSave) return
            if (snaps.isUploading) {
                updateContent { it.copy(saveErrorRes = R.string.pebble_save_error_photo_uploading) }
                return
            }
            if (snaps.hasFailed) {
                updateContent { it.copy(saveErrorRes = R.string.pebble_save_error_photo_failed) }
                return
            }
            val momentId = loadedMomentId ?: return
            updateContent { it.copy(isSaving = true, saveErrorRes = null) }
            viewModelScope.launch {
                val id = userId
                // Always echo the whole set: existing verbatim, a fresh upload as its pair, [] when removed.
                val snapPayload =
                    when (val formSnap = snaps.formSnap) {
                        is FormSnap.Existing -> listOf(PebbleSnapPayload(formSnap.id, formSnap.storagePath, 0))
                        is FormSnap.Pending ->
                            snaps.pendingSnapForPayload()?.let { snap ->
                                id?.let { listOf(PebbleSnapPayload(snap.id, snap.storagePrefix(it), 0)) }
                            } ?: emptyList()
                        null -> emptyList()
                    }
                // Inside NonCancellable as before (#852): popping the entry must not abort an accepted write.
                withContext(NonCancellable) {
                    val moment =
                        runCatchingCancellable {
                            writes.saveMoment(
                                MomentSavePayload.edit(momentId, state.draft.name, state.draft.description, state.draft.happenedAt, snapPayload),
                            )
                        }
                    moment.exceptionOrNull()?.let { failure ->
                        Log.e(TAG, "save_moment failed", failure)
                        updateContent {
                            it.copy(isSaving = false, saveErrorRes = momentWriteErrorRes(failure.toDataError(), R.string.moment_save_error_generic))
                        }
                        return@withContext
                    }
                    detachedSnap?.let { snapFiles.deleteFiles(storagePrefix = it.storagePath) }
                    detachedSnap = null
                    if (!state.hasPebble) {
                        effectsOut.emit(EditPebbleEffect.Saved)
                        return@withContext
                    }
                    runCatchingCancellable { writes.completePebble(PebbleCompletePayload.from(momentId, state.draft.answers)) }
                        .fold(
                            onSuccess = { pebbleId ->
                                karma.notifyEarned(writes.karmaDelta(pebbleId, KarmaReason.PEBBLE_ENRICHED) ?: 0, KarmaReason.PEBBLE_ENRICHED)
                                achievements.fireCheck()
                                effectsOut.emit(EditPebbleEffect.Saved)
                            },
                            onFailure = { failure ->
                                Log.e(TAG, "complete_pebble failed", failure)
                                updateContent {
                                    it.copy(isSaving = false, saveErrorRes = momentWriteErrorRes(failure.toDataError(), R.string.pebble_save_error_generic))
                                }
                            },
                        )
                }
            }
        }
```

A failed save no longer calls `snaps.handleSaveFailure`: the uploaded files stay so a retry can reference them, and leaving the form (`dismiss` → `cancelAndCleanup`) is the cleanup (PD-5).

`PebbleForm`: delete the `GlyphRow` composable, `PickerKind.GLYPH`, the `GlyphPickerSheet` branch, and the parameters `selectedGlyph`, `onGlyphPicked`, `glyphPicker`, `renderSvg`, `strokeColor`, `renderHeight`, `isRemovingExistingSnap`; add

```kotlin
    /** Above the fields: the edit form's stone. */
    header: (@Composable () -> Unit)? = null,
    /** False on a moment without your pebble: only photo, when, name and description show. */
    showPebbleFields: Boolean = true,
```

render `header?.invoke()` where the `PebbleStaticRender` was, and wrap the mood, souls and optional sections in `if (showPebbleFields) { … }`. `ExistingSnapRow(isRemoving = false, …)`.

`EditPebbleScreen(momentId: String, onDismiss, onSaved, …)` (no `glyphPicker`): `saveEnabled = content?.canSave == true`; the form gets

```kotlin
                        header = {
                            val valence = state.draft.valence ?: Valence.NEUTRAL_MEDIUM
                            PebbleThumbnail(
                                valence = valence,
                                palette = state.draft.emotionId?.let(palettes::palette),
                                modifier = Modifier.fillMaxWidth().height(valence.sizeGroup.renderHeightDp.dp),
                            )
                        },
                        showPebbleFields = state.hasPebble,
```

and the `VisibilityChip` row renders only `if (state.hasPebble)`.

`CreatePebbleScreen`: drop the removed arguments (its `glyphPicker` parameter and the `selectedGlyph` state go; the compat `create_pebble` ignores glyph keys anyway). `PebbleWriteServicing` keeps `create` only; delete `update`, `delete`, `UpdateRequest`, `FUNCTION_UPDATE`, and `PebbleUpdatePayload`.

- [ ] **Step 4:** Run `EditPebbleViewModelTest`, `SnapUploadCoordinatorTest`, `PebbleDraftTest` → PASS.

### Task 2.7: Wiring and deletions

- [ ] **Step 1:** `PebblesEntryProvider`: `PebbleDetailScreen(momentId = key.momentId, onEditRequested = { navigator.navigate(PebblesKey.EditPebble(key.momentId)) })`; `EditPebbleScreen(momentId = key.momentId, onDismiss = navigator::goBack, onSaved = navigator::goBack)`; `CreatePebbleScreen(onCreated = { navigator.goBack() }, …)` with a comment that the compat wrapper returns a pebble id, not the moment id the detail key needs.
- [ ] **Step 2:** Delete `core/data/PathService.kt`, `core/data/PebbleDetailService.kt`, `core/model/Pebble.kt`, `core/model/PebbleDetail.kt`, `core/model/PebbleUpdatePayload.kt`, `testing/FakePathService.kt`, `testing/FakePebbleDetailService.kt`, `core/model/PebbleDecodingTest.kt`, `core/model/PebbleDetailDecodingTest.kt`; drop their bindings from `ServiceBindings` and `FakeServicesModule`, the `update`/`delete` overrides from `FakePebbleWriteService`, and the update-payload cases from `PebblePayloadTest`.
- [ ] **Step 3:** `git grep -nE '"path_pebbles"|"pebbles"\)|render_svg|delete_pebble_media|compose-pebble-update' app/src/main` → only comments, `PebbleWriteService` (create) and `ComposePebbleResponse`.
- [ ] **Step 4:** `./gradlew lint`; delete every string it reports as `UnusedResources` from **both** `values/strings.xml` and `values-fr/strings.xml` (expected: `create_glyph_header`, `photo_remove_error`; delete exactly what the report lists).

### Task 2.8: Previews

`app/src/screenshotTest/kotlin/app/pbbls/android/`: screenshot sources cannot see `src/test`, so create `MomentPreviewFixtures.kt` with the same `pathMoment`, `pathPebble`, `momentDetail`, `momentPebble`, `pebbleGlyph` builders as `UiFixtures` (Task 1.9 Step 3). Then:

- [ ] Port `PathRowScreenshots`, `PebbleRowScreenshots`, `PathScreenScreenshots`, `ListDetailScreenshots`, `PebbleDetailScreenshots`, `EditPebbleScreenshots` from `Pebble`/`PebbleDetail`/`render_svg` fixtures to `MomentPreviewFixtures` (keep every existing preview name so the diff shows changed references, not churn).
- [ ] Add `@PreviewTest` previews: `PathRow_UntitledPhotoOnly` (name null, photo, no pebble), `PebbleDetail_BareMoment` (no tiles, no souls, no badge), `EditPebble_BareMoment` (moment fields only).
- [ ] `./gradlew compileDebugScreenshotTestKotlin` → BUILD SUCCESSFUL. Do not run `updateDebugScreenshotTest`.

### Task 2.9: Robolectric journeys and gate

- [ ] **Step 1:** `ui/PebbleDeleteTest`: arm `FakeMomentReadService.path`, assert `FakeMomentWriteService.deletedMoments` after confirming, and that a failed delete (armed `failNext`) shows `R.string.pebble_delete_error` with the row still there. `ui/PathScreenTest`: arm `FakeMomentReadService`. `ui/NavigationTest` "Path opens a pebble's detail, and the detail opens its edit form": arm `FakeMomentReadService.moments`.
- [ ] **Step 2:** `./gradlew ktlintCheck testDebugUnitTest assembleDebug lint` → BUILD SUCCESSFUL.
- [ ] **Step 3:** Commits, in order: `feat(android): read the path from path_moments`, `feat(android): read moments from v_moments_full`, `feat(android): edit moments through save_moment and complete_pebble`, `test(android): port the path, detail and edit previews to moments`.
- [ ] **Step 4:** Open the PR with the `rebaseline-screenshots` label (row titles, the edit header and the detail all change); approve the held runs.

**Manual check on the `oxymore-eclipse` AVD (or the maintainer's device, asking before writes on the real account):** Path loads; a backfilled draft (photo or named, no pebble) shows as a row; the read page opens; edit saves name and description; removing the only photo of an untitled moment shows "A moment needs a name or a photo."

---

## Part 3 — The glyph drawn from strokes (`feat/1048-android-moments-stone`)

**Story:** pebbles carry their derived glyph again. The stone is composed on device the way the deleted engine composed it (shape strokes + glyph in a slot) and traced by the existing renderer. Every surface that took `renderSvg` now takes a `PebbleGlyph?`. The bare stone (no glyph, neutral tone) is added here as a renderer; Part 4 puts it on screen.

**File map** (paths under `app/src/main/` unless noted)

| Action | Path | Responsibility |
|---|---|---|
| Create | `res/raw/shape_{small,medium,large}_{lowlight,neutral,highlight}.svg` (9) | byte-identical copies of `docs/seeds/shape-seeds/{size}-{polarity}.svg` |
| Create | `kotlin/.../core/ui/render/StoneComposer.kt` | slot table, shape-seed parse, composition into a `PebbleSvgModel` |
| Create | `kotlin/.../core/ui/render/StoneShapes.kt` | (size, polarity) → `R.raw.shape_*`; `rememberStoneModel` |
| Create | `kotlin/.../core/ui/render/PebbleStone.kt` | `PebbleStone` (backdrop + traced stone) and `BlankStone` |
| Modify | `kotlin/.../core/ui/render/PebbleStaticRender.kt` | traces a model, keyed by a cache key; no SVG string |
| Modify | `kotlin/.../core/ui/render/PebbleSvgModel.kt:220` | `parseViewBox` becomes `internal` |
| Modify | `kotlin/.../core/ui/render/wobble/WobbleRenderer.kt:55-58` | `pebbleArt(key: String, model)` (the string was only ever a cache key) |
| Modify | `kotlin/.../core/ui/render/PebbleThumbnail.kt` | `(valence, glyph, palette)` → `PebbleStone` |
| Modify | `kotlin/.../features/path/read/PebbleReadPetroglyph.kt`, `PebbleReadBanner.kt`, `PebbleSnapFrame.kt`, `PebbleReadView.kt` | `renderSvg: String?` → `glyph: PebbleGlyph?` |
| Modify | `kotlin/.../features/path/record/steps/RecordSuccessStep.kt`, `RecordFlowScreen.kt` | `glyph: PebbleGlyph?`; the stone always draws |
| Modify | `kotlin/.../features/path/components/PathPebbleRow.kt`, `core/ui/PebbleRow.kt`, `features/path/EditPebbleScreen.kt`, `EditPebbleViewModel.kt` | pass the glyph |
| Modify | `res/values/strings.xml`, `values-fr/strings.xml` | `moment_blank_a11y` |
| Delete | `app/src/screenshotTest/.../PebbleSvgFixtures.kt`, `PebbleSvgScreenshots.kt`, `apps/android/scripts/gen-pebble-svg-fixtures.ts` | server-SVG fixtures; the generator imports the deleted engine |
| Create | `app/src/screenshotTest/.../StoneScreenshots.kt` | 9 valences × glyph/no glyph, blank stone, dark |
| Modify | `apps/android/CLAUDE.md` § "Path rendering" | describe the on-device stone |
| Test | `app/src/test/.../core/ui/render/StoneComposerTest.kt` | |

### Task 3.1: Shape seeds

- [ ] **Step 1:** Copy, renaming to the Android resource alphabet:

```bash
for size in small medium large; do for pol in lowlight neutral highlight; do
  cp "docs/seeds/shape-seeds/$size-$pol.svg" "apps/android/app/src/main/res/raw/shape_${size}_${pol}.svg"
done; done
```

- [ ] **Step 2:** Commit alone: `feat(android): bundle the pebble shape seeds`.

### Task 3.2: The composer

**Files:** Create `core/ui/render/StoneComposer.kt`; Test `app/src/test/kotlin/app/pbbls/android/core/ui/render/StoneComposerTest.kt`; Modify `PebbleSvgModel.kt:220` (`private fun parseViewBox` → `internal fun parseViewBox`).

- [ ] **Step 1: Write the failing test**

```kotlin
package app.pbbls.android.core.ui.render

import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.core.model.PebbleGlyph
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File

/** The on-device stone reproduces the deleted engine's composition (`compose.ts` + `layout.ts`, git 13b5ae3b^). */
class StoneComposerTest {
    private fun seed(
        size: ValenceSizeGroup,
        polarity: ValencePolarity,
    ) = File("src/main/res/raw/shape_${size.key}_${polarity.key}.svg")

    private fun glyph(viewBox: String = "0 0 200 200") =
        PebbleGlyph(
            id = "g",
            strokes = listOf(GlyphStroke("M 20 100 Q 100 20 180 100", 6.0), GlyphStroke("M 50 50 L 150 150", 6.0)),
            viewBox = viewBox,
        )

    @Test
    fun `the bundled seeds are byte-identical to the repo seeds`() {
        ValenceSizeGroup.entries.forEach { size ->
            ValencePolarity.entries.forEach { polarity ->
                val repo = File("../../../docs/seeds/shape-seeds/${size.key}-${polarity.key}.svg")
                assertArrayEquals("$size $polarity", repo.readBytes(), seed(size, polarity).readBytes())
            }
        }
    }

    @Test
    fun `each seed keeps only its stroked paths, on the engine canvas`() {
        val expected =
            mapOf(
                (ValenceSizeGroup.SMALL to ValencePolarity.LOWLIGHT) to 1,
                (ValenceSizeGroup.SMALL to ValencePolarity.NEUTRAL) to 1,
                (ValenceSizeGroup.SMALL to ValencePolarity.HIGHLIGHT) to 1,
                (ValenceSizeGroup.MEDIUM to ValencePolarity.LOWLIGHT) to 2,
                (ValenceSizeGroup.MEDIUM to ValencePolarity.NEUTRAL) to 2,
                (ValenceSizeGroup.MEDIUM to ValencePolarity.HIGHLIGHT) to 3,
                (ValenceSizeGroup.LARGE to ValencePolarity.LOWLIGHT) to 2,
                (ValenceSizeGroup.LARGE to ValencePolarity.NEUTRAL) to 3,
                (ValenceSizeGroup.LARGE to ValencePolarity.HIGHLIGHT) to 7,
            )
        val canvas = mapOf(ValenceSizeGroup.SMALL to (250f to 200f), ValenceSizeGroup.MEDIUM to (260f to 260f), ValenceSizeGroup.LARGE to (260f to 310f))
        expected.forEach { (key, paths) ->
            val model = StoneComposer.shapeModel(seed(key.first, key.second).readText())!!
            val layer = model.layers.single()
            assertEquals("$key", PebbleSvgModel.Layer.Kind.SHAPE, layer.kind)
            assertEquals("$key", paths, layer.paths.size)
            assertEquals("$key", canvas.getValue(key.first), model.viewBox.width to model.viewBox.height)
        }
    }

    @Test
    fun `every size and polarity has the engine's glyph slot`() {
        val slots =
            mapOf(
                (ValenceSizeGroup.SMALL to ValencePolarity.HIGHLIGHT) to GlyphSlot(37.5f, 30f, 140f),
                (ValenceSizeGroup.SMALL to ValencePolarity.NEUTRAL) to GlyphSlot(25f, 30f, 140f),
                (ValenceSizeGroup.SMALL to ValencePolarity.LOWLIGHT) to GlyphSlot(30f, 30f, 140f),
                (ValenceSizeGroup.MEDIUM to ValencePolarity.HIGHLIGHT) to GlyphSlot(26f, 84f, 150f),
                (ValenceSizeGroup.MEDIUM to ValencePolarity.NEUTRAL) to GlyphSlot(26f, 55f, 150f),
                (ValenceSizeGroup.MEDIUM to ValencePolarity.LOWLIGHT) to GlyphSlot(39f, 26f, 150f),
                (ValenceSizeGroup.LARGE to ValencePolarity.HIGHLIGHT) to GlyphSlot(50f, 75f, 160f),
                (ValenceSizeGroup.LARGE to ValencePolarity.NEUTRAL) to GlyphSlot(26f, 88f, 160f),
                (ValenceSizeGroup.LARGE to ValencePolarity.LOWLIGHT) to GlyphSlot(26f, 75f, 160f),
            )
        slots.forEach { (key, slot) -> assertEquals("$key", slot, StoneComposer.glyphSlot(key.first, key.second)) }
    }

    @Test
    fun `a 200 box glyph lands in its slot at the engine's scale`() {
        val shape = StoneComposer.shapeModel(seed(ValenceSizeGroup.MEDIUM, ValencePolarity.NEUTRAL).readText())!!
        val stone = StoneComposer.compose(shape, ValenceSizeGroup.MEDIUM, ValencePolarity.NEUTRAL, glyph())
        val layer = stone.layers.last()
        assertEquals(PebbleSvgModel.Layer.Kind.GLYPH, layer.kind)
        assertEquals(Affine(0.75f, 0f, 0f, 0.75f, 26f, 55f), layer.transform)
        assertEquals(listOf("M 20 100 Q 100 20 180 100", "M 50 50 L 150 150"), layer.paths.map { it.d })
    }

    @Test
    fun `an offset, non-square view box is fitted and shifted into the slot`() {
        val shape = StoneComposer.shapeModel(seed(ValenceSizeGroup.MEDIUM, ValencePolarity.NEUTRAL).readText())!!
        val layer = StoneComposer.compose(shape, ValenceSizeGroup.MEDIUM, ValencePolarity.NEUTRAL, glyph("-10 -10 400 200")).layers.last()
        assertEquals(Affine(0.375f, 0f, 0f, 0.375f, 29.75f, 58.75f), layer.transform)
    }

    @Test
    fun `no glyph, or an unreadable view box, leaves the bare shape`() {
        val shape = StoneComposer.shapeModel(seed(ValenceSizeGroup.SMALL, ValencePolarity.LOWLIGHT).readText())!!
        assertSame(shape, StoneComposer.compose(shape, ValenceSizeGroup.SMALL, ValencePolarity.LOWLIGHT, null))
        assertSame(shape, StoneComposer.compose(shape, ValenceSizeGroup.SMALL, ValencePolarity.LOWLIGHT, glyph("garbage")))
    }

    @Test
    fun `the cache key follows the glyph`() {
        val a = StoneComposer.cacheKey(ValenceSizeGroup.SMALL, ValencePolarity.LOWLIGHT, glyph())
        val b = StoneComposer.cacheKey(ValenceSizeGroup.SMALL, ValencePolarity.LOWLIGHT, null)
        assertNotEquals(a, b)
    }
}
```

- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3: Implement** `core/ui/render/StoneComposer.kt`:

```kotlin
package app.pbbls.android.core.ui.render

import app.pbbls.android.core.model.PebbleGlyph
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import kotlin.math.min

/** Top-left and side of the glyph's square slot in the stone canvas. */
internal data class GlyphSlot(
    val x: Float,
    val y: Float,
    val size: Float,
)

/**
 * Composes a stone on device, as the server engine did before spec D6 deleted
 * it (`functions/_shared/engine/compose.ts` and `layout.ts`, readable at git
 * `13b5ae3b^`): the shape seed's stroked paths as `layer:shape`, then the
 * glyph's strokes as `layer:glyph`, its view box fitted into the slot.
 *
 * One deliberate departure (PD-20): no bounding-box normalization of the
 * strokes. The glyph's own view box maps into the slot, which is how every
 * other glyph surface on Android draws a glyph.
 */
internal object StoneComposer {
    private val slots: Map<Pair<ValenceSizeGroup, ValencePolarity>, GlyphSlot> =
        mapOf(
            (ValenceSizeGroup.SMALL to ValencePolarity.HIGHLIGHT) to GlyphSlot(37.5f, 30f, 140f),
            (ValenceSizeGroup.SMALL to ValencePolarity.NEUTRAL) to GlyphSlot(25f, 30f, 140f),
            (ValenceSizeGroup.SMALL to ValencePolarity.LOWLIGHT) to GlyphSlot(30f, 30f, 140f),
            (ValenceSizeGroup.MEDIUM to ValencePolarity.HIGHLIGHT) to GlyphSlot(26f, 84f, 150f),
            (ValenceSizeGroup.MEDIUM to ValencePolarity.NEUTRAL) to GlyphSlot(26f, 55f, 150f),
            (ValenceSizeGroup.MEDIUM to ValencePolarity.LOWLIGHT) to GlyphSlot(39f, 26f, 150f),
            (ValenceSizeGroup.LARGE to ValencePolarity.HIGHLIGHT) to GlyphSlot(50f, 75f, 160f),
            (ValenceSizeGroup.LARGE to ValencePolarity.NEUTRAL) to GlyphSlot(26f, 88f, 160f),
            (ValenceSizeGroup.LARGE to ValencePolarity.LOWLIGHT) to GlyphSlot(26f, 75f, 160f),
        )

    private val anyFill = Regex("fill=\"[^\"]*\"")
    private val svgOpen = Regex("<svg[^>]*>")

    fun glyphSlot(
        size: ValenceSizeGroup,
        polarity: ValencePolarity,
    ): GlyphSlot = slots.getValue(size to polarity)

    /**
     * The seed as a single `layer:shape`. Every fill becomes `none`, as the
     * engine's `stripFills` did, so the filled detail paths (no stroke) are
     * dropped by [parsePebbleSvg] and only the stroked outline is traced.
     */
    fun shapeModel(seedSvg: String): PebbleSvgModel? =
        parsePebbleSvg(
            seedSvg
                .replace(anyFill, "fill=\"none\"")
                .replaceFirst(svgOpen) { "${it.value}<g id=\"layer:shape\">" }
                .replace("</svg>", "</g></svg>"),
        )

    fun compose(
        shape: PebbleSvgModel,
        size: ValenceSizeGroup,
        polarity: ValencePolarity,
        glyph: PebbleGlyph?,
    ): PebbleSvgModel {
        val layer = glyph?.let { glyphLayer(it, glyphSlot(size, polarity)) } ?: return shape
        return shape.copy(layers = shape.layers + layer)
    }

    fun cacheKey(
        size: ValenceSizeGroup,
        polarity: ValencePolarity,
        glyph: PebbleGlyph?,
    ): String = "stone:${size.key}:${polarity.key}:${glyph?.id ?: "-"}"

    private fun glyphLayer(
        glyph: PebbleGlyph,
        slot: GlyphSlot,
    ): PebbleSvgModel.Layer? {
        val box = parseViewBox(glyph.viewBox) ?: return null
        if (box.width <= 0f || box.height <= 0f) return null
        val scale = min(slot.size / box.width, slot.size / box.height)
        val transform =
            Affine
                .translate(slot.x, slot.y)
                .concat(Affine.scale(scale, scale))
                .concat(Affine.translate(-box.minX, -box.minY))
        return PebbleSvgModel.Layer(
            kind = PebbleSvgModel.Layer.Kind.GLYPH,
            transform = transform,
            opacity = 1f,
            paths = glyph.strokes.map { PebbleSvgModel.PathSpec(d = it.d, transform = Affine.IDENTITY) },
        )
    }
}
```

- [ ] **Step 4:** Run → PASS. (`PebbleSvgModelTest` and `WobbleRendererTest` still pass: the parser is unchanged.)
- [ ] **Step 5:** Commit `feat(android): compose the stone from its shape seed and glyph strokes`.

### Task 3.3: Draw it

- [ ] **Step 1:** `StoneShapes.kt`:

```kotlin
package app.pbbls.android.core.ui.render

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.pbbls.android.R
import app.pbbls.android.core.model.PebbleGlyph
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import java.util.concurrent.ConcurrentHashMap

/** Explicit (size, polarity) → seed resource, compile-checked like [OutlineAssets]. */
object StoneShapeAssets {
    private val ids: Map<Pair<ValenceSizeGroup, ValencePolarity>, Int> =
        mapOf(
            (ValenceSizeGroup.SMALL to ValencePolarity.LOWLIGHT) to R.raw.shape_small_lowlight,
            (ValenceSizeGroup.SMALL to ValencePolarity.NEUTRAL) to R.raw.shape_small_neutral,
            (ValenceSizeGroup.SMALL to ValencePolarity.HIGHLIGHT) to R.raw.shape_small_highlight,
            (ValenceSizeGroup.MEDIUM to ValencePolarity.LOWLIGHT) to R.raw.shape_medium_lowlight,
            (ValenceSizeGroup.MEDIUM to ValencePolarity.NEUTRAL) to R.raw.shape_medium_neutral,
            (ValenceSizeGroup.MEDIUM to ValencePolarity.HIGHLIGHT) to R.raw.shape_medium_highlight,
            (ValenceSizeGroup.LARGE to ValencePolarity.LOWLIGHT) to R.raw.shape_large_lowlight,
            (ValenceSizeGroup.LARGE to ValencePolarity.NEUTRAL) to R.raw.shape_large_neutral,
            (ValenceSizeGroup.LARGE to ValencePolarity.HIGHLIGHT) to R.raw.shape_large_highlight,
        )

    fun resId(
        size: ValenceSizeGroup,
        polarity: ValencePolarity,
    ): Int = ids.getValue(size to polarity)
}

/** Nine small assets, parsed once per process. */
private val parsedShapes = ConcurrentHashMap<Int, PebbleSvgModel>()

@Composable
internal fun rememberStoneModel(
    valence: Valence,
    glyph: PebbleGlyph?,
): PebbleSvgModel? {
    val context = LocalContext.current
    val resId = StoneShapeAssets.resId(valence.sizeGroup, valence.polarity)
    val shape =
        remember(resId) {
            parsedShapes[resId]
                ?: context.resources
                    .openRawResource(resId)
                    .bufferedReader()
                    .use { it.readText() }
                    .let(StoneComposer::shapeModel)
                    ?.also { parsedShapes[resId] = it }
                    .also { if (it == null) Log.e("stone", "shape seed $resId did not parse") }
        }
    return remember(shape, glyph, valence) {
        shape?.let { StoneComposer.compose(it, valence.sizeGroup, valence.polarity, glyph) }
    }
}
```

- [ ] **Step 2:** `PebbleStaticRender` becomes `internal fun PebbleStaticRender(model: PebbleSvgModel, cacheKey: String, strokeHex: String, modifier: Modifier = Modifier)`: delete the `svg` parameter, the `parsePebbleSvg` call and the `PebbleSvg` fallback; an unreadable `strokeHex` logs and draws nothing; `buildWobbleLayers(cacheKey, model)` calls `WobbleRenderer.pebbleArt(cacheKey, model)` (rename that parameter from `svg` to `key`; its KDoc already says it is a content key). Update the KDoc: "Traces a composed stone (`StoneComposer`)…".
- [ ] **Step 3:** `PebbleStone.kt`:

```kotlin
package app.pbbls.android.core.ui.render

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.toRgbHex
import app.pbbls.android.core.model.PebbleFrameColors
import app.pbbls.android.core.model.PebbleGlyph
import app.pbbls.android.core.model.Valence

/**
 * A stone: the outline silhouette filled with [colors], and the composed
 * shape + derived glyph traced on top at one weight, down-scaled per
 * [PebbleOutlineGeometry] as the server render used to be.
 */
@Composable
fun PebbleStone(
    valence: Valence,
    glyph: PebbleGlyph?,
    colors: PebbleFrameColors,
    modifier: Modifier = Modifier,
) {
    val sizeGroup = valence.sizeGroup
    val model = rememberStoneModel(valence, glyph)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.aspectRatio(PebbleOutlineGeometry.aspectRatio(sizeGroup)),
            contentAlignment = Alignment.Center,
        ) {
            PebbleOutlineBackdrop(
                sizeGroup = sizeGroup,
                polarity = valence.polarity,
                fillHex = colors.fillHex,
                fillOpacity = colors.fillOpacity,
                modifier = Modifier.fillMaxSize(),
            )
            if (model != null) {
                PebbleStaticRender(
                    model = model,
                    cacheKey = StoneComposer.cacheKey(sizeGroup, valence.polarity, glyph),
                    strokeHex = colors.strokeHex,
                    modifier = Modifier.fillMaxSize().scale(PebbleOutlineGeometry.pebbleScale(sizeGroup)),
                )
            }
        }
    }
}

/**
 * An uncompleted moment (PD-11): the medium neutral stone with its shape
 * strokes and no glyph, in theme roles rather than an emotion palette.
 */
@Composable
fun BlankStone(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val colors =
        remember(scheme.surfaceVariant, scheme.outline) {
            PebbleFrameColors(strokeHex = scheme.outline.toRgbHex(), fillHex = scheme.surfaceVariant.toRgbHex(), fillOpacity = 1f)
        }
    val label = stringResource(R.string.moment_blank_a11y)
    PebbleStone(
        valence = Valence.NEUTRAL_MEDIUM,
        glyph = null,
        colors = colors,
        modifier = modifier.semantics { contentDescription = label },
    )
}
```

Strings: `<string name="moment_blank_a11y">Not a pebble yet</string>` / `<string name="moment_blank_a11y">Pas encore un galet</string>`.

- [ ] **Step 4:** Rewire the callers:
  - `PebbleThumbnail(valence: Valence, glyph: PebbleGlyph?, palette: EmotionPalette?, modifier)` → the frame colours as today, then `PebbleStone(valence, glyph, frame, modifier)`.
  - `PebbleReadPetroglyph(valence: Valence, glyph: PebbleGlyph?, palette: EmotionPalette?, modifier)` → resolve `PetroglyphColors` as today, then `PebbleStone(valence, glyph, PebbleFrameColors(colors.strokeHex, colors.fillHex, colors.fillOpacity), modifier)`.
  - `PebbleReadBanner` and `PebbleSnapFrame`: rename `renderSvg: String?` to `glyph: PebbleGlyph?` and pass it down. `PebbleReadView` passes `glyph = pebble?.glyph`.
  - `PathPebbleRow`: `PebbleThumbnail(valence = moment.valence, glyph = moment.pebble?.glyph, palette = palette, …)`; `PebbleRow` likewise.
  - `RecordSuccessStep(name, glyph: PebbleGlyph?, karmaDelta, valence, palette, onExit)`: the stone always draws (a null glyph is a bare stone, not an absent one). `RecordFlowScreen` passes `glyph = null` (the eleven-step flow publishes through `compose-pebble`, which returns no glyph; it is deleted in Part 7).
  - Edit: `EditPebbleUiState.Content` gains `val glyph: PebbleGlyph?` (loaded from `detail.pebble?.glyph`), and the header becomes `PebbleThumbnail(valence = valence, glyph = state.glyph, palette = …)`.
- [ ] **Step 5:** `./gradlew ktlintCheck testDebugUnitTest assembleDebug` → BUILD SUCCESSFUL. Commit `feat(android): draw every pebble's derived glyph from its strokes`.

### Task 3.4: Previews, docs, dead fixtures

- [ ] **Step 1:** Delete `PebbleSvgFixtures.kt`, `PebbleSvgScreenshots.kt` and `apps/android/scripts/gen-pebble-svg-fixtures.ts`. Create `StoneScreenshots.kt` with `@PreviewTest` previews: `Stones_AllValences_Glyph` (a 3×3 grid of `PebbleStone` with `MomentPreviewFixtures.pebbleGlyph()`, joy palette), `Stones_AllValences_NoGlyph`, `Stone_Blank` (light and dark, using the existing `PreviewVariants` dark annotation). Every Path, row, detail and edit preview now shows a glyph: they change by design.
- [ ] **Step 2:** In `apps/android/CLAUDE.md` § "Path rendering", replace the first bullet with: "**The stone is composed on device** (`core/ui/render/StoneComposer.kt`): the shape seed for the valence (`res/raw/shape_{size}_{polarity}.svg`, byte-identical copies of `docs/seeds/shape-seeds/`, guarded by `StoneComposerTest`) plus the pebble's derived glyph strokes in the deleted engine's glyph slot, traced at one weight by `PebbleStaticRender`. There is no `render_svg` (spec D6)." Change "the row is outline + `render_svg`" to "the row is outline + composed stone", and delete the "Engine-composed SVG fixtures" bullet. This is a description of the code this part changes, not a learning.
- [ ] **Step 3:** `./gradlew compileDebugScreenshotTestKotlin lint` → BUILD SUCCESSFUL (lint may report nothing new; if it reports `UnusedResources`, delete those strings from both files).
- [ ] **Step 4:** Commit `test(android): preview the composed stones` and `docs(android): describe the on-device stone`. PR with `rebaseline-screenshots`.

**Manual check:** on the AVD, a pebble in a collection with a glyph shows that glyph; one without shows its first domain's default glyph; dark mode reads.

---

## Part 4 — Bare moments on Path and the read page (`feat/1048-android-moments-path`)

**Story:** an uncompleted moment looks like one: a blank stone with its photo on Path, and a read page without pebble sections. Deleting from any row or from the read page asks what to delete when a pebble exists (PD-16).

**File map** (under `app/src/main/kotlin/app/pbbls/android/`)

| Action | Path | Responsibility |
|---|---|---|
| Modify | `features/path/components/PathPebbleRow.kt` | blank stone for an uncompleted moment |
| Modify | `features/path/read/PebbleReadPetroglyph.kt`, `PebbleReadBanner.kt`, `PebbleSnapFrame.kt`, `PebbleReadView.kt` | `valence: Valence?`; null draws `BlankStone` |
| Create | `core/model/MomentDeleteChoice.kt` | `PEBBLE_ONLY` / `MOMENT` |
| Create | `core/data/MomentDeletion.kt` | `MomentWriteServicing.delete(momentId, pebbleId, choice)` |
| Create | `core/ui/MomentDeleteDialog.kt` | the one- or two-choice dialog |
| Modify | `features/path/PathViewModel.kt`, `PathScreen.kt` | `confirmDelete(choice)`; `DeleteConfirmDialog` deleted |
| Modify | `features/profile/SoulDetailViewModel.kt`, `SoulDetailScreen.kt`, `CollectionDetailViewModel.kt`, `CollectionDetailScreen.kt` | same |
| Modify | `features/path/PebbleDetailViewModel.kt`, `PebbleDetailScreen.kt` | delete from the read page |
| Modify | `navigation/PebblesEntryProvider.kt` | `onMomentDeleted = { navigator.closeDetail(…) }` |
| Modify | strings | three delete strings |

### Task 4.1: Blank stones

- [ ] **Step 1:** `PathPebbleRow`: replace the thumbnail call with

```kotlin
            val stoneSize = if (isLarge) 96.dp else 56.dp
            if (moment.isCompleted) {
                PebbleThumbnail(valence = moment.valence, glyph = moment.pebble?.glyph, palette = palette, modifier = Modifier.size(stoneSize))
            } else {
                BlankStone(modifier = Modifier.size(stoneSize))
            }
```

`PebbleReadPetroglyph(valence: Valence?, glyph, palette, modifier)`: `if (valence == null) { BlankStone(modifier); return }`. `PebbleReadBanner`, `PebbleSnapFrame` take `valence: Valence?`; `PebbleReadView` passes `valence = pebble?.valence`. `EditPebbleScreen`'s header shows `BlankStone` when `!state.hasPebble`.
- [ ] **Step 2:** Previews: add `PathRow_BlankStoneNoPhoto`, `PathRow_BlankStoneWithPhoto`, `PathScreen_MixedWeek` (completed, photo-only, named bare), and change `PebbleDetail_BareMoment` (Part 2) to show the blank stone. `./gradlew compileDebugScreenshotTestKotlin` → BUILD SUCCESSFUL.
- [ ] **Step 3:** Commit `feat(android): draw uncompleted moments as blank stones`.

### Task 4.2: Delete what the user means

- [ ] **Step 1: Write the failing tests.** In `PathViewModelTest`:

```kotlin
    @Test
    fun `deleting only the pebble keeps the moment`() =
        runTest {
            val h = Harness(backgroundScope)
            val moment = UiFixtures.pathMoment(id = "m-1", pebble = UiFixtures.pathPebble(id = "p-1"))
            h.read.path = listOf(moment)
            h.viewModel.retry()
            advanceUntilIdle()
            h.viewModel.requestDelete(moment)
            h.viewModel.confirmDelete(MomentDeleteChoice.PEBBLE_ONLY)
            advanceUntilIdle()
            assertEquals(listOf("p-1"), h.write.deletedPebbles)
            assertTrue(h.write.deletedMoments.isEmpty())
            assertEquals(2, h.read.loadPathCount)
        }

    @Test
    fun `deleting the moment deletes the moment`() =
        runTest {
            val h = Harness(backgroundScope)
            val moment = UiFixtures.pathMoment(id = "m-1")
            h.read.path = listOf(moment)
            h.viewModel.retry()
            advanceUntilIdle()
            h.viewModel.requestDelete(moment)
            h.viewModel.confirmDelete(MomentDeleteChoice.MOMENT)
            advanceUntilIdle()
            assertEquals(listOf("m-1"), h.write.deletedMoments)
            assertTrue(h.write.deletedPebbles.isEmpty())
        }
```

Mirror both cases in `SoulDetailViewModelTest` and `CollectionDetailViewModelTest`. In `PebbleDetailViewModelTest`:

```kotlin
    @Test
    fun `deleting the moment from the read page closes it`() =
        runTest {
            val read = FakeMomentReadService(moments = mapOf("m-1" to UiFixtures.momentDetail(id = "m-1")))
            val write = FakeMomentWriteService()
            val vm = PebbleDetailViewModel(read, write)
            val effects = recordEffects(vm.effects)
            vm.start("m-1")
            advanceUntilIdle()
            vm.requestDelete()
            vm.confirmDelete(MomentDeleteChoice.MOMENT)
            advanceUntilIdle()
            assertEquals(listOf("m-1"), write.deletedMoments)
            assertEquals(listOf(PebbleDetailEffect.MomentDeleted), effects.values)
            effects.stop()
        }

    @Test
    fun `deleting only the pebble reloads the page as a bare moment`() =
        runTest {
            val read = FakeMomentReadService(moments = mapOf("m-1" to UiFixtures.momentDetail(id = "m-1")))
            val write = FakeMomentWriteService()
            val vm = PebbleDetailViewModel(read, write)
            vm.start("m-1")
            advanceUntilIdle()
            read.moments = mapOf("m-1" to UiFixtures.momentDetail(id = "m-1", pebble = null))
            vm.requestDelete()
            vm.confirmDelete(MomentDeleteChoice.PEBBLE_ONLY)
            advanceUntilIdle()
            assertEquals(listOf("pebble-1"), write.deletedPebbles)
            assertNull((vm.uiState.value as PebbleDetailUiState.Content).detail.pebble)
        }
```

(`recordEffects` is `testing/EffectRecorder.kt`.)

- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3: Implement.**

```kotlin
package app.pbbls.android.core.model

/** What a delete removes (PD-16). Only offered as a choice when the moment has your pebble. */
enum class MomentDeleteChoice { PEBBLE_ONLY, MOMENT }
```

```kotlin
package app.pbbls.android.core.data

import app.pbbls.android.core.model.MomentDeleteChoice

/** `delete_pebble` takes the PEBBLE id and keeps the moment; `delete_moment` takes the moment id. */
suspend fun MomentWriteServicing.delete(
    momentId: String,
    pebbleId: String?,
    choice: MomentDeleteChoice,
) = when (choice) {
    MomentDeleteChoice.MOMENT -> deleteMoment(momentId)
    MomentDeleteChoice.PEBBLE_ONLY -> deletePebble(requireNotNull(pebbleId) { "no pebble to delete on $momentId" })
}
```

`core/ui/MomentDeleteDialog.kt`:

```kotlin
package app.pbbls.android.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import app.pbbls.android.R
import app.pbbls.android.core.model.MomentDeleteChoice

/**
 * The delete confirmation for a moment (PD-16). Without your pebble it is the
 * plain "Delete <name>? This can't be undone." With one, it asks which: the
 * pebble (the moment stays, uncompleted) or the whole moment.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MomentDeleteDialog(
    name: String,
    hasPebble: Boolean,
    onConfirm: (MomentDeleteChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceContainerHigh,
        title = {
            Text(
                text = stringResource(R.string.pebble_delete_confirm_title, name),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = colors.onSurface,
            )
        },
        text = {
            Text(
                text = stringResource(if (hasPebble) R.string.moment_delete_choice_message else R.string.pebble_delete_confirm_message),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
            )
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (hasPebble) {
                    TextButton(onClick = { onConfirm(MomentDeleteChoice.PEBBLE_ONLY) }) {
                        Text(stringResource(R.string.moment_delete_pebble_only), style = MaterialTheme.typography.labelLarge, color = colors.error)
                    }
                }
                TextButton(onClick = { onConfirm(MomentDeleteChoice.MOMENT) }) {
                    Text(
                        stringResource(if (hasPebble) R.string.moment_delete_moment else R.string.pebble_delete),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.error,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelLarge, color = colors.primary)
            }
        },
    )
}
```

Strings (EN / FR):

```xml
<string name="moment_delete_choice_message">Deleting the moment also deletes its pebble and its photo.</string>
<string name="moment_delete_pebble_only">Delete the pebble, keep the moment</string>
<string name="moment_delete_moment">Delete the moment</string>
```

```xml
<string name="moment_delete_choice_message">Supprimer le moment supprime aussi son galet et sa photo.</string>
<string name="moment_delete_pebble_only">Supprimer le galet, garder le moment</string>
<string name="moment_delete_moment">Supprimer le moment</string>
```

`PathViewModel.confirmDelete(choice: MomentDeleteChoice)` runs `writes.delete(target.id, target.pebble?.id, choice)` then `reload()`; failure sets `didDeleteFail` as today. `PathScreen` shows `MomentDeleteDialog(name = target.name ?: stringResource(R.string.moment_untitled), hasPebble = target.isCompleted, onConfirm = { choice -> viewModel.confirmDelete(choice); if (choice == MomentDeleteChoice.MOMENT) onDeleteConfirmed(target.id) }, onDismiss = viewModel::cancelDelete)`; delete `DeleteConfirmDialog` and switch the two `PebbleDetailScreenshots` previews that used it to `MomentDeleteDialog` (one per `hasPebble`). `SoulDetail*` and `CollectionDetail*` use the same dialog and `confirmDelete(choice)`; their rows always carry a pebble.

`PebbleDetailViewModel` gains `UiEffects<PebbleDetailEffect>` (`data object MomentDeleted`), a `pendingDelete: StateFlow<Boolean>`, `requestDelete()`, `cancelDelete()`, `confirmDelete(choice)` (`MOMENT` → emit `MomentDeleted`; `PEBBLE_ONLY` → `reload()`), and a `didDeleteFail` flag shown with `DeleteErrorDialog`. Its constructor becomes `(moments: MomentReadServicing, writes: MomentWriteServicing)`. `DetailTopBar` gets a trash `IconButton` (`R.drawable.ic_trash`, `contentDescription = stringResource(R.string.action_delete)`) before Edit, enabled once loaded. `PebbleDetailScreen` takes `onMomentDeleted: () -> Unit`; the entry provider passes `{ navigator.closeDetail(PebblesKey.PebbleDetail(key.momentId)) }`.

- [ ] **Step 4:** Run the four ViewModel tests → PASS. `ui/PebbleDeleteTest`: add `a completed row offers both choices, and keeping the moment deletes only the pebble` (tap `R.string.moment_delete_pebble_only`, assert `FakeMomentWriteService.deletedPebbles`).
- [ ] **Step 5:** `./gradlew ktlintCheck testDebugUnitTest assembleDebug lint` → BUILD SUCCESSFUL. Commit `feat(android): choose between deleting the pebble and the moment`. Previews: `MomentDeleteDialog_WithPebble`, `MomentDeleteDialog_BareMoment`, `PebbleDetail_TopBarWithDelete`. PR with `rebaseline-screenshots`.

---

## Part 5 — Complete flow (`feat/1048-android-moments-complete`)

**Story:** a moment becomes a pebble in six steps: valence, emotion, domain, souls, collection, privacy, then `complete_pebble` and a success screen with the stone and the karma earned. Half-finished answers survive on the device per moment. The read page of an uncompleted moment gains its one call to action.

**File map** (under `app/src/main/kotlin/app/pbbls/android/` unless noted)

| Action | Path | Responsibility |
|---|---|---|
| Move | `core/model/KnownDraftIds.kt` (from `PebbleDraftPayload.kt:140-143`) | known soul and collection ids (pure move) |
| Create | `core/model/CompleteSnapshot.kt` | the per-moment autosave value |
| Create | `core/data/CompleteSnapshotStore.kt` | `CompleteSnapshotStoring` + SharedPreferences implementation |
| Modify | `core/data/MomentWriteService.kt` | clears the moment's snapshot after `delete_moment` |
| Modify | `RootViewModel.kt` | clears every snapshot on sign-out |
| Create | `features/path/complete/CompleteStep.kt` | the seven screens |
| Create | `features/path/complete/CompleteFlowState.kt` | state value + `CompleteResult` |
| Create | `features/path/complete/CompleteFlowModel.kt` | the state machine (every interaction buzzes) |
| Create | `features/path/complete/CompleteFlowViewModel.kt` | load, restore, autosave, `complete_pebble`, success |
| Create | `features/path/complete/CompleteStepContent.kt` | step → reused step body |
| Create | `features/path/complete/CompleteFlowScreen.kt` | screen + stateless `CompleteFlowContent` |
| Modify | `features/path/record/RecordFlowChrome.kt`, `RecordFlowScreen.kt` | the chrome takes `position`/`total`/`canGoBack` |
| Modify | `navigation/PebblesKeys.kt`, `PebblesEntryProvider.kt` | `PebblesKey.CompletePebble(momentId)` |
| Modify | `features/path/read/PebbleReadView.kt`, `PebbleDetailScreen.kt` | "Make it a pebble" on a bare moment |
| Modify | `di/ServiceBindings.kt` | `@Binds` the snapshot store |
| Modify | strings | `moment_make_pebble` |
| Test | `CompleteStepTest`, `CompleteFlowModelTest`, `CompleteSnapshotTest`, `CompleteFlowViewModelTest`, `ui/CompleteFlowTest`; fake `FakeCompleteSnapshotStore` | |

### Task 5.1: The per-moment autosave

- [ ] **Step 1:** Pure move of `KnownDraftIds` into its own file; commit `quality(android): give KnownDraftIds its own file`.
- [ ] **Step 2: Write the failing test** `app/src/test/.../core/model/CompleteSnapshotTest.kt`:

```kotlin
package app.pbbls.android.core.model

import app.pbbls.android.core.data.CompleteSnapshotStore
import app.pbbls.android.core.data.OwnedCompleteSnapshot
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompleteSnapshotTest {
    private val answers =
        PebbleAnswers(
            valence = Valence.LOWLIGHT_SMALL,
            emotionId = "e-1",
            domainId = "d-1",
            soulIds = listOf("s-1", "s-gone"),
            collectionId = "c-gone",
            visibility = Visibility.PUBLIC,
        )

    @Test
    fun `answers round-trip through the snapshot`() {
        assertEquals(answers, CompleteSnapshot.from(answers).toAnswers(known = null))
    }

    @Test
    fun `restoring drops souls and collections that no longer exist`() {
        val restored = CompleteSnapshot.from(answers).toAnswers(KnownDraftIds(soulIds = setOf("s-1"), collectionIds = emptySet()))
        assertEquals(listOf("s-1"), restored.soulIds)
        assertNull(restored.collectionId)
    }

    @Test
    fun `a seeded valence and the default grade are not input`() {
        assertTrue(CompleteSnapshot.from(PebbleAnswers(valence = Valence.NEUTRAL_MEDIUM)).isEmpty)
    }

    @Test
    fun `a snapshot is only restorable for the user who wrote it`() {
        val owned = OwnedCompleteSnapshot(ownerId = "user-1", snapshot = CompleteSnapshot.from(answers))
        assertEquals(owned.snapshot, owned.restorableFor("user-1"))
        assertNull(owned.restorableFor("user-2"))
    }

    @Test
    fun `the stored shape is stable`() {
        val json = Json.encodeToString(OwnedCompleteSnapshot.serializer(), OwnedCompleteSnapshot("user-1", CompleteSnapshot.from(answers)))
        assertEquals(
            Json.parseToJsonElement(
                """
                {"ownerId":"user-1","snapshot":{"intensity":1,"positiveness":-1,"emotion_id":"e-1","domain_id":"d-1",
                 "soul_ids":["s-1","s-gone"],"collection_id":"c-gone","visibility":"public"}}
                """.trimIndent(),
            ),
            Json.parseToJsonElement(json),
        )
    }

    @Test
    fun `keys are per moment and lowercase`() {
        assertEquals("completeSnapshot.7d1f6a2e-0c4b-4e8a-9b51-2f3c4d5e6f70", CompleteSnapshotStore.key("7D1F6A2E-0C4B-4E8A-9B51-2F3C4D5E6F70"))
    }
}
```

- [ ] **Step 3:** Run → FAIL.
- [ ] **Step 4: Implement.**

`core/model/CompleteSnapshot.kt`:

```kotlin
package app.pbbls.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A half-finished Complete flow, kept on the device per moment (PD-7). Not a
 * wire shape: it never leaves the device, and server drafts are retired (D7).
 */
@Serializable
data class CompleteSnapshot(
    val intensity: Int? = null,
    val positiveness: Int? = null,
    @SerialName("emotion_id")
    val emotionId: String? = null,
    @SerialName("domain_id")
    val domainId: String? = null,
    @SerialName("soul_ids")
    val soulIds: List<String> = emptyList(),
    @SerialName("collection_id")
    val collectionId: String? = null,
    val visibility: Visibility? = null,
) {
    /** Valence and grade never count: both arrive seeded, and counting them would save every flow merely opened. */
    val isEmpty: Boolean
        get() = emotionId == null && domainId == null && soulIds.isEmpty() && collectionId == null

    /** [known] null means reference data has not loaded: keep the ids rather than drop every one. */
    fun toAnswers(known: KnownDraftIds?): PebbleAnswers =
        PebbleAnswers(
            valence = Valence.orNull(positiveness, intensity),
            emotionId = emotionId,
            domainId = domainId,
            soulIds = if (known == null) soulIds else soulIds.filter { it in known.soulIds },
            collectionId = collectionId?.takeIf { known == null || it in known.collectionIds },
            visibility = visibility ?: Visibility.SECRET,
        )

    companion object {
        fun from(answers: PebbleAnswers): CompleteSnapshot =
            CompleteSnapshot(
                intensity = answers.valence?.intensity,
                positiveness = answers.valence?.positiveness,
                emotionId = answers.emotionId,
                domainId = answers.domainId,
                soulIds = answers.soulIds,
                collectionId = answers.collectionId,
                visibility = answers.visibility,
            )
    }
}
```

`core/data/CompleteSnapshotStore.kt`:

```kotlin
package app.pbbls.android.core.data

import android.content.Context
import android.util.Log
import app.pbbls.android.core.model.CompleteSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** The Complete flow's autosave seam; the fake is a map. */
interface CompleteSnapshotStoring {
    fun load(
        momentId: String,
        ownerId: String,
    ): CompleteSnapshot?

    fun save(
        momentId: String,
        ownerId: String,
        snapshot: CompleteSnapshot,
    )

    fun clear(momentId: String)

    /** Every moment's snapshot, on sign-out. */
    fun clearAll()
}

/** What is persisted: the snapshot and the user who wrote it (GDP-07, as the old composer snapshot). */
@Serializable
data class OwnedCompleteSnapshot(
    val ownerId: String,
    val snapshot: CompleteSnapshot,
) {
    fun restorableFor(requesterId: String): CompleteSnapshot? = snapshot.takeIf { ownerId == requesterId && !it.isEmpty }
}

/**
 * Crash and leave-for-later insurance for the Complete flow, one entry per
 * moment in the existing `pebbles_prefs` file. Not offline support (decision
 * log 2026-07-29). Cleared on completion, on `delete_moment`, and on sign-out.
 */
@Singleton
class CompleteSnapshotStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : CompleteSnapshotStoring {
        private val json = Json { ignoreUnknownKeys = true }

        override fun load(
            momentId: String,
            ownerId: String,
        ): CompleteSnapshot? {
            val raw = prefs().getString(key(momentId), null) ?: return null
            val stored =
                try {
                    json.decodeFromString(OwnedCompleteSnapshot.serializer(), raw)
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "discarding an unreadable snapshot", e)
                    clear(momentId)
                    return null
                }
            if (stored.ownerId != ownerId) {
                Log.i(TAG, "discarding a snapshot owned by another user")
                clear(momentId)
                return null
            }
            return stored.restorableFor(ownerId)
        }

        override fun save(
            momentId: String,
            ownerId: String,
            snapshot: CompleteSnapshot,
        ) {
            if (snapshot.isEmpty) {
                clear(momentId)
                return
            }
            prefs()
                .edit()
                .putString(key(momentId), json.encodeToString(OwnedCompleteSnapshot.serializer(), OwnedCompleteSnapshot(ownerId, snapshot)))
                .apply()
        }

        override fun clear(momentId: String) {
            prefs().edit().remove(key(momentId)).apply()
        }

        override fun clearAll() {
            val prefs = prefs()
            val editor = prefs.edit()
            prefs.all.keys.filter { it.startsWith(PREFIX) }.forEach(editor::remove)
            editor.apply()
        }

        private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        companion object {
            private const val TAG = "complete-snapshot"
            private const val PREFS_NAME = "pebbles_prefs"
            private const val PREFIX = "completeSnapshot."

            fun key(momentId: String): String = PREFIX + momentId.lowercase()
        }
    }
```

Bind it in `ServiceBindings`: `@Binds @Singleton fun bindCompleteSnapshotStoring(impl: CompleteSnapshotStore): CompleteSnapshotStoring`. `MomentWriteService` takes `private val completeSnapshots: CompleteSnapshotStoring` and calls `completeSnapshots.clear(momentId)` right after `delete_moment` returns. `RootViewModel` takes `private val completeSnapshots: CompleteSnapshotStoring` and calls `completeSnapshots.clearAll()` beside `snapshots.clear()` in the signed-out branch (`RootViewModel.kt:165`); update `RootViewModelTest` to construct it with the fake and assert `clearAllCount == 1` after a resolved sign-out.

`app/src/test/.../testing/FakeCompleteSnapshotStore.kt`:

```kotlin
package app.pbbls.android.testing

import app.pbbls.android.core.data.CompleteSnapshotStoring
import app.pbbls.android.core.model.CompleteSnapshot

class FakeCompleteSnapshotStore : CompleteSnapshotStoring {
    val stored = mutableMapOf<String, Pair<String, CompleteSnapshot>>()
    var clearAllCount = 0
        private set

    override fun load(
        momentId: String,
        ownerId: String,
    ): CompleteSnapshot? = stored[momentId]?.takeIf { it.first == ownerId }?.second?.takeUnless { it.isEmpty }

    override fun save(
        momentId: String,
        ownerId: String,
        snapshot: CompleteSnapshot,
    ) {
        if (snapshot.isEmpty) stored.remove(momentId) else stored[momentId] = ownerId to snapshot
    }

    override fun clear(momentId: String) {
        stored.remove(momentId)
    }

    override fun clearAll() {
        clearAllCount += 1
        stored.clear()
    }
}
```

Provide and bind it in `FakeServicesModule` like the other pairs.

- [ ] **Step 5:** Run `CompleteSnapshotTest`, `RootViewModelTest` → PASS. Commit `feat(android): keep a half-finished completion per moment`.

### Task 5.2: Steps, state and machine

- [ ] **Step 1: Write the failing tests.**

`app/src/test/.../features/path/complete/CompleteStepTest.kt`:

```kotlin
package app.pbbls.android.features.path.complete

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompleteStepTest {
    @Test
    fun `six counted steps in the decided order, then success`() {
        assertEquals(
            listOf(
                CompleteStep.VALENCE,
                CompleteStep.EMOTION,
                CompleteStep.DOMAIN,
                CompleteStep.SOULS,
                CompleteStep.COLLECTION,
                CompleteStep.PRIVACY,
            ),
            CompleteStep.counted,
        )
        assertNull(CompleteStep.SUCCESS.dotIndex)
    }

    @Test
    fun `only souls and collection are optional`() {
        assertEquals(setOf(CompleteStep.SOULS, CompleteStep.COLLECTION), CompleteStep.entries.filter { it.isOptional }.toSet())
    }
}
```

`app/src/test/.../features/path/complete/CompleteFlowModelTest.kt`:

```kotlin
package app.pbbls.android.features.path.complete

import app.pbbls.android.R
import app.pbbls.android.core.data.TapHaptic
import app.pbbls.android.core.model.PebbleAnswers
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.Visibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompleteFlowModelTest {
    private val haptics = mutableListOf<TapHaptic>()
    private val model = CompleteFlowModel(haptic = { haptics += it })

    @Test
    fun `valence arrives seeded and commits in place`() {
        model.seedValenceIfNeeded()
        assertEquals(Valence.NEUTRAL_MEDIUM, model.answers.valence)
        assertTrue(haptics.isEmpty())
        model.selectValence(Valence.HIGHLIGHT_LARGE)
        assertEquals(CompleteStep.VALENCE, model.step)
        assertEquals(listOf(TapHaptic.SELECTION), haptics)
    }

    @Test
    fun `an unanswered mandatory step blocks with a warning`() {
        model.goTo(CompleteStep.EMOTION)
        model.advance()
        assertEquals(CompleteStep.EMOTION, model.step)
        assertEquals(listOf(TapHaptic.WARNING), haptics)
    }

    @Test
    fun `a tile pick commits and advances with one selection buzz`() {
        model.goTo(CompleteStep.EMOTION)
        model.selectEmotion("e-1")
        assertEquals(CompleteStep.DOMAIN, model.step)
        model.selectDomain("d-1")
        assertEquals(CompleteStep.SOULS, model.step)
        assertEquals(listOf(TapHaptic.SELECTION, TapHaptic.SELECTION), haptics)
    }

    @Test
    fun `souls toggle without advancing and skip is allowed`() {
        model.goTo(CompleteStep.SOULS)
        model.toggleSoul("s-1")
        model.toggleSoul("s-2")
        model.toggleSoul("s-1")
        assertEquals(listOf("s-2"), model.answers.soulIds)
        assertEquals(CompleteStep.SOULS, model.step)
        model.advance()
        assertEquals(CompleteStep.COLLECTION, model.step)
    }

    @Test
    fun `privacy selects without completing`() {
        model.goTo(CompleteStep.PRIVACY)
        model.selectVisibility(Visibility.PUBLIC)
        assertEquals(CompleteStep.PRIVACY, model.step)
        assertEquals(Visibility.PUBLIC, model.answers.visibility)
    }

    @Test
    fun `back preserves answers`() {
        model.goTo(CompleteStep.EMOTION)
        model.selectEmotion("e-1")
        model.back()
        assertEquals(CompleteStep.EMOTION, model.step)
        assertEquals("e-1", model.answers.emotionId)
    }

    @Test
    fun `a restored flow lands on its first gap`() {
        model.resume(PebbleAnswers(valence = Valence.LOWLIGHT_SMALL, emotionId = "e-1"))
        assertEquals(CompleteStep.DOMAIN, model.step)
        model.resume(PebbleAnswers(valence = Valence.LOWLIGHT_SMALL, emotionId = "e-1", domainId = "d-1"))
        assertEquals(CompleteStep.PRIVACY, model.step)
    }

    @Test
    fun `success and failure buzz and move as decided`() {
        model.goTo(CompleteStep.PRIVACY)
        model.beginComplete()
        model.fail(R.string.pebble_error_reference_gone)
        assertEquals(CompleteStep.PRIVACY, model.step)
        assertFalse(model.isCompleting)
        model.beginComplete()
        model.succeed(CompleteResult(pebbleId = "p-1", karmaDelta = 5, glyph = null))
        assertEquals(CompleteStep.SUCCESS, model.step)
        assertEquals(listOf(TapHaptic.WARNING, TapHaptic.SUCCESS), haptics)
    }
}
```

- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3: Implement.**

`CompleteStep.kt`:

```kotlin
package app.pbbls.android.features.path.complete

import androidx.annotation.StringRes
import app.pbbls.android.R

/**
 * The Complete flow (spec §6), in order. Valence before emotion keeps the
 * emotion categories ordered by the valence just chosen; privacy last sits
 * against the button that makes the pebble. No glyph step: the glyph is derived.
 */
enum class CompleteStep(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int? = null,
    val isOptional: Boolean = false,
    /** The body scrolls itself; the scaffold must not nest a second scroll. */
    val bringsOwnScroll: Boolean = false,
) {
    VALENCE(R.string.record_valence_title, R.string.record_valence_subtitle),
    EMOTION(R.string.record_emotion_title, bringsOwnScroll = true),
    DOMAIN(R.string.record_domain_title),
    SOULS(R.string.record_souls_title, isOptional = true, bringsOwnScroll = true),
    COLLECTION(R.string.record_collection_title, isOptional = true),
    PRIVACY(R.string.record_privacy_title),
    SUCCESS(R.string.record_success_title),
    ;

    val dotIndex: Int? get() = if (this == SUCCESS) null else ordinal
    val next: CompleteStep? get() = entries.getOrNull(ordinal + 1)
    val previous: CompleteStep? get() = if (ordinal == 0) null else entries[ordinal - 1]

    companion object {
        val counted: List<CompleteStep> = entries.filter { it != SUCCESS }
    }
}
```

`CompleteFlowState.kt`:

```kotlin
package app.pbbls.android.features.path.complete

import androidx.annotation.StringRes
import app.pbbls.android.core.model.PebbleAnswers
import app.pbbls.android.core.model.PebbleGlyph

data class CompleteResult(
    val pebbleId: String,
    val karmaDelta: Int?,
    val glyph: PebbleGlyph?,
)

data class CompleteFlowState(
    val answers: PebbleAnswers = PebbleAnswers(),
    val step: CompleteStep = CompleteStep.VALENCE,
    val completed: CompleteResult? = null,
    val isCompleting: Boolean = false,
    @StringRes val errorRes: Int? = null,
) {
    /** Exhaustive with no `else`: a new step must say what "answered" means. */
    fun hasAnswer(forStep: CompleteStep): Boolean =
        when (forStep) {
            CompleteStep.PRIVACY, CompleteStep.SUCCESS -> true
            CompleteStep.VALENCE -> answers.valence != null
            CompleteStep.EMOTION -> answers.emotionId != null
            CompleteStep.DOMAIN -> answers.domainId != null
            CompleteStep.SOULS -> answers.soulIds.isNotEmpty()
            CompleteStep.COLLECTION -> answers.collectionId != null
        }

    val isAnswered: Boolean get() = step.isOptional || hasAnswer(step)

    val optionalButtonIsSkip: Boolean get() = !hasAnswer(step)

    fun firstGap(): CompleteStep = CompleteStep.counted.firstOrNull { !it.isOptional && !hasAnswer(it) } ?: CompleteStep.PRIVACY
}
```

`CompleteFlowModel.kt` (mirrors `RecordFlowModel`; every public mutator buzzes except the silent seed, restore and `goTo`):

```kotlin
package app.pbbls.android.features.path.complete

import app.pbbls.android.core.data.TapHaptic
import app.pbbls.android.core.model.PebbleAnswers
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.Visibility
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The Complete flow's state machine. [haptic] has no default, as in `RecordFlowModel` (M58 D4). */
class CompleteFlowModel(
    private val haptic: (TapHaptic) -> Unit,
    initial: CompleteFlowState = CompleteFlowState(),
) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<CompleteFlowState> = _state.asStateFlow()

    val answers: PebbleAnswers get() = _state.value.answers
    val step: CompleteStep get() = _state.value.step
    val isCompleting: Boolean get() = _state.value.isCompleting
    val completed: CompleteResult? get() = _state.value.completed

    fun advance() {
        if (step == CompleteStep.SUCCESS) return
        if (!_state.value.isAnswered) {
            haptic(TapHaptic.WARNING)
            return
        }
        val next = step.next ?: return
        haptic(TapHaptic.ADVANCE)
        _state.update { it.copy(step = next) }
    }

    fun back() {
        if (step == CompleteStep.SUCCESS) return
        val previous = step.previous ?: return
        haptic(TapHaptic.ADVANCE)
        _state.update { it.copy(step = previous) }
    }

    /** Silent jump, for restore and tests. Never wired to a control. */
    fun goTo(target: CompleteStep) = _state.update { it.copy(step = target) }

    fun seedValenceIfNeeded() {
        if (answers.valence != null) return
        _state.update { it.copy(answers = it.answers.copy(valence = Valence.NEUTRAL_MEDIUM)) }
    }

    fun selectValence(valence: Valence) = select { it.copy(valence = valence) }

    fun selectEmotion(emotionId: String) = commitAndAdvance { it.copy(emotionId = emotionId) }

    fun selectDomain(domainId: String) = commitAndAdvance { it.copy(domainId = domainId) }

    fun selectCollection(collectionId: String) = commitAndAdvance { it.copy(collectionId = collectionId) }

    fun toggleSoul(id: String) = select { a -> a.copy(soulIds = if (id in a.soulIds) a.soulIds - id else a.soulIds + id) }

    fun selectVisibility(visibility: Visibility) = select { it.copy(visibility = visibility) }

    /** Silent: restoring is not something the user did. */
    fun resume(restored: PebbleAnswers) =
        _state.update { current ->
            val hydrated = current.copy(answers = restored)
            hydrated.copy(step = hydrated.firstGap())
        }

    fun beginComplete() = _state.update { it.copy(isCompleting = true, errorRes = null) }

    fun succeed(result: CompleteResult) {
        haptic(TapHaptic.SUCCESS)
        _state.update { it.copy(isCompleting = false, completed = result, step = CompleteStep.SUCCESS) }
    }

    fun fail(messageRes: Int) {
        haptic(TapHaptic.WARNING)
        _state.update { it.copy(isCompleting = false, errorRes = messageRes) }
    }

    private fun select(mutate: (PebbleAnswers) -> PebbleAnswers) {
        haptic(TapHaptic.SELECTION)
        _state.update { it.copy(answers = mutate(it.answers)) }
    }

    /** One emission for the commit and the advance, and one buzz for one tap. */
    private fun commitAndAdvance(mutate: (PebbleAnswers) -> PebbleAnswers) {
        haptic(TapHaptic.SELECTION)
        _state.update { it.copy(answers = mutate(it.answers), step = it.step.next ?: it.step) }
    }
}
```

- [ ] **Step 4:** Run both test classes → PASS. Commit `feat(android): the Complete flow's state machine`.

### Task 5.3: The ViewModel

- [ ] **Step 1: Write the failing test** `app/src/test/.../features/path/complete/CompleteFlowViewModelTest.kt`:

```kotlin
package app.pbbls.android.features.path.complete

import app.pbbls.android.R
import app.pbbls.android.core.data.KarmaNotificationService
import app.pbbls.android.core.model.CompleteSnapshot
import app.pbbls.android.core.model.KarmaReason
import app.pbbls.android.core.model.PebbleAnswers
import app.pbbls.android.core.model.PebbleCompletePayload
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.Visibility
import app.pbbls.android.testing.FakeAchievementsService
import app.pbbls.android.testing.FakeComposerMedia
import app.pbbls.android.testing.FakeCompleteSnapshotStore
import app.pbbls.android.testing.FakeMomentReadService
import app.pbbls.android.testing.FakeMomentWriteService
import app.pbbls.android.testing.FakeReferenceDataService
import app.pbbls.android.testing.FakeSupabaseService
import app.pbbls.android.testing.MainDispatcherRule
import app.pbbls.android.testing.UiFixtures
import app.pbbls.android.testing.postgrestException
import app.pbbls.android.testing.recordEffects
import app.pbbls.android.testing.testSession
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CompleteFlowViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class Harness(scope: CoroutineScope) {
        val read = FakeMomentReadService(moments = mapOf("m-1" to UiFixtures.momentDetail(id = "m-1", name = "Swim", pebble = null)))
        val write = FakeMomentWriteService()
        val refs = FakeReferenceDataService(hasLoaded = true)
        val snapshots = FakeCompleteSnapshotStore()
        val achievements = FakeAchievementsService()
        val supabase = FakeSupabaseService(session = testSession("user-1"))
        val viewModel =
            CompleteFlowViewModel(
                reads = read,
                writes = write,
                refs = refs,
                snapshots = snapshots,
                karma = KarmaNotificationService(scope),
                achievements = achievements,
                supabase = supabase,
                media = FakeComposerMedia(),
            )

        fun answerEverything() {
            val m = viewModel.machine()
            m.seedValenceIfNeeded()
            m.selectValence(Valence.LOWLIGHT_LARGE)
            m.advance()
            m.selectEmotion("e-1")
            m.selectDomain("d-1")
            m.advance()
            m.advance()
            m.selectVisibility(Visibility.PRIVATE)
        }
    }

    @Test
    fun `completing sends every key, clears the autosave, reads karma and glyph, then checks achievements once`() =
        runTest {
            val h = Harness(backgroundScope)
            h.read.glyph = UiFixtures.pebbleGlyph()
            h.write.karma = 5
            h.viewModel.start("m-1")
            advanceUntilIdle()
            h.answerEverything()
            h.viewModel.complete()
            advanceUntilIdle()

            assertEquals(
                PebbleCompletePayload("m-1", "e-1", 3, -1, Visibility.PRIVATE, listOf("d-1"), emptyList(), emptyList()),
                h.write.completed.single(),
            )
            assertTrue(h.snapshots.stored.isEmpty())
            assertEquals(listOf("pebble-new" to KarmaReason.PEBBLE_CREATED), h.write.karmaReads)
            assertEquals(listOf("pebble-new"), h.read.resolvedGlyphFor)
            assertEquals(1, h.achievements.fireCheckCount)
            val flow = h.viewModel.uiState.value.flow
            assertEquals(CompleteStep.SUCCESS, flow.step)
            assertEquals(CompleteResult("pebble-new", 5, UiFixtures.pebbleGlyph()), flow.completed)
            assertEquals("Swim", h.viewModel.uiState.value.momentName)
        }

    @Test
    fun `a missing glyph or karma row still reaches success`() =
        runTest {
            val h = Harness(backgroundScope)
            h.read.glyph = null
            h.write.karma = null
            h.viewModel.start("m-1")
            advanceUntilIdle()
            h.answerEverything()
            h.viewModel.complete()
            advanceUntilIdle()
            assertEquals(CompleteResult("pebble-new", null, null), h.viewModel.uiState.value.flow.completed)
        }

    @Test
    fun `a refusal stays on privacy with its copy and keeps the autosave`() =
        runTest {
            val h = Harness(backgroundScope)
            h.viewModel.start("m-1")
            advanceUntilIdle()
            h.answerEverything()
            advanceTimeBy(AUTOSAVE_DEBOUNCE_MS + 1)
            h.write.failOnComplete = postgrestException("collection_not_owned", sqlState = "42501", status = HttpStatusCode.Forbidden)
            h.viewModel.complete()
            advanceUntilIdle()
            val flow = h.viewModel.uiState.value.flow
            assertEquals(CompleteStep.PRIVACY, flow.step)
            assertEquals(R.string.pebble_error_reference_gone, flow.errorRes)
            assertEquals("e-1", h.snapshots.stored["m-1"]?.second?.emotionId)
            assertEquals(0, h.achievements.fireCheckCount)
        }

    @Test
    fun `answers autosave per moment after the debounce`() =
        runTest {
            val h = Harness(backgroundScope)
            h.viewModel.start("m-1")
            advanceUntilIdle()
            h.viewModel.machine().goTo(CompleteStep.EMOTION)
            h.viewModel.machine().selectEmotion("e-1")
            advanceTimeBy(AUTOSAVE_DEBOUNCE_MS - 1)
            assertNull(h.snapshots.stored["m-1"])
            advanceTimeBy(2)
            assertEquals("user-1" to CompleteSnapshot(emotionId = "e-1", visibility = Visibility.SECRET), h.snapshots.stored["m-1"])
        }

    @Test
    fun `reopening restores silently at the first gap, without vanished souls`() =
        runTest {
            val h = Harness(backgroundScope)
            h.refs.souls = listOf(UiFixtures.soul(id = "s-1"))
            h.snapshots.save(
                "m-1",
                "user-1",
                CompleteSnapshot.from(PebbleAnswers(valence = Valence.HIGHLIGHT_SMALL, emotionId = "e-1", soulIds = listOf("s-1", "s-gone"))),
            )
            h.viewModel.start("m-1")
            advanceUntilIdle()
            val flow = h.viewModel.uiState.value.flow
            assertEquals(CompleteStep.DOMAIN, flow.step)
            assertEquals(listOf("s-1"), flow.answers.soulIds)
        }

    @Test
    fun `closing keeps the answers and dismisses without asking`() =
        runTest {
            val h = Harness(backgroundScope)
            val effects = recordEffects(h.viewModel.effects)
            h.viewModel.start("m-1")
            advanceUntilIdle()
            h.viewModel.machine().goTo(CompleteStep.EMOTION)
            h.viewModel.machine().selectEmotion("e-1")
            h.viewModel.onClose()
            advanceUntilIdle()
            assertEquals("e-1", h.snapshots.stored["m-1"]?.second?.emotionId)
            assertTrue(CompleteFlowEffect.Dismiss in effects.values)
            effects.stop()
        }

    @Test
    fun `back on the first step closes`() =
        runTest {
            val h = Harness(backgroundScope)
            val effects = recordEffects(h.viewModel.effects)
            h.viewModel.start("m-1")
            advanceUntilIdle()
            h.viewModel.onSystemBack()
            advanceUntilIdle()
            assertTrue(CompleteFlowEffect.Dismiss in effects.values)
            effects.stop()
        }

    @Test
    fun `start is guarded so a rotation does not reload or restore`() =
        runTest {
            val h = Harness(backgroundScope)
            h.viewModel.start("m-1")
            h.viewModel.start("m-1")
            advanceUntilIdle()
            assertEquals(listOf("m-1"), h.read.loadedMomentIds)
        }
}
```

(Match `UiFixtures.soul(...)` and `FakeReferenceDataService`'s constructor to the existing test fixtures; `AUTOSAVE_DEBOUNCE_MS` is the `internal const` declared in `CompleteFlowViewModel.kt`.)

- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3: Implement** `CompleteFlowViewModel.kt`:

```kotlin
package app.pbbls.android.features.path.complete

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.core.common.UiEffects
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.AchievementsServicing
import app.pbbls.android.core.data.CompleteSnapshotStoring
import app.pbbls.android.core.data.KarmaNotificationService
import app.pbbls.android.core.data.MomentReadServicing
import app.pbbls.android.core.data.MomentWriteServicing
import app.pbbls.android.core.data.ReferenceDataServicing
import app.pbbls.android.core.data.SupabaseServicing
import app.pbbls.android.core.data.TapHaptic
import app.pbbls.android.core.data.toDataError
import app.pbbls.android.core.model.CompleteSnapshot
import app.pbbls.android.core.model.KarmaReason
import app.pbbls.android.core.model.KnownDraftIds
import app.pbbls.android.core.model.PebbleCompletePayload
import app.pbbls.android.core.ui.momentWriteErrorRes
import app.pbbls.android.features.path.ComposerMedia
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "complete-flow"

/** Long enough that tapping through tiles does not thrash SharedPreferences. */
internal const val AUTOSAVE_DEBOUNCE_MS = 800L

sealed interface CompleteFlowEffect {
    data class Haptic(
        val flavor: TapHaptic,
    ) : CompleteFlowEffect

    data object Dismiss : CompleteFlowEffect
}

data class CompleteFlowUiState(
    val flow: CompleteFlowState = CompleteFlowState(),
    /** For the success screen. Null until loaded, and on a photo-only moment. */
    val momentName: String? = null,
)

/**
 * The Complete flow's orchestration: restore, autosave, `complete_pebble`, and
 * what follows a completion (PD-9). Entry-scoped (#852), so it is cleared with
 * its entry. The write and everything after it run in `NonCancellable`:
 * leaving after the request left the device must not skip clearing the
 * autosave, or the next visit would resurrect answers already completed.
 */
@HiltViewModel
class CompleteFlowViewModel
    @Inject
    constructor(
        private val reads: MomentReadServicing,
        private val writes: MomentWriteServicing,
        private val refs: ReferenceDataServicing,
        private val snapshots: CompleteSnapshotStoring,
        private val karma: KarmaNotificationService,
        private val achievements: AchievementsServicing,
        private val supabase: SupabaseServicing,
        private val media: ComposerMedia,
    ) : ViewModel() {
        private val effectsOut = UiEffects<CompleteFlowEffect>(viewModelScope)
        val effects: Flow<CompleteFlowEffect> = effectsOut.flow

        private val model = CompleteFlowModel(haptic = { effectsOut.emit(CompleteFlowEffect.Haptic(it)) })

        private val _uiState = MutableStateFlow(CompleteFlowUiState())
        val uiState: StateFlow<CompleteFlowUiState> = _uiState.asStateFlow()

        private var momentId: String? = null

        private val userId: String?
            get() = supabase.session?.user?.id

        init {
            viewModelScope.launch { model.state.collect { flow -> _uiState.update { it.copy(flow = flow) } } }
            viewModelScope.launch { media.prewarmValence() }
        }

        /** Every tap goes through the machine (M58 D4). */
        fun machine(): CompleteFlowModel = model

        fun onAchievementCheck() = achievements.fireCheck()

        /** Guarded: a rotation re-runs the screen's `LaunchedEffect` and must not restore over live answers. */
        fun start(id: String) {
            if (momentId == id) return
            momentId = id
            viewModelScope.launch {
                runCatchingCancellable { reads.loadMoment(id) }
                    .onSuccess { detail -> _uiState.update { it.copy(momentName = detail.name) } }
                    .onFailure { Log.w(TAG, "moment load failed; success will say untitled", it) }
            }
            viewModelScope.launch {
                restore(id)
                autosave(id)
            }
        }

        /**
         * Silent restore (PD-7). Souls and collections are sanitized only once
         * reference data has loaded: sanitizing against empty caches would drop
         * every one of them (#647).
         */
        private suspend fun restore(id: String) {
            val owner = userId ?: return
            val snapshot = snapshots.load(id, owner) ?: return
            if (!refs.hasLoaded) {
                runCatchingCancellable { refs.load() }.onFailure { Log.w(TAG, "reference data load failed; restoring unsanitized", it) }
            }
            val known =
                if (refs.hasLoaded) {
                    KnownDraftIds(soulIds = refs.souls.map { it.id }.toSet(), collectionIds = refs.collections.map { it.id }.toSet())
                } else {
                    null
                }
            model.resume(snapshot.toAnswers(known))
        }

        private suspend fun autosave(id: String) {
            model.state
                .map { it.answers }
                .distinctUntilChanged()
                .collectLatest { answers ->
                    if (model.completed != null) return@collectLatest
                    // collectLatest cancels this on the next change: the delay is a debounce, not a queue.
                    delay(AUTOSAVE_DEBOUNCE_MS)
                    val owner = userId ?: return@collectLatest
                    snapshots.save(id, owner, CompleteSnapshot.from(answers))
                }
        }

        private fun flushNow() {
            val id = momentId ?: return
            val owner = userId ?: return
            if (model.completed == null) snapshots.save(id, owner, CompleteSnapshot.from(model.answers))
        }

        /** ✕ never asks (PD-6): the answers stay on the device and resume next time. */
        fun onClose() {
            if (model.isCompleting) return
            flushNow()
            effectsOut.emit(CompleteFlowEffect.Dismiss)
        }

        fun onExit() = effectsOut.emit(CompleteFlowEffect.Dismiss)

        fun onSystemBack() {
            when {
                model.isCompleting -> Unit
                model.step == CompleteStep.SUCCESS -> onExit()
                model.step.previous != null -> model.back()
                else -> onClose()
            }
        }

        fun complete() {
            val id = momentId ?: return
            if (userId == null) {
                Log.e(TAG, "complete: no current user")
                model.fail(R.string.record_signed_out_error)
                return
            }
            val answers = model.answers
            if (!answers.isComplete || model.isCompleting) return
            viewModelScope.launch {
                model.beginComplete()
                withContext(NonCancellable) {
                    runCatchingCancellable { writes.completePebble(PebbleCompletePayload.from(id, answers)) }
                        .fold(
                            onSuccess = { pebbleId ->
                                snapshots.clear(id)
                                val (delta, glyph) =
                                    coroutineScope {
                                        val delta = async { writes.karmaDelta(pebbleId, KarmaReason.PEBBLE_CREATED) }
                                        val glyph =
                                            async {
                                                runCatchingCancellable { reads.resolveGlyph(pebbleId) }
                                                    .onFailure { Log.w(TAG, "glyph resolve failed", it) }
                                                    .getOrNull()
                                            }
                                        delta.await() to glyph.await()
                                    }
                                // The success screen shows the amount, so no capsule (M58 D10).
                                karma.notifyEarned(delta ?: 0, KarmaReason.PEBBLE_CREATED, presentsCapsule = false)
                                achievements.fireCheck()
                                model.succeed(CompleteResult(pebbleId, delta, glyph))
                            },
                            onFailure = {
                                Log.e(TAG, "complete_pebble failed", it)
                                model.fail(momentWriteErrorRes(it.toDataError(), R.string.pebble_save_error_generic))
                            },
                        )
                }
            }
        }
    }
```

- [ ] **Step 4:** Run → PASS. Commit `feat(android): complete a moment through complete_pebble`.

### Task 5.4: The screen

- [ ] **Step 1:** Generalize the chrome: `RecordFlowChrome(position: Int, total: Int, canGoBack: Boolean, onBack, onClose, modifier)` with `StepProgress(position, total)`; `RecordFlowScreen` passes `(step.dotIndex ?: 0) + 1`, `RecordStep.counted.size`, `step.previous != null`. Commit `quality(android): let the step chrome count any flow`.
- [ ] **Step 2:** `CompleteStepContent.kt` maps a step to the existing bodies, all stateless and already used by the record flow:

```kotlin
@Composable
fun CompleteStepContent(
    step: CompleteStep,
    answers: PebbleAnswers,
    model: CompleteFlowModel,
    domains: List<Domain>,
    collections: List<PebbleCollection>,
    error: String?,
    modifier: Modifier = Modifier,
    onAchievementCheck: () -> Unit = {},
) {
    when (step) {
        CompleteStep.VALENCE ->
            RecordValenceStep(selected = answers.valence, onSelect = { model.selectValence(it) }, onSeed = { model.seedValenceIfNeeded() }, modifier = modifier)
        CompleteStep.EMOTION ->
            RecordEmotionStep(selectedId = answers.emotionId, valence = answers.valence, onSelect = { model.selectEmotion(it) }, modifier = modifier)
        CompleteStep.DOMAIN ->
            RecordDomainStep(domains = domains, selectedId = answers.domainId, onSelect = { model.selectDomain(it) }, modifier = modifier)
        CompleteStep.SOULS ->
            RecordSoulsStep(selectedIds = answers.soulIds, onToggle = { model.toggleSoul(it) }, modifier = modifier, onAchievementCheck = onAchievementCheck)
        CompleteStep.COLLECTION ->
            RecordCollectionStep(collections = collections, selectedId = answers.collectionId, onSelect = { model.selectCollection(it) }, modifier = modifier)
        CompleteStep.PRIVACY ->
            RecordPrivacyStep(selected = answers.visibility, onSelect = { model.selectVisibility(it) }, modifier = modifier, publishError = error)
        // Rendered by CompleteFlowContent outside the scaffold.
        CompleteStep.SUCCESS -> Unit
    }
}
```

- [ ] **Step 3:** `CompleteFlowScreen.kt` follows `RecordFlowScreen`'s shape: `CompleteFlowScreen(momentId, onDismiss, modifier, viewModel = hiltViewModel())` collects `uiState` with `collectAsStateWithLifecycle`, calls `LaunchedEffect(momentId) { viewModel.start(momentId) }`, plays `CompleteFlowEffect.Haptic` through the same `rememberTapHaptics()` the record flow uses and calls `onDismiss` on `Dismiss` (via `ObserveUiEffects`), installs `NavigationBackHandler { viewModel.onSystemBack() }`, applies `safeDrawingPadding()` + `readableWidth()`, and delegates to a stateless `CompleteFlowContent(uiState, model, domains, collections, palette, onClose, onComplete, onExit, onAchievementCheck)` that the previews render. `CompleteFlowContent` draws `RecordFlowChrome(position = (step.dotIndex ?: 0) + 1, total = CompleteStep.counted.size, canGoBack = step.previous != null, onBack = model::back, onClose = onClose)` (hidden on `SUCCESS`), then either `RecordSuccessStep(name = uiState.momentName ?: stringResource(R.string.moment_untitled), glyph = completed.glyph, karmaDelta = completed.karmaDelta, valence = answers.valence ?: Valence.NEUTRAL_MEDIUM, palette = palette, onExit = onExit)` or `RecordStepScaffold(title, subtitle, action, contentScrolls = !step.bringsOwnScroll) { CompleteStepContent(…) }` with the action:

```kotlin
private fun actionFor(
    state: CompleteFlowState,
    model: CompleteFlowModel,
    labels: CompleteLabels,
    onComplete: () -> Unit,
): RecordStepAction? =
    when (state.step) {
        CompleteStep.VALENCE -> RecordStepAction.Primary(labels.continueLabel, enabled = state.hasAnswer(CompleteStep.VALENCE), isLoading = false, onClick = model::advance)
        CompleteStep.EMOTION, CompleteStep.DOMAIN, CompleteStep.SUCCESS -> null
        CompleteStep.SOULS, CompleteStep.COLLECTION ->
            RecordStepAction.Text(if (state.optionalButtonIsSkip) labels.skip else labels.done, onClick = model::advance)
        CompleteStep.PRIVACY ->
            RecordStepAction.Primary(labels.makePebble, enabled = !state.isCompleting, isLoading = state.isCompleting, onClick = onComplete)
    }

/** Resolved strings, so the action builder stays a pure function. */
private data class CompleteLabels(val continueLabel: String, val skip: String, val done: String, val makePebble: String)
```

`labels` come from `record_action_continue`, `record_action_skip`, `action_done`, and the new `moment_make_pebble`; the palette for the success stone is `answers.emotionId?.let(LocalEmotionPaletteService.current::palette)`; domains and collections come from `LocalReferenceDataService.current`.

Strings: `<string name="moment_make_pebble">Make it a pebble</string>` / `<string name="moment_make_pebble">En faire un galet</string>`.

- [ ] **Step 4:** Navigation. `PebblesKeys.kt`, in the modal section:

```kotlin
    /** The Complete flow for one moment (#1048). Full-screen, like the edit form. */
    @Serializable
    data class CompletePebble(
        val momentId: String,
    ) : PebblesKey
```

`PebblesEntryProvider.kt`:

```kotlin
    entry<PebblesKey.CompletePebble>(metadata = NavTransitions.forKey(PebblesKey.CompletePebble(""))) { key ->
        // Exit returns to whatever opened it: the read page (which reloads on resume) or Path after a capture.
        CompleteFlowScreen(momentId = key.momentId, onDismiss = navigator::goBack)
    }
```

Add `CompletePebble` to `PebblesKeysTest`'s round-trip and modal lists.

- [ ] **Step 5:** The read page's call to action. `PebbleReadView(detail, palette, modifier, onComplete: (() -> Unit)? = null)`: directly under `PebbleReadTitle`, when `detail.pebble == null && onComplete != null`, render `PebblesPrimaryButton(text = stringResource(R.string.moment_make_pebble), onClick = onComplete, modifier = Modifier.fillMaxWidth())`. `PebbleDetailContent` passes it through; `PebbleDetailScreen` takes `onCompleteRequested: () -> Unit`; the entry provider passes `{ navigator.navigate(PebblesKey.CompletePebble(key.momentId)) }`. The detail's existing `LifecycleResumeEffect` reload shows the completed pebble on return.
- [ ] **Step 6: Journey test** `app/src/test/.../ui/CompleteFlowTest.kt` (extends `AppUiTest`, `@HiltAndroidTest`): arm `FakeMomentReadService.path` with one bare moment and `.moments` with its detail, launch, open the row, tap `R.string.moment_make_pebble`, `Continue`, an emotion tile, a domain tile, `Skip`, `Skip`, `R.string.moment_make_pebble`; assert `FakeMomentWriteService.completed.single().momentId`, that `"+4 karma"` (`R.string.record_karma`) is displayed, then tap `R.string.record_success_exit` and assert the read page is back. Find emotion and domain tiles the way `RecordFlowPublishTest` does today.
- [ ] **Step 7: Previews** `CompleteFlowScreenshots.kt`: one `@PreviewTest` per step through `CompleteFlowContent` (`Complete_Valence`, `Complete_Emotion`, `Complete_Domain`, `Complete_Souls`, `Complete_Collection`, `Complete_Privacy`, `Complete_PrivacyError`, `Complete_Success`, `Complete_SuccessNoKarmaNoGlyph`), plus `PebbleDetail_BareMomentWithCta`. Build the model for previews with a no-op recorder lambda (previews are not interactions), as `RecordFlowScreenshots` does.
- [ ] **Step 8:** `./gradlew ktlintCheck testDebugUnitTest assembleDebug lint compileDebugScreenshotTestKotlin` → BUILD SUCCESSFUL. Commits: `feat(android): the Complete flow screen`, `feat(android): offer to complete a bare moment from its page`. PR with `rebaseline-screenshots`.

**Manual check:** complete a backfilled bare moment on the AVD; leave on the emotion step with ✕, reopen from the read page and land on domain with the emotion kept; finish and see the karma line and the derived glyph; an achievement unlock (first pebble with an emotion) can present over the success screen.

---

## Part 6 — Capture flow (`feat/1048-android-moments-capture`)

**Story:** "New pebble" opens a three-step Capture: photo, when, name. Saving calls `save_moment` and returns to Path, where the moment appears as a blank stone. A secondary action saves and goes straight into Complete. From this part on, nothing calls `compose-pebble`: the eleven-step flow and the long-press composer become unreachable (Part 7 deletes them).

**File map** (under `app/src/main/kotlin/app/pbbls/android/` unless noted)

| Action | Path | Responsibility |
|---|---|---|
| Create | `features/path/capture/CaptureStep.kt` | the three screens |
| Create | `features/path/capture/CaptureFlowState.kt` | `CaptureDraft` + state value |
| Create | `features/path/capture/CaptureFlowModel.kt` | state machine |
| Create | `features/path/capture/CaptureFlowViewModel.kt` | photo, `save_moment`, close/discard, process death |
| Create | `features/path/capture/CaptureFlowScreen.kt` | screen + stateless `CaptureFlowContent` |
| Modify | `features/path/record/RecordStepScaffold.kt` | `RecordStepAction.PrimaryWithSecondary` |
| Modify | `navigation/PebblesKeys.kt`, `PebblesEntryProvider.kt` | `PebblesKey.Capture`; Path's New opens it; long-press removed |
| Modify | `features/path/PathScreen.kt`, `features/path/components/NewPebbleButton.kt`, `NewPebbleFab.kt`, `WeekPebbleList.kt` | drop `onLongPress` / `onCreatePebbleLongPress` |
| Modify | strings | three capture strings |
| Test | `CaptureStepTest`, `CaptureFlowModelTest`, `CaptureFlowViewModelTest`, `ui/CaptureFlowTest`, `ui/NavigationTest`, `ui/RestorationTest` | |

### Task 6.1: Steps, state and machine

- [ ] **Step 1: Write the failing tests.**

`CaptureFlowModelTest.kt`:

```kotlin
package app.pbbls.android.features.path.capture

import app.pbbls.android.R
import app.pbbls.android.core.data.TapHaptic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class CaptureFlowModelTest {
    private val haptics = mutableListOf<TapHaptic>()
    private val model = CaptureFlowModel(haptic = { haptics += it })

    @Test
    fun `the photo step may be skipped and when is always answered`() {
        model.advance()
        assertEquals(CaptureStep.WHEN, model.step)
        model.advance()
        assertEquals(CaptureStep.NAME, model.step)
        assertEquals(listOf(TapHaptic.ADVANCE, TapHaptic.ADVANCE), haptics)
    }

    @Test
    fun `a moment needs a name or a photo before it can be saved`() {
        model.goTo(CaptureStep.NAME)
        assertFalse(model.state.value.canSave)
        model.setName("   ")
        assertFalse(model.state.value.canSave)
        model.hasSnap = true
        assertTrue(model.state.value.canSave)
        model.hasSnap = false
        model.setName("Swim")
        assertTrue(model.state.value.canSave)
    }

    @Test
    fun `the name is clamped to forty characters`() {
        model.setName("x".repeat(41))
        assertEquals(40, model.draft.name.length)
    }

    @Test
    fun `a photo's capture date seeds when, and a missing one keeps now`() {
        val taken = OffsetDateTime.parse("2026-10-11T18:05:27Z")
        model.applyCaptureDate(taken)
        assertEquals(taken, model.draft.happenedAt)
        model.applyCaptureDate(null)
        assertEquals(taken, model.draft.happenedAt)
    }

    @Test
    fun `saving buzzes success, failing buzzes warning and keeps the step`() {
        model.goTo(CaptureStep.NAME)
        model.beginSave()
        model.fail(R.string.moment_error_empty)
        assertEquals(R.string.moment_error_empty, model.state.value.errorRes)
        assertFalse(model.state.value.isSaving)
        model.saved()
        assertEquals(listOf(TapHaptic.WARNING, TapHaptic.SUCCESS), haptics)
    }
}
```

`CaptureStepTest.kt` asserts `CaptureStep.entries == listOf(PHOTO, WHEN, NAME)`, that only `PHOTO` is optional, and that `NAME.previous == WHEN`.

- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3: Implement.**

`CaptureStep.kt`:

```kotlin
package app.pbbls.android.features.path.capture

import androidx.annotation.StringRes
import app.pbbls.android.R

/**
 * Capture (spec §6): photo before when, so the date arrives seeded from the
 * photo's EXIF (M58 D7); name last, against the save buttons.
 */
enum class CaptureStep(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int? = null,
    val isOptional: Boolean = false,
) {
    PHOTO(R.string.record_photo_title, R.string.record_photo_subtitle, isOptional = true),
    WHEN(R.string.record_when_title),
    NAME(R.string.record_name_title),
    ;

    val next: CaptureStep? get() = entries.getOrNull(ordinal + 1)
    val previous: CaptureStep? get() = if (ordinal == 0) null else entries[ordinal - 1]
}
```

`CaptureFlowState.kt`:

```kotlin
package app.pbbls.android.features.path.capture

import androidx.annotation.StringRes
import java.time.OffsetDateTime

data class CaptureDraft(
    val name: String = "",
    val happenedAt: OffsetDateTime = OffsetDateTime.now(),
)

data class CaptureFlowState(
    val draft: CaptureDraft = CaptureDraft(),
    val step: CaptureStep = CaptureStep.PHOTO,
    /** Mirrored from the upload coordinator, so the flow never owns media. */
    val hasSnap: Boolean = false,
    val isSaving: Boolean = false,
    @StringRes val errorRes: Int? = null,
) {
    fun hasAnswer(forStep: CaptureStep): Boolean =
        when (forStep) {
            CaptureStep.PHOTO -> hasSnap
            CaptureStep.WHEN -> true
            CaptureStep.NAME -> draft.name.isNotBlank() || hasSnap
        }

    val isAnswered: Boolean get() = step.isOptional || hasAnswer(step)

    /** A moment needs a date and a name or a photo (D3). */
    val canSave: Boolean get() = !isSaving && hasAnswer(CaptureStep.NAME)

    /** Something the user would lose by leaving: a typed name or a photo. */
    val hasInput: Boolean get() = draft.name.isNotBlank() || hasSnap
}
```

`CaptureFlowModel.kt`:

```kotlin
package app.pbbls.android.features.path.capture

import app.pbbls.android.core.data.TapHaptic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.OffsetDateTime

/** Capture's state machine; every interaction buzzes (M58 D4). [haptic] has no default. */
class CaptureFlowModel(
    private val haptic: (TapHaptic) -> Unit,
    initial: CaptureFlowState = CaptureFlowState(),
) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<CaptureFlowState> = _state.asStateFlow()

    val draft: CaptureDraft get() = _state.value.draft
    val step: CaptureStep get() = _state.value.step
    val isSaving: Boolean get() = _state.value.isSaving

    var hasSnap: Boolean
        get() = _state.value.hasSnap
        set(value) = _state.update { it.copy(hasSnap = value) }

    fun advance() {
        if (!_state.value.isAnswered) {
            haptic(TapHaptic.WARNING)
            return
        }
        val next = step.next ?: return
        haptic(TapHaptic.ADVANCE)
        _state.update { it.copy(step = next) }
    }

    fun back() {
        val previous = step.previous ?: return
        haptic(TapHaptic.ADVANCE)
        _state.update { it.copy(step = previous) }
    }

    fun goTo(target: CaptureStep) = _state.update { it.copy(step = target) }

    /** Front-end clamp only (M58 D3); the server does not constrain names. */
    fun setName(raw: String) = _state.update { it.copy(draft = it.draft.copy(name = raw.take(NAME_LIMIT)), errorRes = null) }

    fun setHappenedAt(value: OffsetDateTime) = _state.update { it.copy(draft = it.draft.copy(happenedAt = value)) }

    fun applyCaptureDate(date: OffsetDateTime?) {
        if (date != null) setHappenedAt(date)
    }

    fun beginSave() = _state.update { it.copy(isSaving = true, errorRes = null) }

    fun saved() {
        haptic(TapHaptic.SUCCESS)
        _state.update { it.copy(isSaving = false) }
    }

    fun fail(messageRes: Int) {
        haptic(TapHaptic.WARNING)
        _state.update { it.copy(isSaving = false, errorRes = messageRes) }
    }

    companion object {
        const val NAME_LIMIT = 40
    }
}
```

- [ ] **Step 4:** Run → PASS. Commit `feat(android): the Capture flow's state machine`.

### Task 6.2: The ViewModel

- [ ] **Step 1: Write the failing test** `CaptureFlowViewModelTest.kt` (harness: `SavedStateHandle()`, `FakeMomentWriteService`, `FakeSupabaseService(session = testSession("user-1"))`, `FakeComposerMedia`, `FakeSnapWriteRepository`; check `FakeComposerMedia`'s knobs for the capture date and processed image before writing the photo cases):

```kotlin
    @Test
    fun `saving sends a whole-second save_moment and dismisses`() =
        runTest {
            val h = Harness()
            val effects = recordEffects(h.viewModel.effects)
            h.viewModel.machine().setHappenedAt(OffsetDateTime.parse("2026-10-12T08:40:00.5+02:00"))
            h.viewModel.machine().goTo(CaptureStep.NAME)
            h.viewModel.machine().setName("Swim at dawn")
            h.viewModel.save(thenComplete = false)
            advanceUntilIdle()
            assertEquals(MomentSavePayload(name = "Swim at dawn", happenedAt = OffsetDateTime.parse("2026-10-12T08:40:00.5+02:00")), h.writes.saved.single())
            assertEquals(listOf(CaptureFlowEffect.Saved), effects.values.filterNot { it is CaptureFlowEffect.Haptic })
            effects.stop()
        }

    @Test
    fun `make it a pebble now saves, then hands over the new moment id`() =
        runTest {
            val h = Harness()
            val effects = recordEffects(h.viewModel.effects)
            h.writes.nextMomentId = "m-9"
            h.viewModel.machine().goTo(CaptureStep.NAME)
            h.viewModel.machine().setName("Walk")
            h.viewModel.save(thenComplete = true)
            advanceUntilIdle()
            assertEquals(listOf(CaptureFlowEffect.SavedThenComplete("m-9")), effects.values.filterNot { it is CaptureFlowEffect.Haptic })
            effects.stop()
        }

    @Test
    fun `a photo-only moment sends its uploaded snap and no name`() =
        runTest {
            val h = Harness()
            h.viewModel.onPhotoPicked(Uri.EMPTY)
            advanceUntilIdle()
            h.viewModel.machine().goTo(CaptureStep.NAME)
            h.viewModel.save(thenComplete = false)
            advanceUntilIdle()
            val saved = h.writes.saved.single()
            assertNull(saved.name)
            val snapId = h.snapRepo.uploaded.single()
            assertEquals(listOf(PebbleSnapPayload(snapId, "user-1/$snapId", 0)), saved.snaps)
        }

    @Test
    fun `a failed save keeps the uploaded files for the retry`() =
        runTest {
            val h = Harness()
            h.viewModel.onPhotoPicked(Uri.EMPTY)
            advanceUntilIdle()
            h.writes.failNext = postgrestException("media_quota_exceeded")
            h.viewModel.machine().goTo(CaptureStep.NAME)
            h.viewModel.save(thenComplete = false)
            advanceUntilIdle()
            assertEquals(R.string.pebble_save_error_media_quota, h.viewModel.uiState.value.flow.errorRes)
            assertTrue(h.snapRepo.deletedSnapIds.isEmpty())
        }

    @Test
    fun `discarding asks first when there is input, and deletes the uploaded files`() =
        runTest {
            val h = Harness()
            val effects = recordEffects(h.viewModel.effects)
            h.viewModel.onPhotoPicked(Uri.EMPTY)
            advanceUntilIdle()
            h.viewModel.onCloseRequested()
            assertTrue(h.viewModel.uiState.value.isCloseConfirmPresented)
            h.viewModel.onDiscard()
            advanceUntilIdle()
            assertEquals(1, h.snapRepo.deletedSnapIds.size)
            assertTrue(CaptureFlowEffect.Dismiss in effects.values)
            assertTrue(h.writes.saved.isEmpty())
            effects.stop()
        }

    @Test
    fun `closing an empty capture does not ask`() =
        runTest {
            val h = Harness()
            val effects = recordEffects(h.viewModel.effects)
            h.viewModel.onCloseRequested()
            advanceUntilIdle()
            assertFalse(h.viewModel.uiState.value.isCloseConfirmPresented)
            assertTrue(CaptureFlowEffect.Dismiss in effects.values)
            effects.stop()
        }

    @Test
    fun `an upload in flight blocks the save with its copy`() =
        runTest {
            val h = Harness()
            h.snapRepo.uploadGate = CompletableDeferred()
            h.viewModel.onPhotoPicked(Uri.EMPTY)
            runCurrent()
            h.viewModel.machine().goTo(CaptureStep.NAME)
            h.viewModel.machine().setName("Walk")
            h.viewModel.save(thenComplete = false)
            assertEquals(R.string.pebble_save_error_photo_uploading, h.viewModel.uiState.value.flow.errorRes)
            assertTrue(h.writes.saved.isEmpty())
        }

    @Test
    fun `name, date and step survive process death`() =
        runTest {
            val handle = SavedStateHandle()
            val first = Harness(handle)
            first.viewModel.machine().setName("Walk")
            first.viewModel.machine().setHappenedAt(OffsetDateTime.parse("2026-10-12T06:40:00Z"))
            first.viewModel.machine().goTo(CaptureStep.NAME)
            advanceUntilIdle()
            val second = Harness(handle)
            val state = second.viewModel.uiState.value.flow
            assertEquals("Walk", state.draft.name)
            assertEquals(OffsetDateTime.parse("2026-10-12T06:40:00Z").toInstant(), state.draft.happenedAt.toInstant())
            assertEquals(CaptureStep.NAME, state.step)
        }
```

Two fakes need knobs for these cases. `FakeComposerMedia` today throws on the photo path ("cover it under Robolectric"); give it opt-in results that keep that default:

```kotlin
    /** When set, the photo path runs on the JVM instead of throwing. */
    var processed: ProcessedImage? = null
    var captureDateResult: OffsetDateTime? = null

    override suspend fun captureDate(uri: Uri): OffsetDateTime? =
        if (processed != null) captureDateResult else error("the photo path needs a real Uri — cover it under Robolectric (#857)")

    override suspend fun process(uri: Uri): ProcessedImage =
        processed ?: error("the photo path needs a real Uri — cover it under Robolectric (#857)")
```

and the Capture `Harness(handle: SavedStateHandle = SavedStateHandle())` sets `media.processed = ProcessedImage(original = byteArrayOf(1), thumb = byteArrayOf(2))`. `FakeSnapWriteRepository` gains `var uploadGate: CompletableDeferred<Unit>? = null`, awaited at the top of `uploadProcessed`, to hold an upload in flight.

- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3: Implement** `CaptureFlowViewModel.kt`:

```kotlin
package app.pbbls.android.features.path.capture

import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.R
import app.pbbls.android.core.common.UiEffects
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.MomentWriteServicing
import app.pbbls.android.core.data.SnapUploadCoordinator
import app.pbbls.android.core.data.SnapWriteRepositing
import app.pbbls.android.core.data.SupabaseServicing
import app.pbbls.android.core.data.TapHaptic
import app.pbbls.android.core.data.toDataError
import app.pbbls.android.core.model.FormSnap
import app.pbbls.android.core.model.MomentSavePayload
import app.pbbls.android.core.model.PebbleSnapPayload
import app.pbbls.android.core.ui.momentWriteErrorRes
import app.pbbls.android.features.path.ComposerMedia
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject

private const val TAG = "capture-flow"
private const val KEY_STEP = "capture-step"
private const val KEY_NAME = "capture-name"
private const val KEY_WHEN = "capture-when-epoch"

sealed interface CaptureFlowEffect {
    data class Haptic(
        val flavor: TapHaptic,
    ) : CaptureFlowEffect

    data object Saved : CaptureFlowEffect

    data class SavedThenComplete(
        val momentId: String,
    ) : CaptureFlowEffect

    data object Dismiss : CaptureFlowEffect
}

data class CaptureFlowUiState(
    val flow: CaptureFlowState = CaptureFlowState(),
    val snap: FormSnap? = null,
    val seededFromPhoto: Boolean = false,
    val isCloseConfirmPresented: Boolean = false,
)

/**
 * Capture: a moment in three steps, saved with `save_moment` (PD-3). Nothing
 * is kept across a crash but what `SavedStateHandle` holds (step, name, date):
 * the server has not seen them, and a three-step form needs no autosave (PD-7).
 */
@HiltViewModel
class CaptureFlowViewModel
    @Inject
    constructor(
        private val savedState: SavedStateHandle,
        private val writes: MomentWriteServicing,
        private val supabase: SupabaseServicing,
        private val media: ComposerMedia,
        snapRepo: SnapWriteRepositing,
    ) : ViewModel() {
        private val effectsOut = UiEffects<CaptureFlowEffect>(viewModelScope)
        val effects: Flow<CaptureFlowEffect> = effectsOut.flow

        private val model =
            CaptureFlowModel(
                haptic = { effectsOut.emit(CaptureFlowEffect.Haptic(it)) },
                initial =
                    CaptureFlowState(
                        draft =
                            CaptureDraft(
                                name = savedState.get<String>(KEY_NAME).orEmpty(),
                                happenedAt =
                                    savedState.get<Long>(KEY_WHEN)?.let {
                                        OffsetDateTime.ofInstant(Instant.ofEpochSecond(it), ZoneId.systemDefault())
                                    } ?: OffsetDateTime.now(),
                            ),
                        step = savedState.get<String>(KEY_STEP)?.let { s -> CaptureStep.entries.firstOrNull { it.name == s } } ?: CaptureStep.PHOTO,
                    ),
            )

        /** Form-scoped (M42 D6), held by the ViewModel so a rotation mid-upload keeps the upload. */
        private val snaps = SnapUploadCoordinator(repo = snapRepo)

        private val _uiState = MutableStateFlow(CaptureFlowUiState(flow = model.state.value))
        val uiState: StateFlow<CaptureFlowUiState> = _uiState.asStateFlow()

        private val userId: String?
            get() = supabase.session?.user?.id

        init {
            viewModelScope.launch {
                model.state.collect { flow ->
                    savedState[KEY_STEP] = flow.step.name
                    savedState[KEY_NAME] = flow.draft.name
                    savedState[KEY_WHEN] = flow.draft.happenedAt.toEpochSecond()
                    _uiState.update { it.copy(flow = flow) }
                }
            }
            viewModelScope.launch {
                snapshotFlow { snaps.formSnap }.collect { snap ->
                    model.hasSnap = snap != null
                    _uiState.update { it.copy(snap = snap) }
                }
            }
        }

        fun machine(): CaptureFlowModel = model

        fun onPhotoPicked(uri: Uri) {
            val id = userId ?: return
            viewModelScope.launch {
                runCatchingCancellable {
                    // EXIF first: the re-encode writes no metadata (M58 D7).
                    val taken = media.captureDate(uri)
                    _uiState.update { it.copy(seededFromPhoto = taken != null) }
                    model.applyCaptureDate(taken)
                    snaps.attach(media.process(uri), id)
                }.onFailure { Log.e(TAG, "photo pick processing failed", it) }
            }
        }

        fun onRetryPhoto() {
            val id = userId ?: return
            viewModelScope.launch { snaps.retryCurrent(id) }
        }

        fun onRemovePhoto() {
            val id = userId ?: return
            viewModelScope.launch { snaps.removePending(id) }
        }

        fun save(thenComplete: Boolean) {
            val id = userId
            if (id == null) {
                Log.e(TAG, "save: no current user")
                model.fail(R.string.record_signed_out_error)
                return
            }
            blockedBySnap()?.let {
                model.fail(it)
                return
            }
            if (!model.state.value.canSave) return
            val draft = model.draft
            val snap = snaps.pendingSnapForPayload()?.let { PebbleSnapPayload(it.id, it.storagePrefix(id), 0) }
            viewModelScope.launch {
                model.beginSave()
                withContext(NonCancellable) {
                    runCatchingCancellable { writes.saveMoment(MomentSavePayload.capture(draft.name, draft.happenedAt, snap)) }
                        .fold(
                            onSuccess = { momentId ->
                                model.saved()
                                // The files now belong to the moment: forget them without deleting.
                                snaps.reset()
                                effectsOut.emit(if (thenComplete) CaptureFlowEffect.SavedThenComplete(momentId) else CaptureFlowEffect.Saved)
                            },
                            onFailure = {
                                Log.e(TAG, "save_moment failed", it)
                                // Files stay for the retry; discarding is the cleanup (PD-5).
                                model.fail(momentWriteErrorRes(it.toDataError(), R.string.moment_save_error_generic))
                            },
                        )
                }
            }
        }

        @StringRes
        private fun blockedBySnap(): Int? =
            when {
                snaps.isUploading -> R.string.pebble_save_error_photo_uploading
                snaps.hasFailed -> R.string.pebble_save_error_photo_failed
                else -> null
            }

        /** Asks only when there is something to lose (PD-4). */
        fun onCloseRequested() {
            if (model.isSaving) return
            if (model.state.value.hasInput) {
                _uiState.update { it.copy(isCloseConfirmPresented = true) }
            } else {
                discardAndClose()
            }
        }

        fun onKeepGoing() = _uiState.update { it.copy(isCloseConfirmPresented = false) }

        fun onDiscard() {
            _uiState.update { it.copy(isCloseConfirmPresented = false) }
            discardAndClose()
        }

        private fun discardAndClose() {
            viewModelScope.launch {
                userId?.let { snaps.cancelAndCleanup(it) }
                effectsOut.emit(CaptureFlowEffect.Dismiss)
            }
        }

        fun onSystemBack() {
            when {
                model.isSaving -> Unit
                model.step.previous != null -> model.back()
                else -> onCloseRequested()
            }
        }
    }
```

- [ ] **Step 4:** Run → PASS. Commit `feat(android): capture a moment through save_moment`.

### Task 6.3: Screen and wiring

- [ ] **Step 1:** `RecordStepScaffold.kt`: add

```kotlin
    /** Capture's name step: the primary save, and the save-then-complete text button beneath it. */
    data class PrimaryWithSecondary(
        val primary: Primary,
        val secondaryLabel: String,
        val onSecondary: () -> Unit,
    ) : RecordStepAction
```

and render it as the `Primary` button followed by a `TextButton(onClick = action.onSecondary, enabled = action.primary.enabled && !action.primary.isLoading)` with `bodyLarge` text in `colors.primary`, padded like the `Text` case. The `when (action)` stays exhaustive with no `else`.
- [ ] **Step 2:** `CaptureFlowScreen(onDismiss, onSaved, onSavedThenComplete, modifier, viewModel = hiltViewModel())` mirrors `CompleteFlowScreen`: haptics through `rememberTapHaptics()`, effects through `ObserveUiEffects` (`Saved` → `onSaved()`, `SavedThenComplete(id)` → `onSavedThenComplete(id)`, `Dismiss` → `onDismiss()`), `NavigationBackHandler { viewModel.onSystemBack() }`, the photo picker launcher (`PickVisualMedia.ImageOnly`), and a stateless `CaptureFlowContent(uiState, model, onPickPhoto, onRetryPhoto, onRemovePhoto, onSave, onSaveThenComplete, onClose, onKeepGoing, onDiscard)`. Content: `RecordFlowChrome(position = step.ordinal + 1, total = CaptureStep.entries.size, canGoBack = step.previous != null, …)`, then `RecordStepScaffold(title, subtitle, action)` over

```kotlin
        when (state.step) {
            CaptureStep.PHOTO -> RecordPhotoStep(snap = uiState.snap, onPick = onPickPhoto, onRetry = onRetryPhoto, onRemove = onRemovePhoto)
            CaptureStep.WHEN -> RecordWhenStep(happenedAt = state.draft.happenedAt, onChange = { model.setHappenedAt(it) }, seededFromPhoto = uiState.seededFromPhoto)
            CaptureStep.NAME -> {
                RecordNameStep(name = state.draft.name, limit = CaptureFlowModel.NAME_LIMIT, onChange = { model.setName(it) })
                state.errorRes?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error) }
            }
        }
```

with actions: `PHOTO` → `RecordStepAction.Text(skip or done, model::advance)`; `WHEN` → `Primary(record_action_continue, enabled = true, isLoading = false, model::advance)`; `NAME` → `PrimaryWithSecondary(Primary(capture_save, enabled = state.canSave, isLoading = state.isSaving, onClick = onSave), capture_make_pebble, onSaveThenComplete)`. The close confirmation is an `AlertDialog` titled `capture_close_title` with `record_close_discard` (error colour) and `record_close_keep_going`.

Strings (EN / FR):

```xml
<string name="capture_save">Save the moment</string>
<string name="capture_make_pebble">Make it a pebble now</string>
<string name="capture_close_title">Discard this moment?</string>
```

```xml
<string name="capture_save">Garder ce moment</string>
<string name="capture_make_pebble">En faire un galet maintenant</string>
<string name="capture_close_title">Abandonner ce moment ?</string>
```

- [ ] **Step 3:** Navigation. `PebblesKeys.kt`: `@Serializable data object Capture : PebblesKey` (modal). `PebblesEntryProvider.kt`:

```kotlin
    entry<PebblesKey.Capture>(metadata = NavTransitions.forKey(PebblesKey.Capture)) {
        CaptureFlowScreen(
            onDismiss = navigator::goBack,
            // Path's resume refresh shows the new blank stone; no detail is opened (PD-24).
            onSaved = navigator::goBack,
            // Replace Capture with Complete rather than stacking it, as Drafts → RecordFlow did.
            onSavedThenComplete = { momentId ->
                navigator.goBack()
                navigator.navigate(PebblesKey.CompletePebble(momentId))
            },
        )
    }
```

Path: `onCreatePebble = { navigator.navigate(PebblesKey.Capture) }`; delete `onCreatePebbleLongPress` from `PathScreen`, the entry provider and `NewPebbleFab`, and `onLongPress` from `NewPebbleButton` (and its `combinedClickable` back to `clickable`). Keep `JourneyTags.NEW_PEBBLE` on the button: `:baselineprofile` taps it.
- [ ] **Step 4:** Journeys. `ui/NavigationTest`: `new pebble opens the capture flow` (asserts `R.string.record_photo_title`); delete `long-pressing new pebble opens the all-at-once composer`. New `ui/CaptureFlowTest`: New pebble → Skip → Continue → type a name → `capture_save` → back on Path, `FakeMomentWriteService.saved.single().name == "Walk"`; and `capture_make_pebble` lands on `R.string.record_valence_title`. `ui/RestorationTest`: replace `a record step survives process death through the restore prompt` with `a capture step survives process death` (type a name on the name step, `restartAfterProcessDeath()`, the name and step are back).
- [ ] **Step 5: Previews** `CaptureFlowScreenshots.kt`: `Capture_Photo`, `Capture_PhotoUploading`, `Capture_When_FromPhoto`, `Capture_NameEmpty` (both buttons disabled), `Capture_NamePhotoOnly` (enabled without a name), `Capture_NameError`, `Capture_CloseConfirm`.
- [ ] **Step 6:** `./gradlew ktlintCheck testDebugUnitTest assembleDebug lint compileDebugScreenshotTestKotlin` → BUILD SUCCESSFUL. `git grep -n "compose-pebble" app/src/main` now hits only `PebbleWriteService.kt` and comments, none reachable from a key. Commits: `feat(android): the Capture flow screen`, `feat(android): new pebble opens Capture`. PR with `rebaseline-screenshots`.

**Manual check:** capture photo-only (EXIF date seeded, "Untitled moment" on Path with the photo and a blank stone); capture name-only and chain into Complete; cancel with a photo attached and confirm the upload disappears from Storage (Supabase dashboard).

---

## Part 7 — Collection glyph and retirement (`feat/1048-android-moments-retire`)

**Story:** collections carry the glyph their pebbles inherit, editable in the collection form. Everything the split retired leaves the codebase: the eleven-step flow, the long-press composer, drafts, the `compose-pebble` write service and their tests, previews and strings. The surviving step bodies move to a neutral package. Docs and the Arkaik map catch up.

### Task 7.1: Collection glyph picker

**Files:** `core/model/Collection.kt`, `core/model/CollectionPayloads.kt`, `core/data/CollectionsService.kt`, `features/profile/CollectionFormViewModel.kt`, `CollectionFormScreen.kt`, `navigation/PebblesEntryProvider.kt` (CollectionForm entry), `testing/FakeCollectionsService.kt`, strings; tests `CollectionDecodingTest`, `CollectionPayloadsTest`, `CollectionFormViewModelTest`.

- [ ] **Step 1: Write the failing tests.** `CollectionPayloadsTest`:

```kotlin
    @Test
    fun `insert carries the glyph, or an explicit null`() {
        assertEquals(
            Json.parseToJsonElement("""{"user_id":"u-1","name":"Sea days","mode":null,"glyph_id":"g-1"}"""),
            collectionInsertPayload(userId = "u-1", name = "Sea days", mode = null, glyphId = "g-1"),
        )
        assertEquals(
            Json.parseToJsonElement("""{"user_id":"u-1","name":"Sea days","mode":"stack","glyph_id":null}"""),
            collectionInsertPayload(userId = "u-1", name = "Sea days", mode = CollectionMode.STACK, glyphId = null),
        )
    }

    @Test
    fun `update clears the glyph with an explicit null`() {
        assertEquals(
            Json.parseToJsonElement("""{"name":"Sea days","mode":null,"glyph_id":null}"""),
            collectionUpdatePayload(name = "Sea days", mode = null, glyphId = null),
        )
    }
```

`CollectionDecodingTest`: a row `{"id":"c-1","name":"Sea days","mode":null,"glyph_id":"g-1","glyphs":{"id":"g-1","name":null,"strokes":[{"d":"M 0 0 L 10 10","width":6}],"view_box":"0 0 200 200"},"pebble_count":[{"count":3}]}` decodes to `Collection(glyphId = "g-1", glyph.id == "g-1", pebbleCount = 3)`, and a row with `"glyph_id":null,"glyphs":null` decodes to null glyph. `CollectionFormViewModelTest`: loading seeds the glyph; picking one and saving passes its id to `FakeCollectionsService.update`; removing it passes null; a create passes the picked id.

- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3: Implement.** `Collection` gains `val glyph: Glyph? = null` (last parameter, defaulted so existing builders compile); `CollectionRow` gains `@SerialName("glyph_id") val glyphId: String? = null` and `val glyphs: Glyph? = null`, mapped in `toCollection()` (`glyph = glyphs`). The two `CollectionsService` selects become `"id, name, mode, glyph_id, glyphs(id, name, strokes, view_box), pebble_count:collection_pebbles(count)"` (the `ProfileService` carousel select is left alone). The payload builders take `glyphId: String?` and always `put("glyph_id", glyphId)`; `CollectionsServicing.create(name, mode, glyphId)` and `update(collectionId, name, mode, glyphId)` pass it. The `collections_glyph_usable` trigger guards both writes (42501 → `DataError.Unauthorized` → the form's existing save error). `set_collection_glyph` is not called (PD-19).

`CollectionFormUiState` gains `val glyph: Glyph? = null` (seeded from `original.glyph`); its dirty check includes `glyph?.id != original?.glyph?.id`; `CollectionFormViewModel.onGlyphPicked(glyph: Glyph?)`. `CollectionFormScreen(collectionId, onDismiss, onSaved, glyphPicker: GlyphPickerSlot, …)` adds, below the mode picker, the same glyph section `SoulFormScreen` uses (read `SoulFormScreen.kt` around its `GlyphPickerSheet` call and mirror it): a tappable `GlyphView(case = if (glyph != null) GlyphViewCase.SELECTED else GlyphViewCase.CREATE, strokes = glyph?.strokes, viewBox = glyph?.viewBox ?: "0 0 200 200", side = 72.dp)`, the hint line, a "Remove glyph" `TextButton` when one is set, and `GlyphPickerSheet(currentGlyphId = glyph?.id, onDismiss, onSelected = { viewModel.onGlyphPicked(it) }, picker = glyphPicker)`. The entry provider passes `glyphPicker` to the CollectionForm entry exactly as it does for SoulForm.

Strings (EN / FR):

```xml
<string name="collection_form_glyph">Glyph</string>
<string name="collection_form_glyph_hint">Pebbles in this collection wear its glyph.</string>
<string name="collection_form_glyph_remove">Remove glyph</string>
```

```xml
<string name="collection_form_glyph">Glyphe</string>
<string name="collection_form_glyph_hint">Les galets de cette collection portent son glyphe.</string>
<string name="collection_form_glyph_remove">Retirer le glyphe</string>
```

- [ ] **Step 4:** Run → PASS. Previews in `CollectionsScreenshots.kt`: `CollectionForm_WithGlyph`, `CollectionForm_NoGlyph`. Commit `feat(android): give a collection the glyph its pebbles wear`.

### Task 7.2: Move the surviving step bodies

- [ ] **Step 1:** Pure move, its own commit: `features/path/record/RecordStepScaffold.kt`, `RecordFlowChrome.kt` and `steps/{RecordPhotoStep,RecordWhenStep,RecordNameStep,RecordValenceStep,RecordEmotionStep,RecordDomainStep,RecordSoulsStep,RecordCollectionStep,RecordPrivacyStep,RecordSuccessStep}.kt` move to `features/path/flow/` and `features/path/flow/steps/`, package `app.pbbls.android.features.path.flow[.steps]`; update imports in `capture/`, `complete/` and the screenshot sources. No renames, no edits. Commit `quality(android): move the shared step bodies out of the record flow`.

### Task 7.3: Delete the retired composers and drafts

- [ ] **Step 1: Delete** (main, under `app/src/main/kotlin/app/pbbls/android/`):
  - `features/path/record/` (what remains: `RecordFlowModel.kt`, `RecordFlowState.kt`, `RecordFlowViewModel.kt`, `RecordFlowScreen.kt`, `RecordStep.kt`, `RecordStepContent.kt`, `steps/RecordGlyphStep.kt`)
  - `features/path/create/CreatePebbleScreen.kt`, `CreatePebbleViewModel.kt` (keep `PebbleForm`, the pickers, `WhenRow`, `VisibilityChip`, `ExistingSnapRow`, `AttachedPhotoView`, `EmotionPickerGrouping`: the edit form uses them)
  - `features/path/DraftsScreen.kt`, `DraftsViewModel.kt`
  - `core/data/PebbleDraftsService.kt`, `ComposerDraftCoordinator.kt`, `ComposerSnapshotStore.kt`, `PebbleWriteService.kt`
  - `core/model/PebbleDraftPayload.kt`, `PebbleCreatePayload.kt`, `ComposePebbleResponse.kt`
  - `PebbleDraft.glyphId` and `PebbleDraft.isValid` (nothing reads them now); `SnapUploadCoordinator.handleSaveFailure` (no caller since Part 2)
- [ ] **Step 2: Unwire.** `PebblesKeys`: remove `RecordFlow`, `CreatePebble`, `Drafts` (and from `PebblesKeysTest`). `PebblesEntryProvider`: remove their entries and Path's `onOpenDrafts`. `PathViewModel`: drop `drafts`, `draftCount`, `refreshDraftCount()`, `PathUiState.Content.draftCount`; `PathScreen`/`PathContent`: drop `onOpenDrafts`, `draftCount` and the drafts `TextButton`. `ServiceBindings`: drop `PebbleWriteServicing`, `PebbleDraftsServicing`. `ServiceModule`: drop `provideComposerSnapshotStore` and `provideComposerSnapshotStoring` (its KDoc's "three services" count changes; update it). `RootViewModel`: drop `ComposerSnapshotStoring`; `completeSnapshots.clearAll()` stays, and also removes the legacy key: add `editor.remove("composerSnapshot")` inside `CompleteSnapshotStore.clearAll()` with a one-line comment (the retired composer's snapshot must not outlive the sign-out it was scoped to). `SupabaseModule`: the `Functions` comment now reads "Functions serves the `delete-account` edge function (ProfileService)".
- [ ] **Step 3: Delete the tests and fakes** for the deleted code: `RecordFlowModelTest`, `RecordFlowViewModelTest`, `RecordStepTest`, `CreatePebbleViewModelTest`, `DraftsViewModelTest`, `PebbleDraftPayloadTest`, `DraftCrossSurfaceDecodingTest`, `PebblePayloadTest`, `PebbleWriteServiceTest`, `ComposerAutosaveTest`, `OwnedComposerSnapshotTest`, `ui/RecordFlowPublishTest`, `testing/FakePebbleWriteService.kt`, `testing/FakePebbleDraftsService.kt`, `testing/FakeComposerSnapshotStore.kt` (and their `FakeServicesModule` bindings and `ServiceFakesTest` cases); trim `PebbleDraftTest` to what `PebbleDraft` still does (`from(MomentDetail)`, `answers`); delete the `handleSaveFailure` cases from `SnapUploadCoordinatorTest`. Screenshots: delete `RecordFlowScreenshots.kt`, `DraftsScreenshots.kt`, `CreateScreenshots.kt` and the references `validateDebugScreenshotTest` reports as orphaned (the `rebaseline-screenshots` run removes them).
- [ ] **Step 4: Strings.** `./gradlew lint`; delete from **both** string files every key reported as `UnusedResources` (expected among them: `drafts_*`, `draft_*`, `record_glyph_title`, `record_glyph_subtitle`, `record_close_title`, `record_action_publish`; `pebble_save_error_generic` stays, Complete and the edit form use it; delete exactly what the report lists, then re-run until clean). `LocalizationParityTest` must stay green.
- [ ] **Step 5: The grep gate.**

```bash
cd apps/android
git grep -nE 'compose-pebble|pebble_drafts|path_pebbles|delete_pebble_media|render_svg|render_version|can_use_glyph|v_pebbles_full|PebbleDraftsServic|ComposerDraftCoordinator|RecordFlow|CreatePebble' app/src
```

Expected: no hits in code. Comments that still describe removed behaviour are rewritten or deleted in the same commit.
- [ ] **Step 6:** `./gradlew ktlintCheck testDebugUnitTest assembleDebug lint compileDebugScreenshotTestKotlin` → BUILD SUCCESSFUL. Also run `./gradlew bundleRelease` once (R8 runs nowhere else locally) → BUILD SUCCESSFUL. Commits: `chore(android): retire the eleven-step flow, the long-press composer and drafts`, `chore(android): drop the strings the retired composers used`.

### Task 7.4: Docs, decision log, Arkaik

- [ ] **Step 1:** `apps/android/CLAUDE.md` § "Behavior that mirrors iOS on purpose": replace the "Two composers coexist (M58, #725)" bullet with: "**Capture and Complete (#1048).** New pebble opens Capture (photo · when · name → `save_moment`); Complete (valence · emotion · domain · souls · collection · privacy → `complete_pebble`) opens from the read page of an uncompleted moment or from Capture's \"Make it a pebble now\". Edits go through `EditPebbleScreen` (`save_moment`, then `complete_pebble` when the moment has your pebble). Every flow interaction goes through its state machine, so the haptic-on-every-tap rule stays structural. Spec: `docs/superpowers/specs/2026-10-11-moments-and-pebbles-split-design.md` §6." In § "Three test layers", replace the `FakePathService` example with `FakeMomentReadService`.
- [ ] **Step 2:** `docs/decisions/log.md`: append one entry (skip it if the iOS stack for #1047 already appended the same one; then append only what differs) titled "2026-10-xx — Mobile composers on the moments split: photo removal rides save_moment, collection glyph is a column write, delete asks pebble-or-moment", recording PD-15, PD-16 and PD-19 with their reasons, so a future agent does not "fix" them back to `delete_moment_media` or `set_collection_glyph`.
- [ ] **Step 3: Arkaik**, through the `arkaik-mcp` tools only (never `docs/arkaik/bundle.json`; if the tools are unavailable, say so and stop). At Part 1's pick-up, move the acceptances these flows cover to `development` for android (`list_nodes` with `platform: android` and queries "capture", "complete", "moment", then `update_node`). At Part 7:
  - **Create** (if the iOS stack has not already): `V-moment-capture` "Capture Moment" and `V-pebble-complete` "Complete Pebble", platforms `ios`, `android`, status `development`.
  - **Update** descriptions: `V-timeline` (Path: blank stones for uncompleted moments), `V-pebble-detail` (moment read page: "Make it a pebble" CTA, pebble-or-moment delete), `V-pebble-edit` (moment and pebble fields, two writers, no glyph), `V-collection-create` and `V-collection-edit` (glyph picker), `V-pebble-visual` (stone composed from shape seed + derived glyph strokes), `V-record-success` (now Complete's success screen).
  - **Retire for android** per the `arkaik` skill's hosted-map rules: `V-pebble-record` (eleven-step flow), `V-quick-pebble-editor` (long-press composer), `V-pebble-drafts`; `V-glyph-attach` loses its pebble use (description), it stays for souls, profiles and collections.
  - **Edges:** `V-timeline` → `V-moment-capture`; `V-moment-capture` → `V-pebble-complete`; `V-pebble-detail` → `V-pebble-complete`; `V-moment-capture` → `API-rpc-save-moment`; `V-pebble-complete` → `API-rpc-complete-pebble`; `V-pebble-detail` → `API-rpc-delete-moment`, `DM-v-moments-full`; `V-timeline` → `API-rpc-path-moments`.
  - Never set `live`: the GitHub App moves to `releasing` at merge.
- [ ] **Step 4:** Commit `docs(android): describe Capture and Complete`. PR body `Resolves #1048`, labels per the PR checklist, `rebaseline-screenshots`.

**Manual check (whole stack, before merging Part 7):** on the AVD and then, with the maintainer's go-ahead, the physical device: capture photo-only, capture named and complete later, chain capture → complete, edit both halves, delete the pebble only, delete a moment, set a collection glyph and watch its pebbles change carving, sign out and back in (no snapshot restored). Regenerate the baseline profile by hand afterwards if Path's startup shape changed (`android-baseline-profile` skill).

---

## Parity decisions

Every choice the iOS plan for #1047 must match. "Copy" gives EN / FR; Android keys in `code`.

- **PD-1 Step order.** Capture: photo → when → name. Complete: valence → emotion → domain → souls → collection → privacy → success. The progress indicator counts 3 and 6 steps; success is uncounted, has no back and no close.
- **PD-2 How each step advances.** Photo: Skip / Done text button, never auto-advances on pick (upload state stays visible). When: Continue, pre-filled from the photo's EXIF capture date or now, "Taken from your photo" note when seeded. Name: primary "Save the moment" plus a text button "Make it a pebble now"; both enabled when the trimmed name is non-empty **or** an uploaded photo is attached; the name is clamped to 40 characters client-side. Valence: arrives seeded at neutral-medium, a pick commits in place, Continue advances. Emotion, domain, collection: a tap commits and advances. Souls: multi-select, Skip / Done. Privacy: a tap selects (default secret); the primary "Make it a pebble" calls `complete_pebble`. One haptic per interaction: selection on picks, advance on step changes, warning on a blocked advance or failure, success on a saved moment and on a completed pebble.
- **PD-3 Capture save.** `save_moment` with `name` (trimmed, key omitted when blank), `happened_at`, and `snaps: [{id, storage_path, sort_order: 0}]` only when a photo is uploaded. Then a success haptic and back to Path, with no success screen, no karma, no `check_achievements`. "Make it a pebble now" performs the same save, then replaces Capture with Complete for the returned moment id.
- **PD-4 Capture close.** ✕, or back on the photo step, asks "Discard this moment?" / "Abandonner ce moment ?" with "Discard" / "Abandonner" and "Keep going" / "Continuer l'édition" only when a name is typed or a photo is attached; Discard deletes the uploaded photo files. With nothing entered it closes silently. There is no drafts list and no "save as draft".
- **PD-5 Save failures keep uploads.** A failed `save_moment` (Capture or edit) keeps the user on the step with the mapped error and does not delete the uploaded photo files, so a retry still references them; leaving (discard/cancel) is the cleanup, the orphan sweep the backstop. This departs from the old composer's delete-on-failure on purpose.
- **PD-6 Complete close.** ✕ closes at once, without a dialog; back on the valence step does the same. The answers stay in the per-moment autosave.
- **PD-7 Autosave.** Device-local, one entry per moment, key `completeSnapshot.<moment id lowercased>`, holding valence (intensity + positiveness), emotion id, domain id, soul ids, collection id, visibility, stamped with the owner's user id and restored only for that user. Debounced 800 ms, flushed on ✕. Restored silently (no prompt) when Complete opens for that moment, landing on the first unanswered of valence/emotion/domain, else privacy. Souls and collections that no longer exist are dropped, but only once reference data has loaded. A seeded valence and the default grade alone are not input and are not saved. Cleared on a successful completion, on `delete_moment` of that moment, and every entry on sign-out. Capture has no autosave; only process-death state (step, name, date) is kept.
- **PD-8 Completion payload.** `complete_pebble` always sends every key: `moment_id`, `emotion_id`, `intensity`, `positiveness`, `visibility`, `domain_ids` (exactly one), `soul_ids`, `collection_ids` (zero or one). Never `new_souls`, `new_collections` or `cards` (souls are still created inline before completion; omitting `cards` keeps existing cards).
- **PD-9 After `complete_pebble` succeeds**, in order: clear the autosave; read karma (newest `karma_events` row for `ref_id` = pebble id and `reason` = `pebble_created`; null on absence or error) and `resolve_pebble_glyph(pebble id)` in parallel, each best-effort; notify karma without the capsule; call `check_achievements` exactly once (its unlock overlay may present over the success screen); show the success screen. On an edit that has a pebble: the same read with `pebble_enriched`, the karma capsule shown, `check_achievements` once after both writes. A bare-moment edit and a Capture save never call it.
- **PD-10 Success screen.** The stone with the derived glyph (or the bare stone when none resolves), the moment's name ("Untitled moment" when null), "+N karma" only when N > 0, and "Back to my path" / "Retour à mon chemin". Android's stone is static (no draw-on exists on Android today); iOS keeps its draw-on.
- **PD-11 Blank stone.** An uncompleted moment is the medium-neutral silhouette with its shape strokes and no glyph, coloured with theme roles (fill = surface-variant, stroke = outline), never an emotion palette. Accessibility label "Not a pebble yet" / "Pas encore un galet". On Path its photo shows exactly as on a completed row; the row uses intensity-2 metrics and a meta line of weekday · time only.
- **PD-12 Untitled.** A null moment name shows "Untitled moment" / "Moment sans nom" (`moment_untitled`) everywhere a name is shown: Path row, soul/collection rows, read page title, delete dialog, success screen.
- **PD-13 Bare-moment read page.** Blank stone heading (or the photo frame with the blank stone over it), title, date, description if any, and one primary button "Make it a pebble" / "En faire un galet" (`moment_make_pebble`) under the title that opens Complete. No emotion/domain/collection tiles, no souls, no privacy badge, no share. Edit stays (moment fields only).
- **PD-14 Edit.** One edit screen. The moment section (photo, when, name, description) always shows; the pebble section (emotion, domain, valence, souls, collection, privacy) only when the moment has your pebble; there is no glyph row. Save = `save_moment` with every moment key (`id`, trimmed `name`, trimmed `description`, `happened_at`, the whole `snaps` set: the existing photo echoed, a new upload as its pair, `[]` when removed; `""` clears a text field), then, if a pebble exists, `complete_pebble` with every key (PD-8). Save is enabled when the name is non-blank or a photo is attached, and the pebble answers are complete when a pebble exists. If the moment saves and the pebble write fails, the pebble error shows and a retry resends both.
- **PD-15 Photo removal.** Removing a saved photo only detaches it in the form; it is removed by the edit's `save_moment` (whole-set `snaps`), which is also where `moment_empty` is enforced. After a successful save the client deletes the old files best-effort. Neither app calls `delete_moment_media`.
- **PD-16 Delete.** Entry points: long-press on a Path row, long-press on a soul or collection detail row, and a trash button in the read page's top bar (before Edit). A moment without your pebble: "Delete <name>?" / "Supprimer <name> ?", "This can't be undone." / existing FR, actions Delete / Cancel → `delete_moment`. A moment with your pebble: same title, message "Deleting the moment also deletes its pebble and its photo." / "Supprimer le moment supprime aussi son galet et sa photo.", actions "Delete the pebble, keep the moment" / "Supprimer le galet, garder le moment" → `delete_pebble(pebble id)`, "Delete the moment" / "Supprimer le moment" → `delete_moment(moment id)`, and Cancel. After `delete_moment` the client deletes the returned storage paths best-effort and the read page closes; after `delete_pebble` the read page reloads as a bare moment and soul/collection lists drop the row.
- **PD-17 Share.** Offered only when your pebble is public; the URL is `https://www.pbbls.app/p/<pebble id>`, never the moment id.
- **PD-18 Error slug → message.** `moment_empty` → "A moment needs a name or a photo." / "Un moment a besoin d'un nom ou d'une photo."; `moment_not_found` (P0002) → "This moment no longer exists." / "Ce moment n'existe plus."; `soul_not_owned`, `collection_not_owned` (42501) → "A soul or collection you picked no longer exists." / "Une âme ou une collection choisie n'existe plus."; `media_quota_exceeded` → existing "Photo limit reached on this pebble."; `not_authenticated` / 401 / other 42501 → existing "You must be signed in to save."; photo still uploading / failed → existing copy, checked before any call; everything else (`pebble_needs_*`, `invalid_payload`, `invalid_snap`, network) → "Couldn't save this moment. Please try again." / "Impossible d'enregistrer ce moment. Réessaie." for `save_moment`, existing "Couldn't save your pebble. Please try again." for `complete_pebble`; delete failures → existing "Something went wrong. Please try again."
- **PD-19 Collection glyph.** The collection create and edit forms gain an optional glyph (the shared glyph picker: own ∪ system ∪ entitled) with "Remove glyph"; it is written as `glyph_id` in the same insert/update as name and mode (explicit null clears), atomically, behind the usability trigger. `set_collection_glyph` is not called. Copy: "Glyph" / "Glyphe", "Pebbles in this collection wear its glyph." / "Les galets de cette collection portent son glyphe.", "Remove glyph" / "Retirer le glyphe".
- **PD-20 Glyph rendering.** The stone = silhouette backdrop + the shape seed's strokes (`docs/seeds/shape-seeds/`) + the derived glyph's strokes, with the glyph's `view_box` fitted into the deleted engine's slot (`translate(slot) · scale(min(slot/w, slot/h)) · translate(-minX, -minY)`; slots: small 140 at (37.5,30)/(25,30)/(30,30), medium 150 at (26,84)/(26,55)/(39,26), large 160 at (50,75)/(26,88)/(26,75) for highlight/neutral/lowlight), every stroke at the outline weight. No bounding-box normalization. A null glyph draws the stone without one.
- **PD-21 No glyph on pebbles anywhere in the UI.** No glyph step, no glyph row in the edit form, no "use on pebble" affordance. (Android's glyph library never had a pebble tab.)
- **PD-22 Retired.** The eleven-step flow, the all-at-once long-press composer, the drafts list and server drafts are removed. "New pebble" opens Capture; there is no long-press.
- **PD-23 Path "active today"** counts a completed moment created today.
- **PD-24 Navigation.** Capture and Complete are full-screen modals. Saving a capture returns to Path without opening the read page. Complete's exit returns to whatever opened it (read page, or Path after a Capture chain).
- **PD-25 Soul and collection lists** show only moments carrying your pebble tagged with that soul/collection, read from `v_moments_full` by jsonb containment on `pebble`, newest `happened_at` first. Tapping opens the edit form, as today.
- **PD-26 Read-page souls.** `v_moments_full` gives `{id, name}`; each soul's glyph comes from the client's souls cache, and a soul missing from it shows its name without a glyph.
- **PD-27 Wire nulls.** Absent optional keys are omitted, never sent as JSON null; `""` clears a text field on edit.
- **PD-28 Timestamps.** Emitted as `yyyy-MM-ddTHH:mm:ssZ` in UTC (whole seconds); decoded tolerantly (`Z`, `+00:00`, any fraction).

---

## Out of scope, and follow-ups to file

- The iOS lit-stone material port to Android (PR #975's tones and shading) — the follow-up the spec names.
- A draw-on animation for Android's success stone (Android never had one).
- Showing a collection's glyph in the collection list and detail header.
- A content report entry point on Android (`report_content` with `moment`); Android has none today.

## Self-review against the brief

- Every Supabase touchpoint is in §1 (32 changing, 26 unchanged, 8 new); every pebble-rendering composable is in §1d.
- Part order is dependency order; each part names its gate and builds alone: Part 1 is additive; Part 2 swaps every reader before deleting the old ones; Part 3 changes only rendering; Part 4 only Path/read page/delete; Part 5 adds Complete before Part 6 needs it; Part 7 only removes and adds the collection glyph.
- Shared shapes are tested against verbatim `path_moments` / `v_moments_full` / RPC payloads with explicit nulls, a photo-only moment, a null pebble, a connection's moment, and whole-second, fractional and `Z` timestamps (Tasks 1.2–1.4, 1.8).
- Types introduced before use: `PathMoment`, `MomentDetail`, `PebbleGlyph`, `PebbleAnswers`, payloads, both seams and fakes (Part 1); `MomentDeleteChoice` (Part 4); `CompleteSnapshot`, `CompleteSnapshotStoring`, `PebblesKey.CompletePebble` (Part 5) before Capture uses `CompletePebble` (Part 6); `KnownDraftIds` moved in Part 5 before `PebbleDraftPayload` is deleted in Part 7.
