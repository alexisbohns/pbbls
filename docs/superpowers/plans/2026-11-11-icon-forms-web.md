# Icon forms on web and admin (#1607, part 4) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every `@pbbls/icons` component draws its glyph in any of its forms through a `form` prop, state forms come from the shared tokens through `glyphForm(Glyph, state, k)`, and web and admin no longer name a twin — the web side of what iOS (#1616) and Android (part 6) do.

**Architecture:** The generator's form engine (`renderForm`, `formsOf`, `resolveState` in `packages/icons/scripts/lib`) is already pure TypeScript, so web needs no port: the browser runs the generator's own code. A small hand-written runtime (`packages/icons/runtime/`) turns a form into SVG paths (`GlyphSvg`), resolves a state (`glyphForm`) and carries an opt-in fade for in-place form changes (`glyph.css`). The React emitter writes each base component from `glyphs.json` with its glyph data inlined (tree-shaken per import) and its form keys attached (`Home.forms`); twin components stay, static, until part 7. Call sites keep their component names; only the twins move to forms.

**Tech Stack:** TypeScript generator and runtime (Vitest, `react-dom/server` for markup tests), Next.js 16 App Router (web, admin), React 19, Tailwind 4.

**Spec:** `docs/superpowers/specs/2026-11-08-icon-forms-design.md` (D3, D6, D9, §2, §3, §4). Native mirrors: `docs/superpowers/plans/2026-11-10-icon-forms-ios.md`, `2026-11-11-icon-forms-android.md`.

