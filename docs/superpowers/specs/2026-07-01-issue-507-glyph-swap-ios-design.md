# Glyph Swap on iOS — Design (issue #507)

**Status:** Approved for planning
**Milestone:** M36 · Pebblestore & Karma Economy
**Labels:** `feat`, `ui`, `ios`

## Context

The glyph marketplace already exists on the backend and web (issues #496, #501, #502).
Users can submit glyphs to the community and swap karma for a community glyph they
like. This task brings the **community swap** to iOS. Glyph *submission* is explicitly
**out of scope** here (deferred to a later iOS task).

The iOS glyph page today is `GlyphsListView` (Profile → Glyphs): a single grid of the
user's glyphs with a "+" to carve a new one, and tap-to-rename. This work turns it into
a three-tab surface and adds the swap flow.

## Goals (acceptance criteria, from #507)

- On the glyph page, a tab bar with **Mine**, **Owned**, **Commu**.
  - **Mine** → glyphs I created.
  - **Owned** → glyphs I've swapped (my entitlements).
  - **Commu** → community glyphs (approved + listed), excluding my own creations.
- Accessing a community glyph opens a **drawer** with the glyph and an **interactive
  slide-to-confirm** control.
  - Sliding produces **increasing haptic feedback** and plays a **pebbles sound**.
  - Completing the slide produces a **sharp success haptic** + a **bamboo sound**, and
    the drawer **transitions in place to the "owned" state**.

## Non-goals

- Glyph submission to the community (deferred).
- Favouriting (web has it; not in this iOS task, not in the mockups).
- Fixing `GlyphPickerSheet`'s widened RLS behaviour (see "Known pre-existing quirk").

## Design decisions

### Backend: no changes required

By keeping the three social/attribution values as placeholders for V1 (see below), the
whole feature is reachable with existing surfaces — **no migration, no new RPC**:

- **Commu** reuses the existing `v_glyph_market` view (`security_invoker`), which already
  exposes `id, user_id, name, strokes, view_box, created_at, price, owned, favourited`
  for approved + listed glyphs. Filter out rows where `user_id = auth.uid()` (match web).
- **Owned** is a client select on `glyph_entitlements` embedding `glyphs`, plus
  `price_paid` and the entitlement `created_at` (acquired date).
- **Mine** is a client select on `glyphs` where `user_id = auth.uid()`, embedding
  `glyph_submissions(price, status, listed)` so each cell can show its cost badge
  (`0` when the glyph has no approved+listed submission).
- **Swap** calls the existing `buy_glyph(p_glyph_id)` RPC → `{ entitlement_id, balance }`.
  It atomically spends karma, grants the entitlement, and credits the creator. Client-side
  we gate the slider on `balance >= price`; the server independently raises
  `insufficient_karma`.

These are single-request PostgREST reads (embeds are one request), so they satisfy the
AGENTS.md "single-table read = client is fine" rule. If a future task adds the creator /
owners / usage data, it lands as **one `security definer` detail RPC** — a clean, additive
follow-up that does not change the iOS view code beyond swapping placeholders for values.

### Placeholders for data we don't cheaply have

The Figma drawer shows three stat cards and a creator handle. Two of those values are
cross-user aggregates behind RLS and one is behind owner-only profile RLS, so they are
**not cheaply available** and are **not in the acceptance criteria**. We keep the full
visual composition and render placeholders, so the later data pass is a drop-in:

| Element                         | V1 treatment                                            |
| ------------------------------- | ------------------------------------------------------- |
| 📅 Created date                 | **Real** — from `created_at` (view / entitlement).      |
| 🐚 Usage count (platform-wide)  | Placeholder — icon + muted "Soon".                      |
| 👥 Owners count                 | Placeholder — icon + muted "Soon".                      |
| Creator `BY @name`              | Placeholder — muted "BY @community" (or similar).       |
| ✦ Cost                          | **Real** — `price`.                                     |
| Me · ✦ balance                  | **Real** — from `PathStatsService.karma`.               |

Placeholder styling (exact copy, whether "Soon" vs em-dash) is a visual detail to finalise
against Figma during implementation; the layout is preserved either way.

### Tap behaviour per tab

- **Mine** cell → existing **rename** alert (unchanged).
- **Owned** cell → open the drawer in the **owned** state (informational).
- **Commu** cell → open the drawer: **swap** state if not yet owned, **owned** state if the
  caller already owns it (`owned = true` from the view).

### Drawer as a morphing sheet

A `.sheet` with a detent (recommended over a nav push or a custom overlay) so the same
presentation can **morph SWAP → OWNED in place** after a successful slide, satisfying "the
Glyph page changes state to the owned glyph state".

## Components

### Data layer — `apps/ios/Pebbles/Features/Glyph/`

- **`GlyphMarketService`** (new, sibling to the existing `GlyphService`, which stays for
  carve/rename): `@MainActor`, wraps `SupabaseService`.
  - `listMine() -> [GlyphGridItem]`
  - `listOwned() -> [GlyphGridItem]`
  - `listCommunity() -> [GlyphGridItem]` (excludes own)
  - `buy(id: UUID) -> BuyGlyphResult` (`{ entitlementId, balance }`)
  - Detail for the drawer is assembled from the grid item + the row's `created_at` /
    `owned` / acquired date — no separate fetch in V1.
- **Models:**
  - `GlyphGridItem { glyph: Glyph, price: Int, owned: Bool, createdAt: Date, acquiredAt: Date? }`
    — one type reused across tabs; fields populated per tab (e.g. `acquiredAt` only for
    Owned, `owned` only meaningful for Commu).
  - `BuyGlyphResult { entitlementId: UUID, balance: Int }` (Decodable from `buy_glyph`).

### UI — `apps/ios/Pebbles/Features/Glyph/Views/`

- **`GlyphsListView`** (refactor): owns `selectedTab: GlyphTab`, loads the relevant list on
  tab change, renders the grid, overlays the tab bar, and presents the drawer. Keeps the
  toolbar "+" carve action and the Mine-tab rename flow.
- **`GlyphTabBar`** (new): floating segmented pill, three segments —
  Mine (person icon), Owned (seal icon), Commu (people icon). Selected segment gets the
  accent pill highlight. Liquid-glass background reusing the iOS-26 `.glassEffect` +
  iOS-17 `.regularMaterial` fallback pattern established in `KarmaEarnedCapsule`.
- **`GlyphCell`** (new or inline): `GlyphView` + name + cost badge (`✦ N`, hidden/`0` when
  unpriced). Shared by all three tabs.
- **`GlyphDetailDrawer`** (new): the sheet body. Renders `swap` or `owned` state. Contains
  the header (Cancel · title · info button — info is inert/removed for V1 since there's no
  extra detail to show), the glyph, the name + creator placeholder, the three stat cards
  (date real, usage/owners placeholder), the divider (cost ✦N for swap, seal for owned),
  the "Me ↔ Creator" row, and either `SlideToConfirm` (swap) or the acquired date (owned).
- **`SlideToConfirm`** (new, generic-ish but lives with the glyph feature for now): a track
  that fills as the thumb is dragged; the thumb carries the cost. Props: `label`, `cost`,
  `isEnabled`, `onConfirm`. Drives haptics/audio via the injected services. Disabled state
  shows the "not enough karma" inline note.

### Haptics & audio (extend existing services)

- **`HapticsService`** (`apps/ios/Pebbles/Services/HapticsService.swift`): add
  - a **continuous** player started on drag begin whose intensity is ramped with drag
    progress via `CHHapticDynamicParameter` (`.hapticIntensityControl`), stopped on
    end/cancel;
  - a **sharp transient** (`playGlyphSwapSuccess()`, high sharpness ~0.9) fired on
    completion.
- **`AudioService`** (`apps/ios/Pebbles/Services/AudioService.swift`): add
  - `playGlyphSlideTick()` → `pbbls-sfx-pebbles_drop`, retriggered at a few progress
    thresholds during the drag (granular scrub, capped so it isn't noisy);
  - `playGlyphSwapSuccessSound()` → `pbbls-sfx-bamboo`.
  - Both `.m4a` assets are provided by the user; copy them into
    `apps/ios/Pebbles/Resources/` and ensure they're bundled (they sit alongside the
    existing `pbbls-sfx-ceramic.m4a`; confirm `project.yml` includes them).

### Karma balance

Read the current balance from the existing `PathStatsService.karma` for the "Me · ✦
balance" row and to gate the slider. After a successful `buy`, update the balance from
`buy_glyph`'s returned `balance` and refresh the affected lists (the glyph leaves Commu's
buyable state and appears in Owned). A swap is a *spend*, so it does **not** trigger the
karma-earned capsule.

