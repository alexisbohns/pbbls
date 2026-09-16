# Moderation State Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace #832's destructive takedown with a reversible `hidden_at` moderation state that the moderated user cannot revoke (issue #833, Kritik SAF-03 ×4).

**Architecture:** Two nullable columns on `pebbles` and `profiles`, pinned against owner writes by trigger guards, gating the cross-user and anon read paths. `resolve_content_report` sets the flag instead of mutating user settings; a new admin RPC toggles it directly (the appeal path); a separate explicit RPC releases a handle.

**Tech Stack:** Postgres (Supabase), plpgsql, Deno harnesses against the **linked remote project** (no local Docker).

**Spec:** `docs/superpowers/specs/2026-09-16-moderation-state-design.md`

---

## Read this before Task 1

- **Migrations push to the REMOTE linked project.** An applied migration is immutable — a correction is a new migration. So each migration file is written completely, then pushed once.
- **Type regeneration uses `db:types:remote`.** The plain `db:types` targets `--local` and truncates the file on failure.
- **Env:** `set -a; . ./.env; set +a` from the repo root. Never echo credential values.
- **Two harnesses, two reasons.** The anon-only one (CI-gated) proves the *guard* — that an owner cannot set or clear the flag. The service-role one (manual) proves the *visibility matrix*, because setting `hidden_at` in the first place needs an admin.

## File structure

| File | Responsibility |
|---|---|
| `packages/supabase/supabase/migrations/20260916100000_moderation_state.sql` | Create: columns, indexes, both guard triggers, `pebbles_select`, `get_shared_pebble`, `admin_set_content_hidden`, `resolve_content_report`, `admin_release_handle` |
| `packages/supabase/supabase/migrations/20260916100100_get_public_profile_hidden.sql` | Create: whole re-emission of `get_public_profile` + one predicate |
| `packages/supabase/scripts/verify-content-reports.ts` | Modify: guard assertions (anon, CI-gated) |
| `packages/supabase/scripts/verify-moderation-state.ts` | Create: visibility matrix (service role, manual) |
| `packages/supabase/package.json` | Modify: `db:verify:moderation` script |
| `packages/supabase/types/database.ts` | Regenerate |

---

### Task 1: Red — the guard assertions

The security property first: an owner must not be able to set **or** clear `hidden_at` on their own row. Setting is as dangerous as clearing — a user who can set it can hide a pebble and later claim it was moderated.

**Files:**
- Modify: `packages/supabase/scripts/verify-content-reports.ts`

- [ ] **Step 1: Add the assertions**

Insert before the section-6 admin block (`// 6. The admin surface is closed to non-admins.`):

```typescript
  // ---------------------------------------------------------------------------
  // 5b. Moderation columns are pinned against their own subject (#833 D7).
  //
  // pebbles_update and profiles_update are owner-scoped with NO column
  // restriction, and Postgres grants UPDATE on the whole table to
  // authenticated. Without the guard triggers, a hidden user clears hidden_at
  // and un-hides themselves — the moderation state would be revocable by
  // exactly the person it is applied to.
  //
  // SETTING is guarded too, not just clearing: a user who can set hidden_at
  // could hide a pebble and later claim it was moderated.
  // ---------------------------------------------------------------------------
  const { error: selfHideErr } = await o.from("pebbles")
    .update({ hidden_at: new Date().toISOString() }).eq("id", publicPebbleId);
  check("an owner cannot SET hidden_at on their own pebble",
    !!selfHideErr, "the pebbles moderation guard did not fire");

  const { error: selfUnhideErr } = await o.from("pebbles")
    .update({ hidden_at: null }).eq("id", publicPebbleId);
  check("an owner cannot CLEAR hidden_at on their own pebble",
    !!selfUnhideErr, "the pebbles moderation guard did not fire");

  const { error: selfProfileHideErr } = await o.from("profiles")
    .update({ hidden_at: null }).eq("user_id", owner.id);
  check("an owner cannot clear hidden_at on their own profile",
    !!selfProfileHideErr, "the profiles privileged guard did not cover hidden_at");

  // An ordinary column on the same table still writes — proving the guard is
  // column-scoped and has not broken normal profile edits.
  const { error: ordinaryErr } = await o.from("profiles")
    .update({ display_name: `owner ${runId}` }).eq("user_id", owner.id);
  check("an ordinary profile column still updates",
    !ordinaryErr, ordinaryErr?.message);
```

