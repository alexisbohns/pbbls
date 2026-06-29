-- Spend karma. Guarded + race-safe. Called by C (glyph buy) and future goods.
-- p_amount is POSITIVE karma to debit; recorded as a negative withdraw delta.
create or replace function public.spend_karma(
  p_amount integer,
  p_reason text,                 -- 'purchase'
  p_ref_id uuid default null
) returns uuid
language plpgsql security definer set search_path = public as $$
declare
  v_user_id  uuid := auth.uid();
  v_balance  integer;
  v_event_id uuid;
begin
  if v_user_id is null then raise exception 'not_authenticated' using errcode='42501'; end if;
  if p_amount is null or p_amount <= 0 then raise exception 'invalid_amount'; end if;
  if p_reason not in ('purchase') then raise exception 'invalid_reason'; end if;

  -- Serialize concurrent spends for this user; create the row if first-ever.
  insert into public.wallet_balances (user_id, balance)
    values (v_user_id, 0) on conflict (user_id) do nothing;
  select balance into v_balance
    from public.wallet_balances where user_id = v_user_id for update;

  if v_balance < p_amount then
    raise exception 'insufficient_karma' using errcode='P0001',
      detail = format('balance=%s amount=%s', v_balance, p_amount);
  end if;

  insert into public.karma_events (user_id, type, delta, reason, ref_id)
  values (v_user_id, 'withdraw', -p_amount, p_reason, p_ref_id)
  returning id into v_event_id;   -- trigger updates wallet_balances in this txn

  return v_event_id;
end;
$$;

-- Refund. Symmetric reversal (positive withdraw-side delta).
create or replace function public.refund_karma(
  p_amount integer,
  p_ref_id uuid
) returns uuid
language plpgsql security definer set search_path = public as $$
declare
  v_user_id  uuid := auth.uid();
  v_event_id uuid;
begin
  if v_user_id is null then raise exception 'not_authenticated' using errcode='42501'; end if;
  if p_amount is null or p_amount <= 0 then raise exception 'invalid_amount'; end if;

  insert into public.karma_events (user_id, type, delta, reason, ref_id)
  values (v_user_id, 'withdraw', p_amount, 'refund', p_ref_id)
  returning id into v_event_id;
  return v_event_id;
end;
$$;

revoke all on function public.spend_karma(integer,text,uuid)  from public, anon;
revoke all on function public.refund_karma(integer,uuid)      from public, anon;
grant execute on function public.spend_karma(integer,text,uuid)  to authenticated;
grant execute on function public.refund_karma(integer,uuid)      to authenticated;
