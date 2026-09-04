# M51 client UI: grade selectors, share affordances, connection shared-pebbles page — design

**Date:** 2026-08-17
**Issues:** #709 (iOS), #710 (Android), #711 (web)
**Milestone:** M51 · Privacy grades
**Depends on:** #712 (backend + web activation), #713/#714 (native secret decode + default fixes) — all merged.

## Context

#712 activated `pebbles.visibility` as `secret | private | public`: backfill to
`secret`, visibility-aware RLS (`private` = mutual connections, `public` = any
authenticated user), and the anonymous `/p/[id]` share page over
`get_shared_pebble`. The web composer already has the three-state
`VisibilityPicker`; #713/#714 gave the native enums the `secret` case and the
`secret` default but no UI. This batch finishes the M51 user-facing surface.

**No database changes anywhere in this batch.** Everything rides on the RLS and
RPCs #712 shipped. Cross-surface behavior is already proven by
`verify-pebble-visibility.ts`.

## Decisions (D1–D8)

### D1 — Batch shape: three PRs, web first

One PR per issue, landed **#711 → #709 → #710**, so the natives mirror settled
web copy. Android mirrors iOS 1:1 per the standing rule.

### D2 — Connection shared-pebbles data access: direct client reads, no new RPC

The `/connections/[id]` page derives the peer and queries their shared pebbles
with **direct client reads** — no `get_connection_pebbles` RPC:

1. Read the `connections` row by id. RLS (`auth.uid() in (user_a, user_b)`)
   already exposes the row — including both user ids — to its two members, so
   peer id = whichever of `user_a`/`user_b` is not `auth.uid()`.
2. Query the `pebbles` table where `user_id = <peer>` with the `emotions` FK
   embed (reference data, RLS `using (true)`), ordered `happened_at desc` —
   not `v_pebbles_full`, which lacks `render_svg`. The widened `pebbles_select`
   trims the result to `private` + `public` rows; enrichments stay owner-only
   and the page must not render them.

Why not an RPC: the roadmap explicitly calls this read "legal under the widened
RLS"; both reads are single-table single-statement (the RPC-first rule targets
multi-table *writes* and non-atomic stitching, neither applies); and the M50
"never return `user_id` from a projection" rule concerns *public/anon*
projections — between mutually-consented connections the raw row already
carries both ids by design (M49).

### D3 — Web grade change: the detail badge becomes the picker (revocation)

Web can currently set a grade only at creation. The grade icon already rendered
in `PebbleDetail`'s header becomes a popover trigger opening the same
three-option menu as the composer; selecting calls the existing
`onUpdatePebble` plumbing (`update_pebble` coalesces `visibility`). Failures
surface inline like other detail edits. This is also **share revocation**:
flipping a public pebble back kills its `/p/[id]` link (the page 404s).

Implementation: extract the shared popover content out of `VisibilityPicker`
into a `VisibilityMenu` (grades + icons + labels in one place); the composer
chip and the detail badge are two triggers of the same menu. Icons stay
Lock/Users/Globe as shipped in #712.

### D4 — Web share affordance: header button, `navigator.share` + clipboard

A `Share2` icon button in `PebbleDetail`'s header, rendered **only when
`visibility === "public"`**. Behavior mirrors `InviteSection`:
`navigator.share({ url })` in a try/catch; unavailable or dismissed → copy to
clipboard with a brief "copied" affordance. URL:
`${window.location.origin}/p/${pebble.id}` (origin-relative so previews and
prod both work).

### D5 — Web `/connections/[id]`: a new detail page (not row expansion)

`ConnectionRow` becomes a link to `/connections/[id]` (id = `connection_id`).
Remove/block actions stay on the list. The page (client component, like the
rest of the authed app):

- **Header:** peer glyph + display name + connected date — from the
  already-fetched `get_connections` projection, matched client-side by
  `connection_id`. No new peer-display query.
- **Body:** "Pebbles they share with you" — compact tiles: `render_svg` tinted
  via the palette CSS custom properties (the `.pbbls-visual` contract), name,
  emotion dot, date. A lean `ConnectionPebbleTile` component, **not**
  `PebbleVisual` (owner-oriented, wants a full `Pebble`). Tiles do not link
  anywhere in v1 (the owner-only `/pebble/[id]` route would 404 on RLS-trimmed
  enrichments; a read-only cross-user pebble view is out of scope).
