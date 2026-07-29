# Achievements — design (M48)

Design doc for milestone **M48 · Achievements**. Parent spec:
`2026-07-28-store-launch-roadmap.md` §M48 (plus the convergence-map row
"`achievements` + `check_achievements()`, displayed by M50" and §5 item 5).
The four issues cut from this doc follow the house cadence: migration +
types → web reference → iOS → Android.

Arkaik already names the whole surface — `V-achievements`, `DM-achievement`,
`API-get-achievements` and `F-gamification` are `idea` nodes — and zero code
exists behind any of them. Everything the milestone needs, however, is
already in place after M45–M47: every count source has an owner-scoped
table, the karma ledger reserves a never-emitted `grant` reason
(`20260629192621_karma_events_type_axis.sql`), and `purge_account` carries
the append marker new user-owned tables hook into.

M48 ships one reference catalog, one unlock ledger, one evaluation RPC, and
an achievements screen with an unlock moment on all three surfaces. Badges
are cosmetic in v1: no karma, no revocation, no push.

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
sort_order integer not null
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

Deliberately **no `name`/`description` columns**: badge copy is client-side
i18n keyed by family (D7). A catalog row is pure structure, so adding an
emotion never requires new copy in the database, and EN/FR never drift from
a single English column the way `emotions.name` already forces clients to
work around.

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

`check_achievements() returns setof text` — a single `insert … select` of
every catalog row the caller now qualifies for, `on conflict do nothing`,
returning the **newly** unlocked slugs (empty set = nothing new). One stats
CTE computes each family's count once per call; qualification is a `case
a.family … end` over it.

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

## D7 — Badge copy is client i18n keyed by family

Titles and descriptions are composed client-side from `family` +
`threshold` + the already-localized emotion/domain name (e.g.
`achievements.family.pebble_count.title` with a count parameter;
`emotion_first` interpolates the emotion's display name). French is a real
adaptation using "Tu", per the house tone. Consequences:

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
- **Unlock moment.** The RPC's returned slugs drive the same explicit-fire
  pattern as karma: web fires a Sonner custom toast beside `notifyKarma`
  (`apps/web/lib/activity/karma-activity.tsx:13-25`), iOS and Android extend
  their `KarmaNotificationService` siblings. Several slugs in one response
  collapse into one moment (e.g. "3 achievements unlocked") rather than
  stacking toasts.

Arkaik: flip `V-achievements`, `DM-achievement`, `API-get-achievements` and
`F-gamification` from `idea` as the surfaces ship, and add the
`check_achievements` endpoint node — via the `arkaik` skill against the
hosted project (2026-07-28 decision: the local bundle is no longer the
authoritative plane).

## D9 — Karma payout is deferred; the hook stays warm

`grant` stays a reserved, never-emitted reason in
`karma_events_reason_check`. If v2 pays badges, the emission belongs
*inside* `check_achievements()`, keyed to the insert that returned rows —
the unlocks PK then makes the payout exactly-once for free. Nothing in v1
writes to `karma_events`, and verification asserts that (D12). The
decision-log entry (roadmap §5 item 5: client-called idempotent RPC, no
triggers/cron, badges permanent, karma `grant` deferred) is appended by the
migration PR, mirroring how M46/M47 recorded theirs.

## D10 — `purge_account` extension (M46 standing rule)

`achievement_unlocks` gets a delete at the section-(4) append marker
(`20260729201326_account_deletion_purge.sql:164`), and
`verify-account-purge.ts` seeds an unlock and asserts zero rows after the
purge. The `on delete cascade` on `user_id` is the backstop for the
`auth.admin.deleteUser` path. The catalog itself is global reference data —
untouched by purge, exactly like `emotions`.

## D11 — M50 convergence

`get_public_profile` (M50) projects achievements into the public profile.
Nothing extra ships now, but the read path stays trivially liftable: the
owner screen reads `achievements` (public catalog) plus a plain owner-scoped
select on `achievement_unlocks`, and M50's definer RPC will recompute the
same join internally for the target user — never by widening
`achievement_unlocks` RLS.

## D12 — Verification

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
- **Karma invariant:** a `check_achievements()` run leaves
  `count(*) from karma_events` unchanged.
- **Regression tolerance:** unlock `pebble-count-10`, delete pebbles below
  10, re-run — the unlock survives and nothing new inserts.
- **Purge:** `verify-account-purge.ts` passes with the unlock seeded and
  asserted at zero.
- **Unlock moment:** on each surface, creating the qualifying pebble fires
  the achievement toast alongside the karma pill without stacking conflicts.

Android cannot be verified locally (no SDK; `scripts/gradle-if-sdk.sh` exits
0 with a warning), so `android.yml` plus `LocalizationParityTest` is the
gate, and new logic stays in pure JVM-testable functions.

## Issues to cut

House cadence, sized per repo triage:

1. **[Feat] Achievements backend: catalog + unlocks + `check_achievements()`**
   — D1–D6, D9–D10. Migration, `sync_achievement_catalog()`, purge
   extension, `verify-account-purge.ts`, types regen, decision-log entry.
   Labels `feat` `db` `api` `supabase`. Size M.
2. **[Feat] Achievements web: grid screen + unlock moment** — D5, D7, D8.
   Provider methods + `useAchievements`, `/achievements` page, fire sites,
   activity toast, EN/FR strings, Arkaik flips. Reference implementation.
   Labels `feat` `ui` `core`. Size M.
3. **[Feat] Achievements iOS** — port of 2 per D7/D8; `AchievementsService`,
   Profile entry, unlock notify. Labels `feat` `ui` `core`. Size M.
4. **[Feat] Achievements Android** — symmetric port; parity test covers
   strings. Labels `feat` `ui` `core`. Size M.

Issue 1 gates 2–4; issues 3 and 4 start from 2's reference implementation
and can run in parallel. M50 consumes the result (roadmap: M48 gates M50
content).
