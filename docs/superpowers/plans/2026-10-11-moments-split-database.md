# Moments and pebbles split — Part 1, the database — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split every recorded moment from the pebble it becomes: a `moments` table owns date, name, description, photos and moderation; `pebbles` becomes a per-user completion that exists only when emotion, valence and a domain are set; a pebble's glyph is derived from its collection or domain; server drafts and the render cache are retired; web keeps working through a database-side compatibility layer.

**Architecture:** Five migrations in one push, each a transaction, ordered schema → writers → readers → moderation/purge/analytics → compat. Every existing pebble id becomes its moment id, so snaps, share links and reports keep their ids. Two new RPCs (`save_moment`, `complete_pebble`) are the write contract; the old `create_pebble`/`update_pebble` survive as wrappers called by the two compose edge functions, which lose their render step. A new Deno harness proves the contract against the linked project.

**Tech Stack:** Postgres 15 (Supabase), plpgsql, Supabase CLI (`db push`, `functions deploy`, `gen types --linked`), Deno harnesses, Homebrew `postgresql@16` for the local rehearsal.

**Spec:** `docs/superpowers/specs/2026-10-11-moments-and-pebbles-split-design.md` · **Issue:** the backend issue of the milestone (create it first: `[Feat] Split moments from pebbles in the database`, labels `feat` `db` `supabase`; its number is `1046` below)

---

## Ground rules for every task

- Work from a worktree created from updated `origin/main` (`git fetch && git worktree add .claude/worktrees/moments-db -b feat/1046-db-moments-schema origin/main`). Copy `packages/supabase/supabase/.temp` from the main checkout or `db push` cannot find the project.
- Supabase commands: `cd packages/supabase && npx supabase <cmd>`. Credentials for harnesses: `set -a; . ./.env; set +a` from the repo root.
- **No Docker.** Migrations are rehearsed on the native Postgres (Task 1) and then pushed to the linked project. Types come from `npm run db:types:remote --workspace=packages/supabase`, never `db:types`.
- Migration file names: `packages/supabase/supabase/migrations/2026101209NN00_<slug>.sql`, versions strictly increasing and unique (CI checks).
- **The five migrations go live in one `db push`** (Task 9). Until Parts 2 and 3 ship, installed iOS/Android builds read `pebbles` directly and will break on the dropped columns. This is accepted pre-launch and stated in the PR body.
- Conventional commits, lowercase, no period, trailer `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- Never cite a Kritik finding id in a commit or PR body.

## Delivery: a five-part stack (`gh stack`)

| Part | Branch | PR title | Tasks |
|---|---|---|---|
| 1 | `feat/1046-db-moments-schema` | `feat(db): add moments and move the moment half of pebbles into them` | 1–3 |
| 2 | `feat/1046-db-moments-writers` | `feat(db): save_moment and complete_pebble replace create_pebble` | 4 |
| 3 | `feat/1046-db-moments-readers` | `feat(db): read moments with the caller's pebble and a derived glyph` | 5–6 |
| 4 | `feat/1046-db-moments-compat` | `feat(db): keep the web contract alive over the moments schema` | 7–8 |
| 5 | `feat/1046-db-moments-harnesses` | `test(db): prove the moments contract and retire the drafts harness` | 9–12 |

Each part lints (`npm run lint --workspace=packages/supabase`) and typechecks (`npm run build --workspace=packages/supabase`) alone. The push to the linked project happens once, from Part 5, after the whole stack reviews green on the rehearsal cluster. Read the `gh-stack` skill before creating the stack.

---

## Task 1: Rehearsal cluster with production rows

**Files:**
- Create: `packages/supabase/scripts/rehearsal/README.md`
- Create: `packages/supabase/scripts/rehearsal/bootstrap.sql`
- Create: `packages/supabase/scripts/rehearsal/run.sh`

The full chain cannot replay on a bare Postgres (it needs `auth`, `storage`, Supabase roles). The rehearsal instead restores a dump of the **current** linked schema and data, stubs the three things the new migrations touch outside `public`, then applies only the new migration files. That is the proof that the backfill and the column drops succeed over real rows.

- [ ] **Step 1: Write the bootstrap stubs**

```sql
-- packages/supabase/scripts/rehearsal/bootstrap.sql
-- Roles the dump's grants and policies name.
do $$ begin
  if not exists (select 1 from pg_roles where rolname = 'anon') then create role anon nologin; end if;
  if not exists (select 1 from pg_roles where rolname = 'authenticated') then create role authenticated nologin; end if;
  if not exists (select 1 from pg_roles where rolname = 'service_role') then create role service_role nologin bypassrls; end if;
  if not exists (select 1 from pg_roles where rolname = 'supabase_auth_admin') then create role supabase_auth_admin nologin; end if;
  if not exists (select 1 from pg_roles where rolname = 'supabase_storage_admin') then create role supabase_storage_admin nologin; end if;
end $$;
create extension if not exists pgcrypto;
create extension if not exists "uuid-ossp";
-- auth.uid() reads a GUC so a test can impersonate a user: set request.jwt.claim.sub = '<uuid>'
create schema if not exists auth;
create or replace function auth.uid() returns uuid language sql stable as $$
  select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid
$$;
create or replace function auth.role() returns text language sql stable as $$
  select coalesce(nullif(current_setting('request.jwt.claim.role', true), ''), 'anon')
$$;
-- storage.foldername, used by list_orphan_snap_files
create schema if not exists storage;
create or replace function storage.foldername(name text) returns text[] language plpgsql immutable as $$
declare parts text[];
begin
  select string_to_array(name, '/') into parts;
  return parts[1 : array_length(parts, 1) - 1];
end $$;
```

- [ ] **Step 2: Write the runner**

```bash
#!/usr/bin/env bash
# packages/supabase/scripts/rehearsal/run.sh
# Usage: SUPABASE_DB_URL=postgresql://postgres:<pw>@db.<ref>.supabase.co:5432/postgres \
#        scripts/rehearsal/run.sh <first-new-migration-version>
# Restores the linked project's public schema + data into a throwaway native
# cluster on 127.0.0.1:55432, then applies every migration >= $1 in order.
set -euo pipefail
PGBIN=/opt/homebrew/opt/postgresql@16/bin
SCRATCH="${SCRATCH:-/tmp/pbbls-rehearsal}"
FROM="$1"
HERE="$(cd "$(dirname "$0")/../.." && pwd)"

rm -rf "$SCRATCH"; mkdir -p "$SCRATCH"
"$PGBIN/initdb" -D "$SCRATCH/pgdata" -U postgres --auth=trust >/dev/null
"$PGBIN/pg_ctl" -D "$SCRATCH/pgdata" -o "-p 55432 -k /tmp -c listen_addresses=127.0.0.1" -l "$SCRATCH/pg.log" start >/dev/null
trap '"$PGBIN/pg_ctl" -D "$SCRATCH/pgdata" stop >/dev/null' EXIT
PSQL="$PGBIN/psql -h 127.0.0.1 -p 55432 -U postgres -v ON_ERROR_STOP=1 -q"

$PSQL -f "$HERE/scripts/rehearsal/bootstrap.sql"
# auth.users and storage.objects are real tables in the dump; everything else in
# those schemas is skipped by --schema=public plus the two explicit tables.
"$PGBIN/pg_dump" "$SUPABASE_DB_URL" --no-owner --no-privileges --no-publications --no-subscriptions \
  --schema=public --table=auth.users --table=storage.objects --table=storage.buckets \
  > "$SCRATCH/dump.sql"
# The dump re-creates auth.uid() if it was dumped with auth.users' defaults; keep ours.
sed -i '' '/CREATE FUNCTION auth.uid/,/\$\$;/d' "$SCRATCH/dump.sql"
$PSQL -f "$SCRATCH/dump.sql" 2> "$SCRATCH/restore.err" || { echo "restore failed, see $SCRATCH/restore.err"; exit 1; }

for f in $(ls "$HERE/supabase/migrations" | sort); do
  v="${f%%_*}"
  if [[ "$v" -ge "$FROM" ]]; then
    echo "== applying $f"
    $PSQL -1 -f "$HERE/supabase/migrations/$f"
  fi
done
echo "== rehearsal ok. Cluster stays up until this shell exits; psql: $PSQL"
read -r -p "press enter to stop the cluster"
```

- [ ] **Step 3: Write the README**

```markdown
# Migration rehearsal (no Docker)

`run.sh <version>` restores the linked project's `public` schema and rows plus
`auth.users` and `storage.objects` into a throwaway native cluster, then applies
every migration whose version is >= `<version>`. Run it before any `db push`
that drops or backfills columns. The DB URL is in the dashboard (Project
settings → Database → connection string, direct, not pooled).

Known gaps: `storage.foldername` and `auth.uid()` are stubs; policies are
restored but nothing runs as `authenticated` unless you
`set role authenticated; set request.jwt.claim.sub = '<uuid>'`.
```

- [ ] **Step 4: Run it against the unchanged migrations to prove the harness itself**

Run: `chmod +x packages/supabase/scripts/rehearsal/run.sh && SUPABASE_DB_URL=... packages/supabase/scripts/rehearsal/run.sh 20261007140000`
Expected: the restore succeeds and the one existing migration >= that version re-applies without error (it is idempotent `create or replace`), or fails only on `drop policy "pebbles_select"` because the policy exists — either is fine; what matters is the restore line count in `$SCRATCH/restore.err` is empty.

- [ ] **Step 5: Commit**

```bash
git add packages/supabase/scripts/rehearsal
git commit -m "chore(db): add a native postgres rehearsal for destructive migrations"
```

---

## Task 2: Migration 1 — schema and backfill

**Files:**
- Create: `packages/supabase/supabase/migrations/20261012090000_moments_split_schema.sql`

- [ ] **Step 1: Write the migration**

