# Icon forms on iOS (#1607, part 5) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The iOS app draws every glyph from generated forms data (regular, solid, parts, progress, dotted, with or without the mark) through `PbblsGlyphView`, picks state forms through the shared state tokens, and no longer names a twin.

**Architecture:** The icons generator emits a compact `pbbls-glyphs.json` resource, a `PbblsGlyph` enum of names and a `PbblsGlyphState` enum into the app. Hand-written Swift parses the normalised path data, ports `renderForm` and `resolveState` from `packages/icons/scripts/lib/forms.ts` and `states.ts`, and draws a form as a template `UIImage` (Core Graphics, cached), so a glyph still works wherever SwiftUI needs an `Image`: tab bars, swipe actions, `Label` icons. Two fixtures make the port provably equal to the generator: a hand-written path-parser contract and a generated forms contract.

**Tech Stack:** TypeScript generator (Vitest, Clipper), SwiftUI + Core Graphics on iOS 17, Swift Testing, `ImageRenderer` references.

**Spec:** `docs/superpowers/specs/2026-11-08-icon-forms-design.md` (D3, D6, D7, D9, §2, §4). Part 4 (web) is not a prerequisite: this part branches from `main` and touches only `packages/icons` and `apps/ios`.

**Branch:** `chore/1607-icon-forms-ios` (the `-ios` slug keeps Vercel from deploying it). Never touch the untracked `apps/web/AGENTS.md`. Commits are conventional and end with a blank line and `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Never hand-edit a generated file: change the emitter, then `npm run generate --workspace=@pbbls/icons`.

**Checks:**
- Package: `npm run build --workspace=@pbbls/icons && npm run test --workspace=@pbbls/icons && npm run check --workspace=@pbbls/icons`.
- iOS (from `apps/ios`): `swiftlint`, and `xcodegen generate && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' -only-testing:PebblesTests/<Suite>`. After `xcodegen`, delete `~/Library/Developer/Xcode/DerivedData/Pebbles-*/Build` before trusting a result (stale objects). Reference suites fail locally on untouched `main` too; CI is their gate, and references are never recorded locally.

## Decisions taken in this plan

- **Drawn as images, not shapes.** `PbblsGlyphView` renders a form to a template `UIImage` at 3× and shows it as `Image`, like today's asset. A SwiftUI `Shape` would disappear in tab bars and swipe actions, which only take an `Image`. So the D9 animation on iOS is a crossfade with a slight scale between two forms (none under Reduce Motion); marks lighting one after another is left for a later part.
- **Carved fills are simplified at build time.** Douglas–Peucker at 0.01 glyph units (0.06 px at the 48 pt hero at 3×) drops the carved data from 3.5 MB to 1.4 MB for every surface. Areas and lint thresholds are computed before simplifying, so nothing else moves.
- **One resource, loaded once, off the main thread at launch.** `GlyphCatalog.shared.prewarm()` in `PebblesApp.init`; any earlier read loads it synchronously under a lock. Task 7 measures it.
- **`PbblsIconSize` keeps its name** in this part: the call sites already pass `.row`, `.tile`. Part 7 renames it with the old enum's removal.
- **The old outputs stay** (`Icons.xcassets`, `PbblsIcon.swift`, `PbblsIconImage`, their tests and the icon-sheet references): part 7 removes them. After this part no app code names `PbblsIcon` or `PbblsIconImage` outside `Theme/`.

## File map

| File | Status | Responsibility |
|---|---|---|
| `packages/icons/scripts/lib/geometry.ts` | modify | `simplifyRing` |
| `packages/icons/scripts/lib/carve.ts` | modify | simplify each carved ring before writing it |
| `packages/icons/scripts/lib/segments.ts` | create | `segmentsOf(d)`: the parser contract's TS side |
| `packages/icons/fixtures/path-contract.json` | create (hand-written) | path strings and the segments every surface must read |
| `packages/icons/scripts/lib/emit-glyphs.ts` | modify | memoised `loadGlyphsOnce`; `formsContract`; the glyphs emitter also writes the forms contract |
| `packages/icons/glyphs/forms-contract.json` | generated | sample glyphs: their form keys, every form rendered, every state resolved |
| `packages/icons/scripts/lib/emit-ios-glyphs.ts` | create | the iOS glyphs emitter |
| `packages/icons/scripts/build.ts` | modify | register it |
| `apps/ios/Pebbles/Resources/pbbls-glyphs.json` | generated | the native data (no meaning, tags or family) |
| `apps/ios/Pebbles/Theme/PbblsGlyph.swift` | generated | `enum PbblsGlyph`, names only |
| `apps/ios/Pebbles/Theme/PbblsGlyphState.swift` | generated | `enum PbblsGlyphState` from `states.json` |
| `apps/ios/Pebbles/Theme/Glyphs/GlyphForm.swift` | create | `GlyphFill`, `GlyphForm`, keys both ways |
| `apps/ios/Pebbles/Theme/Glyphs/GlyphData.swift` | create | the decoded data model and `formKeys` |
| `apps/ios/Pebbles/Theme/Glyphs/GlyphPathParser.swift` | create | `segments(_:)`, `cgPath(_:)` |
| `apps/ios/Pebbles/Theme/Glyphs/GlyphRenderer.swift` | create | `render(_:_:)`, `resolve(...)`, `draw(_:side:)` |
| `apps/ios/Pebbles/Theme/Glyphs/GlyphCatalog.swift` | create | loading, prewarm, image cache |
| `apps/ios/Pebbles/Theme/Glyphs/PbblsGlyph+Forms.swift` | create | `glyph.forms`, `glyph.form(for:k:)` |
| `apps/ios/Pebbles/Theme/Glyphs/PbblsGlyphView.swift` | create | the view |
| `apps/ios/PebblesTests/Theme/Glyphs/*.swift` | create | `RepoFile`, form, parser, renderer, catalog and view tests |
| `apps/ios/PebblesTests/References/GlyphFormSheetReferences.swift` | create | per-form pages of the set |
| call sites under `apps/ios/Pebbles` and `PebblesTests` | modify | `PbblsIconImage` → `PbblsGlyphView`, `PbblsIcon` → `PbblsGlyph`, twins → forms |
| `packages/icons/CLAUDE.md` | modify | the new generated paths and fixtures |

---

### Task 1: Simplify carved rings

**Files:**
- Modify: `packages/icons/scripts/lib/geometry.ts`, `packages/icons/scripts/lib/carve.ts`
- Test: `packages/icons/scripts/lib/geometry.test.ts`

- [ ] **Step 1: Failing test** — append to `geometry.test.ts` (add `simplifyRing` to its import from `./geometry`):

```ts
describe("simplifyRing", () => {
  const distanceToSegment = (p: Pt, a: Pt, b: Pt): number => {
    const dx = b[0] - a[0]
    const dy = b[1] - a[1]
    const len2 = dx * dx + dy * dy
    const t = len2 === 0 ? 0 : Math.max(0, Math.min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / len2))
    return Math.hypot(p[0] - (a[0] + t * dx), p[1] - (a[1] + t * dy))
  }
  const deviation = (ring: Pt[], simple: Pt[]): number =>
    Math.max(...ring.map((p) => Math.min(...simple.map((a, i) => distanceToSegment(p, a, simple[(i + 1) % simple.length] ?? a)))))

  it("keeps a square's corners and drops the points along its edges", () => {
    const ring: Pt[] = [[0, 0], [1, 0], [2, 0], [2, 1], [2, 2], [1, 2], [0, 2], [0, 1]]
    expect(simplifyRing(ring, 0.01)).toEqual([[0, 0], [2, 0], [2, 2], [0, 2]])
  })

  it("thins a dense circle while staying within the tolerance", () => {
    const ring: Pt[] = Array.from({ length: 400 }, (_, i): Pt => [5 * Math.cos((i / 400) * 2 * Math.PI), 5 * Math.sin((i / 400) * 2 * Math.PI)])
    const simple = simplifyRing(ring, 0.01)
    expect(simple.length).toBeLessThan(120)
    expect(deviation(ring, simple)).toBeLessThanOrEqual(0.01 + 1e-9)
  })

  it("leaves a ring of four points or fewer alone", () => {
    const tri: Pt[] = [[0, 0], [1, 0], [0, 1]]
    expect(simplifyRing(tri, 1)).toEqual(tri)
  })
})
```

(`Pt` is exported by `geometry.ts`; import it as a type.)

- [ ] **Step 2: Run** `npx vitest run scripts/lib/geometry.test.ts` from `packages/icons` — FAIL (`simplifyRing` is not exported).

- [ ] **Step 3: Implement** — append to `geometry.ts`:

```ts
function distanceToSegment(p: Pt, a: Pt, b: Pt): number {
  const dx = b[0] - a[0]
  const dy = b[1] - a[1]
  const len2 = dx * dx + dy * dy
  if (len2 === 0) return Math.hypot(p[0] - a[0], p[1] - a[1])
  const t = Math.max(0, Math.min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / len2))
  return Math.hypot(p[0] - (a[0] + t * dx), p[1] - (a[1] + t * dy))
}

/** Douglas–Peucker on an open chain: its ends always stay. */
function douglasPeucker(points: readonly Pt[], tolerance: number): Pt[] {
  const first = points[0]
  const last = points[points.length - 1]
  if (first === undefined || last === undefined || points.length < 3) return [...points]
  let index = -1
  let max = 0
  for (let i = 1; i < points.length - 1; i += 1) {
    const p = points[i]
    if (p === undefined) continue
    const d = distanceToSegment(p, first, last)
    if (d > max) {
      max = d
      index = i
    }
  }
  if (index < 0 || max <= tolerance) return [first, last]
  const left = douglasPeucker(points.slice(0, index + 1), tolerance)
  const right = douglasPeucker(points.slice(index), tolerance)
  return [...left.slice(0, -1), ...right]
}

/**
 * A closed ring with every point dropped that lies within `tolerance` of the
 * outline the remaining points draw: Douglas–Peucker on the two chains between
 * its first point and the point farthest from it. A ring of four points or
 * fewer, or one that would fall under three, is returned as it is.
 */
export function simplifyRing(ring: readonly Pt[], tolerance: number): Pt[] {
  const start = ring[0]
  if (start === undefined || ring.length <= 4) return [...ring]
  let far = 0
  let max = -1
  ring.forEach((p, i) => {
    const d = Math.hypot(p[0] - start[0], p[1] - start[1])
    if (d > max) {
      max = d
      far = i
    }
  })
  const a = douglasPeucker(ring.slice(0, far + 1), tolerance)
  const b = douglasPeucker([...ring.slice(far), start], tolerance)
  const out = [...a.slice(0, -1), ...b.slice(0, -1)]
  return out.length >= 3 ? out : [...ring]
}
```

- [ ] **Step 4: Use it in the carve.** In `carve.ts`, import `simplifyRing` from `./geometry`, add beside `ARC_TOLERANCE`:

```ts
/** Carved rings are thinned to this many glyph units before they are written (0.06 px at the 48 px hero at 3×). */
const SIMPLIFY_TOLERANCE = 0.01
const round2 = (n: number): string => (Math.round(n * 100) / 100).toString()
```

and replace the `d` construction at the end of `carveSolid` with:

```ts
  const d = result
    .filter((ring) => ring.length > 2)
    .map((ring) => simplifyRing(ring.map((pt): Pt => [pt.X / SCALE, pt.Y / SCALE]), SIMPLIFY_TOLERANCE))
    .map((ring) => ring.map(([x, y], i) => `${i === 0 ? "M" : "L"}${round2(x)} ${round2(y)}`).join(" ") + " Z")
    .join(" ")
```

Remove `fmt` if nothing else uses it. `area` stays computed from `result`, before simplifying.

- [ ] **Step 5: Run** `npm run test --workspace=@pbbls/icons` — all pass (the carve fixtures compare areas and shapes, not point counts; if one compares an exact `d`, re-derive it by reading the test, never by loosening it, and say so in the report). Then `npm run generate --workspace=@pbbls/icons` and check the size: `ls -l packages/icons/glyphs/glyphs.json` should drop from about 4.2 MB to about 2.1 MB.

- [ ] **Step 6: Look at it.** `npm run sheet --workspace=@pbbls/icons -- <scratch dir>/simplified.html home people water-well dolmen at pebble-round` in the scratchpad, screenshot it with headless Chrome at device scale 3, Read the PNG: solids must look the same as before (no facets, no slivers).

- [ ] **Step 7: Commit** `chore(ui): thin carved fills to a hundredth of a unit` with the sources and every regenerated file (`git status` shows them: `glyphs.json`, and nothing under `apps/` since only `glyphs.json` carries carved fills).

---

### Task 2: The path-parser contract

**Files:**
- Create: `packages/icons/scripts/lib/segments.ts`, `packages/icons/fixtures/path-contract.json`
- Test: `packages/icons/scripts/lib/segments.test.ts`

- [ ] **Step 1: The fixture** — create `packages/icons/fixtures/path-contract.json`. It is hand-written and is the contract (spec §4): web, iOS and Android each parse every `d` into exactly these segments. `pts` holds the command's numbers in order (M/L: x y; C: x1 y1 x2 y2 x y; Q: x1 y1 x y; Z: none). Extra coordinate groups repeat their command, an `M`'s as `L` (SVG 1.1 §8.3.2).

```json
{
  "about": "The path-parser contract of packages/icons (spec 2026-11-08-icon-forms-design.md §4). Every surface parses each d into exactly these segments. Hand-written: add a case when a parser bug is found, never edit an expectation to make a parser pass.",
  "cases": [
    { "d": "M2 3 L4 5 Z", "segments": [{ "op": "M", "pts": [2, 3] }, { "op": "L", "pts": [4, 5] }, { "op": "Z", "pts": [] }] },
    { "d": "M-1.5-2L3,4", "segments": [{ "op": "M", "pts": [-1.5, -2] }, { "op": "L", "pts": [3, 4] }] },
    { "d": "M.5 .25 L0.5.5", "segments": [{ "op": "M", "pts": [0.5, 0.25] }, { "op": "L", "pts": [0.5, 0.5] }] },
    { "d": "M1 2 3 4 5 6", "segments": [{ "op": "M", "pts": [1, 2] }, { "op": "L", "pts": [3, 4] }, { "op": "L", "pts": [5, 6] }] },
    { "d": "M0 0 L1 1 2 2", "segments": [{ "op": "M", "pts": [0, 0] }, { "op": "L", "pts": [1, 1] }, { "op": "L", "pts": [2, 2] }] },
    { "d": "M1 1 C2 2 3 3 4 4 5 5 6 6 7 7", "segments": [{ "op": "M", "pts": [1, 1] }, { "op": "C", "pts": [2, 2, 3, 3, 4, 4] }, { "op": "C", "pts": [5, 5, 6, 6, 7, 7] }] },
    { "d": "M1 1 Q2 2 3 3", "segments": [{ "op": "M", "pts": [1, 1] }, { "op": "Q", "pts": [2, 2, 3, 3] }] },
    { "d": "M1 1 L2 2 Z M3 3 L4 4 Z", "segments": [{ "op": "M", "pts": [1, 1] }, { "op": "L", "pts": [2, 2] }, { "op": "Z", "pts": [] }, { "op": "M", "pts": [3, 3] }, { "op": "L", "pts": [4, 4] }, { "op": "Z", "pts": [] }] },
    { "d": "M11.38 8.5 C11.9 8.74 13.83 9.15 14.47 9.93", "segments": [{ "op": "M", "pts": [11.38, 8.5] }, { "op": "C", "pts": [11.9, 8.74, 13.83, 9.15, 14.47, 9.93] }] },
    { "d": "M15.58 20.22 L15.53 20.48 L15.41 20.71 Z", "segments": [{ "op": "M", "pts": [15.58, 20.22] }, { "op": "L", "pts": [15.53, 20.48] }, { "op": "L", "pts": [15.41, 20.71] }, { "op": "Z", "pts": [] }] }
  ]
}
```

- [ ] **Step 2: Failing test** — `packages/icons/scripts/lib/segments.test.ts`:

```ts
import { readFileSync } from "node:fs"
import { fileURLToPath } from "node:url"
import { describe, expect, it } from "vitest"
import { segmentsOf } from "./segments"

const FIXTURE = fileURLToPath(new URL("../../fixtures/path-contract.json", import.meta.url))

type Case = { d: string; segments: { op: string; pts: number[] }[] }

function cases(): Case[] {
  const json: unknown = JSON.parse(readFileSync(FIXTURE, "utf8"))
  if (typeof json !== "object" || json === null || !("cases" in json) || !Array.isArray(json.cases)) throw new Error("path-contract.json: expected { cases: [...] }")
  return json.cases.filter((c: unknown): c is Case => typeof c === "object" && c !== null && "d" in c && "segments" in c)
}

describe("segmentsOf (the path-parser contract)", () => {
  it("reads every fixture case as the fixture says", () => {
    const all = cases()
    expect(all.length).toBeGreaterThan(5)
    for (const c of all) expect(segmentsOf(c.d), c.d).toEqual(c.segments)
  })

  it("refuses a command the normaliser never writes", () => {
    expect(() => segmentsOf("M0 0 a1 1 0 0 1 2 2")).toThrow(/not normalised/)
  })
})
```

- [ ] **Step 3: Run** `npx vitest run scripts/lib/segments.test.ts` — FAIL (no module).

- [ ] **Step 4: Implement** `packages/icons/scripts/lib/segments.ts`:

```ts
import { tokenizePath } from "./lint"

/** One drawing command of normalised path data, with its numbers in order. */
export type Segment = { op: "M" | "L" | "C" | "Q" | "Z"; pts: number[] }

const ARITY: Record<string, number> = { M: 2, L: 2, C: 6, Q: 4 }

/**
 * Normalised path data (absolute `M L C Q Z`, spec D7) as the segments every
 * surface must read (`fixtures/path-contract.json`). Extra coordinate groups
 * repeat their command, an `M`'s as `L`.
 */
export function segmentsOf(d: string): Segment[] {
  const out: Segment[] = []
  for (const { command, numbers } of tokenizePath(d)) {
    if (command === "Z") {
      out.push({ op: "Z", pts: [] })
      continue
    }
    const arity = ARITY[command]
    if (arity === undefined) throw new Error(`segmentsOf: ${command} is not normalised (absolute M L C Q Z only)`)
    for (let i = 0; i + arity <= numbers.length; i += arity) {
      const op: Segment["op"] = command === "M" ? (i === 0 ? "M" : "L") : command === "L" ? "L" : command === "C" ? "C" : "Q"
      out.push({ op, pts: numbers.slice(i, i + arity) })
    }
  }
  return out
}
```

- [ ] **Step 5: Run** the test — PASS. Add a third test that every `d` in `glyphs/glyphs.json` (paths, carved, overrides, dotted) parses without throwing and starts with an `M` — read the file the same way `emit-glyphs.test.ts` reads what it needs, or through `loadGlyphs` on the package root.

- [ ] **Step 6: Commit** `test(ui): the path-parser contract every surface reads`.

---

