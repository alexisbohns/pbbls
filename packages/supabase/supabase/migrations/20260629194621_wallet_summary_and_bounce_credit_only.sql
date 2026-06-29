-- 1. One-round-trip wallet summary. Filtered to the caller (mirrors how
--    v_karma_summary was hardened with an auth.uid() filter).
drop view if exists public.v_wallet_summary;
create view public.v_wallet_summary as
select
  wb.user_id,
  wb.balance,
  coalesce((select sum(ke.delta) from public.karma_events ke
            where ke.user_id = wb.user_id and ke.type='credit'), 0)::int   as total_earned,
  coalesce(-(select sum(ke.delta) from public.karma_events ke
            where ke.user_id = wb.user_id and ke.type='withdraw'), 0)::int as total_spent
from public.wallet_balances wb
where wb.user_id = auth.uid();

revoke all on public.v_wallet_summary from public, anon;
grant select on public.v_wallet_summary to authenticated;

-- 2. Keep the admin "bounce karma distribution" meaning EARNED, not spendable.
--    Now that withdraw events exist, the bounces snapshot must ignore them or
--    it silently becomes the spendable balance. Fold only credit-type events.
create or replace function public.apply_karma_event_to_bounce()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  -- Only earn-side movements contribute to the bounce (earned-karma) snapshot.
  if new.type <> 'credit' then
    return new;
  end if;
  insert into public.bounces (user_id, score, updated_at)
  values (new.user_id, new.delta, now())
  on conflict (user_id) do update
    set score      = public.bounces.score + excluded.score,
        updated_at = now();
  return new;
end;
$$;
-- Trigger definition is unchanged; CREATE OR REPLACE on the function is enough.

-- 3. Realign existing bounces snapshots to credit-only (no-op for current data,
--    since no withdraw events predate this, but makes the invariant explicit).
insert into public.bounces (user_id, score, updated_at)
select user_id, coalesce(sum(delta) filter (where type='credit'), 0)::int, now()
from public.karma_events group by user_id
on conflict (user_id) do update
  set score = excluded.score, updated_at = excluded.updated_at;