```sql
-- =============================================================================
-- Moments and pebbles split — schema (spec 2026-10-11, §2)
--
-- A moment is what happened (date, name, description, photos, moderation). A
-- pebble is what it meant to one person and exists only when complete. Every
-- existing pebble id becomes its moment id, so snap paths, share links and
-- report targets keep their ids.
--
-- Order matters: dependents of the dropped pebble columns (views, the
-- moderation trigger, pebbles_select) go first, then the columns.
-- =============================================================================

-- 1. moments ------------------------------------------------------------------
create table public.moments (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users(id),
  name        text,
  description text,
  happened_at timestamptz not null default now(),
  hidden_at   timestamptz,
  hidden_by   uuid references auth.users(id) on delete set null,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);
comment on table public.moments is
  'What happened: date, name, description, snaps. Shared later via participants. A pebble is one user''s completion of a moment.';
create index moments_user_happened_idx on public.moments (user_id, happened_at desc);
create index moments_hidden on public.moments (id) where hidden_at is not null;
create trigger moments_updated_at
  before update on public.moments
  for each row execute function public.set_updated_at();

-- 2. backfill: one moment per pebble, same id ----------------------------------
insert into public.moments (id, user_id, name, description, happened_at, hidden_at, hidden_by, created_at, updated_at)
select id, user_id, name, description, happened_at, hidden_at, hidden_by, created_at, updated_at
from public.pebbles;

alter table public.pebbles
  add column moment_id uuid references public.moments(id) on delete cascade;
update public.pebbles set moment_id = id;
alter table public.pebbles alter column moment_id set not null;
create unique index pebbles_moment_user_key on public.pebbles (moment_id, user_id);

-- 3. snaps hang off the moment --------------------------------------------------
alter table public.snaps rename column pebble_id to moment_id;
alter table public.snaps drop constraint snaps_pebble_id_fkey;
alter table public.snaps
  add constraint snaps_moment_id_fkey foreign key (moment_id) references public.moments(id) on delete cascade;
create index if not exists snaps_moment_id_idx on public.snaps (moment_id);

-- 4. server drafts become moments -----------------------------------------------
do $$
declare
  d      record;
  s      record;
  v_id   uuid;
  v_when timestamptz;
begin
  for d in select * from public.pebble_drafts
           where nullif(btrim(coalesce(payload->>'name', '')), '') is not null
  loop
    begin
      v_when := date_trunc('second', (d.payload->>'happened_at')::timestamptz);
    exception when others then
      v_when := null;
    end;
    insert into public.moments (user_id, name, description, happened_at, created_at, updated_at)
    values (d.user_id, btrim(d.payload->>'name'), nullif(d.payload->>'description', ''),
            coalesce(v_when, d.created_at), d.created_at, d.updated_at)
    returning id into v_id;

    for s in select value from jsonb_array_elements(
               case when jsonb_typeof(d.payload->'snaps') = 'array' then d.payload->'snaps' else '[]'::jsonb end)
    loop
      if jsonb_typeof(s.value) = 'object' and nullif(s.value->>'storage_path', '') is not null then
        insert into public.snaps (id, moment_id, user_id, storage_path, sort_order)
        values (coalesce(nullif(s.value->>'id', '')::uuid, gen_random_uuid()), v_id, d.user_id,
                s.value->>'storage_path', coalesce((s.value->>'sort_order')::smallint, 0))
        on conflict (id) do nothing;
      end if;
    end loop;
  end loop;
end $$;
drop table public.pebble_drafts;

-- 5. dependents of the columns about to go ---------------------------------------
drop trigger pebbles_moderation_guard on public.pebbles;
drop policy "pebbles_select" on public.pebbles;
drop view public.v_pebbles_full;
drop view public.v_analytics_pebble_volume_daily;
drop view public.v_analytics_pebble_enrichment_daily;
-- v_bounce keeps its column list, so it is replaced in place to read moments.
create or replace view public.v_bounce as
select
  u.id as user_id,
  coalesce(stats.active_days, 0) as active_days,
  case
    when coalesce(stats.active_days, 0) = 0  then 0
    when stats.active_days between 1  and 5  then 1
    when stats.active_days between 6  and 9  then 2
    when stats.active_days between 10 and 13 then 3
    when stats.active_days between 14 and 17 then 4
    when stats.active_days between 18 and 20 then 5
    when stats.active_days between 21 and 24 then 6
    else 7
  end::smallint as bounce_level
from auth.users u
left join lateral (
  select count(distinct date(m.happened_at)) as active_days
  from public.pebbles pb
  join public.moments m on m.id = pb.moment_id
  where pb.user_id = u.id
    and m.happened_at >= now() - interval '28 days'
) stats on true
where u.id = auth.uid();

-- 6. the columns ------------------------------------------------------------------
alter table public.pebbles
  drop column name,
  drop column description,
  drop column happened_at,
  drop column glyph_id,
  drop column render_svg,
  drop column render_version,
  drop column hidden_at,
  drop column hidden_by;

-- 7. collections carry a glyph ------------------------------------------------------
alter table public.collections
  add column glyph_id uuid references public.glyphs(id);
create or replace function public.enforce_collection_glyph_usable()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.glyph_id is null then return new; end if;
  if tg_op = 'UPDATE' and new.glyph_id is not distinct from old.glyph_id then return new; end if;
  if not public.can_use_glyph(new.glyph_id, new.user_id) then
    raise exception 'Glyph not usable by user: %', new.glyph_id using errcode = '42501';
  end if;
  return new;
end;
$$;
create trigger collections_glyph_usable
  before insert or update of glyph_id on public.collections
  for each row execute function public.enforce_collection_glyph_usable();

-- 8. reports target moments ------------------------------------------------------------
update public.content_reports set target_kind = 'moment' where target_kind = 'pebble';
alter table public.content_reports drop constraint content_reports_target_kind_check;
alter table public.content_reports
  add constraint content_reports_target_kind_check check (target_kind in ('moment', 'profile', 'glyph'));

-- 9. RLS -------------------------------------------------------------------------------
-- A definer probe keeps moments_select and pebbles_select from recursing into
-- each other: pebbles_select asks "is the moment hidden" without touching
-- moments' own policy. Lives in `private`, which PostgREST never serves.
create or replace function private.moment_is_hidden(p_moment_id uuid)
returns boolean
language sql
security definer
stable
set search_path = public
as $$
  select coalesce((select m.hidden_at is not null from public.moments m where m.id = p_moment_id), true)
$$;
revoke all on function private.moment_is_hidden(uuid) from public, anon;
grant execute on function private.moment_is_hidden(uuid) to authenticated;

create policy "pebbles_select" on public.pebbles
  for select to authenticated using (
    user_id = auth.uid()
    or (
      not private.moment_is_hidden(pebbles.moment_id)
      and (
        visibility = 'public'
        or (
          visibility = 'private'
          and exists (
            select 1 from public.connections c
             where c.user_a = least(auth.uid(), pebbles.user_id)
               and c.user_b = greatest(auth.uid(), pebbles.user_id)
          )
        )
      )
      and not private.is_blocked_with(pebbles.user_id)
    )
  );

alter table public.moments enable row level security;
create policy "moments_select" on public.moments
  for select to authenticated using (
    user_id = auth.uid()
    or (
      hidden_at is null
      and exists (
        select 1 from public.pebbles p
         where p.moment_id = moments.id
           and p.user_id <> auth.uid()
           and (
             p.visibility = 'public'
             or (
               p.visibility = 'private'
               and exists (
                 select 1 from public.connections c
                  where c.user_a = least(auth.uid(), p.user_id)
                    and c.user_b = greatest(auth.uid(), p.user_id)
               )
             )
           )
           and not private.is_blocked_with(p.user_id)
      )
    )
  );
create policy "moments_insert" on public.moments for insert to authenticated with check (user_id = auth.uid());
create policy "moments_update" on public.moments for update to authenticated using (user_id = auth.uid());
create policy "moments_delete" on public.moments for delete to authenticated using (user_id = auth.uid());

-- 10. moderation guard moves to moments -----------------------------------------------------
drop function public.enforce_pebble_moderation_columns();
create function public.enforce_moment_moderation_columns()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  if current_user not in ('authenticated', 'anon') then return new; end if;
  if new.hidden_at is distinct from old.hidden_at or new.hidden_by is distinct from old.hidden_by then
    raise exception 'moments_moderation_column'
      using hint = 'hidden_at and hidden_by are set by moderation, not by the owner.';
  end if;
  return new;
end;
$$;
create trigger moments_moderation_guard
  before update of hidden_at, hidden_by on public.moments
  for each row execute function public.enforce_moment_moderation_columns();
```

- [ ] **Step 2: Rehearse**

Run: `SUPABASE_DB_URL=... packages/supabase/scripts/rehearsal/run.sh 20261012090000`
Expected: `== applying 20261012090000_moments_split_schema.sql` with no error. If `drop column` reports a dependent object, add it to section 5 (drop or replace), never skip the drop.

- [ ] **Step 3: Assert the backfill in the rehearsal psql**

```sql
do $$ begin
  assert (select count(*) from public.moments where id in (select id from public.pebbles))
       = (select count(*) from public.pebbles), 'every pebble has a same-id moment';
  assert not exists (select 1 from public.pebbles where moment_id <> id), 'legacy pebbles point at themselves';
  assert not exists (select 1 from public.snaps s where not exists (select 1 from public.moments m where m.id = s.moment_id)), 'no orphan snap row';
  assert not exists (select 1 from public.content_reports where target_kind = 'pebble'), 'reports retargeted';
end $$;
```
Expected: no output (asserts pass).

- [ ] **Step 4: Commit**

```bash
git add packages/supabase/supabase/migrations/20261012090000_moments_split_schema.sql
git commit -m "feat(db): add moments and move the moment half of pebbles into them"
```

---

## Task 3: Part 1 PR

- [ ] **Step 1: Lint and typecheck** — `npm run lint --workspace=packages/supabase && npm run build --workspace=packages/supabase`. Expected: clean (the migration is not typechecked; this proves nothing else broke).
- [ ] **Step 2: Create the stack** per the `gh-stack` skill, push Part 1, open the PR with body `Resolves #1046` only on the **last** part; Parts 1–4 bodies say `Part N of #1046`. Labels `feat` `db` `supabase`, the milestone. Add `no-lab-note` (database part, nothing user-visible yet).

---

## Task 4: Migration 2 — writers

**Files:**
- Create: `packages/supabase/supabase/migrations/20261012090100_moments_split_writers.sql`

- [ ] **Step 1: Write the migration**

