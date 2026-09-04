# Mutual connections — design (M49)

Design doc for milestone **M49 · Mutual connections**. Parent spec:
`2026-07-28-store-launch-roadmap.md` §M49 (:85-91), plus §1 item 4 (invite/QR-
only discovery, symmetric consent, :10), the convergence-map rows
"`connections` + invites + blocks" and "definer-RPC projection pattern"
(:149-152), and §5 item 4 (the decision-log entry this milestone owes). The
four issues cut from this doc follow the house cadence: migration + types →
web reference → iOS → Android (roadmap :32).

Connections are pure greenfield: "No user↔user primitive exists anywhere. All
user tables RLS to `user_id = auth.uid()`; `profiles_select` is owner-only;
souls are explicitly *not* users" (roadmap :23, verified —
`20260411000001_core_tables.sql:154`). M49 sits on the critical path
(M45 → M49 → M53 → M56 → M57, roadmap :50) and gates the M51 `private` tier,
M52 seaming, M53 pairs and the M56 UGC batch. Its own gate is closed: M45/F1
recreated `v_pebbles_full` with `security_invoker`
(`20260729000000_v_pebbles_full_security_invoker.sql`). M48 is not a
dependency.

M49 ships three tables (`connections`, `connection_invites`,
`connection_blocks`), five definer RPCs (the roadmap's four plus an anonymous
invite preview — D4), and UI ×3: invite screen (link + QR), accept flow (web
`/invite/[token]` including the sign-up-first path, universal/App Links on
mobile), connections list with remove/block. **No push, no realtime** —
accepted connections surface on next app open (roadmap :28, :91).

Pre-constrained by the roadmap throughout: single-row symmetric `connections`
with **no status column** ("accepting the invite *is* the mutual consent",
:87); multi-use invite until revoked/expired, one active invite per user
(:88); definer-RPC-only writes returning display *projections*, never a
`profiles` row (:89, §5 item 8); blocks from day one for Apple UGC review
(:90); text + CHECK, never enums (:28); every migration followed by
`npm run db:types --workspace=packages/supabase` (root `AGENTS.md`).

## Shipped pieces

| Piece | Path |
|---|---|
| Migration (3 tables + RLS + 5 RPCs + purge extension) | `packages/supabase/supabase/migrations/<ts>_mutual_connections.sql` |
| Purge regression harness extension | `packages/supabase/scripts/verify-account-purge.ts` |
| Web data layer | `apps/web/lib/data/useConnections.ts` |
| Web surfaces | `apps/web/app/connections/page.tsx`, `apps/web/app/invite/[token]/page.tsx`, `apps/web/components/connections/` |
| Web pending-invite store | `apps/web/lib/hooks/usePendingInvite.ts` |
| Web link infra | `apps/web/app/.well-known/apple-app-site-association/route.ts`, `apps/web/public/.well-known/assetlinks.json`, `apps/web/app/auth/callback/route.ts` (`next` param) |
| iOS | `Features/Connections/` (list, invite sheet, accept sheet), `Services/ConnectionsService.swift`, `Pebbles/Pebbles.entitlements`, app-root `onOpenURL` routing |
| Android | `features/connections/`, `services/ConnectionsService.kt`, `AndroidManifest.xml` (App Links intent-filter), `gradle/libs.versions.toml` (zxing-core) |

## D1 — Three greenfield tables; the symmetric pair is one ordered row and blocks are a directed pair

Per roadmap :87-90:

- `connections (id uuid pk, user_a uuid, user_b uuid, check (user_a < user_b),
  unique (user_a, user_b), created_at)`.
- `connection_invites (id uuid pk, inviter_id uuid, token text not null
  unique, created_at, expires_at timestamptz not null default now() +
  interval '7 days', revoked_at timestamptz)`.
- `connection_blocks (blocker_id uuid, blocked_id uuid, primary key
  (blocker_id, blocked_id), check (blocker_id <> blocked_id), created_at)`.

