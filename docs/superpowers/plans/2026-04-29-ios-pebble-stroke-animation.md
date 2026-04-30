# iOS Pebble Stroke Animation — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Animate the pebble on the iOS read sheet with a progressive stroke-drawing reveal, while removing the now-redundant server-side `render_manifest` column and JSON.

**Architecture:** Server simplification (drop the manifest from engine/edge functions/DB). iOS gains a small native renderer in `apps/ios/Pebbles/Features/Path/Render/`: a SVG `d`-string → `CGPath` parser, a typed `PebbleSVGModel` parsed via `XMLParser`, a versioned timings table keyed by `render_version`, and a `PebbleAnimatedRenderView` that drives per-layer `.trim` animations. The existing `SVGView`-based `PebbleRenderView` stays in place for non-read-sheet call sites and serves as the fallback when parsing fails or Reduce Motion is on.

**Tech Stack:** SwiftUI (iOS 17+), `Foundation.XMLParser`, Swift Testing, existing third-party `SVGView` (for fallback only). Server-side: Deno + Supabase Edge Functions + Postgres migration.

**Spec:** `docs/superpowers/specs/2026-04-29-ios-pebble-stroke-animation-design.md`. **Issue:** [#333](https://github.com/Bohns/pbbls/issues/333). **Branch:** `feat/333-pebble-stroke-animation` (already created; spec already committed there).

**Operational note (per project memory):** This user cannot run local Supabase / Docker. Migrations are pushed directly to the linked remote project with `supabase db push`, and types are regenerated via `supabase gen types typescript --linked > types/database.ts`. Do not call `npm run db:reset`, `npm run db:start`, or `supabase functions serve`.

---

## File map

```
packages/supabase/supabase/functions/_shared/engine/
  types.ts                              (MOD)  — drop AnimationManifest types, drop manifest from PebbleEngineOutput
  compose.ts                            (MOD)  — drop buildManifest/extractPaths/estimatePathLength/TIMING; output { svg, canvas }
packages/supabase/supabase/functions/_shared/
  compose-and-write.ts                  (MOD)  — drop manifest write/return
packages/supabase/supabase/functions/
  compose-pebble/index.ts               (MOD)  — response no longer carries render_manifest (no code change needed; the spread no longer includes it once compose-and-write changes)
  compose-pebble-update/index.ts        (MOD)  — same
packages/supabase/supabase/migrations/
  20260429000000_drop_pebbles_render_manifest.sql   (NEW) — drop the column
packages/supabase/types/
  database.ts                           (REGEN) — pulled from linked remote after migration push
apps/web/lib/
  types.ts                              (MOD)  — drop render_manifest from Pebble
  data/data-provider.ts                 (MOD)  — drop render_manifest from ServerOwnedPebbleFields
  data/supabase-provider.ts             (MOD)  — drop render_manifest from select / merge / mapping
  seed/seed-data.ts                     (MOD)  — drop render_manifest from omitted-keys union
apps/ios/Pebbles/Features/Path/
  Render/SVGPathParser.swift            (NEW)  — d-string → CGPath
  Render/PebbleSVGModel.swift           (NEW)  — composed SVG → typed layered model
  Render/PebbleAnimationTimings.swift   (NEW)  — render_version → phase timings
  Render/PebbleAnimatedRenderView.swift (NEW)  — animated SwiftUI renderer
  Models/ComposePebbleResponse.swift    (MOD)  — drop the placeholder comment about manifest
  Read/PebbleReadBanner.swift           (MOD)  — accept renderVersion, swap PebbleRenderView for PebbleAnimatedRenderView
  Read/PebbleReadView.swift             (MOD)  — pass detail.renderVersion through
apps/ios/PebblesTests/
  SVGPathParserTests.swift              (NEW)
  PebbleSVGModelTests.swift             (NEW)
  PebbleAnimationTimingsTests.swift     (NEW)
  ComposePebbleResponseDecodingTests.swift (MOD) — drop render_manifest from fixture JSON
```

`apps/ios/project.yml` does not change — its `sources: [{ path: Pebbles }]` recursively picks up new files. After adding files run `xcodegen generate` (or `npm run generate --workspace=@pbbls/ios`) to refresh the gitignored `.xcodeproj`.

---

## Task 1 — Drop AnimationManifest types from the engine

**Files:**
- Modify: `packages/supabase/supabase/functions/_shared/engine/types.ts`

- [ ] **Step 1: Remove the manifest type declarations and field**

Open `packages/supabase/supabase/functions/_shared/engine/types.ts`. Delete the `AnimationManifestLayer` interface, the `AnimationManifest` type alias, and the `manifest` field on `PebbleEngineOutput`. The end of the file should read exactly:

```ts
// ── Compose ─────────────────────────────────────────────────

export interface PebbleEngineInput {
  size: PebbleSize;
  valence: PebbleValence;
  /** Full-canvas SVG string for the pebble shape. */
  shapeSvg: string;
  /** Square SVG string for the glyph (output of createGlyphArtwork). */
  glyphSvg: string;
  /** Full-canvas SVG string for the fossil layer (optional). */
  fossilSvg?: string;
  /** Layout overrides. If omitted, uses default config. */
  layoutOverride?: PebbleLayoutConfig;
}

export interface PebbleEngineOutput {
  /** Composed monochrome SVG with stroke IDs. No fills, no colors. */
  svg: string;
  /** Canvas dimensions used. */
  canvas: CanvasSize;
}
```

- [ ] **Step 2: Verify TypeScript still compiles for the package**

Run from repo root:
```bash
npm run build --workspace=@pbbls/supabase
```
Expected: build fails with errors in `compose.ts` referencing `AnimationManifest`/`AnimationManifestLayer`. That's expected — the next task fixes it. (If no errors here, types must have been imported elsewhere; proceed regardless.)

- [ ] **Step 3: Stage but do not commit yet**

```bash
git add packages/supabase/supabase/functions/_shared/engine/types.ts
```

---

## Task 2 — Strip manifest production from `compose.ts`

**Files:**
- Modify: `packages/supabase/supabase/functions/_shared/engine/compose.ts`

- [ ] **Step 1: Replace `compose.ts` with the manifest-free version**

Overwrite the file contents with exactly:

```ts
/**
 * Pebble Engine · Compositor
 *
 * Layers a pebble shape, glyph, and optional fossil into a single
 * monochrome SVG with stroke IDs.
 *
 * Runs SERVER-SIDE as a Supabase Edge Function (Deno).
 * Called by create_pebble / update_pebble RPCs.
 *
 * Pure function. No DOM. No side effects.
 * All SVG manipulation is string-based.
 */

import type {
  PebbleEngineInput,
  PebbleEngineOutput,
} from "./types.ts";
import { resolveLayout } from "./layout.ts";

// ── SVG Parsing Helpers ─────────────────────────────────────

/**
 * Extract the inner content of an <svg> tag.
 * Returns everything between <svg ...> and </svg>.
 */
function extractSvgInner(svgString: string): string {
  const match = svgString.match(/<svg[^>]*>([\s\S]*?)<\/svg>/i);
  return match ? match[1].trim() : svgString.trim();
}

/**
 * Extract the viewBox from an SVG string.
 */
function extractViewBox(svgString: string): string | null {
  const match = svgString.match(/viewBox=["']([^"']+)["']/);
  return match ? match[1] : null;
}

/**
 * Strip all fill attributes and force fill="none" on shape elements.
 * Inject fill="none" on elements that have no fill attribute
 * (SVG defaults to fill="black").
 */
function stripFills(svgInner: string): string {
  let result = svgInner
    // Replace existing fills with none
    .replace(/fill="[^"]*"/g, 'fill="none"')
    // Kill inline style fills
    .replace(/fill:\s*[^;"]+/g, "fill: none");

  // Inject fill="none" on bare elements (no fill attribute → SVG defaults to black)
  result = result.replace(
    /<(path|circle|ellipse|rect|polygon|polyline)(\s)(?![^>]*fill=)/gi,
    '<$1$2fill="none" '
  );

  return result;
}

/**
 * Replace all stroke colors with "currentColor" (monochrome output).
 * The client applies the emotion color at render time.
 */
function monochromeStrokes(svgInner: string): string {
  return svgInner
    .replace(/stroke="[^"]*"/g, 'stroke="currentColor"')
    .replace(/stroke:\s*[^;"]+/g, "stroke: currentColor");
}

/**
 * Prefix IDs on path/element attributes to namespace layers.
 * Strips any existing `id="…"` from the matched tag first, then adds
 * `id="<prefix>:stroke-N"`. This handles both raw SVG paths (no id) and
 * pre-namespaced glyph paths from createGlyphArtwork (which already writes
 * `id="glyph:stroke-N"` and would otherwise duplicate the attribute).
 */
function namespaceIds(svgInner: string, prefix: string): string {
  let index = 0;
  return svgInner.replace(/<path\b([^>]*)>/g, (_match, attrs: string) => {
    const stripped = attrs.replace(/\s*id="[^"]*"/, "");
    return `<path id="${prefix}:stroke-${index++}"${stripped}>`;
  });
}

// ── Main Compose Function ───────────────────────────────────

/**
 * Compose a pebble from its layers.
 *
 * Stacks shape → fossil (optional) → glyph into a single SVG.
 * All strokes are monochrome (currentColor). No fills.
 *
 * @param input — Shape SVG, glyph SVG, fossil SVG, size, valence.
 * @returns     — Composed SVG + canvas size.
 */
export function composePebble(input: PebbleEngineInput): PebbleEngineOutput {
  const {
    size,
    valence,
    shapeSvg,
    glyphSvg,
    fossilSvg,
    layoutOverride,
  } = input;

  // Resolve layout
  const layout = resolveLayout(size, valence, layoutOverride);
  const { canvas, glyphSlot } = layout;

  // ── Process shape layer ─────────────────────────────────
  const shapeInner = extractSvgInner(shapeSvg);
  const shapeClean = namespaceIds(monochromeStrokes(stripFills(shapeInner)), "shape");

  // ── Process fossil layer (optional) ─────────────────────
  let fossilLayer = "";
  if (fossilSvg) {
    const fossilInner = extractSvgInner(fossilSvg);
    const fossilClean = namespaceIds(monochromeStrokes(stripFills(fossilInner)), "fossil");
    fossilLayer = `\n  <g id="layer:fossil" opacity="0.3">\n    ${fossilClean}\n  </g>`;
  }

  // ── Process glyph layer ─────────────────────────────────
  // Flatten the glyph placement into a single <g transform> instead of a
  // nested <svg viewBox>. SVGView (iOS) doesn't handle nested <svg> elements
  // with their own viewBox correctly, so we compute the viewBox→slot scale
  // ourselves and apply it as part of the transform chain.
  const glyphInner = extractSvgInner(glyphSvg);
  const glyphViewBox = extractViewBox(glyphSvg) || "0 0 200 200";
  const glyphClean = namespaceIds(monochromeStrokes(stripFills(glyphInner)), "glyph");

  const vbParts = glyphViewBox.split(" ").map(Number);
  const vbWidth = vbParts[2] || 200;
  const vbHeight = vbParts[3] || 200;
  const slotScale = Math.min(glyphSlot.size / vbWidth, glyphSlot.size / vbHeight);

  const glyphLayer = [
    `  <g id="layer:glyph" transform="translate(${glyphSlot.x}, ${glyphSlot.y}) scale(${Math.round(slotScale * 1000) / 1000})">`,
    `    ${glyphClean}`,
    `  </g>`,
  ].join("\n");

  // ── Compose final SVG ───────────────────────────────────
  const svg = [
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${canvas.width} ${canvas.height}" width="${canvas.width}" height="${canvas.height}">`,
    `  <g id="layer:shape">`,
    `    ${shapeClean}`,
    `  </g>`,
    fossilLayer,
    glyphLayer,
    `</svg>`,
  ].join("\n");

  return { svg, canvas };
}
```

- [ ] **Step 2: Verify TypeScript build of the package**

Run:
```bash
npm run build --workspace=@pbbls/supabase
```
Expected: now fails inside `compose-and-write.ts` (it still references `output.manifest`). Task 3 fixes it.

- [ ] **Step 3: Stage**

```bash
git add packages/supabase/supabase/functions/_shared/engine/compose.ts
```

---

## Task 3 — Strip manifest write/return from `compose-and-write`

**Files:**
- Modify: `packages/supabase/supabase/functions/_shared/compose-and-write.ts`

- [ ] **Step 1: Update the file to drop manifest plumbing**

Replace the entire contents of `packages/supabase/supabase/functions/_shared/compose-and-write.ts` with:

```ts
/**
 * compose-and-write
 *
 * Given a pebble_id and an admin supabase client, load the pebble + its
 * resolved glyph source, run the engine, write render_svg / render_version
 * back to the row, and return the composed output.
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
    // PostgREST's .single() returns PGRST116 ("0 rows") when the pebble
    // doesn't exist. Normalize that to a "pebble not found" string so
    // callers (e.g. backfill-pebble-render) can map it to a 404.
    // deno-lint-ignore no-explicit-any
    const err = loadError as any;
    const isNotFound = !pebble || err?.code === "PGRST116";
    throw new Error(
      isNotFound
        ? `pebble not found: ${pebbleId}`
        : `load pebble failed: ${err?.message ?? "unknown"}`,
    );
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
        .select("strokes")
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
  } catch (err) {
    console.error("compose-and-write: engine error:", err);
    throw new Error(`engine error: ${err instanceof Error ? err.message : String(err)}`);
  }

  // ── Write render columns ─────────────────────────────────────────────
  const { error: updateError } = await admin
    .from("pebbles")
    .update({
      render_svg: svg,
      render_version: RENDER_VERSION,
    })
    .eq("id", pebbleId);

  if (updateError) {
    console.error("compose-and-write: render write-back failed:", updateError);
    throw new Error(`write-back failed: ${updateError.message}`);
  }

  return { render_svg: svg, render_version: RENDER_VERSION };
}
```

- [ ] **Step 2: Verify package builds**

Run:
```bash
npm run build --workspace=@pbbls/supabase
```
Expected: PASS. (If failures persist, they'll be in code outside `_shared/engine` and `_shared/compose-and-write.ts` that consumed manifest fields — search and clean up before proceeding.)

- [ ] **Step 3: Stage**

```bash
git add packages/supabase/supabase/functions/_shared/compose-and-write.ts
```

---

## Task 4 — Update edge-function comments and verify response shapes

The actual response payloads are produced by spreading `rendered` (the `ComposedRender`) into the JSON body — once Task 3 narrows `ComposedRender` to `{ render_svg, render_version }`, the edge functions automatically stop emitting `render_manifest`. The only thing to fix here are the docblock comments that still mention `render_manifest`.

**Files:**
- Modify: `packages/supabase/supabase/functions/compose-pebble/index.ts`
- Modify: `packages/supabase/supabase/functions/compose-pebble-update/index.ts`

- [ ] **Step 1: Update the response docblock in `compose-pebble/index.ts`**

In `packages/supabase/supabase/functions/compose-pebble/index.ts`, change the line:
```
 * 4. Responds with { pebble_id, render_svg, render_manifest, render_version }
```
to:
```
 * 4. Responds with { pebble_id, render_svg, render_version }
```

- [ ] **Step 2: Update the response docblock in `compose-pebble-update/index.ts`**

In `packages/supabase/supabase/functions/compose-pebble-update/index.ts`, change the line:
```
 * 4. Responds with { pebble_id, render_svg, render_manifest, render_version }
```
to:
```
 * 4. Responds with { pebble_id, render_svg, render_version }
```

- [ ] **Step 3: Stage and commit the engine + edge-function changes as one logical unit**

```bash
git add \
  packages/supabase/supabase/functions/_shared/engine/types.ts \
  packages/supabase/supabase/functions/_shared/engine/compose.ts \
  packages/supabase/supabase/functions/_shared/compose-and-write.ts \
  packages/supabase/supabase/functions/compose-pebble/index.ts \
  packages/supabase/supabase/functions/compose-pebble-update/index.ts
git commit -m "$(cat <<'EOF'
feat(api): drop render_manifest from pebble engine and edge functions

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5 — Drop `render_manifest` from web app

The web app's `Pebble` type and Supabase reads still know about `render_manifest`. Strip them so the type matches reality and the read query stops asking for a column that's about to disappear.

**Files:**
- Modify: `apps/web/lib/types.ts`
- Modify: `apps/web/lib/data/data-provider.ts`
- Modify: `apps/web/lib/data/supabase-provider.ts`
- Modify: `apps/web/lib/seed/seed-data.ts`

- [ ] **Step 1: Drop the field from the `Pebble` type**

In `apps/web/lib/types.ts`, remove the line:
```ts
  render_manifest: unknown | null
```
(found at line 33, between `render_svg` and `render_version`).

- [ ] **Step 2: Drop `render_manifest` from `ServerOwnedPebbleFields`**

In `apps/web/lib/data/data-provider.ts`, remove the `| "render_manifest"` line from the `ServerOwnedPebbleFields` union (around line 49). The union ends up as:
```ts
type ServerOwnedPebbleFields =
  | "id"
  | "created_at"
  | "updated_at"
  | "render_svg"
  | "render_version"
```

- [ ] **Step 3: Drop `render_manifest` from `supabase-provider.ts`**

In `apps/web/lib/data/supabase-provider.ts`:

3a. Update the comment at line 54 from:
```ts
    // The render columns (render_svg / render_manifest / render_version) are
```
to:
```ts
    // The render columns (render_svg / render_version) are
```

3b. Change the select at line 72 from:
```ts
        .select("id, render_svg, render_manifest, render_version")
```
to:
```ts
        .select("id, render_svg, render_version")
```

3c. Change the `renderById` map type at line 88-91 from:
```ts
    const renderById = new Map<
      string,
      { render_svg: string | null; render_manifest: unknown; render_version: string | null }
    >()
```
to:
```ts
    const renderById = new Map<
      string,
      { render_svg: string | null; render_version: string | null }
    >()
```

3d. Remove the `render_manifest: r.render_manifest ?? null,` line (≈line 96) from the `renderById.set(...)` body.

3e. Remove the `render_manifest: null,` line (≈line 105) from the fallback object.

3f. Remove the `render_manifest: render.render_manifest,` line (≈line 126) from the `Pebble` mapping.

- [ ] **Step 4: Drop `render_manifest` from seed-data omitted-keys union**

In `apps/web/lib/seed/seed-data.ts`, line 14 currently reads:
```ts
  "created_at" | "updated_at" | "render_svg" | "render_manifest" | "render_version"
```
Change it to:
```ts
  "created_at" | "updated_at" | "render_svg" | "render_version"
```

- [ ] **Step 5: Verify web build and lint**

Run from repo root:
```bash
npm run build --workspace=@pbbls/web
npm run lint --workspace=@pbbls/web
```
Expected: both PASS.

- [ ] **Step 6: Commit**

```bash
git add \
  apps/web/lib/types.ts \
  apps/web/lib/data/data-provider.ts \
  apps/web/lib/data/supabase-provider.ts \
  apps/web/lib/seed/seed-data.ts
git commit -m "$(cat <<'EOF'
chore(core): drop render_manifest from web pebble model and reads

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6 — Drop the `render_manifest` column on the remote DB

**Operational reminder:** local Supabase / Docker is not used. Migrations are pushed to the linked remote project.

**Files:**
- Create: `packages/supabase/supabase/migrations/20260429000000_drop_pebbles_render_manifest.sql`

- [ ] **Step 1: Create the migration file**

Write the file with exactly this content:

```sql
-- Drop the render_manifest column on pebbles.
--
-- Animation timing has moved off the server entirely: the iOS client owns
-- a versioned phase-timing table keyed by pebbles.render_version, and uses
-- the composed render_svg as the only source of stroke geometry. The
-- render_manifest column is no longer written or read by any client.

alter table public.pebbles
  drop column if exists render_manifest;
```

- [ ] **Step 2: Push the migration to the linked remote project**

```bash
npm run db:push --workspace=packages/supabase
```
Expected: the CLI shows the new migration applied to the remote project. If it prompts for confirmation, accept. If it fails because the remote is not linked, run `npm run db:link --workspace=packages/supabase` first (interactive — the user must run it themselves).

- [ ] **Step 3: Regenerate types from the linked remote**

```bash
cd packages/supabase && npx supabase gen types typescript --linked > types/database.ts && cd -
```
Expected: `types/database.ts` updates. The three `render_manifest` lines (≈ 551, 568, 585 in the file before this change) should now be gone.

If `--linked` fails for any reason, fall back to the Supabase MCP tool `generate_typescript_types` (provides the same content) and write the result to `packages/supabase/types/database.ts`.

- [ ] **Step 4: Verify the supabase package still builds**

```bash
npm run build --workspace=@pbbls/supabase
```
Expected: PASS.

- [ ] **Step 5: Commit migration + regenerated types**

```bash
git add \
  packages/supabase/supabase/migrations/20260429000000_drop_pebbles_render_manifest.sql \
  packages/supabase/types/database.ts
git commit -m "$(cat <<'EOF'
feat(db): drop render_manifest column from pebbles

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7 — iOS: clean up `ComposePebbleResponse`

Remove the placeholder comment about the manifest from the model. The decoder already ignores the field; no behavior change. Update the test fixture so it no longer pretends the field is meaningful.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/ComposePebbleResponse.swift`
- Modify: `apps/ios/PebblesTests/ComposePebbleResponseDecodingTests.swift`

- [ ] **Step 1: Update the model file**

Replace the contents of `apps/ios/Pebbles/Features/Path/Models/ComposePebbleResponse.swift` with:

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

    enum CodingKeys: String, CodingKey {
        case pebbleId = "pebble_id"
        case renderSvg = "render_svg"
        case renderVersion = "render_version"
    }
}
```

- [ ] **Step 2: Update the success-path test fixture**

In `apps/ios/PebblesTests/ComposePebbleResponseDecodingTests.swift`, replace the JSON in `decodesSuccess()` (lines 10–17) with the manifest-free payload:

```swift
        let json = Data("""
        {
          "pebble_id": "550e8400-e29b-41d4-a716-446655440000",
          "render_svg": "<svg xmlns=\\"http://www.w3.org/2000/svg\\"></svg>",
          "render_version": "0.1.0"
        }
        """.utf8)
```

Leave the soft-failure test (`decodesSoftFailure`) untouched.

- [ ] **Step 3: Commit**

```bash
git add \
  apps/ios/Pebbles/Features/Path/Models/ComposePebbleResponse.swift \
  apps/ios/PebblesTests/ComposePebbleResponseDecodingTests.swift
git commit -m "$(cat <<'EOF'
chore(ios): drop render_manifest references from ComposePebbleResponse

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8 — iOS: SVG path-`d` parser (TDD)

Build the smallest viable parser that turns an SVG `d` string into a `CGPath`. Supports `M m L l H h V v C c S s Q q T t A a Z z` with implicit-command continuation. Pure function, no dependencies.

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Render/SVGPathParser.swift`
- Test: `apps/ios/PebblesTests/SVGPathParserTests.swift`

- [ ] **Step 1: Write the failing test**

Create `apps/ios/PebblesTests/SVGPathParserTests.swift` with:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("SVGPathParser")
struct SVGPathParserTests {

    @Test("parses an absolute moveTo + lineTo")
    func absoluteLine() throws {
        let path = try #require(SVGPathParser.parse("M 0 0 L 100 100"))
        let bbox = path.boundingBoxOfPath
        #expect(abs(bbox.minX) < 0.001)
        #expect(abs(bbox.minY) < 0.001)
        #expect(abs(bbox.maxX - 100) < 0.001)
        #expect(abs(bbox.maxY - 100) < 0.001)
    }

    @Test("parses relative lineTo with implicit continuation")
    func relativeLineImplicit() throws {
        // M 10 10 l 5 5 5 5  → ends at (20, 20)
        let path = try #require(SVGPathParser.parse("M10,10l5,5 5,5"))
        #expect(!path.isEmpty)
        #expect(abs(path.boundingBoxOfPath.maxX - 20) < 0.001)
        #expect(abs(path.boundingBoxOfPath.maxY - 20) < 0.001)
    }

    @Test("parses absolute cubic bezier")
    func absoluteCubic() throws {
        let path = try #require(SVGPathParser.parse("M 0 0 C 50 0 50 100 100 100"))
        let bbox = path.boundingBoxOfPath
        #expect(bbox.minX >= -0.001 && bbox.minX <= 0.001)
        #expect(bbox.maxX <= 100.001 && bbox.maxX >= 99.999)
    }

    @Test("parses quadratic bezier")
    func quadratic() throws {
        let path = try #require(SVGPathParser.parse("M 0 0 Q 50 100 100 0"))
        #expect(!path.isEmpty)
    }

    @Test("parses elliptical arc")
    func arc() throws {
        let path = try #require(SVGPathParser.parse("M 0 0 A 50 50 0 0 1 100 0"))
        #expect(!path.isEmpty)
    }

    @Test("parses horizontal/vertical line shortcuts")
    func hAndV() throws {
        let path = try #require(SVGPathParser.parse("M 0 0 H 100 V 100 Z"))
        #expect(abs(path.boundingBoxOfPath.maxX - 100) < 0.001)
        #expect(abs(path.boundingBoxOfPath.maxY - 100) < 0.001)
    }

    @Test("returns nil on garbage input")
    func garbage() {
        #expect(SVGPathParser.parse("not a path") == nil)
        #expect(SVGPathParser.parse("") == nil)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails (no symbol yet)**

```bash
cd apps/ios && npm run generate && cd -
```
then build/test from Xcode or:
```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' test 2>&1 | tail -40
```
Expected: build fails — `SVGPathParser` is not defined.

- [ ] **Step 3: Implement the parser**

Create `apps/ios/Pebbles/Features/Path/Render/SVGPathParser.swift` with:

```swift
import CoreGraphics
import Foundation

/// Parses an SVG path `d` attribute string into a CGPath.
///
/// Supports the standard SVG path commands `M m L l H h V v C c S s Q q T t A a Z z`
/// with implicit-command continuation (e.g. `M 0 0 10 10 20 20` is an
/// implicit `L` after the first `M`). Whitespace, commas, and signed
/// numbers are accepted. Returns `nil` when the string contains no
/// recognizable command or cannot be tokenized.
enum SVGPathParser {

    static func parse(_ d: String) -> CGPath? {
        let tokens = tokenize(d)
        guard !tokens.isEmpty else { return nil }

        let path = CGMutablePath()

        var current = CGPoint.zero
        var subpathStart = CGPoint.zero
        var lastControl: CGPoint? = nil   // for S/T smoothing
        var lastCommand: Character = "M"

        var i = 0
        while i < tokens.count {
            guard case let .command(cmdChar) = tokens[i] else { return nil }
            i += 1

            // Pull all numeric tokens after this command.
            var args: [CGFloat] = []
            while i < tokens.count, case let .number(value) = tokens[i] {
                args.append(value)
                i += 1
            }

            // Step through args repeating the command as needed (implicit continuation).
            var argIndex = 0
            var firstIteration = true

            repeat {
                let activeCmd: Character
                if firstIteration {
                    activeCmd = cmdChar
                    firstIteration = false
                } else {
                    // Implicit M continues as L; m continues as l. Others repeat themselves.
                    switch cmdChar {
                    case "M": activeCmd = "L"
                    case "m": activeCmd = "l"
                    default: activeCmd = cmdChar
                    }
                }

                switch activeCmd {
                case "M":
                    guard argIndex + 1 < args.count else { return nil }
                    current = CGPoint(x: args[argIndex], y: args[argIndex + 1])
                    subpathStart = current
                    path.move(to: current)
                    argIndex += 2
                case "m":
                    guard argIndex + 1 < args.count else { return nil }
                    current = CGPoint(x: current.x + args[argIndex], y: current.y + args[argIndex + 1])
                    subpathStart = current
                    path.move(to: current)
                    argIndex += 2
                case "L":
                    guard argIndex + 1 < args.count else { return nil }
                    current = CGPoint(x: args[argIndex], y: args[argIndex + 1])
                    path.addLine(to: current)
                    argIndex += 2
                case "l":
                    guard argIndex + 1 < args.count else { return nil }
                    current = CGPoint(x: current.x + args[argIndex], y: current.y + args[argIndex + 1])
                    path.addLine(to: current)
                    argIndex += 2
                case "H":
                    guard argIndex < args.count else { return nil }
                    current = CGPoint(x: args[argIndex], y: current.y)
                    path.addLine(to: current)
                    argIndex += 1
                case "h":
                    guard argIndex < args.count else { return nil }
                    current = CGPoint(x: current.x + args[argIndex], y: current.y)
                    path.addLine(to: current)
                    argIndex += 1
                case "V":
                    guard argIndex < args.count else { return nil }
                    current = CGPoint(x: current.x, y: args[argIndex])
                    path.addLine(to: current)
                    argIndex += 1
                case "v":
                    guard argIndex < args.count else { return nil }
                    current = CGPoint(x: current.x, y: current.y + args[argIndex])
                    path.addLine(to: current)
                    argIndex += 1
                case "C":
                    guard argIndex + 5 < args.count else { return nil }
                    let c1 = CGPoint(x: args[argIndex],     y: args[argIndex + 1])
                    let c2 = CGPoint(x: args[argIndex + 2], y: args[argIndex + 3])
                    let end = CGPoint(x: args[argIndex + 4], y: args[argIndex + 5])
                    path.addCurve(to: end, control1: c1, control2: c2)
                    lastControl = c2
                    current = end
                    argIndex += 6
                case "c":
                    guard argIndex + 5 < args.count else { return nil }
                    let c1 = CGPoint(x: current.x + args[argIndex],     y: current.y + args[argIndex + 1])
                    let c2 = CGPoint(x: current.x + args[argIndex + 2], y: current.y + args[argIndex + 3])
                    let end = CGPoint(x: current.x + args[argIndex + 4], y: current.y + args[argIndex + 5])
                    path.addCurve(to: end, control1: c1, control2: c2)
                    lastControl = c2
                    current = end
                    argIndex += 6
                case "S":
                    guard argIndex + 3 < args.count else { return nil }
                    let c1 = reflectedControl(current: current, lastControl: lastControl, lastCommand: lastCommand, isCubicLike: true)
                    let c2 = CGPoint(x: args[argIndex],     y: args[argIndex + 1])
                    let end = CGPoint(x: args[argIndex + 2], y: args[argIndex + 3])
                    path.addCurve(to: end, control1: c1, control2: c2)
                    lastControl = c2
                    current = end
                    argIndex += 4
                case "s":
                    guard argIndex + 3 < args.count else { return nil }
                    let c1 = reflectedControl(current: current, lastControl: lastControl, lastCommand: lastCommand, isCubicLike: true)
                    let c2 = CGPoint(x: current.x + args[argIndex],     y: current.y + args[argIndex + 1])
                    let end = CGPoint(x: current.x + args[argIndex + 2], y: current.y + args[argIndex + 3])
                    path.addCurve(to: end, control1: c1, control2: c2)
                    lastControl = c2
                    current = end
                    argIndex += 4
                case "Q":
                    guard argIndex + 3 < args.count else { return nil }
                    let c = CGPoint(x: args[argIndex],     y: args[argIndex + 1])
                    let end = CGPoint(x: args[argIndex + 2], y: args[argIndex + 3])
                    path.addQuadCurve(to: end, control: c)
                    lastControl = c
                    current = end
                    argIndex += 4
                case "q":
                    guard argIndex + 3 < args.count else { return nil }
                    let c = CGPoint(x: current.x + args[argIndex],     y: current.y + args[argIndex + 1])
                    let end = CGPoint(x: current.x + args[argIndex + 2], y: current.y + args[argIndex + 3])
                    path.addQuadCurve(to: end, control: c)
                    lastControl = c
                    current = end
                    argIndex += 4
                case "T":
                    guard argIndex + 1 < args.count else { return nil }
                    let c = reflectedControl(current: current, lastControl: lastControl, lastCommand: lastCommand, isCubicLike: false)
                    let end = CGPoint(x: args[argIndex], y: args[argIndex + 1])
                    path.addQuadCurve(to: end, control: c)
                    lastControl = c
                    current = end
                    argIndex += 2
                case "t":
                    guard argIndex + 1 < args.count else { return nil }
                    let c = reflectedControl(current: current, lastControl: lastControl, lastCommand: lastCommand, isCubicLike: false)
                    let end = CGPoint(x: current.x + args[argIndex], y: current.y + args[argIndex + 1])
                    path.addQuadCurve(to: end, control: c)
                    lastControl = c
                    current = end
                    argIndex += 2
                case "A":
                    guard argIndex + 6 < args.count else { return nil }
                    let rx = args[argIndex]
                    let ry = args[argIndex + 1]
                    let xAxisRot = args[argIndex + 2]
                    let largeArc = args[argIndex + 3] != 0
                    let sweep = args[argIndex + 4] != 0
                    let end = CGPoint(x: args[argIndex + 5], y: args[argIndex + 6])
                    addArc(to: path, from: current, to: end, rx: rx, ry: ry, xAxisRotationDeg: xAxisRot, largeArc: largeArc, sweep: sweep)
                    current = end
                    lastControl = nil
                    argIndex += 7
                case "a":
                    guard argIndex + 6 < args.count else { return nil }
                    let rx = args[argIndex]
                    let ry = args[argIndex + 1]
                    let xAxisRot = args[argIndex + 2]
                    let largeArc = args[argIndex + 3] != 0
                    let sweep = args[argIndex + 4] != 0
                    let end = CGPoint(x: current.x + args[argIndex + 5], y: current.y + args[argIndex + 6])
                    addArc(to: path, from: current, to: end, rx: rx, ry: ry, xAxisRotationDeg: xAxisRot, largeArc: largeArc, sweep: sweep)
                    current = end
                    lastControl = nil
                    argIndex += 7
                case "Z", "z":
                    path.closeSubpath()
                    current = subpathStart
                    lastControl = nil
                default:
                    return nil
                }

                lastCommand = activeCmd

                // Z/z take no arguments; bail out of the inner loop.
                if activeCmd == "Z" || activeCmd == "z" { break }
            } while argIndex < args.count
        }

        return path.copy()
    }

    // MARK: - Tokenizer

    private enum Token { case command(Character), number(CGFloat) }

    private static func tokenize(_ s: String) -> [Token] {
        var tokens: [Token] = []
        let scalars = Array(s.unicodeScalars)
        var i = 0
        while i < scalars.count {
            let c = scalars[i]
            if isCommandChar(c) {
                tokens.append(.command(Character(c)))
                i += 1
            } else if c == " " || c == "\t" || c == "\n" || c == "\r" || c == "," {
                i += 1
            } else if c == "+" || c == "-" || c == "." || (c.value >= 48 && c.value <= 57) {
                // Number — read until the next non-numeric character.
                let start = i
                if c == "+" || c == "-" { i += 1 }
                var seenDot = false
                var seenExp = false
                while i < scalars.count {
                    let ch = scalars[i]
                    if ch.value >= 48 && ch.value <= 57 {
                        i += 1
                    } else if ch == "." && !seenDot && !seenExp {
                        seenDot = true; i += 1
                    } else if (ch == "e" || ch == "E") && !seenExp {
                        seenExp = true; i += 1
                        if i < scalars.count, scalars[i] == "+" || scalars[i] == "-" { i += 1 }
                    } else {
                        break
                    }
                }
                let str = String(String.UnicodeScalarView(scalars[start..<i]))
                if let n = Double(str) {
                    tokens.append(.number(CGFloat(n)))
                } else {
                    return []
                }
            } else {
                // Unknown character — abort.
                return []
            }
        }
        return tokens
    }

    private static func isCommandChar(_ c: Unicode.Scalar) -> Bool {
        switch Character(c) {
        case "M", "m", "L", "l", "H", "h", "V", "v",
             "C", "c", "S", "s", "Q", "q", "T", "t",
             "A", "a", "Z", "z":
            return true
        default:
            return false
        }
    }

    // MARK: - Smooth-curve control reflection

    private static func reflectedControl(
        current: CGPoint,
        lastControl: CGPoint?,
        lastCommand: Character,
        isCubicLike: Bool
    ) -> CGPoint {
        // S/s reflects only after C/c/S/s; T/t reflects only after Q/q/T/t.
        let qualifies: Bool
        if isCubicLike {
            qualifies = "CcSs".contains(lastCommand)
        } else {
            qualifies = "QqTt".contains(lastCommand)
        }
        guard qualifies, let last = lastControl else { return current }
        return CGPoint(x: 2 * current.x - last.x, y: 2 * current.y - last.y)
    }

    // MARK: - Arc

    /// Adds an SVG-style elliptical arc to the path. Implements the endpoint-to-center
    /// parameterization from the SVG 1.1 spec, then emits the arc as a CGPath arc.
    private static func addArc(
        to path: CGMutablePath,
        from p0: CGPoint,
        to p1: CGPoint,
        rx rxIn: CGFloat,
        ry ryIn: CGFloat,
        xAxisRotationDeg: CGFloat,
        largeArc: Bool,
        sweep: Bool
    ) {
        // Same point → no arc.
        if abs(p0.x - p1.x) < 1e-6 && abs(p0.y - p1.y) < 1e-6 { return }

        // Zero radius → straight line per spec.
        if rxIn == 0 || ryIn == 0 {
            path.addLine(to: p1)
            return
        }

        let phi = xAxisRotationDeg * .pi / 180
        var rx = abs(rxIn)
        var ry = abs(ryIn)

        // F.6.5 step 1
        let dx2 = (p0.x - p1.x) / 2
        let dy2 = (p0.y - p1.y) / 2
        let cosPhi = cos(phi)
        let sinPhi = sin(phi)
        let x1p =  cosPhi * dx2 + sinPhi * dy2
        let y1p = -sinPhi * dx2 + cosPhi * dy2

        // F.6.6 — ensure radii large enough.
        let lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
        if lambda > 1 {
            let s = sqrt(lambda)
            rx *= s
            ry *= s
        }

        // F.6.5 step 2 — center in primed coords.
        let signFactor: CGFloat = largeArc == sweep ? -1 : 1
        let num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
        let denom = rx * rx * y1p * y1p + ry * ry * x1p * x1p
        let coeff = signFactor * sqrt(max(0, num / denom))
        let cxp =  coeff * (rx * y1p / ry)
        let cyp = -coeff * (ry * x1p / rx)

        // Center back in user coords.
        let cx = cosPhi * cxp - sinPhi * cyp + (p0.x + p1.x) / 2
        let cy = sinPhi * cxp + cosPhi * cyp + (p0.y + p1.y) / 2

        // Start/end angles.
        func ang(_ ux: CGFloat, _ uy: CGFloat, _ vx: CGFloat, _ vy: CGFloat) -> CGFloat {
            let dot = ux * vx + uy * vy
            let len = sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy)
            var v = dot / len
            v = max(-1, min(1, v))
            let s = ux * vy - uy * vx
            return (s < 0 ? -1 : 1) * acos(v)
        }

        let theta1 = ang(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
        var deltaTheta = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
        if !sweep && deltaTheta > 0 { deltaTheta -= 2 * .pi }
        if  sweep && deltaTheta < 0 { deltaTheta += 2 * .pi }

        // Build a transform that places a unit circle at the ellipse center,
        // scales it to (rx, ry), and rotates by phi.
        let t = CGAffineTransform(translationX: cx, y: cy)
            .rotated(by: phi)
            .scaledBy(x: rx, y: ry)

        // Add the arc on the unit circle through the transform.
        path.addArc(
            center: .zero,
            radius: 1,
            startAngle: theta1,
            endAngle: theta1 + deltaTheta,
            clockwise: !sweep,
            transform: t
        )
    }
}

private extension CGPath {
    var isEmpty: Bool { boundingBoxOfPath.isNull || boundingBoxOfPath.isEmpty }
}
```

- [ ] **Step 4: Regenerate Xcode project so new files are included**

```bash
npm run generate --workspace=@pbbls/ios
```
Expected: PASS — `apps/ios/Pebbles.xcodeproj/project.pbxproj` is rewritten to include the new files.

- [ ] **Step 5: Run tests**

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' test 2>&1 | tail -40
```
Expected: PASS — all `SVGPathParser` tests succeed.

- [ ] **Step 6: Commit**

```bash
git add \
  apps/ios/Pebbles/Features/Path/Render/SVGPathParser.swift \
  apps/ios/PebblesTests/SVGPathParserTests.swift
git commit -m "$(cat <<'EOF'
feat(ios): SVG path-d parser for native pebble rendering

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9 — iOS: typed `PebbleSVGModel` parsed via XMLParser (TDD)

Walk a composed pebble SVG into a typed layered model. Recognizes the engine-emitted layer ids `layer:shape`, `layer:fossil`, `layer:glyph`. Reads `transform="translate(x, y) scale(s)"` (with optional comma) and `opacity="..."`. Concatenates all `<path d=…>` descendants of a layer into one combined `CGPath` so the layer animates as one trim. Fails closed → `init?` returns `nil` if anything looks off.

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Render/PebbleSVGModel.swift`
- Test: `apps/ios/PebblesTests/PebbleSVGModelTests.swift`

- [ ] **Step 1: Write the failing test**

Create `apps/ios/PebblesTests/PebbleSVGModelTests.swift` with:

```swift
import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

@Suite("PebbleSVGModel")
struct PebbleSVGModelTests {

    private let composedSvg = """
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 240" width="240" height="240">
      <g id="layer:shape">
        <path id="shape:stroke-0" d="M 10 10 L 230 10 L 230 230 L 10 230 Z" fill="none" stroke="currentColor"/>
      </g>
      <g id="layer:fossil" opacity="0.3">
        <path id="fossil:stroke-0" d="M 50 50 L 190 190" fill="none" stroke="currentColor"/>
      </g>
      <g id="layer:glyph" transform="translate(40, 40) scale(0.8)">
        <path id="glyph:stroke-0" d="M 0 0 L 200 200" fill="none" stroke="currentColor"/>
      </g>
    </svg>
    """

    @Test("parses viewBox, layer order, and transforms")
    func happy() throws {
        let model = try #require(PebbleSVGModel(svg: composedSvg))
        #expect(model.viewBox == CGRect(x: 0, y: 0, width: 240, height: 240))
        #expect(model.layers.map(\.kind) == [.shape, .fossil, .glyph])
        #expect(abs(model.layers[1].opacity - 0.3) < 1e-6)
        #expect(model.layers[0].opacity == 1.0)
        let glyphTransform = model.layers[2].transform
        // translate(40, 40) scale(0.8) → tx=40, ty=40, a=0.8, d=0.8
        #expect(abs(glyphTransform.a - 0.8) < 1e-6)
        #expect(abs(glyphTransform.d - 0.8) < 1e-6)
        #expect(abs(glyphTransform.tx - 40) < 1e-6)
        #expect(abs(glyphTransform.ty - 40) < 1e-6)
        for layer in model.layers {
            #expect(!layer.combinedPath.boundingBoxOfPath.isNull)
        }
    }

    @Test("handles fossil-less svg")
    func noFossil() throws {
        let svg = """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="100" height="100">
          <g id="layer:shape"><path d="M 0 0 L 100 100" fill="none"/></g>
          <g id="layer:glyph" transform="translate(0, 0) scale(1)"><path d="M 0 0 L 50 50" fill="none"/></g>
        </svg>
        """
        let model = try #require(PebbleSVGModel(svg: svg))
        #expect(model.layers.map(\.kind) == [.shape, .glyph])
    }

    @Test("returns nil when viewBox is missing")
    func missingViewBox() {
        let svg = """
        <svg xmlns="http://www.w3.org/2000/svg">
          <g id="layer:shape"><path d="M 0 0 L 100 100"/></g>
        </svg>
        """
        #expect(PebbleSVGModel(svg: svg) == nil)
    }

    @Test("returns nil when no recognized layer is present")
    func noLayers() {
        let svg = """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
          <g id="some:other"><path d="M 0 0 L 1 1"/></g>
        </svg>
        """
        #expect(PebbleSVGModel(svg: svg) == nil)
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' test 2>&1 | tail -40
```
Expected: build error — `PebbleSVGModel` is undefined.

- [ ] **Step 3: Implement the model**

Create `apps/ios/Pebbles/Features/Path/Render/PebbleSVGModel.swift` with:

```swift
import CoreGraphics
import Foundation
import os

/// Typed view of a composed pebble SVG: viewBox + ordered layers, each carrying
/// its `transform` chain, opacity, and a single combined `CGPath` of all the
/// descendant `<path d="…">` strings concatenated together.
///
/// Built once on first appearance of `PebbleAnimatedRenderView`. Failure
/// (`init?` returning nil) means the caller falls back to the existing
/// `SVGView`-based static renderer.
struct PebbleSVGModel {
    let viewBox: CGRect
    let layers: [Layer]

    struct Layer {
        enum Kind { case shape, fossil, glyph }
        let kind: Kind
        /// Transform inherited from the layer's `<g transform="...">`. Identity if none.
        let transform: CGAffineTransform
        /// Layer opacity from `<g opacity="...">`. 1.0 if absent.
        let opacity: Double
        /// All descendant `<path>` `d` strings parsed and concatenated into one path.
        let combinedPath: CGPath
    }

    init?(svg: String) {
        guard let data = svg.data(using: .utf8) else { return nil }
        let parser = XMLParser(data: data)
        let delegate = ParserDelegate()
        parser.delegate = delegate
        guard parser.parse(), let viewBox = delegate.viewBox else {
            Logger(subsystem: "app.pbbls.ios", category: "pebble-svg")
                .info("PebbleSVGModel parse failed — falling back to static render")
            return nil
        }

        var layers: [Layer] = []
        for raw in delegate.rawLayers {
            guard let kind = raw.kind else { continue }
            let combined = CGMutablePath()
            for d in raw.pathDStrings {
                guard let p = SVGPathParser.parse(d) else { continue }
                combined.addPath(p)
            }
            // Reject layers with no parseable path so we don't render an empty trim.
            guard !combined.boundingBoxOfPath.isNull else { continue }

            layers.append(Layer(
                kind: kind,
                transform: raw.transform,
                opacity: raw.opacity,
                combinedPath: combined.copy() ?? combined
            ))
        }

        guard !layers.isEmpty else { return nil }
        self.viewBox = viewBox
        self.layers = layers
    }

    // MARK: - XMLParser delegate

    private final class ParserDelegate: NSObject, XMLParserDelegate {
        var viewBox: CGRect?
        var rawLayers: [RawLayer] = []
        private var stack: [RawLayer] = []

        struct RawLayer {
            var kind: Layer.Kind?
            var transform: CGAffineTransform = .identity
            var opacity: Double = 1.0
            var pathDStrings: [String] = []
        }

        func parser(
            _ parser: XMLParser,
            didStartElement elementName: String,
            namespaceURI: String?,
            qualifiedName qName: String?,
            attributes attributeDict: [String: String] = [:]
        ) {
            switch elementName {
            case "svg":
                if let vb = attributeDict["viewBox"] {
                    let parts = vb.split(whereSeparator: { $0 == " " || $0 == "," }).compactMap { Double($0) }
                    if parts.count == 4 {
                        viewBox = CGRect(x: parts[0], y: parts[1], width: parts[2], height: parts[3])
                    }
                }
            case "g":
                var layer = RawLayer()
                if let id = attributeDict["id"] {
                    switch id {
                    case "layer:shape":  layer.kind = .shape
                    case "layer:fossil": layer.kind = .fossil
                    case "layer:glyph":  layer.kind = .glyph
                    default: break
                    }
                }
                if let t = attributeDict["transform"] {
                    layer.transform = parseTransform(t)
                }
                if let o = attributeDict["opacity"], let v = Double(o) {
                    layer.opacity = v
                }
                stack.append(layer)
            case "path":
                if let d = attributeDict["d"], !stack.isEmpty {
                    stack[stack.count - 1].pathDStrings.append(d)
                }
            default:
                break
            }
        }

        func parser(
            _ parser: XMLParser,
            didEndElement elementName: String,
            namespaceURI: String?,
            qualifiedName qName: String?
        ) {
            if elementName == "g", let layer = stack.popLast(), layer.kind != nil {
                rawLayers.append(layer)
            }
        }

        /// Parses the limited transform forms emitted by the engine:
        /// `translate(x, y) scale(s)` (commas optional), `translate(x, y)`,
        /// `scale(s)`. Other forms collapse to identity.
        private func parseTransform(_ s: String) -> CGAffineTransform {
            var t = CGAffineTransform.identity
            let pattern = #"(translate|scale)\s*\(([^)]*)\)"#
            guard let regex = try? NSRegularExpression(pattern: pattern) else { return .identity }
            let range = NSRange(s.startIndex..<s.endIndex, in: s)
            regex.enumerateMatches(in: s, range: range) { match, _, _ in
                guard
                    let match,
                    let nameRange = Range(match.range(at: 1), in: s),
                    let argsRange = Range(match.range(at: 2), in: s)
                else { return }
                let name = String(s[nameRange])
                let args = String(s[argsRange])
                    .split(whereSeparator: { $0 == "," || $0 == " " })
                    .compactMap { Double($0) }
                switch name {
                case "translate":
                    let tx = args.first ?? 0
                    let ty = args.count > 1 ? args[1] : 0
                    t = t.concatenating(CGAffineTransform(translationX: tx, y: ty))
                case "scale":
                    let sx = args.first ?? 1
                    let sy = args.count > 1 ? args[1] : sx
                    t = t.concatenating(CGAffineTransform(scaleX: sx, y: sy))
                default:
                    break
                }
            }
            return t
        }
    }
}
```

- [ ] **Step 4: Regenerate the Xcode project**

```bash
npm run generate --workspace=@pbbls/ios
```

- [ ] **Step 5: Run tests**

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' test 2>&1 | tail -40
```
Expected: PASS for both `SVGPathParserTests` and `PebbleSVGModelTests`.

- [ ] **Step 6: Commit**

```bash
git add \
  apps/ios/Pebbles/Features/Path/Render/PebbleSVGModel.swift \
  apps/ios/PebblesTests/PebbleSVGModelTests.swift
git commit -m "$(cat <<'EOF'
feat(ios): typed PebbleSVGModel for the pebble renderer

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 10 — iOS: `PebbleAnimationTimings` (TDD)

Versioned phase-timings table keyed by `render_version`. Unknown versions return `nil`, which causes the renderer to fall back to static.

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Render/PebbleAnimationTimings.swift`
- Test: `apps/ios/PebblesTests/PebbleAnimationTimingsTests.swift`

- [ ] **Step 1: Write the failing test**

Create `apps/ios/PebblesTests/PebbleAnimationTimingsTests.swift` with:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("PebbleAnimationTimings")
struct PebbleAnimationTimingsTests {
    @Test("returns timings for known version 0.1.0")
    func known() throws {
        let timings = try #require(PebbleAnimationTimings.forVersion("0.1.0"))
        #expect(timings.glyph.delay == 0)
        #expect(timings.glyph.duration == 0.8)
        #expect(timings.shape.delay == 0.4)
        #expect(timings.shape.duration == 0.8)
        #expect(timings.fossil.delay == 0.8)
        #expect(timings.fossil.duration == 0.6)
        #expect(timings.settle.delay == 1.2)
        #expect(timings.settle.duration == 0.4)
    }

    @Test("returns nil for unknown version")
    func unknown() {
        #expect(PebbleAnimationTimings.forVersion("9.9.9") == nil)
        #expect(PebbleAnimationTimings.forVersion(nil) == nil)
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' test 2>&1 | tail -40
```
Expected: build error — `PebbleAnimationTimings` is undefined.

- [ ] **Step 3: Implement the timings module**

Create `apps/ios/Pebbles/Features/Path/Render/PebbleAnimationTimings.swift` with:

```swift
import Foundation

/// Phase timings for the pebble stroke-drawing animation. Keyed by
/// `pebbles.render_version` so a server engine bump can shift the curve
/// without breaking older pebbles.
///
/// All values are in seconds. Returning `nil` from `forVersion` instructs
/// the caller to render the static settled state with no animation.
enum PebbleAnimationTimings {

    struct Phase {
        let delay: Double
        let duration: Double
    }

    struct Timings {
        let glyph: Phase
        let shape: Phase
        let fossil: Phase
        let settle: Phase
    }

    /// Returns timings for the given render version, or `nil` if unknown.
    static func forVersion(_ version: String?) -> Timings? {
        guard let version else { return nil }
        switch version {
        case "0.1.0":
            return Timings(
                glyph:  Phase(delay: 0,    duration: 0.8),
                shape:  Phase(delay: 0.4,  duration: 0.8),
                fossil: Phase(delay: 0.8,  duration: 0.6),
                settle: Phase(delay: 1.2,  duration: 0.4)
            )
        default:
            return nil
        }
    }
}
```

- [ ] **Step 4: Regenerate the Xcode project and run tests**

```bash
npm run generate --workspace=@pbbls/ios
xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' test 2>&1 | tail -40
```
Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add \
  apps/ios/Pebbles/Features/Path/Render/PebbleAnimationTimings.swift \
  apps/ios/PebblesTests/PebbleAnimationTimingsTests.swift
git commit -m "$(cat <<'EOF'
feat(ios): version-keyed timings table for pebble animation

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 11 — iOS: `PebbleAnimatedRenderView`

The animated SwiftUI view used by `PebbleReadBanner`. Parses once on first appearance, animates per-layer trim driven by the timings table, applies a small settle pulse, falls back to the existing `PebbleRenderView` (SVGView) when parsing fails / version is unknown / Reduce Motion is on.

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Render/PebbleAnimatedRenderView.swift`

- [ ] **Step 1: Implement the view**

Create `apps/ios/Pebbles/Features/Path/Render/PebbleAnimatedRenderView.swift` with:

```swift
import SwiftUI
import os

/// Animated counterpart to `PebbleRenderView` used by the pebble read sheet.
///
/// On first appearance the composed SVG is parsed into a `PebbleSVGModel`.
/// If parsing fails, no timings are registered for `renderVersion`, or the
/// system has Reduce Motion enabled, the view falls back to the static
/// `PebbleRenderView` (SVGView). Otherwise it renders each parsed layer as
/// a `Shape` with `.trim(from: 0, to: progress)`, animating progress 0 → 1
/// per phase, then a brief scale pulse for the settle beat.
///
/// The animation replays each time the view appears.
struct PebbleAnimatedRenderView: View {
    let svg: String
    let strokeColor: String
    let renderVersion: String?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var model: PebbleSVGModel?
    @State private var glyphProgress: Double = 0
    @State private var shapeProgress: Double = 0
    @State private var fossilProgress: Double = 0
    @State private var settleScale: Double = 1

    var body: some View {
        Group {
            if let model, let timings = PebbleAnimationTimings.forVersion(renderVersion), !reduceMotion {
                animatedBody(model: model, timings: timings)
            } else {
                PebbleRenderView(svg: svg, strokeColor: strokeColor)
            }
        }
        .onAppear {
            if model == nil {
                model = PebbleSVGModel(svg: svg)
                if model == nil {
                    Logger(subsystem: "app.pbbls.ios", category: "pebble-render")
                        .info("PebbleAnimatedRenderView: parse failed; using SVGView fallback")
                }
            }
            startAnimation()
        }
        .onDisappear { resetProgress() }
    }

    // MARK: - Animated rendering

    @ViewBuilder
    private func animatedBody(model: PebbleSVGModel, timings: PebbleAnimationTimings.Timings) -> some View {
        ZStack {
            ForEach(Array(model.layers.enumerated()), id: \.offset) { _, layer in
                LayerShape(layer: layer, viewBox: model.viewBox)
                    .trim(from: 0, to: progress(for: layer.kind))
                    .stroke(stroke, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                    .opacity(layer.opacity)
            }
        }
        .scaleEffect(settleScale)
        .accessibilityHidden(true)
    }

    private var stroke: Color { Color(hex: strokeColor) ?? Color.pebblesAccent }

    private func progress(for kind: PebbleSVGModel.Layer.Kind) -> Double {
        switch kind {
        case .glyph:  return glyphProgress
        case .shape:  return shapeProgress
        case .fossil: return fossilProgress
        }
    }

    private func resetProgress() {
        glyphProgress = 0
        shapeProgress = 0
        fossilProgress = 0
        settleScale = 1
    }

    private func startAnimation() {
        resetProgress()
        guard let timings = PebbleAnimationTimings.forVersion(renderVersion), !reduceMotion else {
            return
        }
        withAnimation(.easeOut(duration: timings.glyph.duration).delay(timings.glyph.delay)) {
            glyphProgress = 1
        }
        withAnimation(.easeOut(duration: timings.shape.duration).delay(timings.shape.delay)) {
            shapeProgress = 1
        }
        withAnimation(.easeOut(duration: timings.fossil.duration).delay(timings.fossil.delay)) {
            fossilProgress = 1
        }
        // Settle pulse: 1.0 → 1.04 → 1.0 over the settle phase duration.
        let halfSettle = timings.settle.duration / 2
        withAnimation(.easeInOut(duration: halfSettle).delay(timings.settle.delay)) {
            settleScale = 1.04
        }
        withAnimation(.easeInOut(duration: halfSettle).delay(timings.settle.delay + halfSettle)) {
            settleScale = 1
        }
    }
}

// MARK: - Layer shape

private struct LayerShape: Shape {
    let layer: PebbleSVGModel.Layer
    let viewBox: CGRect

    func path(in rect: CGRect) -> Path {
        // Combine the layer's SVG-space transform with the viewBox→rect fit
        // so the resulting path draws at the right size and position inside
        // the Shape's drawing rect.
        let scale = min(rect.width / viewBox.width, rect.height / viewBox.height)
        let scaledWidth = viewBox.width * scale
        let scaledHeight = viewBox.height * scale
        let dx = (rect.width - scaledWidth) / 2 - viewBox.minX * scale
        let dy = (rect.height - scaledHeight) / 2 - viewBox.minY * scale

        var t = layer.transform
            .concatenating(CGAffineTransform(scaleX: scale, y: scale))
            .concatenating(CGAffineTransform(translationX: dx, y: dy))
        guard let transformed = layer.combinedPath.copy(using: &t) else {
            return Path(layer.combinedPath)
        }
        return Path(transformed)
    }
}

#Preview("Animated · with fossil") {
    PebbleAnimatedRenderView(
        svg: """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 240" width="240" height="240">
          <g id="layer:shape">
            <path d="M 20 120 C 20 60 60 20 120 20 C 180 20 220 60 220 120 C 220 180 180 220 120 220 C 60 220 20 180 20 120 Z" fill="none"/>
          </g>
          <g id="layer:fossil" opacity="0.3">
            <path d="M 60 60 L 180 180 M 60 180 L 180 60" fill="none"/>
          </g>
          <g id="layer:glyph" transform="translate(70, 70) scale(0.5)">
            <path d="M 0 0 L 200 200 M 0 200 L 200 0" fill="none"/>
          </g>
        </svg>
        """,
        strokeColor: "#7C5CFA",
        renderVersion: "0.1.0"
    )
    .frame(width: 200, height: 200)
    .padding()
    .background(Color.pebblesBackground)
}

#Preview("Reduce-motion fallback") {
    PebbleAnimatedRenderView(
        svg: """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
          <g id="layer:shape"><path d="M 0 0 L 100 100" fill="none"/></g>
        </svg>
        """,
        strokeColor: "#7C5CFA",
        renderVersion: "0.1.0"
    )
    .frame(width: 200, height: 200)
    .environment(\.accessibilityReduceMotion, true)
}
```

- [ ] **Step 2: Regenerate Xcode project**

```bash
npm run generate --workspace=@pbbls/ios
```

- [ ] **Step 3: Build and run existing tests to verify nothing regressed**

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' test 2>&1 | tail -40
```
Expected: PASS — no new tests, but the code must compile and existing tests stay green.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Render/PebbleAnimatedRenderView.swift
git commit -m "$(cat <<'EOF'
feat(ios): native animated pebble renderer with reduce-motion fallback

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 12 — iOS: plumb `renderVersion` into `PebbleReadBanner` and `PebbleReadView`

Add a `renderVersion: String?` parameter to `PebbleReadBanner`, swap `PebbleRenderView` for `PebbleAnimatedRenderView` in both photo and no-photo branches, and pass `detail.renderVersion` from `PebbleReadView`.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Read/PebbleReadView.swift`

- [ ] **Step 1: Update `PebbleReadBanner` to accept `renderVersion` and use the animated renderer**

Open `apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift` and:

1a. Add the new parameter just below `renderSvg`:

```swift
    let snapStoragePath: String?
    let renderSvg: String?
    let renderVersion: String?
    let emotionColorHex: String
    let valence: Valence
```

1b. In `renderedPebble`, replace the `PebbleRenderView(...)` call with `PebbleAnimatedRenderView(...)`:

```swift
    @ViewBuilder
    private var renderedPebble: some View {
        if let renderSvg {
            PebbleAnimatedRenderView(
                svg: renderSvg,
                strokeColor: emotionColorHex,
                renderVersion: renderVersion
            )
            .frame(height: pebbleHeight)
        } else {
            EmptyView()
        }
    }
```

1c. Update both `#Preview` blocks to add `renderVersion: "0.1.0"`:

```swift
#Preview("With photo · medium") {
    PebbleReadBanner(
        snapStoragePath: nil,
        renderSvg: """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <circle cx="50" cy="50" r="40" fill="none" stroke="currentColor" stroke-width="3"/>
            </svg>
            """,
        renderVersion: "0.1.0",
        emotionColorHex: "#7C5CFA",
        valence: .neutralMedium
    )
    .padding()
    .background(Color.pebblesBackground)
}

#Preview("Without photo · large") {
    PebbleReadBanner(
        snapStoragePath: nil,
        renderSvg: """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <circle cx="50" cy="50" r="40" fill="none" stroke="currentColor" stroke-width="3"/>
            </svg>
            """,
        renderVersion: "0.1.0",
        emotionColorHex: "#7C5CFA",
        valence: .highlightLarge
    )
    .padding()
    .background(Color.pebblesBackground)
}
```

- [ ] **Step 2: Update `PebbleReadView` to pass through `detail.renderVersion`**

In `apps/ios/Pebbles/Features/Path/Read/PebbleReadView.swift`, change the `PebbleReadBanner(...)` call inside `body` to add `renderVersion: detail.renderVersion`:

```swift
                PebbleReadBanner(
                    snapStoragePath: detail.snaps.first?.storagePath,
                    renderSvg: detail.renderSvg,
                    renderVersion: detail.renderVersion,
                    emotionColorHex: detail.emotion.color,
                    valence: detail.valence
                )
```

- [ ] **Step 3: Regenerate the Xcode project, build, and run tests**

```bash
npm run generate --workspace=@pbbls/ios
xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' test 2>&1 | tail -40
```
Expected: PASS.

- [ ] **Step 4: Manual verification (visual)**

Open the project in Xcode, run on simulator, open a pebble read sheet, and confirm:
- Strokes draw progressively (glyph first, then shape, then fossil if present), each in the emotion color, ending with a subtle scale pulse.
- Closing and reopening the sheet replays the animation.
- Toggling Settings → Accessibility → Motion → Reduce Motion in the simulator (Hardware > Toggle Reduce Motion) renders the pebble immediately, no animation.

- [ ] **Step 5: Commit**

```bash
git add \
  apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift \
  apps/ios/Pebbles/Features/Path/Read/PebbleReadView.swift
git commit -m "$(cat <<'EOF'
feat(ios): wire animated pebble renderer into the read banner

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 13 — Final verification + open the PR

- [ ] **Step 1: Top-level repo build + lint**

Run from repo root:
```bash
npm run build
npm run lint
```
Expected: both PASS.

- [ ] **Step 2: iOS build + tests one more time**

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' test 2>&1 | tail -40
```
Expected: PASS.

- [ ] **Step 3: Push branch**

```bash
git push -u origin feat/333-pebble-stroke-animation
```

- [ ] **Step 4: Open the PR**

Inspect the linked issue first to inherit labels/milestone:
```bash
gh issue view 333
```
Issue #333 carries labels `feat`, `ios`, `ui` and milestone `M25 · Improved core UX`. Confirm with the user that the PR should inherit those exactly (per project memory), then:

```bash
gh pr create \
  --title "feat(ios): pebble stroke animation (#333)" \
  --label feat --label ios --label ui \
  --milestone "M25 · Improved core UX" \
  --body "$(cat <<'EOF'
Resolves #333.

## Summary
- Animate the pebble on the iOS read sheet with a progressive stroke-drawing reveal each time the sheet opens.
- Drop the now-redundant server `render_manifest` (column, JSON payload, engine code, web reads) — animation timing lives entirely on the iOS client, keyed by `render_version`.

## Key changes
- `apps/ios/Pebbles/Features/Path/Render/` — new native renderer: `SVGPathParser`, `PebbleSVGModel`, `PebbleAnimationTimings`, `PebbleAnimatedRenderView`. Falls back to existing `SVGView`-based static renderer when parsing fails, version is unknown, or Reduce Motion is on.
- `apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift` + `PebbleReadView.swift` — pass `render_version` through and use the animated renderer.
- `packages/supabase/supabase/functions/_shared/engine/{compose,types}.ts` and `compose-and-write.ts` — drop manifest production and write.
- `packages/supabase/supabase/migrations/20260429000000_drop_pebbles_render_manifest.sql` — drop the column.
- `apps/web/lib/{types,seed/seed-data,data/data-provider,data/supabase-provider}.ts` — drop `render_manifest` from the `Pebble` model and reads.

## Test plan
- [ ] iOS unit tests pass (`xcodebuild ... test`).
- [ ] Web build + lint pass.
- [ ] Supabase package build passes.
- [ ] Open a pebble read sheet on simulator → strokes draw progressively, settle pulse plays.
- [ ] Reopen the sheet → animation replays.
- [ ] Enable Reduce Motion → pebble renders fully drawn, no animation.
- [ ] Edit a pebble → re-rendered SVG continues to display correctly (compose-pebble-update path).

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```
Confirm the PR URL is returned. Done.

---

## Self-review notes

- **Spec coverage:**
  - Server simplification → Tasks 1–4, 6.
  - Web parity drop of `render_manifest` (not strictly in the spec but required because the column is being dropped) → Task 5.
  - iOS `ComposePebbleResponse` cleanup → Task 7.
  - SVGPathParser → Task 8.
  - PebbleSVGModel → Task 9.
  - PebbleAnimationTimings → Task 10.
  - PebbleAnimatedRenderView → Task 11.
  - Plumbing into `PebbleReadBanner` / `PebbleReadView` → Task 12.
  - `RENDER_VERSION` not bumped (per spec) → preserved at `0.1.0` in Task 3 and used as the timings key in Task 10.
  - Reduce Motion + unknown-version → static fallback handled in Task 11.
  - Tests in `PebblesTests/` → Tasks 8–10.
- **No placeholders.** No "TBD"/"TODO"/"add error handling".
- **Type consistency:** `PebbleAnimationTimings.Timings` exposes `.glyph .shape .fossil .settle`; the renderer reads them with the same names. Layer kind enum (`.shape, .fossil, .glyph`) matches between `PebbleSVGModel`, the renderer's `progress(for:)` switch, and the order test. `combinedPath: CGPath` is consistent across model/tests/renderer.
