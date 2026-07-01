# Remove the deprecated glyph shape (#503) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Drop the deprecated `glyphs.shape_id` column and retire the orphaned `pebble_shapes` reference table across DB, web, admin, and iOS.

**Architecture:** Pure cleanup — every surface already writes `shape_id = null`, so no behavior changes. Remove the column's readers/writers first (code), then flip the schema (migration + regenerated types) last, so every commit compiles. `CreateMarkInput`/`UpdateMarkInput` derive from `Mark` via `Omit`/`Partial`, so removing `shape_id` from the `Mark` type cascades through the compiler and surfaces every web edit site.

**Tech Stack:** Next.js 16 / React 19 / TypeScript (web + admin), Supabase Postgres migrations + generated types, Swift (iOS).

**Spec:** `docs/superpowers/specs/2026-07-01-remove-glyph-shape-design.md`

**Branch:** `quality/503-remove-glyph-shape` (already created)

---

## Ordering rationale (read once)

The one hard constraint: `.from("glyphs").insert({ shape_id })` in the web provider is typed against the generated `Database` types. If we regenerate types (dropping `shape_id`) **before** removing that insert key, the build breaks. So:

1. Tasks 1–6 remove all `shape_id` code while the **old** types (which still contain `shape_id`) are in place. Removing an optional/nullable field is always type-safe against the old types.
2. Task 7 applies the migration and regenerates types. The code no longer references `shape_id`, so it still compiles.
3. Task 8 does docs + the final full build/lint.

The DB migration **file** is authored in Task 1 but **not applied** until Task 7.

---

## Task 1: Author the database migration

**Files:**
- Create: `packages/supabase/supabase/migrations/<timestamp>_drop_glyph_shape.sql`

Source-of-truth for the current definitions you will copy and edit:
- `create_pebble` → `20260426000001_pebbles_pictures.sql:72`
- `publish_admin_glyph` → `20260630084718_admin_glyph_moderation.sql:137`
- `admin_list_glyph_submissions` → `20260701102810_glyph_marketplace_curation.sql:166`
- `v_pebbles_full` → `20260411000002_views.sql:10`
- `v_glyph_market` → `20260701102810_glyph_marketplace_curation.sql:32`

- [ ] **Step 1: Generate the migration file**

Run: `npm run db:migration:new drop_glyph_shape --workspace=packages/supabase`
Expected: a new empty file `packages/supabase/supabase/migrations/<timestamp>_drop_glyph_shape.sql`.
Fallback if the CLI is unavailable: create the file manually with a timestamp later than `20260701102810` (e.g. `20260701130000_drop_glyph_shape.sql`).

- [ ] **Step 2: Write the migration header + RPC updates**

Paste this into the file. For `create_pebble` and `admin_list_glyph_submissions`, copy the **full current body** from the source file listed above, then make the single edit noted in the comment.

```sql
-- Remove the deprecated glyph shape (#503).
-- Glyphs are shape-agnostic squares (issue #278); shape_id and pebble_shapes
-- are backward-compat cruft with no live data. Every surface already writes null.
-- Order: stop all dependents referencing glyphs.shape_id, THEN drop the column,
-- THEN drop the orphaned pebble_shapes table.

-- 1. create_pebble: stop inserting glyphs.shape_id.
--    Copy the CURRENT full body from 20260426000001_pebbles_pictures.sql (line 72),
--    then in the inline glyph INSERT remove `shape_id,` from the column list and
--    remove the `(payload->'new_glyph'->>'shape_id')::uuid,` value line.
--    The INSERT becomes:
--      insert into public.glyphs (user_id, name, strokes, view_box)
--      values (
--        v_user_id,
--        (payload->'new_glyph'->>'name'),
--        coalesce(payload->'new_glyph'->'strokes', '[]'::jsonb),
--        (payload->'new_glyph'->>'view_box')
--      )
--      returning id into v_glyph_id;
create or replace function public.create_pebble(payload jsonb)
returns uuid as $$
-- <<< paste full current body here, with the glyph INSERT edited as above >>>
$$ language plpgsql security definer set search_path = public;
```

> Note: reproduce the `language ... security definer set search_path ...` clause exactly as it appears at the end of the current `create_pebble` definition — do not guess it.

- [ ] **Step 3: Add the `publish_admin_glyph` drop + recreate**

The signature changes (drops `p_shape_id`), so `CREATE OR REPLACE` is not enough — drop first. Append:

```sql
-- 2. publish_admin_glyph: drop the p_shape_id parameter (signature change → drop+recreate).
drop function if exists public.publish_admin_glyph(text, uuid, jsonb, text, integer);

create function public.publish_admin_glyph(
  p_name text,
  p_strokes jsonb,
  p_view_box text,
  p_price integer
)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_user          uuid := auth.uid();
  v_glyph_id      uuid;
  v_submission_id uuid;
begin
  if not public.is_admin(v_user) then
    raise exception 'not_admin' using errcode = '42501';
  end if;
  if p_price <= 0 then raise exception 'bad_price'; end if;
  if p_strokes is null or jsonb_array_length(p_strokes) = 0 then
    raise exception 'empty_glyph';
  end if;

  insert into public.glyphs (user_id, name, strokes, view_box)
  values (v_user, nullif(btrim(p_name), ''), p_strokes, p_view_box)
  returning id into v_glyph_id;

  insert into public.glyph_submissions
    (glyph_id, submitter_id, status, price, reviewed_at, reviewed_by)
  values (v_glyph_id, v_user, 'approved', p_price, now(), v_user)
  returning id into v_submission_id;

  return jsonb_build_object('glyph_id', v_glyph_id, 'submission_id', v_submission_id);
end;
$$;

revoke all on function public.publish_admin_glyph(text, jsonb, text, integer) from public, anon;
grant execute on function public.publish_admin_glyph(text, jsonb, text, integer) to authenticated;
```

- [ ] **Step 4: Add the `admin_list_glyph_submissions` update**

Copy the current full body from `20260701102810:166` and remove the `g.shape_id as shape_id,` line from the inner `select`. Append:

```sql
-- 3. admin_list_glyph_submissions: stop projecting g.shape_id.
create or replace function public.admin_list_glyph_submissions(p_status text default null)
returns jsonb
language plpgsql security definer set search_path = public, auth as $$
declare
  v_result jsonb;
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'not_admin' using errcode = '42501';
  end if;

  select coalesce(jsonb_agg(to_jsonb(t) order by t.created_at), '[]'::jsonb)
  into v_result
  from (
    select
      s.id           as submission_id,
      s.glyph_id     as glyph_id,
      s.status       as status,
      s.listed       as listed,
      s.price        as price,
      s.review_note  as review_note,
      s.created_at   as created_at,
      s.reviewed_at  as reviewed_at,
      s.submitter_id as submitter_id,
      su.email       as submitter_email,
      g.user_id      as owner_id,
      ou.email       as owner_email,
      g.name         as name,
      g.strokes      as strokes,
      g.view_box     as view_box
    from public.glyph_submissions s
    join public.glyphs g on g.id = s.glyph_id
    left join auth.users su on su.id = s.submitter_id
    left join auth.users ou on ou.id = g.user_id
    where p_status is null or s.status = p_status
  ) t;

  return v_result;
end;
$$;
```

- [ ] **Step 5: Add the view updates**

`v_pebbles_full` keeps its column signature (`shape_id` is a key inside the `glyph` jsonb), so `CREATE OR REPLACE` works — copy the current full definition from `20260411000002_views.sql:10` and delete only the `'shape_id', g.shape_id,` line inside the glyph `jsonb_build_object`. `v_glyph_market` has `shape_id` as a top-level column, so it must be dropped and recreated. Append:

```sql
-- 4. v_pebbles_full: drop the shape_id key from the glyph jsonb.
--    Copy the CURRENT full definition from 20260411000002_views.sql (line 10) and
--    remove the single line `      'shape_id', g.shape_id,` inside the glyph
--    jsonb_build_object. Reproduce the rest verbatim.
create or replace view public.v_pebbles_full as
-- <<< paste full current definition here, minus the 'shape_id', g.shape_id line >>>
;

-- 5. v_glyph_market: top-level shape_id column → drop + recreate without it.
drop view if exists public.v_glyph_market;

create view public.v_glyph_market with (security_invoker = true) as
select
  g.id, g.user_id, g.name, g.strokes, g.view_box,
  g.created_at, g.updated_at,
  s.price,
  exists (select 1 from public.glyph_entitlements e
          where e.glyph_id = g.id and e.user_id = auth.uid()) as owned,
  exists (select 1 from public.glyph_favourites f
          where f.glyph_id = g.id and f.user_id = auth.uid()) as favourited
from public.glyph_submissions s
join public.glyphs g on g.id = s.glyph_id
where s.status = 'approved' and s.listed;

revoke all on public.v_glyph_market from public, anon;
grant select on public.v_glyph_market to authenticated;
```

