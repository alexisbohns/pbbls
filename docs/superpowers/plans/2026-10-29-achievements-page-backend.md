# Achievements page — back-end implementation plan (#1305)

> **For agentic workers:** REQUIRED SUB-SKILL: use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to carry out this plan task by task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** serve the whole achievements page and its five sheets from Postgres, so iOS, Android and web only decode, draw and localise.

**Architecture:**
- **Part 1, the model:** extend the existing catalog and unlock ledger with the crossing pebble, the six firsts, the long pebble ladder, the per-card "seen" timestamps and the completions, and add a `celebrate` flag to `check_achievements()`.
- **Part 2, the read:** one `security invoker` RPC, `get_achievements(p_tz)`, computed on read. The year disc is its own function, `achievements_year(p_year, p_tz)`, with a JS oracle in the harness.
- **Part 3, the writes and the public profile:** `mark_achievements_seen`, `pull_ceremony`, and a public profile that no longer shows the private families.

**Tech stack:** Postgres (plpgsql and SQL functions, RLS) on Supabase; Deno harnesses against the linked project; a JS oracle run with `deno test`.

**Spec:** `docs/superpowers/specs/2026-10-29-achievements-page-design.md` (D1–D17). **Stack:** three branches with `gh stack`, one PR each, in this order.

**Ground rules for every task:**
- **Migrations live in `packages/supabase/supabase/migrations/`.** Never edit an applied one; add a new file.
- **Diff before applying.** When a migration re-emits a whole function, diff the old and new bodies before applying. This is a standing rule, because `create or replace` has no merge semantics.
- **Check SQL locally first.** Before any `db push`, check the SQL on the native local Postgres (Homebrew `postgresql@16`, no Docker). See the maintainer's note on validating migrations.
- **Apply** with `npm run db:push --workspace=packages/supabase`. If the auto-mode classifier blocks it, hand the maintainer the command and wait.
- **Regenerate types** after applying, with `npm run db:types:remote --workspace=packages/supabase`, and commit `packages/supabase/types/database.ts`. `db:types` targets the local stack and truncates the file on failure, so don't use it here.
- **Harnesses run** with `set -a; . ./.env; set +a` from the repo root, then the npm script.
- **One linked project, three PRs.** Every PR's CI runs its harnesses against the linked project, so push a part's migrations only once the part before it is merged, and merge in order. Part 3's `get_public_profile` change flips the public counts; part 1 and 2 copies of `verify-public-profile.ts` go red if their CI re-runs after part 3 is pushed.
- **Expected harness output** is always `failed=0`. Pass totals are not asserted here, so a recount is never a false alarm.

---

## File map

| File | Part | Responsibility |
|---|---|---|
| `supabase/migrations/20261030090000_achievements_page_model.sql` | 1 | families, `stone_kind`, seeds, `pebble_id`, views and completions tables, silent-karma constraint, helpers `achievement_stone_match` and `achievement_crossing_pebble` |
| `supabase/migrations/20261030090100_achievements_backfill_crossing.sql` | 1 | backfill `achievement_unlocks.pebble_id` |
| `supabase/migrations/20261030090200_check_achievements_celebrate.sql` | 1 | `check_achievements()` re-emitted: crossing pebble, `celebrate`, completions |
| `supabase/migrations/20261030090300_achievements_purge_admin.sql` | 1 | `purge_account` (+2 tables) and `admin_create_achievement` (refuses seeded families) re-emitted |
| `scripts/verify-achievements.ts` | 1–3 | the new anon harness |
| `scripts/verify-account-purge.ts` | 1 | seeds and zero-row checks for the two tables |
| `scripts/verify-public-profile.ts` | 1, 3 | expected unlock count (part 1), private families off the shelf (part 3) |
| `scripts/oracles/achievements/year.js` | 2 | the year-disc oracle (pure) |
| `scripts/oracles/achievements/year.test.js` | 2 | its unit tests |
| `supabase/migrations/20261030100000_achievements_read.sql` | 2 | `achievement_opt`, `achievement_pebble`, `achievement_sales_count`, `achievement_completion`, `achievement_first_subject`, `achievement_ladder`, `achievements_year`, `get_achievements` |
| `supabase/migrations/20261030110000_achievements_writes.sql` | 3 | `mark_achievements_seen`, `pull_ceremony` |
| `supabase/migrations/20261030110100_public_profile_private_families.sql` | 3 | `get_public_profile` re-emitted with the private families filtered |
| `package.json`, `.github/workflows/supabase.yml`, `packages/supabase/CLAUDE.md` | 1 | the harness script, its CI step and its row in the table |
| `docs/decisions/log.md` | 3 | one entry: achievements are served whole, and the private families never leave the owner |

All paths below are relative to `packages/supabase/` unless they start with `.github/`, `docs/` or `apps/`.

---

# Part 1 — the model (branch `feat/1305-achievements-model`)

### Task 1: The model migration

**Files:**
- Create: `supabase/migrations/20261030090000_achievements_page_model.sql`

- [ ] **Step 1: Write the migration**

```sql
-- Achievements page, the model (#1305, spec 2026-10-29 D3–D10).
--
-- * first_stone: the six firsts (small/medium/large intensity, lowlight/neutral/
--   highlight positiveness), seeded only; stone_kind says which.
-- * set_complete: one catalog row per completion stack, unlocked only by
--   pull_ceremony (part 3), so a completion's karma is an admin-set catalog
--   value and is paid exactly once by the unlocks PK.
-- * the pebble ladder continues every 500 up to 10 000 (D3).
-- * achievement_unlocks.pebble_id: the pebble that earned the row (D4).
-- * achievement_views: "new since you last looked", per card (D10).
-- * achievement_completions: a set made whole, offered later, pulled (D9).
-- * contributions never pay karma (D7): zeroed, then constrained.

-- 1. families ---------------------------------------------------------------------
alter table public.achievements drop constraint achievements_family_check;
alter table public.achievements add constraint achievements_family_check check (family in (
  'pebble_count','emotion_first','domain_first','first_collection',
  'first_glyph','first_soul','glyph_count','glyph_sales',
  'first_moment','first_pebble','first_five',
  'first_stone','set_complete'));

alter table public.achievements add column stone_kind text
  check (stone_kind in ('small','medium','large','lowlight','neutral','highlight'));
alter table public.achievements add constraint achievements_stone_kind_family
  check ((family = 'first_stone') = (stone_kind is not null));

-- 2. seeds ---------------------------------------------------------------------------
insert into public.achievements (id, slug, family, stone_kind, sort_order) values
  (md5('achievements:first-stone-small')::uuid,     'first-stone-small',     'first_stone', 'small',     460),
  (md5('achievements:first-stone-medium')::uuid,    'first-stone-medium',    'first_stone', 'medium',    461),
  (md5('achievements:first-stone-large')::uuid,     'first-stone-large',     'first_stone', 'large',     462),
  (md5('achievements:first-stone-lowlight')::uuid,  'first-stone-lowlight',  'first_stone', 'lowlight',  463),
  (md5('achievements:first-stone-neutral')::uuid,   'first-stone-neutral',   'first_stone', 'neutral',   464),
  (md5('achievements:first-stone-highlight')::uuid, 'first-stone-highlight', 'first_stone', 'highlight', 465);

insert into public.achievements (id, slug, family, sort_order) values
  (md5('achievements:complete-emotions')::uuid,   'complete-emotions',   'set_complete', 700),
  (md5('achievements:complete-domains')::uuid,    'complete-domains',    'set_complete', 710),
  (md5('achievements:complete-six-firsts')::uuid, 'complete-six-firsts', 'set_complete', 720);

insert into public.achievements (id, slug, family, threshold, sort_order)
select md5('achievements:pebble-count-' || t)::uuid, 'pebble-count-' || t, 'pebble_count', t, 170 + t / 500
from generate_series(1500, 10000, 500) t;

-- 3. silent karma (D7) ----------------------------------------------------------------
update public.achievements set karma_reward = 0
 where family in ('emotion_first','domain_first','first_stone');
alter table public.achievements add constraint achievements_silent_karma
  check (family not in ('emotion_first','domain_first','first_stone') or karma_reward = 0);

-- 4. the crossing pebble (D4) ------------------------------------------------------------
alter table public.achievement_unlocks
  add column pebble_id uuid references public.pebbles(id) on delete set null;

-- 5. seen, per card (D10) ---------------------------------------------------------------
create table public.achievement_views (
  user_id uuid not null references auth.users(id) on delete cascade,
  card    text not null check (card in ('explorer','emotions','domains','pebbles','year')),
  seen_at timestamptz not null default now(),
  primary key (user_id, card)
);
alter table public.achievement_views enable row level security;
create policy achievement_views_select_self on public.achievement_views
  for select to authenticated using (user_id = auth.uid());
-- No write policies: mark_achievements_seen (part 3) is the only writer.

-- 6. completions (D9) --------------------------------------------------------------------
create table public.achievement_completions (
  user_id      uuid not null references auth.users(id) on delete cascade,
  stack        text not null check (stack in ('emotions','domains','six_firsts')),
  completed_at timestamptz not null default now(),
  pebble_id    uuid references public.pebbles(id) on delete set null,
  pulled_at    timestamptz,
  primary key (user_id, stack)
);
alter table public.achievement_completions enable row level security;
create policy achievement_completions_select_self on public.achievement_completions
  for select to authenticated using (user_id = auth.uid());
-- No write policies: check_achievements records, pull_ceremony (part 3) pulls.

-- 7. helpers ------------------------------------------------------------------------------
create or replace function public.achievement_stone_match(p_kind text, p_intensity int, p_positiveness int)
returns boolean
language sql
immutable
set search_path = ''
as $$
  select case p_kind
    when 'small'     then p_intensity = 1
    when 'medium'    then p_intensity = 2
    when 'large'     then p_intensity = 3
    when 'lowlight'  then p_positiveness = -1
    when 'neutral'   then p_positiveness = 0
    when 'highlight' then p_positiveness = 1
    else false
  end
$$;

-- The pebble that earned a row (D4). Counting: the threshold-th pebble by
-- completion time. Firsts: the earliest pebble of that kind by happened_at.
-- Called only from definer code (check_achievements, the backfill), so it reads
-- the user's rows regardless of RLS; it is not granted to clients.
create or replace function public.achievement_crossing_pebble(
  p_user uuid, p_family text, p_threshold int, p_emotion uuid, p_domain uuid, p_stone text)
returns uuid
language sql
stable
set search_path = ''
as $$
  select case p_family
    when 'pebble_count' then (
      select p.id from public.pebbles p where p.user_id = p_user
       order by p.created_at, p.id offset greatest(p_threshold, 1) - 1 limit 1)
    when 'first_pebble' then (
      select p.id from public.pebbles p where p.user_id = p_user
       order by p.created_at, p.id limit 1)
    when 'emotion_first' then (
      select p.id from public.pebbles p join public.moments m on m.id = p.moment_id
       where p.user_id = p_user and p.emotion_id = p_emotion
       order by m.happened_at, p.created_at, p.id limit 1)
    when 'domain_first' then (
      select p.id from public.pebbles p join public.moments m on m.id = p.moment_id
        join public.pebble_domains pd on pd.pebble_id = p.id
       where p.user_id = p_user and pd.domain_id = p_domain
       order by m.happened_at, p.created_at, p.id limit 1)
    when 'first_stone' then (
      select p.id from public.pebbles p join public.moments m on m.id = p.moment_id
       where p.user_id = p_user and public.achievement_stone_match(p_stone, p.intensity, p.positiveness)
       order by m.happened_at, p.created_at, p.id limit 1)
  end
$$;

revoke all on function public.achievement_crossing_pebble(uuid, text, int, uuid, uuid, text) from public, anon, authenticated;
grant execute on function public.achievement_stone_match(text, int, int) to authenticated;
```

- [ ] **Step 2: Check it on the local Postgres**

Replay the migration chain on the native local Postgres, then this file, and confirm that it applies:

```sql
select family, count(*) from public.achievements group by family order by family;
```