All user FKs are `references auth.users(id) on delete cascade` — the
`pebble_drafts` precedent (`20260729213348_pebble_drafts.sql:31`); explicit
purge deletes stay belt-and-braces on top (D10). `connections` gets a
surrogate id because `remove_connection(p_connection_id, …)` addresses it
(roadmap :89); `connection_blocks` follows the composite-pk join-table
convention (`pebble_souls`, `20260411000001_core_tables.sql:105`).

No status column, deliberately: there is no pending state between two known
users — **the invite table *is* the pending state**. Rejected alternatives:

1. Two mirrored rows per connection — row-count invariants, `get_connections`
   and purge all become double-entry bookkeeping; the ordered pair plus
   `on conflict do nothing` gives structural idempotency for free.
2. A `status` text on invites — `revoked_at`/`expires_at` timestamps carry
   strictly more information (when, not just what), and expiry is evaluated
   at read time everywhere (D3), so a status column could only drift.

Indexes: the `unique (user_a, user_b)` index covers `user_a` lookups; a
`connections (user_b)` index covers the other membership direction;
`connection_invites.token` unique index doubles as the O(1) validation
lookup; plus the partial unique index from D3.

## D2 — The invite token is 32 random bytes, base64url, stored in plaintext

Generation, in-database:
`translate(encode(extensions.gen_random_bytes(32), 'base64'), '+/=', '-_')` —
43 URL-safe chars, 256 bits of entropy. `gen_random_bytes` lives in pgcrypto,
and **no migration creates any extension today** (grep-verified; the
`gen_random_uuid()` used everywhere is a PG13 builtin), so the migration
opens with idempotent `create extension if not exists pgcrypto with schema
extensions;` and schema-qualifies the call — house definer functions pin
`set search_path = public` (`20260629193636_wallet_balances.sql:19`), which
does not include `extensions`.

Plaintext storage is safe and, more importantly, *required*:

1. The token is a short-lived (7-day), revocable, multi-use **capability**,
   not a credential; RLS makes it inviter-only readable (D8) and validation
   happens inside definer RPCs that receive it as an argument.
2. A DB compromise deep enough to read `connection_invites` also reads
   `connections` and `profiles` — strictly worse than the pairing capability
   the token protects.
3. The decisive product reason is **re-display**: "open the invite screen the
   next day and show the same QR" (D3) requires the server to return the
   original token on demand. A hashed token exists in plaintext only at mint
   time, forcing each of three clients to persist it locally — more copies,
   worse multi-device behavior, and it contradicts the multi-use model.

Rejected: storing `sha256(token)` and returning it once (breaks re-display);
JWT-style signed tokens (key management for zero gain — the row already
carries expiry and revocation state).

## D3 — "One active invite per user" is a partial unique index, and `create_connection_invite()` returns the live invite instead of minting a new one

`expires_at > now()` cannot sit in a partial-index predicate (`now()` is not
immutable), so the invariant splits in two: a partial unique index
`on connection_invites (inviter_id) where revoked_at is null`, plus RPC logic
that first revokes (`set revoked_at = now()`) any unrevoked-but-expired
invite of the caller, then returns the live one if present, else inserts.
Consequence: `revoked_at` means "superseded or withdrawn", and at most one
`revoked_at is null` row per user ever exists.

**Return-the-live-one, not revoke-and-reissue**, because of the re-open UX: a
link pasted into a chat yesterday must not silently die because the inviter
re-opened the invite screen, and the QR at the dinner table must survive an
app restart. Signature: `create_connection_invite(p_rotate boolean default
false)` — `p_rotate := true` revokes the live invite and mints fresh, backing
an explicit "new link" affordance. That is the entire revocation surface; no
separate revoke RPC.

Race-safe by construction: two concurrent creates → one insert wins the
partial unique index, the loser catches `unique_violation` and re-selects.
Returns jsonb `{token, expires_at, created_at}`; the client composes the URL
(D11).