## Data flow — the swap

1. User taps a Commu glyph they don't own → `GlyphDetailDrawer` opens in `swap` state.
2. If `balance < price`, the slider is disabled with an inline note.
3. On drag: continuous haptic intensity ramps with progress; `pebbles_drop` ticks at
   thresholds.
4. On crossing the confirm threshold: fire sharp success haptic + `bamboo` sound, call
   `GlyphMarketService.buy(id)`.
5. On success: update balance, mark the item owned, **morph the drawer to `owned` state**
   (seal + acquired date), refresh lists so the glyph appears under Owned.
6. On failure (`insufficient_karma`, `not_in_market`, `already_owned`, generic): revert the
   slider, show a localized inline error; log via `os.Logger`.

## Error handling

- Every list load and the buy path log failures via `os.Logger` and surface a retry /
  inline message (mirror the existing `GlyphsListView` load-error pattern).
- Map `buy_glyph`'s known Postgres error hints to friendly localized copy; fall back to a
  generic "Couldn't complete the swap" message.

## Localization

All new strings go into `Pebbles/Resources/Localizable.xcstrings` with `en` + `fr` values
(tab labels, drawer title SWAP/OWNED, "Acquired", cost/karma labels, placeholder "Soon",
error copy). Brand word "Pebbles" stays `Text(verbatim:)`.

## Testing

Swift Testing (`@Suite`/`@Test`/`#expect`). Focus on the pure/decoding seams (UI is not
unit-tested per the iOS CLAUDE.md "no UI tests for now"):

- Decoding of `BuyGlyphResult` and the `glyph_entitlements`/`glyphs` embed shape.
- `GlyphMarketService` list mappers (row → `GlyphGridItem`, including price `0` fallback and
  the Commu "exclude own" filter).
- `SlideToConfirm` progress→state logic if extracted into a pure helper (threshold, enabled
  gating) — keep the gesture thin and the decision function testable.

## Known pre-existing quirk (not fixed here)

`GlyphPickerSheet` (pebble/soul glyph picker) calls `GlyphService.list()`, which since the
marketplace migration returns approved community glyphs via widened RLS — so the picker may
show glyphs the user hasn't carved or bought. Out of scope for #507; flagged as a follow-up
(the picker should likely show Mine + Owned + system only).

## Cross-cutting deliverables

- **Arkaik:** update `docs/arkaik/bundle.json` — the glyph page gains tabbed states and a
  swap drawer flow.
- **Lab Note:** draft a bilingual (EN/FR) end-user blurb in the PR body (`feat`, user-facing).
- **Build/lint:** Large task → full `npm run build` + `npm run lint` and the plan/TDD
  ceremony.