```sql
-- =============================================================================
-- Moments and pebbles split — writers (spec §3)
-- save_moment / complete_pebble / delete_moment / delete_pebble /
-- compute_karma_delta / set_collection_glyph. create_pebble and update_pebble
-- are re-emitted as compat wrappers in 20261012090400.
-- =============================================================================

-- karma: the glyph bonus is gone, the description bonus reads the moment.
drop function public.compute_karma_delta(text, int, int, int, boolean, int);
create function public.compute_karma_delta(
  p_has_description boolean,
  p_cards_count int,
  p_souls_count int,
  p_domains_count int,
  p_snaps_count int
) returns int as $$
declare
  delta int := 1;
begin
  if p_has_description then delta := delta + 1; end if;
  delta := delta + least(p_cards_count, 4);
  if p_souls_count > 0 then delta := delta + 1; end if;
  if p_domains_count > 0 then delta := delta + 1; end if;
  if p_snaps_count > 0 then delta := delta + 1; end if;
  return least(delta, 10);
end;
$$ language plpgsql immutable;

-- save_moment ------------------------------------------------------------------
create function public.save_moment(payload jsonb)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user      uuid := auth.uid();
  v_id        uuid := nullif(payload->>'id', '')::uuid;
  v_name      text := nullif(btrim(coalesce(payload->>'name', '')), '');
  v_when      timestamptz := date_trunc('second', (payload->>'happened_at')::timestamptz);
  v_snap      record;
  v_max_media int;
  v_snaps     int;
begin
  if v_user is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;

  if v_id is null then
    insert into public.moments (user_id, name, description, happened_at)
    values (v_user, v_name, nullif(payload->>'description', ''), coalesce(v_when, date_trunc('second', now())))
    returning id into v_id;
  else
    update public.moments set
      name        = case when payload ? 'name' then v_name else name end,
      description = case when payload ? 'description' then nullif(payload->>'description', '') else description end,
      happened_at = coalesce(v_when, happened_at)
    where id = v_id and user_id = v_user;
    if not found then
      raise exception 'moment_not_found' using errcode = 'P0002';
    end if;
  end if;

  -- snaps: whole-set replace, per-moment quota (the profile column keeps its
  -- historical name). auth.uid() matches profiles.user_id, not profiles.id (#618).
  if payload ? 'snaps' then
    select coalesce(max_media_per_pebble, 1) into v_max_media
      from public.profiles where user_id = v_user;
    if jsonb_array_length(payload->'snaps') > coalesce(v_max_media, 1) then
      raise exception 'media_quota_exceeded' using errcode = 'P0001';
    end if;
    delete from public.snaps where moment_id = v_id;
    for v_snap in select * from jsonb_array_elements(payload->'snaps') loop
      insert into public.snaps (id, moment_id, user_id, storage_path, sort_order)
      values (
        coalesce(nullif(v_snap.value->>'id', '')::uuid, gen_random_uuid()),
        v_id, v_user, v_snap.value->>'storage_path',
        coalesce((v_snap.value->>'sort_order')::smallint, 0)
      );
    end loop;
  end if;

  -- D3: a date plus a name or a photo. Raising here rolls the whole call back.
  select count(*) into v_snaps from public.snaps where moment_id = v_id;
  if (select m.name from public.moments m where m.id = v_id) is null and v_snaps = 0 then
    raise exception 'moment_empty' using errcode = 'P0001';
  end if;

  return v_id;
end;
$$;

-- complete_pebble ----------------------------------------------------------------
create function public.complete_pebble(payload jsonb)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user          uuid := auth.uid();
  v_moment_id     uuid := (payload->>'moment_id')::uuid;
  v_pebble_id     uuid;
  v_is_new        boolean;
  v_soul_ids      uuid[];
  v_collection_ids uuid[];
  v_new           record;
  v_new_id        uuid;
  v_card          record;
  v_bad_collection uuid;
  v_has_desc      boolean;
  v_cards         int;
  v_souls         int;
  v_domains       int;
  v_snaps         int;
  v_new_karma     int;
  v_old_karma     int;
begin
  if v_user is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;
  -- Owner today; "is a participant" when pairs land.
  if not exists (select 1 from public.moments m where m.id = v_moment_id and m.user_id = v_user) then
    raise exception 'moment_not_found' using errcode = 'P0002';
  end if;

  select id into v_pebble_id from public.pebbles where moment_id = v_moment_id and user_id = v_user;
  v_is_new := v_pebble_id is null;

  -- D2: a pebble needs at least one domain. Required on insert; on update the
  -- key may be absent (keep) but never empty.
  if (v_is_new or payload ? 'domain_ids')
     and coalesce(jsonb_array_length(payload->'domain_ids'), 0) = 0 then
    raise exception 'pebble_needs_domain' using errcode = 'P0001';
  end if;
  if v_is_new and (payload->>'emotion_id') is null then
    raise exception 'pebble_needs_emotion' using errcode = 'P0001';
  end if;
  if v_is_new and ((payload->>'intensity') is null or (payload->>'positiveness') is null) then
    raise exception 'pebble_needs_valence' using errcode = 'P0001';
  end if;

  -- souls: existing ids ∪ inline creations
  if payload ? 'soul_ids' or payload ? 'new_souls' then
    select array_agg(val::uuid) into v_soul_ids
      from jsonb_array_elements_text(coalesce(payload->'soul_ids', '[]'::jsonb)) val;
    v_soul_ids := coalesce(v_soul_ids, '{}');
    for v_new in select * from jsonb_array_elements(coalesce(payload->'new_souls', '[]'::jsonb)) loop
      insert into public.souls (user_id, name) values (v_user, v_new.value->>'name') returning id into v_new_id;
      v_soul_ids := array_append(v_soul_ids, v_new_id);
    end loop;
  end if;

  -- collections: existing ids ∪ inline creations, then ownership
  if payload ? 'collection_ids' or payload ? 'new_collections' then
    select array_agg(val::uuid) into v_collection_ids
      from jsonb_array_elements_text(coalesce(payload->'collection_ids', '[]'::jsonb)) val;
    v_collection_ids := coalesce(v_collection_ids, '{}');
    for v_new in select * from jsonb_array_elements(coalesce(payload->'new_collections', '[]'::jsonb)) loop
      insert into public.collections (user_id, name) values (v_user, v_new.value->>'name') returning id into v_new_id;
      v_collection_ids := array_append(v_collection_ids, v_new_id);
    end loop;
    select c_id into v_bad_collection from unnest(v_collection_ids) c_id
     where not exists (select 1 from public.collections where id = c_id and user_id = v_user) limit 1;
    if v_bad_collection is not null then
      raise exception 'Collection not owned by user: %', v_bad_collection;
    end if;
  end if;

  if v_is_new then
    insert into public.pebbles (user_id, moment_id, emotion_id, intensity, positiveness, visibility)
    values (
      v_user, v_moment_id,
      (payload->>'emotion_id')::uuid,
      (payload->>'intensity')::smallint,
      (payload->>'positiveness')::smallint,
      coalesce(payload->>'visibility', 'secret')
    )
    returning id into v_pebble_id;
  else
    update public.pebbles set
      emotion_id   = coalesce((payload->>'emotion_id')::uuid, emotion_id),
      intensity    = coalesce((payload->>'intensity')::smallint, intensity),
      positiveness = coalesce((payload->>'positiveness')::smallint, positiveness),
      visibility   = coalesce(payload->>'visibility', visibility)
    where id = v_pebble_id;
  end if;

  if payload ? 'cards' then
    delete from public.pebble_cards where pebble_id = v_pebble_id;
    for v_card in select * from jsonb_array_elements(payload->'cards') loop
      insert into public.pebble_cards (pebble_id, species_id, value, sort_order)
      values (v_pebble_id, (v_card.value->>'species_id')::uuid, v_card.value->>'value',
              coalesce((v_card.value->>'sort_order')::smallint, 0));
    end loop;
  end if;
  if v_soul_ids is not null then
    delete from public.pebble_souls where pebble_id = v_pebble_id;
    insert into public.pebble_souls (pebble_id, soul_id) select v_pebble_id, unnest(v_soul_ids);
  end if;
  if payload ? 'domain_ids' then
    delete from public.pebble_domains where pebble_id = v_pebble_id;
    insert into public.pebble_domains (pebble_id, domain_id)
    select v_pebble_id, (val::text)::uuid from jsonb_array_elements_text(payload->'domain_ids') val;
  end if;
  if v_collection_ids is not null then
    delete from public.collection_pebbles where pebble_id = v_pebble_id;
    insert into public.collection_pebbles (collection_id, pebble_id) select unnest(v_collection_ids), v_pebble_id;
  end if;

  -- karma: D4, rewarded on the first completion; enrichment deltas after.
  select m.description is not null and m.description <> '' into v_has_desc from public.moments m where m.id = v_moment_id;
  select count(*) into v_cards   from public.pebble_cards   where pebble_id = v_pebble_id;
  select count(*) into v_souls   from public.pebble_souls   where pebble_id = v_pebble_id;
  select count(*) into v_domains from public.pebble_domains where pebble_id = v_pebble_id;
  select count(*) into v_snaps   from public.snaps          where moment_id = v_moment_id;
  v_new_karma := public.compute_karma_delta(v_has_desc, v_cards, v_souls, v_domains, v_snaps);

  if v_is_new then
    insert into public.karma_events (user_id, delta, reason, ref_id)
    values (v_user, v_new_karma, 'pebble_created', v_pebble_id);
  else
    select coalesce(sum(delta), 0) into v_old_karma
      from public.karma_events where ref_id = v_pebble_id and user_id = v_user;
    if v_new_karma <> v_old_karma then
      insert into public.karma_events (user_id, delta, reason, ref_id)
      values (v_user, v_new_karma - v_old_karma, 'pebble_enriched', v_pebble_id);
    end if;
  end if;

  return v_pebble_id;
end;
$$;

-- delete_pebble: the pebble only, the moment stays ----------------------------------
create or replace function public.delete_pebble(p_pebble_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user  uuid := auth.uid();
  v_total int;
begin
  if not exists (select 1 from public.pebbles where id = p_pebble_id and user_id = v_user) then
    raise exception 'Pebble not found or access denied';
  end if;
  select coalesce(sum(delta), 0) into v_total from public.karma_events where ref_id = p_pebble_id and user_id = v_user;
  if v_total > 0 then
    insert into public.karma_events (user_id, delta, reason, ref_id) values (v_user, -v_total, 'pebble_deleted', p_pebble_id);
  end if;
  delete from public.pebbles where id = p_pebble_id;
end;
$$;

-- delete_moment: the moment, its pebbles and snaps; returns the files to remove ------
create function public.delete_moment(p_moment_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user  uuid := auth.uid();
  v_p     record;
  v_total int;
  v_paths text[];
begin
  if not exists (select 1 from public.moments where id = p_moment_id and user_id = v_user) then
    raise exception 'moment_not_found' using errcode = 'P0002';
  end if;
  for v_p in select id from public.pebbles where moment_id = p_moment_id and user_id = v_user loop
    select coalesce(sum(delta), 0) into v_total from public.karma_events where ref_id = v_p.id and user_id = v_user;
    if v_total > 0 then
      insert into public.karma_events (user_id, delta, reason, ref_id) values (v_user, -v_total, 'pebble_deleted', v_p.id);
    end if;
  end loop;
  select coalesce(array_agg(storage_path), '{}') into v_paths from public.snaps where moment_id = p_moment_id;
  delete from public.moments where id = p_moment_id;
  return jsonb_build_object('storage_paths', to_jsonb(v_paths));
end;
$$;

-- delete_moment_media replaces delete_pebble_media (same body, new name) --------------
drop function public.delete_pebble_media(uuid);
create function public.delete_moment_media(p_snap_id uuid)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user uuid := auth.uid();
  v_path text;
begin
  delete from public.snaps where id = p_snap_id and user_id = v_user returning storage_path into v_path;
  if v_path is null then raise exception 'Snap not found or access denied'; end if;
  return v_path;
end;
$$;

-- set_collection_glyph: named for symmetry with souls; the trigger guards it --------
create function public.set_collection_glyph(p_collection_id uuid, p_glyph_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  update public.collections set glyph_id = p_glyph_id
   where id = p_collection_id and user_id = auth.uid();
  if not found then raise exception 'collection_not_found' using errcode = 'P0002'; end if;
end;
$$;

revoke all on function public.save_moment(jsonb)                      from public, anon;
revoke all on function public.complete_pebble(jsonb)                  from public, anon;
revoke all on function public.delete_pebble(uuid)                     from public, anon;
revoke all on function public.delete_moment(uuid)                     from public, anon;
revoke all on function public.delete_moment_media(uuid)               from public, anon;
revoke all on function public.set_collection_glyph(uuid, uuid)        from public, anon;
grant execute on function public.save_moment(jsonb)               to authenticated;
grant execute on function public.complete_pebble(jsonb)           to authenticated;
grant execute on function public.delete_pebble(uuid)              to authenticated;
grant execute on function public.delete_moment(uuid)              to authenticated;
grant execute on function public.delete_moment_media(uuid)        to authenticated;
grant execute on function public.set_collection_glyph(uuid, uuid) to authenticated;
```

