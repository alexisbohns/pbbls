# Karma Wallet Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn karma into a spendable currency: one append-only ledger with a credit/withdraw axis, a balance snapshot that purchases can never overdraw (but earn-side clawbacks may push negative), atomic `spend_karma`/`refund_karma` RPCs, and a read-only `/wallet` page.

**Architecture:** Extend `public.karma_events` with a `type` column and widen `delta` to `integer`. A `public.wallet_balances` snapshot (no non-negative CHECK) is maintained by a trigger and gives O(1) balance reads plus a single row to lock; the overdraw guard lives only in `spend_karma` (`SELECT … FOR UPDATE` + `balance ≥ amount`). The web app gains `getWallet`/`getWalletHistory`/`spendKarma` on the provider, a `useWallet` hook, and a `/wallet` page. No spend UI ships here — sub-project C is the first caller of `spend_karma`.

**Tech Stack:** Supabase (Postgres, plpgsql, RLS), Next.js 16 App Router, React 19, TypeScript strict, next-intl, shadcn/ui.

**Spec:** `docs/superpowers/specs/2026-06-29-issue-494-karma-wallet-design.md` · Issue #494 · Milestone M36.

---

## Project conventions that shape verification

- **No local Docker.** This repo deploys to the **linked remote Supabase**, not a local container (project preference). Apply migrations with `npm run db:push --workspace=packages/supabase`, regenerate types with `npm run db:types:remote --workspace=packages/supabase`, and run SQL verification blocks in the **Supabase SQL editor** (or `psql` against the linked DB). *If you do have local Docker, substitute `db:reset` + `db:types`.*
- **No test runner exists (V1).** "Verify" means: SQL assertion blocks for DB tasks; `npm run lint --workspace=apps/web` + `npm run build` for TypeScript; manual `npm run dev` check for the page. Pure helpers are written so they *could* be unit-tested later.
- **Commit cadence:** one logical change per commit, conventional-commits lowercase, scope `core`/`db`/`ui`. Branch is already `docs/494-karma-wallet-spec`; create the implementation branch `feat/494-karma-wallet` off latest `main` before Task 1.
- After **every** migration: regenerate `packages/supabase/types/database.ts` and `git add` it (AGENTS.md).

---

## File structure

**Create:**
- `packages/supabase/supabase/migrations/<ts>_karma_events_type_axis.sql` — type column, reason check, indexes (delta stays smallint — widening would force live-DB view surgery).
- `packages/supabase/supabase/migrations/<ts>_wallet_balances.sql` — snapshot table, trigger, backfill.
- `packages/supabase/supabase/migrations/<ts>_wallet_rpcs.sql` — `spend_karma`, `refund_karma`.
- `packages/supabase/supabase/migrations/<ts>_wallet_summary_and_bounce_credit_only.sql` — `v_wallet_summary` view + `bounces` trigger folds credits only.
- `apps/web/lib/data/useWallet.ts` — wallet hook.
- `apps/web/app/wallet/page.tsx` — route shell.
- `apps/web/components/wallet/WalletView.tsx` — page body (balance header + debt hint + history list).
- `apps/web/components/wallet/WalletHistoryItem.tsx` — one ledger row.
- `apps/web/lib/utils/wallet-format.ts` — pure helpers (reason→i18n key, signed amount).

**Modify:**
- `apps/web/lib/types.ts` — extend `KarmaEvent`, add `KarmaReason`, `WalletSnapshot`.
- `apps/web/lib/data/data-provider.ts` — add `getWallet`/`getWalletHistory`/`spendKarma` to the interface + `WalletPage` result type.
- `apps/web/lib/data/supabase-provider.ts` — implement the three methods.
- `apps/web/lib/i18n/messages/en.json` + `fr.json` — `wallet` namespace.
- `apps/web/components/path/PathBottomBar.tsx` — point the karma stat at `/wallet`.
- `docs/arkaik/bundle.json` (repo root, via the `arkaik` skill) — new view + data-model/endpoint nodes.