Expected: `first_stone 6`, `set_complete 3`, `pebble_count 26`, with the 8 from before and 18 new.

- [ ] **Step 3: Commit**

```bash
git add packages/supabase/supabase/migrations/20261030090000_achievements_page_model.sql
git commit -m "feat(db): add the six firsts, the long ladder, seen and completions to achievements"
```

### Task 2: Backfill the crossing pebbles

**Files:**
- Create: `supabase/migrations/20261030090100_achievements_backfill_crossing.sql`

- [ ] **Step 1: Write the migration**

```sql
-- Backfill achievement_unlocks.pebble_id for the rows that existed before
-- 20261030090000, by the rule check_achievements now applies (D4). Runs as the
-- owner, so RLS does not apply. Rows whose pebble no longer exists stay null.
update public.achievement_unlocks u
   set pebble_id = public.achievement_crossing_pebble(
         u.user_id, a.family, a.threshold, a.emotion_id, a.domain_id, a.stone_kind)
  from public.achievements a
 where a.id = u.achievement_id
   and u.pebble_id is null
   and a.family in ('pebble_count','first_pebble','emotion_first','domain_first','first_stone');
```

- [ ] **Step 2: Check it on the local Postgres**

Seed one user with three pebbles, run `check_achievements()` from the old chain, apply both migrations, and check that `pebble-count-1` and `first-pebble` point at the earliest pebble.

```sql
select a.slug, u.pebble_id is not null as has_pebble
from public.achievement_unlocks u
join public.achievements a on a.id = u.achievement_id
order by a.sort_order;
```

Expected: `has_pebble = true` for every pebble-backed family, and `false` for `first-moment`, `first-soul`, `first-collection` and `first-glyph`.

- [ ] **Step 3: Commit**

```bash
git add packages/supabase/supabase/migrations/20261030090100_achievements_backfill_crossing.sql
git commit -m "feat(db): backfill the pebble behind each achievement unlock"
```

### Task 3: `check_achievements()` with the crossing pebble, `celebrate` and completions

**Files:**
- Create: `supabase/migrations/20261030090200_check_achievements_celebrate.sql`

- [ ] **Step 1: Diff the current body**

Run: `sed -n '/create or replace function public.check_achievements/,/^\$\$;/p' supabase/migrations/20261027090100_first_five_achievements.sql`

This is the body being replaced. Its stats CTE, its existing case arms and its karma CTE are carried over unchanged below.

- [ ] **Step 2: Write the migration**

```sql
-- check_achievements(), re-emitted (#1305, spec D4, D7, D8, D9). Body from
-- 20261027090100 with four changes, each marked "#1305":
--   1. qualified carries the family and the crossing pebble; unlocks store it;
--   2. a first_stone arm;
--   3. the return gains celebrate (false for contributions and for a level
--      crossed by a heavy pebble), so OUT columns change: drop, then create;
--   4. after the unlocks, a set made whole records a completion.
drop function public.check_achievements();

create function public.check_achievements()
returns table (slug text, karma_granted integer, celebrate boolean)
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;

  return query
  with stats as (
    select
      (select count(*) from public.pebbles p where p.user_id = v_uid)                       as pebble_count,
      (select count(*) from public.glyphs g where g.user_id = v_uid)                        as glyph_count,
      (select count(*)
         from public.glyph_entitlements e
         join public.glyphs g on g.id = e.glyph_id
        where g.user_id = v_uid and e.user_id <> g.user_id)                                  as glyph_sales,
      exists (select 1 from public.collections c where c.user_id = v_uid)                   as has_collection,
      exists (select 1 from public.souls s where s.user_id = v_uid)                         as has_soul,
      exists (select 1 from public.moments m where m.user_id = v_uid)                       as has_moment
  ),
  qualified as (
    select a.id, a.slug, a.family, a.karma_reward,                                          -- #1305: family
           public.achievement_crossing_pebble(v_uid, a.family, a.threshold,
             a.emotion_id, a.domain_id, a.stone_kind) as pebble_id                         -- #1305
    from public.achievements a, stats st
    where a.is_active
      and case a.family
        when 'pebble_count'     then st.pebble_count >= a.threshold
        when 'emotion_first'    then exists (
          select 1 from public.pebbles p
           where p.user_id = v_uid and p.emotion_id = a.emotion_id)
        when 'domain_first'     then exists (
          select 1 from public.pebble_domains pd
            join public.pebbles p on p.id = pd.pebble_id
           where p.user_id = v_uid and pd.domain_id = a.domain_id)
        when 'first_collection' then st.has_collection
        when 'first_glyph'      then st.glyph_count >= 1
        when 'first_soul'       then st.has_soul
        when 'first_moment'     then st.has_moment
        when 'first_pebble'     then st.pebble_count >= 1
        when 'first_stone'      then exists (                                               -- #1305
          select 1 from public.pebbles p
           where p.user_id = v_uid
             and public.achievement_stone_match(a.stone_kind, p.intensity, p.positiveness))
        when 'glyph_count'      then st.glyph_count >= a.threshold
        when 'glyph_sales'      then st.glyph_sales >= a.threshold
        else false   -- first_five is claimed; set_complete is pulled (pull_ceremony)
      end
  ),
  unlocked as (
    insert into public.achievement_unlocks (user_id, achievement_id, pebble_id)              -- #1305
    select v_uid, q.id, q.pebble_id from qualified q
    on conflict (user_id, achievement_id) do nothing
    returning achievement_id
  ),
  paid as (
    insert into public.karma_events (user_id, delta, reason, type, ref_id)
    select v_uid, q.karma_reward, 'grant', 'credit', q.id
    from unlocked u
    join qualified q on q.id = u.achievement_id
    where q.karma_reward > 0
    returning ref_id, delta
  )
  select q.slug,
         coalesce(p.delta::integer, 0),
         case                                                                               -- #1305 (D7, D8)
           when q.family in ('emotion_first','domain_first','first_stone') then false
           when q.family in ('pebble_count','first_pebble') and exists (
             select 1 from public.pebbles px where px.id = q.pebble_id and px.positiveness = -1) then false
           else true
         end
  from unlocked u
  join qualified q on q.id = u.achievement_id
  left join paid p on p.ref_id = u.achievement_id;

  -- #1305 (D9): a set made whole records its completion once. This runs after
  -- the statement above, so it sees the unlocks it just inserted.
  insert into public.achievement_completions (user_id, stack, completed_at, pebble_id)
  select v_uid, f.stack, now(), s.pebble_id
  from (values ('emotions','emotion_first'), ('domains','domain_first'), ('six_firsts','first_stone')) f(stack, family)
  cross join lateral (
    select
      (select count(*) from public.achievements a where a.family = f.family and a.is_active) as total,
      (select count(*) from public.achievement_unlocks u
         join public.achievements a on a.id = u.achievement_id
        where u.user_id = v_uid and a.family = f.family and a.is_active) as held,
      (select u.pebble_id from public.achievement_unlocks u
         join public.achievements a on a.id = u.achievement_id
         left join public.pebbles p on p.id = u.pebble_id
         left join public.moments m on m.id = p.moment_id
        where u.user_id = v_uid and a.family = f.family
        order by m.happened_at desc nulls last, u.unlocked_at desc
        limit 1) as pebble_id
  ) s
  where s.total > 0 and s.held = s.total
  on conflict (user_id, stack) do nothing;
end;
$$;

revoke all on function public.check_achievements() from public, anon;
grant execute on function public.check_achievements() to authenticated;
```

- [ ] **Step 3: Check it on the local Postgres**

As a seeded user with one medium, highlight pebble, call `select * from public.check_achievements();`.

Expected:
- `first-stone-medium` and `first-stone-highlight` with `celebrate = false`;
- `emotion-first-*` and `domain-first-*` with `false`;
- `pebble-count-1` and `first-pebble` with `true`, since that pebble is bright. Over a heavy first pebble both are `false`: the same pebble is never celebrated twice over, or once.

- [ ] **Step 4: Commit**

```bash
git add packages/supabase/supabase/migrations/20261030090200_check_achievements_celebrate.sql
git commit -m "feat(db): record the crossing pebble, the celebrate flag and completions in check_achievements"
```

### Task 4: `purge_account` and `admin_create_achievement`

**Files:**
- Create: `supabase/migrations/20261030090300_achievements_purge_admin.sql`

- [ ] **Step 1: Copy the current bodies**

Run `sed -n '/create or replace function public.purge_account/,/^\$\$;/p' supabase/migrations/20261027090300_wrap_reveals.sql` and paste the output into the new file.

Then run `sed -n '/create or replace function public.admin_create_achievement/,/^\$\$;/p' supabase/migrations/20261027090100_first_five_achievements.sql` and paste the output below it.

Above each body, add a header saying where it was copied from.

- [ ] **Step 2: Add the two purge deletes** right after the `>>> APPEND new per-user tables from later milestones HERE. <<<` marker, before `achievement_unlocks`:

```sql
  delete from public.achievement_views where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('achievement_views', v_n);

  delete from public.achievement_completions where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('achievement_completions', v_n);
```

- [ ] **Step 3: Refuse the seeded families, and karma on contributions, in `admin_create_achievement`.** Insert this immediately after the `bad_karma` check:

```sql
  -- #1305: first_stone and set_complete are seeded only (their rules key on
  -- stone_kind and on the stack slug, which the admin cannot set).
  if p_family in ('first_stone', 'set_complete') then
    raise exception 'seeded_family';
  end if;
  -- #1305 (D7): contributions never pay; say so before the CHECK does.
  if p_family in ('emotion_first', 'domain_first') and p_karma_reward > 0 then
    raise exception 'silent_family_karma';
  end if;
```

- [ ] **Step 3b: The same guard in `admin_update_achievement`.**
  - Paste its body below the other two: run `sed -n '/create or replace function public.admin_update_achievement/,/^\$\$;/p' supabase/migrations/20260730150000_admin_achievement_management.sql`.
  - Insert this right after its `bad_karma` check:

```sql
  -- #1305 (D7): contributions never pay karma.
  if p_karma_reward > 0 and exists (
       select 1 from public.achievements a
        where a.id = p_achievement_id
          and a.family in ('emotion_first', 'domain_first', 'first_stone')) then
    raise exception 'silent_family_karma';
  end if;
```

- [ ] **Step 4: Diff each body against its source**

Run: `diff <(sed -n '/create or replace function public.purge_account/,/^\$\$;/p' supabase/migrations/20261027090300_wrap_reveals.sql) <(sed -n '/create or replace function public.purge_account/,/^\$\$;/p' supabase/migrations/20261030090300_achievements_purge_admin.sql)`

Expected: only the six added lines.

Run the same `diff` for `admin_create_achievement`, where only the nine added lines should differ, and for `admin_update_achievement`, where only the seven added lines should differ. Its source is `20260730150000_admin_achievement_management.sql`.

- [ ] **Step 5: Commit**

```bash
git add packages/supabase/supabase/migrations/20261030090300_achievements_purge_admin.sql
git commit -m "feat(db): purge the achievement views and completions, and keep seeded families out of the admin"
```

### Task 4b: The admin app knows the two new families

**Files:**
- Modify: `apps/admin/lib/achievements/types.ts`
- Modify: `apps/admin/app/(authed)/achievements/new/_components/NewAchievementForm.tsx` (the family picker, around line 155)

- [ ] **Step 1: Mirror the CHECK constraint.**
  - In `ACHIEVEMENT_FAMILIES`, after `"glyph_sales",`, add `"first_stone",` and `"set_complete",`.
  - Change the comment's "The eleven families" to "The thirteen families".
  - In `FAMILY_LABELS`, add `first_stone: "First of a stone (six firsts)",` and `set_complete: "A completed set",`.
- [ ] **Step 2: Name the seeded families and the new errors.** Add this export below `REFERENCE_FAMILIES`:

```ts
/** Seeded by migration only: the admin edits them but never creates one (#1305). */
export const SEEDED_FAMILIES: readonly AchievementFamily[] = ["first_stone", "set_complete"]
```

