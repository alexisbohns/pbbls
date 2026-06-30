# Glyph Marketplace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users submit glyphs to a community market, buy them with karma over Sub-project A's atomic spend rails (granting use-rights, not a copy), favourite them, and surface each purchase via Sub-project B's activity pill — organised into Mine / Favourites / Market tabs.

**Architecture:** Three new tables (`glyph_submissions` = submission *and* listing; `glyph_entitlements` = use-rights grant + purchase ledger; `glyph_favourites`) + two security-definer RPCs (`submit_glyph`, `buy_glyph`) + a `security_invoker` market view. The `glyphs` SELECT policy widens to expose listed/entitled rows; UPDATE/DELETE lock listed/bought glyphs against their creator (admin-exempt). Web reads through new `DataProvider` methods + hooks; the buy flow updates the karma store and fires a sibling activity pill.

**Tech Stack:** Supabase/Postgres (RLS, plpgsql RPCs, security_invoker view), Next.js 16 App Router (client components), next-intl (EN/FR), Sonner (reused activity), shadcn `alert-dialog`/`badge`/`button`, Lucide icons.

**Reference spec:** `docs/superpowers/specs/2026-06-30-issue-496-glyph-marketplace-design.md`

**Verification model:** No web test runner (V1). Each task verifies with `npm run lint --workspace=apps/web` (and `next build` / `db:types` where types change), plus the manual checklist in the spec §9. DB migrations deploy to **remote** Supabase (no local Docker — see decision log).

**Naming note:** Per the #488 sequencing decision, all *new* code here is `Glyph`-named; pre-existing `Mark`/`useMarks`/`listMarks` stay as-is until #488. New types that extend the glyph domain reference the existing `Mark` type (it becomes `Glyph` automatically when #488 lands).

---

## Task 1: Database — tables, RLS, policy rewrite, RPCs, market view

**Files:**
- Create: `packages/supabase/supabase/migrations/<timestamp>_glyph_marketplace.sql` (generate the timestamp with `supabase migration new glyph_marketplace`)
- Modify (regenerate): `packages/supabase/types/database.ts`

- [ ] **Step 1: Create the migration file**

Run: `supabase migration new glyph_marketplace` (creates the timestamped empty file under `packages/supabase/supabase/migrations/`). Paste the full SQL below into it.

```sql
-- =============================================================================
-- Glyph marketplace (#496) — submissions/listings, entitlements, favourites,
-- submit_glyph + buy_glyph RPCs, market view, and glyphs policy rewrite (D8).
-- Price is per-listing, flat-defaulted; GLYPH_PRICE_DEFAULT mirror in
-- apps/web/lib/config/glyphs.ts must stay in sync with the default literal below.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Tables
-- ---------------------------------------------------------------------------
create table public.glyph_submissions (
  id            uuid primary key default gen_random_uuid(),
  glyph_id      uuid not null references public.glyphs(id) on delete cascade,
  submitter_id  uuid not null references auth.users(id),
  status        text not null default 'pending'
                  check (status in ('pending','approved','rejected')),
  price         integer not null default 25 check (price > 0),  -- GLYPH_PRICE_DEFAULT
  created_at    timestamptz not null default now(),
  reviewed_at   timestamptz,
  reviewed_by   uuid references auth.users(id)
);

-- At most one active (pending|approved) submission per glyph.
create unique index glyph_submissions_one_active
  on public.glyph_submissions (glyph_id)
  where status in ('pending','approved');

create index glyph_submissions_status_idx on public.glyph_submissions (status);

create table public.glyph_entitlements (
  id              uuid primary key default gen_random_uuid(),
  user_id         uuid not null references auth.users(id),
  glyph_id        uuid not null references public.glyphs(id) on delete cascade,
  karma_event_id  uuid not null references public.karma_events(id),
  price_paid      integer not null check (price_paid > 0),
  created_at      timestamptz not null default now(),
  unique (user_id, glyph_id)
);

create index glyph_entitlements_glyph_idx on public.glyph_entitlements (glyph_id);

create table public.glyph_favourites (
  user_id     uuid not null references auth.users(id),
  glyph_id    uuid not null references public.glyphs(id) on delete cascade,
  created_at  timestamptz not null default now(),
  primary key (user_id, glyph_id)
);

-- ---------------------------------------------------------------------------
-- 2. RLS
-- ---------------------------------------------------------------------------
alter table public.glyph_submissions enable row level security;
alter table public.glyph_entitlements enable row level security;
alter table public.glyph_favourites   enable row level security;

create policy glyph_submissions_select on public.glyph_submissions for select
  to authenticated
  using (submitter_id = auth.uid() or status = 'approved');

create policy glyph_entitlements_select on public.glyph_entitlements for select
  to authenticated using (user_id = auth.uid());

create policy glyph_favourites_all on public.glyph_favourites for all
  to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

-- ---------------------------------------------------------------------------
-- 3. glyphs policy rewrite — widen SELECT, lock UPDATE/DELETE (D8)
--    Current (snapshot): glyphs_select = own ∪ null; update/delete = own.
-- ---------------------------------------------------------------------------
drop policy if exists "glyphs_select" on public.glyphs;
create policy "glyphs_select" on public.glyphs for select to authenticated
  using (
    user_id = auth.uid()
    or user_id is null
    or exists (select 1 from public.glyph_submissions s
               where s.glyph_id = glyphs.id and s.status = 'approved')
    or exists (select 1 from public.glyph_entitlements e
               where e.glyph_id = glyphs.id and e.user_id = auth.uid())
  );

drop policy if exists "glyphs_update" on public.glyphs;
create policy "glyphs_update" on public.glyphs for update to authenticated
  using (
    public.is_admin(auth.uid())
    or (
      user_id = auth.uid()
      and not exists (select 1 from public.glyph_submissions s
                      where s.glyph_id = glyphs.id and s.status in ('pending','approved'))
      and not exists (select 1 from public.glyph_entitlements e
                      where e.glyph_id = glyphs.id)
    )
  );

drop policy if exists "glyphs_delete" on public.glyphs;
create policy "glyphs_delete" on public.glyphs for delete to authenticated
  using (
    public.is_admin(auth.uid())
    or (
      user_id = auth.uid()
      and not exists (select 1 from public.glyph_submissions s
                      where s.glyph_id = glyphs.id and s.status in ('pending','approved'))
      and not exists (select 1 from public.glyph_entitlements e
                      where e.glyph_id = glyphs.id)
    )
  );

-- ---------------------------------------------------------------------------
-- 4. RPCs
-- ---------------------------------------------------------------------------
-- Submit one of your own custom glyphs to the community (lands pending for D).
create or replace function public.submit_glyph(p_glyph_id uuid)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_user  uuid := auth.uid();
  v_owner uuid;
  v_row   public.glyph_submissions;
begin
  if v_user is null then raise exception 'not_authenticated' using errcode='42501'; end if;

  select user_id into v_owner from public.glyphs where id = p_glyph_id;
  if v_owner is distinct from v_user then
    raise exception 'not_owner' using errcode='42501';  -- covers system glyphs (null owner)
  end if;

  if exists (select 1 from public.glyph_submissions s
             where s.glyph_id = p_glyph_id and s.status in ('pending','approved')) then
    raise exception 'already_submitted';
  end if;

  insert into public.glyph_submissions (glyph_id, submitter_id)
  values (p_glyph_id, v_user)
  returning * into v_row;

  return to_jsonb(v_row);
end;
$$;

-- Buy a community glyph: spend karma + grant use-rights, atomically.
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

  -- Must be an approved (in-market) listing.
  select s.price, g.user_id into v_price, v_owner
  from public.glyph_submissions s
  join public.glyphs g on g.id = s.glyph_id
  where s.glyph_id = p_glyph_id and s.status = 'approved'
  limit 1;
  if v_price is null then raise exception 'not_in_market'; end if;

  if v_owner = v_user then raise exception 'cannot_buy_own'; end if;

  if exists (select 1 from public.glyph_entitlements e
             where e.user_id = v_user and e.glyph_id = p_glyph_id) then
    raise exception 'already_owned';
  end if;

  -- Spend (row-locked, raises insufficient_karma); records a withdraw event.
  v_event := public.spend_karma(v_price, 'purchase', p_glyph_id);

  -- Grant. unique(user_id, glyph_id) is the race backstop — a concurrent
  -- double-buy rolls back the loser's spend too (same txn).
  insert into public.glyph_entitlements (user_id, glyph_id, karma_event_id, price_paid)
  values (v_user, p_glyph_id, v_event, v_price)
  returning id into v_ent;

  select balance into v_balance from public.wallet_balances where user_id = v_user;

  return jsonb_build_object('entitlement_id', v_ent, 'balance', v_balance);
end;
$$;

revoke all on function public.submit_glyph(uuid) from public, anon;
revoke all on function public.buy_glyph(uuid)    from public, anon;
grant execute on function public.submit_glyph(uuid) to authenticated;
grant execute on function public.buy_glyph(uuid)    to authenticated;

-- ---------------------------------------------------------------------------
-- 5. Market view — approved listings + per-caller owned/favourited flags.
--    security_invoker so the caller's RLS (widened glyphs SELECT, own
--    entitlements/favourites) applies.
-- ---------------------------------------------------------------------------
create view public.v_glyph_market with (security_invoker = true) as
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
where s.status = 'approved';

revoke all on public.v_glyph_market from public, anon;
grant select on public.v_glyph_market to authenticated;
```

