# iOS moments composer (#1047) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the iOS app (`apps/ios`) onto the moments/pebbles split: every read and write goes to the new contract (`path_moments`, `v_moments_full`, `save_moment`, `complete_pebble`, `delete_pebble`, `delete_moment`, `resolve_pebble_glyph`), the stone carves the *derived* glyph from its strokes, and the eleven-step record flow is replaced by a three-step **Capture** flow and a six-step **Complete** flow.

**Architecture:** One `MomentsService` owns every Supabase name the split introduced, so the contract appears once in the app. Read models (`MomentSummary`, `MomentDetail`, `PebbleGlyph`) decode timestamps as strings through `ISO8601Flexible`, so they never depend on the ambient decoder. The stone renderer takes a `StoneCarving` (`.sample`, `.pebble(PebbleGlyph?)`, `.blank`) instead of a server SVG. Capture and Complete reuse the M58 scaffold (`RecordStepScaffold`, `RecordFlowChrome`, `TapHaptics`, `SnapUploadCoordinator`), each with its own `@Observable` model that fires every haptic, and Complete keeps a device-local autosave keyed by moment id.

**Tech Stack:** SwiftUI (iOS 17), `@Observable`, supabase-swift 2.43.1 (pinned), Swift Testing, XcodeGen, SwiftLint.