- [ ] **Step 2: Rehearse and exercise as a user**

Run the rehearsal from `20261012090000`, then in its psql:

```sql
set role authenticated;
set request.jwt.claim.sub = '<an existing auth.users id from the dump>';
select public.save_moment('{"name":"rehearsal"}') ;                               -- uuid
select public.save_moment('{}');                                                  -- ERROR moment_empty
select public.complete_pebble(jsonb_build_object('moment_id', (select id from public.moments where name='rehearsal'),
  'emotion_id', (select id from public.emotions limit 1), 'intensity', 2, 'positiveness', 1));  -- ERROR pebble_needs_domain
select public.complete_pebble(jsonb_build_object('moment_id', (select id from public.moments where name='rehearsal'),
  'emotion_id', (select id from public.emotions limit 1), 'intensity', 2, 'positiveness', 1,
  'domain_ids', jsonb_build_array((select id from public.domains limit 1))));           -- uuid
select reason, delta from public.karma_events order by created_at desc limit 1;    -- pebble_created, 2
reset role;
```

- [ ] **Step 3: Commit**

```bash
git add packages/supabase/supabase/migrations/20261012090100_moments_split_writers.sql
git commit -m "feat(db): save_moment and complete_pebble replace create_pebble"
```

Push as Part 2 of the stack.

---

## Task 5: Migration 3 — readers

**Files:**
- Create: `packages/supabase/supabase/migrations/20261012090200_moments_split_readers.sql`

- [ ] **Step 1: Write the migration**

```sql
-- =============================================================================
-- Moments and pebbles split — readers (spec §4)
-- resolve_pebble_glyph / v_moments_full / path_moments / get_shared_pebble
-- =============================================================================

-- D5: oldest collection carrying a glyph, else first domain default, else null.
-- Definer so every viewer carves the same stone: collection_pebbles is owner-
-- readable, and a connection would otherwise see a different glyph.
create function public.resolve_pebble_glyph(p_pebble_id uuid)
returns jsonb
language sql
security definer
stable
set search_path = public
as $$
  with candidates as (
    select c.glyph_id, 'collection' as source, 0 as rank, c.created_at as ord, c.name as tie
      from public.collection_pebbles cp
      join public.collections c on c.id = cp.collection_id
     where cp.pebble_id = p_pebble_id and c.glyph_id is not null
    union all
    select d.default_glyph_id, 'domain', 1, null::timestamptz, d.slug
      from public.pebble_domains pd
      join public.domains d on d.id = pd.domain_id
     where pd.pebble_id = p_pebble_id and d.default_glyph_id is not null
  ),
  pick as (
    select * from candidates order by rank, ord nulls last, tie limit 1
  )
  select jsonb_build_object(
    'id', g.id, 'name', g.name, 'strokes', g.strokes, 'view_box', g.view_box, 'source', pick.source
  )
  from pick join public.glyphs g on g.id = pick.glyph_id;
$$;
revoke all on function public.resolve_pebble_glyph(uuid) from public, anon;
grant execute on function public.resolve_pebble_glyph(uuid) to authenticated;

-- v_moments_full: one row per readable moment, the caller's own pebble nested.
create view public.v_moments_full
with (security_invoker = true) as
select
  m.id, m.user_id, m.name, m.description, m.happened_at, m.hidden_at, m.created_at, m.updated_at,
  coalesce((
    select jsonb_agg(jsonb_build_object('id', sn.id, 'storage_path', sn.storage_path, 'sort_order', sn.sort_order)
                     order by sn.sort_order)
      from public.snaps sn where sn.moment_id = m.id), '[]'::jsonb) as snaps,
  (
    select jsonb_build_object(
      'id', p.id,
      'emotion_id', p.emotion_id,
      'intensity', p.intensity,
      'positiveness', p.positiveness,
      'visibility', p.visibility,
      'created_at', p.created_at,
      'updated_at', p.updated_at,
      'emotion', jsonb_build_object('id', e.id, 'slug', e.slug, 'name', e.name, 'color', e.color),
      'glyph', public.resolve_pebble_glyph(p.id),
      'cards', coalesce((select jsonb_agg(jsonb_build_object('id', pc.id, 'species_id', pc.species_id, 'value', pc.value, 'sort_order', pc.sort_order) order by pc.sort_order)
                          from public.pebble_cards pc where pc.pebble_id = p.id), '[]'::jsonb),
      'souls', coalesce((select jsonb_agg(jsonb_build_object('id', s.id, 'name', s.name) order by s.name)
                          from public.pebble_souls ps join public.souls s on s.id = ps.soul_id where ps.pebble_id = p.id), '[]'::jsonb),
      'domains', coalesce((select jsonb_agg(jsonb_build_object('id', d.id, 'slug', d.slug, 'name', d.name, 'label', d.label) order by d.slug)
                          from public.pebble_domains pd join public.domains d on d.id = pd.domain_id where pd.pebble_id = p.id), '[]'::jsonb),
      'collections', coalesce((select jsonb_agg(jsonb_build_object('id', c.id, 'name', c.name, 'glyph_id', c.glyph_id) order by c.name)
                          from public.collection_pebbles cp join public.collections c on c.id = cp.collection_id where cp.pebble_id = p.id), '[]'::jsonb)
    )
    from public.pebbles p
    join public.emotions e on e.id = p.emotion_id
    where p.moment_id = m.id and p.user_id = auth.uid()
  ) as pebble
from public.moments m;
revoke all on public.v_moments_full from public, anon;
grant select on public.v_moments_full to authenticated;

-- path_moments: the timeline. A null pebble is an uncompleted moment.
create function public.path_moments()
returns table (
  id uuid, name text, happened_at timestamptz, created_at timestamptz,
  first_snap_path text, pebble jsonb
)
language sql
security invoker
stable
set search_path = public
as $$
  select
    m.id, m.name, m.happened_at, m.created_at,
    (select s.storage_path from public.snaps s where s.moment_id = m.id
      order by s.sort_order asc nulls last, s.created_at asc limit 1) as first_snap_path,
    (select jsonb_build_object(
        'id', p.id, 'intensity', p.intensity, 'positiveness', p.positiveness, 'visibility', p.visibility,
        'emotion', case when e.id is null then null else jsonb_build_object('id', e.id, 'slug', e.slug, 'name', e.name) end,
        'glyph', public.resolve_pebble_glyph(p.id))
       from public.pebbles p left join public.emotions e on e.id = p.emotion_id
      where p.moment_id = m.id and p.user_id = auth.uid()) as pebble
  from public.moments m
  where m.user_id = auth.uid()
  order by m.happened_at desc;
$$;
revoke all on function public.path_moments() from public, anon;
grant execute on function public.path_moments() to authenticated;

-- get_shared_pebble: same name and argument; moment fields + the sharer's pebble.
-- render_svg stays as an explicit null until the web share page reads glyph.
create or replace function public.get_shared_pebble(p_pebble_id uuid)
returns jsonb
language sql
security definer
stable
set search_path = public
as $$
  select jsonb_build_object(
    'id', p.id,
    'name', m.name,
    'description', m.description,
    'happened_at', to_char(m.happened_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
    'intensity', p.intensity,
    'positiveness', p.positiveness,
    'render_svg', null::text,
    'glyph', public.resolve_pebble_glyph(p.id),
    'emotion', jsonb_build_object(
      'id', e.id, 'slug', e.slug, 'name', e.name, 'color', e.color,
      'primary_color', c.primary_color, 'secondary_color', c.secondary_color)
  )
  from public.pebbles p
  join public.moments m on m.id = p.moment_id
  join public.emotions e on e.id = p.emotion_id
  left join public.emotion_categories c on c.id = e.category_id
  where p.id = p_pebble_id
    and p.visibility = 'public'
    and m.hidden_at is null;
$$;
```

- [ ] **Step 2: Rehearse**

Rehearse from `20261012090000`; as the test user from Task 4: `select id, name, pebble->>'id', pebble->'glyph'->>'source' from public.path_moments() limit 3;` Expected: the rehearsal moment with a pebble id and `domain` as glyph source.

- [ ] **Step 3: Commit**

```bash
git add packages/supabase/supabase/migrations/20261012090200_moments_split_readers.sql
git commit -m "feat(db): read moments with the caller's pebble and a derived glyph"
```

---

## Task 6: Migration 4 — moderation, reports, purge, analytics, orphans

**Files:**
- Create: `packages/supabase/supabase/migrations/20261012090300_moments_split_moderation_purge.sql`

Before writing, diff the bases you re-emit against their newest emission (`purge_account` → `20260916090100`, `report_content` and `admin_list_content_reports` → `20260916090000`, `admin_set_content_hidden` → `20260916100000`, `list_orphan_snap_files` → `20261007131500`, the two analytics views and two admin functions → `20260919120000` / `20260501000006`). Only the edits listed in each header below may differ.

- [ ] **Step 1: Write the migration**

