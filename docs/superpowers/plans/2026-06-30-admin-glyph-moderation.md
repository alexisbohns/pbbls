# Admin Glyph Moderation + SVG Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the admin a back-office surface in `apps/admin` to moderate community glyph submissions (approve/reject/re-price) and to upload + adjust raw SVGs into first-party community glyphs.

**Architecture:** One migration adds a `review_note` column and five `is_admin`-gated `SECURITY DEFINER` RPCs (a read RPC that bypasses the pending-read RLS gap, plus approve/reject/re-price/publish). The admin app reuses its Server-Component-fetch → card pattern for the moderation queue and Server Actions for mutations. SVG → stroke-model conversion and the adjust transforms run client-side as pure utilities; a duplicated minimal render helper previews glyphs inside the pebble shape.

**Tech Stack:** Next.js 16 (App Router, RSC + Server Actions), TypeScript strict, Supabase (Postgres RPCs + RLS), Tailwind 4, shadcn/base-nova UI, Sonner.

> **Testing note (read once):** This repo has **no test runner** (CLAUDE.md: "verification is lint + build + manual") and CLAUDE.md overrides the default TDD workflow. Verification per task is therefore: `tsc`/build green, `eslint` green, and — for the pure geometry utilities (`path.ts`, `svg-to-strokes.ts`, `transform-path.ts`) — a **playground page** (`app/(authed)/playground/glyphs/`) mirroring the existing analytics playground convention. These three files are the prime candidates for real unit tests if a runner is ever introduced; do not add one here (new patterns require discussion).

---

## File structure

**Create (DB):**
- `packages/supabase/supabase/migrations/<timestamp>_admin_glyph_moderation.sql` — `review_note` column + 5 RPCs.

**Create (apps/admin):**
- `lib/pebblestore/types.ts` — admin-local glyph/submission types (`GlyphStroke`, `AdminSubmission`, `Adjust`, status union).
- `lib/pebblestore/path.ts` — pure SVG-path subset parser/serializer/bounds/transform.
- `lib/pebblestore/svg-to-strokes.ts` — SVG string → `{ strokes, viewBox, skipped }`.
- `lib/pebblestore/transform-path.ts` — adjust → matrix; bake adjust into strokes.
- `lib/pebblestore/render-preview.ts` — pure fit transform (duplicated minimal `renderGlyphPaths`).
- `lib/pebblestore/fetchers.ts` — server fetchers (read RPC + shapes).
- `app/(authed)/pebblestore/glyphs/actions.ts` — Server Actions (approve/reject/re-price/publish).
- `app/(authed)/pebblestore/glyphs/page.tsx` — moderation queue (SC shell).
- `app/(authed)/pebblestore/glyphs/_components/ModerationQueue.tsx` — list (presentational).
- `app/(authed)/pebblestore/glyphs/_components/SubmissionCard.tsx` — client card + approve/reject/re-price dialogs.
- `app/(authed)/pebblestore/glyphs/new/page.tsx` — upload + adjust (SC shell, fetches shapes).
- `app/(authed)/pebblestore/glyphs/new/_components/UploadAdjust.tsx` — client upload/adjust flow.
- `components/pebblestore/GlyphPreview.tsx` — shared SVG preview (strokes inside shape).
- `app/(authed)/playground/glyphs/page.tsx` — manual verification harness for the pure utils.

**Modify (apps/admin):**
- `components/layout/Sidebar.tsx` — add "Pebblestore" group.

