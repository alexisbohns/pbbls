# Remote Pebble Engine — Slice 1 · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the smallest vertical slice of the Remote Pebble Engine (issue #261) — an iOS user creates a pebble, a Supabase edge function composes the render server-side using a domain fallback glyph, and the recorded-pebble detail sheet opens with the server-composed render.

**Architecture:** Add a DB migration that makes glyphs system-owned (nullable user_id, domain FK) and adds render columns on pebbles. Port the POC engine modules (glyph normalization + layout resolver + compose) into Supabase `_shared/engine/`. Build two edge functions: `compose-pebble` (client-facing, wraps `create_pebble` RPC with JWT forwarding, then composes and writes back) and `backfill-pebble-render` (ops-only, service-role gated). Add a Deno backfill script and an engine smoke-test script. On iOS, add a native SVG package, create `PebbleRenderView` and `PebbleDetailSheet`, migrate `CreatePebbleSheet` from direct RPC to `functions.invoke`, and wire `PathView` to present the detail sheet after create. Webapp is untouched.

**Tech Stack:** Supabase (Postgres + Deno edge functions), Next.js 16 / React 19 / TS (webapp, read-only in this slice), SwiftUI iOS 17+ with `supabase-swift` v2, Swift Testing, a native iOS SVG package (SVGView recommended).

**Spec:** `docs/superpowers/specs/2026-04-15-remote-pebble-engine-slice-1-design.md`

**Issue:** #261

**Branch:** `feat/261-remote-pebble-engine-slice-1` (already checked out, spec already committed)

**Prerequisites before starting Phase 1:**
- **@alexisbohns provides the 18 domain fallback glyph stroke payloads** (one per domain) as JSON blobs matching the `{ d: string, strokeWidth?: number }[]` Stroke shape, with `view_box = "0 0 200 200"`. Place them in a local `domain-glyph-seeds.json` file for the migration to reference.
- **@alexisbohns provides the 9 shape SVG files** (small/medium/large × lowlight/neutral/highlight) as complete `<svg …>…</svg>` strings matching the POC layout config canvas sizes (small 250×200, medium 260×260, large 260×310). Place them in a local `shape-seeds/` directory with filenames matching `{size}-{valence}.svg`.
- **Local Supabase is running.** Run `cd packages/supabase && npm run db:start` and confirm no errors. If Docker is in a corrupted state (per project memory), reset it before starting.
- **Deno is available** via the Supabase CLI bundle (run `supabase --version` to confirm ≥ 1.180.0 which ships with a Deno runtime).

---

## Phase 1: Database migration

### Task 1.1: Draft the migration skeleton

**Files:**
- Create: `packages/supabase/supabase/migrations/20260415000001_remote_pebble_engine.sql`

- [ ] **Step 1: Create the migration file with schema + RLS changes, seed placeholders commented**

```sql
-- Migration: Remote Pebble Engine slice 1
-- Issue: #261
--
-- Adds server-side pebble rendering infrastructure:
--   - glyphs: allow system-owned (NULL user_id) and shapeless (NULL shape_id) rows
--   - domains: FK to a default system glyph used as iOS fallback
--   - pebbles: render_svg, render_manifest, render_version columns written by the
--     compose-pebble edge function
--   - Seeds 18 system glyph rows (one per domain) and links each domain to its
--     default_glyph_id.
--
-- The seed strokes are opinionated per-domain placeholders supplied by the product
-- designer; see `docs/superpowers/specs/2026-04-15-remote-pebble-engine-slice-1-design.md`.

-- ============================================================
-- 1. Relax glyphs constraints
-- ============================================================

alter table public.glyphs alter column user_id drop not null;
alter table public.glyphs alter column shape_id drop not null;

-- ============================================================
-- 2. glyphs RLS — allow reading system glyphs (user_id is null)
-- ============================================================

drop policy if exists "glyphs_select" on public.glyphs;
create policy "glyphs_select" on public.glyphs
  for select using (user_id = auth.uid() or user_id is null);

-- Insert/update/delete policies stay user-scoped and should already exist from
-- the security_hardening migration. Do not relax them here.

-- ============================================================
-- 3. domains: default_glyph_id FK
-- ============================================================

alter table public.domains
  add column default_glyph_id uuid references public.glyphs(id);

-- ============================================================
-- 4. pebbles: render output columns
-- ============================================================

alter table public.pebbles
  add column render_svg text,
  add column render_manifest jsonb,
  add column render_version text;

-- ============================================================
-- 5. Seed 18 system glyphs (one per domain)
-- ============================================================
-- Each glyph row is inserted with a fixed UUID so the seed is idempotent.
-- Strokes are provided per domain; view_box is always "0 0 200 200".
--
-- Replace the <STROKES_FOR_*> placeholders with the JSON array supplied by the
-- designer (domain-glyph-seeds.json). Each entry is a JSON array of objects
-- shaped { d: string, strokeWidth?: number }.

-- PLACEHOLDER: fill in before running. Each INSERT looks like:
-- insert into public.glyphs (id, user_id, name, shape_id, strokes, view_box)
-- values (
--   '00000000-0000-0000-0000-000000000001', NULL, 'domain:work', NULL,
--   '[{"d":"M 20 100 Q 100 20 180 100 T 340 100","strokeWidth":3}]'::jsonb,
--   '0 0 200 200'
-- );

-- (All 18 inserts go here; see Task 1.2 for the full block.)

-- ============================================================
-- 6. Link each domain to its default glyph
-- ============================================================
-- Uses domain slug as the stable join key because domain UUIDs are
-- deterministic but domain_glyph UUIDs were chosen above.
--
-- (Fill in after the 18 INSERTs are in place.)
```

- [ ] **Step 2: Commit the skeleton**

