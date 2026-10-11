# Moments and pebbles split — design

Design doc for splitting a recorded moment from the pebble it becomes, and for
deriving a pebble's glyph from its collection or domain. Follows the strategy
audit of 2026-10-08 (private artifact): capture is eleven steps and dissuasive,
and vision C (one moment shared by a few people, each with their own pebble) is
the north star.

## 1. Decisions

| # | Decision |
|---|---|
| D1 | **A moment is what happened; a pebble is what it meant to one person.** Moment: date, name, description, photos, later participants. Pebble: emotion, valence, domains, souls, collections, cards, sharing grade. |
| D2 | **A pebble row exists only when complete.** Complete = emotion + valence (intensity, positiveness) + at least one domain. Souls, collections and cards stay optional. Partial completion lives in the composer's local autosave, never in the database. |
| D3 | **A moment needs a date plus a name or a photo.** `happened_at` defaults to now. |
| D4 | **Reward only on completion.** Karma and achievements fire the first time a (moment, user) pebble is inserted. Saving a bare moment grants nothing. |
| D5 | **Glyphs are no longer pebble-specific.** Only souls, collections and profiles carry a glyph. A pebble's glyph resolves from its oldest collection carrying one, else its first domain's default glyph, else none. Existing pebble glyphs are dropped; custom glyphs stay in the user's library. |
| D6 | **No render cache.** `render_svg` and `render_version` are dropped. Clients draw the resolved glyph's strokes. |
| D7 | **Server drafts are retired.** A quick capture is a moment. Device-local autosave stays. |
| D8 | **Surfaces.** Database first. iOS and Android ship the new flows together, in parallel sessions. Web gets a database-side compatibility layer only; its flows are a parked issue. |
| D9 | **Identity is stable through the migration.** Every existing pebble id becomes its moment id too. Snap storage paths, share links and report targets keep their ids. |

## 2. Schema

### `moments` (new)

| Column | Type | Notes |
|---|---|---|
| `id` | uuid pk | For backfilled rows, equals the former pebble id |
| `user_id` | uuid → auth.users (no cascade, like `pebbles.user_id`; purge deletes explicitly) | Owner. Participants come with pairs, later |
| `name` | text null | |
| `description` | text null | |
| `happened_at` | timestamptz not null default now() | Whole-second precision on every write |
| `created_at`, `updated_at` | timestamptz | |
| `hidden_at`, `hidden_by` | moved from pebbles | Moderation targets the moment |

RLS: `moments_select` = own rows, or there exists a pebble on this moment the
caller may read under `pebbles_select` (same three arms, same block predicate).
Insert/update/delete: owner only. The select policy is one named policy so a
participants table can widen it without touching readers.

"name or snap" is an RPC rule (cross-table check), not a constraint.

### `pebbles` (changed)

- Dropped: `name`, `description`, `happened_at`, `glyph_id`, `render_svg`,
  `render_version`, `hidden_at`, `hidden_by`.
- Added: `moment_id uuid not null references moments(id) on delete cascade`,
  unique on `(moment_id, user_id)`.
- Kept, NOT NULL: `emotion_id`, `intensity`, `positiveness`. Kept: `visibility`,
  `user_id`, timestamps.
- "At least one domain" is an RPC rule. `pebble_domains`, `pebble_souls`,
  `pebble_cards`, `collection_pebbles` are unchanged.

### `snaps`

`pebble_id` becomes `moment_id` (→ moments on delete cascade). Storage paths and
`sort_order` unchanged. Quota counts snaps per moment owner.

### `collections`

Gains `glyph_id uuid null references glyphs(id)`, guarded by a
`before insert or update of glyph_id` trigger calling `can_use_glyph`, copied
from the souls trigger (`20260919160000`). Purge order: collections before glyphs.

### `content_reports`

`target_kind` check gains `'moment'`. Existing `'pebble'` rows are rewritten to
`'moment'` (same id after the backfill). `'pebble'` is removed from the check.

### `pebble_drafts`

Dropped. Payloads with a non-empty `name` become moments (fresh id, no pebble);
the rest are discarded.

### Backfill (one transaction)

1. Insert one moment per pebble, reusing the pebble id, copying `user_id`,
   `name`, `description`, `happened_at`, `created_at`, `updated_at`,
   `hidden_at`, `hidden_by`.
2. Add `pebbles.moment_id`, set it to `id`, then make it NOT NULL.
3. Repoint `snaps` and rewrite `content_reports` kinds.
4. Convert drafts, drop the table.
5. Drop the moved and glyph columns from pebbles.

