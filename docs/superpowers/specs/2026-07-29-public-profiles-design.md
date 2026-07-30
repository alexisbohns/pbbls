# Public profiles — design (M50)

Design doc for milestone **M50 · Public profiles**. Parent spec:
`2026-07-28-store-launch-roadmap.md` §M50, plus the convergence-map rows
"`handle` + `public_profile`" and "Definer-RPC projection pattern (never widen
`profiles`/enrichment RLS)". The four issues cut from this doc follow the
house cadence: migration + types → web reference → iOS → Android.

A profile today is strictly private: `profiles_select` is owner-only
(`20260411000001_core_tables.sql:155`), every engagement read (`v_ripple`,
`v_bounce`, `get_profile_engagement`) is `auth.uid()`-scoped, and no route on
any surface shows one user to another. M50 makes a profile *optionally* public
behind two explicit opt-ins — claiming a handle, then flipping a switch — and
gives it a shareable home at `https://www.pbbls.app/u/<handle>`: display name,
avatar glyph, pebble count, the evolutive rings (ripple + bounce levels), the
28-day assiduity grid, and (once M48 ships) achievements.

Gate status at design time:

- **M45 landed** — the F1 hard gate is in: `v_pebbles_full` is
  `security_invoker` with anon revoked
  (`20260729000000_v_pebbles_full_security_invoker.sql:35-38,150-151`).
- **M48 (achievements) not started** — the public projection ships a stable,
  empty `achievements` key that M48 fills (maintainer decision, 2026-07-29).
  M50 does not wait for M48.
- **M49 (connections) not started** — before connections there is no in-app
  path to another user's profile, so native viewer screens are deferred to M49
  (maintainer decision, 2026-07-29). M50 ships the RPC (already callable
  authenticated) and the web route, which serves logged-in and anonymous
  visitors alike.

## Shipped pieces

| Piece | Path |
|---|---|
| Migration (columns + `reserved_handles` + guard trigger + `set_handle` + `get_public_profile`) | `packages/supabase/supabase/migrations/<ts>_public_profiles.sql` |
| Types regen | `packages/supabase/types/database.ts` |
| Web public route | `apps/web/app/u/[handle]/page.tsx` + `opengraph-image.tsx`, `apps/web/components/public-profile/` |
| Web settings | `apps/web/components/settings/PublicProfileSection.tsx`, wired in `apps/web/app/settings/page.tsx` |
| iOS | `Features/Profile/Sheets/SettingsSheet.swift` new section + RPC calls in the profile service layer |
| Android | `features/profile/SettingsScreen.kt` new section + `services/ProfileService.kt` |
| Arkaik + decision log | `docs/arkaik/bundle.json` (+ journal), `docs/decisions/log.md` |

## D1 — Schema: `handle`, `public_profile`, and structural invariants

```sql
alter table public.profiles
  add column handle text,
  add column public_profile boolean not null default false;

create unique index profiles_handle_key on public.profiles (handle);

alter table public.profiles
  add constraint profiles_handle_format
    check (handle is null or handle ~ '^[a-z0-9][a-z0-9_]{1,28}[a-z0-9]$'),
  add constraint profiles_public_requires_handle
    check (public_profile = false or handle is not null);
```

- `handle` is nullable — claiming one is the first opt-in, and existing users
  are untouched by the migration.
- The **unique index settles claim races** (roadmap): two concurrent
  `set_handle('same')` calls resolve to one winner and one `unique_violation`,
  no advisory locking.
- The format constraint is the roadmap regex: lowercase `a-z0-9_`, 3–30
  chars, starts and ends alphanumeric. Handles are *stored* normalized
  (lowercase); display is verbatim.
- **Invariants live in CHECK constraints, not only in the RPC**, because
  `profiles_update` RLS lets an owner write columns directly — a client
  `.update({ handle: "Bad Handle!" })` must fail structurally, not by
  convention. (Known quirk left alone: legacy `profiles_update` has no
  `with check`; the newer single-policy style is documented at
  `20260729213348_pebble_drafts.sql:41-50`. Not refactored here.)
- `profiles_public_requires_handle` makes "public but unreachable" (no
  handle → no URL) unrepresentable, so no surface needs to special-case it.

## D2 — `reserved_handles`: admin-extensible without a migration

```sql
create table public.reserved_handles (
  handle text primary key
);
```

Seeded in the migration with three groups:

- **Every top-level web route**, current and already-specced: `path`,
  `record`, `pebble`, `drafts`, `collections`, `souls`, `glyphs`, `carve`,
  `wallet`, `lab`, `docs`, `profile`, `settings`, `offline`, `login`,
  `register`, `onboarding`, `auth`, `sw`, `u`, `p`, `invite`.
- **Infra/brand**: `admin`, `api`, `www`, `app`, `pebbles`, `pbbls`, `root`,
  `system`, `official`, `store`.
- **Impersonation/abuse-prone**: `support`, `help`, `about`, `contact`,
  `legal`, `terms`, `privacy`, `security`, `status`, `team`, `staff`, `mod`,
  `moderator`, `null`, `undefined`.

RLS: `select using (true)` (reference-table convention,
`20260411000000_reference_tables.sql:9-56`); insert/delete policies gated on
`public.is_admin(auth.uid())` — that is the "admin-extensible without a
migration" requirement: an admin inserts a row from the back office and the
name is reserved immediately.

**Enforcement is a trigger, not RPC-side validation alone**: a
`before insert or update of handle` trigger on `profiles` (security-definer
trigger function, so it reads `reserved_handles` regardless of caller) raises
`handle_reserved` when the new handle is in the table. Same reasoning as D1 —
the direct-update path exists, so the reserved list must bind it too. Trigger
precedent: `handle_new_user`, `apply_karma_event_to_bounce`.

Rows in `reserved_handles` are admin data, not user data — no purge
implications (see D8).

## D3 — `set_handle(p_handle)`: invoker RPC, friendly errors

`security invoker` (per roadmap — RLS does the scoping), granted to
`authenticated` only. Behavior:

- Normalizes input (`lower(trim(p_handle))`), then validates the D1 regex in
  the function body to raise a *friendly* `invalid_handle` error before the
  CHECK ever fires (house precedent for body-side regex validation:
  `20260717120000_admin_emotion_management.sql:71-75`).
- Updates the caller's own row (`where user_id = auth.uid()`), returning the
  stored handle.
- Exception mapping so all three clients get stable codes: `unique_violation`
  → `handle_taken`; the D2 trigger exception → `handle_reserved`.
- **`set_handle(null)` releases the handle** and sets `public_profile = false`
  in the same statement (the D1 CHECK would otherwise reject the orphaned
  public flag). Releasing a handle makes it immediately claimable by others —
  no handle history, no redirects (decision-log entry, D9).

The `public_profile` toggle itself is a **direct single-column client
update** — single-table, single-statement, exactly the case `AGENTS.md`
allows without an RPC — guarded by the D1 CHECK for the no-handle case.

## D4 — `get_public_profile(p_handle)`: the first anon-granted definer projection

**`profiles` RLS is never widened** — it carries `is_admin`, consent
timestamps and quota columns, and the roadmap's standing rule (§5 item 8) is
that every cross-user read is a definer-RPC *projection*. This RPC is the
first instance of the pattern, and the first data-returning function granted
to `anon` in the schema (the only prior anon-granted function is the boolean
`is_admin`, `20260421000000_profiles_is_admin.sql:21-29`). Pattern sources:
`admin_list_glyph_submissions` for a definer jsonb projection that reads past
RLS with an explicit guard (`20260701114205_drop_glyph_shape.sql:246-284`),
`is_admin` for the grant style.

```sql
create function public.get_public_profile(p_handle text)
returns jsonb
language sql
security definer
stable
set search_path = public
as $$ … $$;

revoke all on function public.get_public_profile(text) from public;
grant execute on function public.get_public_profile(text) to anon, authenticated;
```

- Input is normalized (`lower(trim(…))`) before lookup.
- Returns **null unless** a profile matches the handle **and**
  `public_profile = true`. Unknown handle and known-but-private handle are
  indistinguishable to the caller — enumeration resistance for the anon
  surface.
- Projected shape (stable contract for all three clients + SSR):

```jsonc
{
  "display_name": "…",
  "handle": "…",
  "glyph": { "strokes": [...], "view_box": "…" },   // null when unset; mirrors the v_pebbles_full embed shape
  "pebbles_count": 123,
  "ripple_level": 4,          // 0–6
  "bounce_level": 5,          // 0–7
  "assiduity": [true, ...],   // 28 booleans, index 1 = 27 days ago, index 28 = today (UTC)
  "days_practiced": 87,
  "member_since": "2026-04-12",
  "achievements": []          // reserved for M48 — stays [] until achievement_unlocks exists
}
```