**Modify (apps/web):**
- C's "Mine"-tab badge component — surface `review_note` on rejected glyphs.
- `messages/en.json` + `messages/fr.json` (or the repo's locale files) — rejected-reason copy.

**Modify (DB types):**
- `packages/supabase/types/database.ts` — regenerated.

**Modify (docs):**
- `docs/arkaik/bundle.json` — two new admin view nodes (via the `arkaik` skill).

---

## Task 1: Migration — `review_note` + moderation/publish RPCs

**Files:**
- Create: `packages/supabase/supabase/migrations/<timestamp>_admin_glyph_moderation.sql`
- Modify: `packages/supabase/types/database.ts` (regenerated)

- [ ] **Step 1: Create the migration file**

Run (from `packages/supabase/`): `npm run db:migration:new -- admin_glyph_moderation`
This creates a timestamped empty file. Put the following in it:

```sql
-- =============================================================================
-- Admin glyph moderation (#497) — reject reason + is_admin-gated RPCs for the
-- moderation queue (read), approve/reject/re-price, and first-party SVG publish.
-- All RPCs are SECURITY DEFINER and guard on is_admin(auth.uid()); the queue
-- read path exists because the widened glyphs_select (D8) does NOT let an admin
-- read a *pending* submission's strokes via RLS.
-- =============================================================================

-- 1. Reject reason (null unless rejected). Submitter reads it via the existing
--    glyph_submissions_select policy (submitter_id = auth.uid()).
alter table public.glyph_submissions
  add column review_note text;

-- 2. Read path for the moderation queue. Joins glyphs (strokes/view_box/name)
--    and the submitter email; ordered oldest-first (FIFO review).
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
      s.price        as price,
      s.review_note  as review_note,
      s.created_at   as created_at,
      s.reviewed_at  as reviewed_at,
      s.submitter_id as submitter_id,
      u.email        as submitter_email,
      g.name         as name,
      g.shape_id     as shape_id,
      g.strokes      as strokes,
      g.view_box     as view_box
    from public.glyph_submissions s
    join public.glyphs g on g.id = s.glyph_id
    left join auth.users u on u.id = s.submitter_id
    where p_status is null or s.status = p_status
  ) t;

  return v_result;
end;
$$;

-- 3. Approve a pending submission → live in Market. Optional price override.
create or replace function public.approve_glyph(p_submission_id uuid, p_price integer default null)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_row public.glyph_submissions;
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'not_admin' using errcode = '42501';
  end if;
  if p_price is not null and p_price <= 0 then raise exception 'bad_price'; end if;

  select * into v_row from public.glyph_submissions where id = p_submission_id;
  if not found then raise exception 'not_found'; end if;
  if v_row.status <> 'pending' then raise exception 'invalid_state'; end if;

  update public.glyph_submissions
  set status = 'approved',
      price = coalesce(p_price, price),
      reviewed_at = now(),
      reviewed_by = auth.uid()
  where id = p_submission_id
  returning * into v_row;

  return to_jsonb(v_row);
end;
$$;

-- 4. Reject a pending submission with a required reason.
create or replace function public.reject_glyph(p_submission_id uuid, p_note text)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_row public.glyph_submissions;
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'not_admin' using errcode = '42501';
  end if;
  if p_note is null or btrim(p_note) = '' then raise exception 'missing_note'; end if;

  select * into v_row from public.glyph_submissions where id = p_submission_id;
  if not found then raise exception 'not_found'; end if;
  if v_row.status <> 'pending' then raise exception 'invalid_state'; end if;

  update public.glyph_submissions
  set status = 'rejected',
      review_note = btrim(p_note),
      reviewed_at = now(),
      reviewed_by = auth.uid()
  where id = p_submission_id
  returning * into v_row;

  return to_jsonb(v_row);
end;
$$;

-- 5. Re-price an approved listing (curation control). Existing entitlements'
--    price_paid snapshots are untouched (C's D4).
create or replace function public.set_glyph_price(p_submission_id uuid, p_price integer)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_row public.glyph_submissions;
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'not_admin' using errcode = '42501';
  end if;
  if p_price <= 0 then raise exception 'bad_price'; end if;

  select * into v_row from public.glyph_submissions where id = p_submission_id;
  if not found then raise exception 'not_found'; end if;
  if v_row.status <> 'approved' then raise exception 'invalid_state'; end if;

  update public.glyph_submissions
  set price = p_price
  where id = p_submission_id
  returning * into v_row;

  return to_jsonb(v_row);
end;
$$;

-- 6. Publish a first-party glyph from an uploaded SVG: insert the glyph
--    (owned by the admin) + an auto-approved submission, atomically.
create or replace function public.publish_admin_glyph(
  p_name text,
  p_shape_id uuid,
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

  insert into public.glyphs (user_id, name, shape_id, strokes, view_box)
  values (v_user, nullif(btrim(p_name), ''), p_shape_id, p_strokes, p_view_box)
  returning id into v_glyph_id;

  insert into public.glyph_submissions
    (glyph_id, submitter_id, status, price, reviewed_at, reviewed_by)
  values (v_glyph_id, v_user, 'approved', p_price, now(), v_user)
  returning id into v_submission_id;

  return jsonb_build_object('glyph_id', v_glyph_id, 'submission_id', v_submission_id);
end;
$$;

-- 7. Grants: authenticated only; the is_admin guard does the real gating.
revoke all on function public.admin_list_glyph_submissions(text)               from public, anon;
revoke all on function public.approve_glyph(uuid, integer)                      from public, anon;
revoke all on function public.reject_glyph(uuid, text)                          from public, anon;
revoke all on function public.set_glyph_price(uuid, integer)                    from public, anon;
revoke all on function public.publish_admin_glyph(text, uuid, jsonb, text, integer) from public, anon;
grant execute on function public.admin_list_glyph_submissions(text)               to authenticated;
grant execute on function public.approve_glyph(uuid, integer)                      to authenticated;
grant execute on function public.reject_glyph(uuid, text)                          to authenticated;
grant execute on function public.set_glyph_price(uuid, integer)                    to authenticated;
grant execute on function public.publish_admin_glyph(text, uuid, jsonb, text, integer) to authenticated;
```

- [ ] **Step 2: Apply the migration to the remote DB**

This project deploys to **remote Supabase** (no local Docker). Apply via the team's
remote workflow (`npm run db:push` from `packages/supabase/`, or the Supabase dashboard
SQL editor / MCP). Confirm it applies without error.

- [ ] **Step 3: Regenerate and commit DB types**

Run (from repo root): `npm run db:types:remote --workspace=packages/supabase`
(`:remote` because there is no local instance — see `packages/supabase/CLAUDE.md`; note the
script already routes stderr to `/dev/null` to avoid corrupting the file).

Verify `packages/supabase/types/database.ts` now contains the five new functions
(`grep -n "admin_list_glyph_submissions\|approve_glyph\|reject_glyph\|set_glyph_price\|publish_admin_glyph" packages/supabase/types/database.ts`) and `review_note` under `glyph_submissions`.

- [ ] **Step 4: Commit**

```bash
git add packages/supabase/supabase/migrations packages/supabase/types/database.ts
git commit -m "feat(db): admin glyph moderation rpcs + review_note (#497)"
```

---

## Task 2: Admin-local types

**Files:**
- Create: `apps/admin/lib/pebblestore/types.ts`

- [ ] **Step 1: Write the types**

The admin app cannot import `apps/web/lib/types`, so mirror the stroke shape locally.
`GlyphStroke` matches the DB `glyphs.strokes` JSON element.

```ts
// apps/admin/lib/pebblestore/types.ts

/** One stroke of a glyph — mirrors the apps/web Mark stroke + DB glyphs.strokes JSON. */
export type GlyphStroke = { d: string; width: number }

export type SubmissionStatus = "pending" | "approved" | "rejected"

/** A row from the admin_list_glyph_submissions RPC. */
export type AdminSubmission = {
  submission_id: string
  glyph_id: string
  status: SubmissionStatus
  price: number
  review_note: string | null
  created_at: string
  reviewed_at: string | null
  submitter_id: string
  submitter_email: string | null
  name: string | null
  shape_id: string | null
  strokes: GlyphStroke[]
  view_box: string
}

/** A pebble shape (reference data) for the shape dropdown + preview clip. */
export type PebbleShape = {
  id: string
  slug: string
  name: string
  path: string
  view_box: string
}

/** Adjust controls for the upload flow. scale=1, no offset, no flip is identity. */
export type Adjust = {
  scale: number
  offsetX: number
  offsetY: number
  flipH: boolean
  flipV: boolean
}

export const IDENTITY_ADJUST: Adjust = {
  scale: 1,
  offsetX: 0,
  offsetY: 0,
  flipH: false,
  flipV: false,
}

/** Display/sanity mirror of the SQL default; the server is authoritative. */
export const GLYPH_PRICE_DEFAULT = 25

/** SVG coordinate-space stroke width used when a source path has none. */
export const DEFAULT_STROKE_WIDTH = 6
```

- [ ] **Step 2: Verify it compiles**

Run: `npx tsc --noEmit -p apps/admin/tsconfig.json`
Expected: no errors from this file.

- [ ] **Step 3: Commit**

```bash
git add apps/admin/lib/pebblestore/types.ts
git commit -m "feat(admin): pebblestore moderation types (#497)"
```

---

## Task 3: SVG path subset parser (`path.ts`)

**Files:**
- Create: `apps/admin/lib/pebblestore/path.ts`

This is a pure module: tokenize an SVG path `d` for the supported command subset
(`M L H V Q C Z`, absolute + relative), **normalizing everything to absolute `M/L/Q/C/Z`**,
so transform/serialize/bounds are trivial. Unsupported commands (e.g. `A`, `S`, `T`) throw
`UnsupportedPathError` so the caller can skip that path.

- [ ] **Step 1: Write the module**

```ts
// apps/admin/lib/pebblestore/path.ts

export type PathPoint = { x: number; y: number }

export type PathCommand =
  | { cmd: "M"; points: [PathPoint] }
  | { cmd: "L"; points: [PathPoint] }
  | { cmd: "Q"; points: [PathPoint, PathPoint] }
  | { cmd: "C"; points: [PathPoint, PathPoint, PathPoint] }
  | { cmd: "Z"; points: [] }

export class UnsupportedPathError extends Error {
  constructor(public readonly command: string) {
    super(`Unsupported path command: ${command}`)
    this.name = "UnsupportedPathError"
  }
}

/** 2×3 affine matrix [a,b,c,d,e,f]: x' = a·x + c·y + e ; y' = b·x + d·y + f */
export type Matrix = [number, number, number, number, number, number]

const NUMBER_RE = /-?\d*\.?\d+(?:e[-+]?\d+)?/gi
const COMMAND_RE = /[a-z][^a-z]*/gi

/** Parse a path `d` into normalized absolute commands. Throws UnsupportedPathError. */
export function parsePath(d: string): PathCommand[] {
  const tokens = d.match(COMMAND_RE) ?? []
  const out: PathCommand[] = []
  let cx = 0
  let cy = 0
  let sx = 0
  let sy = 0

  for (const token of tokens) {
    const letter = token[0]
    const upper = letter.toUpperCase()
    const rel = letter !== upper
    const nums = (token.slice(1).match(NUMBER_RE) ?? []).map(Number)

    switch (upper) {
      case "M": {
        for (let i = 0; i + 1 < nums.length; i += 2) {
          let x = nums[i]
          let y = nums[i + 1]
          if (rel) {
            x += cx
            y += cy
          }
          if (i === 0) {
            out.push({ cmd: "M", points: [{ x, y }] })
            sx = x
            sy = y
          } else {
            out.push({ cmd: "L", points: [{ x, y }] })
          }
          cx = x
          cy = y
        }
        break
      }
      case "L": {
        for (let i = 0; i + 1 < nums.length; i += 2) {
          let x = nums[i]
          let y = nums[i + 1]
          if (rel) {
            x += cx
            y += cy
          }
          out.push({ cmd: "L", points: [{ x, y }] })
          cx = x
          cy = y
        }
        break
      }
      case "H": {
        for (const n of nums) {
          const x = rel ? cx + n : n
          out.push({ cmd: "L", points: [{ x, y: cy }] })
          cx = x
        }
        break
      }
      case "V": {
        for (const n of nums) {
          const y = rel ? cy + n : n
          out.push({ cmd: "L", points: [{ x: cx, y }] })
          cy = y
        }
        break
      }
      case "Q": {
        for (let i = 0; i + 3 < nums.length; i += 4) {
          let x1 = nums[i]
          let y1 = nums[i + 1]
          let x = nums[i + 2]
          let y = nums[i + 3]
          if (rel) {
            x1 += cx
            y1 += cy
            x += cx
            y += cy
          }
          out.push({ cmd: "Q", points: [{ x: x1, y: y1 }, { x, y }] })
          cx = x
          cy = y
        }
        break
      }
      case "C": {
        for (let i = 0; i + 5 < nums.length; i += 6) {
          let x1 = nums[i]
          let y1 = nums[i + 1]
          let x2 = nums[i + 2]
          let y2 = nums[i + 3]
          let x = nums[i + 4]
          let y = nums[i + 5]
          if (rel) {
            x1 += cx
            y1 += cy
            x2 += cx
            y2 += cy
            x += cx
            y += cy
          }
          out.push({ cmd: "C", points: [{ x: x1, y: y1 }, { x: x2, y: y2 }, { x, y }] })
          cx = x
          cy = y
        }
        break
      }
      case "Z": {
        out.push({ cmd: "Z", points: [] })
        cx = sx
        cy = sy
        break
      }
      default:
        throw new UnsupportedPathError(upper)
    }
  }

  return out
}

const fmt = (n: number): string => Number(n.toFixed(2)).toString()

export function serializePath(cmds: PathCommand[]): string {
  return cmds
    .map((c) => {
      switch (c.cmd) {
        case "M":
          return `M ${fmt(c.points[0].x)} ${fmt(c.points[0].y)}`
        case "L":
          return `L ${fmt(c.points[0].x)} ${fmt(c.points[0].y)}`
        case "Q":
          return `Q ${fmt(c.points[0].x)} ${fmt(c.points[0].y)} ${fmt(c.points[1].x)} ${fmt(c.points[1].y)}`
        case "C":
          return `C ${fmt(c.points[0].x)} ${fmt(c.points[0].y)} ${fmt(c.points[1].x)} ${fmt(c.points[1].y)} ${fmt(c.points[2].x)} ${fmt(c.points[2].y)}`
        case "Z":
          return "Z"
      }
    })
    .join(" ")
}

export type Bounds = { minX: number; minY: number; maxX: number; maxY: number }

/** Superset bounds (includes control points) — safe for framing, not exact. */
export function pathBounds(cmds: PathCommand[]): Bounds | null {
  let minX = Infinity
  let minY = Infinity
  let maxX = -Infinity
  let maxY = -Infinity
  let has = false
  for (const c of cmds) {
    for (const p of c.points) {
      has = true
      minX = Math.min(minX, p.x)
      minY = Math.min(minY, p.y)
      maxX = Math.max(maxX, p.x)
      maxY = Math.max(maxY, p.y)
    }
  }
  return has ? { minX, minY, maxX, maxY } : null
}

function applyMatrix(m: Matrix, p: PathPoint): PathPoint {
  return { x: m[0] * p.x + m[2] * p.y + m[4], y: m[1] * p.x + m[3] * p.y + m[5] }
}

export function transformPath(cmds: PathCommand[], m: Matrix): PathCommand[] {
  return cmds.map((c) => ({ ...c, points: c.points.map((p) => applyMatrix(m, p)) }) as PathCommand)
}
```

- [ ] **Step 2: Verify it compiles**

Run: `npx tsc --noEmit -p apps/admin/tsconfig.json`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add apps/admin/lib/pebblestore/path.ts
git commit -m "feat(admin): svg path subset parser/transform (#497)"
```

---

## Task 4: SVG → strokes (`svg-to-strokes.ts`)

**Files:**
- Create: `apps/admin/lib/pebblestore/svg-to-strokes.ts`

- [ ] **Step 1: Write the module (documented supported subset in the header)**

```ts
// apps/admin/lib/pebblestore/svg-to-strokes.ts
//
// SVG → stroke-model conversion for the admin glyph uploader (#497).
//
// SUPPORTED SUBSET (documented — see spec §4):
//   • <path> with commands M L H V Q C Z (absolute + relative). A path using any
//     other command (arcs A, smooth S/T) is SKIPPED and reported.
//   • <line>, <polyline>, <polygon> — converted to an equivalent path `d`.
//   • viewBox: taken from <svg viewBox>; else from width/height; else a padded
//     bounds computed from the parsed strokes; else a 0 0 100 100 fallback.
//   • stroke-width → the stroke's width (DEFAULT_STROKE_WIDTH when absent).
//     NOTE: renderGlyphPaths normalizes width at market-render time, so width is
//     cosmetic here.
//
// NOT SUPPORTED (skipped + reported): <rect> <circle> <ellipse> <text> <image>
//   <use>, gradients/patterns/<style>, CSS classes, and fills. The model is
//   STROKE-ONLY: a filled icon imports as its OUTLINE only. The live preview
//   shows exactly this before publish.
//
// Throws only on unparseable input (no <svg> root / malformed XML).

import { DEFAULT_STROKE_WIDTH, type GlyphStroke } from "./types"
import { parsePath, serializePath, pathBounds, UnsupportedPathError } from "./path"

export type SvgImportResult = {
  strokes: GlyphStroke[]
  viewBox: string
  /** Tag names (or "tag (CMD)") of elements/paths that were skipped. */
  skipped: string[]
}

const SUPPORTED_TAGS = new Set(["path", "line", "polyline", "polygon"])
const STRUCTURAL_TAGS = new Set(["svg", "g", "defs", "title", "desc", "metadata"])

function elementToD(el: Element): string | null {
  const tag = el.tagName.toLowerCase()
  if (tag === "path") return el.getAttribute("d")
  if (tag === "line") {
    const x1 = el.getAttribute("x1") ?? "0"
    const y1 = el.getAttribute("y1") ?? "0"
    const x2 = el.getAttribute("x2") ?? "0"
    const y2 = el.getAttribute("y2") ?? "0"
    return `M ${x1} ${y1} L ${x2} ${y2}`
  }
  if (tag === "polyline" || tag === "polygon") {
    const raw = (el.getAttribute("points") ?? "").trim()
    const nums = raw.split(/[\s,]+/).filter(Boolean).map(Number)
    if (nums.length < 4) return null
    let d = `M ${nums[0]} ${nums[1]}`
    for (let i = 2; i + 1 < nums.length; i += 2) d += ` L ${nums[i]} ${nums[i + 1]}`
    if (tag === "polygon") d += " Z"
    return d
  }
  return null
}

function readWidth(el: Element): number {
  const w = parseFloat(el.getAttribute("stroke-width") ?? "")
  return Number.isFinite(w) && w > 0 ? w : DEFAULT_STROKE_WIDTH
}

function resolveViewBox(root: Element, strokes: GlyphStroke[]): string {
  const vb = root.getAttribute("viewBox")
  if (vb && vb.trim().split(/[\s,]+/).length === 4) return vb.trim().replace(/,/g, " ")

  const w = parseFloat(root.getAttribute("width") ?? "")
  const h = parseFloat(root.getAttribute("height") ?? "")
  if (Number.isFinite(w) && Number.isFinite(h) && w > 0 && h > 0) return `0 0 ${w} ${h}`

  // Compute padded bounds from the parsed strokes.
  let minX = Infinity
  let minY = Infinity
  let maxX = -Infinity
  let maxY = -Infinity
  let has = false
  for (const s of strokes) {
    const b = pathBounds(parsePath(s.d))
    if (!b) continue
    has = true
    minX = Math.min(minX, b.minX)
    minY = Math.min(minY, b.minY)
    maxX = Math.max(maxX, b.maxX)
    maxY = Math.max(maxY, b.maxY)
  }
  if (!has) return "0 0 100 100"
  const pad = Math.max(maxX - minX, maxY - minY) * 0.06 || 1
  return `${minX - pad} ${minY - pad} ${maxX - minX + pad * 2} ${maxY - minY + pad * 2}`
}

export function svgToStrokes(svg: string): SvgImportResult {
  const doc = new DOMParser().parseFromString(svg, "image/svg+xml")
  const root = doc.querySelector("svg")
  if (doc.querySelector("parsererror") || !root) {
    throw new Error("Could not parse this file as SVG.")
  }

  const strokes: GlyphStroke[] = []
  const skipped: string[] = []

  for (const el of Array.from(root.querySelectorAll("*"))) {
    const tag = el.tagName.toLowerCase()
    if (STRUCTURAL_TAGS.has(tag)) continue
    if (!SUPPORTED_TAGS.has(tag)) {
      skipped.push(tag)
      continue
    }
    const d = elementToD(el)
    if (!d) {
      skipped.push(tag)
      continue
    }
    try {
      const cmds = parsePath(d)
      if (cmds.length === 0) continue
      strokes.push({ d: serializePath(cmds), width: readWidth(el) })
    } catch (e) {
      if (e instanceof UnsupportedPathError) {
        skipped.push(`${tag} (${e.command})`)
      } else {
        throw e
      }
    }
  }

  return { strokes, viewBox: resolveViewBox(root, strokes), skipped }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `npx tsc --noEmit -p apps/admin/tsconfig.json`
Expected: no errors. (Behavioral verification happens in the playground, Task 7.)

- [ ] **Step 3: Commit**

```bash
git add apps/admin/lib/pebblestore/svg-to-strokes.ts
git commit -m "feat(admin): svg-to-strokes conversion (#497)"
```

---

## Task 5: Adjust → transform bake (`transform-path.ts`) + preview transform (`render-preview.ts`)

**Files:**
- Create: `apps/admin/lib/pebblestore/transform-path.ts`
- Create: `apps/admin/lib/pebblestore/render-preview.ts`

The adjust transforms operate **within a fixed frame** (the glyph's viewBox stays put), so
scaling < 1 adds padding (glyph shrinks in the pebble), recenter shifts it, and flip mirrors.
The live preview and the publish bake share `buildAdjustMatrix`.

- [ ] **Step 1: Write `transform-path.ts`**

```ts
// apps/admin/lib/pebblestore/transform-path.ts
import { parsePath, serializePath, transformPath, type Matrix } from "./path"
import type { Adjust, GlyphStroke } from "./types"

function parseViewBox(vb: string): { x: number; y: number; w: number; h: number } {
  const [x, y, w, h] = vb.trim().split(/[\s,]+/).map(Number)
  return { x, y, w, h }
}

/**
 * Affine matrix for the adjust controls, about the viewBox centre:
 *   p' = c + offset + S·(p - c),  S = diag(sx, sy)
 * where sx = scale·(flipH ? -1 : 1), sy = scale·(flipV ? -1 : 1).
 */
export function buildAdjustMatrix(viewBox: string, a: Adjust): Matrix {
  const { x, y, w, h } = parseViewBox(viewBox)
  const cx = x + w / 2
  const cy = y + h / 2
  const sx = a.scale * (a.flipH ? -1 : 1)
  const sy = a.scale * (a.flipV ? -1 : 1)
  return [sx, 0, 0, sy, cx + a.offsetX - sx * cx, cy + a.offsetY - sy * cy]
}

/** CSS/SVG transform string for live preview. */
export function matrixToTransform(m: Matrix): string {
  return `matrix(${m.map((n) => Number(n.toFixed(4))).join(",")})`
}

/** Bake the adjust into the stroke geometry for publish. viewBox is unchanged. */
export function bakeAdjust(strokes: GlyphStroke[], viewBox: string, a: Adjust): GlyphStroke[] {
  const m = buildAdjustMatrix(viewBox, a)
  return strokes.map((s) => ({ ...s, d: serializePath(transformPath(parsePath(s.d), m)) }))
}
```

- [ ] **Step 2: Write `render-preview.ts` (duplicated minimal fit helper)**

```ts
// apps/admin/lib/pebblestore/render-preview.ts
//
// DUPLICATED minimal port of apps/web/lib/engine/glyph.ts renderGlyphPaths fit
// math, so the admin app can preview a glyph inside a pebble shape without
// importing across workspaces. Consolidate into a shared package later if the
// engine is extracted (see spec §6.3) — do not refactor unprompted.

type Rect = { x: number; y: number; width: number; height: number }

function parseViewBox(vb: string): Rect {
  const [x, y, width, height] = vb.trim().split(/[\s,]+/).map(Number)
  return { x, y, width, height }
}

/** Uniform scale + centre translate fitting `glyphViewBox` into `zone`. */
export function fitTransform(glyphViewBox: string, zone: Rect): string {
  const vb = parseViewBox(glyphViewBox)
  const scale = Math.min(zone.width / vb.width, zone.height / vb.height)
  const offsetX = zone.x + (zone.width - vb.width * scale) / 2 - vb.x * scale
  const offsetY = zone.y + (zone.height - vb.height * scale) / 2 - vb.y * scale
  return `translate(${offsetX}, ${offsetY}) scale(${scale})`
}

export { parseViewBox }
export type { Rect }
```

- [ ] **Step 3: Verify it compiles**

Run: `npx tsc --noEmit -p apps/admin/tsconfig.json`
Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add apps/admin/lib/pebblestore/transform-path.ts apps/admin/lib/pebblestore/render-preview.ts
git commit -m "feat(admin): adjust matrix bake + preview fit helper (#497)"
```

---

## Task 6: Shared `GlyphPreview` component

**Files:**
- Create: `apps/admin/components/pebblestore/GlyphPreview.tsx`

Renders strokes inside a pebble shape outline. Composition: `<svg viewBox={shape.view_box}>`
→ shape outline path (muted) → `<g fit><g adjust>` strokes `</g></g>`. Strokes are
`stroke="currentColor" fill="none"` so the surrounding text colour drives them (no emotion in
admin). `adjust` is optional (omit it for the moderation queue preview where there's no
adjust).

- [ ] **Step 1: Write the component**

```tsx
// apps/admin/components/pebblestore/GlyphPreview.tsx
import type { Adjust, GlyphStroke, PebbleShape } from "@/lib/pebblestore/types"
import { buildAdjustMatrix, matrixToTransform } from "@/lib/pebblestore/transform-path"
import { fitTransform, parseViewBox } from "@/lib/pebblestore/render-preview"

const STROKE_WIDTH = 6

type Props = {
  strokes: GlyphStroke[]
  glyphViewBox: string
  shape: PebbleShape | null
  adjust?: Adjust
  className?: string
}

export function GlyphPreview({ strokes, glyphViewBox, shape, adjust, className }: Props) {
  // Shape provides the canvas + outline; fall back to the glyph's own box if absent.
  const canvasViewBox = shape?.view_box ?? glyphViewBox
  const zone = parseViewBox(canvasViewBox)
  const fit = fitTransform(glyphViewBox, zone)
  const adjustTransform = adjust ? matrixToTransform(buildAdjustMatrix(glyphViewBox, adjust)) : undefined

  // Pre-divide so the fit scale yields ~STROKE_WIDTH px in canvas coords.
  const fitScale = Math.min(zone.width / parseViewBox(glyphViewBox).width, zone.height / parseViewBox(glyphViewBox).height)
  const strokeWidth = STROKE_WIDTH / (fitScale || 1)

  return (
    <svg
      viewBox={canvasViewBox}
      className={className}
      role="img"
      aria-label="Glyph preview"
    >
      {shape ? (
        <path d={shape.path} fill="none" className="text-muted-foreground/40" stroke="currentColor" strokeWidth={2} />
      ) : null}
      <g transform={fit}>
        <g transform={adjustTransform}>
          {strokes.map((s, i) => (
            <path
              key={i}
              d={s.d}
              fill="none"
              stroke="currentColor"
              strokeWidth={strokeWidth}
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          ))}
        </g>
      </g>
    </svg>
  )
}
```

- [ ] **Step 2: Verify it compiles**

Run: `npx tsc --noEmit -p apps/admin/tsconfig.json`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add apps/admin/components/pebblestore/GlyphPreview.tsx
git commit -m "feat(admin): GlyphPreview component (#497)"
```

---

## Task 7: Playground harness (manual verification of the pure utils)

**Files:**
- Create: `apps/admin/app/(authed)/playground/glyphs/page.tsx`

Mirrors the analytics playground convention — a route to eyeball states without seeded data.
Here it converts a few inline sample SVGs and renders them through `GlyphPreview`, including
adjust transforms, so the conversion + matrix + preview are verifiable by eye.

- [ ] **Step 1: Write the playground page**

```tsx
// apps/admin/app/(authed)/playground/glyphs/page.tsx
import { svgToStrokes } from "@/lib/pebblestore/svg-to-strokes"
import { GlyphPreview } from "@/components/pebblestore/GlyphPreview"
import { IDENTITY_ADJUST } from "@/lib/pebblestore/types"

const SAMPLES: { label: string; svg: string }[] = [
  {
    label: "Line art (path L)",
    svg: `<svg viewBox="0 0 100 100"><path d="M10 90 L50 10 L90 90 Z"/></svg>`,
  },
  {
    label: "Curves (path Q/C)",
    svg: `<svg viewBox="0 0 100 100"><path d="M10 50 Q50 10 90 50 C90 80 10 80 10 50"/></svg>`,
  },
  {
    label: "Polyline + line",
    svg: `<svg viewBox="0 0 100 100"><polyline points="10,10 50,90 90,10"/><line x1="10" y1="50" x2="90" y2="50"/></svg>`,
  },
  {
    label: "Unsupported mix (rect skipped, arc skipped)",
    svg: `<svg viewBox="0 0 100 100"><rect x="10" y="10" width="80" height="80"/><path d="M10 10 A 40 40 0 0 1 90 90"/><path d="M10 90 L90 10"/></svg>`,
  },
]

export default function GlyphPlayground() {
  return (
    <div className="space-y-8">
      <h1 className="text-lg font-semibold">Glyph conversion playground</h1>
      <div className="grid grid-cols-2 gap-6 md:grid-cols-4">
        {SAMPLES.map((s) => {
          const r = svgToStrokes(s.svg)
          return (
            <div key={s.label} className="space-y-2 rounded-lg border p-3 text-xs">
              <div className="font-medium">{s.label}</div>
              <GlyphPreview
                strokes={r.strokes}
                glyphViewBox={r.viewBox}
                shape={null}
                adjust={{ ...IDENTITY_ADJUST, scale: 0.8 }}
                className="aspect-square w-full text-foreground"
              />
              <div className="text-muted-foreground">strokes: {r.strokes.length}</div>
              <div className="text-muted-foreground">skipped: {r.skipped.join(", ") || "none"}</div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Verify in the browser**

Run the admin dev server, sign in as admin, visit `/playground/glyphs`. Confirm:
- triangle, curves, polyline+line render as expected line drawings;
- the unsupported sample shows `skipped: rect, path (A)` and still renders the one valid line;
- `scale: 0.8` visibly shrinks the glyph within its frame.

- [ ] **Step 3: Commit**

```bash
git add "apps/admin/app/(authed)/playground/glyphs/page.tsx"
git commit -m "feat(admin): glyph conversion playground (#497)"
```

---

## Task 8: Fetchers + Server Actions

**Files:**
- Create: `apps/admin/lib/pebblestore/fetchers.ts`
- Create: `apps/admin/app/(authed)/pebblestore/glyphs/actions.ts`

- [ ] **Step 1: Write the fetchers**

`listSubmissions` calls the read RPC; `listShapes` reads the public reference table.
Both throw `new Error(error.message)` so `ErrorBlock` can render it (admin convention).

```ts
// apps/admin/lib/pebblestore/fetchers.ts
import { createServerSupabaseClient } from "@/lib/supabase/server"
import type { AdminSubmission, PebbleShape, SubmissionStatus } from "./types"

export async function listSubmissions(status?: SubmissionStatus): Promise<AdminSubmission[]> {
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.rpc("admin_list_glyph_submissions", {
    p_status: status ?? null,
  })
  if (error) {
    console.error("[pebblestore] listSubmissions failed:", error.message)
    throw new Error(error.message)
  }
  return (data ?? []) as unknown as AdminSubmission[]
}

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

- [ ] **Step 2: Write the Server Actions**

Mirrors `apps/admin/app/(authed)/logs/actions.ts`. Each returns `{ error }` on failure (mapped
from the RPC error contract) or `undefined` on success, and `revalidatePath`s the queue.
`publishGlyph` takes already-baked strokes + viewBox (the client bakes via Task 5 before
calling) and redirects on success.

```ts
// apps/admin/app/(authed)/pebblestore/glyphs/actions.ts
"use server"

import { redirect } from "next/navigation"
import { revalidatePath } from "next/cache"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import type { GlyphStroke } from "@/lib/pebblestore/types"

export type ActionResult = { error: string } | undefined

const QUEUE_PATH = "/pebblestore/glyphs"

/** Map the SQL error contract (§3) to English admin copy. */
function messageFor(code: string): string {
  switch (code) {
    case "not_admin":
      return "You are not authorized to perform this action."
    case "not_found":
      return "That submission no longer exists."
    case "invalid_state":
      return "That submission is not in a state where this action is allowed."
    case "missing_note":
      return "A rejection reason is required."
    case "bad_price":
      return "Price must be a positive number of karma."
    case "empty_glyph":
      return "This glyph has no usable strokes to publish."
    default:
      return "Something went wrong. Check the server console for details."
  }
}

export async function approveGlyph(submissionId: string, price?: number): Promise<ActionResult> {
  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.rpc("approve_glyph", {
    p_submission_id: submissionId,
    p_price: price ?? null,
  })
  if (error) {
    console.error("[pebblestore] approveGlyph failed:", error.message)
    return { error: messageFor(error.message) }
  }
  revalidatePath(QUEUE_PATH)
  return undefined
}

export async function rejectGlyph(submissionId: string, note: string): Promise<ActionResult> {
  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.rpc("reject_glyph", {
    p_submission_id: submissionId,
    p_note: note,
  })
  if (error) {
    console.error("[pebblestore] rejectGlyph failed:", error.message)
    return { error: messageFor(error.message) }
  }
  revalidatePath(QUEUE_PATH)
  return undefined
}

export async function setGlyphPrice(submissionId: string, price: number): Promise<ActionResult> {
  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.rpc("set_glyph_price", {
    p_submission_id: submissionId,
    p_price: price,
  })
  if (error) {
    console.error("[pebblestore] setGlyphPrice failed:", error.message)
    return { error: messageFor(error.message) }
  }
  revalidatePath(QUEUE_PATH)
  return undefined
}

export async function publishGlyph(input: {
  name: string
  shapeId: string
  strokes: GlyphStroke[]
  viewBox: string
  price: number
}): Promise<ActionResult> {
  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.rpc("publish_admin_glyph", {
    p_name: input.name,
    p_shape_id: input.shapeId,
    p_strokes: input.strokes as unknown as never, // jsonb
    p_view_box: input.viewBox,
    p_price: input.price,
  })
  if (error) {
    console.error("[pebblestore] publishGlyph failed:", error.message)
    return { error: messageFor(error.message) }
  }
  revalidatePath(QUEUE_PATH)
  redirect(`${QUEUE_PATH}?status=approved`)
}
```

> Note: Supabase surfaces `RAISE EXCEPTION 'not_found'` as `error.message === "not_found"`.
> Confirm the exact message shape during Step 4 manual testing; if it arrives wrapped, adjust
> `messageFor` to match (e.g. `error.message.includes(code)`).

- [ ] **Step 3: Verify it compiles**

Run: `npx tsc --noEmit -p apps/admin/tsconfig.json`
Expected: no errors. (The `p_status`/`p_price` RPC arg names must match Task 1; the
regenerated types from Task 1 make this type-check.)

- [ ] **Step 4: Commit**

```bash
git add apps/admin/lib/pebblestore/fetchers.ts "apps/admin/app/(authed)/pebblestore/glyphs/actions.ts"
git commit -m "feat(admin): pebblestore fetchers + moderation server actions (#497)"
```

---

## Task 9: Moderation queue UI

**Files:**
- Create: `apps/admin/app/(authed)/pebblestore/glyphs/page.tsx`
- Create: `apps/admin/app/(authed)/pebblestore/glyphs/_components/ModerationQueue.tsx`
- Create: `apps/admin/app/(authed)/pebblestore/glyphs/_components/SubmissionCard.tsx`

- [ ] **Step 1: Write the page (Server Component shell)**

Reads `?status=` (default `pending`), fetches submissions + shapes in parallel, renders the
queue. Includes a status filter via links and an "Upload glyph" CTA.

```tsx
// apps/admin/app/(authed)/pebblestore/glyphs/page.tsx
import Link from "next/link"
import { Button } from "@/components/ui/button"
import { listShapes, listSubmissions } from "@/lib/pebblestore/fetchers"
import type { SubmissionStatus } from "@/lib/pebblestore/types"
import { ModerationQueue } from "./_components/ModerationQueue"

const STATUSES: SubmissionStatus[] = ["pending", "approved", "rejected"]

function isStatus(v: string | undefined): v is SubmissionStatus {
  return v === "pending" || v === "approved" || v === "rejected"
}

export default async function GlyphModerationPage({
  searchParams,
}: {
  searchParams: Promise<{ status?: string }>
}) {
  const { status } = await searchParams
  const active: SubmissionStatus = isStatus(status) ? status : "pending"

  const [submissions, shapes] = await Promise.all([listSubmissions(active), listShapes()])

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-lg font-semibold">Glyph moderation</h1>
        <Button render={<Link href="/pebblestore/glyphs/new" />}>Upload glyph</Button>
      </div>

      <nav className="flex gap-2">
        {STATUSES.map((s) => (
          <Button
            key={s}
            size="sm"
            variant={s === active ? "default" : "outline"}
            render={<Link href={`/pebblestore/glyphs?status=${s}`} />}
          >
            {s[0].toUpperCase() + s.slice(1)}
          </Button>
        ))}
      </nav>

      <ModerationQueue submissions={submissions} shapes={shapes} />
    </div>
  )
}
```

- [ ] **Step 2: Write `ModerationQueue` (presentational)**

```tsx
// apps/admin/app/(authed)/pebblestore/glyphs/_components/ModerationQueue.tsx
import type { AdminSubmission, PebbleShape } from "@/lib/pebblestore/types"
import { SubmissionCard } from "./SubmissionCard"

export function ModerationQueue({
  submissions,
  shapes,
}: {
  submissions: AdminSubmission[]
  shapes: PebbleShape[]
}) {
  if (submissions.length === 0) {
    return (
      <p className="rounded-lg border border-dashed p-8 text-center text-sm text-muted-foreground">
        No submissions in this state.
      </p>
    )
  }
  const shapeById = new Map(shapes.map((s) => [s.id, s]))
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {submissions.map((s) => (
        <SubmissionCard key={s.submission_id} submission={s} shape={shapeById.get(s.shape_id ?? "") ?? null} />
      ))}
    </div>
  )
}
```

- [ ] **Step 3: Write `SubmissionCard` (client; approve/reject/re-price)**

Controlled Base UI dialogs (no `asChild`), `useTransition`, Sonner toast on success, inline
error on failure. Approve shows an optional price field; reject requires a note; approved
cards show "Re-price".

```tsx
// apps/admin/app/(authed)/pebblestore/glyphs/_components/SubmissionCard.tsx
"use client"

import { useState, useTransition } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardFooter, CardHeader } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { Label } from "@/components/ui/label"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { GlyphPreview } from "@/components/pebblestore/GlyphPreview"
import type { AdminSubmission, PebbleShape } from "@/lib/pebblestore/types"
import { approveGlyph, rejectGlyph, setGlyphPrice } from "../actions"

type Mode = "approve" | "reject" | "reprice" | null

export function SubmissionCard({
  submission,
  shape,
}: {
  submission: AdminSubmission
  shape: PebbleShape | null
}) {
  const [mode, setMode] = useState<Mode>(null)
  const [price, setPrice] = useState(String(submission.price))
  const [note, setNote] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [pending, startTransition] = useTransition()

  const close = () => {
    setMode(null)
    setError(null)
    setNote("")
    setPrice(String(submission.price))
  }

  const run = (fn: () => Promise<{ error: string } | undefined>, successMsg: string) => {
    setError(null)
    startTransition(async () => {
      const res = await fn()
      if (res?.error) {
        setError(res.error)
        return
      }
      toast.success(successMsg)
      close()
    })
  }

  const numericPrice = Number(price)

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between gap-2">
        <span className="truncate text-sm font-medium">{submission.name ?? "Untitled glyph"}</span>
        <Badge variant={submission.status === "approved" ? "default" : "secondary"}>
          {submission.status}
        </Badge>
      </CardHeader>
      <CardContent className="space-y-2">
        <GlyphPreview
          strokes={submission.strokes}
          glyphViewBox={submission.view_box}
          shape={shape}
          className="aspect-square w-full rounded-md border bg-card text-foreground"
        />
        <div className="text-xs text-muted-foreground">
          {submission.submitter_email ?? submission.submitter_id} · {submission.price} karma
        </div>
        {submission.status === "rejected" && submission.review_note ? (
          <p className="text-xs text-muted-foreground">Reason: {submission.review_note}</p>
        ) : null}
      </CardContent>
      <CardFooter className="gap-2">
        {submission.status === "pending" ? (
          <>
            <Button size="sm" onClick={() => setMode("approve")}>
              Approve
            </Button>
            <Button size="sm" variant="destructive" onClick={() => setMode("reject")}>
              Reject
            </Button>
          </>
        ) : null}
        {submission.status === "approved" ? (
          <Button size="sm" variant="outline" onClick={() => setMode("reprice")}>
            Re-price
          </Button>
        ) : null}
      </CardFooter>

      {/* Approve */}
      <Dialog open={mode === "approve"} onOpenChange={(o) => (o ? setMode("approve") : close())}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Approve glyph</DialogTitle>
            <DialogDescription>Publish this glyph to the community market.</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="approve-price">Price (karma)</Label>
            <Input
              id="approve-price"
              type="number"
              min={1}
              value={price}
              onChange={(e) => setPrice(e.target.value)}
            />
          </div>
          {error ? <p className="text-sm text-destructive">{error}</p> : null}
          <DialogFooter>
            <Button variant="outline" onClick={close} disabled={pending}>
              Cancel
            </Button>
            <Button
              disabled={pending || !Number.isFinite(numericPrice) || numericPrice <= 0}
              onClick={() => run(() => approveGlyph(submission.submission_id, numericPrice), "Glyph approved")}
            >
              {pending ? "Approving…" : "Approve"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Reject */}
      <Dialog open={mode === "reject"} onOpenChange={(o) => (o ? setMode("reject") : close())}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Reject glyph</DialogTitle>
            <DialogDescription>The submitter will see this reason.</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="reject-note">Reason</Label>
            <Textarea id="reject-note" value={note} onChange={(e) => setNote(e.target.value)} />
          </div>
          {error ? <p className="text-sm text-destructive">{error}</p> : null}
          <DialogFooter>
            <Button variant="outline" onClick={close} disabled={pending}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={pending || note.trim() === ""}
              onClick={() => run(() => rejectGlyph(submission.submission_id, note.trim()), "Glyph rejected")}
            >
              {pending ? "Rejecting…" : "Reject"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Re-price */}
      <Dialog open={mode === "reprice"} onOpenChange={(o) => (o ? setMode("reprice") : close())}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Re-price glyph</DialogTitle>
            <DialogDescription>Existing purchases keep the price they paid.</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="reprice-price">Price (karma)</Label>
            <Input
              id="reprice-price"
              type="number"
              min={1}
              value={price}
              onChange={(e) => setPrice(e.target.value)}
            />
          </div>
          {error ? <p className="text-sm text-destructive">{error}</p> : null}
          <DialogFooter>
            <Button variant="outline" onClick={close} disabled={pending}>
              Cancel
            </Button>
            <Button
              disabled={pending || !Number.isFinite(numericPrice) || numericPrice <= 0}
              onClick={() => run(() => setGlyphPrice(submission.submission_id, numericPrice), "Price updated")}
            >
              {pending ? "Saving…" : "Save"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  )
}
```

- [ ] **Step 2.5 (verify Button render-prop API):** Confirm the admin `Button` supports the
`render={<Link/>}` prop used above (Base UI pattern, seen in `Sidebar.tsx`). If `Button` does
not forward `render`, wrap with `<Link>` + `buttonVariants()` className instead. Grep:
`grep -n "render" apps/admin/components/ui/button.tsx`.

- [ ] **Step 3: Build to verify**

Run: `npm run build --workspace=apps/admin`
Expected: build succeeds; `/pebblestore/glyphs` route is emitted.

- [ ] **Step 4: Commit**

```bash
git add "apps/admin/app/(authed)/pebblestore/glyphs/page.tsx" "apps/admin/app/(authed)/pebblestore/glyphs/_components"
git commit -m "feat(admin): glyph moderation queue UI (#497)"
```

---

## Task 10: Upload + adjust UI

**Files:**
- Create: `apps/admin/app/(authed)/pebblestore/glyphs/new/page.tsx`
- Create: `apps/admin/app/(authed)/pebblestore/glyphs/new/_components/UploadAdjust.tsx`

- [ ] **Step 1: Write the page (SC shell — fetches shapes)**

```tsx
// apps/admin/app/(authed)/pebblestore/glyphs/new/page.tsx
import { listShapes } from "@/lib/pebblestore/fetchers"
import { UploadAdjust } from "./_components/UploadAdjust"

export default async function NewGlyphPage() {
  const shapes = await listShapes()
  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <h1 className="text-lg font-semibold">Upload a glyph</h1>
      <UploadAdjust shapes={shapes} />
    </div>
  )
}
```

- [ ] **Step 2: Write `UploadAdjust` (client flow)**

File input → `svgToStrokes` → metadata + transform controls → live `GlyphPreview` → bake →
`publishGlyph`. Shows the skipped readout and parse errors inline.

```tsx
// apps/admin/app/(authed)/pebblestore/glyphs/new/_components/UploadAdjust.tsx
"use client"

import { useMemo, useState, useTransition } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import { GlyphPreview } from "@/components/pebblestore/GlyphPreview"
import {
  GLYPH_PRICE_DEFAULT,
  IDENTITY_ADJUST,
  type Adjust,
  type GlyphStroke,
  type PebbleShape,
} from "@/lib/pebblestore/types"
import { svgToStrokes } from "@/lib/pebblestore/svg-to-strokes"
import { bakeAdjust } from "@/lib/pebblestore/transform-path"
import { publishGlyph } from "../../actions"

export function UploadAdjust({ shapes }: { shapes: PebbleShape[] }) {
  const [strokes, setStrokes] = useState<GlyphStroke[]>([])
  const [viewBox, setViewBox] = useState("0 0 100 100")
  const [skipped, setSkipped] = useState<string[]>([])
  const [parseError, setParseError] = useState<string | null>(null)

  const [name, setName] = useState("")
  const [price, setPrice] = useState(String(GLYPH_PRICE_DEFAULT))
  const [shapeId, setShapeId] = useState(shapes[0]?.id ?? "")
  const [adjust, setAdjust] = useState<Adjust>(IDENTITY_ADJUST)

  const [formError, setFormError] = useState<string | null>(null)
  const [pending, startTransition] = useTransition()

  const shape = useMemo(() => shapes.find((s) => s.id === shapeId) ?? null, [shapes, shapeId])
  const numericPrice = Number(price)
  const canPublish =
    strokes.length > 0 && shapeId !== "" && Number.isFinite(numericPrice) && numericPrice > 0

  const onFile = async (file: File | undefined) => {
    if (!file) return
    setParseError(null)
    try {
      const text = await file.text()
      const r = svgToStrokes(text)
      if (r.strokes.length === 0) {
        setParseError("No supported strokes found in this SVG (see the supported subset).")
        setStrokes([])
        setSkipped(r.skipped)
        return
      }
      setStrokes(r.strokes)
      setViewBox(r.viewBox)
      setSkipped(r.skipped)
      setAdjust(IDENTITY_ADJUST)
    } catch (e) {
      setParseError(e instanceof Error ? e.message : String(e))
      setStrokes([])
    }
  }

  const onPublish = () => {
    setFormError(null)
    const baked = bakeAdjust(strokes, viewBox, adjust)
    startTransition(async () => {
      const res = await publishGlyph({
        name: name.trim(),
        shapeId,
        strokes: baked,
        viewBox,
        price: numericPrice,
      })
      // On success the action redirects; only an error path returns here.
      if (res?.error) {
        setFormError(res.error)
        return
      }
      toast.success("Glyph published")
    })
  }

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <Label htmlFor="svg-file">SVG file</Label>
        <Input
          id="svg-file"
          type="file"
          accept=".svg,image/svg+xml"
          onChange={(e) => onFile(e.target.files?.[0])}
        />
        {parseError ? <p className="text-sm text-destructive">{parseError}</p> : null}
        {skipped.length > 0 ? (
          <p className="text-xs text-muted-foreground">
            Skipped (unsupported): {skipped.join(", ")}. Glyphs are stroke-only — filled shapes
            import as outlines.
          </p>
        ) : null}
      </div>

      {strokes.length > 0 ? (
        <>
          <GlyphPreview
            strokes={strokes}
            glyphViewBox={viewBox}
            shape={shape}
            adjust={adjust}
            className="mx-auto aspect-square w-48 rounded-md border bg-card text-foreground"
          />

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="glyph-name">Name</Label>
              <Input id="glyph-name" value={name} onChange={(e) => setName(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="glyph-price">Price (karma)</Label>
              <Input
                id="glyph-price"
                type="number"
                min={1}
                value={price}
                onChange={(e) => setPrice(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label>Shape</Label>
              <Select value={shapeId} onValueChange={setShapeId}>
                <SelectTrigger>
                  <SelectValue placeholder="Choose a shape" />
                </SelectTrigger>
                <SelectContent>
                  {shapes.map((s) => (
                    <SelectItem key={s.id} value={s.id}>
                      {s.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          <fieldset className="space-y-3 rounded-lg border p-4">
            <legend className="px-1 text-sm font-medium">Adjust</legend>
            <div className="space-y-1">
              <Label htmlFor="adjust-scale">Scale: {adjust.scale.toFixed(2)}</Label>
              <input
                id="adjust-scale"
                type="range"
                min={0.3}
                max={1.5}
                step={0.05}
                value={adjust.scale}
                onChange={(e) => setAdjust((a) => ({ ...a, scale: Number(e.target.value) }))}
                className="w-full"
              />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <Label htmlFor="adjust-x">Offset X: {adjust.offsetX}</Label>
                <input
                  id="adjust-x"
                  type="range"
                  min={-50}
                  max={50}
                  step={1}
                  value={adjust.offsetX}
                  onChange={(e) => setAdjust((a) => ({ ...a, offsetX: Number(e.target.value) }))}
                  className="w-full"
                />
              </div>
              <div className="space-y-1">
                <Label htmlFor="adjust-y">Offset Y: {adjust.offsetY}</Label>
                <input
                  id="adjust-y"
                  type="range"
                  min={-50}
                  max={50}
                  step={1}
                  value={adjust.offsetY}
                  onChange={(e) => setAdjust((a) => ({ ...a, offsetY: Number(e.target.value) }))}
                  className="w-full"
                />
              </div>
            </div>
            <div className="flex gap-6">
              <label className="flex items-center gap-2 text-sm">
                <Switch
                  checked={adjust.flipH}
                  onCheckedChange={(v) => setAdjust((a) => ({ ...a, flipH: v }))}
                />
                Flip H
              </label>
              <label className="flex items-center gap-2 text-sm">
                <Switch
                  checked={adjust.flipV}
                  onCheckedChange={(v) => setAdjust((a) => ({ ...a, flipV: v }))}
                />
                Flip V
              </label>
            </div>
          </fieldset>

          {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
          <Button disabled={!canPublish || pending} onClick={onPublish}>
            {pending ? "Publishing…" : "Publish to community"}
          </Button>
        </>
      ) : null}
    </div>
  )
}
```

- [ ] **Step 2.5 (verify Select/Switch APIs):** The admin uses Base UI variants. Confirm
`Select`'s `onValueChange` and `Switch`'s `onCheckedChange` prop names match the admin
components (`grep -n "onValueChange\|onCheckedChange" apps/admin/components/ui/select.tsx apps/admin/components/ui/switch.tsx`). Adjust prop names if Base UI differs.

- [ ] **Step 3: Build to verify**

Run: `npm run build --workspace=apps/admin`
Expected: build succeeds; `/pebblestore/glyphs/new` route emitted.

- [ ] **Step 4: Commit**

```bash
git add "apps/admin/app/(authed)/pebblestore/glyphs/new"
git commit -m "feat(admin): glyph upload + adjust flow (#497)"
```

---

## Task 11: Sidebar navigation

**Files:**
- Modify: `apps/admin/components/layout/Sidebar.tsx`

- [ ] **Step 1: Add the Pebblestore group**

Add an icon import and a new `SidebarGroup` between "Insights" and "Logs". Use a lucide icon
already available (e.g. `Store`).

Add to the icon import line:
```tsx
import { BarChart3, Megaphone, Sparkles, Store } from "lucide-react"
```

Add this constant near the others:
```tsx
const PEBBLESTORE_ITEMS = [
  { href: "/pebblestore/glyphs", label: "Glyph moderation", icon: Store },
] as const
```

Add this `SidebarGroup` block before the "Logs" group:
```tsx
        <SidebarGroup>
          <SidebarGroupLabel>Pebblestore</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              {PEBBLESTORE_ITEMS.map(({ href, label, icon: Icon }) => {
                const active = pathname === href || pathname.startsWith(`${href}/`)
                return (
                  <SidebarMenuItem key={href}>
                    <SidebarMenuButton render={<Link href={href} />} isActive={active}>
                      <Icon aria-hidden />
                      <span>{label}</span>
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                )
              })}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
```

- [ ] **Step 2: Build to verify**

Run: `npm run build --workspace=apps/admin`
Expected: build succeeds.

- [ ] **Step 3: Commit**

```bash
git add apps/admin/components/layout/Sidebar.tsx
git commit -m "feat(admin): pebblestore sidebar group (#497)"
```

---

## Task 12: Web cross-app — show reject reason on the "Mine" badge

**Files:**
- Modify: C's glyph "Mine"-tab badge component in `apps/web/components/glyphs/`
- Modify: `apps/web/messages/en.json` + `apps/web/messages/fr.json` (or the repo's locale files)

- [ ] **Step 1: Locate the badge and the submission status source**

Run: `grep -rn "rejected\|review_note\|submission\|status" apps/web/components/glyphs/ | grep -i "badge\|status\|reject"`
Identify where C renders the `rejected` status badge (e.g. `GlyphDetail.tsx` or a card badge),
and the hook that exposes the submission (likely `useGlyphSubmissions`). Confirm the
submission object carries `review_note` (the C-side `GlyphSubmission` type may need
`review_note?: string | null` added — check `apps/web/lib/types.ts` and the mapper in
`SupabaseProvider`).

- [ ] **Step 2: Surface the note**

Where the rejected badge renders, add the reason text. Example (adapt to the actual element):
```tsx
{submission?.status === "rejected" && submission.review_note ? (
  <p className="text-xs text-muted-foreground">
    {t("submit.rejectedReason", { reason: submission.review_note })}
  </p>
) : null}
```
If `review_note` isn't on the web `GlyphSubmission` type / mapper, add it: type field
`review_note: string | null` and select `review_note` in the submissions query/mapper.

- [ ] **Step 3: Add EN/FR copy**

`en.json` (under the existing `glyphs.submit` namespace):
```json
"rejectedReason": "Rejected: {reason}"
```
`fr.json`:
```json
"rejectedReason": "Refusé : {reason}"
```
Match the existing key nesting/format used by C's `glyphs.submit.*` keys (verify with
`grep -n "\"submit\"" apps/web/messages/en.json`).

- [ ] **Step 4: Lint + build web**

Run: `npm run lint --workspace=apps/web` then `npm run build --workspace=apps/web`
Expected: both green.

- [ ] **Step 5: Commit**

```bash
git add apps/web
git commit -m "feat(ui): show glyph rejection reason on Mine tab (#497)"
```

---

## Task 13: Arkaik map + final verification

**Files:**
- Modify: `docs/arkaik/bundle.json`

- [ ] **Step 1: Update the Arkaik bundle**

Invoke the `arkaik` skill. Add two admin view nodes under the back-office surface:
"Glyph moderation" (`/pebblestore/glyphs`) and "Upload glyph" (`/pebblestore/glyphs/new`),
plus edges from the admin shell. Follow the skill's schema exactly.

- [ ] **Step 2: Full lint + build (both apps)**

Run:
```bash
npm run lint --workspace=apps/admin
npm run lint --workspace=apps/web
npm run build --workspace=apps/admin
npm run build --workspace=apps/web
```
Expected: all green.

- [ ] **Step 3: Manual verification (spec §9)**

With an authenticated admin session and at least one `pending` submission from C:
1. Pending submission shows in the queue with a faithful preview + submitter + price.
2. Approve → glyph live in the web Market; a non-admin can buy it; `reviewed_*` set.
3. Reject with a note → status `rejected`, reason stored; web "Mine" tab shows the reason;
   blank-note reject is refused (`missing_note` → inline error).
4. Re-price an approved listing → new buyers pay the new price; old `price_paid` unchanged.
5. Upload a line-art SVG → imports cleanly; an SVG with `<rect>`/fills → skipped readout +
   outline-only preview; malformed SVG → inline error.
6. Adjust scale/recenter/flip → preview updates; Publish bakes the transform and the
   published glyph matches the preview in the real pebble render.
7. Published first-party glyph is admin-owned, live in Market, buyable by others;
   `cannot_buy_own` blocks the admin from buying it.
8. Auth: confirm a non-admin caller gets `not_admin` from the RPCs and `/pebblestore/glyphs`
   redirects a non-admin to `/403`.

- [ ] **Step 4: Commit**

```bash
git add docs/arkaik/bundle.json
git commit -m "docs(arkaik): admin glyph moderation view nodes (#497)"
```

---

## Self-review notes (for the implementer)

- **Spec coverage:** moderation queue (T9) ✓; approve/reject (T1 RPCs + T8 actions + T9 UI) ✓;
  reject reason stored + shown (T1 column, T9 admin display, T12 web display) ✓; SVG upload +
  adjust + preview (T3–T7, T10) ✓; documented SVG subset (T4 header + spec §4) ✓; price/curation
  (T1 `set_glyph_price` / approve override, T9 re-price) ✓; first-party admin-owned publish
  (T1 `publish_admin_glyph`) ✓; `is_admin`-gated end-to-end (every RPC guards; route under
  `requireAdmin()`) ✓; Arkaik + decision log (T13 + PR step) ✓.
- **Type consistency:** RPC arg names (`p_status`, `p_submission_id`, `p_price`, `p_note`,
  `p_name`, `p_shape_id`, `p_strokes`, `p_view_box`) are identical across Task 1 (SQL),
  Task 8 (actions), and Task 1's regenerated types. `GlyphStroke`/`Adjust`/`AdminSubmission`/
  `PebbleShape` are defined once (T2) and reused. `buildAdjustMatrix`/`bakeAdjust`/
  `fitTransform`/`matrixToTransform` names match across T5, T6, T10.
- **Open verification flags carried from the spec (resolve during implementation):** exact
  Supabase error-message shape for the RPC contract (T8 Step 2 note); Base UI prop names for
  `Button render`/`Select`/`Switch` (T9/T10 Step 2.5); the C-side `GlyphSubmission` type may
  need a `review_note` field (T12 Step 1).
