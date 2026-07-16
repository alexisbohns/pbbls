# Android Profile — profile surface, stats, settings, souls & collections management

> Milestone-level design drafted as **"M40 · Android Profile"** — GitHub milestone numbering may differ, as it did for M38/M39 (docs referring to "M40" mean this milestone). Seven PRs: a small pre-flight batch (two `[Fix]` + one `[Docs]` grooming PR, shared with the parity audit's roadmap) plus five sub-projects **A–E**, each its own issue + spec + plan + PR, per the M38/M39 precedent. Foundation: the parity audit (`docs/superpowers/specs/2026-07-16-android-parity-audit.md`), the M38 bootstrap, and the M39 record flow. Every DB object this milestone touches already exists — it is a pure client milestone.

## Goal

Give the Android app its Profile hemisphere, 1:1 with iOS: a Path bottom bar (profile entry + karma count + ripple badge, live-refreshed after every write), the Profile screen (glyph identity banner, shortcuts row, stats card with ripple level/assiduity grid/counters, collections carousel, logout), a Settings surface (display name, profile glyph swap, password-or-providers branch, legal links), and full souls & collections management (lists, detail views with their tagged pebbles, create/edit — including the soul glyph row that M39 D12 explicitly parked "to a Profile milestone"). The milestone retires the temporary sign-out button on PathScreen and ends with the on-device round-trip: record a pebble → watch karma/ripple move on the bar → open Profile → manage a soul's glyph and a collection's mode → change display name + profile glyph in Settings → sign out from Profile.

The finished iOS app is the reference implementation. Where this design says "mirror X", read the named iOS file under `apps/ios/Pebbles/` and port its structure, not just its behavior (`apps/android/CLAUDE.md` rule).

## Non-goals

- **Lab.** `ProfileLabCard` does not ship — the card is omitted (not stubbed) until the Lab milestone; ProfileView composes without it.
- **Glyph store / marketplace / carving.** The shortcuts row ships **two tiles** (Collections, Souls); the Glyphs tile lands with the glyph milestone. The soul glyph row and Settings glyph swap use the existing picker (own + system + entitled after pre-flight Fix-1) — selection-only, no tabs, no buy. #549 stays out of scope here.
- **Karma sound + waveform haptics** (D9 stands), **wallet history** (web-only; iOS is the reference), **ripples explainer sheet** (#446, iOS-first).
- **Account deletion, password reset, email change.** Absent on every surface; cross-surface product decisions, not ports (audit §4/§5). Settings shows email read-only, exactly like iOS.
- **Assiduity/ripple server-side timezone fix.** Android ports the iOS client-side `active_today` override verbatim (D3 below); fixing `v_ripple` server-side is a separate cross-surface decision.
- **Swipe-to-delete, tablet layouts, Play distribution changes.**

## Background: what exists today

- **iOS shape to mirror** — `PathView` pins `PathBottomBar` (62 LOC) under the New-pebble button; all three tap targets push `ProfileView` (136 LOC) via NavigationStack. `PathStatsService` (80 LOC) loads `v_karma_summary`, `v_ripple`, and `get_profile_engagement(p_tz)` in parallel with per-source error isolation, shared by PathView and ProfileView, `refresh()`ed after every create/edit/delete. ProfileView composes `ProfileBanner` (GlyphBanner: 96pt glyph or dashed carve placeholder + hand-font name + "Member since"), `ProfileShortcutsRow`, `ProfileStatsCard` (RipplesRow = RippleBadge + level headline + "X more pebbles to level Y" + 28-cell AssiduityGrid; then Days/Pebbles/Karma DataTiles), `ProfileCollectionsCard` (140pt tile carousel with `collection_pebbles(count)` aggregate), `ProfileLogoutButton`, and a gear button presenting `SettingsSheet` (307 LOC + 132 LOC of `PebblesList` grouped-row chrome). Lists/details/sheets: `SoulsListView`/`CollectionsListView`, `SoulDetailView`/`CollectionDetailView` (+ `GroupPebblesByMonth`), `Create/EditSoulSheet` (glyph row → GlyphPickerSheet), `Create/EditCollectionSheet` (segmented mode, explicit-null encoder), shared `PebbleRow` (108 LOC) for detail pebble lists.
- **Android already owns** (shrinks the port materially): `GlyphPickerSheet` with the exact API Settings/soul-edit need (`currentGlyphId` + `onSelected(Glyph)`), `GlyphImage` stroke renderer, `SurfaceTile`, `LegalDocs` Custom-Tab launcher, Reenie Beanie hand-font tokens, `SoulWithGlyph` model + `SoulRow` count-aggregate decode pattern, `EditPebbleScreen`/`PebbleDetailScreen`/`PebbleWriteService.delete` for the detail screens' pebble rows, `ReferenceDataService.createSoul/refreshSouls/refreshCollections`, and the sign-out plumbing itself (only its Profile home is missing).
- **DB contract (verified, all existing):** `v_karma_summary` (`20260411000005:405`), `v_ripple` (`20260516000001`; level thresholds [1,5,9,13,17,21]; `active_today` is UTC), `get_profile_engagement(p_tz text)` (`20260516104231:73`), `update_profile(p_display_name, p_glyph_id)` (`:33`; null = don't change; **cannot clear glyph_id** — a future "remove glyph" needs a dedicated flag, do not improvise), `profiles.glyph_id`, `souls` + `souls_glyph_usable` trigger, `collections` (mode check `stack/pack/track`, nullable) + owner RLS CRUD, junctions `pebble_souls` / **`collection_pebbles`** (opposite name orders — mind embed strings), `delete_pebble` (`20260411000005:361`). Souls/collections writes are sanctioned direct single-table calls; no new RPCs anywhere.

## Milestone-level settled choices

- **Scope:** bottom bar + stats stack + profile shell + settings + souls & collections management, in five sub-projects. If the milestone balloons, the pre-agreed slice is **D/E (souls, collections) slip to a follow-on milestone** with their shortcut tiles hidden — A–C stay one unit.
- **iOS is the reference; deviations are named decisions** (D1, D5, D7, D11–D13); everything else ports structurally.
- **Two shipped-defect fixes ride as pre-flight** (audit §2): entitled-glyph union in `GlyphService.list()`, soul-picker `pebbles_count` aggregate — plus the retroactive M39 grooming `[Docs]` PR (Arkaik + `apps/android/CLAUDE.md`).

## Sub-project decomposition

Five sub-projects, sequenced **A → B → C → (D ∥ E)**. Pre-flight fixes land first (independent).

### A — Design-system pass 2: screen/toolbar/list/card idioms, icon tokens, shared components

The idiom kit ~38 iOS files consume; porting Profile screens without it guarantees hand-rolled drift. Mirrors how sub-project B preceded C/D in the bootstrap.

- `PebblesScreen` scaffold + `PebblesTopBar` (centered meta-token title, leading/trailing text-button slots; semantics `heading()` for TalkBack) — refactor M39's hand-rolled `CreateTopBar` onto it (mirror `Theme/{PebblesScreen,PebblesToolbarTitle,PebbleToolbarButton}.swift`).
- `PebblesListSection` / row-position segmented 1pt borders / `pebblesSectionHeader` (mirror `Theme/PebblesList.swift` — per-corner radii by row position; explicit divider between rows).
- `Modifier.profileCard()` (mirror `Theme/ProfileCard.swift`).
- `PebblesIcon` size tokens + this milestone's vector-drawable batch (gear, person, sparkle, calendar, fossil-shell, alternating-current, chevron-right, stack, person-pair, scribble, plus) — hand-traced, no icon library (established convention).
- Shared `PebbleRow` composable (mirror `Components/PebbleRow.swift`: outline backdrop + render_svg thumb + name/date + long-press delete slot) and `GlyphView` case wrapper + `GlyphBanner` (mirror `Features/Glyph/Views/{GlyphView,GlyphBanner}.swift`; extract PebbleForm's private `DashedPlaceholder` into it).
- Screenshot previews for every idiom.

Issue title: `[Feat] Android design-system pass 2: screen/toolbar/list/card idioms, icon tokens, shared glyph & pebble rows` — labels: `feat`, `android`, `ui`.

### B — Stats stack: PathStatsService, ripple badge, bottom bar

- **`PathStatsService`** analog (D2): plain class per D4-bootstrap, three parallel loads with per-source error isolation, idempotent `load()` + `refresh()`, new CompositionLocal; models `KarmaSummary`, `ProfileEngagement`, `RippleSummary` (port the [1,5,9,13,17,21] thresholds with the migration-pointing comment; `pebblesToNextLevel`/`nextLevel`).
- **RippleBadge stack** (D4): six bezier ring shapes into Compose `Path` (44×44 viewBox scale), layered opacities 0.33/0.66/1.0, digit overlay, a11y label; `rippleStrokeTone` pure function unit-tested against the iOS truth table; screenshot preview grid (7 levels × active/inactive).
- **`PathBottomBar`** under NewPebbleButton: profile button, karma stat, ripple badge; `rippleWithLocalActiveToday` override ported verbatim (D3); `stats.refresh()` wired into PathScreen's create/edit/delete success paths; profile tap target lands stubbed (wired in C).
- `chunkAssiduity` pure helper + test.
- Unit tests: decoding (incl. `boolean[]` RPC row), thresholds, tone table, chunking.

Issue title: `[Feat] Android path stats: PathStatsService, ripple badge, bottom bar` — labels: `feat`, `android`, `ui`, `core`.

### C — Profile shell + settings + authed navigation

- **Authed NavHost** (D1): `path` (start) → `profile` → `souls`/`collections` list/detail routes; predictive back enabled and the five existing BackHandler sites re-verified.
- **ProfileScreen** (mirror `ProfileView.swift`): profiles single-row fetch + glyph strokes fetch, `GlyphBanner` header (carve placeholder when glyph-less), two-tile shortcuts row (D11), stats card cluster (RipplesRow + AssiduityGrid + counters over B's service), collections carousel card + `Collection` model with mode/count (D10), logout button; load/error/retry treatment (D13).
- **SettingsScreen** (D5, mirror `SettingsSheet.swift`): tappable 120pt glyph header → existing GlyphPickerSheet; Name field + read-only Email; SSO-vs-email branch from session identities (providers list vs new-password field); inline save error; Legal section → `openLegalDoc`. Save sends only changed fields: `update_profile` then `auth.updateUser { password }`; `onSaved` patches ProfileScreen state.
- **Retire the temporary sign-out**: remove PathScreen's TextButton + `onSignOut` threading; sign-out lives on Profile.
- Screenshot previews: profile (loading/loaded/glyph-less), settings (email vs SSO account, dirty/error states).

Issue title: `[Feat] Android profile screen, settings, authed navigation` — labels: `feat`, `android`, `ui`, `core`.

### D — Souls management

- Shared `SoulItem` (lift/replace the private `SoulTile` in SoulPickerSheet; 4-case styling per the #459 contract), `SoulsListView` grid (+ create entry, long-press delete per D7), `SoulDetailView` (header + `pebble_souls!inner` pebble list on shared PebbleRow, tap → EditPebbleScreen, delete → `delete_pebble`), `CreateSoul`/`EditSoul` full forms with the glyph row (D8/D9) + `SoulDraft`/payloads, `SystemGlyph` const; `refreshSouls()` after mutations.
- Plural resources for pebble counts (en/fr).

Issue title: `[Feat] Android souls management: list, detail, create/edit with glyph row` — labels: `feat`, `android`, `ui`, `core`.

### E — Collections management

- `Collection` model consolidation (mode + `[{count}]` unwrap — SoulRow pattern), `CollectionModeBadge`, `CollectionsListView` (long-press delete per D7, pull-to-refresh), `CollectionDetailView` + `groupPebblesByMonth` (java.time `YearMonth`, locale-aware headers, JVM test mirroring `GroupPebblesByMonthTests`), `CreateCollection`/`EditCollection` forms (segmented mode picker, explicit-null mode encoder + exact-keyset unit tests mirroring the iOS encoding suites); carousel wiring back into ProfileScreen.
- FR labels for Stack/Pack/Track confirmed with the maintainer (product vocabulary — no machine translation).

Issue title: `[Feat] Android collections management: list, detail, create/edit` — labels: `feat`, `android`, `ui`, `core`.

**Dependencies:** pre-flight fixes are independent; A blocks B–E (idioms); B blocks C (bar + stats card); C blocks D and E (navigation entry + carousel host); D and E are independent of each other.

## Core design decisions (settled)

- **D1 — Authed navigation: pushes become a real NavHost; modals stay conditional composition.** iOS distinguishes *pushes* (Profile, lists, details — NavigationStack) from *modals* (create/detail/edit pebble — sheets/covers). M39's D5 cover pattern mapped the modals honestly; Profile's multi-level pushes (profile → list → detail → edit) would strain it. So: an authed `NavHost` (`path` start destination; profile/souls/collections routes) for pushes, while the pebble create/detail/edit covers inside PathScreen stay exactly as shipped. The auth gate itself remains conditional composition (RootScreen unchanged in shape). `android:enableOnBackInvokedCallback="true"` lands here — the navigation pass is the moment to re-verify all BackHandler sites (onboarding's unconditional consume, the save-guards).
- **D2 — One shared `PathStatsService`; display-only, never a flash source.** Constructed in `PebblesApp`, provided by CompositionLocal, shared by the bar and Profile (a refresh from either is visible to both, iOS parity). Amounts on the karma pastille continue to come exclusively from edge-function `karma_delta` (M39 D10) — the stats service must never feed the flash.
- **D3 — `active_today`: port the iOS client-side local-day override verbatim.** `v_ripple` compares against UTC `current_date`; iOS recomputes from loaded pebbles in the device calendar (`rippleWithLocalActiveToday`, "M22 follow-up"). Android mirrors the workaround, comment included. Fixing `v_ripple` server-side (e.g. a `p_tz` param) is a deliberate cross-surface follow-up — not this milestone's only DB change.
- **D4 — RippleBadge paths are transcribed, tone table is unit-tested.** Transcribe the six cubics from `RippleStrokes.swift` into Compose `Path` (44-unit viewBox, DrawScope scale) — same approach as prior hand-ported shapes. `rippleStrokeTone(strokeId, level, activeToday)` ports as a pure function with a test asserting the full iOS truth table; screenshot grid is the visual gate.
- **D5 — Settings is a full-screen route, not a sheet.** iOS stacks SettingsSheet → GlyphPickerSheet; per the never-stack-sheets rule, Settings becomes a pushed route (its text fields also avoid the ModalBottomSheet+IME caveat) and the glyph picker is its single ModalBottomSheet level. Save semantics mirror iOS exactly: dirty-check enabled Save, trimmed name, only-changed fields, `update_profile` (null = keep) then password update, stay-open-on-error.
- **D6 — Souls/collections writes stay direct single-table client calls.** The sanctioned cross-surface pattern (AGENTS.md single-table exemption; iOS comments it). Glyph ownership on souls is enforced server-side by `souls_glyph_usable` — client filtering is defense-in-depth only. Pebble deletion inside detail views goes through the existing `delete_pebble` RPC path in `PebbleWriteService`.
- **D7 — Delete affordance unifies on long-press `DropdownMenu` + confirm `AlertDialog` for both lists.** iOS is inconsistent (swipe for collections, context menu for souls); Android standardizes on the M39 D8 idiom everywhere. Named deviation; revisit only with the D8 discoverability trigger.
- **D8 — Soul glyph row and Settings glyph swap use the existing flat picker (post-Fix-1: own + system + entitled).** The tabbed store picker (#549) belongs to the glyph milestone; the picker API (`currentGlyphId` + `onSelected`) already matches, so upgrading later is a drop-in.
- **D9 — Detail → edit is a surface swap; edit forms are full-screen.** Edit-soul needs the glyph picker as its sheet level and both edit forms carry text fields, so they follow the Detail→Edit surface-swap pattern from M39 D5 rather than sheets.
- **D10 — `Collection` gains mode + pebble_count; list screens fetch their own rows.** Extend the model with `CollectionMode` (mirroring the check constraint) and the `[{count: N}]` aggregate unwrap. iOS keeps list fetches in the views and uses ReferenceDataService only for the form's reference lists — mirror that; don't widen the cache contract.
- **D11 — Shortcuts row ships two tiles; the Lab card is omitted.** Glyphs tile arrives with the glyph milestone, Lab card with the Lab milestone. Dead chrome is worse than temporarily-narrower parity. Named temporary deviation, reversed by those milestones.
- **D12 — DataStore stays deferred (M38 D5 window closes: answer is "still no").** The Settings this milestone adds live in Postgres (`update_profile`), not local prefs; `hasSeenOnboarding` remains the only local flag and stays in SharedPreferences. Recording this here prevents re-litigation.
- **D13 — Profile fetch gets Android's standard load/error/retry treatment.** iOS silently swallows a failed profiles fetch into an empty banner; Android's established screens (picker, path, detail) all have retry states — porting the silent swallow would be a regression against the port's own bar. Named deviation; a companion iOS quality issue may be filed rather than porting the gap.
- **D14 — Member-since and month headers format via the active locale** (`java.time` + locale-aware patterns; never pinned — repo rule), counters use tabular figures (`FontFeatureSettings "tnum"` or the counter token).

## Risks and open questions

1. **Navigation refactor regression risk** — introducing the authed NavHost touches RootScreen/PathScreen wiring; the funnel gate must stay conditional composition. Mitigation: C's plan starts with a no-behavior-change refactor commit (PathScreen mounted as start destination) before any new route; on-device funnel pass is C's first checkpoint.
2. **RippleBadge fidelity** — hand-transcribed cubics can drift subtly. Mitigation: the screenshot preview grid compared side-by-side with the iOS `RipplePreviewGrid` before B merges.
3. **Predictive back** — flipping the manifest flag changes system-back visuals for all five existing BackHandler sites. Mitigation: explicit on-device back-flow pass (onboarding, create-with-dirty-draft, detail, edit) on the Play internal track.
4. **Providers rendering for cross-platform accounts** — an account created on iOS with Apple SSO carries an `apple` identity; Apple branding on Android has usage rules. v1: text-only provider labels ("Apple", "Google") with no logo commitments; maintainer taste-check on device.
5. **Asset dependency** — ~11 hand-traced vector drawables (A) and the Stack/Pack/Track FR vocabulary (E) need maintainer sign-off; neither blocks other sub-projects.
6. **Milestone size** — ~3,900 iOS-LOC of scope vs M39's ~2,900. The pre-agreed slice (settled above): D/E split off with hidden tiles if A–C consume the budget.
7. **`update_profile` cannot clear `glyph_id`** — the Settings glyph row must not offer "remove glyph" (iOS doesn't either); doing so would require a new RPC flag and is out of scope.
8. **Stats freshness** — iOS refreshes stats after every write; Android's M39 reload paths currently reload only the timeline. B wires `refresh()` into all three write paths; verification includes watching the bar move after each.

## Verification strategy

Per repo reality: **CI green → unit tests → screenshot artifact → maintainer on device** (Play internal track).

- **Pre-flight:** entitled glyph (bought on web) appears in the Android picker and attaches to a pebble + a soul (server trigger accepts); soul picker shows live counts; Arkaik validator green.
- **A:** screenshot previews of every idiom; CreateTopBar refactor pixel-compares against the shipped M39 screenshots (no visual drift).
- **B:** tone-table/threshold/chunking/decoding tests green; badge preview grid matches iOS side-by-side; on device: bar shows real karma/ripple; create a pebble → karma count and ripple state update without app restart; `active_today` flips correctly near local midnight vs UTC (the override's whole point).
- **C:** on device: Profile round-trip (banner with real glyph + member-since; glyph-less account shows the carve placeholder), Settings — rename + glyph swap persist (verify `profiles` row), password change on an email account, providers list on an SSO account, legal links open; sign-out from Profile returns to Welcome; the temporary Path button is gone; back gestures behave at every level with predictive back on; fr pass.
- **D:** on device: souls grid with live counts → detail lists the right pebbles → edit a soul's name + glyph (glyph visible on its pebble rows after) → delete a soul ("linked pebbles stay" verified — pebbles survive, links gone) → inline-create still works from the pebble form.
- **E:** on device: collections list with mode badges → detail groups by month with localized headers → create with mode, edit clearing the mode (explicit-null actually clears the column — verify in dashboard) → delete; carousel on Profile reflects changes; **milestone exit round-trip** as stated in the Goal.

## Arkaik

As sub-projects land, update `docs/arkaik/bundle.json`: fix `V-profile`'s null title and mark it implemented on Android; update `V-souls-list`/`V-soul-detail`/`V-soul-create`/`V-soul-edit`, `V-collections-list`/`V-collection-detail`/`V-collection-create`/`V-collection-edit`, `F-manage-souls`/`F-manage-collections`; refresh `V-home`/`V-timeline` for the bottom bar (and consider the open question of merging them — maintainer call); `V-settings` platforms gains `android` when C lands. The pre-flight `[Docs]` grooming PR handles the M39 backlog (`V-karma-flash` platforms, `V-timeline` description, `V-pebble-detail` platformStatuses, `apps/android/CLAUDE.md` staleness). Run `node .claude/skills/arkaik/scripts/validate-bundle.js` on every bundle edit. **Lab Note:** a distributable build now exists (Play internal testing), so propose the single milestone-level bilingual note when E lands, per the M38/M39 rule — a human publishes it via the Lab admin; never write to `logs` from the dev loop.

## Open questions deferred (not blocking)

- Tabbed glyph picker + store (#549 → glyph milestone); Lab card + Lab milestone; Glyphs shortcut tile.
- `v_ripple` server-side timezone fix (cross-surface; would supersede D3's ported workaround on all three clients).
- Ripples explainer sheet (#446) / `V-mechanic-sheet` platform claims.
- Whether `V-home` and `V-timeline` merge in Arkaik post-bottom-bar.
- Companion iOS quality issue for D13 (silent profile-fetch swallow) — file, don't port.
