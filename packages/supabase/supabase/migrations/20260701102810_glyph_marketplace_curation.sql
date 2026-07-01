-- =============================================================================
-- Glyph marketplace curation (#497): delist toggle, creator payout, admin
-- delete, and glyph attribution (transfer ownership to a real user).
--   - glyph_submissions.listed: an approved glyph can be pulled from the market
--     (no longer buyable) without deleting it or revoking existing owners.
--   - buy_glyph now credits the glyph's owner (glyphs.user_id) the full price as
--     a `glyph_sale` karma credit — a net-zero transfer (buyer withdraws, creator
--     credits), so no karma is minted. Admin-owned (unattributed) glyphs pay the
--     admin account.
--   - admin RPCs (is_admin-gated): set_glyph_listed, admin_delete_glyph,
--     admin_find_user, admin_attribute_glyph.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Delist flag
-- ---------------------------------------------------------------------------
alter table public.glyph_submissions
  add column listed boolean not null default true;

-- ---------------------------------------------------------------------------
-- 2. Allow the `glyph_sale` credit reason on the karma ledger
-- ---------------------------------------------------------------------------
alter table public.karma_events drop constraint if exists karma_events_reason_check;
alter table public.karma_events add constraint karma_events_reason_check check (reason in (
  'pebble_created','pebble_enriched','pebble_deleted','grant','glyph_sale',  -- credit
  'purchase','refund'                                                        -- withdraw
));

-- ---------------------------------------------------------------------------
-- 3. Market view — approved AND listed only
-- ---------------------------------------------------------------------------
create or replace view public.v_glyph_market with (security_invoker = true) as
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
where s.status = 'approved' and s.listed;

-- ---------------------------------------------------------------------------
-- 4. buy_glyph — require listed; pay the creator the full price (net-zero)
-- ---------------------------------------------------------------------------
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

  -- Must be an approved AND listed (in-market) listing.
  select s.price, g.user_id into v_price, v_owner
  from public.glyph_submissions s
  join public.glyphs g on g.id = s.glyph_id
  where s.glyph_id = p_glyph_id and s.status = 'approved' and s.listed
  limit 1;
  if v_price is null then raise exception 'not_in_market'; end if;

  if v_owner = v_user then raise exception 'cannot_buy_own'; end if;

  if exists (select 1 from public.glyph_entitlements e
             where e.user_id = v_user and e.glyph_id = p_glyph_id) then
    raise exception 'already_owned';
  end if;

  -- Spend (row-locked, raises insufficient_karma); records a withdraw event.
  v_event := public.spend_karma(v_price, 'purchase', p_glyph_id);

  -- Grant. unique(user_id, glyph_id) is the race backstop.
  insert into public.glyph_entitlements (user_id, glyph_id, karma_event_id, price_paid)
  values (v_user, p_glyph_id, v_event, v_price)
  returning id into v_ent;

  -- Pay the creator (glyph owner) the full price — a net-zero transfer. The
  -- wallet trigger applies this credit to their balance in the same txn.
  if v_owner is not null then
    insert into public.karma_events (user_id, delta, type, reason, ref_id)
    values (v_owner, v_price, 'credit', 'glyph_sale', p_glyph_id);
  end if;

  select balance into v_balance from public.wallet_balances where user_id = v_user;

  return jsonb_build_object('entitlement_id', v_ent, 'balance', v_balance);
end;
$$;

-- ---------------------------------------------------------------------------
-- 5. Admin curation RPCs (all is_admin-gated, security definer)
-- ---------------------------------------------------------------------------

-- Delist / relist an approved glyph (owners keep it; it leaves the market).
create or replace function public.set_glyph_listed(p_submission_id uuid, p_listed boolean)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare v_row public.glyph_submissions;
begin
  if not public.is_admin(auth.uid()) then raise exception 'not_admin' using errcode='42501'; end if;
  select * into v_row from public.glyph_submissions where id = p_submission_id;
  if not found then raise exception 'not_found'; end if;
  if v_row.status <> 'approved' then raise exception 'invalid_state'; end if;
  update public.glyph_submissions set listed = p_listed where id = p_submission_id
  returning * into v_row;
  return to_jsonb(v_row);
end;
$$;

-- Hard-delete a glyph. Cascades to its submission + any entitlements (buyers
-- lose it) via the on-delete-cascade FKs. Destructive, admin-only.
create or replace function public.admin_delete_glyph(p_glyph_id uuid)
returns void
language plpgsql security definer set search_path = public as $$
begin
  if not public.is_admin(auth.uid()) then raise exception 'not_admin' using errcode='42501'; end if;
  if not exists (select 1 from public.glyphs where id = p_glyph_id) then
    raise exception 'not_found';
  end if;
  delete from public.glyphs where id = p_glyph_id;
end;
$$;

-- Resolve a user by email for attribution. Returns { id, email } or null.
create or replace function public.admin_find_user(p_email text)
returns jsonb
language plpgsql security definer set search_path = public, auth as $$
declare v_id uuid; v_email text;
begin
  if not public.is_admin(auth.uid()) then raise exception 'not_admin' using errcode='42501'; end if;
  select id, email into v_id, v_email from auth.users
  where lower(email) = lower(btrim(p_email)) limit 1;
  if v_id is null then return null; end if;
  return jsonb_build_object('id', v_id, 'email', v_email);
end;
$$;

-- Attribute a glyph to a user: transfer ownership. They become the creator —
-- it appears in their gallery, they can't buy it, and payouts route to them.
create or replace function public.admin_attribute_glyph(p_glyph_id uuid, p_user_id uuid)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare v_glyph_id uuid;
begin
  if not public.is_admin(auth.uid()) then raise exception 'not_admin' using errcode='42501'; end if;
  if not exists (select 1 from auth.users where id = p_user_id) then
    raise exception 'user_not_found';
  end if;
  update public.glyphs set user_id = p_user_id where id = p_glyph_id
  returning id into v_glyph_id;
  if v_glyph_id is null then raise exception 'not_found'; end if;
  return jsonb_build_object('glyph_id', v_glyph_id, 'user_id', p_user_id);
end;
$$;

-- ---------------------------------------------------------------------------
-- 6. admin_list_glyph_submissions — add `listed` + owner (creator) info
-- ---------------------------------------------------------------------------
create or replace function public.admin_list_glyph_submissions(p_status text default null)
returns jsonb
language plpgsql security definer set search_path = public, auth as $$
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
      s.id           as submission_id,
      s.glyph_id     as glyph_id,
      s.status       as status,
      s.listed       as listed,
      s.price        as price,
      s.review_note  as review_note,
      s.created_at   as created_at,
      s.reviewed_at  as reviewed_at,
      s.submitter_id as submitter_id,
      su.email       as submitter_email,
      g.user_id      as owner_id,
      ou.email       as owner_email,
      g.name         as name,
      g.shape_id     as shape_id,
      g.strokes      as strokes,
      g.view_box     as view_box
    from public.glyph_submissions s
    join public.glyphs g on g.id = s.glyph_id
    left join auth.users su on su.id = s.submitter_id
    left join auth.users ou on ou.id = g.user_id
    where p_status is null or s.status = p_status
  ) t;

  return v_result;
end;
$$;

-- ---------------------------------------------------------------------------
-- 7. Grants
-- ---------------------------------------------------------------------------
revoke all on function public.set_glyph_listed(uuid, boolean)     from public, anon;
revoke all on function public.admin_delete_glyph(uuid)            from public, anon;
revoke all on function public.admin_find_user(text)               from public, anon;
revoke all on function public.admin_attribute_glyph(uuid, uuid)   from public, anon;
grant execute on function public.set_glyph_listed(uuid, boolean)     to authenticated;
grant execute on function public.admin_delete_glyph(uuid)            to authenticated;
grant execute on function public.admin_find_user(text)               to authenticated;
grant execute on function public.admin_attribute_glyph(uuid, uuid)   to authenticated;
