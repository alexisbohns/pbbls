# Icon forms amendment (part 3, model and review tool) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the generator and the design page to the amended forms model: roles `body` (optionally split into parts), `mark` and `step`; a form is a fill (regular, solid, parts, progress, dotted) plus `marked`; and the Icons page filters and draws by fill, mark and progress, with small signs per tile.

**Architecture:** `packages/icons` reads the new roles (`roles.ts`), carves every solid and parts fill at build time, with and without the mark cut out (`carve.ts`, `emit-glyphs.ts`), and stores them in `glyphs.json` keyed by form key (`forms.ts`). A drawn `src/<name>.dotted.svg` becomes the `dotted` form. The web design page mirrors `renderForm` in `apps/web/lib/design/glyph-forms.ts` (part 4 replaces it with the real components) and replaces the single Form select with three pickers that both filter and draw.

**Tech Stack:** TypeScript (tsx, Vitest), `clipper-lib` (already a dependency), Next.js 16 / React 19 (the sandbox design page), Tailwind 4.

**Spec:** `docs/superpowers/specs/2026-11-08-icon-forms-design.md` as amended on 2026-11-09 (commit 9d46cbc1e): D1–D4, D6, §1–§3. Decision log entry "2026-11-09 — Icon forms: a fill plus a mark".

**Branch:** `chore/1607-icon-roles` (part 3, already checked out, 16 commits ahead of `main`). Do not create a branch. Never touch the untracked `apps/web/AGENTS.md`.

**Out of scope (part 3b, its own plan):** redrawing and retagging glyphs from the maintainer's notes, the merges (`pebble-glyph-dotted` → `pebble-glyph`, `palette-1`/`palette-3` → `palette`), dropping twins.

**Commit trailer:** every commit message ends with a blank line and `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

**Commands** (run from the repo root):

| What | Command |
|---|---|
| package tests | `npm run test --workspace=@pbbls/icons` (one file: append `-- scripts/lib/roles.test.ts`) |
| package typecheck | `npm run build --workspace=@pbbls/icons` |
| sources lint | `npm run lint --workspace=@pbbls/icons` |
| sources format | `npm run format --workspace=@pbbls/icons` |
| generate outputs | `npm run generate --workspace=@pbbls/icons` |
| freshness | `npm run check --workspace=@pbbls/icons` |
| web tests | `npm run test --workspace=@pbbls/web` (one file: append `-- lib/design/glyph-forms.test.ts`) |
| web lint | `npm run lint --workspace=@pbbls/web` |
| web typecheck | `cd apps/web && npx tsc --noEmit` |

---

## File structure

| File | Responsibility | Task |
|---|---|---|
| `packages/icons/scripts/lib/carve.ts` | carve a fill: lines within half a stroke of the body cut, holes (the mark) cut filled | 1 |
| `packages/icons/scripts/lib/roles.ts` | read `body`/`data-part`, `mark`, `step`/`data-step`, `data-rest` | 2 |
| `packages/icons/scripts/lib/forms.ts` | `GlyphData`, `Fill`, `Form`, form keys, `formKeysOf`, `renderForm` | 2 |
| `packages/icons/scripts/lib/emit-glyphs.ts` | build a glyph: paths with roles, carved fills per form key, dotted, overrides | 2, 3 |
| `packages/icons/scripts/lib/manifest.ts` | `isOverrideForm` from form keys, `DOTTED_FORM` | 2, 3 |
| `packages/icons/scripts/lib/lint.ts` | role attributes, override forms, form words, dotted drawings | 2, 3 |
| `packages/icons/scripts/lib/states.ts`, `packages/icons/states.json` | `half` → `marked`; resolve to the new `Form` | 2 |
| `packages/icons/src/*.svg` | `focus` → `mark`, numbered `mark` → `step` | 2 |
| `packages/icons/DNA.md`, `packages/icons/CLAUDE.md`, the spec, the parts 1–3 plan | docs | 4 |
| `apps/web/lib/design/glyph-forms.ts` (+ test) | mirror of `renderForm`, page view filters, signs | 5 |
| `apps/web/components/sandbox/design/{DesignContext,DesignDirectory,DesignTools}.tsx` | the Fill / Mark / Progress pickers | 6 |
| `apps/web/components/sandbox/design/GlyphSigns.tsx` (new), `pages/IconsPage.tsx` | tile signs, filtered list | 7 |
| `apps/web/components/sandbox/design/{GlyphFormView,GlyphReviewDialog}.tsx` | draw any `Form`; dialog grid and roles legend | 8 |

---

### Task 1: Carve lines that stand on the body's edge, and cut holes

**Files:**
- Modify: `packages/icons/scripts/lib/carve.ts`
- Test: `packages/icons/scripts/lib/carve.test.ts`

Today a line is cut out of the solid only when every point lies inside the body's fill, so `home`'s door, whose feet stand on the tent's bottom edge, is never carved. The amended spec D3 cuts any line within half a stroke of the fill. The marked solid also needs to cut the mark out as a filled shape (fill + stroke band): those are `holes`.

- [ ] **Step 1: Write the failing tests**

Append inside the `describe("carveSolid", …)` block of `carve.test.ts`:

```ts
  it("cuts a line standing on the body's edge", () => {
    // A door whose feet stand on the bottom edge (y = 20).
    const { keep } = carveSolid([SQUARE], ["M10 20 L10 15 L14 15 L14 20"])
    expect(keep).toEqual([])
  })

  it("keeps a line further than half a stroke from the body", () => {
    // x = 2 is 2 units left of the edge at x = 4.
    const { keep } = carveSolid([SQUARE], ["M2 4 L2 20"])
    expect(keep).toEqual([0])
  })

  it("cuts a hole as a filled shape and reports a hole outside the body", () => {
    const dot = "M11 11 L13 11 L13 13 L11 13 Z"
    const whole = carveSolid([SQUARE], []).area
    const { area, outside, keep } = carveSolid([SQUARE], [], [dot, "M1.6 1.6 L2 2 L1.6 2 Z"])
    // A 2×2 square grown by half a stroke, round corners: 2² + 4·2·0.875 + π·0.875².
    const cut = 4 + 8 * 0.875 + Math.PI * 0.875 * 0.875
    expect(whole - area).toBeCloseTo(cut, 0)
    expect(outside).toEqual([1])
    expect(keep).toEqual([])
  })
```

- [ ] **Step 2: Run them to see them fail**

Run: `npm run test --workspace=@pbbls/icons -- scripts/lib/carve.test.ts`
Expected: FAIL: the door is kept (`keep` is `[0]`), and `outside` is undefined.

- [ ] **Step 3: Implement**

In `carve.ts`, replace the `Carved` type and the whole `carveSolid` function with:

```ts
export type Carved = {
  /** One compound path, filled even-odd with no stroke. */
  d: string
  /** Indices (into `others`) of the paths outside the body: drawn stroked on top. */
  keep: number[]
  /** Indices (into `holes`) of the holes outside the body: not cut, drawn filled on top. */
  outside: number[]
  /** Filled area in square glyph units. */
  area: number
}

/** A path as SVG paints it filled and stroked: its fill (each subpath implicitly closed) plus its stroke band. */
function paintedRegion(d: string): IntPath[] {
  const subs = flattenPath(d)
  const fill = boolean(ClipperLib.ClipType.ctUnion, subs.filter((s) => s.points.length >= 3).map((s) => toInt(s.points)), [])
  const band = subs.flatMap((s) => {
    const end = isClosed(s) ? ClipperLib.EndType.etClosedLine : ClipperLib.EndType.etOpenRound
    return offset([toInt(s.points)], ClipperLib.JoinType.jtRound, end, HALF_STROKE)
  })
  return boolean(ClipperLib.ClipType.ctUnion, fill, band)
}

/**
 * The solid form (spec D3, D8), as SVG paints a path that is filled and stroked:
 * the body's subpaths, each implicitly closed for the fill and unioned, plus
 * their stroke band (a line offset by half the stroke), minus every other path
 * lying within half a stroke of the fill (a door standing on the ground line
 * counts), outlined at the stroke width with round caps, and minus every hole
 * there (the mark of a marked solid) as it is painted, fill and stroke.
 */
export function carveSolid(body: readonly string[], others: readonly string[], holes: readonly string[] = []): Carved {
  const bodySubs = body.map((d) => flattenPath(d).filter((s) => s.points.length >= 3))
  const subs = bodySubs.flat()
  // SVG fills each path on its own (non-zero over that path's subpaths), so two paths
  // wound in opposite directions must not cancel: fill per path, then union the regions.
  // Clipper returns outer rings and holes with opposite, normalised orientations, so the
  // final non-zero union keeps a hole open unless another path covers it.
  const regions = bodySubs.flatMap((paths) => boolean(ClipperLib.ClipType.ctUnion, paths.map((s) => toInt(s.points)), []))
  const fill = boolean(ClipperLib.ClipType.ctUnion, regions, [])
  const band = subs.flatMap((s) => {
    const end = isClosed(s) ? ClipperLib.EndType.etClosedLine : ClipperLib.EndType.etOpenRound
    return offset([toInt(s.points)], ClipperLib.JoinType.jtRound, end, HALF_STROKE)
  })
  const base = boolean(ClipperLib.ClipType.ctUnion, fill, band)
  // A line counts as inside when it lies within half a stroke of the fill.
  const reach = offset(fill, ClipperLib.JoinType.jtRound, ClipperLib.EndType.etClosedPolygon, HALF_STROKE)
  const keep: number[] = []
  const cutters: IntPath[] = []
  others.forEach((d, i) => {
    if (!inside(d, reach)) {
      keep.push(i)
      return
    }
    for (const sub of flattenPath(d)) {
      const end = isClosed(sub) ? ClipperLib.EndType.etClosedLine : ClipperLib.EndType.etOpenRound
      cutters.push(...offset([toInt(sub.points)], ClipperLib.JoinType.jtRound, end, HALF_STROKE))
    }
  })
  const outside: number[] = []
  holes.forEach((d, i) => {
    if (!inside(d, reach)) outside.push(i)
    else cutters.push(...paintedRegion(d))
  })
  const result = cutters.length > 0 ? boolean(ClipperLib.ClipType.ctDifference, base, boolean(ClipperLib.ClipType.ctUnion, cutters, [])) : base
  const area = result.reduce((sum, ring) => sum + ClipperLib.Clipper.Area(ring), 0) / (SCALE * SCALE)
  const d = result
    .filter((ring) => ring.length > 2)
    .map((ring) => ring.map((pt, i) => `${i === 0 ? "M" : "L"}${fmt(pt.X)} ${fmt(pt.Y)}`).join(" ") + " Z")
    .join(" ")
  return { d, keep, outside, area: Math.abs(area) }
}
```

Note the cutters are unioned before the difference, so a hole's fill and its stroke band (opposite ring orientations) cannot cancel under the non-zero rule.

- [ ] **Step 4: Run the package tests**

Run: `npm run test --workspace=@pbbls/icons`
Expected: PASS, every file (the other callers ignore the new `outside` field).

- [ ] **Step 5: Regenerate and list the solids that changed**

```bash
git show HEAD:packages/icons/glyphs/glyphs.json > /private/tmp/claude-503/-Users-alexis-code-pbbls/d5cce965-85cd-4a54-952e-f04224f61d58/scratchpad/glyphs-before.json
npm run generate --workspace=@pbbls/icons
node -e '
const a = require("/private/tmp/claude-503/-Users-alexis-code-pbbls/d5cce965-85cd-4a54-952e-f04224f61d58/scratchpad/glyphs-before.json").glyphs, b = require("./packages/icons/glyphs/glyphs.json").glyphs
const before = new Map(a.map((g) => [g.name, g]))
const changed = b.filter((g) => JSON.stringify(g.solid) !== JSON.stringify(before.get(g.name)?.solid) || JSON.stringify(g.paths) !== JSON.stringify(before.get(g.name)?.paths)).map((g) => g.name)
console.log(changed.length, "glyphs changed"); console.log(changed.join("\n"))' > /private/tmp/claude-503/-Users-alexis-code-pbbls/d5cce965-85cd-4a54-952e-f04224f61d58/scratchpad/carve-reach-changed.txt
head -1 /private/tmp/claude-503/-Users-alexis-code-pbbls/d5cce965-85cd-4a54-952e-f04224f61d58/scratchpad/carve-reach-changed.txt
npm run lint --workspace=@pbbls/icons
```

Expected: lint exits 0 (if a solid now falls under the 4-unit floor, the lint names it: report it, do not change the rule). Report the count and copy `/private/tmp/claude-503/-Users-alexis-code-pbbls/d5cce965-85cd-4a54-952e-f04224f61d58/scratchpad/carve-reach-changed.txt` into your report verbatim; the controller shows those solids to the maintainer before the PR.

- [ ] **Step 6: Commit**

```bash
git add packages/icons/scripts/lib/carve.ts packages/icons/scripts/lib/carve.test.ts packages/icons/glyphs/glyphs.json
git commit -m "chore(ui): carve lines standing on the body's edge and cut holes from a solid

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Roles body/parts, mark, step; forms as a fill plus the mark

