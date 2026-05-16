# Profile data foundations — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the SQL foundations (one migration + two RPCs + a column) needed by the iOS Profile redesign (#451) and the new Settings sheet (#452). No iOS code.

**Architecture:** Single migration adds `profiles.glyph_id` (nullable FK to `glyphs`), `update_profile(p_display_name, p_glyph_id)` for atomic field-level edits, and `get_profile_engagement(p_tz)` returning a 28-day timezone-aware assiduity bitmap + all-time distinct-day count. Both RPCs use `security invoker` and rely on the existing `profiles_*` / `pebbles_*` RLS policies (`user_id = auth.uid()`) — matching the pattern in `path_pebbles`.

**Tech Stack:** Postgres 15 (Supabase), `supabase` CLI, generated TypeScript types in `packages/supabase/types/database.ts`.

**Issue:** #450. **Closes:** #450. **Spec:** `docs/superpowers/specs/2026-05-16-ios-profile-redesign-and-settings-design.md` § Issue 1.

**No tests:** `@pbbls/supabase` has no test harness (the `lint` script is a placeholder). Verification is done by calling the new RPCs against the remote Supabase database and inspecting output (Tasks 5–6). Repo policy per project memory: deploy to remote, not local Docker.

**Arkaik:** Skipped here. RPCs without UI consumers are invisible to the product graph. The Arkaik update happens in #451 when the iOS surface lands.

---

### Task 1: Create the feature branch

**Files:** none.

- [ ] **Step 1: Verify clean working tree on `main`**

```bash
git status
git branch --show-current
```

Expected: `nothing to commit, working tree clean` and current branch is `main`.

- [ ] **Step 2: Create and switch to the feature branch**

```bash
git checkout -b feat/450-profile-data-foundations
```

Expected: `Switched to a new branch 'feat/450-profile-data-foundations'`.

---

### Task 2: Generate the migration file

**Files:**
- Create: `packages/supabase/supabase/migrations/<timestamp>_profile_glyph_and_engagement.sql`

- [ ] **Step 1: Generate an empty migration via the CLI**

```bash
npm run db:migration:new --workspace=packages/supabase -- profile_glyph_and_engagement
```

Expected: a new file appears under `packages/supabase/supabase/migrations/` whose name ends in `_profile_glyph_and_engagement.sql`. The file will be empty.

- [ ] **Step 2: Record the generated filename**

```bash
ls -t packages/supabase/supabase/migrations/ | head -1
```

Note the printed filename. Subsequent tasks refer to it as `<MIGRATION_FILE>`.

---

### Task 3: Write the migration SQL

**Files:**
- Modify: `packages/supabase/supabase/migrations/<MIGRATION_FILE>` (created in Task 2)

- [ ] **Step 1: Write the full migration contents**

Replace the empty file with exactly:

```sql
-- ============================================================
-- Migration: profile glyph FK + engagement RPC
-- ------------------------------------------------------------
-- 1. Adds profiles.glyph_id (nullable FK to glyphs).
-- 2. update_profile(p_display_name, p_glyph_id):
--    atomic field-level edit. Null args mean "do not change".
--    Relies on profiles_update RLS (user_id = auth.uid()).
-- 3. get_profile_engagement(p_tz):
--    returns days_practiced (all-time distinct days with a
--    pebble) and assiduity (28-element bool[] for the last 28
--    days), both bucketed in the caller's IANA timezone.
-- ============================================================

-- ------------------------------------------------------------
-- 1. Schema: profiles.glyph_id
-- ------------------------------------------------------------

alter table public.profiles
  add column glyph_id uuid references public.glyphs(id) on delete set null;

create index profiles_glyph_id_idx on public.profiles(glyph_id);

-- ------------------------------------------------------------
-- 2. update_profile
-- ------------------------------------------------------------
-- Returns the updated profile row (or no row if RLS blocked /
-- profile missing — caller treats absence as a not-found error).
-- Both args default to null; null means "leave column unchanged".
-- We intentionally cannot clear glyph_id via this RPC: there is
-- no UX for it. A future "remove glyph" feature should add a
-- dedicated p_clear_glyph boolean rather than re-interpreting null.

create or replace function public.update_profile(
  p_display_name text default null,
  p_glyph_id     uuid default null
)
returns public.profiles
language sql
security invoker
set search_path = public
as $$
  update public.profiles
     set display_name = coalesce(p_display_name, display_name),
         glyph_id     = case
                          when p_glyph_id is not null then p_glyph_id
                          else glyph_id
                        end,
         updated_at   = now()
   where user_id = auth.uid()
   returning *;
$$;

grant execute on function public.update_profile(text, uuid) to authenticated;

-- ------------------------------------------------------------
-- 3. get_profile_engagement
-- ------------------------------------------------------------
-- p_tz: IANA timezone string (e.g. 'Europe/Paris'). The client
-- passes TimeZone.current.identifier on iOS.
--
-- Returns:
--   days_practiced int       — all-time distinct days the user
--                              has created at least one pebble,
--                              bucketed in p_tz.
--   assiduity     boolean[]  — length 28. Index 1 = 27 days ago,
--                              index 28 = today (in p_tz). true
--                              when ≥1 pebble was created on
--                              that local-day.
--
-- security invoker: RLS on public.pebbles already restricts to
-- the caller's rows (user_id = auth.uid()).

create or replace function public.get_profile_engagement(p_tz text)
returns table (
  days_practiced int,
  assiduity      boolean[]
)
language sql
security invoker
stable
set search_path = public
as $$
  with
    today_local as (
      select (now() at time zone p_tz)::date as d
    ),
    window_days as (
      select generate_series(
        (select d from today_local) - interval '27 days',
        (select d from today_local),
        interval '1 day'
      )::date as d
    ),
    active_days as (
      select distinct (p.created_at at time zone p_tz)::date as d
        from public.pebbles p
       where p.user_id = auth.uid()
    ),
    grid as (
      select array_agg((ad.d is not null) order by w.d) as assiduity
        from window_days w
        left join active_days ad using (d)
    ),
    counts as (
      select count(*)::int as days_practiced from active_days
    )
  select counts.days_practiced, grid.assiduity
    from counts, grid;
$$;

grant execute on function public.get_profile_engagement(text) to authenticated;
```

---

### Task 4: Deploy the migration to remote Supabase

**Files:** none modified; deploys the migration created in Task 3 to the linked remote project.

Per project memory (`avoid_docker.md`), we deploy to remote and **never** use local Docker. The CLI must already be linked to the remote project (`npm run db:status --workspace=packages/supabase` will confirm).

- [ ] **Step 1: Confirm the link**

```bash
npm run db:migration:list --workspace=packages/supabase
```

Expected: a table listing local + remote migration versions side-by-side. Your new `<MIGRATION_FILE>` appears in the `Local` column with no entry in `Remote`.

- [ ] **Step 2: Push migrations to remote**

```bash
npm run db:push --workspace=packages/supabase
```

Expected: the CLI prints the list of migrations to apply (your new one), prompts `Do you want to push these migrations to the remote database? [Y/n]`. Confirm `Y`. Final output ends with `Finished supabase db push.` and no error.

- [ ] **Step 3: Re-verify the push**

```bash
npm run db:migration:list --workspace=packages/supabase
```

Expected: your `<MIGRATION_FILE>` now appears in both `Local` and `Remote` columns.

---

### Task 5: Verify `update_profile` against remote

**Files:** none.

We verify by calling the RPC as an authenticated user. Use the Supabase dashboard SQL editor (signed in as a developer/owner of the project) — direct SQL works because the dashboard runs as `postgres` and we can impersonate via `set local request.jwt.claim.sub`. Alternative: any test account on iOS/web that exercises the call.

- [ ] **Step 1: Run a read-back to capture a baseline**

In the Supabase dashboard SQL editor, run (substitute `<YOUR_USER_ID>` with a real `auth.users.id` you own):

```sql
select id, display_name, glyph_id, updated_at
  from public.profiles
 where user_id = '<YOUR_USER_ID>';
```

Expected: one row. Note current `display_name`, `glyph_id`, `updated_at`.

- [ ] **Step 2: Call `update_profile` as that user**

```sql
set local role authenticated;
set local request.jwt.claim.sub = '<YOUR_USER_ID>';

select * from public.update_profile(p_display_name => 'Verification Name');
```