```sql
-- =============================================================================
-- Moments and pebbles split — moderation, reports, purge, analytics, orphans
-- Bases re-emitted verbatim except where a header says otherwise.
-- =============================================================================

-- admin_set_content_hidden: 'moment' is the kind; 'pebble' stays accepted as an
-- alias until the admin app (Part 5) sends 'moment'. Base 20260916100000.
create or replace function public.admin_set_content_hidden(
  p_target_kind text, p_target_id uuid, p_hidden boolean, p_note text default null
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
  if p_target_kind in ('moment', 'pebble') then
    update public.moments set hidden_at = v_when, hidden_by = v_who where id = p_target_id;
  elsif p_target_kind = 'profile' then
    update public.profiles set hidden_at = v_when, hidden_by = v_who where user_id = p_target_id;
  else
    raise exception 'invalid_kind';
  end if;
  get diagnostics v_rows = row_count;
  if v_rows = 0 then raise exception 'not_found'; end if;
  return jsonb_build_object('target_kind', 'moment', 'target_id', p_target_id, 'hidden', p_hidden, 'note', nullif(btrim(p_note), ''));
end;
$$;

-- report_content: a 'moment' target; 'pebble' + pebble id is normalised to the
-- pebble's moment for callers not yet migrated. Gate: the reporter may read a
-- pebble on that moment (mirrors pebbles_select minus the owner arm).
-- Base 20260916090000; only the kind check and the first gate branch change.
create or replace function public.report_content(
  p_target_kind text, p_target_id uuid, p_reason text, p_detail text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user     uuid := auth.uid();
  v_kind     text := p_target_kind;
  v_target   uuid := p_target_id;
  v_owner    uuid;
  v_snapshot jsonb;
  v_row      public.content_reports;
begin
  if v_user is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;
  if v_kind not in ('moment', 'pebble', 'profile', 'glyph') then
    raise exception 'invalid_kind';
  end if;
  if p_reason not in ('sexual', 'violence', 'hate', 'harassment',
                      'self_harm', 'illegal', 'spam', 'impersonation', 'other') then
    raise exception 'invalid_reason';
  end if;

  if v_kind = 'pebble' then
    v_kind := 'moment';
    select p.moment_id into v_target from public.pebbles p where p.id = p_target_id;
    -- unknown pebble id: v_target null falls through to not_found below
  end if;

  if v_kind = 'moment' then
    select m.user_id, jsonb_build_object('name', m.name, 'description', m.description)
      into v_owner, v_snapshot
      from public.moments m
     where m.id = v_target
       and exists (
         select 1 from public.pebbles p
          where p.moment_id = m.id
            and (
              p.visibility = 'public'
              or (p.visibility = 'private' and exists (
                    select 1 from public.connections c
                     where c.user_a = least(v_user, p.user_id)
                       and c.user_b = greatest(v_user, p.user_id)))
            )
       );
  elsif v_kind = 'profile' then
    select pr.user_id, jsonb_build_object('handle', pr.handle, 'display_name', pr.display_name)
      into v_owner, v_snapshot
      from public.profiles pr
     where pr.user_id = v_target and pr.public_profile = true;
  else
    select g.user_id, jsonb_build_object('name', g.name)
      into v_owner, v_snapshot
      from public.glyphs g
     where g.id = v_target
       and exists (select 1 from public.glyph_submissions s
                    where s.glyph_id = g.id and s.status = 'approved' and s.listed = true);
  end if;

  if v_owner is null then raise exception 'not_found'; end if;
  if v_owner = v_user then raise exception 'cannot_report_own'; end if;

  insert into public.content_reports
    (reporter_id, target_kind, target_id, target_user_id, reason, detail, target_snapshot)
  values
    (v_user, v_kind, v_target, v_owner, p_reason, nullif(btrim(p_detail), ''), v_snapshot)
  on conflict (reporter_id, target_kind, target_id) where status = 'open'
  do nothing
  returning * into v_row;

  if v_row.id is null then
    select * into v_row from public.content_reports
     where reporter_id = v_user and target_kind = v_kind and target_id = v_target and status = 'open';
  end if;

  return jsonb_build_object(
    'id', v_row.id, 'status', v_row.status,
    'created_at', to_char(v_row.created_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'));
end;
$$;

-- admin_list_content_reports: the 'moment' branches. Base 20260916090000.
create or replace function public.admin_list_content_reports(p_status text default null)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_result jsonb;
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'not_admin' using errcode = '42501';
  end if;
  select coalesce(jsonb_agg(to_jsonb(t) order by t.created_at), '[]'::jsonb)
  into v_result
  from (
    select
      r.id, r.reporter_id, r.target_kind, r.target_id, r.target_user_id, r.reason, r.detail,
      r.target_snapshot, r.status, r.resolution_note, r.resolved_at, r.resolved_by,
      to_char(r.created_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') as created_at,
      (case r.target_kind
         when 'moment'  then not exists (select 1 from public.moments  m  where m.id      = r.target_id)
         when 'profile' then not exists (select 1 from public.profiles pr where pr.user_id = r.target_id)
         else                not exists (select 1 from public.glyphs   g  where g.id      = r.target_id)
       end) as target_missing,
      (select count(*) from public.content_reports o
        where o.target_kind = r.target_kind and o.target_id = r.target_id and o.status = 'open') as open_reports_against_target,
      (case r.target_kind
         when 'moment' then (select jsonb_build_object(
                               'name', m.name, 'description', m.description, 'hidden', m.hidden_at is not null,
                               'visibility', (select max(p.visibility) from public.pebbles p where p.moment_id = m.id))
                               from public.moments m where m.id = r.target_id)
         when 'profile' then (select jsonb_build_object(
                               'handle', pr.handle, 'display_name', pr.display_name, 'public_profile', pr.public_profile)
                               from public.profiles pr where pr.user_id = r.target_id)
         else (select jsonb_build_object(
                 'name', g.name,
                 'listed', exists (select 1 from public.glyph_submissions s where s.glyph_id = g.id and s.listed))
                 from public.glyphs g where g.id = r.target_id)
       end) as target_live
    from public.content_reports r
    where p_status is null or r.status = p_status
  ) t;
  return v_result;
end;
$$;

-- purge_account. Base 20260916090100, three edits: the kept-glyph predicate
-- reads collections instead of pebbles.glyph_id; section (3) deletes moments
-- (cascading own pebbles and snaps) and then any pebble of the user on another
-- user's moment; the pebble_drafts delete is gone. Counts gain 'moments'.
create or replace function public.purge_account(p_user_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_kept   uuid[];
  v_counts jsonb := '{}'::jsonb;
  v_n      bigint;
begin
  if p_user_id is null then
    raise exception 'missing_user_id' using errcode = '22004';
  end if;

  delete from public.glyph_entitlements where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('glyph_entitlements', v_n);

  delete from public.glyph_favourites where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('glyph_favourites', v_n);

  select coalesce(array_agg(g.id), '{}') into v_kept
  from public.glyphs g
  where g.user_id = p_user_id
    and (
         exists (select 1 from public.glyph_entitlements e where e.glyph_id = g.id and e.user_id <> p_user_id)
      or exists (select 1 from public.souls s where s.glyph_id = g.id and s.user_id <> p_user_id)
      or exists (select 1 from public.collections c where c.glyph_id = g.id and c.user_id <> p_user_id)
      or exists (select 1 from public.profiles pr where pr.glyph_id = g.id and pr.user_id <> p_user_id)
      or exists (select 1 from public.domains d where d.default_glyph_id = g.id)
    );

  update public.glyphs set user_id = null where id = any(v_kept);
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('glyphs_anonymized', v_n);

  update public.glyph_submissions set listed = false where glyph_id = any(v_kept) and submitter_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('glyph_submissions_delisted', v_n);

  update public.glyph_submissions set submitter_id = null where submitter_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('glyph_submissions_detached', v_n);

  update public.glyph_submissions set reviewed_by = null where reviewed_by = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('glyph_submissions_unreviewed', v_n);

  -- (3) Content. moments cascade own pebbles + snaps; the second delete takes
  -- pebbles of this user on other users' moments (participants, later).
  delete from public.moments where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('moments', v_n);

  delete from public.pebbles where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('pebbles', v_n);

  delete from public.snaps where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('snaps', v_n);

  delete from public.souls where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('souls', v_n);

  delete from public.collections where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('collections', v_n);

  delete from public.profiles where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('profiles', v_n);

  delete from public.glyphs where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('glyphs_deleted', v_n);

  delete from public.karma_events where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('karma_events', v_n);

  -- (4) auth.users-CASCADE rows.
  -- >>> APPEND new per-user tables from later milestones HERE. <<<
  update public.content_reports set reporter_id = null where reporter_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('content_reports_detached', v_n);

  delete from public.content_reports where target_user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('content_reports', v_n);

  delete from public.user_consents where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('user_consents', v_n);

  delete from public.connections where p_user_id in (user_a, user_b);
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('connections', v_n);

  delete from public.connection_invites where inviter_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('connection_invites', v_n);

  delete from public.connection_blocks where p_user_id in (blocker_id, blocked_id);
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('connection_blocks', v_n);

  delete from public.achievement_unlocks where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('achievement_unlocks', v_n);

  delete from public.log_reactions where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('log_reactions', v_n);

  delete from public.wallet_balances where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('wallet_balances', v_n);

  delete from public.bounces where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('bounces', v_n);

  return jsonb_build_object('user_id', p_user_id, 'kept_glyphs', coalesce(array_length(v_kept, 1), 0), 'counts', v_counts);
end;
$$;

-- Analytics views, dropped in 20261012090000. "Custom glyph" now means the
-- pebble inherits a user-carved glyph through one of its collections.
create view public.v_analytics_pebble_volume_daily as
with days as (
  select generate_series(coalesce((select min(created_at)::date from public.pebbles), current_date), current_date, interval '1 day')::date as bucket_date
),
day_pebbles as (
  select
    p.created_at::date as bucket_date,
    count(*)::int as pebbles,
    count(*) filter (where exists (select 1 from public.snaps s where s.moment_id = p.moment_id))::int as pebbles_with_picture,
    count(*) filter (where exists (select 1 from public.collection_pebbles cp where cp.pebble_id = p.id))::int as pebbles_in_collection,
    count(*) filter (where exists (
      select 1 from public.collection_pebbles cp join public.collections c on c.id = cp.collection_id
        join public.glyphs g on g.id = c.glyph_id where cp.pebble_id = p.id and g.is_custom))::int as pebbles_with_custom_glyph,
    count(distinct p.user_id)::int as active_users
  from public.pebbles p
  group by p.created_at::date
)
select d.bucket_date,
  coalesce(dp.pebbles, 0) as pebbles,
  coalesce(dp.pebbles_with_picture, 0) as pebbles_with_picture,
  coalesce(dp.pebbles_in_collection, 0) as pebbles_in_collection,
  coalesce(dp.pebbles_with_custom_glyph, 0) as pebbles_with_custom_glyph,
  coalesce(dp.active_users, 0) as active_users
from days d left join day_pebbles dp using (bucket_date);

create view public.v_analytics_pebble_enrichment_daily as
with base as (
  select
    p.created_at::date as bucket_date, p.id, m.description, p.intensity,
    exists (select 1 from public.snaps s where s.moment_id = p.moment_id) as has_picture,
    exists (select 1 from public.collection_pebbles cp where cp.pebble_id = p.id) as in_collection,
    exists (select 1 from public.pebble_souls ps where ps.pebble_id = p.id) as has_soul,
    exists (select 1 from public.collection_pebbles cp join public.collections c on c.id = cp.collection_id
              join public.glyphs g on g.id = c.glyph_id where cp.pebble_id = p.id and g.is_custom) as has_custom_glyph
  from public.pebbles p join public.moments m on m.id = p.moment_id
)
select bucket_date,
  count(*)::int as total_pebbles,
  round(100.0 * count(*) filter (where has_picture) / nullif(count(*), 0), 1) as pct_with_picture,
  round(100.0 * count(*) filter (where in_collection) / nullif(count(*), 0), 1) as pct_in_collection,
  round(100.0 * count(*) filter (where has_custom_glyph) / nullif(count(*), 0), 1) as pct_with_custom_glyph,
  round(100.0 * count(*) filter (where description is not null and length(description) > 0) / nullif(count(*), 0), 1) as pct_with_thought,
  round(100.0 * count(*) filter (where has_soul) / nullif(count(*), 0), 1) as pct_with_soul,
  round(100.0 * count(*) filter (where intensity is not null) / nullif(count(*), 0), 1) as pct_with_intensity
from base group by bucket_date;
revoke all on public.v_analytics_pebble_volume_daily, public.v_analytics_pebble_enrichment_daily from public, anon, authenticated;
grant select on public.v_analytics_pebble_volume_daily, public.v_analytics_pebble_enrichment_daily to service_role;

-- get_pebble_enrichment / get_pebble_volume_series: copy each body verbatim from
-- 20260501000006_glyphs_is_custom.sql and apply exactly these substitutions:
--   s.pebble_id = p.id                      -> s.moment_id = p.moment_id      (snaps)
--   p.description                           -> m.description, with `join public.moments m on m.id = p.moment_id`
--   g.id = p.glyph_id and g.is_custom       -> the collection-glyph exists() used in the views above
-- (paste the two re-emitted functions here)

-- list_orphan_snap_files: the draft_snaps CTE is gone with the table. Base 20261007131500.
create or replace function public.list_orphan_snap_files(p_min_age_seconds integer default 86400, p_owner uuid default null)
returns table(name text, size bigint, created_at timestamptz)
language sql
stable
security definer
set search_path = ''
as $$
  with candidates as materialized (
    select o.name, coalesce((o.metadata->>'size')::bigint, 0) as size, o.created_at,
           lower((storage.foldername(o.name))[2]) as snap_id
    from storage.objects o
    where o.bucket_id = 'pebbles-media'
      and array_length(storage.foldername(o.name), 1) >= 2
      and (storage.foldername(o.name))[2] ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
      and o.created_at < now() - make_interval(secs => greatest(coalesce(p_min_age_seconds, 86400), 0))
      and (p_owner is null or (storage.foldername(o.name))[1] = p_owner::text)
  )
  select c.name, c.size, c.created_at
  from candidates c
  where not exists (select 1 from public.snaps sn where sn.id = c.snap_id::uuid)
    and not exists (select 1 from public.snaps sn where sn.storage_path = c.name or starts_with(c.name, sn.storage_path || '/'))
  order by c.created_at, c.name;
$$;
revoke all on function public.list_orphan_snap_files(integer, uuid) from public, anon, authenticated;
grant execute on function public.list_orphan_snap_files(integer, uuid) to service_role;
```