In `achievementErrorMessage`, add before `default:`:

```ts
    case "seeded_family":
      return "The six firsts and completed sets are seeded by migration and cannot be created here."
    case "silent_family_karma":
      return "Firsts of an emotion, a domain or a stone never pay karma."
```

- [ ] **Step 3: Keep seeded families out of the create picker.** In `NewAchievementForm.tsx`, import `SEEDED_FAMILIES` and change `{ACHIEVEMENT_FAMILIES.map((f) => (` to `{ACHIEVEMENT_FAMILIES.filter((f) => !SEEDED_FAMILIES.includes(f)).map((f) => (`.
- [ ] **Step 4: Check and commit.** Run `npm run lint --workspace=apps/admin` and `npm run build --workspace=apps/admin`; both must be green. Then:

```bash
git add apps/admin/lib/achievements/types.ts "apps/admin/app/(authed)/achievements/new/_components/NewAchievementForm.tsx"
git commit -m "feat(admin): know the six firsts and completed sets, and refuse karma on contributions"
```

### Task 5: The harness, part 1

**Files:**
- Create: `scripts/verify-achievements.ts`
- Modify: `package.json` (scripts)
- Modify: `.github/workflows/supabase.yml` (add a step after "Home (#1254)")
- Modify: `packages/supabase/CLAUDE.md` (harness table, orphans line)

- [ ] **Step 1: Write the harness.** It covers part 1 only; parts 2 and 3 extend it.

```ts
#!/usr/bin/env -S deno run --allow-env --allow-net --allow-read --allow-write
/**
 * Acceptance test for the achievements page back-end (#1305) — runs against the REMOTE project.
 *
 *   1. The model (part 1):
 *      - the six firsts unlock independently, so three pebbles earn all six;
 *      - every pebble-backed unlock records its crossing pebble;
 *      - contributions and a heavy-crossed level return celebrate = false and pay nothing;
 *      - making the six firsts whole records one completion, once;
 *      - the new tables are owner-scoped and not client-writable.
 *
 * ACHIEVEMENTS_DUMP=1 writes the verbatim payloads to scripts/oracles/achievements/payloads/.
 *
 * Run: set -a; . ./.env; set +a; npm run db:verify:achievements --workspace=packages/supabase
 * Needs NO service-role key: signs up throwaway users (achievements-verify-*@example.test)
 * and deletes them through the real `delete-account` edge function, even on failure.
 */

const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY");
if (!SUPABASE_URL || !ANON_KEY) {
  console.error("SUPABASE_URL and SUPABASE_ANON_KEY must be set");
  Deno.exit(2);
}

import { createClient, type SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

let passed = 0;
let failed = 0;
function check(name: string, ok: boolean, detail?: string) {
  if (ok) { passed += 1; console.log(`✓ ${name}`); }
  else { failed += 1; console.log(`✗ ${name}${detail ? ` — ${detail}` : ""}`); }
}

const DUMP = Deno.env.get("ACHIEVEMENTS_DUMP") === "1";
const ORACLE_DIR = new URL("./oracles/achievements/", import.meta.url);
async function dump(name: string, payload: unknown) {
  if (!DUMP) return;
  await Deno.mkdir(new URL("payloads/", ORACLE_DIR), { recursive: true });
  await Deno.writeTextFile(new URL(`payloads/${name}.json`, ORACLE_DIR), JSON.stringify(payload, null, 2) + "\n");
}

const runId = crypto.randomUUID().slice(0, 8);
const password = `Ach-${crypto.randomUUID()}`;
type TestUser = { client: SupabaseClient; id: string; token: string };
type Unlock = { slug: string; karma_granted: number; celebrate: boolean };

async function signUp(label: string): Promise<TestUser> {
  const client = createClient(SUPABASE_URL!, ANON_KEY!, { auth: { persistSession: false } });
  const email = `achievements-verify-${label}-${runId}@example.test`;
  const { data, error } = await client.auth.signUp({ email, password });
  if (error || !data.session || !data.user) {
    throw new Error(`signUp ${label}: ${error?.message ?? "no session (email confirmations on?)"}`);
  }
  return { client, id: data.user.id, token: data.session.access_token };
}
async function deleteAccount(token: string): Promise<Response> {
  return await fetch(`${SUPABASE_URL}/functions/v1/delete-account`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, apikey: ANON_KEY!, "Content-Type": "application/json" },
  });
}
async function pebble(c: SupabaseClient, name: string, at: string, emotionId: string,
  intensity: number, positiveness: number, domainIds: string[]): Promise<{ momentId: string; pebbleId: string }> {
  const { data: momentId, error: mErr } = await c.rpc("save_moment", { payload: { name, happened_at: at } });
  if (mErr || typeof momentId !== "string") throw new Error(`save_moment ${name}: ${mErr?.message}`);
  const { data: pebbleId, error: pErr } = await c.rpc("complete_pebble", {
    payload: { moment_id: momentId, emotion_id: emotionId, intensity, positiveness, domain_ids: domainIds, soul_ids: [] },
  });
  if (pErr || typeof pebbleId !== "string") throw new Error(`complete_pebble ${name}: ${pErr?.message}`);
  return { momentId, pebbleId };
}
async function unlocks(c: SupabaseClient): Promise<{ slug: string; pebble_id: string | null }[]> {
  const { data, error } = await c.from("achievement_unlocks").select("pebble_id, achievements(slug)");
  if (error) throw new Error(`unlocks: ${error.message}`);
  return ((data ?? []) as unknown as { pebble_id: string | null; achievements: { slug: string } | null }[])
    .map((r) => ({ slug: r.achievements?.slug ?? "?", pebble_id: r.pebble_id }));
}

const anon = createClient(SUPABASE_URL, ANON_KEY, { auth: { persistSession: false } });
const users: TestUser[] = [];

try {
  const alice = await signUp("a"); users.push(alice);
  const a = alice.client;
  const { data: emo } = await a.from("emotions").select("id").order("slug").limit(1).single();
  const { data: dom } = await a.from("domains").select("id").order("slug").limit(1).single();
  if (!emo || !dom) throw new Error("reference data missing");

  // 1. the model ----------------------------------------------------------------------------
  // Three pebbles earn all six firsts: (small, lowlight), (medium, neutral), (large, highlight).
  const p1 = await pebble(a, `heavy small ${runId}`, "2026-03-02T09:00:00Z", emo.id, 1, -1, [dom.id]);
  const first = (await a.rpc("check_achievements")).data as Unlock[];
  const bySlug = Object.fromEntries(first.map((u) => [u.slug, u]));
  check("the first, heavy pebble unlocks pebble-count-1 without a celebration (D8)",
    bySlug["pebble-count-1"]?.celebrate === false, JSON.stringify(bySlug["pebble-count-1"]));
  check("first-stone-small and first-stone-lowlight unlock silently (D7)",
    bySlug["first-stone-small"]?.celebrate === false && bySlug["first-stone-lowlight"]?.celebrate === false,
    JSON.stringify(first));
  check("the emotion and domain firsts are silent and pay nothing",
    first.filter((u) => u.slug.startsWith("emotion-first-") || u.slug.startsWith("domain-first-"))
      .every((u) => u.celebrate === false && u.karma_granted === 0), JSON.stringify(first));
  check("first-pebble over a heavy pebble is quiet too", bySlug["first-pebble"]?.celebrate === false, JSON.stringify(bySlug["first-pebble"]));

  await pebble(a, `medium even ${runId}`, "2026-03-03T09:00:00Z", emo.id, 2, 0, [dom.id]);
  const p3 = await pebble(a, `large bright ${runId}`, "2026-03-04T09:00:00Z", emo.id, 3, 1, [dom.id]);
  const second = (await a.rpc("check_achievements")).data as Unlock[];
  const six = ["small", "medium", "large", "lowlight", "neutral", "highlight"].map((k) => `first-stone-${k}`);
  const held = (await unlocks(a)).map((u) => u.slug);
  check("three pebbles earn all six firsts", six.every((s) => held.includes(s)), JSON.stringify(held));
  check("the second check returns only the four new firsts",
    second.map((u) => u.slug).sort().join(",") === six.filter((s) => !["first-stone-small", "first-stone-lowlight"].includes(s)).sort().join(","),
    JSON.stringify(second));

  const rows = await unlocks(a);
  const pebbleOf = (s: string) => rows.find((r) => r.slug === s)?.pebble_id ?? null;
  check("pebble-count-1 and first-pebble record the first pebble (D4)",
    pebbleOf("pebble-count-1") === p1.pebbleId && pebbleOf("first-pebble") === p1.pebbleId,
    JSON.stringify([pebbleOf("pebble-count-1"), pebbleOf("first-pebble"), p1.pebbleId]));
  check("first-stone-large records the large pebble", pebbleOf("first-stone-large") === p3.pebbleId,
    JSON.stringify([pebbleOf("first-stone-large"), p3.pebbleId]));
  check("first-moment records no pebble", pebbleOf("first-moment") === null, JSON.stringify(pebbleOf("first-moment")));

  const { data: comps } = await a.from("achievement_completions").select("stack, pulled_at");
  check("the six firsts made whole record one completion, not yet pulled",
    (comps ?? []).length === 1 && comps![0].stack === "six_firsts" && comps![0].pulled_at === null, JSON.stringify(comps));
  await a.rpc("check_achievements");
  const { data: comps2 } = await a.from("achievement_completions").select("stack");
  check("a re-check records no second completion", (comps2 ?? []).length === 1, JSON.stringify(comps2));

  const { error: wv } = await a.from("achievement_views").insert({ user_id: alice.id, card: "explorer" });
  const { error: wc } = await a.from("achievement_completions").update({ pulled_at: new Date().toISOString() }).eq("stack", "six_firsts");
  const { data: stillOpen } = await a.from("achievement_completions").select("pulled_at").eq("stack", "six_firsts").single();
  check("a client cannot write achievement_views or achievement_completions",
    wv !== null && (wc !== null || stillOpen?.pulled_at === null), [wv?.message, wc?.message].join(" | "));

  const bob = await signUp("b"); users.push(bob);
  const { data: peek } = await bob.client.from("achievement_completions").select("*");
  check("a user sees only their own completions", (peek ?? []).length === 0, JSON.stringify(peek));
  const anonCheck = await anon.rpc("check_achievements");
  check("check_achievements is refused to anon", !!anonCheck.error, JSON.stringify(anonCheck.data));
  const { data: ladder } = await a.from("achievements").select("threshold, is_active").eq("family", "pebble_count").order("threshold");
  const want = [1, 10, 25, 50, 100, 250, 500, 1000, ...Array.from({ length: 18 }, (_, k) => 1500 + 500 * k)];
  check("the pebble ladder has no gap up to 10 000 and every rung is active (D3)",
    JSON.stringify((ladder ?? []).map((r: { threshold: number }) => r.threshold)) === JSON.stringify(want)
      && (ladder ?? []).every((r: { is_active: boolean }) => r.is_active), JSON.stringify(ladder));
  // D3's other half, "no user within 500 of the top", needs every user's count:
  // it cannot run anon. The maintainer runs it by hand in the SQL editor:
  //   select max(n) from (select count(*) n from pebbles group by user_id) x;   -- must stay below 9 500
  await dump("check-achievements", first);
} catch (err) {
  failed += 1;
  console.error(`✗ aborted: ${err instanceof Error ? err.message : String(err)}`);
} finally {
  for (const u of users) {
    const res = await deleteAccount(u.token).catch(() => null);
    if (!res?.ok) console.error(`cleanup: delete-account returned ${res?.status ?? "no response"}`);
  }
}

console.log(`\nSummary: passed=${passed} failed=${failed}`);
Deno.exit(failed > 0 ? 1 : 0);
```

- [ ] **Step 2: Wire the script.** In `package.json`, after `db:verify:home`, add:

```json
    "db:verify:achievements": "deno run --allow-env --allow-net --allow-read --allow-write scripts/verify-achievements.ts",
```

Then append ` && npm run db:verify:achievements` to the end of the `db:verify` script.