- [ ] **Step 2: Deploy to remote Supabase**

Run: `supabase db push` (remote-first per the decision log). Expected: migration applies cleanly, no errors.

- [ ] **Step 3: Regenerate TypeScript types**

Run: `npm run db:types --workspace=packages/supabase`
Then: `git add packages/supabase/types/database.ts`
Expected: `database.ts` now contains `glyph_submissions`, `glyph_entitlements`, `glyph_favourites`, `v_glyph_market`, and the `submit_glyph` / `buy_glyph` function signatures.

- [ ] **Step 4: Sanity-check the RPCs against remote (manual)**

In the Supabase SQL editor (as an authenticated test user, or via `set local role`): create a custom glyph, `select submit_glyph('<id>')` → pending row; `update glyph_submissions set status='approved' where glyph_id='<id>'`; as a *second* user `select buy_glyph('<id>')` → returns `{entitlement_id, balance}` and debits karma; calling `buy_glyph` again → `already_owned`; the seller calling it → `cannot_buy_own`. Confirm a listed glyph can no longer be updated/deleted by its creator (RLS denies).

- [ ] **Step 5: Commit**

```bash
git add packages/supabase/supabase/migrations/ packages/supabase/types/database.ts
git commit -m "feat(db): glyph marketplace tables, rpcs, market view and glyph lock"
```

---

## Task 2: Data layer — types, price constant, provider interface

**Files:**
- Modify: `apps/web/lib/config/glyphs.ts`
- Modify: `apps/web/lib/types.ts`
- Modify: `apps/web/lib/data/data-provider.ts`

- [ ] **Step 1: Add the price constant**

In `apps/web/lib/config/glyphs.ts`, add (mirror of the SQL default — keep in sync):

```ts
/**
 * Flat community-glyph price in karma. Mirrors the `price` DEFAULT in
 * `<timestamp>_glyph_marketplace.sql`. Server (`buy_glyph`) is authoritative;
 * this is for display only. Keep both in sync.
 */
export const GLYPH_PRICE_DEFAULT = 25
```

- [ ] **Step 2: Add domain types**

In `apps/web/lib/types.ts`, after the `Mark` type, add:

```ts
export type GlyphSubmissionStatus = "pending" | "approved" | "rejected"

// A community-market glyph: the glyph plus its listing price and the caller's
// relationship to it. Extends Mark (becomes Glyph when #488 lands).
export type MarketGlyph = Mark & {
  price: number
  owned: boolean // caller is entitled (bought)
  favourited: boolean // caller has favourited
}

export type GlyphSubmission = {
  id: string
  glyph_id: string
  status: GlyphSubmissionStatus
  price: number
  created_at: string
}
```

- [ ] **Step 3: Extend the Store with entitled glyphs (for D7 rendering/picker)**

In `apps/web/lib/data/data-provider.ts`, add `entitledMarks: Mark[]` to the `Store` type and `entitledMarks: []` to `EMPTY_STORE` (place both next to `marks`).

- [ ] **Step 4: Extend the DataProvider interface**

In the same file, import `MarketGlyph` and `GlyphSubmission` from `@/lib/types`, then add these methods to the `DataProvider` interface (near the existing Mark methods):

```ts
listMarketGlyphs(): Promise<MarketGlyph[]>     // approved, others' glyphs, + caller flags
listFavouriteGlyphs(): Promise<MarketGlyph[]>  // entitled ∪ favourited
getMySubmissions(): Promise<GlyphSubmission[]> // caller's submissions (status badges)
submitGlyph(glyphId: string): Promise<GlyphSubmission>
buyGlyph(glyphId: string): Promise<{ entitlementId: string; karma: number }>
setFavourite(glyphId: string, favourite: boolean): Promise<void>
```