**Branch:** `chore/1607-icon-forms-web` from updated `main` (after #1616, which carries the forms contract). Web deploys previews from this branch on purpose. Never touch the untracked `apps/web/AGENTS.md`; read `node_modules/next/dist/docs/` before any Next-specific change (root `AGENTS.md`). Commits are conventional and end with a blank line and `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Never hand-edit a generated file: change the emitter, then `npm run generate --workspace=@pbbls/icons`.

**Checks:**
- Package: `npm run build --workspace=@pbbls/icons && npm run test --workspace=@pbbls/icons && npm run check --workspace=@pbbls/icons && npm run lint:replaces --workspace=@pbbls/icons`.
- Web: `npm run lint --workspace=@pbbls/web && npm run test --workspace=@pbbls/web && npm run build --workspace=@pbbls/web` (use the workspace names in `apps/web/package.json` / `apps/admin/package.json` if they differ). Admin the same.

## Decisions taken in this plan

- **No web port of the engine.** The components call the generator's `renderForm` (pure: its only imports are types and `partCombinations`). `resolveState` moves out of `states.ts` (which reads files) into a pure module. The forms contract still runs on web, as a guard on the runtime's fallback and on the generated data. The path-parser contract has no web side: the browser parses `d` itself, and the generator's `segmentsOf` is the TypeScript reference the natives are held to.
- **The `form` prop is the engine's `Form`** (`{ fill: { kind: "solid" }, marked?: true }`), not the spec's sugar (`{ fill: "solid" }`): one type from the generator to the page, no translation layer. The runtime exports constants for the common ones (`SOLID`, `MARKED`, `DOTTED`, `REGULAR`, `progress(k)`).
- **A missing form draws regular** on web as on iOS and Android (`renderForm` throws, a component must not).
- **Animation is opt-in** (spec D9, "when the caller asks for it"): `animate` adds a class whose filled paths fade and scale in through `@starting-style`, inside `prefers-reduced-motion: no-preference`. Without it nothing moves, so a page load never fades its solids in.
- **The nav shows the selected form.** `lib/config/navigation.ts` said the nav marks the current tab "never by an `active` twin (spec D11)"; the icon forms spec (§3) supersedes that for forms: the current tab draws its `selected` form (People its solid with the mark, as iOS after the device pass and Android), on the bar, the rail and the desktop sidebar. The colour and `aria-current` stay.
- **Twins stay emitted, unused**, until part 7 deletes them with the other surfaces' twins. After this part no web or admin file outside `packages/icons` imports a twin (`*Active`, `*Lit<N>`) or a `pebble-glyph-dotted*` row.
- **The design page draws from `glyphs.json` through `GlyphSvg`**, so it stops shipping its own engine and stops pulling every component through `import * as PbblsIcons`.
- **The app-facing helpers live on the `@pbbls/icons/runtime` subpath** (`GlyphSvg`, `glyphForm`, `formsOf`, `drawForm` and the form constants; added in Task 6), so a call site imports them without reaching into the package's directories.

## File map

| File | Status | Responsibility |
|---|---|---|
| `packages/icons/scripts/lib/resolve-state.ts` | create (move) | `StateTable`, `resolveState`: pure |
| `packages/icons/scripts/lib/states.ts` | modify | re-export them; keep `parseStates`, `loadStates` |
| `packages/icons/scripts/lib/forms.ts` | modify | `GlyphForms`: the fields the engine reads; `shapeOf`/`formsOf`/`renderForm` take it |
| `packages/icons/runtime/forms.ts` | create | `drawForm`, `glyphForm`, form constants |
| `packages/icons/runtime/GlyphSvg.tsx` | create | the SVG element every component renders |
| `packages/icons/runtime/glyph.css` | create | the opt-in fade |
| `packages/icons/runtime/*.test.ts(x)` | create | runtime against the forms contract, markup |
| `packages/icons/scripts/lib/emit-react.ts` | modify | base components from glyphs, `states.ts`, types |
| `packages/icons/react/` | generated | components, `types.ts`, `states.ts`, `index.ts` |
| `packages/icons/package.json`, `vitest.config.ts`, `tsconfig.json` | modify | `./glyph.css` export; runtime tests and sources included |
| `apps/web/app/globals.css`, `apps/admin/app/globals.css` | modify | import `@pbbls/icons/glyph.css` |
| `apps/web/lib/icons/pbbls-icon.ts` | modify | `PbblsGlyphComponent`; doc: forms, not twins |
| `apps/web/lib/config/navigation.ts` (+ test) | modify | `selectedForm(item)` |
| web and admin call sites | modify | twins → forms (table in Task 5) |
| `apps/web/lib/design/*`, `components/sandbox/design/*` | modify | draw through `GlyphSvg`, drop the hand engine and the namespace import |
| `packages/icons/CLAUDE.md` | modify | the runtime and the CSS import |

---

### Task 1: The engine, browser-safe

**Files:**
- Create: `packages/icons/scripts/lib/resolve-state.ts`
- Modify: `packages/icons/scripts/lib/states.ts`, `packages/icons/scripts/lib/forms.ts`

- [ ] **Step 1: Move `resolveState`.** Cut `StateTable` and `resolveState` from `states.ts` into `resolve-state.ts` unchanged (it imports only `type { Form } from "./forms"`), and in `states.ts` add `export { resolveState, type StateTable } from "./resolve-state"` plus `import type { StateTable } from "./resolve-state"` for its own use. No caller changes. `npm run build && npm run test --workspace=@pbbls/icons` — PASS. Commit `chore(ui): move resolveState out of the module that reads files`.

- [ ] **Step 2: Narrow what the engine reads.** In `forms.ts` add:

```ts
/** What the form engine reads of a glyph: `GlyphData` without its manifest prose, as the components inline it. */
export type GlyphForms = Pick<GlyphData, "name" | "paths" | "parts" | "steps" | "carved" | "dotted" | "overrides">
```

and change the parameter types of `shapeOf`, `formsOf` and `renderForm` from `GlyphData` to `GlyphForms` (a `GlyphData` still fits). `npm run build && npm run test` — PASS, `npm run check` — nothing stale. Commit `chore(ui): the form engine reads only a glyph's drawing`.

---

### Task 2: The runtime

**Files:**
- Create: `packages/icons/runtime/forms.ts`, `packages/icons/runtime/GlyphSvg.tsx`, `packages/icons/runtime/glyph.css`
- Test: `packages/icons/runtime/forms.test.ts`, `packages/icons/runtime/GlyphSvg.test.tsx`
- Modify: `packages/icons/vitest.config.ts` (`include: ["scripts/**/*.test.ts", "runtime/**/*.test.{ts,tsx}"]`), `packages/icons/tsconfig.json` (`include` adds `"runtime/**/*.ts", "runtime/**/*.tsx"`), `packages/icons/package.json` (`"./glyph.css": "./runtime/glyph.css"` in `exports`)

- [ ] **Step 1: Failing tests.** `runtime/forms.test.ts` reads the real data and the contract:

```ts
import { readFileSync } from "node:fs"
import { join } from "node:path"
import { describe, expect, it } from "vitest"
import type { GlyphData, RenderedPath } from "../scripts/lib/forms"
import { formsOf, parseFormKey } from "../scripts/lib/forms"
import { DOTTED, MARKED, REGULAR, SOLID, drawForm, formKey, progress, resolveWith } from "./forms"

const ROOT = join(__dirname, "..")
const data = JSON.parse(readFileSync(join(ROOT, "glyphs/glyphs.json"), "utf8")) as { states: Record<string, string[]>; glyphs: GlyphData[] }
const contract = JSON.parse(readFileSync(join(ROOT, "glyphs/forms-contract.json"), "utf8")) as {
  glyphs: Record<string, { forms: string[]; rendered: Record<string, RenderedPath[]>; states: Record<string, string> }>
}
const byName = new Map(data.glyphs.map((g) => [g.name, g]))

describe("the runtime against the forms contract", () => {
  for (const [name, entry] of Object.entries(contract.glyphs)) {
    const glyph = byName.get(name)
    it(`${name}: forms, renders and states`, () => {
      expect(glyph).toBeDefined()
      if (!glyph) return
      expect(formsOf(glyph)).toEqual(entry.forms)
      for (const [key, expected] of Object.entries(entry.rendered)) {
        const form = parseFormKey(key)
        expect(form, key).not.toBeNull()
        if (form) expect(drawForm(glyph, form), key).toEqual(expected)
      }
      for (const [spec, expected] of Object.entries(entry.states)) {
        const [state, k] = spec.split("@")
        // Compare keys: `progress@0` resolves to `progress-0`, a key no glyph lists but the contract records.
        expect(formKey(resolveWith(data.states, entry.forms, state ?? "", Number(k ?? 0))), spec).toBe(expected)
      }
    })
  }

  it("draws regular for a form the glyph does not have, instead of throwing", () => {
    const stack = byName.get("stack")
    if (!stack) throw new Error("stack missing")
    expect(drawForm(stack, SOLID)).toEqual(drawForm(stack, REGULAR))
    expect(drawForm(stack, progress(0))).toEqual(drawForm(stack, REGULAR))
  })

  it("names the common forms", () => {
    expect([REGULAR, MARKED, SOLID, DOTTED, progress(2)]).toEqual([
      { fill: { kind: "regular" } },
      { fill: { kind: "regular" }, marked: true },
      { fill: { kind: "solid" } },
      { fill: { kind: "dotted" } },
      { fill: { kind: "progress", k: 2 } },
    ])
  })
})
```

If `parseFormKey`'s name or return type differs, adapt to `forms.ts` as it stands.

`runtime/GlyphSvg.test.tsx`:

```tsx
import { renderToStaticMarkup } from "react-dom/server"
import { describe, expect, it } from "vitest"
import { GlyphSvg } from "./GlyphSvg"
import { MARKED, SOLID } from "./forms"

const glyph = {
  name: "dot",
  paths: [
    { d: "M4 12 C4 6 20 6 20 12 C20 18 4 18 4 12", role: "body" as const },
    { d: "M12 11 L12 13", role: "mark" as const },
  ],
  parts: [],
  steps: 0,
  carved: { solid: { d: "M4 12 L20 12 L12 4 Z", keep: [1], lit: [] } },
  dotted: null,
  overrides: {},
}

describe("GlyphSvg", () => {
  it("draws regular as strokes, hidden from assistive tech, 24 by default", () => {
    const html = renderToStaticMarkup(<GlyphSvg glyph={glyph} />)
    expect(html).toContain('width="24"')
    expect(html).toContain('aria-hidden="true"')
    expect(html).toContain('focusable="false"')
    expect(html).toContain('stroke="currentColor"')
    expect(html).not.toContain('fill="currentColor"')
  })

  it("draws a solid's carved fill even-odd and unstroked, then its kept lines", () => {
    const html = renderToStaticMarkup(<GlyphSvg glyph={glyph} form={SOLID} />)
    expect(html).toContain('<path d="M4 12 L20 12 L12 4 Z" fill="currentColor" fill-rule="evenodd" stroke="none"')
    expect(html).toContain('<path d="M12 11 L12 13"')
    expect(html).toContain('data-form="solid"')
  })

  it("labels when asked, and marks filled paths for the fade only when animated", () => {
    const html = renderToStaticMarkup(<GlyphSvg glyph={glyph} form={MARKED} aria-label="Dot" animate />)
    expect(html).toContain('role="img"')
    expect(html).not.toContain("aria-hidden")
    expect(html).toContain('class="pbbls-glyph-fill"')
    expect(renderToStaticMarkup(<GlyphSvg glyph={glyph} form={MARKED} />)).not.toContain("pbbls-glyph-fill")
  })
})
```

- [ ] **Step 2: Run** `npm run test --workspace=@pbbls/icons` — FAIL (no module).

- [ ] **Step 3: Implement** `runtime/forms.ts`:

```ts
/**
 * The form engine as the components use it (spec D3, D6): the generator's own
 * `renderForm` and `resolveState`, with a missing form drawing regular as it
 * does on iOS and Android. Browser-safe: nothing here reads a file.
 */
import { formKey, renderForm, type Form, type GlyphForms, type RenderedPath } from "../scripts/lib/forms"
import { resolveState, type StateTable } from "../scripts/lib/resolve-state"

export type { Form, GlyphForms, RenderedPath }

export const REGULAR: Form = { fill: { kind: "regular" } }
export const MARKED: Form = { fill: { kind: "regular" }, marked: true }
export const SOLID: Form = { fill: { kind: "solid" } }
export const DOTTED: Form = { fill: { kind: "dotted" } }
export const progress = (k: number): Form => ({ fill: { kind: "progress", k } })

/** The paths a form draws; a form the glyph does not have draws regular. */
export function drawForm(glyph: GlyphForms, form: Form): RenderedPath[] {
  try {
    return renderForm(glyph, form)
  } catch {
    return renderForm(glyph, REGULAR)
  }
}

/** The form `state` shows on a glyph with these form keys, against a state table; an unknown state shows regular. */
export function resolveWith(table: StateTable, forms: readonly string[], state: string, k = 0): Form {
  return table[state] ? resolveState(table, forms, state, k) : REGULAR
}

export { formKey }
```

`runtime/GlyphSvg.tsx`:

```tsx
import type { SVGProps } from "react"
import { REGULAR, drawForm, formKey, type Form, type GlyphForms } from "./forms"

export type GlyphSvgProps = Omit<SVGProps<SVGSVGElement>, "children"> & {
  /** Width and height, 24 by default; size by class where you can (`PBBLS_ICON_SIZE`). */
  size?: number | string
  /** The form to draw (spec D3), regular by default. */
  form?: Form
  /** Fade a newly filled path in (spec D9): for a form that changes in place. Nothing moves under reduced motion. */
  animate?: boolean
}

/**
 * One glyph in one form as an inline SVG: the DNA's 1.75 round stroke in
 * `currentColor`, filled paths in `currentColor` (even-odd and unstroked when
 * carved). Hidden from assistive tech unless labelled, `role="img"` when it is.
 * No hooks: it renders in a Server Component.
 */
export function GlyphSvg({ glyph, size = 24, form = REGULAR, animate = false, ...props }: GlyphSvgProps & { glyph: GlyphForms }) {
  const labelled = props["aria-label"] !== undefined || props["aria-labelledby"] !== undefined
  const paths = drawForm(glyph, form)
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.75}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden={labelled ? undefined : true}
      role={labelled ? "img" : undefined}
      focusable="false"
      data-form={formKey(form)}
      {...props}
    >
      {paths.map((p, i) => (
        <path
          // A path that starts filling is a new element, so the fade starts from its first frame.
          key={`${i}${p.fill ? "f" : "s"}${p.d.length}`}
          d={p.d}
          fill={p.fill ? "currentColor" : undefined}
          fillRule={p.evenOdd ? "evenodd" : undefined}
          stroke={p.stroke ? undefined : "none"}
          className={animate && p.fill ? "pbbls-glyph-fill" : undefined}
        />
      ))}
    </svg>
  )
}
```

`runtime/glyph.css`:

```css
/* @pbbls/icons: a form change that fills a path fades and scales it in (spec D9), only where the caller passes `animate`. */
@media (prefers-reduced-motion: no-preference) {
  .pbbls-glyph-fill {
    transform-box: fill-box;
    transform-origin: center;
    transition:
      opacity 220ms ease-out,
      scale 220ms ease-out;
  }

  @starting-style {
    .pbbls-glyph-fill {
      opacity: 0;
      scale: 0.9;
    }
  }
}
```

If the markup test's attribute order differs (React writes `fill-rule` after `fill`), loosen the assertion to `toContain` each attribute on the carved path, not the order.

- [ ] **Step 4: Run** — PASS. `npm run build --workspace=@pbbls/icons` — PASS (the runtime typechecks under the package's `jsx: react-jsx`).

- [ ] **Step 5: Commit** `feat(ui): draw a glyph's form as SVG on web`.

---

### Task 3: Components with forms

**Files:**
- Modify: `packages/icons/scripts/lib/emit-react.ts`, `packages/icons/scripts/lib/emit-react.test.ts`, `packages/icons/scripts/build.ts`
- Generated: `packages/icons/react/*`

- [ ] **Step 1: Failing tests** in `emit-react.test.ts` (keep the existing twin and index tests; the emitter now takes the glyphs and states beside the icons):

```ts
it("writes a base component from its glyph: data inlined, forms attached, drawn by GlyphSvg", () => {
  const files = reactFiles(FIXTURE_ICONS, [HOME_GLYPH], { rest: ["regular"], selected: ["solid", "regular"] })
  const home = files.get("packages/icons/react/Home.tsx") ?? ""
  expect(home).toContain('import { GlyphSvg } from "../runtime/GlyphSvg"')
  expect(home).toContain("export function Home(props: PbblsGlyphProps) {")
  expect(home).toContain("return <GlyphSvg glyph={GLYPH} {...props} />")
  expect(home).toContain(`Home.forms = ${JSON.stringify(formsOf(HOME_GLYPH))} as const`)
  expect(home).not.toMatch(/"meaning"|"tags"|"family"/)
})

it("keeps writing twins as static SVG until part 7", () => {
  const files = reactFiles(FIXTURE_ICONS, [HOME_GLYPH], { rest: ["regular"] })
  expect(files.get("packages/icons/react/HomeActive.tsx")).toContain("(the `active` twin)")
})

it("writes the state table and its type", () => {
  const states = reactFiles(FIXTURE_ICONS, [HOME_GLYPH], { rest: ["regular"], selected: ["solid", "regular"] }).get("packages/icons/react/states.ts") ?? ""
  expect(states).toContain('export const STATES = {"rest":["regular"],"selected":["solid","regular"]} as const')
  expect(states).toContain("export type PbblsGlyphState = keyof typeof STATES")
})
```

`HOME_GLYPH` is `buildGlyph(row("home", …), SVG(…with data-role…), {})` as in `emit-ios-glyphs.test.ts`; `FIXTURE_ICONS` is today's fixture (a base and its twin). Keep the existing render-from-a-temp-dir test and extend it: render `<Home form={SOLID} aria-label="Home" />` and expect the carved path and `role="img"`. The temp dir must now also hold `runtime/` (copy `packages/icons/runtime` beside the written `react/`).

- [ ] **Step 2: Run** — FAIL.

- [ ] **Step 3: Implement.** In `emit-react.ts`:
  - `TYPES` adds `PbblsGlyphProps` and `PbblsGlyphComponent`:

```ts
const TYPES = `${HEADER}import type { FunctionComponent, SVGProps } from "react"
import type { GlyphSvgProps } from "../runtime/GlyphSvg"

/** A twin's props (static drawing, until part 7): every SVG prop but \`children\`, plus \`size\`. */
export type PbblsIconProps = Omit<SVGProps<SVGSVGElement>, "children"> & { size?: number | string }

/** A glyph's props: a twin's, plus \`form\` and \`animate\`. */
export type PbblsGlyphProps = GlyphSvgProps

/** A glyph component: it draws any of its forms, listed in \`forms\` (form keys, generator order). */
export type PbblsGlyphComponent = FunctionComponent<PbblsGlyphProps> & { readonly forms: readonly string[] }
`
```

  - A base component (every `GlyphData`) is written by `glyphComponentFile(glyph)`:

```ts
export function glyphComponentFile(glyph: GlyphData): string {
  const name = pascalName(glyph.name)
  const meaning = glyph.meaning.replace(/\*\//g, "* /")
  const drawing: GlyphForms = { name: glyph.name, paths: glyph.paths, parts: glyph.parts, steps: glyph.steps, carved: glyph.carved, dotted: glyph.dotted, overrides: glyph.overrides }
  return `${HEADER}import type { GlyphForms } from "../runtime/forms"
import { GlyphSvg } from "../runtime/GlyphSvg"
import type { PbblsGlyphProps } from "./types"

const GLYPH: GlyphForms = ${JSON.stringify(drawing)}

/** \`${glyph.name}\` — ${meaning} */
export function ${name}(props: PbblsGlyphProps) {
  return <GlyphSvg glyph={GLYPH} {...props} />
}
${name}.forms = ${JSON.stringify(formsOf(glyph))} as const
`
}
```

  - Twins keep `componentFile(icon)` as today. `reactFiles(icons, glyphs, states)` writes every glyph's base file, every twin's file (icons with `variant !== null`), `types.ts`, `states.ts` (`export const STATES = ${JSON.stringify(states)} as const` + the type) and `index.ts` (bases and twins in manifest order as today, plus `export type { PbblsIconProps, PbblsGlyphProps, PbblsGlyphComponent } from "./types"`, `export { STATES, type PbblsGlyphState } from "./states"`, and `export { glyphForm, REGULAR, MARKED, SOLID, DOTTED, progress, type Form } from "../runtime/public"`).
  - `reactEmitter` becomes `reactEmitter(packageRoot)` whose `emit(icons)` calls `reactFiles(icons, loadGlyphsOnce(packageRoot), loadStates(packageRoot))`; update `build.ts`.

Create `packages/icons/runtime/public.ts` (hand-written, the one place the index re-exports from the runtime):

```ts
import { STATES, type PbblsGlyphState } from "../react/states"
import type { PbblsGlyphComponent } from "../react/types"
import { resolveWith, type Form } from "./forms"

export { DOTTED, MARKED, REGULAR, SOLID, progress, type Form } from "./forms"

/** The form a state shows on this glyph (spec D6): `glyphForm(Home, "selected")`; `k` is the caller's progress. */
export function glyphForm(glyph: Pick<PbblsGlyphComponent, "forms">, state: PbblsGlyphState, k = 0): Form {
  return resolveWith(STATES, glyph.forms, state, k)
}
```

Add a test to `runtime/forms.test.ts` once generated: `glyphForm(Stack, "done")` equals `progress(3)` and `glyphForm(People, "selected")` equals `SOLID`, importing from `../react`.

- [ ] **Step 4: Run, generate, measure.** Package checks — PASS. `npm run generate --workspace=@pbbls/icons`: every base `react/<Name>.tsx` changes, twins do not, `states.ts` is new. Then:
  - `du -sh packages/icons/react` before/after (note both).
  - `time npx tsc --noEmit -p apps/web` before (on `main`) and after: if typechecking web slows by more than half, report it before going on (the fallback is one `glyphs/<name>.json` per glyph imported by its component).
  - `npm run lint:replaces --workspace=@pbbls/icons` — PASS.

- [ ] **Step 5: Commit** `feat(ui): every icon component draws its glyph's forms`.

---

### Task 4: The CSS, the size helper and the nav

**Files:**
- Modify: `apps/web/app/globals.css`, `apps/admin/app/globals.css`, `apps/web/lib/icons/pbbls-icon.ts`, `apps/web/lib/config/navigation.ts`, `apps/web/components/layout/AppNavigation.tsx`, `apps/web/components/layout/Sidebar.tsx`
- Test: `apps/web/lib/config/navigation.test.ts` (create or extend)

- [ ] **Step 1: Failing test** — `navigation.test.ts`:

```ts
import { describe, expect, it } from "vitest"
import { MARKED, SOLID, glyphForm, progress } from "@pbbls/icons"
import { NAV_ITEMS, selectedForm } from "./navigation"

describe("the nav's selected forms (#1607 spec §3)", () => {
  it("People keeps its mark lit on the solid; the others follow the selected token", () => {
    const by = Object.fromEntries(NAV_ITEMS.map((item) => [item.key, selectedForm(item)]))
    expect(by.people).toEqual({ fill: { kind: "solid" }, marked: true })
    expect(by.home).toEqual(SOLID)
    expect(by.collections).toEqual(SOLID)
    expect(by.path).toEqual(progress(3))
    expect(by.you).toEqual(MARKED)
    for (const item of NAV_ITEMS) if (item.key !== "people") expect(selectedForm(item)).toEqual(glyphForm(item.icon, "selected"))
  })
})
```

The expected values are what iOS and Android draw (home/collection solid, path its last step, you its mark); if the data says otherwise for one of them, trust `glyphForm` and fix the expectation, noting it in the PR.

- [ ] **Step 2: Run** `npx vitest run lib/config/navigation.test.ts` (from `apps/web`) — FAIL.

- [ ] **Step 3: Implement.**
  - `pbbls-icon.ts`: add `export type { PbblsGlyphComponent } from "@pbbls/icons"`; keep `PbblsIconComponent` for props that take any icon; rewrite the doc line that says "a filled state is a twin" to: a state shows a form (`form={glyphForm(Glyph, state)}`), never a twin.
  - `navigation.ts`: `icon: PbblsGlyphComponent`; replace the "never by an active twin (spec D11)" comment with "the current tab draws its `selected` form (icon forms spec §3), on top of the colour and `aria-current`"; add:

```ts
/** The form the current tab draws: the `selected` token's, except People, which keeps its mark lit on the solid (iOS `AppTab.selectedForm`). */
export function selectedForm(item: NavItem): Form {
  return item.key === "people" ? { fill: { kind: "solid" }, marked: true } : glyphForm(item.icon, "selected")
}
```

  - `AppNavigation.tsx`: `<Icon className={PBBLS_ICON_SIZE.toolbar} form={current ? selectedForm(item) : undefined} />` (destructure the item, keep everything else). `Sidebar.tsx` the same with `isActive`. `useNavItems` must still pass `key` and `icon` through: check it.
  - Both `globals.css`: `@import "@pbbls/icons/glyph.css";` beside the other imports (Tailwind 4 resolves package CSS; if the build refuses the bare specifier, use the relative path to `node_modules/@pbbls/icons/runtime/glyph.css` and say so in the PR).

- [ ] **Step 4: Run** the test — PASS; web lint, test, build — PASS. Commit `feat(web): the current tab draws its selected form`.

---

### Task 5: Move every twin

**Files:** web and admin call sites of twins and of the `pebble-glyph-dotted` row. Read each before editing; report any that draws something the table does not expect.

| Site | Today | After |
|---|---|---|
| `apps/web/components/glyphs/MarketGlyphCard.tsx:4,41` | `HeartActive` vs `Heart` (favourite, `aria-pressed`) | `<Heart form={on ? glyphForm(Heart, "selected") : undefined} animate />` |
| `apps/web/components/lab/LogTimeline.tsx:13-17` | `in_progress` → `PebbleRoundLit1` | `PebbleRound` for both pebble modes, with `form={glyphForm(PebbleRound, "selected")}` for `in_progress` (iOS `LogTimeline.iconForm`) |
| `apps/admin/app/(authed)/logs/_components/LogSection.tsx:12,41` | `PebbleRoundLit1` for `in_progress` | the same as web |
| `apps/web/components/achievements/AchievementBadge.tsx:22,24`, `apps/web/components/profile/AchievementShelfBadge.tsx:15,17` | `first_glyph`, `glyph_count` → `PebbleGlyphDotted` (map duplicated) | `PebbleGlyph` with `DOTTED`; if `first_five` draws a stacked/lit drawing, `Stack` with `glyphForm(Stack, "done")`. Do not merge the two maps here (refactors need approval); mention the duplicate in the PR |
| `settings/GlyphHeader.tsx:56`, `public-profile/PublicProfileView.tsx:60`, `profile/ProfileBanner.tsx:32`, `you/YouHeader.tsx:39`, `profile/ShortcutsRow.tsx:18`, `foundations/StonesSpecimen.tsx:81` (all under `apps/web/components`) | `PebbleGlyphDotted` | `<PebbleGlyph form={DOTTED} … />` |
| `apps/web/lib/config/guides.ts:14` | a config entry holding `PebbleGlyphDotted` | `PebbleGlyph`, plus an optional `iconForm?: Form` on the entry type (`DOTTED` here) passed where the guide icon is drawn |
| `apps/web/lib/design/directory-pages.ts:1` | `PebbleGlyphDotted` | as guides: the glyph plus a form, or `PebbleGlyph` drawn dotted where it is rendered |
| `DrawDotted` sites (`GlyphDetail`, `MomentReadToolbar`, `SoulDetailHeader`, `YouGlyphs`, `CollectionDetailHeader`) | `DrawDotted` | unchanged: `draw-dotted` is its own drawing |

- [ ] **Step 1:** Edit the sites. Where a component takes a `PbblsIconComponent` prop and a caller now needs a form, add an optional `iconForm?: Form` prop passed to the icon, defaulting to none.

- [ ] **Step 2: Prove nothing is left.** From the repo root:

```bash
grep -rnE '\b[A-Z][A-Za-z0-9]*(Active|Lit[0-9])\b|PebbleGlyphDotted' --include='*.ts' --include='*.tsx' apps/web apps/admin \
  | grep -vE 'node_modules|\.next/' | grep -E '@pbbls/icons|<[A-Z][A-Za-z0-9]*(Active|Lit[0-9])|PebbleGlyphDotted'
```

Expected: no output (read what remains: a hit on a non-icon identifier is fine; say which in the commit body). Tests that named twins (`apps/web/lib/design/icon-catalog.test.ts`, `pbbls-icons.test.ts`) move with Task 6 or are updated here to assert forms instead.

- [ ] **Step 3:** Web and admin lint, test, build — PASS. Commit `chore(web): twins become forms`.

---

### Task 6: The design page draws through the runtime

**Files:**
- Modify: `apps/web/lib/design/glyph-forms.ts` (+ test), `apps/web/lib/design/icon-catalog.ts` (+ test), `apps/web/components/sandbox/design/GlyphFormView.tsx`, `pages/IconsPage.tsx`, `GlyphReviewDialog.tsx`, `pages/OverviewPage.tsx` (paths under `apps/web/components/sandbox/design/` unless found elsewhere)

- [ ] **Step 1:** In `glyph-forms.ts`, delete the hand engine (`formKey`, `glyphForms`, `formPaths`, `drawnForm`, `isLit`, `cutIndices` and their local `Form` type) in favour of `formsOf`/`drawForm`/`parseFormKey` from `@pbbls/icons` (export what the page needs from `runtime/public.ts` — `drawForm`, `formsOf`, `parseFormKey` — rather than deep-importing `scripts/lib`). Keep the review, category, search and round helpers and their tests; rewrite the drawing tests to assert through `drawForm` (same expectations: forms order, hidden steps, carved lit mark, solid).

- [ ] **Step 2:** `GlyphFormView` renders `<GlyphSvg glyph={glyph} form={form} animate className={className} />` (the page's crossfade becomes the runtime's fade). `icon-catalog.ts` maps names to `GlyphData` from `glyphs.json` (already loaded as `GLYPHS`) instead of `import * as PbblsIcons`; its consumers draw `GlyphSvg`. Twins disappear from the catalog with that (the page reviews forms now); update `icon-catalog.test.ts` to the forms it can still assert.

- [ ] **Step 3: Look at it.** `npm run dev --workspace=@pbbls/web` (if a dev server is already on :3000, use it), open `/sandbox/design/icons`: switch fill, mark and progress, open a tile. Screenshot one solid view and one opened tile to the scratchpad. Web lint, test, build — PASS. Commit `chore(web): the design page draws glyphs through the runtime`.

---

### Task 7: Look, measure, docs and the PR

- [ ] **Step 1: Look.** With the dev server, sign-in pages and any page reachable signed out; signed in only with the maintainer's account if they open one (never sign up a throwaway on production): the bar, the rail and the desktop sidebar with each tab current (selected forms), the market heart toggling (the fade, and none with "reduce motion" emulated in DevTools), the Lab timeline, the profile placeholder. Screenshots to the scratchpad.

- [ ] **Step 2: Measure.** `npm run build --workspace=@pbbls/web` on `main` and on the branch: note First Load JS for `/home`, `/path` and `/sandbox/design/icons` from both outputs for the PR body. If a product route grows by more than 30 KB, find which glyph data it pulls before opening the PR.

- [ ] **Step 3: Docs.** `packages/icons/CLAUDE.md`: under "Never hand-edit a generated file" note that `react/` now holds forms-aware components plus `states.ts`; add one line that `runtime/` is hand-written (the SVG element, `glyphForm`, the opt-in fade) and that each app imports `@pbbls/icons/glyph.css` once. Fix the "When `Icons` is red" web sentence if it still says "migrate the call site to `PbblsIcon`" for web.

- [ ] **Step 4: PR**, base `main`: title `chore(web): draw glyphs from their forms`; body `Part of #1607` (stays open for Android and removal); what changed (engine reused, runtime, components, nav, twins, design page), the nav's selected look to check, the bundle numbers, the typecheck timing, `Data safety: no change`, ending with `🤖 Generated with [Claude Code](https://claude.com/claude-code)`. Labels `chore`, `web`, `ui`, `no-lab-note`; milestone `M66 · Brand icons`. Wait for green (`web.yml`, `icons.yml`, Vercel previews) and open the web preview's nav before calling it done.

## Lessons learned

(Filled in after the PR merges.)
