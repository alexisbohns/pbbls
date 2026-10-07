-- ---------------------------------------------------------------------------
-- admin_list_glyph_submissions — stop materialising third-party emails (#766)
--
-- Data minimisation (GDPR Art. 5(1)(c)). The glyph moderation page is an async
-- Server Component, so every render pulled every listed submitter's and
-- owner's email address out of auth.users and into the admin runtime, only to
-- print the owner's as "Creator". submitter_email was never read at all.
--
-- Re-emitted from the latest body (20260701114205_drop_glyph_shape.sql §c),
-- with both auth.users joins and both email keys removed. People are now
-- identified the way admin_list_content_reports does it
-- (20260916090000_content_reports.sql §4): by the ids already projected here.
-- On top of the ids, the opt-in profiles.handle is projected when set, so a
-- creator who claimed one reads as a name rather than a uuid. handle is null
-- for most accounts; the client falls back to the id.
--
-- Unchanged on purpose: the is_admin guard, security definer, search_path,
-- the signature, the jsonb return (so types/database.ts does not move) and the
-- grants. The grants survive create or replace; they are restated below,
-- identical to 20260630084718 §7, so this file states the full posture.
--
-- admin_find_user (email search, 20260701102810) is deliberately left as is:
-- attribution needs to locate one specific person, which is an operator
-- looking one address up on purpose rather than every page view processing N.
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
      sp.handle      as submitter_handle,
      g.user_id      as owner_id,
      op.handle      as owner_handle,
      g.name         as name,
      g.strokes      as strokes,
      g.view_box     as view_box
    from public.glyph_submissions s
    join public.glyphs g on g.id = s.glyph_id
    left join public.profiles sp on sp.user_id = s.submitter_id
    left join public.profiles op on op.user_id = g.user_id
    where p_status is null or s.status = p_status
  ) t;

  return v_result;
end;
$$;

revoke all on function public.admin_list_glyph_submissions(text) from public, anon;
grant execute on function public.admin_list_glyph_submissions(text) to authenticated;