Rehearsed on the native local Postgres with a dump of production rows before
`db push`. The sweep harness is run against the result to prove no snap file is
orphaned.

## 3. Write contract

### `save_moment(payload jsonb) → uuid`

Create or update, keyed on optional `id`. Keys: `name`, `description`,
`happened_at`, `snaps` (array of `{storage_path, sort_order}`, same shape as
today's `create_pebble`). Owner only. Refuses a moment with neither a name nor a
snap (`moment_empty`). Emits timestamps at whole seconds. No karma, no
achievements.

### `complete_pebble(payload jsonb) → uuid`

Create or update, keyed on `moment_id` + caller. Keys: `moment_id`,
`emotion_id`, `intensity`, `positiveness`, `visibility`, `domain_ids` (required,
non-empty, else `pebble_needs_domain`), `soul_ids`, `new_souls`,
`collection_ids`, `new_collections`, `cards`. Caller must own the moment (later:
be a participant). First insert for the (moment, user) writes the
`pebble_created` karma event; updates rewrite the join tables as
`update_pebble` does today and write `pebble_enriched` deltas. Clients keep
calling `check_achievements` after a completion, as they do after a publish
today, so the unlock celebration contract does not move server-side. Collection
and soul ownership are both checked (`collection_not_owned`, `soul_not_owned`,
42501); the old RPCs only checked collections. In both writers an explicit JSON
null means "absent" (Kotlin and Swift encoders emit explicit nulls) and array
keys are type-checked (`invalid_payload`, 22023).

### `compute_karma_delta`

Loses `p_has_glyph`. Description bonus reads the moment's description.

### `delete_pebble(p_pebble_id)` / `delete_moment(p_moment_id)`

`delete_pebble` removes the pebble only, the moment stays. `delete_moment`
removes the moment, its pebbles and snaps by cascade, and marks the snap files
for the orphan sweep the way `delete_pebble` does today.

### `set_collection_glyph(p_collection_id, p_glyph_id)`

Single-row update behind the trigger, named for symmetry with souls.

### Retired

`create_pebble`, `update_pebble` (kept as compat wrappers, see §5), the drafts
RPCs, the `new_glyph` and `glyph_id` payload keys.

## 4. Read side

### `resolve_pebble_glyph(p_pebble_id uuid) → jsonb`

`{id, name, strokes, view_box, source}` or null, `source` being `collection` or
`domain`. Order: the pebble's collection with a glyph and the oldest
`collections.created_at`; else the pebble's domain with a `default_glyph_id`
and the lowest slug (domains carry no order column); else null.

Two layers, both `stable`, `set search_path = public`:

- `private.resolve_pebble_glyph` is `security definer` and does the resolution.
  `collection_pebbles` is owner-readable, so an invoker would carve a different
  stone for a connection than for the owner; the definer makes every viewer
  see the same glyph. `private` is never served by PostgREST, so this is not an
  API; `authenticated` holds execute only so the wrapper can call it.
- `public.resolve_pebble_glyph` is `security invoker` and is the API: it
  returns the private result only `where exists (select 1 from pebbles where
  id = p_pebble_id)`. That exists runs under the caller's `pebbles_select`, so
  a pebble the caller cannot read answers null, the same as an unknown id. A
  bare definer in `public` would have let any authenticated user probe a
  secret pebble's custom glyph by uuid; this closes that oracle.

`v_moments_full` and `path_moments` (both invoker) call the public layer; the
owner always passes the gate. `get_shared_pebble` is definer and serves anon,
so it calls the private layer directly: its own `public` + not-hidden where
clause is the readability gate there.

### `v_moments_full` (replaces `v_pebbles_full`)

One row per readable moment: moment columns, `snaps`, and `pebble` = the
caller's own pebble on that moment as jsonb or null (`id`, `emotion`,
`intensity`, `positiveness`, `visibility`, `domains`, `souls`, `collections`,
`cards`, `glyph`). `security invoker`.

### `path_moments` (replaces `path_pebbles`)

`id`, `name`, `happened_at`, `first_snap_path`, `pebble` jsonb or null
(`emotion`, `intensity`, `glyph`). Null pebble = uncompleted moment.

### `get_shared_pebble(p_pebble_id)`

Same name and argument. Returns moment fields plus the sharer's pebble in the
new shape. `get_public_profile` unchanged.

### Moderation and reports

`hidden_at` logic moves to moments (`enforce_pebble_moderation_columns` →
moments). `report_content` accepts `moment`; the gate is "the reporter may read
a pebble on this moment". `admin_list_content_reports` resolves moment targets.

### Achievements and karma

