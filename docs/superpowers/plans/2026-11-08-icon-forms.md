# Icon forms (parts 1–3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every glyph role-tagged paths and a generated data file holding its forms (regular, half, solid, progress), with state tokens, then guess the roles of the whole set from today's twins and let the maintainer review them on the design page.

**Architecture:** The TypeScript generator in `packages/icons` gains a forms engine (`roles`, `normalize`, `geometry`, `carve`, `forms`, `states`) and one new emitter that writes `packages/icons/glyphs/glyphs.json`, the single data format every surface will read. The old emitters keep running unchanged (old and new outputs side by side, spec §5). A stdlib Python wave tool guesses roles from the old twins and writes them into the sources; the web design page renders the forms from `glyphs.json` with a form switcher, and the maintainer records verdicts on the flagged tiles there.

**Tech Stack:** TypeScript (tsx, Vitest 5), `clipper-lib` 6.4.2 + `@types/clipper-lib` 6.4.0 (polygon offset and boolean ops), Python 3 stdlib (`python3 -I`), Next.js 16 sandbox page (React 19).

**Spec:** `docs/superpowers/specs/2026-11-08-icon-forms-design.md` (issue #1607).

**Scope of this plan:** parts 1–3 of the spec's §5 order. Parts 4 (web), 5 (iOS), 6 (Android) and 7 (removal) each get their own plan once part 2's data format and part 3's reviewed roles exist: their renderers read `glyphs.json` as defined in Task 2.9, so they are written against it, not guessed now.

**Prerequisite:** PR #1606 is merged into `main`. Every branch below starts from an updated `main`.

---

## Part 1 — docs (branch `docs/1607-icon-forms`, already holds the spec)

### Task 1.1: Decision-log entry and PR

**Files:**
- Modify: `docs/decisions/log.md` (append at the end)

- [ ] **Step 1: Rebase the branch on main**

```bash
git switch docs/1607-icon-forms
git fetch origin main && git rebase origin/main
```

Expected: the spec commit sits on top of `main` (which now holds #1606).

- [ ] **Step 2: Append the entry**

Append to `docs/decisions/log.md`:

```markdown

## 2026-11-08 — Icons are drawings with roles; forms are worked out, states are tokens

- **Status:** taken
- **Scope:** all
- **Context:** The icon package named states, not drawings: `<name>.active` covered anything from one lit dot to a solid body, `<name>.lit-N` did not say out of how many, and the only generic consumer was the tab bar's `activeTwin`. The 2026-11 everyday wave brought 828 more `.active` twins, and the set reached 1,878 emitted glyphs.
- **Decision:** A glyph is one drawing named for what it shows. Its paths carry roles (`body`, numbered `mark`s, `focus`); the generator works out the forms regular, half, solid (carved at build time) and progress k of n; an override file may replace a worked-out form. Interaction states map to forms through shared state tokens (`states.json`), read by components on every surface. The apps draw glyphs from generated data (`glyphs.json`) instead of the iOS asset catalog and Android VectorDrawables, so form changes can animate. Tags in the manifest make the set searchable.
- **Why:** A name that says what is drawn and a form that says how much is filled keep the set consistent as it grows; state is a component's concern. Drawing from data removes the per-glyph enum initializer that hit the JVM's 64 KB limit in the screenshot previews.
- **Consequences:** Every call site on web, iOS and Android moves (parts 4–6 of the stack); the twin files, asset catalog, VectorDrawables and old enums go in part 7. Until then the generator emits both models.
- **Supersedes / Superseded-by:** supersedes D5 and D9 of `docs/superpowers/specs/2026-10-28-icon-package-design.md` (twin names, `activeTwin`, `lit(n)`).
- **Refs:** #1607, #1606, `docs/superpowers/specs/2026-11-08-icon-forms-design.md`.
```

- [ ] **Step 3: Commit**

```bash
git add docs/decisions/log.md docs/superpowers/plans/2026-11-08-icon-forms.md
git commit -m "docs(ui): log the icon forms decision and plan parts 1-3"
```

- [ ] **Step 4: Open the PR (ask the maintainer to confirm labels and milestone first)**

Proposed: labels `docs`, `ui`, `no-lab-note`; milestone `M66 · Brand icons`; base `main`.

```bash
git push -u origin docs/1607-icon-forms
gh pr create --title "docs(ui): design icon forms, roles and state tokens" --label docs --label ui --label no-lab-note --milestone "M66 · Brand icons" --body "Resolves part 1 of #1607

The spec and the decision-log entry for the icon forms redesign: glyphs with role-tagged paths, worked-out forms, state tokens, apps drawing from data. Plan for parts 1-3 included.

Data safety: no change"
```

Expected: a PR URL. This part changes no code.

---

## Part 2 — package (branch `chore/1607-icon-forms-package` from updated `main`)

Every task in this part runs from `packages/icons`. Test command: `npx vitest run <file>`; whole package: `npm run test --workspace=@pbbls/icons`.

### Task 2.0: Branch and dependency

**Files:**
- Modify: `packages/icons/package.json`
- Modify: `package-lock.json` (root)

- [ ] **Step 1: Branch**

```bash
git switch main && git pull --ff-only
git switch -c chore/1607-icon-forms-package
```

- [ ] **Step 2: Add clipper-lib**

```bash
npm install --workspace=@pbbls/icons --save-dev --save-exact clipper-lib@6.4.2 @types/clipper-lib@6.4.0
```

Expected: both appear under `devDependencies` of `packages/icons/package.json` (the generator is a dev tool; nothing ships it).

- [ ] **Step 3: Prove the import works under tsx and tsc**

Create `packages/icons/scripts/lib/clipper.ts`:

```ts
/**
 * The one import of clipper-lib (CommonJS) for the ES-module generator, so the
 * interop is decided once. Coordinates are integers: callers scale by SCALE.
 */
import ClipperLib from "clipper-lib"

export { ClipperLib }

/** Glyph units to Clipper integers: 0.001 of a unit is far below a 3× hero pixel. */
export const SCALE = 1000
```

Run: `npx tsx -e "import('./scripts/lib/clipper.ts').then(m => console.log(typeof m.ClipperLib.ClipperOffset))"`
Expected: `function`

Run: `npm run build --workspace=@pbbls/icons`
Expected: no error.

- [ ] **Step 4: Commit**

```bash
git add package.json ../../package-lock.json scripts/lib/clipper.ts
git commit -m "chore(ui): add clipper-lib to the icon generator"
```

### Task 2.1: Manifest fields `family` and `tags`

**Files:**
- Modify: `packages/icons/scripts/lib/manifest.ts`
- Test: `packages/icons/scripts/lib/manifest.test.ts`

- [ ] **Step 1: Write the failing tests**

Append inside the file's top-level `describe` in `manifest.test.ts` (it already imports `parseManifest`):

```ts
  it("reads an optional family and tags", () => {
    const row = {
      name: "knot-sm", meaning: "m", variants: [], wave: "w", status: "candidate", migrated: [],
      replaces: { ios: [], android: [], web: [] },
      family: "knot",
      tags: { subject: ["knot"], size: ["sm"], keywords: ["done", "tied"] },
    }
    const parsed = parseManifest({ icons: [row] }).icons[0]
    expect(parsed?.family).toBe("knot")
    expect(parsed?.tags).toEqual({ subject: ["knot"], size: ["sm"], keywords: ["done", "tied"] })
  })

  it("defaults family to null and tags to empty", () => {
    const row = { name: "a", meaning: "m", variants: [], wave: "w", status: "candidate", migrated: [], replaces: { ios: [], android: [], web: [] } }
    const parsed = parseManifest({ icons: [row] }).icons[0]
    expect(parsed?.family).toBeNull()
    expect(parsed?.tags).toEqual({})
  })

  it("refuses an unknown tag key or a non-array tag", () => {
    const base = { name: "a", meaning: "m", variants: [], wave: "w", status: "candidate", migrated: [], replaces: { ios: [], android: [], web: [] } }
    expect(() => parseManifest({ icons: [{ ...base, tags: { colour: ["red"] } }] })).toThrow(/tags\.colour is not a tag key/)
    expect(() => parseManifest({ icons: [{ ...base, tags: { size: "sm" } }] })).toThrow(/tags\.size is not a string array/)
    expect(() => parseManifest({ icons: [{ ...base, family: 3 }] })).toThrow(/family is not a string/)
  })
```

- [ ] **Step 2: Run them to see them fail**

Run: `npx vitest run scripts/lib/manifest.test.ts`
Expected: FAIL (`family` is undefined).

- [ ] **Step 3: Implement**

In `manifest.ts`, add after the `SURFACES` export:

```ts
/** The tag keys a row may carry (spec D5). Tags help search; code never reads them. */
export const TAG_KEYS = ["subject", "wrapper", "number", "side", "direction", "size", "keywords"] as const
export type TagKey = (typeof TAG_KEYS)[number]
export type Tags = Partial<Record<TagKey, string[]>>

const isTagKey = (v: string): v is TagKey => (TAG_KEYS as readonly string[]).includes(v)
```

Add to `IconRow` (after `meaning`):

```ts
  /** Groups versions of one idea (`knot` for `knot-sm` and `knot-lg`); null when alone. */
  family: string | null
  tags: Tags
```

In `rowFrom`, destructure `family` and `tags` too:

```ts
  const { name, meaning, variants, wave, status, migrated, replaces, family, tags } = value
```

and before the final `return`, add:

```ts
  if (family !== undefined && family !== null && typeof family !== "string") throw new Error(`${where} (${name}).family is not a string`)
  const tagRecord: Tags = {}
  if (tags !== undefined) {
    if (!isRecord(tags)) throw new Error(`${where} (${name}).tags is not an object`)
    for (const [key, list] of Object.entries(tags)) {
      if (!isTagKey(key)) throw new Error(`${where} (${name}).tags.${key} is not a tag key (${TAG_KEYS.join(", ")})`)
      if (!isStringArray(list)) throw new Error(`${where} (${name}).tags.${key} is not a string array`)
      tagRecord[key] = list
    }
  }
```

and change the return to:

```ts
  return { name, meaning, family: typeof family === "string" ? family : null, tags: tagRecord, variants, wave, status, migrated: migratedSurfaces, replaces: lists }
```

- [ ] **Step 4: Run the tests**

Run: `npx vitest run scripts/lib/manifest.test.ts && npm run build --workspace=@pbbls/icons`
Expected: PASS, no type error. (Other files that build `IconRow` literals in tests may now fail typecheck; add `family: null, tags: {}` to each literal the compiler names.)

- [ ] **Step 5: Commit**

```bash
git add scripts/lib/manifest.ts scripts/lib/manifest.test.ts scripts/lib/*.test.ts
git commit -m "chore(ui): read family and tags from the icon manifest"
```

### Task 2.2: Roles

**Files:**
- Create: `packages/icons/scripts/lib/roles.ts`
- Test: `packages/icons/scripts/lib/roles.test.ts`

- [ ] **Step 1: Write the failing tests**

```ts
import { describe, expect, it } from "vitest"
import type { ParsedPath } from "./parse"
import { readRoles } from "./roles"

const p = (attrs: Record<string, string>): ParsedPath => ({ d: attrs.d ?? "M3 3 L4 4", filled: false, evenOdd: false, unstroked: false, attrs: { d: "M3 3 L4 4", ...attrs } })

describe("readRoles", () => {
  it("collects body, numbered marks and focus by path index", () => {
    const { roles, issues } = readRoles([
      p({ "data-role": "body" }),
      p({}),
      p({ "data-role": "mark", "data-mark": "2" }),
      p({ "data-role": "mark", "data-mark": "1" }),
      p({ "data-role": "mark", "data-mark": "1" }),
      p({ "data-role": "focus" }),
    ])
    expect(issues).toEqual([])
    expect(roles).toEqual({ body: [0], marks: [[3, 4], [2]], focus: [5] })
  })

  it("has no roles for a plain drawing", () => {
    expect(readRoles([p({}), p({})]).roles).toEqual({ body: [], marks: [], focus: [] })
  })

  it("reports unknown roles, missing or stray mark numbers and gaps", () => {
    const { issues } = readRoles([
      p({ "data-role": "halo" }),
      p({ "data-role": "mark" }),
      p({ "data-mark": "1" }),
      p({ "data-role": "mark", "data-mark": "3" }),
    ])
    expect(issues).toEqual([
      'path 1: data-role="halo" is not body, mark or focus',
      "path 2: a mark needs data-mark",
      "path 3: data-mark without data-role=\"mark\"",
      "marks must run 1…n without gaps; found 3",
    ])
  })
})
```

- [ ] **Step 2: Run to see it fail**

Run: `npx vitest run scripts/lib/roles.test.ts`
Expected: FAIL (module not found).

- [ ] **Step 3: Implement**

```ts
import type { ParsedPath } from "./parse"

/**
 * A glyph's roles (spec D2), as indices into its paths: the body (unioned),
 * the marks in fill order (`marks[0]` is mark 1, possibly several paths) and
 * the focus. Untagged paths are plain lines.
 */
export type GlyphRoles = { body: number[]; marks: number[][]; focus: number[] }

export const ROLES = ["body", "mark", "focus"] as const

export function readRoles(paths: readonly ParsedPath[]): { roles: GlyphRoles; issues: string[] } {
  const issues: string[] = []
  const body: number[] = []
  const focus: number[] = []
  const byMark = new Map<number, number[]>()
  paths.forEach((path, i) => {
    const where = `path ${i + 1}`
    const role = path.attrs["data-role"]
    const mark = path.attrs["data-mark"]
    if (role === undefined) {
      if (mark !== undefined) issues.push(`${where}: data-mark without data-role="mark"`)
      return
    }
    if (role === "body") body.push(i)
    else if (role === "focus") focus.push(i)
    else if (role === "mark") {
      const n = mark === undefined ? NaN : Number(mark)
      if (!Number.isInteger(n) || n < 1) {
        issues.push(`${where}: a mark needs data-mark`)
        return
      }
      byMark.set(n, [...(byMark.get(n) ?? []), i])
    } else issues.push(`${where}: data-role="${role}" is not body, mark or focus`)
    if (role !== "mark" && mark !== undefined) issues.push(`${where}: data-mark without data-role="mark"`)
  })
  const numbers = [...byMark.keys()].sort((a, b) => a - b)
  if (numbers.some((n, i) => n !== i + 1)) issues.push(`marks must run 1…n without gaps; found ${numbers.join(", ")}`)
  const marks = numbers.map((n) => byMark.get(n) ?? [])
  return { roles: { body, marks, focus }, issues }
}
```

- [ ] **Step 4: Run the tests**

Run: `npx vitest run scripts/lib/roles.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add scripts/lib/roles.ts scripts/lib/roles.test.ts
git commit -m "chore(ui): read body, mark and focus roles from icon sources"
```

### Task 2.3: Normalise path data (arcs to cubics)

**Files:**
- Create: `packages/icons/scripts/lib/normalize.ts`
- Test: `packages/icons/scripts/lib/normalize.test.ts`

- [ ] **Step 1: Write the failing tests**

```ts
import { describe, expect, it } from "vitest"
import { normalizePath } from "./normalize"

describe("normalizePath", () => {
  it("passes M L C Q Z through with two decimals at most", () => {
    expect(normalizePath("M3 3 L4.123 4 C5 5 6 6 7 7 Q8 8 9 9 Z")).toBe("M3 3 L4.12 4 C5 5 6 6 7 7 Q8 8 9 9 Z")
  })

  it("turns a half-circle arc into two cubics ending at the arc's end", () => {
    const out = normalizePath("M4 12 A8 8 0 0 1 20 12")
    expect(out.startsWith("M4 12 C")).toBe(true)
    expect(out.match(/C/g)?.length).toBe(2)
    expect(out.endsWith(" 20 12")).toBe(true)
    expect(out).not.toMatch(/A/)
  })

  it("draws a zero-radius arc as a line", () => {
    expect(normalizePath("M4 4 A0 0 0 0 1 8 8")).toBe("M4 4 L8 8")
  })
})
```

- [ ] **Step 2: Run to see it fail**

Run: `npx vitest run scripts/lib/normalize.test.ts`
Expected: FAIL (module not found).

- [ ] **Step 3: Implement**

```ts
import { tokenizePath } from "./lint"

/**
 * Path data every surface can parse with one small parser (spec D7): absolute
 * M L C Q Z only, numbers with at most two decimals. Arcs become cubic Béziers
 * (one per quarter turn at most), the standard endpoint-to-centre conversion.
 */

const fmt = (n: number): string => {
  const r = Math.round(n * 100) / 100
  return (Object.is(r, -0) ? 0 : r).toString()
}

type Pt = [number, number]

function arcToCubics(from: Pt, rx: number, ry: number, rotationDeg: number, largeArc: boolean, sweep: boolean, to: Pt): Pt[][] {
  if (rx === 0 || ry === 0) return []
  const phi = (rotationDeg * Math.PI) / 180
  const cos = Math.cos(phi)
  const sin = Math.sin(phi)
  const dx = (from[0] - to[0]) / 2
  const dy = (from[1] - to[1]) / 2
  const x1p = cos * dx + sin * dy
  const y1p = -sin * dx + cos * dy
  let a = Math.abs(rx)
  let b = Math.abs(ry)
  const lambda = (x1p * x1p) / (a * a) + (y1p * y1p) / (b * b)
  if (lambda > 1) {
    a *= Math.sqrt(lambda)
    b *= Math.sqrt(lambda)
  }
  const num = a * a * b * b - a * a * y1p * y1p - b * b * x1p * x1p
  const den = a * a * y1p * y1p + b * b * x1p * x1p
  const coef = (largeArc === sweep ? -1 : 1) * Math.sqrt(Math.max(0, num / den))
  const cxp = (coef * a * y1p) / b
  const cyp = (-coef * b * x1p) / a
  const cx = cos * cxp - sin * cyp + (from[0] + to[0]) / 2
  const cy = sin * cxp + cos * cyp + (from[1] + to[1]) / 2
  const angle = (ux: number, uy: number, vx: number, vy: number): number => {
    const sign = ux * vy - uy * vx < 0 ? -1 : 1
    const dot = (ux * vx + uy * vy) / (Math.hypot(ux, uy) * Math.hypot(vx, vy))
    return sign * Math.acos(Math.min(1, Math.max(-1, dot)))
  }
  const theta1 = angle(1, 0, (x1p - cxp) / a, (y1p - cyp) / b)
  let delta = angle((x1p - cxp) / a, (y1p - cyp) / b, (-x1p - cxp) / a, (-y1p - cyp) / b)
  if (!sweep && delta > 0) delta -= 2 * Math.PI
  if (sweep && delta < 0) delta += 2 * Math.PI
  const segments = Math.max(1, Math.ceil(Math.abs(delta) / (Math.PI / 2)))
  const step = delta / segments
  const k = (4 / 3) * Math.tan(step / 4)
  const point = (t: number): Pt => [cx + a * Math.cos(t) * cos - b * Math.sin(t) * sin, cy + a * Math.cos(t) * sin + b * Math.sin(t) * cos]
  const tangent = (t: number): Pt => [-a * Math.sin(t) * cos - b * Math.cos(t) * sin, -a * Math.sin(t) * sin + b * Math.cos(t) * cos]
  const out: Pt[][] = []
  for (let i = 0; i < segments; i += 1) {
    const t0 = theta1 + i * step
    const t1 = t0 + step
    const p0 = point(t0)
    const p3 = i === segments - 1 ? to : point(t1)
    const d0 = tangent(t0)
    const d1 = tangent(t1)
    out.push([
      [p0[0] + k * d0[0], p0[1] + k * d0[1]],
      [p3[0] - k * d1[0], p3[1] - k * d1[1]],
      p3,
    ])
  }
  return out
}

export function normalizePath(d: string): string {
  const parts: string[] = []
  let cursor: Pt = [0, 0]
  for (const { command, numbers } of tokenizePath(d)) {
    if (command === "Z") {
      parts.push("Z")
      continue
    }
    const arity = { M: 2, L: 2, C: 6, Q: 4, A: 7 }[command]
    if (arity === undefined) throw new Error(`normalizePath: ${command} is outside M L C Q A Z`)
    for (let i = 0; i + arity <= numbers.length; i += arity) {
      const g = numbers.slice(i, i + arity)
      const at = (j: number): number => g[j] ?? 0
      if (command === "A") {
        const to: Pt = [at(5), at(6)]
        const cubics = arcToCubics(cursor, at(0), at(1), at(2), at(3) !== 0, at(4) !== 0, to)
        if (cubics.length === 0) parts.push(`L${fmt(to[0])} ${fmt(to[1])}`)
        for (const [c1, c2, end] of cubics) {
          if (!c1 || !c2 || !end) continue
          parts.push(`C${fmt(c1[0])} ${fmt(c1[1])} ${fmt(c2[0])} ${fmt(c2[1])} ${fmt(end[0])} ${fmt(end[1])}`)
        }
        cursor = to
      } else {
        parts.push(command + g.map(fmt).join(" "))
        cursor = [at(arity - 2), at(arity - 1)]
      }
    }
  }
  return parts.join(" ")
}
```

- [ ] **Step 4: Run the tests**

Run: `npx vitest run scripts/lib/normalize.test.ts`
Expected: PASS. If the half-circle test reports 3 cubics, the segment count is off by float error: change `Math.ceil(Math.abs(delta) / (Math.PI / 2))` to `Math.ceil(Math.abs(delta) / (Math.PI / 2) - 1e-9)` and rerun.

- [ ] **Step 5: Commit**

```bash
git add scripts/lib/normalize.ts scripts/lib/normalize.test.ts
git commit -m "chore(ui): normalise icon path data to absolute M L C Q Z"
```

### Task 2.4: Geometry (flattening)

**Files:**
- Create: `packages/icons/scripts/lib/geometry.ts`
- Test: `packages/icons/scripts/lib/geometry.test.ts`

- [ ] **Step 1: Write the failing tests**

```ts
import { describe, expect, it } from "vitest"
import { flattenPath, isClosed } from "./geometry"

describe("flattenPath", () => {
  it("splits subpaths and marks the closed ones", () => {
    const subs = flattenPath("M2 2 L10 2 L10 10 Z M12 12 L20 12")
    expect(subs).toHaveLength(2)
    expect(subs[0]?.closed).toBe(true)
    expect(subs[0]?.points).toEqual([[2, 2], [10, 2], [10, 10]])
    expect(subs[1]?.closed).toBe(false)
  })

  it("samples a cubic finely enough for the hero size", () => {
    const [sub] = flattenPath("M2 12 C2 2 22 2 22 12")
    expect(sub?.points.length).toBeGreaterThan(40)
    expect(sub?.points.at(-1)).toEqual([22, 12])
  })

  it("treats a subpath whose ends meet as closed", () => {
    expect(isClosed({ points: [[2, 2], [10, 2], [2.2, 2.1]], closed: false })).toBe(true)
    expect(isClosed({ points: [[2, 2], [10, 2]], closed: false })).toBe(false)
  })
})
```

- [ ] **Step 2: Run to see it fail**

Run: `npx vitest run scripts/lib/geometry.test.ts`
Expected: FAIL (module not found).

- [ ] **Step 3: Implement**

```ts
import { tokenizePath } from "./lint"
import { normalizePath } from "./normalize"

export type Pt = [number, number]
export type Subpath = { points: Pt[]; closed: boolean }

/** Glyph units per sampled segment: 0.25 is about 1.5 px at the 48 px hero at 3×. */
const SEGMENT = 0.25
/** Ends closer than this close a subpath, as a hand closes a ring (half the stroke). */
const CLOSE_GAP = 0.875

function cubicPoints(p0: Pt, p1: Pt, p2: Pt, p3: Pt): Pt[] {
  const hull = Math.hypot(p1[0] - p0[0], p1[1] - p0[1]) + Math.hypot(p2[0] - p1[0], p2[1] - p1[1]) + Math.hypot(p3[0] - p2[0], p3[1] - p2[1])
  const n = Math.max(8, Math.ceil(hull / SEGMENT))
  const out: Pt[] = []
  for (let k = 1; k <= n; k += 1) {
    const t = k / n
    const u = 1 - t
    out.push([
      u * u * u * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t * t * t * p3[0],
      u * u * u * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t * t * t * p3[1],
    ])
  }
  out[out.length - 1] = p3
  return out
}

/** Flattens path data (arcs included, through `normalizePath`) into polylines. */
export function flattenPath(d: string): Subpath[] {
  const subs: Subpath[] = []
  let current: Subpath | null = null
  let cursor: Pt = [0, 0]
  for (const { command, numbers } of tokenizePath(normalizePath(d))) {
    if (command === "Z") {
      if (current) current.closed = true
      continue
    }
    const arity = { M: 2, L: 2, C: 6, Q: 4 }[command] ?? 0
    for (let i = 0; i + arity <= numbers.length; i += arity) {
      const g = numbers.slice(i, i + arity)
      const at = (j: number): number => g[j] ?? 0
      if (command === "M") {
        current = { points: [[at(0), at(1)]], closed: false }
        subs.push(current)
        cursor = [at(0), at(1)]
      } else if (current && command === "L") {
        cursor = [at(0), at(1)]
        current.points.push(cursor)
      } else if (current && command === "C") {
        const end: Pt = [at(4), at(5)]
        current.points.push(...cubicPoints(cursor, [at(0), at(1)], [at(2), at(3)], end))
        cursor = end
      } else if (current && command === "Q") {
        const q: Pt = [at(0), at(1)]
        const end: Pt = [at(2), at(3)]
        const c1: Pt = [cursor[0] + (2 / 3) * (q[0] - cursor[0]), cursor[1] + (2 / 3) * (q[1] - cursor[1])]
        const c2: Pt = [end[0] + (2 / 3) * (q[0] - end[0]), end[1] + (2 / 3) * (q[1] - end[1])]
        current.points.push(...cubicPoints(cursor, c1, c2, end))
        cursor = end
      }
    }
  }
  return subs
}

/** A subpath is closed when it says Z or when its ends meet within half a stroke. */
export function isClosed(sub: Subpath): boolean {
  if (sub.closed) return true
  const first = sub.points[0]
  const last = sub.points[sub.points.length - 1]
  if (!first || !last || sub.points.length < 3) return false
  return Math.hypot(first[0] - last[0], first[1] - last[1]) <= CLOSE_GAP
}
```

- [ ] **Step 4: Run the tests**

Run: `npx vitest run scripts/lib/geometry.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add scripts/lib/geometry.ts scripts/lib/geometry.test.ts
git commit -m "chore(ui): flatten icon paths into polylines"
```

### Task 2.5: Carve the solid form

**Files:**
- Create: `packages/icons/scripts/lib/carve.ts`
- Test: `packages/icons/scripts/lib/carve.test.ts`

The solid is the body (unioned, grown by half the 1.75 stroke so it keeps the drawn silhouette) minus every other path that lies inside the body, outlined at the stroke width with round caps. Paths outside the body are returned as `keep`: they stay stroked on top (spec D3).

- [ ] **Step 1: Write the failing tests**

```ts
import { describe, expect, it } from "vitest"
import { filledArea } from "./lint"
import { carveSolid } from "./carve"

const SQUARE = "M4 4 L20 4 L20 20 L4 20 Z"

describe("carveSolid", () => {
  it("fills the body grown by half the stroke", () => {
    const { d, keep, area } = carveSolid([SQUARE], [])
    expect(keep).toEqual([])
    // 16×16 grown by 0.875 on every side with round corners: a little under 17.75².
    expect(area).toBeGreaterThan(310)
    expect(area).toBeLessThan(17.75 * 17.75)
    expect(filledArea(d, true)).toBeGreaterThan(300)
  })

  it("cuts an inner line out of the body and keeps an outside line", () => {
    const inner = "M8 12 L16 12"
    const outside = "M2 22 L6 22"
    const whole = carveSolid([SQUARE], []).area
    const { area, keep } = carveSolid([SQUARE], [inner, outside])
    // An 8-unit line outlined at 1.75 with round caps: 8 × 1.75 + π × 0.875².
    const cut = 8 * 1.75 + Math.PI * 0.875 * 0.875
    expect(whole - area).toBeGreaterThan(cut - 0.5)
    expect(whole - area).toBeLessThan(cut + 0.5)
    expect(keep).toEqual([1])
  })

  it("rounds its path data to two decimals and closes every ring", () => {
    const { d } = carveSolid([SQUARE], ["M8 12 L16 12"])
    expect(d).not.toMatch(/\d\.\d{3}/)
    expect(d.trim().endsWith("Z")).toBe(true)
  })
})
```

- [ ] **Step 2: Run to see it fail**

Run: `npx vitest run scripts/lib/carve.test.ts`
Expected: FAIL (module not found).

- [ ] **Step 3: Implement**

```ts
import { ClipperLib, SCALE } from "./clipper"
import { flattenPath, isClosed, type Pt } from "./geometry"

/** Half the 1.75 stroke: the body grows by it, inner lines are outlined by it. */
const HALF_STROKE = 0.875
/** Round joins and caps are approximated within this many glyph units. */
const ARC_TOLERANCE = 0.01

type IntPath = { X: number; Y: number }[]

const toInt = (points: Pt[]): IntPath => points.map(([x, y]) => ({ X: Math.round(x * SCALE), Y: Math.round(y * SCALE) }))
const fmt = (n: number): string => (Math.round((n / SCALE) * 100) / 100).toString()

function offset(paths: IntPath[], join: number, end: number, delta: number): IntPath[] {
  const co = new ClipperLib.ClipperOffset(2, ARC_TOLERANCE * SCALE)
  co.AddPaths(paths, join, end)
  const out: IntPath[] = []
  co.Execute(out, delta * SCALE)
  return out
}

function boolean(type: number, subject: IntPath[], clip: IntPath[]): IntPath[] {
  const c = new ClipperLib.Clipper()
  c.AddPaths(subject, ClipperLib.PolyType.ptSubject, true)
  c.AddPaths(clip, ClipperLib.PolyType.ptClip, true)
  const out: IntPath[] = []
  c.Execute(type, out, ClipperLib.PolyFillType.pftNonZero, ClipperLib.PolyFillType.pftNonZero)
  return out
}

/** True when every sampled point of `d` lies inside (or on) the region. */
function inside(d: string, region: IntPath[]): boolean {
  return flattenPath(d)
    .flatMap((s) => toInt(s.points))
    .every((pt) => region.some((ring) => ClipperLib.Clipper.PointInPolygon(pt, ring) !== 0))
}

export type Carved = {
  /** One compound path, filled even-odd with no stroke. */
  d: string
  /** Indices (into `others`) of the paths outside the body: drawn stroked on top. */
  keep: number[]
  /** Filled area in square glyph units. */
  area: number
}

/**
 * The solid form (spec D3, D8): the body's closed subpaths unioned and grown
 * by half the stroke, minus every other path inside the body outlined at the
 * stroke width with round caps.
 */
export function carveSolid(body: readonly string[], others: readonly string[]): Carved {
  const rings = body.flatMap((d) => flattenPath(d)).filter(isClosed).map((s) => toInt(s.points))
  const region = boolean(ClipperLib.ClipType.ctUnion, rings, [])
  const grown = offset(region, ClipperLib.JoinType.jtRound, ClipperLib.EndType.etClosedPolygon, HALF_STROKE)
  const keep: number[] = []
  const cutters: IntPath[] = []
  others.forEach((d, i) => {
    if (!inside(d, region)) {
      keep.push(i)
      return
    }
    for (const sub of flattenPath(d)) {
      const end = isClosed(sub) ? ClipperLib.EndType.etClosedLine : ClipperLib.EndType.etOpenRound
      cutters.push(...offset([toInt(sub.points)], ClipperLib.JoinType.jtRound, end, HALF_STROKE))
    }
  })
  const result = cutters.length > 0 ? boolean(ClipperLib.ClipType.ctDifference, grown, cutters) : grown
  const area = result.reduce((sum, ring) => sum + ClipperLib.Clipper.Area(ring), 0) / (SCALE * SCALE)
  const d = result
    .filter((ring) => ring.length > 2)
    .map((ring) => ring.map((pt, i) => `${i === 0 ? "M" : "L"}${fmt(pt.X)} ${fmt(pt.Y)}`).join(" ") + " Z")
    .join(" ")
  return { d, keep, area: Math.abs(area) }
}
```

- [ ] **Step 4: Run the tests**

Run: `npx vitest run scripts/lib/carve.test.ts`
Expected: PASS. `Clipper.Area` returns signed areas (holes negative), so the sum is the filled area; if the first test shows an area near 0 or doubled, the output orientation is reversed: replace the reduce with `result.reduce((s, r) => s + Math.abs(ClipperLib.Clipper.Area(r)) * (ClipperLib.Clipper.Orientation(r) ? 1 : -1), 0)` and rerun.

- [ ] **Step 5: Commit**

```bash
git add scripts/lib/carve.ts scripts/lib/carve.test.ts
git commit -m "chore(ui): carve an icon's solid form from its body"
```

### Task 2.6: Forms and overrides

**Files:**
- Create: `packages/icons/scripts/lib/forms.ts`
- Test: `packages/icons/scripts/lib/forms.test.ts`

- [ ] **Step 1: Write the failing tests**

```ts
import { describe, expect, it } from "vitest"
import { formKey, formsOf, renderForm, type GlyphData } from "./forms"

const glyph: GlyphData = {
  name: "path-river",
  meaning: "m",
  family: null,
  tags: {},
  paths: [
    { d: "M4 4 L20 4 L20 20 L4 20 Z", role: "body" },
    { d: "M8 12 L16 12" },
    { d: "M6 6 L7 7", role: "mark", mark: 1 },
    { d: "M9 9 L10 10", role: "mark", mark: 2 },
    { d: "M12 12 L13 13", role: "focus" },
  ],
  marks: 2,
  solid: { d: "M1 1 L2 2 Z", keep: [] },
  overrides: {},
}

describe("forms", () => {
  it("lists the forms the roles allow", () => {
    expect(formsOf(glyph)).toEqual(["regular", "half", "solid", "progress-1", "progress-2"])
    expect(formsOf({ ...glyph, paths: [{ d: "M3 3 L4 4" }], marks: 0, solid: null })).toEqual(["regular"])
  })

  it("draws regular as strokes only", () => {
    expect(renderForm(glyph, { kind: "regular" }).every((p) => p.stroke && !p.fill)).toBe(true)
  })

  it("fills the focus for half and the first k marks for progress", () => {
    expect(renderForm(glyph, { kind: "half" }).map((p) => p.fill)).toEqual([false, false, false, false, true])
    expect(renderForm(glyph, { kind: "progress", k: 1 }).map((p) => p.fill)).toEqual([false, false, true, false, false])
    expect(renderForm(glyph, { kind: "progress", k: 0 })).toEqual(renderForm(glyph, { kind: "regular" }))
  })

  it("draws solid as the carved path plus the kept lines", () => {
    const out = renderForm(glyph, { kind: "solid" })
    expect(out[0]).toEqual({ d: "M1 1 L2 2 Z", fill: true, evenOdd: true, stroke: false })
    expect(out).toHaveLength(1)
  })

  it("lets an override replace a form", () => {
    const over = { ...glyph, overrides: { half: [{ d: "M5 5 L6 6", fill: true, evenOdd: false, stroke: true }] } }
    expect(renderForm(over, { kind: "half" })).toEqual([{ d: "M5 5 L6 6", fill: true, evenOdd: false, stroke: true }])
    expect(formKey({ kind: "progress", k: 2 })).toBe("progress-2")
  })

  it("refuses a form the glyph does not have", () => {
    expect(() => renderForm({ ...glyph, solid: null }, { kind: "solid" })).toThrow(/path-river has no solid form/)
    expect(() => renderForm(glyph, { kind: "progress", k: 3 })).toThrow(/path-river has no progress-3 form/)
  })
})
```

- [ ] **Step 2: Run to see it fail**

Run: `npx vitest run scripts/lib/forms.test.ts`
Expected: FAIL (module not found).

- [ ] **Step 3: Implement**

```ts
import type { Tags } from "./manifest"

/** One path of a glyph in `glyphs.json`: normalised data and its role. */
export type GlyphPath = { d: string; role?: "body" | "mark" | "focus"; mark?: number }

/** A path as a form draws it. */
export type RenderedPath = { d: string; fill: boolean; evenOdd: boolean; stroke: boolean }

/** A glyph as `glyphs.json` stores it (spec §2): the contract every surface reads. */
export type GlyphData = {
  name: string
  meaning: string
  family: string | null
  tags: Tags
  paths: GlyphPath[]
  /** The number of marks, n of progress(k). */
  marks: number
  /** The carved solid and the paths kept stroked on top, or null without a body. */
  solid: { d: string; keep: number[] } | null
  /** Hand-drawn replacements, keyed by form (`half`, `solid`, `progress-2`). */
  overrides: Record<string, RenderedPath[]>
}

export type Form = { kind: "regular" } | { kind: "half" } | { kind: "solid" } | { kind: "progress"; k: number }

export function formKey(form: Form): string {
  return form.kind === "progress" ? `progress-${form.k}` : form.kind
}

/** The forms a glyph has, in a stable order (spec D3). */
export function formsOf(glyph: GlyphData): string[] {
  const forms = ["regular"]
  if (glyph.paths.some((p) => p.role === "focus")) forms.push("half")
  if (glyph.solid) forms.push("solid")
  for (let k = 1; k <= glyph.marks; k += 1) forms.push(`progress-${k}`)
  return forms
}

const stroked = (d: string): RenderedPath => ({ d, fill: false, evenOdd: false, stroke: true })
const filled = (d: string): RenderedPath => ({ d, fill: true, evenOdd: false, stroke: true })

/** The paths a form draws, overrides first (spec D3, D4). `progress(0)` is regular. */
export function renderForm(glyph: GlyphData, form: Form): RenderedPath[] {
  if (form.kind === "progress" && form.k === 0) return renderForm(glyph, { kind: "regular" })
  const key = formKey(form)
  if (!formsOf(glyph).includes(key)) throw new Error(`${glyph.name} has no ${key} form`)
  const override = glyph.overrides[key]
  if (override) return override
  switch (form.kind) {
    case "regular":
      return glyph.paths.map((p) => stroked(p.d))
    case "half":
      return glyph.paths.map((p) => (p.role === "focus" ? filled(p.d) : stroked(p.d)))
    case "progress":
      return glyph.paths.map((p) => (p.role === "mark" && (p.mark ?? 0) <= form.k ? filled(p.d) : stroked(p.d)))
    case "solid": {
      const solid = glyph.solid
      if (!solid) throw new Error(`${glyph.name} has no solid form`)
      const others = glyph.paths.filter((p) => p.role !== "body")
      return [{ d: solid.d, fill: true, evenOdd: true, stroke: false }, ...solid.keep.flatMap((i) => (others[i] ? [stroked(others[i].d)] : []))]
    }
  }
}
```

- [ ] **Step 4: Run the tests**

Run: `npx vitest run scripts/lib/forms.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add scripts/lib/forms.ts scripts/lib/forms.test.ts
git commit -m "chore(ui): work out an icon's forms from its roles"
```

### Task 2.7: State tokens

**Files:**
- Create: `packages/icons/states.json`
- Create: `packages/icons/scripts/lib/states.ts`
- Test: `packages/icons/scripts/lib/states.test.ts`

- [ ] **Step 1: Write `states.json`**

```json
{
  "states": {
    "rest": ["regular"],
    "hover": ["half", "regular"],
    "pressed": ["half", "regular"],
    "selected": ["solid", "progress-n", "half", "regular"],
    "disabled": ["regular"],
    "progress": ["progress-k", "regular"],
    "done": ["progress-n", "solid", "regular"]
  }
}
```

`progress-n` means the glyph's last mark; `progress-k` the caller's k (clamped to n).

- [ ] **Step 2: Write the failing tests**

```ts
import { describe, expect, it } from "vitest"
import { parseStates, resolveState } from "./states"

const table = parseStates({
  states: { rest: ["regular"], hover: ["half", "regular"], selected: ["solid", "progress-n", "half", "regular"], progress: ["progress-k", "regular"], done: ["progress-n", "solid", "regular"] },
})

describe("states", () => {
  it("takes the first form the glyph has", () => {
    expect(resolveState(table, ["regular", "half", "solid"], "selected")).toEqual({ kind: "solid" })
    expect(resolveState(table, ["regular", "progress-1", "progress-2"], "selected")).toEqual({ kind: "progress", k: 2 })
    expect(resolveState(table, ["regular"], "hover")).toEqual({ kind: "regular" })
  })

  it("clamps progress-k to the glyph's marks and falls back without marks", () => {
    expect(resolveState(table, ["regular", "progress-1", "progress-2", "progress-3"], "progress", 5)).toEqual({ kind: "progress", k: 3 })
    expect(resolveState(table, ["regular", "progress-1"], "progress", 0)).toEqual({ kind: "progress", k: 0 })
    expect(resolveState(table, ["regular"], "progress", 2)).toEqual({ kind: "regular" })
  })

  it("refuses a table whose state does not end in regular, or an unknown form", () => {
    expect(() => parseStates({ states: { rest: ["solid"] } })).toThrow(/rest must end with regular/)
    expect(() => parseStates({ states: { rest: ["glow", "regular"] } })).toThrow(/rest: unknown form glow/)
  })
})
```

- [ ] **Step 3: Run to see it fail**

Run: `npx vitest run scripts/lib/states.test.ts`
Expected: FAIL (module not found).

- [ ] **Step 4: Implement**

```ts
import { readFileSync } from "node:fs"
import { join } from "node:path"
import type { Form } from "./forms"

/** A state's forms, first available wins (spec D6). */
export type StateTable = Record<string, string[]>

export const STATES_FILE = "states.json"
const FORM_WORDS = new Set(["regular", "half", "solid", "progress-n", "progress-k"])

export function parseStates(json: unknown): StateTable {
  if (typeof json !== "object" || json === null || !("states" in json)) throw new Error(`${STATES_FILE}: expected { "states": {...} }`)
  const states = (json as { states: unknown }).states
  if (typeof states !== "object" || states === null) throw new Error(`${STATES_FILE}: states is not an object`)
  const table: StateTable = {}
  for (const [state, list] of Object.entries(states)) {
    if (!Array.isArray(list) || !list.every((f): f is string => typeof f === "string")) throw new Error(`${STATES_FILE}: ${state} is not a string array`)
    for (const f of list) if (!FORM_WORDS.has(f)) throw new Error(`${STATES_FILE}: ${state}: unknown form ${f}`)
    if (list[list.length - 1] !== "regular") throw new Error(`${STATES_FILE}: ${state} must end with regular, the form every glyph has`)
    table[state] = list
  }
  return table
}

export function loadStates(packageRoot: string): StateTable {
  return parseStates(JSON.parse(readFileSync(join(packageRoot, STATES_FILE), "utf8")))
}

/** The form a state shows on a glyph whose forms are `forms` (from `formsOf`). */
export function resolveState(table: StateTable, forms: readonly string[], state: string, k = 0): Form {
  const list = table[state]
  if (!list) throw new Error(`unknown state ${state}`)
  const n = forms.filter((f) => f.startsWith("progress-")).length
  for (const word of list) {
    if (word === "regular") return { kind: "regular" }
    if ((word === "half" || word === "solid") && forms.includes(word)) return { kind: word }
    if (word === "progress-n" && n > 0) return { kind: "progress", k: n }
    if (word === "progress-k" && n > 0) return { kind: "progress", k: Math.max(0, Math.min(k, n)) }
  }
  return { kind: "regular" }
}
```

- [ ] **Step 5: Run the tests**

Run: `npx vitest run scripts/lib/states.test.ts`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add states.json scripts/lib/states.ts scripts/lib/states.test.ts
git commit -m "chore(ui): add icon state tokens with their fallbacks"
```

### Task 2.8: Lint roles, overrides, form-word names and tags

**Files:**
- Modify: `packages/icons/scripts/lib/lint.ts`
- Modify: `packages/icons/scripts/lib/manifest.ts` (override form names)
- Test: `packages/icons/scripts/lib/lint.test.ts`

- [ ] **Step 1: Read how `lint.test.ts` builds a fixture package**

Run: `sed -n 1,40p scripts/lib/lint.test.ts`
It writes a temporary package (an `icons.json`, `src/` files and a `waves/<wave>/` folder) and calls `lint(root)`. Reuse its helper in the new tests below; the helper's name in that file is the one to call (the snippets below call it `fixture(rows, files)`; rename to match).

- [ ] **Step 2: Write the failing tests**

Append to `lint.test.ts`:

```ts
describe("roles and overrides", () => {
  const svg = (paths: string) =>
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">\n${paths}\n</svg>\n`
  const row = (name: string) => ({ name, meaning: "m", variants: [], wave: "w", status: "candidate", migrated: [], replaces: { ios: [], android: [], web: [] } })

  it("accepts role attributes", () => {
    const root = fixture([row("ring")], { "ring.svg": svg('  <path d="M4 4 L20 4 L20 20 L4 20 Z" data-role="body"/>\n  <path d="M8 12 L16 12" data-role="mark" data-mark="1"/>') })
    expect(lint(root).errors).toEqual([])
  })

  it("reports role mistakes and an open body", () => {
    const root = fixture([row("ring")], { "ring.svg": svg('  <path d="M4 4 L20 4" data-role="body"/>\n  <path d="M8 12 L16 12" data-role="mark" data-mark="2"/>') })
    const messages = lint(root).errors.map((e) => e.message)
    expect(messages).toContain("marks must run 1…n without gaps; found 2")
    expect(messages).toContain("the body has no closed subpath")
  })

  it("accepts an override for a form the glyph has and refuses one it lacks", () => {
    const base = svg('  <path d="M4 4 L20 4 L20 20 L4 20 Z" data-role="body"/>')
    const over = svg('  <path d="M4 4 L20 4 L20 20 L4 20 Z" fill="currentColor"/>')
    expect(lint(fixture([row("ring")], { "ring.svg": base, "ring.solid.svg": over })).errors).toEqual([])
    const messages = lint(fixture([row("ring")], { "ring.svg": base, "ring.half.svg": over })).errors.map((e) => e.message)
    expect(messages).toContain('override "half" for a form ring does not have')
  })

  it("refuses a name ending in a form word", () => {
    const messages = lint(fixture([row("scale-half")], { "scale-half.svg": svg('  <path d="M4 4 L20 20"/>') })).errors.map((e) => e.message)
    expect(messages).toContain('"scale-half" ends in a form word (regular, half, solid, of)')
  })
})
```

- [ ] **Step 3: Run to see them fail**

Run: `npx vitest run scripts/lib/lint.test.ts`
Expected: FAIL (role attributes are "not allowed", overrides are unknown variants).

- [ ] **Step 4: Implement in `manifest.ts`**

Add after `VARIANTS`:

```ts
/** An override file's form (spec D4): `half`, `solid` or `progress-<k>`. */
export const OVERRIDE_FORM = /^(half|solid|progress-[1-9]\d*)$/
export const isOverrideForm = (v: string): boolean => OVERRIDE_FORM.test(v)
```

- [ ] **Step 5: Implement in `lint.ts`**

1. Imports: add `isOverrideForm` to the `./manifest` import, and:

```ts
import { carveSolid } from "./carve"
import { flattenPath, isClosed } from "./geometry"
import { readRoles } from "./roles"
```

2. Allow the role attributes: change `PATH_ATTRIBUTES` to

```ts
const PATH_ATTRIBUTES = new Set(["d", "fill", "fill-rule", "stroke", "data-role", "data-mark"])
```

3. Add the constants after `WARN_AREA`:

```ts
/** A carved solid must keep at least this much filled area (square units). */
export const MIN_CARVED_AREA = 4
/** A name's last segment may not be one of these (spec D1). */
const FORM_WORDS = ["regular", "half", "solid", "of"]
```

4. In `lintSource`, after `parsed.paths.forEach(...)`, add the role checks for base files:

```ts
  if (!isTwin) {
    const { roles, issues } = readRoles(parsed.paths)
    for (const message of issues) result.errors.push({ file, rule: "roles", message })
    if (roles.body.length > 0) {
      const bodyD = roles.body.flatMap((i) => (parsed.paths[i] ? [parsed.paths[i].d] : []))
      if (!bodyD.flatMap((d) => flattenPath(d)).some(isClosed)) result.errors.push({ file, rule: "roles", message: "the body has no closed subpath" })
      else {
        const others = parsed.paths.filter((_, i) => !roles.body.includes(i)).map((p) => p.d)
        const { area } = carveSolid(bodyD, others)
        if (area < MIN_CARVED_AREA) result.errors.push({ file, rule: "roles", message: `the carved solid keeps ${area.toFixed(1)} square units, under ${MIN_CARVED_AREA}` })
      }
    }
  }
```

5. Nothing changes in `lint()`: it already passes `isTwin = true` for any `<name>.<x>.svg`, so an override may carry fills and skips the role checks, like a twin.

6. In `lintManifest`, in the row loop, add the form-word rule:

```ts
    const last = row.name.split("-").at(-1) ?? ""
    if (FORM_WORDS.includes(last)) errors.push({ file, rule: "manifest", message: `"${row.name}" ends in a form word (${FORM_WORDS.join(", ")})` })
```

and in the files loop, replace the variant branch with one that accepts overrides:

```ts
    if (isOverrideForm(split.variant)) {
      if (!base) errors.push({ file: f, rule: "manifest", message: `override without its base: no row "${split.base}"` })
      else if (!overrideAllowed(root, split.base, split.variant)) errors.push({ file: f, rule: "manifest", message: `override "${split.variant}" for a form ${split.base} does not have` })
      continue
    }
```

placed right after the `if (split.variant === null) {…}` block, and add the helper above `lintManifest`:

```ts
/** Whether the base's roles allow the override's form (spec D4). */
function overrideAllowed(root: string, base: string, form: string): boolean {
  let parsed: ParsedSvg
  try {
    parsed = parseSvg(readFileSync(join(root, SRC_DIR, `${base}.svg`), "utf8"), `${base}.svg`)
  } catch {
    return false
  }
  const { roles } = readRoles(parsed.paths)
  if (form === "half") return roles.focus.length > 0
  if (form === "solid") return roles.body.length > 0
  const k = Number(form.replace("progress-", ""))
  return k >= 1 && k <= roles.marks.length
}
```

- [ ] **Step 6: Run the lint tests, then the whole package lint**

Run: `npx vitest run scripts/lib/lint.test.ts`
Expected: PASS.

Run: `npm run lint --workspace=@pbbls/icons`
Expected: exactly 3 new errors, the form-word names `scale-half`, `appearance-half`, `stones-4-half`. Part 3 renames them; until then the rule is a warning so this part stays green. Change `lintManifest`'s last parameter from `errors: LintIssue[]` to `result: LintResult`, add `const { errors } = result` as its first line (every other push stays as it is), push the form-word issue to `result.warnings` with the comment `// error once part 3 renames the three -half names (#1607)`, and update the call in `lint()` to `lintManifest(root, manifest, files, result)`.

Run: `npm run lint --workspace=@pbbls/icons`
Expected: 0 errors.

- [ ] **Step 7: Commit**

```bash
git add scripts/lib/lint.ts scripts/lib/lint.test.ts scripts/lib/manifest.ts
git commit -m "chore(ui): lint icon roles, overrides and form-word names"
```

### Task 2.9: The `glyphs.json` emitter

**Files:**
- Create: `packages/icons/scripts/lib/emit-glyphs.ts`
- Test: `packages/icons/scripts/lib/emit-glyphs.test.ts`
- Modify: `packages/icons/scripts/build.ts` (register the emitter)
- Modify: `packages/icons/package.json` (export `./glyphs.json`)

The emitter reads base rows only (twins are the old model's), their sources with roles, and override files, and writes one file: `packages/icons/glyphs/glyphs.json`, shaped as `{ "version": 1, "states": <states.json's table>, "glyphs": GlyphData[] }`.

- [ ] **Step 1: Write the failing test**

```ts
import { describe, expect, it } from "vitest"
import { buildGlyph } from "./emit-glyphs"
import { formsOf, renderForm } from "./forms"

const row = { name: "ring", meaning: "A ring.", family: "ring", tags: { wrapper: ["circle"] }, variants: [], wave: "w", status: "candidate", migrated: [], replaces: { ios: [], android: [], web: [] } }
const source = (paths: string) =>
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">\n${paths}\n</svg>\n`

describe("buildGlyph", () => {
  it("stores normalised paths with roles, the mark count and the carved solid", () => {
    const g = buildGlyph(row, source('  <path d="M4 4 L20 4 L20 20 L4 20 Z" data-role="body"/>\n  <path d="M8 12 L16 12" data-role="mark" data-mark="1"/>\n  <path d="M2 22 A1 1 0 0 1 4 22"/>'), {})
    expect(g.paths[0]).toEqual({ d: "M4 4 L20 4 L20 20 L4 20 Z", role: "body" })
    expect(g.paths[1]).toEqual({ d: "M8 12 L16 12", role: "mark", mark: 1 })
    expect(g.paths[2]?.d).toMatch(/^M2 22 C/)
    expect(g.marks).toBe(1)
    expect(g.solid?.keep).toEqual([1])
    expect(formsOf(g)).toEqual(["regular", "solid", "progress-1"])
    expect(g.family).toBe("ring")
    expect(g.tags).toEqual({ wrapper: ["circle"] })
  })

  it("stores overrides by form, as rendered paths", () => {
    const g = buildGlyph(row, source('  <path d="M4 4 L20 4 L20 20 L4 20 Z" data-role="body"/>'), {
      solid: source('  <path d="M4 4 L20 4 L20 20 L4 20 Z" fill="currentColor" stroke="none"/>'),
    })
    expect(renderForm(g, { kind: "solid" })).toEqual([{ d: "M4 4 L20 4 L20 20 L4 20 Z", fill: true, evenOdd: false, stroke: false }])
  })
})
```

- [ ] **Step 2: Run to see it fail**

Run: `npx vitest run scripts/lib/emit-glyphs.test.ts`
Expected: FAIL (module not found).

- [ ] **Step 3: Implement**

```ts
import { readdirSync, readFileSync } from "node:fs"
import { join } from "node:path"
import { carveSolid } from "./carve"
import { GENERATED_NOTICE, type Emitter } from "./emit"
import type { GlyphData, GlyphPath, RenderedPath } from "./forms"
import { SRC_DIR, splitSourceName } from "./lint"
import { isOverrideForm, loadManifest, type IconRow } from "./manifest"
import { normalizePath } from "./normalize"
import { parseSvg } from "./parse"
import { readRoles } from "./roles"
import { loadStates } from "./states"

export const GLYPHS_FILE = "packages/icons/glyphs/glyphs.json"

/** One manifest row and its sources as `glyphs.json` stores them (spec §2). */
export function buildGlyph(row: IconRow, source: string, overrides: Record<string, string>): GlyphData {
  const { paths: parsed } = parseSvg(source, `${row.name}.svg`)
  const { roles } = readRoles(parsed)
  const paths: GlyphPath[] = parsed.map((p, i) => {
    const d = normalizePath(p.d)
    if (roles.body.includes(i)) return { d, role: "body" }
    if (roles.focus.includes(i)) return { d, role: "focus" }
    const mark = roles.marks.findIndex((group) => group.includes(i))
    return mark >= 0 ? { d, role: "mark", mark: mark + 1 } : { d }
  })
  const body = paths.filter((p) => p.role === "body").map((p) => p.d)
  const others = paths.filter((p) => p.role !== "body").map((p) => p.d)
  const carved = body.length > 0 ? carveSolid(body, others) : null
  const rendered: Record<string, RenderedPath[]> = {}
  for (const [form, text] of Object.entries(overrides)) {
    rendered[form] = parseSvg(text, `${row.name}.${form}.svg`).paths.map((p) => ({
      d: normalizePath(p.d),
      fill: p.filled,
      evenOdd: p.evenOdd,
      stroke: !p.unstroked,
    }))
  }
  return {
    name: row.name,
    meaning: row.meaning,
    family: row.family,
    tags: row.tags,
    paths,
    marks: roles.marks.length,
    solid: carved ? { d: carved.d, keep: carved.keep } : null,
    overrides: rendered,
  }
}

/** Every non-exploring row with its source and overrides, in manifest order. */
export function loadGlyphs(packageRoot: string): GlyphData[] {
  const dir = join(packageRoot, SRC_DIR)
  const files = readdirSync(dir)
  return loadManifest(packageRoot)
    .icons.filter((row) => row.status !== "exploring")
    .map((row) => {
      const overrides: Record<string, string> = {}
      for (const f of files) {
        const split = splitSourceName(f)
        if (split?.base === row.name && split.variant !== null && isOverrideForm(split.variant)) overrides[split.variant] = readFileSync(join(dir, f), "utf8")
      }
      return buildGlyph(row, readFileSync(join(dir, `${row.name}.svg`), "utf8"), overrides)
    })
}

/** Builds the emitter for a package root: it reads the sources itself, not the old `EmittedIcon` list. */
export function glyphsEmitter(packageRoot: string): Emitter {
  return {
    name: "glyphs",
    owned: { dirs: [], files: [GLYPHS_FILE], patterns: [] },
    emit() {
      const data = { notice: GENERATED_NOTICE, version: 1, states: loadStates(packageRoot), glyphs: loadGlyphs(packageRoot) }
      return new Map([[GLYPHS_FILE, JSON.stringify(data) + "\n"]])
    },
  }
}
```

- [ ] **Step 4: Run the test**

Run: `npx vitest run scripts/lib/emit-glyphs.test.ts`
Expected: PASS.

- [ ] **Step 5: Register the emitter and export the file**

In `scripts/build.ts`, add the import and the emitter (after `PACKAGE_ROOT` is defined):

```ts
import { glyphsEmitter } from "./lib/emit-glyphs"
```

```ts
/** Every surface the generator emits, in the order they are written. */
const EMITTERS: readonly Emitter[] = [reactEmitter, iosEmitter, androidEmitter, glyphsEmitter(PACKAGE_ROOT)]
```

In `package.json` `exports`, add `"./glyphs.json": "./glyphs/glyphs.json"`.

- [ ] **Step 6: Generate and check**

Run:

```bash
npm run generate --workspace=@pbbls/icons
npm run check --workspace=@pbbls/icons
npm run build --workspace=@pbbls/icons
npm run test --workspace=@pbbls/icons
ls -la glyphs/glyphs.json
```

Expected: generate writes one more file; check reports every file fresh; build and tests pass. Record the size of `glyphs.json` in the PR body (spec risk: measured here). No glyph has roles yet, so every entry has `marks: 0` and `solid: null`.

- [ ] **Step 7: Commit**

```bash
git add scripts/lib/emit-glyphs.ts scripts/lib/emit-glyphs.test.ts scripts/build.ts package.json glyphs/glyphs.json
git commit -m "chore(ui): emit glyphs.json, the forms data every surface will read"
```

### Task 2.10: Package docs and PR

**Files:**
- Modify: `packages/icons/CLAUDE.md`
- Modify: `packages/icons/DNA.md`

- [ ] **Step 1: Document roles in `DNA.md`**

Append a section:

```markdown
## Roles and forms (2026-11, #1607)

A path may carry `data-role="body"` (the main closed shape), `data-role="mark" data-mark="1…n"` (the countable parts, in fill order) or `data-role="focus"` (the part that carries the meaning). The generator works out the forms: regular, half (focus filled), solid (body filled, inner lines carved out at the stroke width) and progress k of n (marks 1…k filled). A source is never filled; `src/<name>.<form>.svg` (`half`, `solid`, `progress-<k>`) overrides a worked-out form that looks wrong. A name never ends in a form word (regular, half, solid, of).
```

- [ ] **Step 2: Document the commands and file in `CLAUDE.md`**

In the "Never hand-edit a generated file" list, add `packages/icons/glyphs/glyphs.json` (the forms data: every glyph's paths with roles, its carved solid and overrides, and the state tokens). Under "Adding or redrawing an icon", step 2, add: "Tag its paths with `data-role` (see DNA.md, Roles and forms)."

- [ ] **Step 3: Commit and open the PR (confirm labels and milestone with the maintainer)**

```bash
git add CLAUDE.md DNA.md
git commit -m "docs(ui): document icon roles and the glyphs data"
git push -u origin chore/1607-icon-forms-package
```

Proposed: labels `chore`, `ui`, `no-lab-note`; milestone `M66 · Brand icons`. Body starts with `Part of #1607`, lists the new modules, the size of `glyphs.json`, and `Data safety: no change`.

---

## Part 3 — roles and renames, reviewed (branch `chore/1607-icon-roles` from updated `main` after part 2 merges)

### Task 3.1: Guess roles from the old twins

**Files:**
- Create: `packages/icons/waves/2026-11-forms/BRIEF.md`
- Create: `packages/icons/scripts/explore/roles.py`
- Create (output): `packages/icons/waves/2026-11-forms/review.json`
- Modify (output): `packages/icons/src/*.svg` (base files gain `data-role` attributes)

- [ ] **Step 1: Branch and create the wave**

```bash
git switch main && git pull --ff-only && git switch -c chore/1607-icon-roles
mkdir -p packages/icons/waves/2026-11-forms
```

Write `packages/icons/waves/2026-11-forms/BRIEF.md`:

```markdown
# Wave 2026-11-forms

No new drawings: this wave gives every existing glyph its roles (#1607). `scripts/explore/roles.py` guesses them from the old `.active` and `.lit-N` twins and writes them into `src/`; `review.json` lists every guess and every flag; the maintainer reviews the forms on the design page and records verdicts there; `verdict.md` records the outcome.
```

- [ ] **Step 2: Write the tool**

`packages/icons/scripts/explore/roles.py`:

```python
#!/usr/bin/env python3
"""Guess each glyph's roles from its old twins: python3 -I roles.py <package root>

For every base `src/<name>.svg` with twins:
- a path filled in `<name>.active.svg` whose d equals a base path: `body` if its
  area is at least FOCUS_MAX square units, else `focus`;
- the path newly filled in `<name>.lit-N.svg` (compared with lit-(N-1)) whose d
  equals a base path: `mark` N;
- a filled twin path with no equal base path: flagged `redrawn` (keep as an
  override or drop);
- an `.active` whose filled area is under DUPLICATE_MAX: flagged `near-duplicate`.
A path guessed both as a mark and as body/focus keeps the mark and is flagged
`conflict`. Writes data-role/data-mark into the base files and
waves/2026-11-forms/review.json. Run `npm run format --workspace=@pbbls/icons` after.
"""
import json
import re
import sys
from pathlib import Path

FOCUS_MAX = 24.0
DUPLICATE_MAX = 3.0
PATH = re.compile(r"<path\b([^>]*?)/>")
ATTR = re.compile(r'([\w:-]+)="([^"]*)"')
TOKEN = re.compile(r"([MLCQAZ])|(-?(?:\d+\.?\d*|\.\d+))")


def paths(text):
    return [dict(ATTR.findall(m.group(1))) for m in PATH.finditer(text)]


def norm(d):
    return " ".join(d.split())


def area(d):
    """Shoelace over the path's points, curves sampled at 8 steps: a rough filled area."""
    pts, cur, cmd, nums = [], (0.0, 0.0), None, []

    def flush():
        nonlocal cur
        if cmd in ("M", "L"):
            for i in range(0, len(nums) - 1, 2):
                cur = (nums[i], nums[i + 1])
                pts.append(cur)
        elif cmd == "C":
            for i in range(0, len(nums) - 5, 6):
                p0, p1, p2, p3 = cur, (nums[i], nums[i + 1]), (nums[i + 2], nums[i + 3]), (nums[i + 4], nums[i + 5])
                for k in range(1, 9):
                    t = k / 8
                    u = 1 - t
                    pts.append(tuple(u**3 * a + 3 * u * u * t * b + 3 * u * t * t * c + t**3 * e for a, b, c, e in zip(p0, p1, p2, p3)))
                cur = p3
        elif cmd == "Q":
            for i in range(0, len(nums) - 3, 4):
                cur = (nums[i + 2], nums[i + 3])
                pts.append(cur)
        elif cmd == "A":
            for i in range(0, len(nums) - 6, 7):
                cur = (nums[i + 5], nums[i + 6])
                pts.append(cur)

    for m in TOKEN.finditer(d):
        if m.group(1):
            flush()
            cmd, nums = m.group(1), []
        else:
            nums.append(float(m.group(2)))
    flush()
    s = sum(a[0] * b[1] - b[0] * a[1] for a, b in zip(pts, pts[1:] + pts[:1]))
    return abs(s) / 2


def filled(ps):
    return [p for p in ps if p.get("fill") == "currentColor"]


def main():
    root = Path(sys.argv[1]).resolve()
    src = root / "src"
    manifest = json.loads((root / "icons.json").read_text(encoding="utf-8"))
    review = []
    for row in manifest["icons"]:
        name = row["name"]
        base_file = src / f"{name}.svg"
        text = base_file.read_text(encoding="utf-8")
        base = paths(text)
        index = {norm(p["d"]): i for i, p in enumerate(base)}
        roles, flags = {}, []
        if "active" in row["variants"]:
            twin = filled(paths((src / f"{name}.active.svg").read_text(encoding="utf-8")))
            total = sum(area(p["d"]) for p in twin)
            if total < DUPLICATE_MAX:
                flags.append({"kind": "near-duplicate", "area": round(total, 1)})
            for p in twin:
                i = index.get(norm(p["d"]))
                if i is None:
                    flags.append({"kind": "redrawn", "from": "active", "area": round(area(p["d"]), 1)})
                else:
                    roles[i] = ("body" if area(p["d"]) >= FOCUS_MAX else "focus", None)
        lit = sorted((v for v in row["variants"] if v.startswith("lit-")), key=lambda v: int(v[4:]))
        before = set()
        for v in lit:
            n = int(v[4:])
            now = {norm(p["d"]) for p in filled(paths((src / f"{name}.{v}.svg").read_text(encoding="utf-8")))}
            for d in now - before:
                i = index.get(d)
                if i is None:
                    flags.append({"kind": "redrawn", "from": v})
                    continue
                if i in roles and roles[i][0] != "mark":
                    flags.append({"kind": "conflict", "path": i + 1, "was": roles[i][0]})
                roles[i] = ("mark", n)
            before = now
        if roles:
            def tag(m):
                attrs = m.group(1)
                d = dict(ATTR.findall(attrs)).get("d", "")
                i = index.get(norm(d))
                if i is None or i not in roles:
                    return m.group(0)
                role, mark = roles[i]
                extra = f' data-role="{role}"' + (f' data-mark="{mark}"' if mark else "")
                return f"<path{attrs}{extra}/>"
            base_file.write_text(PATH.sub(tag, text), encoding="utf-8")
        review.append({
            "name": name,
            "wave": row["wave"],
            "roles": {str(i + 1): (r if m is None else f"{r} {m}") for i, (r, m) in sorted(roles.items())},
            "flags": flags,
        })
    out = root / "waves" / "2026-11-forms" / "review.json"
    out.write_text(json.dumps(review, indent=1) + "\n", encoding="utf-8")
    guessed = sum(1 for r in review if r["roles"])
    flagged = sum(1 for r in review if r["flags"])
    print(f"{len(review)} glyphs, {guessed} with roles, {flagged} flagged")


main()
```

- [ ] **Step 3: Run it, format and lint**

```bash
cd packages/icons
python3 -I scripts/explore/roles.py .
npm run format --workspace=@pbbls/icons
npm run lint --workspace=@pbbls/icons
```

Expected: a summary line (about 950 glyphs, roughly 890 with roles); format rewrites the touched files into canonical order; lint passes with no `roles` errors. If a glyph fails "the body has no closed subpath" or the carve threshold, change that path's `data-role="body"` to `data-role="focus"` in its source and add `{"kind": "body-to-focus"}` to its `review.json` flags by hand; rerun lint.

- [ ] **Step 4: Generate and commit**

```bash
npm run generate --workspace=@pbbls/icons && npm run check --workspace=@pbbls/icons
git add scripts/explore/roles.py waves/2026-11-forms src glyphs/glyphs.json
git commit -m "chore(ui): guess every glyph's roles from its old twins"
```

### Task 3.2: Renames

**Files:**
- Create: `packages/icons/waves/2026-11-forms/renames.json`
- Create: `packages/icons/scripts/explore/rename.py`
- Modify: `packages/icons/src/*.svg` (renamed files), `packages/icons/icons.json`, call sites under `apps/`

- [ ] **Step 1: Write the proposed renames**

`packages/icons/waves/2026-11-forms/renames.json` (proposals: the maintainer confirms or edits them in Task 3.4's review before this task's commit is pushed):

```json
{
  "pebble-outlined-glyph": "pebble-glyph",
  "pebble-outlined-glyph-2": "pebble-glyph-open",
  "pebble-dotted-glyph": "pebble-glyph-dotted",
  "pebble-dotted-glyph-2": "pebble-glyph-dotted-open",
  "scale-half": "scale-balanced",
  "appearance-half": "appearance-split",
  "stones-4-half": "stones-4-halved"
}
```

- [ ] **Step 2: Write the rename tool**

`packages/icons/scripts/explore/rename.py`:

```python
#!/usr/bin/env python3
"""Rename glyphs everywhere: python3 -I rename.py <repo root> <renames.json>

Moves src/<old>.svg and every src/<old>.<variant>.svg, renames the icons.json
row, and rewrites hand-written call sites under apps/ in each surface's
spelling (React PascalCase, Swift camelCase, Kotlin CONSTANT, the kebab name in
strings), twins included. Generated files are left alone: run generate after.
"""
import json
import re
import sys
from pathlib import Path

GENERATED = (
    "packages/icons/react/",
    "apps/ios/Pebbles/Resources/Icons.xcassets/",
    "apps/ios/Pebbles/Theme/PbblsIcon.swift",
    "apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/PbblsIcons.kt",
)
SUFFIXES = ("", ".active", ".lit-1", ".lit-2", ".lit-3")


def words(name):
    return [w for w in re.split(r"[.-]", name) if w]


def spellings(name):
    w = words(name)
    return {
        "pascal": "".join(x[:1].upper() + x[1:] for x in w),
        "camel": w[0] + "".join(x[:1].upper() + x[1:] for x in w[1:]),
        "constant": "_".join(x.upper() for x in w),
        "kebab": name,
    }


def main():
    repo = Path(sys.argv[1]).resolve()
    renames = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
    src = repo / "packages/icons/src"
    manifest_file = repo / "packages/icons/icons.json"
    manifest = manifest_file.read_text(encoding="utf-8")
    pairs = []
    for old, new in renames.items():
        for suffix in SUFFIXES:
            f = src / f"{old}{suffix}.svg"
            if f.exists():
                f.rename(src / f"{new}{suffix}.svg")
            pairs.append((old + suffix, new + suffix))
        manifest = manifest.replace(f'"name": "{old}"', f'"name": "{new}"')
    manifest_file.write_text(manifest, encoding="utf-8")
    subs = []
    for old, new in sorted(pairs, key=lambda p: -len(p[0])):
        o, n = spellings(old), spellings(new)
        for key in ("pascal", "camel", "constant"):
            subs.append((re.compile(rf"\b{re.escape(o[key])}\b"), n[key]))
        subs.append((re.compile(rf'(["\']){re.escape(old)}(["\'])'), rf"\g<1>{new}\g<2>"))
    changed = 0
    for path in (repo / "apps").rglob("*"):
        rel = path.relative_to(repo).as_posix()
        if not path.is_file() or path.suffix not in (".swift", ".kt", ".ts", ".tsx") or rel.startswith(GENERATED) or "/node_modules/" in rel or "/build/" in rel:
            continue
        text = path.read_text(encoding="utf-8")
        out = text
        for pattern, repl in subs:
            out = pattern.sub(repl, out)
        if out != text:
            path.write_text(out, encoding="utf-8")
            changed += 1
    print(f"{len(renames)} glyphs renamed, {changed} app files rewritten")


main()
```

- [ ] **Step 3: Run it, regenerate, and build every surface**

```bash
python3 -I packages/icons/scripts/explore/rename.py . packages/icons/waves/2026-11-forms/renames.json
npm run generate --workspace=@pbbls/icons
npm run lint --workspace=@pbbls/icons
git diff --stat -- apps
npm run lint --workspace=apps/web && npx tsc --noEmit -p apps/web
(cd apps/android && export ANDROID_HOME="$HOME/Library/Android/sdk" && ./gradlew :app:compileDebugKotlin -q)
```

Expected: the summary line; `git diff --stat -- apps` lists only call sites of the renamed glyphs; lint, web typecheck and Android compile pass. For iOS, build on a dedicated simulator (memory: one simulator and a private derived-data folder per worktree): `xcodebuild build -scheme Pebbles -destination 'id=<sim>' -derivedDataPath <scratchpad>/dd-roles`.

- [ ] **Step 4: Make the form-word rule an error again**

In `lint.ts`, move the form-word push back from `warnings` to `errors` and delete the `// error once part 3 …` comment. Run `npm run lint --workspace=@pbbls/icons`: expected 0 errors.

- [ ] **Step 5: Commit**

```bash
git add -A packages/icons apps
git commit -m "chore(ui): rename glyphs to the forms naming"
```

### Task 3.3: Form switcher on the design page

**Files:**
- Create: `apps/web/lib/design/glyph-forms.ts`
- Test: `apps/web/lib/design/glyph-forms.test.ts`
- Create: `apps/web/components/sandbox/design/GlyphFormView.tsx`
- Modify: `apps/web/components/sandbox/design/DesignContext.tsx`, `DesignDirectory.tsx`, `DesignTools.tsx`, `pages/IconsPage.tsx`

The sandbox renders glyphs from `@pbbls/icons/glyphs.json` with the same rules as the generator's `renderForm`; part 4 replaces this with the real web components.

- [ ] **Step 1: Write the failing test**

`apps/web/lib/design/glyph-forms.test.ts`:

```ts
import { describe, expect, it } from "vitest"
import { GLYPHS, availableForms, formPaths, needsReview } from "./glyph-forms"

describe("glyph forms", () => {
  it("reads every glyph from glyphs.json", () => {
    expect(GLYPHS.length).toBeGreaterThan(900)
    expect(new Set(GLYPHS.map((g) => g.name)).size).toBe(GLYPHS.length)
  })

  it("lists forms and falls back to regular for a form a glyph lacks", () => {
    const plain = GLYPHS.find((g) => g.marks === 0 && g.solid === null && !g.paths.some((p) => p.role === "focus"))
    if (!plain) throw new Error("no plain glyph")
    expect(availableForms(plain)).toEqual(["regular"])
    expect(formPaths(plain, "solid").every((p) => !p.fill)).toBe(true)
  })

  it("flags glyphs the review lists", () => {
    expect(typeof needsReview("home")).toBe("boolean")
  })
})
```

- [ ] **Step 2: Run to see it fail**

Run: `cd apps/web && npx vitest run lib/design/glyph-forms.test.ts`
Expected: FAIL (module not found).

- [ ] **Step 3: Implement `glyph-forms.ts`**

```ts
import data from "@pbbls/icons/glyphs.json"
import review from "@pbbls/icons/review.json"

/**
 * The forms data the design page reviews (#1607, part 3): every glyph from the
 * generator's `glyphs.json`, drawn with the same rules as `renderForm` in
 * packages/icons/scripts/lib/forms.ts. Part 4 replaces this with the web
 * components.
 */

export type GlyphPath = { d: string; role?: "body" | "mark" | "focus"; mark?: number }
export type RenderedPath = { d: string; fill: boolean; evenOdd: boolean; stroke: boolean }
export type Glyph = {
  name: string
  meaning: string
  family: string | null
  tags: Record<string, string[]>
  paths: GlyphPath[]
  marks: number
  solid: { d: string; keep: number[] } | null
  overrides: Record<string, RenderedPath[]>
}

export const GLYPHS: readonly Glyph[] = data.glyphs as Glyph[]

/** The page's form choice: a form name, `progress` meaning k from the slider. */
export type FormChoice = "regular" | "half" | "solid" | "progress"

export function availableForms(glyph: Glyph): string[] {
  const forms = ["regular"]
  if (glyph.paths.some((p) => p.role === "focus")) forms.push("half")
  if (glyph.solid) forms.push("solid")
  for (let k = 1; k <= glyph.marks; k += 1) forms.push(`progress-${k}`)
  return forms
}

const stroked = (d: string): RenderedPath => ({ d, fill: false, evenOdd: false, stroke: true })
const filledPath = (d: string): RenderedPath => ({ d, fill: true, evenOdd: false, stroke: true })

/** The paths a form draws; a form the glyph lacks draws regular (the page dims it). */
export function formPaths(glyph: Glyph, choice: FormChoice, k = 0): RenderedPath[] {
  const key = choice === "progress" ? `progress-${Math.min(k, glyph.marks)}` : choice
  const override = glyph.overrides[key]
  if (override) return override
  if (choice === "half" && glyph.paths.some((p) => p.role === "focus")) return glyph.paths.map((p) => (p.role === "focus" ? filledPath(p.d) : stroked(p.d)))
  if (choice === "progress" && glyph.marks > 0) return glyph.paths.map((p) => (p.role === "mark" && (p.mark ?? 0) <= k ? filledPath(p.d) : stroked(p.d)))
  if (choice === "solid" && glyph.solid) {
    const others = glyph.paths.filter((p) => p.role !== "body")
    return [{ d: glyph.solid.d, fill: true, evenOdd: true, stroke: false }, ...glyph.solid.keep.flatMap((i) => (others[i] ? [stroked(others[i].d)] : []))]
  }
  return glyph.paths.map((p) => stroked(p.d))
}

export function hasForm(glyph: Glyph, choice: FormChoice): boolean {
  if (choice === "regular") return true
  if (choice === "progress") return glyph.marks > 0
  return availableForms(glyph).includes(choice)
}

const FLAGGED: ReadonlySet<string> = new Set((review as { name: string; flags: unknown[] }[]).filter((r) => r.flags.length > 0).map((r) => r.name))

export function needsReview(name: string): boolean {
  return FLAGGED.has(name)
}
```

Add `"./review.json": "./waves/2026-11-forms/review.json"` to `packages/icons/package.json` `exports` (sandbox-only: the page reads the wave's review list; the generator still never reads `waves/`).

- [ ] **Step 4: Run the test**

Run: `cd apps/web && npx vitest run lib/design/glyph-forms.test.ts`
Expected: PASS.

- [ ] **Step 5: The animated glyph view**

`apps/web/components/sandbox/design/GlyphFormView.tsx`:

```tsx
"use client"

import { formPaths, type FormChoice, type Glyph, type GlyphPath } from "@/lib/design/glyph-forms"
import { cn } from "@/lib/utils"

const FADE = "transition-[fill-opacity,opacity] duration-300 ease-out motion-reduce:transition-none"

/**
 * One glyph in one form for the design page's review. The strokes are always
 * drawn and each path's fill fades in or out, so half and progress animate
 * mark by mark; the carved solid sits in its own layer and crossfades over the
 * strokes. An override is drawn as it is, without a transition.
 */
export function GlyphFormView({ glyph, form, k, className }: { glyph: Glyph; form: FormChoice; k: number; className?: string }) {
  const key = form === "progress" ? `progress-${Math.min(k, glyph.marks)}` : form
  const override = form === "regular" ? undefined : glyph.overrides[key]
  const showSolid = form === "solid" && glyph.solid !== null && !override
  const lit = (p: GlyphPath): boolean =>
    (form === "half" && p.role === "focus") || (form === "progress" && p.role === "mark" && (p.mark ?? 0) <= k)
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.75} strokeLinecap="round" strokeLinejoin="round" aria-hidden className={className}>
      {override ? (
        override.map((p, i) => (
          <path key={i} d={p.d} fill={p.fill ? "currentColor" : "none"} fillRule={p.evenOdd ? "evenodd" : undefined} stroke={p.stroke ? "currentColor" : "none"} />
        ))
      ) : (
        <>
          <g className={cn(FADE, showSolid ? "opacity-0" : "opacity-100")}>
            {glyph.paths.map((p, i) => (
              <path key={i} d={p.d} fill="currentColor" fillOpacity={lit(p) ? 1 : 0} className={FADE} />
            ))}
          </g>
          {glyph.solid && (
            <g className={cn(FADE, showSolid ? "opacity-100" : "opacity-0")}>
              {formPaths(glyph, "solid").map((p, i) => (
                <path key={i} d={p.d} fill={p.fill ? "currentColor" : "none"} fillRule={p.evenOdd ? "evenodd" : undefined} stroke={p.stroke ? "currentColor" : "none"} />
              ))}
            </g>
          )}
        </>
      )}
    </svg>
  )
}
```

- [ ] **Step 6: Wire the switcher**

1. `DesignContext.tsx`: add to `DesignContextValue`

```ts
  /** The Icons page's form and progress (#1607 review). */
  glyphForm: FormChoice
  glyphProgress: number
  reviewOnly: boolean
```

with `import type { FormChoice } from "@/lib/design/glyph-forms"`.

2. `DesignDirectory.tsx`: state `const [glyphForm, setGlyphForm] = useState<FormChoice>("regular")`, `const [glyphProgress, setGlyphProgress] = useState(1)`, `const [reviewOnly, setReviewOnly] = useState(false)`; add the three values to the context `useMemo` (value and deps) and the six fields to `tools`.

3. `DesignTools.tsx`: add to `DesignToolsState`

```ts
  glyphForm: FormChoice
  onGlyphForm: (form: FormChoice) => void
  glyphProgress: number
  onGlyphProgress: (k: number) => void
  reviewOnly: boolean
  onReviewOnly: (on: boolean) => void
```

and, inside the `icons &&` block after the Size select:

```tsx
      {icons && (
        <DesignSelect
          label="Form"
          options={[
            { value: "regular", label: "Regular" },
            { value: "half", label: "Half" },
            { value: "solid", label: "Solid" },
            { value: "progress", label: "Progress" },
          ]}
          value={s.glyphForm}
          onChange={s.onGlyphForm}
        />
      )}
      {icons && s.glyphForm === "progress" && (
        <DesignSelect
          label="Marks lit"
          options={[0, 1, 2, 3, 4, 5].map((k) => ({ value: k, label: String(k) }))}
          value={s.glyphProgress}
          onChange={s.onGlyphProgress}
        />
      )}
      {icons && (
        <DesignSelect
          label="Show"
          options={[
            { value: "all", label: "All glyphs" },
            { value: "review", label: "Needs review" },
          ]}
          value={s.reviewOnly ? "review" : "all"}
          onChange={(v) => s.onReviewOnly(v === "review")}
        />
      )}
```

4. `pages/IconsPage.tsx`: the tiles come from the forms data (one per glyph, no twins). Add to the imports:

```ts
import { GLYPHS, hasForm, needsReview, type Glyph } from "@/lib/design/glyph-forms"
import { GlyphFormView } from "../GlyphFormView"
```

Add above `GlyphTile`:

```ts
const MIGRATED: ReadonlyMap<string, readonly string[]> = new Map(ICON_ROWS.map((row) => [row.name, row.migrated]))

/** Search over the name, the meaning and every tag value. */
function glyphMatchesQuery(glyph: Glyph, query: string): boolean {
  const q = query.trim().toLowerCase()
  if (!q) return true
  return glyph.name.includes(q) || glyph.meaning.toLowerCase().includes(q) || Object.values(glyph.tags).some((list) => list.some((v) => v.includes(q)))
}
```

Replace `migrationSentence` and `GlyphTile` with:

```tsx
function migrationSentence(migrated: readonly string[]): string {
  const on = SURFACES.filter((s) => migrated.includes(s.surface)).map((s) => s.label)
  return on.length === 0 ? "Not migrated on any surface." : `Migrated on ${on.join(", ")}.`
}

function GlyphTile({ glyph, size, form, k }: { glyph: Glyph; size: PbblsIconSize; form: FormChoice; k: number }) {
  const migrated = MIGRATED.get(glyph.name) ?? []
  return (
    <li
      title={glyph.meaning}
      className={cn(
        "group relative flex aspect-square flex-col items-center gap-2 rounded-2xl bg-card px-3 pt-6 pb-3 text-card-foreground transition-colors hover:bg-muted motion-reduce:transition-none",
        !hasForm(glyph, form) && "opacity-40",
      )}
    >
      {needsReview(glyph.name) && <span aria-label="Needs review" className="absolute top-2.5 left-2.5 size-2 rounded-full bg-primary" />}
      <span className="flex min-h-12 flex-1 items-center justify-center">
        <GlyphFormView glyph={glyph} form={form} k={k} className={PBBLS_ICON_SIZE[size]} />
      </span>
      <code className="line-clamp-2 h-8 max-w-full text-center text-xs leading-4 break-words text-muted-foreground">
        <GlyphName name={glyph.name} />
      </code>
      <span className="sr-only">{migrationSentence(migrated)}</span>
      <span
        aria-hidden
        className="pointer-events-none absolute top-2 right-2 flex gap-1 opacity-0 transition-opacity duration-150 group-hover:opacity-100 motion-reduce:transition-none"
      >
        {SURFACES.map(({ surface, Icon: Mark }) => (
          <Mark key={surface} className={cn(PBBLS_ICON_SIZE.inline, migrated.includes(surface) ? "text-primary" : "text-muted-foreground/35")} />
        ))}
      </span>
    </li>
  )
}
```

with `import type { FormChoice } from "@/lib/design/glyph-forms"` merged into the glyph-forms import. In `IconsPage`, replace the `useDesign()` line and the `glyphs` memo with:

```ts
  const { iconQuery, iconSize, glyphForm, glyphProgress, reviewOnly } = useDesign()
  const glyphs = useMemo(
    () => GLYPHS.filter((g) => glyphMatchesQuery(g, iconQuery) && (!reviewOnly || needsReview(g.name))),
    [iconQuery, reviewOnly],
  )
```

and the tile render with `<GlyphTile key={glyph.name} glyph={glyph} size={iconSize} form={glyphForm} k={glyphProgress} />`. Remove the now-unused `ICON_GLYPHS`, `glyphMatches` and `IconGlyph` imports. In `DesignTools.tsx`, count with the same data: replace `ICON_GLYPHS` there with `GLYPHS` and the matcher with a search over `name` and `meaning` (the count line reads "N glyphs").

- [ ] **Step 7: Run the web checks and look at it**

```bash
cd apps/web
npx eslint components/sandbox/design lib/design && npx tsc --noEmit -p . && npx vitest run
npm run dev
```

Open `http://localhost:3000/sandbox/design/icons`, switch Form between Regular, Half, Solid and Progress, move "Marks lit", and check the animation and the "Needs review" filter. Expected: lint, typecheck and the suite pass.

- [ ] **Step 8: Commit**

```bash
git add -A apps/web/lib/design apps/web/components/sandbox/design packages/icons/package.json
git commit -m "feat(web): review glyph forms on the design page"
```

### Task 3.4: The maintainer's review

The review happens on the design page itself (spec §3): in "Needs review" mode each flagged tile gets a verdict button, verdicts are kept in the browser, and a "Copy verdicts" button hands them over as JSON, the way the everyday wave's results were pasted.

**Files:**
- Create: `apps/web/lib/design/useReviewVerdicts.ts`
- Test: `apps/web/lib/design/review-verdicts.test.ts`
- Modify: `apps/web/components/sandbox/design/pages/IconsPage.tsx`
- Create (results): `packages/icons/waves/2026-11-forms/results/review-<date>.json`, `packages/icons/waves/2026-11-forms/verdict.md`

- [ ] **Step 1: Write the failing test for the verdict cycle**

```ts
import { describe, expect, it } from "vitest"
import { nextVerdict } from "./useReviewVerdicts"

describe("nextVerdict", () => {
  it("cycles unset → ok → fix → drop-twin → unset", () => {
    expect(nextVerdict(undefined)).toBe("ok")
    expect(nextVerdict("ok")).toBe("fix")
    expect(nextVerdict("fix")).toBe("drop-twin")
    expect(nextVerdict("drop-twin")).toBeUndefined()
  })
})
```

Run: `cd apps/web && npx vitest run lib/design/review-verdicts.test.ts`
Expected: FAIL (module not found).

- [ ] **Step 2: Implement the hook**

`apps/web/lib/design/useReviewVerdicts.ts`:

```ts
"use client"

import { useCallback, useEffect, useState } from "react"

/** The maintainer's verdict on a flagged glyph (#1607 review). */
export type Verdict = "ok" | "fix" | "drop-twin"

const ORDER: readonly Verdict[] = ["ok", "fix", "drop-twin"]
const KEY = "pbbls-glyph-review-v1"

export function nextVerdict(current: Verdict | undefined): Verdict | undefined {
  if (current === undefined) return ORDER[0]
  return ORDER[ORDER.indexOf(current) + 1]
}

function read(): Record<string, Verdict> {
  try {
    const raw = window.localStorage.getItem(KEY)
    const parsed: unknown = raw ? JSON.parse(raw) : {}
    if (typeof parsed !== "object" || parsed === null) return {}
    const out: Record<string, Verdict> = {}
    for (const [name, v] of Object.entries(parsed)) if (v === "ok" || v === "fix" || v === "drop-twin") out[name] = v
    return out
  } catch {
    return {}
  }
}

/** Verdicts kept in this browser only; `copy` puts them on the clipboard as JSON. */
export function useReviewVerdicts() {
  const [verdicts, setVerdicts] = useState<Record<string, Verdict>>({})
  useEffect(() => setVerdicts(read()), [])
  const cycle = useCallback((name: string) => {
    setVerdicts((prev) => {
      const next = { ...prev }
      const v = nextVerdict(prev[name])
      if (v) next[name] = v
      else delete next[name]
      try {
        window.localStorage.setItem(KEY, JSON.stringify(next))
      } catch {
        // Private mode or blocked storage: the verdicts live until reload.
      }
      return next
    })
  }, [])
  const copy = useCallback(async () => {
    await navigator.clipboard.writeText(JSON.stringify({ verdicts, copiedAt: new Date().toISOString() }, null, 1))
  }, [verdicts])
  return { verdicts, cycle, copy }
}
```

Run: `cd apps/web && npx vitest run lib/design/review-verdicts.test.ts`
Expected: PASS.

- [ ] **Step 3: Put the verdicts on the tiles**

In `IconsPage.tsx`: call `const { verdicts, cycle, copy } = useReviewVerdicts()` in `IconsPage`; pass `verdict={verdicts[glyph.name]}` and `onVerdict={() => cycle(glyph.name)}` to `GlyphTile`. In `GlyphTile`, add the props `verdict?: Verdict` and `onVerdict: () => void`, and replace the needs-review dot with:

```tsx
      {needsReview(glyph.name) && (
        <button
          type="button"
          onClick={onVerdict}
          aria-label={`Review ${glyph.name}: ${verdict ?? "no verdict"}`}
          className={cn(
            "absolute top-2 left-2 rounded-full px-2 py-0.5 text-[10px] font-medium outline-none focus-visible:ring-2 focus-visible:ring-ring",
            verdict === "ok" && "bg-primary text-primary-foreground",
            verdict === "fix" && "bg-secondary text-secondary-foreground",
            verdict === "drop-twin" && "bg-muted text-muted-foreground line-through",
            verdict === undefined && "bg-primary/15 text-primary",
          )}
        >
          {verdict ?? "review"}
        </button>
      )}
```

Above the grid (inside the `<section id="icons">`), when `reviewOnly`, add:

```tsx
        {reviewOnly && (
          <div className="mb-4 flex items-center justify-between gap-4 text-sm text-muted-foreground">
            <span>
              {Object.keys(verdicts).length} of {glyphs.length} reviewed · tap a tile's label: ok, fix, drop-twin
            </span>
            <Button size="sm" variant="secondary" onClick={() => void copy()}>
              Copy verdicts
            </Button>
          </div>
        )}
```

with `import { Button } from "@/components/ui/button"` and `import { useReviewVerdicts, type Verdict } from "@/lib/design/useReviewVerdicts"`. Run `npx eslint components/sandbox/design lib/design && npx tsc --noEmit -p . && npx vitest run`: expected pass. Commit:

```bash
git add apps/web/lib/design apps/web/components/sandbox/design
git commit -m "feat(web): record glyph review verdicts on the design page"
```

- [ ] **Step 4: Hand the review to the maintainer**

Push the branch (the Vercel preview serves `/sandbox/design/icons`) or run `npm run dev` locally, and give the maintainer the URL with this brief: switch Show to "Needs review", look at each glyph in every form (Form and Marks lit), tap its label to ok / fix / drop-twin, describe each "fix" in chat, confirm or change the seven renames in `renames.json`, then press "Copy verdicts" and paste the JSON. Wait for it.

- [ ] **Step 5: Apply the verdicts**

Save the pasted JSON as `packages/icons/waves/2026-11-forms/results/review-<date>.json`. Then:
- `fix`: edit the glyph's `data-role` / `data-mark` attributes in `src/<name>.svg` as the maintainer described; when they keep a `redrawn` twin, copy the old twin's filled paths into `src/<name>.<form>.svg` as an override.
- `drop-twin`: nothing to delete now (part 7 removes every twin); list the names in `verdict.md`.
- renames the maintainer changed: write the pairs `{ "<proposed name>": "<final name>" }` to `waves/2026-11-forms/renames-final.json` and run `python3 -I packages/icons/scripts/explore/rename.py . packages/icons/waves/2026-11-forms/renames-final.json`.

```bash
npm run format --workspace=@pbbls/icons && npm run lint --workspace=@pbbls/icons
npm run generate --workspace=@pbbls/icons && npm run check --workspace=@pbbls/icons
npm run test --workspace=@pbbls/icons
```

Expected: 0 errors, fresh outputs, tests pass.

- [ ] **Step 6: Write `verdict.md` and commit**

`packages/icons/waves/2026-11-forms/verdict.md`: the date, how many glyphs got roles, how many were fixed, the overrides kept, the twins to drop in part 7, the final renames.

```bash
git add packages/icons apps
git commit -m "chore(ui): apply the maintainer's review of glyph roles"
```

### Task 3.5: Part 3 PR

- [ ] **Step 1: Run every check at the scope of the change**

```bash
npm run lint --workspace=@pbbls/icons && npm run check --workspace=@pbbls/icons && npm run test --workspace=@pbbls/icons && npm run lint:replaces --workspace=@pbbls/icons
cd apps/web && npx eslint . && npx tsc --noEmit -p . && npx vitest run
```

Expected: all pass. The Android and iOS checks run in CI; the renames touch their call sites, so watch both. The old outputs are regenerated under the new names, so the screenshot references of renamed glyphs may move: if the iOS icon sheet or Android previews differ only by those names' order, add the `rebaseline-ios-screenshots` / `rebaseline-screenshots` labels (skill `android-screenshot-gate`), then approve the held runs.

- [ ] **Step 2: Open the PR (confirm labels and milestone with the maintainer)**

Proposed: `chore`, `ui`, `no-lab-note`, milestone `M66 · Brand icons`; body starts `Part of #1607`, lists the counts from `verdict.md`, the renames, and `Data safety: no change`.

---

## After part 3

Write the plans for parts 4 (web components and call sites), 5 (iOS renderer, data resource, call sites, per-form references), 6 (Android, mirroring 5) and 7 (removal), each against `glyphs.json` as Task 2.9 defines it and the state tokens of Task 2.7. Each surface plan includes the path-parser contract test: one fixture file of normalised path strings with expected points, read by Vitest, Swift Testing and JUnit (spec §4).