### Task 3: The forms contract

**Files:**
- Modify: `packages/icons/scripts/lib/emit-glyphs.ts`
- Test: `packages/icons/scripts/lib/emit-glyphs.test.ts`
- Generated: `packages/icons/glyphs/forms-contract.json`

The forms contract records, for a dozen glyphs that between them use every rule (body, hidden mark, lit mark, carved mark, steps, hidden step, parts, dotted, overrides), what the generator's `formsOf`, `renderForm` and `resolveState` say. iOS (and Android in part 6) must say the same.

- [ ] **Step 1: Failing test** — append to `emit-glyphs.test.ts` (import `formsContract`, `CONTRACT_GLYPHS` from `./emit-glyphs`, and `buildGlyph`'s existing fixture helpers; the `ring` row at the top of the file is the model):

```ts
describe("formsContract", () => {
  const states = { rest: ["regular"], selected: ["solid", "progress-n", "marked", "regular"], progress: ["progress-k", "regular"] }
  const home = buildGlyph(
    { ...row, name: "home" },
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
  <path d="M4 20 L12 4 L20 20" data-role="body"/>
  <path d="M11 17 L13 17" data-role="mark"/>
</svg>`,
    {},
  )

  it("lists each glyph's forms, renders every one and resolves every state", () => {
    const contract = formsContract([home], states, ["home"])
    const entry = contract.glyphs.home
    expect(entry?.forms).toEqual(["regular", "marked", "solid", "solid-marked"])
    expect(Object.keys(entry?.rendered ?? {})).toEqual(entry?.forms)
    expect(entry?.rendered.solid?.[0]).toMatchObject({ fill: true, evenOdd: true, stroke: false })
    expect(entry?.states).toEqual({ rest: "regular", selected: "solid", "progress@0": "regular", "progress@1": "regular" })
  })

  it("names a missing glyph", () => {
    expect(() => formsContract([home], states, ["nope"])).toThrow(/nope/)
  })

  it("samples glyphs that exist in the set", () => {
    expect(CONTRACT_GLYPHS.length).toBeGreaterThanOrEqual(10)
  })
})
```

(`progress@k` runs k = 0…steps+1; with no steps that is 0 and 1, and `progress-k` falls back to regular.)

- [ ] **Step 2: Run** `npx vitest run scripts/lib/emit-glyphs.test.ts` — FAIL.

- [ ] **Step 3: Implement** in `emit-glyphs.ts` (imports: `formsOf`, `formKey`, `parseFormKey`, `renderForm`, `type RenderedPath` from `./forms`; `resolveState`, `type StateTable` from `./states`):

```ts
export const CONTRACT_FILE = "packages/icons/glyphs/forms-contract.json"

/**
 * The glyphs the forms contract samples: between them a body, a hidden mark
 * lit beside a solid (home), a carved mark (paper-plane-trail), steps
 * (stack, path), a hidden step (people), parts (users, dolmen), a dotted
 * drawing (pebble-glyph) and overrides (water-well, at).
 */
export const CONTRACT_GLYPHS = [
  "home", "people", "person", "path", "stack", "collection", "pebble-round",
  "users", "dolmen", "pebble-glyph", "water-well", "at", "paper-plane-trail",
] as const

export type ContractEntry = { forms: string[]; rendered: Record<string, RenderedPath[]>; states: Record<string, string> }

/** What the generator says each sampled glyph draws (spec §4): every surface's renderer must say the same. */
export function formsContract(glyphs: readonly GlyphData[], states: StateTable, names: readonly string[] = CONTRACT_GLYPHS): { notice: string; glyphs: Record<string, ContractEntry> } {
  const out: Record<string, ContractEntry> = {}
  for (const name of names) {
    const glyph = glyphs.find((g) => g.name === name)
    if (!glyph) throw new Error(`forms contract: ${name} is not an emitted glyph`)
    const forms = formsOf(glyph)
    const rendered: Record<string, RenderedPath[]> = {}
    for (const key of forms) {
      const form = parseFormKey(key)
      if (!form) throw new Error(`forms contract: ${name}: ${key} is not a form key`)
      rendered[key] = renderForm(glyph, form)
    }
    const resolved: Record<string, string> = {}
    for (const state of Object.keys(states)) {
      if (state === "progress") for (let k = 0; k <= glyph.steps + 1; k += 1) resolved[`progress@${k}`] = formKey(resolveState(states, forms, state, k))
      else resolved[state] = formKey(resolveState(states, forms, state))
    }
    out[name] = { forms, rendered, states: resolved }
  }
  return { notice: GENERATED_NOTICE, glyphs: out }
}
```

Memoise the build so the two emitters that read glyphs carve once per run:

```ts
const built = new Map<string, GlyphData[]>()

/** `loadGlyphs`, computed once per package root and process: every emitter of one generate run reads the same glyphs. */
export function loadGlyphsOnce(packageRoot: string): GlyphData[] {
  const cached = built.get(packageRoot)
  if (cached) return cached
  const glyphs = loadGlyphs(packageRoot)
  built.set(packageRoot, glyphs)
  return glyphs
}
```

and in `glyphsEmitter`: `owned.files` becomes `[GLYPHS_FILE, CONTRACT_FILE]`, and `emit()` is

```ts
    emit() {
      const states = loadStates(packageRoot)
      const glyphs = loadGlyphsOnce(packageRoot)
      const data = { notice: GENERATED_NOTICE, version: 1, states, glyphs }
      return new Map([
        [GLYPHS_FILE, JSON.stringify(data) + "\n"],
        [CONTRACT_FILE, JSON.stringify(formsContract(glyphs, states), null, 2) + "\n"],
      ])
    },
```

If an existing test asserts `owned` or the emitted map's keys, update it to the two files.

- [ ] **Step 4: Run** the package checks — PASS. `npm run generate --workspace=@pbbls/icons`; open `packages/icons/glyphs/forms-contract.json` and check `home.states.selected` is `solid`, `stack.states.selected` is `progress-3`, `person.states.selected` is `marked`, `water-well.rendered.solid` has the override's paths.

- [ ] **Step 5: Commit** `test(ui): the forms contract every renderer reads` with the generated contract.

---

### Task 4: The iOS glyphs emitter

**Files:**
- Create: `packages/icons/scripts/lib/emit-ios-glyphs.ts`
- Modify: `packages/icons/scripts/build.ts`
- Test: `packages/icons/scripts/lib/emit-ios-glyphs.test.ts`
- Generated: `apps/ios/Pebbles/Resources/pbbls-glyphs.json`, `apps/ios/Pebbles/Theme/PbblsGlyph.swift`, `apps/ios/Pebbles/Theme/PbblsGlyphState.swift`

- [ ] **Step 1: Failing test** — `emit-ios-glyphs.test.ts`:

```ts
import { describe, expect, it } from "vitest"
import { buildGlyph } from "./emit-glyphs"
import { IOS_GLYPH_DATA, IOS_GLYPH_SWIFT, IOS_STATE_SWIFT, iosGlyphFiles } from "./emit-ios-glyphs"
import type { IconRow } from "./manifest"

const row = (name: string, meaning: string): IconRow => ({ name, meaning, family: null, tags: { category: ["stones"] }, variants: [], wave: "w", status: "candidate", migrated: [], replaces: { ios: [], android: [], web: [] } })
const SVG = (inner: string) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">${inner}</svg>`
const glyphs = [
  buildGlyph(row("pebble-round", "A recorded moment."), SVG('<path d="M4 12 C4 6 20 6 20 12 C20 18 4 18 4 12" data-role="body"/>'), {}),
  buildGlyph(row("case", "A case."), SVG('<path d="M4 4 L20 20"/>'), {}),
]
const states = { rest: ["regular"], selected: ["solid", "regular"] }

describe("iosGlyphFiles", () => {
  const files = iosGlyphFiles(glyphs, states)

  it("writes the data keyed by name, without meaning, tags or family", () => {
    const data: unknown = JSON.parse(files.get(IOS_GLYPH_DATA) ?? "")
    expect(data).toMatchObject({ version: 1, states })
    const round = (data as { glyphs: Record<string, Record<string, unknown>> }).glyphs["pebble-round"]
    expect(Object.keys(round ?? {}).sort()).toEqual(["carved", "dotted", "overrides", "parts", "paths", "steps"])
  })

  it("writes one enum case per glyph, its meaning as the doc comment, keywords in backticks", () => {
    const swift = files.get(IOS_GLYPH_SWIFT) ?? ""
    expect(swift).toContain("enum PbblsGlyph: String, CaseIterable, Sendable {")
    expect(swift).toContain('    /// A recorded moment.\n    case pebbleRound = "pebble-round"')
    expect(swift).toContain('case `case` = "case"')
    expect(swift.startsWith("// Generated by packages/icons")).toBe(true)
  })

  it("writes the states in the table's order", () => {
    const swift = files.get(IOS_STATE_SWIFT) ?? ""
    expect(swift).toContain("enum PbblsGlyphState: String, CaseIterable, Sendable {\n    case rest\n    case selected\n}")
  })
})
```

The `as` cast in the first test is a test-local read of parsed JSON; if the package lint refuses it, narrow with `typeof`/`in` checks instead.

- [ ] **Step 2: Run** — FAIL (no module).

- [ ] **Step 3: Implement** `emit-ios-glyphs.ts`:

```ts
/**
 * The iOS glyphs emitter (spec D7, §2): the forms data as an app resource, the
 * enum of names and the enum of state tokens. The old asset catalog and
 * `PbblsIcon` stay with `iosEmitter` until part 7 removes them.
 */