`check_achievements` keeps counting `pebbles` rows (complete by construction).
No new achievement kinds.

### Purge

`purge_account` deletes moments (cascades pebbles and snaps). Body unioned by
hand against the current emission. Harness gains the `moments` seed and
zero-row assertion.

## 5. Web compatibility layer (database side)

Kept for web until its composer issue lands, each marked deprecated in its
migration comment:

- `v_pebbles_full` as a view in its old column set, joining moment + own pebble,
  `glyph` = resolved glyph, `glyph_id` = resolved glyph id, `render_svg` null.
- `path_pebbles` in its old signature, `render_svg` null.
- `create_pebble` / `update_pebble` wrappers calling `save_moment` then
  `complete_pebble`, ignoring glyph keys. Behaviour as today: one call, one
  complete pebble.
- The `compose-pebble` and `compose-pebble-update` edge functions, which are
  what all three clients call today (not the RPCs), keep their request and
  response shapes, call the wrappers, and return `render_svg` and
  `render_version` as null. The render engine, `compose-and-write` and
  `backfill-pebble-render` are deleted.

Proven by running the current web app against the migrated local database
before Part 1 merges. Removed when the web composer ships.

**Installed iOS and Android builds break at the push.** They read `pebbles`
directly with embedded selects on the dropped columns. Pre-launch this is
accepted; Parts 2 and 3 ship the fix. The PR body says so.

## 6. Clients

### iOS and Android (1:1, parallel sessions)

Two flows replacing the eleven-step composer, both on the existing step
scaffold and haptics model:

| Flow | Steps | Ends with |
|---|---|---|
| Capture | photo · when · name | `save_moment`, return to Path. A "make it a pebble now" button chains into Complete. |
| Complete | valence · emotion · domain · souls · collection · privacy | `complete_pebble`, success screen with stone draw-on and karma. |

- Path: an uncompleted moment is a blank stone in the neutral tone, with its
  photo if any. The read page carries one call to action to complete it.
- Collection editor gains a glyph picker. Glyph library loses the pebble tab.
- Local autosave keeps a half-finished Complete flow per moment id.
- Drafts list and service are removed.
- Android draws the resolved glyph strokes over the stone (the planned port of
  the iOS stone render starts here).
- Decoder tests use verbatim payloads from `v_moments_full` and `path_moments`:
  null pebble, explicit null name, photo-only moment, whole-second and
  fractional timestamps.

### Web (this milestone)

Compat only: render the resolved glyph strokes instead of `render_svg`, hide the
glyph step, remove the drafts page. Full flows are a parked issue.

### Admin

Moderation list and hide action target moments. Dashboard shows moment count
beside pebble count (completion rate).

## 7. Plan

**Milestone**: one backend issue, one iOS, one Android, one web compat, one
admin, one parked web composer issue.

**Part 1 — database** (stacked, merged before any client branch forks):

1. Schema + backfill + types (`db:types:remote`).
2. Writers: `save_moment`, `complete_pebble`, `delete_moment`,
   `compute_karma_delta`, `set_collection_glyph`.
3. Readers: `resolve_pebble_glyph`, `v_moments_full`, `path_moments`,
   `get_shared_pebble`, moderation, reports, `purge_account`.
4. Web compat views and wrappers.
5. Harnesses: new `verify-moments`; update visibility, reports, moderation,
   orphan sweep, purge; delete `verify-pebble-drafts`. Purge harness run
   manually against the linked project.

**Part 2 — iOS** and **Part 3 — Android**, in parallel worktrees: models and
services → Capture flow → Complete flow → Path and read page → collection
glyph editor → drafts removal → foreign-payload tests.

**Part 4 — web compat.** **Part 5 — admin.**

Decision log: one entry (moment/pebble split, pebble exists only when complete,
glyph derived). Arkaik: Moment data model node, Pebble and Collection updated,
two iOS and two Android views, endpoints renamed, drafts views retired,
acceptances moved to `development` as parts start.

## 8. Risks

- The schema migration is the one irreversible step: rehearsed on local
  Postgres with production rows, sweep harness run on the result.
- `purge_account` re-emission: diff against the latest body before writing.
- Compat wrappers must reproduce today's single-call semantics exactly, or web
  users lose pebbles silently. Covered by running the web app and
  `verify-pebble-visibility` against the compat view.
- Glyph resolution order changes a pebble's carving when collections change.
  Accepted: that is the meaning of an inherited glyph.

## 9. Out of scope

Participants and shared moments (pairs), new achievement kinds, web Capture and
Complete flows, cleanup of the old iOS renderers, export.
