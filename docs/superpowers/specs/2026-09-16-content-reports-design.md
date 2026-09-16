# content_reports — the UGC report primitive (M56)

**Date:** 2026-09-16
**Issue:** [#831](https://github.com/alexisbohns/pbbls/issues/831)
**Kritik finding:** `F-2026-08-PLT-supabase-01` — high / P1 (impact 5 × likelihood 3, cost L)
**Surface:** `packages/supabase` only.

## 1. Why

The schema ships three cross-user content paths and no way to report any of them:

| Path | Free text a stranger can read | Migration |
|---|---|---|
| Public pebbles (`visibility = 'public'`) | `name`, `description` | `20260817130000` |
| Connection-visible pebbles (`'private'`) | `name`, `description` | `20260817130000` |
| Anonymous share links (`get_shared_pebble`) | `name`, `description` | `20260817130000` |
| Public profiles | `handle`, `display_name` | `20260730120000` |
| Marketplace glyphs | `name`, the drawing itself | `20260630084718` |

The only review apparatus is *pre-publication* admin review of glyph submissions. Apple 1.2 and Play's UGC policy require an in-product reporting mechanism wired to an operator queue, and store review checks for its visible presence.

Because the database is the contract for all four clients, no client can ship a report button until the server primitive exists. The gap is structural, not a per-client omission — which is why this is one backend change ahead of four client ones.

`purge_account` has named `reports` in its future-tables list since `20260729201326`. This is the milestone that writes it.

## 2. Scope

**In:** the table, its RLS posture, three RPCs, the `purge_account` extension, and two harnesses.

**Out, deliberately:**

- **Client report UI** — one issue per surface, per the standing split (a feature landing everywhere is one backend issue plus one per client).
- **Block enforcement.** `connection_blocks` exists (`20260730070347`) but only gates invite acceptance; it does not hide a blocked user's public pebbles or profile. Making it do so touches `pebbles_select` RLS, `get_shared_pebble` and `get_public_profile` — a different blast radius that earns its own review. Apple 1.2's block pillar stays open after this change.
- **Content filtering** (the third 1.2 pillar). Unbuilt, tracked separately.

## 3. The table

```sql
create table public.content_reports (
  id              uuid primary key default gen_random_uuid(),
  reporter_id     uuid references auth.users(id) on delete set null,
  target_kind     text not null check (target_kind in ('pebble','profile','glyph')),
  target_id       uuid not null,
  target_user_id  uuid not null references auth.users(id) on delete cascade,
  reason          text not null check (reason in (
                    'sexual','violence','hate','harassment',
                    'self_harm','illegal','spam','impersonation','other')),
  detail          text check (detail is null or length(detail) <= 1000),
  target_snapshot jsonb,
  status          text not null default 'open'
                    check (status in ('open','actioned','dismissed')),
  resolution_note text,
  resolved_at     timestamptz,
  resolved_by     uuid references auth.users(id) on delete set null,
  created_at      timestamptz not null default now()
);

create unique index content_reports_open_unique
  on public.content_reports (reporter_id, target_kind, target_id)
  where status = 'open';

create index content_reports_open_queue
  on public.content_reports (created_at)
  where status = 'open';
```

### D1 — Polymorphic target, no FK

`target_id` carries a pebble id, a profile `user_id`, or a glyph id depending on `target_kind`. Three kinds cannot share one foreign key, and three nullable typed columns (`pebble_id`, `profile_user_id`, `glyph_id`) would need a check constraint to enforce exactly-one-set and would force every consumer to branch on which column is populated. The polymorphic pair keeps the queue read uniform.

The cost is real and accepted: **nothing cascades when the target row is deleted.** A user who deletes a reported pebble leaves an orphan report behind. Section 6 says how the queue handles it.

### D2 — `target_user_id` is denormalised, not derived

Resolved server-side inside `report_content` (a definer function, so it reads past RLS) and stored. Two things depend on it:

1. `purge_account` deletes reports against a departing user with one statement instead of three kind-specific joins.
2. The queue groups by offender — the signal that separates one bad pebble from a bad account — without joining three tables.

It is `not null`: a report always has an owner at file time, because the RPC refuses targets it cannot resolve.

### D3 — `reason` is a check-constrained slug set, not a lookup table

Nine slugs, closed set, clients localise them. A reference table would need seeding, an admin CRUD surface, and a `sync_achievement_catalog()`-style drift rule for nine values that change roughly never. The CHECK makes an invalid reason unrepresentable on every write path, which is what the reference tables buy and all this needs.

`detail` is capped at 1000 characters. It is free text from a reporter and it is displayed to an operator; an uncapped column is a place to paste a novel.

### D4 — `target_snapshot` captures the reported content at file time

A jsonb snapshot of the free text as it read when reported: `name`/`description` for a pebble, `handle`/`display_name` for a profile, `name` for a glyph. Populated inside the definer RPC.

Without it, an owner who edits their pebble after being reported presents innocent content to the reviewer, who dismisses it — a one-line evasion of the whole pillar. It also keeps an orphaned report meaningful: the reviewer can still see what was reported after the target is gone.

**Privacy note.** This copies personal data into a second table, so it is new surface for a DSAR and for the DPIA. The erasure story holds: `purge_account` deletes every report where `target_user_id` is the departing user, and the snapshot only ever contains the *target's* text, never the reporter's. The privacy policy needs a line describing it — tracked with the client issues, not here.

### D5 — RLS enabled, no policies at all

Not even a `select`. Every read and write goes through the RPCs below. This is the `wallet_balances` / `connection_blocks` pattern (`20260629193636`, `20260730070347`), not the `pebble_drafts` `for all` pattern — the latter is for sanctioned direct-client single-table CRUD, which this is not.

A reporter never reads their report back. The report is fire-and-forget: the client shows a confirmation and moves on. A readable `content_reports` is a map of who reported whom, and there is no product reason to expose it.

`revoke all on public.content_reports from anon` follows, matching the defense-in-depth on `pebbles` and `v_pebbles_full`.

## 4. `report_content` — the insert path

```sql
report_content(
  p_target_kind text,
  p_target_id   uuid,
  p_reason      text,
  p_detail      text default null
) returns jsonb
```

`security definer`, granted to `authenticated` only.

**Anonymous reporting is deliberately not supported.** An anon share-link viewer cannot file. Granting this to `anon` would be an unauthenticated write to a table any bot can find, and the schema has no rate-limit primitive to lean on. Apple 1.2 asks for a report mechanism for the app's users; the anon share page routes its report affordance to sign-in or to the published moderation contact. This is the one loophole the design accepts knowingly — the most widely reachable surface is the one without an in-product path.

**Visibility gate.** The RPC asserts the reporter can actually see the target, mirroring each kind's live read rule:

| kind | visible when |
|---|---|
| `pebble` | `visibility = 'public'`, or `'private'` with a mutual connection to the owner |
| `profile` | `public_profile = true` |
| `glyph` | has a submission that is `approved` and `listed` |

**Invisible and nonexistent raise the same `not_found`.** Without this, the RPC is an existence oracle: feed it uuids and learn which secret pebbles exist. It is the same choice `get_shared_pebble` already makes by returning null for both cases.

Reporting your own content raises `cannot_report_own`.

**Ownerless glyphs resolve cleanly.** `purge_account` anonymises a sold glyph to `user_id = null` but delists it in the same pass, so an ownerless glyph fails the `listed` gate and raises `not_found` before `target_user_id` (which is `not null`) is ever needed. A glyph re-attributed to a new owner by `admin_attribute_glyph` stays listed and resolves to that owner.

**Idempotency.** `insert ... on conflict do nothing` against `content_reports_open_unique`, then return the existing row. A second file is a success, not an error — from the user's point of view they reported it, and forcing the client to handle an `already_reported` slug for that is ceremony. Once a report is resolved the partial index no longer covers it, so a reoffending target can be reported again.

**Error slugs are a wire contract** (clients substring-match them, per `20260730070347`): `not_authenticated` (42501), `not_found`, `cannot_report_own`, `invalid_kind`, `invalid_reason`.

## 5. The admin RPCs

Both `security definer`, both guarded on `is_admin(auth.uid())` with `raise exception 'not_admin' using errcode = '42501'`, both granted to `authenticated` with the guard doing the real gating. This is `admin_list_glyph_submissions` followed verbatim (`20260630084718`).

### `admin_list_content_reports(p_status text default null)`

Returns the queue oldest-first (FIFO review), joining the live target so the reviewer sees current content beside the snapshot. Each row carries `target_missing: true` when the target row is gone — the orphan case from D1. Includes an `open_reports_against_target` count so a flooded target sorts above a one-off.

The reporter's email is **not** joined. `admin_list_glyph_submissions` joins `auth.users` for the submitter email and #766 is open against exactly that — materialising third-party emails into an admin list. This one does not repeat it.

### `resolve_content_report(p_report_id uuid, p_outcome text, p_note text default null)`

`p_outcome` is `'actioned'` or `'dismissed'`. Writes `status`, `resolution_note`, `resolved_at`, `resolved_by`. Refuses a report that is not `open` with `invalid_state`, matching `approve_glyph`.

`'actioned'` performs the takedown **in the same transaction as the status write**, dispatching on `target_kind`:

| kind | takedown |
|---|---|
| `pebble` | `update pebbles set visibility = 'secret'` |
| `glyph` | `update glyph_submissions set listed = false` for every submission on that glyph |
| `profile` | `update profiles set public_profile = false, handle = null` |

**Why enforcement lives here rather than in a separate RPC.** There is no admin takedown path in the schema today — `pebblestore` only approves and rejects *pending* glyph submissions, so an approved-and-live glyph has no removal route at all. A triage-only queue would let a reviewer mark a report `actioned` with nothing actually happening, which is precisely the failure store review looks for. One RPC makes "the status says actioned" and "the content is down" the same fact.

The profile case releases the handle, which is the actual remedy for the handle-impersonation abuse `docs/decisions/log.md` defers to "M56 adds reporting". `display_name` is left alone: it is only reachable through the public page that just went away. Both columns are written in one statement so `profiles_public_requires_handle` sees a consistent row at statement end.

Takedowns are not reversible through this RPC. Restoring wrongly-removed content is a service-role operation until an appeal flow exists.

## 6. Orphans

Nothing cascades from `target_id` (D1). A user who deletes their own reported pebble leaves a report pointing at nothing.

The queue surfaces these with `target_missing: true` and the snapshot intact, and an admin dismisses them. No trigger, no cleanup job: the volume is bounded by report volume, deleting the reported content is itself self-remediation, and the alternative is three triggers on three tables to save an operator one click.

## 7. `purge_account`

A new migration re-emits `purge_account` **whole**, per the standing rule. The header carries the pairwise-diff note (`create or replace` has no merge semantics; git reports no conflict; anything not carried forward is silently dropped) and the body is copied verbatim from `20260911090100_purge_account_consents.sql` with one addition at the section-(4) `>>> APPEND <<<` marker:

```sql
-- Detach, don't delete: the moderation trail must survive a reporter who
-- leaves. Mirrors the glyph_submissions submitter detach in section (2).
update public.content_reports set reporter_id = null where reporter_id = p_user_id;
get diagnostics v_n = row_count;
v_counts := v_counts || jsonb_build_object('content_reports_detached', v_n);

-- Reports AGAINST the departing user go: their content is gone with the
-- account, so an open report about it is unresolvable noise.
delete from public.content_reports where target_user_id = p_user_id;
get diagnostics v_n = row_count;
v_counts := v_counts || jsonb_build_object('content_reports', v_n);
```

Ordering: the detach must precede the delete, or a self-report (impossible today — `cannot_report_own` — but cheap to be right about) would be deleted before it could be detached, and the count would mislead.

`reporter_id` carries `on delete set null` and `target_user_id` `on delete cascade`, so `auth.admin.deleteUser` would eventually reap both. The explicit statements exist for the section-(4) reason already documented there: they make "all personal rows gone" true at RPC success, and keep a re-run meaningful when `deleteUser` was the step that failed.

**Standing-rule check:** no emotion or domain inserts, so no `sync_achievement_catalog()` re-run. Stated in the migration header.

## 8. Harnesses

### `verify-account-purge.ts` (extended, same change — standing rule)

The seller seeds a report **filed against the buyer** and the buyer seeds a report **filed against the seller**. After the seller is purged:

- the seller's filed report survives with `reporter_id = null` (trail intact, identifier gone)
- reports against the seller are zero
- the purge re-run still converges to all-zero counts

It also carries the three assertions the anon-only harness structurally cannot (see below), all of which need the service role:

- `target_user_id` is resolved server-side to the content's real owner, and `target_snapshot` captured the text as filed — **and editing the pebble afterwards does not rewrite it**, which is the property the whole snapshot decision (D4) exists for
- a report filed against the seeded **listed glyph**
- a service-role-minted admin driving `admin_list_content_reports` and `resolve_content_report` through one takedown per `target_kind`, plus the refusal to resolve an already-resolved report

### `verify-content-reports.ts` (new, anon-only)

Anon-only so it can join the CI gate — it signs up throwaway users and deletes them through the real `delete-account` edge function, like `verify-public-profile.ts`. Proves:

- a report on a public pebble and on a public profile is accepted, returning an `open` report and a whole-second UTC `created_at`
- another user's *unlisted* glyph submission raises `not_found` (the negative half of the glyph gate)
- a second file returns the same row (idempotency), and no duplicate lands
- a secret pebble and a random uuid both raise `not_found` — the same message (enumeration resistance)
- own content raises `cannot_report_own`
- the table is opaque: a direct `select` under RLS returns nothing to the reporter who just filed
- a non-admin calling either admin RPC gets `not_admin`

**What it cannot prove, and where the gap is covered instead.** Two things need an admin, and an anon-only harness cannot mint one past `profiles_privileged_guard` (`20260902090000`):

1. *That `target_user_id` and `target_snapshot` are resolved correctly.* Both are unreadable from here **by construction** — §5's whole point is that the reporter cannot read the table back. Asserting them needs the service role.
2. *Reporting a listed glyph.* A submission is `pending` until an admin approves it, so this harness can never produce a listed target. It asserts the negative instead (an unlisted glyph is not reportable). The positive case moves to `verify-account-purge.ts`, which holds the service role and **already seeds an approved, listed, sold glyph** — a report against it costs one more seed statement there.
3. *The positive admin path* (`admin_list_content_reports` returning a queue, `resolve_content_report` taking down content). This is the limit glyph moderation already lives with — `admin_list_glyph_submissions` has no harness either. The negative case (non-admin refused) is the security assertion and it is covered here; the takedown dispatch is exercised in `verify-account-purge.ts` under the service role, where an admin can be minted directly.

Wired into `db:verify` and `supabase.yml` as a sixth step, alongside the other anon-only four.

## 9. Follow-up issues

| Work | Surface |
|---|---|
| Report affordance on public pebbles, public profiles, marketplace glyphs | web |
| Same | iOS |
| Same | Android |
| Moderation queue page reading `admin_list_content_reports` | admin |
| Block enforcement: make `connection_blocks` hide public content | supabase |
| Privacy policy line describing `target_snapshot` retention | legal |
