# Moderation state — replacing destructive takedown (M56)

**Date:** 2026-09-16
**Issue:** [#833](https://github.com/alexisbohns/pbbls/issues/833)
**Kritik:** `F-2026-08-SAF-admin-01`, `F-2026-08-SAF-supabase-03`, `F-2026-08-SAF-web-04`, `F-2026-08-SAF-ios-05` (SAF-03, P2)
**Surface:** `packages/supabase` only.
**Supersedes part of:** `2026-09-16-content-reports-design.md` §5.

## 1. Why, and what this admits

[#832](https://github.com/alexisbohns/pbbls/pull/832) gave operators a takedown for the first time. It is **destructive**: `resolve_content_report(..., 'actioned')` overwrites `pebbles.visibility` with `'secret'` and nulls a profile's `handle`. That was the right first move — a triage-only queue with no teeth is the failure store review looks for — but it is the wrong long-term shape, and all four SAF-03 findings say so independently.

Three concrete problems with destructive takedown:

1. **It cannot be undone.** The previous `visibility` is gone. A wrongly-hidden pebble cannot be restored to `public` because nothing recorded that it *was* public.
2. **It cannot be audited.** A pebble at `secret` is indistinguishable from one the user set to `secret` themselves. "Was this hidden by us?" has no answer.
3. **It overwrites user data.** A moderation decision silently mutates a setting the user owns.

This change replaces it with a reversible flag that sits *beside* the user's own settings rather than on top of them.

## 2. The shape

```sql
alter table public.pebbles
  add column hidden_at timestamptz,
  add column hidden_by uuid references auth.users(id) on delete set null;

alter table public.profiles
  add column hidden_at timestamptz,
  add column hidden_by uuid references auth.users(id) on delete set null;

create index pebbles_hidden on public.pebbles (id) where hidden_at is not null;
create index profiles_hidden on public.profiles (user_id) where hidden_at is not null;
```

### D1 — Columns on the tables, not a polymorphic takedown table

`content_reports` is polymorphic and that was right: reports are sparse and never read on a hot path. Takedown state is read on the **hottest path in the schema** — `pebbles_select` runs on every timeline read, for every row. A `not exists (select 1 from content_takedowns …)` per row buys a join where a null check would do.

The cost is two near-empty columns on two wide tables and a partial index each, which is the cheap direction of this trade.

### D2 — Hidden content stays visible to its owner

The gate goes on the cross-user and anonymous arms only. The owner keeps seeing their own pebble.

Hiding it from the owner too would present as data loss — the user would believe the app ate their entry. Suspending someone's public reach is a moderation act; deleting their journal is not, and the two must not look the same from inside the app. (Telling the owner *why* it is hidden needs a notification surface that does not exist; noted as follow-up, not built here.)

### D3 — Glyphs keep `listed = false`

No `hidden_at` on glyphs. `glyph_submissions.listed` is **already** a reversible flag in the existing moderation vocabulary, and glyphs already have a `pending/approved/rejected` state machine. Adding a parallel mechanism for the same job would be the new pattern this repo's conventions ask us to discuss first, and it would buy nothing.

### D4 — A hidden profile darkens public reach, not established connections

Gated: `get_public_profile`. **Not gated:** `get_connections`.

`F-2026-08-SAF-supabase-03` cites `get_connections` as an exposure path for `display_name`, and it is — but blanking names inside the connection lists of people who already know the person punishes the wrong users and reads as a bug. The abuse vector this closes is *strangers*.

An abusive `display_name` has a better-targeted remedy: the admin reset RPC in [#835](https://github.com/alexisbohns/pbbls/issues/835). Hiding a profile suspends its public page; resetting a display name fixes an abusive name. Two harms, two tools.

### D5 — `preview_connection_invite` is deliberately NOT touched here

It is an anon-granted path projecting `display_name` and raw glyph strokes, so a hidden profile arguably should darken it. It is left alone anyway, because **[#834](https://github.com/alexisbohns/pbbls/issues/834) already re-emits that exact function** to close the block oracle.

Two migrations re-emitting one function body is the failure mode the standing rule exists for: `create or replace` has no merge semantics, git reports no conflict, and whichever lands second silently deletes the other's work. Rather than race, the hidden-profile gate on `preview_connection_invite` belongs to #834, which owns that function this milestone. **#834's scope must be extended to include it** — recorded there, not here.

### D6 — Releasing a handle becomes a separate, explicit action

`resolve_content_report` stops nulling `handle`. The default takedown is now reversible, and releasing a handle is not — the name goes back to the pool and someone else can claim it.

A new `admin_release_handle(p_user_id, p_note)` does it deliberately. This is the right tool for impersonation (`Pebbles Support`), which is the case that actually needs the name freed; every other profile report is served by hiding.

## 3. RPC changes

### `admin_set_content_hidden(p_target_kind, p_target_id, p_hidden, p_note)`

New, `is_admin`-gated definer. Sets or clears `hidden_at` / `hidden_by` on a pebble or profile. This is the **un-hide path** the findings ask for — the appeal half — and it works independently of any report, so an operator can act on a complaint that arrived by other means.

### `resolve_content_report`

Re-emitted. The `actioned` branch now calls `admin_set_content_hidden` internally instead of mutating `visibility` / `handle`. Glyphs still go `listed = false` (D3).

Same transaction as the verdict, unchanged — "the status says actioned" and "the content is down" stay one fact.

## 4. Read paths gated

| Path | Change |
|---|---|
| `pebbles_select` RLS | `and hidden_at is null` on the `public` and `private` arms; owner arm untouched (D2) |
| `get_shared_pebble` | `and p.hidden_at is null` |
| `get_public_profile` | `and p.hidden_at is null` in the `target` CTE — everything keys off it, so one line gates the whole function |
| `v_pebbles_full` | **nothing.** It is `security_invoker` since `20260729000000`, so it inherits `pebbles_select`. Verify with a harness assertion rather than re-emitting. |

`get_public_profile`'s latest emission is in `20260817090000_public_profile_achievements.sql` and is long. It is a whole re-emission under the standing rule: copy that file's body verbatim, add the one line, and prove additions-only by pairwise diff.

## 5. Migration of existing state

`resolve_content_report` has actioned nothing in production — the only calls were harness runs, which delete their fixtures. So there is no destructive-takedown history to reverse, and no backfill.

This is worth stating rather than assuming: if any real report had been actioned, the previous `visibility` would be unrecoverable and those users would need contacting by hand.

## 6. Harness

`verify-content-reports.ts` (anon-only, CI-gated) gains the hidden-content matrix:

- a hidden public pebble is dark to a stranger, dark to a connection, dark to anon via `get_shared_pebble`, and **still visible to its owner** (D2)
- a hidden pebble is absent from `v_pebbles_full` for a stranger — proving the `security_invoker` inheritance rather than assuming it
- a hidden profile resolves null from `get_public_profile`
- a hidden profile's `handle` is **still claimed** (hiding is not releasing — D6)
- un-hiding restores every path

`verify-account-purge.ts` gains: the `actioned` takedown sets `hidden_at` and leaves `visibility` untouched, and `admin_release_handle` frees the handle.

## 7. Follow-ups

- Tell the owner their content was hidden — needs a notification surface.
- `preview_connection_invite` hidden-profile gate → **#834**.
- Abusive `display_name` reset → **#835**.
