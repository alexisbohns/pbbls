# Android profile shell — profile screen, settings, authed navigation

> Sub-project **C** of the Android Profile milestone (M41 on GitHub, drafted "M40"; issue #567, blocked by #565/#566). Umbrella design: `docs/superpowers/specs/2026-07-16-android-profile-design.md` §C, decisions D1, D5, D11–D13.

## Approach

The authed side gains a real NavHost (pushes = routes, modals stay covers — D1); the Profile screen composes entirely from A's idiom kit and B's stats stack; Settings is a full-screen cover whose data access lives in a new `ProfileService` so both screens stay out of supabase-kt. Zero DB work (`profiles`, `update_profile`, `collections + collection_pebbles` aggregate, GoTrue password update all pre-exist).

## Deliverables

- **`services/ProfileService.kt`** — profile row fetch (`display_name, created_at, glyph_id`), glyph-strokes fetch, collections-with-counts fetch (newest first, mirroring `ProfileCollectionsCard.load()`), and `saveSettings` (only-changed fields: `update_profile` with absent-keys-mean-keep, then `auth.updateUser { password }`). New CompositionLocal, wired in `PebblesApp`/`MainActivity`.
- **`features/profile/models/Collection.kt`** (D10) — `CollectionMode` enum mirroring the check constraint + the `[{count}]` aggregate unwrap (decoding test mirrors `CollectionDecodingTests`).
- **Components** (all pure, screenshot-previewed): `ProfileBanner` (hand-face name + locale-formatted "Member since"), `AssiduityGrid` (fossil/wave icons over `chunkAssiduity`), `RipplesRow` (badge + level headline + plural progress copy), `DataTile`/`ProfileCountersRow` (tabular figures via `tnum`), `ProfileStatsCard`, `ProfileCollectionCard` (filled/dashed-empty variants) + `ProfileCollectionsCard` (optional tap callbacks stay null until E — no dead tap targets, D11), `ProfileLogoutButton`.
- **`features/profile/ProfileScreen.kt`** — banner → stats card → collections carousel → logout under a `PebblesTopBar` (back + gear); load/error/Retry treatment (D13 — named deviation from iOS's silent empty banner); Settings as a conditionally-composed full-screen cover. Shortcuts row and Lab card deliberately absent until D/E and the Lab milestone (D11).
- **`features/profile/SettingsScreen.kt`** (D5) — header glyph → existing `GlyphPickerSheet` (this screen's single sheet level); Informations section (inline trailing-aligned name field, read-only email); Providers (text-only brand labels, risk-4 v1) or Password section by account type; inline save error; Legal rows → `openLegalDoc`. Pure `settingsIsDirty` + `linkedProviders` with JVM tests.
- **Navigation (D1)** — `AuthedNavHost` in RootScreen (`path` start, `profile` route; D/E add theirs); `PathScreen(onSignOut)` → `PathScreen(onProfile)`; the temporary sign-out TextButton and its `path_sign_out` string are deleted; `android:enableOnBackInvokedCallback="true"` lands (predictive back), with the existing BackHandler sites re-verified on device.
- **Strings** — 26 en/fr pairs + 2 plurals; `auth_consent_terms_link`/`auth_consent_privacy_link` reused for the Legal rows.
- **Arkaik** — `V-profile` + `V-timeline` descriptions updated; `V-settings` gains the `android` platform.

## Verification

CI green; `SettingsLogicTest` + `CollectionDecodingTest` + existing suites; `ProfileScreenshots` gallery (banner ×2, stats loaded/loading, carousel filled/empty, logout) — light/dark; on device: bottom-bar taps push Profile; banner shows real glyph/name/member-since; stats live; Settings round-trip (rename + glyph swap persist to `profiles`, password change on an email account, providers on an SSO account, legal links open); sign-out from Profile → Welcome; back gestures clean at every level with predictive back on; fr pass.

## Lessons learned

- (fill at review)