- [ ] **Step 2: Run it — expect the three guard assertions to FAIL**

```bash
cd /Users/alexis/code/pbbls && set -a && . ./.env && set +a && \
  deno run --allow-env --allow-net packages/supabase/scripts/verify-content-reports.ts
```

Expected: `passed=16 failed=3`. The three guard checks fail because the columns do not exist yet, so the update is a no-op error or a "column does not exist" error — either way `!!err` is... **careful**: a missing column DOES produce an error, so these would pass vacuously.

**This is the trap from the last plan.** Verify the failure reason: print the actual error. If it says `column "hidden_at" does not exist`, the assertion is passing for the wrong reason and proves nothing yet. That is expected at this stage — the real proof comes in Task 6, after the columns exist. Note it and move on; do not "fix" it.

- [ ] **Step 3: Commit**

```bash
git add packages/supabase/scripts/verify-content-reports.ts
git commit -m "test(db): assert the moderation columns are pinned against their subject

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: The moderation-state migration

**Files:**
- Create: `packages/supabase/supabase/migrations/20260916100000_moderation_state.sql`

- [ ] **Step 1: Write the migration**

```sql
-- =============================================================================
-- Moderation state (#833, M56) — a reversible hidden_at replacing the
-- destructive takedown shipped in 20260916090000.
-- =============================================================================
-- resolve_content_report's `actioned` branch overwrote pebbles.visibility with
-- 'secret' and nulled a profile's handle. That gave the queue teeth, which was
-- the right first move, but it is the wrong shape: the takedown cannot be
-- undone (the previous visibility is gone), cannot be audited (a hidden pebble
-- is indistinguishable from one the user set to secret), and silently
-- overwrites a setting the user owns. Kritik F-2026-08-SAF-{admin-01,
-- supabase-03, web-04, ios-05} all say so independently.
--
-- Design: docs/superpowers/specs/2026-09-16-moderation-state-design.md
--
-- get_public_profile's gate lands in 20260916100100 (its own file — it is a
-- whole re-emission under the standing rule).
-- preview_connection_invite's gate belongs to #834, which already re-emits
-- that function this milestone; two migrations re-emitting one body is how
-- appends get silently dropped (20260731090000 exists to repair that accident).
--
-- STANDING-RULE CHECK: no emotion or domain inserts, so
-- sync_achievement_catalog() is deliberately NOT re-run.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. The state. Columns rather than a polymorphic takedown table: pebbles_select
-- runs on every timeline read for every row, so a `not exists` subquery buys a
-- join where a null check does. Partial indexes stay tiny — hidden rows are
-- rare by construction.
-- ---------------------------------------------------------------------------
alter table public.pebbles
  add column hidden_at timestamptz,
  add column hidden_by uuid references auth.users(id) on delete set null;

alter table public.profiles
  add column hidden_at timestamptz,
  add column hidden_by uuid references auth.users(id) on delete set null;

create index pebbles_hidden on public.pebbles (id) where hidden_at is not null;
create index profiles_hidden on public.profiles (user_id) where hidden_at is not null;

comment on column public.pebbles.hidden_at is
  'Set by moderation (admin_set_content_hidden). Null = not hidden. The owner still sees the row; every cross-user and anon path is dark.';
comment on column public.profiles.hidden_at is
  'Set by moderation. Suspends the PUBLIC profile only — established connections still see the person (design D4).';

