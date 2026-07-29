# Achievements — design (M48)

Design doc for milestone **M48 · Achievements**. Parent spec:
`2026-07-28-store-launch-roadmap.md` §M48 (plus the convergence-map row
"`achievements` + `check_achievements()`, displayed by M50" and §5 item 5),
extended by three maintainer-requested satellites (2026-07-29): an admin
library manager, a rewarding unlock screen, and a Duolingo-style profile
showcase. The eight issues cut from this doc follow the house cadence:
migration + types → web reference → iOS → Android, with the satellites
layered on top.

Arkaik already names the whole surface — `V-achievements`, `DM-achievement`,
`API-get-achievements` and `F-gamification` are `idea` nodes — and zero code
exists behind any of them. Everything the milestone needs, however, is
already in place after M45–M47: every count source has an owner-scoped
table, the karma ledger reserves a never-emitted `grant` reason
(`20260629192621_karma_events_type_axis.sql`), and `purge_account` carries
the append marker new user-owned tables hook into.

M48 ships one reference catalog, one unlock ledger, one evaluation RPC, and
an achievements screen with an unlock moment on all three surfaces — then
the satellites: the back-office manages the catalog (copy, visual, karma,
tiers), the unlock moment graduates into a rewarding screen, and Profile
gains a badge showcase. Badges pay karma per badge via the reserved `grant`
reason (default 0, maintainer-set in admin — D9); still no revocation, no
push.

Because the satellites arrived before any issue was cut, their schema
consequences land in the *core* migration rather than a follow-up: the
catalog carries `glyph_id`, `karma_reward`, `is_active` and bilingual copy
overrides from day one (D1), and `check_achievements()` emits the karma and
returns what it granted (D4, D9). Only the admin RPCs and the client
surfaces ship later.

## Shipped pieces

| Piece | Path |
|---|---|
| Migration (catalog + unlocks + RPCs + purge extension) | `packages/supabase/supabase/migrations/<ts>_achievements.sql` |
| Purge regression harness | `packages/supabase/scripts/verify-account-purge.ts` |
| Web data layer | `apps/web/lib/data/useAchievements.ts`, `supabase-provider.ts` |
| Web unlock moment | `apps/web/lib/activity/achievement-activity.tsx`, `components/activity/` |
| Web achievements surface | `apps/web/app/achievements/page.tsx`, `components/achievements/` |
| iOS | `Features/Profile/AchievementsView.swift`, `Services/AchievementsService.swift`, unlock notify beside `Features/Karma/KarmaNotificationService.swift` |
| Android | `features/profile/AchievementsScreen.kt`, `services/AchievementsService.kt`, unlock notify beside `features/karma/KarmaNotificationService.kt` |
| Admin library manager (satellite) | `packages/supabase/supabase/migrations/<ts>_admin_achievement_management.sql`, `apps/admin` achievements tab |
| Rewarding moment ×3 (satellite) | web achievement moment beside `lib/activity/`, iOS `AchievementMomentService`, Android sibling |
| Profile showcase ×3 (satellite) | shelf component on each surface's existing Profile screen |

## D1 — The catalog is a seeded reference table with no copy columns

`public.achievements` follows the `emotions`/`domains` template
(`20260411000000_reference_tables.sql`): RLS enabled, one `for select using
(true)` policy, no client write path, ids deterministic per the
`md5('<table>:<slug>')::uuid` convention
(`20260411000006_deterministic_reference_ids.sql`) so three hand-written
clients can hardcode nothing and cache everything.

```
id uuid pk, slug text unique not null,
family text not null check (family in (
  'pebble_count','emotion_first','domain_first','first_collection',
  'first_glyph','first_soul','glyph_count','glyph_sales')),
threshold integer,          -- null for the one-shot families
emotion_id uuid references public.emotions(id),   -- emotion_first only
domain_id uuid references public.domains(id),     -- domain_first only
sort_order integer not null,
glyph_id uuid references public.glyphs(id),       -- system-owned visual (D12)
karma_reward integer not null default 0 check (karma_reward >= 0),  -- D9
is_active boolean not null default true,          -- retirement, never delete (D12)
title_en text, title_fr text,                     -- admin copy overrides (D7)
description_en text, description_fr text
```

