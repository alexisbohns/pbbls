# Remove the deprecated glyph shape (#503)

**Issue:** #503 — [Quality] Deprecate glyph shape — shape-agnostic square glyphs
**Milestone:** M36 · Pebblestore & Karma Economy
**Date:** 2026-07-01

## Intention

A glyph is just strokes in a square viewBox, scaled into the pebble slot at render
time. The notion of a *shape on a glyph* is deprecated. This change removes the
`glyphs.shape_id` column and retires the now-orphaned `pebble_shapes` reference
table system-wide.

This is a **cleanup**, not a behavior change. Every surface (web `/carve`, admin
upload, iOS carve, the 18 system seeds) already writes `shape_id = null`; the
column and table are pure backward-compat cruft. The canonical shapeless model was
established by #278 (migration `20260415000001` made `shape_id` nullable) and the
code surfaces were aligned on PR #502.

## Current state (why this is safe)

A full-surface audit confirms no live data flows through `shape_id`:

- **No writes with a real value.** Web `CarveEditor`, admin `publishGlyph`, and iOS
  `GlyphInsertPayload` all send `null` (or omit the field).
- **Reads are vestigial.** Web `GlyphDetail`/`GlyphCard` look up a shape *name* for
  legacy display only; the admin moderation queue receives `shape_id` but never uses
  it; iOS never selects it.
- **`pebble_shapes` is a confirmed orphan.** Its only FK reference is
  `glyphs.shape_id`. Pebble *outlines* are rendered from baked-in templates in
  `apps/web/lib/engine/templates.ts`, **not** from this table. Nothing else reads it.
- **Legacy geometry renders fine.** Some old web-carved rows carry a non-null
  `shape_id` and a non-square `view_box`; the render engine fits by `view_box`, so
  they render correctly. We are **not** normalizing their geometry (decision below).

## Decisions

1. **Drop `pebble_shapes` entirely** — confirmed orphan; pebble outlines come from
   `templates.ts`.
2. **Leave legacy row geometry as-is** — dropping the column removes `shape_id` from
   every row automatically. Normalizing `view_box` would require transforming stored
   stroke coordinates — risky, no user-visible benefit.
3. **Light iOS tidy only** — iOS is already canonical; update doc comments and keep
   the "omits `shape_id`" regression test.

## Scope

### 1. Database — one forward migration

Order matters: dependents must stop referencing `shape_id` **before** the column is
dropped, or `DROP COLUMN` fails on the dependent view.

1. **RPCs**
   - `create_pebble(payload jsonb)` — `CREATE OR REPLACE`; drop the
     `payload->'new_glyph'->>'shape_id'` line from the glyph `INSERT`. Signature
     unchanged. (Current def: `20260426000001_pebbles_pictures.sql`.)
   - `publish_admin_glyph(...)` — `DROP FUNCTION` + recreate **without** the
     `p_shape_id uuid` parameter (signature change requires drop, not replace).
     (Current def: `20260630084718_admin_glyph_moderation.sql`.)
   - `admin_list_glyph_submissions(...)` — `CREATE OR REPLACE`; stop projecting
     `g.shape_id as shape_id`. (Current def: `20260701102810_glyph_marketplace_curation.sql`.)
2. **Views**
   - `v_pebbles_full` — `CREATE OR REPLACE`; `shape_id` is a key *inside* the `glyph`
     jsonb object, so the view's column signature is unchanged — just drop the key.
   - `v_glyph_market` — `shape_id` is a *top-level* column here, so `CREATE OR REPLACE`
     cannot remove it: `DROP VIEW` + recreate. Watch for the redefinition in
     `20260701102810` and any dependent views (drop/recreate in dependency order).
3. **Drop column** — `ALTER TABLE public.glyphs DROP COLUMN shape_id;` (cascades the
   `glyphs_shape_id_fkey` constraint).
4. **Drop table** — `DROP TABLE public.pebble_shapes;` (its RLS policy and seed rows
   drop with it).
5. **Regenerate types** — `npm run db:types --workspace=packages/supabase`, then
   `git add packages/supabase/types/database.ts`.

### 2. Web (`apps/web/`)

- `lib/types.ts` — remove `shape_id` from the `Mark` type.
- `lib/data/supabase-provider.ts` — remove the `shape_id` mappings (reads at ~215,
  611, 635, 656; write at ~625; conditional update at ~648).
- `components/glyphs/GlyphDetail.tsx` — remove the `PEBBLE_SHAPES.find(...)` lookup,
  the `useShapeName` call, and the shape-name row in the rendered detail.
- `components/glyphs/GlyphCard.tsx` — remove the lookup + name display.
- `components/carve/CarveEditor.tsx` — remove the `shape_id: null` field from the
  create payload.
- `lib/config/pebble-shapes.ts` — delete the file; remove its re-export from
  `lib/config/index.ts`.
- `lib/i18n/useReferenceCatalog.ts` — remove `useShapeName` and the `PebbleShape`
  import (verify no remaining caller first).

### 3. Admin (`apps/admin/`)

- `lib/pebblestore/types.ts` — remove `shape_id` from `AdminSubmission`; delete the
  `PebbleShape` type.
- `lib/pebblestore/fetchers.ts` — delete `listShapes` and its `PebbleShape` import
  (no callers).
- `app/(authed)/pebblestore/glyphs/actions.ts` — drop the `p_shape_id: null` arg from
  the `publish_admin_glyph` RPC call (matches the new signature).

### 4. iOS (`apps/ios/`) — light tidy

- `Features/Glyph/Models/GlyphInsertPayload.swift` — update the doc comment that
  describes `shape_id` as a nullable column (it no longer exists).
- `Features/Glyph/Services/GlyphService.swift` — update the same doc comment.
- `PebblesTests/Features/Glyph/GlyphInsertPayloadEncodingTests.swift` — keep the
  "omits `shape_id`" assertion (still a valid regression guard); reword the comment.
- No functional change (payload already omits the field; SELECTs never included it).

### 5. Docs & housekeeping

- **Decision log** (`docs/decisions/log.md`) — append one entry: glyph→shape link
  removed, `pebble_shapes` retired.
- **Arkaik** (`docs/arkaik/bundle.json`) — remove a `pebble_shapes` data-model node
  if one exists (via the `arkaik` skill).
- **Memory** — update `project_glyph_model.md` to note #503 landed (column + table
  gone).
- **Lab Note** — none. Zero user-visible change; delete the PR section.

## Verification

- `npm run build` and `npm run lint` from repo root (large, cross-app change).
- Regenerated `database.ts` compiles with no dangling `shape_id`/`pebble_shapes`
  references (`grep` for both across `apps/` and `packages/` returns only historical
  migration files).
- Manual smoke: create a pebble with a glyph (web `/carve`), view it in
  `GlyphDetail`/`GlyphCard`, and confirm the admin moderation queue + marketplace
  still load.
- iOS `PebblesTests` glyph suite still passes.

## Out of scope

- Normalizing legacy row geometry to a 200×200 square.
- Any change to pebble-outline rendering (`templates.ts` is untouched).
- Reintroducing any glyph→shape association.