import { GENERATED_NOTICE, type Emitter } from "./emit"
import { loadGlyphsOnce } from "./emit-glyphs"
import { docLines, swiftCase } from "./emit-ios"
import type { GlyphData } from "./forms"
import { loadStates, type StateTable } from "./states"

export const IOS_GLYPH_DATA = "apps/ios/Pebbles/Resources/pbbls-glyphs.json"
export const IOS_GLYPH_SWIFT = "apps/ios/Pebbles/Theme/PbblsGlyph.swift"
export const IOS_STATE_SWIFT = "apps/ios/Pebbles/Theme/PbblsGlyphState.swift"

function nativeData(glyphs: readonly GlyphData[], states: StateTable): string {
  const byName: Record<string, Omit<GlyphData, "name" | "meaning" | "family" | "tags">> = {}
  for (const { name, meaning: _m, family: _f, tags: _t, ...forms } of glyphs) byName[name] = forms
  return JSON.stringify({ notice: GENERATED_NOTICE, version: 1, states, glyphs: byName }) + "\n"
}

function glyphSwift(glyphs: readonly GlyphData[]): string {
  const cases = glyphs.map((g) => [...docLines(g.meaning), `    case ${swiftCase(g.name)} = "${g.name}"`].join("\n"))
  return `// ${GENERATED_NOTICE}
// swiftlint:disable file_length type_body_length

/// The hand-drawn Pebbles glyphs (\`packages/icons\`): one case per drawing, the
/// raw value being its manifest name. Its paths and forms are in
/// \`pbbls-glyphs.json\`; draw it with \`PbblsGlyphView\`.
enum PbblsGlyph: String, CaseIterable, Sendable {
${cases.join("\n")}
}
`
}

function stateSwift(states: StateTable): string {
  return `// ${GENERATED_NOTICE}

/// The interaction states of \`packages/icons/states.json\` (spec D6). Each
/// names the forms it shows, first available wins; \`PbblsGlyph.form(for:k:)\`
/// resolves one, the same way on every surface.
enum PbblsGlyphState: String, CaseIterable, Sendable {
${Object.keys(states).map((s) => `    case ${s}`).join("\n")}
}
`
}

/** The three iOS files for these glyphs and states. */
export function iosGlyphFiles(glyphs: readonly GlyphData[], states: StateTable): Map<string, string> {
  return new Map([
    [IOS_GLYPH_DATA, nativeData(glyphs, states)],
    [IOS_GLYPH_SWIFT, glyphSwift(glyphs)],
    [IOS_STATE_SWIFT, stateSwift(states)],
  ])
}

export function iosGlyphsEmitter(packageRoot: string): Emitter {
  return {
    name: "ios-glyphs",
    owned: { dirs: [], files: [IOS_GLYPH_DATA, IOS_GLYPH_SWIFT, IOS_STATE_SWIFT], patterns: [] },
    emit: () => iosGlyphFiles(loadGlyphsOnce(packageRoot), loadStates(packageRoot)),
  }
}
```

If the package lint refuses the unused destructured names, build the object field by field (`{ paths: g.paths, parts: g.parts, steps: g.steps, carved: g.carved, dotted: g.dotted, overrides: g.overrides }`) instead. Export `docLines` and `swiftCase` from `emit-ios.ts` if they are not exported already (they are, at the time of writing).

In `build.ts`, import `iosGlyphsEmitter` and register it after `glyphsEmitter(PACKAGE_ROOT)`:

```ts
const EMITTERS: readonly Emitter[] = [reactEmitter, iosEmitter, androidEmitter, glyphsEmitter(PACKAGE_ROOT), iosGlyphsEmitter(PACKAGE_ROOT)]
```

- [ ] **Step 4: Run** the package checks — PASS. `npm run generate --workspace=@pbbls/icons`. Expect three new files under `apps/ios`. `ls -l apps/ios/Pebbles/Resources/pbbls-glyphs.json` — about 1.9 MB. `cd apps/ios && swiftlint lint Pebbles/Theme/PbblsGlyph.swift Pebbles/Theme/PbblsGlyphState.swift` — no errors (the old `PbblsIcon.swift` passes with the same style; if a rule fires that `PbblsIcon.swift` silences, add the same `swiftlint:disable` to the generated header, in the emitter).

- [ ] **Step 5:** `cd apps/ios && xcodegen generate && xcodebuild build -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet` — builds (the JSON lands in Copy Bundle Resources because `Pebbles/` is a source folder; confirm with `find ~/Library/Developer/Xcode/DerivedData/Pebbles-*/Build/Products -name pbbls-glyphs.json`).

- [ ] **Step 6: Commit** `feat(ios): emit the glyph forms data and names for iOS` with the emitter, its test, `build.ts` and the three generated files.

---

### Task 5: Forms, data and the path parser in Swift

**Files:**
- Create: `apps/ios/Pebbles/Theme/Glyphs/GlyphForm.swift`, `GlyphData.swift`, `GlyphPathParser.swift`
- Test: `apps/ios/PebblesTests/Theme/Glyphs/RepoFile.swift`, `GlyphFormTests.swift`, `GlyphPathContractTests.swift`

- [ ] **Step 1: Failing tests.** `RepoFile.swift`:

```swift
import Foundation

/// A file of this repository, read in place from the simulator as `PbblsIconTests` reads the manifest.
enum RepoFile {
    static let root = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent() // Glyphs
        .deletingLastPathComponent() // Theme
        .deletingLastPathComponent() // PebblesTests
        .deletingLastPathComponent() // ios
        .deletingLastPathComponent() // apps
        .deletingLastPathComponent() // the repository

    static func url(_ path: String) -> URL {
        root.appendingPathComponent(path)
    }
}
```

`GlyphFormTests.swift`:

```swift
import Testing
@testable import Pebbles

@Suite("Glyph forms")
struct GlyphFormTests {
    @Test(arguments: [
        ("regular", GlyphForm(.regular)),
        ("marked", GlyphForm(.regular, marked: true)),
        ("solid", GlyphForm(.solid)),
        ("solid-marked", GlyphForm(.solid, marked: true)),
        ("dotted", GlyphForm(.dotted)),
        ("parts-left+middle", GlyphForm(.parts(["left", "middle"]))),
        ("parts-moon-marked", GlyphForm(.parts(["moon"]), marked: true)),
        ("progress-2", GlyphForm(.progress(2))),
        ("progress-12-marked", GlyphForm(.progress(12), marked: true)),
    ])
    func keysRoundTrip(key: String, form: GlyphForm) {
        #expect(form.key == key)
        #expect(GlyphForm(key: key) == form)
    }

    @Test(arguments: ["", "marked-marked", "regular-marked", "dotted-marked", "progress-0", "progress-02", "parts-", "parts-Moon", "parts-a++b", "half"])
    func refusesWhatIsNotAFormKey(key: String) {
        #expect(GlyphForm(key: key) == nil)
    }

    @Test func partCombinationsFollowTheGenerator() {
        #expect(GlyphData.partCombinations([]).isEmpty)
        #expect(GlyphData.partCombinations(["a"]).isEmpty)
        #expect(GlyphData.partCombinations(["l", "m", "r"]) == [["l"], ["m"], ["r"], ["l", "m"], ["l", "r"], ["m", "r"]])
    }
}
```

`GlyphPathContractTests.swift`:

```swift
import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

/// The iOS side of the path-parser contract (spec §4): `packages/icons/fixtures/path-contract.json`.
@Suite("Glyph path contract")
struct GlyphPathContractTests {
    struct Seg: Decodable, Equatable {
        let op: String
        let pts: [Double]
    }

    struct Case: Decodable {
        let d: String
        let segments: [Seg]
    }

    struct Fixture: Decodable {
        let cases: [Case]
    }

    static func flat(_ s: GlyphPathParser.Segment) -> Seg {
        func xy(_ p: CGPoint) -> [Double] { [Double(p.x), Double(p.y)] }
        switch s {
        case .move(let p): return Seg(op: "M", pts: xy(p))
        case .line(let p): return Seg(op: "L", pts: xy(p))
        case .cubic(let c1, let c2, let p): return Seg(op: "C", pts: xy(c1) + xy(c2) + xy(p))
        case .quad(let c, let p): return Seg(op: "Q", pts: xy(c) + xy(p))
        case .close: return Seg(op: "Z", pts: [])
        }
    }

    @Test func everyCaseParsesAsTheFixtureSays() throws {
        let data = try Data(contentsOf: RepoFile.url("packages/icons/fixtures/path-contract.json"))
        let fixture = try JSONDecoder().decode(Fixture.self, from: data)
        #expect(fixture.cases.count > 5)
        for c in fixture.cases {
            #expect(GlyphPathParser.segments(c.d).map(Self.flat) == c.segments, Comment(rawValue: c.d))
        }
    }

    @Test func buildsAPathWithTheSegmentsBounds() {
        let path = GlyphPathParser.cgPath("M2 3 L20 3 L20 21 Z")
        #expect(path.boundingBox == CGRect(x: 2, y: 3, width: 18, height: 18))
    }
}
```

- [ ] **Step 2: Run** `cd apps/ios && xcodegen generate && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' -only-testing:PebblesTests/GlyphFormTests -only-testing:PebblesTests/GlyphPathContractTests` — fails to compile (types missing).

- [ ] **Step 3: Implement** `GlyphForm.swift`:

```swift
/// What a form fills (spec D3): nothing, the body, some of its parts, the
/// first k steps, or the drawn dotted line.
enum GlyphFill: Hashable, Sendable {
    case regular
    case solid
    case parts([String])
    case progress(Int)
    case dotted
}

/// A form (spec D3): a fill, plus the mark when the glyph has one. Its `key`
/// is the generator's form key (`solid-marked`, `parts-moon`, `progress-2`).
struct GlyphForm: Hashable, Sendable {
    var fill: GlyphFill
    var marked: Bool

    init(_ fill: GlyphFill = .regular, marked: Bool = false) {
        self.fill = fill
        self.marked = marked
    }

    static let regular = GlyphForm(.regular)
    static let marked = GlyphForm(.regular, marked: true)
    static let solid = GlyphForm(.solid)
    static let dotted = GlyphForm(.dotted)

    var fillKey: String {
        switch fill {
        case .regular: "regular"
        case .solid: "solid"
        case .dotted: "dotted"
        case .parts(let names): "parts-" + names.joined(separator: "+")
        case .progress(let k): "progress-\(k)"
        }
    }

    /// `regular` marked is `marked`; any other fill takes a `-marked` suffix.
    var key: String {
        guard marked else { return fillKey }
        return fillKey == "regular" ? "marked" : fillKey + "-marked"
    }

    /// The form a key names, or nil when it is not a form key (`parseFormKey` in `forms.ts`).
    init?(key: String) {
        if key == "marked" {
            self.init(.regular, marked: true)
            return
        }
        let marked = key.hasSuffix("-marked")
        let head = marked ? String(key.dropLast("-marked".count)) : key
        switch head {
        case "regular" where !marked: self.init(.regular)
        case "dotted" where !marked: self.init(.dotted)
        case "solid": self.init(.solid, marked: marked)
        default:
            if head.hasPrefix("parts-") {
                let names = head.dropFirst("parts-".count).split(separator: "+", omittingEmptySubsequences: false).map(String.init)
                let word = { (s: String) in !s.isEmpty && s.allSatisfy { ("a"..."z").contains($0) } }
                guard names.allSatisfy(word) else { return nil }
                self.init(.parts(names), marked: marked)
            } else if head.hasPrefix("progress-") {
                let digits = head.dropFirst("progress-".count)
                guard let k = Int(digits), k >= 1, !digits.hasPrefix("0") else { return nil }
                self.init(.progress(k), marked: marked)
            } else {
                return nil
            }
        }
    }
}
```

`GlyphData.swift`:

```swift
/// A path as a form draws it: filled (non-zero or even-odd), stroked, or both.
struct RenderedPathData: Decodable, Hashable, Sendable {
    let d: String
    let fill: Bool
    let evenOdd: Bool
    let stroke: Bool
}

/// One path of a glyph with its role (spec D2), as `glyphs.json` stores it.
struct GlyphPathData: Decodable, Sendable {
    enum Role: String, Decodable, Sendable {
        case body, mark, step
    }

    let d: String
    let role: Role?
    let part: String?
    let step: Int?
    let hidden: Bool?
    /// A mark or step with lines inside it: its carved fill, drawn instead of `d` when it fills.
    let fill: String?
    /// The paths `fill` is carved through, left out while it fills.
    let cuts: [Int]?
}

/// A solid or parts fill worked out at build time: indices are into `paths`.
struct CarvedFillData: Decodable, Sendable {
    let d: String
    let keep: [Int]
    let lit: [Int]
}

/// A glyph as `pbbls-glyphs.json` stores it (spec §2), the contract the generator writes.
struct GlyphData: Decodable, Sendable {
    let paths: [GlyphPathData]
    let parts: [String]
    let steps: Int
    let carved: [String: CarvedFillData]
    let dotted: [RenderedPathData]?
    let overrides: [String: [RenderedPathData]]

    static let empty = GlyphData(paths: [], parts: [], steps: 0, carved: [:], dotted: nil, overrides: [:])

    var hasMark: Bool { paths.contains { $0.role == .mark } }
    var hasBody: Bool { paths.contains { $0.role == .body } }

    /// The forms this glyph has, as keys, in the generator's order (`formKeysOf` in `forms.ts`).
    var formKeys: [String] {
        var fills: [GlyphFill] = [.regular]
        if hasBody { fills.append(.solid) }
        fills += Self.partCombinations(parts).map { GlyphFill.parts($0) }
        if steps > 0 { fills += (1...steps).map { GlyphFill.progress($0) } }
        var keys = fills.flatMap { fill in
            hasMark ? [GlyphForm(fill).key, GlyphForm(fill, marked: true).key] : [GlyphForm(fill).key]
        }
        if dotted != nil { keys.insert("dotted", at: hasMark ? 2 : 1) }
        return keys
    }

    /// Every non-empty combination of parts short of all of them, smallest first, in source order (`partCombinations` in `roles.ts`).
    static func partCombinations(_ names: [String]) -> [[String]] {
        let n = names.count
        guard n >= 2 else { return [] }
        let all = (1..<((1 << n) - 1)).map { bits in
            names.enumerated().filter { bits & (1 << $0.offset) != 0 }.map(\.element)
        }
        // JavaScript's sort is stable: keep the bit order within a size.
        return all.enumerated()
            .sorted { ($0.element.count, $0.offset) < ($1.element.count, $1.offset) }
            .map(\.element)
    }
}

/// The resource as a whole.
struct GlyphFile: Decodable, Sendable {
    let version: Int
    let states: [String: [String]]
    let glyphs: [String: GlyphData]

    static let empty = GlyphFile(version: 1, states: [:], glyphs: [:])
}
```

`GlyphPathParser.swift`:

```swift
import CoreGraphics

/// Reads the normalised path data of `pbbls-glyphs.json` (spec D7): absolute
/// `M L C Q Z`, numbers split by spaces, commas or a sign. A command's extra
/// coordinate groups repeat it, an `M`'s as `L` (SVG 1.1 §8.3.2). Any other
/// command is skipped: the generator never writes one
/// (`packages/icons/fixtures/path-contract.json` is the contract).
enum GlyphPathParser {
    enum Segment: Equatable, Sendable {
        case move(CGPoint)
        case line(CGPoint)
        case cubic(CGPoint, CGPoint, CGPoint)
        case quad(CGPoint, CGPoint)
        case close
    }

    static func segments(_ d: String) -> [Segment] {
        var out: [Segment] = []
        for token in tokens(d) {
            let n = token.numbers
            let points = stride(from: 0, to: n.count - 1, by: 2).map { CGPoint(x: n[$0], y: n[$0 + 1]) }
            switch token.command {
            case "M":
                out += points.enumerated().map { $0.offset == 0 ? .move($0.element) : .line($0.element) }
            case "L":
                out += points.map { .line($0) }
            case "C":
                out += stride(from: 0, to: points.count - 2, by: 3).map { .cubic(points[$0], points[$0 + 1], points[$0 + 2]) }
            case "Q":
                out += stride(from: 0, to: points.count - 1, by: 2).map { .quad(points[$0], points[$0 + 1]) }
            case "Z":
                out.append(.close)
            default:
                continue
            }
        }
        return out
    }

    static func cgPath(_ d: String) -> CGPath {
        let path = CGMutablePath()
        for segment in segments(d) {
            switch segment {
            case .move(let p): path.move(to: p)
            case .line(let p): path.addLine(to: p)
            case .cubic(let c1, let c2, let p): path.addCurve(to: p, control1: c1, control2: c2)
            case .quad(let c, let p): path.addQuadCurve(to: p, control: c)
            case .close: path.closeSubpath()
            }
        }
        return path
    }

    private struct Token {
        let command: Character
        var numbers: [CGFloat]
    }

    private static func tokens(_ d: String) -> [Token] {
        var out: [Token] = []
        var number = ""
        func flush() {
            defer { number = "" }
            guard !number.isEmpty, let value = Double(number), !out.isEmpty else { return }
            out[out.count - 1].numbers.append(CGFloat(value))
        }
        for ch in d {
            switch ch {
            case "M", "L", "C", "Q", "Z", "m", "l", "c", "q", "z", "A", "a", "H", "h", "V", "v", "S", "s", "T", "t":
                flush()
                out.append(Token(command: ch, numbers: []))
            case "-":
                if !number.isEmpty && !number.hasSuffix("e") { flush() }
                number.append(ch)
            case ".":
                if number.contains(".") && !number.contains("e") { flush() }
                number.append(ch)
            case "e", "0"..."9":
                number.append(ch)
            default:
                flush()
            }
        }
        flush()
        return out
    }
}
```

- [ ] **Step 4: Run** the two suites — PASS. `swiftlint` from `apps/ios` — no errors.

- [ ] **Step 5: Commit** `feat(ios): read glyph forms, data and path strings`.

---

### Task 6: The renderer and the state tokens, against the forms contract

**Files:**
- Create: `apps/ios/Pebbles/Theme/Glyphs/GlyphRenderer.swift`, `GlyphCatalog.swift`, `PbblsGlyph+Forms.swift`
- Test: `apps/ios/PebblesTests/Theme/Glyphs/GlyphFormsContractTests.swift`