- [ ] **Step 2: Paste the two admin analytics functions** where the comment says so, from `20260501000006_glyphs_is_custom.sql`, with the three substitutions. Diff the result against the base: only those lines may differ.

- [ ] **Step 3: Rehearse** from `20261012090000`. Then: `select count(*) from public.v_analytics_pebble_volume_daily; select public.list_orphan_snap_files(0) limit 1;` Expected: a count, and no error from the orphan list.

- [ ] **Step 4: Commit**

```bash
git add packages/supabase/supabase/migrations/20261012090300_moments_split_moderation_purge.sql
git commit -m "feat(db): moderation, reports, purge and analytics follow the moment"
```

Push as Part 3 together with Task 5.

---

## Task 7: Migration 5 — web compatibility layer

**Files:**
- Create: `packages/supabase/supabase/migrations/20261012090400_moments_split_web_compat.sql`

- [ ] **Step 1: Write the migration**

```sql
-- =============================================================================
-- Moments and pebbles split — web compatibility (spec §5). DEPRECATED on
-- arrival: every object here is removed when the web composer ships. Nothing
-- new may depend on them.
-- =============================================================================

-- v_pebbles_full in its 20260701114205 column set. glyph = the resolved glyph.
create view public.v_pebbles_full
with (security_invoker = true) as
select
  p.id, p.user_id, m.name, m.description, m.happened_at, p.intensity, p.positiveness, p.visibility,
  p.emotion_id,
  (public.resolve_pebble_glyph(p.id)->>'id')::uuid as glyph_id,
  p.created_at, p.updated_at,
  jsonb_build_object('id', e.id, 'slug', e.slug, 'name', e.name, 'color', e.color) as emotion,
  public.resolve_pebble_glyph(p.id) as glyph,
  coalesce((select jsonb_agg(jsonb_build_object('id', pc.id, 'species_id', pc.species_id, 'value', pc.value, 'sort_order', pc.sort_order) order by pc.sort_order)
            from public.pebble_cards pc where pc.pebble_id = p.id), '[]'::jsonb) as cards,
  coalesce((select jsonb_agg(jsonb_build_object('id', s.id, 'name', s.name) order by s.name)
            from public.pebble_souls ps join public.souls s on s.id = ps.soul_id where ps.pebble_id = p.id), '[]'::jsonb) as souls,
  coalesce((select jsonb_agg(jsonb_build_object('id', d.id, 'slug', d.slug, 'name', d.name, 'label', d.label) order by d.slug)
            from public.pebble_domains pd join public.domains d on d.id = pd.domain_id where pd.pebble_id = p.id), '[]'::jsonb) as domains,
  coalesce((select jsonb_agg(jsonb_build_object('id', sn.id, 'storage_path', sn.storage_path, 'sort_order', sn.sort_order) order by sn.sort_order)
            from public.snaps sn where sn.moment_id = p.moment_id), '[]'::jsonb) as snaps,
  coalesce((select jsonb_agg(jsonb_build_object('id', c.id, 'name', c.name) order by c.name)
            from public.collection_pebbles cp join public.collections c on c.id = cp.collection_id where cp.pebble_id = p.id), '[]'::jsonb) as collections
from public.pebbles p
join public.moments m on m.id = p.moment_id
join public.emotions e on e.id = p.emotion_id;
revoke all on public.v_pebbles_full from public, anon;
grant select on public.v_pebbles_full to authenticated;

-- path_pebbles in its 20260519141000 signature; render_svg is null.
create or replace function public.path_pebbles()
returns table (
  id uuid, name text, happened_at timestamptz, created_at timestamptz,
  intensity smallint, positiveness smallint, render_svg text, emotion jsonb, first_snap_path text
)
language sql
security invoker
stable
set search_path = public
as $$
  select p.id, m.name, m.happened_at, p.created_at, p.intensity, p.positiveness, null::text,
    case when e.id is null then null else jsonb_build_object('id', e.id, 'slug', e.slug, 'name', e.name) end,
    (select s.storage_path from public.snaps s where s.moment_id = m.id
      order by s.sort_order asc nulls last, s.created_at asc limit 1)
  from public.pebbles p
  join public.moments m on m.id = p.moment_id
  left join public.emotions e on e.id = p.emotion_id
  where p.user_id = auth.uid()
  order by m.happened_at desc;
$$;

-- create_pebble / update_pebble: one call, one complete pebble, as today.
-- glyph keys are ignored.
create or replace function public.create_pebble(payload jsonb)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_moment uuid;
begin
  v_moment := public.save_moment(jsonb_strip_nulls(jsonb_build_object(
    'name', payload->'name', 'description', payload->'description',
    'happened_at', payload->'happened_at', 'snaps', payload->'snaps')));
  return public.complete_pebble((payload - 'name' - 'description' - 'happened_at' - 'snaps' - 'glyph_id' - 'new_glyph')
                                || jsonb_build_object('moment_id', v_moment));
end;
$$;

create or replace function public.update_pebble(p_pebble_id uuid, payload jsonb)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_moment uuid;
  v_moment_part jsonb;
begin
  select moment_id into v_moment from public.pebbles where id = p_pebble_id and user_id = auth.uid();
  if v_moment is null then
    raise exception 'Pebble not found or access denied';
  end if;
  v_moment_part := jsonb_build_object('id', v_moment)
    || (payload - 'emotion_id' - 'intensity' - 'positiveness' - 'visibility' - 'domain_ids'
                - 'soul_ids' - 'new_souls' - 'collection_ids' - 'new_collections' - 'cards' - 'glyph_id' - 'new_glyph');
  if v_moment_part - 'id' <> '{}'::jsonb then
    perform public.save_moment(v_moment_part);
  end if;
  perform public.complete_pebble((payload - 'name' - 'description' - 'happened_at' - 'snaps' - 'glyph_id' - 'new_glyph')
                                 || jsonb_build_object('moment_id', v_moment));
end;
$$;
comment on function public.create_pebble(jsonb) is 'DEPRECATED compat wrapper over save_moment + complete_pebble. Remove with the web composer.';
comment on function public.update_pebble(uuid, jsonb) is 'DEPRECATED compat wrapper over save_moment + complete_pebble. Remove with the web composer.';
```

- [ ] **Step 2: Rehearse** from `20261012090000`; as the test user: `select public.create_pebble(jsonb_build_object('name','compat','happened_at','2026-10-11T10:00:00.123Z','emotion_id',(select id from emotions limit 1),'intensity',1,'positiveness',0,'domain_ids',jsonb_build_array((select id from domains limit 1))));` then `select name, happened_at, glyph_id from public.v_pebbles_full where name = 'compat';` Expected: one row, `happened_at` ends in `:00+00`, `glyph_id` set.

- [ ] **Step 3: Commit**

```bash
git add packages/supabase/supabase/migrations/20261012090400_moments_split_web_compat.sql
git commit -m "feat(db): keep the web contract alive over the moments schema"
```

---

## Task 8: Edge functions lose the render step