**Files:**
- Modify: `packages/icons/scripts/lib/roles.ts`, `roles.test.ts`
- Modify: `packages/icons/scripts/lib/forms.ts`, `forms.test.ts`
- Modify: `packages/icons/scripts/lib/emit-glyphs.ts`, `emit-glyphs.test.ts`
- Modify: `packages/icons/scripts/lib/manifest.ts`
- Modify: `packages/icons/scripts/lib/lint.ts`, `lint.test.ts`
- Modify: `packages/icons/scripts/lib/states.ts`, `states.test.ts`, `packages/icons/states.json`
- Modify: every `packages/icons/src/*.svg` carrying `data-role="focus"` or `data-mark`
- Modify (keep the page compiling): `apps/web/lib/design/glyph-forms.ts`, `apps/web/lib/design/glyph-forms.test.ts`, `apps/web/components/sandbox/design/{GlyphFormView,GlyphReviewDialog,DesignTools}.tsx`

This is one task because the role names, `GlyphData` and `renderForm` must change together for the package to typecheck. The web page only needs to keep compiling and drawing here (Task 5 rewrites its data layer).

- [ ] **Step 1: Rename the roles in the sources**

```bash
cd packages/icons
grep -l 'data-mark' src/*.svg | wc -l          # expect 215
grep -L 'data-role="mark" data-mark=' $(grep -l 'data-mark' src/*.svg) | wc -l   # expect 0: data-mark always follows data-role="mark"
sed -i '' -E 's/data-role="mark" data-mark="([0-9]+)"/data-role="step" data-step="\1"/g' src/*.svg
sed -i '' 's/data-role="focus"/data-role="mark"/g' src/*.svg
grep -c 'data-mark\|data-role="focus"' src/*.svg | grep -v ':0$' | wc -l   # expect 0
cd ../..
```

If the second count is not 0, stop and report the files: the sed would leave them half-renamed.

- [ ] **Step 2: Write `roles.ts`**

Replace the whole file with:

```ts
import type { ParsedPath } from "./parse"

/**
 * A glyph's roles (spec D2), as indices into its paths: the body (unioned) and
 * its parts in source order, the mark, and the steps in fill order (`steps[0]`
 * is step 1, possibly several paths). Untagged paths are plain lines. `hidden`
 * lists the mark and step paths that carry `data-rest="hidden"`: not drawn at
 * rest, drawn filled when their role fills.
 */
export type GlyphRoles = {
  body: number[]
  /** The body's parts in the order they first appear; empty when the body is whole. */
  parts: { name: string; paths: number[] }[]
  mark: number[]
  steps: number[][]
  hidden: number[]
}

export const ROLES = ["body", "mark", "step"] as const
/** A body splits into 2…4 parts at most: 4 parts already give 14 combinations. */
export const MAX_PARTS = 4
const PART_NAME = /^[a-z]+$/

export function readRoles(paths: readonly ParsedPath[]): { roles: GlyphRoles; issues: string[] } {
  const issues: string[] = []
  const body: number[] = []
  const mark: number[] = []
  const hidden: number[] = []
  const parts: { name: string; paths: number[] }[] = []
  let unnamed = 0
  const bySteps = new Map<number, number[]>()
  paths.forEach((path, i) => {
    const where = `path ${i + 1}`
    const role = path.attrs["data-role"]
    const step = path.attrs["data-step"]
    const part = path.attrs["data-part"]
    const rest = path.attrs["data-rest"]
    if (rest !== undefined) {
      if (role !== "mark" && role !== "step") issues.push(`${where}: data-rest is only allowed on a mark or step`)
      else if (rest !== "hidden") issues.push(`${where}: data-rest="${rest}" is not hidden`)
      else hidden.push(i)
    }
    if (step !== undefined && role !== "step") issues.push(`${where}: data-step without data-role="step"`)
    if (part !== undefined && role !== "body") issues.push(`${where}: data-part without data-role="body"`)
    if (role === undefined) return
    if (role === "body") {
      body.push(i)
      if (part === undefined) unnamed += 1
      else if (!PART_NAME.test(part)) issues.push(`${where}: data-part="${part}" is not one lowercase word`)
      else {
        const known = parts.find((p) => p.name === part)
        if (known) known.paths.push(i)
        else parts.push({ name: part, paths: [i] })
      }
    } else if (role === "mark") mark.push(i)
    else if (role === "step") {
      const n = step === undefined ? NaN : Number(step)
      if (!Number.isInteger(n) || n < 1) {
        issues.push(`${where}: a step needs data-step`)
        return
      }
      bySteps.set(n, [...(bySteps.get(n) ?? []), i])
    } else issues.push(`${where}: data-role="${role}" is not body, mark or step`)
  })
  if (parts.length > 0 && unnamed > 0) issues.push("when one body path names a part, every body path does")
  if (parts.length === 1) issues.push(`a body with one part (${parts[0]?.name}) is a whole body: drop data-part`)
  if (parts.length > MAX_PARTS) issues.push(`${parts.length} parts; a body has at most ${MAX_PARTS}`)
  const numbers = [...bySteps.keys()].sort((a, b) => a - b)
  if (numbers.some((n, i) => n !== i + 1)) issues.push(`steps must run 1…n without gaps; found ${numbers.join(", ")}`)
  const steps = numbers.map((n) => bySteps.get(n) ?? [])
  return { roles: { body, parts: parts.length >= 2 ? parts : [], mark, steps, hidden }, issues }
}

/**
 * Every combination of parts short of all of them (all of them is the solid),
 * smallest first, each in the parts' order: [left], [middle], [left, middle], …
 */
export function partCombinations(names: readonly string[]): string[][] {
  const out: string[][] = []
  const n = names.length
  for (let bits = 1; bits < (1 << n) - 1; bits += 1) out.push(names.filter((_, i) => bits & (1 << i)))
  return out.sort((a, b) => a.length - b.length)
}
```

- [ ] **Step 3: Write `roles.test.ts`**

Replace the whole file with:

```ts
import { describe, expect, it } from "vitest"
import type { ParsedPath } from "./parse"
import { partCombinations, readRoles } from "./roles"

const p = (attrs: Record<string, string>): ParsedPath => ({ d: attrs.d ?? "M3 3 L4 4", filled: false, evenOdd: false, unstroked: false, attrs: { d: "M3 3 L4 4", ...attrs } })

describe("readRoles", () => {
  it("collects body, mark and numbered steps by path index", () => {
    const { roles, issues } = readRoles([
      p({ "data-role": "body" }),
      p({}),
      p({ "data-role": "step", "data-step": "2" }),
      p({ "data-role": "step", "data-step": "1" }),
      p({ "data-role": "step", "data-step": "1" }),
      p({ "data-role": "mark" }),
    ])
    expect(issues).toEqual([])
    expect(roles).toEqual({ body: [0], parts: [], mark: [5], steps: [[3, 4], [2]], hidden: [] })
  })

  it("has no roles for a plain drawing", () => {
    expect(readRoles([p({}), p({})]).roles).toEqual({ body: [], parts: [], mark: [], steps: [], hidden: [] })
  })

  it("reports unknown roles, missing or stray step numbers and gaps", () => {
    const { issues } = readRoles([
      p({ "data-role": "halo" }),
      p({ "data-role": "step" }),
      p({ "data-step": "1" }),
      p({ "data-role": "step", "data-step": "3" }),
    ])
    expect(issues).toEqual([
      'path 1: data-role="halo" is not body, mark or step',
      "path 2: a step needs data-step",
      'path 3: data-step without data-role="step"',
      "steps must run 1…n without gaps; found 3",
    ])
  })

  it("collects the hidden mark and step paths", () => {
    const { roles, issues } = readRoles([
      p({ "data-role": "body" }),
      p({ "data-role": "step", "data-step": "1", "data-rest": "hidden" }),
      p({ "data-role": "mark", "data-rest": "hidden" }),
    ])
    expect(issues).toEqual([])
    expect(roles.hidden).toEqual([1, 2])
    expect(roles.steps).toEqual([[1]])
    expect(roles.mark).toEqual([2])
  })

  it("reports data-rest on a body or a plain line, and any value but hidden", () => {
    const { issues } = readRoles([
      p({ "data-role": "body", "data-rest": "hidden" }),
      p({ "data-rest": "hidden" }),
      p({ "data-role": "mark", "data-rest": "shown" }),
    ])
    expect(issues).toEqual([
      "path 1: data-rest is only allowed on a mark or step",
      "path 2: data-rest is only allowed on a mark or step",
      'path 3: data-rest="shown" is not hidden',
    ])
  })

  it("reads a body split into named parts, in source order", () => {
    const { roles, issues } = readRoles([
      p({ "data-role": "body", "data-part": "moon" }),
      p({ "data-role": "body", "data-part": "cloud" }),
      p({ "data-role": "body", "data-part": "moon" }),
      p({}),
    ])
    expect(issues).toEqual([])
    expect(roles.body).toEqual([0, 1, 2])
    expect(roles.parts).toEqual([
      { name: "moon", paths: [0, 2] },
      { name: "cloud", paths: [1] },
    ])
  })

  it("reports parts on some body paths only, one part, a bad name, too many, and a part off the body", () => {
    const body = (part?: string) => p(part === undefined ? { "data-role": "body" } : { "data-role": "body", "data-part": part })
    expect(readRoles([body("a"), body(), body("b")]).issues).toEqual(["when one body path names a part, every body path does"])
    expect(readRoles([body("moon")]).issues).toEqual(["a body with one part (moon) is a whole body: drop data-part"])
    expect(readRoles([body("Moon-1")]).issues).toEqual(['path 1: data-part="Moon-1" is not one lowercase word'])
    expect(readRoles(["a", "b", "c", "d", "e"].map((n) => body(n))).issues).toEqual(["5 parts; a body has at most 4"])
    expect(readRoles([p({ "data-role": "mark", "data-part": "moon" })]).issues).toEqual(['path 1: data-part without data-role="body"'])
  })
})

describe("partCombinations", () => {
  it("lists every combination short of all of them, smallest first", () => {
    expect(partCombinations(["left", "middle", "right"])).toEqual([["left"], ["middle"], ["right"], ["left", "middle"], ["left", "right"], ["middle", "right"]])
    expect(partCombinations(["moon", "cloud"])).toEqual([["moon"], ["cloud"]])
    expect(partCombinations([])).toEqual([])
  })
})
```

- [ ] **Step 4: Write `forms.ts`**

Replace the whole file with:

```ts
import type { Tags } from "./manifest"
import { partCombinations } from "./roles"

/** One path of a glyph in `glyphs.json`: normalised data and its role. A hidden path (mark or step) is not drawn until its role fills. */
export type GlyphPath = {
  d: string
  role?: "body" | "mark" | "step"
  /** The body part this path belongs to, when the body is split (spec D2). */
  part?: string
  /** A step's number, 1…n. */
  step?: number
  hidden?: true
  /** Mark or step with other lines inside it: the path filled and stroked, minus those lines outlined at the stroke (even-odd, unstroked). */
  fill?: string
  /** Indices into `paths` of the lines `fill` is carved through; they are not drawn when this path is filled. */
  cuts?: number[]
}

/** A path as a form draws it. */
export type RenderedPath = { d: string; fill: boolean; evenOdd: boolean; stroke: boolean }

/**
 * A solid or parts fill worked out at build time: the carved fill, the paths
 * drawn stroked on top of it (`keep`) and the mark paths drawn filled beside it
 * (`lit`: a mark outside the fill, under `marked`). Indices are into `paths`.
 */
export type CarvedFill = { d: string; keep: number[]; lit: number[] }

/** A glyph as `glyphs.json` stores it (spec §2): the contract every surface reads. */
export type GlyphData = {
  name: string
  meaning: string
  family: string | null
  tags: Tags
  paths: GlyphPath[]
  /** The body's parts in source order; empty when the body is whole. */
  parts: string[]
  /** The number of steps, n of progress(k). */
  steps: number
  /** Every solid and parts form, carved, by form key: `solid`, `solid-marked`, `parts-moon`, `parts-moon-marked`, … */
  carved: Record<string, CarvedFill>
  /** The drawn dotted form (`src/<name>.dotted.svg`), or null. */
  dotted: RenderedPath[] | null
  /** Hand-drawn replacements, keyed by form key (`marked`, `solid`, `progress-2`). */
  overrides: Record<string, RenderedPath[]>
}

export type Fill =
  | { kind: "regular" }
  | { kind: "solid" }
  | { kind: "parts"; parts: string[] }
  | { kind: "progress"; k: number }
  | { kind: "dotted" }

/** A form (spec D3): a fill, plus the mark when the glyph has one. */
export type Form = { fill: Fill; marked?: boolean }

export function fillKey(fill: Fill): string {
  switch (fill.kind) {
    case "parts":
      return `parts-${fill.parts.join("+")}`
    case "progress":
      return `progress-${fill.k}`
    default:
      return fill.kind
  }
}

/** `regular` marked is `marked`; any other fill takes a `-marked` suffix. */
export function formKey(form: Form): string {
  const fill = fillKey(form.fill)
  if (!form.marked) return fill
  return fill === "regular" ? "marked" : `${fill}-marked`
}

const KEY = /^(regular|marked|dotted|solid|parts-([a-z]+(?:\+[a-z]+)*)|progress-(\d+))(-marked)?$/

/** The form a key names, or null when it is not a form key. */
export function parseFormKey(key: string): Form | null {
  const m = KEY.exec(key)
  if (!m) return null
  const [, head, parts, k, marked] = m
  if (head === "marked") return marked ? null : { fill: { kind: "regular" }, marked: true }
  if ((head === "regular" || head === "dotted") && marked) return null
  let fill: Fill
  if (parts !== undefined) fill = { kind: "parts", parts: parts.split("+") }
  else if (k !== undefined) fill = { kind: "progress", k: Number(k) }
  else if (head === "solid") fill = { kind: "solid" }
  else if (head === "dotted") fill = { kind: "dotted" }
  else fill = { kind: "regular" }
  return marked ? { fill, marked: true } : { fill }
}

/** What decides a glyph's forms. */
export type FormShape = { mark: boolean; body: boolean; parts: readonly string[]; steps: number; dotted: boolean }

/** The forms a glyph has, as keys, in a stable order (spec D3): each fill then the same fill marked, dotted after regular. */
export function formKeysOf(shape: FormShape): string[] {
  const fills: Fill[] = [{ kind: "regular" }]
  if (shape.body) fills.push({ kind: "solid" })
  for (const parts of partCombinations(shape.parts)) fills.push({ kind: "parts", parts })
  for (let k = 1; k <= shape.steps; k += 1) fills.push({ kind: "progress", k })
  const keys = fills.flatMap((fill) => (shape.mark ? [formKey({ fill }), formKey({ fill, marked: true })] : [formKey({ fill })]))
  if (shape.dotted) keys.splice(shape.mark ? 2 : 1, 0, "dotted")
  return keys
}

export function shapeOf(glyph: GlyphData): FormShape {
  return {
    mark: glyph.paths.some((p) => p.role === "mark"),
    body: glyph.paths.some((p) => p.role === "body"),
    parts: glyph.parts,
    steps: glyph.steps,
    dotted: glyph.dotted !== null,
  }
}

/** The forms a glyph has, as keys (spec D3). */
export function formsOf(glyph: GlyphData): string[] {
  return formKeysOf(shapeOf(glyph))
}

const stroked = (d: string): RenderedPath => ({ d, fill: false, evenOdd: false, stroke: true })
const filled = (d: string): RenderedPath => ({ d, fill: true, evenOdd: false, stroke: true })
const carvedPath = (d: string): RenderedPath => ({ d, fill: true, evenOdd: true, stroke: false })
/** A filled mark or step: its carved fill when lines run inside it. */
const lit = (p: GlyphPath): RenderedPath => (p.fill !== undefined ? carvedPath(p.fill) : filled(p.d))

/** The paths a form draws, overrides first (spec D3, D4). `progress(0)` is regular. */
export function renderForm(glyph: GlyphData, form: Form): RenderedPath[] {
  if (form.fill.kind === "progress" && form.fill.k === 0) return renderForm(glyph, { fill: { kind: "regular" }, marked: form.marked })
  const key = formKey(form)
  if (!formsOf(glyph).includes(key)) throw new Error(`${glyph.name} has no ${key} form`)
  const override = glyph.overrides[key]
  if (override) return override
  if (form.fill.kind === "dotted") return glyph.dotted ?? []
  if (form.fill.kind === "solid" || form.fill.kind === "parts") {
    const carved = glyph.carved[key]
    if (!carved) throw new Error(`${glyph.name}: no carved ${key}`)
    return [
      carvedPath(carved.d),
      ...carved.keep.flatMap((i) => {
        const p = glyph.paths[i]
        return p ? [stroked(p.d)] : []
      }),
      ...carved.lit.flatMap((i) => {
        const p = glyph.paths[i]
        return p ? [lit(p)] : []
      }),
    ]
  }
  const k = form.fill.kind === "progress" ? form.fill.k : 0
  // A hidden path is drawn only when its role fills it; everything else is stroked.
  // A filled path with a carved `fill` draws that instead, and the lines it is carved through are left out.
  const fills = (p: GlyphPath) => (form.marked === true && p.role === "mark") || (p.role === "step" && (p.step ?? 0) <= k)
  const cut = new Set(glyph.paths.flatMap((p) => (fills(p) && p.fill !== undefined ? (p.cuts ?? []) : [])))
  return glyph.paths.flatMap((p, i) => {
    const on = fills(p)
    if ((p.hidden && !on) || cut.has(i)) return []
    return [on ? lit(p) : stroked(p.d)]
  })
}
```

- [ ] **Step 5: Write `forms.test.ts`**

Replace the whole file with:

```ts
import { describe, expect, it } from "vitest"
import { formKey, formKeysOf, formsOf, parseFormKey, renderForm, type Form, type GlyphData } from "./forms"

const SQ = "M4 4 L20 4 L20 20 L4 20 Z"
const regular: Form = { fill: { kind: "regular" } }

const glyph: GlyphData = {
  name: "path-river",
  meaning: "m",
  family: null,
  tags: {},
  paths: [
    { d: SQ, role: "body" },
    { d: "M8 12 L16 12" },
    { d: "M6 6 L7 7", role: "step", step: 1 },
    { d: "M9 9 L10 10", role: "step", step: 2 },
    { d: "M12 12 L13 13", role: "mark" },
  ],
  parts: [],
  steps: 2,
  carved: { solid: { d: "M1 1 L2 2 Z", keep: [], lit: [] }, "solid-marked": { d: "M1 1 L3 3 Z", keep: [], lit: [] } },
  dotted: null,
  overrides: {},
}

describe("form keys", () => {
  it("joins a fill and the mark", () => {
    expect(formKey(regular)).toBe("regular")
    expect(formKey({ fill: { kind: "regular" }, marked: true })).toBe("marked")
    expect(formKey({ fill: { kind: "solid" }, marked: true })).toBe("solid-marked")
    expect(formKey({ fill: { kind: "parts", parts: ["left", "middle"] } })).toBe("parts-left+middle")
    expect(formKey({ fill: { kind: "progress", k: 2 }, marked: true })).toBe("progress-2-marked")
  })

  it("parses every key it writes and refuses the rest", () => {
    for (const key of ["regular", "marked", "dotted", "solid", "solid-marked", "parts-moon", "parts-left+middle-marked", "progress-3", "progress-3-marked"]) {
      const form = parseFormKey(key)
      expect(form && formKey(form)).toBe(key)
    }
    for (const key of ["half", "regular-marked", "marked-marked", "dotted-marked", "parts-", "parts-Moon", "solid-half", "active", "lit-1"]) expect(parseFormKey(key)).toBeNull()
  })

  it("lists each fill then the same fill marked, dotted after regular", () => {
    expect(formKeysOf({ mark: true, body: true, parts: ["moon", "cloud"], steps: 1, dotted: true })).toEqual([
      "regular",
      "marked",
      "dotted",
      "solid",
      "solid-marked",
      "parts-moon",
      "parts-moon-marked",
      "parts-cloud",
      "parts-cloud-marked",
      "progress-1",
      "progress-1-marked",
    ])
    expect(formKeysOf({ mark: false, body: false, parts: [], steps: 0, dotted: false })).toEqual(["regular"])
  })
})

describe("renderForm", () => {
  it("lists the forms the roles allow", () => {
    expect(formsOf(glyph)).toEqual(["regular", "marked", "solid", "solid-marked", "progress-1", "progress-1-marked", "progress-2", "progress-2-marked"])
  })

  it("draws regular as strokes only", () => {
    expect(renderForm(glyph, regular).every((p) => p.stroke && !p.fill)).toBe(true)
  })

  it("fills the mark when marked and the first k steps for progress", () => {
    expect(renderForm(glyph, { fill: { kind: "regular" }, marked: true }).map((p) => p.fill)).toEqual([false, false, false, false, true])
    expect(renderForm(glyph, { fill: { kind: "progress", k: 1 } }).map((p) => p.fill)).toEqual([false, false, true, false, false])
    expect(renderForm(glyph, { fill: { kind: "progress", k: 2 }, marked: true }).map((p) => p.fill)).toEqual([false, false, true, true, true])
    expect(renderForm(glyph, { fill: { kind: "progress", k: 0 } })).toEqual(renderForm(glyph, regular))
  })

  it("draws solid and solid-marked from their carved fills", () => {
    expect(renderForm(glyph, { fill: { kind: "solid" } })).toEqual([{ d: "M1 1 L2 2 Z", fill: true, evenOdd: true, stroke: false }])
    expect(renderForm(glyph, { fill: { kind: "solid" }, marked: true })[0]?.d).toBe("M1 1 L3 3 Z")
  })

  it("draws the kept lines stroked and a lit mark filled beside a carved fill", () => {
    const g: GlyphData = { ...glyph, carved: { ...glyph.carved, "solid-marked": { d: "M1 1 L3 3 Z", keep: [1], lit: [4] } } }
    expect(renderForm(g, { fill: { kind: "solid" }, marked: true })).toEqual([
      { d: "M1 1 L3 3 Z", fill: true, evenOdd: true, stroke: false },
      { d: "M8 12 L16 12", fill: false, evenOdd: false, stroke: true },
      { d: "M12 12 L13 13", fill: true, evenOdd: false, stroke: true },
    ])
  })

  it("draws a parts form from its carved fill", () => {
    const parted: GlyphData = {
      ...glyph,
      paths: [
        { d: "M4 4 L10 4 L10 10 Z", role: "body", part: "left" },
        { d: "M14 4 L20 4 L20 10 Z", role: "body", part: "right" },
      ],
      parts: ["left", "right"],
      steps: 0,
      carved: { solid: { d: "M0 0 Z", keep: [], lit: [] }, "parts-left": { d: "M5 5 Z", keep: [1], lit: [] }, "parts-right": { d: "M15 5 Z", keep: [0], lit: [] } },
    }
    expect(formsOf(parted)).toEqual(["regular", "solid", "parts-left", "parts-right"])
    expect(renderForm(parted, { fill: { kind: "parts", parts: ["left"] } }).map((p) => [p.d, p.fill])).toEqual([
      ["M5 5 Z", true],
      ["M14 4 L20 4 L20 10 Z", false],
    ])
  })

  it("draws a drawn dotted form as it is", () => {
    const dotted: GlyphData = { ...glyph, dotted: [{ d: "M2 2 L3 3", fill: false, evenOdd: false, stroke: true }] }
    expect(formsOf(dotted).slice(0, 3)).toEqual(["regular", "marked", "dotted"])
    expect(renderForm(dotted, { fill: { kind: "dotted" } })).toEqual(dotted.dotted)
  })

  it("lets an override replace a form", () => {
    const over: GlyphData = { ...glyph, overrides: { "solid-marked": [{ d: "M5 5 L6 6", fill: true, evenOdd: false, stroke: true }] } }
    expect(renderForm(over, { fill: { kind: "solid" }, marked: true })).toEqual([{ d: "M5 5 L6 6", fill: true, evenOdd: false, stroke: true }])
  })

  it("refuses a form the glyph does not have", () => {
    expect(() => renderForm({ ...glyph, paths: glyph.paths.slice(1), carved: {} }, { fill: { kind: "solid" } })).toThrow(/path-river has no solid form/)
    expect(() => renderForm(glyph, { fill: { kind: "progress", k: 3 } })).toThrow(/path-river has no progress-3 form/)
    expect(() => renderForm(glyph, { fill: { kind: "dotted" } })).toThrow(/path-river has no dotted form/)
  })

  it("draws a carved step fill instead of the plain path and omits its cuts", () => {
    const carved: GlyphData = {
      ...glyph,
      paths: [{ d: SQ, role: "step", step: 1, fill: "M0 0 L1 1 Z", cuts: [1] }, { d: "M8 12 L16 12" }, { d: "M2 2 L3 3" }],
      steps: 1,
      carved: {},
    }
    expect(renderForm(carved, { fill: { kind: "progress", k: 1 } })).toEqual([
      { d: "M0 0 L1 1 Z", fill: true, evenOdd: true, stroke: false },
      { d: "M2 2 L3 3", fill: false, evenOdd: false, stroke: true },
    ])
    expect(renderForm(carved, regular)).toHaveLength(3)
  })

  it("omits a hidden path at rest and draws it filled once its role fills", () => {
    const hidden: GlyphData = {
      ...glyph,
      paths: [
        { d: SQ, role: "body" },
        { d: "M8 12 L16 12" },
        { d: "M6 6 L7 7", role: "step", step: 1, hidden: true },
        { d: "M9 9 L10 10", role: "step", step: 2 },
        { d: "M12 12 L13 13", role: "mark", hidden: true },
      ],
    }
    expect(renderForm(hidden, regular).map((p) => p.d)).toEqual([SQ, "M8 12 L16 12", "M9 9 L10 10"])
    expect(renderForm(hidden, { fill: { kind: "progress", k: 1 } }).map((p) => [p.d, p.fill])).toEqual([
      [SQ, false],
      ["M8 12 L16 12", false],
      ["M6 6 L7 7", true],
      ["M9 9 L10 10", false],
    ])
    expect(renderForm(hidden, { fill: { kind: "regular" }, marked: true }).map((p) => [p.d, p.fill])).toEqual([
      [SQ, false],
      ["M8 12 L16 12", false],
      ["M9 9 L10 10", false],
      ["M12 12 L13 13", true],
    ])
  })
})
```

- [ ] **Step 6: Write `emit-glyphs.ts`**

Replace everything from the top of the file down to (not including) `/** Every non-exploring row with its source and overrides, in manifest order. */` with:

```ts
import { readdirSync, readFileSync } from "node:fs"
import { join } from "node:path"
import { carveSolid } from "./carve"
import { GENERATED_NOTICE, type Emitter } from "./emit"
import { formKey, type CarvedFill, type Fill, type GlyphData, type GlyphPath, type RenderedPath } from "./forms"
import { SRC_DIR, splitSourceName } from "./lint"
import { isOverrideForm, loadManifest, type IconRow } from "./manifest"
import { normalizePath } from "./normalize"
import { parseSvg } from "./parse"
import { partCombinations, readRoles } from "./roles"
import { loadStates } from "./states"

export const GLYPHS_FILE = "packages/icons/glyphs/glyphs.json"

/** `list[n]` for each n, skipping any out of range. */
const at = (list: readonly number[], ns: readonly number[]): number[] =>
  ns.flatMap((n) => {
    const i = list[n]
    return i === undefined ? [] : [i]
  })

/**
 * A solid or parts fill (spec D3): the chosen body paths filled and carved by
 * every other drawn path. Under `marked` the mark is cut out where it lies
 * inside the fill and drawn filled (`lit`) where it does not.
 */
function carveFill(paths: readonly GlyphPath[], body: readonly number[], marked: boolean): CarvedFill {
  const isMark = (p: GlyphPath) => p.role === "mark"
  const lines = paths.flatMap((p, i) => (!body.includes(i) && !p.hidden && !(marked && isMark(p)) ? [i] : []))
  const holes = marked ? paths.flatMap((p, i) => (isMark(p) ? [i] : [])) : []
  const d = (i: number) => paths[i]?.d ?? ""
  const carved = carveSolid(body.map(d), lines.map(d), holes.map(d))
  const lit = at(holes, carved.outside)
  // A lit mark that is carved itself leaves out the lines it is cut through.
  const cut = new Set(lit.flatMap((i) => (paths[i]?.fill !== undefined ? (paths[i]?.cuts ?? []) : [])))
  return { d: carved.d, keep: at(lines, carved.keep).filter((i) => !cut.has(i)), lit }
}

function rendered(text: string, file: string): RenderedPath[] {
  return parseSvg(text, file).paths.map((p) => ({ d: normalizePath(p.d), fill: p.filled, evenOdd: p.evenOdd, stroke: !p.unstroked }))
}

/** One manifest row and its sources as `glyphs.json` stores them (spec §2). */
export function buildGlyph(row: IconRow, source: string, overrides: Record<string, string>, dotted: string | null = null): GlyphData {
  const { paths: parsed } = parseSvg(source, `${row.name}.svg`)
  const { roles } = readRoles(parsed)
  const partOf = new Map(roles.parts.flatMap((part) => part.paths.map((i): [number, string] => [i, part.name])))
  const paths: GlyphPath[] = parsed.map((p, i) => {
    const d = normalizePath(p.d)
    const hidden = roles.hidden.includes(i) ? { hidden: true as const } : {}
    if (roles.body.includes(i)) {
      const part = partOf.get(i)
      return part === undefined ? { d, role: "body" } : { d, role: "body", part }
    }
    if (roles.mark.includes(i)) return { d, role: "mark", ...hidden }
    const step = roles.steps.findIndex((group) => group.includes(i))
    return step >= 0 ? { d, role: "step", step: step + 1, ...hidden } : { d }
  })
  // A mark or step with lines inside it would paint over them: store it carved like the solid.
  paths.forEach((p, i) => {
    if (p.role !== "mark" && p.role !== "step") return
    const inner = paths.flatMap((q, j) => (j !== i && !q.hidden ? [j] : []))
    const carved = carveSolid([p.d], inner.map((j) => paths[j]?.d ?? ""))
    if (carved.keep.length === inner.length) return
    p.fill = carved.d
    p.cuts = inner.filter((_, n) => !carved.keep.includes(n))
  })
  const carved: Record<string, CarvedFill> = {}
  if (roles.body.length > 0) {
    const fills: { fill: Fill; body: number[] }[] = [
      { fill: { kind: "solid" }, body: roles.body },
      ...partCombinations(roles.parts.map((p) => p.name)).map((names) => ({
        fill: { kind: "parts" as const, parts: names },
        body: roles.parts.filter((p) => names.includes(p.name)).flatMap((p) => p.paths),
      })),
    ]
    for (const { fill, body } of fills) {
      carved[formKey({ fill })] = carveFill(paths, body, false)
      if (roles.mark.length > 0) carved[formKey({ fill, marked: true })] = carveFill(paths, body, true)
    }
  }
  const drawn: Record<string, RenderedPath[]> = {}
  for (const [form, text] of Object.entries(overrides)) drawn[form] = rendered(text, `${row.name}.${form}.svg`)
  return {
    name: row.name,
    meaning: row.meaning,
    family: row.family,
    tags: row.tags,
    paths,
    parts: roles.parts.map((p) => p.name),
    steps: roles.steps.length,
    carved,
    dotted: dotted === null ? null : rendered(dotted, `${row.name}.dotted.svg`),
    overrides: drawn,
  }
}

```