- [ ] **Step 1: Failing test** — `GlyphFormsContractTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

/// The iOS side of the forms contract (spec §4): `packages/icons/glyphs/forms-contract.json`,
/// generated from `formsOf`, `renderForm` and `resolveState`, against the bundled data.
@Suite("Glyph forms contract")
struct GlyphFormsContractTests {
    struct Entry: Decodable {
        let forms: [String]
        let rendered: [String: [RenderedPathData]]
        let states: [String: String]
    }

    struct Contract: Decodable {
        let glyphs: [String: Entry]
    }

    static func contract() throws -> Contract {
        try JSONDecoder().decode(Contract.self, from: Data(contentsOf: RepoFile.url("packages/icons/glyphs/forms-contract.json")))
    }

    @Test func everySampledGlyphHasTheGeneratorsForms() throws {
        let contract = try Self.contract()
        #expect(contract.glyphs.count >= 10)
        for (name, entry) in contract.glyphs {
            let glyph = try #require(PbblsGlyph(rawValue: name), Comment(rawValue: name))
            #expect(GlyphCatalog.shared.glyph(glyph).formKeys == entry.forms, Comment(rawValue: name))
        }
    }

    @Test func everyFormRendersAsTheGeneratorRendersIt() throws {
        for (name, entry) in try Self.contract().glyphs {
            let data = GlyphCatalog.shared.glyph(try #require(PbblsGlyph(rawValue: name)))
            for (key, expected) in entry.rendered {
                let form = try #require(GlyphForm(key: key), Comment(rawValue: "\(name) \(key)"))
                #expect(GlyphRenderer.render(data, form) == expected, Comment(rawValue: "\(name) \(key)"))
            }
        }
    }

    @Test func everyStateResolvesAsTheGeneratorResolvesIt() throws {
        for (name, entry) in try Self.contract().glyphs {
            let glyph = try #require(PbblsGlyph(rawValue: name))
            for (spec, expected) in entry.states {
                let parts = spec.split(separator: "@")
                let state = try #require(PbblsGlyphState(rawValue: String(parts[0])), Comment(rawValue: spec))
                let k = parts.count > 1 ? Int(parts[1]) ?? 0 : 0
                #expect(glyph.form(for: state, k: k).key == expected, Comment(rawValue: "\(name) \(spec)"))
            }
        }
    }

    @Test func progressZeroIsRegularAndAMissingFormFallsBackToRegular() throws {
        let stack = GlyphCatalog.shared.glyph(.stack)
        #expect(GlyphRenderer.render(stack, GlyphForm(.progress(0))) == GlyphRenderer.render(stack, .regular))
        #expect(GlyphRenderer.render(stack, .solid) == GlyphRenderer.render(stack, .regular))
    }

    @Test func everyCaseHasData() {
        for glyph in PbblsGlyph.allCases {
            #expect(!GlyphCatalog.shared.glyph(glyph).paths.isEmpty, Comment(rawValue: glyph.rawValue))
        }
    }
}
```

- [ ] **Step 2: Run** `-only-testing:PebblesTests/GlyphFormsContractTests` — fails to compile.

- [ ] **Step 3: Implement** `GlyphRenderer.swift` (drawing comes in Task 7; this task is the port):

```swift
/// `renderForm` and `resolveState` from `packages/icons/scripts/lib`, in Swift.
/// The forms contract (`GlyphFormsContractTests`) holds them equal.
enum GlyphRenderer {
    private static func stroked(_ d: String) -> RenderedPathData { RenderedPathData(d: d, fill: false, evenOdd: false, stroke: true) }
    private static func filled(_ d: String) -> RenderedPathData { RenderedPathData(d: d, fill: true, evenOdd: false, stroke: true) }
    private static func carved(_ d: String) -> RenderedPathData { RenderedPathData(d: d, fill: true, evenOdd: true, stroke: false) }
    /// A filled mark or step: its carved fill when lines run inside it.
    private static func lit(_ p: GlyphPathData) -> RenderedPathData { p.fill.map(carved) ?? filled(p.d) }

    /// The paths a form draws, overrides first (spec D3, D4). `progress(0)` is
    /// regular; a form the glyph does not have draws regular (the generator
    /// throws there, a view should not).
    static func render(_ glyph: GlyphData, _ form: GlyphForm) -> [RenderedPathData] {
        if case .progress(0) = form.fill { return render(glyph, GlyphForm(.regular, marked: form.marked)) }
        let key = form.key
        guard glyph.formKeys.contains(key) else { return key == "regular" ? [] : render(glyph, .regular) }
        if let override = glyph.overrides[key] { return override }
        let path = { (i: Int) in glyph.paths.indices.contains(i) ? glyph.paths[i] : nil }
        switch form.fill {
        case .dotted:
            return glyph.dotted ?? []
        case .solid, .parts:
            guard let fill = glyph.carved[key] else { return render(glyph, .regular) }
            return [carved(fill.d)]
                + fill.keep.compactMap { path($0).map { stroked($0.d) } }
                + fill.lit.compactMap { path($0).map(lit) }
        case .regular, .progress:
            break
        }
        var k = 0
        if case .progress(let n) = form.fill { k = n }
        // A hidden path is drawn only when its role fills it; everything else is stroked.
        // A filled path with a carved `fill` draws that instead, and the lines it is carved through are left out.
        let fills = { (p: GlyphPathData) in (form.marked && p.role == .mark) || (p.role == .step && (p.step ?? 0) <= k) }
        let cut = Set(glyph.paths.filter { fills($0) && $0.fill != nil }.flatMap { $0.cuts ?? [] })
        return glyph.paths.enumerated().compactMap { i, p in
            let on = fills(p)
            if (p.hidden == true && !on) || cut.contains(i) { return nil }
            return on ? lit(p) : stroked(p.d)
        }
    }

    /// The form a state shows on a glyph with these form keys (`resolveState` in `states.ts`).
    static func resolve(states: [String: [String]], formKeys: [String], state: String, k: Int = 0) -> GlyphForm {
        let n = formKeys.filter { $0.hasPrefix("progress-") && !$0.hasSuffix("-marked") }.count
        for word in states[state] ?? ["regular"] {
            switch word {
            case "regular": return .regular
            case "marked" where formKeys.contains("marked"): return .marked
            case "solid" where formKeys.contains("solid"): return .solid
            case "progress-n" where n > 0: return GlyphForm(.progress(n))
            case "progress-k" where n > 0: return GlyphForm(.progress(max(0, min(k, n))))
            default: continue
            }
        }
        return .regular
    }
}
```

`GlyphCatalog.swift` (the image cache is added in Task 7):

```swift
import Foundation
import os

/// `pbbls-glyphs.json`, loaded once (spec §2): off the main thread by
/// `prewarm()` at launch, or synchronously under the lock by the first read
/// that comes before it.
final class GlyphCatalog: @unchecked Sendable {
    static let shared = GlyphCatalog(bundle: .main)
    static let resource = "pbbls-glyphs"
    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "glyphs")

    private let bundle: Bundle
    private let lock = NSLock()
    private var file: GlyphFile?

    init(bundle: Bundle) {
        self.bundle = bundle
    }

    /// Loads the data in the background so the first glyph on screen does not wait for it.
    func prewarm() {
        DispatchQueue.global(qos: .userInitiated).async { _ = self.loaded() }
    }

    var states: [String: [String]] { loaded().states }

    func glyph(_ glyph: PbblsGlyph) -> GlyphData {
        guard let data = loaded().glyphs[glyph.rawValue] else {
            Self.logger.error("no data for glyph \(glyph.rawValue, privacy: .public)")
            return .empty
        }
        return data
    }

    private func loaded() -> GlyphFile {
        lock.lock()
        defer { lock.unlock() }
        if let file { return file }
        let read = Self.read(bundle)
        file = read
        return read
    }

    static func read(_ bundle: Bundle) -> GlyphFile {
        guard let url = bundle.url(forResource: resource, withExtension: "json") else {
            logger.error("\(resource, privacy: .public).json is not in the bundle")
            return .empty
        }
        do {
            return try JSONDecoder().decode(GlyphFile.self, from: Data(contentsOf: url))
        } catch {
            logger.error("\(resource, privacy: .public).json did not decode: \(error.localizedDescription, privacy: .public)")
            return .empty
        }
    }
}
```

(Match the subsystem string other `Logger`s in the app use: `grep -rn 'Logger(subsystem' apps/ios/Pebbles | head -3`.)

`PbblsGlyph+Forms.swift`:

```swift
extension PbblsGlyph {
    /// The forms this glyph has, in the generator's order (spec D3).
    var forms: [GlyphForm] {
        GlyphCatalog.shared.glyph(self).formKeys.compactMap(GlyphForm.init(key:))
    }

    /// The form a state shows on this glyph (spec D6): the state's first form
    /// the glyph has. `k` is the caller's progress for `.progress`.
    func form(for state: PbblsGlyphState, k: Int = 0) -> GlyphForm {
        GlyphRenderer.resolve(states: GlyphCatalog.shared.states, formKeys: GlyphCatalog.shared.glyph(self).formKeys,
                              state: state.rawValue, k: k)
    }
}
```

- [ ] **Step 4: Run** the suite — PASS. A failure here is a porting bug: fix the Swift until it says what the generator says; never edit the contract.

- [ ] **Step 5: Commit** `feat(ios): work out glyph forms and state tokens as the generator does`.

---

### Task 7: Drawing, the view, and launch cost

**Files:**
- Modify: `apps/ios/Pebbles/Theme/Glyphs/GlyphRenderer.swift`, `GlyphCatalog.swift`, `apps/ios/Pebbles/PebblesApp.swift`
- Create: `apps/ios/Pebbles/Theme/Glyphs/PbblsGlyphView.swift`
- Test: `apps/ios/PebblesTests/Theme/Glyphs/GlyphDrawingTests.swift`

- [ ] **Step 1: Failing test** — `GlyphDrawingTests.swift`:

```swift
import SwiftUI
import Testing
import UIKit
@testable import Pebbles

@Suite("Glyph drawing")
struct GlyphDrawingTests {
    /// The share of pixels with any ink.
    static func ink(_ image: UIImage) -> Double {
        guard let cg = image.cgImage else { return 0 }
        let w = cg.width, h = cg.height
        var bytes = [UInt8](repeating: 0, count: w * h * 4)
        let ctx = CGContext(data: &bytes, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
                            space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
        ctx?.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        let inked = stride(from: 3, to: bytes.count, by: 4).filter { bytes[$0] > 0 }.count
        return Double(inked) / Double(w * h)
    }

    @Test func drawsATemplateImageAtThreeTimesTheSide() {
        let image = GlyphCatalog.shared.image(.home, form: .regular, side: 24)
        #expect(image.renderingMode == .alwaysTemplate)
        #expect(image.size == CGSize(width: 24, height: 24))
        #expect(image.scale == 3)
    }

    @Test func aSolidCarriesMoreInkThanItsLine() {
        let line = Self.ink(GlyphCatalog.shared.image(.pebbleRound, form: .regular, side: 48))
        let solid = Self.ink(GlyphCatalog.shared.image(.pebbleRound, form: .solid, side: 48))
        #expect(line > 0.05)
        #expect(solid > line * 1.5)
    }

    @Test func cachesOneImagePerGlyphFormAndSide() {
        let a = GlyphCatalog.shared.image(.stack, form: GlyphForm(.progress(2)), side: 28)
        let b = GlyphCatalog.shared.image(.stack, form: GlyphForm(.progress(2)), side: 28)
        #expect(a === b)
        #expect(a !== GlyphCatalog.shared.image(.stack, form: GlyphForm(.progress(3)), side: 28))
    }

    @Test func loadsTheWholeSetQuickly() {
        let clock = ContinuousClock()
        let elapsed = clock.measure { _ = GlyphCatalog(bundle: .main).glyph(.home) }
        print("pbbls-glyphs.json loaded in \(elapsed)")
        #expect(elapsed < .seconds(1))
    }
}
```

- [ ] **Step 2: Run** `-only-testing:PebblesTests/GlyphDrawingTests` — fails to compile.

- [ ] **Step 3: Implement.** Add to `GlyphRenderer`:

```swift
import UIKit

extension GlyphRenderer {
    /// Draws paths on the 24-unit square, scaled to `side` points at 3×, as a
    /// template image: fill then stroke per path, in order, as SVG paints them,
    /// with the 1.75 stroke, round caps and joins.
    static func draw(_ paths: [RenderedPathData], side: CGFloat) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 3
        format.opaque = false
        let image = UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: format).image { context in
            let cg = context.cgContext
            cg.scaleBy(x: side / 24, y: side / 24)
            cg.setFillColor(UIColor.black.cgColor)
            cg.setStrokeColor(UIColor.black.cgColor)
            cg.setLineWidth(1.75)
            cg.setLineCap(.round)
            cg.setLineJoin(.round)
            for path in paths {
                let shape = GlyphPathParser.cgPath(path.d)
                if path.fill {
                    cg.addPath(shape)
                    cg.fillPath(using: path.evenOdd ? .evenOdd : .winding)
                }
                if path.stroke {
                    cg.addPath(shape)
                    cg.strokePath()
                }
            }
        }
        return image.withRenderingMode(.alwaysTemplate)
    }
}
```

Add to `GlyphCatalog` (import `UIKit`):

```swift
    private let images = NSCache<NSString, UIImage>()

    /// A form drawn at `side` points (rounded up to a whole point), cached.
    func image(_ glyph: PbblsGlyph, form: GlyphForm, side: CGFloat) -> UIImage {
        let points = max(1, side.rounded(.up))
        let key = "\(glyph.rawValue)|\(form.key)|\(Int(points))" as NSString
        if let cached = images.object(forKey: key) { return cached }
        let image = GlyphRenderer.draw(GlyphRenderer.render(self.glyph(glyph), form), side: points)
        images.setObject(image, forKey: key)
        return image
    }
```

`PbblsGlyphView.swift`:

```swift
import SwiftUI

/// A `PbblsGlyph` in one of its forms (spec §2), drawn as a template image on
/// a square side that follows Dynamic Type through `@ScaledMetric` (24 pt at
/// the default size, relative to `.body` unless told otherwise). It takes the
/// ambient foreground style unless `style` is given, and is hidden from
/// accessibility unless `label` is. A form change animates when the caller
/// animates it: a crossfade with a slight scale, none under Reduce Motion
/// (spec D9). Being an `Image`, it also draws in tab bars and swipe actions.
struct PbblsGlyphView: View {
    private let glyph: PbblsGlyph
    private let form: GlyphForm
    private let style: AnyShapeStyle?
    private let label: LocalizedStringResource?
    @ScaledMetric private var side: CGFloat
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(_ glyph: PbblsGlyph, size: CGFloat = 24, form: GlyphForm = .regular, relativeTo textStyle: Font.TextStyle = .body,
         style: AnyShapeStyle? = nil, label: LocalizedStringResource? = nil) {
        self.glyph = glyph
        self.form = form
        self.style = style
        self.label = label
        _side = ScaledMetric(wrappedValue: size, relativeTo: textStyle)
    }

    /// `PbblsGlyphView(.home, .tile)`: at a named size.
    init(_ glyph: PbblsGlyph, _ size: PbblsIconSize, form: GlyphForm = .regular, relativeTo textStyle: Font.TextStyle = .body,
         style: AnyShapeStyle? = nil, label: LocalizedStringResource? = nil) {
        self.init(glyph, size: size.rawValue, form: form, relativeTo: textStyle, style: style, label: label)
    }

    var body: some View {
        let image = Image(uiImage: GlyphCatalog.shared.image(glyph, form: form, side: side))
            .renderingMode(.template)
            .resizable()
            .scaledToFit()
            .frame(width: side, height: side)
            .id(form)
            .transition(reduceMotion ? .identity : .opacity.combined(with: .scale(scale: 0.9)))
        labelled(styled(image))
    }

    @ViewBuilder
    private func styled<Content: View>(_ content: Content) -> some View {
        if let style {
            content.foregroundStyle(style)
        } else {
            content
        }
    }

    @ViewBuilder
    private func labelled<Content: View>(_ content: Content) -> some View {
        if let label {
            content.accessibilityLabel(Text(label))
        } else {
            content.accessibilityHidden(true)
        }
    }
}
```

In `PebblesApp.init` (add an `init()` if it has none, keeping whatever it already does), call `GlyphCatalog.shared.prewarm()` first.

- [ ] **Step 4: Run** the suite — PASS. Note the printed load time (the test log, `grep 'loaded in'`) for the PR body. If it is above 200 ms on the simulator, stop and report: the plan's single-resource decision needs revisiting before call sites move.

- [ ] **Step 5: Commit** `feat(ios): draw glyph forms as template images with PbblsGlyphView`.

---

### Task 8: Move every call site

**Files:** every Swift file under `apps/ios/Pebbles` and `apps/ios/PebblesTests` that names `PbblsIcon` or `PbblsIconImage`, except `Pebbles/Theme/PbblsIcon.swift`, `Pebbles/Theme/Icon+Pebbles.swift`, `PebblesTests/Theme/PbblsIconTests.swift`, `PebblesTests/Theme/PbblsIconSizeTests.swift` and `PebblesTests/References/IconSheetReferences.swift` (part 7 removes those with the catalog). `Pebbles/Theme/Debug/IconLabView.swift` is Task 9.

- [ ] **Step 1: Rename mechanically.** From `apps/ios`:

```bash
grep -rlE 'PbblsIcon(Image)?\b' --include='*.swift' Pebbles PebblesTests \
  | grep -vE 'Theme/PbblsIcon\.swift|Theme/Icon\+Pebbles\.swift|Theme/Debug/IconLabView\.swift|PebblesTests/Theme/PbblsIcon(Size)?Tests\.swift|References/IconSheetReferences\.swift' \
  | xargs perl -pi -e 's/\bPbblsIconImage\(/PbblsGlyphView(/g; s/\bPbblsIcon\b/PbblsGlyph/g'
```

`PbblsGlyphView` takes the same argument labels as `PbblsIconImage` (`size:`, a `PbblsIconSize`, `relativeTo:`, `style:`, `label:`), so the rename compiles wherever the case is a base glyph.

- [ ] **Step 2: Build** (`xcodebuild build …`) and fix what is left by hand. Every remaining error is a twin or a row part 7 removes:

| Site | Today | After |
|---|---|---|
| `Features/Tabs/PebblesTabView.swift` `tabImage` | `tab.icon.activeTwin ?? tab.icon`, `Image(icon.assetName)` | `let form = nav.selected == tab ? tab.icon.form(for: .selected) : .regular` and `Image(uiImage: GlyphCatalog.shared.image(tab.icon, form: form, side: 24))`; update its doc comment: the selected tab shows the `selected` token's form (home, people and collection their solid, path its last step, you its mark) |
| `Features/Home/Checklist/HomeChecklistView.swift:56` | `.pebbleRoundLit1` | `PbblsGlyphView(.pebbleRound, .row, form: PbblsGlyph.pebbleRound.form(for: .selected), style: …)` |
| `Features/Onboarding/Steps/ReasonsStep.swift:52` | `on ? .pebbleRoundLit1 : .pebbleRound` | `.pebbleRound, …, form: on ? PbblsGlyph.pebbleRound.form(for: .selected) : .regular` |
| `Features/Onboarding/Steps/PromiseStep.swift:90` | `.pebbleRoundLit1` | `.pebbleRound`, `form: PbblsGlyph.pebbleRound.form(for: .selected)` |
| `Features/Onboarding/Steps/ProgressStones.swift:27` | `lit ? .pebbleRoundLit1 : .pebbleRound` | `.pebbleRound`, `form: lit ? PbblsGlyph.pebbleRound.form(for: .selected) : .regular` |
| `Features/Lab/Components/LogTimeline.swift` `icon` | `.inProgress: .pebbleRoundLit1` | `icon` returns `.pebbleRound` for both pebble modes; add `private var iconForm: GlyphForm { mode == .inProgress ? PbblsGlyph.pebbleRound.form(for: .selected) : .regular }` and pass `form: iconForm` where `icon` is drawn |
| `Features/Profile/Models/Achievement+Localized.swift` `familySymbol` | `.stackLit3`, `.pebbleGlyphDotted` | `familySymbol` returns `.stack` and `.pebbleGlyph`; add `var familyForm: GlyphForm` beside it: `first_five` → `PbblsGlyph.stack.form(for: .done)` (its last step), `first_glyph` and `glyph_count` → `.dotted`, everything else `.regular`; `YouAchievementsSection.swift:89` passes `form: record.familyForm` |
| `Features/Profile/Header/YouHeader.swift:40`, `Features/Glyph/Views/GlyphsListView.swift:115`, `PebblesTests/References/SurfaceKitReferences.swift:60` | `.pebbleGlyphDotted` | `.pebbleGlyph, …, form: .dotted` |
| `Features/Glyph/Views/GlyphView.swift:64` | `mark(.pebbleGlyphDotted, color)` | give `mark` a `form: GlyphForm = .regular` parameter passed through to `PbblsGlyphView`; call `mark(.pebbleGlyph, color, form: .dotted)` |