- [ ] **Step 3: Add the CI step** in `.github/workflows/supabase.yml`, right after the "Home (#1254)" step:

```yaml
      # The achievements page (#1305): the six firsts, the crossing pebbles, the
      # celebrate flag and completions (part 1); get_achievements and the year
      # disc against the oracle (part 2); seen, pull_ceremony and the private
      # families off the public profile (part 3).
      - name: Achievements (#1305)
        if: '!cancelled()'
        run: .github/scripts/verify-harness.sh "achievements" db:verify:achievements
```

- [ ] **Step 4: Document it.** In `packages/supabase/CLAUDE.md`, add this row to the harness table after `db:verify:home`:

```markdown
| `npm run db:verify:achievements` | `verify-achievements.ts` | the achievements page (#1305): the six firsts, crossing pebbles, the celebrate flag, completions; `get_achievements` and `achievements_year` against the oracle (`scripts/oracles/achievements/`); seen, `pull_ceremony`, private families off the public profile. `ACHIEVEMENTS_DUMP=1` writes the payloads to `scripts/oracles/achievements/payloads/` | anon |
```

Then add `achievements-verify-` to the list of orphan prefixes.

- [ ] **Step 5: Run it after the push** (the migrations must be applied first; see Task 7)

Run: `set -a; . ./.env; set +a; npm run db:verify:achievements --workspace=packages/supabase`

Expected: `failed=0`.

- [ ] **Step 6: Commit**

```bash
git add packages/supabase/scripts/verify-achievements.ts packages/supabase/package.json .github/workflows/supabase.yml packages/supabase/CLAUDE.md
git commit -m "test(db): prove the achievements model against the linked project"
```

### Task 6: Keep the other harnesses true

**Files:**
- Modify: `scripts/verify-account-purge.ts`
- Modify: `scripts/verify-public-profile.ts`

- [ ] **Step 1: Seed the two tables in the purge harness.** After the seller's `check_achievements` call (around the comment `// Achievement unlocks (M48)`), add:

```ts
  // #1305: one seen row and one completion, through the service role (both
  // tables are write-locked to clients; their RPCs land in part 3).
  const { error: viewErr } = await admin.from("achievement_views").insert({ user_id: sellerId, card: "explorer" });
  if (viewErr) throw new Error(`seed achievement_views: ${viewErr.message}`);
  const { error: compErr } = await admin.from("achievement_completions").insert({ user_id: sellerId, stack: "domains" });
  if (compErr) throw new Error(`seed achievement_completions: ${compErr.message}`);
```

Add these two entries to `expectedPurged`:

```ts
    ["achievement_views", 1], // the seller's seen row (#1305)
    ["achievement_completions", 1], // the seller's completion (#1305)
```

Add these two entries to `sellerScoped`:

```ts
    ["achievement_views", "user_id"],
    ["achievement_completions", "user_id"],
```

- [ ] **Step 2: Update the public profile harness's expected count.** The seed pebbles are medium and bright, so `first-stone-medium` and `first-stone-highlight` unlock too. Change the check `nine badges unlock` to:

```ts
  check(
    `eleven badges unlock (got ${unlockedSlugs.length}: ${unlockedSlugs.join(", ")})`,
    unlockedSlugs.length === 11,
  );
```

Leave the shelf assertions alone; they change in part 3. The shelf still projects every family until then, but the new rows sort at 460+ and fall off the six-slot shelf, so the cap check holds.

Change `shelf.achievements_count === 9` to `=== 11`. In section 4, change `check("the total grows with it", recent.achievements_count === 10)` to `=== 12`. Its eight extra pebbles are medium and bright, so they add no first; `pebble-count-10` takes 11 to 12.

- [ ] **Step 3: Run both** after the push.

Run: `npm run db:verify:public-profile --workspace=packages/supabase`. Expected: no failures.

Then run `npm run db:verify:purge --workspace=packages/supabase` by hand; it needs the service-role key from `.env`. Expected: no failures.

- [ ] **Step 4: Commit**

```bash
git add packages/supabase/scripts/verify-account-purge.ts packages/supabase/scripts/verify-public-profile.ts
git commit -m "test(db): purge the achievement views and completions and count the six firsts"
```

### Task 7: Apply, regenerate types, open the PR

- [ ] **Step 1: Push the migrations.** Run `npm run db:push --workspace=packages/supabase`. If the classifier blocks it, hand the maintainer that exact command.
- [ ] **Step 2: Regenerate types.** Run `npm run db:types:remote --workspace=packages/supabase`, then run `git diff --stat packages/supabase/types/database.ts`. Expected: the new tables and columns, and `check_achievements` returning `celebrate`.
- [ ] **Step 3: Run the harnesses** from Tasks 5 and 6. All must be green.
- [ ] **Step 4: Check the clients still compile against the new return type.** The `celebrate` column is additive.
  - Run `npm run lint --workspace=apps/web` and `npm test --workspace=apps/web`.
  - Android needs no change. `AchievementsService` uses supabase-kt's default serializer (`supabaseJson`), which already sets `ignoreUnknownKeys = true`, and no custom `Json` instance exists. iOS (`Decodable`, explicit CodingKeys) and web (which maps `slug` and `karma_granted`) are additive-safe too.
- [ ] **Step 5: Commit the types and open the PR.**
  - Commit `types/database.ts` with `chore(db): regenerate types for the achievements model`.
  - Run `gh stack` to create the PR, titled `feat(db): the achievements model for the new page`.
  - The body opens with `Part of #1305`; it isn't `Resolves`, because part 3 closes the issue.
  - Labels `feat`, `db`, `supabase`, `no-lab-note`; milestone M65 · Achievements.

---

# Part 2 — the read (branch `feat/1305-achievements-read`, stacked on part 1)

### Task 8: The year oracle, test first

**Files:**
- Create: `scripts/oracles/achievements/year.test.js`
- Create: `scripts/oracles/achievements/year.js`

- [ ] **Step 1: Write the failing tests**

```js
// Run: deno test scripts/oracles/achievements/year.test.js
import { assertEquals } from "https://deno.land/std@0.224.0/assert/mod.ts";
import { yearPayload } from "./year.js";

const days = (month, list, pebble = true, family = "joy", intensity = 2) =>
  list.map((d) => ({ date: `2025-${String(month).padStart(2, "0")}-${String(d).padStart(2, "0")}`, pebble, family, intensity }));

Deno.test("a past month with 14 pebble days and every full week filled is on the rim", () => {
  // June 2025: full weeks start on Mondays 2, 9, 16 and 23 (the 30th's week runs into July).
  const items = days(6, [2, 3, 4, 9, 10, 11, 16, 17, 18, 23, 24, 25, 26, 27]);
  const y = yearPayload(2025, items, "2026-01-10", "2025-06-02");
  const june = y.months[5];
  assertEquals([june.state, june.level, june.moment_days, june.pebble_days, june.full_weeks, june.weeks_filled, june.progress, june.family],
    ["kept", 2, 14, 14, 4, 4, 0, "joy"]);
});

Deno.test("14 days of moments with one empty full week stays at level 0", () => {
  const items = days(6, [2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15], false);
  const june = yearPayload(2025, items, "2026-01-10", "2025-06-02").months[5];
  // weeks of 16 and 23 are empty: 2 of 4 filled.
  assertEquals([june.state, june.level, june.weeks_filled, june.progress], ["quiet", 0, 2, 0.5]);
});

Deno.test("level 1 then progress toward the rim is the smaller share", () => {
  const moments = days(6, [2, 3, 4, 9, 10, 11, 16, 17, 18, 23, 24, 25, 26, 27], false);
  const pebbles = days(6, [2, 9, 16], true, "peace", 1);
  const june = yearPayload(2025, [...moments, ...pebbles], "2026-01-10", "2025-06-02").months[5];
  // 3 pebble days of 14 = 0.214; 3 of 4 full weeks = 0.75 → 0.214
  assertEquals([june.level, june.pebble_days, june.weeks_filled, june.progress, june.family], [1, 3, 3, 0.214, "peace"]);
});

Deno.test("family ties break on intensity, then wheel order", () => {
  const items = [...days(6, [2], true, "sadness", 3), ...days(6, [3], true, "joy", 1)];
  assertEquals(yearPayload(2025, items, "2026-01-10", "2025-06-02").months[5].family, "sadness");
  const even = [...days(6, [2], true, "sadness", 2), ...days(6, [3], true, "joy", 2)];
  assertEquals(yearPayload(2025, even, "2026-01-10", "2025-06-02").months[5].family, "joy");
});

Deno.test("the current year: upcoming months, the listed months and the next step", () => {
  const items = [{ date: "2026-03-02", pebble: true, family: "joy", intensity: 2 }, { date: "2026-11-02", pebble: true, family: "joy", intensity: 2 }];
  const y = yearPayload(2026, items, "2026-11-04", "2026-03-02");
  assertEquals(y.current, true);
  assertEquals(y.months[11].state, "upcoming");
  assertEquals(y.months[0].state, "quiet");
  assertEquals(y.listed, [11, 10, 9, 8, 7, 6, 5, 4, 3]);
  assertEquals(y.next_step, { month: 11, needs: "moments", days_left: 13, reachable: true });
});

Deno.test("a past year before the first moment lists nothing", () => {
  assertEquals(yearPayload(2024, [], "2026-11-04", "2026-03-02").listed, []);
});
```

- [ ] **Step 2: Run them and watch them fail**

Run: `deno test scripts/oracles/achievements/year.test.js`

Expected: FAIL with `Module not found "…/year.js"`.

- [ ] **Step 3: Write the oracle**

```js
// The year disc's reference (#1305, spec D12–D13), ported from the approved
// board's monthStats/monthLevels. The harness compares achievements_year
// against it month by month. Pure: dates are YYYY-MM-DD strings already local
// to the reader's zone; nothing here reads a clock.
export const WHEEL = ["joy", "pride", "anger", "fear", "shame", "sadness", "peace"];

const iso = (y, m, d) => new Date(Date.UTC(y, m - 1, d)).toISOString().slice(0, 10);
const daysIn = (y, m) => new Date(Date.UTC(y, m, 0)).getUTCDate();
const round3 = (x) => Math.round(x * 1000) / 1000;

/** items: [{date, pebble, family, intensity}], one per moment. */
export function monthPayload(year, month, items, today) {
  const start = iso(year, month, 1);
  if (start > today) {
    return { month, state: "upcoming", moment_days: 0, pebble_days: 0, full_weeks: 0, weeks_filled: 0, level: 0, progress: 0 };
  }
  const n = daysIn(year, month);
  const inMonth = items.filter((i) => i.date.slice(0, 7) === start.slice(0, 7) && i.date <= today);
  const mDays = new Set(inMonth.map((i) => Number(i.date.slice(8))));
  const pDays = new Set(inMonth.filter((i) => i.pebble).map((i) => Number(i.date.slice(8))));
  const weeks = [];
  for (let d = 1; d + 6 <= n; d++) {
    if (new Date(Date.UTC(year, month - 1, d)).getUTCDay() === 1 && iso(year, month, d + 6) <= today) weeks.push(d);
  }
  const covered = (set) => weeks.filter((w) => [0, 1, 2, 3, 4, 5, 6].some((k) => set.has(w + k))).length;
  const wm = covered(mDays), wp = covered(pDays), fw = weeks.length;
  const level = pDays.size >= 14 && wp === fw ? 2 : mDays.size >= 14 && wm === fw ? 1 : 0;
  const share = (days, filled) => Math.min(days / 14, fw > 0 ? filled / fw : 1);
  const progress = level === 0 ? round3(share(mDays.size, wm)) : level === 1 ? round3(share(pDays.size, wp)) : 0;
  const out = {
    month, state: pDays.size === 0 ? "quiet" : "kept",
    moment_days: mDays.size, pebble_days: pDays.size, full_weeks: fw,
    weeks_filled: level === 0 ? wm : wp, level, progress,
  };
  const tally = new Map();
  for (const i of inMonth.filter((x) => x.pebble && x.family)) {
    const t = tally.get(i.family) ?? { n: 0, s: 0 };
    tally.set(i.family, { n: t.n + 1, s: t.s + i.intensity });
  }
  const best = [...tally.entries()].sort((a, b) => b[1].n - a[1].n || b[1].s - a[1].s || WHEEL.indexOf(a[0]) - WHEEL.indexOf(b[0]))[0];
  if (best) out.family = best[0];
  return out;
}

/** firstMoment: the user's first moment ever (local YYYY-MM-DD) or null. */
export function yearPayload(year, items, today, firstMoment) {
  const months = Array.from({ length: 12 }, (_, k) => monthPayload(year, k + 1, items, today));
  const current = Number(today.slice(0, 4)) === year;
  const listed = [];
  if (firstMoment) {
    for (let m = 12; m >= 1; m--) {
      const start = iso(year, m, 1);
      if (start <= today && start >= firstMoment.slice(0, 7) + "-01") listed.push(m);
    }
  }
  const out = { year, current, months, listed };
  if (current) {
    const cur = months[Number(today.slice(5, 7)) - 1];
    if (cur.level < 2) {
      const days = cur.level === 0 ? cur.moment_days : cur.pebble_days;
      out.next_step = { month: cur.month, needs: cur.level === 0 ? "moments" : "pebbles",
        days_left: Math.max(0, 14 - days), reachable: cur.weeks_filled === cur.full_weeks };
    }
  }
  return out;
}
```