Rejected: always-reissue on screen open (kills shared links, defeats
multi-use); an `active boolean` column (derivable, drifts); expiry sweeps
(no cron in house — expiry is evaluated at read time everywhere).

## D4 — A fifth definer RPC, `preview_connection_invite(p_token)`, granted to `anon`, shows who invited you before you have an account

The sign-up-first path (roadmap :91) is incoherent without it: the
`/invite/[token]` page must render "*Alex* invites you to connect" to an
anonymous visitor, but `profiles_select` is owner-only
(`20260411000001_core_tables.sql:154`) and the house rule forbids widening it
— cross-user reads go through definer-RPC jsonb projections (roadmap §5 item
8; shipped precedent `admin_find_user`,
`20260701102810_glyph_marketplace_curation.sql:132`; planned precedent
`get_public_profile` granted to `anon`, roadmap :96).

It returns jsonb and never raises — it renders a page, unlike the mutating
accept: `{status: 'valid', inviter: {display_name, glyph: {strokes,
view_box} | null}}`, or `{status: 'expired'}` / `{status: 'not_found'}`.
Expired and revoked share one outward shape and return **no inviter
projection** — a withdrawn token goes fully dark.

Token possession is the authorization: the preview reveals exactly what the
accepter would learn seconds after accepting (display name + avatar glyph
geometry — the established cross-surface glyph read shape `glyphs(id, name,
strokes, view_box)`, `ReferenceDataService.swift:52`, minus name/ownership).
Nothing is enumerable against 2^256 tokens (D2). Grants: revoke from
`public`, grant execute to `anon, authenticated` (grant hygiene per
`20260729201326_account_deletion_purge.sql:189-190`).

Rejected:

1. A blind invite page ("someone wants to connect") — destroys conversion on
   the sign-up-first path; a personal invite is the whole point.
2. Preview only post-auth — forces account creation before showing who is
   asking; backwards consent order.
3. Widening `profiles` RLS with a "display columns only" policy — banned
   outright (roadmap :96, §5 item 8).

## D5 — `accept_connection_invite(p_token)` is idempotent, treats blocks in either direction as expiry, and returns a projection with `already_connected`

Validation order, one definer transaction, house short-slug errors
(`raise exception … using errcode='42501'` style,
`20260701102810_glyph_marketplace_curation.sql:59`):

1. Token lookup (`revoked_at is null and expires_at > now()`) → else
   `invite_not_found` / `invite_expired`.
2. Self-accept (`inviter_id = auth.uid()`) → `cannot_accept_own_invite`.
3. Block check in **either direction** (inviter→accepter or accepter→inviter
   in `connection_blocks`) → raise `invite_expired`, deliberately
   indistinguishable from real expiry so a block is never revealed to either
   party. This is also the mechanism that stops a removed-with-block peer
   from re-entering through the remover's still-live multi-use QR (D6).

Why either-direction: a block means one party wants no relation; checking
only blocker→accepter would let the blocker themselves reconnect by scanning
the blocked user's invite — an asymmetry with no product meaning in a
symmetric-connections model.

Insert: normalize to `(least(a,b), greatest(a,b))`, `on conflict do nothing`,
read the row back. If it pre-existed the RPC **succeeds** with
`already_connected: true` — never errors. The multi-use QR at the dinner
table makes re-scans, double-taps and network retries the *normal* case; the
client renders "you're already connected" from the same payload. Rejected:
raising on already-connected (forces all three clients to special-case a
non-failure and breaks retry-safety).

Returns jsonb `{connection_id, already_connected, peer: {display_name,
glyph: {strokes, view_box} | null}, connected_at}` — the "inviter display
*projection* — never a profiles row" (roadmap :89). The invite row is not
consumed (multi-use, roadmap :88), and nothing here touches karma (D9).

## D6 — `remove_connection(p_connection_id, p_block)` ships with a loud M52/M53 extension marker, and blocking is one directed row, irreversible in-app until M56