**Files:**
- Modify: `packages/supabase/supabase/functions/compose-pebble/index.ts`
- Modify: `packages/supabase/supabase/functions/compose-pebble-update/index.ts`
- Delete: `packages/supabase/supabase/functions/_shared/compose-and-write.ts`, `packages/supabase/supabase/functions/_shared/engine/` (whole directory), `packages/supabase/supabase/functions/backfill-pebble-render/`, `packages/supabase/scripts/backfill-renders.ts`, `packages/supabase/scripts/smoke-test-engine.ts`
- Modify: `packages/supabase/package.json` (`test` script)

- [ ] **Step 1: Rewrite compose-pebble**

Replace the body after the body-parse block with:

```ts
  // Step 1: the compat wrapper (save_moment + complete_pebble) as the end user
  const authClient = createAuthForwardedClient(req);
  const { data: pebbleId, error: rpcError } = await authClient.rpc("create_pebble", {
    payload: body.payload,
  });
  if (rpcError || !pebbleId) {
    console.error("compose-pebble: create_pebble rpc failed:", rpcError);
    return json({ error: rpcError?.message ?? "create_pebble returned no id" }, 400);
  }

  // Step 2: no render any more (the client carves the derived glyph). The keys
  // stay, null, so unmigrated clients decode the body.
  const karmaDelta = await readKarmaDelta(authClient, pebbleId as string, "pebble_created");
  return json(
    { pebble_id: pebbleId, karma_delta: karmaDelta, render_svg: null, render_version: null },
    200,
  );
```

Remove the `createAdminClient` and `composeAndWriteRender` imports. Update the header comment to say the function wraps the deprecated `create_pebble` compat RPC and returns null render keys.

- [ ] **Step 2: Same for compose-pebble-update** with `update_pebble` and `"pebble_enriched"`, returning `{ pebble_id: body.pebble_id, karma_delta, render_svg: null, render_version: null }`.

- [ ] **Step 3: Delete the engine and the backfill**

```bash
git rm -r packages/supabase/supabase/functions/_shared/compose-and-write.ts packages/supabase/supabase/functions/_shared/engine packages/supabase/supabase/functions/backfill-pebble-render packages/supabase/scripts/backfill-renders.ts packages/supabase/scripts/smoke-test-engine.ts
```

In `package.json`, `"test": "deno test supabase/functions/sweep-orphan-snaps/"`.

- [ ] **Step 4: Check**

Run: `npm run lint --workspace=packages/supabase && npm run test --workspace=packages/supabase`
Expected: `deno check` clean (the lint glob no longer finds the deleted scripts), sweep tests pass.

- [ ] **Step 5: Commit**

```bash
git add -A packages/supabase
git commit -m "chore(api): compose edge functions stop rendering and the engine is removed"
```

Push as Part 4 with Task 7.

---

## Task 9: Push, deploy, regenerate types

This is the irreversible step. Do it only after Parts 1–4 are reviewed and the full rehearsal (`run.sh 20261012090000`) passed on fresh production rows the same day.

- [ ] **Step 1: Push the five migrations** — `cd packages/supabase && npx supabase db push`. Expected: five files applied.
- [ ] **Step 2: Deploy the edge functions** — `npx supabase functions deploy compose-pebble && npx supabase functions deploy compose-pebble-update && npx supabase functions delete backfill-pebble-render`.
- [ ] **Step 3: Types** — `npm run db:types:remote --workspace=packages/supabase`; `git diff --stat packages/supabase/types/database.ts` must show `moments`, `v_moments_full`, `save_moment`, `complete_pebble`, `path_moments`, no `pebble_drafts`, no `render_svg` on `pebbles`. If the file shrank to a few lines the generation failed: restore it and re-run.
- [ ] **Step 4: Typecheck web and admin against the new types** — `npm run build --workspace=apps/web && npm run build --workspace=apps/admin`. Expected: web compiles (it reads `v_pebbles_full`, `path_pebbles`, `create_pebble` via the edge function — all kept). Admin may fail on `pebble_drafts` or `render_svg` references: fix only type errors, with minimal edits, and list them in the PR body for Part 5 of the milestone.
- [ ] **Step 5: Commit**

```bash
git add packages/supabase/types/database.ts apps/web apps/admin
git commit -m "chore(db): regenerate types for the moments schema"
```

---

## Task 10: `verify-moments.ts`

**Files:**
- Create: `packages/supabase/scripts/verify-moments.ts`
- Modify: `packages/supabase/package.json`, `.github/workflows/supabase.yml`, `packages/supabase/CLAUDE.md` (harness table)

- [ ] **Step 1: Write the harness**

```ts
#!/usr/bin/env -S deno run --allow-env --allow-net
/**
 * Acceptance test for the moments / pebbles split — runs against the REMOTE project.
 *
 *   1. A moment needs a date plus a name or a photo; saving one costs no karma.
 *   2. A pebble needs emotion, valence and a domain; the first completion is the
 *      reward (one pebble_created event), later edits are enrichment deltas.
 *   3. The glyph is derived: domain default first, a collection glyph wins once set.
 *   4. Readers: path_moments shows a null pebble for an uncompleted moment;
 *      v_moments_full nests only the caller's pebble; a second user reads
 *      nothing until the pebble is public, and never the sharer's pebble body.
 *   5. Compat: create_pebble + v_pebbles_full + path_pebbles still answer.
 *   6. Whole-second timestamps on every write.
 *   7. delete_pebble keeps the moment; delete_moment removes all and reverses karma.
 *
 * Run: set -a; . ./.env; set +a; npm run db:verify:moments --workspace=packages/supabase
 * Needs NO service-role key. Cleanup through the real delete-account function.
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

const runId = crypto.randomUUID().slice(0, 8);
const password = `Moments-${crypto.randomUUID()}`;
type TestUser = { client: SupabaseClient; id: string; token: string };

async function signUp(label: string): Promise<TestUser> {
  const client = createClient(SUPABASE_URL!, ANON_KEY!, { auth: { persistSession: false } });
  const email = `moments-verify-${label}-${runId}@example.test`;
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

async function karmaSum(client: SupabaseClient): Promise<number> {
  const { data } = await client.from("karma_events").select("delta");
  return (data ?? []).reduce((acc: number, r: { delta: number }) => acc + r.delta, 0);
}

let alice: TestUser | null = null;
let bob: TestUser | null = null;

try {
  alice = await signUp("a");
  bob = await signUp("b");
  const a = alice.client;
  const b = bob.client;
  console.log(`alice=${alice.id} bob=${bob.id}\n`);

  const { data: emotion } = await a.from("emotions").select("id").limit(1).single();
  const { data: domain } = await a.from("domains").select("id, default_glyph_id").not("default_glyph_id", "is", null).limit(1).single();
  if (!emotion || !domain) throw new Error("reference data missing (emotion / domain with a default glyph)");

  // 1. moments ----------------------------------------------------------------
  const empty = await a.rpc("save_moment", { payload: {} });
  check("a moment with neither name nor photo is refused", !!empty.error && empty.error.message.includes("moment_empty"), empty.error?.message);

  const saved = await a.rpc("save_moment", { payload: { name: `coffee ${runId}`, happened_at: "2026-10-11T10:00:00.123Z" } });
  check("a name alone saves a moment", !saved.error && typeof saved.data === "string", saved.error?.message);
  const momentId = saved.data as string;
  check("saving a moment grants no karma", (await karmaSum(a)) === 0);

  const { data: m1 } = await a.from("moments").select("happened_at").eq("id", momentId).single();
  check("happened_at is stored at whole seconds", !!m1 && new Date(m1.happened_at).getMilliseconds() === 0, m1?.happened_at);

  const { data: path1 } = await a.rpc("path_moments");
  const row1 = (path1 ?? []).find((r: { id: string }) => r.id === momentId);
  check("path_moments lists the moment with a null pebble", !!row1 && row1.pebble === null);

  // 2. completion ---------------------------------------------------------------
  const noDomain = await a.rpc("complete_pebble", { payload: { moment_id: momentId, emotion_id: emotion.id, intensity: 2, positiveness: 1 } });
  check("a pebble without a domain is refused", !!noDomain.error && noDomain.error.message.includes("pebble_needs_domain"), noDomain.error?.message);

  const done = await a.rpc("complete_pebble", { payload: { moment_id: momentId, emotion_id: emotion.id, intensity: 2, positiveness: 1, domain_ids: [domain.id] } });
  check("emotion + valence + domain completes the pebble", !done.error && typeof done.data === "string", done.error?.message);
  const pebbleId = done.data as string;

  const { data: events1 } = await a.from("karma_events").select("reason, delta").eq("ref_id", pebbleId);
  check("the first completion writes exactly one pebble_created event", (events1 ?? []).length === 1 && events1![0].reason === "pebble_created");

  const again = await a.rpc("complete_pebble", { payload: { moment_id: momentId, emotion_id: emotion.id, intensity: 3, positiveness: 1, domain_ids: [domain.id] } });
  check("completing again updates the same pebble", !again.error && again.data === pebbleId, again.error?.message);
  const { count: pebbleCount } = await a.from("pebbles").select("*", { count: "exact", head: true }).eq("moment_id", momentId);
  check("one pebble per (moment, user)", pebbleCount === 1);

  // 3. glyph --------------------------------------------------------------------
  const { data: full1 } = await a.from("v_moments_full").select("pebble").eq("id", momentId).single();
  check("the glyph resolves from the domain default", full1?.pebble?.glyph?.source === "domain" && full1?.pebble?.glyph?.id === domain.default_glyph_id, JSON.stringify(full1?.pebble?.glyph));

  const { data: coll } = await a.from("collections").insert({ user_id: alice.id, name: `trip ${runId}` }).select("id").single();
  const setGlyph = await a.rpc("set_collection_glyph", { p_collection_id: coll!.id, p_glyph_id: domain.default_glyph_id });
  check("a collection can carry a usable glyph", !setGlyph.error, setGlyph.error?.message);
  await a.rpc("complete_pebble", { payload: { moment_id: momentId, collection_ids: [coll!.id] } });
  const { data: full2 } = await a.from("v_moments_full").select("pebble").eq("id", momentId).single();
  check("a collection glyph wins over the domain", full2?.pebble?.glyph?.source === "collection", JSON.stringify(full2?.pebble?.glyph));

  // 4. readers and RLS ------------------------------------------------------------
  const { data: bobSees1 } = await b.from("v_moments_full").select("id").eq("id", momentId);
  check("a secret pebble's moment is invisible to another user", (bobSees1 ?? []).length === 0);
  await a.rpc("complete_pebble", { payload: { moment_id: momentId, visibility: "public" } });
  const { data: bobSees2 } = await b.from("v_moments_full").select("id, name, pebble").eq("id", momentId);
  check("a public pebble opens its moment to another user", (bobSees2 ?? []).length === 1 && bobSees2![0].name === `coffee ${runId}`);
  check("the other user never gets the sharer's pebble body", (bobSees2 ?? []).length === 1 && bobSees2![0].pebble === null);
  const { data: bobPebbles } = await b.from("pebbles").select("id").eq("moment_id", momentId);
  check("pebbles_select still shows the public pebble row", (bobPebbles ?? []).length === 1);

  const shared = await b.rpc("get_shared_pebble", { p_pebble_id: pebbleId });
  check("get_shared_pebble carries the moment name and a glyph", !shared.error && shared.data?.name === `coffee ${runId}` && shared.data?.glyph?.id, shared.error?.message);

  // 5. compat -----------------------------------------------------------------------
  const compat = await a.rpc("create_pebble", { payload: { name: `compat ${runId}`, happened_at: "2026-10-11T11:00:00Z", emotion_id: emotion.id, intensity: 1, positiveness: 0, domain_ids: [domain.id], glyph_id: null } });
  check("create_pebble wrapper still returns a pebble id", !compat.error && typeof compat.data === "string", compat.error?.message);
  const { data: vpf } = await a.from("v_pebbles_full").select("id, name, glyph_id, happened_at").eq("id", compat.data as string).single();
  check("v_pebbles_full compat row carries name and resolved glyph_id", vpf?.name === `compat ${runId}` && vpf?.glyph_id === domain.default_glyph_id);
  const { data: pp } = await a.rpc("path_pebbles");
  check("path_pebbles compat answers with render_svg null", Array.isArray(pp) && pp.length >= 2 && pp.every((r: { render_svg: unknown }) => r.render_svg === null));
  await a.rpc("delete_moment", { p_moment_id: (await a.from("pebbles").select("moment_id").eq("id", compat.data as string).single()).data!.moment_id });

  // 6/7. deletes ---------------------------------------------------------------------
  const delP = await a.rpc("delete_pebble", { p_pebble_id: pebbleId });
  check("delete_pebble removes the pebble", !delP.error, delP.error?.message);
  const { data: stillThere } = await a.from("moments").select("id").eq("id", momentId);
  check("the moment survives its pebble", (stillThere ?? []).length === 1);
  check("deleting the pebble reverses its karma", (await karmaSum(a)) === 0);

  const delM = await a.rpc("delete_moment", { p_moment_id: momentId });
  check("delete_moment returns the snap paths to remove", !delM.error && Array.isArray(delM.data?.storage_paths), delM.error?.message);
  const { data: gone } = await a.from("moments").select("id").eq("id", momentId);
  check("the moment is gone", (gone ?? []).length === 0);
} catch (err) {
  failed += 1;
  console.log(`✗ harness threw — ${err instanceof Error ? err.message : String(err)}`);
} finally {
  for (const u of [alice, bob]) {
    if (!u) continue;
    const res = await deleteAccount(u.token);
    console.log(`cleanup ${u.id}: ${res.status}`);
  }
}

console.log(`\nSummary: passed=${passed} failed=${failed}`);
Deno.exit(failed === 0 ? 0 : 1);
```

