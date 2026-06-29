-- Karma becomes a spendable currency: add a movement category to the ledger.
-- `type` keys off the CATEGORY of movement, not the sign of `delta`:
--   credit   = earn-side  (pebble_created, pebble_enriched, pebble_deleted, grant) — may be negative
--   withdraw = spend-side (purchase negative, refund positive)
-- Sign stays in `delta`. balance = Σ delta; earned = Σ delta where credit;
-- spent = -Σ delta where withdraw.
--
-- delta stays smallint on purpose: per-event values are small (earn ≤ 10), and
-- balance/total sums promote to integer/bigint anyway (wallet_balances.balance is
-- integer). Widening the column would force dropping & recreating the views that
-- depend on it (v_karma_summary, v_analytics_bounce_distribution_today) on the
-- live DB — not worth it. Revisit only if a single good ever needs a price > 32767.

alter table public.karma_events
  add column type text not null default 'credit'
    check (type in ('credit','withdraw'));

-- Constrain reason now that it spans both sides. Existing rows are all earn-side
-- and already use the first three reasons, so the default 'credit' covers them.
alter table public.karma_events
  add constraint karma_events_reason_check check (reason in (
    'pebble_created','pebble_enriched','pebble_deleted','grant',  -- credit
    'purchase','refund'                                           -- withdraw
  ));

-- Indexes: (user_id, type) powers earned/spent sums; (user_id, created_at desc)
-- powers the wallet history read.
create index if not exists karma_events_user_type_idx
  on public.karma_events (user_id, type);
create index if not exists karma_events_user_created_idx
  on public.karma_events (user_id, created_at desc);