Read each site before editing; if one draws something other than what the table assumes, report it rather than guess. No call site may name a case whose raw value contains a `.` (a twin): `PbblsGlyph` has none, so the compiler enforces it.

- [ ] **Step 3: Tests that named twins.** `PebblesTests/AchievementFamilyIconTests.swift` and `PebblesTests/Features/Home/AchievementFamilyCopyTests.swift` now expect `.stack` and `.pebbleGlyph`; add `#expect` lines for `familyForm` (`first_five` → `.init(.progress(3))`, `first_glyph` → `.dotted`, `first_moment` → `.regular`).

- [ ] **Step 4: Prove nothing is left.** From `apps/ios`:

```bash
grep -rnE '\bPbblsIcon\b|PbblsIconImage|activeTwin|\.lit\(|assetName' --include='*.swift' Pebbles PebblesTests \
  | grep -vE 'Theme/PbblsIcon\.swift|Theme/Icon\+Pebbles\.swift|Theme/Debug/IconLabView\.swift|PbblsIcon(Size)?Tests\.swift|IconSheetReferences\.swift|PebblesColors\.swift'
```

Expected: no output. Then `swiftlint`, the full unit suite except references: `xcodebuild test … -skip-testing:PebblesTests/References` — PASS (references are Task 10, on CI).

- [ ] **Step 5: Look at it.** Build and run on the simulator (`xcrun simctl` install/launch as in the iOS device-deploy memory, or Xcode): sign-in screen, then with the maintainer's throwaway account if one is open — the tab bar (each tab selected once), Collections' swipe-to-delete (the trash icon must show in the swipe action), onboarding's promise and reasons steps if reachable. Screenshot the tab bar with each tab selected. If the swipe action shows no icon, stop and report.

- [ ] **Step 6: Commit** `chore(ios): draw every glyph through PbblsGlyphView`.

---

### Task 9: The icon lab shows forms

**Files:**
- Modify: `apps/ios/Pebbles/Theme/Debug/IconLabView.swift`

- [ ] **Step 1:** Move the lab to `PbblsGlyph`: the grid lists `PbblsGlyph.allCases` (filtered by the query) drawn with `PbblsGlyphView(glyph, size: side, form: shownForm(glyph))`; a segmented picker under the size buttons picks what the grid shows (`Regular`, `Selected`, `Done`, `Marked` — verbatim text): regular is `.regular`, the other three resolve the `selected` and `done` tokens and `.marked` falls back to regular when the glyph has no mark (`glyph.forms.contains(.marked) ? .marked : .regular`). Tapping a tile (`picked`) shows every form the glyph has (`glyph.forms`) in a row, each captioned with `form.key`, instead of its twins. Keep the verbatim-text rule (`Text(verbatim:)`), the on-primary toggle and the size controls as they are. Update the doc comment: forms, not twins.

- [ ] **Step 2:** Build, open Settings → Developer → Icons on the simulator, screenshot the grid in `Selected` and one picked glyph (`home`, which has four forms). `swiftlint`.

- [ ] **Step 3: Commit** `chore(ios): the icon lab lays out each glyph's forms`.

---

### Task 10: Per-form references

**Files:**
- Create: `apps/ios/PebblesTests/References/GlyphFormSheetReferences.swift`

- [ ] **Step 1:** Model it on `IconSheetReferences` (same paging: 12 per row, 20 rows per page, rows built by hand because `LazyVGrid` lays out nothing under `ImageRenderer`, `Spacing.xs`, surface paper, `ReferenceConfig.phoneWidth`, both schemes). One sheet family per form group, each glyph in that group's form at 24 pt:

```swift
extension References {
    /// The glyph set in its forms (spec §4): regular, solid, marked, and one
    /// page of the rest (each stepped glyph done, each parted glyph's first
    /// parts form, each dotted drawing), at 24 pt, pages of at most 240.
    @Suite("Glyph form sheet references") @MainActor
    struct GlyphFormSheetReferences {
        nonisolated static let perRow = 12
        nonisolated static let rowsPerPage = 20
        nonisolated static let perPage = perRow * rowsPerPage

        /// Each group's glyphs with the form they are drawn in, in manifest order.
        nonisolated static func group(_ name: String) -> [(PbblsGlyph, GlyphForm)] {
            PbblsGlyph.allCases.compactMap { glyph in
                let forms = glyph.forms
                switch name {
                case "regular": return (glyph, .regular)
                case "solid": return forms.contains(.solid) ? (glyph, .solid) : nil
                case "marked": return forms.contains(.marked) ? (glyph, .marked) : nil
                default:
                    if let done = forms.last(where: { if case .progress = $0.fill { !$0.marked } else { false } }) { return (glyph, done) }
                    if let part = forms.first(where: { if case .parts = $0.fill { true } else { false } }) { return (glyph, part) }
                    if forms.contains(.dotted) { return (glyph, .dotted) }
                    return nil
                }
            }
        }

        nonisolated static let groups = ["regular", "solid", "marked", "other"]
        nonisolated static let pages: [(String, Int)] = groups.flatMap { g in
            (0..<((group(g).count + perPage - 1) / perPage)).map { (g, $0) }
        }
        // sheet(group:page:) and the @Test over ReferenceScheme.allCases × pages, named "\(group)-\(page + 1)",
        // exactly as IconSheetReferences draws and asserts its pages, with PbblsGlyphView(glyph, form: form).
    }
}
```

Write the sheet builder and the `@Test` in full, copying `IconSheetReferences`' body and swapping in the group's pairs (`ForEach(row, id: \.0) { PbblsGlyphView($0.0, form: $0.1) }`). If Swift Testing refuses a tuple argument list, pass `pages` as an array of a small `Hashable` `struct Page { let group: String; let index: Int }` with a `CustomTestStringConvertible` description.

Expected counts with today's set (check with a quick unit test or a print, not by hand): regular 950 (4 pages), solid 598 (3), marked 521 (3), other about 100 (1): 11 pages × 2 schemes = 22 references.

- [ ] **Step 2:** It compiles and runs locally (`-only-testing:PebblesTests/GlyphFormSheetReferences`): every page fails with "missing reference", which is expected; open one `-actual` PNG it wrote next to the suite and look at it (solids carved, marks lit, progress full). Delete the `-actual` files; never commit a locally rendered reference.

- [ ] **Step 3: Commit** `test(ios): reference pages of every glyph form`.

---

### Task 11: Docs and the PR

**Files:**
- Modify: `packages/icons/CLAUDE.md`, this plan (Lessons learned)

- [ ] **Step 1:** In `packages/icons/CLAUDE.md`, "Never hand-edit a generated file": add `apps/ios/Pebbles/Resources/pbbls-glyphs.json`, `apps/ios/Pebbles/Theme/PbblsGlyph.swift`, `apps/ios/Pebbles/Theme/PbblsGlyphState.swift` and `packages/icons/glyphs/forms-contract.json`; mention `fixtures/path-contract.json` as hand-written ("add a case when a parser bug is found, never edit an expectation"). Keep it to those lines.

- [ ] **Step 2:** Package checks, `swiftlint`, the iOS unit suite (references skipped) — all green. Push the branch.

- [ ] **Step 3: PR** (REST or `gh pr create`), base `main`:
  - Title: `chore(ios): draw glyphs from their forms`
  - Body: `Part of #1607` (the issue stays open for Android and removal), what changed (data resource, renderer, view, call sites, references), the tab bar's new selected look (solid for home, people and collections, path's last step, you's mark) to check on device, the measured load time from Task 7, the two contracts, and `Data safety: no change`. End with `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.
  - Labels `chore`, `ios`, `ui`, `no-lab-note`; milestone `M66 · Brand icons`, as on parts 3–3c (a `feat` label would require a Lab Note; the one visible change, the tab bar's selected look, is covered by the maintainer's device check).
- [ ] **Step 4:** Add `rebaseline-ios-screenshots` alone (its own `gh pr edit`), approve the held runs on the bot's commit (`gh run list --branch chore/1607-icon-forms-ios --json databaseId,conclusion` → `conclusion == "action_required"` → `gh api --method POST repos/alexisbohns/pbbls/actions/runs/<id>/approve`), wait for green. Read a few re-rendered references from the bot's commit (one form page, one screen with icons) before calling it done.

## Lessons learned

(Filled in after the PR merges.)