- [ ] **Step 6: Drop the column and the orphaned table**

Append:

```sql
-- 6. Drop the column (cascades glyphs_shape_id_fkey) and the now-orphaned table.
alter table public.glyphs drop column shape_id;
drop table public.pebble_shapes;
```

- [ ] **Step 7: Commit the migration file**

```bash
git add packages/supabase/supabase/migrations/*_drop_glyph_shape.sql
git commit -m "feat(db): drop glyphs.shape_id and pebble_shapes (#503)"
```

---

## Task 2: Remove the vestigial shape display in web glyph components

**Files:**
- Modify: `apps/web/components/glyphs/GlyphDetail.tsx`
- Modify: `apps/web/components/glyphs/GlyphCard.tsx`

- [ ] **Step 1: GlyphDetail — remove the shape lookup, hook, imports, and render**

Remove the import `import { PEBBLE_SHAPES } from "@/lib/config"` (line ~11). Change the i18n import from `import { useFormatDate, useShapeName } from "@/lib/i18n"` to `import { useFormatDate } from "@/lib/i18n"`. Delete these two lines in the component body:

```tsx
  const shape = PEBBLE_SHAPES.find((s) => s.id === mark.shape_id)
  const shapeName = useShapeName(shape ?? { slug: "", name: "" })
```

In the JSX, delete the shape span so the metadata row becomes:

```tsx
        <div className="mt-2 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
          <span>{tCard("strokeCount", { count: mark.strokes.length })}</span>
          <time dateTime={mark.created_at}>{created}</time>
        </div>
```

- [ ] **Step 2: GlyphCard — remove the shape lookup, hook, imports, and render**

Remove `import { PEBBLE_SHAPES } from "@/lib/config"` (line ~5). Change `import { useFormatDate, useShapeName } from "@/lib/i18n"` to `import { useFormatDate } from "@/lib/i18n"`. Delete:

```tsx
  const shape = PEBBLE_SHAPES.find((s) => s.id === mark.shape_id)
  const shapeName = useShapeName(shape ?? { slug: "", name: "" })
```

Delete the `{shape && <span>{shapeName}</span>}` line (line ~37).

- [ ] **Step 3: Verify the web app still compiles**

Run: `npm run lint --workspace=apps/web`
Expected: PASS (no unused-import or undefined-variable errors in the two files). `mark.shape_id` is no longer referenced here, but the `Mark` type still has it, so this is green.

- [ ] **Step 4: Commit**

```bash
git add apps/web/components/glyphs/GlyphDetail.tsx apps/web/components/glyphs/GlyphCard.tsx
git commit -m "quality(ui): drop vestigial glyph shape name display (#503)"
```

---

## Task 3: Remove the `useShapeName` hook and the pebble-shapes config

**Files:**
- Modify: `apps/web/lib/i18n/useReferenceCatalog.ts`
- Modify: `apps/web/lib/i18n/index.ts`
- Delete: `apps/web/lib/config/pebble-shapes.ts`
- Modify: `apps/web/lib/config/index.ts`

- [ ] **Step 1: Remove `useShapeName` and its `PebbleShape` import**

In `apps/web/lib/i18n/useReferenceCatalog.ts`, delete the entire `useShapeName` function (the doc comment + function, lines ~70–87):

```tsx
/**
 * Resolve a pebble shape's display name for the active locale.
 * Falls back to the static `name` when the slug isn't in the catalog.
 */
export function useShapeName(shape: Pick<PebbleShape, "slug" | "name">): string {
  const t = useTranslations() as unknown as {
    (key: string): string
    has(key: string): boolean
  }
  const key = `shape.${shape.slug}.name`
  return t.has(key) ? t(key) : shape.name
}
```

Also delete the now-unused import at the top: `import type { PebbleShape } from "@/lib/config/pebble-shapes"` (line ~6).

- [ ] **Step 2: Remove the `useShapeName` re-export**

In `apps/web/lib/i18n/index.ts`, delete the `useShapeName,` line (line ~16) from the export block.

- [ ] **Step 3: Delete the config file and its re-export**

Delete `apps/web/lib/config/pebble-shapes.ts`. In `apps/web/lib/config/index.ts`, delete line 6:

```tsx
export { type PebbleShape, PEBBLE_SHAPES } from "./pebble-shapes"
```