- [ ] **Step 2: Wire it up**

`package.json`: add `"db:verify:moments": "deno run --allow-env --allow-net scripts/verify-moments.ts"`, remove `db:verify:drafts`, and in `db:verify` replace `npm run db:verify:drafts` with `npm run db:verify:moments`.

`.github/workflows/supabase.yml`: replace the `Pebble drafts (M47 draft lifecycle)` step with

```yaml
      - name: Moments and pebbles (split contract)
        if: ${{ !cancelled() }}
        run: .github/scripts/verify-harness.sh "moments" db:verify:moments
```

(keep the `if:` form the neighbouring steps use). `packages/supabase/CLAUDE.md`: replace the drafts row of the harness table with `db:verify:moments` / `verify-moments.ts` / "the moments / pebbles split contract" / anon, and add `moments-verify-` to the orphan prefixes.

- [ ] **Step 3: Run it**

Run: `set -a; . ./.env; set +a; npm run db:verify:moments --workspace=packages/supabase`
Expected: `Summary: passed=26 failed=0` (count the `check(` calls if you changed any).

- [ ] **Step 4: Commit**

```bash
git rm packages/supabase/scripts/verify-pebble-drafts.ts
git add packages/supabase/scripts/verify-moments.ts packages/supabase/package.json .github/workflows/supabase.yml packages/supabase/CLAUDE.md
git commit -m "test(db): prove the moments contract and retire the drafts harness"
```

---

## Task 11: Update the five harnesses that touch the moved columns

**Files:**
- Modify: `packages/supabase/scripts/verify-pebble-visibility.ts`, `verify-content-reports.ts`, `verify-moderation-state.ts`, `verify-orphan-snap-sweep.ts`, `verify-account-purge.ts`

Each harness builds pebbles through `compose-pebble` or `create_pebble` and then asserts on columns. Apply the contract mapping below, run the harness, and fix every assertion the mapping explains. An assertion the mapping does **not** explain is a bug in the migrations, not in the harness: stop and fix the migration.

| Harness reads or writes | Now |
|---|---|
| `pebbles.name`, `description`, `happened_at` | the same columns on `moments`, joined via `pebbles.moment_id` |
| `pebbles.hidden_at` / `hidden_by`, `admin_set_content_hidden('pebble', pebbleId)` | `moments.hidden_at`; kind `'moment'` with the **moment** id (equal to the pebble id only for legacy rows — read `moment_id` from the pebble) |
| `report_content('pebble', pebbleId)` | still accepted; the stored row has `target_kind = 'moment'` and `target_id = moment_id` |
| `snaps.pebble_id` | `snaps.moment_id` |
| `pebble_drafts` seed and the `pebble_drafts` key in purge counts | removed; `verify-account-purge.ts` seeds one bare moment (`save_moment` with a name) plus one completed pebble and asserts `moments`, `pebbles`, `snaps` all reach zero rows and appear in `counts` |
| `render_svg` in compose responses | `null` |
| `get_shared_pebble(...).render_svg` | `null`; assert `glyph.id` instead |
| `delete_pebble_media` | `delete_moment_media` |
| `collections` glyph in purge kept-glyph logic | a glyph on another user's collection keeps the glyph (anonymised), mirror of the souls case |

- [ ] **Step 1–5:** one harness at a time: edit, run (`npm run db:verify:<name> --workspace=packages/supabase`; purge needs `SUPABASE_SERVICE_ROLE_KEY`), expect `failed=0`, commit `test(db): <harness> follows the moments split`.

- [ ] **Step 6: Run the whole gate** — `npm run db:verify --workspace=packages/supabase` then `npm run db:verify:purge --workspace=packages/supabase`. Expected: every harness `failed=0`. Paste the purge summary line in the PR body.

---

## Task 12: Decision log, Arkaik, PR

- [ ] **Step 1: Decision log** — append to `docs/decisions/log.md`:

```markdown
## 2026-10-12 — A moment is what happened, a pebble is what it meant to one person; a pebble exists only when complete; its glyph is derived (#1046)

- **Status:** taken
- **Scope:** db, api
- **Context:** Capture was eleven steps and dissuasive. Glyphs were pebble-specific. The strategy audit's north star is one moment shared by a few people, each with their own reading of it, which a single `pebbles` row cannot hold.
- **Decision:** `moments` (date, name, description, snaps, moderation) and `pebbles` (emotion, valence, domains, souls, collections, cards, grade), joined by `pebbles.moment_id`, unique per (moment, user). A pebble row is inserted only by `complete_pebble`, which refuses a missing emotion, valence or domain. Karma and achievements therefore keep counting `pebbles` rows unchanged. The glyph is `resolve_pebble_glyph`: oldest collection carrying one, else first domain default. `pebbles.glyph_id`, `render_svg`, `render_version` and `pebble_drafts` are gone; the compose edge functions no longer render. Legacy pebble ids were reused as moment ids.
- **Why:** One identity through the migration keeps snap paths, share links and report targets valid. Splitting now avoids a second identity migration when participants arrive. Deriving the glyph removes a step and a render pipeline.
- **Consequences:** `create_pebble`, `update_pebble`, `v_pebbles_full` and `path_pebbles` are deprecated compat objects for web only and die with the web composer. `moments_select` and `pebbles_select` reference each other only through `private.moment_is_hidden`; a new cross-user read path on either table must keep that shape or it recurses. Participants later widen `moments_select` only. Installed iOS/Android builds broke at this push until their composers shipped (accepted pre-launch).
- **Supersedes / Superseded-by:** Supersedes the M47 drafts design (`2026-07-29-drafts-and-autosave-design.md`) for server drafts; local autosave stands.
- **Refs:** #1046, `docs/superpowers/specs/2026-10-11-moments-and-pebbles-split-design.md`, migrations `20261012090000`–`20261012090400`, `packages/supabase/scripts/verify-moments.ts`.
```

- [ ] **Step 2: Arkaik** (hosted, `arkaik-mcp` with `ARKAIK_TOKEN`; if the tools are unavailable say so and stop): `create_node` data model **Moment**; `update_node` **Pebble** (fields moved, moment reference), **Collection** (glyph); `create_node` endpoints `save_moment`, `complete_pebble`, `delete_moment`, `path_moments`, `v_moments_full`; mark `create_pebble`, `update_pebble`, `path_pebbles`, `v_pebbles_full` deprecated and the drafts endpoints retired; edges Moment→Pebble, Pebble→Collection. Move the backend acceptance to `development`.

- [ ] **Step 3: Commit and PR**

```bash
git add docs/decisions/log.md
git commit -m "docs(core): record the moments and pebbles split decision"
```

Part 5 PR body: `Resolves #1046`, the five migration files, the harness results table, the admin type fixes from Task 9 if any, the compat objects list and the statement that mobile builds are broken until Parts 2 and 3 of the milestone. Labels `feat` `db` `supabase`, `no-lab-note`, the milestone.

---

## Self-review against the spec

- §2 schema: Task 2 (moments, pebbles, snaps, collections, reports, drafts, backfill). ✓
- §3 writers: Task 4. `set_collection_glyph` ✓, `compute_karma_delta` ✓, `delete_moment` ✓.
- §4 readers: Task 5 (`resolve_pebble_glyph`, `v_moments_full`, `path_moments`, `get_shared_pebble`); moderation, reports, purge, analytics, orphans: Task 6. `check_achievements` unchanged by design (clients call it).
- §5 compat: Task 7 + the edge functions in Task 8.
- §7 harnesses: Tasks 10–11. Purge harness manual run: Task 11 step 6.
- §8 risks: rehearsal Task 1, purge union Task 6 header, compat proof Task 10 §5 and Task 9 step 4.