Body at M49: assert the caller is `in (user_a, user_b)` else `not_found`;
delete the row; if `p_block`, insert `(auth.uid(), peer)` into
`connection_blocks` `on conflict do nothing`. Block direction is fixed:
**the remover blocks the removed**.

The roadmap promises "severs dependent seams and pairs in the same
transaction" (:89), but those tables arrive in M52/M53. The function ships
with an explicit `>>> M52/M53: sever seams and pairs for this pair HERE <<<`
comment block — the `purge_account` append-marker pattern
(`20260729213348_pebble_drafts.sql:192`) applied to a function body, and the
sibling-symmetry discipline of root `AGENTS.md` applied forward in time.

Blocking retroactively defends the still-live invite with zero invite
mutation: `accept_connection_invite` checks blocks (D5), so the blocked peer
scanning the remover's live QR gets `invite_expired` while third parties keep
using the same invite. This is a deliberate property, not an accident.

A blocked pair cannot reconnect in v1 until the block row is removed, and
block *management* UI is deferred to M56 ("blocks surfaced", roadmap :137).
Coherence check passes: M56 sits after the feature freeze and gates M57, so
block management exists before any public user does. Residual risk, recorded:
for internal/TestFlight users an accidental block is recoverable only by a
service-role row delete.

Rejected: symmetric double-row blocks (one row already reads bidirectionally
in D5); auto-revoking the remover's invite on block (punishes the other N
dinner guests for one bad actor); a "block both ways" variant (no product
story).

## D7 — `get_connections()` is a definer jsonb projection, because a view cannot cross `profiles` RLS without recreating the leak class

Returns a jsonb array `[{connection_id, connected_at, peer: {display_name,
glyph: {strokes, view_box} | null}}]`, peer resolved via `case when user_a =
auth.uid() then user_b else user_a end`, ordered `connected_at desc`
(newest-first list convention, `20260729213348_pebble_drafts.sql:37`).

Why not a view — the two-sided trap: a `security_invoker` view joining
`profiles` returns null peers (owner-only `profiles_select`), and a
definer-rights view is exactly the vulnerability class the 2026-07-29
decision-log entry banned after #616 (`v_pebbles_full` readable cross-user).
A definer **RPC** projecting three display fields is the sanctioned narrow
channel (roadmap :151, §5 item 8).

Glyph geometry crosses users here deliberately: strokes + view_box only —
the render payload, the same minimal exposure M50's public profile will make
(roadmap :96). One call feeds the list on all three surfaces; refreshed on
screen open, no realtime (roadmap :28).

## D8 — RLS posture: SELECT-only policies on all three tables; writes exist only inside the definer RPCs

- `connections`: `for select to authenticated using (auth.uid() in (user_a,
  user_b))`.
- `connection_invites`: `for select to authenticated using (inviter_id =
  auth.uid())` — the token is owner-visible only, which is what makes D2's
  plaintext safe.
- `connection_blocks`: `for select to authenticated using (blocker_id =
  auth.uid())` — the blocked user must never learn of the row, consistent
  with D5's indistinguishable error.
- **No insert/update/delete policies on any of the three.**

The RPC-only-writes posture is established house practice: `wallet_balances`
— "No INSERT/UPDATE/DELETE policies: maintained exclusively by the trigger
below" (`20260629193636_wallet_balances.sql:15`); `karma_events` — the
permissive insert policy was deliberately dropped because "karma events are
only created by security definer functions"
(`20260411000005_security_hardening.sql:394-398`); planned M54 whispers take
the same shape (roadmap :126).

Deliberately **not** the drafts-style single `for all` policy
(`20260729213348_pebble_drafts.sql:48`): that pattern is for the sanctioned
direct-client single-table CRUD case (root `AGENTS.md`). Every connections
write is multi-table validated logic — accept touches invites, blocks and
connections — so client writes must be structurally impossible, not merely
owner-scoped. All policies say explicit `to authenticated`
(`20260729213348:41-45` rationale); all five RPCs revoke from `public` and
grant to `authenticated`, the preview additionally to `anon` (D4).