(`"parts" as const` narrows a literal, the codebase's existing `true as const` pattern; it is not a type assertion on a value.) Leave `loadGlyphs` and `glyphsEmitter` as they are; Task 3 passes the dotted drawing.

- [ ] **Step 7: Write `emit-glyphs.test.ts`**

Replace the `describe("buildGlyph", …)`, `describe("hidden paths", …)` and `describe("carved fills", …)` blocks (keep the imports block, `row`, `source` and `describe("loadGlyphs", …)`), and add `import { filledArea } from "./lint"` to the imports:

```ts
describe("buildGlyph", () => {
  it("stores normalised paths with roles, the step count and the carved solid", () => {
    const g = buildGlyph(row, source('  <path d="M4 4 L20 4 L20 20 L4 20 Z" data-role="body"/>\n  <path d="M8 12 L16 12" data-role="step" data-step="1"/>\n  <path d="M2 22 A1 1 0 0 1 4 22"/>'), {})
    expect(g.paths[0]).toEqual({ d: "M4 4 L20 4 L20 20 L4 20 Z", role: "body" })
    expect(g.paths[1]).toEqual({ d: "M8 12 L16 12", role: "step", step: 1 })
    expect(g.paths[2]?.d).toMatch(/^M2 22 C/)
    expect(g.steps).toBe(1)
    expect(g.parts).toEqual([])
    expect(g.dotted).toBeNull()
    // keep indexes paths: the arc below the body stays stroked.
    expect(g.carved.solid?.keep).toEqual([2])
    expect(formsOf(g)).toEqual(["regular", "solid", "progress-1"])
    expect(g.family).toBe("ring")
    expect(g.tags).toEqual({ wrapper: ["circle"] })
  })

  it("stores overrides by form key, as rendered paths", () => {
    const g = buildGlyph(row, source('  <path d="M4 4 L20 4 L20 20 L4 20 Z" data-role="body"/>'), {
      solid: source('  <path d="M4 4 L20 4 L20 20 L4 20 Z" fill="currentColor" stroke="none"/>'),
    })
    expect(renderForm(g, { fill: { kind: "solid" } })).toEqual([{ d: "M4 4 L20 4 L20 20 L4 20 Z", fill: true, evenOdd: false, stroke: false }])
  })

  it("carves a fill per part combination, the other parts stroked", () => {
    const g = buildGlyph(row, source('  <path d="M3 4 L11 4 L11 20 L3 20 Z" data-role="body" data-part="left"/>\n  <path d="M13 4 L21 4 L21 20 L13 20 Z" data-role="body" data-part="right"/>'), {})
    expect(g.parts).toEqual(["left", "right"])
    expect(g.paths[0]).toEqual({ d: "M3 4 L11 4 L11 20 L3 20 Z", role: "body", part: "left" })
    expect(Object.keys(g.carved)).toEqual(["solid", "parts-left", "parts-right"])
    expect(g.carved["parts-left"]?.keep).toEqual([1])
    expect(formsOf(g)).toEqual(["regular", "solid", "parts-left", "parts-right"])
  })

  it("cuts the mark out of a marked solid, and fills a mark that lies outside the body", () => {
    const body = '  <path d="M4 4 L20 4 L20 20 L4 20 Z" data-role="body"/>'
    const inside = buildGlyph(row, source(`${body}\n  <path d="M11 11 L13 11 L13 13 L11 13 Z" data-role="mark" data-rest="hidden"/>`), {})
    expect(Object.keys(inside.carved)).toEqual(["solid", "solid-marked"])
    expect(inside.carved["solid-marked"]?.lit).toEqual([])
    expect(filledArea(inside.carved["solid-marked"]?.d ?? "", true)).toBeLessThan(filledArea(inside.carved.solid?.d ?? "", true) - 4)
    const outside = buildGlyph(row, source(`${body}\n  <path d="M2 22 L3 22" data-role="mark"/>`), {})
    expect(outside.carved["solid-marked"]?.lit).toEqual([1])
    expect(outside.carved.solid?.keep).toEqual([1])
    expect(renderForm(outside, { fill: { kind: "solid" }, marked: true }).map((p) => [p.d, p.fill])).toEqual([
      [outside.carved["solid-marked"]?.d, true],
      ["M2 22 L3 22", true],
    ])
  })
})

describe("hidden paths", () => {
  const hidden = '  <path d="M10 10 L11 11" data-role="step" data-step="1" data-rest="hidden"/>'
  const body = '  <path d="M4 4 L20 4 L20 20 L4 20 Z" data-role="body"/>\n  <path d="M8 12 L16 12"/>'

  it("writes hidden: true and leaves the path out of the carve", () => {
    const g = buildGlyph(row, source(`${body}\n${hidden}`), {})
    expect(g.paths[2]).toEqual({ d: "M10 10 L11 11", role: "step", step: 1, hidden: true })
    expect(g.paths[0]).not.toHaveProperty("hidden")
    expect(g.carved.solid?.keep).toEqual([])
    const withoutDot = buildGlyph(row, source(body), {})
    expect(g.carved.solid?.d).toBe(withoutDot.carved.solid?.d)
  })

  it("keeps keep consistent with renderForm when a hidden path comes before a kept line", () => {
    const g = buildGlyph(row, source(`${body.split("\n")[0]}\n${hidden}\n  <path d="M2 22 L4 22"/>`), {})
    expect(g.carved.solid?.keep).toEqual([2])
    expect(renderForm(g, { fill: { kind: "solid" } }).map((p) => p.d).slice(1)).toEqual(["M2 22 L4 22"])
    expect(renderForm(g, { fill: { kind: "progress", k: 1 } }).filter((p) => p.fill).map((p) => p.d)).toEqual(["M10 10 L11 11"])
    expect(renderForm(g, { fill: { kind: "regular" } }).map((p) => p.d)).not.toContain("M10 10 L11 11")
  })
})

describe("carved role fills", () => {
  const square = "M4 4 L20 4 L20 20 L4 20 Z"
  it("gives a step with a line inside a carved fill and the line's index as a cut", () => {
    const g = buildGlyph(row, source(`  <path d="${square}" data-role="step" data-step="1"/>\n  <path d="M8 12 L16 12"/>`), {})
    expect(g.paths[0]?.cuts).toEqual([1])
    expect(g.paths[0]?.fill).toMatch(/^M/)
    expect(g.paths[1]).not.toHaveProperty("fill")
    expect(renderForm(g, { fill: { kind: "progress", k: 1 } })).toEqual([{ d: g.paths[0]?.fill, fill: true, evenOdd: true, stroke: false }])
    expect(renderForm(g, { fill: { kind: "regular" } }).map((p) => p.d)).toEqual([square, "M8 12 L16 12"])
  })

  it("stores nothing on a step with nothing inside", () => {
    const g = buildGlyph(row, source(`  <path d="${square}" data-role="step" data-step="1"/>\n  <path d="M2 22 L3 22"/>`), {})
    expect(g.paths[0]).toEqual({ d: square, role: "step", step: 1 })
    expect(renderForm(g, { fill: { kind: "progress", k: 1 } }).map((p) => [p.d, p.fill, p.evenOdd])).toEqual([
      [square, true, false],
      ["M2 22 L3 22", false, false],
    ])
  })

  it("carves a hidden mark too", () => {
    const g = buildGlyph(row, source(`  <path d="${square}" data-role="mark" data-rest="hidden"/>\n  <path d="M8 12 L16 12"/>`), {})
    expect(g.paths[0]?.cuts).toEqual([1])
    expect(renderForm(g, { fill: { kind: "regular" }, marked: true }).map((p) => p.d)).toEqual([g.paths[0]?.fill])
  })
})
```

- [ ] **Step 8: Override forms in `manifest.ts`**

Replace the two lines

```ts
/** An override file's form (spec D4): `half`, `solid` or `progress-<k>`. */
export const OVERRIDE_FORM = /^(half|solid|progress-[1-9]\d*)$/
export const isOverrideForm = (v: string): boolean => OVERRIDE_FORM.test(v)
```

with

```ts
/** An override file's form (spec D4): any form key but `regular` and `dotted`. */
export const isOverrideForm = (v: string): boolean => v !== "regular" && v !== "dotted" && parseFormKey(v) !== null
```

and add `import { parseFormKey } from "./forms"` after the `node:path` import. (`forms.ts` imports only a type from `manifest.ts`, so there is no runtime cycle.)

- [ ] **Step 9: Lint in `lint.ts`**

1. Replace `const FORM_WORDS = ["regular", "half", "solid", "of"]` with `const FORM_WORDS = ["regular", "marked", "solid", "parts", "of"]`. (`dotted` stays a valid last word: `star-dotted` and `draw-dotted` are dotted drawings with no solid-line sibling; Task 4 records this in the spec.)
2. Replace `"data-role", "data-mark", "data-rest"` in `PATH_ATTRIBUTES` with `"data-role", "data-part", "data-step", "data-rest"`.
3. Add `import { formKeysOf } from "./forms"` and replace the body of `overrideAllowed` after `const { roles } = readRoles(parsed.paths)` with:

```ts
  const shape = { mark: roles.mark.length > 0, body: roles.body.length > 0, parts: roles.parts.map((p) => p.name), steps: roles.steps.length, dotted: false }
  return formKeysOf(shape).includes(form)
```

- [ ] **Step 10: Update `lint.test.ts`**

In `describe("roles and overrides", …)`:

- every `data-role="mark" data-mark="N"` in the fixtures becomes `data-role="step" data-step="N"`;
- `"path 1: data-rest is only allowed on a mark or focus"` becomes `"path 1: data-rest is only allowed on a mark or step"`;
- `"marks must run 1…n without gaps; found 2"` becomes `"steps must run 1…n without gaps; found 2"`;
- in "accepts an override for a form the glyph has and refuses one it lacks", `"ring.half.svg"` becomes `"ring.marked.svg"` and the message `'override "marked" for a form ring does not have'`;
- in "refuses a name ending in a form word", the name `scale-half` becomes `scale-marked` (row, file and message) and the message lists `(regular, marked, solid, parts, of)`.

Then add, inside the same `describe`:

```ts
  it("accepts an override for a part combination and a marked solid", () => {
    const parts = svg('<path d="M3 4 L11 4 L11 20 L3 20 Z" data-role="body" data-part="left"/>', '<path d="M13 4 L21 4 L21 20 L13 20 Z" data-role="body" data-part="right"/>', '<path d="M12 2 L12 3" data-role="mark"/>')
    const over = svg('<path d="M3 4 L11 4 L11 20 L3 20 Z" fill="currentColor"/>')
    const files = { "ring.svg": parts, "ring.parts-left.svg": over, "ring.solid-marked.svg": over }
    expect(run({ manifest: { icons: [row("ring")] }, files }).errors).toEqual([])
    const messages = run({ manifest: { icons: [row("ring")] }, files: { "ring.svg": parts, "ring.parts-middle.svg": over } }).errors.map((e) => e.message)
    expect(messages).toContain('override "parts-middle" for a form ring does not have')
  })
```

Note `"ring.parts-middle.svg"`: `parts-middle` is a well-formed key (so `isOverrideForm` takes it) for a part the glyph does not have.

- [ ] **Step 11: States**

In `packages/icons/states.json` replace every `"half"` with `"marked"`.

In `states.ts`: replace `"half"` with `"marked"` in `FORM_WORDS`, and replace `resolveState` with:

```ts
/** The form a state shows on a glyph whose forms are `forms` (from `formsOf`). */
export function resolveState(table: StateTable, forms: readonly string[], state: string, k = 0): Form {
  const list = table[state]
  if (!list) throw new Error(`unknown state ${state}`)
  const n = forms.filter((f) => /^progress-\d+$/.test(f)).length
  for (const word of list) {
    if (word === "regular") return { fill: { kind: "regular" } }
    if (word === "marked" && forms.includes("marked")) return { fill: { kind: "regular" }, marked: true }
    if (word === "solid" && forms.includes("solid")) return { fill: { kind: "solid" } }
    if (word === "progress-n" && n > 0) return { fill: { kind: "progress", k: n } }
    if (word === "progress-k" && n > 0) return { fill: { kind: "progress", k: Math.max(0, Math.min(Number.isInteger(k) ? k : 0, n)) } }
  }
  return { fill: { kind: "regular" } }
}
```

In `states.test.ts`: the table's `"half"` becomes `"marked"`; the forms list `["regular", "half", "solid"]` becomes `["regular", "marked", "solid"]`; every expected `{ kind: "solid" }` becomes `{ fill: { kind: "solid" } }`, `{ kind: "progress", k }` becomes `{ fill: { kind: "progress", k } }`, `{ kind: "regular" }` becomes `{ fill: { kind: "regular" } }`. Add one case to "takes the first form the glyph has":

```ts
    expect(resolveState(table, ["regular", "marked"], "hover")).toEqual({ fill: { kind: "regular" }, marked: true })
    expect(resolveState(table, ["regular", "progress-1", "progress-1-marked", "progress-2", "progress-2-marked"], "selected")).toEqual({ fill: { kind: "progress", k: 2 } })
```

- [ ] **Step 12: Keep the web page compiling**

`apps/web/lib/design/glyph-forms.ts` casts `glyphs.json`, so it compiles whatever the data says, but its drawing would break. Make these minimal edits (Task 5 rewrites the file):

- `GlyphPath.role` becomes `"body" | "mark" | "step"`; `mark?: number` becomes `step?: number`; add `part?: string`.
- In `Glyph`: replace `marks: number` with `steps: number`, replace `solid: { d: string; keep: number[] } | null` with `carved: Record<string, { d: string; keep: number[]; lit: number[] }>`, add `parts: string[]` and `dotted: RenderedPath[] | null`.
- `FormChoice` becomes `"regular" | "marked" | "solid" | "progress"`.
- Everywhere: `p.role === "focus"` → `p.role === "mark"`; `p.role === "mark"` (the numbered one) → `p.role === "step"`; `p.mark` → `p.step`; `glyph.marks` → `glyph.steps`; the string `"half"` → `"marked"`; `glyph.solid` (non-null check) → `glyph.carved.solid`.
- In `formPaths`, the solid branch becomes:

```ts
  const solid = glyph.carved.solid
  if (effective === "solid" && solid) {
    return [
      { d: solid.d, fill: true, evenOdd: true, stroke: false },
      ...solid.keep.flatMap((i) => {
        const p = glyph.paths[i]
        return p ? [stroked(p.d)] : []
      }),
    ]
  }
```

Apply the same renames in `GlyphFormView.tsx` (`glyph.marks` → `glyph.steps`, `glyph.solid` → `glyph.carved.solid`), `GlyphReviewDialog.tsx` (`roleLabel`: `p.role === "mark" ? \`mark ${p.mark ?? "?"}\`` becomes `p.role === "step" ? \`step ${p.step ?? "?"}\``; `roleColour`: `"mark"` → `"step"` for chart-1, `"focus"` → `"mark"` for chart-2; the textarea placeholder's `tag the moon as focus` becomes `tag the moon as mark`), `DesignTools.tsx` (`{ value: "half", label: "Half" }` becomes `{ value: "marked", label: "Marked" }`, the select label `Marks lit` becomes `Steps lit`) and `glyph-forms.test.ts` (`g.marks` → `g.steps`; `p.role === "focus"` → `p.role === "mark"`; in the cromlech test the hidden path is `p.role === "step"` and `hidden.mark` → `hidden.step`; `pouch.marks` → `pouch.steps`; in the plain-glyph finder `g.solid === null` → `g.carved.solid === undefined`).

- [ ] **Step 13: Format, lint, generate, test**

```bash
npm run format --workspace=@pbbls/icons      # expect "0 rewritten"; if not, keep the rewrite
npm run lint --workspace=@pbbls/icons        # expect 0 errors
npm run build --workspace=@pbbls/icons
npm run test --workspace=@pbbls/icons
npm run generate --workspace=@pbbls/icons
git status --short                           # expect only src/*.svg, packages/icons/{scripts,states.json,glyphs}, apps/web files
npm run check --workspace=@pbbls/icons
npm run test --workspace=@pbbls/web -- lib/design
cd apps/web && npx tsc --noEmit && cd ../..
npm run lint --workspace=@pbbls/web
```

All must pass. If `generate` rewrites anything under `packages/icons/react/`, `apps/ios/` or `apps/android/`, stop and report: the old emitters must not see the role attributes.

- [ ] **Step 14: Commit**

```bash
git add packages/icons apps/web/lib/design apps/web/components/sandbox/design
git status --short   # apps/web/AGENTS.md must stay untracked
git commit -m "chore(ui): read body parts, a mark and steps, and work out forms as a fill plus the mark

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: The dotted form

**Files:**
- Modify: `packages/icons/scripts/lib/manifest.ts`, `lint.ts`, `lint.test.ts`, `emit-glyphs.ts`, `emit-glyphs.test.ts`

`src/<name>.dotted.svg` creates the `dotted` form (spec D3, D4). It is a plain line drawing: no role, no fill.

- [ ] **Step 1: Write the failing tests**

In `lint.test.ts`, inside `describe("roles and overrides", …)`:

```ts
  it("accepts a dotted drawing beside its base and refuses one with a role, a fill, or no base", () => {
    const dotted = svg('<path d="M4 12 L5 12 M7 12 L8 12"/>')
    const manifest = { icons: [row("ring")] }
    expect(run({ manifest, files: { "ring.svg": svg(BODY), "ring.dotted.svg": dotted } }).errors).toEqual([])
    const roled = run({ manifest, files: { "ring.svg": svg(BODY), "ring.dotted.svg": svg('<path d="M4 12 L5 12" data-role="mark"/>') } }).errors.map((e) => e.message)
    expect(roled).toContain("path 1: a dotted drawing carries no role")
    const filled = run({ manifest, files: { "ring.svg": svg(BODY), "ring.dotted.svg": svg('<path d="M4 4 L8 4 L8 8 Z" fill="currentColor"/>') } }).errors.map((e) => e.message)
    expect(filled).toContain("path 1: a dotted drawing is never filled")
    const orphan = run({ manifest, files: { "ring.svg": svg(BODY), "knot.dotted.svg": dotted } }).errors.map((e) => e.message)
    expect(orphan).toContain('dotted drawing without its base: no row "knot"')
  })
```

In `emit-glyphs.test.ts`, inside `describe("loadGlyphs", …)`, add:

```ts
  it("attaches a dotted drawing as the dotted form", () => {
    const root = mkdtempSync(join(tmpdir(), "icons-glyphs-"))
    mkdirSync(join(root, "src"))
    writeFileSync(join(root, "src", "ring.svg"), source('  <path d="M4 4 L20 4 L20 20 L4 20 Z"/>'))
    writeFileSync(join(root, "src", "ring.dotted.svg"), source('  <path d="M4 4 L5 4 M7 4 L8 4"/>'))
    writeFileSync(join(root, "icons.json"), JSON.stringify({ icons: [{ ...row, variants: [] }] }))
    const [ring] = loadGlyphs(root)
    expect(ring?.dotted).toEqual([{ d: "M4 4 L5 4 M7 4 L8 4", fill: false, evenOdd: false, stroke: true }])
    expect(ring ? formsOf(ring) : []).toEqual(["regular", "dotted"])
    expect(ring ? renderForm(ring, { fill: { kind: "dotted" } }) : []).toEqual(ring?.dotted)
    expect(Object.keys(ring?.overrides ?? {})).toEqual([])
  })
```

- [ ] **Step 2: Run them to see them fail**

Run: `npm run test --workspace=@pbbls/icons -- scripts/lib/lint.test.ts scripts/lib/emit-glyphs.test.ts`
Expected: FAIL: `variant "dotted" is not one of active, lit-1, lit-2, lit-3`, and `dotted` is null.

- [ ] **Step 3: Implement**

`manifest.ts`, below `isOverrideForm`:

```ts
/** `src/<name>.dotted.svg`: the drawn dotted form (spec D3, D4), not an override. */
export const DOTTED_FORM = "dotted"
```

`lint.ts`:

1. Import `DOTTED_FORM` from `./manifest`.
2. In `lintManifest`'s file loop, right after the `if (split.variant === null) { … continue }` block, insert:

```ts
    if (split.variant === DOTTED_FORM) {
      if (!base) errors.push({ file: f, rule: "manifest", message: `dotted drawing without its base: no row "${split.base}"` })
      continue
    }
```

3. Add this function above `lint`:

```ts
/** A dotted drawing (spec D4) is a plain line drawing: no role, no fill. */
function lintDotted(root: string, file: string, result: LintResult): void {
  let parsed: ParsedSvg
  try {
    parsed = parseSvg(readFileSync(join(root, SRC_DIR, file), "utf8"), file)
  } catch {
    return // lintSource has reported it
  }
  parsed.paths.forEach((p, i) => {
    if (p.attrs["data-role"] !== undefined) result.errors.push({ file, rule: "dotted", message: `path ${i + 1}: a dotted drawing carries no role` })
    if (p.filled) result.errors.push({ file, rule: "dotted", message: `path ${i + 1}: a dotted drawing is never filled` })
  })
}
```

4. In `lint`'s loop over files, after the `lintSource(…)` call, add `if (variant === DOTTED_FORM) lintDotted(root, f, result)`.

`emit-glyphs.ts`, in `loadGlyphs`, collect the dotted drawing beside the overrides and pass it:

```ts
    .map((row) => {
      const overrides: Record<string, string> = {}
      let dotted: string | null = null
      for (const f of files) {
        const split = splitSourceName(f)
        if (split?.base !== row.name || split.variant === null) continue
        if (split.variant === DOTTED_FORM) dotted = readFileSync(join(dir, f), "utf8")
        else if (isOverrideForm(split.variant)) overrides[split.variant] = readFileSync(join(dir, f), "utf8")
      }
      return buildGlyph(row, readFileSync(join(dir, `${row.name}.svg`), "utf8"), overrides, dotted)
    })
```

and import `DOTTED_FORM` from `./manifest`.

- [ ] **Step 4: Run the tests, lint, generate, check**

```bash
npm run test --workspace=@pbbls/icons
npm run build --workspace=@pbbls/icons
npm run lint --workspace=@pbbls/icons
npm run generate --workspace=@pbbls/icons    # no dotted file exists yet: expect glyphs.json unchanged
npm run check --workspace=@pbbls/icons
```

Expected: all pass, `git status --short` shows only the five script files.

- [ ] **Step 5: Commit**

```bash
git add packages/icons/scripts/lib
git commit -m "chore(ui): draw a dotted form from <name>.dotted.svg

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Docs

**Files:**
- Modify: `packages/icons/DNA.md` (section "Roles and forms (2026-11, #1607)")
- Modify: `packages/icons/CLAUDE.md`
- Modify: `docs/superpowers/specs/2026-11-08-icon-forms-design.md` (D1)
- Modify: `docs/superpowers/plans/2026-11-08-icon-forms.md` (a note under the header)

- [ ] **Step 1: DNA.md**

Replace the paragraph under `## Roles and forms (2026-11, #1607)` with:

```markdown
A path may carry `data-role="body"` (the main shape, filled the way SVG fills a path: an open path closes with a straight segment), optionally split into 2–4 named pieces with `data-part="<word>"` (every body path then names one); `data-role="mark"` (the one accent that carries the meaning: a door dot, a notch); or `data-role="step" data-step="1…n"` (a real sequence, in fill order). Steps are for sequences only: pieces that fill in any combination are parts, a single accent is the mark.

A form is a fill plus, when the glyph has a mark, `marked`. The fills are regular, solid (the body filled, every line within half a stroke of it carved out at the stroke width), parts (any combination of parts short of all of them, carved the same way), progress k of n (steps 1…k filled) and dotted (only where `src/<name>.dotted.svg` is drawn: a plain line drawing, no role, no fill). Marked fills the mark on regular and progress, and cuts it out of a solid or parts fill (or fills it beside the fill when it lies outside). A mark or step path may carry `data-rest="hidden"`: it is not drawn at rest and is drawn filled whenever its role fills (a lit dot the base does not show). A source is never filled; `src/<name>.<form>.svg`, `<form>` a form key such as `marked`, `solid-marked`, `parts-moon` or `progress-2`, overrides a worked-out form that looks wrong. A name never ends in a form word (regular, marked, solid, parts, of).
```

- [ ] **Step 2: packages/icons/CLAUDE.md**

Replace the bullet

```markdown
- `packages/icons/glyphs/glyphs.json` (the forms data: every glyph's paths with roles, its carved solid and overrides, and the state tokens)
```

with

```markdown
- `packages/icons/glyphs/glyphs.json` (the forms data: every glyph's paths with roles, its carved solid and parts fills with and without the mark, its dotted drawing and overrides, and the state tokens)
```

and in "Adding or redrawing an icon", step 2, replace `Tag its paths with \`data-role\` (see DNA.md, Roles and forms).` with `Tag its paths with \`data-role\` (\`body\`, optionally \`data-part\`; \`mark\`; \`step\` with \`data-step\`; see DNA.md, Roles and forms); a dotted version goes in \`src/<name>.dotted.svg\`.`

- [ ] **Step 3: Spec D1**

In `docs/superpowers/specs/2026-11-08-icon-forms-design.md`, replace

```
The last segment of a name is never a form word (`regular`, `marked`, `solid`, `dotted`, `parts`, `of`), so a name can't be mistaken for a form.
```

with

```
The last segment of a name is never a form word (`regular`, `marked`, `solid`, `parts`, `of`), so a name can't be mistaken for a form. `dotted` may end the name of a drawing that is dotted and has no solid-line sibling (`star-dotted`, `draw-dotted`).
```

- [ ] **Step 4: Note in the parts 1–3 plan**

In `docs/superpowers/plans/2026-11-08-icon-forms.md`, insert after the `**Prerequisite:** …` line:

```markdown

**Amended 2026-11-09:** the maintainer's review changed the model (spec D1–D4, D6). `focus` is now `mark` and the form `half` is `marked`; the numbered `mark` is now `step` (`data-step`); a body may split into parts; marked combines with every fill; dotted is a drawn form. Part 3 finishes with `docs/superpowers/plans/2026-11-09-icon-forms-amendment.md`; the drawings follow in part 3b. Read "half", "focus" and "mark N" below in those terms; parts 4–7 are planned against the amended spec.
```

- [ ] **Step 5: Commit**

```bash
git add packages/icons/DNA.md packages/icons/CLAUDE.md docs/superpowers/specs/2026-11-08-icon-forms-design.md docs/superpowers/plans/2026-11-08-icon-forms.md
git commit -m "docs(ui): describe parts, the mark, steps and dotted in the icon docs

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: The design page's data layer

**Files:**
- Modify: `apps/web/lib/design/glyph-forms.ts`
- Test: `apps/web/lib/design/glyph-forms.test.ts`

The page mirrors `renderForm` (packages/icons/scripts/lib/forms.ts) until part 4 brings the components, and owns the page's view: three pickers that both filter the list and choose the form drawn.

- [ ] **Step 1: Write the failing tests**

Replace the `describe("glyph forms", …)` block of `glyph-forms.test.ts` (keep `describe("showsGlyph", …)`) with the block below, and change the import line to:

```ts
import { DEFAULT_VIEW, GLYPHS, describeSigns, formChoices, formKey, formPaths, glyphForms, needsReview, showsGlyph, signsOf, viewForm, viewMatches, type Glyph } from "./glyph-forms"
```

```ts
const find = (test: (g: Glyph) => boolean, what: string): Glyph => {
  const g = GLYPHS.find(test)
  if (!g) throw new Error(`no ${what}`)
  return g
}

describe("glyph forms", () => {
  it("reads every glyph from glyphs.json", () => {
    expect(GLYPHS.length).toBeGreaterThan(900)
    expect(new Set(GLYPHS.map((g) => g.name)).size).toBe(GLYPHS.length)
  })

  it("lists a plain glyph's single form and draws a missing form as regular", () => {
    const plain = find((g) => g.steps === 0 && g.carved.solid === undefined && g.dotted === null && !g.paths.some((p) => p.role === "mark"), "plain glyph")
    expect(glyphForms(plain).map(formKey)).toEqual(["regular"])
    expect(formPaths(plain, { fill: { kind: "solid" } }).every((p) => !p.fill)).toBe(true)
  })

  it("lists each fill then the same fill marked", () => {
    const marked = find((g) => g.carved.solid !== undefined && g.paths.some((p) => p.role === "mark"), "glyph with a body and a mark")
    expect(glyphForms(marked).map(formKey).slice(0, 4)).toEqual(["regular", "marked", "solid", "solid-marked"])
    expect(formChoices(marked)[3]?.label).toBe("solid · marked")
  })

  it("draws a hidden step only once progress reaches it", () => {
    const cromlech = find((g) => g.name === "cromlech", "cromlech")
    const hidden = cromlech.paths.find((p) => p.hidden && p.role === "step")
    if (!hidden) throw new Error("cromlech has no hidden step")
    const at = (k: number) => formPaths(cromlech, { fill: { kind: "progress", k } })
    expect(at(0).some((p) => p.d === hidden.d)).toBe(false)
    expect(at(hidden.step ?? 1).find((p) => p.d === hidden.d)?.fill).toBe(true)
  })

  it("carves a filled step through the lines inside it", () => {
    const pouch = find((g) => g.name === "pouch", "pouch")
    const carved = pouch.paths.filter((p) => p.fill !== undefined && p.cuts)
    expect(carved.length).toBeGreaterThan(0)
    const drawn = formPaths(pouch, { fill: { kind: "progress", k: pouch.steps } })
    expect(drawn.some((p) => p.fill && p.evenOdd && !p.stroke)).toBe(true)
    for (const c of carved) for (const i of c.cuts ?? []) expect(drawn.some((p) => p.d === pouch.paths[i]?.d)).toBe(false)
  })

  it("draws a solid from its carved fill", () => {
    const solid = find((g) => g.carved.solid !== undefined, "glyph with a body")
    expect(formPaths(solid, { fill: { kind: "solid" } })[0]).toEqual({ d: solid.carved.solid?.d, fill: true, evenOdd: true, stroke: false })
  })

  it("flags glyphs the review lists", () => {
    expect(typeof needsReview("home")).toBe("boolean")
  })
})

describe("the page's view", () => {
  const marked = find((g) => g.carved.solid !== undefined && g.paths.some((p) => p.role === "mark"), "glyph with a body and a mark")
  const stepped = find((g) => g.steps > 1, "glyph with steps")
  const plain = find((g) => g.steps === 0 && g.carved.solid === undefined && g.dotted === null && !g.paths.some((p) => p.role === "mark"), "plain glyph")

  it("lists every glyph by default and draws it regular", () => {
    expect(viewMatches(plain, DEFAULT_VIEW)).toBe(true)
    expect(viewForm(marked, DEFAULT_VIEW)).toEqual({ fill: { kind: "regular" }, marked: false })
  })

  it("filters and draws by fill, mark and progress", () => {
    const solid = { ...DEFAULT_VIEW, fill: "solid" as const }
    expect(viewMatches(marked, solid)).toBe(true)
    expect(viewMatches(plain, solid)).toBe(false)
    expect(viewForm(marked, { ...solid, mark: "marked" })).toEqual({ fill: { kind: "solid" }, marked: true })
    expect(viewMatches(plain, { ...DEFAULT_VIEW, fill: "regular" })).toBe(true)
    expect(viewMatches(marked, { ...DEFAULT_VIEW, fill: "regular" })).toBe(false)
    expect(viewMatches(plain, { ...DEFAULT_VIEW, mark: "marked" })).toBe(false)
    expect(viewMatches(plain, { ...DEFAULT_VIEW, mark: "unmarked" })).toBe(true)
    const progress = { ...DEFAULT_VIEW, progress: true, k: 99 }
    expect(viewMatches(plain, progress)).toBe(false)
    expect(viewForm(stepped, progress).fill).toEqual({ kind: "progress", k: stepped.steps })
  })

  it("names the forms a glyph has beyond regular", () => {
    expect(describeSigns(signsOf(plain))).toBe("regular only")
    const signs = signsOf(stepped)
    expect(signs.steps).toBe(stepped.steps)
    expect(describeSigns(signs)).toContain(`${stepped.steps} steps`)
  })
})
```

- [ ] **Step 2: Run them to see them fail**

Run: `npm run test --workspace=@pbbls/web -- lib/design/glyph-forms.test.ts`
Expected: FAIL: `glyphForms`, `viewMatches`, … are not exported.

- [ ] **Step 3: Rewrite the forms half of `glyph-forms.ts`**

Replace everything from the top of the file down to (not including) `/** The Icons page's search: the name, the meaning and every tag value. */` with:

```ts
import data from "@pbbls/icons/glyphs.json"
import review from "@pbbls/icons/review.json"

/**
 * The forms data the design page reviews (#1607, part 3): every glyph from the
 * generator's `glyphs.json`, drawn with the same rules as `renderForm` in
 * packages/icons/scripts/lib/forms.ts. Part 4 replaces this with the web
 * components.
 */

/** A hidden path (mark or step) is not drawn at rest; it appears filled when its role fills. */
export type GlyphPath = {
  d: string
  role?: "body" | "mark" | "step"
  part?: string
  step?: number
  hidden?: true
  /** Mark or step with lines inside it: filled and stroked minus those lines, even-odd and unstroked. */
  fill?: string
  /** Indices into `paths` of the lines `fill` is carved through; not drawn once this path is filled. */
  cuts?: number[]
}
export type RenderedPath = { d: string; fill: boolean; evenOdd: boolean; stroke: boolean }
/** A solid or parts fill: `keep` drawn stroked on it, `lit` (a mark outside it) drawn filled; indices into `paths`. */
export type CarvedFill = { d: string; keep: number[]; lit: number[] }
export type Glyph = {
  name: string
  meaning: string
  family: string | null
  tags: Record<string, string[]>
  paths: GlyphPath[]
  parts: string[]
  steps: number
  carved: Record<string, CarvedFill>
  dotted: RenderedPath[] | null
  overrides: Record<string, RenderedPath[]>
}

export const GLYPHS: readonly Glyph[] = data.glyphs as Glyph[]

export type Fill = { kind: "regular" } | { kind: "solid" } | { kind: "parts"; parts: string[] } | { kind: "progress"; k: number } | { kind: "dotted" }
/** A form (spec D3): a fill, plus the mark when the glyph has one. */
export type Form = { fill: Fill; marked?: boolean }

const REGULAR: Form = { fill: { kind: "regular" } }

export function formKey(form: Form): string {
  const f = form.fill
  const fill = f.kind === "parts" ? `parts-${f.parts.join("+")}` : f.kind === "progress" ? `progress-${f.k}` : f.kind
  if (!form.marked) return fill
  return fill === "regular" ? "marked" : `${fill}-marked`
}

export function hasMark(glyph: Glyph): boolean {
  return glyph.paths.some((p) => p.role === "mark")
}

/** Every combination of parts short of all of them, smallest first (as packages/icons/scripts/lib/roles.ts). */
function partCombinations(names: readonly string[]): string[][] {
  const out: string[][] = []
  for (let bits = 1; bits < (1 << names.length) - 1; bits += 1) out.push(names.filter((_, i) => bits & (1 << i)))
  return out.sort((a, b) => a.length - b.length)
}

/** Every form the glyph has, in the generator's order: each fill then the same fill marked, dotted after regular. */
export function glyphForms(glyph: Glyph): Form[] {
  const fills: Fill[] = [{ kind: "regular" }]
  if (glyph.carved.solid) fills.push({ kind: "solid" })
  for (const parts of partCombinations(glyph.parts)) fills.push({ kind: "parts", parts })
  for (let k = 1; k <= glyph.steps; k += 1) fills.push({ kind: "progress", k })
  const mark = hasMark(glyph)
  const forms = fills.flatMap((fill): Form[] => (mark ? [{ fill }, { fill, marked: true }] : [{ fill }]))
  if (glyph.dotted) forms.splice(mark ? 2 : 1, 0, { fill: { kind: "dotted" } })
  return forms
}

/** progress(0) is regular. */
function normalize(form: Form): Form {
  return form.fill.kind === "progress" && form.fill.k <= 0 ? { fill: { kind: "regular" }, marked: form.marked } : form
}

export function hasForm(glyph: Glyph, form: Form): boolean {
  const key = formKey(normalize(form))
  return glyphForms(glyph).some((f) => formKey(f) === key)
}

/** The form actually drawn: the form itself when the glyph has it, else regular. */
export function drawnForm(glyph: Glyph, form: Form): Form {
  const f = normalize(form)
  return hasForm(glyph, f) ? f : REGULAR
}

export function isCarved(form: Form): boolean {
  return form.fill.kind === "solid" || form.fill.kind === "parts"
}

/** Whether a stroke-level form (regular, marked, progress) fills this path; a hidden path is drawn only then. */
export function isLit(p: GlyphPath, form: Form): boolean {
  const k = form.fill.kind === "progress" ? form.fill.k : 0
  return (form.marked === true && p.role === "mark") || (p.role === "step" && (p.step ?? 0) <= k)
}

/** The paths a lit, carved path is cut through: they are not drawn. */
export function cutIndices(glyph: Glyph, form: Form): Set<number> {
  return new Set(glyph.paths.flatMap((p) => (isLit(p, form) && p.fill !== undefined ? (p.cuts ?? []) : [])))
}

const stroked = (d: string): RenderedPath => ({ d, fill: false, evenOdd: false, stroke: true })
const filledPath = (d: string): RenderedPath => ({ d, fill: true, evenOdd: false, stroke: true })
const carvedPath = (d: string): RenderedPath => ({ d, fill: true, evenOdd: true, stroke: false })
const litPath = (p: GlyphPath): RenderedPath => (p.fill !== undefined ? carvedPath(p.fill) : filledPath(p.d))

/** The paths a form draws, as `renderForm` does; a form the glyph lacks draws regular. */
export function formPaths(glyph: Glyph, form: Form): RenderedPath[] {
  const drawn = drawnForm(glyph, form)
  const key = formKey(drawn)
  const override = glyph.overrides[key]
  if (override) return override
  if (drawn.fill.kind === "dotted") return glyph.dotted ?? []
  const carved = isCarved(drawn) ? glyph.carved[key] : undefined
  if (carved) {
    const pick = (i: number, draw: (p: GlyphPath) => RenderedPath) => {
      const p = glyph.paths[i]
      return p ? [draw(p)] : []
    }
    return [carvedPath(carved.d), ...carved.keep.flatMap((i) => pick(i, (p) => stroked(p.d))), ...carved.lit.flatMap((i) => pick(i, litPath))]
  }
  const cut = cutIndices(glyph, drawn)
  return glyph.paths.flatMap((p, i) => {
    const on = isLit(p, drawn)
    if ((p.hidden && !on) || cut.has(i)) return []
    return [on ? litPath(p) : stroked(p.d)]
  })
}

/** A form in words, for the review dialog: `solid · marked`, `left + middle`, `progress 2`. */
export function formLabel(form: Form): string {
  const f = form.fill
  const fill = f.kind === "parts" ? f.parts.join(" + ") : f.kind === "progress" ? `progress ${f.k}` : f.kind
  if (!form.marked) return fill
  return f.kind === "regular" ? "marked" : `${fill} · marked`
}

/** Every form the glyph has, as the review dialog lays them out. */
export function formChoices(glyph: Glyph): { key: string; label: string; form: Form; override: boolean }[] {
  return glyphForms(glyph).map((form) => {
    const key = formKey(form)
    return { key, label: formLabel(form), form, override: key in glyph.overrides }
  })
}

/** The fill picker: every glyph, those with no fill but regular, or those with a dotted, solid or parts fill. */
export type FillFilter = "all" | "regular" | "dotted" | "solid" | "parts"
export type MarkFilter = "any" | "marked" | "unmarked"
/** The Icons page's pickers (spec §3): each lists only the glyphs that have its form and draws them in it. */
export type GlyphView = { fill: FillFilter; mark: MarkFilter; progress: boolean; k: number }
export const DEFAULT_VIEW: GlyphView = { fill: "all", mark: "any", progress: false, k: 1 }

export function isDefaultView(view: GlyphView): boolean {
  return view.fill === "all" && view.mark === "any" && !view.progress
}

/** A glyph whose only fill is regular: no body, no steps, no dotted drawing (it may still have a mark). */
export function onlyRegular(glyph: Glyph): boolean {
  return glyph.carved.solid === undefined && glyph.steps === 0 && glyph.dotted === null
}

export function viewMatches(glyph: Glyph, view: GlyphView): boolean {
  const fill =
    view.fill === "all" ||
    (view.fill === "regular" && onlyRegular(glyph)) ||
    (view.fill === "dotted" && glyph.dotted !== null) ||
    (view.fill === "solid" && glyph.carved.solid !== undefined) ||
    (view.fill === "parts" && glyph.parts.length > 0)
  const mark = view.mark === "any" || (view.mark === "marked") === hasMark(glyph)
  return fill && mark && (!view.progress || glyph.steps > 0)
}

/** The form a tile draws: progress draws the steps (the fill picker then only filters); parts fills the first part. */
export function viewForm(glyph: Glyph, view: GlyphView): Form {
  const marked = view.mark === "marked" && hasMark(glyph)
  if (view.progress && glyph.steps > 0) return { fill: { kind: "progress", k: Math.min(view.k, glyph.steps) }, marked }
  if (view.fill === "dotted" && glyph.dotted) return { fill: { kind: "dotted" } }
  if (view.fill === "solid" && glyph.carved.solid) return { fill: { kind: "solid" }, marked }
  const first = glyph.parts[0]
  if (view.fill === "parts" && first !== undefined) return { fill: { kind: "parts", parts: [first] }, marked }
  return { fill: { kind: "regular" }, marked }
}

/** The kinds of form a glyph has beyond regular: the tile's signs and its label. */
export type GlyphSignSet = { solid: boolean; mark: boolean; parts: number; steps: number; dotted: boolean }

export function signsOf(glyph: Glyph): GlyphSignSet {
  return { solid: glyph.carved.solid !== undefined, mark: hasMark(glyph), parts: glyph.parts.length, steps: glyph.steps, dotted: glyph.dotted !== null }
}

export function describeSigns(s: GlyphSignSet): string {
  const words = [s.solid && "solid", s.mark && "mark", s.parts > 0 && `${s.parts} parts`, s.steps > 0 && `${s.steps} ${s.steps === 1 ? "step" : "steps"}`, s.dotted && "dotted"].filter(
    (w): w is string => typeof w === "string",
  )
  return words.length === 0 ? "regular only" : words.join(", ")
}

```

Then, further down the file:

- delete `availableForms`, `FormChoice`, the old `isLit`, `formPaths`, `cutIndices`, `hasForm` and `formChoices` if any of them survive below the replaced region (the old `formChoices` sits at the end of the file: delete it, the new one above replaces it);
- in `describeFlag`, the review data still uses the roles' old names: add above it

```ts
/** review.json was written before the 2026-11-09 amendment: its `focus` is today's mark, its `mark` a step. */
const ROLE_NOW: Readonly<Record<string, string>> = { focus: "mark", mark: "step", body: "body" }
const roleNow = (role: string): string => ROLE_NOW[role] ?? role
```

and in its `added-dot` case replace `as a hidden mark (area ${flag.area}): it is not drawn at rest, only lit.` with `as a hidden dot (area ${flag.area}): it is not drawn at rest, only lit.`; in the `conflict` case wrap `flag.was` and `flag.wanted` in `roleNow(…)`.

- [ ] **Step 4: Run the tests**

Run: `npm run test --workspace=@pbbls/web -- lib/design`
Expected: PASS. The components still import `FormChoice`, so `tsc` fails until Task 6: do not commit here; Tasks 5 and 6 are committed together (Task 6, Step 6).

- [ ] **Step 5: Commit together with Task 6** (see Task 6, Step 6).

---

### Task 6: The Fill, Mark and Progress pickers

**Files:**
- Modify: `apps/web/components/sandbox/design/DesignContext.tsx`
- Modify: `apps/web/components/sandbox/design/DesignDirectory.tsx`
- Modify: `apps/web/components/sandbox/design/DesignTools.tsx`
- Modify: `apps/web/components/sandbox/design/pages/IconsPage.tsx` (only what keeps it compiling; Task 7 finishes it)
- Modify: `apps/web/components/sandbox/design/GlyphFormView.tsx` (signature only; Task 8 finishes it)

- [ ] **Step 1: Context**

In `DesignContext.tsx`, replace the import of `FormChoice, GlyphShow` with `GlyphShow, GlyphView`, and replace

```ts
  /** The Icons page's form and progress (#1607 review). */
  glyphForm: FormChoice
  glyphProgress: number
```

with

```ts
  /** The Icons page's Fill, Mark and Progress pickers (#1607 review): what is listed and how it is drawn. */
  glyphView: GlyphView
```

- [ ] **Step 2: Directory**

In `DesignDirectory.tsx`: import `DEFAULT_VIEW, type GlyphShow, type GlyphView` from `@/lib/design/glyph-forms` (drop `FormChoice`); replace the two `useState` lines for `glyphForm` and `glyphProgress` with `const [glyphView, setGlyphView] = useState<GlyphView>(DEFAULT_VIEW)`; in the context value and its dependency list replace `glyphForm, glyphProgress` with `glyphView`; in `tools` replace the four `glyphForm`/`onGlyphForm`/`glyphProgress`/`onGlyphProgress` entries with `glyphView,` and `onGlyphView: setGlyphView,`.

- [ ] **Step 3: Tools**

In `DesignTools.tsx`:

1. Import `GLYPHS, glyphMatchesQuery, isDefaultView, showsGlyph, viewMatches, type FillFilter, type GlyphShow, type GlyphView, type MarkFilter` from `@/lib/design/glyph-forms`.
2. Replace `FORM_OPTIONS` and `PROGRESS_OPTIONS` with:

```ts
const FILL_OPTIONS: SelectOption<FillFilter>[] = [
  { value: "all", label: "All" },
  { value: "regular", label: "Regular only" },
  { value: "dotted", label: "Dotted" },
  { value: "solid", label: "Solid" },
  { value: "parts", label: "Parts" },
]
const MARK_OPTIONS: SelectOption<MarkFilter>[] = [
  { value: "any", label: "Any" },
  { value: "marked", label: "Marked" },
  { value: "unmarked", label: "Unmarked" },
]
const PROGRESS_OPTIONS: SelectOption<"off" | "on">[] = [
  { value: "off", label: "Off" },
  { value: "on", label: "Steps" },
]
const STEPS_OPTIONS: SelectOption<number>[] = [0, 1, 2, 3, 4, 5, 6, 7].map((k) => ({ value: k, label: String(k) }))
```

3. In `DesignToolsState`, replace the four `glyphForm` / `onGlyphForm` / `glyphProgress` / `onGlyphProgress` members with:

```ts
  glyphView: GlyphView
  onGlyphView: (view: GlyphView) => void
```

4. Replace the `matching` memo with:

```ts
  const filtering = icons && (s.iconQuery.trim() !== "" || s.glyphShow !== "all" || !isDefaultView(s.glyphView))
  const matching = useMemo(() => {
    if (!filtering) return null
    const list = GLYPHS.filter((g) => glyphMatchesQuery(g, s.iconQuery) && showsGlyph(g.name, s.glyphShow, verdicts) && viewMatches(g, s.glyphView))
    return { count: list.length, fixes: list.filter((g) => verdicts[g.name] === "fix").length }
  }, [filtering, s.iconQuery, s.glyphShow, s.glyphView, verdicts])
```

5. Replace the Form select and the progress select with:

```tsx
      {icons && <DesignSelect label="Fill" options={FILL_OPTIONS} value={s.glyphView.fill} onChange={(fill) => s.onGlyphView({ ...s.glyphView, fill })} />}
      {icons && <DesignSelect label="Mark" options={MARK_OPTIONS} value={s.glyphView.mark} onChange={(mark) => s.onGlyphView({ ...s.glyphView, mark })} />}
      {icons && (
        <DesignSelect
          label="Progress"
          options={PROGRESS_OPTIONS}
          value={s.glyphView.progress ? "on" : "off"}
          onChange={(v) => s.onGlyphView({ ...s.glyphView, progress: v === "on" })}
        />
      )}
      {icons && s.glyphView.progress && (
        <DesignSelect label="Steps lit" options={STEPS_OPTIONS} value={s.glyphView.k} onChange={(k) => s.onGlyphView({ ...s.glyphView, k })} />
      )}
```

6. Replace the count paragraph's text with:

```tsx
          {matching === null
            ? `${count.format(GLYPHS.length)} glyphs`
            : `${count.format(matching.count)} of ${count.format(GLYPHS.length)} glyphs${matching.fixes > 0 ? ` · ${count.format(matching.fixes)} marked fix` : ""}`}
```

- [ ] **Step 4: Keep the page and the view compiling**

`GlyphFormView.tsx`: change its props to `{ glyph: Glyph; form: Form; className?: string }` and replace its body's first four lines with:

```tsx
  const drawn = drawnForm(glyph, form)
  const override = formKey(drawn) === "regular" ? undefined : glyph.overrides[formKey(drawn)]
  const cut = cutIndices(glyph, drawn)
  const showSolid = drawn.fill.kind === "solid" && !override
```

replace `isLit(p, form, k)` with `isLit(p, drawn)`, `glyph.solid &&` with `glyph.carved.solid &&`, and `formPaths(glyph, "solid")` with `formPaths(glyph, { fill: { kind: "solid" } })`. Import `cutIndices, drawnForm, formKey, formPaths, isLit, type Form, type Glyph`. (Task 8 gives it parts and dotted layers.)

`IconsPage.tsx`: read `glyphView` instead of `glyphForm, glyphProgress` from `useDesign()`; in `GlyphTile` replace the `form: FormChoice` and `k: number` props with `form: Form`, pass `form={viewForm(glyph, glyphView)}`, render `<GlyphFormView glyph={glyph} form={form} className={…} />`, and delete the `!hasForm(glyph, form) && "opacity-40"` class (the list is filtered now). Add `viewMatches(g, glyphView)` to the list filter and `glyphView` to its memo dependencies. Fix the imports.

`GlyphReviewDialog.tsx`: replace the forms grid's map with

```tsx
                {formChoices(glyph).map((c) => (
                  <Cell key={c.key} label={c.label} note={c.override ? "override" : undefined}>
                    <GlyphFormView glyph={glyph} form={c.form} className="size-16" />
                  </Cell>
                ))}
```

- [ ] **Step 5: Check**

```bash
npm run test --workspace=@pbbls/web -- lib/design
cd apps/web && npx tsc --noEmit && cd ../..
npm run lint --workspace=@pbbls/web
```

All must pass.

- [ ] **Step 6: Commit Tasks 5 and 6**

```bash
git add apps/web/lib/design apps/web/components/sandbox/design
git status --short   # apps/web/AGENTS.md stays untracked
git commit -m "feat(web): pick the icons' fill, mark and progress, each listing and drawing its form

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Signs on each tile

**Files:**
- Create: `apps/web/components/sandbox/design/GlyphSigns.tsx`
- Modify: `apps/web/components/sandbox/design/pages/IconsPage.tsx`

One small sign per kind of form a glyph has beyond regular, each shaped like its form, so the row reads without a legend; the sign of the active picker turns primary.

- [ ] **Step 1: Create `GlyphSigns.tsx`**

```tsx
import type { GlyphSignSet, GlyphView } from "@/lib/design/glyph-forms"
import { cn } from "@/lib/utils"

const MAX_STEP_DOTS = 7

/**
 * The forms a glyph has beyond regular, as discreet signs under its name:
 * ● solid, ◉ mark, ◐ parts (with their count), a dot per step, a dotted ring.
 * The sign of the picker in use is primary. Decorative: the tile's label says
 * the same in words.
 */
export function GlyphSigns({ signs, view }: { signs: GlyphSignSet; view: GlyphView }) {
  const tone = (on: boolean) => (on ? "text-primary" : "text-muted-foreground/60")
  return (
    <span aria-hidden className="pointer-events-none absolute inset-x-0 bottom-1.5 flex h-2 items-center justify-center gap-1.5">
      {signs.solid && (
        <svg viewBox="0 0 6 6" className={cn("size-1.5", tone(view.fill === "solid"))}>
          <circle cx="3" cy="3" r="3" fill="currentColor" />
        </svg>
      )}
      {signs.mark && (
        <svg viewBox="0 0 6 6" className={cn("size-1.5", tone(view.mark === "marked"))}>
          <circle cx="3" cy="3" r="2.4" fill="none" stroke="currentColor" strokeWidth="1.2" />
          <circle cx="3" cy="3" r="1" fill="currentColor" />
        </svg>
      )}
      {signs.parts > 0 && (
        <span className={cn("flex items-center gap-0.5 text-[9px] leading-none tabular-nums", tone(view.fill === "parts"))}>
          <svg viewBox="0 0 6 6" className="size-1.5">
            <circle cx="3" cy="3" r="2.4" fill="none" stroke="currentColor" strokeWidth="1.2" />
            <path d="M3 0.6 A2.4 2.4 0 0 0 3 5.4 Z" fill="currentColor" />
          </svg>
          {signs.parts}
        </span>
      )}
      {signs.steps > 0 && (
        <span className={cn("flex items-center gap-px", tone(view.progress))}>
          {Array.from({ length: Math.min(signs.steps, MAX_STEP_DOTS) }, (_, i) => (
            <svg key={i} viewBox="0 0 2 2" className="size-[3px]">
              <circle cx="1" cy="1" r="1" fill="currentColor" />
            </svg>
          ))}
          {signs.steps > MAX_STEP_DOTS && <span className="text-[9px] leading-none">+</span>}
        </span>
      )}
      {signs.dotted && (
        <svg viewBox="0 0 6 6" className={cn("size-1.5", tone(view.fill === "dotted"))}>
          <circle cx="3" cy="3" r="2.4" fill="none" stroke="currentColor" strokeWidth="1.2" strokeDasharray="1.1 0.9" />
        </svg>
      )}
    </span>
  )
}
```

- [ ] **Step 2: Use it in the tile**

In `IconsPage.tsx`'s `GlyphTile`:

- add a `view: GlyphView` prop, passed as `view={glyphView}` from `IconsPage`;
- compute `const signs = signsOf(glyph)` at the top of `GlyphTile`;
- the tile's `li` padding `pb-3` becomes `pb-5`, so the signs sit under the name without touching it;
- the open button's `aria-label` becomes ``Open ${glyph.name} (${describeSigns(signs)}): every form, its twins and its roles``;
- render `<GlyphSigns signs={signs} view={view} />` as the last child of the `li`.

Import `describeSigns, signsOf, type GlyphView` and `GlyphSigns` from `../GlyphSigns`.

- [ ] **Step 3: Check**

```bash
cd apps/web && npx tsc --noEmit && cd ../..
npm run lint --workspace=@pbbls/web
npm run test --workspace=@pbbls/web -- lib/design
```

- [ ] **Step 4: Look at it**

The dev server already runs on http://localhost:3000 (do not start another). Open http://localhost:3000/sandbox/design/icons in a headless browser (or report that you cannot), at a 1280 px wide window, and check, in light and dark (the `D` key toggles):

- signs sit centred under the name, never over it, and a plain glyph shows none;
- Fill = Solid lists only glyphs with ●, and every ● turns primary;
- Mark = Marked lists only glyphs with ◉ and draws their mark lit;
- Progress = Steps, Steps lit 2 draws two steps on every listed glyph.

- [ ] **Step 5: Commit**

```bash
git add apps/web/components/sandbox/design
git commit -m "feat(web): sign the forms each glyph has on its tile

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Draw every form; the dialog's grid and roles legend

**Files:**
- Modify: `apps/web/components/sandbox/design/GlyphFormView.tsx`
- Modify: `apps/web/components/sandbox/design/GlyphReviewDialog.tsx`

- [ ] **Step 1: `GlyphFormView` draws carved, parts and dotted forms**

Replace the whole file with:

```tsx
"use client"

import { cutIndices, drawnForm, formKey, formPaths, isCarved, isLit, type Form, type Glyph, type RenderedPath } from "@/lib/design/glyph-forms"
import { cn } from "@/lib/utils"

const FADE = "transition-[fill-opacity,opacity] duration-300 ease-out motion-reduce:transition-none"

function Drawn({ paths }: { paths: RenderedPath[] }) {
  return paths.map((p, i) => (
    <path key={i} d={p.d} fill={p.fill ? "currentColor" : "none"} fillRule={p.evenOdd ? "evenodd" : undefined} stroke={p.stroke ? "currentColor" : "none"} />
  ))
}

/**
 * One glyph in one form for the design page's review. The strokes are always
 * drawn and each path's fill fades in or out, so marked and progress animate
 * step by step; a hidden path fades in filled when its role fills. A carved
 * form (solid or parts) and the dotted drawing sit in their own layers and
 * crossfade over the strokes. An override is drawn as it is, without a
 * transition. A form the glyph lacks draws regular.
 */
export function GlyphFormView({ glyph, form, className }: { glyph: Glyph; form: Form; className?: string }) {
  const drawn = drawnForm(glyph, form)
  const key = formKey(drawn)
  const override = key === "regular" ? undefined : glyph.overrides[key]
  const layer = override ? "override" : drawn.fill.kind === "dotted" ? "dotted" : isCarved(drawn) ? "carved" : "paths"
  const cut = cutIndices(glyph, drawn)
  // The carved layer keeps the last carved form it showed while it fades out: the solid when the glyph has one.
  const carvedForm: Form | null = layer === "carved" ? drawn : glyph.carved.solid ? { fill: { kind: "solid" } } : null
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.75} strokeLinecap="round" strokeLinejoin="round" aria-hidden className={className}>
      {override ? (
        <Drawn paths={override} />
      ) : (
        <>
          <g className={cn(FADE, layer === "paths" ? "opacity-100" : "opacity-0")}>
            {glyph.paths.map((p, i) => {
              const lit = layer === "paths" && isLit(p, drawn)
              const shown = (lit || !p.hidden) && !cut.has(i)
              const carved = p.fill !== undefined
              return (
                <g key={i}>
                  <path d={p.d} fill="currentColor" fillOpacity={lit && !carved ? 1 : 0} stroke={shown ? undefined : "none"} className={cn(FADE, shown ? "opacity-100" : "opacity-0")} />
                  {carved && <path d={p.fill} fill="currentColor" fillRule="evenodd" stroke="none" className={cn(FADE, lit ? "opacity-100" : "opacity-0")} />}
                </g>
              )
            })}
          </g>
          {carvedForm && (
            <g className={cn(FADE, layer === "carved" ? "opacity-100" : "opacity-0")}>
              <Drawn paths={formPaths(glyph, carvedForm)} />
            </g>
          )}
          {glyph.dotted && (
            <g className={cn(FADE, layer === "dotted" ? "opacity-100" : "opacity-0")}>
              <Drawn paths={glyph.dotted} />
            </g>
          )}
        </>
      )}
    </svg>
  )
}
```

- [ ] **Step 2: The dialog's roles legend**

In `GlyphReviewDialog.tsx`, replace `roleLabel` and `roleColour` with:

```ts
/** Parts take the three chart hues after the step and mark ones, in source order. */
const PART_COLOURS = ["text-chart-3", "text-chart-4", "text-chart-5"] as const