```bash
git add packages/supabase/supabase/migrations/20260415000001_remote_pebble_engine.sql
git commit -m "$(cat <<'EOF'
feat(db): scaffold remote pebble engine migration (#261)

Schema changes for slice 1: nullable glyphs.user_id + shape_id, relaxed
glyphs SELECT policy for system rows, domains.default_glyph_id FK, and
render output columns on pebbles. Seed placeholder left for the 18
domain fallback glyphs — filled in the next commit.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 1.2: Fill in the 18 system glyph seeds

**Files:**
- Modify: `packages/supabase/supabase/migrations/20260415000001_remote_pebble_engine.sql`

**Prerequisite:** The 18 stroke payloads must be available (from `domain-glyph-seeds.json` or directly from Alexis).

- [ ] **Step 1: List the 18 domain slugs**

```bash
cat packages/supabase/supabase/migrations/20260411000000_reference_tables.sql | grep -A 40 "insert into public.domains"
```

Copy the exact list of 18 domain slugs into scratch memory.

- [ ] **Step 2: Generate 18 fixed UUIDs for the glyph rows**

Use any deterministic method. One option:

```bash
python3 -c "import uuid; [print(uuid.uuid5(uuid.NAMESPACE_DNS, f'pbbls.domain-glyph.{s}')) for s in ['work','health','relationships']]"
```

(Replace the slug list with the full 18.)

Record the resulting `(slug → uuid)` mapping.

- [ ] **Step 3: Replace the seed placeholder block in the migration**

Replace the `-- PLACEHOLDER` block with 18 concrete INSERT statements. Template for each:

```sql
insert into public.glyphs (id, user_id, name, shape_id, strokes, view_box)
values (
  '<fixed uuid from step 2>'::uuid,
  NULL,
  'domain:<slug>',
  NULL,
  '<stroke json array from domain-glyph-seeds.json>'::jsonb,
  '0 0 200 200'
);
```

Repeat for all 18 domains.

- [ ] **Step 4: Add the 18 domain UPDATEs at the bottom**

```sql
update public.domains set default_glyph_id = '<glyph uuid>'::uuid where slug = '<slug>';
```

Repeat for all 18 domains.

- [ ] **Step 5: Commit**

```bash
git add packages/supabase/supabase/migrations/20260415000001_remote_pebble_engine.sql
git commit -m "$(cat <<'EOF'
feat(db): seed 18 domain fallback glyphs (#261)

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 1.3: Apply the migration locally and verify

**Files:** (none)

- [ ] **Step 1: Reset the local DB**

```bash
cd packages/supabase
npm run db:reset
```

Expected: all migrations apply in order; no errors.

- [ ] **Step 2: Verify schema changes via `psql` or Supabase Studio**

```bash
supabase db execute "select column_name, is_nullable from information_schema.columns where table_name = 'glyphs' and column_name in ('user_id','shape_id');"
```

Expected: both rows show `is_nullable = YES`.

```bash
supabase db execute "select column_name from information_schema.columns where table_name = 'pebbles' and column_name like 'render_%';"
```

Expected: three rows: `render_svg`, `render_manifest`, `render_version`.

```bash
supabase db execute "select count(*) from public.glyphs where user_id is null;"
```

Expected: 18.

```bash
supabase db execute "select count(*) from public.domains where default_glyph_id is not null;"
```

Expected: 18.

- [ ] **Step 3: Regenerate database.ts**

```bash
npm run db:types --workspace=packages/supabase
```

- [ ] **Step 4: Inspect the diff**

```bash
git diff packages/supabase/types/database.ts | head -80
```

Expected: the diff shows `default_glyph_id` added to the `domains` Row/Insert/Update types, `render_svg/render_manifest/render_version` added to `pebbles`, and `user_id/shape_id` types on `glyphs` relaxed to nullable.

- [ ] **Step 5: Commit the regenerated types**

```bash
git add packages/supabase/types/database.ts
git commit -m "$(cat <<'EOF'
chore(db): regenerate types after remote pebble engine migration (#261)

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 6: Verify the webapp still builds against the new types**

```bash
cd ../../apps/web
npm run build
```

Expected: build succeeds. Slice 1 doesn't touch webapp source, so any failure here is a TypeScript inference issue with the regenerated types — fix before proceeding.

---

## Phase 2: Engine port (shared Deno modules)

### Task 2.1: Scaffold the `_shared/engine/` directory

**Files:**
- Create: `packages/supabase/supabase/functions/_shared/engine/types.ts`
- Create: `packages/supabase/supabase/functions/_shared/engine/glyph.ts`
- Create: `packages/supabase/supabase/functions/_shared/engine/layout.ts`
- Create: `packages/supabase/supabase/functions/_shared/engine/compose.ts`
- Create: `packages/supabase/supabase/functions/_shared/engine/resolve.ts`
- Create: `packages/supabase/supabase/functions/_shared/engine/shapes/index.ts`

- [ ] **Step 1: Create empty stubs for every module so later tasks can be implemented independently**

`_shared/engine/types.ts`:

```typescript
// Stub — implementation in Task 2.2.
export type PebbleSize = "small" | "medium" | "large";
export type PebbleValence = "highlight" | "neutral" | "lowlight";
export interface Stroke { d: string; strokeWidth?: number }
```

`_shared/engine/glyph.ts`:

```typescript
import type { Stroke } from "./types.ts";
export function createGlyphArtwork(_strokes: Stroke[]): { svg: string; viewBox: string; size: number } {
  throw new Error("not implemented");
}
```

`_shared/engine/layout.ts`:

```typescript
import type { PebbleSize, PebbleValence } from "./types.ts";
export function resolveLayout(_size: PebbleSize, _valence: PebbleValence): never {
  throw new Error("not implemented");
}
```

`_shared/engine/compose.ts`:

```typescript
export function composePebble(_input: unknown): { svg: string; manifest: unknown[] } {
  throw new Error("not implemented");
}
```

`_shared/engine/resolve.ts`:

```typescript
import type { PebbleSize, PebbleValence } from "./types.ts";
export function intensityToSize(_intensity: number): PebbleSize {
  throw new Error("not implemented");
}
export function positivenessToValence(_positiveness: number): PebbleValence {
  throw new Error("not implemented");
}
```

`_shared/engine/shapes/index.ts`:

```typescript
import type { PebbleSize, PebbleValence } from "../types.ts";
export function getShape(_size: PebbleSize, _valence: PebbleValence): string {
  throw new Error("not implemented");
}
```

- [ ] **Step 2: Commit the stubs**

```bash
git add packages/supabase/supabase/functions/_shared/engine
git commit -m "$(cat <<'EOF'
feat(api): scaffold pebble engine shared modules (#261)

Empty module stubs for types, glyph, layout, compose, resolve, and
shapes. Implementations land in the following tasks, gated by the
smoke-test script in Task 2.9.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2.2: Port `types.ts` from the POC

**Files:**
- Modify: `packages/supabase/supabase/functions/_shared/engine/types.ts`

The source of truth is the `types.ts` block in issue #260 (the POC output).

- [ ] **Step 1: Replace the stub with the full POC types**

Open `_shared/engine/types.ts` and replace it with the full `Types.ts` block from issue #260, **with these adaptations for Deno**:

1. Imports use `.ts` extensions where referenced.
2. The file exports everything the POC types file exports: `PebbleSize`, `PebbleValence`, `Point`, `BoundingBox`, `Stroke`, `GlyphArtwork`, `GlyphSlot`, `CanvasSize`, `PebbleLayoutConfig`, `PebbleEngineInput`, `AnimationManifestLayer`, `AnimationManifest`, `PebbleEngineOutput`.

Reference the POC body: issue 260, under `### Types.ts`.

- [ ] **Step 2: Verify the file type-checks**

```bash
cd packages/supabase/supabase/functions
deno check _shared/engine/types.ts
```

Expected: no output (no errors).

- [ ] **Step 3: Commit**

```bash
git add packages/supabase/supabase/functions/_shared/engine/types.ts
git commit -m "$(cat <<'EOF'
feat(api): port pebble engine types from POC (#261)

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2.3: Port `glyph.ts` (createGlyphArtwork)

**Files:**
- Modify: `packages/supabase/supabase/functions/_shared/engine/glyph.ts`

- [ ] **Step 1: Replace the stub with the full POC implementation**

Open `_shared/engine/glyph.ts` and replace with the POC `glyph.ts` body (issue #260, under `### Glyph.ts`), **with these Deno adaptations**:

1. Import path: `import type { Stroke, BoundingBox, GlyphArtwork, Point } from "./types.ts";`
2. All exported functions: `computeStrokesBoundingBox`, `createGlyphArtwork`.
3. No DOM, no browser APIs. Pure function.

- [ ] **Step 2: deno check**

```bash
cd packages/supabase/supabase/functions
deno check _shared/engine/glyph.ts
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add packages/supabase/supabase/functions/_shared/engine/glyph.ts
git commit -m "$(cat <<'EOF'
feat(api): port createGlyphArtwork from POC (#261)

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2.4: Port `layout.ts` (resolveLayout + 9-variant config)

**Files:**
- Modify: `packages/supabase/supabase/functions/_shared/engine/layout.ts`

- [ ] **Step 1: Replace the stub with the POC `layout.ts` body**

Open `_shared/engine/layout.ts` and replace with the POC layout module (issue #260, under `### Layout.ts`), with these Deno adaptations:

1. Import path: `import type { PebbleSize, PebbleValence, PebbleLayoutConfig, GlyphSlot, CanvasSize } from "./types.ts";`
2. Export: `resolveLayout`, `resolveGlyphSlot`, `resolveCanvas`, `computeGlyphPosition`, and the raw `LAYOUT`, `CANVAS`, `GLYPH_SIZE`, `GLYPH_POSITION` constants.

- [ ] **Step 2: deno check**

```bash
cd packages/supabase/supabase/functions
deno check _shared/engine/layout.ts
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add packages/supabase/supabase/functions/_shared/engine/layout.ts
git commit -m "$(cat <<'EOF'
feat(api): port 9-variant layout config from POC (#261)

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2.5: Port `compose.ts` (composePebble)

**Files:**
- Modify: `packages/supabase/supabase/functions/_shared/engine/compose.ts`

- [ ] **Step 1: Replace the stub with the POC `compose.ts` body**

Open `_shared/engine/compose.ts` and replace with the POC compose module (issue #260, under `### Compose.ts`), with these Deno adaptations:

1. Imports:
   ```typescript
   import type {
     PebbleEngineInput,
     PebbleEngineOutput,
     AnimationManifest,
     AnimationManifestLayer,
     CanvasSize,
     GlyphSlot,
   } from "./types.ts";
   import { resolveLayout } from "./layout.ts";
   ```
2. Export: `composePebble`.
3. Internal helpers (`extractSvgInner`, `extractViewBox`, `extractPaths`, `estimatePathLength`, `stripFills`, `monochromeStrokes`, `namespaceIds`, `buildManifest`) are module-private.

- [ ] **Step 2: deno check**

```bash
cd packages/supabase/supabase/functions
deno check _shared/engine/compose.ts
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add packages/supabase/supabase/functions/_shared/engine/compose.ts
git commit -m "$(cat <<'EOF'
feat(api): port composePebble from POC (#261)

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2.6: Implement `resolve.ts`

**Files:**
- Modify: `packages/supabase/supabase/functions/_shared/engine/resolve.ts`

- [ ] **Step 1: Replace the stub with the real implementation**

```typescript
import type { PebbleSize, PebbleValence } from "./types.ts";

/**
 * Map the pebble `intensity` column (1..3) to the POC's PebbleSize.
 * Unknown values fall back to "medium" — the engine still renders,
 * the caller is expected to enforce the 1..3 check at insertion time.
 */
export function intensityToSize(intensity: number): PebbleSize {
  switch (intensity) {
    case 1: return "small";
    case 2: return "medium";
    case 3: return "large";
    default: return "medium";
  }
}

/**
 * Map the pebble `positiveness` column (-1, 0, 1) to the POC's PebbleValence.
 * Unknown values fall back to "neutral".
 */
export function positivenessToValence(positiveness: number): PebbleValence {
  switch (positiveness) {
    case -1: return "lowlight";
    case 0:  return "neutral";
    case 1:  return "highlight";
    default: return "neutral";
  }
}
```

- [ ] **Step 2: deno check**

```bash
cd packages/supabase/supabase/functions
deno check _shared/engine/resolve.ts
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add packages/supabase/supabase/functions/_shared/engine/resolve.ts
git commit -m "$(cat <<'EOF'
feat(api): add intensity/positiveness → size/valence resolvers (#261)

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2.7: Drop the 9 shape SVG constants

**Files:**
- Create: `packages/supabase/supabase/functions/_shared/engine/shapes/small-lowlight.ts`
- Create: `packages/supabase/supabase/functions/_shared/engine/shapes/small-neutral.ts`
- Create: `packages/supabase/supabase/functions/_shared/engine/shapes/small-highlight.ts`
- Create: `packages/supabase/supabase/functions/_shared/engine/shapes/medium-lowlight.ts`
- Create: `packages/supabase/supabase/functions/_shared/engine/shapes/medium-neutral.ts`
- Create: `packages/supabase/supabase/functions/_shared/engine/shapes/medium-highlight.ts`
- Create: `packages/supabase/supabase/functions/_shared/engine/shapes/large-lowlight.ts`
- Create: `packages/supabase/supabase/functions/_shared/engine/shapes/large-neutral.ts`
- Create: `packages/supabase/supabase/functions/_shared/engine/shapes/large-highlight.ts`

**Prerequisite:** The 9 shape SVG files from `shape-seeds/` must be available (from Alexis).

- [ ] **Step 1: Create each of the 9 files from the same template**

Template (repeat 9 times, swapping filename and the SVG string):

`_shared/engine/shapes/medium-neutral.ts`:

```typescript
/**
 * Pebble shape — medium size, neutral valence.
 * Canvas: 260×260 (per POC layout config).
 * Source: designer-supplied SVG committed with slice 1 of #261.
 */
export const shape: string = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 260 260" width="260" height="260">
  <!-- paste the full <path>, <g>, etc. contents from shape-seeds/medium-neutral.svg -->
</svg>`;
```

Create the 9 files. The `<!-- paste … -->` placeholder is replaced with the real SVG content. No other logic in these files.

- [ ] **Step 2: deno check all 9**

```bash
cd packages/supabase/supabase/functions
for f in _shared/engine/shapes/*.ts; do deno check "$f" || exit 1; done
```

Expected: all 9 type-check (they're just a single string export each).

- [ ] **Step 3: Commit**

```bash
git add packages/supabase/supabase/functions/_shared/engine/shapes
git commit -m "$(cat <<'EOF'
feat(api): add 9 pebble shape SVG constants (#261)

Small / medium / large × lowlight / neutral / highlight, bundled as
inline TypeScript strings in _shared/engine/shapes/. Assets versioned
alongside the engine so render_version captures the full rendering
contract.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2.8: Implement `shapes/index.ts` (getShape)

**Files:**
- Modify: `packages/supabase/supabase/functions/_shared/engine/shapes/index.ts`

- [ ] **Step 1: Replace the stub**

```typescript
import type { PebbleSize, PebbleValence } from "../types.ts";

import { shape as smallLowlight } from "./small-lowlight.ts";
import { shape as smallNeutral } from "./small-neutral.ts";
import { shape as smallHighlight } from "./small-highlight.ts";
import { shape as mediumLowlight } from "./medium-lowlight.ts";
import { shape as mediumNeutral } from "./medium-neutral.ts";
import { shape as mediumHighlight } from "./medium-highlight.ts";
import { shape as largeLowlight } from "./large-lowlight.ts";
import { shape as largeNeutral } from "./large-neutral.ts";
import { shape as largeHighlight } from "./large-highlight.ts";

const TABLE: Record<PebbleSize, Record<PebbleValence, string>> = {
  small:  { lowlight: smallLowlight,  neutral: smallNeutral,  highlight: smallHighlight  },
  medium: { lowlight: mediumLowlight, neutral: mediumNeutral, highlight: mediumHighlight },
  large:  { lowlight: largeLowlight,  neutral: largeNeutral,  highlight: largeHighlight  },
};

export function getShape(size: PebbleSize, valence: PebbleValence): string {
  return TABLE[size][valence];
}
```

- [ ] **Step 2: deno check**

```bash
cd packages/supabase/supabase/functions
deno check _shared/engine/shapes/index.ts
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add packages/supabase/supabase/functions/_shared/engine/shapes/index.ts
git commit -m "$(cat <<'EOF'
feat(api): add getShape lookup over the 9 variants (#261)

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2.9: Write the engine smoke-test script

**Files:**
- Create: `packages/supabase/scripts/smoke-test-engine.ts`

- [ ] **Step 1: Write the script**

```typescript
#!/usr/bin/env -S deno run
/**
 * Engine smoke-test — runs composePebble against synthetic input for all 9
 * (size, valence) variants, asserting output shape. No DB, no network.
 *
 * Run: deno run packages/supabase/scripts/smoke-test-engine.ts
 *
 * Exit 0 on success, non-zero on any assertion failure.
 */

import { createGlyphArtwork } from "../supabase/functions/_shared/engine/glyph.ts";
import { composePebble } from "../supabase/functions/_shared/engine/compose.ts";
import { getShape } from "../supabase/functions/_shared/engine/shapes/index.ts";
import type { PebbleSize, PebbleValence, Stroke } from "../supabase/functions/_shared/engine/types.ts";

const SIZES: PebbleSize[] = ["small", "medium", "large"];
const VALENCES: PebbleValence[] = ["lowlight", "neutral", "highlight"];

const SYNTHETIC_STROKES: Stroke[] = [
  { d: "M 20 100 Q 100 20 180 100 T 340 100", strokeWidth: 3 },
  { d: "M 50 50 L 150 150", strokeWidth: 3 },
];

function assert(cond: boolean, msg: string): void {
  if (!cond) {
    console.error(`✗ ${msg}`);
    Deno.exit(1);
  }
}

let ok = 0;
for (const size of SIZES) {
  for (const valence of VALENCES) {
    const artwork = createGlyphArtwork(SYNTHETIC_STROKES);
    assert(artwork.svg.startsWith("<svg"), `${size}/${valence}: artwork starts with <svg`);
    assert(artwork.viewBox === "0 0 200 200", `${size}/${valence}: artwork viewBox is 200×200`);

    const shapeSvg = getShape(size, valence);
    assert(shapeSvg.startsWith("<svg"), `${size}/${valence}: shape starts with <svg`);

    const { svg, manifest } = composePebble({
      size,
      valence,
      shapeSvg,
      glyphSvg: artwork.svg,
    });

    assert(svg.startsWith("<svg"), `${size}/${valence}: composed svg starts with <svg`);
    assert(svg.includes(`<g id="layer:shape">`), `${size}/${valence}: composed svg has shape layer`);
    assert(svg.includes(`<g id="layer:glyph"`), `${size}/${valence}: composed svg has glyph layer`);

    assert(Array.isArray(manifest) && manifest.length > 0, `${size}/${valence}: manifest is non-empty array`);
    assert(manifest.some((l) => l.type === "glyph"), `${size}/${valence}: manifest has glyph layer`);
    assert(manifest.some((l) => l.type === "shape"), `${size}/${valence}: manifest has shape layer`);

    ok += 1;
    console.log(`✓ ${size}/${valence}`);
  }
}

console.log(`\nrendered=${ok}/9`);
Deno.exit(ok === 9 ? 0 : 1);
```

- [ ] **Step 2: Run it**

```bash
cd packages/supabase
deno run scripts/smoke-test-engine.ts
```

Expected: 9 `✓` lines and `rendered=9/9`. If any variant fails, fix the port in the relevant engine module and re-run.

- [ ] **Step 3: Commit**

```bash
git add packages/supabase/scripts/smoke-test-engine.ts
git commit -m "$(cat <<'EOF'
feat(api): add engine smoke-test script (#261)

Synthetic-input smoke test that runs composePebble against all 9
(size, valence) variants with assertion-level checks. Pure function
only — no DB, no network. Gate for engine port correctness.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 3: Supabase helpers

### Task 3.1: Create `_shared/supabase-client.ts`

**Files:**
- Create: `packages/supabase/supabase/functions/_shared/supabase-client.ts`

- [ ] **Step 1: Write the helpers**

```typescript
/**
 * Supabase client factories used by the compose-pebble and
 * backfill-pebble-render edge functions.
 */

import { createClient, type SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

/**
 * Builds a supabase-js client that forwards the caller's JWT.
 * Use this when calling RPCs that depend on `auth.uid()`.
 */
export function createAuthForwardedClient(req: Request): SupabaseClient {
  const auth = req.headers.get("Authorization") ?? "";
  return createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
    global: { headers: { Authorization: auth } },
    auth: { persistSession: false },
  });
}

/**
 * Builds a service-role supabase-js client that bypasses RLS.
 * Use this for server-owned writes (render columns) and backfill.
 */
export function createAdminClient(): SupabaseClient {
  return createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
    auth: { persistSession: false },
  });
}
```

- [ ] **Step 2: deno check**

```bash
cd packages/supabase/supabase/functions
deno check _shared/supabase-client.ts
```

Expected: no errors. Deno may need to fetch the remote import on first run — this is normal.

- [ ] **Step 3: Commit**

```bash
git add packages/supabase/supabase/functions/_shared/supabase-client.ts
git commit -m "$(cat <<'EOF'
feat(api): add auth-forwarded and admin supabase client factories (#261)

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3.2: Create `_shared/compose-and-write.ts`

**Files:**
- Create: `packages/supabase/supabase/functions/_shared/compose-and-write.ts`

- [ ] **Step 1: Write the helper**

```typescript
/**
 * compose-and-write
 *
 * Given a pebble_id and an admin supabase client, load the pebble + its
 * resolved glyph source, run the engine, write render_svg/render_manifest/
 * render_version back to the row, and return the composed output.
 *
 * Shared by both compose-pebble (create flow) and backfill-pebble-render
 * (ops flow) so both produce byte-identical output for the same pebble_id.
 */

import type { SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

import type { Stroke } from "./engine/types.ts";
import { createGlyphArtwork } from "./engine/glyph.ts";
import { composePebble } from "./engine/compose.ts";
import { getShape } from "./engine/shapes/index.ts";
import { intensityToSize, positivenessToValence } from "./engine/resolve.ts";

const RENDER_VERSION = "0.1.0";

export interface ComposedRender {
  render_svg: string;
  // deno-lint-ignore no-explicit-any
  render_manifest: any;
  render_version: string;
}

export async function composeAndWriteRender(
  admin: SupabaseClient,
  pebbleId: string,
): Promise<ComposedRender> {
  // ── Load pebble + first domain + domain's default glyph id ────────────
  //
  // PostgREST nested select returns pebble_domains in insertion order
  // (no explicit ordering column exists on the junction table). For iOS
  // pebbles this is a one-element array so "first" is unambiguous.
  const { data: pebble, error: loadError } = await admin
    .from("pebbles")
    .select(`
      id, intensity, positiveness, glyph_id,
      pebble_domains(
        domains(default_glyph_id)
      )
    `)
    .eq("id", pebbleId)
    .single();

  if (loadError || !pebble) {
    console.error("compose-and-write: load pebble failed:", loadError);
    throw new Error(`load pebble failed: ${loadError?.message ?? "not found"}`);
  }

  // ── Resolve glyph strokes per the priority rule ──────────────────────
  //   1. pebbles.glyph_id (if new-format view_box === "0 0 200 200")
  //   2. domain's default_glyph_id
  //   3. empty (engine produces a blank 200×200 glyph)
  let strokes: Stroke[] = [];

  if (pebble.glyph_id) {
    const { data: userGlyph, error: userGlyphError } = await admin
      .from("glyphs")
      .select("strokes, view_box")
      .eq("id", pebble.glyph_id)
      .single();
    if (userGlyphError) {
      console.error("compose-and-write: load user glyph failed:", userGlyphError);
    } else if (userGlyph && userGlyph.view_box === "0 0 200 200") {
      strokes = (userGlyph.strokes ?? []) as Stroke[];
    }
  }

  if (strokes.length === 0) {
    // deno-lint-ignore no-explicit-any
    const pebbleDomains = (pebble as any).pebble_domains as Array<{
      domains: { default_glyph_id: string | null } | null;
    }> | null;

    const defaultGlyphId = pebbleDomains?.[0]?.domains?.default_glyph_id ?? null;
    if (defaultGlyphId) {
      const { data: domainGlyph, error: domainGlyphError } = await admin
        .from("glyphs")
        .select("strokes, view_box")
        .eq("id", defaultGlyphId)
        .single();
      if (domainGlyphError) {
        console.error("compose-and-write: load domain glyph failed:", domainGlyphError);
      } else if (domainGlyph) {
        strokes = (domainGlyph.strokes ?? []) as Stroke[];
      }
    }
  }

  // ── Run the engine ───────────────────────────────────────────────────
  let svg: string;
  // deno-lint-ignore no-explicit-any
  let manifest: any;
  try {
    const artwork = createGlyphArtwork(strokes);
    const size = intensityToSize((pebble as { intensity: number }).intensity);
    const valence = positivenessToValence((pebble as { positiveness: number }).positiveness);
    const shapeSvg = getShape(size, valence);
    const output = composePebble({
      size,
      valence,
      shapeSvg,
      glyphSvg: artwork.svg,
    });
    svg = output.svg;
    manifest = output.manifest;
  } catch (err) {
    console.error("compose-and-write: engine error:", err);
    throw new Error(`engine error: ${err instanceof Error ? err.message : String(err)}`);
  }

  // ── Write render columns ─────────────────────────────────────────────
  const { error: updateError } = await admin
    .from("pebbles")
    .update({
      render_svg: svg,
      render_manifest: manifest,
      render_version: RENDER_VERSION,
    })
    .eq("id", pebbleId);

  if (updateError) {
    console.error("compose-and-write: render write-back failed:", updateError);
    throw new Error(`write-back failed: ${updateError.message}`);
  }

  return { render_svg: svg, render_manifest: manifest, render_version: RENDER_VERSION };
}
```

- [ ] **Step 2: deno check**

```bash
cd packages/supabase/supabase/functions
deno check _shared/compose-and-write.ts
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add packages/supabase/supabase/functions/_shared/compose-and-write.ts
git commit -m "$(cat <<'EOF'
feat(api): add compose-and-write helper shared by both edge functions (#261)

Loads pebble + resolves glyph source per the priority rule (user glyph
if new-format, else domain fallback, else empty), runs the engine, and
writes render_svg/render_manifest/render_version back to the row.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 4: Edge functions

### Task 4.1: Create `compose-pebble/index.ts`

**Files:**
- Create: `packages/supabase/supabase/functions/compose-pebble/index.ts`

- [ ] **Step 1: Write the handler**

```typescript
/**
 * Edge function: compose-pebble
 *
 * Client-facing. Wraps the existing create_pebble RPC:
 * 1. Auth-forwards the caller's JWT so the RPC runs as the end user
 * 2. Calls create_pebble(payload) → returns pebble_id
 * 3. Calls compose-and-write → writes render columns + returns composed output
 * 4. Responds with { pebble_id, render_svg, render_manifest, render_version }
 *
 * On RPC failure: 4xx with the RPC error.
 * On compose failure after successful insert: 500 with pebble_id in the body
 * so the iOS client can advance to the detail sheet (soft-success path).
 */

import { serve } from "https://deno.land/std@0.224.0/http/server.ts";

import { createAuthForwardedClient, createAdminClient } from "../_shared/supabase-client.ts";
import { composeAndWriteRender } from "../_shared/compose-and-write.ts";

interface RequestBody {
  // deno-lint-ignore no-explicit-any
  payload: any;
}

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: CORS });
  }
  if (req.method !== "POST") {
    return json({ error: "method not allowed" }, 405);
  }

  // Parse body
  let body: RequestBody;
  try {
    body = await req.json();
  } catch (err) {
    console.error("compose-pebble: body parse failed:", err);
    return json({ error: "invalid body: not JSON" }, 400);
  }
  if (!body || typeof body !== "object" || !("payload" in body)) {
    return json({ error: "invalid body: missing payload" }, 400);
  }

  // Step 1: call create_pebble RPC with auth-forwarded client
  const authClient = createAuthForwardedClient(req);
  const { data: pebbleId, error: rpcError } = await authClient.rpc("create_pebble", {
    payload: body.payload,
  });

  if (rpcError || !pebbleId) {
    console.error("compose-pebble: create_pebble rpc failed:", rpcError);
    return json({ error: rpcError?.message ?? "create_pebble returned no id" }, 400);
  }

  // Step 2: compose + write-back
  const admin = createAdminClient();
  try {
    const rendered = await composeAndWriteRender(admin, pebbleId as string);
    return json({ pebble_id: pebbleId, ...rendered }, 200);
  } catch (err) {
    console.error("compose-pebble: composeAndWrite failed:", err);
    // Soft-success: pebble exists, render failed. Return 500 with pebble_id
    // so iOS can advance to the detail sheet with text-only fallback.
    return json(
      {
        error: `compose failed: ${err instanceof Error ? err.message : String(err)}`,
        pebble_id: pebbleId,
      },
      500,
    );
  }
});

// deno-lint-ignore no-explicit-any
function json(body: any, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...CORS },
  });
}
```

- [ ] **Step 2: deno check**

```bash
cd packages/supabase/supabase/functions
deno check compose-pebble/index.ts
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add packages/supabase/supabase/functions/compose-pebble
git commit -m "$(cat <<'EOF'
feat(api): add compose-pebble edge function (#261)

Client-facing HTTP handler that wraps create_pebble RPC (auth-forwarded)
and composes the render via compose-and-write. Soft-success on compose
failure returns 500 with pebble_id so iOS can advance to the detail
sheet with a text-only fallback.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4.2: Create `backfill-pebble-render/index.ts`

**Files:**
- Create: `packages/supabase/supabase/functions/backfill-pebble-render/index.ts`

- [ ] **Step 1: Write the handler**

```typescript
/**
 * Edge function: backfill-pebble-render
 *
 * Ops-only. Takes { pebble_id }, requires the caller to present the
 * SUPABASE_SERVICE_ROLE_KEY as a bearer token, and calls
 * compose-and-write against an existing pebble.
 *
 * Used by scripts/backfill-renders.ts to rehydrate pebbles whose render
 * columns are NULL (legacy pebbles, failed composes, post-engine-bump
 * re-renders).
 */

import { serve } from "https://deno.land/std@0.224.0/http/server.ts";

import { createAdminClient } from "../_shared/supabase-client.ts";
import { composeAndWriteRender } from "../_shared/compose-and-write.ts";

interface RequestBody {
  pebble_id: string;
}

const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

serve(async (req: Request) => {
  if (req.method !== "POST") {
    return json({ error: "method not allowed" }, 405);
  }

  // Bearer check: constant-time compare against the service role key.
  const auth = req.headers.get("Authorization") ?? "";
  const presented = auth.startsWith("Bearer ") ? auth.slice(7) : "";
  if (!constantTimeEqual(presented, SERVICE_ROLE)) {
    console.error("backfill-pebble-render: auth failed");
    return json({ error: "unauthorized" }, 401);
  }

  let body: RequestBody;
  try {
    body = await req.json();
  } catch (err) {
    console.error("backfill-pebble-render: body parse failed:", err);
    return json({ error: "invalid body" }, 400);
  }
  if (!body || typeof body.pebble_id !== "string") {
    return json({ error: "invalid pebble_id" }, 400);
  }

  const admin = createAdminClient();
  try {
    const rendered = await composeAndWriteRender(admin, body.pebble_id);
    return json({ pebble_id: body.pebble_id, ...rendered }, 200);
  } catch (err) {
    console.error("backfill-pebble-render: compose failed:", err);
    const message = err instanceof Error ? err.message : String(err);
    const status = message.includes("not found") ? 404 : 500;
    return json({ error: message, pebble_id: body.pebble_id }, status);
  }
});

function constantTimeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) {
    diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return diff === 0;
}

// deno-lint-ignore no-explicit-any
function json(body: any, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
```

- [ ] **Step 2: deno check**

```bash
cd packages/supabase/supabase/functions
deno check backfill-pebble-render/index.ts
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add packages/supabase/supabase/functions/backfill-pebble-render
git commit -m "$(cat <<'EOF'
feat(api): add backfill-pebble-render edge function (#261)

Service-role-gated endpoint that re-renders an existing pebble by id.
Used by the one-off backfill script for legacy pebbles and post-engine-
bump re-renders. Constant-time bearer compare on the service role key.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4.3: Serve the edge functions locally and smoke-test `compose-pebble`

**Files:** (none)

- [ ] **Step 1: Start local Supabase if it isn't already**

```bash
cd packages/supabase
npm run db:status
```

Expected: all services `RUNNING`. If not, `npm run db:start`.

- [ ] **Step 2: Serve the edge functions**

```bash
supabase functions serve --env-file supabase/.env.local
```

(If `.env.local` doesn't exist, create it with `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_ROLE_KEY` copied from `supabase status` output.)

Keep this process running in one terminal. Use a second terminal for the curl tests.

Expected: the server logs `Serving functions on http://localhost:54321/functions/v1/` and lists both `compose-pebble` and `backfill-pebble-render`.

- [ ] **Step 3: Get a user JWT**

Log into the webapp running against local Supabase, open DevTools → Application → Local Storage, and copy the `sb-…-auth-token`'s `access_token` field value. Export it:

```bash
export USER_JWT='<paste here>'
```

(Alternative: run the Supabase CLI's `supabase auth sign-in` if configured.)

- [ ] **Step 4: Get an emotion and domain id**

```bash
supabase db execute "select id from public.emotions limit 1;"
supabase db execute "select id from public.domains limit 1;"
```

Export the resulting uuids:

```bash
export EMOTION_ID='<uuid>'
export DOMAIN_ID='<uuid>'
```

- [ ] **Step 5: curl compose-pebble**

```bash
curl -sS -X POST http://localhost:54321/functions/v1/compose-pebble \
  -H "Authorization: Bearer $USER_JWT" \
  -H "Content-Type: application/json" \
  -d "$(cat <<EOF
{
  "payload": {
    "name": "smoke test pebble",
    "happened_at": "2026-04-15T12:00:00Z",
    "intensity": 2,
    "positiveness": 0,
    "visibility": "private",
    "emotion_id": "$EMOTION_ID",
    "domain_ids": ["$DOMAIN_ID"]
  }
}
EOF
)" | jq .
```

Expected: JSON response with `pebble_id`, `render_svg` (starts with `"<svg`), `render_manifest` (array), `render_version: "0.1.0"`.

- [ ] **Step 6: Visually inspect the render_svg**

Copy the `render_svg` string, paste into https://svgviewer.dev or into a `data:image/svg+xml,<urlencoded>` URL in a browser.

Expected: a shape outline with a glyph drawn inside the glyph slot.

- [ ] **Step 7: Verify the DB row**

```bash
supabase db execute "select id, length(render_svg) as svg_len, jsonb_array_length(render_manifest) as mf_len, render_version from pebbles order by created_at desc limit 1;"
```

Expected: `svg_len > 100`, `mf_len >= 3`, `render_version = "0.1.0"`.

---

### Task 4.4: Smoke-test `backfill-pebble-render`

**Files:** (none)

- [ ] **Step 1: Clear the render columns on the pebble created in Task 4.3**

```bash
supabase db execute "update pebbles set render_svg = null, render_manifest = null, render_version = null where name = 'smoke test pebble';"
```

- [ ] **Step 2: Grab the pebble_id and service role key**

```bash
PEBBLE_ID=$(supabase db execute "select id from pebbles where name = 'smoke test pebble' limit 1;" --output csv | tail -1)
echo $PEBBLE_ID
```

Service role key:

```bash
supabase status | grep service_role
export SERVICE_ROLE='<paste>'
```

- [ ] **Step 3: curl backfill-pebble-render**

```bash
curl -sS -X POST http://localhost:54321/functions/v1/backfill-pebble-render \
  -H "Authorization: Bearer $SERVICE_ROLE" \
  -H "Content-Type: application/json" \
  -d "{\"pebble_id\":\"$PEBBLE_ID\"}" | jq .
```

Expected: 200 with the same shape as Task 4.3 Step 5 (pebble_id + render_svg + render_manifest + render_version).

- [ ] **Step 4: Verify the DB row was rehydrated**

```bash
supabase db execute "select id, length(render_svg) from pebbles where id = '$PEBBLE_ID';"
```

Expected: non-null svg length.

- [ ] **Step 5: Verify auth rejection**

```bash
curl -sS -X POST http://localhost:54321/functions/v1/backfill-pebble-render \
  -H "Authorization: Bearer wrong-key" \
  -H "Content-Type: application/json" \
  -d "{\"pebble_id\":\"$PEBBLE_ID\"}"
```

Expected: 401 with `{"error":"unauthorized"}`.

---

## Phase 5: Backfill script

### Task 5.1: Create `scripts/backfill-renders.ts`

**Files:**
- Create: `packages/supabase/scripts/backfill-renders.ts`

- [ ] **Step 1: Write the script**

```typescript
#!/usr/bin/env -S deno run --allow-env --allow-net
/**
 * Backfill script — composes renders for every pebble with render_svg IS NULL.
 *
 * Run:
 *   SUPABASE_URL=... SUPABASE_SERVICE_ROLE_KEY=... \
 *     deno run --allow-env --allow-net packages/supabase/scripts/backfill-renders.ts
 *
 * Sequential. Idempotent (only touches null rows). Logs ✓/✗ per pebble and
 * a final summary. Exits 0 even on partial failures — re-run to retry.
 */

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

if (!SUPABASE_URL || !SERVICE_ROLE) {
  console.error("SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY must be set");
  Deno.exit(2);
}

const admin = createClient(SUPABASE_URL, SERVICE_ROLE, {
  auth: { persistSession: false },
});

const { data: rows, error } = await admin
  .from("pebbles")
  .select("id")
  .is("render_svg", null)
  .order("created_at", { ascending: true });

if (error) {
  console.error("Query failed:", error);
  Deno.exit(1);
}

const ids = (rows ?? []).map((r) => r.id as string);
console.log(`Found ${ids.length} pebble(s) with render_svg=null`);

let rendered = 0;
let failed = 0;

for (const id of ids) {
  const res = await fetch(`${SUPABASE_URL}/functions/v1/backfill-pebble-render`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${SERVICE_ROLE}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ pebble_id: id }),
  });

  if (res.ok) {
    rendered += 1;
    console.log(`✓ ${id}`);
  } else {
    failed += 1;
    const text = await res.text();
    console.log(`✗ ${id} [${res.status}] ${text}`);
  }
}

console.log(`\nSummary: rendered=${rendered} failed=${failed} total=${ids.length}`);
Deno.exit(failed > 0 && rendered === 0 ? 1 : 0);
```

- [ ] **Step 2: Run it against the local DB**

```bash
cd packages/supabase
SUPABASE_URL=http://localhost:54321 \
SUPABASE_SERVICE_ROLE_KEY=$SERVICE_ROLE \
  deno run --allow-env --allow-net scripts/backfill-renders.ts
```

Expected: `Found 0 pebble(s) with render_svg=null` (because the smoke-test pebble was already rehydrated in Task 4.4). Then: `Summary: rendered=0 failed=0 total=0`.

- [ ] **Step 3: Force a test run**

```bash
supabase db execute "update pebbles set render_svg = null where name = 'smoke test pebble';"
SUPABASE_URL=http://localhost:54321 \
SUPABASE_SERVICE_ROLE_KEY=$SERVICE_ROLE \
  deno run --allow-env --allow-net scripts/backfill-renders.ts
```

Expected: `Found 1 pebble(s)`, then `✓ <uuid>`, then `Summary: rendered=1 failed=0 total=1`.

- [ ] **Step 4: Verify idempotence**

Re-run the same command without clearing first.

Expected: `Found 0`, `Summary: rendered=0 failed=0 total=0`.

- [ ] **Step 5: Commit**

```bash
git add packages/supabase/scripts/backfill-renders.ts
git commit -m "$(cat <<'EOF'
feat(api): add backfill-renders Deno script (#261)

Iterates pebbles with render_svg IS NULL, invokes backfill-pebble-render
for each. Sequential, idempotent, logs progress, returns a summary.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 6: iOS — data layer + dependency

### Task 6.1: Add the SVG package dependency

**Files:**
- Modify: `apps/ios/project.yml`

- [ ] **Step 1: Pick the package after a one-hour tryout**

Stand up a scratch branch (or a disposable SwiftUI Preview) and import all three candidates against one of the 9 shape SVGs:
1. `SVGView` from `https://github.com/exyte/SVGView` (recommended)
2. `SwiftDraw` from `https://github.com/swhitty/SwiftDraw`
3. `SVGKit` from `https://github.com/SVGKit/SVGKit`

Evaluate: does it parse the SVG from a string at runtime? does it render? does it expose per-path nodes (for future animation)? Pick one. Document the choice in a short scratch note.

- [ ] **Step 2: Add the package to project.yml**

Open `apps/ios/project.yml` and add the chosen package under `packages:` and reference it in the `Pebbles` target's `dependencies:` list. For SVGView:

```yaml
packages:
  SVGView:
    url: https://github.com/exyte/SVGView
    majorVersion: 1.0.4

targets:
  Pebbles:
    dependencies:
      - package: SVGView
```

(Adapt the values to the actual package chosen + its latest stable version.)

- [ ] **Step 3: Regenerate the Xcode project**

```bash
cd apps/ios
npm run generate --workspace=@pbbls/ios
```

Expected: `xcodegen` runs and updates `Pebbles.xcodeproj` with the new dependency.

- [ ] **Step 4: Verify the package resolves**

```bash
xcodebuild -project Pebbles.xcodeproj -list 2>&1 | head -20
```

Expected: no error. Open the project in Xcode once to let Swift Package Manager resolve the dependency, or run `xcodebuild -resolvePackageDependencies`.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/project.yml
# intentionally NOT adding Pebbles.xcodeproj (git-ignored per project convention)
git commit -m "$(cat <<'EOF'
feat(ios): add SVGView dependency for runtime SVG rendering (#261)

Adds the SVGView Swift package to the Pebbles target for runtime
parsing and rendering of server-composed SVG strings. Chosen after
evaluating SVGView / SwiftDraw / SVGKit for a SwiftUI-native,
runtime-parse, per-path-accessible fit.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 6.2: Create `ComposePebbleResponse.swift`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Models/ComposePebbleResponse.swift`
- Test: `apps/ios/PebblesTests/ComposePebbleResponseDecodingTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("ComposePebbleResponse decoding")
struct ComposePebbleResponseDecodingTests {

    @Test("decodes a successful compose response")
    func decodesSuccess() throws {
        let json = """
        {
          "pebble_id": "550e8400-e29b-41d4-a716-446655440000",
          "render_svg": "<svg xmlns=\\"http://www.w3.org/2000/svg\\"></svg>",
          "render_manifest": [{"type":"glyph","delay":0,"duration":800}],
          "render_version": "0.1.0"
        }
        """.data(using: .utf8)!

        let decoded = try JSONDecoder().decode(ComposePebbleResponse.self, from: json)

        #expect(decoded.pebbleId.uuidString.lowercased() == "550e8400-e29b-41d4-a716-446655440000")
        #expect(decoded.renderSvg?.hasPrefix("<svg") == true)
        #expect(decoded.renderVersion == "0.1.0")
    }

    @Test("decodes a soft-success 5xx response (render fields null)")
    func decodesSoftFailure() throws {
        let json = """
        {
          "pebble_id": "550e8400-e29b-41d4-a716-446655440000",
          "error": "compose failed: engine exploded"
        }
        """.data(using: .utf8)!

        let decoded = try JSONDecoder().decode(ComposePebbleResponse.self, from: json)

        #expect(decoded.pebbleId.uuidString.lowercased() == "550e8400-e29b-41d4-a716-446655440000")
        #expect(decoded.renderSvg == nil)
        #expect(decoded.renderVersion == nil)
    }
}
```

- [ ] **Step 2: Run test → expect compile failure**

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  test -only-testing:PebblesTests/ComposePebbleResponseDecodingTests 2>&1 | tail -20
```

Expected: compile error — `ComposePebbleResponse` does not exist.

- [ ] **Step 3: Create the type**

```swift
import Foundation

/// Decodable wrapper for the `compose-pebble` edge function response.
///
/// Fields are optional because the edge function may return a soft-success
/// 5xx body with only `pebble_id` set when the insert succeeded but the
/// compose step failed. The iOS client advances to the detail sheet in that
/// case; the sheet renders text-only when `renderSvg` is nil.
struct ComposePebbleResponse: Decodable {
    let pebbleId: UUID
    let renderSvg: String?
    let renderVersion: String?

    // `render_manifest` is accepted but not stored on this struct — slice 1
    // does not consume it. It will be added back when the iOS animation
    // consumer is built in a later slice.

    enum CodingKeys: String, CodingKey {
        case pebbleId = "pebble_id"
        case renderSvg = "render_svg"
        case renderVersion = "render_version"
    }
}
```

- [ ] **Step 4: Run tests → expect 2 passing**

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  test -only-testing:PebblesTests/ComposePebbleResponseDecodingTests 2>&1 | tail -20
```

Expected: 2/2 pass.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/ComposePebbleResponse.swift \
        apps/ios/PebblesTests/ComposePebbleResponseDecodingTests.swift
git commit -m "$(cat <<'EOF'
feat(ios): add ComposePebbleResponse model (#261)

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 6.3: Extend `PebbleDetail.swift` with render fields

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/PebbleDetail.swift`

- [ ] **Step 1: Add the three optional properties and their CodingKeys**

Open `apps/ios/Pebbles/Features/Path/Models/PebbleDetail.swift`. In the `PebbleDetail` struct, add three new stored properties after `collections`:

```swift
let renderSvg: String?
let renderVersion: String?
// renderManifest is intentionally not stored on PebbleDetail in slice 1 —
// the animation consumer lives in a later slice. We still decode it as an
// opaque placeholder so the field doesn't break decoding when present.
```

In the `CodingKeys` enum, add:

```swift
case renderSvg = "render_svg"
case renderVersion = "render_version"
```

In the `init(from:)` decoder, add after the `collections` wrapper decoding:

```swift
self.renderSvg = try container.decodeIfPresent(String.self, forKey: .renderSvg)
self.renderVersion = try container.decodeIfPresent(String.self, forKey: .renderVersion)
```

- [ ] **Step 2: Extend the existing decoding test** (`apps/ios/PebblesTests/PebbleDetailDecodingTests.swift`) to cover the new fields

Add a new test:

```swift
@Test("decodes render columns when present")
func decodesRenderColumns() throws {
    let json = """
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "test",
      "happened_at": "2026-04-15T12:00:00Z",
      "intensity": 2,
      "positiveness": 0,
      "visibility": "private",
      "emotion": {"id": "550e8400-e29b-41d4-a716-446655440001", "name": "joy", "color": "#fff"},
      "pebble_domains": [],
      "pebble_souls": [],
      "collection_pebbles": [],
      "render_svg": "<svg/>",
      "render_version": "0.1.0"
    }
    """.data(using: .utf8)!

    let decoded = try JSONDecoder().pebbleDetailDecoder().decode(PebbleDetail.self, from: json)

    #expect(decoded.renderSvg == "<svg/>")
    #expect(decoded.renderVersion == "0.1.0")
}

@Test("decodes when render columns are absent (legacy pebble)")
func decodesLegacy() throws {
    let json = """
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "legacy",
      "happened_at": "2026-04-15T12:00:00Z",
      "intensity": 2,
      "positiveness": 0,
      "visibility": "private",
      "emotion": {"id": "550e8400-e29b-41d4-a716-446655440001", "name": "joy", "color": "#fff"},
      "pebble_domains": [],
      "pebble_souls": [],
      "collection_pebbles": []
    }
    """.data(using: .utf8)!

    let decoded = try JSONDecoder().pebbleDetailDecoder().decode(PebbleDetail.self, from: json)

    #expect(decoded.renderSvg == nil)
    #expect(decoded.renderVersion == nil)
}
```

Note: the helper `.pebbleDetailDecoder()` is used in existing tests in this file — mirror that pattern. If it doesn't exist, use a plain `JSONDecoder()` with `.iso8601` date strategy.

- [ ] **Step 3: Run the tests**

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  test -only-testing:PebblesTests/PebbleDetailDecodingTests 2>&1 | tail -30
```

Expected: all existing tests + 2 new ones pass.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/PebbleDetail.swift \
        apps/ios/PebblesTests/PebbleDetailDecodingTests.swift
git commit -m "$(cat <<'EOF'
feat(ios): extend PebbleDetail with render columns (#261)

Adds renderSvg + renderVersion as optional decoded fields so the same
model serves both the post-create detail sheet (slice 1) and the
existing edit flow. Fields are decodeIfPresent so legacy pebbles keep
decoding.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 6.4: Extend `EditPebbleSheet.load()` select query

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift`

- [ ] **Step 1: Read the current select string**

Open `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift:87-99` and note the current `async let detailQuery` string.

- [ ] **Step 2: Add the render columns to the select**

Replace the select list:

```swift
async let detailQuery: PebbleDetail = supabase.client
    .from("pebbles")
    .select("""
        id, name, description, happened_at, intensity, positiveness, visibility,
        render_svg, render_version,
        emotion:emotions(id, name, color),
        pebble_domains(domain:domains(id, name)),
        pebble_souls(soul:souls(id, name)),
        collection_pebbles(collection:collections(id, name))
    """)
    .eq("id", value: pebbleId)
    .single()
    .execute()
    .value
```

(Only `render_svg, render_version` are added; the rest is unchanged.)

- [ ] **Step 3: Build + run existing tests**

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  build 2>&1 | tail -10
```

Expected: build succeeds. No behavioral change — the edit form just decodes two extra optional fields and ignores them.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift
git commit -m "$(cat <<'EOF'
chore(ios): include render columns in EditPebbleSheet load query (#261)

No behavioral change — the edit form still ignores the new fields. This
keeps the shared PebbleDetail decoder from having to distinguish edit
vs detail queries.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 7: iOS — views and wiring

### Task 7.1: Create `PebbleRenderView.swift`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/PebbleRenderView.swift`

- [ ] **Step 1: Write the view (for SVGView; adapt if you picked a different package)**

```swift
import SwiftUI
import SVGView

/// Renders a server-composed pebble SVG string.
///
/// Slice 1: static display only. The animation manifest consumer is a
/// later slice. Fills width and scales to fit; aspect ratio is preserved.
struct PebbleRenderView: View {
    let svg: String

    var body: some View {
        SVGView(string: svg)
            .aspectRatio(contentMode: .fit)
            .accessibilityHidden(true)
    }
}

#Preview {
    PebbleRenderView(svg: """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
          <circle cx="50" cy="50" r="40" fill="none" stroke="black" stroke-width="2"/>
        </svg>
        """)
    .frame(width: 260, height: 260)
}
```

- [ ] **Step 2: Build**

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  build 2>&1 | tail -10
```

Expected: builds cleanly.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/PebbleRenderView.swift
git commit -m "$(cat <<'EOF'
feat(ios): add PebbleRenderView wrapping SVGView (#261)

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 7.2: Create `PebbleDetailSheet.swift`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift`

- [ ] **Step 1: Write the sheet**

```swift
import SwiftUI
import os

/// Post-create viewer sheet.
///
/// Presented by `PathView` after `CreatePebbleSheet` successfully dismisses.
/// Loads the `PebbleDetail` from the DB (now including `render_svg`) and
/// renders `PebbleRenderView` at the top when the render is available,
/// followed by a metadata block and a Done button.
///
/// Distinct from `EditPebbleSheet`: this sheet is a read-only reveal, not a
/// form. Tap-to-view of existing pebbles from the path list remains on
/// `EditPebbleSheet` in slice 1.
struct PebbleDetailSheet: View {
    let pebbleId: UUID

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var detail: PebbleDetail?
    @State private var isLoading = true
    @State private var loadError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "pebble-detail")

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Recorded pebble")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done") { dismiss() }
                    }
                }
        }
        .task { await load() }
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            ProgressView()
        } else if let loadError {
            VStack(spacing: 12) {
                Text(loadError).foregroundStyle(.secondary)
                Button("Retry") { Task { await load() } }
            }
        } else if let detail {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if let svg = detail.renderSvg {
                        PebbleRenderView(svg: svg)
                            .frame(maxWidth: .infinity)
                            .frame(height: 260)
                            .padding(.vertical)
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text(detail.name).font(.headline)
                        if let description = detail.description, !description.isEmpty {
                            Text(description).font(.body)
                        }
                        Text(detail.happenedAt, style: .date)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        if !detail.domains.isEmpty {
                            Text(detail.domains.map(\.name).joined(separator: " · "))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal)
                }
            }
        }
    }

    private func load() async {
        isLoading = true
        loadError = nil
        do {
            let loaded: PebbleDetail = try await supabase.client
                .from("pebbles")
                .select("""
                    id, name, description, happened_at, intensity, positiveness, visibility,
                    render_svg, render_version,
                    emotion:emotions(id, name, color),
                    pebble_domains(domain:domains(id, name)),
                    pebble_souls(soul:souls(id, name)),
                    collection_pebbles(collection:collections(id, name))
                """)
                .eq("id", value: pebbleId)
                .single()
                .execute()
                .value
            self.detail = loaded
            self.isLoading = false
        } catch {
            logger.error("pebble detail load failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Couldn't load this pebble."
            self.isLoading = false
        }
    }
}

#Preview {
    PebbleDetailSheet(pebbleId: UUID())
        .environment(SupabaseService())
}
```

- [ ] **Step 2: Build**

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  build 2>&1 | tail -10
```

Expected: build succeeds.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift
git commit -m "$(cat <<'EOF'
feat(ios): add PebbleDetailSheet for post-create reveal (#261)

New viewer sheet presented after CreatePebbleSheet dismisses.
Distinct from EditPebbleSheet — this is a read-only reveal for the
newly-recorded pebble, not the ongoing edit surface.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 7.3: Migrate `CreatePebbleSheet.save()` to `functions.invoke`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift`

- [ ] **Step 1: Change the `onCreated` callback type**

In `CreatePebbleSheet.swift`, replace:

```swift
let onCreated: () -> Void
```

with:

```swift
let onCreated: (UUID) -> Void
```

- [ ] **Step 2: Replace `save()`**

Replace the `private func save() async { … }` body with:

```swift
private func save() async {
    guard draft.isValid else { return }
    isSaving = true
    saveError = nil

    do {
        let payload = PebbleCreatePayload(from: draft)

        let response: ComposePebbleResponse = try await supabase.client
            .functions
            .invoke(
                "compose-pebble",
                options: FunctionInvokeOptions(body: ComposePebbleRequest(payload: payload))
            )

        onCreated(response.pebbleId)
        dismiss()
    } catch {
        logger.error("compose-pebble invoke failed: \(error.localizedDescription, privacy: .private)")

        // Soft-success: if the error carries a pebble_id (edge function returned
        // 5xx with pebble_id in body), advance anyway so the user sees the
        // detail sheet with a text-only fallback.
        if let softId = Self.softSuccessPebbleId(from: error) {
            onCreated(softId)
            dismiss()
            return
        }

        self.saveError = "Couldn't save your pebble. Please try again."
        self.isSaving = false
    }
}

/// Extract a `pebble_id` from a FunctionInvokeError response body.
/// Returns nil if the error isn't a soft-success (i.e., body doesn't include pebble_id).
private static func softSuccessPebbleId(from error: Error) -> UUID? {
    guard let fnError = error as? FunctionsError else { return nil }
    guard let data = fnError.responseBody else { return nil }
    guard let payload = try? JSONDecoder().decode(ComposePebbleResponse.self, from: data) else { return nil }
    return payload.pebbleId
}
```

Note: `FunctionsError` / `FunctionInvokeOptions` types are from `supabase-swift` v2. Exact spelling may differ — consult the SDK source if the build fails. The intent is: (a) invoke the edge function with the payload wrapped in a body that matches `{ "payload": {…} }`, (b) decode a soft-success body when the invoke throws with a non-2xx response, (c) fall back to the save-error UI otherwise.

- [ ] **Step 3: Add the request wrapper**

At the bottom of the file, replace the existing `private struct CreatePebbleParams` with:

```swift
/// Wrapper matching the compose-pebble edge function body shape.
/// The function expects `{ "payload": {...} }` where `payload` mirrors
/// the create_pebble RPC payload.
private struct ComposePebbleRequest: Encodable {
    let payload: PebbleCreatePayload
}
```

- [ ] **Step 4: Build**

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  build 2>&1 | tail -20
```

Expected: build errors **in `PathView.swift`** because `CreatePebbleSheet.onCreated` signature changed. Leave those for Task 7.4. If the build errors are **only** in `PathView.swift`, proceed. If they're in `CreatePebbleSheet.swift` itself, fix them before Task 7.4.

- [ ] **Step 5: Commit (expect Pebbles target to not yet build as a whole — PathView fix lands in next task)**

```bash
git add apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift
git commit -m "$(cat <<'EOF'
feat(ios): migrate CreatePebbleSheet to compose-pebble edge function (#261)

save() now calls supabase.client.functions.invoke("compose-pebble",…)
instead of the direct create_pebble RPC. onCreated signature changes
from () to (UUID) so PathView can present the detail sheet with the
new pebble_id. Soft-success handling advances to the detail sheet with
a text fallback when the edge function returns 5xx with pebble_id.

PathView fix lands in the next commit.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 7.4: Wire `PathView.swift` to present `PebbleDetailSheet`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/PathView.swift`

- [ ] **Step 1: Add the state and new sheet modifier**

Replace the existing state declarations with:

```swift
@State private var pebbles: [Pebble] = []
@State private var isLoading = true
@State private var loadError: String?
@State private var isPresentingCreate = false
@State private var selectedPebbleId: UUID?
@State private var presentedDetailPebbleId: UUID?
```

- [ ] **Step 2: Update the `body` to add the detail sheet presentation + the new `onCreated` signature**

Replace the `body` block:

```swift
var body: some View {
    NavigationStack {
        content
            .navigationTitle("Path")
    }
    .task { await load() }
    .sheet(isPresented: $isPresentingCreate) {
        CreatePebbleSheet(onCreated: { newPebbleId in
            presentedDetailPebbleId = newPebbleId
            Task { await load() }
        })
    }
    .sheet(item: $selectedPebbleId) { id in
        EditPebbleSheet(pebbleId: id, onSaved: {
            Task { await load() }
        })
    }
    .sheet(item: $presentedDetailPebbleId) { id in
        PebbleDetailSheet(pebbleId: id)
    }
}
```

- [ ] **Step 3: Build the full project**

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  build 2>&1 | tail -20
```

Expected: build succeeds.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/PathView.swift
git commit -m "$(cat <<'EOF'
feat(ios): present PebbleDetailSheet after create (#261)

PathView now holds presentedDetailPebbleId state and uses it to drive
PebbleDetailSheet presentation after CreatePebbleSheet dismisses with a
successful save. Tap-to-view (selectedPebbleId → EditPebbleSheet) is
untouched.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 8: iOS verification

### Task 8.1: E2E smoke in the simulator

**Files:** (none)

- [ ] **Step 1: Ensure local Supabase + edge functions are running**

In one terminal:

```bash
cd packages/supabase
npm run db:status   # confirm RUNNING; restart if needed
supabase functions serve --env-file supabase/.env.local
```

- [ ] **Step 2: Update iOS `Pebbles-Debug.xcconfig` (or equivalent) to point at local Supabase**

Confirm the `SUPABASE_URL` and `SUPABASE_ANON_KEY` are the local values. If they're already set correctly, skip.

- [ ] **Step 3: Build + run on the simulator**

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  build 2>&1 | tail -5
```

Then launch via Xcode (`⌘R`) or `xcrun simctl` against the simulator booted to `iPhone 17`.

- [ ] **Step 4: Log in, create a pebble, observe the reveal**

1. Sign in as a test user.
2. Tap "Record a pebble" on the Path tab.
3. Fill in name, description, date, emotion, domain, valence (pick intensity=2, positiveness=0 first).
4. Tap Save.
5. Expected: `CreatePebbleSheet` dismisses, `PebbleDetailSheet` opens, a composed render is visible at the top.
6. Tap Done — sheet dismisses, return to Path with the new row in the list.

- [ ] **Step 5: Repeat for 3 variants spanning the grid**

- `(intensity: 1, positiveness: -1)` → small, lowlight
- `(intensity: 2, positiveness: 0)` → medium, neutral
- `(intensity: 3, positiveness: 1)` → large, highlight

Expected: the shape and glyph position visibly change across variants.

---

### Task 8.2: Negative-path checks

**Files:** (none)

- [ ] **Step 1: Force a compose failure — confirm soft-success**

In a scratch terminal, break one domain's seed glyph:

```bash
supabase db execute "update public.glyphs set strokes = '[{\"d\":\"INVALID PATH\",\"strokeWidth\":3}]'::jsonb where name = 'domain:<pick-one-slug>';"
```

Create a pebble in that domain via the simulator.

Expected: the sheet advances to `PebbleDetailSheet`; `renderSvg` is nil; the sheet shows the metadata-only layout (name, date, domain). Logs (Xcode console) show the compose error with label `compose-pebble: composeAndWrite failed`.

Then revert:

```bash
supabase db execute "update public.glyphs set strokes = '<valid-json-from-domain-glyph-seeds.json>'::jsonb where name = 'domain:<same-slug>';"
```

Re-run the backfill script to heal:

```bash
cd packages/supabase
SUPABASE_URL=http://localhost:54321 \
SUPABASE_SERVICE_ROLE_KEY=$SERVICE_ROLE \
  deno run --allow-env --allow-net scripts/backfill-renders.ts
```

Reopen the broken pebble in the app (via tap-to-edit and back). After the next app launch the detail sheet will show the composed render on demand.

- [ ] **Step 2: Bad payload — confirm 4xx surfaces as save error**

Temporarily change the simulator's pebble creation to omit `emotion_id` (either via a debug hack or by tapping Save without selecting an emotion if `isValid` allows it).

Expected: `CreatePebbleSheet` stays open with the `saveError` state populated. No advance to detail sheet.

- [ ] **Step 3: Backfill auth — confirm 401**

```bash
curl -sS -X POST http://localhost:54321/functions/v1/backfill-pebble-render \
  -H "Authorization: Bearer wrong-key" \
  -H "Content-Type: application/json" \
  -d '{"pebble_id":"00000000-0000-0000-0000-000000000000"}'
```

Expected: 401 with `{"error":"unauthorized"}`.

---

## Phase 9: Final gates and webapp regression

### Task 9.1: Webapp regression check

**Files:** (none — read-only verification)

- [ ] **Step 1: Build the webapp against regenerated types**

```bash
cd apps/web
npm run build
```

Expected: build succeeds.

- [ ] **Step 2: Run it locally**

```bash
npm run dev
```

- [ ] **Step 3: Create a webapp pebble and verify it still renders**

Open http://localhost:3000, sign in, create a pebble via the webapp create flow, and verify it still renders via the existing client-side composition path. No regression.

- [ ] **Step 4: Open an iOS-created pebble in the webapp**

Navigate to the path/timeline view in the webapp. The iOS-created pebbles should show — note that they will have `glyph_id = NULL`, so the webapp will fall through to whatever its existing "no glyph" rendering path does. **This is expected.** If the webapp crashes on null glyphs, that's a pre-existing bug and should be filed as a separate issue, **not** addressed in this slice.

---

### Task 9.2: Lint + type gates

**Files:** (none)

- [ ] **Step 1: Webapp lint**

```bash
cd apps/web
npm run lint
```

Expected: no errors.

- [ ] **Step 2: Deno check on every new `.ts` file**

```bash
cd packages/supabase/supabase/functions
deno check _shared/engine/types.ts
deno check _shared/engine/glyph.ts
deno check _shared/engine/layout.ts
deno check _shared/engine/compose.ts
deno check _shared/engine/resolve.ts
deno check _shared/engine/shapes/index.ts
deno check _shared/supabase-client.ts
deno check _shared/compose-and-write.ts
deno check compose-pebble/index.ts
deno check backfill-pebble-render/index.ts

cd ../../scripts
deno check backfill-renders.ts
deno check smoke-test-engine.ts
```

Expected: all silent (no errors).

- [ ] **Step 3: iOS build + tests**

```bash
cd apps/ios
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  test 2>&1 | tail -40
```

Expected: build passes; all Swift Testing suites (including the new `ComposePebbleResponseDecodingTests` and the extended `PebbleDetailDecodingTests`) pass.

- [ ] **Step 4: Engine smoke-test re-run**

```bash
cd packages/supabase
deno run scripts/smoke-test-engine.ts
```

Expected: `rendered=9/9`.

---

### Task 9.3: Update MEMORY / review issue checklist

**Files:** (none — housekeeping)

- [ ] **Step 1: Walk through issue #261's checklist**

Open issue #261 and manually tick each checklist item against the commits on this branch:

```bash
git log --oneline main..HEAD
```

Map each commit to a checklist item. Flag anything missing for follow-up.

- [ ] **Step 2: Add any newly-discovered learnings to auto-memory**

Useful memory candidates that may have emerged during implementation:
- The chosen iOS SVG package (so future slices don't re-evaluate)
- Any Supabase CLI / Deno gotchas encountered while getting edge functions to serve locally
- Any auth-forwarding or CORS quirks that surprised you

Save as `feedback` or `reference` memory per the auto-memory section of the system prompt.

---

### Task 9.4: Open the PR

**Files:** (none)

- [ ] **Step 1: Push the branch**

```bash
git push -u origin feat/261-remote-pebble-engine-slice-1
```

- [ ] **Step 2: Open the PR against main**

```bash
gh pr create --title "feat(core): remote pebble engine — slice 1 (iOS fallback render)" \
  --body "$(cat <<'EOF'
Resolves #261.

## Summary

Walking-skeleton slice 1 of the Remote Pebble Engine (PRD #260). A single vertical that touches DB, engine, two edge functions, and iOS — the smallest cut that demystifies Supabase edge functions end-to-end and produces a real server-composed pebble render on iOS.

## Key changes

- **DB migration** `20260415000001_remote_pebble_engine.sql`: nullable `glyphs.user_id` + `shape_id`, relaxed `glyphs_select` RLS for system rows, `domains.default_glyph_id` FK, render columns on `pebbles`, seeds 18 system glyphs + links each domain.
- **Engine port** `packages/supabase/supabase/functions/_shared/engine/`: `types.ts`, `glyph.ts`, `layout.ts`, `compose.ts`, `resolve.ts`, `shapes/×9` — ported verbatim from the POC in #260, adapted for Deno imports.
- **Edge functions**: `compose-pebble` (client-facing, wraps `create_pebble` RPC with JWT forwarding, then composes and writes back) and `backfill-pebble-render` (ops-only, service-role gated).
- **Scripts**: `backfill-renders.ts` (Deno, iterates null-render pebbles) and `smoke-test-engine.ts` (synthetic input, pure function only).
- **iOS**: new `PebbleRenderView` (SVGView wrapper), new `PebbleDetailSheet` (post-create reveal, distinct from `EditPebbleSheet`), `ComposePebbleResponse` model, extended `PebbleDetail` with optional render fields, `CreatePebbleSheet.save()` migrated from direct RPC to `functions.invoke`, `PathView` wired to present the detail sheet after create.

## What's NOT in this PR (deferred to later slices)

- Webapp composition path (zero changes to `apps/web/`)
- iOS carve editor
- Animation manifest consumer
- Fossil layer
- Path-list visual rendering
- Retry logic / idempotency keys
- `pg_net`-driven compose orchestration
- Render versioning workflow
- Backfill concurrency

Full deferred list in the spec under "Out of slice 1".

## Test plan

- [x] `npm run build` (webapp) passes against regenerated types
- [x] `npm run lint` (webapp) clean
- [x] `deno check` clean on every new `.ts` file
- [x] `xcodebuild test` passes; new `ComposePebbleResponseDecodingTests` + extended `PebbleDetailDecodingTests` pass
- [x] `deno run scripts/smoke-test-engine.ts` → `rendered=9/9`
- [x] E2E in simulator: create pebble in 3 valence/size variants → `PebbleDetailSheet` opens with matching render
- [x] Negative-path: forced engine error → 500 with `pebble_id` → `PebbleDetailSheet` shows metadata-only fallback
- [x] `backfill-pebble-render` 401s on wrong bearer
- [x] Backfill script is idempotent (two runs: second is a no-op)
- [x] Webapp regression check — create + render a webapp pebble, existing path still works

## Labels & milestone

Inheriting from issue #261: `feat`, `core`, `db`, `api`, `ios`, `supabase` · milestone `M19 · iOS ShameVP`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 3: Verify labels and milestone are applied**

```bash
gh pr view --json labels,milestone
```

Expected: all 6 labels present; milestone = `M19 · iOS ShameVP`.

---

## Acceptance

The slice is done when:

- [ ] All tasks above are checked off
- [ ] Issue #261's checklist is fully green
- [ ] The acceptance criterion holds: **creating a pebble on iOS opens `PebbleDetailSheet` with a server-composed render visible at the top, for all 9 `(intensity, positiveness)` combinations**
- [ ] The webapp is unaffected (no regression on its existing path)
- [ ] The PR is open, labeled, milestoned, and has a green CI check