- [ ] **Step 4: Run the tests and watch them pass**

Run: `deno test scripts/oracles/achievements/year.test.js`

Expected: `ok | 6 passed | 0 failed`.

- [ ] **Step 5: Commit**

```bash
git add packages/supabase/scripts/oracles/achievements/
git commit -m "test(db): add the year disc oracle"
```

### Task 9: The read migration

**Files:**
- Create: `supabase/migrations/20261030100000_achievements_read.sql`

- [ ] **Step 1: Write the helpers and `achievements_year`**

```sql
-- Achievements page, the read (#1305, spec D10–D15, §3.3). Computed on read,
-- security invoker, scoped by RLS and auth.uid(). An absent value omits its key.

create or replace function public.achievement_opt(p_key text, p_value jsonb)
returns jsonb language sql immutable set search_path = '' as $$
  select case when p_value is null then '{}'::jsonb else jsonb_build_object(p_key, p_value) end
$$;

-- The crossing pebble's display snapshot (D15). Null when it no longer exists.
create or replace function public.achievement_pebble(p_pebble_id uuid, p_tz text)
returns jsonb language sql stable security invoker set search_path = '' as $$
  select jsonb_build_object(
    'moment_id', m.id, 'name', m.name,
    'happened_at', to_char(m.happened_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
    'emotion_id', p.emotion_id, 'intensity', p.intensity, 'positiveness', p.positiveness)
  from public.pebbles p join public.moments m on m.id = p.moment_id
  where p.id = p_pebble_id
$$;

-- Glyph sales need other users' entitlements, which RLS hides from the seller:
-- a definer count scoped to the caller, as check_achievements counts them.
create or replace function public.achievement_sales_count()
returns int language sql stable security definer set search_path = '' as $$
  select count(*)::int
  from public.glyph_entitlements e join public.glyphs g on g.id = e.glyph_id
  where g.user_id = (select auth.uid()) and e.user_id <> g.user_id
$$;

-- A completion with its offer (D9): offered from 06:00 local the next day.
create or replace function public.achievement_completion(p_stack text, p_tz text)
returns jsonb language sql stable security invoker set search_path = '' as $$
  select jsonb_build_object(
      'completed_at', to_char(c.completed_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
      'offered_from', to_char(o.offered_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
      'ready', c.pulled_at is null and now() >= o.offered_at)
    || case when c.pulled_at is null then '{}'::jsonb
            else jsonb_build_object('pulled_at', to_char(c.pulled_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')) end
  from public.achievement_completions c
  cross join lateral (select ((((c.completed_at at time zone p_tz)::date + 1) + time '06:00') at time zone p_tz) as offered_at) o
  where c.user_id = (select auth.uid()) and c.stack = p_stack
$$;

-- What a first-five task made, and when (the sheet's row). '{}' when gone.
create or replace function public.achievement_first_subject(p_slug text, p_tz text)
returns jsonb language sql stable security invoker set search_path = '' as $$
  select coalesce((
    select jsonb_build_object('on', to_char(x.created_at at time zone p_tz, 'YYYY-MM-DD'),
                              'subject', jsonb_build_object('kind', x.kind, 'id', x.id, 'name', x.name))
    from (
      select 'moment' as kind, m.id, m.name, m.created_at from public.moments m
       where p_slug = 'first-moment' and m.user_id = (select auth.uid())
      union all
      select 'pebble', m.id, m.name, p.created_at from public.pebbles p join public.moments m on m.id = p.moment_id
       where p_slug = 'first-pebble' and p.user_id = (select auth.uid())
      union all
      select 'collection', c.id, c.name, c.created_at from public.collections c
       where p_slug = 'first-collection' and c.user_id = (select auth.uid())
      union all
      select 'soul', s.id, s.name, s.created_at from public.souls s
       where p_slug = 'first-soul' and s.user_id = (select auth.uid())
      union all
      select 'glyph', g.id, g.name, g.created_at from public.glyphs g
       where p_slug = 'first-glyph' and g.user_id = (select auth.uid())
    ) x
    order by x.created_at, x.id
    limit 1), '{}'::jsonb)
$$;

-- One counting stack: count, levels reached, the next, the ladder (D3).
create or replace function public.achievement_ladder(p_family text, p_count int, p_seen timestamptz, p_tz text)
returns jsonb language sql stable security invoker set search_path = '' as $$
  select jsonb_build_object(
      'count', p_count,
      'levels', coalesce((
        select jsonb_agg(jsonb_build_object(
                 'threshold', a.threshold,
                 'on', to_char(coalesce(p.created_at, u.unlocked_at) at time zone p_tz, 'YYYY-MM-DD'),
                 'new', coalesce(u.unlocked_at > p_seen, false))
               || public.achievement_opt('pebble', public.achievement_pebble(u.pebble_id, p_tz))
               order by a.threshold)
        from public.achievement_unlocks u
        join public.achievements a on a.id = u.achievement_id and a.family = p_family
        left join public.pebbles p on p.id = u.pebble_id
        where u.user_id = (select auth.uid())), '[]'::jsonb),
      'ladder', coalesce((
        select jsonb_agg(a.threshold order by a.threshold) from public.achievements a
        where a.family = p_family and a.is_active and (p_family <> 'pebble_count' or a.threshold <= 1000)), '[]'::jsonb))
    || coalesce((
      select jsonb_build_object('next', jsonb_build_object(
               'threshold', nx.t, 'left', nx.t - p_count,
               'progress', round((p_count - coalesce(pv.t, 0))::numeric / (nx.t - coalesce(pv.t, 0)), 3)))
      from (select min(a.threshold) as t from public.achievements a
             where a.family = p_family and a.is_active and a.threshold > p_count) nx
      cross join (select max(a.threshold) as t from public.achievements a
             where a.family = p_family and a.is_active and a.threshold <= p_count) pv
      where nx.t is not null), '{}'::jsonb)
    || case when p_family = 'pebble_count' then jsonb_build_object('then_every', 500) else '{}'::jsonb end
$$;

-- One year disc (D12). Mirrors scripts/oracles/achievements/year.js exactly.
create or replace function public.achievements_year(p_year int, p_tz text)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  wheel constant text[] := array['joy','pride','anger','fear','shame','sadness','peace'];
  uid uuid := (select auth.uid());
  today date;
  first_day date;
  j_months jsonb;
  j_listed jsonb;
  j_next jsonb;
begin
  if uid is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;
  begin
    today := (now() at time zone p_tz)::date;
  exception when others then
    raise exception 'invalid_tz' using errcode = '22023';
  end;
  if p_year is null or p_year < 1970 or p_year > 9999 then
    raise exception 'invalid_year' using errcode = '22023';
  end if;

  select min((m.happened_at at time zone p_tz)::date) into first_day
  from public.moments m where m.user_id = uid;

  with items as (
    select (m.happened_at at time zone p_tz)::date as d, p.id is not null as is_pebble, ec.slug as family, p.intensity
    from public.moments m
    left join public.pebbles p on p.moment_id = m.id and p.user_id = uid
    left join public.emotions e on e.id = p.emotion_id
    left join public.emotion_categories ec on ec.id = e.category_id
    where m.user_id = uid
      and m.happened_at >= make_date(p_year, 1, 1)::timestamp at time zone p_tz
      and m.happened_at <  make_date(p_year + 1, 1, 1)::timestamp at time zone p_tz
  ),
  mon as (
    select g.mo, make_date(p_year, g.mo, 1) as ms, (make_date(p_year, g.mo, 1) + interval '1 month - 1 day')::date as me
    from generate_series(1, 12) g(mo)
  ),
  dys as (
    select mo.mo,
           count(distinct i.d) filter (where i.d <= today) as mdays,
           count(distinct i.d) filter (where i.d <= today and i.is_pebble) as pdays
    from mon mo left join items i on i.d between mo.ms and mo.me
    group by mo.mo
  ),
  fw as (
    select mo.mo, w.ws::date as ws
    from mon mo cross join lateral generate_series(mo.ms, mo.me, interval '1 day') w(ws)
    where extract(isodow from w.ws) = 1 and w.ws::date + 6 <= mo.me and w.ws::date + 6 <= today
  ),
  wk as (
    select fw.mo, count(*) as full_weeks,
           count(*) filter (where exists (select 1 from items i where i.d between fw.ws and fw.ws + 6)) as wm,
           count(*) filter (where exists (select 1 from items i where i.is_pebble and i.d between fw.ws and fw.ws + 6)) as wp
    from fw group by fw.mo
  ),
  fam as (
    select distinct on (mo.mo) mo.mo, i.family
    from mon mo join items i on i.is_pebble and i.family is not null and i.d between mo.ms and mo.me and i.d <= today
    group by mo.mo, i.family
    order by mo.mo, count(*) desc, sum(i.intensity) desc, array_position(wheel, i.family)
  ),
  lv as (
    select mo.mo, mo.ms, coalesce(d.mdays, 0) as mdays, coalesce(d.pdays, 0) as pdays,
           coalesce(w.full_weeks, 0) as fw, coalesce(w.wm, 0) as wm, coalesce(w.wp, 0) as wp, f.family
    from mon mo left join dys d on d.mo = mo.mo left join wk w on w.mo = mo.mo left join fam f on f.mo = mo.mo
  ),
  lv2 as (
    select *, case when pdays >= 14 and wp = fw then 2 when mdays >= 14 and wm = fw then 1 else 0 end as level from lv
  )
  select jsonb_agg(
           case when ms > today then jsonb_build_object('month', mo, 'state', 'upcoming', 'moment_days', 0, 'pebble_days', 0,
                                                        'full_weeks', 0, 'weeks_filled', 0, 'level', 0, 'progress', 0)
           else jsonb_build_object(
             'month', mo, 'state', case when pdays = 0 then 'quiet' else 'kept' end,
             'moment_days', mdays, 'pebble_days', pdays, 'full_weeks', fw,
             'weeks_filled', case when level = 0 then wm else wp end, 'level', level,
             'progress', case level
                when 0 then round(least(mdays / 14.0, case when fw > 0 then wm::numeric / fw else 1 end), 3)
                when 1 then round(least(pdays / 14.0, case when fw > 0 then wp::numeric / fw else 1 end), 3)
                else 0 end)
             || public.achievement_opt('family', to_jsonb(family))
           end order by mo)
  into j_months from lv2;

  select coalesce(jsonb_agg(g.mo order by g.mo desc), '[]'::jsonb) into j_listed
  from generate_series(1, 12) g(mo)
  where first_day is not null
    and make_date(p_year, g.mo, 1) <= today
    and make_date(p_year, g.mo, 1) >= date_trunc('month', first_day)::date;

  if p_year = extract(year from today)::int then
    select case when (x->>'level')::int = 2 then null else jsonb_build_object(
             'month', (x->>'month')::int,
             'needs', case when (x->>'level')::int = 0 then 'moments' else 'pebbles' end,
             'days_left', greatest(0, 14 - case when (x->>'level')::int = 0 then (x->>'moment_days')::int else (x->>'pebble_days')::int end),
             'reachable', (x->>'weeks_filled')::int = (x->>'full_weeks')::int) end
    into j_next
    from jsonb_array_elements(j_months) x
    where (x->>'month')::int = extract(month from today)::int;
  end if;

  return jsonb_build_object('year', p_year, 'current', p_year = extract(year from today)::int,
                            'months', j_months, 'listed', j_listed)
         || public.achievement_opt('next_step', j_next);
end;
$$;
```