**Shared brief:** `/private/tmp/claude-503/-Users-alexis-code-pbbls/96a8494b-de79-4228-8dc6-9f051297ca71/scratchpad/mobile-brief.md` (not in the repo). The Android plan (#1048) mirrors this one; the **Parity decisions** section below is the contract between the two.

**Contract sources (on `main`, read the SQL before calling anything):**
- Spec: `docs/superpowers/specs/2026-10-11-moments-and-pebbles-split-design.md` (§1, §3, §4, §6)
- Migrations: `packages/supabase/supabase/migrations/20261012090000_moments_split_schema.sql` … `20261012090400_moments_split_web_compat.sql`
- Types: `packages/supabase/types/database.ts`
- Decision log: "2026-10-12 — A moment is what happened…" in `docs/decisions/log.md`
- The flow being replaced: `docs/superpowers/specs/2026-08-23-ios-record-flow-design.md`

---

## How to build and test (baseline recorded 2026-10-13 on `main` @ `8a6b6023`)

`project.yml` is the source of truth; the `.xcodeproj` is generated. In a fresh worktree, copy the git-ignored config first:

```bash
cp /Users/alexis/code/pbbls/apps/ios/Config/Secrets.xcconfig apps/ios/Config/
```

Build and test (run from `apps/ios`). Always clear the build products first: after `xcodegen`, `xcodebuild` can serve stale objects and a green build means nothing until they are gone.

```bash
cd apps/ios
xcodegen generate
rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*/Build
xcodebuild build-for-testing -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17'
xcodebuild test-without-building -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17'
swiftlint --quiet
```

Baseline result (untouched `main`, run with `-derivedDataPath` in a scratch dir): `** TEST BUILD SUCCEEDED **`; `✔ Test run with 518 tests in 112 suites passed`; `swiftlint`: 0 errors, 299 warnings. A part is green when the build succeeds, every test passes, and `swiftlint` reports **0 errors** (warnings are not a gate, see `.swiftlint.yml`).

To run one suite: `xcodebuild test-without-building -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' -only-testing:PebblesTests/<SuiteStructName>`.

Wherever a step says **"Build & test"**, it means the four commands above (`xcodegen`, `rm -rf …/Build`, `build-for-testing`, `test-without-building`) plus `swiftlint --quiet`.

**Localization.** Every new user-facing literal lands in `Pebbles/Resources/Localizable.xcstrings` with an `en` and a `fr` value. The catalog is formatting-sensitive: insert entries as text at the right alphabetical anchor, never re-serialize the whole file with a JSON tool. The build auto-extracts new literals (`SWIFT_EMIT_LOC_STRINGS=YES`); after the build, open the catalog in Xcode and confirm no entry is `New` or `Stale` and every new row has its `fr` value from the **copy catalog** in Parity decisions.

**Errors in red.** The app still renders inline errors with `.red` everywhere; issue #921 swaps them for the orange error token. New code follows the existing pattern (`.foregroundStyle(.red)` with a `// #921` comment) rather than inventing a token here.

---

## Part map (one part = one branch = one PR, chained with `gh stack`)

| # | Branch | What it ships | Stands alone because |
|---|---|---|---|
| 1 | `feat/1047-ios-moments-data` | Read models, payloads, `MomentsService`, error mapping, decoder tests; every read moves to `path_moments` / `v_moments_full`; deletes go to `delete_moment`; photo removal detaches and saves the whole snap set (R6). **Unbreaks the installed app's reads.** | The old composers still write through `compose-pebble` (live compat), so nothing regresses |
| 2 | `feat/1047-ios-moments-stone` | The stone carves `PebbleGlyph` strokes; `StoneCarving`; blank stone in the neutral ramp | Pure rendering over Part 1's models |
| 3 | `feat/1047-ios-moments-path` | Untitled names, the bare-moment read page, the two-option delete UX on every surface, soul/collection rows open the read page | Uses Parts 1–2 only |
| 4 | `feat/1047-ios-moments-complete` | The Complete flow, per-moment autosave, success screen, `check_achievements`, the read page's "Make it a pebble" call to action | Entry point is Part 3's read page |
| 5 | `feat/1047-ios-moments-capture` | The Capture flow behind `+`, "Make it a pebble now" chaining into Complete | Chains into Part 4 |
| 6 | `feat/1047-ios-moments-retire-composer` | Deletes the old composer, server drafts and every compat writer; splits editing into **Edit moment** (`save_moment`) and **Edit pebble** (`complete_pebble`) | Capture + Complete replace everything it deletes |
| 7 | `feat/1047-ios-moments-collection-glyph` | Collection glyph picker, the final compat grep gate, Arkaik | Last layer; touches only Profile |

Order deviates from the brief's suggestion in two places, by dependency: **Complete comes before Capture** (Capture's "make it a pebble now" chains into Complete, and the read page's call to action needs Complete to exist), and **drafts removal travels with the old composer's deletion** (Part 6), because `PebbleDraftsService`, `DraftsListSheet` and `ComposerDraftCoordinator` are entangled with the two composers it deletes. Until Part 6, `PebbleDraftsService` reads a dropped table: its calls already fail softly (logged, count 0, the drafts button stays hidden), which is today's behaviour on the installed app.

**Interim states a reviewer will see:**
- Between Part 1 and Part 2 every stone carries the bundled sample glyph (Part 1 passes no glyph, and `StoneView` draws the sample when it has none). Part 2 fixes it.
- Between Part 1 and Part 6 the old composer (`RecordFlowView`, long-press `CreatePebbleSheet`) and the old `EditPebbleSheet` save through the `compose-pebble` / `compose-pebble-update` compat edge functions, which still work. Rewriting the publish path of composers that Part 6 deletes would be throwaway work.

Workflow per part (see the `gh-stack` skill): `gh stack` branch on top of the previous one, tasks in order, **Build & test** green, commit per task, open the PR. PR bodies start with `Part N of the #1047 stack`, then `Resolves #1047` on Part 7 only. Labels on every PR: `feat`, `ios` (+ `ui`); milestone: inherit #1047's. Never write a Kritik finding id (`F-…`) in a PR body.

---
## Inventory 1 — every Supabase touchpoint in `apps/ios` (main @ `8a6b6023`)

Produced with:

```bash
cd apps/ios
grep -rnE '\.from\("|\.rpc\("|\.rpc\($|functions\.invoke|\.invoke\($|storage\.from' --include='*.swift' Pebbles PebblesWidget Shared
grep -rnE -A1 '\.rpc\($|\.invoke\($' --include='*.swift' Pebbles | grep '"'
grep -rnE 'path_pebbles|v_pebbles_full|pebble_drafts|delete_pebble_media|compose-pebble|render_svg|render_version|glyph_id|happened_at|"create_pebble"|"update_pebble"' --include='*.swift' Pebbles PebblesTests
```

74 call sites (`PebblesWidget/` and `Shared/` have none). The **Becomes** column is the end state; **Part** is where it changes. "Unchanged" rows were checked against the split migrations: they name no dropped column, table or function.

| # | File:line | Object | Op / select | Today | Becomes | Part |
|---|---|---|---|---|---|---|
| 1 | `Features/PebbleMedia/PebbleSnapRepository.swift:48` | storage `pebbles-media` | upload original + thumb | ok | unchanged (paths are `{user}/{snap}`, keyed by snap id, untouched by the split) | — |
| 2 | `…/PebbleSnapRepository.swift:67` | storage | remove by snap id | ok | unchanged | — |
| 3 | `…/PebbleSnapRepository.swift:82` | storage | remove by prefix | ok | unchanged; also called for every path `delete_moment` returns and for detached photos after a save | 1 |
| 4 | `…/PebbleSnapRepository.swift:96` | rpc `delete_pebble_media` | `p_snap_id` | **dropped function** | removed: no RPC. Removing a photo detaches it in the coordinator; the save sends the whole `snaps` set (`[]`), and the detached files are deleted after a successful save (R6). `delete_moment_media` is never called | 1 |
| 5 | `…/PebbleSnapRepository.swift:124` | storage | `createSignedURLs` | ok | unchanged | — |
| 6 | `Features/Path/PebblePublisher.swift:33` | edge fn `compose-pebble` | create | compat (wraps `create_pebble`, `render_svg: null`) | deleted with the old composer; Capture → `save_moment`, Complete → `complete_pebble` | 6 (replacement in 4, 5) |
| 7 | `Features/Path/PebbleDetailSheet.swift:88` | table `pebbles` | `id, name, description, happened_at, …, render_svg, render_version, glyph_id, emotion:…, pebble_domains(…), pebble_souls(…), collection_pebbles(…), snaps(…)` | **dropped columns** | `MomentsService.moment(id:)` → `v_moments_full` `.eq("id", momentId).single()` | 1 |
| 8 | `Features/Path/EditPebbleSheet.swift:165` | table `pebbles` | same select + `glyph:glyphs(…)` | **dropped columns** | Part 1: `MomentsService.moment(id:)`; Part 6: the sheet receives the loaded `MomentDetail.Pebble`, no fetch | 1, 6 |
| 9 | `Features/Path/EditPebbleSheet.swift:229` | edge fn `compose-pebble-update` | update | compat | Part 6: **Edit pebble** → `complete_pebble`; **Edit moment** → `save_moment` | 6 |
| 10 | `Features/Path/SoulPickerSheet.swift:96` | table `souls` | `id, name, glyph_id, glyphs(…), pebbles_count:pebble_souls(count)` | ok | unchanged | — |
| 11 | `Features/Path/PathView.swift:234` | rpc `path_pebbles` | — | compat (web only, `render_svg` null, hides uncompleted moments) | `MomentsService.pathMoments()` → rpc `path_moments` | 1 |
| 12 | `Features/Path/PathView.swift:266` | rpc `delete_pebble` | `p_pebble_id` = row id | semantics changed (keeps the moment) | Part 1: rpc `delete_moment` + file cleanup; Part 3: two-option dialog (`delete_pebble` or `delete_moment`) | 1, 3 |
| 13 | `Features/Path/ComposerDraftCoordinator.swift:144` | rpc `can_use_glyph` | resumed draft's glyph | pebbles carry no glyph | deleted (no glyph step) | 6 |
| 14 | `Features/Path/ComposerDraftCoordinator.swift:155` | table `glyphs` | `id, name, strokes, view_box, user_id` | glyph preview for a draft | deleted | 6 |
| 15 | `Features/Path/Services/PathStatsService.swift:64` | view `v_karma_summary` | `total_karma, pebbles_count` | ok | unchanged (counts pebble rows = completed pebbles) | — |
| 16 | `…/PathStatsService.swift:67` | view `v_ripple` | `ripple_level, pebbles_28d, active_today` | ok | unchanged | — |
| 17 | `…/PathStatsService.swift:70` | rpc `get_profile_engagement` | `p_tz` | ok | unchanged | — |
| 18–21 | `Features/Lab/Services/LogsService.swift:19,35,53,67` | view `v_logs_with_counts` | lab logs | ok | unchanged | — |
| 22–24 | `…/LogsService.swift:90,106,122` | table `log_reactions` | select / insert / delete | ok | unchanged | — |
| 25 | `Features/Profile/ProfileView.swift:106` | table `profiles` | `display_name, created_at, glyph_id, handle, public_profile` | ok (`glyph_id` is the profile's) | unchanged | — |
| 26 | `Features/Profile/ProfileView.swift:128` | table `glyphs` | `strokes` | ok | unchanged | — |
| 27 | `Features/Profile/Sheets/CreateCollectionSheet.swift:93` | table `collections` | insert `user_id, name, mode` | ok | insert `user_id, name, mode, glyph_id` (R11) | 7 |
| 28 | `Features/Profile/Sheets/CreateSoulSheet.swift:91` | table `glyphs` | `id, name, strokes, view_box` | ok | unchanged | — |
| 29 | `Features/Profile/Sheets/CreateSoulSheet.swift:123` | table `souls` | insert + select | ok | unchanged | — |
| 30 | `Features/Profile/Sheets/SettingsSheet.swift:500` | edge fn `delete-account` | — | ok | unchanged | — |
| 31 | `…/SettingsSheet.swift:635` | rpc `set_handle` | — | ok | unchanged | — |
| 32 | `…/SettingsSheet.swift:657` | rpc `update_profile` | — | ok | unchanged | — |
| 33 | `…/SettingsSheet.swift:666` | table `profiles` | update | ok | unchanged | — |
| 34 | `Features/Profile/Lists/CollectionsListView.swift:120` | table `collections` | `id, name, mode, pebble_count:collection_pebbles(count)` | ok | adds `glyph_id, glyph:glyphs(id, name, strokes, view_box, user_id)` | 7 |
| 35 | `…/CollectionsListView.swift:138` | table `collections` | delete | ok | unchanged | — |
| 36 | `Features/Profile/Sheets/EditSoulSheet.swift:101` | table `souls` | update `name, glyph_id` | ok | unchanged | — |
| 37 | `Features/Profile/Sheets/EditCollectionSheet.swift:96` | table `collections` | update `name, mode` | ok | update `name, mode, glyph_id` (trigger-guarded) | 7 |
| 38 | `Features/Profile/Lists/SoulsListView.swift:123` | table `souls` | `…, pebbles_count:pebble_souls(count)` | ok | unchanged | — |
| 39 | `…/SoulsListView.swift:140` | table `souls` | delete | ok | unchanged | — |
| 40 | `Features/Profile/Components/ProfileCollectionsCard.swift:80` | table `collections` | `id, name, mode, pebble_count:…` | ok | adds the glyph columns (same `Collection` decoder as #34) | 7 |
| 41 | `Features/Profile/Views/CollectionDetailView.swift:169` | table `pebbles` | `id, name, happened_at, created_at, intensity, positiveness, render_svg, emotion:…, collection_pebbles!inner(…)` | **dropped columns** | `MomentsService.moments(inCollection:)` = `collection_pebbles.select("pebble_id").eq("collection_id")` ∩ `path_moments()` | 1 |
| 42 | `…/CollectionDetailView.swift:187` | rpc `delete_pebble` | — | semantics changed | Part 1: `delete_moment`; Part 3: two-option dialog | 1, 3 |
| 43 | `…/CollectionDetailView.swift:199` | table `collections` | `id, name, mode, pebble_count:…` | ok | adds the glyph columns | 7 |
| 44 | `Features/Profile/Views/SoulDetailView.swift:147` | table `souls` | `id, name, glyph_id, glyphs(…)` | ok | unchanged | — |
| 45 | `…/SoulDetailView.swift:168` | table `pebbles` | `… render_svg … pebble_souls!inner(soul_id)` | **dropped columns** | `MomentsService.moments(taggedWithSoul:)` = `pebble_souls.select("pebble_id").eq("soul_id")` ∩ `path_moments()` | 1 |
| 46 | `…/SoulDetailView.swift:186` | rpc `delete_pebble` | — | semantics changed | Part 1: `delete_moment`; Part 3: two-option dialog | 1, 3 |
| 47–49 | `Features/Glyph/Services/GlyphService.swift:24,45,62` | table `glyphs` | list / insert / rename | ok | unchanged | — |
| 50 | `Features/Glyph/Services/GlyphMarketService.swift:18` | table `glyphs` | — | ok | unchanged | — |
| 51 | `…/GlyphMarketService.swift:30` | table `glyph_entitlements` | — | ok | unchanged | — |
| 52 | `…/GlyphMarketService.swift:42` | view `v_glyph_market` | — | ok | unchanged | — |
| 53 | `…/GlyphMarketService.swift:56` | rpc `buy_glyph` | — | ok | unchanged | — |
| 54 | `Services/AchievementsService.swift:129` | table `achievements` | — | ok | unchanged | — |
| 55 | `Services/AchievementsService.swift:140` | table `achievement_unlocks` | — | ok | unchanged | — |
| 56 | `Services/AchievementsService.swift:151` | rpc `check_achievements` | — | ok (still counts `pebbles`) | unchanged; called once after every successful `complete_pebble` (A4) | 4, 6 |
| 57 | `Services/ReferenceDataService.swift:61` | view `v_domains_with_glyph` | `id, slug, name, label, strokes, view_box` | ok | unchanged | — |
| 58 | `…/ReferenceDataService.swift:67` | table `souls` | `id, name, glyph_id, glyphs(…)` | ok | unchanged (also the read page's soul-glyph source, R4) | — |
| 59 | `…/ReferenceDataService.swift:73` | table `collections` | `id, name` | ok | unchanged | — |
| 60 | `…/ReferenceDataService.swift:127` | table `souls` | refresh | ok | unchanged | — |
| 61 | `…/ReferenceDataService.swift:145` | table `collections` | refresh | ok | unchanged | — |
| 62–66 | `Services/PebbleDraftsService.swift:77,88,107,115,142` | table `pebble_drafts` | list / count / insert / update / delete | **dropped table** | deleted with the service | 6 |
| 67 | `Services/EmotionPaletteService.swift:37` | view `v_emotions_with_palette` | — | ok | unchanged | — |
| 68–72 | `Services/ConnectionsService.swift:32,42,51,61,70` | rpcs `get_connections`, `create_connection_invite`, `preview_connection_invite`, `accept_connection_invite`, `remove_connection` | — | ok | unchanged | — |
| 73–74 | `Services/SupabaseService.swift:231,239` | table `profiles` | display-name patch | ok | unchanged | — |

**New touchpoints, all inside `Pebbles/Services/MomentsService.swift`:** rpc `path_moments`; view `v_moments_full`; table `pebble_souls` (`pebble_id` by `soul_id`); table `collection_pebbles` (`pebble_id` by `collection_id`); rpc `resolve_pebble_glyph`; table `karma_events` (`delta` by `ref_id`, owner-readable via `karma_events_select`); rpcs `save_moment`, `complete_pebble`, `delete_pebble`, `delete_moment`. Not used by iOS: `delete_moment_media` (R6), `set_collection_glyph` (R11), `get_shared_pebble` (iOS only builds the `/p/<pebble id>` link).

Every row is mapped; none were left undecided.

**Final gate** (Part 7, also run at the end of every part to see the shrinking residue): no non-comment hit for

```bash
cd apps/ios
grep -rnE 'path_pebbles|v_pebbles_full|pebble_drafts|delete_pebble_media|delete_moment_media|compose-pebble|render_svg|render_version|"create_pebble"|"update_pebble"|can_use_glyph|from\("pebbles"\)' Pebbles PebblesTests | grep -vE '^[^:]+:[0-9]+:\s*///?'
```

(Comment-only mentions exist today in `GlyphPickerContent.swift:18`, `Render/PebbleAnimationTimings.swift:4`, `ISO8601Flexible.swift:6`, `Valence.swift:48`; the filter skips them.)

## Inventory 2 — every view that shows a pebble, and where its glyph comes from

Today the glyph reaches the screen one way: `pebbles.render_svg` (the server's composed SVG) → `StoneCarvingArt.art(for:pebbleSvg:)` parses it with `PebbleSVGModel`, wobbles the non-shape layers with `WobbleRenderer.pebbleArt`, and merges them over the seed's veins (`Features/Path/Stone/StoneCarvingArt.swift:66-87`). With `render_svg` now always null, `StoneView` falls back to the **sample** glyph (`stone-glyph.json`) on the Path and the read page shows **no stone at all** (`PebbleReadPetroglyph` renders nothing when `renderSvg == nil`).

| Surface | View chain | Glyph today | Glyph after | Part |
|---|---|---|---|---|
| Path rows | `PathView` → `WeekPathView` → `PathPebbleRow` → `PebbleStoneView(renderSvg:)` → `StoneView(pebbleSvg:)` | `render_svg` carving (now: sample glyph) | `.pebble(moment.pebble.glyph)`; uncompleted moment → `.blank` in `StonePalette.blank` | 2 |
| Read page heading | `PebbleDetailSheet` → `PebbleReadView` → `PebbleReadBanner` → `PebbleReadPetroglyph` → `PebbleStoneView(followsTilt: true)` | `render_svg` (now: nothing) | `.pebble(detail.pebble.glyph)`; bare moment → blank stone | 2, 3 |
| Read page over a photo | `PebbleReadBanner` → `PebbleSnapFrame` → `PebbleReadPetroglyph` | same | same | 2, 3 |
| Soul detail list | `SoulDetailView` → `PebbleRow` (Components) → `PebbleStoneView` 40 pt, grey square when `renderSvg == nil` | `render_svg` | `.pebble(glyph)`, no grey square | 2 |
| Collection detail list | `CollectionDetailView` → `PebbleRow` | `render_svg` | same | 2 |
| Record success | `RecordSuccessStep` → `PebbleReadPetroglyph` | compose response `render_svg` (now null: no artwork) | `CompleteSuccessStep` → `PebbleStoneView(.pebble(resolve_pebble_glyph(pebbleId)))` | 4 |
| Edit form header | `EditPebbleSheet` → `PebbleFormView` → `PebbleRenderView(svg:)` (SVGView) | `render_svg` | removed (no render to show) | 1 |
| Drafts list | `DraftsListSheet` rows | none (drafts had no render) | deleted | 6 |
| Valence picker / Stone lab | `ValenceStoneView`, `StoneLabView` → `StoneView` | sample glyph | unchanged (`.sample`) | — |
| Profile, collection cards, souls, share, widget | no stone drawn (`ShareLink` sends the URL only; the widget shows karma) | — | — | — |

Old SVG render stack (`Features/Path/Render/PebbleAnimatedRenderView`, `PebbleStaticRenderView`, `PebbleSVGModel`, `PebbleRenderView`): after Part 2 nothing on a pebble surface reaches it. Removing it is out of scope (spec §9, "cleanup of the old iOS renderers"); leave it compiled.

---
## Parity decisions (reconciled with the Android plan #1048)

R1–R13 below are pasted verbatim from the controller's reconciliation (`scratchpad/parity-reconciliation.md`, 2026-10-13), which both plans adopt line for line. Where they are silent, the A-items that follow keep the decisions the two plans already shared. The copy catalog lists every new string iOS adds. Task text cites these ids.

R1. **Capture name step copy.** Primary "Save moment" / "Garder le moment"
    (`capture_save`). Secondary "Make it a pebble now" / "En faire un galet
    maintenant" (`capture_make_pebble_now`). Subtitle "A name, a photo, or both." /
    "Un nom, une photo, ou les deux." (`capture_name_subtitle`).

R2. **Capture close.** ✕ (and Android back on the photo step) with nothing
    entered closes silently. Otherwise one dialog: title "Keep this moment?" /
    "Tu gardes ce moment ?" (`capture_close_title`), actions **Save moment** (only
    when savable and the photo is not uploading/failed), **Discard** (destructive,
    deletes the uploaded photo files), **Keep going**. (iOS P1 wins.)

R3. **Untitled.** "Untitled" / "Sans titre" (`moment_untitled`), rendered italic
    in the secondary text colour, everywhere a null name shows: Path row,
    soul/collection rows, read page title, delete dialog, success screen.

R4. **Blank stone.** Medium size, neutral silhouette, outline stroke only:
    nothing carved inside (no glyph, no veins, no fossil, no shape detail strokes).
    Colours fixed, not theme roles: the web `BLANK_PALETTE` ramp (primary
    `#868686`, secondary `#B9B9B9`, light `#F3F3F3`, shaded `#5E5E5E`, dark
    `#1B1B1B`, surface `#8686861A`). Accessibility label "Not a pebble yet" /
    "Pas encore un galet". The photo shows as on a completed row. Android Path row:
    intensity-2 metrics, meta line weekday · time (iOS matches).

R5. **Editing is two screens, one write each.** **Edit moment** (when, name
    clamped to 40, description, photo) → `save_moment` only. **Edit pebble**
    (emotion, domain, valence, souls, collection, privacy) → `complete_pebble` only.
    Never two writes behind one Save. (iOS P9 wins over Android PD-14.)

R6. **Photo removal goes through `save_moment`.** In Edit moment, removing the
    photo detaches it in the form; Save sends the whole `snaps` set (`[]` when
    removed), so the server enforces "a name or a photo" (`moment_empty`) in the
    same write. Old files are deleted best-effort after a successful save. Neither
    app calls `delete_moment_media`. Save is enabled when the trimmed name is
    non-empty or a photo is attached; hint `edit_moment_needs_content` "A moment
    needs a name or a photo." / "Un moment a besoin d'un nom ou d'une photo."
    (Android PD-15 wins over iOS's eager delete.)

R7. **Read page actions.** A toolbar overflow menu on both (iOS `…`, Android ⋮):
    **Edit moment**, **Edit pebble** (only when your pebble exists), **Delete**.
    No standalone trash button. Share stays as today, public pebbles only, URL with
    the pebble id.

R8. **Delete dialog.** Bare moment: title "Delete this moment?" / "Supprimer ce
    moment ?", message "This can't be undone.", action **Delete moment** →
    `delete_moment`. Moment with your pebble: title "Delete this pebble?" /
    "Supprimer ce galet ?", message `delete_pebble_message` (iOS text), actions
    **Delete the pebble, keep the moment** → `delete_pebble` and **Delete the
    moment too** → `delete_moment`, plus Cancel. Entry points: Path row long-press,
    soul/collection detail row long-press, read page overflow **Delete**. After
    `delete_moment`: remove returned storage paths best-effort, close the read page;
    after `delete_pebble`: the read page stays as a bare moment, lists drop the row.
    (iOS P5 copy wins.)

R9. **Soul and collection detail rows open the moment read page** (iOS P11), not
    the edit form. Which query feeds the list is each platform's choice; the
    behaviour is: moments carrying your pebble tagged with that soul/collection,
    newest `happened_at` first, drawn like Path rows.

R10. **Error copy** is iOS P3's table, verbatim (EN/FR per slug, generic
    "Couldn't save. Please try again." / "Impossible d'enregistrer. Réessaie."
    for everything else). Android maps P0002 → `moment_not_found`. Existing
    image-pipeline and sign-in copy is kept. Delete failures keep "Couldn't delete".

R11. **Collection glyph.** Edit **and** create forms get the glyph row (shared
    picker, **Remove glyph**), written as `glyph_id` in the same insert/update as
    name and mode; `set_collection_glyph` is not called. Footer "Pebbles in this
    collection carry its glyph." / "Les galets de cette collection portent son
    glyphe." (Android PD-19 scope, iOS P10 copy.)

R12. **Success screen.** Name or "Untitled", stone with the derived glyph (bare
    stone when none), "+N karma" only when N > 0, "Back to my path". iOS keeps its
    draw-on; Android is static (accepted capability gap, not a parity bug).

R13. **Karma read after a write.** Either method is fine (iOS sum-after minus
    sum-before; Android newest event row for the reason), as long as the shown N is
    the delta of that write and absence/error shows nothing.

### Already aligned (kept from the iOS plan; the reconciliation's "unchanged" list)

**A1 — Steps and advancing.** Capture: `photo · when · name` (3 dots, no success screen). The photo is optional (`Skip`/`Done`, no auto-advance on pick). When is seeded from the photo's EXIF, else now, with `Continue`. The name is clamped to 40 characters and optional when a photo is attached. **Save moment** closes the cover and the Path focuses the moment's week. **Make it a pebble now** saves, then swaps the same cover to Complete. Complete: `valence · emotion · domain · souls · collection · privacy` (6 dots), then the success screen.
- **Valence** arrives on neutral-medium, commits in place, and advances on `Continue`.
- **Emotion, domain and collection** commit and advance on tap.
- **Domain** is single-select.
- **Collection** is single-select and optional, with no inline creation.
- **Souls** is multi-select and optional, with the existing create tile.
- **Privacy** selects without advancing, defaults to `secret`, and its button is **Make it a pebble**.
- **Entry** is the first unanswered mandatory step of the restored draft.
- Complete's ✕ never asks: it flushes the autosave and closes.

There is no glyph step anywhere. Haptics follow M58: `selection` on a tile or soul toggle, `advance` on a step change, `success` on a successful write, `warning` on a blocked advance, a failed save, or a delete confirmation.

**A2 — Payloads.**
- **Format:** lowercase UUID strings and whole-second UTC timestamps (`yyyy-MM-dd'T'HH:mm:ss'Z'`). Absent keys are omitted, never sent as null.
- **`save_moment` create:** no `id`; `name` only when non-blank; no `description`; `happened_at` always; `snaps` only with a photo (`[{id, storage_path: "{user}/{snap}", sort_order: 0}]`).
- **`save_moment` edit:** `id`, `name`, `description`, `happened_at` and the whole `snaps` set, always. Blanks are sent as `""` to clear (null means keep), and `snaps: []` removes the photo (R6).
- **`complete_pebble`:** always `moment_id`, `emotion_id`, `intensity`, `positiveness`, `visibility`, `domain_ids: [one]`, `soul_ids` and `collection_ids: [] | [one]`. Never `new_souls`, `new_collections` or `cards`.
- **Failed saves** keep the uploaded photo so the user can retry. Only Discard deletes its files.

**A3 — Autosave.** Device-local, per moment, Complete flow only.
- **Timing:** debounced 800 ms; flushed on background and on ✕.
- **Restore:** silent and sanitized, once reference data has loaded, landing on the first gap.
- **Clearing:** on a successful completion, on `delete_moment`, and on any signed-out state. A file owned by another account is deleted on read.
- **iOS storage:** `Caches/complete-drafts/<moment id lowercase>.json`, with `complete_pebble`'s key names plus `owner_id` and `moment_id`. The storage format is platform-native.

**A4 — Achievements.** `check_achievements` runs once after every successful `complete_pebble` (completion and Edit pebble), after the karma read, and never otherwise. Completion suppresses the karma capsule (the success screen shows `+N`). Edit pebble shows the capsule with reason `pebbleEnriched`. The success stone carves `resolve_pebble_glyph(pebble_id)`, fetched after the write.

**A5 — Path.** "Active today" counts only completed moments created today. After a capture or a completion, the Path focuses the moment's week. Share URLs use the pebble id.

**A6 — iOS implementation notes (no parity weight).**
- **Soul glyphs on the read page** come from `ReferenceDataService.souls`, because `v_moments_full` souls are `{id, name}`.
- **Soul and collection lists** are `pebble_souls` / `collection_pebbles` ids intersected with `path_moments`.
- **Missing glyph:** a completed pebble with a null glyph draws veins and fossil with no glyph (R12's "bare stone when none"), never the sample glyph.
- **Edit pebble** keeps the first domain and the first collection, as before the split.

### Copy catalog (iOS adds these to `Localizable.xcstrings`, en + fr)

| Id | EN | FR | From |
|---|---|---|---|
| `moment_untitled` | Untitled | Sans titre | R3 |
| `blank_stone_a11y` | Not a pebble yet | Pas encore un galet | R4 |
| `capture_name_subtitle` | A name, a photo, or both. | Un nom, une photo, ou les deux. | R1 |
| `capture_save` | Save moment | Garder le moment | R1, R2 |
| `capture_make_pebble_now` | Make it a pebble now | En faire un galet maintenant | R1 |
| `capture_close_title` | Keep this moment? | Tu gardes ce moment ? | R2 |
| `complete_cta` | Make it a pebble | En faire un galet | A1 |
| `delete_pebble_title` | Delete this pebble? | Supprimer ce galet ? | R8 |
| `delete_moment_title` | Delete this moment? | Supprimer ce moment ? | R8 |
| `delete_pebble_keep_moment` | Delete the pebble, keep the moment | Supprimer le galet, garder le moment | R8 |
| `delete_moment_too` | Delete the moment too | Supprimer aussi le moment | R8 |
| `delete_moment` | Delete moment | Supprimer le moment | R8 |
| `delete_pebble_message` | Keeping the moment leaves it on your path as a blank stone. Deleting the moment removes its photo too. This can't be undone. | Garder le moment le laisse sur ton chemin comme une pierre vierge. Supprimer le moment retire aussi sa photo. Cette action est irréversible. | R8 |
| `edit_moment` | Edit moment | Modifier le moment | R5, R7 |
| `edit_moment_needs_content` | A moment needs a name or a photo. | Un moment a besoin d'un nom ou d'une photo. | R6 |
| `collection_glyph_footer` | Pebbles in this collection carry its glyph. | Les galets de cette collection portent son glyphe. | R11 |
| `remove_glyph` | Remove glyph | Retirer le glyphe | R11 |
| `more` (overflow a11y) | More | Plus | R7 |

The error lines (R10, with EN/FR per slug):

| Slug | EN | FR |
|---|---|---|
| `moment_empty` | Add a name or a photo first. | Ajoute d'abord un nom ou une photo. |
| `media_quota_exceeded` | Photo limit reached on this moment. | Limite de photos atteinte pour ce moment. |
| `pebble_needs_emotion` | Pick an emotion first. | Choisis d'abord une émotion. |
| `pebble_needs_valence` | Pick how it landed first. | Choisis d'abord ce que ça t'a fait. |
| `pebble_needs_domain` | Pick a part of life first. | Choisis d'abord un domaine de ta vie. |
| `soul_not_owned` | One of those souls is gone. Remove it and try again. | Une de ces âmes n'existe plus. Retire-la et réessaie. |
| `collection_not_owned` | That collection is gone. Pick another one. | Cette collection n'existe plus. Choisis-en une autre. |
| `moment_not_found` | This moment no longer exists. | Ce moment n'existe plus. |
| `not_authenticated` | You must be signed in to save. | Tu dois être connecté pour enregistrer. |
| `invalid_payload`, `invalid_snap`, anything else | Couldn't save. Please try again. | Impossible d'enregistrer. Réessaie. |

Reused existing keys: the old flow's step titles and subtitles, `Skip`, `Done`, `Continue`, `Discard`, `Keep going`, `Back to my path`, `+%lld karma`, `Edit pebble`, `Delete`, `Cancel`, `Couldn't delete`, `This can't be undone.`, `Who can see it?` and its three explanations.

---

## Part 1 — data layer and reads (`feat/1047-ios-moments-data`)

**Outcome:** every read the app makes is on the new contract, and every delete uses the new writers. `MomentsService` exists with all reads and writes, tested at the decoding/encoding boundary against verbatim payloads. The old composers are untouched and still publish through compat.

**Files**
- Create: `Pebbles/Features/Path/Models/ReferenceRefs.swift`, `FlexibleDateDecoding.swift`, `MomentName.swift`, `PebbleGlyph.swift`, `MomentSummary.swift`, `MomentDetail.swift`, `SaveMomentPayload.swift`, `CompletePebblePayload.swift` (all in `Pebbles/Features/Path/Models/`); `Pebbles/Services/MomentsService.swift`, `Pebbles/Services/MomentWriteError.swift`, `Pebbles/Services/MomentDeletion.swift`
- Create tests: `PebblesTests/Moments/MomentFixtures.swift`, `PebbleGlyphTests.swift`, `MomentSummaryDecodingTests.swift`, `MomentDetailDecodingTests.swift`, `SaveMomentPayloadEncodingTests.swift`, `CompletePebblePayloadEncodingTests.swift`, `MomentWriteErrorTests.swift`, `MomentsServiceTests.swift`, `PebbleDraftFromMomentDetailTests.swift`
- Move: `Pebbles/Features/Path/Models/PebbleDetail+Localized.swift` → `ReferenceRefs+Localized.swift`
- Modify: `Features/Path/Models/Valence.swift`, `Features/PebbleMedia/PebbleSnapRepository.swift`, `Features/PebbleMedia/SnapUploadCoordinator.swift`, `Features/PebbleMedia/ExistingSnapRow.swift` (comment), `Features/PebbleMedia/Models/FormSnap.swift` (comment), `Features/Path/PebbleFormView.swift` (comment), `Features/Path/Models/PebbleUpdatePayload.swift` (comment), `Features/Path/PathView.swift`, `Features/Path/Services/WeekRollBuilder.swift`, `Features/Path/Models/WeekRollEntry.swift`, `Features/Path/Components/WeekPathView.swift`, `Features/Path/Components/PathPebbleRow.swift`, `Features/Path/Components/WeekRollCairnCell.swift`, `Features/Path/GroupPebblesByISOWeek.swift`, `Features/Profile/Views/GroupPebblesByMonth.swift`, `Components/PebbleRow.swift`, `Features/Path/PebbleDetailSheet.swift`, `Features/Path/Read/PebbleReadView.swift`, `Features/Path/Read/PebbleReadBanner.swift`, `Features/Path/Read/PebbleSnapFrame.swift` (previews only), `Features/Path/EditPebbleSheet.swift`, `Features/Path/Models/PebbleDraft.swift`, `Features/Profile/Views/SoulDetailView.swift`, `Features/Profile/Views/CollectionDetailView.swift`, `Resources/Localizable.xcstrings`
- Modify tests: `PebblesTests/SnapUploadCoordinatorTests.swift`, `WeekRollBuilderTests.swift`, `GroupPebblesByISOWeekTests.swift`, `GroupPebblesByMonthTests.swift`
- Delete: `Features/Path/Models/Pebble.swift`, `Features/Path/Models/PebbleDetail.swift`; tests `PebbleDecodingTests.swift`, `PebbleDetailDecodingTests.swift`, `PebbleDetailSoulGlyphDecodingTests.swift`, `PebbleDraftFromDetailTests.swift`

### Task 1.0: Branch

- [ ] **Step 1:** `git fetch origin && gh stack init` is not needed for the first layer; create the branch from fresh `main`:

```bash
git checkout -b feat/1047-ios-moments-data origin/main
cp /Users/alexis/code/pbbls/apps/ios/Config/Secrets.xcconfig apps/ios/Config/   # if in a worktree
```

- [ ] **Step 2:** Move the Arkaik acceptances this stack covers to `development` (see Part 7, Task 7.4 for the node list) with `mcp__arkaik-mcp__update_node`. If the `arkaik-mcp` tools are not available, say so and stop; never touch `docs/arkaik/bundle.json`.

### Task 1.1: Move `EmotionRef` / `DomainRef` out of `PebbleDetail.swift` (pure move)

`PebbleDetail.swift` is deleted in Task 1.11, but its two ref types are used across the app.

**Files:** Create `Pebbles/Features/Path/Models/ReferenceRefs.swift`; modify `PebbleDetail.swift`; move `PebbleDetail+Localized.swift`.

- [ ] **Step 1:** Create `ReferenceRefs.swift` with the two structs cut verbatim from `PebbleDetail.swift:3-28` (their doc comment included):

```swift
import Foundation

// `EmotionRef` and `DomainRef` are the narrow shapes the read models embed:
// `{id, slug, name}`. Extra keys (`color`, `label`) are ignored, so the same
// type decodes `path_moments`, `v_moments_full` and PostgREST embeds. Colour
// comes from the palette service (`EmotionPaletteService.palette(for:)`).

struct EmotionRef: Decodable, Hashable, Identifiable {
    let id: UUID
    let slug: String
    let name: String
}

struct DomainRef: Decodable, Hashable, Identifiable {
    let id: UUID
    let slug: String
    let name: String
}
```

- [ ] **Step 2:** Delete those two structs and their comment block from `PebbleDetail.swift`.
- [ ] **Step 3:** `git mv apps/ios/Pebbles/Features/Path/Models/PebbleDetail+Localized.swift apps/ios/Pebbles/Features/Path/Models/ReferenceRefs+Localized.swift` (content unchanged).
- [ ] **Step 4:** Build & test. Expected: green, 518 tests.
- [ ] **Step 5:** Commit: `git commit -m "quality(ios): move the emotion and domain refs beside their localization"`

### Task 1.2: Tolerant dates, `Valence.resolved`, `MomentName`, `PebbleGlyph`

**Files:** Create `FlexibleDateDecoding.swift`, `MomentName.swift`, `PebbleGlyph.swift`; modify `Valence.swift`; create `PebblesTests/Moments/PebbleGlyphTests.swift`.

- [ ] **Step 1: Write the failing test** `PebblesTests/Moments/PebbleGlyphTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("PebbleGlyph + MomentName")
struct PebbleGlyphTests {

    @Test("decodes the resolver's shape, source included")
    func decodesResolverShape() throws {
        let json = Data("""
        {"id":"4759c37c-68a6-46a6-b4fc-046bd0316752","name":null,
         "strokes":[{"d":"M 40,40 L 160,160","width":6}],
         "view_box":"0 0 200 200","source":"collection"}
        """.utf8)
        let glyph = try JSONDecoder().decode(PebbleGlyph.self, from: json)
        #expect(glyph.id == UUID(uuidString: "4759c37c-68a6-46a6-b4fc-046bd0316752"))
        #expect(glyph.name == nil)
        #expect(glyph.strokes == [GlyphStroke(d: "M 40,40 L 160,160", width: 6)])
        #expect(glyph.source == .collection)
        #expect(glyph.viewBoxSide == 200)
    }

    @Test("an unknown source decodes to nil instead of failing the row")
    func unknownSource() throws {
        let json = Data("""
        {"id":"4759c37c-68a6-46a6-b4fc-046bd0316752","name":"x","strokes":[],"view_box":"0 0 200 200","source":"profile"}
        """.utf8)
        #expect(try JSONDecoder().decode(PebbleGlyph.self, from: json).source == nil)
    }

    @Test("view_box side: imported sizes, commas, garbage falls back to 200")
    func viewBoxSide() {
        func side(_ box: String) -> Double {
            PebbleGlyph(id: UUID(), name: nil, strokes: [], viewBox: box, source: nil).viewBoxSide
        }
        #expect(side("0 0 100 100") == 100)
        #expect(side("0,0,240,240") == 240)
        #expect(side("nonsense") == 200)
        #expect(side("0 0 0 0") == 200)
    }

    @Test("display names: trimmed, blank is nil")
    func momentName() {
        #expect(MomentName.display(nil) == nil)
        #expect(MomentName.display("   ") == nil)
        #expect(MomentName.display("  Coffee \n") == "Coffee")
    }

    @Test("resolved valence falls back to neutral-medium on drift")
    func resolvedValence() {
        #expect(Valence.resolved(positiveness: 1, intensity: 3) == .highlightLarge)
        #expect(Valence.resolved(positiveness: 7, intensity: 0) == .neutralMedium)
    }
}
```

- [ ] **Step 2: Implement `FlexibleDateDecoding.swift`:**

```swift
import Foundation

/// Timestamps from PostgREST arrive at whatever precision the column holds
/// (`…T08:30:00+00:00`, `…T08:31:12.517203+00:00`, `….9+00:00`), and moments
/// backfilled from pre-split pebbles carry fractional `happened_at`. Decoding
/// them as strings through `ISO8601Flexible` keeps the moment models
/// independent of whatever date strategy the ambient decoder carries
/// (standing cross-surface rule).
extension KeyedDecodingContainer {
    func decodeFlexibleDate(forKey key: Key) throws -> Date {
        let raw = try decode(String.self, forKey: key)
        guard let date = ISO8601Flexible.parse(raw) else {
            throw DecodingError.dataCorruptedError(
                forKey: key, in: self, debugDescription: "Not ISO-8601: \(raw)"
            )
        }
        return date
    }

    func decodeFlexibleDateIfPresent(forKey key: Key) throws -> Date? {
        guard let raw = try decodeIfPresent(String.self, forKey: key) else { return nil }
        guard let date = ISO8601Flexible.parse(raw) else {
            throw DecodingError.dataCorruptedError(
                forKey: key, in: self, debugDescription: "Not ISO-8601: \(raw)"
            )
        }
        return date
    }
}
```

- [ ] **Step 3: Implement `MomentName.swift`:**

```swift
import Foundation

/// A moment's name is optional since the split (spec D3: a name *or* a photo).
/// One rule for every surface that shows or sends it.
enum MomentName {
    /// The name to display or send, or nil when there is none worth showing.
    static func display(_ raw: String?) -> String? {
        guard let trimmed = raw?.trimmingCharacters(in: .whitespacesAndNewlines),
              !trimmed.isEmpty else { return nil }
        return trimmed
    }
}
```

- [ ] **Step 4: Add `Valence.resolved` to `Valence.swift`** (below `from(positiveness:intensity:)`, inside the same `extension Valence` or a new one; add `import os` at the top if missing):

```swift
extension Valence {
    /// `from(positiveness:intensity:)` for read paths that must render
    /// something: logs and falls back to `.neutralMedium` on a pair the DB
    /// CHECKs forbid (reachable only through decode drift).
    static func resolved(positiveness: Int, intensity: Int) -> Valence {
        if let valence = from(positiveness: positiveness, intensity: intensity) { return valence }
        Logger(subsystem: "app.pbbls.ios", category: "valence").error(
            """
            unexpected (positiveness, intensity) pair: \
            (\(positiveness, privacy: .public), \(intensity, privacy: .public)) — neutralMedium
            """
        )
        return .neutralMedium
    }
}
```

- [ ] **Step 5: Implement `PebbleGlyph.swift`:**

```swift
import Foundation

/// A pebble's derived glyph (spec D5) as every reader returns it:
/// `{id, name, strokes, view_box, source}`. Pebbles no longer own a glyph or
/// a server render (D6); the client carves these strokes into the stone.
struct PebbleGlyph: Decodable, Hashable, Identifiable {
    enum Source: String, Hashable {
        case collection
        case domain
    }

    let id: UUID
    let name: String?
    let strokes: [GlyphStroke]
    let viewBox: String
    /// Nil when the server sends a source this build does not know.
    let source: Source?

    private enum CodingKeys: String, CodingKey {
        case id, name, strokes, source
        case viewBox = "view_box"
    }

    init(id: UUID, name: String?, strokes: [GlyphStroke], viewBox: String, source: Source?) {
        self.id = id
        self.name = name
        self.strokes = strokes
        self.viewBox = viewBox
        self.source = source
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(UUID.self, forKey: .id)
        name = try container.decodeIfPresent(String.self, forKey: .name)
        strokes = try container.decodeIfPresent([GlyphStroke].self, forKey: .strokes) ?? []
        viewBox = try container.decodeIfPresent(String.self, forKey: .viewBox) ?? "0 0 200 200"
        source = try container.decodeIfPresent(String.self, forKey: .source).flatMap(Source.init(rawValue:))
    }

    /// The square side the strokes are drawn in: the third number of
    /// `view_box`. iOS carves at 200; imported web glyphs may differ.
    var viewBoxSide: Double {
        let numbers = viewBox
            .split(whereSeparator: { $0 == " " || $0 == "," })
            .compactMap { Double($0) }
        guard numbers.count == 4, numbers[2] > 0 else { return 200 }
        return numbers[2]
    }
}
```

`GlyphStroke` is `Codable, Hashable` with a memberwise `init(d:width:)` (`Features/Glyph/Models/GlyphStroke.swift`).

- [ ] **Step 6:** Build & test (`-only-testing:PebblesTests/PebbleGlyphTests` is enough here). Expected: PASS.
- [ ] **Step 7:** Commit: `git commit -m "feat(ios): decode the derived pebble glyph and tolerant moment dates"`

### Task 1.3: Verbatim fixtures and `MomentSummary` (the `path_moments` row)

**Files:** Create `PebblesTests/Moments/MomentFixtures.swift`, `PebblesTests/Moments/MomentSummaryDecodingTests.swift`, `Pebbles/Features/Path/Models/MomentSummary.swift`.

The fixtures are written from the SQL bodies (`20261012090200_moments_split_readers.sql`), with the exact text PostgREST emits: `timestamptz` as `…+00:00` with trailing fractional zeros trimmed (`.004`, `.9`, `.517203`), explicit `null`s, lowercase UUIDs. They cover a completed moment, a photo-only bare moment (`name: null`), a named bare moment with a fractional legacy `happened_at`, and a legacy pebble whose id equals its moment id (D9) with `emotion: null` and `glyph: null`. **Before merging Part 1**, run the app against the linked project on the maintainer's test account, log one raw `path_moments` and one `v_moments_full` body (`String(data: response.data, encoding: .utf8)` at `.debug`), and diff them against these fixtures. Fix the fixture (never the decoder's tolerance) if a key or precision differs.

- [ ] **Step 1: Create `MomentFixtures.swift`:**

```swift
import Foundation

/// Verbatim payloads from the moments split readers (#1047). Shared by every
/// decoder suite so a contract change is edited in one place. The Android
/// plan (#1048) decodes the same strings.
enum MomentFixtures {

    static let ownerId = "5f0e1d2c-3b4a-4958-8776-655443322110"
    static let joyId = "22222222-2222-4222-8222-222222222222"
    static let domainId = "33333333-3333-4333-8333-333333333333"
    static let soulId = "44444444-4444-4444-8444-444444444444"
    static let collectionId = "55555555-5555-4555-8555-555555555555"
    static let glyphId = "4759c37c-68a6-46a6-b4fc-046bd0316752"

    /// `rpc("path_moments")` — four rows, newest first.
    static let pathMoments = Data("""
    [
      {
        "id": "0b7c3f5e-2d41-4a8e-9f6b-1c2d3e4f5a6b",
        "name": "Coffee with Ana",
        "happened_at": "2026-10-12T08:30:00+00:00",
        "created_at": "2026-10-12T08:31:12.517203+00:00",
        "first_snap_path": "5f0e1d2c-3b4a-4958-8776-655443322110/a1b2c3d4-e5f6-4789-8abc-def012345678",
        "pebble": {
          "id": "7e6d5c4b-3a29-4817-8f6e-5d4c3b2a1908",
          "intensity": 2,
          "positiveness": 1,
          "visibility": "private",
          "emotion": {"id": "22222222-2222-4222-8222-222222222222", "slug": "joy", "name": "Joy"},
          "glyph": {
            "id": "4759c37c-68a6-46a6-b4fc-046bd0316752",
            "name": null,
            "strokes": [{"d": "M 40,40 L 160,160", "width": 6}, {"d": "M 160,40 Q 100,100 40,160", "width": 6}],
            "view_box": "0 0 200 200",
            "source": "domain"
          }
        }
      },
      {
        "id": "1c8d4a6f-3e52-4b9f-a07c-2d3e4f5a6b7c",
        "name": null,
        "happened_at": "2026-10-11T19:04:51+00:00",
        "created_at": "2026-10-11T19:05:02.004+00:00",
        "first_snap_path": "5f0e1d2c-3b4a-4958-8776-655443322110/b2c3d4e5-f6a7-4890-9bcd-ef0123456789",
        "pebble": null
      },
      {
        "id": "2d9e5b70-4f63-4ca0-b18d-3e4f5a6b7c8d",
        "name": "Quiet evening",
        "happened_at": "2026-09-30T21:15:07.123456+00:00",
        "created_at": "2026-09-30T21:15:07.123456+00:00",
        "first_snap_path": null,
        "pebble": null
      },
      {
        "id": "3eaf6c81-5074-4db1-82e9-4f5a6b7c8d9e",
        "name": "Shipped the thing",
        "happened_at": "2026-09-29T16:00:00+00:00",
        "created_at": "2026-09-29T16:02:44.9+00:00",
        "first_snap_path": null,
        "pebble": {
          "id": "3eaf6c81-5074-4db1-82e9-4f5a6b7c8d9e",
          "intensity": 3,
          "positiveness": -1,
          "visibility": "secret",
          "emotion": null,
          "glyph": null
        }
      }
    ]
    """.utf8)

    /// `from("v_moments_full").select(MomentsService.detailColumns).eq("id", …).single()`
    /// for the owner's completed moment: every relation populated, a glyph
    /// inherited from a collection, one card (ignored by iOS).
    static let completedMoment = Data("""
    {
      "id": "0b7c3f5e-2d41-4a8e-9f6b-1c2d3e4f5a6b",
      "user_id": "5f0e1d2c-3b4a-4958-8776-655443322110",
      "name": "Coffee with Ana",
      "description": "First rain of autumn.",
      "happened_at": "2026-10-12T08:30:00+00:00",
      "hidden_at": null,
      "created_at": "2026-10-12T08:31:12.517203+00:00",
      "updated_at": "2026-10-12T09:02:40.004+00:00",
      "snaps": [
        {"id": "a1b2c3d4-e5f6-4789-8abc-def012345678", "storage_path": "5f0e1d2c-3b4a-4958-8776-655443322110/a1b2c3d4-e5f6-4789-8abc-def012345678", "sort_order": 0}
      ],
      "pebble": {
        "id": "7e6d5c4b-3a29-4817-8f6e-5d4c3b2a1908",
        "emotion_id": "22222222-2222-4222-8222-222222222222",
        "intensity": 2,
        "positiveness": 1,
        "visibility": "private",
        "created_at": "2026-10-12T08:40:03.88+00:00",
        "updated_at": "2026-10-12T09:02:40.004+00:00",
        "emotion": {"id": "22222222-2222-4222-8222-222222222222", "slug": "joy", "name": "Joy", "color": "#FFD166"},
        "glyph": {
          "id": "66666666-6666-4666-8666-666666666666",
          "name": "Autumn",
          "strokes": [{"d": "M 20,100 Q 100,20 180,100", "width": 6}],
          "view_box": "0 0 200 200",
          "source": "collection"
        },
        "cards": [
          {"id": "77777777-7777-4777-8777-777777777777", "species_id": "88888888-8888-4888-8888-888888888888", "value": "It rained.", "sort_order": 0}
        ],
        "souls": [{"id": "44444444-4444-4444-8444-444444444444", "name": "Ana"}],
        "domains": [{"id": "33333333-3333-4333-8333-333333333333", "slug": "zoe", "name": "Work", "label": "Your job and your craft"}],
        "collections": [
          {"id": "55555555-5555-4555-8555-555555555555", "name": "Autumn", "glyph_id": "66666666-6666-4666-8666-666666666666"},
          {"id": "99999999-9999-4999-8999-999999999999", "name": "Wins", "glyph_id": null}
        ]
      }
    }
    """.utf8)

    /// The owner's photo-only moment, not completed: explicit null name and
    /// description, one snap, null pebble.
    static let photoOnlyMoment = Data("""
    {
      "id": "1c8d4a6f-3e52-4b9f-a07c-2d3e4f5a6b7c",
      "user_id": "5f0e1d2c-3b4a-4958-8776-655443322110",
      "name": null,
      "description": null,
      "happened_at": "2026-10-11T19:04:51+00:00",
      "hidden_at": null,
      "created_at": "2026-10-11T19:05:02.004+00:00",
      "updated_at": "2026-10-11T19:05:02.004+00:00",
      "snaps": [
        {"id": "b2c3d4e5-f6a7-4890-9bcd-ef0123456789", "storage_path": "5f0e1d2c-3b4a-4958-8776-655443322110/b2c3d4e5-f6a7-4890-9bcd-ef0123456789", "sort_order": 0}
      ],
      "pebble": null
    }
    """.utf8)

    /// A connection's public moment seen by another user: `pebble` is null
    /// (only the caller's own pebble nests) and `snaps` is empty (owner-only).
    static let connectionMoment = Data("""
    {
      "id": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
      "user_id": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
      "name": "Ana's birthday",
      "description": null,
      "happened_at": "2026-10-03T18:00:00+00:00",
      "hidden_at": null,
      "created_at": "2026-10-03T18:20:11.25+00:00",
      "updated_at": "2026-10-03T18:20:11.25+00:00",
      "snaps": [],
      "pebble": null
    }
    """.utf8)

    /// A pre-split pebble: moment id = pebble id (D9), fractional
    /// happened_at, no domain, no glyph, empty relations.
    static let legacyMoment = Data("""
    {
      "id": "3eaf6c81-5074-4db1-82e9-4f5a6b7c8d9e",
      "user_id": "5f0e1d2c-3b4a-4958-8776-655443322110",
      "name": "Shipped the thing",
      "description": "",
      "happened_at": "2026-09-29T16:00:00.371+00:00",
      "hidden_at": null,
      "created_at": "2026-09-29T16:02:44.9+00:00",
      "updated_at": "2026-10-12T09:00:00+00:00",
      "snaps": [],
      "pebble": {
        "id": "3eaf6c81-5074-4db1-82e9-4f5a6b7c8d9e",
        "emotion_id": "22222222-2222-4222-8222-222222222222",
        "intensity": 3,
        "positiveness": -1,
        "visibility": "secret",
        "created_at": "2026-09-29T16:02:44.9+00:00",
        "updated_at": "2026-09-29T16:02:44.9+00:00",
        "emotion": {"id": "22222222-2222-4222-8222-222222222222", "slug": "joy", "name": "Joy", "color": "#FFD166"},
        "glyph": null,
        "cards": [],
        "souls": [],
        "domains": [],
        "collections": []
      }
    }
    """.utf8)

    static func uuid(_ string: String) -> UUID { UUID(uuidString: string)! }

    static func date(_ iso: String) -> Date { ISO8601Flexible.parse(iso)! }
}
```

- [ ] **Step 2: Write the failing test** `MomentSummaryDecodingTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("MomentSummary decoding (path_moments)")
struct MomentSummaryDecodingTests {

    private func rows(_ decoder: JSONDecoder = JSONDecoder()) throws -> [MomentSummary] {
        try decoder.decode([MomentSummary].self, from: MomentFixtures.pathMoments)
    }

    @Test("a completed moment carries its pebble, emotion and glyph")
    func completed() throws {
        let row = try rows()[0]
        #expect(row.name == "Coffee with Ana")
        #expect(row.isCompleted)
        #expect(row.happenedAt == MomentFixtures.date("2026-10-12T08:30:00Z"))
        let pebble = try #require(row.pebble)
        #expect(pebble.id == MomentFixtures.uuid("7e6d5c4b-3a29-4817-8f6e-5d4c3b2a1908"))
        #expect(pebble.id != row.id)
        #expect(pebble.valence == .highlightMedium)
        #expect(pebble.visibility == .private)
        #expect(pebble.emotion?.slug == "joy")
        #expect(pebble.glyph?.strokes.count == 2)
        #expect(pebble.glyph?.source == .domain)
    }

    @Test("a photo-only bare moment: explicit null name, null pebble")
    func photoOnly() throws {
        let row = try rows()[1]
        #expect(row.name == nil)
        #expect(row.pebble == nil)
        #expect(!row.isCompleted)
        #expect(row.firstSnapPath?.hasSuffix("b2c3d4e5-f6a7-4890-9bcd-ef0123456789") == true)
    }

    @Test("fractional legacy timestamps parse at every precision Postgres trims to")
    func fractional() throws {
        let all = try rows()
        #expect(all[2].happenedAt.timeIntervalSince1970.rounded(.down) == MomentFixtures.date("2026-09-30T21:15:07Z").timeIntervalSince1970)
        #expect(all[1].createdAt > all[1].happenedAt)            // .004
        #expect(all[3].createdAt > MomentFixtures.date("2026-09-29T16:02:44Z")) // .9
    }

    @Test("a legacy pebble: same id as its moment, null emotion, null glyph")
    func legacy() throws {
        let row = try rows()[3]
        let pebble = try #require(row.pebble)
        #expect(pebble.id == row.id)
        #expect(pebble.emotion == nil)
        #expect(pebble.glyph == nil)
        #expect(pebble.valence == .lowlightLarge)
    }

    @Test("decoding ignores the ambient decoder's date strategy")
    func ambientStrategy() throws {
        let strict = JSONDecoder()
        strict.dateDecodingStrategy = .iso8601
        let deferred = JSONDecoder()
        deferred.dateDecodingStrategy = .deferredToDate
        #expect(try rows(strict) == rows())
        #expect(try rows(deferred) == rows())
    }
}
```

- [ ] **Step 3: Run it to verify it fails.** Run: `xcodebuild build-for-testing …`. Expected: compile error `cannot find 'MomentSummary' in scope`.

- [ ] **Step 4: Implement `MomentSummary.swift`:**

```swift
import Foundation

/// One row of `path_moments()` (spec §4): a moment on the caller's Path.
/// `pebble` is nil for an uncompleted moment (D2), which draws as a blank
/// stone. Also the row shape of the soul and collection detail lists.
struct MomentSummary: Identifiable, Decodable, Hashable {

    struct PebbleSummary: Identifiable, Decodable, Hashable {
        let id: UUID
        let intensity: Int
        let positiveness: Int
        let visibility: Visibility
        let emotion: EmotionRef?
        let glyph: PebbleGlyph?

        var valence: Valence { Valence.resolved(positiveness: positiveness, intensity: intensity) }
    }

    let id: UUID
    let name: String?
    let happenedAt: Date
    let createdAt: Date
    let firstSnapPath: String?
    let pebble: PebbleSummary?

    var isCompleted: Bool { pebble != nil }

    private enum CodingKeys: String, CodingKey {
        case id, name, pebble
        case happenedAt = "happened_at"
        case createdAt = "created_at"
        case firstSnapPath = "first_snap_path"
    }

    init(
        id: UUID,
        name: String?,
        happenedAt: Date,
        createdAt: Date,
        firstSnapPath: String?,
        pebble: PebbleSummary?
    ) {
        self.id = id
        self.name = name
        self.happenedAt = happenedAt
        self.createdAt = createdAt
        self.firstSnapPath = firstSnapPath
        self.pebble = pebble
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(UUID.self, forKey: .id)
        name = try container.decodeIfPresent(String.self, forKey: .name)
        happenedAt = try container.decodeFlexibleDate(forKey: .happenedAt)
        createdAt = try container.decodeFlexibleDate(forKey: .createdAt)
        firstSnapPath = try container.decodeIfPresent(String.self, forKey: .firstSnapPath)
        pebble = try container.decodeIfPresent(PebbleSummary.self, forKey: .pebble)
    }
}
```

`Visibility` is already `Decodable` and `Hashable`.

- [ ] **Step 5:** Run `-only-testing:PebblesTests/MomentSummaryDecodingTests`. Expected: PASS.
- [ ] **Step 6:** Commit: `git commit -m "feat(ios): decode path_moments rows into MomentSummary"`

### Task 1.4: `MomentDetail` (the `v_moments_full` row)

**Files:** Create `Pebbles/Features/Path/Models/MomentDetail.swift`, `PebblesTests/Moments/MomentDetailDecodingTests.swift`.

- [ ] **Step 1: Write the failing test:**

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("MomentDetail decoding (v_moments_full)")
struct MomentDetailDecodingTests {

    private func decode(_ data: Data) throws -> MomentDetail {
        try JSONDecoder().decode(MomentDetail.self, from: data)
    }

    @Test("the owner's completed moment: every relation, the inherited glyph")
    func completed() throws {
        let detail = try decode(MomentFixtures.completedMoment)
        #expect(detail.name == "Coffee with Ana")
        #expect(detail.description == "First rain of autumn.")
        #expect(detail.userId == MomentFixtures.uuid(MomentFixtures.ownerId))
        #expect(detail.hiddenAt == nil)
        #expect(detail.snaps.map(\.sortOrder) == [0])
        let pebble = try #require(detail.pebble)
        #expect(pebble.id == MomentFixtures.uuid("7e6d5c4b-3a29-4817-8f6e-5d4c3b2a1908"))
        #expect(pebble.emotion.slug == "joy")
        #expect(pebble.valence == .highlightMedium)
        #expect(pebble.visibility == .private)
        #expect(pebble.glyph?.source == .collection)
        #expect(pebble.domains.map(\.name) == ["Work"])
        #expect(pebble.souls.map(\.name) == ["Ana"])
        #expect(pebble.collections.map(\.name) == ["Autumn", "Wins"])
        #expect(pebble.collections[0].glyphId == MomentFixtures.uuid("66666666-6666-4666-8666-666666666666"))
        #expect(pebble.collections[1].glyphId == nil)
    }

    @Test("a photo-only bare moment: null name, null description, null pebble")
    func photoOnly() throws {
        let detail = try decode(MomentFixtures.photoOnlyMoment)
        #expect(detail.name == nil)
        #expect(detail.description == nil)
        #expect(detail.pebble == nil)
        #expect(detail.snaps.count == 1)
    }

    @Test("a connection's moment: no pebble, no snaps")
    func connection() throws {
        let detail = try decode(MomentFixtures.connectionMoment)
        #expect(detail.pebble == nil)
        #expect(detail.snaps.isEmpty)
    }

    @Test("a legacy pebble: fractional happened_at, empty relations, no glyph")
    func legacy() throws {
        let detail = try decode(MomentFixtures.legacyMoment)
        #expect(detail.happenedAt > MomentFixtures.date("2026-09-29T16:00:00Z"))
        #expect(detail.description == "")
        let pebble = try #require(detail.pebble)
        #expect(pebble.id == detail.id)
        #expect(pebble.domains.isEmpty)
        #expect(pebble.glyph == nil)
    }

    @Test("snaps come back sorted by sort_order whatever the wire order")
    func snapOrder() throws {
        let json = String(decoding: MomentFixtures.photoOnlyMoment, as: UTF8.self)
            .replacingOccurrences(
                of: "\"snaps\": [",
                with: "\"snaps\": [{\"id\": \"c3d4e5f6-a7b8-4901-8cde-f01234567890\", \"storage_path\": \"x/y\", \"sort_order\": 3},"
            )
        let detail = try decode(Data(json.utf8))
        #expect(detail.snaps.map(\.sortOrder) == [0, 3])
    }
}
```

- [ ] **Step 2: Run to verify it fails** (`cannot find 'MomentDetail'`).

- [ ] **Step 3: Implement `MomentDetail.swift`:**

```swift
import Foundation

/// One row of `v_moments_full` (spec §4): the moment, its snaps, and
/// `pebble` = the CALLER's own pebble on it, or nil (uncompleted, or a
/// connection's moment). Read model of the read page and both edit sheets.
struct MomentDetail: Identifiable, Decodable, Hashable {

    struct Snap: Identifiable, Decodable, Hashable {
        let id: UUID
        let storagePath: String
        let sortOrder: Int

        private enum CodingKeys: String, CodingKey {
            case id
            case storagePath = "storage_path"
            case sortOrder = "sort_order"
        }
    }

    /// `v_moments_full.pebble.souls[]` is `{id, name}` only. The read page
    /// takes each soul's glyph from `ReferenceDataService.souls` (A6).
    struct SoulRef: Identifiable, Decodable, Hashable {
        let id: UUID
        let name: String
    }

    struct CollectionRef: Identifiable, Decodable, Hashable {
        let id: UUID
        let name: String
        let glyphId: UUID?

        private enum CodingKeys: String, CodingKey {
            case id, name
            case glyphId = "glyph_id"
        }
    }

    struct Pebble: Identifiable, Decodable, Hashable {
        let id: UUID
        let intensity: Int
        let positiveness: Int
        let visibility: Visibility
        let emotion: EmotionRef
        let glyph: PebbleGlyph?
        let domains: [DomainRef]
        let souls: [SoulRef]
        let collections: [CollectionRef]

        var valence: Valence { Valence.resolved(positiveness: positiveness, intensity: intensity) }

        private enum CodingKeys: String, CodingKey {
            case id, intensity, positiveness, visibility, emotion, glyph, domains, souls, collections
        }

        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            id = try container.decode(UUID.self, forKey: .id)
            intensity = try container.decode(Int.self, forKey: .intensity)
            positiveness = try container.decode(Int.self, forKey: .positiveness)
            visibility = try container.decode(Visibility.self, forKey: .visibility)
            emotion = try container.decode(EmotionRef.self, forKey: .emotion)
            glyph = try container.decodeIfPresent(PebbleGlyph.self, forKey: .glyph)
            domains = try container.decodeIfPresent([DomainRef].self, forKey: .domains) ?? []
            souls = try container.decodeIfPresent([SoulRef].self, forKey: .souls) ?? []
            collections = try container.decodeIfPresent([CollectionRef].self, forKey: .collections) ?? []
        }
    }

    let id: UUID
    let userId: UUID
    let name: String?
    let description: String?
    let happenedAt: Date
    let hiddenAt: Date?
    let createdAt: Date
    let updatedAt: Date
    let snaps: [Snap]
    let pebble: Pebble?

    private enum CodingKeys: String, CodingKey {
        case id, name, description, snaps, pebble
        case userId = "user_id"
        case happenedAt = "happened_at"
        case hiddenAt = "hidden_at"
        case createdAt = "created_at"
        case updatedAt = "updated_at"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(UUID.self, forKey: .id)
        userId = try container.decode(UUID.self, forKey: .userId)
        name = try container.decodeIfPresent(String.self, forKey: .name)
        description = try container.decodeIfPresent(String.self, forKey: .description)
        happenedAt = try container.decodeFlexibleDate(forKey: .happenedAt)
        hiddenAt = try container.decodeFlexibleDateIfPresent(forKey: .hiddenAt)
        createdAt = try container.decodeFlexibleDate(forKey: .createdAt)
        updatedAt = try container.decodeFlexibleDate(forKey: .updatedAt)
        snaps = (try container.decodeIfPresent([Snap].self, forKey: .snaps) ?? [])
            .sorted { $0.sortOrder < $1.sortOrder }
        pebble = try container.decodeIfPresent(Pebble.self, forKey: .pebble)
    }
}
```

- [ ] **Step 4:** Build the test target; `MomentDetailDecodingTests` compiles.
- [ ] **Step 5:** Commit: `git commit -m "feat(ios): decode v_moments_full rows into MomentDetail"`

### Task 1.5: Writer payloads

**Files:** Create `SaveMomentPayload.swift`, `CompletePebblePayload.swift`, `PebblesTests/Moments/SaveMomentPayloadEncodingTests.swift`, `PebblesTests/Moments/CompletePebblePayloadEncodingTests.swift`.

- [ ] **Step 1: Write the failing tests.** `SaveMomentPayloadEncodingTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("SaveMomentPayload encoding")
struct SaveMomentPayloadEncodingTests {

    private func object(_ payload: SaveMomentPayload) throws -> [String: Any] {
        let data = try JSONEncoder().encode(payload)
        return try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }

    @Test("unset keys are omitted, never sent as null")
    func omitsUnset() throws {
        let json = try object(SaveMomentPayload())
        #expect(json.isEmpty)
    }

    @Test("happened_at is whole seconds, UTC, Z")
    func wholeSeconds() throws {
        var payload = SaveMomentPayload()
        payload.happenedAt = Date(timeIntervalSince1970: 1_791_793_800.517)
        #expect(try object(payload)["happened_at"] as? String == "2026-10-12T08:30:00Z")
    }

    @Test("ids are lowercase, snaps carry storage_path and sort_order")
    func idsAndSnaps() throws {
        let momentId = UUID(uuidString: "0B7C3F5E-2D41-4A8E-9F6B-1C2D3E4F5A6B")!
        let snapId = UUID(uuidString: "A1B2C3D4-E5F6-4789-8ABC-DEF012345678")!
        var payload = SaveMomentPayload()
        payload.id = momentId
        payload.snaps = [.init(id: snapId, storagePath: "u/s", sortOrder: 0)]
        let json = try object(payload)
        #expect(json["id"] as? String == "0b7c3f5e-2d41-4a8e-9f6b-1c2d3e4f5a6b")
        let snap = try #require((json["snaps"] as? [[String: Any]])?.first)
        #expect(snap["id"] as? String == "a1b2c3d4-e5f6-4789-8abc-def012345678")
        #expect(snap["storage_path"] as? String == "u/s")
        #expect(snap["sort_order"] as? Int == 0)
    }

    @Test("an empty snaps array is sent, so the photo is removed")
    func emptySnaps() throws {
        var payload = SaveMomentPayload()
        payload.snaps = []
        #expect((try object(payload)["snaps"] as? [Any])?.isEmpty == true)
    }

    @Test("an empty name and description are sent as \"\", which clears them")
    func emptyStringsClear() throws {
        var payload = SaveMomentPayload()
        payload.name = ""
        payload.description = ""
        let json = try object(payload)
        #expect(json["name"] as? String == "")
        #expect(json["description"] as? String == "")
    }

    @Test("snap descriptors from the coordinator's FormSnap")
    func snapsFromFormSnap() {
        let userId = UUID(uuidString: "5F0E1D2C-3B4A-4958-8776-655443322110")!
        let snapId = UUID()
        #expect(SaveMomentPayload.snaps(for: nil, userId: userId).isEmpty)
        let existing = SaveMomentPayload.snaps(for: .existing(id: snapId, storagePath: "a/b"), userId: userId)
        #expect(existing == [.init(id: snapId, storagePath: "a/b", sortOrder: 0)])
    }
}
```

`CompletePebblePayloadEncodingTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("CompletePebblePayload encoding")
struct CompletePebblePayloadEncodingTests {

    private func object(_ payload: CompletePebblePayload) throws -> [String: Any] {
        let data = try JSONEncoder().encode(payload)
        return try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }

    private let momentId = UUID(uuidString: "0B7C3F5E-2D41-4A8E-9F6B-1C2D3E4F5A6B")!

    @Test("moment_id alone: every other key omitted")
    func minimal() throws {
        let json = try object(CompletePebblePayload(momentId: momentId))
        #expect(json.keys.sorted() == ["moment_id"])
        #expect(json["moment_id"] as? String == "0b7c3f5e-2d41-4a8e-9f6b-1c2d3e4f5a6b")
    }

    @Test("a full completion, lowercase ids, visibility raw value")
    func full() throws {
        let emotion = UUID(uuidString: "22222222-2222-4222-8222-222222222222")!
        let domain = UUID(uuidString: "33333333-3333-4333-8333-333333333333")!
        var payload = CompletePebblePayload(momentId: momentId)
        payload.emotionId = emotion
        payload.intensity = 2
        payload.positiveness = 1
        payload.visibility = .private
        payload.domainIds = [domain]
        payload.soulIds = []
        payload.collectionIds = []
        let json = try object(payload)
        #expect(json["emotion_id"] as? String == "22222222-2222-4222-8222-222222222222")
        #expect(json["intensity"] as? Int == 2)
        #expect(json["positiveness"] as? Int == 1)
        #expect(json["visibility"] as? String == "private")
        #expect(json["domain_ids"] as? [String] == ["33333333-3333-4333-8333-333333333333"])
        #expect((json["soul_ids"] as? [String])?.isEmpty == true)
        #expect((json["collection_ids"] as? [String])?.isEmpty == true)
        #expect(json["new_souls"] == nil)
        #expect(json["cards"] == nil)
    }
}
```

- [ ] **Step 2: Run to verify they fail** (types missing).

- [ ] **Step 3: Implement `SaveMomentPayload.swift`:**

```swift
import Foundation

/// The `payload` of `save_moment(payload jsonb)` (spec §3). Create when `id`
/// is nil, else update the caller's moment.
///
/// Every key is optional and **omitted** when nil: the server reads an
/// explicit JSON null as "absent" (keep), so null can never clear a field.
/// To clear `name` or `description`, send `""` (A2). `snaps` replaces the
/// whole set when present; `[]` removes the photo.
struct SaveMomentPayload: Encodable, Equatable {

    struct Snap: Encodable, Equatable {
        let id: UUID
        let storagePath: String
        let sortOrder: Int

        private enum CodingKeys: String, CodingKey {
            case id
            case storagePath = "storage_path"
            case sortOrder = "sort_order"
        }

        func encode(to encoder: Encoder) throws {
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(id.uuidString.lowercased(), forKey: .id)
            try container.encode(storagePath, forKey: .storagePath)
            try container.encode(sortOrder, forKey: .sortOrder)
        }
    }

    var id: UUID?
    var name: String?
    var description: String?
    var happenedAt: Date?
    var snaps: [Snap]?

    private enum CodingKeys: String, CodingKey {
        case id, name, description, snaps
        case happenedAt = "happened_at"
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encodeIfPresent(id.map { $0.uuidString.lowercased() }, forKey: .id)
        try container.encodeIfPresent(name, forKey: .name)
        try container.encodeIfPresent(description, forKey: .description)
        // Whole seconds (standing timestamp rule); the server truncates too.
        try container.encodeIfPresent(happenedAt.map(ISO8601Flexible.string(from:)), forKey: .happenedAt)
        try container.encodeIfPresent(snaps, forKey: .snaps)
    }

    /// The snap set for the coordinator's current photo: empty without one.
    /// The bytes are already in Storage (upload happens at pick time).
    static func snaps(for formSnap: FormSnap?, userId: UUID) -> [Snap] {
        switch formSnap {
        case .none:
            return []
        case .existing(let id, let storagePath):
            return [Snap(id: id, storagePath: storagePath, sortOrder: 0)]
        case .pending(let snap):
            return [Snap(id: snap.id, storagePath: snap.storagePrefix(userId: userId), sortOrder: 0)]
        }
    }
}
```

`AttachedSnap.storagePrefix(userId:)` already returns `"{user}/{snap}"` lowercased (`Features/PebbleMedia/Models/AttachedSnap.swift:26-28`).

- [ ] **Step 4: Implement `CompletePebblePayload.swift`:**

```swift
import Foundation

/// The `payload` of `complete_pebble(payload jsonb)` (spec §3): the caller's
/// one pebble on `momentId`, inserted or updated. Keys other than
/// `moment_id` are omitted when nil (null means "keep"). iOS never sends
/// `new_souls`, `new_collections` or `cards` (A2).
struct CompletePebblePayload: Encodable, Equatable {
    let momentId: UUID
    var emotionId: UUID?
    var intensity: Int?
    var positiveness: Int?
    var visibility: Visibility?
    var domainIds: [UUID]?
    var soulIds: [UUID]?
    var collectionIds: [UUID]?

    init(momentId: UUID) {
        self.momentId = momentId
    }

    private enum CodingKeys: String, CodingKey {
        case intensity, positiveness, visibility
        case momentId = "moment_id"
        case emotionId = "emotion_id"
        case domainIds = "domain_ids"
        case soulIds = "soul_ids"
        case collectionIds = "collection_ids"
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        func lower(_ ids: [UUID]?) -> [String]? { ids?.map { $0.uuidString.lowercased() } }
        try container.encode(momentId.uuidString.lowercased(), forKey: .momentId)
        try container.encodeIfPresent(emotionId.map { $0.uuidString.lowercased() }, forKey: .emotionId)
        try container.encodeIfPresent(intensity, forKey: .intensity)
        try container.encodeIfPresent(positiveness, forKey: .positiveness)
        try container.encodeIfPresent(visibility?.rawValue, forKey: .visibility)
        try container.encodeIfPresent(lower(domainIds), forKey: .domainIds)
        try container.encodeIfPresent(lower(soulIds), forKey: .soulIds)
        try container.encodeIfPresent(lower(collectionIds), forKey: .collectionIds)
    }
}
```

- [ ] **Step 5:** Build the test target; both suites compile.
- [ ] **Step 6:** Commit: `git commit -m "feat(ios): encode save_moment and complete_pebble payloads"`

### Task 1.6: Error slugs → copy

**Files:** Create `Pebbles/Services/MomentWriteError.swift`, `PebblesTests/Moments/MomentWriteErrorTests.swift`; modify `Localizable.xcstrings`.

- [ ] **Step 1: Write the failing test:**

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("MomentWriteError")
struct MomentWriteErrorTests {

    @Test("finds the slug inside a PostgREST message", arguments: MomentWriteError.allCases)
    func findsEverySlug(_ error: MomentWriteError) {
        #expect(MomentWriteError.slug(in: "ERROR: \(error.rawValue) (SQLSTATE P0001)") == error)
    }

    @Test("no slug, no match")
    func noSlug() {
        #expect(MomentWriteError.slug(in: "connection reset") == nil)
        #expect(MomentWriteError.slug(in: "") == nil)
    }

    @Test("a slug maps to its own line, an unknown message to the generic one")
    func messages() {
        #expect(userMessageForMomentWriteMessage("moment_empty") == String(localized: "Add a name or a photo first."))
        #expect(userMessageForMomentWriteMessage("pebble_needs_domain") == String(localized: "Pick a part of life first."))
        #expect(userMessageForMomentWriteMessage("invalid_payload") == String(localized: "Couldn't save. Please try again."))
        #expect(userMessageForMomentWriteMessage("boom") == String(localized: "Couldn't save. Please try again."))
    }
}
```

- [ ] **Step 2: Run to verify it fails.**

- [ ] **Step 3: Implement `MomentWriteError.swift`** (copy from **R10**):

```swift
import Foundation
import Supabase

/// The slugs `save_moment` / `complete_pebble` / `delete_moment` raise
/// (migration 20261012090100). They arrive in the PostgREST error `message`.
enum MomentWriteError: String, CaseIterable {
    case momentEmpty = "moment_empty"
    case mediaQuotaExceeded = "media_quota_exceeded"
    case pebbleNeedsEmotion = "pebble_needs_emotion"
    case pebbleNeedsValence = "pebble_needs_valence"
    case pebbleNeedsDomain = "pebble_needs_domain"
    case soulNotOwned = "soul_not_owned"
    case collectionNotOwned = "collection_not_owned"
    case momentNotFound = "moment_not_found"
    case notAuthenticated = "not_authenticated"
    case invalidPayload = "invalid_payload"
    case invalidSnap = "invalid_snap"

    static func slug(in message: String) -> MomentWriteError? {
        allCases.first { message.contains($0.rawValue) }
    }

    var userMessage: String {
        switch self {
        case .momentEmpty:         return String(localized: "Add a name or a photo first.")
        case .mediaQuotaExceeded:  return String(localized: "Photo limit reached on this moment.")
        case .pebbleNeedsEmotion:  return String(localized: "Pick an emotion first.")
        case .pebbleNeedsValence:  return String(localized: "Pick how it landed first.")
        case .pebbleNeedsDomain:   return String(localized: "Pick a part of life first.")
        case .soulNotOwned:        return String(localized: "One of those souls is gone. Remove it and try again.")
        case .collectionNotOwned:  return String(localized: "That collection is gone. Pick another one.")
        case .momentNotFound:      return String(localized: "This moment no longer exists.")
        case .notAuthenticated:    return String(localized: "You must be signed in to save.")
        case .invalidPayload, .invalidSnap:
            return String(localized: "Couldn't save. Please try again.")
        }
    }
}

/// Message → copy. Pure, so the mapping is testable without a PostgrestError.
func userMessageForMomentWriteMessage(_ message: String) -> String {
    MomentWriteError.slug(in: message)?.userMessage ?? String(localized: "Couldn't save. Please try again.")
}

/// Any thrown error from a moment write → copy (R10). Image pipeline errors
/// keep the copy the old composer used.
func userMessageForMomentWriteError(_ error: Error) -> String {
    if let postgrest = error as? PostgrestError {
        return userMessageForMomentWriteMessage(postgrest.message)
    }
    if let pipelineError = error as? ImagePipelineError {
        switch pipelineError {
        case .unsupportedFormat:    return String(localized: "That image format isn't supported.")
        case .decodeFailed:         return String(localized: "Couldn't read the image.")
        case .encodeFailed:         return String(localized: "Couldn't process the image.")
        case .tooLargeAfterResize:  return String(localized: "That image is too large to attach.")
        }
    }
    return userMessageForMomentWriteMessage(error.localizedDescription)
}
```

- [ ] **Step 4:** Add the ten **R10** lines to `Localizable.xcstrings` (en + fr), inserted as text at their alphabetical anchors. Add `Untitled` / `Sans titre` now too (used by Task 1.9).
- [ ] **Step 5:** Build the test target; the suite compiles.
- [ ] **Step 6:** Commit: `git commit -m "feat(ios): map moment writer slugs to copy"`

### Task 1.7: `MomentsService` and `MomentDeletion`

**Files:** Create `Pebbles/Services/MomentsService.swift`, `Pebbles/Services/MomentDeletion.swift`, `PebblesTests/Moments/MomentsServiceTests.swift`.

- [ ] **Step 1: Write the failing test:**

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("MomentsService pure helpers")
struct MomentsServiceTests {

    @Test("soul/collection lists keep only moments whose pebble is linked")
    func filtersByPebbleIds() throws {
        let all = try JSONDecoder().decode([MomentSummary].self, from: MomentFixtures.pathMoments)
        let legacyPebble = MomentFixtures.uuid("3eaf6c81-5074-4db1-82e9-4f5a6b7c8d9e")
        let kept = MomentsService.moments(all, carrying: [legacyPebble])
        #expect(kept.map(\.id) == [legacyPebble])
        // A moment id that is not a pebble id never matches.
        let byMomentId = MomentsService.moments(all, carrying: [all[0].id])
        #expect(byMomentId.isEmpty)
    }

    @Test("a JSON null resolver answer (or an empty body) is no glyph")
    func nullGlyph() throws {
        #expect(try MomentsService.decodeOptionalGlyph(Data("null".utf8)) == nil)
        #expect(try MomentsService.decodeOptionalGlyph(Data()) == nil)
        let glyph = Data(#"{"id":"4759c37c-68a6-46a6-b4fc-046bd0316752","name":null,"strokes":[],"view_box":"0 0 200 200","source":"domain"}"#.utf8)
        #expect(try MomentsService.decodeOptionalGlyph(glyph)?.source == .domain)
    }

    @Test("delete_moment's answer lists the storage prefixes to remove")
    func deleteMomentResult() throws {
        let json = Data(#"{"storage_paths": ["u/a", "u/b"]}"#.utf8)
        #expect(try JSONDecoder().decode(DeleteMomentResult.self, from: json).storagePaths == ["u/a", "u/b"])
        let none = Data(#"{"storage_paths": []}"#.utf8)
        #expect(try JSONDecoder().decode(DeleteMomentResult.self, from: none).storagePaths.isEmpty)
    }
}
```

- [ ] **Step 2: Run to verify it fails.**

- [ ] **Step 3: Implement `MomentsService.swift`:**

```swift
import Foundation
import Supabase

/// Every read and write the moments split introduced (spec §3–§4), in one
/// place: a Supabase name the split owns appears in this file and nowhere
/// else, so a grep finds every caller of the contract. Views construct it
/// from `SupabaseService.client`; it holds no state.
struct MomentsService {
    let client: SupabaseClient

    /// Explicit, so a dropped column fails loudly here rather than silently
    /// in a `*` (the web break, #1053).
    static let detailColumns =
        "id, user_id, name, description, happened_at, hidden_at, created_at, updated_at, snaps, pebble"

    // MARK: - Reads

    /// The caller's Path, newest `happened_at` first.
    func pathMoments() async throws -> [MomentSummary] {
        try await client.rpc("path_moments").execute().value
    }

    func moment(id: UUID) async throws -> MomentDetail {
        try await client
            .from("v_moments_full")
            .select(Self.detailColumns)
            .eq("id", value: id)
            .single()
            .execute()
            .value
    }

    /// Moments whose pebble carries this soul (R9). Two reads, no write:
    /// the link ids, intersected with the Path the caller already reads.
    func moments(taggedWithSoul soulId: UUID) async throws -> [MomentSummary] {
        async let links: [PebbleLink] = client
            .from("pebble_souls")
            .select("pebble_id")
            .eq("soul_id", value: soulId)
            .execute()
            .value
        async let all = pathMoments()
        return try await Self.moments(all, carrying: Set(links.map(\.pebbleId)))
    }

    func moments(inCollection collectionId: UUID) async throws -> [MomentSummary] {
        async let links: [PebbleLink] = client
            .from("collection_pebbles")
            .select("pebble_id")
            .eq("collection_id", value: collectionId)
            .execute()
            .value
        async let all = pathMoments()
        return try await Self.moments(all, carrying: Set(links.map(\.pebbleId)))
    }

    static func moments(_ moments: [MomentSummary], carrying pebbleIds: Set<UUID>) -> [MomentSummary] {
        moments.filter { moment in moment.pebble.map { pebbleIds.contains($0.id) } ?? false }
    }

    /// The pebble's derived glyph, or nil (none, unreadable, unknown id).
    func resolveGlyph(pebbleId: UUID) async throws -> PebbleGlyph? {
        let data = try await client
            .rpc("resolve_pebble_glyph", params: ["p_pebble_id": pebbleId.uuidString.lowercased()])
            .execute()
            .data
        return try Self.decodeOptionalGlyph(data)
    }

    static func decodeOptionalGlyph(_ data: Data) throws -> PebbleGlyph? {
        guard !data.isEmpty else { return nil }
        return try JSONDecoder().decode(PebbleGlyph?.self, from: data)
    }

    /// Sum of the caller's karma events for one pebble (A4). Read before and
    /// after a write; the difference is what the write earned.
    func karmaTotal(refId: UUID) async throws -> Int {
        let rows: [KarmaDeltaRow] = try await client
            .from("karma_events")
            .select("delta")
            .eq("ref_id", value: refId)
            .execute()
            .value
        return rows.reduce(0) { $0 + $1.delta }
    }

    // MARK: - Writes

    func saveMoment(_ payload: SaveMomentPayload) async throws -> UUID {
        try await client.rpc("save_moment", params: PayloadParams(payload: payload)).execute().value
    }

    func completePebble(_ payload: CompletePebblePayload) async throws -> UUID {
        try await client.rpc("complete_pebble", params: PayloadParams(payload: payload)).execute().value
    }

    /// The pebble only; the moment stays, as a blank stone.
    func deletePebble(id: UUID) async throws {
        try await client.rpc("delete_pebble", params: ["p_pebble_id": id.uuidString.lowercased()]).execute()
    }

    /// The moment, its pebbles and snap rows. Returns the snap storage
    /// prefixes whose files the client removes (spec §3).
    func deleteMoment(id: UUID) async throws -> [String] {
        let result: DeleteMomentResult = try await client
            .rpc("delete_moment", params: ["p_moment_id": id.uuidString.lowercased()])
            .execute()
            .value
        return result.storagePaths
    }
}

/// `{ "payload": … }`, the single jsonb argument of both writers.
struct PayloadParams<Payload: Encodable>: Encodable {
    let payload: Payload
}

struct DeleteMomentResult: Decodable, Equatable {
    let storagePaths: [String]

    private enum CodingKeys: String, CodingKey {
        case storagePaths = "storage_paths"
    }
}

private struct PebbleLink: Decodable {
    let pebbleId: UUID

    private enum CodingKeys: String, CodingKey {
        case pebbleId = "pebble_id"
    }
}

private struct KarmaDeltaRow: Decodable {
    let delta: Int
}
```

- [ ] **Step 4: Implement `MomentDeletion.swift`:**

```swift
import Foundation
import Supabase

/// The two deletes (R8), with the client's half of each: `delete_moment`
/// hands back the snap prefixes and the client removes their files,
/// best-effort (the orphan sweep is the backstop).
@MainActor
enum MomentDeletion {
    static func deleteMoment(_ momentId: UUID, client: SupabaseClient) async throws {
        let paths = try await MomentsService(client: client).deleteMoment(id: momentId)
        let repo = PebbleSnapRepository(client: client)
        for path in paths {
            await repo.deleteFiles(storagePrefix: path)
        }
    }

    static func deletePebble(_ pebbleId: UUID, client: SupabaseClient) async throws {
        try await MomentsService(client: client).deletePebble(id: pebbleId)
    }
}
```

- [ ] **Step 5: Build & test.** Expected: the new suites (`PebbleGlyphTests`, `MomentSummaryDecodingTests`, `MomentDetailDecodingTests`, both payload suites, `MomentWriteErrorTests`, `MomentsServiceTests`) pass; the rest of the app is still on `Pebble` and builds.
- [ ] **Step 6:** Commit: `git commit -m "feat(ios): add MomentsService over the split readers and writers"`

### Task 1.8: Remove a photo by detaching it, not through an RPC (R6)

`delete_pebble_media` is gone, and R6 says neither app calls its successor `delete_moment_media`. Removing a saved photo becomes a form-local detach. The save sends the whole snap set (`snaps: []`), and the server deletes the row in the same write that enforces `moment_empty`. Only after a successful save does the client delete the detached files, best-effort. Until Part 6, the old `EditPebbleSheet` saves through `compose-pebble-update`. It already sends `snaps: []` when no photo is attached (`PebbleUpdatePayload` always sends `snaps`), and the compat `update_pebble` passes that set to `save_moment`, so the interim follows R6 too.

**Files:** Modify `PebbleSnapRepository.swift:19-24,77,91-98`, `SnapUploadCoordinator.swift:155-170,197-207`, `ExistingSnapRow.swift:12` (comment), `Models/FormSnap.swift:8` (comment), `EditPebbleSheet.swift:146-158` (`removeExisting`) and its save success paths, `PebbleFormView.swift:42` (comment), `Models/PebbleUpdatePayload.swift:16` (comment), `PebblesTests/SnapUploadCoordinatorTests.swift:11,31,50-56,220-260`.

- [ ] **Step 1: Rewrite the failing tests.** In `SnapUploadCoordinatorTests.swift`, drop `case deletePebbleMedia` from `FakeRepoCall`, `deletePebbleMediaResult` and the fake's `deletePebbleMedia(snapId:)`. Replace the two `removeExisting` tests with:

```swift
    @Test("detachExisting: clears the photo, calls nothing, remembers its files")
    func detachExistingIsLocal() {
        let repo = FakeRepo()
        let coordinator = SnapUploadCoordinator(
            repo: repo,
            initialSnap: .existing(id: UUID(), storagePath: "uid/sid"),
            retryDelay: .milliseconds(1)
        )

        coordinator.detachExisting()

        #expect(coordinator.formSnap == nil)
        #expect(coordinator.detachedStoragePaths == ["uid/sid"])
        #expect(repo.calls.isEmpty)
    }

    @Test("purgeDetached: deletes the detached files once, after a save")
    func purgeDetachedDeletesFiles() async {
        let repo = FakeRepo()
        let coordinator = SnapUploadCoordinator(
            repo: repo,
            initialSnap: .existing(id: UUID(), storagePath: "uid/sid"),
            retryDelay: .milliseconds(1)
        )
        coordinator.detachExisting()

        await coordinator.purgeDetached()
        await coordinator.purgeDetached()

        #expect(repo.calls == [.deleteFilesByPrefix(prefix: "uid/sid")])
        #expect(coordinator.detachedStoragePaths.isEmpty)
    }

    @Test("detachExisting is a no-op without an existing photo")
    func detachWithoutExisting() {
        let repo = FakeRepo()
        let coordinator = SnapUploadCoordinator(repo: repo, initialSnap: nil, retryDelay: .milliseconds(1))
        coordinator.detachExisting()
        #expect(coordinator.detachedStoragePaths.isEmpty)
    }
```

  Replacing an existing photo with a new pick must also remember the old files. Add a test beside the existing "replace" tests that seeds `.existing(id:storagePath: "uid/old")`, picks through the same helper the suite's pick tests use, and expects `detachedStoragePaths == ["uid/old"]` with no `deleteFilesByPrefix` call yet.
- [ ] **Step 2:** Run to verify they fail (`detachExisting` missing).
- [ ] **Step 3: Repository.** Delete `deletePebbleMedia(snapId:)` from `PebbleSnapRepositoryProtocol` and from `PebbleSnapRepository` (and their doc comments). `deleteFiles(storagePrefix:)` stays; update its comment to "callers that already have the `storage_path` (a `delete_moment` result, or a photo detached in Edit moment)".
- [ ] **Step 4: Coordinator.** Replace `removeExisting()` in `SnapUploadCoordinator.swift` with:

```swift
    /// Storage prefixes of saved photos the user removed or replaced in this
    /// form. Their rows go when the save sends the whole `snaps` set (R6);
    /// their files go in `purgeDetached(_:)`, after that save succeeds.
    private(set) var detachedStoragePaths: [String] = []

    /// Remove the current `.existing` photo from the form. No RPC: the next
    /// `save_moment` sends `snaps: []` and the server enforces "a name or a
    /// photo" in that same write. No-op unless the current snap is `.existing`.
    func detachExisting() {
        guard case .existing(_, let storagePath) = formSnap else { return }
        detachedStoragePaths.append(storagePath)
        formSnap = nil
    }

    /// Best-effort file cleanup for detached photos. Call only after the save
    /// that dropped their rows has succeeded; the orphan sweep is the backstop.
    func purgeDetached(_ paths: [String]) async {
        let paths = detachedStoragePaths
        detachedStoragePaths = []
        for path in paths {
            await repo.deleteFiles(storagePrefix: path)
        }
    }
```

  In `ingest(_:uti:userId:)`, just before the existing `if case .pending(let replaced) = formSnap` block, add:

```swift
        // A saved photo replaced by a new pick: its row goes with the next
        // save's whole-set `snaps`, its files after that save (R6).
        if case .existing(_, let storagePath) = formSnap {
            detachedStoragePaths.append(storagePath)
        }
```

  `cancelAndCleanup(userId:)` also sets `detachedStoragePaths = []`: a cancelled form keeps its saved photo.
- [ ] **Step 5: The interim edit sheet.** In `EditPebbleSheet.swift`, `removeExisting()` becomes `snaps?.detachExisting()`. Drop `isRemovingExistingSnap` and pass `isRemovingExistingSnap: false`. Purge only on the 2xx path of `compose-pebble-update`, fire-and-forget, over the paths detached when the payload was built: `Task { await snaps?.purgeDetached(detachedAtSave) }`. `compose-pebble-update` has no soft-success path any more, and a 5xx may mean the save never ran (R6: failed saves keep their files).
- [ ] **Step 6:** Update the comments that name the old RPC (`ExistingSnapRow.swift:12`, `FormSnap.swift:8`, `PebbleFormView.swift:42`, `PebbleUpdatePayload.swift:16`): removal now detaches, and the save's whole snap set deletes the row.
- [ ] **Step 7:** Build & test. Expected: `SnapUploadCoordinatorTests` green.
- [ ] **Step 8:** Commit: `git commit -m "fix(ios): remove a photo by saving the whole snap set"`

### Task 1.9: The Path reads `path_moments`

**Files:** Modify `PathView.swift`, `WeekRollBuilder.swift`, `WeekRollEntry.swift`, `WeekPathView.swift`, `PathPebbleRow.swift`, `WeekRollCairnCell.swift:51`, `GroupPebblesByISOWeek.swift`, `GroupPebblesByMonth.swift`; tests `WeekRollBuilderTests.swift`, `GroupPebblesByISOWeekTests.swift`, `GroupPebblesByMonthTests.swift`. Delete `Pebble.swift`, `PebbleDecodingTests.swift` (after Task 1.12, when nothing else uses `Pebble`).

- [ ] **Step 1: Update the tests first.** In `WeekRollBuilderTests.swift`, replace the `pebble(_:)` helper's `Pebble(...)` with:

```swift
        MomentSummary(
            id: UUID(),
            name: "p",
            happenedAt: date(happened),
            createdAt: date(happened),
            firstSnapPath: nil,
            pebble: .init(
                id: UUID(), intensity: intensity, positiveness: 0,
                visibility: .secret, emotion: nil, glyph: nil
            )
        )
```

and rename `pebbles:` → `moments:` at each `WeekRollBuilder.build(...)` call and `entries[i].pebbles` → `entries[i].moments`. `MomentSummary.PebbleSummary` declares no custom init, so its memberwise `.init(id:intensity:positiveness:visibility:emotion:glyph:)` exists alongside the synthesized `Decodable`. In `GroupPebblesByISOWeekTests.swift` / `GroupPebblesByMonthTests.swift`, change the helper's JSON to a `path_moments` row and decode `MomentSummary`:

```swift
    private func pebble(_ happened: String) throws -> MomentSummary {
        let json = Data("""
        {"id":"\(UUID().uuidString.lowercased())","name":"p","happened_at":"\(happened)",
         "created_at":"\(happened)","first_snap_path":null,"pebble":null}
        """.utf8)
        return try JSONDecoder().decode(MomentSummary.self, from: json)
    }
```

- [ ] **Step 2:** Run to verify they fail (`WeekRollBuilder.build(moments:)` missing).
- [ ] **Step 3:** In `WeekRollEntry.swift` rename `let pebbles: [Pebble]` → `let moments: [MomentSummary]`. In `WeekRollBuilder.swift` change the signature to `build(moments: [MomentSummary], calendar: Calendar, today: Date)`, rename locals, keep the sorting rules byte-identical (they read `happenedAt`). In `GroupPebblesByISOWeek.swift` / `GroupPebblesByMonth.swift` change `[Pebble]` → `[MomentSummary]` (function names unchanged). `WeekRollCairnCell.swift:51`: `entry.moments.count`.
- [ ] **Step 4: `WeekPathView.swift`:** `onTap: (MomentSummary) -> Void`, `onDelete: (MomentSummary) -> Void`, iterate `entry.moments`, pass `moment:` to `PathPebbleRow`, cascade key uses `entry.moments.count`.
- [ ] **Step 5: `PathPebbleRow.swift`:** replace `let pebble: Pebble` with `let moment: MomentSummary`, and:

```swift
    private var intensity: Int { moment.pebble?.intensity ?? 2 }
    private var isLarge: Bool { intensity >= 3 }
    private var hasPhoto: Bool { moment.firstSnapPath != nil }
```

name line: `Text(verbatim: MomentName.display(moment.name) ?? String(localized: "Untitled"))` (Part 3 replaces it with `MomentNameText`), `rowHeight(intensity: intensity, …)`, photo from `moment.firstSnapPath`, `formattedWeekdayTime` from `moment.happenedAt`, palette from `moment.pebble?.emotion?.id`, `frameColors` from `intensity`. Thumbnail (interim until Part 2):

```swift
    private var thumbnail: some View {
        PebbleStoneView(valence: moment.pebble?.valence ?? .neutralMedium, renderSvg: nil, palette: palette)
            .frame(width: thumbnailSize, height: thumbnailSize)
    }
```

Update the `#Preview` to build a `MomentSummary` (pebble nil).
- [ ] **Step 6: `PathView.swift`:**
  - `@State private var moments: [MomentSummary] = []` replaces `pebbles`; `selectedPebbleId` → `selectedMomentId`; `pendingDeletion: MomentSummary?`.
  - `rippleWithLocalActiveToday`: `let activeToday = moments.contains { $0.isCompleted && Calendar.current.isDateInToday($0.createdAt) }` (A5).
  - `load()`:

```swift
    private func load() async {
        do {
            let result = try await MomentsService(client: supabase.client).pathMoments()
            self.moments = result
            self.entries = WeekRollBuilder.build(moments: result, calendar: isoCalendar, today: today)
            // …focused-week selection unchanged…
            self.isLoading = false
        } catch {
            logger.error("path fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = String(localized: "Couldn't load your pebbles.")
            self.isLoading = false
        }
    }
```

  - The old composers report a **pebble** id (`compose-pebble` returns `pebble_id`, which differs from the moment id for every post-split pebble). Add, and use from `CreatePebbleSheet`'s and `DraftsListSheet`'s `onCreated` after the reload, and from `focusWeek(containing:)`:

```swift
    /// The moment holding `id`, which may be a moment id or a pebble id
    /// (the old composers report pebble ids; equal only for pre-split rows).
    private func moment(matching id: UUID) -> MomentSummary? {
        moments.first { $0.id == id || $0.pebble?.id == id }
    }
```

    `CreatePebbleSheet(onCreated: { newPebbleId in Task { …reload…; selectedMomentId = moment(matching: newPebbleId)?.id } })`, same for `DraftsListSheet`. `focusWeek(containing:)` matches `entry.moments.contains { $0.id == id || $0.pebble?.id == id }`.
  - Delete:

```swift
    private func delete(_ moment: MomentSummary) async {
        pendingDeletion = nil
        do {
            try await MomentDeletion.deleteMoment(moment.id, client: supabase.client)
            async let timeline: Void = load()
            async let statsReload: Void = stats.refresh()
            _ = await (timeline, statsReload)
        } catch {
            logger.error("delete moment failed: \(error.localizedDescription, privacy: .private)")
            deleteError = String(localized: "Something went wrong. Please try again.")
        }
    }
```

    and the dialog title `pendingDeletion.map { "Delete \(MomentName.display($0.name) ?? String(localized: "Untitled"))?" } ?? ""` (Part 3 replaces the dialog).
  - `.sheet(item: $selectedMomentId) { id in PebbleDetailSheet(momentId: id, onPebbleUpdated: …) }` (Task 1.10 changes the sheet's init).
- [ ] **Step 7:** Build (the app target compiles once Tasks 1.10–1.12 land; run the build after Task 1.12). Commit after Task 1.12 builds: see Task 1.12, Step 5.

### Task 1.10: The read page reads `v_moments_full`

**Files:** Modify `PebbleDetailSheet.swift`, `Read/PebbleReadView.swift`, `Read/PebbleReadBanner.swift`, `Read/PebbleSnapFrame.swift` (previews).

- [ ] **Step 1: `PebbleDetailSheet`:** `let momentId: UUID` replaces `pebbleId`; `@State private var detail: MomentDetail?`; toolbar:

```swift
                    ToolbarItem(placement: .topBarLeading) {
                        if let pebble = detail?.pebble {
                            PebblePrivacyBadge(visibility: pebble.visibility, style: .chip)
                        }
                    }
                    ToolbarItem(placement: .topBarTrailing) {
                        HStack(spacing: 8) {
                            // Shares the PEBBLE, whose id differs from the moment's (R4).
                            if let pebble = detail?.pebble, pebble.visibility == .public {
                                ShareLink(item: SharedPebbleLink.url(for: pebble.id)) {
                                    Image(systemName: "square.and.arrow.up")
                                }
                                .accessibilityLabel(Text("Share", comment: "Accessibility label for the public pebble share button"))
                            }
                            PebbleToolbarButton("Edit") { isPresentingEdit = true }
                                .disabled(detail?.pebble == nil)
                        }
                    }
```

title: `.pebblesToolbarTitle(MomentName.display(detail?.name) ?? "")`; edit sheet: `EditPebbleSheet(momentId: momentId, onSaved: …)`; content: `PebbleReadView(detail: detail, palette: detail.pebble.flatMap { palettes.palette(for: $0.emotion.id) })`; `load()`:

```swift
            self.detail = try await MomentsService(client: supabase.client).moment(id: momentId)
```

  Preview: `PebbleDetailSheet(momentId: UUID())` plus `.environment(ReferenceDataService(client: supabase.client))`.
- [ ] **Step 2: `PebbleReadView`:** `let detail: MomentDetail`; add `@Environment(ReferenceDataService.self) private var refs`. Body:

```swift
                PebbleReadBanner(
                    snapStoragePath: detail.snaps.first?.storagePath,
                    renderSvg: nil,
                    renderVersion: nil,
                    emotionId: detail.pebble?.emotion.id,
                    valence: detail.pebble?.valence ?? .neutralMedium
                )

                PebbleReadTitle(
                    name: MomentName.display(detail.name) ?? "",
                    happenedAt: detail.happenedAt,
                    nameColor: pageColors?.title,
                    dateColor: pageColors?.date
                )

                if let pebble = detail.pebble {
                    metadataRow(pebble)
                }

                if let description = detail.description, !description.isEmpty { … unchanged … }

                if let pebble = detail.pebble, !pebble.souls.isEmpty {
                    soulsRow(pebble)
                }
```

  `metadataRow(_ pebble: MomentDetail.Pebble)` reads `pebble.emotion`, `pebble.domains`, `pebble.collections` exactly as today. `soulsRow(_ pebble:)`:

```swift
    @ViewBuilder
    private func soulsRow(_ pebble: MomentDetail.Pebble) -> some View {
        LazyVGrid(columns: SoulPillGrid.columns, spacing: SoulPillGrid.spacing) {
            ForEach(pebble.souls) { soul in
                DetailSoulCell(
                    name: soul.name,
                    // v_moments_full carries {id, name}; the glyph comes from
                    // the cached soul list (A6). Unknown → empty glyph.
                    strokes: refs.souls.first { $0.id == soul.id }?.glyph.strokes ?? [],
                    glyphColor: pageColors?.soulGlyph,
                    nameColor: pageColors?.soulName
                )
            }
        }
    }
```

  `DetailSoulCell` takes `name: String` and `strokes: [GlyphStroke]` instead of `soul: SoulWithGlyph` (body unchanged otherwise).
- [ ] **Step 3: `PebbleReadBanner`:** `let emotionId: UUID?`; `palette` becomes `emotionId.flatMap { palettes.palette(for: $0) }`. Previews pass `emotionId: nil`. (`renderSvg` / `renderVersion` stay until Part 2.)
- [ ] **Step 4:** Commit after Task 1.12 builds.

### Task 1.11: The edit sheet loads `v_moments_full`; `PebbleDraft(from: MomentDetail)`

**Files:** Modify `EditPebbleSheet.swift`, `Models/PebbleDraft.swift`; delete `Models/PebbleDetail.swift`, tests `PebbleDetailDecodingTests.swift`, `PebbleDetailSoulGlyphDecodingTests.swift`, `PebbleDraftFromDetailTests.swift`; create `PebblesTests/Moments/PebbleDraftFromMomentDetailTests.swift`.

- [ ] **Step 1: Write the failing test:**

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("PebbleDraft init(from: MomentDetail)")
struct PebbleDraftFromMomentDetailTests {

    @Test("a completed moment prefills every field the old form edits")
    func completed() throws {
        let detail = try JSONDecoder().decode(MomentDetail.self, from: MomentFixtures.completedMoment)
        let draft = try #require(PebbleDraft(from: detail))
        #expect(draft.name == "Coffee with Ana")
        #expect(draft.description == "First rain of autumn.")
        #expect(draft.happenedAt == detail.happenedAt)
        #expect(draft.emotionId == MomentFixtures.uuid(MomentFixtures.joyId))
        #expect(draft.domainId == MomentFixtures.uuid(MomentFixtures.domainId))
        #expect(draft.valence == .highlightMedium)
        #expect(draft.soulIds == [MomentFixtures.uuid(MomentFixtures.soulId)])
        #expect(draft.collectionId == MomentFixtures.uuid(MomentFixtures.collectionId))
        #expect(draft.visibility == .private)
        #expect(draft.glyphId == nil)
    }

    @Test("a bare moment has no pebble to edit")
    func bare() throws {
        let detail = try JSONDecoder().decode(MomentDetail.self, from: MomentFixtures.photoOnlyMoment)
        #expect(PebbleDraft(from: detail) == nil)
    }
}
```

- [ ] **Step 2:** Run to verify it fails.
- [ ] **Step 3:** In `PebbleDraft.swift`, replace `init(from detail: PebbleDetail)` with:

```swift
extension PebbleDraft {
    /// Prefill the old edit form from a moment and the caller's pebble on it.
    /// Nil for an uncompleted moment. Pebbles carry no glyph since the split
    /// (D5), so `glyphId` stays nil. Replaced by `CompleteDraft` in Part 6.
    init?(from detail: MomentDetail) {
        guard let pebble = detail.pebble else { return nil }
        self.init()
        self.happenedAt = detail.happenedAt
        self.name = detail.name ?? ""
        self.description = detail.description ?? ""
        self.emotionId = pebble.emotion.id
        self.domainId = pebble.domains.first?.id
        self.valence = pebble.valence
        self.soulIds = pebble.souls.map(\.id)
        self.collectionId = pebble.collections.first?.id
        self.visibility = pebble.visibility
    }
}
```

- [ ] **Step 4: `EditPebbleSheet`:** `let momentId: UUID`; `@State private var pebbleId: UUID?`; delete `renderSvg`/`strokeColor`/`sizeGroup` state and pass nothing for them to `PebbleFormView` (its defaults are nil / 260); `selectedGlyph` stays nil. `load()`:

```swift
            let detail = try await MomentsService(client: supabase.client).moment(id: momentId)
            guard let pebble = detail.pebble, let draft = PebbleDraft(from: detail) else {
                logger.error("edit pebble: moment has no pebble")
                self.loadError = String(localized: "Couldn't load this pebble.")
                self.isLoading = false
                return
            }
            self.pebbleId = pebble.id
            self.draft = draft
            if let existing = detail.snaps.first {
                snaps?.seedExisting(.existing(id: existing.id, storagePath: existing.storagePath))
            }
            self.isLoading = false
```

  `save()` builds `ComposePebbleUpdateRequest(pebbleId: pebbleId, payload: payload)` after `guard let pebbleId else { return }`; drop `self.renderSvg = response.renderSvg ?? self.renderSvg`. Photo removal is the detach from Task 1.8. Preview: `EditPebbleSheet(momentId: UUID(), onSaved: {})`.
- [ ] **Step 5:** Delete `PebbleDetail.swift` and the three old test files. Update `Valence.swift`'s comment that names `PebbleDetail.valence` to name `Valence.resolved`.
- [ ] **Step 6:** Commit after Task 1.12 builds.

### Task 1.12: Soul and collection detail on `MomentsService`

**Files:** Modify `SoulDetailView.swift`, `CollectionDetailView.swift`, `Components/PebbleRow.swift`; delete `Pebble.swift`, `PebbleDecodingTests.swift`.

- [ ] **Step 1: `PebbleRow`:** `let moment: MomentSummary`; name `Text(verbatim: MomentName.display(moment.name) ?? String(localized: "Untitled"))`; date `moment.happenedAt`; thumbnail always the stone (the grey square was for a missing render):

```swift
    private var thumbnail: some View {
        PebbleStoneView(valence: moment.pebble?.valence ?? .neutralMedium, renderSvg: nil, palette: palette)
            .frame(width: 40, height: 40)
    }

    private var palette: EmotionPalette? {
        moment.pebble?.emotion.flatMap { palettes.palette(for: $0.id) }
    }
```

- [ ] **Step 2: `SoulDetailView`:** `@State private var moments: [MomentSummary] = []`, `pendingDeletion: MomentSummary?`, `selectedMomentId`. `load()`:

```swift
            self.moments = try await MomentsService(client: supabase.client)
                .moments(taggedWithSoul: soulWithGlyph.id)
```

  Rows: `PebbleRow(moment:onTap:onDelete:)`; tap → `EditPebbleSheet(momentId: id, …)` (Part 3 changes it to the read page); delete → `MomentDeletion.deleteMoment(moment.id, client: supabase.client)`; dialog title as in Task 1.9.
- [ ] **Step 3: `CollectionDetailView`:** same with `.moments(inCollection: collection.id)`; `groupPebblesByMonth(moments, calendar: .current)`; `pebbleCountLabel` counts `moments`.
- [ ] **Step 4:** Delete `Features/Path/Models/Pebble.swift` and `PebblesTests/PebbleDecodingTests.swift`.
- [ ] **Step 5: Build & test.** Expected: green; test count = 518 − (deleted suites' tests) + (new suites' tests). Then commit Tasks 1.9–1.12 as three commits, staging per file group:

```bash
git add apps/ios/Pebbles/Features/Path/PathView.swift apps/ios/Pebbles/Features/Path/Services/WeekRollBuilder.swift apps/ios/Pebbles/Features/Path/Models/WeekRollEntry.swift apps/ios/Pebbles/Features/Path/Components apps/ios/Pebbles/Features/Path/GroupPebblesByISOWeek.swift apps/ios/Pebbles/Features/Profile/Views/GroupPebblesByMonth.swift apps/ios/PebblesTests/WeekRollBuilderTests.swift apps/ios/PebblesTests/GroupPebblesBy*Tests.swift
git commit -m "fix(ios): read the path from path_moments"
git add apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift apps/ios/Pebbles/Features/Path/Read apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift apps/ios/Pebbles/Features/Path/Models apps/ios/PebblesTests
git commit -m "fix(ios): read a moment from v_moments_full"
git add apps/ios/Pebbles/Features/Profile/Views apps/ios/Pebbles/Components/PebbleRow.swift
git commit -m "fix(ios): list a soul's and a collection's moments from path_moments"
```

### Task 1.13: Verify and open the PR

- [ ] **Step 1:** Run the final-gate grep (Inventory 1). Expected remaining hits, all owned by Part 6: `PebblePublisher.swift` (`compose-pebble`), `EditPebbleSheet.swift` (`compose-pebble-update`), `ComposerDraftCoordinator.swift` (`can_use_glyph`), `PebbleDraftsService.swift` (`pebble_drafts`), `DraftsListSheet.swift`, `PebbleDraftPayload.swift`, `ComposePebbleResponse.swift`, `PebbleUpdatePayload.swift` (comments/keys), `PebbleFormView.swift`, `ExistingSnapRow`/`FormSnap` comments if any were missed. **No `from("pebbles")`, `path_pebbles`, `delete_pebble_media` or `delete_moment_media`.**
- [ ] **Step 2:** Run the app on the simulator against the linked project with the test account: the Path lists bare moments (sample-glyph stones until Part 2), a tap opens the read page, delete works, a soul's and a collection's lists load. Capture the two raw payloads for the fixture diff (Task 1.3 note).
- [ ] **Step 3:** Push and open the PR (`gh stack` bottom layer), title `fix(ios): read moments and pebbles from the split contract`, body: `Part 1 of the #1047 stack.` + what moved, the interim notes, key files. Labels `fix`, `ios`; milestone from #1047. No Lab Note: nothing new is user-visible beyond repair. Add `no-lab-note`.

---
## Part 2 — the stone carves the derived glyph (`feat/1047-ios-moments-stone`)

**Outcome:** every pebble surface carves `PebbleGlyph.strokes` into its lit stone, placed in the engine's glyph slot for the valence, the way the sample glyph is placed today. A completed pebble with no glyph shows veins and fossil only. An uncompleted moment is a blank stone: the neutral-medium silhouette in the fixed grey ramp with the engine outline only, labelled "Not a pebble yet" (R4). `render_svg` disappears from the stone code.

**Files**
- Create: `Pebbles/Features/Path/Stone/StoneCarving.swift`, `PebblesTests/Stone/StonePaletteBlankTests.swift`
- Modify: `Stone/StonePalette.swift`, `Stone/StoneCarvingArt.swift`, `Stone/StoneView.swift`, `Stone/PebbleStoneView.swift`, `Read/PebbleReadPetroglyph.swift`, `Read/PebbleReadBanner.swift`, `Read/PebbleSnapFrame.swift`, `Read/PebbleReadView.swift`, `Components/PathPebbleRow.swift`, `Components/PebbleRow.swift`, `Record/Steps/RecordSuccessStep.swift`
- Rewrite tests: `PebblesTests/Stone/PebbleStoneCarvingTests.swift`

### Task 2.0: Branch

- [ ] `gh stack add feat/1047-ios-moments-stone` (on top of Part 1).

### Task 2.1: `StonePalette.blank`

- [ ] **Step 1: Write the failing test** `PebblesTests/Stone/StonePaletteBlankTests.swift`:

```swift
import SwiftUI
import Testing
@testable import Pebbles

@Suite("StonePalette.blank")
struct StonePaletteBlankTests {

    private func hex(_ color: Color) -> String {
        let resolved = color.resolve(in: EnvironmentValues())
        func byte(_ c: Float) -> Int { Int((c * 255).rounded()) }
        return String(format: "#%02X%02X%02X", byte(resolved.red), byte(resolved.green), byte(resolved.blue))
    }

    @Test("the web BLANK_PALETTE grey ramp, slot for slot (R4)")
    func ramp() {
        let blank = StonePalette.blank
        #expect(hex(blank.primary) == "#868686")
        #expect(hex(blank.secondary) == "#B9B9B9")
        #expect(hex(blank.light) == "#F3F3F3")
        #expect(hex(blank.shaded) == "#5E5E5E")
        #expect(hex(blank.dark) == "#1B1B1B")
        #expect(hex(blank.surface) == "#868686")
        #expect(abs(blank.surface.resolve(in: EnvironmentValues()).opacity - 0.1) < 0.01)
    }
}
```

- [ ] **Step 2:** Run to verify it fails (`type 'StonePalette' has no member 'blank'`).
- [ ] **Step 3:** Add to `StonePalette.swift`, under `brand`:

```swift
    /// An uncompleted moment's stone (R4): the web `BLANK_PALETTE` grey ramp
    /// (`apps/web/lib/config/stone-tones.ts`), the same in both schemes.
    /// `surface` is the primary at 10% alpha, the rule the emotion palettes follow.
    static let blank = StonePalette(
        primary: Color(.sRGB, red: 134 / 255, green: 134 / 255, blue: 134 / 255),
        secondary: Color(.sRGB, red: 185 / 255, green: 185 / 255, blue: 185 / 255),
        light: Color(.sRGB, red: 243 / 255, green: 243 / 255, blue: 243 / 255),
        surface: Color(.sRGB, red: 134 / 255, green: 134 / 255, blue: 134 / 255, opacity: 0.1),
        dark: Color(.sRGB, red: 27 / 255, green: 27 / 255, blue: 27 / 255),
        shaded: Color(.sRGB, red: 94 / 255, green: 94 / 255, blue: 94 / 255)
    )
```

- [ ] **Step 4:** Run the suite. Expected: PASS.
- [ ] **Step 5:** Commit: `git commit -m "feat(ios): add the blank stone's grey ramp"`

### Task 2.2: `StoneCarving` and carving a `PebbleGlyph`

- [ ] **Step 1: Rewrite the failing test** `PebblesTests/Stone/PebbleStoneCarvingTests.swift` (keep the existing `emotionPalette()` test verbatim at the bottom):

```swift
import Testing
import CoreGraphics
import Foundation
@testable import Pebbles

@Suite("StoneCarvingArt · real pebble")
struct PebbleStoneCarvingTests {

    /// A cross filling its whole 200 box, like an iOS-carved glyph.
    private func glyph(viewBox: String = "0 0 200 200", side: Double = 200) -> PebbleGlyph {
        PebbleGlyph(
            id: UUID(),
            name: nil,
            strokes: [GlyphStroke(d: "M 0,0 L \(side),\(side) M 0,\(side) L \(side),0", width: 6)],
            viewBox: viewBox,
            source: .domain
        )
    }

    @Test("the glyph lands in the engine's slot for the valence")
    func glyphInSlot() throws {
        let seed = try #require(StoneCarvingArt.art(for: .highlightMedium))
        let art = try #require(StoneCarvingArt.art(for: .highlightMedium, glyph: glyph()))
        // highlight-medium slot: origin (26, 84), side 150, with wobble slack.
        let slot = CGRect(x: 26, y: 84, width: 150, height: 150).insetBy(dx: -12, dy: -12)
        let glyphOnly = try #require(StoneCarvingArt.placedGlyphInk(
            strokes: glyph().strokes, viewBoxSide: 200, size: .medium, polarity: .highlight
        ))
        #expect(slot.contains(glyphOnly.boundingBoxOfPath))
        #expect(art.ink.boundingBoxOfPath.contains(glyphOnly.boundingBoxOfPath.insetBy(dx: 8, dy: 8)))
        #expect(art.veins.boundingBoxOfPath == seed.veins.boundingBoxOfPath)
        #expect(art.viewBox == seed.viewBox)
    }

    @Test("a 100-unit glyph scales into the same slot as a 200-unit one")
    func viewBoxScaling() throws {
        let big = try #require(StoneCarvingArt.placedGlyphInk(
            strokes: glyph().strokes, viewBoxSide: 200, size: .large, polarity: .neutral))
        let small = try #require(StoneCarvingArt.placedGlyphInk(
            strokes: glyph(viewBox: "0 0 100 100", side: 100).strokes, viewBoxSide: 100, size: .large, polarity: .neutral))
        #expect(abs(big.boundingBoxOfPath.midX - small.boundingBoxOfPath.midX) < 4)
        #expect(abs(big.boundingBoxOfPath.width - small.boundingBoxOfPath.width) < 8)
    }

    @Test("no glyph: veins and fossil, never the sample glyph")
    func noGlyph() throws {
        let seed = try #require(StoneCarvingArt.art(for: .neutralLarge))
        let art = try #require(StoneCarvingArt.art(for: .neutralLarge, glyph: nil))
        #expect(art.ink.boundingBoxOfPath == seed.veins.boundingBoxOfPath)
        #expect(art.fossil?.path.boundingBoxOfPath == seed.fossil?.path.boundingBoxOfPath)
    }

    @Test("a glyph whose strokes do not parse keeps the veins")
    func unparseableStrokes() throws {
        let seed = try #require(StoneCarvingArt.art(for: .lowlightSmall))
        let broken = PebbleGlyph(id: UUID(), name: nil, strokes: [GlyphStroke(d: "nope", width: 6)],
                                 viewBox: "0 0 200 200", source: nil)
        let art = try #require(StoneCarvingArt.art(for: .lowlightSmall, glyph: broken))
        #expect(art.ink.boundingBoxOfPath == seed.veins.boundingBoxOfPath)
    }

    @Test("the same glyph and valence hit the cache")
    func cached() throws {
        let g = glyph()
        let first = try #require(StoneCarvingArt.art(for: .neutralSmall, glyph: g))
        let second = try #require(StoneCarvingArt.art(for: .neutralSmall, glyph: g))
        #expect(first === second)
    }

    @Test("the blank stone: the engine outline only, nothing carved inside (R4)")
    func blank() throws {
        let seed = try #require(StoneCarvingArt.art(for: .neutralMedium))
        let blank = try #require(StoneCarvingArt.blankArt(for: .neutralMedium))
        #expect(blank.ink.boundingBoxOfPath == seed.outline.boundingBoxOfPath)
        #expect(blank.veins.isEmpty)
        #expect(blank.fossil == nil)
    }

    // emotionPalette() — unchanged from the current file.
}
```

If `WobbledBackdropArt` does not expose `path` (check `Render/Wobble/`), compare `art.fossil == nil` against `seed.fossil == nil` instead.

- [ ] **Step 2:** Run to verify it fails (`art(for:glyph:)` missing).
- [ ] **Step 3: Create `Stone/StoneCarving.swift`:**

```swift
import Foundation

/// What a stone has carved into it.
enum StoneCarving: Hashable {
    /// The bundled sample glyph over the seed's veins: pickers and the lab.
    case sample
    /// A real pebble: the seed's veins and fossil, plus its derived glyph
    /// when it has one (spec D5). Nil draws no glyph, never the sample.
    case pebble(PebbleGlyph?)
    /// An uncompleted moment (R4): the engine outline only, nothing carved
    /// inside (no glyph, veins or fossil).
    case blank
}
```

- [ ] **Step 4: `StoneCarvingArt.swift`:**
  - Delete `art(for:pebbleSvg:)` and its doc comment (the only use of `PebbleSVGModel` / `WobbleRenderer.pebbleArt` in the stone).
  - Replace `pebbleCache`'s comment and add:

```swift
    /// The carving of a real pebble: the seed's veins and fossil for its
    /// valence, plus its derived glyph's strokes, wobbled and placed in the
    /// engine's slot exactly as the sample glyph is. Nil glyph → veins only.
    /// Strokes that do not wobble are skipped and logged. Cached per
    /// (valence, glyph id): a glyph's strokes never change after carving
    /// (`GlyphService` only renames).
    static func art(for valence: Valence, glyph: PebbleGlyph?) -> Art? {
        guard let seed = art(for: valence) else { return nil }
        guard let glyph else {
            return Art(viewBox: seed.viewBox, ink: seed.veins, veins: seed.veins,
                       outline: seed.outline, fossil: seed.fossil)
        }
        let key = "\(valence.rawValue)|\(glyph.id.uuidString)" as NSString
        if let cached = pebbleCache.object(forKey: key) { return cached }
        let ink = CGMutablePath()
        ink.addPath(seed.veins)
        if let placed = placedGlyphInk(
            strokes: glyph.strokes, viewBoxSide: glyph.viewBoxSide,
            size: valence.sizeGroup, polarity: valence.polarity
        ) {
            ink.addPath(placed)
        }
        let built = Art(viewBox: seed.viewBox, ink: ink.copy() ?? ink, veins: seed.veins,
                        outline: seed.outline, fossil: seed.fossil)
        pebbleCache.setObject(built, forKey: key)
        return built
    }
```

  - Generalize `placedGlyphInk`:

```swift
    /// `strokes` drawn in a `viewBoxSide` square, wobbled and placed in the
    /// engine's slot for this size and polarity, merged into one ink path.
    /// Nil when no stroke wobbled.
    static func placedGlyphInk(
        strokes: [GlyphStroke],
        viewBoxSide: Double,
        size: ValenceSizeGroup,
        polarity: ValencePolarity
    ) -> CGPath? {
        var transform = glyphTransform(size: size, polarity: polarity, glyphViewBox: viewBoxSide)
        let placed = CGMutablePath()
        for (index, stroke) in strokes.enumerated() {
            guard let path = WobbleRenderer.glyphInk(d: stroke.d, width: stroke.width),
                  let moved = path.copy(using: &transform) else {
                logger.error("stone: glyph stroke \(index, privacy: .public) did not wobble")
                continue
            }
            placed.addPath(moved)
        }
        return placed.isEmpty ? nil : placed.copy()
    }
```

    and make `build(_:)` call it with the sample: `placedGlyphInk(strokes: glyph.strokes.map { GlyphStroke(d: $0.d, width: $0.width) }, viewBoxSide: glyph.viewBox, size: valence.sizeGroup, polarity: valence.polarity)` where `glyph` is the decoded `stone-glyph.json` (guard it non-nil first). Delete the old two-argument `placedGlyphInk(size:polarity:)`.

    Add the blank carving (R4), which inks the engine outline and nothing else:

```swift
    /// An uncompleted moment (R4): the seed's engine outline as the only ink.
    /// No veins, no fossil, no glyph. Built from the cached seed, so it is free.
    static func blankArt(for valence: Valence) -> Art? {
        guard let seed = art(for: valence) else { return nil }
        return Art(viewBox: seed.viewBox, ink: seed.outline, veins: CGMutablePath(),
                   outline: seed.outline, fossil: nil)
    }
```
- [ ] **Step 5:** Run the suite. Expected: PASS.
- [ ] **Step 6:** Commit: `git commit -m "feat(ios): carve the derived glyph's strokes into the stone"`

### Task 2.3: `StoneView` / `PebbleStoneView` take a `StoneCarving`

- [ ] **Step 1: `StoneView.swift`:** replace `var pebbleSvg: String?` with

```swift
    /// What is carved. `.sample` (the default) is the picker and the lab.
    var carving: StoneCarving = .sample
```

  and the head of `carvingLayer`:

```swift
    private var carvingArt: StoneCarvingArt.Art? {
        switch carving {
        case .sample: return StoneCarvingArt.art(for: valence)
        case .pebble(let glyph): return StoneCarvingArt.art(for: valence, glyph: glyph)
        case .blank: return StoneCarvingArt.blankArt(for: valence)
        }
    }

    @ViewBuilder
    private var carvingLayer: some View {
        if let art = carvingArt {
            // …body unchanged…
        }
    }
```

  `ValenceStoneView` and `StoneLabView` keep the default `.sample`.
- [ ] **Step 2: `PebbleStoneView.swift`:** replace the stored `renderSvg: String?` and `palette: EmotionPalette?` with:

```swift
    let valence: Valence
    let carving: StoneCarving
    let stonePalette: StonePalette
    var followsTilt: Bool = false

    /// A completed pebble: its derived glyph, coloured by its emotion
    /// (the brand accent when the palette is unknown).
    init(valence: Valence, glyph: PebbleGlyph?, palette: EmotionPalette?, followsTilt: Bool = false) {
        self.valence = valence
        self.carving = .pebble(glyph)
        self.stonePalette = palette.map(StonePalette.init(emotion:)) ?? .brand
        self.followsTilt = followsTilt
    }

    /// An uncompleted moment (R4): neutral-medium silhouette, outline only,
    /// the fixed grey ramp, labelled "Not a pebble yet" for VoiceOver.
    static func blank(followsTilt: Bool = false) -> PebbleStoneView {
        PebbleStoneView(valence: .neutralMedium, carving: .blank, stonePalette: .blank, followsTilt: followsTilt)
    }

    private init(valence: Valence, carving: StoneCarving, stonePalette: StonePalette, followsTilt: Bool) {
        self.valence = valence
        self.carving = carving
        self.stonePalette = stonePalette
        self.followsTilt = followsTilt
    }
```

  Delete the computed `stonePalette`; pass `palette: stonePalette, … carving: carving` to `StoneView` (drop `pebbleSvg:`). Update the doc comment ("its own glyph carved from its derived glyph's strokes"). The blank stone carries its VoiceOver label (R4); on the `GeometryReader` in `body` add:

```swift
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(carving == .blank ? Text("Not a pebble yet") : Text(verbatim: ""))
        .accessibilityHidden(carving != .blank)
```

  (A completed pebble's stone stays decorative, as today; the row's text carries it.) Add `Not a pebble yet` / `Pas encore un galet` to the catalog.
- [ ] **Step 3: `PebbleReadPetroglyph.swift`:** replace its four properties (`renderSvg`, `renderVersion`, `valence`, `palette`) with the pebble and its palette:

```swift
/// The read-view "Petroglyph" (#599), the lit stone: the pebble's derived
/// glyph carved into stone coloured by its emotion, or the blank stone for an
/// uncompleted moment, the light following the phone and sweeping once.
struct PebbleReadPetroglyph: View {
    /// Nil for an uncompleted moment.
    let pebble: MomentDetail.Pebble?
    let palette: EmotionPalette?

    var body: some View {
        if let pebble {
            PebbleStoneView(valence: pebble.valence, glyph: pebble.glyph, palette: palette, followsTilt: true)
        } else {
            PebbleStoneView.blank(followsTilt: true)
        }
    }
}
```

- [ ] **Step 4: `PebbleReadBanner` / `PebbleSnapFrame`:** replace `renderSvg`, `renderVersion`, `emotionId`, `valence` with `let pebble: MomentDetail.Pebble?`; the banner's palette is `pebble.flatMap { palettes.palette(for: $0.emotion.id) }`; both pass `PebbleReadPetroglyph(pebble: pebble, palette: palette)`. Update the previews: they decode `MomentFixtures`-like JSON inline, or pass `pebble: nil` (the no-photo heading then shows the blank stone). `PebbleReadView` passes `pebble: detail.pebble`.
- [ ] **Step 5: Path and list rows.** `PathPebbleRow.thumbnail` and `PebbleRow.thumbnail`:

```swift
    @ViewBuilder
    private var stone: some View {
        if let pebble = moment.pebble {
            PebbleStoneView(valence: pebble.valence, glyph: pebble.glyph, palette: palette)
        } else {
            PebbleStoneView.blank()
        }
    }
```

  framed at their current sizes (`thumbnailSize`, 40 pt).
- [ ] **Step 6: `RecordSuccessStep.swift`:** the compose response never carries a render now; delete the `if response.renderSvg != nil { PebbleReadPetroglyph(…) }` block (the old flow is deleted in Part 6; its replacement draws the stone).
- [ ] **Step 7:** Build & test. Then grep: `grep -rn "pebbleSvg\|renderSvg: nil" apps/ios/Pebbles` → only `PebbleFormView` / `CreatePebbleSheet` / `EditPebbleSheet` / old Render stack remain.
- [ ] **Step 8:** Look at it: run on the simulator; the Path shows carved glyphs on completed pebbles, a grey blank stone on bare moments, the read page lit stone sweeps on appearance. Light and dark mode.
- [ ] **Step 9:** Commit: `git commit -m "feat(ios): draw every pebble stone from its derived glyph"`; push; PR title `feat(ios): carve the derived glyph into every stone`, body `Part 2 of the #1047 stack.`, labels `feat`, `ios`, `ui`, `no-lab-note` (covered by the stack's final note).

---
## Part 3 — Path, read page and delete UX (`feat/1047-ios-moments-path`)

**Outcome:** a bare moment reads as one everywhere (R4). Null names render "Untitled". The read page shows a bare moment without pebble chrome and gains a `…` menu. One two-option delete confirmation is shared by the Path, the soul and collection lists, and the read page (R8). Soul and collection rows open the read page (R9).

**Files**
- Create: `Pebbles/Features/Path/MomentNameText.swift`, `Pebbles/Features/Path/MomentDeleteDialog.swift`, `PebblesTests/Moments/MomentDeleteTargetTests.swift`
- Modify: `Services/MomentDeletion.swift`, `Features/Path/PathView.swift`, `Components/PathPebbleRow.swift`, `Components/PebbleRow.swift`, `Read/PebbleReadTitle.swift`, `Read/PebbleReadView.swift`, `PebbleDetailSheet.swift`, `Profile/Views/SoulDetailView.swift`, `Profile/Views/CollectionDetailView.swift`, `Resources/Localizable.xcstrings`

### Task 3.0: Branch

- [ ] `gh stack add feat/1047-ios-moments-path`

### Task 3.1: `MomentNameText`

- [ ] **Step 1: Create `MomentNameText.swift`:**

```swift
import SwiftUI

/// A moment's name, or an italic "Untitled" when it has none (spec D3: a
/// photo is enough). Callers set the font; the colour differs per case so an
/// untitled moment never reads as a title (R4).
struct MomentNameText: View {
    let name: String?
    var color: Color = Color.system.foreground
    var untitledColor: Color = Color.system.secondary

    var body: some View {
        if let display = MomentName.display(name) {
            // User-authored, never localized.
            Text(verbatim: display).foregroundStyle(color)
        } else {
            Text("Untitled").italic().foregroundStyle(untitledColor)
        }
    }
}
```

- [ ] **Step 2:** Use it: `PathPebbleRow` (`MomentNameText(name: moment.name, color: nameColor).font(.ysabeauSemibold(17))`), `PebbleRow` (`MomentNameText(name: moment.name).font(.body)`), `PebbleReadTitle` (`let name: String?`; `MomentNameText(name: name, color: nameColor ?? Color.system.foreground, untitledColor: dateColor ?? Color.system.secondary).font(.custom("Ysabeau-SemiBold", size: 24))`; `PebbleReadView` passes `detail.name`). Add a preview case with `name: nil`.
- [ ] **Step 3:** Build & test; commit `feat(ios): show an untitled moment as untitled`.

### Task 3.2: The shared delete confirmation

- [ ] **Step 1: Write the failing test** `PebblesTests/Moments/MomentDeleteTargetTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("MomentDeleteTarget")
struct MomentDeleteTargetTests {

    private func summaries() throws -> [MomentSummary] {
        try JSONDecoder().decode([MomentSummary].self, from: MomentFixtures.pathMoments)
    }

    @Test("a completed moment offers both deletes, pebble first")
    func completed() throws {
        let target = MomentDeleteTarget(summary: try summaries()[0])
        #expect(target.pebbleId == MomentFixtures.uuid("7e6d5c4b-3a29-4817-8f6e-5d4c3b2a1908"))
        #expect(target.options == [.pebbleKeepMoment, .moment])
        #expect(target.closesReadPage(after: .moment))
        #expect(!target.closesReadPage(after: .pebbleKeepMoment))
    }

    @Test("a bare moment offers the moment only")
    func bare() throws {
        let target = MomentDeleteTarget(summary: try summaries()[1])
        #expect(target.pebbleId == nil)
        #expect(target.options == [.moment])
    }

    @Test("built from a read-page detail the same way")
    func fromDetail() throws {
        let detail = try JSONDecoder().decode(MomentDetail.self, from: MomentFixtures.completedMoment)
        #expect(MomentDeleteTarget(detail: detail).options == [.pebbleKeepMoment, .moment])
    }
}
```

- [ ] **Step 2:** Run to verify it fails.
- [ ] **Step 3: Create `MomentDeleteDialog.swift`:**

```swift
import SwiftUI

/// What a delete acts on (R8): the moment, and its pebble when completed.
struct MomentDeleteTarget: Identifiable, Equatable {
    let momentId: UUID
    let pebbleId: UUID?

    var id: UUID { momentId }

    init(momentId: UUID, pebbleId: UUID?) {
        self.momentId = momentId
        self.pebbleId = pebbleId
    }

    init(summary: MomentSummary) {
        self.init(momentId: summary.id, pebbleId: summary.pebble?.id)
    }

    init(detail: MomentDetail) {
        self.init(momentId: detail.id, pebbleId: detail.pebble?.id)
    }

    var options: [MomentDeleteOption] {
        pebbleId == nil ? [.moment] : [.pebbleKeepMoment, .moment]
    }

    /// The read page closes when the moment is gone and stays (as a bare
    /// moment) when only the pebble went.
    func closesReadPage(after option: MomentDeleteOption) -> Bool {
        option == .moment
    }

    var title: LocalizedStringResource {
        pebbleId == nil ? "Delete this moment?" : "Delete this pebble?"
    }

    var message: LocalizedStringResource {
        pebbleId == nil
            ? "This can't be undone."
            : "Keeping the moment leaves it on your path as a blank stone. Deleting the moment removes its photo too. This can't be undone."
    }

    func label(for option: MomentDeleteOption) -> LocalizedStringResource {
        switch option {
        case .pebbleKeepMoment: return "Delete the pebble, keep the moment"
        case .moment: return pebbleId == nil ? "Delete moment" : "Delete the moment too"
        }
    }
}

enum MomentDeleteOption: Hashable, Identifiable {
    case pebbleKeepMoment
    case moment

    var id: Self { self }
}

extension MomentDeletion {
    /// Runs the chosen delete. Throws so the host can show its error alert.
    static func perform(
        _ option: MomentDeleteOption,
        on target: MomentDeleteTarget,
        client: SupabaseClient
    ) async throws {
        switch option {
        case .pebbleKeepMoment:
            guard let pebbleId = target.pebbleId else { return }
            try await deletePebble(pebbleId, client: client)
        case .moment:
            try await deleteMoment(target.momentId, client: client)
        }
    }
}

extension View {
    /// The one delete confirmation (R8). `perform` runs on a destructive tap,
    /// after the warning haptic.
    func momentDeleteDialog(
        target: Binding<MomentDeleteTarget?>,
        perform: @escaping (MomentDeleteTarget, MomentDeleteOption) -> Void
    ) -> some View {
        confirmationDialog(
            target.wrappedValue.map { Text($0.title) } ?? Text(verbatim: ""),
            isPresented: Binding(
                get: { target.wrappedValue != nil },
                set: { if !$0 { target.wrappedValue = nil } }
            ),
            titleVisibility: .visible,
            presenting: target.wrappedValue
        ) { presented in
            ForEach(presented.options) { option in
                Button(role: .destructive) {
                    TapHaptics.play(.warning)
                    target.wrappedValue = nil
                    perform(presented, option)
                } label: {
                    Text(presented.label(for: option))
                }
            }
            Button("Cancel", role: .cancel) { target.wrappedValue = nil }
        } message: { presented in
            Text(presented.message)
        }
    }
}
```

  Add `import Supabase` at the top (for `SupabaseClient` in the extension).
- [ ] **Step 4:** Add the six **copy catalog** delete strings to the catalog (en + fr).
- [ ] **Step 5:** Run the suite: PASS. Commit `feat(ios): one delete confirmation for pebbles and moments`.

### Task 3.3: Wire the dialog everywhere

- [ ] **Step 1: `PathView`:** replace `pendingDeletion: MomentSummary?`, its `confirmationDialog` and `delete(_:)` with:

```swift
    @State private var pendingDeletion: MomentDeleteTarget?
    …
        .momentDeleteDialog(target: $pendingDeletion) { target, option in
            Task { await delete(option, on: target) }
        }
    …
    private func delete(_ option: MomentDeleteOption, on target: MomentDeleteTarget) async {
        do {
            try await MomentDeletion.perform(option, on: target, client: supabase.client)
            async let timeline: Void = load()
            async let statsReload: Void = stats.refresh()
            _ = await (timeline, statsReload)
        } catch {
            logger.error("delete failed: \(error.localizedDescription, privacy: .private)")
            deleteError = String(localized: "Something went wrong. Please try again.")
        }
    }
```

  `WeekPathView(onDelete: { moment in pendingDeletion = MomentDeleteTarget(summary: moment) })`. The `Couldn't delete` alert stays.
- [ ] **Step 2: `SoulDetailView` / `CollectionDetailView`:** the same `pendingDeletion: MomentDeleteTarget?`, `.momentDeleteDialog`, `delete(_:on:)` reloading the list (and calling `onChanged()` so counts refresh). Row tap opens the read page (R9):

```swift
            .sheet(item: $selectedMomentId) { id in
                PebbleDetailSheet(momentId: id, onPebbleUpdated: {
                    Task { await load() }
                    onChanged()
                })
            }
```

  (they no longer present `EditPebbleSheet`). Both views need `ReferenceDataService` in the environment for the read page; it is injected at the root, so nothing to add.
- [ ] **Step 3: `PebbleDetailSheet` menu.** Replace the trailing `Edit` button with a menu, keep `ShareLink` beside it:

```swift
    @State private var pendingDeletion: MomentDeleteTarget?
    @State private var deleteError: String?
    @Environment(\.dismiss) private var dismiss
    …
                            Menu {
                                if detail?.pebble != nil {
                                    Button { isPresentingEdit = true } label: {
                                        Label("Edit pebble", systemImage: "pencil")
                                    }
                                }
                                Button(role: .destructive) {
                                    if let detail { pendingDeletion = MomentDeleteTarget(detail: detail) }
                                } label: {
                                    Label("Delete", systemImage: "trash")
                                }
                            } label: {
                                Image(systemName: "ellipsis.circle")
                            }
                            .disabled(detail == nil)
                            .accessibilityLabel(Text("More"))
    …
        .momentDeleteDialog(target: $pendingDeletion) { target, option in
            Task {
                do {
                    try await MomentDeletion.perform(option, on: target, client: supabase.client)
                    onPebbleUpdated?()
                    if target.closesReadPage(after: option) { dismiss() } else { await load() }
                } catch {
                    logger.error("delete failed: \(error.localizedDescription, privacy: .private)")
                    deleteError = String(localized: "Something went wrong. Please try again.")
                }
            }
        }
        .alert("Couldn't delete", isPresented: Binding(get: { deleteError != nil }, set: { if !$0 { deleteError = nil } })) {
            Button("OK", role: .cancel) { deleteError = nil }
        } message: { Text(deleteError ?? "") }
```

  Add `More` / `Plus` to the catalog. (Part 6 adds **Edit moment** to this menu.)
- [ ] **Step 4:** Build & test; commit `feat(ios): delete a pebble or its whole moment from every list`.

### Task 3.4: The bare-moment read page

- [ ] **Step 1: `PebbleReadView`:** for `detail.pebble == nil` the page already skips the tiles and souls (Part 1) and shows the blank stone (Part 2); confirm the description still shows and the page background falls back to `Color.system.background` (palette nil). No other change: the call to action lands in Part 4 with the flow it opens.
- [ ] **Step 2:** Simulator check, light and dark: a photo-only moment (photo banner with the blank stone overlapping, "Untitled" in italic), a named bare moment (blank stone heading), a completed moment (unchanged). Long-press a Path row: completed → two options; bare → one. Delete the pebble from the read page: the page stays and turns bare; delete the moment: the page closes, the Path row is gone.
- [ ] **Step 3:** Push; PR `feat(ios): show bare moments and delete pebbles or moments`, `Part 3 of the #1047 stack.`, labels `feat`, `ios`, `ui`, `no-lab-note`.

---
## Part 4 — the Complete flow (`feat/1047-ios-moments-complete`)

**Outcome:** a bare moment's read page carries **Make it a pebble**, which opens a full-screen six-step flow (`valence · emotion · domain · souls · collection · privacy`) ending in `complete_pebble`, a success screen with the lit stone carving the resolved glyph and `+N karma`, and `check_achievements`. A half-finished flow autosaves per moment and restores silently (A3).

**Files**
- Create: `Pebbles/Features/Complete/CompleteStep.swift`, `CompleteStep+Copy.swift`, `CompleteDraft.swift`, `CompleteFlowModel.swift`, `CompleteFlowView.swift`, `CompleteStepContent.swift`, `Steps/CompleteValenceStep.swift`, `Steps/CompleteEmotionStep.swift`, `Steps/CompleteDomainStep.swift`, `Steps/CompleteSoulsStep.swift`, `Steps/CompleteCollectionStep.swift`, `Steps/CompletePrivacyStep.swift`, `Steps/CompleteSuccessStep.swift`; `Pebbles/Services/CompleteDraftStore.swift`
- Create tests: `PebblesTests/Features/Complete/CompleteStepTests.swift`, `CompleteFlowModelTests.swift`, `CompleteDraftStoreTests.swift`, `CompletePebblePayloadFromDraftTests.swift`
- Modify: `Features/Path/Models/CompletePebblePayload.swift`, `Features/Record/RecordFlowChrome.swift`, `Features/Record/RecordFlowView.swift` (chrome call only), `Services/MomentDeletion.swift`, `Features/Path/MomentDeleteDialog.swift`, `Features/Path/Read/PebbleReadView.swift`, `Features/Path/PebbleDetailSheet.swift`, `Features/Path/PathView.swift`, `Features/Profile/Views/SoulDetailView.swift`, `Features/Profile/Views/CollectionDetailView.swift`, `Services/SignedOutPurge.swift`, `PebblesApp.swift`, `RootView.swift`, `PebblesTests/SignedOutPurgeTests.swift`, `Resources/Localizable.xcstrings`

The step views are thin wrappers over the existing picker contents (`ValencePickerContent`, `EmotionPickerContent`, `DomainPickerContent`, `SoulPickerContent`), the same shape as `Features/Record/Steps/Record*Step.swift`, retargeted to `CompleteFlowModel`. The `Record*` copies die in Part 6, so the duplication lives for two parts only.

### Task 4.0: Branch

- [ ] `gh stack add feat/1047-ios-moments-complete`

### Task 4.1: `CompleteStep`

- [ ] **Step 1: Write the failing test** `PebblesTests/Features/Complete/CompleteStepTests.swift`:

```swift
import Testing
@testable import Pebbles

@Suite("CompleteStep")
struct CompleteStepTests {

    @Test("order is valence · emotion · domain · souls · collection · privacy, then success")
    func order() {
        #expect(CompleteStep.allCases == [.valence, .emotion, .domain, .souls, .collection, .privacy, .success])
    }

    @Test("six dots; success is uncounted")
    func dots() {
        #expect(CompleteStep.counted.count == 6)
        #expect(CompleteStep.success.dotIndex == nil)
        #expect(CompleteStep.privacy.dotIndex == 5)
    }

    @Test("only souls and collection are optional")
    func optional() {
        #expect(CompleteStep.allCases.filter(\.isOptional) == [.souls, .collection])
    }

    @Test("no glyph step exists")
    func noGlyph() {
        #expect(!CompleteStep.allCases.map { "\($0)" }.contains("glyph"))
    }
}
```

- [ ] **Step 2:** Run to verify it fails.
- [ ] **Step 3: Create `CompleteStep.swift`:**

```swift
import Foundation

/// The Complete flow's screens (spec §6, A1). No glyph step: a pebble's glyph
/// is derived from its collection or domain (D5).
enum CompleteStep: Int, CaseIterable, Identifiable, Hashable {
    case valence
    case emotion
    case domain
    case souls
    case collection
    case privacy
    case success

    var id: Int { rawValue }

    var isOptional: Bool {
        switch self {
        case .souls, .collection: return true
        case .valence, .emotion, .domain, .privacy, .success: return false
        }
    }

    static var counted: [CompleteStep] { allCases.filter { $0 != .success } }

    var dotIndex: Int? { self == .success ? nil : rawValue }

    var next: CompleteStep? { CompleteStep(rawValue: rawValue + 1) }

    var previous: CompleteStep? { rawValue == 0 ? nil : CompleteStep(rawValue: rawValue - 1) }
}
```

  and `CompleteStep+Copy.swift` (existing keys, already translated):

```swift
import Foundation

extension CompleteStep {
    var title: LocalizedStringResource {
        switch self {
        case .valence:    return "How did it land?"
        case .emotion:    return "What did you feel?"
        case .domain:     return "What part of life?"
        case .souls:      return "Anyone in this one?"
        case .collection: return "Add it to a collection?"
        case .privacy:    return "Who can see it?"
        case .success:    return "Your pebble"
        }
    }

    var subtitle: LocalizedStringResource? {
        switch self {
        case .valence: return "How much of your life did this take up?"
        case .emotion, .domain, .souls, .collection, .privacy, .success: return nil
        }
    }
}
```

- [ ] **Step 4:** Run: PASS. Commit `feat(ios): define the complete flow's steps`.

### Task 4.2: `CompleteDraft` and the payload from it

- [ ] **Step 1: Write the failing test** `CompletePebblePayloadFromDraftTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("CompletePebblePayload from CompleteDraft")
struct CompletePebblePayloadFromDraftTests {

    private let momentId = UUID()
    private let emotion = UUID()
    private let domain = UUID()

    @Test("an incomplete draft makes no payload")
    func incomplete() {
        var draft = CompleteDraft()
        draft.valence = .neutralMedium
        draft.emotionId = emotion
        #expect(CompletePebblePayload(momentId: momentId, draft: draft) == nil)
    }

    @Test("a complete draft sends every key the flow owns (A2)")
    func complete() throws {
        var draft = CompleteDraft()
        draft.valence = .lowlightLarge
        draft.emotionId = emotion
        draft.domainId = domain
        let payload = try #require(CompletePebblePayload(momentId: momentId, draft: draft))
        #expect(payload.intensity == 3)
        #expect(payload.positiveness == -1)
        #expect(payload.domainIds == [domain])
        #expect(payload.soulIds == [])
        #expect(payload.collectionIds == [])
        #expect(payload.visibility == .secret)
    }

    @Test("sanitizing drops souls and collections that no longer exist")
    func sanitize() {
        let kept = UUID(), gone = UUID(), collection = UUID()
        var draft = CompleteDraft()
        draft.soulIds = [kept, gone]
        draft.collectionId = collection
        let clean = draft.sanitized(known: .init(soulIds: [kept], collectionIds: []))
        #expect(clean.soulIds == [kept])
        #expect(clean.collectionId == nil)
    }

    @Test("visibility alone is not an answer")
    func isEmpty() {
        var draft = CompleteDraft()
        draft.visibility = .public
        #expect(draft.isEmpty)
        draft.domainId = domain
        #expect(!draft.isEmpty)
    }
}
```

- [ ] **Step 2:** Run to verify it fails.
- [ ] **Step 3: Create `CompleteDraft.swift`:**

```swift
import Foundation

/// The Complete flow's answers (spec §6). Complete = valence + emotion +
/// domain (D2); souls and collection are optional; visibility defaults to
/// secret. One domain and one collection at most (A1).
struct CompleteDraft: Equatable {
    var valence: Valence?
    var emotionId: UUID?
    var domainId: UUID?
    var soulIds: [UUID] = []
    var collectionId: UUID?
    var visibility: Visibility = .secret

    var isComplete: Bool { valence != nil && emotionId != nil && domainId != nil }

    /// Nothing the user picked. Visibility is defaulted, so it never counts.
    var isEmpty: Bool {
        valence == nil && emotionId == nil && domainId == nil && soulIds.isEmpty && collectionId == nil
    }

    struct KnownIds {
        let soulIds: Set<UUID>
        let collectionIds: Set<UUID>
    }

    /// Souls and collections are deletable; a restored draft can outlive
    /// them. Dropping them here costs a chip, not a `soul_not_owned` later.
    func sanitized(known: KnownIds) -> CompleteDraft {
        var copy = self
        copy.soulIds = soulIds.filter { known.soulIds.contains($0) }
        if let collectionId, !known.collectionIds.contains(collectionId) { copy.collectionId = nil }
        return copy
    }
}
```

- [ ] **Step 4:** Add to `CompletePebblePayload.swift`:

```swift
extension CompletePebblePayload {
    /// A full completion from the flow or Edit pebble (A2). Nil until the
    /// draft is complete, so a caller cannot send a half pebble.
    init?(momentId: UUID, draft: CompleteDraft) {
        guard draft.isComplete, let valence = draft.valence,
              let emotionId = draft.emotionId, let domainId = draft.domainId else { return nil }
        self.init(momentId: momentId)
        self.emotionId = emotionId
        self.intensity = valence.intensity
        self.positiveness = valence.positiveness
        self.visibility = draft.visibility
        self.domainIds = [domainId]
        self.soulIds = draft.soulIds
        self.collectionIds = draft.collectionId.map { [$0] } ?? []
    }
}
```

- [ ] **Step 5:** Run: PASS. Commit `feat(ios): hold the complete flow's answers in CompleteDraft`.

### Task 4.3: Per-moment autosave

- [ ] **Step 1: Write the failing test** `CompleteDraftStoreTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("CompleteDraftStore")
@MainActor
struct CompleteDraftStoreTests {

    private func store() -> (CompleteDraftStore, URL) {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("complete-drafts-\(UUID().uuidString)")
        return (CompleteDraftStore(directory: dir), dir)
    }

    private func answered() -> CompleteDraft {
        var draft = CompleteDraft()
        draft.valence = .highlightSmall
        draft.emotionId = UUID()
        draft.soulIds = [UUID()]
        draft.visibility = .private
        return draft
    }

    @Test("round-trips per moment, owner-checked")
    func roundTrip() {
        let (store, _) = store()
        let owner = UUID(), moment = UUID(), other = UUID()
        let draft = answered()
        store.save(draft, momentId: moment, ownerId: owner)
        #expect(store.load(momentId: moment, ownerId: owner) == draft)
        #expect(store.load(momentId: other, ownerId: owner) == nil)
    }

    @Test("another account's file is deleted on read")
    func otherOwner() {
        let (store, _) = store()
        let moment = UUID()
        store.save(answered(), momentId: moment, ownerId: UUID())
        #expect(store.load(momentId: moment, ownerId: UUID()) == nil)
        #expect(store.load(momentId: moment, ownerId: UUID()) == nil)
    }

    @Test("the file body uses complete_pebble's keys (A3)")
    func wireKeys() throws {
        let (store, dir) = store()
        let moment = UUID()
        store.save(answered(), momentId: moment, ownerId: UUID())
        let url = dir.appendingPathComponent("\(moment.uuidString.lowercased()).json")
        let json = try #require(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        #expect(Set(json.keys) == ["owner_id", "moment_id", "intensity", "positiveness", "emotion_id", "soul_ids", "visibility"])
    }

    @Test("an empty draft clears; clear(momentId:) and clearAll scope correctly")
    func clearing() {
        let (store, _) = store()
        let owner = UUID(), a = UUID(), b = UUID()
        store.save(answered(), momentId: a, ownerId: owner)
        store.save(answered(), momentId: b, ownerId: owner)
        store.save(CompleteDraft(), momentId: a, ownerId: owner)
        #expect(store.load(momentId: a, ownerId: owner) == nil)
        store.clear(momentId: b)
        #expect(store.load(momentId: b, ownerId: owner) == nil)
        store.save(answered(), momentId: a, ownerId: owner)
        store.clearAll()
        #expect(store.load(momentId: a, ownerId: owner) == nil)
    }

    @Test("an unreadable file is discarded, not thrown")
    func unreadable() throws {
        let (store, dir) = store()
        let moment = UUID()
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        try Data("{nope".utf8).write(to: dir.appendingPathComponent("\(moment.uuidString.lowercased()).json"))
        #expect(store.load(momentId: moment, ownerId: UUID()) == nil)
    }

    @Test("autosave debounces, flush writes at once, clear drops the pending write")
    func autosave() async throws {
        let (store, _) = store()
        let owner = UUID(), moment = UUID()
        let autosave = CompleteAutosave(store: store, momentId: moment, debounce: .milliseconds(20), ownerId: { owner })
        autosave.schedule(answered())
        #expect(store.load(momentId: moment, ownerId: owner) == nil)
        try await Task.sleep(for: .milliseconds(80))
        #expect(store.load(momentId: moment, ownerId: owner) != nil)

        let other = UUID()
        let second = CompleteAutosave(store: store, momentId: other, debounce: .seconds(10), ownerId: { owner })
        second.schedule(answered())
        second.flush()
        #expect(store.load(momentId: other, ownerId: owner) != nil)
        second.schedule(answered())
        second.clear()
        #expect(store.load(momentId: other, ownerId: owner) == nil)
    }
}
```

- [ ] **Step 2:** Run to verify it fails.
- [ ] **Step 3: Create `Services/CompleteDraftStore.swift`:**

```swift
import Foundation
import Observation
import os

/// Device-local autosave for a half-finished Complete flow, one file per
/// moment (A3, spec D7). Not offline support: no sync, no merge. The file
/// carries its owner; another account's file is deleted on read, and
/// `SignedOutPurge` clears them all.
@Observable
@MainActor
final class CompleteDraftStore {

    private let directory: URL
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "complete-drafts")

    convenience init() {
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first
            ?? URL(fileURLWithPath: NSTemporaryDirectory())
        self.init(directory: base.appendingPathComponent("complete-drafts"))
    }

    init(directory: URL) {
        self.directory = directory
    }

    func load(momentId: UUID, ownerId: UUID) -> CompleteDraft? {
        let url = fileURL(for: momentId)
        guard let data = try? Data(contentsOf: url) else { return nil }
        let snapshot: CompleteDraftSnapshot
        do {
            snapshot = try JSONDecoder().decode(CompleteDraftSnapshot.self, from: data)
        } catch {
            logger.warning("discarding unreadable draft: \(error.localizedDescription, privacy: .private)")
            clear(momentId: momentId)
            return nil
        }
        guard snapshot.ownerId == ownerId, snapshot.momentId == momentId else {
            logger.info("discarding a draft owned by another account")
            clear(momentId: momentId)
            return nil
        }
        let draft = snapshot.draft
        return draft.isEmpty ? nil : draft
    }

    func save(_ draft: CompleteDraft, momentId: UUID, ownerId: UUID) {
        guard !draft.isEmpty else {
            clear(momentId: momentId)
            return
        }
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            let snapshot = CompleteDraftSnapshot(ownerId: ownerId, momentId: momentId, draft: draft)
            try JSONEncoder().encode(snapshot).write(to: fileURL(for: momentId), options: .atomic)
        } catch {
            logger.error("draft write failed: \(error.localizedDescription, privacy: .private)")
        }
    }

    func clear(momentId: UUID) {
        try? FileManager.default.removeItem(at: fileURL(for: momentId))
    }

    func clearAll() {
        try? FileManager.default.removeItem(at: directory)
    }

    private func fileURL(for momentId: UUID) -> URL {
        directory.appendingPathComponent("\(momentId.uuidString.lowercased()).json")
    }
}

/// Debounced writes for one moment's flow; flushed on background and on ✕.
@MainActor
final class CompleteAutosave {
    private let store: CompleteDraftStore
    private let momentId: UUID
    private let debounce: Duration
    private let ownerId: () -> UUID?
    private var task: Task<Void, Never>?
    private var pending: CompleteDraft?

    init(store: CompleteDraftStore, momentId: UUID, debounce: Duration = .milliseconds(800), ownerId: @escaping () -> UUID?) {
        self.store = store
        self.momentId = momentId
        self.debounce = debounce
        self.ownerId = ownerId
    }

    func schedule(_ draft: CompleteDraft) {
        pending = draft
        task?.cancel()
        task = Task { [weak self] in
            guard let self else { return }
            try? await Task.sleep(for: debounce)
            guard !Task.isCancelled else { return }
            flush()
        }
    }

    func flush() {
        task?.cancel()
        task = nil
        guard let draft = pending else { return }
        pending = nil
        guard let owner = ownerId() else { return }
        store.save(draft, momentId: momentId, ownerId: owner)
    }

    func clear() {
        task?.cancel()
        task = nil
        pending = nil
        store.clear(momentId: momentId)
    }
}

/// The file body (A3): `complete_pebble`'s key names, unset keys omitted.
struct CompleteDraftSnapshot: Codable, Equatable {
    let ownerId: UUID
    let momentId: UUID
    var intensity: Int?
    var positiveness: Int?
    var emotionId: UUID?
    var domainIds: [UUID]?
    var soulIds: [UUID]?
    var collectionIds: [UUID]?
    var visibility: String?

    private enum CodingKeys: String, CodingKey {
        case intensity, positiveness, visibility
        case ownerId = "owner_id"
        case momentId = "moment_id"
        case emotionId = "emotion_id"
        case domainIds = "domain_ids"
        case soulIds = "soul_ids"
        case collectionIds = "collection_ids"
    }

    init(ownerId: UUID, momentId: UUID, draft: CompleteDraft) {
        self.ownerId = ownerId
        self.momentId = momentId
        intensity = draft.valence?.intensity
        positiveness = draft.valence?.positiveness
        emotionId = draft.emotionId
        domainIds = draft.domainId.map { [$0] }
        soulIds = draft.soulIds.isEmpty ? nil : draft.soulIds
        collectionIds = draft.collectionId.map { [$0] }
        visibility = draft.visibility.rawValue
    }

    var draft: CompleteDraft {
        var draft = CompleteDraft()
        if let positiveness, let intensity {
            draft.valence = Valence.from(positiveness: positiveness, intensity: intensity)
        }
        draft.emotionId = emotionId
        draft.domainId = domainIds?.first
        draft.soulIds = soulIds ?? []
        draft.collectionId = collectionIds?.first
        draft.visibility = visibility.flatMap(Visibility.init(rawValue:)) ?? .secret
        return draft
    }
}
```

  The synthesized `encode` of optionals uses `encodeIfPresent`, which is the "omit unset keys" rule; `wireKeys()` checks it.
- [ ] **Step 4:** Run: PASS. Commit `feat(ios): autosave a half-finished pebble per moment`.

### Task 4.4: `CompleteFlowModel`

- [ ] **Step 1: Write the failing test** `CompleteFlowModelTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("CompleteFlowModel")
@MainActor
struct CompleteFlowModelTests {

    private final class Recorder { var taps: [TapHaptic] = [] }

    private func model() -> (CompleteFlowModel, Recorder) {
        let recorder = Recorder()
        return (CompleteFlowModel(haptic: { recorder.taps.append($0) }), recorder)
    }

    @Test("starts on valence; seeding parks on neutral-medium without a haptic")
    func seed() {
        let (model, recorder) = model()
        #expect(model.step == .valence)
        #expect(!model.isAnswered)
        model.seedValenceIfNeeded()
        #expect(model.draft.valence == .neutralMedium)
        #expect(recorder.taps.isEmpty)
    }

    @Test("valence commits in place; emotion and domain commit and advance")
    func tiles() {
        let (model, recorder) = model()
        model.select(valence: .highlightLarge)
        #expect(model.step == .valence)
        model.advance()
        #expect(model.step == .emotion)
        model.select(emotionId: UUID())
        #expect(model.step == .domain)
        model.select(domainId: UUID())
        #expect(model.step == .souls)
        #expect(recorder.taps == [.selection, .advance, .selection, .selection])
    }

    @Test("a mandatory step blocks advance with a warning")
    func gated() {
        let (model, recorder) = model()
        model.advance()
        #expect(model.step == .valence)
        #expect(recorder.taps == [.warning])
    }

    @Test("souls toggle without advancing; collection commits and advances")
    func optionalSteps() {
        let (model, _) = model()
        model.go(to: .souls)
        #expect(model.optionalButtonIsSkip)
        let soul = UUID()
        model.toggleSoul(soul)
        #expect(model.step == .souls)
        #expect(!model.optionalButtonIsSkip)
        model.toggleSoul(soul)
        #expect(model.draft.soulIds.isEmpty)
        model.advance()
        #expect(model.step == .collection)
        model.select(collectionId: UUID())
        #expect(model.step == .privacy)
    }

    @Test("privacy selects without advancing; back preserves answers")
    func privacyAndBack() {
        let (model, _) = model()
        model.go(to: .privacy)
        model.select(visibility: .public)
        #expect(model.step == .privacy)
        model.back()
        #expect(model.step == .collection)
        #expect(model.draft.visibility == .public)
    }

    @Test("restore sanitizes and lands on the first unanswered mandatory step")
    func restore() {
        let (model, _) = model()
        var saved = CompleteDraft()
        saved.valence = .neutralSmall
        saved.emotionId = UUID()
        saved.soulIds = [UUID()]
        model.restore(saved, known: .init(soulIds: [], collectionIds: []))
        #expect(model.step == .domain)
        #expect(model.draft.soulIds.isEmpty)
        saved.domainId = UUID()
        model.restore(saved, known: .init(soulIds: [], collectionIds: []))
        #expect(model.step == .privacy)
    }

    @Test("success goes to the success step with a success haptic; failure stays put")
    func outcomes() {
        let (model, recorder) = model()
        model.go(to: .privacy)
        model.beginCompleting()
        model.fail("nope")
        #expect(model.step == .privacy)
        #expect(model.completeError == "nope")
        model.beginCompleting()
        #expect(model.completeError == nil)
        let result = CompletionResult(pebbleId: UUID(), karmaDelta: 4, glyph: nil)
        model.succeed(with: result)
        #expect(model.step == .success)
        #expect(model.completed == result)
        #expect(recorder.taps.suffix(2) == [.warning, .success])
    }
}
```

- [ ] **Step 2:** Run to verify it fails.
- [ ] **Step 3: Create `CompleteFlowModel.swift`** (same discipline as `RecordFlowModel`: every interaction is a method here, and every method fires its haptic):

```swift
import Foundation
import Observation

/// What a completion produced, for the success screen (A4).
struct CompletionResult: Equatable {
    let pebbleId: UUID
    let karmaDelta: Int?
    let glyph: PebbleGlyph?
}

/// The Complete flow's state machine. Views own no flow state: a tap that
/// does not go through a method here changes nothing, and every method here
/// fires its haptic (M58 D4). The haptic is injected so tests can record it.
@Observable
@MainActor
final class CompleteFlowModel {

    var draft = CompleteDraft()
    private(set) var step: CompleteStep = .valence
    private(set) var completed: CompletionResult?
    private(set) var isCompleting = false
    private(set) var completeError: String?

    private let haptic: @MainActor (TapHaptic) -> Void

    init(haptic: @escaping @MainActor (TapHaptic) -> Void = { TapHaptics.play($0) }) {
        self.haptic = haptic
    }

    // MARK: Answers

    /// Exhaustive with no `default`: the one place that says what answered means.
    func hasAnswer(for step: CompleteStep) -> Bool {
        switch step {
        case .privacy, .success: return true
        case .valence: return draft.valence != nil
        case .emotion: return draft.emotionId != nil
        case .domain: return draft.domainId != nil
        case .souls: return !draft.soulIds.isEmpty
        case .collection: return draft.collectionId != nil
        }
    }

    var isAnswered: Bool { step.isOptional || hasAnswer(for: step) }

    var optionalButtonIsSkip: Bool { !hasAnswer(for: step) }

    // MARK: Navigation

    func advance() {
        guard step != .success else { return }
        guard isAnswered else {
            haptic(.warning)
            return
        }
        guard let next = step.next, next != .success else { return }
        haptic(.advance)
        step = next
    }

    func back() {
        guard step != .success, let previous = step.previous else { return }
        haptic(.advance)
        step = previous
    }

    /// Jump without gating or feedback: restore and tests only.
    func go(to step: CompleteStep) {
        self.step = step
    }

    // MARK: Selection

    private func commitAndAdvance(_ mutate: (inout CompleteDraft) -> Void) {
        haptic(.selection)
        mutate(&draft)
        if let next = step.next, next != .success { step = next }
    }

    func seedValenceIfNeeded() {
        guard draft.valence == nil else { return }
        draft.valence = .neutralMedium
    }

    func select(valence: Valence) {
        haptic(.selection)
        draft.valence = valence
    }

    func select(emotionId: UUID) { commitAndAdvance { $0.emotionId = emotionId } }
    func select(domainId: UUID) { commitAndAdvance { $0.domainId = domainId } }
    func select(collectionId: UUID) { commitAndAdvance { $0.collectionId = collectionId } }

    func toggleSoul(_ id: UUID) {
        haptic(.selection)
        if let index = draft.soulIds.firstIndex(of: id) {
            draft.soulIds.remove(at: index)
        } else {
            draft.soulIds.append(id)
        }
    }

    func select(visibility: Visibility) {
        haptic(.selection)
        draft.visibility = visibility
    }

    // MARK: Restore (A3)

    func firstGap() -> CompleteStep {
        for candidate in CompleteStep.counted where !candidate.isOptional {
            if !hasAnswer(for: candidate) { return candidate }
        }
        return .privacy
    }

    func restore(_ saved: CompleteDraft, known: CompleteDraft.KnownIds) {
        draft = saved.sanitized(known: known)
        step = firstGap()
    }

    // MARK: Completing

    func beginCompleting() {
        isCompleting = true
        completeError = nil
    }

    func succeed(with result: CompletionResult) {
        haptic(.success)
        isCompleting = false
        completed = result
        step = .success
    }

    func fail(_ message: String) {
        haptic(.warning)
        isCompleting = false
        completeError = message
    }
}
```

- [ ] **Step 4:** Run: PASS. Commit `feat(ios): drive the complete flow from one model`.

### Task 4.5: Generalize `RecordFlowChrome`

- [ ] **Step 1:** In `RecordFlowChrome.swift` replace `let step: RecordStep` with

```swift
    let dotCount: Int
    /// 0-based; nil on an uncounted step.
    let dotIndex: Int?
    let canGoBack: Bool
```

  delete the computed `canGoBack`; `ForEach(0..<dotCount, id: \.self) { index in Circle().fill(fill(for: index)) … }`; `fill(for index: Int)` compares `index <= (dotIndex ?? -1)`; the announcement uses `dotIndex` and `dotCount`.
- [ ] **Step 2:** `RecordFlowView` passes `RecordFlowChrome(dotCount: RecordStep.counted.count, dotIndex: model.step.dotIndex, canGoBack: model.step.previous != nil, onBack: { model.back() }, onClose: handleClose)`.
- [ ] **Step 3:** Build & test (the M58 suites stay green). Commit `quality(ios): let the flow chrome count any flow's steps`.

### Task 4.6: The step views and the flow container

- [ ] **Step 1: Create the six step views** in `Features/Complete/Steps/`. Each mirrors its `Record*` sibling with `model: CompleteFlowModel`:

```swift
// CompleteValenceStep.swift
import SwiftUI

struct CompleteValenceStep: View {
    let model: CompleteFlowModel

    var body: some View {
        ValencePickerContent(selected: model.draft.valence) { model.select(valence: $0) }
            .onAppear { model.seedValenceIfNeeded() }
    }
}
```

```swift
// CompleteEmotionStep.swift
import SwiftUI

struct CompleteEmotionStep: View {
    let model: CompleteFlowModel

    var body: some View {
        EmotionPickerContent(selected: model.draft.emotionId, valence: model.draft.valence) {
            model.select(emotionId: $0)
        }
    }
}
```

```swift
// CompleteDomainStep.swift
import SwiftUI

struct CompleteDomainStep: View {
    let model: CompleteFlowModel
    @Environment(ReferenceDataService.self) private var refs

    var body: some View {
        DomainPickerContent(domains: refs.domains, selected: model.draft.domainId) {
            model.select(domainId: $0)
        }
    }
}
```

```swift
// CompleteSoulsStep.swift
import SwiftUI

struct CompleteSoulsStep: View {
    let model: CompleteFlowModel
    @Environment(ReferenceDataService.self) private var refs
    @State private var isPresentingCreate = false

    var body: some View {
        SoulPickerContent(
            souls: refs.souls,
            selection: Set(model.draft.soulIds),
            onToggle: { model.toggleSoul($0) },
            onCreate: { isPresentingCreate = true }
        )
        .sheet(isPresented: $isPresentingCreate) {
            CreateSoulSheet { inserted in
                model.toggleSoul(inserted.id)
                Task { await refs.refreshSouls() }
            }
        }
    }
}
```

  `CompleteCollectionStep.swift`: `RecordCollectionStep`'s body verbatim with `model: CompleteFlowModel`. `CompletePrivacyStep.swift`: `RecordPrivacyStep`'s body verbatim with `model: CompleteFlowModel`, `model.completeError` in place of `model.publishError`, and no `snapBlockedMessage` (no photo here).

  `CompleteSuccessStep.swift`:

```swift
import SwiftUI

/// The pebble, lit: its derived glyph carved into the stone (fetched after
/// the write, A4), its name or "Untitled", and what it earned.
struct CompleteSuccessStep: View {
    let name: String?
    let result: CompletionResult
    let valence: Valence
    let emotionId: UUID?
    let onExit: () -> Void

    @Environment(EmotionPaletteService.self) private var palettes

    var body: some View {
        VStack(spacing: Spacing.xl) {
            Spacer(minLength: 0)

            PebbleStoneView(
                valence: valence,
                glyph: result.glyph,
                palette: emotionId.flatMap { palettes.palette(for: $0) },
                followsTilt: true
            )
            .frame(maxWidth: .infinity)
            .frame(height: 280)

            VStack(spacing: Spacing.sm) {
                MomentNameText(name: name)
                    .pebblesFont(.title)
                    .multilineTextAlignment(.center)

                if let karma = result.karmaDelta, karma > 0 {
                    HStack(spacing: 6) {
                        Image(systemName: "sparkle").foregroundStyle(Color.accent.primary)
                        Text("+\(karma) karma")
                            .pebblesFont(.headline)
                            .foregroundStyle(Color.system.foreground)
                    }
                    .accessibilityElement(children: .combine)
                    .accessibilityLabel(Text("Earned \(karma) karma"))
                }
            }

            Spacer(minLength: 0)

            Button("Back to my path", action: onExit)
                .buttonStyle(PebblesPrimaryButtonStyle())
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.bottom, Spacing.lg)
    }
}
```

- [ ] **Step 2: Create `CompleteStepContent.swift`:**

```swift
import SwiftUI

/// Renders the body of whichever Complete step is current. Exhaustive: a new
/// step without a body fails to compile.
struct CompleteStepContent: View {
    let step: CompleteStep
    let model: CompleteFlowModel

    var body: some View {
        switch step {
        case .valence:    CompleteValenceStep(model: model)
        case .emotion:    CompleteEmotionStep(model: model)
        case .domain:     CompleteDomainStep(model: model)
        case .souls:      CompleteSoulsStep(model: model)
        case .collection: CompleteCollectionStep(model: model)
        case .privacy:    CompletePrivacyStep(model: model)
        case .success:    EmptyView()
        }
    }
}
```

- [ ] **Step 3: Create `CompleteFlowView.swift`:**

```swift
import SwiftUI
import os

/// The Complete flow (spec §6): turns a moment into the caller's pebble.
/// Full-screen; presented from the read page and chained from Capture.
struct CompleteFlowView: View {
    let momentId: UUID
    /// For the success screen's title line.
    let momentName: String?
    /// Fired as soon as `complete_pebble` succeeds, while the success step is
    /// up, so the parent reloads behind the cover.
    let onCompleted: (UUID) -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(ReferenceDataService.self) private var refs
    @Environment(KarmaNotificationService.self) private var karma
    @Environment(AchievementsService.self) private var achievements
    @Environment(CompleteDraftStore.self) private var drafts
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dismiss) private var dismiss

    @State private var model = CompleteFlowModel()
    @State private var autosave: CompleteAutosave?
    @State private var hasRestored = false
    @State private var isMovingBack = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "complete-flow")

    var body: some View {
        VStack(spacing: 0) {
            if model.step != .success {
                RecordFlowChrome(
                    dotCount: CompleteStep.counted.count,
                    dotIndex: model.step.dotIndex,
                    canGoBack: model.step.previous != nil,
                    onBack: { model.back() },
                    onClose: close
                )
            }
            stepBody.frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(Color.system.background)
        .pebblesScreen()
        .animation(.snappy(duration: 0.28), value: model.step)
        .onChange(of: model.step) { old, new in isMovingBack = new.rawValue < old.rawValue }
        .task {
            TapHaptics.prepare()
            Task.detached(priority: .utility) {
                ValenceArt.prewarm()
                StoneCarvingArt.prewarm()
            }
            if autosave == nil {
                autosave = CompleteAutosave(
                    store: drafts, momentId: momentId,
                    ownerId: { [supabase] in supabase.session?.user.id }
                )
            }
            restoreIfReady()
        }
        // `.task` may run before the reference fetch settles (#647).
        .onChange(of: refs.hasLoaded) { _, _ in restoreIfReady() }
        .onChange(of: model.draft) { _, draft in
            guard hasRestored, model.step != .success else { return }
            autosave?.schedule(draft)
        }
        .onChange(of: scenePhase) { _, phase in
            if phase != .active { autosave?.flush() }
        }
    }

    @ViewBuilder
    private var stepBody: some View {
        Group {
            if model.step == .success, let result = model.completed {
                CompleteSuccessStep(
                    name: momentName,
                    result: result,
                    valence: model.draft.valence ?? .neutralMedium,
                    emotionId: model.draft.emotionId,
                    onExit: { dismiss() }
                )
            } else {
                RecordStepScaffold(
                    title: model.step.title,
                    subtitle: model.step.subtitle,
                    action: action(for: model.step)
                ) {
                    CompleteStepContent(step: model.step, model: model)
                }
            }
        }
        .id(model.step)
        .transition(.asymmetric(
            insertion: .move(edge: isMovingBack ? .leading : .trailing).combined(with: .opacity),
            removal: .move(edge: isMovingBack ? .trailing : .leading).combined(with: .opacity)
        ))
    }

    private func action(for step: CompleteStep) -> RecordStepAction? {
        switch step {
        case .emotion, .domain, .success:
            return nil
        case .valence:
            return .primary("Continue", enabled: model.isAnswered, loading: false) { model.advance() }
        case .souls, .collection:
            return .primary(model.optionalButtonIsSkip ? "Skip" : "Done", enabled: true, loading: false) {
                model.advance()
            }
        case .privacy:
            return .primary("Make it a pebble", enabled: model.draft.isComplete, loading: model.isCompleting) {
                Task { await complete() }
            }
        }
    }

    /// Silent restore (A3), once, after reference data has loaded.
    private func restoreIfReady() {
        guard !hasRestored, refs.hasLoaded else { return }
        hasRestored = true
        guard let owner = supabase.session?.user.id,
              let saved = drafts.load(momentId: momentId, ownerId: owner) else { return }
        model.restore(saved, known: .init(
            soulIds: Set(refs.souls.map(\.id)),
            collectionIds: Set(refs.collections.map(\.id))
        ))
    }

    /// ✕ never asks (A1): the answers are already on disk.
    private func close() {
        autosave?.flush()
        dismiss()
    }

    private func complete() async {
        guard let payload = CompletePebblePayload(momentId: momentId, draft: model.draft) else {
            model.fail(MomentWriteError.pebbleNeedsDomain.userMessage)
            return
        }
        model.beginCompleting()
        let service = MomentsService(client: supabase.client)
        do {
            let pebbleId = try await service.completePebble(payload)
            // First completion: no events existed for this pebble id (A4).
            async let glyph = try? service.resolveGlyph(pebbleId: pebbleId)
            async let earned = try? service.karmaTotal(refId: pebbleId)
            let (resolved, karmaDelta) = await (glyph, earned)
            karma.notifyEarned(amount: karmaDelta ?? 0, reason: .pebbleCreated, presentsCapsule: false)
            achievements.fireCheck()
            autosave?.clear()
            model.succeed(with: CompletionResult(pebbleId: pebbleId, karmaDelta: karmaDelta, glyph: resolved))
            onCompleted(momentId)
        } catch {
            logger.error("complete_pebble failed: \(error.localizedDescription, privacy: .private)")
            model.fail(userMessageForMomentWriteError(error))
        }
    }
}
```

  Note: `try?` flattens, so `resolved` is `PebbleGlyph?` and `karmaDelta` is `Int?`.
- [ ] **Step 4:** Build & test. Commit `feat(ios): complete a moment into a pebble`.

### Task 4.7: Wire the store, the read page call to action, and deletes

- [ ] **Step 1: Environment.** `PebblesApp.swift`: `@State private var completeDrafts: CompleteDraftStore` initialised `CompleteDraftStore()`, injected with `.environment(completeDrafts)` beside `snapshots`. `RootView.swift`: read it from the environment, pass it to `SignedOutPurge(…, completeDrafts: completeDrafts, …)`, and add `.environment(CompleteDraftStore())` to its preview.
- [ ] **Step 2: `SignedOutPurge`:** add `let completeDrafts: CompleteDraftStore` and `completeDrafts.clearAll()` in `run()`. In `SignedOutPurgeTests.swift`, construct it with a temp-dir store, save a draft, run, and assert `load` returns nil.
- [ ] **Step 3: Clear on delete (A3).** `MomentDeletion.deleteMoment(_:client:drafts:)` gains `drafts: CompleteDraftStore? = nil` and calls `drafts?.clear(momentId: momentId)` after the RPC succeeds; `perform(_:on:client:drafts:)` threads it through. Every host (`PathView`, `SoulDetailView`, `CollectionDetailView`, `PebbleDetailSheet`) reads `@Environment(CompleteDraftStore.self) private var completeDrafts` and passes it.
- [ ] **Step 4: Read page call to action.** `PebbleReadView` gains `var onComplete: (() -> Void)? = nil` and, under the description, for a bare moment:

```swift
                if detail.pebble == nil, let onComplete {
                    Button("Make it a pebble", action: onComplete)
                        .buttonStyle(PebblesPrimaryButtonStyle())
                        .padding(.top, Spacing.sm)
                }
```

  `PebbleDetailSheet`:

```swift
    @State private var isPresentingComplete = false
    …
            PebbleReadView(detail: detail, palette: …, onComplete: { isPresentingComplete = true })
    …
        .fullScreenCover(isPresented: $isPresentingComplete) {
            CompleteFlowView(
                momentId: momentId,
                momentName: detail?.name,
                onCompleted: { _ in
                    Task { await load() }
                    onPebbleUpdated?()
                }
            )
        }
```

- [ ] **Step 5:** Add `Make it a pebble` / `En faire un galet` to the catalog.
- [ ] **Step 6: Look at it.** Simulator, test account: open a bare moment → **Make it a pebble** → step through (haptics on device) → success shows the lit stone carving the domain's glyph and `+N karma` → **Back to my path** → the read page now shows the completed pebble. Kill the app mid-flow at the souls step, reopen the same moment's flow: it lands on the first unanswered mandatory step with the answers restored. Check an achievement unlock presents over the success screen when one is due (fresh test account: the first pebble).
- [ ] **Step 7:** Build & test; commit `feat(ios): offer to make a bare moment a pebble`; push; PR `feat(ios): complete a moment into a pebble`, `Part 4 of the #1047 stack.`, labels `feat`, `ios`, `ui`, `no-lab-note`.

---
## Part 5 — the Capture flow (`feat/1047-ios-moments-capture`)

**Outcome:** `+` opens a three-step Capture (`photo · when · name`) that saves a moment with `save_moment` and returns to the Path, or, with **Make it a pebble now**, swaps the same cover to the Complete flow for the new moment (A1). The long-press escape hatch to the old sheet is removed.

**Files**
- Create: `Pebbles/Features/Capture/CaptureStep.swift`, `CaptureStep+Copy.swift`, `CaptureDraft.swift`, `CaptureFlowModel.swift`, `CaptureFlowView.swift`
- Create tests: `PebblesTests/Features/Capture/CaptureStepTests.swift`, `CaptureFlowModelTests.swift`, `SaveMomentPayloadCaptureTests.swift`
- Modify: `Features/Path/Models/SaveMomentPayload.swift`, `Features/Record/RecordStepScaffold.swift`, `Features/Path/PathView.swift`, `Features/Path/Components/NewPebbleButton.swift`, `Resources/Localizable.xcstrings`

Reused unchanged: `RecordPhotoStep`, `RecordWhenStep`, `RecordNameStep` (they take bindings and closures, not a model), `SnapUploadCoordinator`, `ExifCaptureDate`.

### Task 5.0: Branch

- [ ] `gh stack add feat/1047-ios-moments-capture`

### Task 5.1: `CaptureStep`, `CaptureDraft`, and the create payload

- [ ] **Step 1: Write the failing tests.** `CaptureStepTests.swift`:

```swift
import Testing
@testable import Pebbles

@Suite("CaptureStep")
struct CaptureStepTests {
    @Test("photo · when · name, three dots, photo optional")
    func order() {
        #expect(CaptureStep.allCases == [.photo, .when, .name])
        #expect(CaptureStep.allCases.map(\.dotIndex) == [0, 1, 2])
        #expect(CaptureStep.allCases.filter(\.isOptional) == [.photo])
        #expect(CaptureStep.name.next == nil)
        #expect(CaptureStep.photo.previous == nil)
    }
}
```

`SaveMomentPayloadCaptureTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("SaveMomentPayload from a capture")
struct SaveMomentPayloadCaptureTests {

    private let userId = UUID(uuidString: "5F0E1D2C-3B4A-4958-8776-655443322110")!

    @Test("a named capture without a photo: no id, no description, no snaps")
    func named() {
        var draft = CaptureDraft()
        draft.name = "  Coffee  "
        draft.happenedAt = Date(timeIntervalSince1970: 1_791_793_800)
        let payload = SaveMomentPayload(capture: draft, formSnap: nil, userId: userId)
        #expect(payload.id == nil)
        #expect(payload.name == "Coffee")
        #expect(payload.description == nil)
        #expect(payload.snaps == nil)
        #expect(payload.happenedAt == draft.happenedAt)
    }

    @Test("a photo-only capture omits the name and sends the snap")
    func photoOnly() {
        let snapId = UUID()
        let payload = SaveMomentPayload(
            capture: CaptureDraft(),
            formSnap: .existing(id: snapId, storagePath: "u/s"),
            userId: userId
        )
        #expect(payload.name == nil)
        #expect(payload.snaps == [.init(id: snapId, storagePath: "u/s", sortOrder: 0)])
    }
}
```

- [ ] **Step 2:** Run to verify they fail.
- [ ] **Step 3: Create `CaptureStep.swift`:**

```swift
import Foundation

/// The Capture flow's screens (spec §6, A1). No success screen: saving
/// returns to the Path, or chains into Complete.
enum CaptureStep: Int, CaseIterable, Identifiable, Hashable {
    case photo
    case when
    case name

    var id: Int { rawValue }
    var isOptional: Bool { self == .photo }
    var dotIndex: Int { rawValue }
    var next: CaptureStep? { CaptureStep(rawValue: rawValue + 1) }
    var previous: CaptureStep? { rawValue == 0 ? nil : CaptureStep(rawValue: rawValue - 1) }
}
```

  `CaptureStep+Copy.swift`:

```swift
import Foundation

extension CaptureStep {
    var title: LocalizedStringResource {
        switch self {
        case .photo: return "Start with a picture"
        case .when:  return "When did it happen?"
        case .name:  return "What do you call it?"
        }
    }

    var subtitle: LocalizedStringResource? {
        switch self {
        case .photo: return "Or skip it and write from memory."
        case .when:  return nil
        case .name:  return "A name, a photo, or both."
        }
    }
}
```

  `CaptureDraft.swift`:

```swift
import Foundation

/// What Capture asks (spec D3): a date, plus a name or a photo. The photo
/// lives on the `SnapUploadCoordinator`, not here.
struct CaptureDraft: Equatable {
    var happenedAt = Date()
    var name = ""
}
```

  Add to `SaveMomentPayload.swift`:

```swift
extension SaveMomentPayload {
    /// A new moment from Capture (A2): no id, the name only when non-blank,
    /// no description, the photo only when there is one.
    init(capture draft: CaptureDraft, formSnap: FormSnap?, userId: UUID) {
        self.init()
        name = MomentName.display(draft.name)
        happenedAt = draft.happenedAt
        let snaps = Self.snaps(for: formSnap, userId: userId)
        self.snaps = snaps.isEmpty ? nil : snaps
    }
}
```

- [ ] **Step 4:** Run: PASS. Commit `feat(ios): define the capture flow's steps and payload`.

### Task 5.2: `CaptureFlowModel`

- [ ] **Step 1: Write the failing test** `CaptureFlowModelTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("CaptureFlowModel")
@MainActor
struct CaptureFlowModelTests {

    private final class Recorder { var taps: [TapHaptic] = [] }

    private func model() -> (CaptureFlowModel, Recorder) {
        let recorder = Recorder()
        return (CaptureFlowModel(haptic: { recorder.taps.append($0) }), recorder)
    }

    @Test("photo and when always advance; name is the last step")
    func advance() {
        let (model, recorder) = model()
        #expect(model.optionalButtonIsSkip)
        model.advance()
        #expect(model.step == .when)
        model.advance()
        #expect(model.step == .name)
        model.advance()
        #expect(model.step == .name)
        #expect(recorder.taps == [.advance, .advance])
    }

    @Test("savable with a name or a photo, not with neither (D3)")
    func canSave() {
        let (model, _) = model()
        #expect(!model.canSave)
        model.setName("   ")
        #expect(!model.canSave)
        model.hasSnap = true
        #expect(model.canSave)
        model.hasSnap = false
        model.setName("Coffee")
        #expect(model.canSave)
    }

    @Test("the name is clamped to 40 characters")
    func clamp() {
        let (model, _) = model()
        model.setName(String(repeating: "a", count: 55))
        #expect(model.draft.name.count == CaptureFlowModel.nameLimit)
    }

    @Test("the photo's capture date seeds when; nil leaves now")
    func captureDate() {
        let (model, _) = model()
        let before = model.draft.happenedAt
        model.applyCaptureDate(nil)
        #expect(model.draft.happenedAt == before)
        let exif = Date(timeIntervalSince1970: 1_700_000_000)
        model.applyCaptureDate(exif)
        #expect(model.draft.happenedAt == exif)
    }

    @Test("save outcomes: success haptic, failure keeps the error and stays")
    func outcomes() {
        let (model, recorder) = model()
        model.go(to: .name)
        model.beginSaving()
        #expect(model.isSaving)
        model.fail("nope")
        #expect(!model.isSaving)
        #expect(model.saveError == "nope")
        model.beginSaving()
        #expect(model.saveError == nil)
        model.succeed()
        #expect(recorder.taps == [.warning, .success])
    }
}
```

- [ ] **Step 2:** Run to verify it fails.
- [ ] **Step 3: Create `CaptureFlowModel.swift`:**

```swift
import Foundation
import Observation

/// The Capture flow's state machine (M58 D4 discipline: every interaction is
/// a method here and fires its haptic).
@Observable
@MainActor
final class CaptureFlowModel {

    /// Front-end only, as in the M58 flow.
    static let nameLimit = 40

    var draft = CaptureDraft()
    private(set) var step: CaptureStep = .photo
    /// Mirrored from the `SnapUploadCoordinator` by the view.
    var hasSnap = false
    private(set) var isSaving = false
    private(set) var saveError: String?

    private let haptic: @MainActor (TapHaptic) -> Void

    init(haptic: @escaping @MainActor (TapHaptic) -> Void = { TapHaptics.play($0) }) {
        self.haptic = haptic
    }

    /// D3: a moment needs a name or a photo.
    var canSave: Bool { hasSnap || MomentName.display(draft.name) != nil }

    /// `Skip` on the photo step until a photo is picked, then `Done`.
    var optionalButtonIsSkip: Bool { step == .photo && !hasSnap }

    func advance() {
        guard let next = step.next else { return }
        haptic(.advance)
        step = next
    }

    func back() {
        guard let previous = step.previous else { return }
        haptic(.advance)
        step = previous
    }

    func go(to step: CaptureStep) {
        self.step = step
    }

    func setName(_ raw: String) {
        draft.name = String(raw.prefix(Self.nameLimit))
    }

    func applyCaptureDate(_ date: Date?) {
        guard let date else { return }
        draft.happenedAt = date
    }

    func beginSaving() {
        isSaving = true
        saveError = nil
    }

    func succeed() {
        haptic(.success)
        isSaving = false
    }

    func fail(_ message: String) {
        haptic(.warning)
        isSaving = false
        saveError = message
    }
}
```

- [ ] **Step 4:** Run: PASS. Commit `feat(ios): drive the capture flow from one model`.

### Task 5.3: A secondary action on the step scaffold

- [ ] **Step 1:** In `RecordStepScaffold.swift`, add beside `RecordStepAction`:

```swift
/// A quieter text button under the primary one. Capture's name step uses it
/// for "Make it a pebble now" (A1).
struct RecordStepSecondaryAction {
    let label: LocalizedStringResource
    let enabled: Bool
    let perform: () -> Void
}
```

  add `var secondaryAction: RecordStepSecondaryAction? = nil` after `contentFillsHeight`, and in the non-filling branch replace the action block with:

```swift
                if let action {
                    VStack(spacing: Spacing.xs) {
                        actionView(action)
                        if let secondaryAction {
                            Button(action: secondaryAction.perform) {
                                Text(secondaryAction.label)
                                    .pebblesFont(.bodyEmphasized)
                                    .foregroundStyle(Color.accent.primary)
                                    .frame(maxWidth: .infinity, minHeight: 44)
                            }
                            .buttonStyle(.plain)
                            .disabled(!secondaryAction.enabled)
                            .opacity(secondaryAction.enabled ? 1 : 0.4)
                        }
                    }
                    .padding(.horizontal, Spacing.lg)
                    .padding(.bottom, Spacing.sm)
                }
```

  Existing callers pass nothing new and render as before.
- [ ] **Step 2:** Build & test. Commit `feat(ios): allow a secondary action under a flow step`.

### Task 5.4: `CaptureFlowView`

- [ ] **Step 1: Create `CaptureFlowView.swift`:**

```swift
import PhotosUI
import SwiftUI
import os

/// The Capture flow (spec §6, A1): photo · when · name → `save_moment`.
/// "Make it a pebble now" saves, then swaps this same cover to the Complete
/// flow for the new moment, so there is never a second presentation.
struct CaptureFlowView: View {
    /// Fired after the moment is saved and again after it is completed, so
    /// the Path reloads and focuses its week (A5).
    let onMomentChanged: (UUID) -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var model = CaptureFlowModel()
    @State private var snaps: SnapUploadCoordinator?
    @State private var pickedPhoto: PhotosPickerItem?
    @State private var isCloseConfirmPresented = false
    @State private var isMovingBack = false
    @State private var completing: CompleteTarget?

    private struct CompleteTarget: Equatable {
        let momentId: UUID
        let name: String?
    }

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "capture-flow")

    private var currentUserId: UUID? { supabase.session?.user.id }

    private var snapBlockedMessage: String? {
        if snaps?.isUploading == true { return String(localized: "Photo is still uploading.") }
        if snaps?.hasFailed == true { return String(localized: "Photo upload failed. Retry or remove it.") }
        return nil
    }

    private var canSaveNow: Bool { model.canSave && snapBlockedMessage == nil && !model.isSaving }

    var body: some View {
        Group {
            if let completing {
                CompleteFlowView(
                    momentId: completing.momentId,
                    momentName: completing.name,
                    onCompleted: onMomentChanged
                )
            } else {
                capture
            }
        }
        .task {
            TapHaptics.prepare()
            if snaps == nil {
                snaps = SnapUploadCoordinator(repo: PebbleSnapRepository(client: supabase.client))
            }
        }
    }

    private var capture: some View {
        VStack(spacing: 0) {
            RecordFlowChrome(
                dotCount: CaptureStep.allCases.count,
                dotIndex: model.step.dotIndex,
                canGoBack: model.step.previous != nil,
                onBack: { model.back() },
                onClose: handleClose
            )
            stepBody.frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(Color.system.background)
        .pebblesScreen()
        .animation(.snappy(duration: 0.28), value: model.step)
        .onChange(of: model.step) { old, new in isMovingBack = new.rawValue < old.rawValue }
        .onChange(of: snaps?.formSnap != nil) { _, hasSnap in model.hasSnap = hasSnap }
        .onChange(of: pickedPhoto) { _, picked in
            guard let picked, let userId = currentUserId, let snaps else { return }
            Task {
                await snaps.handlePicked(picked, userId: userId)
                model.applyCaptureDate(snaps.pickedCaptureDate)
            }
        }
        .confirmationDialog("Keep this moment?", isPresented: $isCloseConfirmPresented, titleVisibility: .visible) {
            if canSaveNow {
                Button("Save moment") { Task { await save(thenComplete: false) } }
            }
            Button("Discard", role: .destructive) { Task { await discard() } }
            Button("Keep going", role: .cancel) {}
        }
    }

    @ViewBuilder
    private var stepBody: some View {
        RecordStepScaffold(
            title: model.step.title,
            subtitle: model.step.subtitle,
            action: action(for: model.step),
            contentFillsHeight: model.step == .photo,
            secondaryAction: model.step == .name
                ? RecordStepSecondaryAction(label: "Make it a pebble now", enabled: canSaveNow) {
                    Task { await save(thenComplete: true) }
                }
                : nil
        ) {
            switch model.step {
            case .photo:
                RecordPhotoStep(
                    snap: snaps?.formSnap,
                    selection: $pickedPhoto,
                    onRetry: {
                        if let userId = currentUserId, let snaps { Task { await snaps.retryCurrent(userId: userId) } }
                    },
                    onRemove: {
                        pickedPhoto = nil
                        if let userId = currentUserId, let snaps { Task { await snaps.removePending(userId: userId) } }
                    }
                )
            case .when:
                RecordWhenStep(
                    happenedAt: Binding(get: { model.draft.happenedAt }, set: { model.draft.happenedAt = $0 }),
                    seededFromPhoto: snaps?.pickedCaptureDate != nil
                )
            case .name:
                VStack(spacing: Spacing.md) {
                    RecordNameStep(
                        name: model.draft.name,
                        limit: CaptureFlowModel.nameLimit,
                        onChange: { model.setName($0) }
                    )
                    if let blocked = snapBlockedMessage {
                        Text(verbatim: blocked)
                            .pebblesFont(.subhead)
                            .foregroundStyle(Color.system.secondary)
                    }
                    if let error = model.saveError {
                        Text(verbatim: error)
                            .pebblesFont(.callout)
                            .foregroundStyle(.red) // #921
                            .multilineTextAlignment(.center)
                    }
                }
            }
        }
        .id(model.step)
        .transition(.asymmetric(
            insertion: .move(edge: isMovingBack ? .leading : .trailing).combined(with: .opacity),
            removal: .move(edge: isMovingBack ? .trailing : .leading).combined(with: .opacity)
        ))
    }

    private func action(for step: CaptureStep) -> RecordStepAction {
        switch step {
        case .photo:
            return .primary(model.optionalButtonIsSkip ? "Skip" : "Done", enabled: true, loading: false) {
                model.advance()
            }
        case .when:
            return .primary("Continue", enabled: true, loading: false) { model.advance() }
        case .name:
            return .primary("Save moment", enabled: canSaveNow, loading: model.isSaving) {
                Task { await save(thenComplete: false) }
            }
        }
    }

    /// Only asks when there is something to keep (A1).
    private func handleClose() {
        if model.canSave {
            isCloseConfirmPresented = true
        } else {
            Task { await discard() }
        }
    }

    private func discard() async {
        if let userId = currentUserId, let snaps {
            await snaps.cancelAndCleanup(userId: userId)
        }
        dismiss()
    }

    private func save(thenComplete: Bool) async {
        guard let userId = currentUserId else {
            model.fail(MomentWriteError.notAuthenticated.userMessage)
            return
        }
        if let blocked = snapBlockedMessage {
            model.fail(blocked)
            return
        }
        model.beginSaving()
        let payload = SaveMomentPayload(capture: model.draft, formSnap: snaps?.formSnap, userId: userId)
        do {
            let momentId = try await MomentsService(client: supabase.client).saveMoment(payload)
            model.succeed()
            onMomentChanged(momentId)
            if thenComplete {
                completing = CompleteTarget(momentId: momentId, name: MomentName.display(model.draft.name))
            } else {
                dismiss()
            }
        } catch {
            // A2: the uploaded photo stays so a retry can use it.
            logger.error("save_moment failed: \(error.localizedDescription, privacy: .private)")
            model.fail(userMessageForMomentWriteError(error))
        }
    }
}
```

  `RecordStepScaffold`'s memberwise init takes `secondaryAction:` after `contentFillsHeight:` (declaration order).
- [ ] **Step 2:** Add the **copy catalog** capture strings (`A name, a photo, or both.`, `Save moment`, `Make it a pebble now`, `Keep this moment?`) to the catalog.
- [ ] **Step 3:** Build & test. Commit `feat(ios): capture a moment in three steps`.

### Task 5.5: `+` opens Capture

- [ ] **Step 1: `NewPebbleButton.swift`:** delete `onLongPress` and the `.simultaneousGesture` (the M58 D1 escape hatch: the sheet it opened is deleted in Part 6).
- [ ] **Step 2: `PathView.swift`:** replace `isPresentingFlow` / `isPresentingCreate` and their two presentations with:

```swift
    @State private var isPresentingCapture = false
    …
        .fullScreenCover(isPresented: $isPresentingCapture) {
            CaptureFlowView(onMomentChanged: { momentId in
                Task {
                    async let timeline: Void = load()
                    async let statsReload: Void = stats.refresh()
                    _ = await (timeline, statsReload)
                    focusWeek(containing: momentId)
                }
            })
        }
```

  `NewPebbleButton(onTap: { isPresentingCapture = true })`; `WeekPathView(onCreate: { isPresentingCapture = true })`. `RecordFlowView` and `CreatePebbleSheet` are now unreachable from the Path; `DraftsListSheet` still presents `RecordFlowView` behind a button that never shows (count 0). All three go in Part 6.
- [ ] **Step 3: Look at it.** Simulator: `+` → skip photo → date → name "Coffee" → **Save moment** → back on the Path with a blank stone in today's week. `+` → pick a photo (EXIF date seeds the when step) → skip the name → **Make it a pebble now** → Complete opens in the same cover → finish → **Back to my path** → the row is now a carved stone. ✕ on the name step with a photo picked → "Keep this moment?" with Save/Discard/Keep going; Discard deletes the files (check Storage in the dashboard for the snap prefix).
- [ ] **Step 4:** Build & test; commit `feat(ios): open capture from the path`; push; PR `feat(ios): capture a moment, then make it a pebble`, `Part 5 of the #1047 stack.`, labels `feat`, `ios`, `ui`, `no-lab-note`.

---
## Part 6 — retire the old composer, server drafts and compat writers; split editing (`feat/1047-ios-moments-retire-composer`)

**Outcome:** no compat object is named anywhere in the app. Editing splits in two (R5): **Edit moment** (form → `save_moment`) and **Edit pebble** (form → `complete_pebble`), both from the read page's `…` menu.

**Files**
- Create: `Pebbles/Features/Path/EditMomentSheet.swift`, `Pebbles/Features/Path/Models/EditMomentDraft.swift`, `PebblesTests/Moments/EditMomentDraftTests.swift`, `PebblesTests/Moments/CompleteDraftFromPebbleTests.swift`
- Rewrite: `Pebbles/Features/Path/EditPebbleSheet.swift`, `Pebbles/Features/Path/PebbleFormView.swift`
- Modify: `Features/Path/Models/CompleteDraft.swift`, `Features/Path/Models/SaveMomentPayload.swift`, `Features/Path/PebbleDetailSheet.swift`, `Features/Path/PathView.swift`, `Services/SignedOutPurge.swift`, `PebblesApp.swift`, `RootView.swift`, `PebblesTests/SignedOutPurgeTests.swift`, `PebblesTests/ValenceRollTests.swift`, `Features/PebbleMedia/SnapUploadCoordinator.swift` (comment), `Features/PebbleMedia/Models/FormSnap.swift` (comment), `Resources/Localizable.xcstrings`
- Delete (app): `Features/Path/CreatePebbleSheet.swift`, `CreatePebbleSheet+Drafts.swift`, `DraftsListSheet.swift`, `ComposerDraftCoordinator.swift`, `PebblePublisher.swift`; `Features/Path/Models/ComposePebbleResponse.swift`, `PebbleCreatePayload.swift`, `PebbleUpdatePayload.swift`, `PebbleDraftPayload.swift`, `PebbleDraft.swift`; `Services/PebbleDraftsService.swift`, `Services/ComposerSnapshotStore.swift`; `Features/Record/RecordFlowView.swift`, `RecordFlowView+Drafts.swift`, `RecordFlowModel.swift`, `RecordStep.swift`, `RecordStep+Copy.swift`, `RecordStepContent.swift`, `Steps/RecordValenceStep.swift`, `RecordEmotionStep.swift`, `RecordDomainStep.swift`, `RecordSoulsStep.swift`, `RecordCollectionStep.swift`, `RecordGlyphStep.swift`, `RecordPrivacyStep.swift`, `RecordSuccessStep.swift`
- Delete (tests): `ComposePebbleResponseDecodingTests.swift`, `ComposerSnapshotStoreTests.swift`, `DraftCrossSurfaceDecodingTests.swift`, `PebbleCreatePayloadEncodingTests.swift`, `PebbleDraftPayloadTests.swift`, `PebbleUpdatePayloadEncodingTests.swift`, `Moments/PebbleDraftFromMomentDetailTests.swift`, `Features/Record/ComposerDraftCoordinatorTests.swift`, `Features/Record/PebblePublisherTests.swift`, `Features/Record/RecordFlowModelTests.swift`, `Features/Record/RecordStepTests.swift`
- Kept in `Features/Record/` (shared by Capture and Complete): `RecordStepScaffold.swift`, `RecordFlowChrome.swift`, `Steps/RecordPhotoStep.swift`, `Steps/RecordWhenStep.swift`, `Steps/RecordNameStep.swift`. Kept tests: `TapHapticsTests`, `ExifCaptureDateTests`, `DomainWithGlyphDecodingTests`.

### Task 6.0: Branch

- [ ] `gh stack add feat/1047-ios-moments-retire-composer`

### Task 6.1: Edit pebble on `complete_pebble`

- [ ] **Step 1: Write the failing test** `CompleteDraftFromPebbleTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("CompleteDraft init(from: MomentDetail.Pebble)")
struct CompleteDraftFromPebbleTests {

    @Test("prefills every pebble field, first domain and first collection (R5)")
    func prefill() throws {
        let detail = try JSONDecoder().decode(MomentDetail.self, from: MomentFixtures.completedMoment)
        let pebble = try #require(detail.pebble)
        let draft = CompleteDraft(from: pebble)
        #expect(draft.valence == .highlightMedium)
        #expect(draft.emotionId == MomentFixtures.uuid(MomentFixtures.joyId))
        #expect(draft.domainId == MomentFixtures.uuid(MomentFixtures.domainId))
        #expect(draft.soulIds == [MomentFixtures.uuid(MomentFixtures.soulId)])
        #expect(draft.collectionId == MomentFixtures.uuid(MomentFixtures.collectionId))
        #expect(draft.visibility == .private)
        #expect(draft.isComplete)
    }

    @Test("a legacy pebble with no domain is not complete until one is picked")
    func legacy() throws {
        let detail = try JSONDecoder().decode(MomentDetail.self, from: MomentFixtures.legacyMoment)
        let draft = CompleteDraft(from: try #require(detail.pebble))
        #expect(draft.domainId == nil)
        #expect(!draft.isComplete)
    }
}
```

- [ ] **Step 2:** Run to verify it fails.
- [ ] **Step 3:** Add to `CompleteDraft.swift`:

```swift
extension CompleteDraft {
    /// Prefill Edit pebble from the caller's pebble (R5). Keeps the first
    /// domain and collection, as the pre-split edit did.
    init(from pebble: MomentDetail.Pebble) {
        self.init()
        valence = pebble.valence
        emotionId = pebble.emotion.id
        domainId = pebble.domains.first?.id
        soulIds = pebble.souls.map(\.id)
        collectionId = pebble.collections.first?.id
        visibility = pebble.visibility
    }
}
```

- [ ] **Step 4: Rewrite `PebbleFormView.swift`** to the pebble's fields only: `@Binding var draft: CompleteDraft`, `let domains: [Domain]`, `let souls: [SoulWithGlyph]`, `let collections: [PebbleCollection]`, `let saveError: String?`. Keep the existing **Mood** section (emotion row + `EmotionPickerSheet`, domain menu picker, valence row + `ValencePickerSheet`), the **Souls** section (`SelectedSoulsRow(soulIds: $draft.soulIds, allSouls: souls)`), the **Optional** collection picker, and the error row, verbatim. Delete the render preview, the When/Name/Description section, the Glyph section and its `GlyphPickerSheet`, the Photo section, and every init parameter that fed them. Update the header comment: "The pebble `Form` body for Edit pebble: emotion, domain, valence, souls, collection. The moment's fields edit in `EditMomentSheet`."
- [ ] **Step 5: Rewrite `EditPebbleSheet.swift`:**

```swift
import SwiftUI
import os

/// Edit pebble (R5): the caller's pebble on a moment, saved through
/// `complete_pebble`. The moment's fields edit in `EditMomentSheet`.
struct EditPebbleSheet: View {
    let momentId: UUID
    let pebble: MomentDetail.Pebble
    let onSaved: () -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(ReferenceDataService.self) private var refs
    @Environment(KarmaNotificationService.self) private var karma
    @Environment(AchievementsService.self) private var achievements
    @Environment(\.dismiss) private var dismiss

    @State private var draft: CompleteDraft
    @State private var isSaving = false
    @State private var saveError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "edit-pebble")

    init(momentId: UUID, pebble: MomentDetail.Pebble, onSaved: @escaping () -> Void) {
        self.momentId = momentId
        self.pebble = pebble
        self.onSaved = onSaved
        self._draft = State(initialValue: CompleteDraft(from: pebble))
    }

    var body: some View {
        NavigationStack {
            PebbleFormView(
                draft: $draft,
                domains: refs.domains,
                souls: refs.souls,
                collections: refs.collections,
                saveError: saveError
            )
            .pebblesToolbarTitle("Edit pebble")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    PebbleToolbarButton("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if isSaving {
                        ProgressView()
                    } else {
                        PebbleToolbarButton("Save") { Task { await save() } }
                            .disabled(!draft.isComplete)
                    }
                }
                ToolbarItemGroup(placement: .bottomBar) {
                    VisibilityChip(visibility: $draft.visibility)
                    Spacer()
                }
            }
            .pebblesScreen()
        }
    }

    private func save() async {
        guard let payload = CompletePebblePayload(momentId: momentId, draft: draft) else { return }
        isSaving = true
        saveError = nil
        let service = MomentsService(client: supabase.client)
        do {
            // A4: what this edit earned = total after − total before.
            let before = (try? await service.karmaTotal(refId: pebble.id)) ?? 0
            _ = try await service.completePebble(payload)
            let after = (try? await service.karmaTotal(refId: pebble.id)) ?? before
            karma.notifyEarned(amount: after - before, reason: .pebbleEnriched)
            // An edit can change the emotion, newly qualifying an emotion badge.
            achievements.fireCheck()
            onSaved()
            dismiss()
        } catch {
            logger.error("complete_pebble (edit) failed: \(error.localizedDescription, privacy: .private)")
            saveError = userMessageForMomentWriteError(error)
            isSaving = false
        }
    }
}
```

- [ ] **Step 6:** Build the app target. Expected failure only where the old composers use `PebbleFormView`'s old init / `PebbleDraft` (they are deleted in Task 6.3). Do Task 6.2 and 6.3, then build.

### Task 6.2: Edit moment on `save_moment`

- [ ] **Step 1: Write the failing test** `EditMomentDraftTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("EditMomentDraft and its payload")
struct EditMomentDraftTests {

    private let userId = UUID(uuidString: "5F0E1D2C-3B4A-4958-8776-655443322110")!

    @Test("prefills from the moment; null name and description become empty")
    func prefill() throws {
        let photoOnly = try JSONDecoder().decode(MomentDetail.self, from: MomentFixtures.photoOnlyMoment)
        let draft = EditMomentDraft(from: photoOnly)
        #expect(draft.name == "")
        #expect(draft.description == "")
        #expect(draft.happenedAt == photoOnly.happenedAt)
    }

    @Test("savable with a name or a photo (D3)")
    func canSave() {
        var draft = EditMomentDraft(happenedAt: .now, name: " ", description: "")
        #expect(!draft.canSave(hasSnap: false))
        #expect(draft.canSave(hasSnap: true))
        draft.name = "Coffee"
        #expect(draft.canSave(hasSnap: false))
    }

    @Test("blank name and description are sent as \"\" to clear (A2); snaps always sent")
    func payload() {
        let momentId = UUID()
        let draft = EditMomentDraft(happenedAt: Date(timeIntervalSince1970: 1_791_793_800), name: "  ", description: " \n")
        let payload = SaveMomentPayload(edit: draft, momentId: momentId, formSnap: nil, userId: userId)
        #expect(payload.id == momentId)
        #expect(payload.name == "")
        #expect(payload.description == "")
        #expect(payload.snaps == [])
        #expect(payload.happenedAt == draft.happenedAt)
    }

    @Test("the name is clamped to 40 like Capture")
    func clamp() {
        var draft = EditMomentDraft(happenedAt: .now, name: "", description: "")
        draft.setName(String(repeating: "b", count: 50))
        #expect(draft.name.count == 40)
    }
}
```

- [ ] **Step 2:** Run to verify it fails.
- [ ] **Step 3: Create `Models/EditMomentDraft.swift`:**

```swift
import Foundation

/// Edit moment's form state (R5). The photo lives on the coordinator.
struct EditMomentDraft: Equatable {
    var happenedAt: Date
    var name: String
    var description: String

    init(happenedAt: Date, name: String, description: String) {
        self.happenedAt = happenedAt
        self.name = name
        self.description = description
    }

    init(from detail: MomentDetail) {
        self.init(happenedAt: detail.happenedAt, name: detail.name ?? "", description: detail.description ?? "")
    }

    func canSave(hasSnap: Bool) -> Bool {
        hasSnap || MomentName.display(name) != nil
    }

    mutating func setName(_ raw: String) {
        name = String(raw.prefix(CaptureFlowModel.nameLimit))
    }
}

extension SaveMomentPayload {
    /// An edit (A2): every moment field sent, blanks as "" (null cannot
    /// clear), and the full snap set (`[]` removes the photo).
    init(edit draft: EditMomentDraft, momentId: UUID, formSnap: FormSnap?, userId: UUID) {
        self.init()
        id = momentId
        name = draft.name.trimmingCharacters(in: .whitespacesAndNewlines)
        description = draft.description.trimmingCharacters(in: .whitespacesAndNewlines)
        happenedAt = draft.happenedAt
        snaps = Self.snaps(for: formSnap, userId: userId)
    }
}
```

- [ ] **Step 4: Create `EditMomentSheet.swift`:**

```swift
import SwiftUI
import os

/// Edit moment (R5): when, name, description, photo → `save_moment`.
struct EditMomentSheet: View {
    let detail: MomentDetail
    let onSaved: () -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var draft: EditMomentDraft
    @State private var snaps: SnapUploadCoordinator?
    @State private var isPhotoPickerPresented = false
    @State private var isSaving = false
    @State private var saveError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "edit-moment")

    init(detail: MomentDetail, onSaved: @escaping () -> Void) {
        self.detail = detail
        self.onSaved = onSaved
        self._draft = State(initialValue: EditMomentDraft(from: detail))
    }

    private var currentUserId: UUID? { supabase.session?.user.id }
    private var hasSnap: Bool { snaps?.formSnap != nil }
    private var snapBlocked: Bool { snaps?.isBlocking == true }
    private var canSave: Bool { draft.canSave(hasSnap: hasSnap) && !snapBlocked }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    DatePicker("When", selection: $draft.happenedAt, displayedComponents: [.date, .hourAndMinute])
                        .tint(Color.accent.primary)
                        .pebblesListRow(position: .top)
                    TextField("Name", text: Binding(get: { draft.name }, set: { draft.setName($0) }))
                        .pebblesListRow(position: .middle)
                    TextField("Description (optional)", text: $draft.description, axis: .vertical)
                        .lineLimit(1...5)
                        .pebblesListRow(position: .bottom)
                }

                Section {
                    photoRow
                } header: {
                    Text("Photo").pebblesSectionHeader()
                } footer: {
                    if !draft.canSave(hasSnap: hasSnap) {
                        Text("A moment needs a name or a photo.")
                    }
                }

                if let saveError {
                    Section {
                        Text(saveError)
                            .foregroundStyle(.red) // #921
                            .font(.callout)
                            .pebblesListRow(position: .only)
                    }
                }
            }
            .pebblesList()
            .pebblesToolbarTitle("Edit moment")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    PebbleToolbarButton("Cancel") { Task { await cancel() } }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if isSaving {
                        ProgressView()
                    } else {
                        PebbleToolbarButton("Save") { Task { await save() } }
                            .disabled(!canSave)
                    }
                }
            }
            .pebblesScreen()
        }
        .task {
            guard snaps == nil else { return }
            let coordinator = SnapUploadCoordinator(repo: PebbleSnapRepository(client: supabase.client))
            if let existing = detail.snaps.first {
                coordinator.seedExisting(.existing(id: existing.id, storagePath: existing.storagePath))
            }
            snaps = coordinator
        }
        .sheet(isPresented: $isPhotoPickerPresented) {
            PhotoPickerView { picked in
                isPhotoPickerPresented = false
                if let picked, let userId = currentUserId, let snaps {
                    Task { await snaps.handlePicked(picked, userId: userId) }
                }
            }
        }
    }

    @ViewBuilder
    private var photoRow: some View {
        switch snaps?.formSnap {
        case .none:
            Button { isPhotoPickerPresented = true } label: {
                Label("Add a photo", systemImage: "photo.badge.plus")
            }
            .pebblesListRow(position: .only)
        case .existing(_, let storagePath):
            // R6: removal only detaches; Save (enabled by the name-or-photo
            // rule) sends `snaps: []`, and the server checks `moment_empty`.
            ExistingSnapRow(
                storagePath: storagePath,
                isRemoving: false,
                onRemove: { snaps?.detachExisting() }
            )
            .pebblesListRow(position: .only)
        case .pending(let snap):
            AttachedPhotoView(
                snap: snap,
                onRetry: { if let userId = currentUserId, let snaps { Task { await snaps.retryCurrent(userId: userId) } } },
                onRemove: { if let userId = currentUserId, let snaps { Task { await snaps.removePending(userId: userId) } } }
            )
            .pebblesListRow(position: .only)
        }
    }

    private func cancel() async {
        if let userId = currentUserId, let snaps {
            await snaps.cancelAndCleanup(userId: userId)
        }
        dismiss()
    }

    private func save() async {
        guard let userId = currentUserId else {
            saveError = MomentWriteError.notAuthenticated.userMessage
            return
        }
        isSaving = true
        saveError = nil
        let payload = SaveMomentPayload(edit: draft, momentId: detail.id, formSnap: snaps?.formSnap, userId: userId)
        do {
            _ = try await MomentsService(client: supabase.client).saveMoment(payload)
            // R6: the rows are gone; now the detached (or replaced) files.
            await snaps?.purgeDetached()
            onSaved()
            dismiss()
        } catch {
            logger.error("save_moment (edit) failed: \(error.localizedDescription, privacy: .private)")
            saveError = userMessageForMomentWriteError(error)
            isSaving = false
        }
    }
}
```

  `SnapUploadCoordinator.cancelAndCleanup` only deletes a *pending* snap's files and forgets detached ones (Task 1.8), so Cancel never touches the saved photo. A failed save keeps the detached list, so a retry still cleans up.
- [ ] **Step 5:** Add `Edit moment` / `Modifier le moment` and `A moment needs a name or a photo.` / `Un moment a besoin d'un nom ou d'une photo.` to the catalog.

### Task 6.3: Delete the old composer, server drafts and the compat writers

- [ ] **Step 1:** `git rm` every file in this part's **Delete** lists.
- [ ] **Step 2: `PathView`:** drop `@Environment(PebbleDraftsService.self)`, `.task { await draftsService.refreshCount() }`, `isPresentingDrafts` and its sheet, the drafts button in the bottom inset, and `moment(matching:)`'s pebble-id branch now that nothing reports pebble ids (keep the moment-id match).
- [ ] **Step 3: App wiring:** `PebblesApp.swift` loses `drafts` and `snapshots` state and their `.environment(...)`; `RootView.swift` loses the two `@Environment` reads and preview injections; `SignedOutPurge` loses `snapshots` and `drafts` (keeps `completeDrafts`); update `SignedOutPurgeTests`.
- [ ] **Step 4: `ValenceRollTests.swift`:** delete the `RecordFlowValenceSeedTests` suite (lines ~73–100). `CompleteFlowModelTests.seed` covers the same rule.
- [ ] **Step 5:** Update the comments that name the old RPC or composer: `SnapUploadCoordinator.swift` ("Called by the sheet after a save (compose-pebble / compose-pebble-update) failure"), `FormSnap.swift`, `ISO8601Flexible.swift` (the drafts rationale stays as history but say "was"), `Valence.swift:48`.
- [ ] **Step 6: Read page menu.** In `PebbleDetailSheet`:

```swift
    @State private var isPresentingEditMoment = false
    @State private var isPresentingEditPebble = false
    …
                            Menu {
                                Button { isPresentingEditMoment = true } label: {
                                    Label("Edit moment", systemImage: "pencil")
                                }
                                if detail?.pebble != nil {
                                    Button { isPresentingEditPebble = true } label: {
                                        Label("Edit pebble", systemImage: "circle.hexagongrid")
                                    }
                                }
                                Button(role: .destructive) { … } label: { Label("Delete", systemImage: "trash") }
                            } label: { Image(systemName: "ellipsis.circle") }
    …
        .sheet(isPresented: $isPresentingEditMoment) {
            if let detail {
                EditMomentSheet(detail: detail, onSaved: { Task { await load() }; onPebbleUpdated?() })
            }
        }
        .sheet(isPresented: $isPresentingEditPebble) {
            if let detail, let pebble = detail.pebble {
                EditPebbleSheet(momentId: detail.id, pebble: pebble, onSaved: { Task { await load() }; onPebbleUpdated?() })
            }
        }
```

  (replacing the single `isPresentingEdit`).
- [ ] **Step 7: Build & test.** Then the gate grep (Inventory 1): expected **no non-comment hit**.
- [ ] **Step 8: Look at it.** Simulator: Edit moment on a photo-only moment. Save is enabled because there is a photo. Remove the photo: the row clears, Save disables and the footer hint shows (no RPC has run). Type a name: Save enables. Save: the moment is now name-only, and the old photo's files are gone from Storage. Remove the photo, then Cancel: the photo is still there on reopening. Edit pebble: change the emotion → the read page retints, the capsule shows the karma delta if any. Confirm the Path's drafts button is gone and long-press `+` does nothing.
- [ ] **Step 9:** Commits, one per logical change: `feat(ios): edit a pebble through complete_pebble`, `feat(ios): edit a moment through save_moment`, `chore(ios): retire the eleven-step composer and server drafts`. Push; PR `feat(ios): retire the old composer and split moment and pebble editing`, `Part 6 of the #1047 stack.`, labels `feat`, `ios`, `ui`, `no-lab-note`.

---
## Part 7 — collection glyph picker, final gate, Arkaik (`feat/1047-ios-moments-collection-glyph`)

**Outcome:** a collection can carry a glyph, set from both the create and the edit form (R11), which is what pebbles in it inherit (D5). The compat grep gate is clean. The hosted Arkaik map describes the new views.

**Files**
- Create: `Pebbles/Features/Profile/Components/CollectionGlyphSection.swift`
- Modify: `Features/Profile/Models/Collection.swift`, `Features/Profile/Sheets/EditCollectionSheet.swift`, `Features/Profile/Sheets/CreateCollectionSheet.swift`, `Features/Profile/Lists/CollectionsListView.swift:121`, `Features/Profile/Components/ProfileCollectionsCard.swift:81`, `Features/Profile/Views/CollectionDetailView.swift` (reload select), `Resources/Localizable.xcstrings`
- Modify tests: `PebblesTests/CollectionDecodingTests.swift`, `PebblesTests/CollectionUpdatePayloadEncodingTests.swift`, `PebblesTests/CollectionInsertPayloadEncodingTests.swift`

### Task 7.0: Branch

- [ ] `gh stack add feat/1047-ios-moments-collection-glyph`

### Task 7.1: `Collection` decodes its glyph

- [ ] **Step 1: Add the failing tests** to `CollectionDecodingTests.swift`:

```swift
    @Test("decodes the collection's glyph when the select embeds it")
    func decodesGlyph() throws {
        let json = Data("""
        {"id":"55555555-5555-4555-8555-555555555555","name":"Autumn","mode":null,
         "glyph_id":"66666666-6666-4666-8666-666666666666",
         "glyph":{"id":"66666666-6666-4666-8666-666666666666","name":"Leaf",
                  "strokes":[{"d":"M 20,100 Q 100,20 180,100","width":6}],
                  "view_box":"0 0 200 200","user_id":"5f0e1d2c-3b4a-4958-8776-655443322110"},
         "pebble_count":[{"count":3}]}
        """.utf8)
        let collection = try JSONDecoder().decode(Collection.self, from: json)
        #expect(collection.glyphId == UUID(uuidString: "66666666-6666-4666-8666-666666666666"))
        #expect(collection.glyph?.name == "Leaf")
        #expect(collection.pebbleCount == 3)
    }

    @Test("an explicit null glyph and a missing one both decode to nil")
    func nullGlyph() throws {
        let explicit = Data(#"{"id":"55555555-5555-4555-8555-555555555555","name":"A","mode":"stack","glyph_id":null,"glyph":null}"#.utf8)
        let missing = Data(#"{"id":"55555555-5555-4555-8555-555555555555","name":"A","mode":"stack"}"#.utf8)
        #expect(try JSONDecoder().decode(Collection.self, from: explicit).glyph == nil)
        #expect(try JSONDecoder().decode(Collection.self, from: missing).glyphId == nil)
    }
```

  and to `CollectionUpdatePayloadEncodingTests.swift`:

```swift
    @Test("glyph_id is always sent: a lowercase id, or an explicit null that clears it (R11)")
    func glyphId() throws {
        let id = UUID(uuidString: "66666666-6666-4666-8666-666666666666")!
        let set = try JSONSerialization.jsonObject(with: JSONEncoder().encode(
            CollectionUpdatePayload(name: "A", mode: nil, glyphId: id))) as? [String: Any]
        #expect(set?["glyph_id"] as? String == "66666666-6666-4666-8666-666666666666")
        let cleared = try JSONSerialization.jsonObject(with: JSONEncoder().encode(
            CollectionUpdatePayload(name: "A", mode: nil, glyphId: nil))) as? [String: Any]
        #expect(cleared?.keys.contains("glyph_id") == true)
        #expect(cleared?["glyph_id"] is NSNull)
    }
```

  Existing `CollectionUpdatePayload(name:mode:)` calls in that file gain `glyphId: nil`.
- [ ] **Step 2:** Run to verify they fail.
- [ ] **Step 3: `Collection.swift`:** add `let glyphId: UUID?` and `let glyph: Glyph?`, `CodingKeys` `glyphId = "glyph_id"`, `glyph`, decoded with `decodeIfPresent`. **`EditCollectionSheet.swift`:** `CollectionUpdatePayload` gains `let glyphId: UUID?`, key `glyph_id`, encoded explicitly (`try container.encode(glyphId?.uuidString.lowercased(), forKey: .glyphId)` encodes `null` when nil).
- [ ] **Step 4:** Select strings (`CollectionsListView.swift:121`, `ProfileCollectionsCard.swift:81`, `CollectionDetailView.reloadCollection`): `"id, name, mode, glyph_id, glyph:glyphs(id, name, strokes, view_box, user_id), pebble_count:collection_pebbles(count)"`.
- [ ] **Step 5:** Run: PASS. Commit `feat(ios): read a collection's glyph`.

### Task 7.2: The glyph row in the create and edit collection forms (R11)

- [ ] **Step 1: Write the failing test.** In `CollectionInsertPayloadEncodingTests.swift`, existing `CollectionInsertPayload(userId:name:mode:)` calls gain `glyphId: nil`, and add:

```swift
    @Test("glyph_id is always sent on insert: a lowercase id, or an explicit null (R11)")
    func glyphId() throws {
        let id = UUID(uuidString: "66666666-6666-4666-8666-666666666666")!
        let set = try encode(CollectionInsertPayload(userId: userId, name: "A", mode: nil, glyphId: id))
        #expect(set["glyph_id"] as? String == "66666666-6666-4666-8666-666666666666")
        let none = try encode(CollectionInsertPayload(userId: userId, name: "A", mode: nil, glyphId: nil))
        #expect(none.keys.contains("glyph_id"))
        #expect(none["glyph_id"] is NSNull)
    }
```

- [ ] **Step 2:** Run to verify it fails.
- [ ] **Step 3: `CreateCollectionSheet.swift`:** `CollectionInsertPayload` gains `let glyphId: UUID?`, key `glyph_id`, encoded explicitly (`try container.encode(glyphId?.uuidString.lowercased(), forKey: .glyphId)`). The `collections_glyph_usable` trigger guards inserts as well as updates.
- [ ] **Step 4: Create the shared row** `Pebbles/Features/Profile/Components/CollectionGlyphSection.swift`. Both sheets use it, so the row, its picker and its copy exist once:

```swift
import SwiftUI

/// The collection's glyph row (R11), shared by create and edit: tap to pick
/// through the shared glyph picker, **Remove glyph** when one is set, and the
/// footer saying what the glyph does. Pebbles in the collection inherit it (D5).
struct CollectionGlyphSection: View {
    @Binding var glyph: Glyph?
    @State private var isPresentingPicker = false

    var body: some View {
        Section {
            Button { isPresentingPicker = true } label: {
                HStack(spacing: 12) {
                    if let glyph {
                        GlyphView(case: .default, strokes: glyph.strokes, side: 32)
                            .accessibilityHidden(true)
                    } else {
                        RoundedRectangle(cornerRadius: 6)
                            .strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [3]))
                            .frame(width: 32, height: 32)
                            .foregroundStyle(.secondary)
                    }
                    Text("Tap to choose").foregroundStyle(.primary)
                    Spacer()
                    Image(systemName: "chevron.right").font(.caption).foregroundStyle(.tertiary)
                }
            }
            .buttonStyle(.plain)
            .pebblesListRow(position: glyph == nil ? .only : .top)
            if glyph != nil {
                Button("Remove glyph", role: .destructive) { glyph = nil }
                    .pebblesListRow(position: .bottom)
            }
        } header: {
            Text("Glyph").pebblesSectionHeader()
        } footer: {
            Text("Pebbles in this collection carry its glyph.")
        }
        .sheet(isPresented: $isPresentingPicker) {
            GlyphPickerSheet(currentGlyphId: glyph?.id, onSelected: { glyph = $0 })
        }
    }
}
```

- [ ] **Step 5: Use it in both sheets,** between the Name and Mode sections:
  - `CreateCollectionSheet`: add `@State private var glyph: Glyph?` and `CollectionGlyphSection(glyph: $glyph)`. Its insert sends `CollectionInsertPayload(userId: userId, name: trimmedName, mode: mode?.rawValue, glyphId: glyph?.id)`.
  - `EditCollectionSheet`: add `@State private var glyph: Glyph?`, initialised to `collection.glyph`, and `CollectionGlyphSection(glyph: $glyph)`. `canSave` is also true when `glyph?.id != collection.glyphId`. `save()` sends `CollectionUpdatePayload(name: trimmedName, mode: mode?.rawValue, glyphId: glyph?.id)`.

  A 42501 from the trigger lands in each sheet's existing generic save error. Neither sheet calls `set_collection_glyph` (R11).
- [ ] **Step 6:** Add `Remove glyph` / `Retirer le glyphe` (if not already in the catalog) and `Pebbles in this collection carry its glyph.` / `Les galets de cette collection portent son glyphe.`.
- [ ] **Step 7: Glyph library check:** `grep -rni "pebble" apps/ios/Pebbles/Features/Glyph` shows only `pebbles*` modifiers and toolbar helpers. There is no pebble tab and no "use on pebble" action (the tabs are Mine / Owned / Commu), so nothing needs removing. Record this in the PR body.
- [ ] **Step 8: Look at it.**
  - Create a collection with a glyph.
  - Set a glyph on an existing collection, then open a pebble in it: its stone now carves the collection's glyph (the resolver prefers a collection over the domain).
  - Remove the glyph: the stone falls back to the domain default.
- [ ] **Step 9:** Build & test; commit `feat(ios): give a collection a glyph its pebbles inherit`.

### Task 7.3: Final gate

- [ ] **Step 1:** The Inventory 1 grep: no non-comment hit.
- [ ] **Step 2:** `grep -rnE '\.from\("|\.rpc\("|\.rpc\($|functions\.invoke|\.invoke\($' --include='*.swift' apps/ios/Pebbles` and compare against Inventory 1: every remaining site is an "unchanged" row or lives in `MomentsService.swift` / `PebbleSnapRepository.swift`.
- [ ] **Step 3:** Full **Build & test**; `swiftlint` 0 errors; open `Localizable.xcstrings` in Xcode: no `New`/`Stale`, every new row has `fr`.
- [ ] **Step 4:** Device pass on the maintainer's iPhone 15 (`project_ios_device_deploy` memory), test account only: capture with a photo, chain into Complete, achievement on a fresh account, edit both halves, delete both ways, collection glyph inheritance.

### Task 7.4: Arkaik (hosted map, `arkaik-mcp` tools only)

If the `arkaik-mcp` tools are not available, say so and stop. Never edit `docs/arkaik/bundle.json` or `journal.jsonl`. Move statuses to `development` only, and never set `live`. The Arkaik GitHub App marks `releasing` at merge.

- [ ] **Create** (`create_node`, species `view`, platforms `ios` + `android`, status `development`; the Android plan creates the same nodes or adds its platform, so coordinate on ids):
  - `V-moment-capture`: "Capture a moment". Photo · when · name → `save_moment`; "Make it a pebble now" chains into Complete. Edges: `V-timeline` → it; it → `API-rpc-save-moment`; it → `V-pebble-complete`.
  - `V-pebble-complete`: "Complete a pebble". Valence · emotion · domain · souls · collection · privacy → `complete_pebble`, then the success screen (lit stone, karma), then `check_achievements`. Edges: `V-pebble-detail` → it; it → `API-rpc-complete-pebble`; it → `V-record-success`; it → `V-achievement-unlock-moment`.
  - `V-moment-edit`: "Edit Moment". Form → `save_moment`. Edges: `V-pebble-detail` → it; it → `API-rpc-save-moment` (photo removal rides the whole-set `snaps`, R6; no edge to `API-rpc-delete-moment-media`).
- [ ] **Update** (`update_node`):
  - `V-timeline` (Path): bare moments draw as blank stones, `+` opens Capture, the two-option delete, reads `API-rpc-path-moments` (add the edge).
  - `V-pebble-detail`: rename in its description to "Moment read page": bare-moment layout with "Make it a pebble", the `…` menu (Edit moment, Edit pebble, Delete), reads `DM-v-moments-full` (edge), `API-rpc-delete-moment` (edge).
  - `V-pebble-edit`: pebble-only form, now through `API-rpc-complete-pebble` (replace the `API-compose-pebble-update` edge for ios).
  - `V-collection-edit` and `V-collection-create`: both gain the glyph row (R11).
  - `V-record-success`: now the Complete flow's success screen, with the stone carving the resolved glyph.
- [ ] **Retire for iOS** (status stays as the other platforms need; remove the `ios` platform or edges as the node model allows, or file a request with `file_request` if a platform cannot be removed by update): `V-pebble-record` (the eleven-step flow), `V-quick-pebble-editor` (the long-press sheet), `V-pebble-drafts`, `V-glyph-attach` for pebbles. The `ios` edges to `API-compose-pebble`, `API-compose-pebble-update`, `API-delete-pebble-media`, `API-get-pebbles` go.
- [ ] Acceptance `AC-glyph-on-pebble` ("Mark a moment with your own glyph") is superseded by D5 on iOS: do not mark anything `live`. Leave a note on the node via `update_node` description, or `propose_idea` if the maintainer should decide its fate.

### Task 7.5: Open the last PR

- [ ] PR `feat(ios): give collections a glyph and finish the moments split`, body starting `Resolves #1047` then `Part 7 of the #1047 stack`, the list of the seven parts, the Parity decisions link (this plan), the touchpoint inventory result. Labels `feat`, `ios`, `ui`; milestone from #1047.
- [ ] **Lab Note.** The stack is user-facing (`feat`, new views). The repo `CLAUDE.md` asks for a `## Lab Note (EN/FR)` written with the `lab-note` skill. The maintainer's standing preference is that Arkaik coverage replaces it, with the `no-lab-note` label. Ask which applies when opening this PR. If a note is wanted, write one note for the whole stack here.
- [ ] **Decision log:** none needed. The split's decision is already logged (2026-10-12), and the R1–R13 reconciliation (incl. R6: no `delete_moment_media`, R11: no `set_collection_glyph`) is a client-side parity record kept in both plans. If the maintainer reverses one of them, append a superseding log entry rather than editing.

---

## Self-review (run once the plan was written)

- **Spec coverage.** §6 Capture flow, Part 5. Complete flow, Part 4. Path blank stone, Parts 2–3. Read page CTA, Part 4. Collection glyph picker, Part 7. Glyph library pebble tab, Part 7 (none on iOS). Per-moment autosave, Part 4. Drafts list and service removed, Part 6. Decoder tests with null pebble, explicit null name, photo-only, whole-second and fractional timestamps, Part 1 fixtures. Brief: every compat object removed (Inventory 1, final gate in Part 7); `check_achievements` after completion (A4, Parts 4 and 6); delete UX decided (R8, Part 3); edit split (R5, Part 6). Reconciliation R1–R13 with the Android plan: copy (R1, R3, R10, copy catalog), capture close (R2, Part 5), blank stone outline + label (R4, Part 2), photo removal through `save_moment` with no `delete_moment_media` (R6, Tasks 1.8 and 6.2), read page overflow menu (R7, Parts 3 and 6), detail rows open the read page (R9, Part 3), collection glyph on create and edit (R11, Part 7), success screen (R12, Part 4), karma delta (R13, Parts 4 and 6).
- **Type consistency.** `MomentSummary` / `.PebbleSummary`, `MomentDetail` / `.Pebble` / `.Snap` / `.SoulRef` / `.CollectionRef`, `PebbleGlyph`, `SaveMomentPayload` (+ `capture:`, `edit:`), `CompletePebblePayload` (+ `init?(momentId:draft:)`), `MomentsService`, `MomentDeletion` (`deleteMoment`, `deletePebble`, `perform`, with `drafts:` from Part 4), `MomentDeleteTarget`, `MomentDeleteOption`, `StoneCarving`, `StoneCarvingArt.art(for:glyph:)` / `.blankArt(for:)` / `.placedGlyphInk(strokes:viewBoxSide:size:polarity:)`, `StonePalette.blank`, `SnapUploadCoordinator.detachExisting()` / `.purgeDetached()` / `.detachedStoragePaths` (Task 1.8), `CollectionGlyphSection`, `CollectionInsertPayload.glyphId`, `CollectionUpdatePayload.glyphId`, `PebbleStoneView(valence:glyph:palette:followsTilt:)` and `.blank(followsTilt:)`, `CompleteStep`, `CompleteDraft`, `CompleteDraftStore`, `CompleteAutosave`, `CompleteDraftSnapshot`, `CompleteFlowModel`, `CompletionResult`, `CompleteFlowView(momentId:momentName:onCompleted:)`, `CaptureStep`, `CaptureDraft`, `CaptureFlowModel`, `CaptureFlowView(onMomentChanged:)`, `RecordFlowChrome(dotCount:dotIndex:canGoBack:onBack:onClose:)`, `RecordStepSecondaryAction`, `EditMomentDraft`, `EditMomentSheet(detail:onSaved:)`, `EditPebbleSheet(momentId:pebble:onSaved:)`, `MomentNameText`, `MomentName.display`. Each one is introduced in an earlier task than its first use.
- **Existing types relied on (checked on main):** `ISO8601Flexible`, `Valence.from(positiveness:intensity:)`, `Visibility`, `GlyphStroke`, `Glyph`, `SoulWithGlyph.glyph`, `FormSnap`, `AttachedSnap.storagePrefix(userId:)`, `SnapUploadCoordinator` (`handlePicked`, `retryCurrent`, `removePending`, `cancelAndCleanup`, `seedExisting`, `isUploading`, `hasFailed`, `isBlocking`, `pickedCaptureDate`, `formSnap`), `PebbleSnapRepository.deleteFiles(storagePrefix:)`, `TapHaptic` / `TapHaptics`, `RecordStepScaffold`, `RecordStepAction.primary`, `RecordPhotoStep` / `RecordWhenStep` / `RecordNameStep`, `ValencePickerContent`, `EmotionPickerContent`, `DomainPickerContent`, `SoulPickerContent`, `CreateSoulSheet`, `GlyphPickerSheet`, `GlyphView`, `PhotoPickerView`, `ExistingSnapRow`, `AttachedPhotoView`, `SelectedSoulsRow`, `VisibilityChip`, `KarmaNotificationService.notifyEarned(amount:reason:presentsCapsule:)`, `AchievementsService.fireCheck()`, `ReferenceDataService` (`souls`, `collections`, `domains`, `hasLoaded`, `refreshSouls`), `EmotionPaletteService.palette(for:)`, `StoneCarvingArt.glyphTransform`, `WobbleRenderer.glyphInk(d:width:)`, `PebblesPrimaryButtonStyle`, `SharedPebbleLink.url(for:)`.
- **Known risk to watch in review.** `PebbleRow`'s palette expression and `PathPebbleRow`'s preview need a hand-built `MomentSummary`. `MomentSummary.PebbleSummary` has a synthesized memberwise init because it declares no custom one; `MomentDetail.Pebble` does not, which is why no test constructs it by hand.
