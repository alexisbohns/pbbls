# Glyph marketplace — share, submit, favourite & buy glyphs (#496)

**Sub-project C of 4** in **M36 · Pebblestore & Karma Economy**. The first thing the
Pebblestore actually sells: **community glyphs**, bought with karma over Sub-project
A's spend rails, surfaced via Sub-project B's in-app activity.

- **Depends on:** A (`spend_karma` RPC, wallet store) and B (`notifyKarma` activity primitive).
- **Feeds:** D (admin moderation + raw-SVG upload moderates the submissions this creates).

---

## 1. Context & current state

Glyphs are typed **`Mark`** in code (DB table `glyphs`; UI says "glyph"). Hooks `useMarks`/
`useMark`; provider methods `listMarks`/`getMark`/`createMark`/`updateMark`/`deleteMark`.
The `Mark` domain type is `{ id, name?, shape_id, strokes, viewBox, created_at, updated_at }`
(no `user_id`/`is_custom` surfaced client-side). `glyphs.user_id` is nullable — `NULL` rows
are per-domain system seeds; `is_custom` is a generated column (`user_id IS NOT NULL`).

Today glyphs are **single-owner** (`glyphs.user_id`), loaded own-only
(`.eq("user_id", userId)`). There is **no sharing, no favourites, no ownership beyond the
creator, no market.** Surfaces: `/glyphs` gallery (`GlyphList`/`GlyphCard`/`GlyphDetail`),
`/carve` editor, `GlyphPickerDialog` for attaching a glyph to pebbles/souls.

Sub-project A already exposes `spendKarma(amount, "purchase", refId?)` on `DataProvider`
and the `spend_karma(p_amount, p_reason, p_ref_id)` RPC (race-safe, row-locked, raises
`insufficient_karma`). Sub-project B exposes `notifyKarma(amount, reason)` (credit-only;
no-ops on non-positive delta) plus the Sonner `Toaster` mounted in `app/layout.tsx`.

---

## 2. Decisions (locked)

- **D1 — Buying grants use-rights, not a copy.** A purchase inserts an *entitlement* row
  granting the buyer the right to **use** the original glyph (attach to pebbles/souls). One
  source of truth; the creator keeps authorship; no stroke duplication.
- **D2 — Price lives on the listing row, flat-defaulted.** `glyph_submissions.price` is a
  per-listing column defaulting to a single constant (`GLYPH_PRICE_DEFAULT`). Flat today
  (every row gets the default), but per-glyph already, so D can re-price one glyph later
  without a schema change. The buy RPC reads the price **from the approved row** — the
  client never supplies it.
- **D3 — The submission row *is* the market listing.** `glyph_submissions.status` drives it:
  `pending` (awaiting D) → `approved` (live in Market) → `rejected`. No separate listing table.
- **D4 — Purchase history is captured now, analytics deferred (YAGNI).** `glyph_entitlements`
  records `(glyph_id, user_id, karma_event_id, price_paid, created_at)` per purchase — enough
  to derive buyers-per-glyph, buyers-per-month, and per-glyph revenue *whenever* those views
  are built. `price_paid` is a **snapshot** so history survives any future price change.
  "Glyph value" stays a derived aggregate (`SUM(price_paid)`, `COUNT(buyers)`, …), never a
  stored column.
- **D5 — Market stays empty until D approves.** Submissions land `pending`; Market shows only
  `status='approved'`. Until D ships, approve a row by hand (`UPDATE … SET status='approved'`)
  to test the buy flow. C ships **no** approve/reject UI.