function roleLabel(p: GlyphPath): string {
  const role = p.role === "step" ? `step ${p.step ?? "?"}` : p.role === "body" && p.part ? `body · ${p.part}` : (p.role ?? "line")
  return p.hidden ? `${role}, hidden` : role
}

/** Steps and the mark in two chart hues, each part in its own, a whole body in ink; a line without a role stays muted. */
function roleColour(p: GlyphPath, parts: readonly string[]): string {
  if (p.role === "step") return "text-chart-1"
  if (p.role === "mark") return "text-chart-2"
  if (p.role === "body" && p.part !== undefined) return PART_COLOURS[Math.max(0, parts.indexOf(p.part)) % PART_COLOURS.length] ?? "text-foreground"
  if (p.role === "body") return "text-foreground"
  return "text-muted-foreground"
}
```

and pass `glyph.parts` at its two call sites in `RolesMap`: `roleColour(p, glyph.parts)`.

Update the dialog's doc comment (`every form the generator works out`) to mention that the forms grid lists every fill with and without the mark, every part combination and the dotted drawing; no other change to the dialog.

- [ ] **Step 3: Check**

```bash
cd apps/web && npx tsc --noEmit && cd ../..
npm run lint --workspace=@pbbls/web
npm run test --workspace=@pbbls/web
```

- [ ] **Step 4: Look at it**

On http://localhost:3000/sandbox/design/icons (dev server already running): open `home`'s tile; the forms grid shows `regular`, `marked`, `solid`, `solid · marked` (home has a body only if it was tagged; if not, pick any glyph with ● and ◉ signs). Switch Fill between All and Solid on the page and confirm the tiles crossfade. Report what you saw.

- [ ] **Step 5: Commit**

```bash
git add apps/web/components/sandbox/design
git commit -m "feat(web): draw parts, marked solids and dotted forms in the glyph review

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Full checks (controller)

- [ ] `npm run lint --workspace=@pbbls/icons && npm run build --workspace=@pbbls/icons && npm run test --workspace=@pbbls/icons && npm run check --workspace=@pbbls/icons && npm run lint:replaces --workspace=@pbbls/icons`
- [ ] `npm run lint --workspace=@pbbls/web && npm run test --workspace=@pbbls/web && (cd apps/web && npx tsc --noEmit)`
- [ ] Render the solids listed by Task 1, Step 5 before and after (scratchpad sheet) and show them to the maintainer.
- [ ] Hand the page to the maintainer for a look before part 3b's plan.
