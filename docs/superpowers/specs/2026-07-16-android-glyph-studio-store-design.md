# Android Glyph Studio & Store — carve stack, market, tabbed picker

> Milestone **M43 · Android Glyph Studio & Store** (issues #583–#587; closes #549). Reference: the parity audit ("Glyph feature", ~2,100 LOC) and iOS sources under `apps/ios/Pebbles/Features/Glyph/`. Already shipped and reused, not re-planned: `GlyphView` case chrome, `GlyphBanner`, `SurfaceTile`, the Profile shortcuts row host, `PathStatsService` (M41).

## Goal

Android users can create glyphs (carve → name → save, byte-parity strokes), browse the store (Mine / Owned / Commu with live prices), buy with karma via `buy_glyph` (slide-to-confirm drawer), rename their own glyphs, and pick from the same tabbed picker on every glyph slot — closing #549. Exit: carve on Android → renders identically on web; buy a community glyph → karma decrements live → usable on a soul + pebble immediately.

## Non-goals

Submit-to-market and favourites (web-only on iOS too — not gaps); client refunds (`refund_karma` is service-role-only); the audio half of swap feedback (D6); store search/filters.

## Scope — sub-projects

- **A (#584)** carve studio: canvas, RDP, serializer, naming, insert. Parallel-safe with B.
- **B (#585)** market data layer: `GlyphMarketService` reads + `buy_glyph` + error copy.
- **C (#586)** store screen + detail drawer + slide-to-confirm + rename. Blocked by A/B.
- **D (#587)** tabbed picker harmonization (closes #549). Blocked by A/B/C.

## Core design decisions

- **D1 — Serializer + RDP are ported with the iOS quirks intact and locked by fixture tests.** RDP uses **clamped-segment** perpendicular distance (not infinite-line), strict `> ε`, max-point in both recursion halves joined `left.dropLast() + right`, `count <= 2` passthrough; ε = 1.5 applied in **canvas space** (default 280dp side) before the ×(200/side) scale. Serializer: `M`/`Q`-midpoint chain/final explicit `L`; 1-point strokes emit the zero-length `M L` pair; numbers round half-away-from-zero to 2 decimals, whole values print with **no decimal point**, fractional via `%.4g`-equivalent trailing-zero stripping (Kotlin `Double.toString` gives "10.0" — a formatter helper owns this). Test vectors from the iOS suite pin byte equality.
- **D2 — The carve surface is a full-screen cover with a hard-white canvas.** A downward stroke must never dismiss it (iOS deliberately avoids a sheet; matches our D5 discipline anyway). Canvas: white in both themes, `Spacing.xxl` (34dp) squircle-ish rounded clip + 1dp muted border, committed strokes re-drawn via the ported SVG path parser (all-or-nothing: any malformed token drops the whole stroke), in-progress stroke raw polyline (no live smoothing), stroke width constant 6.0 in 200-space. Cancel with strokes → "Discard your glyph?" alert; save failure keeps strokes with "Couldn't save your glyph. Please try again."; insert payload is exactly `{user_id, strokes, view_box: "0 0 200 200", name: string|explicit null}` — no shape key (#503).
- **D3 — Store = pushed NavHost route from the Profile Glyphs tile (D11 reversal), tabs cache stale-while-refetch.** Tabs Mine/Owned/**"Commu"** (literal label). Every tab switch re-fetches; cached items render during the refetch (spinner only on an empty tab; a failed refresh over cache is log-only). Error state copy: "Couldn't load glyphs" / "Please try again.".
- **D4 — Market reads are the three iOS queries verbatim; `buy_glyph` decodes the scalar jsonb directly.** Mine: `glyphs` + embedded `glyph_submissions(price, status, listed)`, price = first `approved && listed` submission else 0. Owned: `glyph_entitlements` with **no user filter** (RLS scopes), ordered by acquisition time, price = `price_paid`. Commu: `v_glyph_market` with server-side `.neq("user_id", me)` (view does NOT exclude own rows). Buy: `postgrest.rpc("buy_glyph", {p_glyph_id})` decoded straight to `BuyGlyphResult(entitlement_id, balance)` — never a single-row accessor. Error mapping = substring match on the lowercased message (`insufficient_karma` bubbles from `spend_karma`), copy: not_authenticated → "Please sign in again.", not_in_market → "This glyph isn't available.", cannot_buy_own → "You created this glyph.", already_owned → "You already own this glyph.", insufficient_karma → "Not enough karma yet.", fallback "Couldn't complete the swap. Please try again.".
- **D5 — Balance propagates by refresh, never a setter, never a flash.** After a successful swap the drawer morphs in place to its Owned state (no dismiss) and `onSwapped` triggers `PathStatsService.refresh()` — server truth, visible to the bar and Profile alike (M41 D2). Purchases never feed the karma flash (M39 D10).
- **D6 — Slide-to-confirm ports the geometry; feedback is haptic-only v1.** 56dp thumb, drag engages only when the press starts on the resting thumb, translation-based progress, confirm at 0.9 × (track − thumb), thumb parks on success / springs back on failure. iOS fires success feedback at the threshold **before** the RPC — ported as-is (named quirk). Feedback = Compose `LocalHapticFeedback` confirm; the audio half (bamboo clack) is deferred with `AudioService` left as a scaffold — the reduced-feedback precedent from M41 D9.
- **D7 — Android's Mine tab includes system glyphs (named deviation).** iOS `listMine` filters `eq(user_id, me)`, silently dropping system glyphs from its picker; Android's M41 pre-flight deliberately guaranteed own + system + entitled in the picker (souls default to a system glyph). Regressing that to close #549 would shrink the picker's contract, so Mine = user-created (newest first) **then** system glyphs (`user_id is null`, not renamable, price 0). Store and picker share the predicate.
- **D8 — Rename is a dialog on own glyphs only.** The M39 `CreateSoulDialog` chrome with "Name (optional)" placeholder; empty/whitespace input **clears** the name (explicit null — not a no-op); optimistic list update, revert + inline error on failure. `user_id == null || user_id != me` rows expose no rename.
- **D9 — Service split: `GlyphService` keeps CRUD (list/create/updateName), new `GlyphMarketService` owns market reads + buy.** Both D4-pattern plain classes; the picker/store read market data only through `GlyphMarketService`. Timestamps decode via `OffsetDateTime` (handles PostgREST's variable fractional digits natively — no manual normalization).
- **D10 — Picker migration is drop-in (#549).** `GlyphPickerSheet(currentGlyphId, onDismiss, onSelected)` keeps its signature; body becomes the tabbed grid (tab bar pinned at the sheet bottom), inline buy opens the drawer (buy → select → dismiss the picker on first `onSwapped`), carve row on Mine (also its empty state) opens the studio as a cover and selects the fresh glyph on save. Commu additionally client-filters `!owned` (owned community glyphs live under Owned) — both filters are load-bearing.

## Risks

1. Serializer byte-parity — mitigated by fixture tests transcribed from the iOS suite before any UI lands (A merges only with them green).
2. Slide-to-confirm gesture feel — screenshot states + maintainer on-device pass; geometry constants are pinned, feel is tunable later.
3. `buy_glyph` error text must survive into the thrown exception message for substring mapping — verify with a deliberate `cannot_buy_own` on device.

## Verification

CI green (fixture/RDP/market-decode/error-map/canSave tests, screenshot galleries per surface) → on device: carve→web render identity; store tabs show right rows/prices; buy round-trip (balance moves on the bar without restart, drawer morphs to Owned, glyph attaches to soul + pebble); rename + clear-name; picker round-trips from pebble form, soul form, Settings; #549 closes; fr pass.

## Arkaik

`V-glyph-studio` / `V-glyph-store` / `V-glyph-detail` (or the existing glyph view nodes — reconcile ids at landing), `F-carve-glyph` / `F-buy-glyph`, `V-profile` (Glyphs tile un-hides). Validate on every bundle edit.