- [ ] **Step 5: Verify + commit**

Run: `npm run lint --workspace=apps/web` (expect: passes; SupabaseProvider will error on missing methods only after it's the next task — if lint fails on the interface, that's expected until Task 3. Run `tsc` only after Task 3.)

```bash
git add apps/web/lib/config/glyphs.ts apps/web/lib/types.ts apps/web/lib/data/data-provider.ts
git commit -m "feat(core): glyph market types, price constant and provider interface"
```

---

## Task 3: SupabaseProvider — implement the market methods + load entitled glyphs

**Files:**
- Modify: `apps/web/lib/data/supabase-provider.ts`

- [ ] **Step 1: Import the new types**

Add `MarketGlyph`, `GlyphSubmission` to the `@/lib/types` import block.

- [ ] **Step 2: Add a row→Mark mapper helper (DRY)**

The provider repeats the `glyphs` row→`Mark` shape three times. Add a private helper and reuse it in the new methods (do not refactor existing call sites unless trivial):

```ts
private rowToMark(row: Record<string, unknown>): Mark {
  return {
    id: row.id as string,
    name: (row.name as string) ?? undefined,
    shape_id: row.shape_id as string,
    strokes: row.strokes as Mark["strokes"],
    viewBox: row.view_box as string,
    created_at: row.created_at as string,
    updated_at: row.updated_at as string,
  }
}
```

- [ ] **Step 3: Load entitled glyphs in `loadFromSupabase`**

Add a parallel query to the `Promise.all` (alongside `glyphsRes`):

```ts
this.supabase.from("v_glyph_market").select("*").eq("owned", true),
```

Name the result `entitledRes`, add an error guard mirroring the others, and after the `marks` mapping add:

```ts
const entitledMarks: Mark[] = (entitledRes.data ?? []).map((row) =>
  this.rowToMark(row as Record<string, unknown>),
)
```

Include `entitledMarks` in the returned/mutated store object next to `marks`.

- [ ] **Step 4: Implement the market methods**

Add near the Mark mutations section:

```ts
// ---------------------------------------------------------------------------
// Glyph marketplace (#496)
// ---------------------------------------------------------------------------
private rowToMarketGlyph(row: Record<string, unknown>): MarketGlyph {
  return {
    ...this.rowToMark(row),
    price: row.price as number,
    owned: Boolean(row.owned),
    favourited: Boolean(row.favourited),
  }
}

async listMarketGlyphs(): Promise<MarketGlyph[]> {
  const { data, error } = await this.supabase.from("v_glyph_market").select("*")
  if (error) throw new Error(`Failed to load market glyphs: ${error.message}`)
  // Hide the caller's own creations — they live under Mine, not the Market.
  return (data ?? [])
    .filter((row) => (row as Record<string, unknown>).user_id !== this.userId)
    .map((row) => this.rowToMarketGlyph(row as Record<string, unknown>))
}

async listFavouriteGlyphs(): Promise<MarketGlyph[]> {
  // Bought (owned) ∪ favourited, both drawn from approved listings. (A glyph
  // delisted after purchase would drop out — acceptable for V1; D doesn't exist yet.)
  const { data, error } = await this.supabase
    .from("v_glyph_market")
    .select("*")
    .or("owned.eq.true,favourited.eq.true")
  if (error) throw new Error(`Failed to load favourites: ${error.message}`)
  return (data ?? []).map((row) => this.rowToMarketGlyph(row as Record<string, unknown>))
}

async getMySubmissions(): Promise<GlyphSubmission[]> {
  const { data, error } = await this.supabase
    .from("glyph_submissions")
    .select("id, glyph_id, status, price, created_at")
    .eq("submitter_id", this.userId)
  if (error) throw new Error(`Failed to load submissions: ${error.message}`)
  return (data ?? []).map((row) => {
    const r = row as Record<string, unknown>
    return {
      id: r.id as string,
      glyph_id: r.glyph_id as string,
      status: r.status as GlyphSubmission["status"],
      price: r.price as number,
      created_at: r.created_at as string,
    }
  })
}

async submitGlyph(glyphId: string): Promise<GlyphSubmission> {
  const { data, error } = await this.supabase.rpc("submit_glyph", { p_glyph_id: glyphId })
  if (error) throw new Error(error.message)
  const r = data as Record<string, unknown>
  return {
    id: r.id as string,
    glyph_id: r.glyph_id as string,
    status: r.status as GlyphSubmission["status"],
    price: r.price as number,
    created_at: r.created_at as string,
  }
}

async buyGlyph(glyphId: string): Promise<{ entitlementId: string; karma: number }> {
  const { data, error } = await this.supabase.rpc("buy_glyph", { p_glyph_id: glyphId })
  if (error) throw new Error(error.message)
  const r = data as { entitlement_id: string; balance: number }
  this.mutate({ ...this.store, karma: r.balance })
  return { entitlementId: r.entitlement_id, karma: r.balance }
}

async setFavourite(glyphId: string, favourite: boolean): Promise<void> {
  if (favourite) {
    const { error } = await this.supabase
      .from("glyph_favourites")
      .upsert({ user_id: this.userId, glyph_id: glyphId }, { onConflict: "user_id,glyph_id" })
    if (error) throw new Error(error.message)
  } else {
    const { error } = await this.supabase
      .from("glyph_favourites")
      .delete()
      .eq("user_id", this.userId)
      .eq("glyph_id", glyphId)
    if (error) throw new Error(error.message)
  }
}
```

- [ ] **Step 5: Verify + commit**

Run: `npm run lint --workspace=apps/web` then `npm run build --workspace=apps/web` (types changed). Expect both green.

```bash
git add apps/web/lib/data/supabase-provider.ts
git commit -m "feat(core): implement glyph market provider methods"
```

---

## Task 4: Hooks — market, favourites, submissions, usable glyphs

**Files:**
- Create: `apps/web/lib/data/useGlyphMarket.ts`
- Create: `apps/web/lib/data/useGlyphFavourites.ts`
- Create: `apps/web/lib/data/useGlyphSubmissions.ts`
- Create: `apps/web/lib/data/useUsableGlyphs.ts`

- [ ] **Step 1: `useUsableGlyphs` (own ∪ entitled, for the picker)**

```ts
"use client"

import { useDataProvider } from "@/lib/data/provider-context"
import type { Mark } from "@/lib/types"

/** Glyphs the user may attach to pebbles/souls: own ∪ entitled (bought). */
export function useUsableGlyphs(): { glyphs: Mark[]; loading: boolean } {
  const { store, loading } = useDataProvider()
  const seen = new Set(store.marks.map((m) => m.id))
  const glyphs = [...store.marks, ...store.entitledMarks.filter((m) => !seen.has(m.id))]
  return { glyphs, loading }
}
```