- [ ] **Step 4: Verify no dangling references**

Run: `grep -rn "useShapeName\|PEBBLE_SHAPES\|pebble-shapes\|PebbleShape" apps/web`
Expected: no matches.

Run: `npm run lint --workspace=apps/web`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/web/lib/i18n/useReferenceCatalog.ts apps/web/lib/i18n/index.ts apps/web/lib/config/index.ts apps/web/lib/config/pebble-shapes.ts
git commit -m "quality(ui): remove useShapeName hook and pebble-shapes config (#503)"
```

---

## Task 4: Remove `shape_id` from the `Mark` type, provider, and carve editor

**Files:**
- Modify: `apps/web/lib/types.ts:99`
- Modify: `apps/web/lib/data/supabase-provider.ts` (lines ~215, 611, 625, 635, 648, 656)
- Modify: `apps/web/components/carve/CarveEditor.tsx:63`

These change together because `CreateMarkInput = Omit<Mark, …>` and `UpdateMarkInput = Partial<…>` derive from `Mark` — the compiler will flag every site once `shape_id` leaves `Mark`.

- [ ] **Step 1: Remove `shape_id` from the `Mark` type**

In `apps/web/lib/types.ts`, delete line 99:

```tsx
  shape_id: string | null // null = shapeless (the canonical model, #278); legacy glyphs carry a shape
```

- [ ] **Step 2: Remove `shape_id` from the provider mappings**

In `apps/web/lib/data/supabase-provider.ts`, delete every `shape_id` line:
- In the marks mapping (~line 215): `shape_id: row.shape_id as string | null,`
- In `rowToMark` (~line 611): `shape_id: row.shape_id as string | null,`
- In `createMark`'s insert object (~line 625): `shape_id: input.shape_id,`
- In `createMark`'s returned `Mark` (~line 635): `shape_id: row.shape_id as string | null,`
- In `updateMark`'s conditional (~line 648): `if (input.shape_id !== undefined) updates.shape_id = input.shape_id`
- In `updateMark`'s returned `Mark` (~line 656): `shape_id: row.shape_id as string | null,`

- [ ] **Step 3: Remove `shape_id` from the carve editor create payload**

In `apps/web/components/carve/CarveEditor.tsx`, delete line 63:

```tsx
        shape_id: null, // shapeless — the canonical model (#278)
```

- [ ] **Step 4: Verify no dangling references and the app compiles**

Run: `grep -rn "shape_id" apps/web`
Expected: no matches.

Run: `npm run lint --workspace=apps/web`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/web/lib/types.ts apps/web/lib/data/supabase-provider.ts apps/web/components/carve/CarveEditor.tsx
git commit -m "quality(core): remove shape_id from Mark type and provider (#503)"
```

---

## Task 5: Remove the `shape` i18n message block

**Files:**
- Modify: `apps/web/lib/i18n/messages/en.json` (lines ~239–246)
- Modify: `apps/web/lib/i18n/messages/fr.json` (lines ~239–246)

- [ ] **Step 1: Delete the `shape` block in both locale files**

In each of `en.json` and `fr.json`, delete the entire `"shape": { … }` object (and its trailing comma). For `en.json` the block is:

```json
  "shape": {
    "river-smooth": { "name": "River Smooth" },
    "creek-flat":   { "name": "Creek Flat" },
    "moss-round":   { "name": "Moss Round" },
    "canyon-long":  { "name": "Canyon Long" },
    "shore-wide":   { "name": "Shore Wide" },
    "dusk-pebble":  { "name": "Dusk Pebble" }
  },
```

Delete the equivalent block (French names) in `fr.json`.

- [ ] **Step 2: Verify valid JSON and no stray shape keys**

Run: `node -e "JSON.parse(require('fs').readFileSync('apps/web/lib/i18n/messages/en.json')); JSON.parse(require('fs').readFileSync('apps/web/lib/i18n/messages/fr.json')); console.log('ok')"`
Expected: `ok`

Run: `grep -rn "river-smooth\|\"shape\"" apps/web/lib/i18n/messages`
Expected: no matches.

- [ ] **Step 3: Commit**

```bash
git add apps/web/lib/i18n/messages/en.json apps/web/lib/i18n/messages/fr.json
git commit -m "quality(ui): remove shape name i18n strings (#503)"
```

---

## Task 6: Clean up the admin app