Tiers (slugs are `<family-kebab>-<qualifier>`, e.g. `pebble-count-50`,
`emotion-first-joy`):

| Family | Tiers / rows |
|---|---|
| `pebble_count` | 1 · 10 · 25 · 50 · 100 · 250 · 500 · 1000 |
| `emotion_first` | one row per emotion, `emotion_id` set |
| `domain_first` | one row per domain, `domain_id` set |
| `first_collection` · `first_glyph` · `first_soul` | one row each, `threshold` null |
| `glyph_count` | 10 · 25 · 50 · 75 · 100 (roadmap-fixed) |
| `glyph_sales` | 1 · 5 · 10 · 25 · 50 |

Deliberately **no required copy columns**: seeded rows carry null copy and
clients compose their titles from family-keyed i18n (D7), so adding an
emotion never requires new database copy and EN/FR never drift from a
single English column the way `emotions.name` already forces clients to
work around. The nullable `title_*`/`description_*` pairs exist for one
consumer only — admin-created or admin-renamed rows (D12), which no client
can have shipped strings for; when present they win over the i18n key.

## D2 — Per-emotion and per-domain rows are generated, and the generator is the drift guard

The roadmap says "extend the admin add-emotion/add-domain RPCs" — but those
RPCs do not exist. The admin surface is edit-only
(`admin_update_emotion_emoji`, `admin_update_emotion_palette` in
`20260717120000_admin_emotion_management.sql`; `admin_update_domain`,
`admin_set_domain_glyph` in `20260703000000_admin_domain_management.sql`);
emotions and domains are only ever added by migration. So the drift guard is
not an RPC extension, it is a generator:

- `sync_achievement_catalog()` — a `security definer` function holding one
  idempotent `insert … select` over the **live** `emotions` and `domains`
  rows (deterministic ids, `on conflict (slug) do nothing`). The seed
  migration calls it once after inserting the static families.
- **Standing rule:** any future migration — or future admin add-RPC — that
  inserts an emotion or a domain re-runs `sync_achievement_catalog()` in the
  same transaction. Granted to `service_role` only; there is no client
  reason to call it.

This keeps the paired `emotion_first`/`domain_first` rows a projection of
the reference tables rather than a second list that silently drifts.

Deterministic ids are a *seeded-row* convention: rows the admin creates at
runtime (D12) take `gen_random_uuid()` like any user-generated row. Nothing
client-side keys on achievement ids — the catalog is always fetched — so
the two id regimes coexist without a mapping layer.

## D3 — `achievement_unlocks` is structurally idempotent and write-locked

```
achievement_unlocks (
  user_id uuid not null references auth.users(id) on delete cascade,
  achievement_id uuid not null references public.achievements(id),
  unlocked_at timestamptz not null default now(),
  primary key (user_id, achievement_id)
)
```

Select is owner-only; there are **no insert/update/delete policies** — the
`wallet_balances` precedent (`20260629193636_wallet_balances.sql`), where
the only writer is `security definer` code. A client structurally cannot
mint, edit or revoke a badge. Badges are permanent (decision, roadmap §5
item 5): deleting pebbles, glyphs or a buyer's account never removes an
unlock, and there is deliberately no revocation path to forget about.

## D4 — Evaluation is one client-callable, idempotent definer RPC