> **Design note (divergence from spec, intentional):** the spec mentioned populating `store.karma_log` on global load. We deliberately **keep wallet history out of the eager global load** (it's paginated and wallet-page-specific; loading it on every app start is wasteful). `store.karma` already equals the balance via `v_karma_summary` (Σ delta), so the bottom bar keeps working unchanged. `useWallet` fetches history on demand.

---

## Task 1: Migration — `karma_events` gains the credit/withdraw axis

**Files:**
- Create: `packages/supabase/supabase/migrations/<ts>_karma_events_type_axis.sql`

- [ ] **Step 1: Create the migration file**

Run: `npm run db:migration:new --workspace=packages/supabase -- karma_events_type_axis`
This creates an empty timestamped file. Paste:

```sql
-- Karma becomes a spendable currency: add a movement category to the ledger.
-- `type` keys off the CATEGORY of movement, not the sign of `delta`:
--   credit   = earn-side  (pebble_created, pebble_enriched, pebble_deleted, grant) — may be negative
--   withdraw = spend-side (purchase negative, refund positive)
-- Sign stays in `delta`. balance = Σ delta; earned = Σ delta where credit;
-- spent = -Σ delta where withdraw.

alter table public.karma_events
  add column type text not null default 'credit'
    check (type in ('credit','withdraw'));

-- delta stays smallint: per-event values are small (earn ≤ 10) and balance/total
-- sums promote to integer/bigint anyway. Widening would force dropping & recreating
-- the views that depend on this column (v_karma_summary,
-- v_analytics_bounce_distribution_today) on the live DB — not worth it.

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
```

- [ ] **Step 2: Apply to the linked Supabase**

Run: `npm run db:push --workspace=packages/supabase`
Expected: migration applies with no error (the `reason` check passes because all existing rows use `pebble_created`/`pebble_enriched`/`pebble_deleted`).

- [ ] **Step 3: Verify in the Supabase SQL editor**

```sql
-- All historical rows are now typed 'credit', delta is integer, and the
-- reason check holds.
select count(*)                         as total_rows,
       count(*) filter (where type='credit')   as credit_rows,
       count(*) filter (where type='withdraw') as withdraw_rows
from public.karma_events;
```
Expected: `withdraw_rows = 0`, `credit_rows = total_rows` (every historical row is typed `credit`).

- [ ] **Step 4: Regenerate types**

Run: `npm run db:types:remote --workspace=packages/supabase`
Expected: `packages/supabase/types/database.ts` updates (`karma_events.Row` gains `type`; `delta` stays `number`).

- [ ] **Step 5: Commit**

```bash
git add packages/supabase/supabase/migrations packages/supabase/types/database.ts
git commit -m "feat(db): add credit/withdraw type axis to karma_events"
```

---

## Task 2: Migration — `wallet_balances` snapshot + trigger + backfill

**Files:**
- Create: `packages/supabase/supabase/migrations/<ts>_wallet_balances.sql`

- [ ] **Step 1: Create the migration file**

Run: `npm run db:migration:new --workspace=packages/supabase -- wallet_balances`
Paste:

```sql
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
```

- [ ] **Step 2: Apply**

Run: `npm run db:push --workspace=packages/supabase`
Expected: applies cleanly; backfill populates one row per user that has karma events.

- [ ] **Step 3: Verify the snapshot equals the ledger sum**

```sql
-- Every snapshot row must equal Σ delta from the ledger. Zero mismatches.
select count(*) as mismatches
from public.wallet_balances wb
join (
  select user_id, sum(delta)::int as ledger_balance
  from public.karma_events group by user_id
) led on led.user_id = wb.user_id
where wb.balance <> led.ledger_balance;
```
Expected: `mismatches = 0`.

- [ ] **Step 4: Regenerate types + commit**

```bash
npm run db:types:remote --workspace=packages/supabase
git add packages/supabase/supabase/migrations packages/supabase/types/database.ts
git commit -m "feat(db): add wallet_balances snapshot maintained by karma_events trigger"
```

---

## Task 3: Migration — `spend_karma` + `refund_karma` RPCs

**Files:**
- Create: `packages/supabase/supabase/migrations/<ts>_wallet_rpcs.sql`

- [ ] **Step 1: Create the migration file**

Run: `npm run db:migration:new --workspace=packages/supabase -- wallet_rpcs`
Paste:

```sql
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
```

- [ ] **Step 2: Apply**

Run: `npm run db:push --workspace=packages/supabase`
Expected: both functions created.

- [ ] **Step 3: Verify behavior in the SQL editor (as a real authenticated user)**

Run this block **impersonating a test user** (Supabase SQL editor: set the role/JWT, or wrap with `set local role authenticated; set local request.jwt.claim.sub = '<test-user-uuid>';`). Substitute a real `<test-user-uuid>` that has some earned karma.

```sql
-- A) Read starting balance.
select balance from public.wallet_balances where user_id = '<test-user-uuid>';

-- B) Successful spend of 1: returns an event id, balance drops by 1.
select public.spend_karma(1, 'purchase', null);
select balance from public.wallet_balances where user_id = '<test-user-uuid>';  -- down 1

-- C) Overdraw is rejected: spending more than balance raises insufficient_karma.
--    Expect ERROR: insufficient_karma (nothing written).
select public.spend_karma(999999, 'purchase', null);

-- D) Refund of 1 restores the balance.
select public.refund_karma(1, null);
select balance from public.wallet_balances where user_id = '<test-user-uuid>';  -- back up 1

-- E) Invalid inputs rejected.
select public.spend_karma(0, 'purchase', null);     -- ERROR invalid_amount
select public.spend_karma(1, 'grant', null);        -- ERROR invalid_reason
```
Expected: A returns a number; B returns a uuid and balance −1; **C errors `insufficient_karma`**; D restores; E errors as annotated.

- [ ] **Step 4: Verify the debt path (clawback may go negative; purchase then blocked)**

```sql
-- Drain to a known low balance first via spends, then simulate a pebble-delete
-- clawback that exceeds the balance to prove negatives are allowed on the
-- earn-side and that a subsequent purchase is then refused.
-- Insert a clawback directly (this is what delete_pebble does):
insert into public.karma_events (user_id, type, delta, reason, ref_id)
values ('<test-user-uuid>', 'credit', -100000, 'pebble_deleted', null);
select balance from public.wallet_balances where user_id='<test-user-uuid>'; -- NEGATIVE, allowed
select public.spend_karma(1, 'purchase', null);  -- ERROR insufficient_karma (in debt)
-- Cleanup so the test user isn't left in debt:
insert into public.karma_events (user_id, type, delta, reason, ref_id)
values ('<test-user-uuid>', 'credit', 100000, 'grant', null);
```
Expected: balance goes negative without error; the purchase is refused; cleanup restores it.

- [ ] **Step 5: (Optional) Verify concurrent-spend serialization**

In two separate `psql` sessions against the linked DB, in session 1: `begin; select balance from wallet_balances where user_id='<u>' for update;` (hold). In session 2: `select public.spend_karma(1,'purchase',null);` — it should **block** until session 1 commits/rolls back, proving the row lock serializes spends. Roll back both afterward.

- [ ] **Step 6: Regenerate types + commit**

```bash
npm run db:types:remote --workspace=packages/supabase
git add packages/supabase/supabase/migrations packages/supabase/types/database.ts
git commit -m "feat(db): add guarded spend_karma and refund_karma RPCs"
```

---

## Task 4: Migration — `v_wallet_summary` view + `bounces` folds credits only

**Files:**
- Create: `packages/supabase/supabase/migrations/<ts>_wallet_summary_and_bounce_credit_only.sql`

- [ ] **Step 1: Create the migration file**

Run: `npm run db:migration:new --workspace=packages/supabase -- wallet_summary_and_bounce_credit_only`
Paste:

```sql
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

-- 3. Realign existing bounces snapshots to credit-only (they currently include
--    any historical negatives, which are all credit-side today, so this is a
--    no-op for current data but makes the invariant explicit going forward).
insert into public.bounces (user_id, score, updated_at)
select user_id, coalesce(sum(delta) filter (where type='credit'), 0)::int, now()
from public.karma_events group by user_id
on conflict (user_id) do update
  set score = excluded.score, updated_at = excluded.updated_at;
```

- [ ] **Step 2: Apply**

Run: `npm run db:push --workspace=packages/supabase`
Expected: view created, function replaced, bounces realigned.

- [ ] **Step 3: Verify the summary math and the bounce/withdraw isolation**

```sql
-- Summary reconciles: balance = total_earned - total_spent for the caller.
-- Run impersonating <test-user-uuid> (so auth.uid() resolves):
select balance, total_earned, total_spent,
       (total_earned - total_spent) as derived_balance
from public.v_wallet_summary;   -- expect balance = derived_balance

-- A withdraw must NOT move the bounce snapshot. Capture, spend, compare:
select score from public.bounces where user_id='<test-user-uuid>';   -- note value
select public.spend_karma(1,'purchase',null);
select score from public.bounces where user_id='<test-user-uuid>';   -- UNCHANGED
select public.refund_karma(1, null);  -- cleanup
```
Expected: `balance = derived_balance`; the bounce `score` is identical before and after the spend.

- [ ] **Step 4: Regenerate types + commit**

```bash
npm run db:types:remote --workspace=packages/supabase
git add packages/supabase/supabase/migrations packages/supabase/types/database.ts
git commit -m "feat(db): add v_wallet_summary and fold only credits into bounce snapshot"
```

---

## Task 5: Web types — extend `KarmaEvent`, add `WalletSnapshot`

**Files:**
- Modify: `apps/web/lib/types.ts:67-72`

- [ ] **Step 1: Replace the `KarmaEvent` type and add the new types**

Replace lines 67-72 (the current `KarmaEvent`) with:

```ts
export type KarmaReason =
  | "pebble_created"
  | "pebble_enriched"
  | "pebble_deleted"
  | "grant"
  | "purchase"
  | "refund"

export type KarmaEvent = {
  id: string
  type: "credit" | "withdraw"
  delta: number
  reason: KarmaReason
  ref_id?: string
  created_at: string
}

// Read-only wallet summary projected from v_wallet_summary.
export type WalletSnapshot = {
  balance: number
  totalEarned: number
  totalSpent: number
}
```

- [ ] **Step 2: Verify it type-checks**

Run: `npm run lint --workspace=apps/web`
Expected: passes. (No code constructs `KarmaEvent` today, so widening `reason` to a union breaks nothing. `store.karma_log` stays `KarmaEvent[]` and remains empty.)

- [ ] **Step 3: Commit**

```bash
git add apps/web/lib/types.ts
git commit -m "feat(core): extend KarmaEvent with type/reason union and add WalletSnapshot"
```

---

## Task 6: Provider — `getWallet`, `getWalletHistory`, `spendKarma`

**Files:**
- Modify: `apps/web/lib/data/data-provider.ts` (interface + a result type)
- Modify: `apps/web/lib/data/supabase-provider.ts` (implementation)

- [ ] **Step 1: Add the history page result type + interface methods**

In `apps/web/lib/data/data-provider.ts`, add `WalletSnapshot` to the type import from `@/lib/types` (line 1-8 block) and, just above `getPebblesCount()` (line 83), add:

```ts
  getWallet(): Promise<WalletSnapshot>
  getWalletHistory(cursor?: string, limit?: number): Promise<WalletHistoryPage>
  spendKarma(amount: number, reason: "purchase", refId?: string): Promise<string>
```

Then add this exported type near the other input types (after line 71):

```ts
export type WalletHistoryPage = {
  events: KarmaEvent[]
  nextCursor: string | null
}
```
(Add `KarmaEvent` and `WalletSnapshot` to the existing `import type { … } from "@/lib/types"` at the top — `KarmaEvent` is already imported; add `WalletSnapshot`.)

- [ ] **Step 2: Implement in `SupabaseProvider`**

In `apps/web/lib/data/supabase-provider.ts`, add these three methods next to `getKarma()` (around line 244). Match the file's existing error-handling style (it reads `.data`/`.error` from the Supabase client; mirror whatever `logError` helper the file already uses — if none, throw on error as other mutating methods do):

```ts
  async getWallet(): Promise<WalletSnapshot> {
    const { data } = await this.supabase
      .from("v_wallet_summary")
      .select("*")
      .eq("user_id", this.userId)
      .maybeSingle()
    return {
      balance: (data?.balance as number) ?? 0,
      totalEarned: (data?.total_earned as number) ?? 0,
      totalSpent: (data?.total_spent as number) ?? 0,
    }
  }

  async getWalletHistory(cursor?: string, limit = 20): Promise<WalletHistoryPage> {
    let query = this.supabase
      .from("karma_events")
      .select("id, type, delta, reason, ref_id, created_at")
      .eq("user_id", this.userId)
      .order("created_at", { ascending: false })
      .order("id", { ascending: false })
      .limit(limit + 1)
    if (cursor) query = query.lt("created_at", cursor)
    const { data, error } = await query
    if (error) throw error
    const rows = (data ?? []) as KarmaEvent[]
    const hasMore = rows.length > limit
    const events = hasMore ? rows.slice(0, limit) : rows
    const nextCursor = hasMore ? events[events.length - 1].created_at : null
    return { events, nextCursor }
  }

  async spendKarma(amount: number, reason: "purchase", refId?: string): Promise<string> {
    const { data, error } = await this.supabase.rpc("spend_karma", {
      p_amount: amount,
      p_reason: reason,
      p_ref_id: refId ?? null,
    })
    if (error) throw error
    return data as string
  }
```

Add `WalletSnapshot`, `WalletHistoryPage` to the provider's imports (`WalletHistoryPage` from `./data-provider`, `WalletSnapshot` + `KarmaEvent` from `@/lib/types`).

- [ ] **Step 3: Verify**

Run: `npm run lint --workspace=apps/web && npm run build`
Expected: both pass. The `spend_karma` RPC name resolves against the regenerated `database.ts` from Task 3.

- [ ] **Step 4: Commit**

```bash
git add apps/web/lib/data/data-provider.ts apps/web/lib/data/supabase-provider.ts
git commit -m "feat(core): add wallet read + spendKarma provider methods"
```

---

## Task 7: `useWallet` hook

**Files:**
- Create: `apps/web/lib/data/useWallet.ts`

- [ ] **Step 1: Write the hook**

```ts
"use client"

import { useCallback, useEffect, useState } from "react"
import { useDataProvider } from "@/lib/data/provider-context"
import type { KarmaEvent, WalletSnapshot } from "@/lib/types"

const EMPTY: WalletSnapshot = { balance: 0, totalEarned: 0, totalSpent: 0 }

// Wallet history is fetched on demand (not part of the eager global store load).
export function useWallet() {
  const { provider } = useDataProvider()
  const [summary, setSummary] = useState<WalletSnapshot>(EMPTY)
  const [history, setHistory] = useState<KarmaEvent[]>([])
  const [cursor, setCursor] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)

  useEffect(() => {
    let active = true
    setLoading(true)
    Promise.all([provider.getWallet(), provider.getWalletHistory()])
      .then(([s, page]) => {
        if (!active) return
        setSummary(s)
        setHistory(page.events)
        setCursor(page.nextCursor)
      })
      .catch((e) => active && setError(e as Error))
      .finally(() => active && setLoading(false))
    return () => {
      active = false
    }
  }, [provider])

  const loadMore = useCallback(async () => {
    if (!cursor) return
    const page = await provider.getWalletHistory(cursor)
    setHistory((prev) => [...prev, ...page.events])
    setCursor(page.nextCursor)
  }, [provider, cursor])

  return {
    balance: summary.balance,
    totalEarned: summary.totalEarned,
    totalSpent: summary.totalSpent,
    history,
    hasMore: cursor !== null,
    loadMore,
    loading,
    error,
  }
}
```

- [ ] **Step 2: Verify**

Run: `npm run lint --workspace=apps/web`
Expected: passes.

- [ ] **Step 3: Commit**

```bash
git add apps/web/lib/data/useWallet.ts
git commit -m "feat(core): add useWallet hook (on-demand balance + paginated history)"
```

---

## Task 8: i18n — `wallet` namespace (EN + FR)

**Files:**
- Modify: `apps/web/lib/i18n/messages/en.json`
- Modify: `apps/web/lib/i18n/messages/fr.json`

- [ ] **Step 1: Add the `wallet` block to `en.json`**

Add this top-level key (sibling of the existing namespaces):

```json
"wallet": {
  "title": "Wallet",
  "balance": "Balance",
  "earned": "Total earned",
  "spent": "Total spent",
  "debtHint": "Earn karma to clear your balance before you can shop again.",
  "history": "History",
  "empty": "No karma movements yet.",
  "loadMore": "Load more",
  "reason": {
    "pebble_created": "Pebble created",
    "pebble_enriched": "Pebble enriched",
    "pebble_deleted": "Pebble deleted",
    "grant": "Karma granted",
    "purchase": "Purchase",
    "refund": "Refund"
  }
}
```

- [ ] **Step 2: Add the translated `wallet` block to `fr.json`**

```json
"wallet": {
  "title": "Porte-karma",
  "balance": "Solde",
  "earned": "Total gagné",
  "spent": "Total dépensé",
  "debtHint": "Gagnez du karma pour remettre votre solde à zéro avant de pouvoir acheter à nouveau.",
  "history": "Historique",
  "empty": "Aucun mouvement de karma pour l’instant.",
  "loadMore": "Voir plus",
  "reason": {
    "pebble_created": "Caillou créé",
    "pebble_enriched": "Caillou enrichi",
    "pebble_deleted": "Caillou supprimé",
    "grant": "Karma offert",
    "purchase": "Achat",
    "refund": "Remboursement"
  }
}
```

- [ ] **Step 3: Verify both files are valid JSON**

Run: `node -e "JSON.parse(require('fs').readFileSync('apps/web/lib/i18n/messages/en.json','utf8')); JSON.parse(require('fs').readFileSync('apps/web/lib/i18n/messages/fr.json','utf8')); console.log('ok')"`
Expected: prints `ok`.

- [ ] **Step 4: Commit**

```bash
git add apps/web/lib/i18n/messages/en.json apps/web/lib/i18n/messages/fr.json
git commit -m "feat(ui): add wallet i18n strings (en/fr)"
```

---

## Task 9: Wallet page + history components + nav link

**Files:**
- Create: `apps/web/lib/utils/wallet-format.ts`
- Create: `apps/web/components/wallet/WalletHistoryItem.tsx`
- Create: `apps/web/components/wallet/WalletView.tsx`
- Create: `apps/web/app/wallet/page.tsx`
- Modify: `apps/web/components/path/PathBottomBar.tsx:44-51`

- [ ] **Step 1: Pure format helpers**

Create `apps/web/lib/utils/wallet-format.ts`:

```ts
import type { KarmaEvent } from "@/lib/types"

// i18n key suffix for a movement's reason (consumed under the `wallet.reason` ns).
export function reasonKey(reason: KarmaEvent["reason"]): string {
  return reason
}

// Signed, display-ready amount: "+3" for credits in, "-50" for spends.
export function signedAmount(delta: number): string {
  return delta > 0 ? `+${delta}` : `${delta}`
}

// Direction for non-colour-only cues / icon choice.
export function direction(delta: number): "in" | "out" {
  return delta >= 0 ? "in" : "out"
}
```

- [ ] **Step 2: History row component**

Create `apps/web/components/wallet/WalletHistoryItem.tsx`:

```tsx
"use client"

import { useTranslations } from "next-intl"
import { ArrowDownLeft, ArrowUpRight } from "lucide-react"
import type { KarmaEvent } from "@/lib/types"
import { signedAmount, direction, reasonKey } from "@/lib/utils/wallet-format"

export function WalletHistoryItem({ event }: { event: KarmaEvent }) {
  const t = useTranslations("wallet")
  const dir = direction(event.delta)
  const Icon = dir === "in" ? ArrowDownLeft : ArrowUpRight
  const date = new Date(event.created_at).toLocaleDateString()

  return (
    <li className="flex items-center justify-between gap-3 py-3">
      <div className="flex items-center gap-3">
        <Icon className="size-4 text-muted-foreground" aria-hidden />
        <div>
          <p className="text-sm font-medium">{t(`reason.${reasonKey(event.reason)}`)}</p>
          <p className="text-xs text-muted-foreground">{date}</p>
        </div>
      </div>
      <span className="text-sm font-semibold tabular-nums">{signedAmount(event.delta)}</span>
    </li>
  )
}
```

- [ ] **Step 3: Page body**

Create `apps/web/components/wallet/WalletView.tsx`:

```tsx
"use client"

import { useTranslations } from "next-intl"
import { Sparkle } from "lucide-react"
import { useWallet } from "@/lib/data/useWallet"
import { Card } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { WalletHistoryItem } from "./WalletHistoryItem"

export function WalletView() {
  const t = useTranslations("wallet")
  const { balance, totalEarned, totalSpent, history, hasMore, loadMore, loading } = useWallet()

  return (
    <div className="mx-auto flex w-full max-w-md flex-col gap-6 p-4">
      <Card className="flex flex-col gap-2 p-6">
        <div className="flex items-center gap-2">
          <Sparkle className="size-5" aria-hidden />
          <span className="text-3xl font-bold tabular-nums">{loading ? "—" : balance}</span>
          <span className="text-sm text-muted-foreground">{t("balance")}</span>
        </div>
        {balance < 0 && !loading && (
          <p className="text-sm text-amber-600 dark:text-amber-400">{t("debtHint")}</p>
        )}
        <div className="mt-2 flex gap-6 text-sm text-muted-foreground">
          <span>{t("earned")}: <span className="tabular-nums">{totalEarned}</span></span>
          <span>{t("spent")}: <span className="tabular-nums">{totalSpent}</span></span>
        </div>
      </Card>

      <section>
        <h2 className="mb-2 text-sm font-semibold text-muted-foreground">{t("history")}</h2>
        {history.length === 0 && !loading ? (
          <p className="text-sm text-muted-foreground">{t("empty")}</p>
        ) : (
          <ul className="divide-y">
            {history.map((e) => <WalletHistoryItem key={e.id} event={e} />)}
          </ul>
        )}
        {hasMore && (
          <Button variant="ghost" className="mt-3 w-full" onClick={() => loadMore()}>
            {t("loadMore")}
          </Button>
        )}
      </section>
    </div>
  )
}
```
(If `@/components/ui/card` or `button` aren't present yet, add them: `npx shadcn@latest add card button`.)

- [ ] **Step 4: Route shell**

Create `apps/web/app/wallet/page.tsx`:

```tsx
import { WalletView } from "@/components/wallet/WalletView"

export default function WalletPage() {
  return <WalletView />
}
```

- [ ] **Step 5: Point the karma stat at the wallet**

In `apps/web/components/path/PathBottomBar.tsx`, the karma `Stat` is wrapped in a `<Link href="/profile">` (lines 44-51). Change **that** link's `href` to `/wallet` (leave the profile avatar link above it untouched):

```tsx
      <Link
        href="/wallet"
```

- [ ] **Step 6: Verify build + manual check**

Run: `npm run lint --workspace=apps/web && npm run build`
Expected: pass.
Then `npm run dev`, sign in, and visit `/wallet`: balance shows, history lists your earn events, EN/FR both render (toggle locale), and tapping the karma stat in the bottom bar navigates to `/wallet`. With a negative test balance the debt hint appears.

- [ ] **Step 7: Commit**

```bash
git add apps/web/lib/utils/wallet-format.ts apps/web/components/wallet apps/web/app/wallet apps/web/components/path/PathBottomBar.tsx
git commit -m "feat(ui): add wallet page with balance, debt hint and paginated history"
```

---

## Task 10: Arkaik map update

**Files:**
- Modify: `docs/arkaik/bundle.json` (repo root, via the `arkaik` skill)

- [ ] **Step 1: Invoke the `arkaik` skill** and add:
  - a **view node** for `/wallet` (status: development),
  - **data-model** nodes for the wallet ledger (`karma_events` type axis) and `wallet_balances`,
  - **endpoint** nodes for `spend_karma` / `refund_karma`,
  - edges: `/wallet` → `v_wallet_summary`/`wallet_balances`; `spend_karma` → `karma_events`/`wallet_balances`.

- [ ] **Step 2: Verify** the bundle still parses (the skill validates) and commit:

```bash
git add docs/arkaik/bundle.json
git commit -m "docs(core): map wallet page, ledger and spend RPCs in arkaik"
```

---

## Wrap-up (PR, not a task step)

When opening the PR for #494:
- Title: `feat(core): karma wallet — spendable currency rails + wallet page`.
- Body starts `Resolves #494`; inherit M36 + labels (`feat`, `db`, `core`, `ui`, `web`, `supabase`) — confirm with the user per CLAUDE.md.
- **Lab Note (EN/FR):** gated in (touches a user-visible Arkaik view node). Draft a short bilingual blurb in the PR body — proposal only.
- Run `npm run lint --workspace=apps/web` + `npm run build` green before opening.

---

## Self-review

**Spec coverage:**
- Ledger `type` axis + widened `delta` + reason check + indexes → Task 1. ✓
- `wallet_balances` snapshot + trigger + backfill, no CHECK → Task 2. ✓
- `spend_karma` (guarded, FOR UPDATE) + `refund_karma` + grants → Task 3. ✓
- Debt model / negatives allowed on earn-side, purchase blocked → Task 3 Step 4 verifies. ✓
- `bounces` folds only credits → Task 4. ✓
- `v_wallet_summary` → Task 4. ✓
- Types (`KarmaEvent`, `KarmaReason`, `WalletSnapshot`) → Task 5. ✓
- Provider `getWallet`/`getWalletHistory`/`spendKarma` → Task 6. ✓
- `useWallet` → Task 7. ✓ (history-on-demand divergence documented above.)
- Wallet page: balance, earned/spent, debt hint, paginated EN/FR history, nav link → Tasks 8-9. ✓
- Arkaik + Lab Note → Task 10 + wrap-up. ✓
- Idempotency is C's responsibility (A stays generic) → noted in spec; no A task needed. ✓

**Placeholder scan:** none — every code/SQL step is concrete. The only `<placeholders>` are migration timestamps (generated by `db:migration:new`) and `<test-user-uuid>` (a real value the engineer supplies at verification time), both unavoidable and explained.

**Type consistency:** `WalletSnapshot` {balance,totalEarned,totalSpent}, `KarmaEvent` {id,type,delta,reason,ref_id?,created_at}, and `WalletHistoryPage` {events,nextCursor} are used identically across Tasks 5-9. RPC param names (`p_amount`,`p_reason`,`p_ref_id`) match between Task 3 SQL and Task 6 `.rpc()` call. `spendKarma(amount, reason, refId?)` signature matches interface and impl.