**Files:**
- Modify: `apps/admin/lib/pebblestore/types.ts` (line ~23 and the `PebbleShape` type ~28–35)
- Modify: `apps/admin/lib/pebblestore/fetchers.ts` (import line 2, `listShapes` ~16–29)
- Modify: `apps/admin/app/(authed)/pebblestore/glyphs/actions.ts` (lines ~85–88)

- [ ] **Step 1: Remove `shape_id` and `PebbleShape` from admin types**

In `apps/admin/lib/pebblestore/types.ts`, delete the `shape_id: string | null` line from `AdminSubmission` (line ~23). Delete the entire `PebbleShape` type and its doc comment:

```ts
/** A pebble shape (reference data) for the shape dropdown + preview clip. */
export type PebbleShape = {
  id: string
  slug: string
  name: string
  path: string
  view_box: string
}
```

- [ ] **Step 2: Remove the `listShapes` fetcher**

In `apps/admin/lib/pebblestore/fetchers.ts`, change the import on line 2 from `import type { AdminSubmission, PebbleShape, SubmissionStatus } from "./types"` to `import type { AdminSubmission, SubmissionStatus } from "./types"`. Delete the entire `listShapes` function (lines ~16–29):

```ts
export async function listShapes(): Promise<PebbleShape[]> {
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase
    .from("pebble_shapes")
    .select("id, slug, name, path, view_box")
    .order("name")
  if (error) {
    console.error("[pebblestore] listShapes failed:", error.message)
    throw new Error(error.message)
  }
  return data ?? []
}
```

- [ ] **Step 3: Drop the `p_shape_id` arg from the publish RPC call**

In `apps/admin/app/(authed)/pebblestore/glyphs/actions.ts`, edit the `publish_admin_glyph` call so the `p_shape_id` arg and its comment are removed:

```ts
  const { error } = await supabase.rpc("publish_admin_glyph", {
    p_name: input.name,
    p_strokes: input.strokes as unknown as never, // jsonb
    p_view_box: input.viewBox,
    p_price: input.price,
  })
```

- [ ] **Step 4: Verify no dangling references and the admin app compiles**

Run: `grep -rn "shape_id\|PebbleShape\|listShapes\|pebble_shapes" apps/admin`
Expected: no matches.

Run: `npm run lint --workspace=apps/admin`
Expected: PASS.

> Note: `publish_admin_glyph` is called with the new arg shape but the generated types still describe the old signature until Task 7 regenerates them. `.rpc(...)` args are structurally typed; dropping a key is safe against the old type. If the admin build (not lint) flags the RPC arg, complete Task 7 first, then re-run.

- [ ] **Step 5: Commit**

```bash
git add apps/admin/lib/pebblestore/types.ts apps/admin/lib/pebblestore/fetchers.ts "apps/admin/app/(authed)/pebblestore/glyphs/actions.ts"
git commit -m "quality(core): remove shape_id and pebble_shapes usage from admin (#503)"
```

---

## Task 7: Apply the migration and regenerate types

**Files:**
- Modify: `packages/supabase/types/database.ts` (regenerated)