Expected: returns the updated profile row with `display_name = 'Verification Name'`, `glyph_id` unchanged, `updated_at` bumped to roughly `now()`.

- [ ] **Step 3: Verify the null-arg semantics**

```sql
select * from public.update_profile();
```

Expected: returns the same profile row, `display_name` still `'Verification Name'`, no values changed. `updated_at` still bumps (the `set updated_at = now()` always fires) — acceptable, documented as such.

- [ ] **Step 4: Restore the original `display_name`**

```sql
select * from public.update_profile(p_display_name => '<ORIGINAL_NAME>');
```

Expected: row restored to baseline.

- [ ] **Step 5: Verify RLS isolation**

```sql
set local request.jwt.claim.sub = '<DIFFERENT_USER_ID>';

select * from public.update_profile(p_display_name => 'Should Not Apply');
```

Expected: returns no rows (the UPDATE matched zero because RLS scoped the `where user_id = auth.uid()` to the other user; if no profile exists for that user, also zero rows). Confirm the original user's row in step 1 is unchanged.

---

### Task 6: Verify `get_profile_engagement` against remote

**Files:** none.

- [ ] **Step 1: Call the RPC with a known timezone**

In the dashboard SQL editor:

```sql
set local role authenticated;
set local request.jwt.claim.sub = '<YOUR_USER_ID>';

select * from public.get_profile_engagement('Europe/Paris');
```

Expected: one row with two columns. `days_practiced` is a non-negative integer matching your manual count of distinct `(p.created_at at time zone 'Europe/Paris')::date` across that user's pebbles. `assiduity` is an array of exactly 28 booleans.

- [ ] **Step 2: Confirm the array length**

```sql
select array_length((public.get_profile_engagement('Europe/Paris')).assiduity, 1) as len;
```

Expected: `len = 28`.

- [ ] **Step 3: Confirm timezone sensitivity**

```sql
select 'paris' as tz,    (public.get_profile_engagement('Europe/Paris')).*
union all
select 'auckland' as tz, (public.get_profile_engagement('Pacific/Auckland')).*;
```

Expected: both rows return 28-element arrays. If you created a pebble in a window where Paris-local-date and Auckland-local-date disagree (e.g. shortly before/after Auckland midnight), the assiduity arrays differ on those boundary days. This proves the bucketing is timezone-aware.

- [ ] **Step 4: Confirm `today` is included**

The last element (`assiduity[28]`) should be `true` if you have a pebble created today in `p_tz`. If you don't have one, create a pebble through the iOS app or insert manually, then re-run step 1 and confirm `assiduity[28]` flips to `true`.

- [ ] **Step 5: Verify RLS isolation**

```sql
set local request.jwt.claim.sub = '<DIFFERENT_USER_ID>';

select * from public.get_profile_engagement('Europe/Paris');
```

Expected: `days_practiced = 0`, `assiduity` is 28 `false` values (assuming the other user has no pebbles, or only their own data is returned regardless).

---

### Task 7: Regenerate TypeScript types from remote

**Files:**
- Modify: `packages/supabase/types/database.ts`

- [ ] **Step 1: Regenerate from the linked remote project**

```bash
npm run db:types:remote --workspace=packages/supabase
```

Expected: `packages/supabase/types/database.ts` is rewritten in place, no stderr leakage. The script's redirect (`2>/dev/null`) is required — verified in `packages/supabase/CLAUDE.md`.

- [ ] **Step 2: Sanity-check the regenerated file**

```bash
grep -n "glyph_id" packages/supabase/types/database.ts | head -10
grep -n "update_profile\|get_profile_engagement" packages/supabase/types/database.ts | head -10
```

Expected:
- `glyph_id` appears in the `profiles` table's `Row`, `Insert`, and `Update` types.
- Both function names appear (under `Functions`) with correct argument and return-type shapes.

- [ ] **Step 3: Confirm the workspace still type-checks**

```bash
npm run build --workspace=packages/supabase
```

Expected: exits 0 (the `build` script is `tsc --noEmit`).

---

### Task 8: Commit and push

**Files:** none modified.

- [ ] **Step 1: Stage the migration and regenerated types**

```bash
git add packages/supabase/supabase/migrations/*_profile_glyph_and_engagement.sql \
        packages/supabase/types/database.ts
git status
```

Expected: only those two files staged. No unrelated changes.

