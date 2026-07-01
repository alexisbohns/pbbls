-- =============================================================================
-- Admin glyph moderation (#497) — reject reason + is_admin-gated RPCs for the
-- moderation queue (read), approve/reject/re-price, and first-party SVG publish.
-- All RPCs are SECURITY DEFINER and guard on is_admin(auth.uid()); the queue
-- read path exists because the widened glyphs_select (D8) does NOT let an admin
-- read a *pending* submission's strokes via RLS.
-- =============================================================================

-- 1. Reject reason (null unless rejected). Submitter reads it via the existing
--    glyph_submissions_select policy (submitter_id = auth.uid()).
alter table public.glyph_submissions
  add column review_note text;

-- 2. Read path for the moderation queue. Joins glyphs (strokes/view_box/name)
--    and the submitter email; ordered oldest-first (FIFO review).
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
      s.price        as price,
      s.review_note  as review_note,
      s.created_at   as created_at,
      s.reviewed_at  as reviewed_at,
      s.submitter_id as submitter_id,
      u.email        as submitter_email,
      g.name         as name,
      g.shape_id     as shape_id,
      g.strokes      as strokes,
      g.view_box     as view_box
    from public.glyph_submissions s
    join public.glyphs g on g.id = s.glyph_id
    left join auth.users u on u.id = s.submitter_id
    where p_status is null or s.status = p_status
  ) t;

  return v_result;
end;
$$;

-- 3. Approve a pending submission → live in Market. Optional price override.
create or replace function public.approve_glyph(p_submission_id uuid, p_price integer default null)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_row public.glyph_submissions;
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'not_admin' using errcode = '42501';
  end if;
  if p_price is not null and p_price <= 0 then raise exception 'bad_price'; end if;

  select * into v_row from public.glyph_submissions where id = p_submission_id;
  if not found then raise exception 'not_found'; end if;
  if v_row.status <> 'pending' then raise exception 'invalid_state'; end if;

  update public.glyph_submissions
  set status = 'approved',
      price = coalesce(p_price, price),
      reviewed_at = now(),
      reviewed_by = auth.uid()
  where id = p_submission_id
  returning * into v_row;

  return to_jsonb(v_row);
end;
$$;

-- 4. Reject a pending submission with a required reason.
create or replace function public.reject_glyph(p_submission_id uuid, p_note text)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_row public.glyph_submissions;
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'not_admin' using errcode = '42501';
  end if;
  if p_note is null or btrim(p_note) = '' then raise exception 'missing_note'; end if;

  select * into v_row from public.glyph_submissions where id = p_submission_id;
  if not found then raise exception 'not_found'; end if;
  if v_row.status <> 'pending' then raise exception 'invalid_state'; end if;

  update public.glyph_submissions
  set status = 'rejected',
      review_note = btrim(p_note),
      reviewed_at = now(),
      reviewed_by = auth.uid()
  where id = p_submission_id
  returning * into v_row;

  return to_jsonb(v_row);
end;
$$;

-- 5. Re-price an approved listing (curation control). Existing entitlements'
--    price_paid snapshots are untouched (C's D4).
create or replace function public.set_glyph_price(p_submission_id uuid, p_price integer)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_row public.glyph_submissions;
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'not_admin' using errcode = '42501';
  end if;
  if p_price <= 0 then raise exception 'bad_price'; end if;

  select * into v_row from public.glyph_submissions where id = p_submission_id;
  if not found then raise exception 'not_found'; end if;
  if v_row.status <> 'approved' then raise exception 'invalid_state'; end if;

  update public.glyph_submissions
  set price = p_price
  where id = p_submission_id
  returning * into v_row;

  return to_jsonb(v_row);
end;
$$;

-- 6. Publish a first-party glyph from an uploaded SVG: insert the glyph
--    (owned by the admin) + an auto-approved submission, atomically.
create or replace function public.publish_admin_glyph(
  p_name text,
  p_shape_id uuid,
  p_strokes jsonb,
  p_view_box text,
  p_price integer
)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_user          uuid := auth.uid();
  v_glyph_id      uuid;
  v_submission_id uuid;
begin
  if not public.is_admin(v_user) then
    raise exception 'not_admin' using errcode = '42501';
  end if;
  if p_price <= 0 then raise exception 'bad_price'; end if;
  if p_strokes is null or jsonb_array_length(p_strokes) = 0 then
    raise exception 'empty_glyph';
  end if;

  insert into public.glyphs (user_id, name, shape_id, strokes, view_box)
  values (v_user, nullif(btrim(p_name), ''), p_shape_id, p_strokes, p_view_box)
  returning id into v_glyph_id;

  insert into public.glyph_submissions
    (glyph_id, submitter_id, status, price, reviewed_at, reviewed_by)
  values (v_glyph_id, v_user, 'approved', p_price, now(), v_user)
  returning id into v_submission_id;

  return jsonb_build_object('glyph_id', v_glyph_id, 'submission_id', v_submission_id);
end;
$$;

-- 7. Grants: authenticated only; the is_admin guard does the real gating.
revoke all on function public.admin_list_glyph_submissions(text)               from public, anon;
revoke all on function public.approve_glyph(uuid, integer)                      from public, anon;
revoke all on function public.reject_glyph(uuid, text)                          from public, anon;
revoke all on function public.set_glyph_price(uuid, integer)                    from public, anon;
revoke all on function public.publish_admin_glyph(text, uuid, jsonb, text, integer) from public, anon;
grant execute on function public.admin_list_glyph_submissions(text)               to authenticated;
grant execute on function public.approve_glyph(uuid, integer)                      to authenticated;
grant execute on function public.reject_glyph(uuid, text)                          to authenticated;
grant execute on function public.set_glyph_price(uuid, integer)                    to authenticated;
grant execute on function public.publish_admin_glyph(text, uuid, jsonb, text, integer) to authenticated;