> This project deploys to the **remote** linked Supabase project (no local Docker — see the project's avoid-Docker convention). Use the remote commands below. AGENTS.md's `npm run db:types` targets `--local`; prefer `db:types:remote` here to match the remote-first workflow.

- [ ] **Step 1: Apply the migration to the linked remote**

Run: `npm run db:push --workspace=packages/supabase`
Expected: the `<timestamp>_drop_glyph_shape.sql` migration applies cleanly with no errors. If it fails on a view dependency, confirm the drop order (RPCs/views updated before `alter table … drop column`).

- [ ] **Step 2: Regenerate the TypeScript types**

Run: `npm run db:types:remote --workspace=packages/supabase`
Expected: `packages/supabase/types/database.ts` is rewritten. `shape_id` no longer appears under the `glyphs` table, and the `pebble_shapes` table is gone.

- [ ] **Step 3: Verify the generated types are clean**

Run: `grep -n "shape_id\|pebble_shapes" packages/supabase/types/database.ts`
Expected: no matches.

- [ ] **Step 4: Full typecheck across the affected workspaces**

Run: `npm run build`
Expected: PASS. (This is the point where the web provider insert + admin RPC call are validated against the new types.)

- [ ] **Step 5: Commit**

```bash
git add packages/supabase/types/database.ts
git commit -m "chore(db): regenerate types after dropping glyph shape (#503)"
```

---

## Task 8: iOS tidy, docs, and final verification

**Files:**
- Modify: `apps/ios/Pebbles/Features/Glyph/Models/GlyphInsertPayload.swift` (doc comment ~6–8)
- Modify: `apps/ios/Pebbles/Features/Glyph/Services/GlyphService.swift` (doc comment ~9–12)
- Modify: `apps/ios/PebblesTests/Features/Glyph/GlyphInsertPayloadEncodingTests.swift` (comment ~25)
- Modify: `docs/decisions/log.md`
- Modify: `docs/arkaik/bundle.json` (only if a `pebble_shapes` node exists)
- Modify: memory `project_glyph_model.md`

- [ ] **Step 1: Update the iOS doc comments**

In `GlyphInsertPayload.swift`, replace the `shape_id` paragraph (lines ~6–8) with a note that reflects the dropped column:

```swift
/// The glyphs table has no shape column — glyphs are shape-agnostic squares
/// (issue #278, #503). The payload carries only user_id, strokes, view_box, name.
```

In `GlyphService.swift`, replace the `shape_id = NULL` paragraph (lines ~9–12) with:

```swift
/// Glyphs are shape-agnostic squares (#278, #503) — there is no shape column;
/// the glyph zone is always a square.
```

- [ ] **Step 2: Reword the encoding test comment (keep the assertion)**

In `GlyphInsertPayloadEncodingTests.swift`, keep the `#expect(json["shape_id"] == nil, …)` assertion as a regression guard, but reword its message (line ~25):

```swift
        #expect(json["shape_id"] == nil, "payload must not carry a shape column (dropped in #503)")
```

The `@Test("encodes snake_case keys without shape_id")` name stays valid.

- [ ] **Step 3: Run the iOS glyph tests (if the toolchain is available)**

Run the `GlyphInsertPayload encoding` suite (Xcode or `xcodebuild test`).
Expected: PASS. If the iOS toolchain isn't available in this environment, note it and rely on the unchanged assertion logic.

- [ ] **Step 4: Append the decision-log entry**

In `docs/decisions/log.md`, append one entry recording that the glyph→shape link was removed and `pebble_shapes` retired (glyphs are shape-agnostic squares; pebble outlines come from `apps/web/lib/engine/templates.ts`, not the table). Follow the existing entry format in that file.

- [ ] **Step 5: Update Arkaik if a shape node exists**

Run: `grep -n "pebble_shapes\|shape" docs/arkaik/bundle.json`
If a `pebble_shapes` data-model node (or a glyph→shape edge) exists, remove it using the `arkaik` skill. If nothing shape-related exists, skip.

- [ ] **Step 6: Update the glyph-model memory**

Edit `/Users/alexis/.claude/projects/-Users-alexis-code-pbbls/memory/project_glyph_model.md`: change the final "Remaining (issue #503)" line to record that #503 landed — `glyphs.shape_id` column and `pebble_shapes` table are dropped; legacy rows keep their own `view_box` (not normalized).

- [ ] **Step 7: Final full build + lint from the repo root**

Run: `npm run build && npm run lint`
Expected: both PASS.

Run: `grep -rn "shape_id\|pebble_shapes\|PebbleShape\|useShapeName" apps packages/supabase/types | grep -v "supabase/migrations/"`
Expected: no matches (all hits, if any, are in historical migration files only).

- [ ] **Step 8: Commit**

```bash
git add apps/ios docs/decisions/log.md docs/arkaik/bundle.json
git commit -m "quality: tidy iOS comments and record glyph shape removal (#503)"
```

---

## Self-review checklist (completed during authoring)

- **Spec coverage:** DB migration (Task 1, 7), web type/provider/carve (Task 4), web components (Task 2), web config/i18n hook (Task 3), web i18n strings (Task 5), admin (Task 6), iOS (Task 8), docs/arkaik/memory/decision-log (Task 8). Lab Note intentionally omitted (no user-visible change). ✔
- **Placeholder scan:** The two large RPC bodies (`create_pebble`, `v_pebbles_full`) are handled by "copy current definition + delete this exact line" instructions rather than transcription, to avoid drift; every other SQL/TS/Swift edit shows the full replacement text. ✔
- **Type consistency:** `publish_admin_glyph` new signature `(text, jsonb, text, integer)` matches the admin `.rpc` call args in Task 6 and the grant/revoke in Task 1. `Mark` removal cascades to `CreateMarkInput`/`UpdateMarkInput`. ✔