-- ---------------------------------------------------------------------------
-- 2. THE GUARDS. Without these the feature is theatre.
--
-- pebbles_update and profiles_update are owner-scoped with no column
-- restriction (20260411000001:169), and Supabase grants UPDATE on the whole
-- table to authenticated — a table-level grant covers every column. So a user
-- whose content is hidden could `update pebbles set hidden_at = null` and
-- un-hide themselves.
--
-- SETTING is pinned as well as clearing: a user who can set hidden_at could
-- hide a pebble and later claim it was moderated.
--
-- Shape and exemption copied from enforce_profile_privileged_columns
-- (20260902090000): security INVOKER so current_user stays the role that
-- issued the statement, and postgres / service_role / any definer function
-- passes through — which is what lets admin_set_content_hidden below work.
-- ---------------------------------------------------------------------------
create function public.enforce_pebble_moderation_columns()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  if current_user not in ('authenticated', 'anon') then
    return new;
  end if;

  if new.hidden_at is distinct from old.hidden_at
     or new.hidden_by is distinct from old.hidden_by then
    raise exception 'pebbles_moderation_column'
      using hint = 'hidden_at and hidden_by are set by moderation, not by the owner.';
  end if;

  return new;
end;
$$;

-- `of <columns>` keeps the trigger off the hot path: an ordinary pebble edit
-- never fires it. Naming a pinned column in the SET list is what fires it.
create trigger pebbles_moderation_guard
  before update of hidden_at, hidden_by
  on public.pebbles
  for each row execute function public.enforce_pebble_moderation_columns();

-- profiles already has the guard; extend it. BOTH halves are required: adding
-- the columns to the function body without adding them to the trigger's
-- `before update of` list is a silent no-op, because the trigger would never
-- fire on a statement that touches only hidden_at.
create or replace function public.enforce_profile_privileged_columns()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  if current_user not in ('authenticated', 'anon') then
    return new;
  end if;

  if new.is_admin is distinct from old.is_admin
     or new.max_media_per_pebble is distinct from old.max_media_per_pebble
     or new.terms_accepted_at is distinct from old.terms_accepted_at
     or new.privacy_accepted_at is distinct from old.privacy_accepted_at
     or new.hidden_at is distinct from old.hidden_at
     or new.hidden_by is distinct from old.hidden_by then
    raise exception 'profiles_privileged_column'
      using hint = 'is_admin, max_media_per_pebble, the consent timestamps and the moderation columns are not client-writable.';
  end if;

  return new;
end;
$$;

drop trigger profiles_privileged_guard on public.profiles;

create trigger profiles_privileged_guard
  before update of is_admin, max_media_per_pebble, terms_accepted_at,
                   privacy_accepted_at, hidden_at, hidden_by
  on public.profiles
  for each row execute function public.enforce_profile_privileged_columns();

-- ---------------------------------------------------------------------------
-- 3. pebbles_select: the gate on the cross-user arms only.
--
-- The OWNER ARM IS DELIBERATELY UNGATED (design D2). Hiding a pebble from its
-- author would present as data loss — they would believe the app ate their
-- entry. Suspending someone's public reach and deleting their journal must not
-- look the same from inside the app.
--
-- Base: 20260817130000 §3, with `hidden_at is null` wrapped around the two
-- non-owner arms.
-- ---------------------------------------------------------------------------
drop policy "pebbles_select" on public.pebbles;

create policy "pebbles_select" on public.pebbles
  for select to authenticated using (
    user_id = auth.uid()
    or (
      hidden_at is null
      and (
        visibility = 'public'
        or (
          visibility = 'private'
          and exists (
            select 1
              from public.connections c
             where c.user_a = least(auth.uid(), pebbles.user_id)
               and c.user_b = greatest(auth.uid(), pebbles.user_id)
          )
        )
      )
    )
  );

