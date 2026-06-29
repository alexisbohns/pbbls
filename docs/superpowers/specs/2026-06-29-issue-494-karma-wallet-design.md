# Karma Wallet — Spendable Currency Rails

> Sub-project **A** of 4 in **M36 · Pebblestore & Karma Economy**. Issue [#494](https://github.com/alexisbohns/pbbls/issues/494).
> Blocks B (in-app activity), C (glyph marketplace), D (admin moderation).

## Goal

Turn **karma** from an earn-only score into a **spendable currency** with one auditable, append-only ledger: a single balance that can be debited safely, an atomic spend operation that can never let a *purchase* overdraw, and a wallet page where the user reads their full credit/withdraw history. These rails are the foundation the rest of the Pebblestore (glyphs now; themes and pebbleskins later) spends against — so nothing here hard-codes "glyph" as the thing being bought.

## Non-goals

- The "+karma credited" in-app activity → sub-project **B** (#495). This spec builds the credit events; B reacts to them.
- Anything that actually *spends* karma (the glyph buy flow) → sub-project **C** (#496). This spec builds `spend_karma` + the `DataProvider` method; C is the first caller. **No spend UI ships in A.**
- Themes / pebbleskins as goods → future, but the ledger stays generic.
- Touching the **bounce level** (`v_bounce`, 0–7). It is computed purely from pebble active-days over a 28-day window and is **blind to karma**. It is not affected by this work and is not referenced again below.

## Background: what exists today

- **`public.karma_events`** — append-only earn log: `(id, user_id, delta smallint, reason text, ref_id uuid, created_at)`. `reason` ∈ `pebble_created | pebble_enriched | pebble_deleted`. Deltas can already be **negative** (enrich-down corrections; the `delete_pebble` clawback below).
- **Write-path is already locked down.** The early permissive INSERT policy was dropped in `20260411000005_security_hardening`. `karma_events` now has only a **SELECT** policy (`user_id = auth.uid()`); every write goes through `security definer` RPCs. **There is no client path to mint karma**, and a spend RPC drops into this exact pattern.
- **`delete_pebble` claws back karma**: on delete it inserts `(-sum_of_what_the_pebble_earned, reason='pebble_deleted', ref_id=pebble)`. This is why a balance can legitimately go negative once spending exists (see Integrity model).
- **`public.bounces`** — a snapshot table (`user_id, score, updated_at`) maintained by an `after insert` trigger on `karma_events` that sums `delta`. Despite the name it is **not** the bounce level; its only consumer is the admin "bounce karma distribution" histogram (`get_bounce_distribution_today`). Relevant because adding withdraw events changes what it sums (see § Admin analytics).
- **`v_karma_summary`** — view exposing `total_karma` (Σ delta) + `pebbles_count`. The web `SupabaseProvider` reads it into `store.karma`. `store.karma_log` exists but is never populated.
- **UI**: karma shown as a `Sparkle` count in `PathBottomBar` + `GamificationBlocks`. No wallet page, no spend, no history display.

## Core design decisions (settled)

1. **One ledger, one balance.** `karma_events` becomes the full wallet ledger. Balance = Σ delta. "Total earned" / "total spent" are indexed sum queries, not stored numbers.
2. **A `type` axis** (`credit` | `withdraw`) is added, keyed off the *category of movement*, **not** the raw sign:
   - `credit` = earn-side: `pebble_created`, `pebble_enriched`, `pebble_deleted` (clawback), `grant` (admin). May be negative.
   - `withdraw` = spend-side: `purchase` (negative), `refund` (positive reversal).
   - This keeps each aggregate meaningful even though both sides can carry either sign:
     - `balance` = Σ delta (all rows)
     - `total_earned` = Σ delta WHERE `type='credit'` (net of corrections/clawbacks)
     - `total_spent` = −Σ delta WHERE `type='withdraw'` (net of refunds)
3. **The non-negative rule belongs to the spend path, not the balance column.** Earn-side events are *always* recorded, even when they drive the balance negative (deleting pebbles must never be blocked by wallet state). Purchases are *always* guarded (`balance ≥ amount`). A negative balance is a **debt** the user clears by re-earning before they can buy again — which needs zero special-casing, because the purchase guard already refuses any purchase while `balance < price`.
4. **No `CHECK(balance >= 0)`.** Such a constraint would roll back a legitimate pebble deletion. The guard lives in `spend_karma`, made race-safe by a row lock (below).

## Data model

### Migration 1 — extend the ledger

```sql
-- karma_events gains a movement category. Sign stays in `delta`.
alter table public.karma_events
  add column type text not null default 'credit'
    check (type in ('credit','withdraw'));

-- Widen delta: a smallint (max 32767) is fine per-event today, but future
-- goods (themes/skins) and lifetime sums make integer the safe choice.
alter table public.karma_events
  alter column delta type integer;

-- Backfill is trivial: every existing row is earn-side.
-- (default 'credit' already covers historical rows; pebble_deleted included —
--  a downward correction is still an earn-side movement.)

-- Constrain reason to the known set now that it spans both sides.
alter table public.karma_events
  add constraint karma_events_reason_check check (reason in (
    'pebble_created','pebble_enriched','pebble_deleted','grant',  -- credit
    'purchase','refund'                                           -- withdraw
  ));

-- Index for the type-filtered sum queries (earned/spent) and history reads.
create index karma_events_user_type_idx on public.karma_events (user_id, type);
create index karma_events_user_created_idx on public.karma_events (user_id, created_at desc);
```

`ref_id` semantics by reason: `pebble_*` → pebble id; `purchase`/`refund` → the store item (glyph id in C, generic later); `grant` → nullable. A nullable `ref_id` stays acceptable.

### Migration 2 — balance snapshot

```sql
-- O(1) balance reads + a single row to lock for serializing concurrent spends.
-- NO non-negative CHECK: clawbacks may legally drive this below zero.
create table public.wallet_balances (
  user_id    uuid primary key references auth.users(id) on delete cascade,
  balance    integer not null default 0,
  updated_at timestamptz not null default now()
);

alter table public.wallet_balances enable row level security;
create policy "wallet_balances_select_self" on public.wallet_balances
  for select using (user_id = auth.uid());
-- No INSERT/UPDATE/DELETE policies: maintained only by the trigger below.

-- Maintain it from EVERY karma_events insert (credits and withdraws).
create or replace function public.apply_karma_event_to_wallet()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.wallet_balances (user_id, balance, updated_at)
  values (new.user_id, new.delta, now())
  on conflict (user_id) do update
    set balance = public.wallet_balances.balance + excluded.balance,
        updated_at = now();
  return new;
end;
$$;

create trigger karma_events_apply_to_wallet
  after insert on public.karma_events
  for each row execute function public.apply_karma_event_to_wallet();

-- One-shot backfill from existing ledger.
insert into public.wallet_balances (user_id, balance, updated_at)
select user_id, sum(delta)::int, now()
from public.karma_events group by user_id
on conflict (user_id) do update
  set balance = excluded.balance, updated_at = excluded.updated_at;
```

> **Decision — why a snapshot table over a pure derived `SUM`:** it gives O(1) balance reads, a natural single row to lock for concurrency, and it mirrors the existing `bounces` pattern the codebase already trusts. The trade-off (a snapshot to keep in sync) is contained: one trigger, one backfill, and the ledger remains the source of truth (the snapshot is always reconstructible via the backfill query).

### Migration 3 — RPCs

```sql
-- Spend. Guarded + race-safe. Used by C (glyph buy) and any future good.
-- amount is POSITIVE karma to debit; recorded as a negative withdraw delta.
create or replace function public.spend_karma(
  p_amount integer,
  p_reason text,                 -- 'purchase'
  p_ref_id uuid default null
) returns uuid                    -- the ledger event id
language plpgsql security definer set search_path = public as $$
declare
  v_user_id uuid := auth.uid();
  v_balance integer;
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
  returning id into v_event_id;   -- trigger updates wallet_balances

  return v_event_id;
end;
$$;

-- Refund. Symmetric reversal (positive withdraw-side delta). Idempotent per ref.
create or replace function public.refund_karma(
  p_amount integer,
  p_ref_id uuid
) returns uuid
language plpgsql security definer set search_path = public as $$
declare
  v_user_id uuid := auth.uid();
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

grant execute on function public.spend_karma(integer,text,uuid)  to authenticated;
grant execute on function public.refund_karma(integer,uuid)      to authenticated;
```

**Idempotency / double-charge protection.** A single purchase must not be charged twice on a network retry, and a user must not buy what they already own. The clean place for this is the *caller's* domain (C owns "what is a glyph purchase"), enforced by a unique constraint on C's ownership/grant table (e.g. `unique(user_id, glyph_id)`), with the grant + `spend_karma` happening in **one** RPC transaction in C so a failed grant rolls back the debit. A's contract: **`spend_karma` is atomic and self-contained; idempotency of a *purchase* is the caller's responsibility, and A makes that possible by being callable inside the caller's transaction.** This keeps A generic across future goods.

### Migration 4 — keep `update_pebble` / clawback labelled correctly

The existing earn-side inserts (`create_pebble`, `update_pebble`, `delete_pebble`) write `reason` but not `type`. With `type` defaulting to `'credit'`, they remain correct without change. **No edit required** — but the spec notes it explicitly so the implementer verifies the default covers all three and doesn't add `type` asymmetrically. (Per AGENTS.md "keep sibling RPCs symmetric".)

### Admin analytics (`bounces`) — scope boundary

Once withdraw events exist, the `bounces` snapshot (Σ all delta) would silently shift from "earned" to "spendable balance", changing what the admin histogram means. **In-scope for A:** make the `apply_karma_event_to_bounce` trigger fold **only `type='credit'`** events, so "bounce karma distribution" keeps meaning *earned*. This is a one-line `WHERE`/guard in the trigger + a note in the admin migration. (The new `wallet_balances` is the thing that tracks spendable.) No admin UI change.

## Data layer (apps/web)

- **Types** (`lib/types.ts`): extend `KarmaEvent` with `type: 'credit' | 'withdraw'` and the widened reason union; add a `WalletSnapshot` type (`balance`, `totalEarned`, `totalSpent`).
- **`DataProvider` interface** + `SupabaseProvider`:
  - `getWallet(): Promise<WalletSnapshot>` — reads `wallet_balances` + two type-filtered sums (or a small `v_wallet_summary` view for one round-trip).
  - `getWalletHistory(cursor?, limit): Promise<{ events: KarmaEvent[]; nextCursor }>` — paginated read of `karma_events` for the user, newest first (keyset pagination on `(created_at, id)`).
  - `spendKarma(amount, reason, refId?): Promise<string>` — calls the `spend_karma` RPC. **Built in A, no UI caller in A**; C uses it.
  - Populate `store.karma_log` from the first history page on load so existing `useKarma` consumers keep working.
- **Optional view** `v_wallet_summary` (`user_id, balance, total_earned, total_spent`) to collapse the summary into one select; recommended.
- **Hook** `lib/data/useWallet.ts`: `{ balance, totalEarned, totalSpent, history, loadMore, loading, error }`. UI reads only through this hook (never the provider directly), per the data-layer convention.

## Wallet page (apps/web)

- **Route**: `/wallet`. Linked from the karma display in `PathBottomBar`/`GamificationBlocks` and from profile. (The existing karma stat keeps showing the live `balance`.)
- **Contents** (read-only):
  1. **Balance header** — current `balance` (the `Sparkle` count), with `total_earned` / `total_spent` as secondary stats.
  2. **Debt state** — if `balance < 0`, show it honestly with a gentle hint: *"Earn karma to clear your balance before you can shop again."* Never hidden, never alarming.
  3. **History list** — chronological credit/withdraw entries: direction icon (＋/−), human reason label, amount, date, and a link to what it referenced (pebble / store item) when resolvable. Paginated via `loadMore`.
- **i18n**: all copy bilingual EN/FR (`next-intl`, new message keys under a `wallet` namespace).
- **a11y/theming**: follows shadcn-first + color-world theming; amounts have non-colour-only direction cues (the ＋/− glyph, not just red/green).
- **Build it shadcn-first**: Card for the header, a simple list/table for history, existing Badge for direction. No bespoke styling beyond tokens.

## Integrity model (summary)

| Scenario | Result |
|---|---|
| Buy when `balance ≥ price` | `spend_karma` inserts a `withdraw`; balance drops. |
| Buy when `balance < price` | `spend_karma` raises `insufficient_karma`; nothing written. |
| Two devices buy at once | Row lock on `wallet_balances` serializes them; the second sees the updated balance and is accepted/rejected correctly. |
| Delete a pebble after spending | Clawback (`pebble_deleted`, credit) is **always** recorded; balance may go **negative** (debt). Deletion never blocked. |
| Try to buy while in debt | `balance < 0 < price` → rejected until re-earned. |
| Refund a purchase | `refund_karma` adds a positive withdraw-side event; balance recovers; `total_spent` nets down. |

## Testing

The repo has no test harness yet (V1), so "test-ready" + manual verification:

- **SQL-level** (verify via `db:reset` + scripted RPC calls): spend success, `insufficient_karma` rejection, concurrent-spend serialization (two transactions racing one balance), clawback-into-negative, buy-while-in-debt rejection, refund recovery, backfill correctness (snapshot == Σ ledger), and that `bounces` ignores withdraws.
- **App-level**: wallet page renders balance + paginated history in EN/FR; negative-balance hint appears; `useWallet` updates after a (C-driven) spend.
- Pure helpers (reason→label, amount formatting, ±direction) are pure functions, unit-test-ready.

## Migration checklist (per AGENTS.md)

1. Add migrations 1–4 under `packages/supabase/supabase/migrations/`.
2. `npm run db:reset` then `npm run db:types --workspace=packages/supabase`.
3. `git add packages/supabase/types/database.ts`.
4. Keep sibling earn-side RPCs symmetric re: `type` (verify the default covers all three; don't add `type` to one without the others).

## Arkaik

Adds a screen (`/wallet`) and data concepts (wallet ledger, balance, `spend_karma`/`refund_karma`). Update `docs/arkaik/bundle.json` as part of implementation (new view node + data-model/endpoint nodes), per the `arkaik` skill — this clears the user-facing-view bar, so the PR also carries a bilingual **Lab Note**.

## Open questions deferred to C/D (not blocking A)

- Who sets a glyph's price and where it's stored (C).
- Whether buying is ownership vs licence-to-use (C) — drives the unique constraint shape.
- Pricing of future goods (themes/skins) — rails are already generic.
