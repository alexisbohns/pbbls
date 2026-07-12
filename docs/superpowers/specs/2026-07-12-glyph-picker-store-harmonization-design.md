# Glyph picker ↔ store harmonization

**Date:** 2026-07-12
**Status:** Approved design, pending plan
**Scope:** `apps/web` (glyph picker UI) + `packages/supabase` (server-side ownership guard)

## Problem

When creating a pebble (and when creating/editing a soul), the glyph picker sheet
presents a flat list of glyphs and lets the user select any of them. The user
reports being able to pick glyphs they neither created nor bought. Two distinct
issues sit behind this:

1. **UX / product:** the picker is not aligned with the glyph store. It shows a
   single undifferentiated list, gives no sense of ownership, and offers no way
   to acquire a new glyph without leaving the flow. The user wants the picker to
   mirror the store — owned glyphs grouped, community glyphs buyable inline.

2. **Authorization gap (server):** `create_pebble` / `update_pebble` take
   `glyph_id` verbatim from the payload and insert it with **no ownership check**
   (contrast the explicit collection-ownership check in the same RPC). Souls
   write `glyph_id` directly to the `souls` table, also unchecked. The client is
   the only gate, so any client — web, iOS, Android — can attach a glyph the user
   does not own.

### On the reported "leak"

The picker is fed by exactly one hook, `useUsableGlyphs()`
(`apps/web/lib/data/useUsableGlyphs.ts`), which returns `store.marks` (glyphs
where `user_id = you`) ∪ `store.entitledMarks` (`v_glyph_market` where
`owned = true`). Both are correctly scoped in code. The most likely reason the
user sees "everything" is that **their account authored the catalog**:
`publish_admin_glyph` stamps `user_id = the admin` on every glyph it publishes,
so for the publisher/admin account those glyphs are literally "Mine" and thus
appear in the picker (while `listMarketGlyphs` hides them from the market). A
normal user would not see them. **Decision:** this is acceptable and truthful —
"Mine" = every glyph with `user_id = you`, admin-published included. The redesign
does not try to hide authored-and-listed glyphs. Empirical confirmation that no
*foreign* glyph (neither authored nor entitled) reaches the picker is a
verification step, not a code change; the server guard below makes any residual
leak non-exploitable.

## Goals

- Replace the flat picker with a tabbed sheet: **Mine · Owned · Community**.
- Mine and Owned glyphs are directly selectable. Community glyphs are buyable
  inline; a successful purchase auto-selects the glyph and closes the sheet.
- Enforce glyph usability **server-side** for pebbles (RPC) and souls (trigger),
  so no client can attach an unowned glyph.
- Reuse existing store primitives; do **not** refactor the working store page.

## Non-goals

- No shared "one browser for store + picker" extraction. (Possible future unifier;
  out of scope — would require rewiring the store page, which is gated behind
  explicit approval.)