- **Empty state:** friendly copy for zero shared pebbles.
- **Unknown/removed id:** render the empty/"not found" state with a link back
  to `/connections` — never a crash.
- New hook `useConnectionPebbles(connectionId)` in `lib/data/`; peer-derivation
  is a pure exported function so it gets a unit test.

### D6 — Native selector: a toolbar chip (Menu), not a form row

Both native composers get a **compact chip** showing the current grade's icon +
label, opening a three-option menu — the native analog of the web composer
chip:

- **iOS:** a SwiftUI `Menu` chip in the create/edit sheets' **bottom toolbar**
  (create: alongside "Save as draft"; edit: same placement), bound to
  `PebbleDraft.visibility`. Default remains `.secret` (#713).
- **Android:** an `AssistChip` opening a `DropdownMenu`, in the composer/edit
  screens' bottom control area, bound to the draft state. Default remains
  `SECRET` (#714).

### D7 — Grade wording and badge icons: parity with web

Labels are **Secret / Connections / Public** (FR: **Secret / Connexions /
Public**) on every surface — `private`'s user-facing label is "Connections"
everywhere; the wire strings never change.

- **iOS:** `Visibility.label` for `.private` changes from "Private" to
  "Connections" (xcstrings gains "Connections" en+fr; "Private" is removed if
  nothing else references it). `PebblePrivacyBadge` maps per-grade SF Symbols:
  `lock.fill` (secret), `person.2.fill` (private), `globe` (public) — both
  `.capsule` and `.chip` styles.
- **Android:** grade labels land in both `strings.xml` files; the badge gains
  per-grade icons via two new vector drawables (`ic_people`, `ic_globe`)
  drawn in the same style as `ic_lock`.

### D8 — Native share: system share sheet when public

Visible only when the open pebble is `public`; URL is always
`https://www.pbbls.app/p/<id>` (canonical host, the invite-link precedent —
native apps have no notion of a web origin).

- **iOS:** `ShareLink` as a toolbar item in `PebbleDetailSheet`; URL built by a
  small helper next to `Connection.swift`'s invite URL.
- **Android:** a share icon in `PebbleDetailScreen`'s top bar firing an
  `Intent.ACTION_SEND` chooser; host reuses the `INVITE_HOST`-style constant.

## Out of scope

- Native connection-detail / shared-pebbles screens (ride with later social
  work; M53 pairs touches the same surfaces).
- A cross-user pebble read view (tiles don't navigate in v1).
- Snap display anywhere in shared contexts (excluded from shares in v1, #712).
- Any migration, RPC, or RLS change.

## Error handling

- Web grade update failure → inline error in `PebbleDetail` (existing pattern);
  the badge reverts to the server state.
- Connection page fetch failures → logged (`console.error` with scope prefix)
  and rendered as the empty/error state, never a blank screen.
- Native share/menu affordances are display-only over already-fetched state —
  no new failure paths beyond the existing update flows.

## Testing

- **Web:** pure unit tests for peer derivation (vitest); workspace lint + build
  + test. Manual smoke via prod build for `/connections/[id]`.
- **iOS:** Swift Testing for the share-URL helper and the per-grade label/icon
  mapping; full local test run (fresh DerivedData).
- **Android:** JUnit for the share-URL builder; `android.yml` CI gates ktlint,
  unit tests, screenshot render, assemble.
- No harness changes: `verify-pebble-visibility.ts` already proves the
  database contract this UI sits on.

## Bookkeeping

- **Arkaik (hosted + local dual-write):** new `V-connection-detail` view +
  `displays`/`calls` edges; `AC-pebble-grade-choice` flips live per platform as
  each PR lands (web covers the detail-badge picker too — add a `covers` edge
  to `V-pebble-detail`); new acceptance for the shared-pebbles list covering
  `V-connection-detail`; `AC-shared-pebble-link` gains ios/android platform
  statuses when the native share sheets land (platforms list widens from
  `["web"]` to all three).
- **Lab Notes:** every PR is `feat` → EN/FR note required (platform: `webapp`,
  `ios`, `android` respectively).
- **Decision log:** no new entries expected — this batch implements decisions
  already recorded (2026-08-17 privacy-grades entry). Re-check at PR time.
