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
--
-- NOT safe to push alone. 20261012090100–20261012090400 re-emit the writers,
-- readers, purge, reports, analytics and orphan list this file breaks:
-- purge_account, list_orphan_snap_files, get_public_profile, get_shared_pebble,
-- report_content, admin_list_content_reports, admin_set_content_hidden,
-- create_pebble, update_pebble, path_pebbles, get_pebble_volume_series,
-- get_pebble_enrichment. Unnamed server drafts are discarded, so any files they
-- uploaded become orphans for the sweep (list_orphan_snap_files) by design.
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
comment on column public.moments.hidden_at is
  'Set by moderation (admin_set_content_hidden). Null = not hidden. The owner still sees the moment and its pebbles; every cross-user and anon path is dark.';
comment on column public.moments.hidden_by is
  'The admin who set hidden_at (auth.users). Set by moderation only; null when not hidden or the admin account is gone.';
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
-- The backfill is not an edit: keep pebbles.updated_at as it was.
alter table public.pebbles disable trigger pebbles_updated_at;
update public.pebbles set moment_id = id;
alter table public.pebbles enable trigger pebbles_updated_at;
alter table public.pebbles alter column moment_id set not null;
create unique index pebbles_moment_user_key on public.pebbles (moment_id, user_id);

-- 3. snaps hang off the moment --------------------------------------------------
alter table public.snaps rename column pebble_id to moment_id;
alter table public.snaps drop constraint snaps_pebble_id_fkey;
alter table public.snaps
  add constraint snaps_moment_id_fkey foreign key (moment_id) references public.moments(id) on delete cascade;
-- snaps_pebble_id_idx (20260411000001) already indexes this column; a rename
-- keeps it from sitting next to a duplicate.
alter index if exists public.snaps_pebble_id_idx rename to snaps_moment_id_idx;
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
    values (d.user_id, btrim(d.payload->>'name'), nullif(btrim(coalesce(d.payload->>'description', '')), ''),
            coalesce(v_when, d.created_at), d.created_at, d.updated_at)
    returning id into v_id;

    for s in select value from jsonb_array_elements(
               case when jsonb_typeof(d.payload->'snaps') = 'array' then d.payload->'snaps' else '[]'::jsonb end)
    loop
      if jsonb_typeof(s.value) = 'object' and nullif(s.value->>'storage_path', '') is not null then
        -- One malformed element (bad id or sort_order) skips that snap, not the push.
        begin
          insert into public.snaps (id, moment_id, user_id, storage_path, sort_order)
          values (coalesce(nullif(s.value->>'id', '')::uuid, gen_random_uuid()), v_id, d.user_id,
                  s.value->>'storage_path', coalesce((s.value->>'sort_order')::smallint, 0))
          on conflict (id) do nothing;
        exception when others then
          raise notice 'draft % snap skipped: %', d.id, sqlerrm;
        end;
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
-- Definer and no current_user exemption, same shape and reasons as
-- enforce_soul_glyph_usable (20260919160000) and the header of 20260919140000.
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
-- The old check forbids 'moment', so it goes before the retarget and comes back after.
alter table public.content_reports drop constraint content_reports_target_kind_check;
update public.content_reports set target_kind = 'moment' where target_kind = 'pebble';
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
      -- Deliberate defence in depth: pebbles_select already applies to this subquery.
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
create policy "moments_update" on public.moments for update to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
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
