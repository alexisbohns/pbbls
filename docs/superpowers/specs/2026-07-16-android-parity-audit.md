# Android port — parity audit & roadmap

> Full-source audit of `apps/android` against the iOS reference (`apps/ios/Pebbles`), taken after milestones **M38 · Android App** (bootstrap) and **M39 · Android Record Flow** both shipped. Ten feature areas were audited file-by-file and the findings adversarially verified: every headline "missing on Android" claim was attacked with full-tree greps (all 12 confirmed, zero false gaps), every DB object named below was verified to exist in `packages/supabase/supabase/migrations/`, and every deferral classification was checked against the two milestone design docs and the decisions log. This doc is the planning input for the next Android milestones; the first of them is specced in `2026-07-16-android-profile-design.md`.

## 1. Where the port stands

Shipped and verified working (M38 + M39 + follow-ons, PRs #533–#536, #538–#543, #550, #552–#553, #558–#559):

- **Entry funnel** — Welcome (Rive splash, timed reveal, carousel), email/password auth with iOS-parity validation + consent metadata, Google hosted OAuth (PKCE, `pebbles://auth-callback`), session gate, 4-page onboarding with SharedPreferences gate. No Apple sign-in (M38 non-goal); onboarding illustrations are placeholders (risk 6).
- **Path timeline** — `path_pebbles()` → week roll (static cairn) + header + pager, refocus rule, rows with outline + `render_svg` + rotated snap thumbs, long-press delete.
- **Record flow** — create/edit/delete + detail read screen, shared `PebbleForm`, all four pickers (emotion/valence/soul/glyph), inline soul creation (with the D11 cache fix iOS doesn't have), karma flash pastille (D9/D10), `PebbleWriteService` with soft-success handling, snaps round-tripped on edit (risk 5 resolved as round-trip — an iOS-attached photo survives an Android edit).
- **Render stack** — layer-tracing static renderer (#552), full wobble port with golden-fixture parity (#558), valence picker SVGs.
- **Infra** — first repo CI, screenshot render-to-view artifact, Play internal-testing release pipeline (signed AAB, `versionCode` from run number), config-baked debug APK, ktlint, 38 JVM test files, en/fr localization with parity gate.

Remaining to port: **~7,500–8,000 iOS-LOC equivalent** — roughly the back half of the app. **None of it needs DB work**: every view, RPC, table, bucket, and edge function the gaps depend on already exists, built for web/iOS.

## 2. Verified defects in shipped code ([Fix] candidates, pre-flight)

1. **`GlyphService.list()` excludes marketplace-entitled glyphs** (`features/glyph/services/GlyphService.kt` filters `userId == null || userId == me`). The M39 design explicitly put entitled-glyph selection **in scope** ("own, system, or marketplace-entitled … IS in scope", non-goals §), but the shipped filter narrows it with only a code comment as authority. Consequence: a user who bought glyphs on web/iOS **cannot attach them on Android** even though `can_use_glyph` permits it. Right-sized fix: union one `glyph_entitlements` read into `list()` — do **not** wait for the tabbed-picker port (#549).
2. **Soul picker renders permanent zero counts** — `ReferenceDataService.fetchSouls` selects `id, name, glyph_id, glyphs(…)` without the `pebbles_count:pebble_souls(count)` aggregate, while `SoulTile` renders `soul.pebblesCount`; the wire model's default decodes to 0 for every soul. iOS fetches the aggregate. ~2-line select fix (mirror it in the `createSoul` select-back).
3. **Missed M39 grooming pass** — PR #550 added screens but made zero Arkaik edits, violating the repo rule. Stale: `V-timeline` description ("no dock, stats, create or detail yet"), `V-pebble-detail` `platformStatuses.android: "idea"`, `V-karma-flash` still `platforms: ["ios"]` (the M39 doc explicitly instructed adding `android`), `V-profile` has a **null title** (schema violation the validator misses). `apps/android/CLAUDE.md` is equally stale: "No create / edit / delete / detail / stats yet" and the valence-picker "port when the create flow lands" note both predate M39. One retroactive grooming PR fixes all of it (docs-only).

## 3. Parity matrix

Classification: **DD** = deliberately deferred by a named design-doc decision (now unowned/coming due); **gap** = absent with no doc authority; **n/a** = does not translate to Android. Sizes are measured iOS LOC (`wc -l`), not guesses.

### Profile surface (~1,125 LOC in-area + ~600 shared) — nothing exists on Android

| Feature | iOS refs | Size | Class |
|---|---|---|---|
| PathBottomBar + Profile nav entry | `PathBottomBar.swift`, `PathView.swift` | ~107 | DD (M39 non-goal) |
| PathStatsService + stats models | `PathStatsService.swift`, `KarmaSummary`, `ProfileEngagement`, `RippleSummary` | ~154 | DD |
| RippleBadge stroke system (6 hand-ported beziers, tone table) | `Shared/Ripples/{RippleBadge,RippleStrokes,RippleStrokeColor}` | ~209 | DD (named in M39 "ripple on the timeline") |
| ProfileView shell (banner, fetch, logout, card chrome) | `ProfileView`, `ProfileBanner`, `ProfileLogoutButton`, `Theme/ProfileCard`, `GlyphBanner`, `GlyphView` | ~412 | DD (M38 blanket non-goal) |
| Stats card cluster (RipplesRow, AssiduityGrid, counters, DataTile) | `Profile/Components/*` | ~196 | DD |
| Shortcuts row (Collections/Souls/Glyphs tiles) | `ProfileShortcutsRow`, `ProfileShortcutTile` | ~41 (+560 destinations) | DD |
| Collections carousel card + `Collection` model (mode + count) | `ProfileCollectionsCard`, `ProfileCollectionCard`, `Collection`, `CollectionModeBadge` | ~296 | DD |
| SettingsSheet (name, glyph swap, password/providers branch, legal) | `SettingsSheet.swift` + `PebblesList.swift` chrome | ~439 | DD |
| Remove temporary Path sign-out | — | ~15 Android LOC | rides Profile |

DB: `profiles(+glyph_id)`, `v_karma_summary` (latest def `20260411000005:405`), `v_ripple` (thresholds [1,5,9,13,17,21]), `get_profile_engagement(p_tz)` (`20260516104231:73`), `update_profile` (`:33` — null = keep; **cannot clear `glyph_id`** by design), `collections + collection_pebbles` aggregate — **all exist**.

### Souls & collections CRUD (~1,700 LOC) — only the M39 picker fragments exist

| Feature | Size | Class |
|---|---|---|
| Souls list (grid + SoulItem + delete) | ~286 | DD |
| Collections list (+ mode badge, swipe-delete on iOS) | ~233 | DD |
| Soul detail (pebbles via `pebble_souls!inner`, shared PebbleRow) | ~323 | DD |
| Collection detail (+ `groupPebblesByMonth` util + test) | ~232 | DD |
| Create/Edit soul with glyph row (M39 D12 pre-assigned "to a Profile milestone") | ~403 | DD |
| Create/Edit collection (segmented mode, explicit-null encoder + tests) | ~257 | DD |

DB: all owner-scoped RLS single-table writes (sanctioned pattern, no RPCs), `souls.glyph_id` NOT NULL with system default `4759c37c-…` and the server-side `souls_glyph_usable` trigger — **all exist**. ⚠️ Junction-naming trap: it's `pebble_souls` but `collection_pebbles` (not `pebble_collections`) — mind PostgREST embed strings.

### Pebble media / snaps (~1,230 LOC) — read side shipped, write side absent

| Feature | Size | Class |
|---|---|---|
| Photo attach + upload write path (picker, pipeline 1024px/≤1MB + 420px/≤300KB, `SnapUploadCoordinator` state machine with retry/compensating deletes, form Photo section, `ExistingSnapRow` + eager `delete_pebble_media`) | ~950 (+286 tests) | DD ("own milestone") |
| Detail read-banner photo reveal (two-phase, `BannerAspect` 16:9/4:3/1:1 buckets) | ~280 | DD (D7 "drives no UI") |
| Write/delete half of `PebbleSnapRepository` | ~90 | DD |

DB: `pebbles-media` bucket (private, 1.5 MB, `image/jpeg` only, owner-scoped on first path segment, **no UPDATE policy** — delete + re-upload), `snaps` table, quota-raising `create_pebble`/`update_pebble`, `delete_pebble_media(p_snap_id) returns text` — **all exist**. ⚠️ `update_pebble` snaps semantics: replace-on-key-present, `[]` deletes all — Android's always-echo round-trip must stay. Android translation is friendlier than iOS: `PickVisualMedia` needs **no runtime permission** on minSdk 33, and `Bitmap.compress` strips EXIF inherently.

### Glyph feature (~2,090 LOC) — models + flat picker + renderer only

| Feature | Size | Class |
|---|---|---|
| Carve stack (canvas capture → RDP simplification ε1.5 → quadratic-midpoint SVG serializer, byte-parity with web/iOS) | ~565 (+175 tests) | DD (M39 non-goal) |
| Glyph naming at carve + rename in store list | ~75 | gap |
| Store surface (GlyphsListView Mine/Owned/Commu + GlyphTabBar) | ~300 | DD (M38 blanket) |
| Market data layer (`GlyphMarketService`, models, `buy_glyph`) | ~270 | DD |
| Detail drawer + SlideToConfirm + swap feedback (haptics/audio) | ~380 (+~100 service slices) | DD |
| Tabbed picker harmonization (**= open issue #549**, inline buy, carve row) | ~211 rework | gap (server guard live; Android picker is pre-harmonization flat) |
| GlyphView case-based chrome wrapper | ~122 | gap |
| GlyphBanner (profile header + drawer) | ~74 | gap |

DB: `v_glyph_market` (latest `20260701114205:405`), `buy_glyph` (error contract: `not_authenticated`, `not_in_market`, `cannot_buy_own`, `already_owned`, + `insufficient_karma` from `spend_karma`, returns jsonb `{entitlement_id, balance}`), `glyph_submissions`/`glyph_entitlements`, `can_use_glyph` + souls trigger — **all exist**. ⚠️ `refund_karma` is **service-role-only** since `20260629194418` — never plan it as a client call. iOS deliberately omits submit-to-market and favourites (web-only) — not Android gaps.

### Lab / changelog (~1,025 LOC) — literally zero Android code

| Feature | Size | Class |
|---|---|---|
| Log model + LogsService + lossy decode (4 feeds over `v_logs_with_counts`, reactions on `log_reactions` — deliberately no RPC) | ~317 (+249 tests) | DD (M38 blanket) |
| Lab main screen (5 concurrent feeds, WhatsApp card, optimistic reactions) | ~254 | DD |
| LogTimeline + ReactionButton | ~148 | DD |
| AnnouncementRow + detail (V1 markdown subset — match, don't exceed) | ~183 | DD |
| LogListView (see-all changelog/backlog) | ~123 | DD |
| Entry point (ProfileLabCard) | ~36 | DD, blocked on Profile |

⚠️ `logs.platform` check allows `project`/`infra`; iOS's strict enum silently drops those rows via lossy decode — Android must pick a matching behavior (decide in the Lab milestone spec).

### Path completion & polish

| Feature | Size | Class |
|---|---|---|
| Pebble draw-on/appear animation (`PebbleAnimatedRenderView` + `PebbleAnimationTimings`, keyed on `render_version`, settle pulse, reduce-motion static) | ~315 | **gap** — the only substantial iOS behavior inside M39's own scope dropped without a named decision |
| Animated cairn week-roll (Rive `pbbls-cairn-states.riv`, `isSelected` + `strokeColor` data binding) | ~101 + asset | DD (documented fast-follow) — verify rive-android data-binding color support first; `packages/rive/pbbls-cairn-states` is missing its `.riv` extension |
| Week-list reveal cascade + bottom gradient fade | ~50 | gap (code-comment-only skip) |
| Week-roll initial centering + snap-to-cell | ~30 | gap |
| Detail nav title + serif description font | ~15 | gap (serif face = maintainer asset decision) |

### Entry funnel & auth completion

| Feature | Size | Class |
|---|---|---|
| **Apple sign-in** via hosted web OAuth — supabase-kt ships an `Apple` provider; same Custom-Tab/PKCE/deep-link path as Google; **no nonce/native service needed**; provider already dashboard-configured (web uses `signInWithOAuth({provider:'apple'})`); Android's generic display-name patch already covers it | ~100–150 Android LOC | DD — highest-value auth item: iOS Apple-SSO accounts are hard-locked out of Android today |
| Onboarding illustrations — **the four 720×720 PNGs exist in-repo** (iOS asset catalog); copy to `drawable-nodpi` + flip `OnboardingSteps` from `Placeholder` to `Asset` (~10 LOC). Risk 6's "needs maintainer design sources" only applies to crisper exports | ~10 LOC + 4 assets | DD, now trivially closable |
| Welcome reveal layout animation (content slide-in + logo push-up) | ~30 | gap |
| Carousel parity (crossfade vs pager wrap-around artifact, swipe timer reset) | ~60 | gap |

Not gaps (absent on **every** surface, product decisions, not ports): password reset, email change, account deletion — see §5.

### Karma & ripples

| Feature | Size | Class |
|---|---|---|
| Ceramic sound on earn (`AudioService`, ambient/mix-with-others etiquette) | ~48 + 1 asset | DD (D9) — silent-mode policy needs a named Android decision |
| Waveform-envelope haptic (`HapticsService` → `VibrationEffect.createWaveform`; precompute the ~20-point envelope offline) | ~150 | DD (D9) |
| Karma Live Activity / Dynamic Island | ~129 retained-unused | **n/a** (Apple-only; abandoned even on iOS per 2026-07-01 decision) |
| Web `/wallet` history page | — | **n/a** (iOS doesn't have it; iOS is the reference) |

⚠️ `v_ripple.active_today` is UTC; iOS overrides client-side (`rippleWithLocalActiveToday`, "M22 follow-up"). Android should port the workaround; a server-side `p_tz` fix is a separate cross-surface decision.

### Platform services, theme idioms, infra

| Feature | Size | Class |
|---|---|---|
| Screen scaffold + toolbar idioms (`pebblesScreen`/`pebblesToolbarTitle`/`PebbleToolbarButton`) | ~140 | gap — consumed by ~38 iOS files; without it every new screen hand-rolls chrome |
| List/form chrome (`pebblesList`/`pebblesListRow` segmented borders/`pebblesSectionHeader`) | ~132 | gap — prerequisite for Settings + all CRUD sheets |
| `profileCard()` modifier | ~24 | gap |
| Icon size tokens + per-icon vector-drawable pipeline (no SF Symbols on Android) | ~36 + assets | gap |
| Shared `PebbleRow` (souls/collections detail lists) | ~108 | gap |
| App icon + themed/monochrome icon + Android-12 splash theming | config + assets | gap — **default system icon is live on the Play internal track**; blocked on maintainer's layered design source |
| Predictive back not actually enabled (`android:enableOnBackInvokedCallback` unset despite being a stated minSdk-33 rationale) | 1 attr + 5-site BackHandler audit | gap |
| `rememberSaveable` draft persistence | small Saver | DD (D4/risk 7) — costlier on Android than the iOS-parity framing suggests |
| Crash reporting (Play vitals vs SDK) | decision | DD — needs a decisions-log entry, symmetric with iOS |
| Screenshot tests as regression gate | config | DD (adoption path documented) |
| `UUID+Identifiable`, widgets/Glance | — | n/a |

## 4. Cross-cutting findings (no area owned these)

1. **Google Play compliance beyond the icon** — the Data Safety form, a privacy-policy URL, and store-listing assets gate any promotion past internal testing. **Account deletion is a Play policy hard blocker** for production: no surface has it, no delete-account RPC/edge function exists — it needs a cross-surface design (cascade over pebbles, karma ledger, storage, glyphs with entitlements), not an Android-first port.
2. **Auth session lifecycle** — expired/revoked refresh-token behavior, token storage location/encryption, and re-auth UX are untested on Android beyond the initial gate.
3. **Reference-slug catalog sync** — adding an emotion/domain server-side now requires touching iOS (`ReferenceSlugs` + xcstrings) **and** Android (`ReferenceSlugs.kt` + both `strings.xml`) in lockstep; each platform's parity test only covers itself. Process note for `packages/supabase` PRs.
4. **Font-scaling a11y** — several Android layouts pin dp heights (e.g. the 110.dp carousel pager) under sp-scaled text; no pass has been done on either platform. TalkBack has never been validated on-device (no instrumented tests, SDK-less maintainer loop).
5. **Rendering performance** — AndroidSVG re-parse per row bind, Coil cache sizing, and cairn-Rive per-cell allocation are unmeasured; watch when the timeline grows.
6. **Offline is a non-goal on every surface** but recorded nowhere — worth one decisions-log line so it stops being re-litigated.
7. **Wobble experiment** — `WOBBLE_ENABLED` is baked into internal-testing releases; **flip to `'false'` in `android-release.yml` before any public-track promotion** (2026-07-14 decision).
8. **Uncaptured expired deferral** — M38 D5 deferred DataStore "until real settings exist"; the Settings port is that moment. (Resolution proposed in the M40 spec: still not needed — settings live in Postgres, not local prefs.)

## 5. Roadmap

Proposed sequence. Every milestone is pure client work (zero migrations). Sizes are the measured iOS-LOC scope being ported.

| Order | Milestone / batch | Contents | Size |
|---|---|---|---|
| now | **Pre-flight batch** (independent small PRs) | [Fix] entitled-glyph union in `GlyphService.list()` · [Fix] soul-picker `pebbles_count` aggregate · [Docs] retroactive M39 grooming (Arkaik + `apps/android/CLAUDE.md`) · [Feat] Apple sign-in (web OAuth) · [Feat] onboarding artwork (in-repo PNGs) | ~200 |
| M40 | **Android Profile** (specced: `2026-07-16-android-profile-design.md`) | design-idiom kit → stats stack + bottom bar → profile shell + settings → souls management → collections management; retires the temporary sign-out | ~3,900 |
| M41 | **Android Snaps** | photo attach/upload write path + form Photo section + existing-snap removal; detail-banner photo reveal (`BannerAspect`) | ~1,250 |
| M42 | **Android Glyph studio & store** | carve stack + naming; GlyphView chrome; market data layer; store page (hosted by Profile shortcuts); detail drawer + slide-to-confirm (reduced feedback per the D9 precedent); tabbed picker — **closes #549**; rename; AudioService/HapticsService scaffolds | ~2,100 |
| M43 | **Android Lab** | Log model/service, Lab screen, timeline components, announcement detail, see-all lists, reactions; Profile Lab card un-hides | ~1,060 |
| rolling | **Polish bucket** (schedulable into milestone gaps) | pebble appear animation (the one undocumented in-scope drop — highest polish priority) · cairn Rive · reveal cascade + fade · roll centering/snap · welcome reveal + carousel parity · karma sound + waveform haptic · predictive-back flag · `rememberSaveable` Saver · detail title/serif | ~900 |
| cross-surface | **Not Android-only** | account deletion (Play blocker — own design) · password reset · `v_ripple` timezone server fix · crash-reporting decision · app icon/splash (maintainer assets) · Play listing/Data Safety | — |

**Why Profile first:** five of the six other work streams dead-end into it — souls/collections lists are unreachable without the shortcuts row, the Lab card and glyph store page need the shell, the bottom bar's three tap targets need a Profile destination, the M39 D12 soul-glyph debt was pre-assigned to it, and the temporary Path sign-out only retires with it. It also carries the design-idiom kit (toolbar/list/card chrome) that every later screen consumes — porting Lab or the store first would hand-roll chrome that immediately drifts.

**M41 vs M42 order is swappable.** Snaps-first favors content parity (photos already exist cross-surface and are invisible in Android's detail view); glyphs-first favors creation parity (Android users cannot create glyphs at all) and closes #549 sooner. Snaps has zero dependency on M40; glyphs needs M40's stats service (karma balance) and Profile host. Recommendation stands: snaps first. #549 should be re-homed from M39 into the glyph milestone (maintainer call) — the pre-flight entitled-glyph [Fix] removes its urgency.

**Issue/label conventions carried forward:** one species label per issue, `android` scope label, sub-projects A–E each own issue + spec + plan + PR, milestone naming `M4x · Android <name>` (GitHub numbering may drift from draft numbering, per the M38/M39 precedent).

## 6. Corrections to prior doc claims (found during verification)

- The junction table is `collection_pebbles` (not `pebble_collections`); its sibling is `pebble_souls` — the two are named in opposite orders.
- `delete_pebble`'s latest definition is `20260411000005:361` (the `20260426000002` migration touches `update_pebble` + `delete_pebble_media` only).
- `v_karma_summary`'s latest definition is `20260411000005:405`; `20260629194621` created `v_wallet_summary`, it did not revise `v_karma_summary`.
- `handle_new_user` still does not copy consent timestamps into `profiles` — the open fix(db) both mobile clients' comments reference remains open.
- iOS `SnapImageView.swift` is dead code (zero call sites) — do not port it. `PebbleMetaPill`/`PebblePillFlow` are legacy and unused — Android correctly ported the SurfaceTile-era read view.