- **D6 — Purchase fires a *spend* activity, reusing B's Sonner infra.** A purchase is a debit,
  so B's credit-only `notifyKarma` deliberately won't fire. C adds a sibling
  `notifyGlyphPurchased(glyphId, name, amount)` rendering a **"✨ Glyph unlocked · −N karma"**
  pill (tappable → the glyph's detail).
- **D7 — Entitlement = usable everywhere own glyphs are.** For the grant to mean anything,
  entitled glyphs appear in the glyph picker alongside owned glyphs (own ∪ entitled).
- **D8 — A listed/bought glyph is immutable to its creator (backend-enforced).** Once a glyph
  is actively listed (`pending`|`approved`) or has any entitlement, the creator can no longer
  edit or delete it — **only an admin can** (D's curation domain). Enforced in RLS (§3.4), not
  just the UI; a frontend-only lock would be bypassable via the raw update path. Rationale:
  the strokes are what buyers paid for (no bait-and-switch), and `on delete cascade` would
  otherwise let a creator erase buyers' entitlements.

---

## 3. Data model

Three new tables, all under RLS. Migrations in `packages/supabase/supabase/migrations/`.
Regenerate `packages/supabase/types/database.ts` after (`npm run db:types --workspace=packages/supabase`).

### 3.1 `glyph_submissions` — submission *and* market listing

```sql
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

-- At most one *active* (pending or approved) submission per glyph.
create unique index glyph_submissions_one_active
  on public.glyph_submissions (glyph_id)
  where status in ('pending','approved');
```

`reviewed_at`/`reviewed_by` are written by D (left null by C). The `price` default literal
must match `GLYPH_PRICE_DEFAULT` in TS (§5.1) — documented in a migration comment.

### 3.2 `glyph_entitlements` — the use-rights grant + purchase ledger

```sql
create table public.glyph_entitlements (
  id              uuid primary key default gen_random_uuid(),
  user_id         uuid not null references auth.users(id),       -- buyer
  glyph_id        uuid not null references public.glyphs(id) on delete cascade,
  karma_event_id  uuid not null references public.karma_events(id),
  price_paid      integer not null check (price_paid > 0),       -- snapshot at purchase
  created_at      timestamptz not null default now(),
  unique (user_id, glyph_id)   -- idempotency: no double-buy
);
```

### 3.3 `glyph_favourites`

```sql
create table public.glyph_favourites (
  user_id     uuid not null references auth.users(id),
  glyph_id    uuid not null references public.glyphs(id) on delete cascade,
  created_at  timestamptz not null default now(),
  primary key (user_id, glyph_id)
);
```

### 3.4 RLS

```sql
alter table public.glyph_submissions enable row level security;
alter table public.glyph_entitlements enable row level security;
alter table public.glyph_favourites   enable row level security;

-- Submissions: read your own + any approved (the Market). Writes go through RPCs / D.
create policy glyph_submissions_select on public.glyph_submissions for select
  to authenticated
  using (submitter_id = auth.uid() or status = 'approved');

-- Entitlements: read your own. Inserts only via buy_glyph (security definer bypasses RLS).
create policy glyph_entitlements_select on public.glyph_entitlements for select
  to authenticated using (user_id = auth.uid());

-- Favourites: full self-management.
create policy glyph_favourites_all on public.glyph_favourites for all
  to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
```

**Critical knock-on — rewrite `glyphs` SELECT/UPDATE/DELETE.** Today (from
`20260415000001_remote_pebble_engine.sql` + `20260411000001_core_tables.sql`):

```sql
glyphs_select : using (user_id = auth.uid() or user_id is null)   -- own ∪ system seeds
glyphs_update : using (user_id = auth.uid())
glyphs_delete : using (user_id = auth.uid())
```

Two changes, both drop-and-recreate the named policies (preserving the `user_id is null`
system-seed clause):

**(a) Widen SELECT** so the Market and picker can read *other people's* listed/bought glyph
rows (strokes/viewBox/name) — own ∪ system seeds ∪ approved-in-market ∪ entitled:

```sql
drop policy if exists "glyphs_select" on public.glyphs;
create policy "glyphs_select" on public.glyphs for select to authenticated
  using (
    user_id = auth.uid()
    or user_id is null                                          -- system seeds
    or exists (select 1 from public.glyph_submissions s
               where s.glyph_id = glyphs.id and s.status = 'approved')
    or exists (select 1 from public.glyph_entitlements e
               where e.glyph_id = glyphs.id and e.user_id = auth.uid())
  );
```

**(b) Lock listed/bought glyphs (D8).** A creator may no longer UPDATE or DELETE a glyph once
it is actively listed (`pending`|`approved`) **or** has been bought by anyone — admins are
exempt (D curates listed glyphs). This is the integrity rule behind the user's "once locked,
not editable except by admin": the strokes are what buyers paid for, and `on delete cascade`
would otherwise let a creator wipe buyers' entitlements.

```sql
-- shared lock predicate, inlined into both policies:
--   not actively listed AND not bought
--   not exists (… glyph_submissions s where s.glyph_id = id and s.status in ('pending','approved'))
--   not exists (… glyph_entitlements e where e.glyph_id = id)

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
  using ( /* identical predicate to glyphs_update */ );
```

Verify the exact live policy names/bodies before dropping (they match the snapshot above as of
this spec).

---

## 4. RPCs

Both `security definer set search_path = public`, granted to `authenticated`, revoked from
`public`/`anon`. New migration alongside the tables.

### 4.1 `submit_glyph(p_glyph_id uuid) returns uuid`

Multi-table (reads `glyphs`, writes `glyph_submissions`) → RPC per AGENTS.md.

Logic / guards (each raises a distinct error):
1. `not_authenticated` if `auth.uid()` is null.
2. `not_owner` if the glyph's `user_id <> auth.uid()`.
3. `not_custom` if the glyph is a system seed (`user_id is null` / `is_custom = false`).
4. `already_submitted` if an active (pending|approved) submission exists for it
   (the partial-unique index is the backstop; pre-check gives the clean error).
5. Insert `(glyph_id, submitter_id, status='pending', price=default)`; return the new id.

### 4.2 `buy_glyph(p_glyph_id uuid) returns table(entitlement_id uuid, balance integer)`

Atomic spend + grant in **one transaction**.

Logic / guards:
1. `not_authenticated` if `auth.uid()` is null.
2. Read the **approved** submission for `p_glyph_id` (`for share`); raise `not_in_market`
   if none. Capture its `price`.
3. `cannot_buy_own` if the glyph's `user_id = auth.uid()` (creators already hold use-rights).
4. `already_owned` if an entitlement already exists for `(auth.uid(), p_glyph_id)`.
5. `v_event_id := spend_karma(price, 'purchase', p_glyph_id)` — reuses A's row-locked guard;
   propagates `insufficient_karma` on overdraw.
6. Insert `glyph_entitlements (user_id, glyph_id, karma_event_id=v_event_id, price_paid=price)`.
   The `unique(user_id, glyph_id)` is the race backstop — a concurrent double-buy rolls back
   the loser's spend too (same txn), so the buyer is charged at most once.
7. Return `(entitlement_id, new wallet balance)` so the client updates the karma store
   without a full reload.

**Error contract** (surfaced to UI, mapped EN/FR): `not_authenticated`, `not_in_market`,
`cannot_buy_own`, `already_owned`, `insufficient_karma`.

---

## 5. Data layer (apps/web)

### 5.1 Types & price constant

`lib/config/glyphs.ts` (already holds `DEFAULT_GLYPH_ID`): add
`export const GLYPH_PRICE_DEFAULT = 25` — display + sanity mirror of the SQL default
(documented to stay in sync; server is authoritative).

`lib/types.ts`:
```ts
export type GlyphSubmissionStatus = "pending" | "approved" | "rejected"

export type MarketGlyph = Mark & {
  price: number
  owned: boolean        // caller is entitled (bought)
  favourited: boolean   // caller has favourited
}

export type GlyphSubmission = {
  id: string
  glyph_id: string
  status: GlyphSubmissionStatus
  price: number
  created_at: string
}
```

### 5.2 `DataProvider` additions

```ts
listMarketGlyphs(): Promise<MarketGlyph[]>          // approved ∪ flags for caller
listFavouriteGlyphs(): Promise<MarketGlyph[]>       // entitled ∪ favourited
listUsableGlyphs(): Promise<Mark[]>                 // own ∪ entitled (for the picker, D7)
                                                   // returns Mark[] until #488 renames the type
getMySubmissions(): Promise<GlyphSubmission[]>      // for Mine-tab status badges
submitGlyph(glyphId: string): Promise<GlyphSubmission>
buyGlyph(glyphId: string): Promise<{ entitlementId: string; karma: number }>
setFavourite(glyphId: string, favourite: boolean): Promise<void>
```

- **SupabaseProvider:** `submitGlyph`/`buyGlyph` call the RPCs; `buyGlyph` returns the
  RPC's `balance` so the karma store updates in place. `setFavourite` is a single-table
  insert/delete (RLS-guarded) — direct client call, no RPC. List methods join
  `glyph_submissions`/`glyph_entitlements`/`glyph_favourites` to `glyphs` and map to
  `MarketGlyph` (reuse the existing row→`Mark` mapper).
- **LocalProvider:** mirror with local arrays. The local market is inherently self-only, so
  `listMarketGlyphs` returns approved local submissions (empty by default). `buyGlyph`
  debits local karma via the existing local spend path. Keeps the interface honest for
  local-mode dev.

### 5.3 Hooks

- `useGlyphMarket()` → `{ glyphs, loading, buy, favourite, error }` (Market tab).
- `useGlyphFavourites()` → `{ glyphs, loading, favourite }` (Favourites tab).
- `useGlyphSubmissions()` → `{ submissions, loading, submit }` (Mine-tab badges + submit action).
- `buy` updates the karma store from the RPC's returned balance, then fires
  `notifyGlyphPurchased` (§6). It does **not** go through `notifyKarma`.

---

## 6. Activity — reuse B's primitive (D6)

- `components/activity/GlyphPurchasePill.tsx` — sibling of `KarmaActivityPill`, same
  always-dark capsule, `Sparkle` icon, text **"Glyph unlocked"** + **"−{amount} karma"**.
  Tappable → the glyph's detail (`/glyphs/<id>`). Same a11y treatment: full sentence via
  `aria-label`, visible content `aria-hidden`, `focus-visible` ring, `motion-reduce`.
- `lib/activity/glyph-activity.tsx` — `notifyGlyphPurchased(glyphId, name, amount)` via
  `toast.custom` with stable id `"glyph-activity"` (replace-on-new), `duration: 3000`,
  wrapped in `flex w-full justify-center` (the centering fix B already learned).

---

## 7. UI

### 7.1 `/glyphs` — three tabs

shadcn `Tabs`: **Mine** · **Favourites** · **Market**. Tab state in the URL (`?tab=`,
default `mine`) so the purchase pill and favourites are deep-linkable. New
`components/glyphs/GlyphTabs.tsx` (client) owns tab state from `useSearchParams`.

- **Mine** — existing `GlyphList`; each owned **custom** glyph gains a status-aware
  "Submit to community" affordance (in `GlyphDetail` and/or card): not-submitted → CTA;
  `pending`/`approved`/`rejected` → a badge. System seeds show no submit CTA. **Reflect the
  D8 lock:** when a glyph is actively listed or bought, `GlyphDetail` hides the
  edit-name/delete controls and shows a "Listed — locked" indicator (the RLS policy is the
  real enforcement; this is just honest UX so the user isn't offered an action that will fail).
- **Market** — `MarketGlyphList` + `MarketGlyphCard` (glyph preview, karma price, Buy /
  "Owned" state, favourite heart). Buy opens a shadcn confirm dialog → `buyGlyph`; on
  success the glyph moves into Favourites and the pill fires; `insufficient_karma` and other
  errors render inline (mapped EN/FR). Empty state when no approved listings.
- **Favourites** — entitled ∪ favourited, rendered with the market card; heart toggles
  favourite. Bought glyphs always appear here even if not explicitly favourited. Empty state.

### 7.2 Picker (D7)

`GlyphPickerDialog`/`GlyphPickerGrid` source from `listUsableGlyphs()` (own ∪ entitled) so a
bought glyph is attachable to pebbles/souls. (Confirm exact current data source during
implementation.)

### 7.3 i18n (EN + FR, both files)

- `glyphs.tabs.{mine,favourites,market}`
- `glyphs.empty.{market,favourites}.{title,description}` (Mine keeps the existing empty state)
- `glyphs.submit.{cta,pending,approved,rejected,confirmTitle,confirmDescription,confirm,cancel}`
- `market.{price,buy,owned,buyTitle,buyDescription,buyConfirm,cancel,favourite,unfavourite}`
  (`favourite`/`unfavourite` are aria-labels; `price` = `"{amount} karma"`)
- `market.errors.{insufficient,notInMarket,cannotBuyOwn,alreadyOwned,generic}`
- `activity.{glyphUnlocked,spent,srUnlocked,viewGlyph}` (`spent` = `"−{amount} karma"`;
  `srUnlocked` = the full screen-reader sentence). FR mirrors using existing tone
  (e.g. wallet is "Porte-karma").

---

## 8. Out of scope (→ Sub-project D)

Admin moderation UI (approve/reject), raw-SVG upload/adjust, per-glyph price *editing* UI,
themes/pebbleskins, creator payouts / revenue-share / resale. No Arkaik view-node change
beyond the glyph tabs (revisit during the plan: tabs may warrant a bundle note).

---

## 9. Verification

No web test runner (V1) — verification is lint + build + manual, per the prior sub-projects.

- `npm run lint --workspace=apps/web` and `next build` green; `npm run db:types` regenerated
  and committed.
- **Manual (needs an authenticated dev session + a hand-approved listing):**
  1. Submit an owned custom glyph → lands `pending`, badge shows; system seeds show no CTA.
  2. `UPDATE glyph_submissions SET status='approved' WHERE …` → glyph appears in Market with price.
  3. Buy with sufficient karma → karma debits by `price`, "✨ Glyph unlocked · −N karma" pill
     fires, glyph appears under Favourites, Buy becomes "Owned".
  4. Re-buy the same glyph → blocked (`already_owned`), not charged again.
  5. Buy with insufficient karma → inline error, no debit.
  6. Buy your own approved glyph → blocked (`cannot_buy_own`).
  7. Favourite/unfavourite a market glyph → moves in/out of Favourites.
  8. Attach a bought glyph to a pebble via the picker (D7).
  9. **D8 lock:** with a glyph listed (pending or approved) or bought, `updateMark`/`deleteMark`
     are rejected by RLS for the creator (and the UI hides the controls); an admin can still
     edit. Confirm a non-listed glyph remains editable as before.
  10. EN + FR across all new surfaces; pill respects reduced-motion + dark/color-world.

---

## 10. Decision-log entries to append at PR time

- Glyph buy = use-rights entitlement (not a copy); price on the listing row, flat-defaulted;
  `price_paid` snapshot captures the purchase ledger for deferred per-glyph analytics.
- `buy_glyph` RPC wraps `spend_karma` + entitlement insert atomically; idempotent via
  `unique(user_id, glyph_id)`. New goods reuse this shape.
- `glyphs` SELECT widened to own ∪ system seeds ∪ approved-listed ∪ entitled — the one place
  community glyph rows become readable; future market goods must keep this policy in mind.
- Listed/bought glyphs are immutable to their creator (RLS UPDATE/DELETE lock, admin-exempt) —
  protects what buyers paid for; only an admin (D) may edit a listed glyph.