`check_achievements() returns table (slug text, karma_granted integer)` — a
single `insert … select` of every **active** catalog row the caller now
qualifies for, `on conflict do nothing`, returning the **newly** unlocked
slugs with the karma each one paid (empty set = nothing new). One stats CTE
computes each family's count once per call; qualification is a `case
a.family … end` over it. For every returned row with `karma_reward > 0`,
the same transaction inserts the `grant` karma event (D9) — the return
value reports what was actually emitted, not the catalog's current value,
so the rewarding screen (D13) can never display a number the ledger
doesn't hold.

Why not the alternatives:

1. **No triggers.** The eight families span six tables (`pebbles`,
   `pebble_domains`, `collections`, `souls`, `glyphs`,
   `glyph_entitlements`). Triggers there would fire during admin operations
   (`admin_attribute_glyph`), backfills, and `purge_account` — and web glyph
   carving is a direct client insert with no RPC seam anyway
   (`createMark`, `apps/web/lib/data/supabase-provider.ts:729`).
2. **No cron.** The repo has no scheduled-job infrastructure and no
   realtime; the screen-open call (D5) already covers everything a nightly
   sweep would.
3. **`create_pebble` untouched.** Its `returns uuid` is a three-surface wire
   contract; piggybacking unlock data on it would churn all three clients
   for one of eight families.

Grants: `authenticated` only. The RPC reads only the caller's rows
(`auth.uid()`), so there is nothing to leak even as definer.

## D5 — Fire sites: after mutations, and on screen open (which *is* the retroactive grant)

Clients call `check_achievements()` fire-and-forget after the mutations that
can change a family's count — pebble create/publish, glyph carve, soul,
collection — and unconditionally when the achievements screen opens. The
screen-open call is what grants existing users their historical badges: the
stats CTE counts live rows, so a veteran's first visit unlocks everything
they already earned in one call. No backfill job exists or is needed.

`glyph_sales` unlocks are inherently deferred: the seller is not online when
a buyer purchases, so the badge appears at the seller's next mutation or
screen open. That is the accepted no-push/no-realtime posture (house
constraint), same as M49's "accepted connections surface on next app open".

A failed fire-and-forget call needs no retry — the next call self-heals by
construction.

## D6 — Count sources, grounded

| Family | Source |
|---|---|
| `pebble_count` | `count(*) from pebbles where user_id = auth.uid()` |
| `emotion_first` | `exists` pebble with `emotion_id = a.emotion_id` (NOT NULL on `pebbles`) |
| `domain_first` | `exists` via `pebble_domains` join |
| `first_collection` | `exists` row in `collections` |
| `first_soul` | `exists` row in `souls` |
| `first_glyph` / `glyph_count` | `count(*) from glyphs where user_id = auth.uid()` — ownership *is* the custom flag (`is_custom` is generated as `user_id is not null`, `20260501000006_glyphs_is_custom.sql:31-33`) |
| `glyph_sales` | `count(*) from glyph_entitlements e join glyphs g on g.id = e.glyph_id where g.user_id = auth.uid() and e.user_id <> g.user_id` |

Notes that make these the right sources:

- **Drafts never count.** `pebble_drafts` is a separate table (M47), so a
  quick capture cannot inflate `pebble_count` — structural, not filtered.
- **Sales come from entitlements, not the karma ledger** (roadmap §M48). An
  entitlement held by another user *is* the sale fact; ledger rows are
  per-user accounting that `purge_account` deletes. The
  `e.user_id <> g.user_id` guard is belt-and-braces — `buy_glyph` already
  rejects self-purchase (`cannot_buy_own`,
  `20260630003348_glyph_marketplace.sql:161`).
- **Counts may regress** — pebble deletion, a buyer's account purge deleting
  their entitlements, glyph deletion. Permanence (D3) makes that harmless:
  the unlock survives, and re-crossing a threshold inserts nothing because
  the row already exists.

## D7 — Badge copy is client i18n keyed by family, with admin overrides

Titles and descriptions are composed client-side from `family` +
`threshold` + the already-localized emotion/domain name (e.g.
`achievements.family.pebble_count.title` with a count parameter;
`emotion_first` interpolates the emotion's display name). When a row
carries `title_en`/`title_fr` (admin-created or admin-renamed, D12), the
override wins — both languages are mandatory together in the admin RPC, so
a row is never half-translated. French is a real adaptation using "Tu",
per the house tone. Consequences:

- A new emotion ships its badge with **zero new client strings** — the
  interpolation covers it, which is what makes D2's generator sufficient.
- Android's `LocalizationParityTest` keeps EN/FR key parity honest; web and
  iOS follow their existing `en/fr` message files.

## D8 — UI ×3: a grid reached from Profile, and an explicit-fire unlock moment

- **Screen.** An achievements grid grouped by family, reached from the
  Profile screen (the ripple/bounce "evolutive rings" context it belongs
  to). The catalog is public-read, so locked badges render greyed alongside
  unlocked ones — the full ladder is visible, which is the point of tiers.
  Web `/achievements`; iOS a pushed view from Profile; Android a screen from
  profile. No per-badge progress bars in v1 — unlocked/locked and
  `sort_order` only.
- **Unlock moment.** The RPC's returned rows drive the same explicit-fire
  pattern as karma: web fires a Sonner custom toast beside `notifyKarma`
  (`apps/web/lib/activity/karma-activity.tsx:13-25`), iOS and Android extend
  their `KarmaNotificationService` siblings. Several slugs in one response
  collapse into one moment (e.g. "3 achievements unlocked") rather than
  stacking toasts. This toast is the core deliverable so the milestone never
  blocks on celebration UI; the rewarding-screen satellite (D13) then
  replaces it on the mutation path.

Arkaik: flip `V-achievements`, `DM-achievement`, `API-get-achievements` and
`F-gamification` from `idea` as the surfaces ship, and add the
`check_achievements` endpoint node — via the `arkaik` skill against the
hosted project (2026-07-28 decision: the local bundle is no longer the
authoritative plane).

## D9 — Achievement karma rides the reserved `grant` reason, per badge, default 0

The maintainer sets each badge's `karma_reward` in the admin (D12); the
roadmap's "karma deferred" stance is superseded by this doc — the *rail*
ships in the core migration, and "cosmetic" is just the default value.

Emission lives **inside `check_achievements()`**, one `karma_events` row
per newly unlocked badge with `karma_reward > 0`: `reason = 'grant'`
(reserved since `20260629192621` and emitted nowhere else), `type =
'credit'`, `delta = karma_reward`, `ref_id = achievement_id`. The unlocks
PK makes the payout exactly-once for free — a re-run inserts no unlock, so
it emits no karma. Consequences pinned down now:

- The wallet and bounce snapshots fold the grant automatically (both are
  `after insert` triggers on `karma_events`; `grant` is credit-type), so
  achievement karma is spendable *and* raises the bounce level. The web
  karma mirror `apps/web/lib/data/karma.ts` needs no change — grants come
  from the server, never computed client-side.
- `karma_reward` is read at unlock time only. An admin edit never re-emits
  and never retro-pays already-unlocked users; the returned
  `karma_granted` (D4) records what actually happened.
- Retirement (`is_active = false`) stops future unlocks and their karma;
  emitted events are ledger history and stay.

The decision-log entry (roadmap §5 item 5, updated wording: client-called
idempotent RPC, no triggers/cron, badges permanent, karma per badge via
`grant` at unlock with default 0) is appended by the migration PR,
mirroring how M46/M47 recorded theirs.

## D10 — `purge_account` extension (M46 standing rule)

`achievement_unlocks` gets a delete at the section-(4) append marker
(`20260729201326_account_deletion_purge.sql:164`), and
`verify-account-purge.ts` seeds an unlock and asserts zero rows after the
purge. The `on delete cascade` on `user_id` is the backstop for the
`auth.admin.deleteUser` path. The catalog itself is global reference data —
untouched by purge, exactly like `emotions`. Badge glyphs are purge-inert
by the same invariant domain glyphs rely on: `admin_set_achievement_glyph`
(D12) only ever creates system-owned rows (`user_id = null`), which
`purge_account` never matches — no kept-predicate extension needed.

## D11 — M50 convergence

`get_public_profile` (M50) projects achievements into the public profile.
Nothing extra ships now, but the read path stays trivially liftable: the
owner screen reads `achievements` (public catalog) plus a plain owner-scoped
select on `achievement_unlocks`, and M50's definer RPC will recompute the
same join internally for the target user — never by widening
`achievement_unlocks` RLS. The profile showcase (D14) is the exact shape
that projection will reuse: most-recent badges + total count.

## D12 — Satellite: the admin manages the catalog, not the rule engine

The back-office gains an Achievements tab following the emotion/domain
admin pattern (`is_admin`-gated `security definer` RPCs, `authenticated`
grants, `20260717120000_admin_emotion_management.sql`):

- `admin_list_achievements()` — the full catalog including inactive rows,
  joined glyph strokes, plus a per-badge unlock count so curation decisions
  are informed (which badges users actually reach).
- `admin_create_achievement(p_slug, p_family, p_threshold, p_emotion_id,
  p_domain_id, p_title_en, p_title_fr, p_description_en, p_description_fr,
  p_karma_reward, p_sort_order)` — **family-bound**: the `family` CHECK is
  the boundary of what `check_achievements()` can evaluate. The admin can
  add a new *tier* of an existing family (a `pebble-count-2000`, a new
  sales rung); a new *kind* of rule is still a migration plus a new `case`
  arm in the RPC, never an admin action. Bilingual copy is mandatory here
  (D7) — a runtime-created row is one no client has strings for.
- `admin_update_achievement(...)` — copy, `karma_reward`, `sort_order`,
  `is_active`. Karma edits apply to future unlocks only (D9).
- `admin_set_achievement_glyph(p_achievement_id, p_strokes, p_view_box)` —
  the `admin_set_domain_glyph` replace-in-place pattern verbatim
  (`20260703000000_admin_domain_management.sql:88-129`): a system-owned
  glyph (`user_id = null`, shapeless), same glyph id kept on replace so
  every consumer reflects the change.

**The visual is a glyph, not an image.** An image would need a new public
bucket, remote-image loading and caching on three surfaces, and a
moderation story; glyphs already render natively everywhere via the
petroglyph pipeline and carry the brand. `glyph_id` stays nullable — a
badge without one falls back to a family default icon client-side.

**Retirement is `is_active = false`, never a delete**: unlocks FK the row
and permanence (D3) forbids revocation. `check_achievements()` evaluates
active rows only; the grid hides inactive *locked* badges but keeps
showing earned ones. The one true delete is a correction path:
`admin_delete_achievement` refuses unless the badge has zero unlocks.

Arkaik: none — `apps/admin` is deliberately unlinked (2026-07-28
decision), so this issue moves no platform status.

## D13 — Satellite: the rewarding screen replaces the toast on the mutation path

The D8 toast ships first; this satellite upgrades it into a Duolingo-style
celebration driven by `check_achievements()`'s returned
`(slug, karma_granted)` rows joined to the already-cached catalog (glyph,
copy):

- One modal moment per response, one card per badge — badge glyph rendered
  large, title, and its own "+N karma" line — chained in `sort_order`,
  tap/swipe to advance. Web a dialog fired from the activity layer beside
  the karma pill; iOS a full-screen cover from an `AchievementMomentService`
  sibling of `KarmaNotificationService`; Android symmetric.
- **Stacking rule:** the achievement's karma line lives inside its card, so
  the moment and the pebble-karma pill never announce the same number
  twice; the pill keeps firing independently for the pebble's own karma.
- **Retro grants don't celebrate.** The screen-open call (D5) can return a
  veteran's entire history at once; chaining twenty cards would be
  punishing. Mutation-path calls fire the moment; the screen-open call
  renders its results directly in the grid (a subtle "new" state at most).
- Dismissal is never blocking — tap-outside/back skips the remaining queue.

## D14 — Satellite: profile showcase (the Duolingo shelf)

The Profile screen on each surface gains an achievements section: the
most recent unlocks (`unlocked_at desc`, ~6), the total unlocked count,
and a "view all" affordance into the D8 grid. It reads the same two
queries the grid already uses — no new endpoint, no new RLS. No per-badge
progress bars, matching D8's v1 posture; the shelf shows what you've
earned, the grid shows the ladder. M50's public profile projects this
exact shape (D11).

## D15 — Verification

Per surface, beyond lint/build:

- **Idempotency** (roadmap §6): call `check_achievements()` twice —
  the second call returns zero rows and `count(*) from achievement_unlocks`
  is unchanged.
- **RLS probes** with a second test user: B cannot select A's unlocks; B's
  direct insert into `achievement_unlocks` is rejected (no policy); the
  catalog is readable by both.
- **Retroactive grant:** seed a veteran user (50 pebbles across 3 emotions,
  a soul, a collection, a carved glyph), open the achievements screen once —
  all corresponding badges unlock in that single call.
- **Drift guard:** insert a test emotion, re-run
  `sync_achievement_catalog()` — the paired `emotion_first` row appears with
  its deterministic id; a second run inserts nothing.
- **Karma exactly-once:** with every `karma_reward` at 0, a run leaves
  `count(*) from karma_events` unchanged. Set one badge to 5 and qualify:
  exactly one `grant` event (`type = 'credit'`, `delta = 5`, `ref_id` = the
  achievement id), wallet balance +5, bounce score +5. A re-run emits
  nothing; an `admin_update_achievement` karma edit emits nothing.
- **Admin gating:** every `admin_*` RPC raises `42501` for a non-admin;
  `admin_create_achievement` rejects a missing FR title and an unknown
  family; `admin_set_achievement_glyph` creates only `user_id is null`
  glyph rows.
- **Retirement:** `is_active = false` removes the badge from evaluation and
  from the locked grid; an existing unlock still renders.
- **Regression tolerance:** unlock `pebble-count-10`, delete pebbles below
  10, re-run — the unlock survives and nothing new inserts.
- **Purge:** `verify-account-purge.ts` passes with the unlock seeded and
  asserted at zero.
- **Unlock moment:** on each surface, creating the qualifying pebble fires
  the achievement toast (core) or the chained rewarding cards (D13)
  alongside the karma pill without stacking conflicts; a retroactive
  screen-open grant renders in the grid without firing the moment.
- **Showcase:** the profile shelf shows the most recent unlocks and the
  total count, and its "view all" lands on the grid.

Android cannot be verified locally (no SDK; `scripts/gradle-if-sdk.sh` exits
0 with a warning), so `android.yml` plus `LocalizationParityTest` is the
gate, and new logic stays in pure JVM-testable functions.

## Issues to cut

House cadence, sized per repo triage. Core first:

1. **[Feat] Achievements backend: catalog + unlocks + `check_achievements()`**
   — D1–D6, D9–D10. Migration (full schema including the satellite columns),
   `sync_achievement_catalog()`, karma emission, purge extension,
   `verify-account-purge.ts`, types regen, decision-log entry.
   Labels `feat` `db` `api` `supabase`. Size M.
2. **[Feat] Achievements web: grid screen + unlock moment** — D5, D7, D8.
   Provider methods + `useAchievements`, `/achievements` page, fire sites,
   activity toast, EN/FR strings, Arkaik flips. Reference implementation.
   Labels `feat` `ui` `core`. Size M.
3. **[Feat] Achievements iOS** — port of 2 per D7/D8; `AchievementsService`,
   Profile entry, unlock notify. Labels `feat` `ui` `core`. Size M.
4. **[Feat] Achievements Android** — symmetric port; parity test covers
   strings. Labels `feat` `ui` `core`. Size M.

Then the satellites:

5. **[Feat] Achievements admin: library manager** — D12. Migration with the
   four `admin_*` RPCs + the `apps/admin` achievements tab (list, create,
   edit copy/karma/sort/active, glyph editor). Labels `feat` `db` `api`
   `facility`. Size M. No Lab Note (admin-only surface).
6. **[Feat] Achievements web: rewarding moment + profile showcase** — D13,
   D14. Labels `feat` `ui` `core`. Size M.
7. **[Feat] Achievements iOS: rewarding moment + profile showcase** — port
   of 6. Labels `feat` `ui` `core`. Size M.
8. **[Feat] Achievements Android: rewarding moment + profile showcase** —
   symmetric port. Labels `feat` `ui` `core`. Size M.

Gating: issue 1 gates everything. Issues 3–4 start from 2's reference
implementation and run in parallel; issue 5 needs only 1 and runs in
parallel with 2–4; issues 6/7/8 each need their platform's core issue
(2/3/4 respectively), and 6 is the reference for 7–8. M50 consumes the
result (roadmap: M48 gates M50 content).