- [ ] **Step 2: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(db): add profile glyph fk and engagement rpc

Adds profiles.glyph_id (nullable FK), update_profile RPC for atomic
display_name/glyph edits, and get_profile_engagement RPC returning the
28-day timezone-aware assiduity bitmap. Foundations for the profile
redesign (#451) and settings sheet (#452).

Refs #450

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

Expected: one commit on `feat/450-profile-data-foundations` containing the two files.

- [ ] **Step 3: Push the branch**

```bash
git push -u origin feat/450-profile-data-foundations
```

Expected: branch published, returns a `Create a pull request for…` URL.

---

### Task 9: Open the PR

**Files:** none.

Per CLAUDE.md, PRs need labels + milestone confirmed before opening. The species/scope labels here mirror the issue: `feat`, `supabase`, `db`, `api`. Milestone: `M22 · Bounce karma & gamification`.

- [ ] **Step 1: Open the PR**

```bash
gh pr create \
  --title "feat(db): profile glyph fk and engagement rpc" \
  --label "feat,supabase,db,api" \
  --milestone "M22 · Bounce karma & gamification" \
  --body "$(cat <<'EOF'
## Summary

- Adds `profiles.glyph_id` (nullable FK to `glyphs`, on delete set null) + index.
- New RPC `update_profile(p_display_name, p_glyph_id)` — atomic field-level edits, null args mean "leave column unchanged", relies on existing `profiles_update` RLS.
- New RPC `get_profile_engagement(p_tz)` — returns `days_practiced` + a 28-element `assiduity` boolean array bucketed in the caller's IANA timezone.
- Regenerated `packages/supabase/types/database.ts` from the deployed remote schema.

Foundations for the profile screen redesign (#451) and settings sheet (#452). Spec: `docs/superpowers/specs/2026-05-16-ios-profile-redesign-and-settings-design.md` § Issue 1.

## Test plan

- [ ] Migration applied cleanly to remote (`db:push` exit 0, `db:migration:list` shows it in both columns).
- [ ] `update_profile` updates only the caller's row (verified via `set local request.jwt.claim.sub`).
- [ ] `update_profile()` with no args is a no-op on column values (only `updated_at` bumps).
- [ ] `get_profile_engagement(tz)` returns exactly 28 booleans and reflects the caller's pebbles.
- [ ] `get_profile_engagement('Europe/Paris')` vs `'Pacific/Auckland')` differ on boundary days when relevant.
- [ ] `packages/supabase` builds (`tsc --noEmit` exits 0).

Resolves #450
EOF
)"
```

Expected: PR URL printed. Confirm the PR has the requested labels + milestone before merging.

---

## Self-review

**Spec coverage (Issue 1 in the spec):**
- `profiles.glyph_id` migration with FK + `on delete set null` → Task 3 step 1.
- `update_profile(p_display_name, p_glyph_id)` security definer / `security invoker` — **deviation from spec**: spec showed `security definer`, plan uses `security invoker`. Justification embedded in the SQL comment: existing RLS already covers ownership, and matching `path_pebbles` style is cleaner. Spec's open question #5 ("verify by reading a sibling RPC") effectively closed by inspecting `path_pebbles` + `profiles_update` policy. Flag for reviewer.
- `get_profile_engagement(p_tz)` returning `days_practiced` + `assiduity boolean[]` of length 28, timezone-aware → Task 3 step 1; verified Task 6.
- Regenerated types committed → Tasks 7–8.
- "Out of scope: any iOS code, clearing a profile glyph" — both honored (no iOS changes; null `p_glyph_id` documented as "do not change").
- "Done when: migration deployed, RPCs callable with correct types, `database.ts` committed" → Tasks 4, 5–7, 8.

**Placeholder scan:** no TBDs, no "add appropriate handling", every SQL block and command is concrete.

**Type consistency:** `p_display_name` / `p_glyph_id` / `p_tz` parameter names used consistently across SQL, comments, and verification queries. `days_practiced` + `assiduity` column names match between RPC definition and verification queries.

**One deliberate small ambiguity:** `<MIGRATION_FILE>` is a placeholder because the CLI generates the timestamp. The plan tells the engineer to record it in Task 2 step 2 and substitute thereafter. This is fine.