## D9 — Connections emit zero karma, structurally

`karma_events_reason_check` constrains `reason` to exactly
`('pebble_created','pebble_enriched','pebble_deleted','grant','purchase',
'refund')` (`20260629192621_karma_events_type_axis.sql:20-24`). None of the
five RPCs touches `karma_events`, and an accidental insert would violate the
CHECK — the constraint is the guard; no new reason is added. Social-graph
mechanics must not be karma-farmable (a connect/disconnect loop would
otherwise be a mint) — the drafts milestone set the same invariant for
save/publish (M47 design D1, roadmap :175). Verified end-to-end in D14.

## D10 — `purge_account` deletes connections and blocks where the user is *either* side, invites where inviter

Third customer of the M46 standing rule
(`20260729201326_account_deletion_purge.sql:26-28`). The deletes go at the
section-(4) `>>> APPEND new per-user tables from later milestones HERE. <<<`
marker (current position `20260729213348_pebble_drafts.sql:192`):

- `delete from connections where p_user_id in (user_a, user_b)`
- `delete from connection_invites where inviter_id = p_user_id`
- `delete from connection_blocks where p_user_id in (blocker_id, blocked_id)`

Two of the three are **not** the usual `user_id = p_user_id` shape — worth
stating loudly so a future copy-paste doesn't silently halve them. All FKs
point only at `auth.users` (with cascade, D1) and nothing references these
tables at M49 — M52/M53 will insert their own severing above these lines —
so section-(4) placement is free; the explicit deletes keep "all personal
rows gone at RPC success" true when `deleteUser` is the failing step
(`20260729213348:189-191`).

Counterparty semantics: deleting A removes the shared connection row (B
simply stops seeing it — correct for a symmetric primitive) and removes
blocks in both directions involving A (a block against a nonexistent user is
meaningless). `verify-account-purge.ts` gains: seed an A↔B connection, A's
live invite, A→B and B→A blocks; assert all four at zero post-purge, B's
unrelated rows intact, and a re-run converges. The `delete-account` edge
function is unchanged — it is table-agnostic and connections have no storage
footprint.

## D11 — One canonical URL, `https://www.pbbls.app/invite/<token>`, opened via universal/App Links; no custom-scheme fallback

`www.pbbls.app` is the only production web URL in the repo — iOS already
deep-links legal docs to it (`Features/Auth/LegalDocumentSheet.swift:14-15`),
and those paths are `apps/web` routes, so the domain serves the web app.
Nothing in `apps/web` carries a canonical-URL config (`vercel.json` is
schema-only; auth uses `window.location.origin`,
`lib/data/useSupabaseAuth.ts:153`) — web composes invite links from
`window.location.origin`; native surfaces hardcode the host as
`LegalDocumentSheet` does.

The deep-link starting point is near-zero: the **only** scheme anywhere is
`pebbles://auth-callback`, registered only by Android
(`AndroidManifest.xml:37-42`); iOS has no `CFBundleURLTypes` and no
`onOpenURL` anywhere — OAuth is absorbed inside `ASWebAuthenticationSession`
(`Services/SupabaseService.swift:115`). No AASA, no assetlinks, no
share-sheet code exists. Per platform, M49 ships:

- **Web** — `/.well-known/apple-app-site-association` via a route handler
  (`app/.well-known/apple-app-site-association/route.ts`), because the
  extension-less file must serve `application/json` (a `public/` static file
  would not); content `appID "256Z7G8WLM.app.pbbls.ios"` (team
  `project.yml:15`, bundle id `:51`), paths `/invite/*`. Plus static
  `public/.well-known/assetlinks.json` for package `app.pbbls.android`.
