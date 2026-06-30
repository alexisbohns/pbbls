-- =============================================================================
-- Glyph marketplace (#496) — submissions/listings, entitlements, favourites,
-- submit_glyph + buy_glyph RPCs, market view, and glyphs policy rewrite (D8).
-- Price is per-listing, flat-defaulted; GLYPH_PRICE_DEFAULT mirror in
-- apps/web/lib/config/glyphs.ts must stay in sync with the default literal below.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Tables
-- ---------------------------------------------------------------------------
create table public.glyph_submissions (
  id            uuid primary key default gen_random_uuid(),
  glyph_id      uuid not null references public.glyphs(id) on delete cascade,
  submitter_id  uuid not null references auth.users(id),
  status        text not null default 'pending'
                  check (status in ('pending','approved','rejected')),
  price         integer not null default 25 check (price > 0),  -- GLYPH_PRICE_DEFAULT
  created_at    timestamptz not null default now(),
  reviewed_at   timestamptz,
  reviewed_by   uuid references auth.users(id)
);

-- At most one active (pending|approved) submission per glyph.
create unique index glyph_submissions_one_active
  on public.glyph_submissions (glyph_id)
  where status in ('pending','approved');

create index glyph_submissions_status_idx on public.glyph_submissions (status);

create table public.glyph_entitlements (
  id              uuid primary key default gen_random_uuid(),
  user_id         uuid not null references auth.users(id),
  glyph_id        uuid not null references public.glyphs(id) on delete cascade,
  karma_event_id  uuid not null references public.karma_events(id),
  price_paid      integer not null check (price_paid > 0),
  created_at      timestamptz not null default now(),
  unique (user_id, glyph_id)
);

create index glyph_entitlements_glyph_idx on public.glyph_entitlements (glyph_id);

create table public.glyph_favourites (
  user_id     uuid not null references auth.users(id),
  glyph_id    uuid not null references public.glyphs(id) on delete cascade,
  created_at  timestamptz not null default now(),
  primary key (user_id, glyph_id)
);

-- ---------------------------------------------------------------------------
-- 2. RLS
-- ---------------------------------------------------------------------------
alter table public.glyph_submissions enable row level security;
alter table public.glyph_entitlements enable row level security;
alter table public.glyph_favourites   enable row level security;

create policy glyph_submissions_select on public.glyph_submissions for select
  to authenticated
  using (submitter_id = auth.uid() or status = 'approved');

create policy glyph_entitlements_select on public.glyph_entitlements for select
  to authenticated using (user_id = auth.uid());

create policy glyph_favourites_all on public.glyph_favourites for all
  to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

-- ---------------------------------------------------------------------------
-- 3. glyphs policy rewrite — widen SELECT, lock UPDATE/DELETE (D8)
--    Current (snapshot): glyphs_select = own ∪ null; update/delete = own.
-- ---------------------------------------------------------------------------
drop policy if exists "glyphs_select" on public.glyphs;
create policy "glyphs_select" on public.glyphs for select to authenticated
  using (
    user_id = auth.uid()
    or user_id is null
    or exists (select 1 from public.glyph_submissions s
               where s.glyph_id = glyphs.id and s.status = 'approved')
    or exists (select 1 from public.glyph_entitlements e
               where e.glyph_id = glyphs.id and e.user_id = auth.uid())
  );

drop policy if exists "glyphs_update" on public.glyphs;
create policy "glyphs_update" on public.glyphs for update to authenticated
  using (
    public.is_admin(auth.uid())
    or (
      user_id = auth.uid()
      and not exists (select 1 from public.glyph_submissions s
                      where s.glyph_id = glyphs.id and s.status in ('pending','approved'))
      and not exists (select 1 from public.glyph_entitlements e
                      where e.glyph_id = glyphs.id)
    )
  );

drop policy if exists "glyphs_delete" on public.glyphs;
create policy "glyphs_delete" on public.glyphs for delete to authenticated
  using (
    public.is_admin(auth.uid())
    or (
      user_id = auth.uid()
      and not exists (select 1 from public.glyph_submissions s
                      where s.glyph_id = glyphs.id and s.status in ('pending','approved'))
      and not exists (select 1 from public.glyph_entitlements e
                      where e.glyph_id = glyphs.id)
    )
  );

-- ---------------------------------------------------------------------------
-- 4. RPCs
-- ---------------------------------------------------------------------------
-- Submit one of your own custom glyphs to the community (lands pending for D).
create or replace function public.submit_glyph(p_glyph_id uuid)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_user  uuid := auth.uid();
  v_owner uuid;
  v_row   public.glyph_submissions;
begin
  if v_user is null then raise exception 'not_authenticated' using errcode='42501'; end if;

  select user_id into v_owner from public.glyphs where id = p_glyph_id;
  if v_owner is distinct from v_user then
    raise exception 'not_owner' using errcode='42501';  -- covers system glyphs (null owner)
  end if;

  if exists (select 1 from public.glyph_submissions s
             where s.glyph_id = p_glyph_id and s.status in ('pending','approved')) then
    raise exception 'already_submitted';
  end if;

  insert into public.glyph_submissions (glyph_id, submitter_id)
  values (p_glyph_id, v_user)
  returning * into v_row;

  return to_jsonb(v_row);
end;
$$;

-- Buy a community glyph: spend karma + grant use-rights, atomically.
create or replace function public.buy_glyph(p_glyph_id uuid)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_user    uuid := auth.uid();
  v_price   integer;
  v_owner   uuid;
  v_event   uuid;
  v_ent     uuid;
  v_balance integer;
begin
  if v_user is null then raise exception 'not_authenticated' using errcode='42501'; end if;

  -- Must be an approved (in-market) listing.
  select s.price, g.user_id into v_price, v_owner
  from public.glyph_submissions s
  join public.glyphs g on g.id = s.glyph_id
  where s.glyph_id = p_glyph_id and s.status = 'approved'
  limit 1;
  if v_price is null then raise exception 'not_in_market'; end if;

  if v_owner = v_user then raise exception 'cannot_buy_own'; end if;

  if exists (select 1 from public.glyph_entitlements e
             where e.user_id = v_user and e.glyph_id = p_glyph_id) then
    raise exception 'already_owned';
  end if;

  -- Spend (row-locked, raises insufficient_karma); records a withdraw event.
  v_event := public.spend_karma(v_price, 'purchase', p_glyph_id);

  -- Grant. unique(user_id, glyph_id) is the race backstop — a concurrent
  -- double-buy rolls back the loser's spend too (same txn).
  insert into public.glyph_entitlements (user_id, glyph_id, karma_event_id, price_paid)
  values (v_user, p_glyph_id, v_event, v_price)
  returning id into v_ent;

  select balance into v_balance from public.wallet_balances where user_id = v_user;

  return jsonb_build_object('entitlement_id', v_ent, 'balance', v_balance);
end;
$$;

revoke all on function public.submit_glyph(uuid) from public, anon;
revoke all on function public.buy_glyph(uuid)    from public, anon;
grant execute on function public.submit_glyph(uuid) to authenticated;
grant execute on function public.buy_glyph(uuid)    to authenticated;

-- ---------------------------------------------------------------------------
-- 5. Market view — approved listings + per-caller owned/favourited flags.
--    security_invoker so the caller's RLS (widened glyphs SELECT, own
--    entitlements/favourites) applies.
-- ---------------------------------------------------------------------------
create view public.v_glyph_market with (security_invoker = true) as
select
  g.id, g.user_id, g.name, g.shape_id, g.strokes, g.view_box,
  g.created_at, g.updated_at,
  s.price,
  exists (select 1 from public.glyph_entitlements e
          where e.glyph_id = g.id and e.user_id = auth.uid()) as owned,
  exists (select 1 from public.glyph_favourites f
          where f.glyph_id = g.id and f.user_id = auth.uid()) as favourited
from public.glyph_submissions s
join public.glyphs g on g.id = s.glyph_id
where s.status = 'approved';

revoke all on public.v_glyph_market from public, anon;
grant select on public.v_glyph_market to authenticated;