- [ ] **Step 2: `useGlyphSubmissions` (Mine-tab badges + submit)**

```ts
"use client"

import { useCallback, useEffect, useState } from "react"
import { useDataProvider } from "@/lib/data/provider-context"
import type { GlyphSubmission } from "@/lib/types"

export function useGlyphSubmissions() {
  const { provider } = useDataProvider()
  const [submissions, setSubmissions] = useState<GlyphSubmission[]>([])
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    if (!provider) return
    setSubmissions(await provider.getMySubmissions())
    setLoading(false)
  }, [provider])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const submit = useCallback(
    async (glyphId: string) => {
      if (!provider) throw new Error("Not authenticated")
      const created = await provider.submitGlyph(glyphId)
      await refresh()
      return created
    },
    [provider, refresh],
  )

  return { submissions, loading, submit, refresh }
}
```

- [ ] **Step 3: `useGlyphMarket` (list + buy + favourite)**

```ts
"use client"

import { useCallback, useEffect, useState } from "react"
import { useTranslations } from "next-intl"
import { useDataProvider } from "@/lib/data/provider-context"
import { notifyGlyphPurchased } from "@/lib/activity/glyph-activity"
import type { MarketGlyph } from "@/lib/types"

export function useGlyphMarket() {
  const { provider, setStore } = useDataProvider()
  const tGlyphs = useTranslations("glyphs")
  const [glyphs, setGlyphs] = useState<MarketGlyph[]>([])
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    if (!provider) return
    setGlyphs(await provider.listMarketGlyphs())
    setLoading(false)
  }, [provider])

  useEffect(() => {
    void refresh()
  }, [refresh])

  // Throws on failure (e.g. "insufficient_karma") — caller maps to a message.
  const buy = useCallback(
    async (glyph: MarketGlyph) => {
      if (!provider) throw new Error("Not authenticated")
      await provider.buyGlyph(glyph.id)
      setStore(provider.getStore()) // karma + entitledMarks refreshed by reload-on-buy
      notifyGlyphPurchased(glyph.id, glyph.name || tGlyphs("untitled"), glyph.price)
      await refresh()
    },
    [provider, setStore, refresh, tGlyphs],
  )

  const favourite = useCallback(
    async (glyphId: string, value: boolean) => {
      if (!provider) return
      await provider.setFavourite(glyphId, value)
      await refresh()
    },
    [provider, refresh],
  )

  return { glyphs, loading, buy, favourite, refresh }
}
```

> Note: `buyGlyph` updates only `karma` in the provider store. To also refresh
> `entitledMarks` (so the bought glyph becomes usable immediately) the buy hook
> can call `await provider.loadFromSupabase()` then `setStore(provider.getStore())`.
> Use whichever the implementer confirms reloads the store; the existing pebble
> hooks call `loadFromSupabase` internally — check `buyGlyph` vs that pattern and
> prefer a full reload after buy for correctness.

- [ ] **Step 4: `useGlyphFavourites` (list + favourite toggle)**

```ts
"use client"

import { useCallback, useEffect, useState } from "react"
import { useDataProvider } from "@/lib/data/provider-context"
import type { MarketGlyph } from "@/lib/types"

export function useGlyphFavourites() {
  const { provider } = useDataProvider()
  const [glyphs, setGlyphs] = useState<MarketGlyph[]>([])
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    if (!provider) return
    setGlyphs(await provider.listFavouriteGlyphs())
    setLoading(false)
  }, [provider])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const favourite = useCallback(
    async (glyphId: string, value: boolean) => {
      if (!provider) return
      await provider.setFavourite(glyphId, value)
      await refresh()
    },
    [provider, refresh],
  )

  return { glyphs, loading, favourite, refresh }
}
```

- [ ] **Step 5: Reconcile buy → store reload**

In `supabase-provider.ts` `buyGlyph`, after the RPC succeeds, replace the `this.mutate({ ...this.store, karma })` line with a full reload so `entitledMarks` (and karma) refresh together:

```ts
await this.loadFromSupabase()  // refreshes karma + entitledMarks
return { entitlementId: r.entitlement_id, karma: this.store.karma }
```

