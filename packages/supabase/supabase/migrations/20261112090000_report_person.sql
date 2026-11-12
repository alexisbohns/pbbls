-- report_person (#1008, #838): report a connected peer or an inviter.
--
-- report_content's 'profile' branch takes the person's user_id and requires a
-- public profile. The connection projections (get_connections,
-- preview_connection_invite) never hand a client a user_id (standing rule:
-- cross-user reads never return one), so a connected peer or an inviter with
-- a private profile could not be reported at all. This RPC takes the ROUTE the
-- reporter met the person by (their connection id, or the invite token they
-- hold) and resolves the person here.
--
-- It files an ordinary 'profile' report (target_id = target_user_id = the
-- person), so the admin queue, admin_set_content_hidden('profile', …) and
-- content_reports_open_unique work unchanged: a person reported from their
-- invite and again after connecting is ONE open report. The snapshot records
-- the route in `via`.
--
-- report_content is deliberately not re-emitted. remove_connection is CALLED,
-- never re-emitted, so its in-body append markers stay intact.
create or replace function public.report_person(
  p_reason           text,
  p_detail           text    default null,
  p_connection_id    uuid    default null,
  p_invite_token     text    default null,
  p_remove_and_block boolean default false
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user     uuid := auth.uid();
  v_person   uuid;
  v_via      text;
  v_conn     public.connections;
  v_invite   public.connection_invites;
  v_snapshot jsonb;
  v_row      public.content_reports;
begin
  if v_user is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;

  -- Exactly one route. Both or neither is a malformed call, reported with the
  -- same slug report_content uses for an unknown target kind.
  if (p_connection_id is null) = (p_invite_token is null) then
    raise exception 'invalid_kind';
  end if;

  if p_reason is null
     or p_reason not in ('sexual', 'violence', 'hate', 'harassment',
                         'self_harm', 'illegal', 'spam', 'impersonation', 'other') then
    raise exception 'invalid_reason';
  end if;

  if p_connection_id is not null then
    -- A foreign connection and a missing one answer the same not_found, as
    -- remove_connection does: never confirm to a non-member that the row exists.
    select * into v_conn from public.connections where id = p_connection_id;
    if not found or v_user not in (v_conn.user_a, v_conn.user_b) then
      raise exception 'not_found';
    end if;
    v_person := case when v_conn.user_a = v_user then v_conn.user_b else v_conn.user_a end;
    v_via := 'connection';
  else
    -- preview_connection_invite's gates (20261007140000): an unknown, revoked
    -- or expired token, an inviter who has blocked the caller, and a hidden
    -- inviter all answer one not_found. A token the caller could not preview
    -- must not be a way to file against its inviter.
    select * into v_invite from public.connection_invites where token = p_invite_token;
    if not found
       or v_invite.revoked_at is not null
       or v_invite.expires_at <= now()
       or exists (
            select 1 from public.connection_blocks b
             where b.blocker_id = v_invite.inviter_id
               and b.blocked_id = v_user)
       or not exists (
            select 1 from public.profiles pr
             where pr.user_id = v_invite.inviter_id
               and pr.hidden_at is null)
    then
      raise exception 'not_found';
    end if;
    if v_invite.inviter_id = v_user then
      raise exception 'cannot_report_own';
    end if;
    v_person := v_invite.inviter_id;
    v_via := 'invite';
  end if;

  -- The person as they read at file time, plus the route. Only the TARGET's
  -- text, so purge-by-target_user_id erases it (same rule as report_content).
  select jsonb_build_object('handle', pr.handle, 'display_name', pr.display_name, 'via', v_via)
    into v_snapshot
    from public.profiles pr
   where pr.user_id = v_person;
  v_snapshot := coalesce(v_snapshot, jsonb_build_object('via', v_via));

  -- Idempotent on content_reports_open_unique, exactly as report_content: a
  -- second file is a success and returns the standing report.
  insert into public.content_reports
    (reporter_id, target_kind, target_id, target_user_id, reason, detail, target_snapshot)
  values
    (v_user, 'profile', v_person, v_person, p_reason,
     nullif(btrim(p_detail), ''), v_snapshot)
  on conflict (reporter_id, target_kind, target_id) where status = 'open'
  do nothing
  returning * into v_row;

  if v_row.id is null then
    select * into v_row
      from public.content_reports
     where reporter_id = v_user
       and target_kind = 'profile'
       and target_id   = v_person
       and status      = 'open';
  end if;

  -- Report and block in one transaction. Only the connection route can block:
  -- an invite has no connection yet.
  if v_via = 'connection' and coalesce(p_remove_and_block, false) then
    perform public.remove_connection(p_connection_id, true);
  end if;

  -- report_content's projection: never a user id, never the snapshot;
  -- created_at whole-second UTC (standing timestamp rule).
  return jsonb_build_object(
    'id', v_row.id,
    'status', v_row.status,
    'created_at', to_char(v_row.created_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
  );
end;
$$;

revoke all on function public.report_person(text, text, uuid, text, boolean) from public, anon;
grant execute on function public.report_person(text, text, uuid, text, boolean) to authenticated;