The `to_jsonb(family)` with a null family yields SQL null, so `achievement_opt` omits the key.

- [ ] **Step 2: Append `get_achievements`** to the same file:

```sql
create or replace function public.get_achievements(p_tz text)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  wheel constant text[] := array['joy','pride','anger','fear','shame','sadness','peace'];
  uid uuid := (select auth.uid());
  today date;
  since date;
  seen jsonb;
  n_pebbles int; n_glyphs int;
  n_five int; n_six int; has25 boolean;
  n_emo int; of_emo int; n_dom int; of_dom int;
  rings text[] := '{}';
  j_explorer jsonb; j_emotions jsonb; j_domains jsonb; j_years jsonb;
begin
  if uid is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;
  begin
    today := (now() at time zone p_tz)::date;
  exception when others then
    raise exception 'invalid_tz' using errcode = '22023';
  end;

  select (pr.created_at at time zone p_tz)::date into since from public.profiles pr where pr.user_id = uid;
  select coalesce(jsonb_object_agg(v.card, to_jsonb(v.seen_at)), '{}'::jsonb) into seen
    from public.achievement_views v where v.user_id = uid;
  select count(*) into n_pebbles from public.pebbles p where p.user_id = uid;
  select count(*) into n_glyphs from public.glyphs g where g.user_id = uid;

  select count(*) filter (where a.slug in ('first-moment','first-pebble','first-collection','first-soul','first-glyph')),
         count(*) filter (where a.family = 'first_stone'),
         bool_or(a.slug = 'pebble-count-25'),
         count(*) filter (where a.family = 'emotion_first' and a.emotion_id is not null),
         count(*) filter (where a.family = 'domain_first' and a.domain_id is not null)
    into n_five, n_six, has25, n_emo, n_dom
    from public.achievement_unlocks u join public.achievements a on a.id = u.achievement_id
   where u.user_id = uid;
  select count(*) into of_emo from public.emotions;
  select count(*) into of_dom from public.domains;
  if n_five = 5 then rings := rings || 'first_five'; end if;
  if n_six = 6 then rings := rings || 'six_firsts'; end if;
  if coalesce(has25, false) then rings := rings || 'twenty_five'; end if;
  if n_dom = of_dom and of_dom > 0 then rings := rings || 'domains'; end if;
  if n_emo = of_emo and of_emo > 0 then rings := rings || 'emotions'; end if;

  -- Explorer ------------------------------------------------------------------------
  select jsonb_build_object(
      'first_five', (
        select jsonb_agg(jsonb_build_object('slug', s.slug, 'done', u.user_id is not null,
                   'new', coalesce(u.unlocked_at > (seen->>'explorer')::timestamptz, false))
                 || case when u.user_id is null then '{}'::jsonb else public.achievement_first_subject(s.slug, p_tz) end
               order by s.ord)
        from unnest(array['first-moment','first-pebble','first-collection','first-soul','first-glyph']) with ordinality s(slug, ord)
        left join public.achievements a on a.slug = s.slug
        left join public.achievement_unlocks u on u.achievement_id = a.id and u.user_id = uid),
      'six_firsts', (
        select jsonb_agg(jsonb_build_object('kind', a.stone_kind, 'done', u.user_id is not null,
                   'new', coalesce(u.unlocked_at > (seen->>'explorer')::timestamptz, false))
                 || public.achievement_opt('pebble', public.achievement_pebble(u.pebble_id, p_tz))
               order by a.sort_order)
        from public.achievements a
        left join public.achievement_unlocks u on u.achievement_id = a.id and u.user_id = uid
        where a.family = 'first_stone'),
      'twenty_five', (
        select jsonb_build_object('count', least(n_pebbles, 25), 'done', u.user_id is not null,
                 'new', coalesce(u.unlocked_at > (seen->>'explorer')::timestamptz, false))
               || public.achievement_opt('pebble', public.achievement_pebble(u.pebble_id, p_tz))
        from public.achievements a
        left join public.achievement_unlocks u on u.achievement_id = a.id and u.user_id = uid
        where a.slug = 'pebble-count-25'),
      'rings_done', to_jsonb(rings))
    || public.achievement_opt('first_pebble', (
         select public.achievement_pebble(u.pebble_id, p_tz)
         from public.achievement_unlocks u join public.achievements a on a.id = u.achievement_id
         where u.user_id = uid and a.slug = 'pebble-count-1'))
    || public.achievement_opt('completion', public.achievement_completion('six_firsts', p_tz))
  into j_explorer;

  -- Emotions: 38 seats, wheel order then slug ------------------------------------------
  with seats as (
    select e.id, ec.slug as family,
           (row_number() over (order by array_position(wheel, ec.slug), e.slug) - 1)::int as seat
    from public.emotions e join public.emotion_categories ec on ec.id = e.category_id
  )
  select jsonb_build_object('count', n_emo, 'of', of_emo,
           'seats', coalesce(jsonb_agg(jsonb_build_object('seat', s.seat, 'emotion_id', s.id, 'family', s.family,
                      'met', u.user_id is not null,
                      'new', coalesce(u.unlocked_at > (seen->>'emotions')::timestamptz, false))
                    || public.achievement_opt('pebble', public.achievement_pebble(u.pebble_id, p_tz))
                    order by s.seat), '[]'::jsonb))
         || public.achievement_opt('completion', public.achievement_completion('emotions', p_tz))
  into j_emotions
  from seats s
  left join public.achievements a on a.family = 'emotion_first' and a.emotion_id = s.id
  left join public.achievement_unlocks u on u.achievement_id = a.id and u.user_id = uid;

  -- Domains: coverage tier, the order they came, not yet (D11) --------------------------
  select jsonb_build_object('count', n_dom, 'of', of_dom,
           'coverage', case when n_dom = 0 then 'none'
                            when n_dom < of_dom / 3.0 then 'few'
                            when n_dom < of_dom / 2.0 then 'third'
                            when n_dom < of_dom * 0.75 then 'half'
                            when n_dom < of_dom then 'most'
                            else 'all' end,
           'entered', coalesce((
             select jsonb_agg(jsonb_build_object('domain_id', a.domain_id,
                        'new', coalesce(u.unlocked_at > (seen->>'domains')::timestamptz, false))
                      || public.achievement_opt('pebble', public.achievement_pebble(u.pebble_id, p_tz))
                    order by m.happened_at desc nulls last, u.unlocked_at desc)
             from public.achievement_unlocks u
             join public.achievements a on a.id = u.achievement_id and a.family = 'domain_first' and a.domain_id is not null
             left join public.pebbles p on p.id = u.pebble_id
             left join public.moments m on m.id = p.moment_id
             where u.user_id = uid), '[]'::jsonb),
           'not_yet', coalesce((
             select jsonb_agg(d.id order by d.slug) from public.domains d
             where not exists (
               select 1 from public.achievement_unlocks u join public.achievements a on a.id = u.achievement_id
               where u.user_id = uid and a.family = 'domain_first' and a.domain_id = d.id)), '[]'::jsonb))
         || public.achievement_opt('completion', public.achievement_completion('domains', p_tz))
  into j_domains;

  -- Years: the current one always, past ones with a moment, newest first (D12) ----------
  select coalesce(jsonb_agg(public.achievements_year(ys.y, p_tz) order by ys.y desc), '[]'::jsonb) into j_years
  from (
    select extract(year from today)::int as y
    union
    select distinct extract(year from (m.happened_at at time zone p_tz))::int from public.moments m where m.user_id = uid
  ) ys;

  return public.achievement_opt('since', to_jsonb(to_char(since, 'YYYY-MM-DD')))
      || public.achievement_opt('days_kept', to_jsonb(today - since + 1))
      || jsonb_build_object(
           'explorer', j_explorer,
           'emotions', j_emotions,
           'domains', j_domains,
           'pebbles', public.achievement_ladder('pebble_count', n_pebbles, (seen->>'pebbles')::timestamptz, p_tz),
           'glyphs_carved', public.achievement_ladder('glyph_count', n_glyphs, (seen->>'pebbles')::timestamptz, p_tz),
           'glyph_sales', public.achievement_ladder('glyph_sales', public.achievement_sales_count(), (seen->>'pebbles')::timestamptz, p_tz),
           'years', j_years);
end;
$$;

revoke all on function public.achievement_opt(text, jsonb) from public, anon;
revoke all on function public.achievement_pebble(uuid, text) from public, anon;
revoke all on function public.achievement_sales_count() from public, anon;
revoke all on function public.achievement_completion(text, text) from public, anon;
revoke all on function public.achievement_first_subject(text, text) from public, anon;
revoke all on function public.achievement_ladder(text, int, timestamptz, text) from public, anon;
revoke all on function public.achievements_year(int, text) from public, anon;
revoke all on function public.get_achievements(text) from public, anon;
grant execute on function public.achievement_opt(text, jsonb) to authenticated;
grant execute on function public.achievement_pebble(uuid, text) to authenticated;
grant execute on function public.achievement_sales_count() to authenticated;
grant execute on function public.achievement_completion(text, text) to authenticated;
grant execute on function public.achievement_first_subject(text, text) to authenticated;
grant execute on function public.achievement_ladder(text, int, timestamptz, text) to authenticated;
grant execute on function public.achievements_year(int, text) to authenticated;
grant execute on function public.get_achievements(text) to authenticated;
```

Note: `today - since + 1` is a `date - date` integer, so day one counts as 1.

- [ ] **Step 3: Check it on the local Postgres**

As a seeded user, run `select public.get_achievements('Europe/Paris');` and check:
- the payload has every top-level key;
- `emotions.seats` has 38 entries;
- `years[0].months` has 12.

Then run `select public.achievements_year(2026, 'Mars/Olympus');`. Expected: `invalid_tz`.

- [ ] **Step 4: Commit**

```bash
git add packages/supabase/supabase/migrations/20261030100000_achievements_read.sql
git commit -m "feat(db): serve the achievements page and its year discs on read"
```

### Task 10: The harness, part 2

**Files:**
- Modify: `scripts/verify-achievements.ts`

- [ ] **Step 1: Import the oracle** at the top, after the supabase import:

```ts
import { yearPayload } from "./oracles/achievements/year.js";
```

- [ ] **Step 2: Add section 2** before the `} catch (err) {`:

```ts
  // 2. the read (part 2) ------------------------------------------------------------------
  type Payload = Record<string, any>;
  const tz = "Europe/Paris";
  const got = await a.rpc("get_achievements", { p_tz: tz });
  const pl = got.data as Payload;
  await dump("get-achievements", pl);
  check("get_achievements answers with every card", !got.error
    && ["explorer", "emotions", "domains", "pebbles", "glyphs_carved", "glyph_sales", "years"].every((k) => k in pl),
    got.error?.message ?? Object.keys(pl ?? {}).join(","));
  check("the explorer's centre is the first pebble", pl.explorer.first_pebble?.moment_id === p1.momentId,
    JSON.stringify(pl.explorer.first_pebble));
  check("six_firsts and the first five are listed in order, the six done",
    pl.explorer.six_firsts.map((s: Payload) => s.kind).join(",") === "small,medium,large,lowlight,neutral,highlight"
      && pl.explorer.six_firsts.every((s: Payload) => s.done)
      && pl.explorer.first_five.map((s: Payload) => s.slug).join(",") === "first-moment,first-pebble,first-collection,first-soul,first-glyph",
    JSON.stringify(pl.explorer));
  check("rings_done holds six_firsts, and the completion is not ready before tomorrow 06:00",
    pl.explorer.rings_done.includes("six_firsts") && pl.explorer.completion?.ready === false,
    JSON.stringify([pl.explorer.rings_done, pl.explorer.completion]));
  check("emotions has 38 seats with one met", pl.emotions.seats.length === 38 && pl.emotions.count === 1
    && pl.emotions.seats.filter((s: Payload) => s.met).length === 1, JSON.stringify([pl.emotions.count, pl.emotions.of]));
  check("domains report one entered and the 'few' tier", pl.domains.count === 1 && pl.domains.coverage === "few"
    && pl.domains.entered.length === 1 && pl.domains.not_yet.length === pl.domains.of - 1, JSON.stringify(pl.domains));
  check("pebbles: three kept, the next is 10 with 7 left and progress 0.222",
    pl.pebbles.count === 3 && pl.pebbles.next.threshold === 10 && pl.pebbles.next.left === 7
      && pl.pebbles.next.progress === 0.222 && pl.pebbles.then_every === 500, JSON.stringify(pl.pebbles));
  check("nothing is new before the first look (D10)",
    pl.explorer.six_firsts.every((s: Payload) => s.new === false) && pl.pebbles.levels.every((l: Payload) => l.new === false),
    JSON.stringify(pl.explorer.six_firsts.map((s: Payload) => s.new)));
  check("timestamps are whole-second UTC",
    /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/.test(pl.explorer.first_pebble.happened_at), pl.explorer.first_pebble.happened_at);

  // The year disc against the oracle: a seeded random 2025 for Bob, plus the current year.
  const b = users[1].client;
  const { data: emos } = await b.from("emotions").select("id, emotion_categories(slug)");
  const fams = (emos ?? []) as unknown as { id: string; emotion_categories: { slug: string } }[];
  let seed = parseInt(runId, 16) || 1;
  const rand = () => (seed = (seed * 1103515245 + 12345) % 2147483648) / 2147483648;
  const items: { date: string; pebble: boolean; family: string; intensity: number }[] = [];
  // Four months (March to June) keep the harness to ~40 round-trips while still
  // covering kept, quiet and every level; the rest of 2025 is quiet by construction.
  for (let m = 3; m <= 6; m++) {
    for (let d = 1; d <= 28; d++) {
      if (rand() > 0.35) continue;
      const date = `2025-${String(m).padStart(2, "0")}-${String(d).padStart(2, "0")}`;
      const isPebble = rand() > 0.3;
      const e = fams[Math.floor(rand() * fams.length)];
      const intensity = 1 + Math.floor(rand() * 3);
      const { data: mid, error: mErr } = await b.rpc("save_moment", { payload: { name: `y ${date} ${runId}`, happened_at: `${date}T10:00:00Z` } });
      if (mErr) throw new Error(`save_moment: ${mErr.message}`);
      if (isPebble) {
        const { error: pErr } = await b.rpc("complete_pebble", { payload: { moment_id: mid, emotion_id: e.id, intensity, positiveness: 1, domain_ids: [dom.id], soul_ids: [] } });
        if (pErr) throw new Error(`complete_pebble: ${pErr.message}`);
      }
      items.push({ date, pebble: isPebble, family: e.emotion_categories.slug, intensity });
    }
  }
  const today = new Intl.DateTimeFormat("en-CA", { timeZone: tz }).format(new Date());
  const firstMoment = items.map((i) => i.date).sort()[0] ?? null;
  const y2025 = await b.rpc("achievements_year", { p_year: 2025, p_tz: tz });
  const expected = yearPayload(2025, items, today, firstMoment);
  const mismatches = (expected.months as Payload[]).filter((e, k) => !same(e, (y2025.data as Payload).months[k]));
  check("achievements_year(2025) equals the oracle month by month", !y2025.error && mismatches.length === 0,
    y2025.error?.message ?? JSON.stringify(mismatches.slice(0, 2)) + " vs " + JSON.stringify((y2025.data as Payload).months.slice(0, 2)));
  check("the listed months equal the oracle's", same((y2025.data as Payload).listed, expected.listed),
    JSON.stringify([(y2025.data as Payload).listed, expected.listed]));
  const yNow = await b.rpc("achievements_year", { p_year: Number(today.slice(0, 4)), p_tz: tz });
  const expNow = yearPayload(Number(today.slice(0, 4)), items, today, firstMoment);
  check("the current year equals the oracle, next step included", same(yNow.data, expNow),
    JSON.stringify(yNow.data).slice(0, 300));
  const pb = (await b.rpc("get_achievements", { p_tz: tz })).data as Payload;
  check("get_achievements lists the current year and 2025, newest first",
    pb.years.map((y: Payload) => y.year).join(",") === `${today.slice(0, 4)},2025`, JSON.stringify(pb.years.map((y: Payload) => y.year)));
  await dump("get-achievements-year", pb);
  // Coverage tiers at their boundaries (D11): one pebble per step, on a fresh user.
  const carol = await signUp("c"); users.push(carol);
  const { data: allDomains } = await carol.client.from("domains").select("id").order("slug");
  const domIds = (allDomains ?? []).map((d: { id: string }) => d.id);
  const total = domIds.length;
  const tierAt = async (n: number) => {
    await pebble(carol.client, `cover ${n} ${runId}`, "2026-03-10T09:00:00Z", emo.id, 2, 1, domIds.slice(0, n));
    await carol.client.rpc("check_achievements");
    return ((await carol.client.rpc("get_achievements", { p_tz: tz })).data as Payload).domains.coverage;
  };
  const expectTier = (n: number) => n === 0 ? "none" : n < total / 3 ? "few" : n < total / 2 ? "third" : n < total * 0.75 ? "half" : n < total ? "most" : "all";
  const steps = [Math.ceil(total / 3), Math.ceil(total / 2), Math.ceil(total * 0.75), total];
  const tiers: string[] = [];
  for (const n of steps) tiers.push(await tierAt(n));
  check("coverage crosses third, half, most and all at their boundaries",
    JSON.stringify(tiers) === JSON.stringify(steps.map(expectTier)) && tiers.join(",") === "third,half,most,all",
    JSON.stringify([steps, tiers]));
  const badTz = await b.rpc("get_achievements", { p_tz: "Mars/Olympus" });
  check("get_achievements refuses an unknown zone", !!badTz.error && badTz.error.message.includes("invalid_tz"), badTz.error?.message ?? "accepted");
  const anonRead = await anon.rpc("get_achievements", { p_tz: tz });
  check("get_achievements is refused to anon", !!anonRead.error, JSON.stringify(anonRead.data));
```

The two whole-object comparisons rely on both sides emitting keys in the same order. `jsonb` sorts object keys (by length, then bytewise), so the comparisons above already go through `same`. Add this helper near `check`:

```ts
const canon = (v: unknown): unknown => Array.isArray(v) ? v.map(canon)
  : v && typeof v === "object" ? Object.fromEntries(Object.keys(v as object).sort().map((k) => [k, canon((v as Record<string, unknown>)[k])])) : v;
const same = (x: unknown, y: unknown) => JSON.stringify(canon(x)) === JSON.stringify(canon(y));
```

- [ ] **Step 3: Update the header comment's list**, adding `2. The read (part 2)` with a one-line description of each check above.

- [ ] **Step 4: Run it after pushing the part 2 migration**

Run: `npm run db:verify:achievements --workspace=packages/supabase`. Expected: `failed=0`.

Then run with `ACHIEVEMENTS_DUMP=1` once and commit the payloads under `scripts/oracles/achievements/payloads/`. The client parts replay them.

- [ ] **Step 5: Commit**

```bash
git add packages/supabase/scripts/verify-achievements.ts packages/supabase/scripts/oracles/achievements/payloads/
git commit -m "test(db): prove get_achievements and the year disc against the oracle"
```

### Task 11: Apply, regenerate types, open the PR

- [ ] **Step 1:** Push the migrations, then run `npm run db:types:remote --workspace=packages/supabase`.
- [ ] **Step 2:** Run the harness from Task 10 and `deno test scripts/oracles/achievements/year.test.js`. Both must be green.
- [ ] **Step 3:** Commit `chore(db): regenerate types for get_achievements`.
- [ ] **Step 4:** Open the PR with `gh stack`, titled `feat(db): serve the achievements page from one read`. The body opens with `Part of #1305`; same labels and milestone as part 1.

---

# Part 3 — the writes and the public profile (branch `feat/1305-achievements-writes`, stacked on part 2)

### Task 12: `mark_achievements_seen` and `pull_ceremony`

**Files:**
- Create: `supabase/migrations/20261030110000_achievements_writes.sql`

- [ ] **Step 1: Write the migration**

```sql
-- Achievements page, the writes (#1305, spec D9, D10, §3.4).

create or replace function public.mark_achievements_seen(p_card text)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;
  if p_card is null or p_card not in ('explorer','emotions','domains','pebbles','year') then
    raise exception 'invalid_card' using errcode = '22023';
  end if;
  insert into public.achievement_views (user_id, card, seen_at)
  values (v_uid, p_card, now())
  on conflict (user_id, card) do update set seen_at = excluded.seen_at;
end;
$$;

-- Pulls a completion once it is offered (06:00 local the day after). Pays the
-- stack's set_complete karma exactly once (the unlocks PK feeding the karma
-- insert, as in check_achievements). A second call returns no row.
create or replace function public.pull_ceremony(p_stack text, p_tz text)
returns table (stack text, karma_granted integer)
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_completed timestamptz;
  v_pulled timestamptz;
  v_offer timestamptz;
  v_slug text;
begin
  if v_uid is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;
  v_slug := case p_stack when 'emotions' then 'complete-emotions'
                         when 'domains' then 'complete-domains'
                         when 'six_firsts' then 'complete-six-firsts' end;
  if v_slug is null then
    raise exception 'invalid_stack' using errcode = '22023';
  end if;
  begin
    perform now() at time zone p_tz;
  exception when others then
    raise exception 'invalid_tz' using errcode = '22023';
  end;

  select c.completed_at, c.pulled_at into v_completed, v_pulled
  from public.achievement_completions c where c.user_id = v_uid and c.stack = p_stack
  for update;
  if v_completed is null then
    raise exception 'not_ready' using errcode = 'P0001';
  end if;
  if v_pulled is not null then
    return;   -- already pulled: no row
  end if;
  v_offer := (((v_completed at time zone p_tz)::date + 1) + time '06:00') at time zone p_tz;
  if now() < v_offer then
    raise exception 'not_ready' using errcode = 'P0001';
  end if;

  -- Qualified: an unqualified "stack" would clash with the OUT column of the
  -- same name (plpgsql.variable_conflict = error).
  update public.achievement_completions c set pulled_at = now()
   where c.user_id = v_uid and c.stack = p_stack and c.pulled_at is null;

  return query
  with target as (
    select a.id, a.karma_reward from public.achievements a where a.slug = v_slug and a.is_active
  ),
  unlocked as (
    insert into public.achievement_unlocks (user_id, achievement_id)
    select v_uid, t.id from target t
    on conflict (user_id, achievement_id) do nothing
    returning achievement_id
  ),
  paid as (
    insert into public.karma_events (user_id, delta, reason, type, ref_id)
    select v_uid, t.karma_reward, 'grant', 'credit', t.id
    from unlocked u join target t on t.id = u.achievement_id
    where t.karma_reward > 0
    returning delta
  )
  select p_stack, coalesce((select delta::integer from paid), 0);
end;
$$;

revoke all on function public.mark_achievements_seen(text) from public, anon;
revoke all on function public.pull_ceremony(text, text) from public, anon;
grant execute on function public.mark_achievements_seen(text) to authenticated;
grant execute on function public.pull_ceremony(text, text) to authenticated;
```

- [ ] **Step 2: Commit**