- No new "free defaults" surface in the picker. Pebble glyph is optional, so a
  new user with an empty Mine/Owned simply attaches no glyph. System default
  glyphs remain usable server-side (grandfathering + souls' default) but are not
  offered in the pebble picker.
- No changes to karma pricing, favourites, or the submission/curation flow.

## Design

### 1. Tabbed glyph picker (`apps/web`)

**Definition of "usable" the UI mirrors (single source of truth):**
a glyph is usable by a user iff it was authored by them (`glyphs.user_id = user`),
it is a system default (`glyphs.user_id IS NULL`), or the user holds an
entitlement. The picker surfaces the first and third of these as Mine / Owned.

**Component shape.** `GlyphPickerDialog` currently wraps `PickerSheet` around a
single `GlyphPickerGrid`. Introduce a tabbed body inside the same sheet:

```
GlyphPickerDialog (PickerSheet)
└── GlyphPickerTabs                      ← new: 3-tab shell (Mine · Owned · Community)
    ├── Mine       → GlyphPickerGrid(marks = store.marks)                  [selectable]
    ├── Owned      → GlyphPickerGrid(marks = store.entitledMarks)          [selectable]
    └── Community  → GlyphMarketPickerList(useGlyphMarket, onBought)       [buy → auto-select]
```

- **Tab shell.** A local, sheet-scoped tab control (does not reuse `GlyphTabs`,
  which is URL/router-driven for the `/glyphs` page). Tabs: `mine | owned | community`.
  Follow the existing `GlyphTabs` visual style (segmented control) but drive state
  with local `useState`, not the router.
- **Mine / Owned tabs.** Reuse `GlyphPickerGrid` unchanged, fed the two existing
  arrays. Selecting calls the existing `onSelect` → `onSave` → close path.
  `store.entitledMarks` are `MarketGlyph`s, a superset of `Mark`, so they satisfy
  the grid's `Mark[]` prop directly.
- **Community tab.** A new `GlyphMarketPickerList` that consumes `useGlyphMarket()`
  and filters to `!owned` (owned market glyphs already appear under Owned; the
  user's own creations are already excluded by `listMarketGlyphs`). Each row
  reuses `GlyphPreview` + `BuyGlyphDialog` (same buy affordance as the store).
  On a successful `buy(glyph)`:
  1. `useGlyphMarket.buy` already reloads the store (`entitledMarks` + karma) and
     refreshes the market list.
  2. The picker then calls `onSave(glyph.id)` and closes — the just-bought glyph
     is selected. On next open it appears under Owned.
- **Empty states.** Each tab has its own empty copy (existing
  `glyphs.picker.empty` for Mine/Owned; a new market-empty string for Community).

**Call sites (unchanged wiring).** `QuickPebbleEditor`, `AddSoulForm`,
`SoulDetailHeader` all render `GlyphPickerDialog` and pass `marks` today. The
dialog will source Mine/Owned/Community itself via hooks, so those props change:
`GlyphPickerDialog` stops taking a `marks` prop and instead reads
`useDataProvider` (Mine/Owned) + `useGlyphMarket` (Community) internally. The
three call sites drop the `marks={…}` prop and the now-unused `useUsableGlyphs`
import. `useUsableGlyphs` is removed if it has no other consumers (verify first;
`useLookupMaps` uses `store.entitledMarks` directly, not this hook).

**i18n.** Add tab labels (`record.glyph.tabs.mine|owned|community`) and a
community empty-state string, EN + FR, in the existing message catalogs.

### 2. Server-side ownership guard (`packages/supabase`)

New migration `packages/supabase/supabase/migrations/<ts>_glyph_usability_guard.sql`:

**a. Helper function.**
```sql
create or replace function public.can_use_glyph(p_glyph_id uuid, p_user uuid)
returns boolean
language sql stable security definer set search_path = public as $$
  select p_glyph_id is null
      or exists (
        select 1 from public.glyphs g
        where g.id = p_glyph_id
          and (g.user_id = p_user or g.user_id is null)
      )
      or exists (
        select 1 from public.glyph_entitlements e
        where e.glyph_id = p_glyph_id and e.user_id = p_user
      );
$$;
```
`security definer` so the entitlement check sees the row regardless of the
caller's RLS context; the `p_user` argument is always the row's owner
(`auth.uid()` in the RPCs, `NEW.user_id` in the trigger). A `NULL` glyph is
"usable" (pebble glyph is optional).

**b. `create_pebble` / `update_pebble`.** Recreate both to add, right after the
final `v_glyph_id` is resolved (covering both the inline-`new_glyph` and the
`glyph_id` branches):
```sql
if not public.can_use_glyph(v_glyph_id, v_user_id) then
  raise exception 'Glyph not usable by user: %', v_glyph_id using errcode = '42501';
end if;
```
Recreating `update_pebble` also **drops its stale `shape_id` reference** in the
inline `new_glyph` INSERT (the `glyphs.shape_id` column was removed in
`20260701114205`; the current `update_pebble` body still references it and would
fail on an inline-glyph update — a latent bug fixed here as a side effect of the
recreate).

**c. Souls trigger.** Souls are written directly (no RPC), so guard at the table:
```sql
create or replace function public.enforce_soul_glyph_usable()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if not public.can_use_glyph(new.glyph_id, new.user_id) then
    raise exception 'Glyph not usable by user: %', new.glyph_id using errcode = '42501';
  end if;
  return new;
end; $$;

create trigger souls_glyph_usable
  before insert or update of glyph_id on public.souls
  for each row execute function public.enforce_soul_glyph_usable();
```
The souls default glyph (`4759c37c-…`, a `user_id IS NULL` system glyph) passes
via the `user_id IS NULL` branch, so existing souls and the column default remain
valid.

**d. Types.** Regenerate `packages/supabase/types/database.ts`
(`npm run db:types:remote --workspace=packages/supabase`, per project convention)
and commit it.

## Error handling

- **Buy failure in Community tab** (e.g. `insufficient_karma`): `BuyGlyphDialog`
  already surfaces the thrown message; the sheet stays open, nothing is selected.
- **Server guard rejection** (`42501`): should never happen from the redesigned
  UI (it only offers usable glyphs). If a stale/rogue client sends an unowned
  `glyph_id`, the RPC/trigger raises and the create/update fails cleanly. The web
  provider surfaces the RPC error via its existing error path.
- **Empty Owned/Mine:** handled by per-tab empty states; not an error.

## Testing / verification

No automated test harness in the web app yet (V1). Verification is manual +
build/lint at change scope:

- **Picker:** as a non-publisher account, open the pebble create sheet →
  Community tab lists buyable glyphs; buying one selects it and moves it to Owned;
  Mine/Owned only list owned glyphs. Repeat via a soul (create + edit).
- **Leak check:** confirm no glyph that is neither authored nor entitled appears
  under Mine or Owned for a normal account. (If one does, that is a separate
  data/RLS bug to debug — the guard still blocks attaching it.)
- **Server guard:** call `create_pebble` / a direct soul insert with a foreign
  `glyph_id` → expect `42501`. Existing pebbles/souls on system-default glyphs
  still save.
- `npm run lint --workspace=apps/web`; `npm run build` if shared types change
  (they do — `database.ts`). Migration applied to remote per project workflow.

## Rollout / ordering

1. Migration (`can_use_glyph`, RPC recreates, souls trigger) + regenerated types.
2. Picker tab shell + Community list + call-site prop changes + i18n.
3. Manual verification, lint/build, Arkaik check (no new nodes — same views; a
   picker enhancement, not a new screen — likely a no-op map update).

## Open questions

None outstanding. Admin-glyph treatment ("show as Mine") and souls-guard scope
("cover now, via trigger") were decided during design.