- **Engagement is recomputed inside the function for the target user** — the
  roadmap forbids un-scoping `v_ripple` / `v_bounce` /
  `get_profile_engagement`, and the decisions log (2026-07-29, #616) rules
  out new trailing-`auth.uid()` views. Buckets are copied verbatim: ripple
  from `20260516000001_v_ripple_security_filter.sql` (`created_at`, 28 days,
  levels 0–6), bounce from `20260411000005_security_hardening.sql:413-435`
  (`happened_at` distinct days, 28 days, levels 0–7), assiduity/days_practiced
  from `20260516104231_profile_glyph_and_engagement.sql:73-111` — but the
  public variant is fixed to **UTC** (no `p_tz` parameter; a visitor has no
  claim on the owner's timezone, and the owner's tz-aware RPC is untouched).
- The avatar glyph is projected as raw geometry (`strokes` + `view_box`) —
  there is no pre-rendered SVG for profile glyphs, and `glyphs` RLS
  (`20260630003348_glyph_marketplace.sql:70-79`, authenticated-only) is
  likewise never widened; the definer read is the projection.
- **Deliberately excluded**: `user_id` (no stable cross-user identifier leaks
  from the public surface; M49 mints its own projections for invites),
  `is_admin`, consent timestamps, quota columns, karma, `color_world`, raw
  counts behind the levels (`pebbles_28d`, `active_days`), and `active_today`
  (a presence signal). The assiduity grid is already the product's chosen
  granularity for public activity.
- One extra keyword the roadmap attaches here: when M48 lands, it extends
  this function to project unlocked achievement slugs into the existing
  `achievements` key — clients render the key from day one and need no
  contract change (convergence row "`achievements` + `check_achievements()` …
  displayed by M50").

## D5 — Web `/u/[handle]`: first SSR data route + OG card

- `apps/web/app/u/[handle]/page.tsx` is a **server component** — the first
  data-fetching SSR route in the app (`lib/supabase/server.ts`'s
  `createServerSupabaseClient` currently has a single consumer, the auth
  callback). It calls `.rpc("get_public_profile", { p_handle })` with the
  publishable key — works with or without a session — and `notFound()`s on
  null. Data flows into a client `PublicProfileView` in
  `apps/web/components/public-profile/`, which **reuses** `RippleBadge` and
  `AssiduityGrid` from `components/profile/` and translates via the existing
  client `LocaleProvider` (`publicProfile.*` keys in `messages/en.json` /
  `fr.json`).
- Route gating needs zero AuthGate change — `/u` is not in
  `PROTECTED_PREFIXES` (`components/auth/AuthGate.tsx:7-16`). Chrome: add
  `/u` to the standalone-route handling in
  `components/layout/MainContent.tsx` (the `isDocs`/`isLanding` pattern) so
  the page renders without the app shell, plus a small "made with Pebbles"
  CTA footer linking to the landing page.
- `generateMetadata`: title `{display_name} (@{handle})`, a short
  description, `openGraph` + `twitter` card fields. This introduces
  `metadataBase` from a new `NEXT_PUBLIC_SITE_URL` env var (fallback
  `https://www.pbbls.app`, the host already hardcoded in the mobile
  legal-doc links).
- `opengraph-image.tsx`: dynamic 1200×630 card via `next/og` `ImageResponse`
  (maintainer decision, 2026-07-29 — image card over text-only) rendering
  display name, `@handle`, and the ripple level; satori renders inline SVG,
  so the ring strokes reuse the `ripple-strokes` geometry. It refetches
  through the same RPC (OG requests carry no session; the anon grant is what
  makes the card possible). First OG infrastructure in the repo.

## D6 — Settings ×3: claim, toggle, share

One "Public profile" section per surface, added inside each surface's
existing section + dirty-tracking + single-save pattern:

- **Web**: `PublicProfileSection.tsx` built from `SettingsGroup` /
  `SettingsRow`, wired into the save flow at `app/settings/page.tsx:63-91`.
- **iOS**: a new `Section` computed var appended to the `List` in
  `SettingsSheet.swift` (sections at :68-86, save at :318), rows styled with
  `.pebblesListRow` / `.pebblesSectionHeader` like the rest.
- **Android**: a new `PebblesListSection` block in `SettingsScreen.kt`
  (dirty flag at :448).

Section contents, identical across surfaces:

1. **Handle field** — claim when empty, edit thereafter. Inline errors map
   `set_handle`'s stable codes: `invalid_handle`, `handle_taken`,
   `handle_reserved`. `set_handle` is called on save **only when the handle
   changed** (it is not folded into `update_profile` — extending that RPC
   would force the symmetric-siblings rule across three clients for an
   orthogonal concern).
2. **Public toggle** — disabled until a handle exists; direct
   `public_profile` column update on save.
3. **Share row** — visible once public; shows
   `https://www.pbbls.app/u/{handle}`. Web: copy button +
   `navigator.share` where available (origin-relative URL). iOS: `ShareLink`
   — the **first share-sheet use in the app**. Android: `Intent.ACTION_SEND`
   chooser — likewise a first; both are small, self-contained precedents.

Localization: EN + FR strings on every surface (`Localizable.xcstrings`,
`values/strings.xml` + `values-fr/`, `messages/*.json`).

## D7 — Deliberate deferrals

- **Native viewer of others' profiles → M49.** No entry point exists before
  the connections list; the RPC is already auth-callable, so M49 only adds
  UI. Building an unreachable screen now would be dead weight.
- **Universal/app links for `/u/…` → M49**, which introduces link-handling
  infrastructure for `/invite/[token]` anyway; today neither platform has
  associated domains or a VIEW intent-filter beyond the auth callback.
- **Public pebbles + `/p/[id]` → M51** (per roadmap; the public profile shows
  aggregates only, never pebble content).
- **Report affordance on public profiles → M56** (UGC batch; the roadmap
  places `content_reports` and the admin moderation queue there).
- **Achievements content → M48** (empty key shipped now, see D4).

## D8 — `purge_account`: no extension needed (standing-rule check)

The M46 standing rule says every milestone appends its new per-user tables to
`purge_account` and `verify-account-purge.ts`. M50 adds **no** per-user
table: `handle` and `public_profile` are columns on `profiles`, whose row is
already deleted at `20260729201326_account_deletion_purge.sql:140` — deleting
an account frees its handle automatically — and `reserved_handles` is admin
data. Stated here so the rule's checklist shows a conscious no-op, not an
omission. The purge harness gains one cheap assertion: after purge, the
purged user's handle resolves to null via `get_public_profile`.

## D9 — Arkaik + decision log (land with the implementation PRs)

- Arkaik nodes: `V-public-profile` (view, `web`, `development`),
  `API-set-handle` and `API-get-public-profile` (RPC-shaped titles per the
  newer convention), `DM-reserved-handles`; update `DM-profile`'s description
  (handle, public flag). Edges: `V-settings` **calls** `API-set-handle`;
  `V-public-profile` **displays** `DM-profile`, **calls**
  `API-get-public-profile`. Journal events per the arkaik skill.
- Decision-log entries (appended, per PR that establishes them):
  1. Cross-user reads only via definer-RPC projections; never widen
     `profiles` RLS — first instance shipped by M50 (roadmap §5 item 8).
  2. Handle policy: lowercase 3–30 `[a-z0-9_]`, reserved list enforced by
     trigger, freed on account deletion, no handle history or redirects;
     public share links break when a handle is released.

## D10 — Verification

- Migration: `npm run db:types --workspace=packages/supabase`, commit
  `types/database.ts`.
- RLS/permission probes with a second test user and an anon client:
  - `get_public_profile` returns null for an unknown handle, null for a
    known-but-private handle, data only when opted in — identically for anon
    and authenticated callers.
  - Direct `.update` attempts: bad format, reserved handle, and
    `public_profile = true` without a handle all rejected (CHECK/trigger).
  - Two concurrent claims of the same handle: exactly one succeeds.
  - `v_ripple` / `v_bounce` / `get_profile_engagement` still return only the
    caller's rows; `profiles` cross-user select still returns nothing.
  - The projection jsonb contains none of the excluded keys (D4 list).
- Purge harness: seeded public user → purge → handle resolves null and is
  re-claimable.
- Web: `npm run lint --workspace=apps/web`; full `npm run build` on the
  backend PR (shared types change). OG card eyeballed via a social-card
  preview against a seeded public profile.
- iOS/Android: workspace lint per house triage; manual pass of claim → error
  states → toggle → share on device/simulator.