- **iOS** — `com.apple.developer.associated-domains:
  applinks:www.pbbls.app` in `Pebbles/Pebbles.entitlements` (wired via
  `CODE_SIGN_ENTITLEMENTS`, `project.yml:54`; xcodegen regenerates), plus
  the app's first `.onOpenURL` routing at the root. Portal-side, the
  capability must be enabled on the `app.pbbls.ios` App ID (maintainer).
- **Android** — an `autoVerify="true"` https intent-filter for
  `www.pbbls.app` + `/invite/` pathPrefix beside the existing OAuth filter.

**Skip the `pebbles://invite?token=` custom scheme.** The https URL is its
own fallback: a not-installed user lands on the fully functional web accept
page, which a custom scheme can never do (browser error instead); it would
be net-new scheme registration on iOS; and one URL must serve link, QR and
web simultaneously — a second format doubles every surface's parsing for
zero coverage gain. Corollary worth stating: if App/universal-link
verification fails on a device, the QR/link still opens the browser and the
web flow completes — link infra risk cannot brick the feature.

## D12 — Sign-up-first: the token persists client-side as a pending invite, and the auth callback learns an optional `next` param

The web invite page is public and server-renders from
`preview_connection_invite` with the anon key (the M50 `/u/[handle]`
pattern, roadmap :98). Authenticated visitors get the accept confirm;
anonymous visitors get the preview plus "sign up / log in to accept".

The funnel loses the URL today: `register/page.tsx:30` replaces authed users
to `/onboarding`, and `/auth/callback/route.ts:52` hardcodes the destination
to `/path` or `/onboarding`. Two small mechanisms restore it:

1. The invite page stores the token via a new `lib/hooks/usePendingInvite.ts`
   backed by `localStorage` — components never touch `localStorage` directly
   (`docs/agents/data-and-async.md:8`), and the hook follows the
   `useSyncExternalStore` pattern the drafts design canonized (M47 D10).
   `localStorage` over sessionStorage because it survives the OAuth tab
   round-trip, and an email-confirmation round-trip if M55 enables it
   (roadmap :131).
2. `/auth/callback` gains a validated, **relative-path-only** `next` search
   param (open-redirect guard), threaded through the OAuth `redirectTo`.

After auth *and onboarding* complete, the pending token routes the user back
to `/invite/<token>` for an **explicit accept tap** — accept is never fired
implicitly on sign-up. Consent must be a deliberate act, and this keeps the
accept path identical for both funnels. The token is cleared on accept, on a
terminal invite error, or when expired.

Native surfaces skip all of this: a universal link only opens an installed
app; the unauthenticated native case shows the preview, then holds the token
in the service until a session exists (same explicit-accept rule).

## D13 — QR codes are generated client-side: `qrcode` on web, CoreImage on iOS, zxing-core on Android

The QR encodes the D11 https URL and nothing else — a stock camera app
resolves it through App/universal links or the browser.

- **Web** — no QR capability exists (`apps/web/package.json`); add the small
  `qrcode` package (SVG/canvas output, no React wrapper) as its own isolated
  commit in the web issue.
- **iOS** — `CIFilter.qrCodeGenerator()` (CoreImage): zero new dependencies.
- **Android** — no QR/zxing entry exists in `gradle/libs.versions.toml`; add
  `com.google.zxing:core` (encode-only, pure JVM → unit-testable) via the
  version catalog as a deliberate isolated commit, per
  `apps/android/CLAUDE.md`.

Rejected: server-side QR rendering (an endpoint for data the client already
has); a shared QR implementation (three different render stacks — the URL
string is the only shared artifact).

## Scope — the four issues

House cadence (roadmap :32):

1. **Backend** — the `<ts>_mutual_connections.sql` migration: D1 tables +
   indexes, D2/D3 invite machinery, D4-D7 RPCs, D8 RLS, D10 purge extension;
   `npm run db:types` regen committed; `verify-account-purge.ts` extension.