```bash
git add packages/supabase/supabase/migrations/20261030110000_achievements_writes.sql
git commit -m "feat(db): mark achievement cards seen and pull a completed set's ceremony"
```

### Task 13: Keep the private families off the public profile

**Files:**
- Create: `supabase/migrations/20261030110100_public_profile_private_families.sql`

- [ ] **Step 1: Copy the current body**

Run `sed -n '/create or replace function public.get_public_profile/,/^\$\$;/p' supabase/migrations/20261012090300_moments_split_moderation_purge.sql` and paste the output into the new file under a header saying where it came from.

- [ ] **Step 2: Filter the `unlocked` CTE.** Its `where u.user_id = (select user_id from target)` becomes:

```sql
     where u.user_id = (select user_id from target)
       -- #1305 (D16): emotions, domains, the six firsts and set completions
       -- never leave the owner; the shelf and achievements_count both read
       -- this CTE, so both drop them.
       and a.family not in ('emotion_first', 'domain_first', 'first_stone', 'set_complete')
```

- [ ] **Step 3: Diff the body against its source.** Expected: only those four lines.

- [ ] **Step 4: Commit**

```bash
git add packages/supabase/supabase/migrations/20261030110100_public_profile_private_families.sql
git commit -m "fix(db): keep emotion, domain and six-firsts achievements off public profiles"
```

### Task 14: The harnesses, part 3

**Files:**
- Modify: `scripts/verify-achievements.ts`
- Modify: `scripts/verify-public-profile.ts`

- [ ] **Step 1: Add section 3** to `verify-achievements.ts` before the `} catch (err) {`:

```ts
  // 3. the writes (part 3) ---------------------------------------------------------------
  const markBad = await a.rpc("mark_achievements_seen", { p_card: "nope" });
  check("mark_achievements_seen refuses an unknown card", !!markBad.error && markBad.error.message.includes("invalid_card"),
    markBad.error?.message ?? "accepted");
  const mark = await a.rpc("mark_achievements_seen", { p_card: "explorer" });
  check("mark_achievements_seen records the card", !mark.error, mark.error?.message);
  await pebble(a, `after the look ${runId}`, "2026-03-05T09:00:00Z", emo.id, 2, 1, [dom.id]);
  // A new emotion first lands after the look: Explorer's items stay old, the emotion card is unseen (nothing new there either).
  await a.rpc("check_achievements");
  const afterLook = (await a.rpc("get_achievements", { p_tz: tz })).data as Payload;
  check("items recorded before the look are not new", afterLook.explorer.six_firsts.every((s: Payload) => s.new === false),
    JSON.stringify(afterLook.explorer.six_firsts.map((s: Payload) => s.new)));
  const { data: other } = await a.from("emotions").select("id").order("slug", { ascending: false }).limit(1).single();
  await pebble(a, `a new feeling ${runId}`, "2026-03-06T09:00:00Z", other!.id, 2, 1, [dom.id]);
  await a.rpc("check_achievements");   // record it BEFORE the look, so only the third is new
  await a.rpc("mark_achievements_seen", { p_card: "emotions" });
  await new Promise((r) => setTimeout(r, 1100));
  const { data: third } = await a.from("emotions").select("id").order("slug").range(1, 1).single();
  await pebble(a, `a third feeling ${runId}`, "2026-03-07T09:00:00Z", third!.id, 2, 1, [dom.id]);
  await a.rpc("check_achievements");
  const seenPl = (await a.rpc("get_achievements", { p_tz: tz })).data as Payload;
  const newSeats = seenPl.emotions.seats.filter((s: Payload) => s.new).map((s: Payload) => s.emotion_id);
  check("an emotion first recorded after the emotions card was seen is new", newSeats.length === 1 && newSeats[0] === third!.id,
    JSON.stringify(newSeats));

  const early = await a.rpc("pull_ceremony", { p_stack: "six_firsts", p_tz: tz });
  check("pull_ceremony raises not_ready before 06:00 the next day", !!early.error && early.error.message.includes("not_ready"),
    early.error?.message ?? JSON.stringify(early.data));
  const none = await a.rpc("pull_ceremony", { p_stack: "domains", p_tz: tz });
  check("pull_ceremony raises not_ready for a stack never completed", !!none.error && none.error.message.includes("not_ready"),
    none.error?.message ?? JSON.stringify(none.data));
  const badStack = await a.rpc("pull_ceremony", { p_stack: "nope", p_tz: tz });
  check("pull_ceremony refuses an unknown stack", !!badStack.error && badStack.error.message.includes("invalid_stack"),
    badStack.error?.message ?? "accepted");
  // The ready path needs a completion older than its offer, which only the
  // service role can produce: run it when the key is present (by hand), skip in CI.
  const SERVICE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (SERVICE) {
    const admin = createClient(SUPABASE_URL!, SERVICE, { auth: { persistSession: false } });
    await admin.from("achievement_completions").update({ completed_at: "2026-01-01T00:00:00Z" })
      .eq("user_id", alice.id).eq("stack", "six_firsts");
    const [r1, r2] = await Promise.all([
      a.rpc("pull_ceremony", { p_stack: "six_firsts", p_tz: tz }), a.rpc("pull_ceremony", { p_stack: "six_firsts", p_tz: tz })]);
    const pulledRows = [...((r1.data ?? []) as unknown[]), ...((r2.data ?? []) as unknown[])];
    check("two concurrent pulls of a ready ceremony return one row", !r1.error && !r2.error && pulledRows.length === 1,
      [r1.error?.message, r2.error?.message, JSON.stringify(pulledRows)].filter(Boolean).join(" | "));
    const after = (await a.rpc("get_achievements", { p_tz: tz })).data as Payload;
    check("the pulled completion reports pulled_at and is no longer ready",
      typeof after.explorer.completion?.pulled_at === "string" && after.explorer.completion.ready === false,
      JSON.stringify(after.explorer.completion));
  } else {
    console.log("· pull_ceremony's ready path skipped (no SUPABASE_SERVICE_ROLE_KEY)");
  }
  const anonPull = await anon.rpc("pull_ceremony", { p_stack: "six_firsts", p_tz: tz });
  check("pull_ceremony is refused to anon", !!anonPull.error, JSON.stringify(anonPull.data));
  await dump("get-achievements-seen", seenPl);
```

- [ ] **Step 2: Make the public profile harness assert the private families are gone.** In `verify-public-profile.ts`, replace the four checks after `const slugs = …` with the block below.

The unlocks are: `pebble-count-1`, two `emotion-first-*`, one `domain-first-*`, `first-collection`, `first-glyph`, `first-soul`, `first-moment`, `first-pebble`, `first-stone-medium` and `first-stone-highlight`. That makes 6 public and 5 private.

```ts
  check(
    "achievements_count counts only the public families (6 of the 11 unlocked)",
    shelf.achievements_count === 6,
    `got ${shelf.achievements_count}`,
  );
  check(
    `the six public badges fill the shelf (got ${shelf.achievements.length})`,
    shelf.achievements.length === 6,
  );
  check(
    "emotion, domain and six-firsts badges never reach the public profile (#1305 D16)",
    shelf.achievements.every((b) => !["emotion_first", "domain_first", "first_stone", "set_complete"].includes(b.family)),
    slugs.join(", "),
  );
  check(
    "locked badges are never projected",
    slugs.every((s) => unlockedSlugs.includes(s)),
    slugs.join(", "),
  );
```

Then delete the old check `the reference ids the clients localize by are projected`; it asserted the leak this part removes.

In section 4 (`Recency wins over catalog order`), change `recent.achievements_count === 12` (set in part 1) to `=== 7`: the six public badges plus `pebble-count-10`.

- [ ] **Step 3: Run both harnesses** after pushing the part 3 migrations.

Expected: `db:verify:achievements` reports `failed=0`. It prints one skip line in CI, where there is no service key, and runs two more checks when run by hand with the key. `db:verify:public-profile` must show no failures.

- [ ] **Step 4: Commit**

```bash
git add packages/supabase/scripts/verify-achievements.ts packages/supabase/scripts/verify-public-profile.ts
git commit -m "test(db): prove seen, pull_ceremony and the private families off public profiles"
```

### Task 15: Decision log, types, PR

**Files:**
- Modify: `docs/decisions/log.md` (append)

- [ ] **Step 1: Append the decision**

```markdown

## 2026-10-30 — The achievements page is served whole; private families never leave the owner (#1305)

- **Status:** taken
- **Scope:** db (ios, android, web render)
- **Context:**
  - The new achievements page (spec 2026-10-29) shows levels, firsts, completions, "new" marks and a year disc on three clients.
  - The public profile projected every unlocked family, including emotion and domain firsts.
- **Decision:**
  - **One read:** `get_achievements(p_tz)` (security invoker, computed on read) serves the page and its five sheets, with a snapshot of each crossing pebble.
  - **The year disc:** `achievements_year` computes it and matches the committed JS oracle (`packages/supabase/scripts/oracles/achievements/`).
  - **Celebration:** `check_achievements()` returns `celebrate`, so contributions (emotion, domain, six firsts) and levels crossed by a heavy pebble are recorded silently and pay no karma (the catalog constrains it).
  - **Completions** are offered from 06:00 local the next day and pulled with `pull_ceremony`.
  - **Privacy:** `get_public_profile` drops `emotion_first`, `domain_first`, `first_stone` and `set_complete`.
- **Why:**
  - Clients compute nothing, so the three surfaces cannot disagree.
  - A first sadness or a heavy stone must never be celebrated.
  - Emotions and domains are disclosures.
- **Consequences:**
  - Client unit tests replay `scripts/oracles/achievements/payloads/`.
  - The pebble ladder is seeded to 10 000 (extend it before anyone nears it).
  - Admin karma on contributions now fails the `achievements_silent_karma` check.
- **Supersedes / Superseded-by:** narrows achievements design D11 (2026-07-29) for the public shelf.
- **Refs:** #1305, `docs/superpowers/specs/2026-10-29-achievements-page-design.md`.
```

- [ ] **Step 2: Push and regenerate.** Push the migrations, run `npm run db:types:remote --workspace=packages/supabase`, and commit `chore(db): regenerate types for the achievements writes`.
- [ ] **Step 3: Open the PR** with `gh stack`, titled `feat(db): mark achievements seen, pull ceremonies, keep private families private`. The body opens with `Resolves #1305`; same labels and milestone.
- [ ] **Step 4: On the Arkaik map**, move `DM-achievement` and `API-get-achievements` to `development` (`update_node`), following the `arkaik` skill's hosted-map rules.

---

## Self-review notes

- **Spec coverage:**
  - D1 and D2 → `get_achievements` (`since`, `days_kept`).
  - D3 → the ladder seed and `achievement_ladder`.
  - D4 → `achievement_crossing_pebble`, the backfill and the check.
  - D5 → `first_stone`.
  - D6 → explorer `first_pebble`.
  - D7 and D8 → `celebrate` and the karma constraint.
  - D9 → completions and `pull_ceremony`.
  - D10 → views and `mark_achievements_seen`.
  - D11 → coverage and entered order.
  - D12 and D13 → `achievements_year` and the oracle.
  - D14 → `get_achievements`.
  - D15 → `achievement_pebble`.
  - D16 → Task 13.
  - D17 → whole-second UTC and omitted keys.
  - Purge → Tasks 4 and 6.
  - The admin app → Task 4b.
  - D3's "no user within 500 of the top" → a manual SQL query (it needs a cross-user read, so it cannot run anon).
- **Reviewed** by a Fable 5.1 critic on 2026-10-29; its twelve findings are folded in. The main ones:
  - the `stack` column clash in `pull_ceremony`;
  - the seen sequence;
  - the missed public-profile count;
  - admin karma;
  - a heavy first pebble going quiet.
- **Spec deltas, folded into the spec (PR #1309):**
  - Explorer's "ready" becomes `explorer.completion`, the same shape as on emotions and domains.
  - `pull_ceremony` takes `p_tz`.
  - `next_step` carries `reachable` instead of `weeks_left`.
  - Completion karma comes from three `set_complete` catalog rows.