(Then the hook's `setStore(provider.getStore())` reflects both.)

- [ ] **Step 6: Verify + commit**

Run: `npm run lint --workspace=apps/web` (build deferred until activity exists in Task 5). Expect lint green except the not-yet-created `@/lib/activity/glyph-activity` import — that's resolved in Task 5; if lint blocks on it, do Task 5 before re-running build.

```bash
git add apps/web/lib/data/useGlyphMarket.ts apps/web/lib/data/useGlyphFavourites.ts apps/web/lib/data/useGlyphSubmissions.ts apps/web/lib/data/useUsableGlyphs.ts apps/web/lib/data/supabase-provider.ts
git commit -m "feat(core): glyph market hooks (market, favourites, submissions, usable)"
```

---

## Task 5: Activity — glyph purchase pill (reuses Sonner)

**Files:**
- Create: `apps/web/components/activity/GlyphPurchasePill.tsx`
- Create: `apps/web/lib/activity/glyph-activity.tsx`
- Modify: `apps/web/lib/i18n/messages/en.json`, `apps/web/lib/i18n/messages/fr.json`

- [ ] **Step 1: Add `activity` i18n keys (EN then FR)**

EN — extend the existing `activity` object:
```json
"glyphUnlocked": "Glyph unlocked",
"spent": "−{amount} karma",
"srUnlocked": "Unlocked {name} — {amount} karma spent.",
"viewGlyph": "View glyph"
```
FR:
```json
"glyphUnlocked": "Glyphe débloqué",
"spent": "−{amount} karma",
"srUnlocked": "{name} débloqué — {amount} karma dépensés.",
"viewGlyph": "Voir le glyphe"
```

- [ ] **Step 2: Create `GlyphPurchasePill` (mirror `KarmaActivityPill`)**

```tsx
"use client"

import { useRouter } from "next/navigation"
import { useTranslations } from "next-intl"
import { Sparkle } from "lucide-react"
import { toast } from "sonner"

type GlyphPurchasePillProps = {
  toastId: string | number
  glyphId: string
  name: string
  amount: number
}

export function GlyphPurchasePill({ toastId, glyphId, name, amount }: GlyphPurchasePillProps) {
  const router = useRouter()
  const t = useTranslations("activity")

  const handleTap = () => {
    toast.dismiss(toastId)
    router.push(`/glyphs/${glyphId}`)
  }

  // "Unlocked Star — 25 karma spent. View glyph."
  const label = `${t("srUnlocked", { name, amount })} ${t("viewGlyph")}.`

  return (
    <button
      type="button"
      onClick={handleTap}
      aria-label={label}
      className="flex items-center gap-2 rounded-full bg-neutral-900 px-4 py-2 text-sm font-semibold text-white shadow-lg ring-1 ring-white/10 transition active:scale-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-300 focus-visible:ring-offset-2 dark:bg-neutral-800 motion-reduce:transition-none motion-reduce:active:scale-100"
    >
      <Sparkle aria-hidden className="size-4 text-amber-300" />
      <span aria-hidden>{t("glyphUnlocked")}</span>
      <span aria-hidden className="text-amber-300">{t("spent", { amount })}</span>
    </button>
  )
}
```

- [ ] **Step 3: Create `glyph-activity.tsx` (mirror `karma-activity.tsx`)**

```tsx
import { toast } from "sonner"
import { GlyphPurchasePill } from "@/components/activity/GlyphPurchasePill"

// Stable id → a new purchase replaces the current pill rather than stacking.
const GLYPH_ACTIVITY_ID = "glyph-activity"

/** Fire a glanceable "Glyph unlocked · −N karma" pill after a purchase. */
export function notifyGlyphPurchased(glyphId: string, name: string, amount: number): void {
  if (amount <= 0) return
  toast.custom(
    (id) => (
      <div className="flex w-full justify-center">
        <GlyphPurchasePill toastId={id} glyphId={glyphId} name={name} amount={amount} />
      </div>
    ),
    { id: GLYPH_ACTIVITY_ID, duration: 3000 },
  )
}
```

- [ ] **Step 4: Verify + commit**

Run: `npm run lint --workspace=apps/web` then `npm run build --workspace=apps/web`. Expect green (Task 4's import now resolves).

```bash
git add apps/web/components/activity/GlyphPurchasePill.tsx apps/web/lib/activity/glyph-activity.tsx apps/web/lib/i18n/messages/en.json apps/web/lib/i18n/messages/fr.json
git commit -m "feat(ui): glyph purchase activity pill"
```

---

## Task 6: i18n — glyph tabs, empty states, submit, market namespace

**Files:**
- Modify: `apps/web/lib/i18n/messages/en.json`, `apps/web/lib/i18n/messages/fr.json`

- [ ] **Step 1: Extend the `glyphs` namespace (EN)**

Add to the existing `glyphs` object:
```json
"tabs": { "mine": "Mine", "favourites": "Favourites", "market": "Market" },
"submit": {
  "cta": "Submit to community",
  "pending": "Pending review",
  "approved": "Listed",
  "rejected": "Not accepted",
  "locked": "Listed — locked",
  "confirmTitle": "Submit this glyph to the community?",
  "confirmDescription": "It will be reviewed before appearing in the Market. Once submitted, you can no longer edit or delete it.",
  "confirm": "Submit",
  "cancel": "Cancel"
}
```
Extend `glyphs.empty` with per-tab entries (keep the existing `title`/`description`/`cta` for Mine):
```json
"marketTitle": "No glyphs for sale yet",
"marketDescription": "Community glyphs will appear here once they're approved.",
"favouritesTitle": "No favourites yet",
"favouritesDescription": "Glyphs you buy or favourite from the Market show up here."
```

- [ ] **Step 2: Add the `market` namespace (EN, top-level)**

```json
"market": {
  "price": "{amount} karma",
  "buy": "Buy",
  "owned": "Owned",
  "buyTitle": "Buy this glyph?",
  "buyDescription": "This spends {amount} karma. You'll be able to use it on your pebbles and souls.",
  "buyConfirm": "Buy for {amount} karma",
  "cancel": "Cancel",
  "favourite": "Add to favourites",
  "unfavourite": "Remove from favourites",
  "errors": {
    "insufficient": "You don't have enough karma for this glyph.",
    "notInMarket": "This glyph is no longer available.",
    "cannotBuyOwn": "You can't buy your own glyph.",
    "alreadyOwned": "You already own this glyph.",
    "generic": "Something went wrong. Please try again."
  }
}
```

- [ ] **Step 3: Mirror everything in FR**

```json
"tabs": { "mine": "Mes glyphes", "favourites": "Favoris", "market": "Marché" },
"submit": {
  "cta": "Proposer à la communauté",
  "pending": "En attente de validation",
  "approved": "Publié",
  "rejected": "Refusé",
  "locked": "Publié — verrouillé",
  "confirmTitle": "Proposer ce glyphe à la communauté ?",
  "confirmDescription": "Il sera examiné avant d'apparaître dans le Marché. Une fois proposé, vous ne pourrez plus le modifier ni le supprimer.",
  "confirm": "Proposer",
  "cancel": "Annuler"
}
```
`glyphs.empty` FR additions:
```json
"marketTitle": "Aucun glyphe en vente",
"marketDescription": "Les glyphes de la communauté apparaîtront ici après validation.",
"favouritesTitle": "Aucun favori",
"favouritesDescription": "Les glyphes achetés ou mis en favori depuis le Marché apparaissent ici."
```
`market` FR namespace:
```json
"market": {
  "price": "{amount} karma",
  "buy": "Acheter",
  "owned": "Acquis",
  "buyTitle": "Acheter ce glyphe ?",
  "buyDescription": "Cela dépense {amount} karma. Vous pourrez l'utiliser sur vos cailloux et vos âmes.",
  "buyConfirm": "Acheter pour {amount} karma",
  "cancel": "Annuler",
  "favourite": "Ajouter aux favoris",
  "unfavourite": "Retirer des favoris",
  "errors": {
    "insufficient": "Vous n'avez pas assez de karma pour ce glyphe.",
    "notInMarket": "Ce glyphe n'est plus disponible.",
    "cannotBuyOwn": "Vous ne pouvez pas acheter votre propre glyphe.",
    "alreadyOwned": "Vous possédez déjà ce glyphe.",
    "generic": "Une erreur est survenue. Veuillez réessayer."
  }
}
```

- [ ] **Step 4: Verify + commit**

Run: `npm run lint --workspace=apps/web` (and confirm both JSON files parse — `node -e "require('./apps/web/lib/i18n/messages/en.json'); require('./apps/web/lib/i18n/messages/fr.json')"`).

```bash
git add apps/web/lib/i18n/messages/en.json apps/web/lib/i18n/messages/fr.json
git commit -m "feat(ui): en/fr copy for glyph tabs, submit and market"
```

---

## Task 7: Tabs scaffold — `/glyphs` page + `GlyphTabs`

**Files:**
- Create: `apps/web/components/glyphs/GlyphTabs.tsx`
- Modify: `apps/web/app/glyphs/page.tsx`

- [ ] **Step 1: Create `GlyphTabs` (URL-synced segmented nav)**

```tsx
"use client"

import { useRouter, useSearchParams } from "next/navigation"
import { useTranslations } from "next-intl"

export type GlyphTab = "mine" | "favourites" | "market"
const TABS: GlyphTab[] = ["mine", "favourites", "market"]

export function GlyphTabs({ active }: { active: GlyphTab }) {
  const router = useRouter()
  const params = useSearchParams()
  const t = useTranslations("glyphs.tabs")

  const select = (tab: GlyphTab) => {
    const next = new URLSearchParams(params)
    next.set("tab", tab)
    router.replace(`/glyphs?${next.toString()}`)
  }

  return (
    <nav className="mb-6 flex gap-1 rounded-lg bg-muted p-1" role="tablist">
      {TABS.map((tab) => (
        <button
          key={tab}
          role="tab"
          aria-selected={active === tab}
          onClick={() => select(tab)}
          className={`flex-1 rounded-md px-3 py-1.5 text-sm font-medium transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${
            active === tab
              ? "bg-background text-foreground shadow-sm"
              : "text-muted-foreground hover:text-foreground"
          }`}
        >
          {t(tab)}
        </button>
      ))}
    </nav>
  )
}
```

- [ ] **Step 2: Rebuild `/glyphs/page.tsx` with tabs (Suspense-wrapped for `useSearchParams`)**

```tsx
"use client"

import { Suspense } from "react"
import Link from "next/link"
import { useSearchParams } from "next/navigation"
import { useTranslations } from "next-intl"
import { useMarks } from "@/lib/data/useMarks"
import { GlyphList } from "@/components/glyphs/GlyphList"
import { GlyphsEmptyState } from "@/components/glyphs/GlyphsEmptyState"
import { GlyphTabs, type GlyphTab } from "@/components/glyphs/GlyphTabs"
import { MarketGlyphs } from "@/components/glyphs/MarketGlyphs"
import { FavouriteGlyphs } from "@/components/glyphs/FavouriteGlyphs"
import { PageLayout } from "@/components/layout/PageLayout"
import { PageHeader } from "@/components/layout/PageHeader"

function GlyphsView() {
  const params = useSearchParams()
  const tab = (params.get("tab") as GlyphTab) ?? "mine"
  const t = useTranslations("glyphs")
  const { marks, loading } = useMarks()

  return (
    <section>
      <PageHeader
        title={t("title")}
        rightSlot={
          <Link
            href="/carve"
            className="text-sm font-medium text-primary underline underline-offset-4 hover:text-primary/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          >
            {t("carveNew")}
          </Link>
        }
      />
      <GlyphTabs active={tab} />

      {tab === "market" ? (
        <MarketGlyphs />
      ) : tab === "favourites" ? (
        <FavouriteGlyphs />
      ) : loading ? (
        <p className="text-sm text-muted-foreground">{t("loading")}</p>
      ) : marks.length === 0 ? (
        <GlyphsEmptyState />
      ) : (
        <GlyphList marks={marks} />
      )}
    </section>
  )
}

export default function GlyphsPage() {
  return (
    <PageLayout>
      <Suspense>
        <GlyphsView />
      </Suspense>
    </PageLayout>
  )
}
```

- [ ] **Step 3: Verify + commit**

`MarketGlyphs`/`FavouriteGlyphs` don't exist yet — lint/build will fail until Task 8/9. Commit the tab scaffold together with Task 8 if execution order matters; otherwise stub the two imports temporarily. Preferred: implement Task 8 and 9 before building, then verify all three together.

```bash
git add apps/web/components/glyphs/GlyphTabs.tsx apps/web/app/glyphs/page.tsx
git commit -m "feat(ui): glyph space tabs (mine/favourites/market)"
```

---

## Task 8: Market tab — card, list, buy dialog, wiring

**Files:**
- Create: `apps/web/components/glyphs/MarketGlyphCard.tsx`
- Create: `apps/web/components/glyphs/BuyGlyphDialog.tsx`
- Create: `apps/web/components/glyphs/MarketGlyphs.tsx`

- [ ] **Step 1: `BuyGlyphDialog` (async buy + inline error mapping)**

Uses `alert-dialog` primitives directly (ConfirmDialog's `onConfirm` is sync and can't show errors/loading).

```tsx
"use client"

import { useState, type ReactElement } from "react"
import { useTranslations } from "next-intl"
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent,
  AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog"

const ERROR_KEYS = ["insufficient", "notInMarket", "cannotBuyOwn", "alreadyOwned"] as const

function messageKey(error: unknown): string {
  const msg = error instanceof Error ? error.message : ""
  if (msg.includes("insufficient_karma")) return "insufficient"
  if (msg.includes("not_in_market")) return "notInMarket"
  if (msg.includes("cannot_buy_own")) return "cannotBuyOwn"
  if (msg.includes("already_owned")) return "alreadyOwned"
  return "generic"
}

type BuyGlyphDialogProps = {
  trigger: ReactElement
  amount: number
  onBuy: () => Promise<void>
}

export function BuyGlyphDialog({ trigger, amount, onBuy }: BuyGlyphDialogProps) {
  const t = useTranslations("market")
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [errorKey, setErrorKey] = useState<string | null>(null)

  const handleConfirm = async () => {
    setBusy(true)
    setErrorKey(null)
    try {
      await onBuy()
      setOpen(false)
    } catch (e) {
      setErrorKey(messageKey(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AlertDialog open={open} onOpenChange={setOpen}>
      <AlertDialogTrigger render={trigger} />
      <AlertDialogContent size="sm">
        <AlertDialogHeader>
          <AlertDialogTitle>{t("buyTitle")}</AlertDialogTitle>
          <AlertDialogDescription>{t("buyDescription", { amount })}</AlertDialogDescription>
        </AlertDialogHeader>
        {errorKey && (
          <p role="alert" className="text-sm text-destructive">
            {t(`errors.${errorKey}` as `errors.${(typeof ERROR_KEYS)[number]}` | "errors.generic")}
          </p>
        )}
        <AlertDialogFooter>
          <AlertDialogCancel disabled={busy}>{t("cancel")}</AlertDialogCancel>
          <AlertDialogAction
            onClick={(e) => {
              e.preventDefault() // keep the dialog open to show errors / busy state
              void handleConfirm()
            }}
            disabled={busy}
          >
            {t("buyConfirm", { amount })}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
```

- [ ] **Step 2: `MarketGlyphCard` (preview, price, buy/owned, favourite heart)**

```tsx
"use client"

import { useTranslations } from "next-intl"
import { Heart } from "lucide-react"
import type { MarketGlyph } from "@/lib/types"
import { GlyphPreview } from "@/components/glyphs/GlyphPreview"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { BuyGlyphDialog } from "@/components/glyphs/BuyGlyphDialog"

type MarketGlyphCardProps = {
  glyph: MarketGlyph
  onBuy: (glyph: MarketGlyph) => Promise<void>
  onFavourite: (glyphId: string, value: boolean) => void
}

export function MarketGlyphCard({ glyph, onBuy, onFavourite }: MarketGlyphCardProps) {
  const t = useTranslations("glyphs")
  const tMarket = useTranslations("market")

  return (
    <article className="flex items-center gap-4 rounded-lg border border-border px-4 py-3">
      <GlyphPreview mark={glyph} className="w-14 shrink-0 aspect-square" />
      <div className="min-w-0 flex-1">
        <h3 className="truncate text-sm font-medium">{glyph.name || t("untitled")}</h3>
        <p className="mt-1 text-xs text-muted-foreground">
          {tMarket("price", { amount: glyph.price })}
        </p>
      </div>

      <button
        type="button"
        onClick={() => onFavourite(glyph.id, !glyph.favourited)}
        aria-label={glyph.favourited ? tMarket("unfavourite") : tMarket("favourite")}
        aria-pressed={glyph.favourited}
        className="rounded-full p-2 text-muted-foreground transition hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <Heart className={`size-4 ${glyph.favourited ? "fill-current text-rose-500" : ""}`} />
      </button>

      {glyph.owned ? (
        <Badge variant="secondary">{tMarket("owned")}</Badge>
      ) : (
        <BuyGlyphDialog
          amount={glyph.price}
          onBuy={() => onBuy(glyph)}
          trigger={<Button size="sm">{tMarket("buy")}</Button>}
        />
      )}
    </article>
  )
}
```

- [ ] **Step 3: `MarketGlyphs` (tab content)**

```tsx
"use client"

import { useTranslations } from "next-intl"
import { useGlyphMarket } from "@/lib/data/useGlyphMarket"
import { MarketGlyphCard } from "@/components/glyphs/MarketGlyphCard"
import { EmptyState } from "@/components/layout/EmptyState"

export function MarketGlyphs() {
  const t = useTranslations("glyphs")
  const tEmpty = useTranslations("glyphs.empty")
  const { glyphs, loading, buy, favourite } = useGlyphMarket()

  if (loading) return <p className="text-sm text-muted-foreground">{t("loading")}</p>
  if (glyphs.length === 0)
    return <EmptyState title={tEmpty("marketTitle")} description={tEmpty("marketDescription")} />

  return (
    <ul className="flex flex-col gap-2">
      {glyphs.map((glyph) => (
        <li key={glyph.id}>
          <MarketGlyphCard glyph={glyph} onBuy={buy} onFavourite={favourite} />
        </li>
      ))}
    </ul>
  )
}
```

- [ ] **Step 4: Verify (deferred to Task 9 — `FavouriteGlyphs` still missing). Commit**

```bash
git add apps/web/components/glyphs/MarketGlyphCard.tsx apps/web/components/glyphs/BuyGlyphDialog.tsx apps/web/components/glyphs/MarketGlyphs.tsx
git commit -m "feat(ui): glyph market tab with buy flow"
```

---

## Task 9: Favourites tab

**Files:**
- Create: `apps/web/components/glyphs/FavouriteGlyphs.tsx`

- [ ] **Step 1: `FavouriteGlyphs` (reuses `MarketGlyphCard`)**

```tsx
"use client"

import { useTranslations } from "next-intl"
import { useGlyphFavourites } from "@/lib/data/useGlyphFavourites"
import { MarketGlyphCard } from "@/components/glyphs/MarketGlyphCard"
import { EmptyState } from "@/components/layout/EmptyState"

export function FavouriteGlyphs() {
  const t = useTranslations("glyphs")
  const tEmpty = useTranslations("glyphs.empty")
  const { glyphs, loading, favourite } = useGlyphFavourites()

  if (loading) return <p className="text-sm text-muted-foreground">{t("loading")}</p>
  if (glyphs.length === 0)
    return (
      <EmptyState title={tEmpty("favouritesTitle")} description={tEmpty("favouritesDescription")} />
    )

  return (
    <ul className="flex flex-col gap-2">
      {glyphs.map((glyph) => (
        <li key={glyph.id}>
          {/* Favourites are already owned/favourited; Buy still guarded by `owned`. */}
          <MarketGlyphCard glyph={glyph} onBuy={async () => {}} onFavourite={favourite} />
        </li>
      ))}
    </ul>
  )
}
```

- [ ] **Step 2: Verify the whole tab surface + commit**

Run: `npm run lint --workspace=apps/web` then `npm run build --workspace=apps/web`. Expect green (Tasks 7–9 now resolve each other). Manually: `/glyphs?tab=market` and `?tab=favourites` render (empty states until a row is approved).

```bash
git add apps/web/components/glyphs/FavouriteGlyphs.tsx
git commit -m "feat(ui): glyph favourites tab"
```

---

## Task 10: Mine — submit to community + D8 lock reflection

**Files:**
- Create: `apps/web/components/glyphs/SubmitToCommunity.tsx`
- Modify: `apps/web/components/glyphs/GlyphDetail.tsx`
- Modify: `apps/web/app/glyphs/[id]/page.tsx`

- [ ] **Step 1: `SubmitToCommunity` (status-aware action/badge)**

```tsx
"use client"

import { useState, type ReactElement } from "react"
import { useTranslations } from "next-intl"
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent,
  AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import type { GlyphSubmissionStatus } from "@/lib/types"

type SubmitToCommunityProps = {
  status?: GlyphSubmissionStatus // undefined = not submitted
  onSubmit: () => Promise<void>
}

export function SubmitToCommunity({ status, onSubmit }: SubmitToCommunityProps) {
  const t = useTranslations("glyphs.submit")
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)

  if (status) {
    const variant = status === "approved" ? "default" : status === "rejected" ? "destructive" : "secondary"
    return <Badge variant={variant}>{t(status)}</Badge>
  }

  const confirm = async () => {
    setBusy(true)
    try {
      await onSubmit()
      setOpen(false)
    } finally {
      setBusy(false)
    }
  }

  const trigger: ReactElement = <Button variant="outline" size="sm">{t("cta")}</Button>

  return (
    <AlertDialog open={open} onOpenChange={setOpen}>
      <AlertDialogTrigger render={trigger} />
      <AlertDialogContent size="sm">
        <AlertDialogHeader>
          <AlertDialogTitle>{t("confirmTitle")}</AlertDialogTitle>
          <AlertDialogDescription>{t("confirmDescription")}</AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={busy}>{t("cancel")}</AlertDialogCancel>
          <AlertDialogAction
            onClick={(e) => { e.preventDefault(); void confirm() }}
            disabled={busy}
          >
            {t("confirm")}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
```

- [ ] **Step 2: Add `locked` + `submitSlot` to `GlyphDetail`**

Extend `GlyphDetailProps`:
```ts
type GlyphDetailProps = {
  mark: Mark
  onDelete: () => void
  onUpdateName: (name: string | null) => Promise<void>
  locked?: boolean              // listed/bought → no edit/delete
  submitSlot?: React.ReactNode  // SubmitToCommunity rendered by the page
}
```
Then in the component:
- When `locked`, **hide** the edit pencil button and the delete `ConfirmDialog`, and show `glyphs.submit.locked` as a `Badge` where the delete button was.
- Render `submitSlot` in the header area (next to the title) when provided.
Apply minimally — wrap the pencil `<Button>` in `{!locked && (…)}`, and replace the bottom delete block with `locked ? <Badge variant="secondary">{tDetail wiring}</Badge> : <ConfirmDialog …/>`. Use `useTranslations("glyphs.submit")` for the `locked` label (add `const tSubmit = useTranslations("glyphs.submit")`).

- [ ] **Step 3: Wire the page**

In `apps/web/app/glyphs/[id]/page.tsx`:
- `const { submissions, submit } = useGlyphSubmissions()`
- `const submission = submissions.find((s) => s.glyph_id === id)`
- `const locked = submission?.status === "pending" || submission?.status === "approved"`
- Pass to `GlyphDetail`: `locked={locked}` and
  `submitSlot={<SubmitToCommunity status={submission?.status} onSubmit={() => submit(id).then(() => {})} />}`
- The system-seed case (a glyph with no `user_id`) can't be submitted; since the Mine list only shows the user's own glyphs this is moot, but guard `submitSlot` behind "is a real owned glyph" if the page can render seeds (it can't today — own-only load).

- [ ] **Step 4: Verify + commit**

Run: `npm run lint --workspace=apps/web` then `npm run build --workspace=apps/web`. Manually: open an owned glyph → "Submit to community" → confirm → badge flips to "Pending review"; the edit/delete controls disappear and a "Listed — locked" badge shows.

```bash
git add apps/web/components/glyphs/SubmitToCommunity.tsx apps/web/components/glyphs/GlyphDetail.tsx apps/web/app/glyphs/[id]/page.tsx
git commit -m "feat(ui): submit glyph to community and lock listed glyphs"
```

---

## Task 11: Picker — make bought glyphs usable (D7)

**Files:**
- Modify: `apps/web/lib/data/useLookupMaps.ts` (glyph rendering map)
- Modify: `apps/web/app/pebble/[id]/page.tsx`, `apps/web/app/pebble/[id]/edit/page.tsx`, `apps/web/app/souls/page.tsx`, `apps/web/app/souls/[id]/page.tsx`, `apps/web/components/path/PebblePeek.tsx` (picker feeders)

- [ ] **Step 1: Include entitled glyphs in the lookup map**

In `useLookupMaps.ts`, the `glyphMap` is built from `store.marks`. Change its source to `[...store.marks, ...store.entitledMarks]` (dedupe by id) so a bought glyph attached to a pebble/soul renders. Read the file first to match its exact construction; apply the smallest change that merges `entitledMarks`.

- [ ] **Step 2: Feed the picker from usable glyphs**

At each of the five call sites currently doing `const { marks } = useMarks()` and passing `marks` into `GlyphPickerDialog`, switch the picker's source to `useUsableGlyphs()`:
```ts
import { useUsableGlyphs } from "@/lib/data/useUsableGlyphs"
const { glyphs: usableGlyphs } = useUsableGlyphs()
```
Pass `usableGlyphs` (own ∪ entitled) to the `GlyphPickerDialog` `marks` prop. Keep any *other* uses of `marks` on those pages unchanged unless they also drive glyph display (in which case prefer `usableGlyphs` so bought glyphs resolve). Read each file before editing; this is a prop-source swap, not a refactor.

- [ ] **Step 3: Verify + commit**

Run: `npm run lint --workspace=apps/web` then `npm run build --workspace=apps/web`. Manually (after buying a glyph): open the glyph picker on a pebble → the bought glyph appears and can be attached and renders.

```bash
git add apps/web/lib/data/useLookupMaps.ts apps/web/app/pebble apps/web/app/souls apps/web/components/path/PebblePeek.tsx
git commit -m "feat(ui): make bought glyphs usable in the glyph picker"
```

---

## Final review

After all tasks: dispatch the whole-branch code review (subagent-driven-development's final step), then `superpowers:finishing-a-development-branch`. Before opening the PR, complete the spec §9 manual checklist against a hand-approved listing, append the three decision-log entries (spec §10), and add the gated bilingual Lab Note (this PR has `feat` + touches user-visible glyph surfaces).

---

## Self-review notes (author)

- **Spec coverage:** tabs (T7), submit→pending (T10), atomic buy/no-double/no-overdraw (T1 RPC + T8), favourites incl. bought (T1 view + T9), EN/FR (T6), activity pill (T5), D7 usable (T11), D8 lock (T1 RLS + T10 reflection), price-on-row + price_paid ledger (T1), market empty until approved (T1, no approve UI). All covered.
- **Type consistency:** `MarketGlyph`/`GlyphSubmission`/`GlyphSubmissionStatus` defined in T2, used unchanged in T3–T10. Provider method names match interface (T2) and impl (T3) and hooks (T4). `notifyGlyphPurchased(glyphId, name, amount)` consistent across T4/T5.
- **Known sequencing coupling:** T7 imports components from T8/T9; build green only after all three land. Flagged in T7 Step 3. Execute T7→T8→T9 before the first full build.
- **Risk to watch:** the `buyGlyph` reload-on-buy (T4 Step 5) — confirm `loadFromSupabase` is the right refresh and doesn't double-count; the existing pebble mutation hooks are the reference pattern.