2. **Web (reference)** — `/connections` list with remove/block, invite UI
   with link + QR (`qrcode` dep, isolated commit), `/invite/[token]`
   preview/accept, `usePendingInvite`, auth-callback `next`, the two
   `.well-known` files. **Web deploys first** — it gates both mobile
   platforms' link verification. Sonner explicit-fire feedback; Lab Note.
3. **iOS** — associated-domains entitlement + `xcodegen generate`, first
   `onOpenURL` routing (flagged: net-new plumbing), `Features/Connections/`
   + `ConnectionsService`, CoreImage QR, ShareLink, `Localizable.xcstrings`
   en+fr; Lab Note.
4. **Android** — `autoVerify` intent-filter, zxing-core catalog commit,
   mirrored feature + service, `strings.xml` en+fr; CI is the gate (no local
   SDK — M47 design D12 precedent); Lab Note.

Risk register: the unusually risky piece is universal/App Links end-to-end —
AASA CDN caching delays, the Apple App ID capability (portal, maintainer),
and Android verification needing the **Play App Signing SHA-256 fingerprint
from the Play Console** (maintainer input; not derivable from the repo).
D11's browser fallback bounds the blast radius. The DB work is small and
low-risk. Stated assumptions: pgcrypto is available on the hosted project
(the idempotent `create extension` makes the migration correct either way);
`www.pbbls.app` is attached to the `apps/web` deployment.

Arkaik (hosted project is authoritative per the 2026-07-28 decision-log
entry; updated per-issue as code ships, via the `arkaik` skill): views
`V-connection-invite`, `V-connections-list`, `V-invite-accept`; data models
`DM-connection`, `DM-connection-invite`, `DM-connection-block`; endpoints
`API-create-connection-invite`, `API-preview-connection-invite`,
`API-accept-connection-invite`, `API-get-connections`,
`API-remove-connection`; flow `F-connect`.

Decision-log entry owed (roadmap §5 item 4), to be appended with the backend
PR per M46/M47 precedent: "Connections: single-row symmetric, invite/QR
only, no search; blocks from day one" — plus this doc's refinements
(plaintext token, return-the-live-invite, either-direction block check, no
custom scheme).

## D14 — Verification

Per surface, beyond lint/build:

- **RLS probes, second test user** (roadmap :174 style): B cannot select A's
  invite (token invisible) nor A's block rows; both A and B select the
  shared `connections` row; direct insert/update/delete on all three tables
  is rejected for `authenticated` (no write policies). **Anon probe** with
  only the publishable key and no session — all three tables return `[]`,
  `preview_connection_invite` works: the mirror of the #616 live probe.
- **Invite lifecycle** — re-calling `create_connection_invite()` returns the
  same token; `p_rotate` revokes and reissues; an expired invite is
  auto-revoked and replaced on the next create; two concurrent creates yield
  exactly one live row (partial-unique-index race).
- **Accept matrix** — valid token → row + projection; repeat accept →
  `already_connected: true` and no second row; both directions between the
  same pair → one row (ordered-pair normalization); self →
  `cannot_accept_own_invite`; expired/revoked → `invite_expired`; block in
  *either* direction → `invite_expired`, indistinguishable; two different
  friends on one QR → two distinct connections.
- **Remove/block** — remove severs for both sides; remove-with-block then a
  re-scan of the still-live invite by the blocked peer fails while a third
  party still succeeds on the same invite.
- **Karma invariant** — the full invite → accept → remove → block flow
  leaves `count(*) from karma_events` unchanged (D9).
- **Purge** — the extended `verify-account-purge.ts` passes: connection,
  invite and both-direction blocks seeded, all zero after purge,
  counterparty rows intact, re-run converges.
- **Links** — iOS device tap + `swcutil` diagnostics; `adb shell pm
  get-app-links` shows verified; a stock-camera QR scan opens the app when
  installed and the web page when not.
- **Sign-up-first e2e** (the real test of D12) — anon scans QR → preview
  renders → register → onboarding → returned to `/invite/[token]` → explicit
  accept → both users see each other in their lists on next app open (the
  no-push assertion).