-- ---------------------------------------------------------------------------
-- 4. get_shared_pebble: the anon path. Base: 20260817130000 §4, one predicate
-- added. A hidden pebble is null to a link holder — same shape as secret,
-- private and unknown, so the link cannot distinguish "taken down" from
-- "never existed".
-- ---------------------------------------------------------------------------
create or replace function public.get_shared_pebble(p_pebble_id uuid)
returns jsonb
language sql
security definer
stable
set search_path = public
as $$
  select jsonb_build_object(
    'id', p.id,
    'name', p.name,
    'description', p.description,
    'happened_at', to_char(p.happened_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
    'intensity', p.intensity,
    'positiveness', p.positiveness,
    'render_svg', p.render_svg,
    'emotion', jsonb_build_object(
      'id', e.id,
      'slug', e.slug,
      'name', e.name,
      'color', e.color,
      'primary_color', c.primary_color,
      'secondary_color', c.secondary_color
    )
  )
  from public.pebbles p
  join public.emotions e on e.id = p.emotion_id
  left join public.emotion_categories c on c.id = e.category_id
  where p.id = p_pebble_id
    and p.visibility = 'public'
    and p.hidden_at is null;
$$;

-- ---------------------------------------------------------------------------
-- 5. admin_set_content_hidden — the toggle, and the APPEAL PATH.
--
-- Independent of any report, so an operator can act on a complaint that
-- arrived by other means, and can UN-hide wrongly-removed content, which
-- 20260916090000's destructive takedown made impossible.
-- ---------------------------------------------------------------------------
create function public.admin_set_content_hidden(
  p_target_kind text,
  p_target_id   uuid,
  p_hidden      boolean,
  p_note        text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_admin uuid := auth.uid();
  v_when  timestamptz := case when p_hidden then now() else null end;
  v_who   uuid := case when p_hidden then v_admin else null end;
  v_rows  integer;
begin
  if not public.is_admin(v_admin) then
    raise exception 'not_admin' using errcode = '42501';
  end if;

  if p_target_kind = 'pebble' then
    update public.pebbles set hidden_at = v_when, hidden_by = v_who
     where id = p_target_id;
  elsif p_target_kind = 'profile' then
    update public.profiles set hidden_at = v_when, hidden_by = v_who
     where user_id = p_target_id;
  else
    -- Glyphs keep glyph_submissions.listed: it is already a reversible flag in
    -- the existing moderation vocabulary (design D3).
    raise exception 'invalid_kind';
  end if;

  get diagnostics v_rows = row_count;
  if v_rows = 0 then raise exception 'not_found'; end if;

  return jsonb_build_object(
    'target_kind', p_target_kind,
    'target_id', p_target_id,
    'hidden', p_hidden,
    'note', nullif(btrim(p_note), '')
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 6. admin_release_handle — the DESTRUCTIVE half, now explicit.
--
-- resolve_content_report no longer nulls a handle (section 7). Releasing one
-- returns the name to the pool where anyone can claim it, so it is a separate
-- deliberate act — the right tool for impersonation ('Pebbles Support'), which
-- is the case that actually needs the name freed. Every other profile report
-- is served by hiding.
--
-- public_profile is forced false in the same statement:
-- profiles_public_requires_handle (20260730120000) is checked at statement end,
-- so a null handle with public_profile still true would violate it.
-- ---------------------------------------------------------------------------
create function public.admin_release_handle(p_user_id uuid, p_note text default null)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_admin  uuid := auth.uid();
  v_handle text;
begin
  if not public.is_admin(v_admin) then
    raise exception 'not_admin' using errcode = '42501';
  end if;

  select handle into v_handle from public.profiles where user_id = p_user_id;
  if not found then raise exception 'not_found'; end if;

  update public.profiles
     set handle = null, public_profile = false
   where user_id = p_user_id;

  return jsonb_build_object(
    'user_id', p_user_id,
    'released_handle', v_handle,
    'note', nullif(btrim(p_note), '')
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 7. resolve_content_report — re-emitted so `actioned` sets the flag instead
-- of mutating the user's own settings.
--
-- Base: 20260916090000 §5, with only the takedown branch changed. The verdict
-- and the takedown stay in one transaction: "the status says actioned" and
-- "the content is down" must remain one fact.
-- ---------------------------------------------------------------------------
create or replace function public.resolve_content_report(
  p_report_id uuid,
  p_outcome   text,
  p_note      text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_admin uuid := auth.uid();
  v_row   public.content_reports;
begin
  if not public.is_admin(v_admin) then
    raise exception 'not_admin' using errcode = '42501';
  end if;

  if p_outcome not in ('actioned', 'dismissed') then
    raise exception 'invalid_outcome';
  end if;

  -- for update: two admins resolving the same row race otherwise, and the
  -- second takedown would run against already-removed content.
  select * into v_row from public.content_reports where id = p_report_id for update;
  if not found then raise exception 'not_found'; end if;
  if v_row.status <> 'open' then raise exception 'invalid_state'; end if;

  if p_outcome = 'actioned' then
    if v_row.target_kind = 'glyph' then
      -- Every submission for the glyph: a re-attributed glyph can carry more
      -- than one, and leaving any listed leaves it in the Market.
      update public.glyph_submissions set listed = false where glyph_id = v_row.target_id;
    else
      -- Reversible, auditable, and it leaves the user's own visibility /
      -- handle settings alone. The handle of an impersonator is released
      -- separately and deliberately via admin_release_handle.
      perform public.admin_set_content_hidden(
        v_row.target_kind, v_row.target_id, true, p_note
      );
    end if;
  end if;

  update public.content_reports
     set status          = p_outcome,
         resolution_note = nullif(btrim(p_note), ''),
         resolved_at     = now(),
         resolved_by     = v_admin
   where id = p_report_id
  returning * into v_row;

  return to_jsonb(v_row);
end;
$$;

-- ---------------------------------------------------------------------------
-- 8. Grants: authenticated only; the is_admin guard does the real gating.
-- ---------------------------------------------------------------------------
revoke all on function public.admin_set_content_hidden(text, uuid, boolean, text) from public, anon;
revoke all on function public.admin_release_handle(uuid, text)                     from public, anon;
grant execute on function public.admin_set_content_hidden(text, uuid, boolean, text) to authenticated;
grant execute on function public.admin_release_handle(uuid, text)                     to authenticated;
```

- [ ] **Step 2: Push**

```bash
cd /Users/alexis/code/pbbls && npm run db:push --workspace=packages/supabase
```

Expected: `Applying migration 20260916100000_moderation_state.sql...` then `Finished supabase db push.` On a SQL error nothing was applied — report it and STOP.

- [ ] **Step 3: Commit**

```bash
git add packages/supabase/supabase/migrations/20260916100000_moderation_state.sql
git commit -m "feat(db): reversible moderation state, pinned against its subject

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Gate `get_public_profile`

A whole re-emission under the standing rule. Copy, insert, diff.

**Files:**
- Create: `packages/supabase/supabase/migrations/20260916100100_get_public_profile_hidden.sql`

- [ ] **Step 1: Copy the previous emission verbatim**

```bash
cd /Users/alexis/code/pbbls && \
  cp packages/supabase/supabase/migrations/20260817090000_public_profile_achievements.sql \
     packages/supabase/supabase/migrations/20260916100100_get_public_profile_hidden.sql
```

If that file contains anything besides the `get_public_profile` emission (check it), keep only the function and drop the rest, noting what you dropped in your report.

- [ ] **Step 2: Replace the header**

```sql
-- =============================================================================
-- get_public_profile: a hidden profile resolves null (#833)
-- =============================================================================
-- profiles.hidden_at (20260916100000) suspends a profile's PUBLIC reach. Every
-- CTE in this function keys off `target`, so gating that one CTE darkens the
-- whole projection — handle, display name, avatar glyph, assiduity grid,
-- badges. Null is the same answer the function already gives for an unknown or
-- unpublished handle, so "suspended" is indistinguishable from "never existed".
--
-- Established connections are deliberately NOT affected: get_connections is
-- untouched. Suspending public reach and blanking someone's name inside the
-- connection lists of people who already know them are different acts, and an
-- abusive display_name has its own remedy (#835). Design D4.
--
-- The body below is copied VERBATIM from
-- 20260817090000_public_profile_achievements.sql with exactly one addition, in
-- the `target` CTE. Re-emitting a whole function is how work gets silently
-- dropped: `create or replace` has no merge semantics and git reports no
-- conflict. The check is the pairwise diff — additions only.
-- =============================================================================
```

- [ ] **Step 3: Add the predicate**

In the `target` CTE, after `and p.public_profile = true`, add:

```sql
       and p.hidden_at is null
```

- [ ] **Step 4: The diff gate — additions only**

```bash
cd /Users/alexis/code/pbbls && diff \
  <(sed -n '/function public.get_public_profile/,$p' packages/supabase/supabase/migrations/20260817090000_public_profile_achievements.sql) \
  <(sed -n '/function public.get_public_profile/,$p' packages/supabase/supabase/migrations/20260916100100_get_public_profile_hidden.sql)
```

Expected: exactly one `>` line, zero `<` lines. **Any `<` means you dropped something — restore it before pushing.** Report the diff verbatim.

- [ ] **Step 5: Push, then commit**

```bash
cd /Users/alexis/code/pbbls && npm run db:push --workspace=packages/supabase
git add packages/supabase/supabase/migrations/20260916100100_get_public_profile_hidden.sql
git commit -m "feat(db): a hidden profile resolves null from get_public_profile

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Regenerate types

- [ ] **Step 1**

```bash
cd /Users/alexis/code/pbbls && npm run db:types:remote --workspace=packages/supabase
```

- [ ] **Step 2: Verify it grew, not emptied**

```bash
cd /Users/alexis/code/pbbls && git diff --numstat packages/supabase/types/database.ts && \
  grep -c "hidden_at\|admin_set_content_hidden\|admin_release_handle" packages/supabase/types/database.ts
```

Expected: insertions with **zero deletions**, and a grep count of at least 5. An empty file means generation failed — re-run, do not commit.

- [ ] **Step 3: Commit**

```bash
git add packages/supabase/types/database.ts
git commit -m "chore(db): regenerate types for the moderation columns

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: The visibility matrix harness

Needs the service role — setting `hidden_at` requires an admin, so this cannot be anon-only and does not join the CI gate.

**Files:**
- Create: `packages/supabase/scripts/verify-moderation-state.ts`
- Modify: `packages/supabase/package.json`

- [ ] **Step 1: Write the harness**

Model it on `verify-pebble-visibility.ts` for the signup/teardown helpers and on `verify-account-purge.ts` for `mintModerator` (sign up, then service-role `update profiles set is_admin = true`; the admin RPCs read `auth.uid()`, so they need the moderator's own session).

Seed: an owner, a connection, a stranger, a moderator. The owner has a `public` pebble, a `private` pebble, and a published profile. Then assert:

```
the guard, against a genuinely hidden row (moved here from the anon harness —
see its comment: null-to-null is not `is distinct from`, so the CLEAR case is
unprovable until something is actually hidden):
  the owner CANNOT clear hidden_at on their own hidden pebble
        -> error mentions pebbles_moderation_column
  the owner CANNOT clear hidden_at on their own hidden profile
        -> error mentions profiles_privileged_column
  this is THE assertion the feature rests on: without it moderation is
        revocable by its own subject

hidden pebble:
  owner still sees it via v_pebbles_full            <- D2, the anti-data-loss property
  stranger does not see it                           <- pebbles_select public arm
  connection does not see it                         <- pebbles_select private arm
  anon get_shared_pebble returns null
  it is absent from v_pebbles_full for the stranger  <- proves security_invoker
                                                        inheritance, not assumed
un-hidden again:
  every path above is restored                       <- the appeal path works

hidden profile:
  get_public_profile returns null
  the handle is STILL CLAIMED (set_handle by another user fails)  <- D6:
        hiding is not releasing
  after admin_release_handle, another user CAN claim it

resolve_content_report('actioned') on a pebble:
  sets hidden_at
  leaves visibility UNCHANGED at 'public'            <- the whole point of #833
```

Every assertion uses the `check(name, ok, detail)` helper and the harness exits non-zero on failure. Clean up all four accounts in a `finally`.

- [ ] **Step 2: Add the npm script**

```json
    "db:verify:moderation": "deno run --allow-env --allow-net scripts/verify-moderation-state.ts",
```

Do **not** add it to the `db:verify` chain — that chain is the anon-only CI gate, and this needs the service role. It sits beside `db:verify:purge` as a manual run.

- [ ] **Step 3: Run it**

```bash
cd /Users/alexis/code/pbbls && set -a && . ./.env && set +a && \
  npm run db:verify:moderation --workspace=packages/supabase
```

Expected: `failed=0`. If an assertion fails, the migration is the likely culprit — report it, do not weaken the assertion, and do not edit the applied migrations.

- [ ] **Step 4: Commit**

```bash
git add packages/supabase/scripts/verify-moderation-state.ts packages/supabase/package.json
git commit -m "test(db): prove the hidden-content visibility matrix

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Confirm the guards now pass for the RIGHT reason

Task 1's three guard assertions may have passed vacuously (`column does not exist` is also an error). Now that the columns exist, they must fail for the real reason.

- [ ] **Step 1: Run the anon harness**

```bash
cd /Users/alexis/code/pbbls && set -a && . ./.env && set +a && \
  npm run db:verify:reports --workspace=packages/supabase
```

Expected: `passed=19 failed=0`.

- [ ] **Step 2: Prove the error text is the guard, not a missing column**

Write a throwaway script in the scratchpad (NOT in the repo) that signs up a user, creates a pebble, attempts `update pebbles set hidden_at = now()`, and prints the raw error. It must mention `pebbles_moderation_column`, and the profile one `profiles_privileged_column` — **not** `column "hidden_at" does not exist`.

Report the exact strings. This is the assertion that the whole feature rests on.

- [ ] **Step 3: Full gate**

```bash
cd /Users/alexis/code/pbbls && set -a && . ./.env && set +a && \
  npm run db:verify --workspace=packages/supabase && \
  npm run db:verify:purge --workspace=packages/supabase && \
  npm run lint --workspace=packages/supabase && \
  npm run build --workspace=packages/supabase
```

`db:verify:purge` matters here: #832's purge assertions exercise `resolve_content_report`, whose takedown branch just changed. If the purge harness asserted `visibility === 'secret'` after actioning, that assertion is now **wrong** and must be updated to assert `hidden_at is not null` and `visibility` unchanged. Fix the harness, not the migration.

- [ ] **Step 4: Commit any harness fix**

```bash
git add packages/supabase/scripts/verify-account-purge.ts
git commit -m "test(db): actioning now hides rather than re-grading

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Map and PR

- [ ] **Step 1: Arkaik** — `create_node` for `API-admin-set-content-hidden` and `API-admin-release-handle` (create with the bare function name, then `update_node` the title to `RPC <fn>`; the id derives from the title verbatim). Edge each to `DM-pebbles` / `DM-profiles`. Update `DM-pebbles` and `DM-profiles` descriptions to mention the moderation columns. Hosted tools only — never `docs/arkaik/bundle.json`.

- [ ] **Step 2: PR** — title `feat(db): reversible moderation state for cross-user content`, body starting `Resolves #833`, closing the four SAF-03 findings. Labels `feat`, `db`, `supabase`, `legal`; milestone `M56 · Compliance Batch B`; `no-lab-note` (no user-visible change). **Confirm labels with the user before opening.**

- [ ] **Step 3: After merge** — resolve the four SAF-03 findings with the PR URL, and note on #834 that #833 has landed so the `hidden_at` column now exists for its `preview_connection_invite` gate.
