-- O(1) balance reads + a single row to lock for serializing concurrent spends.
-- NO non-negative CHECK: earn-side clawbacks (pebble deletion) may legally
-- drive this below zero. The overdraw guard lives in spend_karma, not here.
create table if not exists public.wallet_balances (
  user_id    uuid primary key references auth.users(id) on delete cascade,
  balance    integer not null default 0,
  updated_at timestamptz not null default now()
);

alter table public.wallet_balances enable row level security;

drop policy if exists "wallet_balances_select_self" on public.wallet_balances;
create policy "wallet_balances_select_self" on public.wallet_balances
  for select using (user_id = auth.uid());
-- No INSERT/UPDATE/DELETE policies: maintained exclusively by the trigger below.

-- Keep the snapshot in sync with EVERY karma_events insert (credit and withdraw).
create or replace function public.apply_karma_event_to_wallet()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.wallet_balances (user_id, balance, updated_at)
  values (new.user_id, new.delta, now())
  on conflict (user_id) do update
    set balance    = public.wallet_balances.balance + excluded.balance,
        updated_at = now();
  return new;
end;
$$;

drop trigger if exists karma_events_apply_to_wallet on public.karma_events;
create trigger karma_events_apply_to_wallet
  after insert on public.karma_events
  for each row execute function public.apply_karma_event_to_wallet();

-- One-shot backfill from the existing ledger. Idempotent.
insert into public.wallet_balances (user_id, balance, updated_at)
select user_id, sum(delta)::int, now()
from public.karma_events group by user_id
on conflict (user_id) do update
  set balance = excluded.balance, updated_at = excluded.updated_at;
