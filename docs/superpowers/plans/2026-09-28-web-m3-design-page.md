# Web Material 3 theme + design page — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the web a Material 3 theme generated from Android's `ColorSchemes.kt`, scoped under `.m3`, and a `/sandbox/design` page that renders the app's foundations, primitives, feature components and four views under a Current ↔ M3 × light/dark × contrast switcher.

**Architecture:** Pure TS in `lib/theme/` parses the Kotlin schemes and renders CSS (Vitest-covered, including a drift gate); a `tsx` script writes `app/m3-theme.css`. `globals.css` gains a hand-written `.m3` bridge that points every shadcn token at an M3 role, plus M3 type utilities and shape radii. The design page drives classes on `<html>` from URL params, restores them on leave, and renders specimens from fixtures through a fake `DataContext`.

**Tech Stack:** Next.js 16 App Router, React 19, Tailwind CSS 4 (`@theme inline`, `@custom-variant`, `@utility`), shadcn base-nova on `@base-ui/react` (triggers take `render={<Button/>}`, not `asChild`), Vitest 5 (node env, `lib/**/*.test.ts` only), `tsx`.

**Spec:** `docs/superpowers/specs/2026-09-28-web-m3-design-page-design.md` · **Issue:** #986

---

## Ground rules for every task

- Work from the worktree root `/Users/alexis/code/pbbls/.claude/worktrees/saf-android-03-reauth`. Web commands: `npm run <script> --workspace=apps/web`.
- Next 16 has breaking changes. Before using a Next API you haven't used in this repo, read its page under `node_modules/next/dist/docs/01-app/`.
- Vitest only picks up `apps/web/lib/**/*.test.ts` and runs in `node` (no DOM). Put testable logic in `lib/`.
- Conventional commits, lowercase, no period, ending with the trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Never touch `localStorage`, `next-themes` or `ColorWorldProvider` from the design page.
- Do not refactor components to make them renderable. If a component can't render from props/fixtures, leave it out and list it in the PR body.

## Delivery: a two-part stack

| Part | Branch | PR title |
|---|---|---|
| 1 | `feat/986-web-m3-theme-tokens` (already created from `origin/main`; the spec is committed on it) | `feat(web): generate the material 3 theme from the android schemes` |
| 2 | `feat/986-web-m3-design-page` (stacked on Part 1) | `feat(web): add a design page to tune the material 3 theme` |

Part 1 must lint, test and build alone, and changes nothing visible: nothing applies `.m3` until Part 2.

---

# Part 1 — the theme layer

### File map (Part 1)

| File | Responsibility |
|---|---|
| Create `apps/web/lib/theme/m3-schemes.ts` | Parse `ColorSchemes.kt` → `M3Scheme[]`; render the generated CSS. Pure, no IO. |
| Create `apps/web/lib/theme/m3-schemes.test.ts` | Parser/renderer unit tests + the drift gate against the real files. |
| Create `apps/web/scripts/generate-m3-theme.ts` | IO only: read Kotlin, write `app/m3-theme.css`. |
| Create `apps/web/app/m3-theme.css` | GENERATED: `@theme inline` role colours + six scheme selectors. |
| Modify `apps/web/package.json` | `generate:m3` script; `@fontsource-variable/inclusive-sans` dependency. |
| Modify `apps/web/app/globals.css` | Import the generated file, `m3` variant, font indirection, M3 radii, `.m3` bridge, M3 type utilities. |
| Modify `apps/web/app/layout.tsx` | Import Inclusive Sans. |
| Modify `docs/decisions/log.md` | Append the decision entry. |

### Task 1.1: Parse the Kotlin schemes

**Files:**
- Create: `apps/web/lib/theme/m3-schemes.ts`
- Test: `apps/web/lib/theme/m3-schemes.test.ts`

- [ ] **Step 1: Write the failing tests**

`apps/web/lib/theme/m3-schemes.test.ts`:

```ts
import { describe, expect, it } from "vitest"
import { parseColorSchemes, toKebab } from "./m3-schemes"

// Minimal stand-in for ColorSchemes.kt: same shape as the generated Kotlin,
// two roles per scheme, plus a non-scheme literal the parser must ignore.
function kotlin(overrides: Partial<Record<string, string>> = {}): string {
  const block = (name: string, fn: "light" | "dark", primary: string) =>
    overrides[name] ??
    `internal val ${name}: ColorScheme =
    ${fn}ColorScheme(
        primary = Color(0xFF${primary}),
        surfaceContainerLowest = Color(0xFFFFFFFF),
    )
`
  return [
    "package app.pbbls.android.core.designsystem\n",
    block("LightScheme", "light", "8E4955"),
    block("LightMediumContrastScheme", "light", "5D222E"),
    block("LightHighContrastScheme", "light", "4F1824"),
    block("DarkScheme", "dark", "FFB2BC"),
    block("DarkMediumContrastScheme", "dark", "FFD1D6"),
    block("DarkHighContrastScheme", "dark", "FFEBED"),
    "internal val GoogleCapsuleInk = Color(0xFF4A3639)\n",
  ].join("\n")
}

describe("toKebab", () => {
  it("kebab-cases a camelCase role", () => {
    expect(toKebab("surfaceContainerLowest")).toBe("surface-container-lowest")
    expect(toKebab("primary")).toBe("primary")
  })
})

describe("parseColorSchemes", () => {
  it("finds the six schemes with mode, contrast and roles in source order", () => {
    const schemes = parseColorSchemes(kotlin())
    expect(schemes.map((s) => [s.name, s.mode, s.contrast])).toEqual([
      ["LightScheme", "light", "standard"],
      ["LightMediumContrastScheme", "light", "medium"],
      ["LightHighContrastScheme", "light", "high"],
      ["DarkScheme", "dark", "standard"],
      ["DarkMediumContrastScheme", "dark", "medium"],
      ["DarkHighContrastScheme", "dark", "high"],
    ])
    expect(schemes[0].roles).toEqual([
      ["primary", "#8E4955"],
      ["surface-container-lowest", "#FFFFFF"],
    ])
  })

  it("ignores Color literals outside a scheme block", () => {
    const roles = parseColorSchemes(kotlin()).flatMap((s) => s.roles.map(([r]) => r))
    expect(roles).not.toContain("google-capsule-ink")
  })

  it("fails when a scheme is missing", () => {
    expect(() => parseColorSchemes(kotlin({ DarkHighContrastScheme: "" }))).toThrow(
      /DarkHighContrastScheme/,
    )
  })

  it("fails when a scheme's role set differs from LightScheme's", () => {
    const drifted = `internal val DarkScheme: ColorScheme =
    darkColorScheme(
        primary = Color(0xFFFFB2BC),
    )
`
    expect(() => parseColorSchemes(kotlin({ DarkScheme: drifted }))).toThrow(
      /DarkScheme.*surface-container-lowest/,
    )
  })

  it("fails on a translucent colour", () => {
    const translucent = `internal val LightScheme: ColorScheme =
    lightColorScheme(
        primary = Color(0x808E4955),
        surfaceContainerLowest = Color(0xFFFFFFFF),
    )
`
    expect(() => parseColorSchemes(kotlin({ LightScheme: translucent }))).toThrow(/alpha/)
  })

  it("fails when a Dark* name is built with lightColorScheme", () => {
    const wrongFn = `internal val DarkScheme: ColorScheme =
    lightColorScheme(
        primary = Color(0xFFFFB2BC),
        surfaceContainerLowest = Color(0xFFFFFFFF),
    )
`
    expect(() => parseColorSchemes(kotlin({ DarkScheme: wrongFn }))).toThrow(/DarkScheme/)
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `npm run test --workspace=apps/web -- lib/theme/m3-schemes.test.ts`
Expected: FAIL. The module `./m3-schemes` doesn't exist.

- [ ] **Step 3: Implement the parser**

`apps/web/lib/theme/m3-schemes.ts`:

```ts
/**
 * Android is the source of truth for the M3 palette (#853, #986): the web's
 * `app/m3-theme.css` is generated from `ColorSchemes.kt`, never hand-copied.
 * Pure string-in/string-out so the drift test can run it in Vitest's node env.
 */

export type M3Mode = "light" | "dark"
export type M3Contrast = "standard" | "medium" | "high"

export interface M3Scheme {
  name: string
  mode: M3Mode
  contrast: M3Contrast
  /** [kebab-role, "#RRGGBB"] in Kotlin source order. */
  roles: [string, string][]
}

/** Paths relative to the `apps/web` workspace root. */
export const ANDROID_SCHEMES_FROM_WEB =
  "../android/app/src/main/kotlin/app/pbbls/android/core/designsystem/ColorSchemes.kt"
export const M3_CSS_FROM_WEB = "app/m3-theme.css"

const EXPECTED: Record<string, { mode: M3Mode; contrast: M3Contrast }> = {
  LightScheme: { mode: "light", contrast: "standard" },
  LightMediumContrastScheme: { mode: "light", contrast: "medium" },
  LightHighContrastScheme: { mode: "light", contrast: "high" },
  DarkScheme: { mode: "dark", contrast: "standard" },
  DarkMediumContrastScheme: { mode: "dark", contrast: "medium" },
  DarkHighContrastScheme: { mode: "dark", contrast: "high" },
}

const SCHEME_BLOCK = /internal val (\w+): ColorScheme =\s*(light|dark)ColorScheme\(([\s\S]*?)\n\s*\)/g
const ROLE_LINE = /(\w+) = Color\(0x([0-9A-Fa-f]{2})([0-9A-Fa-f]{6})\)/g

export function toKebab(role: string): string {
  return role.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)
}

export function parseColorSchemes(kotlin: string): M3Scheme[] {
  const found = new Map<string, M3Scheme>()

  for (const [, name, fn, body] of kotlin.matchAll(SCHEME_BLOCK)) {
    const expected = EXPECTED[name]
    if (!expected) continue
    if (expected.mode !== fn) {
      throw new Error(`${name} is built with ${fn}ColorScheme, expected ${expected.mode}ColorScheme`)
    }
    const roles: [string, string][] = []
    for (const [, role, alpha, rgb] of body.matchAll(ROLE_LINE)) {
      if (alpha.toUpperCase() !== "FF") {
        throw new Error(`${name}.${role} has alpha 0x${alpha}; M3 roles must be opaque`)
      }
      roles.push([toKebab(role), `#${rgb.toUpperCase()}`])
    }
    found.set(name, { name, ...expected, roles })
  }

  const missing = Object.keys(EXPECTED).filter((n) => !found.has(n))
  if (missing.length > 0) {
    throw new Error(`ColorSchemes.kt is missing scheme(s): ${missing.join(", ")}`)
  }

  const schemes = Object.keys(EXPECTED).map((n) => found.get(n)!)
  const reference = schemes[0].roles.map(([r]) => r).sort()
  for (const scheme of schemes.slice(1)) {
    const own = scheme.roles.map(([r]) => r).sort()
    const absent = reference.filter((r) => !own.includes(r))
    const extra = own.filter((r) => !reference.includes(r))
    if (absent.length > 0 || extra.length > 0) {
      throw new Error(
        `${scheme.name} role set differs from LightScheme (missing: ${absent.join(", ") || "none"}; extra: ${extra.join(", ") || "none"})`,
      )
    }
  }
  return schemes
}
```

Note: the `!` in `found.get(n)!` is safe because `missing` was checked just above. If ESLint's `no-non-null-assertion` is on, replace it with a `flatMap` that skips `undefined`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `npm run test --workspace=apps/web -- lib/theme/m3-schemes.test.ts`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add apps/web/lib/theme/m3-schemes.ts apps/web/lib/theme/m3-schemes.test.ts
git commit -m "feat(web): parse the android material 3 schemes"
```

### Task 1.2: Render the CSS and add the drift gate

**Files:**
- Modify: `apps/web/lib/theme/m3-schemes.ts`
- Modify: `apps/web/lib/theme/m3-schemes.test.ts`

- [ ] **Step 1: Write the failing tests**

Append to `apps/web/lib/theme/m3-schemes.test.ts` (merge the imports into the top of the file):

```ts
import { readFileSync } from "node:fs"
import path from "node:path"
import { fileURLToPath } from "node:url"
import {
  ANDROID_SCHEMES_FROM_WEB,
  M3_CSS_FROM_WEB,
  renderM3ThemeCss,
  schemeSelector,
} from "./m3-schemes"

describe("schemeSelector", () => {
  it("gives each scheme a unique, contrast-and-mode-specific selector", () => {
    expect(schemeSelector("light", "standard")).toBe(".m3")
    expect(schemeSelector("light", "medium")).toBe(".m3.m3-contrast-medium")
    expect(schemeSelector("dark", "standard")).toBe(".m3.dark")
    expect(schemeSelector("dark", "high")).toBe(".m3.dark.m3-contrast-high")
  })
})

describe("renderM3ThemeCss", () => {
  const css = renderM3ThemeCss(parseColorSchemes(kotlin()))

  it("starts with the do-not-edit header", () => {
    expect(css.startsWith("/* GENERATED")).toBe(true)
  })

  it("exposes every role as a Tailwind colour", () => {
    expect(css).toContain("@theme inline {")
    expect(css).toContain("  --color-m3-primary: var(--m3-primary);")
    expect(css).toContain("  --color-m3-surface-container-lowest: var(--m3-surface-container-lowest);")
  })

  it("writes each scheme's values under its selector", () => {
    expect(css).toContain(".m3 {\n  --m3-primary: #8E4955;")
    expect(css).toContain(".m3.dark.m3-contrast-high {\n  --m3-primary: #FFEBED;")
  })

  it("ends with a single newline", () => {
    expect(css.endsWith("}\n")).toBe(true)
  })
})

// The drift gate: the committed CSS must be exactly what the generator writes
// from the committed Kotlin. Fails when Android re-exports without a web regen.
describe("app/m3-theme.css", () => {
  const webRoot = fileURLToPath(new URL("../..", import.meta.url))
  const kotlinSource = readFileSync(path.join(webRoot, ANDROID_SCHEMES_FROM_WEB), "utf8")

  it("parses the real ColorSchemes.kt into six schemes of 48 roles", () => {
    const schemes = parseColorSchemes(kotlinSource)
    expect(schemes).toHaveLength(6)
    for (const scheme of schemes) expect(scheme.roles).toHaveLength(48)
  })

  it("matches the generator output (run `npm run generate:m3 --workspace=apps/web`)", () => {
    const committed = readFileSync(path.join(webRoot, M3_CSS_FROM_WEB), "utf8")
    expect(committed).toBe(renderM3ThemeCss(parseColorSchemes(kotlinSource)))
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `npm run test --workspace=apps/web -- lib/theme/m3-schemes.test.ts`
Expected: FAIL. `renderM3ThemeCss` / `schemeSelector` aren't exported, and `app/m3-theme.css` doesn't exist yet. The file is created in Task 1.3, so the last test stays red until then.

- [ ] **Step 3: Implement the renderer**

Append to `apps/web/lib/theme/m3-schemes.ts`:

```ts
export function schemeSelector(mode: M3Mode, contrast: M3Contrast): string {
  return `.m3${mode === "dark" ? ".dark" : ""}${contrast === "standard" ? "" : `.m3-contrast-${contrast}`}`
}

const HEADER = `/* GENERATED from apps/android/.../core/designsystem/ColorSchemes.kt by
   \`npm run generate:m3 --workspace=apps/web\`. Do not edit: change the Android
   export and regenerate. lib/theme/m3-schemes.test.ts fails when this file and
   the Kotlin disagree.

   Each scheme's selector is one class more specific than the last axis it adds,
   so exactly one block wins for any combination of .m3 / .dark / .m3-contrast-*.
   The bridge that points shadcn tokens at these roles lives in globals.css. */
`

export function renderM3ThemeCss(schemes: M3Scheme[]): string {
  const roles = schemes[0].roles.map(([r]) => r)
  const theme = [
    "@theme inline {",
    ...roles.map((r) => `  --color-m3-${r}: var(--m3-${r});`),
    "}",
  ].join("\n")
  const blocks = schemes.map((s) =>
    [
      `${schemeSelector(s.mode, s.contrast)} {`,
      ...s.roles.map(([r, hex]) => `  --m3-${r}: ${hex};`),
      "}",
    ].join("\n"),
  )
  return `${[HEADER, theme, ...blocks].join("\n")}\n`
}
```

- [ ] **Step 4: Run the tests**

Run: `npm run test --workspace=apps/web -- lib/theme/m3-schemes.test.ts`
Expected: everything passes except `matches the generator output` (ENOENT on `app/m3-theme.css`). That one goes green in Task 1.3.

- [ ] **Step 5: Commit**

```bash
git add apps/web/lib/theme/m3-schemes.ts apps/web/lib/theme/m3-schemes.test.ts
git commit -m "feat(web): render the material 3 theme css with a drift gate"
```

### Task 1.3: Generator script and the generated file

**Files:**
- Create: `apps/web/scripts/generate-m3-theme.ts`
- Modify: `apps/web/package.json` (scripts)
- Create (generated): `apps/web/app/m3-theme.css`

- [ ] **Step 1: Write the script**

`apps/web/scripts/generate-m3-theme.ts`:

```ts
/**
 * Writes app/m3-theme.css from Android's ColorSchemes.kt (#986).
 * Run from the web workspace: `npm run generate:m3 --workspace=apps/web`.
 */
import { readFileSync, writeFileSync } from "node:fs"
import path from "node:path"
import {
  ANDROID_SCHEMES_FROM_WEB,
  M3_CSS_FROM_WEB,
  parseColorSchemes,
  renderM3ThemeCss,
} from "../lib/theme/m3-schemes"

const webRoot = process.cwd()
const source = path.resolve(webRoot, ANDROID_SCHEMES_FROM_WEB)
const target = path.resolve(webRoot, M3_CSS_FROM_WEB)

try {
  const schemes = parseColorSchemes(readFileSync(source, "utf8"))
  writeFileSync(target, renderM3ThemeCss(schemes))
  console.log(`[generate:m3] wrote ${schemes.length} schemes × ${schemes[0].roles.length} roles to ${M3_CSS_FROM_WEB}`)
} catch (err) {
  console.error("[generate:m3] failed:", err instanceof Error ? err.message : err)
  process.exit(1)
}
```

- [ ] **Step 2: Add the npm script**

In `apps/web/package.json` `scripts`, after `"generate:splash"`, add:

```json
    "generate:m3": "tsx scripts/generate-m3-theme.ts"
```

(Put a comma after the `generate:splash` line.)

- [ ] **Step 3: Generate**

Run: `npm run generate:m3 --workspace=apps/web`
Expected: `[generate:m3] wrote 6 schemes × 48 roles to app/m3-theme.css`

Spot-check: `grep -n "^\.m3\|--m3-primary:\|--m3-error:" apps/web/app/m3-theme.css`. You should see 6 selectors, `.m3 { --m3-primary: #8E4955` and `--m3-error: #7F560F` (amber) in the light standard block.

- [ ] **Step 4: Run the whole web suite**

Run: `npm run test --workspace=apps/web`
Expected: all green, including the drift gate.

- [ ] **Step 5: Commit**

```bash
git add apps/web/scripts/generate-m3-theme.ts apps/web/package.json apps/web/app/m3-theme.css
git commit -m "feat(web): generate the material 3 colour roles from android"
```

### Task 1.4: Wire the theme into globals.css (type, shape, bridge)

**Files:**
- Modify: `apps/web/package.json`, `package-lock.json` (Inclusive Sans)
- Modify: `apps/web/app/layout.tsx:1-5` (font import)
- Modify: `apps/web/app/globals.css`

- [ ] **Step 1: Install Inclusive Sans**

Run: `npm install @fontsource-variable/inclusive-sans@^5.3.0 --workspace=apps/web`
Then confirm the family name: `grep -m1 "font-family" node_modules/@fontsource-variable/inclusive-sans/index.css`
Expected: `font-family: 'Inclusive Sans Variable';`. If the name differs, use the printed name everywhere this task says `Inclusive Sans Variable`.

- [ ] **Step 2: Import the font**

In `apps/web/app/layout.tsx`, after `import "@fontsource-variable/caveat";`, add:

```ts
import "@fontsource-variable/inclusive-sans";
```

The `@font-face` is registered globally, but browsers only download a face when a rendered element uses it. Only `.m3` uses it, so real users don't fetch it.

- [ ] **Step 3: Import the generated file and add the `m3` variant**

At the top of `apps/web/app/globals.css`, the file becomes:

```css
@import "tailwindcss";
@import "tw-animate-css";
@import "shadcn/tailwind.css";
@import "./m3-theme.css";

@custom-variant dark (&:is(.dark *));
/* Tuned components opt into M3 roles with `m3:` so the Current theme keeps its
   own classes: `bg-card m3:bg-m3-surface-container-low`. */
@custom-variant m3 (&:is(.m3 *));
```

- [ ] **Step 4: Route `font-sans` through a variable and add the M3 radii**

`@theme inline` inlines literal values into utilities, so overriding `--font-sans` under `.m3` would do nothing. Route it through `--app-font-sans`. In the `@theme inline` block, replace:

```css
  --font-sans: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
```

with:

```css
  --font-sans: var(--app-font-sans);
```

and add at the end of the same `@theme inline` block (before its closing `}`):

```css
  /* M3 Expressive shape scale (Shapes.kt: stock Shapes()). `rounded-m3-*`. */
  --radius-m3-none: 0px;
  --radius-m3-extra-small: 4px;
  --radius-m3-small: 8px;
  --radius-m3-medium: 12px;
  --radius-m3-large: 16px;
  --radius-m3-large-increased: 20px;
  --radius-m3-extra-large: 28px;
  --radius-m3-extra-large-increased: 32px;
  --radius-m3-extra-extra-large: 48px;
  --radius-m3-full: 9999px;
```

In the `:root { … }` block (Blush Quartz light, starts at the `--safe-area-top` line), add after `--safe-area-left`:

```css
  --app-font-sans: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  /* M3 faces (Typography.kt): Ysabeau for display/headline/title, Inclusive
     Sans for body/label. Defined globally so the m3-type-* utilities render the
     same on the design page under either theme. */
  --m3-font-brand: "Ysabeau Variable", system-ui, sans-serif;
  --m3-font-plain: "Inclusive Sans Variable", system-ui, sans-serif;
```

- [ ] **Step 5: Add the `.m3` bridge**

Insert this block immediately **before** the `@layer base {` line, after the last color world (`.moss-pool.dark`). `:root.m3` (0,2,0) outranks `.dark` (0,1,0) wherever it sits, but keeping it after the worlds keeps the precedence readable:

```css
/* ============================================================
   Material 3 (M3-evo, seed #CE7E8A) — the bridge (#986)
   Only /sandbox/design applies `.m3`, and it removes the color-world class
   first, so this never stacks with a world. The role values come from the
   generated m3-theme.css; this block points every shadcn token at a role so
   untuned components render M3 as-is.

   shadcn token              → M3 role
   background / foreground   → surface / on-surface
   card                      → surface-container-low
   popover                   → surface-container
   primary / -foreground     → primary / on-primary
   secondary / -foreground   → secondary-container / on-secondary-container
   muted / -foreground       → surface-container-high / on-surface-variant
   accent / -foreground      → surface-container-highest / on-surface
   destructive / -foreground → error / on-error   (amber, not red: #853)
   border / input / ring     → outline-variant / outline / primary
   surface / surface-alt     → surface-container / surface-container-high
   chart-1…5                 → primary, tertiary, secondary, error, outline
   sidebar-*                 → surface-container, primary, secondary-container
   ============================================================ */
:root.m3 {
  --app-font-sans: var(--m3-font-plain);
  --radius: 0.75rem; /* M3 medium (12px); shadcn's rounded-* scale derives from it */

  --background: var(--m3-surface);
  --foreground: var(--m3-on-surface);
  --surface: var(--m3-surface-container);
  --surface-alt: var(--m3-surface-container-high);
  --card: var(--m3-surface-container-low);
  --card-foreground: var(--m3-on-surface);
  --popover: var(--m3-surface-container);
  --popover-foreground: var(--m3-on-surface);
  --primary: var(--m3-primary);
  --primary-foreground: var(--m3-on-primary);
  --secondary: var(--m3-secondary-container);
  --secondary-foreground: var(--m3-on-secondary-container);
  --muted: var(--m3-surface-container-high);
  --muted-foreground: var(--m3-on-surface-variant);
  --accent: var(--m3-surface-container-highest);
  --accent-foreground: var(--m3-on-surface);
  --destructive: var(--m3-error);
  --destructive-foreground: var(--m3-on-error);
  --border: var(--m3-outline-variant);
  --input: var(--m3-outline);
  --ring: var(--m3-primary);
  --chart-1: var(--m3-primary);
  --chart-2: var(--m3-tertiary);
  --chart-3: var(--m3-secondary);
  --chart-4: var(--m3-error);
  --chart-5: var(--m3-outline);
  --sidebar: var(--m3-surface-container);
  --sidebar-foreground: var(--m3-on-surface);
  --sidebar-primary: var(--m3-primary);
  --sidebar-primary-foreground: var(--m3-on-primary);
  --sidebar-accent: var(--m3-secondary-container);
  --sidebar-accent-foreground: var(--m3-on-secondary-container);
  --sidebar-border: var(--m3-outline-variant);
  --sidebar-ring: var(--m3-primary);
}
```

- [ ] **Step 6: Add the M3 type utilities**

Append after the bridge block (still before `@layer base`). These are the M3 baseline scale (Compose `Typography()` defaults, as `Typography.kt` uses them) with the faces swapped as on Android. Tracking is in px, the same as Compose's sp at 1×. Emphasized styles are out of scope until a tuned component needs one:

```css
/* M3 type scale — `m3-type-<style>-<size>`. Named m3-type-* rather than
   text-m3-* so they can't be confused with the text-m3-<role> colours. */
@utility m3-type-display-large { font-family: var(--m3-font-brand); font-feature-settings: "pnum", "lnum"; font-size: 3.5625rem; line-height: 4rem; letter-spacing: -0.25px; font-weight: 400; }
@utility m3-type-display-medium { font-family: var(--m3-font-brand); font-feature-settings: "pnum", "lnum"; font-size: 2.8125rem; line-height: 3.25rem; letter-spacing: 0; font-weight: 400; }
@utility m3-type-display-small { font-family: var(--m3-font-brand); font-feature-settings: "pnum", "lnum"; font-size: 2.25rem; line-height: 2.75rem; letter-spacing: 0; font-weight: 400; }
@utility m3-type-headline-large { font-family: var(--m3-font-brand); font-feature-settings: "pnum", "lnum"; font-size: 2rem; line-height: 2.5rem; letter-spacing: 0; font-weight: 400; }
@utility m3-type-headline-medium { font-family: var(--m3-font-brand); font-feature-settings: "pnum", "lnum"; font-size: 1.75rem; line-height: 2.25rem; letter-spacing: 0; font-weight: 400; }
@utility m3-type-headline-small { font-family: var(--m3-font-brand); font-feature-settings: "pnum", "lnum"; font-size: 1.5rem; line-height: 2rem; letter-spacing: 0; font-weight: 400; }
@utility m3-type-title-large { font-family: var(--m3-font-brand); font-feature-settings: "pnum", "lnum"; font-size: 1.375rem; line-height: 1.75rem; letter-spacing: 0; font-weight: 400; }
@utility m3-type-title-medium { font-family: var(--m3-font-brand); font-feature-settings: "pnum", "lnum"; font-size: 1rem; line-height: 1.5rem; letter-spacing: 0.15px; font-weight: 500; }
@utility m3-type-title-small { font-family: var(--m3-font-brand); font-feature-settings: "pnum", "lnum"; font-size: 0.875rem; line-height: 1.25rem; letter-spacing: 0.1px; font-weight: 500; }
@utility m3-type-body-large { font-family: var(--m3-font-plain); font-size: 1rem; line-height: 1.5rem; letter-spacing: 0.5px; font-weight: 400; }
@utility m3-type-body-medium { font-family: var(--m3-font-plain); font-size: 0.875rem; line-height: 1.25rem; letter-spacing: 0.25px; font-weight: 400; }
@utility m3-type-body-small { font-family: var(--m3-font-plain); font-size: 0.75rem; line-height: 1rem; letter-spacing: 0.4px; font-weight: 400; }
@utility m3-type-label-large { font-family: var(--m3-font-plain); font-size: 0.875rem; line-height: 1.25rem; letter-spacing: 0.1px; font-weight: 500; }
@utility m3-type-label-medium { font-family: var(--m3-font-plain); font-size: 0.75rem; line-height: 1rem; letter-spacing: 0.5px; font-weight: 500; }
@utility m3-type-label-small { font-family: var(--m3-font-plain); font-size: 0.6875rem; line-height: 1rem; letter-spacing: 0.5px; font-weight: 500; }
```

- [ ] **Step 7: Verify: lint, test, build**

Run:
```bash
npm run lint --workspace=apps/web
npm run test --workspace=apps/web
npm run build --workspace=apps/web
```
Expected: all green. The build proves Tailwind accepted the imported `@theme inline`, the `@utility` blocks and the `m3` variant.

- [ ] **Step 8: Verify zero visual change**

Run `npm run dev --workspace=apps/web`, open `http://localhost:3000/` in the browser and check with devtools:
- `getComputedStyle(document.documentElement).fontFamily` still starts with `system-ui` (the font indirection preserved Current).
- `document.documentElement.classList.add("m3")` turns the page M3 (pinkish surface `#FFF8F7`, Inclusive Sans), and removing the class restores it. That's the manual smoke test of the bridge. Don't commit anything that applies the class.

- [ ] **Step 9: Commit**

```bash
git add apps/web/package.json package-lock.json apps/web/app/layout.tsx apps/web/app/globals.css
git commit -m "feat(web): bridge the shadcn tokens onto material 3 under .m3"
```

### Task 1.5: Decision log and Part 1 PR

**Files:**
- Modify: `docs/decisions/log.md` (append at the end)

- [ ] **Step 1: Append the decision**

```markdown

## 2026-09-28 — The web follows Android's M3-evo theme; colour is generated from ColorSchemes.kt (#986)

- **Status:** taken
- **Scope:** web, ui
- **Context:** Android adopted the M3-evo Material 3 Expressive theme (2026-09-24, #853) and stock M3 chrome (#854). The web still renders the iOS-derived color worlds through shadcn tokens. Before any default changes, the maintainer wants to see M3 on every web component and tune them one at a time.
- **Decision:** The web's M3 colour roles are **generated** from `apps/android/.../core/designsystem/ColorSchemes.kt` into `apps/web/app/m3-theme.css` (`npm run generate:m3 --workspace=apps/web`), scoped under `.m3` with `.dark` and `.m3-contrast-medium|high` for the six schemes. A hand-written bridge in `globals.css` points every shadcn token at an M3 role, so untuned components render M3 as-is. Tuning a component means adding explicit `m3:` role classes (`bg-card m3:bg-m3-surface-container-low`) so the Current theme is unaffected. Type follows `Typography.kt` (Ysabeau for display/headline/title, Inclusive Sans for body/label) as `m3-type-*` utilities; shapes follow `Shapes.kt` as `rounded-m3-*`. Only `/sandbox/design` applies `.m3`.
- **Why:** A hand-copied palette drifts the first time Android re-exports. A Vitest drift gate on every PR makes the Kotlin file the single source for both surfaces. The bridge shows the whole app in M3 on day one, and `m3:` variants let tuning land incrementally without touching what real users see.
- **Consequences:**
  - Re-exporting the Android palette now means running `generate:m3` in the same PR, or `web.yml` fails.
  - The design page is where the migration's progress is tracked (`bridged` / `tuned` per specimen).
  - Flipping the web's default to M3, retiring the color worlds, and iOS (#921) are separate decisions.
  - `--font-sans` is now `var(--app-font-sans)`. Change the app font there, not in `@theme`.
- **Supersedes / Superseded-by:** None.
- **Refs:** #986, #853, #854, #921, `docs/superpowers/specs/2026-09-28-web-m3-design-page-design.md`, `apps/web/lib/theme/m3-schemes.ts`, `apps/web/app/m3-theme.css`.
```

- [ ] **Step 2: Commit**

```bash
git add docs/decisions/log.md
git commit -m "docs: log the web material 3 theme decision"
```

- [ ] **Step 3: Controller only: push and open the Part 1 PR**

The controller (not a subagent) runs this, following the `gh-stack` skill and the root CLAUDE.md PR checklist. Labels `feat`, `web`, `ui`, `no-lab-note`; milestone as the maintainer confirms. Part 2 is the PR that resolves #986, so Part 1's body starts with `Part of #986`.

---

# Part 2 — the design page

> **Starts on its own branch.** Before Task 2.1, the controller creates `feat/986-web-m3-design-page` on top of `feat/986-web-m3-theme-tokens` with `gh stack` (see the `gh-stack` skill). Every Part 2 commit lands there.

### File map (Part 2)

| File | Responsibility |
|---|---|
| Create `apps/web/lib/theme/design-theme.ts` (+ `.test.ts`) | Pure URL ↔ theme state; theme → `<html>` class list / class diff. |
| Create `apps/web/lib/theme/contrast.ts` (+ `.test.ts`) | WCAG luminance, contrast ratio, hex formatting. |
| Create `apps/web/lib/theme/m3-bridge.ts` (+ `.test.ts`) | The shadcn → M3 bridge as data. A test pins it to `globals.css`'s `:root.m3` block and to the real role names. |
| Create `apps/web/lib/seed/design-fixtures.ts` (+ `.test.ts`) | Store, account, profile, ripple, assiduity and consent fixtures. |
| Create `apps/web/lib/design/specimens.ts` (+ `.test.ts`) | Specimen registry + `bridged`/`tuned` status. A test proves every `components/ui` file has a specimen. |
| Create `apps/web/app/sandbox/design/page.tsx` | Thin shell: `Suspense` around `DesignScreen`. |
| Create `apps/web/components/sandbox/design/useDesignTheme.ts` | URL state ↔ `<html>` classes: apply, defend against the race, restore on leave. |
| Create `apps/web/components/sandbox/design/DesignScreen.tsx` | Page composition, keyboard shortcuts, link blocking, palette priming. |
| Create `apps/web/components/sandbox/design/DesignToolbar.tsx` | Sticky switcher + progress + section links. |
| Create `apps/web/components/sandbox/design/FixtureProviders.tsx` | Fake `DataContext` + `AuthContext` over the fixtures. |
| Create `apps/web/components/sandbox/design/DesignSection.tsx`, `Specimen.tsx`, `SpecimenState.tsx` | Page layout primitives. |
| Create `apps/web/components/sandbox/design/foundations/*` | `FoundationsSection`, `ColorSwatch`, `M3Roles`, `BridgeTable`, `TypeScale`, `ShapeScale`. |
| Create `apps/web/components/sandbox/design/primitives/*` | `PrimitivesSection`, `OverlaySpecimens`. |
| Create `apps/web/components/sandbox/design/ComponentsSection.tsx` | Curated feature components. |
| Create `apps/web/components/sandbox/design/views/*` | `ViewsSection`, `DeviceFrame`, `PathView`, `PebbleDetailView`, `ProfileView`, `SettingsView`. |

**Deviation from the spec, on purpose:** the spec named the type utilities `text-m3-*` with `--m3-type-*` tokens. Part 1 ships them as `m3-type-*` utilities only, so they can't be confused with the `text-m3-<role>` colour utilities, and no component needs the raw tokens yet.

### Task 2.1: Theme state (pure)

**Files:**
- Create: `apps/web/lib/theme/design-theme.ts`
- Test: `apps/web/lib/theme/design-theme.test.ts`

- [ ] **Step 1: Write the failing tests**

```ts
import { describe, expect, it } from "vitest"
import {
  DEFAULT_DESIGN_THEME,
  appClassesFor,
  classDiff,
  designClassesFor,
  parseDesignTheme,
  serializeDesignTheme,
  type DesignTheme,
} from "./design-theme"

const params = (query: string) => new URLSearchParams(query)

describe("parseDesignTheme", () => {
  it("defaults to M3, light, standard contrast, Blush Quartz", () => {
    expect(parseDesignTheme(params(""))).toEqual(DEFAULT_DESIGN_THEME)
    expect(DEFAULT_DESIGN_THEME).toEqual({
      theme: "m3",
      world: "blush-quartz",
      mode: "light",
      contrast: "standard",
    })
  })

  it("reads every param", () => {
    expect(parseDesignTheme(params("theme=current&world=moss-pool&mode=dark&contrast=high"))).toEqual({
      theme: "current",
      world: "moss-pool",
      mode: "dark",
      contrast: "high",
    })
  })

  it("falls back param by param on unknown values", () => {
    expect(parseDesignTheme(params("theme=neon&world=lava&mode=dark&contrast=max"))).toEqual({
      ...DEFAULT_DESIGN_THEME,
      mode: "dark",
    })
  })

  it("round-trips through serializeDesignTheme, keeping the world while on M3", () => {
    const theme: DesignTheme = { theme: "m3", world: "dusk-stone", mode: "dark", contrast: "medium" }
    expect(parseDesignTheme(params(serializeDesignTheme(theme)))).toEqual(theme)
  })
})

describe("designClassesFor", () => {
  it("M3 light standard is .m3 alone", () => {
    expect(designClassesFor(DEFAULT_DESIGN_THEME)).toEqual(["m3"])
  })

  it("M3 dark high contrast adds both axes", () => {
    expect(designClassesFor({ ...DEFAULT_DESIGN_THEME, mode: "dark", contrast: "high" })).toEqual([
      "m3",
      "dark",
      "m3-contrast-high",
    ])
  })

  it("Current ignores contrast and has no class for the default world", () => {
    expect(designClassesFor({ ...DEFAULT_DESIGN_THEME, theme: "current", contrast: "high" })).toEqual([])
  })

  it("Current dark in another world", () => {
    expect(designClassesFor({ theme: "current", world: "dusk-stone", mode: "dark", contrast: "standard" })).toEqual([
      "dusk-stone",
      "dark",
    ])
  })
})

describe("classDiff", () => {
  it("swaps a world for M3 and leaves classes it doesn't manage alone", () => {
    expect(classDiff(["light", "moss-pool", "some-font"], ["m3"])).toEqual({ remove: ["moss-pool"], add: ["m3"] })
  })

  it("is empty once the target is applied", () => {
    expect(classDiff(["m3", "dark", "other"], ["m3", "dark"])).toEqual({ remove: [], add: [] })
  })

  it("hands the app back its own classes on leave", () => {
    expect(classDiff(["m3", "dark", "m3-contrast-medium"], appClassesFor("light", "stoic-rock"))).toEqual({
      remove: ["m3", "dark", "m3-contrast-medium"],
      add: ["stoic-rock"],
    })
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `npm run test --workspace=apps/web -- lib/theme/design-theme.test.ts`
Expected: FAIL, because the module doesn't exist.

- [ ] **Step 3: Implement**

```ts
import { COLOR_WORLDS } from "@/lib/config/color-worlds"
import type { ColorWorld } from "@/lib/types"

/**
 * State of the /sandbox/design switcher (#986). It lives in the URL so a reload
 * or a shared link lands on the same theme, and so a two-iframe compare view
 * can drive it later. Pure: the hook in components/sandbox/design applies it.
 */

export type DesignThemeName = "current" | "m3"
export type DesignMode = "light" | "dark"
export type DesignContrast = "standard" | "medium" | "high"

export interface DesignTheme {
  theme: DesignThemeName
  /** Used when theme is "current"; kept in the URL while on M3 so T flips back to it. */
  world: ColorWorld
  mode: DesignMode
  /** Used when theme is "m3". */
  contrast: DesignContrast
}

const DEFAULT_WORLD: ColorWorld = "blush-quartz"

export const DEFAULT_DESIGN_THEME: DesignTheme = {
  theme: "m3",
  world: DEFAULT_WORLD,
  mode: "light",
  contrast: "standard",
}

const THEMES: readonly DesignThemeName[] = ["current", "m3"]
const MODES: readonly DesignMode[] = ["light", "dark"]
const CONTRASTS: readonly DesignContrast[] = ["standard", "medium", "high"]
const WORLDS: readonly ColorWorld[] = COLOR_WORLDS.map((w) => w.id)

/** Every <html> class the design page may add, and therefore may remove. */
const MANAGED_CLASSES: readonly string[] = [
  "m3",
  "dark",
  "m3-contrast-medium",
  "m3-contrast-high",
  ...WORLDS.filter((w) => w !== DEFAULT_WORLD),
]

function pick<T extends string>(value: string | null, allowed: readonly T[], fallback: T): T {
  return allowed.find((a) => a === value) ?? fallback
}

export function parseDesignTheme(params: Pick<URLSearchParams, "get">): DesignTheme {
  return {
    theme: pick(params.get("theme"), THEMES, DEFAULT_DESIGN_THEME.theme),
    world: pick(params.get("world"), WORLDS, DEFAULT_DESIGN_THEME.world),
    mode: pick(params.get("mode"), MODES, DEFAULT_DESIGN_THEME.mode),
    contrast: pick(params.get("contrast"), CONTRASTS, DEFAULT_DESIGN_THEME.contrast),
  }
}

export function serializeDesignTheme(theme: DesignTheme): string {
  return new URLSearchParams({
    theme: theme.theme,
    world: theme.world,
    mode: theme.mode,
    contrast: theme.contrast,
  }).toString()
}

/** The <html> classes that render `theme`. The default world has no class (globals.css :root). */
export function designClassesFor(theme: DesignTheme): string[] {
  const dark = theme.mode === "dark" ? ["dark"] : []
  if (theme.theme === "m3") {
    return ["m3", ...dark, ...(theme.contrast === "standard" ? [] : [`m3-contrast-${theme.contrast}`])]
  }
  return [...(theme.world === DEFAULT_WORLD ? [] : [theme.world]), ...dark]
}

/** The classes the app itself would put on <html> for the viewer's saved preferences. */
export function appClassesFor(mode: DesignMode, world: ColorWorld): string[] {
  return designClassesFor({ ...DEFAULT_DESIGN_THEME, theme: "current", mode, world })
}

/**
 * What to remove/add to turn `current` into `target`, touching only the
 * classes this page manages. Empty when nothing differs, which is what keeps
 * the MutationObserver in useDesignTheme from looping.
 */
export function classDiff(
  current: readonly string[],
  target: readonly string[],
): { remove: string[]; add: string[] } {
  return {
    remove: current.filter((c) => MANAGED_CLASSES.includes(c) && !target.includes(c)),
    add: target.filter((c) => !current.includes(c)),
  }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `npm run test --workspace=apps/web -- lib/theme/design-theme.test.ts`
Expected: PASS (11 tests).

- [ ] **Step 5: Commit**

```bash
git add apps/web/lib/theme/design-theme.ts apps/web/lib/theme/design-theme.test.ts
git commit -m "feat(web): model the design page theme switcher state"
```

### Task 2.2: Contrast maths and the bridge as data

**Files:**
- Create: `apps/web/lib/theme/contrast.ts`, `apps/web/lib/theme/contrast.test.ts`
- Create: `apps/web/lib/theme/m3-bridge.ts`, `apps/web/lib/theme/m3-bridge.test.ts`

- [ ] **Step 1: Write the failing tests**

`apps/web/lib/theme/contrast.test.ts`:

```ts
import { describe, expect, it } from "vitest"
import { contrastRatio, toHex } from "./contrast"

describe("contrastRatio", () => {
  it("is 21 for black on white and 1 for a colour on itself", () => {
    expect(contrastRatio([0, 0, 0], [255, 255, 255])).toBeCloseTo(21, 5)
    expect(contrastRatio([142, 73, 85], [142, 73, 85])).toBeCloseTo(1, 5)
  })

  it("is symmetric and matches the WCAG value for #777 on white", () => {
    expect(contrastRatio([119, 119, 119], [255, 255, 255])).toBeCloseTo(4.48, 2)
    expect(contrastRatio([255, 255, 255], [119, 119, 119])).toBeCloseTo(4.48, 2)
  })
})

describe("toHex", () => {
  it("formats upper-case, zero-padded", () => {
    expect(toHex([142, 73, 85])).toBe("#8E4955")
    expect(toHex([0, 5, 255])).toBe("#0005FF")
  })
})
```

`apps/web/lib/theme/m3-bridge.test.ts`:

```ts
import { readFileSync } from "node:fs"
import path from "node:path"
import { fileURLToPath } from "node:url"
import { describe, expect, it } from "vitest"
import { M3_BRIDGE } from "./m3-bridge"
import { ANDROID_SCHEMES_FROM_WEB, parseColorSchemes } from "./m3-schemes"

const webRoot = fileURLToPath(new URL("../..", import.meta.url))

describe("M3_BRIDGE", () => {
  it("mirrors the :root.m3 block in globals.css, in order", () => {
    const css = readFileSync(path.join(webRoot, "app/globals.css"), "utf8")
    const block = css.match(/:root\.m3 \{([\s\S]*?)\n\}/)?.[1] ?? ""
    const pairs = [...block.matchAll(/--([\w-]+): var\(--m3-([\w-]+)\);/g)]
      .map(([, token, role]) => [token, role])
      // The font indirection also reads an --m3-* variable; it is not a colour role.
      .filter(([token]) => token !== "app-font-sans")
    expect(pairs).toEqual(M3_BRIDGE.map(([token, role]) => [token, role]))
  })

  it("only points at roles that exist in the Android schemes", () => {
    const kotlin = readFileSync(path.join(webRoot, ANDROID_SCHEMES_FROM_WEB), "utf8")
    const roles = parseColorSchemes(kotlin)[0].roles.map(([r]) => r)
    for (const [token, role] of M3_BRIDGE) expect(roles, `--${token} → ${role}`).toContain(role)
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `npm run test --workspace=apps/web -- lib/theme/contrast.test.ts lib/theme/m3-bridge.test.ts`
Expected: FAIL, because the modules don't exist.

- [ ] **Step 3: Implement**

`apps/web/lib/theme/contrast.ts`:

```ts
/** WCAG 2.x contrast maths for the design page's swatches. sRGB 0–255 channels. */
export type Rgb = readonly [number, number, number]

function linear(channel: number): number {
  const c = channel / 255
  return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
}

export function relativeLuminance([r, g, b]: Rgb): number {
  return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
}

export function contrastRatio(a: Rgb, b: Rgb): number {
  const [hi, lo] = [relativeLuminance(a), relativeLuminance(b)].sort((x, y) => y - x)
  return (hi + 0.05) / (lo + 0.05)
}

export function toHex([r, g, b]: Rgb): string {
  return `#${[r, g, b].map((c) => c.toString(16).padStart(2, "0")).join("").toUpperCase()}`
}
```

`apps/web/lib/theme/m3-bridge.ts`:

```ts
/**
 * The shadcn token → M3 role bridge, as data for the design page's bridge
 * table. The CSS in globals.css (`:root.m3`) is what actually applies it;
 * m3-bridge.test.ts fails if the two ever disagree.
 */
export const M3_BRIDGE: readonly (readonly [token: string, role: string])[] = [
  ["background", "surface"],
  ["foreground", "on-surface"],
  ["surface", "surface-container"],
  ["surface-alt", "surface-container-high"],
  ["card", "surface-container-low"],
  ["card-foreground", "on-surface"],
  ["popover", "surface-container"],
  ["popover-foreground", "on-surface"],
  ["primary", "primary"],
  ["primary-foreground", "on-primary"],
  ["secondary", "secondary-container"],
  ["secondary-foreground", "on-secondary-container"],
  ["muted", "surface-container-high"],
  ["muted-foreground", "on-surface-variant"],
  ["accent", "surface-container-highest"],
  ["accent-foreground", "on-surface"],
  ["destructive", "error"],
  ["destructive-foreground", "on-error"],
  ["border", "outline-variant"],
  ["input", "outline"],
  ["ring", "primary"],
  ["chart-1", "primary"],
  ["chart-2", "tertiary"],
  ["chart-3", "secondary"],
  ["chart-4", "error"],
  ["chart-5", "outline"],
  ["sidebar", "surface-container"],
  ["sidebar-foreground", "on-surface"],
  ["sidebar-primary", "primary"],
  ["sidebar-primary-foreground", "on-primary"],
  ["sidebar-accent", "secondary-container"],
  ["sidebar-accent-foreground", "on-secondary-container"],
  ["sidebar-border", "outline-variant"],
  ["sidebar-ring", "primary"],
]
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `npm run test --workspace=apps/web -- lib/theme`
Expected: PASS. If the bridge-order test fails, the list above and the `:root.m3` block from Task 1.4 Step 5 disagree. Fix the TS list to match the CSS, never the reverse.

- [ ] **Step 5: Commit**

```bash
git add apps/web/lib/theme/contrast.ts apps/web/lib/theme/contrast.test.ts apps/web/lib/theme/m3-bridge.ts apps/web/lib/theme/m3-bridge.test.ts
git commit -m "feat(web): add contrast maths and the m3 bridge table for the design page"
```

### Task 2.3: Fixtures

**Files:**
- Create: `apps/web/lib/seed/design-fixtures.ts`
- Test: `apps/web/lib/seed/design-fixtures.test.ts`

- [ ] **Step 1: Write the failing tests**

```ts
import { describe, expect, it } from "vitest"
import {
  DESIGN_ASSIDUITY,
  DESIGN_PROFILE,
  designCollections,
  designPebbles,
  designStore,
} from "./design-fixtures"

const NOW = new Date("2026-09-28T12:00:00Z")

describe("design fixtures", () => {
  const store = designStore(NOW)
  const soulIds = store.souls.map((s) => s.id)
  const markIds = store.marks.map((m) => m.id)
  const pebbleIds = store.pebbles.map((p) => p.id)

  it("dates every pebble within the week before now, newest first", () => {
    const times = designPebbles(NOW).map((p) => Date.parse(p.happened_at))
    for (const t of times) {
      expect(t).toBeLessThan(NOW.getTime())
      expect(NOW.getTime() - t).toBeLessThan(7 * 24 * 3_600_000)
    }
    expect([...times].sort((a, b) => b - a)).toEqual(times)
  })

  it("only references souls and marks that are in the store", () => {
    for (const pebble of store.pebbles) {
      for (const id of pebble.soul_ids) expect(soulIds).toContain(id)
      if (pebble.mark_id) expect(markIds).toContain(pebble.mark_id)
    }
    expect(markIds).toContain(DESIGN_PROFILE.glyph_id)
  })

  it("fills collections with pebbles from the store, one per mode", () => {
    const collections = designCollections(store.pebbles)
    expect(collections.map((c) => c.mode)).toEqual(["stack", "pack", "track"])
    for (const c of collections) for (const id of c.pebble_ids) expect(pebbleIds).toContain(id)
  })

  it("has 28 days of assiduity", () => {
    expect(DESIGN_ASSIDUITY).toHaveLength(28)
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `npm run test --workspace=apps/web -- lib/seed/design-fixtures.test.ts`
Expected: FAIL, because the module doesn't exist.

- [ ] **Step 3: Implement**

```ts
import type { Store } from "@/lib/data/data-provider"
import type { ConsentRow } from "@/lib/data/consent"
import type { Account, Collection, Pebble, Profile, RippleSummary } from "@/lib/types"
import { SANDBOX_MARKS, SANDBOX_SCENARIOS, SANDBOX_SOULS } from "./sandbox-pebbles"

// ---------------------------------------------------------------------------
// Fixture content for /sandbox/design (#986). Builds on the /sandbox/path seed
// so it stays network-free: `render_svg: null` pebbles, local pictures, sandbox
// palettes primed by the page.
// ---------------------------------------------------------------------------

const HOUR = 3_600_000
const SIGNED_UP = "2025-11-02T09:00:00Z"

/**
 * The sandbox "mixed" pebbles, re-dated to the hours before `now` (newest
 * first) so the Path view opens on a filled current week.
 */
export function designPebbles(now: Date): Pebble[] {
  const mixed = SANDBOX_SCENARIOS.find((s) => s.key === "mixed")
  if (!mixed) throw new Error("[design-fixtures] the sandbox 'mixed' scenario is missing")
  return mixed.pebbles.map((pebble, i) => {
    const at = new Date(now.getTime() - (i + 1) * 5 * HOUR).toISOString()
    return { ...pebble, happened_at: at, created_at: at, updated_at: at }
  })
}

export function designCollections(pebbles: Pebble[]): Collection[] {
  const ids = pebbles.map((p) => p.id)
  return [
    { id: "design-col-stack", name: "Summer by the canal", mode: "stack", pebble_ids: ids.slice(0, 3), created_at: SIGNED_UP, updated_at: SIGNED_UP },
    { id: "design-col-pack", name: "Little wins", mode: "pack", pebble_ids: ids.slice(3, 5), created_at: SIGNED_UP, updated_at: SIGNED_UP },
    { id: "design-col-track", name: "Morning runs", mode: "track", pebble_ids: [], created_at: SIGNED_UP, updated_at: SIGNED_UP },
  ]
}

export function designStore(now: Date): Store {
  const pebbles = designPebbles(now)
  return {
    pebbles,
    souls: SANDBOX_SOULS,
    collections: designCollections(pebbles),
    marks: SANDBOX_MARKS,
    entitledMarks: [],
    pebbles_count: pebbles.length,
    karma: 42,
    karma_log: [],
    bounce: 3,
    bounce_window: [],
  }
}

export const DESIGN_ACCOUNT: Account = {
  id: "design-user",
  email: "mia@example.com",
  created_at: SIGNED_UP,
  providers: ["email", "google"],
}

export const DESIGN_PROFILE: Profile = {
  id: "design-profile",
  user_id: DESIGN_ACCOUNT.id,
  display_name: "Mia",
  glyph_id: SANDBOX_MARKS[0].id,
  handle: "mia",
  public_profile: true,
  onboarding_completed: true,
  color_world: "blush-quartz",
  terms_accepted_at: SIGNED_UP,
  privacy_accepted_at: SIGNED_UP,
  created_at: SIGNED_UP,
  updated_at: SIGNED_UP,
}

export const DESIGN_RIPPLE: RippleSummary = { level: 3, activeToday: true, pebbles28d: 17 }

/** 28 days, most practised, a gap every third day. */
export const DESIGN_ASSIDUITY: boolean[] = Array.from({ length: 28 }, (_, i) => i % 3 !== 1)

export const DESIGN_CONSENT: ConsentRow = {
  id: "design-consent",
  kind: "health_data",
  document_version: "2026-09",
  source: "web_settings",
  granted_at: "2026-09-01T10:00:00Z",
  withdrawn_at: null,
  superseded_at: null,
}
```

If `Account`, `Profile` or `RippleSummary` isn't exported from `@/lib/types`, import it from wherever `grep -rn "export type RippleSummary\|export type Profile\b\|export type Account\b" apps/web/lib` finds it.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `npm run test --workspace=apps/web -- lib/seed/design-fixtures.test.ts`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add apps/web/lib/seed/design-fixtures.ts apps/web/lib/seed/design-fixtures.test.ts
git commit -m "feat(web): add fixtures for the design page"
```

### Task 2.4: Specimen registry

**Files:**
- Create: `apps/web/lib/design/specimens.ts`
- Test: `apps/web/lib/design/specimens.test.ts`

- [ ] **Step 1: Write the failing tests**

```ts
import { existsSync, readdirSync } from "node:fs"
import path from "node:path"
import { fileURLToPath } from "node:url"
import { describe, expect, it } from "vitest"
import { SPECIMENS, tuningProgress, type SpecimenEntry } from "./specimens"

const webRoot = fileURLToPath(new URL("../..", import.meta.url))

describe("SPECIMENS", () => {
  it("has unique ids", () => {
    const ids = SPECIMENS.map((s) => s.id)
    expect(new Set(ids).size).toBe(ids.length)
  })

  it("points every entry at a file that exists", () => {
    for (const s of SPECIMENS) expect(existsSync(path.join(webRoot, s.source)), s.source).toBe(true)
  })

  // A new shadcn/custom primitive must show up on the design page.
  it("covers every file in components/ui", () => {
    const sources = SPECIMENS.filter((s) => s.section === "primitives").map((s) => s.source)
    for (const file of readdirSync(path.join(webRoot, "components/ui")).filter((f) => f.endsWith(".tsx"))) {
      expect(sources, `components/ui/${file} has no specimen`).toContain(`components/ui/${file}`)
    }
  })
})

describe("tuningProgress", () => {
  it("counts tuned entries", () => {
    const entries: SpecimenEntry[] = [
      { id: "a", name: "A", source: "a.tsx", section: "primitives", m3: "tuned" },
      { id: "b", name: "B", source: "b.tsx", section: "primitives", m3: "bridged" },
    ]
    expect(tuningProgress(entries)).toEqual({ tuned: 1, total: 2 })
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `npm run test --workspace=apps/web -- lib/design/specimens.test.ts`
Expected: FAIL, because the module doesn't exist.

- [ ] **Step 3: Implement**

```ts
/**
 * Everything /sandbox/design renders, and how far its M3 migration has come
 * (#986).
 * - `bridged`: renders M3 only through the shadcn-token bridge in globals.css.
 * - `tuned`: reads M3 roles explicitly (`m3:` classes) and has been reviewed
 *   on the page.
 * Flip an entry to `tuned` in the same PR that tunes the component.
 */

export type M3Status = "bridged" | "tuned"
export type SpecimenSection = "primitives" | "components" | "views"

export interface SpecimenEntry {
  readonly id: string
  readonly name: string
  /** Relative to apps/web. */
  readonly source: string
  readonly section: SpecimenSection
  readonly m3: M3Status
}

export const SPECIMENS = [
  // primitives: every file in components/ui (the test enforces it)
  { id: "button", name: "Button", source: "components/ui/button.tsx", section: "primitives", m3: "bridged" },
  { id: "badge", name: "Badge", source: "components/ui/badge.tsx", section: "primitives", m3: "bridged" },
  { id: "card", name: "Card", source: "components/ui/card.tsx", section: "primitives", m3: "bridged" },
  { id: "checkbox", name: "Checkbox", source: "components/ui/checkbox.tsx", section: "primitives", m3: "bridged" },
  { id: "input", name: "Input", source: "components/ui/input.tsx", section: "primitives", m3: "bridged" },
  { id: "calendar", name: "Calendar", source: "components/ui/calendar.tsx", section: "primitives", m3: "bridged" },
  { id: "dialog", name: "Dialog", source: "components/ui/dialog.tsx", section: "primitives", m3: "bridged" },
  { id: "alert-dialog", name: "Alert dialog", source: "components/ui/alert-dialog.tsx", section: "primitives", m3: "bridged" },
  { id: "sheet", name: "Sheet", source: "components/ui/sheet.tsx", section: "primitives", m3: "bridged" },
  { id: "popover", name: "Popover", source: "components/ui/popover.tsx", section: "primitives", m3: "bridged" },
  { id: "dropdown-menu", name: "Dropdown menu", source: "components/ui/dropdown-menu.tsx", section: "primitives", m3: "bridged" },
  { id: "toast", name: "Toast", source: "components/ui/sonner.tsx", section: "primitives", m3: "bridged" },
  { id: "confirm-dialog", name: "ConfirmDialog", source: "components/ui/ConfirmDialog.tsx", section: "primitives", m3: "bridged" },
  { id: "emotion-badge", name: "EmotionBadge", source: "components/ui/EmotionBadge.tsx", section: "primitives", m3: "bridged" },
  { id: "picker-sheet", name: "PickerSheet", source: "components/ui/PickerSheet.tsx", section: "primitives", m3: "bridged" },
  { id: "searchable-list", name: "SearchableList", source: "components/ui/SearchableList.tsx", section: "primitives", m3: "bridged" },
  { id: "section-label", name: "SectionLabel", source: "components/ui/SectionLabel.tsx", section: "primitives", m3: "bridged" },
  { id: "selectable-item", name: "SelectableItem", source: "components/ui/SelectableItem.tsx", section: "primitives", m3: "bridged" },
  { id: "tag-list", name: "TagList", source: "components/ui/TagList.tsx", section: "primitives", m3: "bridged" },
  // components: curated feature components that render from props or the fixture store
  { id: "pebble-card", name: "PebbleCard", source: "components/path/PebbleCard.tsx", section: "components", m3: "bridged" },
  { id: "path-pebble-row", name: "PathPebbleRow", source: "components/path/PathPebbleRow.tsx", section: "components", m3: "bridged" },
  { id: "path-empty-state", name: "PathEmptyState", source: "components/path/PathEmptyState.tsx", section: "components", m3: "bridged" },
  { id: "gamification-block", name: "GamificationBlock", source: "components/path/GamificationBlock.tsx", section: "components", m3: "bridged" },
  { id: "pebble-visual", name: "PebbleVisual", source: "components/pebble/PebbleVisual.tsx", section: "components", m3: "bridged" },
  { id: "pebble-indicators", name: "IntensityDots · PositivenessIndicator", source: "components/pebble/PebbleIndicators.tsx", section: "components", m3: "bridged" },
  { id: "soul-card", name: "SoulCard", source: "components/souls/SoulCard.tsx", section: "components", m3: "bridged" },
  { id: "souls-empty-state", name: "SoulsEmptyState", source: "components/souls/SoulsEmptyState.tsx", section: "components", m3: "bridged" },
  { id: "collection-card", name: "CollectionCard", source: "components/collections/CollectionCard.tsx", section: "components", m3: "bridged" },
  { id: "mode-badge", name: "ModeBadge", source: "components/collections/ModeBadge.tsx", section: "components", m3: "bridged" },
  { id: "profile-banner", name: "ProfileBanner", source: "components/profile/ProfileBanner.tsx", section: "components", m3: "bridged" },
  { id: "stats-card", name: "StatsCard", source: "components/profile/StatsCard.tsx", section: "components", m3: "bridged" },
  { id: "data-tile", name: "DataTile", source: "components/profile/DataTile.tsx", section: "components", m3: "bridged" },
  { id: "shortcut-tile", name: "ShortcutTile", source: "components/profile/ShortcutTile.tsx", section: "components", m3: "bridged" },
  { id: "section-card", name: "SectionCard", source: "components/profile/SectionCard.tsx", section: "components", m3: "bridged" },
  { id: "lab-card", name: "LabCard", source: "components/profile/LabCard.tsx", section: "components", m3: "bridged" },
  { id: "settings-row", name: "SettingsGroup · SettingsRow", source: "components/settings/SettingsRow.tsx", section: "components", m3: "bridged" },
  { id: "page-header", name: "PageHeader", source: "components/layout/PageHeader.tsx", section: "components", m3: "bridged" },
  { id: "empty-state", name: "EmptyState", source: "components/layout/EmptyState.tsx", section: "components", m3: "bridged" },
  // views: composed from their feature components, fed by the fixture store
  { id: "view-path", name: "Path", source: "app/path/page.tsx", section: "views", m3: "bridged" },
  { id: "view-pebble", name: "Pebble detail", source: "app/pebble/[id]/page.tsx", section: "views", m3: "bridged" },
  { id: "view-profile", name: "Profile", source: "app/profile/page.tsx", section: "views", m3: "bridged" },
  { id: "view-settings", name: "Settings", source: "app/settings/page.tsx", section: "views", m3: "bridged" },
] as const satisfies readonly SpecimenEntry[]

export type SpecimenId = (typeof SPECIMENS)[number]["id"]

export function getSpecimen(id: SpecimenId): SpecimenEntry {
  const entry = SPECIMENS.find((s) => s.id === id)
  if (!entry) throw new Error(`[specimens] unknown specimen "${id}"`)
  return entry
}

export function tuningProgress(entries: readonly SpecimenEntry[] = SPECIMENS): { tuned: number; total: number } {
  return { tuned: entries.filter((e) => e.m3 === "tuned").length, total: entries.length }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `npm run test --workspace=apps/web -- lib/design/specimens.test.ts`
Expected: PASS (4 tests). If "covers every file in components/ui" fails, a primitive was added since this plan was written. Add an entry and a specimen for it in Task 2.7.

- [ ] **Step 5: Commit**

```bash
git add apps/web/lib/design/specimens.ts apps/web/lib/design/specimens.test.ts
git commit -m "feat(web): add the design page specimen registry"
```

### Task 2.5: The page shell (hook, toolbar, providers, layout primitives)

**Files:**
- Create: `apps/web/components/sandbox/design/useDesignTheme.ts`
- Create: `apps/web/components/sandbox/design/DesignToolbar.tsx`
- Create: `apps/web/components/sandbox/design/FixtureProviders.tsx`
- Create: `apps/web/components/sandbox/design/DesignSection.tsx`, `Specimen.tsx`, `SpecimenState.tsx`
- Create: `apps/web/components/sandbox/design/DesignScreen.tsx`
- Create: `apps/web/app/sandbox/design/page.tsx`

- [ ] **Step 1: The hook**

`apps/web/components/sandbox/design/useDesignTheme.ts`:

```ts
"use client"

import { useCallback, useEffect, useMemo, useRef } from "react"
import { usePathname, useRouter, useSearchParams } from "next/navigation"
import { useTheme } from "next-themes"
import { useColorWorld } from "@/components/layout/ColorWorldProvider"
import {
  appClassesFor,
  classDiff,
  designClassesFor,
  parseDesignTheme,
  serializeDesignTheme,
  type DesignTheme,
} from "@/lib/theme/design-theme"

function applyClasses(target: readonly string[]) {
  const root = document.documentElement
  const { remove, add } = classDiff(Array.from(root.classList), target)
  if (remove.length > 0) root.classList.remove(...remove)
  if (add.length > 0) root.classList.add(...add)
}

/**
 * Drives /sandbox/design's theme from the URL and paints it on <html>, where
 * portaled overlays (dialogs, sheets, popovers under <body>) pick it up too.
 *
 * Never writes the viewer's preferences. next-themes and ColorWorldProvider are
 * only read, so leaving the page hands back exactly the classes the app wants.
 */
export function useDesignTheme(): {
  theme: DesignTheme
  themeKey: string
  setTheme: (patch: Partial<DesignTheme>) => void
} {
  const params = useSearchParams()
  const router = useRouter()
  const pathname = usePathname()
  const theme = useMemo(() => parseDesignTheme(params), [params])
  const themeKey = serializeDesignTheme(theme)

  const { resolvedTheme } = useTheme()
  const { colorWorld } = useColorWorld()
  const appClassesRef = useRef<string[]>([])
  useEffect(() => {
    appClassesRef.current = appClassesFor(resolvedTheme === "dark" ? "dark" : "light", colorWorld)
  }, [resolvedTheme, colorWorld])

  useEffect(() => {
    const target = designClassesFor(parseDesignTheme(new URLSearchParams(themeKey)))
    applyClasses(target)
    // ColorWorldProvider and next-themes write <html> classes too, and on mount
    // their effects run after this one (parents after children). Re-apply
    // whenever someone else changes the list. applyClasses mutates nothing when
    // nothing differs, so the observer cannot loop.
    const observer = new MutationObserver(() => applyClasses(target))
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ["class"] })
    return () => observer.disconnect()
  }, [themeKey])

  // Declared after the effect above on purpose: React runs cleanups in
  // declaration order, so the observer is disconnected before this restores
  // the app's classes (otherwise it would put the design classes straight back).
  useEffect(() => () => applyClasses(appClassesRef.current), [])

  const setTheme = useCallback(
    (patch: Partial<DesignTheme>) => {
      const next = serializeDesignTheme({ ...parseDesignTheme(new URLSearchParams(themeKey)), ...patch })
      router.replace(`${pathname}?${next}`, { scroll: false })
    },
    [pathname, router, themeKey],
  )

  return { theme, themeKey, setTheme }
}
```

`react-hooks/exhaustive-deps` may warn that `appClassesRef.current` "will likely have changed" by the cleanup. That's the intent here: we want the latest preferences at leave time. If it warns, put `// eslint-disable-next-line react-hooks/exhaustive-deps -- reads the viewer's preferences at leave time, by design` above that effect.

- [ ] **Step 2: Layout primitives**

`apps/web/components/sandbox/design/DesignSection.tsx`:

```tsx
import type { ReactNode } from "react"

type DesignSectionProps = {
  id: string
  title: string
  description: string
  children: ReactNode
}

export function DesignSection({ id, title, description, children }: DesignSectionProps) {
  return (
    <section id={id} aria-labelledby={`${id}-title`} className="flex scroll-mt-32 flex-col gap-10">
      <header className="flex flex-col gap-1">
        <h2 id={`${id}-title`} className="font-heading text-3xl">
          {title}
        </h2>
        <p className="max-w-2xl text-sm text-muted-foreground">{description}</p>
      </header>
      {children}
    </section>
  )
}
```

`apps/web/components/sandbox/design/Specimen.tsx`:

```tsx
import type { ReactNode } from "react"
import { Badge } from "@/components/ui/badge"
import { getSpecimen, type SpecimenId } from "@/lib/design/specimens"
import { cn } from "@/lib/utils"

type SpecimenProps = {
  id: SpecimenId
  /** A known limitation of rendering this specimen from fixtures. */
  note?: string
  className?: string
  children: ReactNode
}

/** One registry entry on the design page: name, source, M3 status, then the rendered states. */
export function Specimen({ id, note, className, children }: SpecimenProps) {
  const entry = getSpecimen(id)
  return (
    <article id={`specimen-${id}`} aria-labelledby={`specimen-${id}-title`} className="flex flex-col gap-3">
      <header className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
        <h3 id={`specimen-${id}-title`} className="font-heading text-xl">
          {entry.name}
        </h3>
        <code className="text-xs text-muted-foreground">{entry.source}</code>
        <Badge variant={entry.m3 === "tuned" ? "default" : "outline"}>{entry.m3}</Badge>
      </header>
      {note && <p className="max-w-2xl text-sm text-muted-foreground">{note}</p>}
      <div
        className={cn(
          "flex flex-wrap items-start gap-4 rounded-xl border border-dashed border-border p-4",
          className,
        )}
      >
        {children}
      </div>
    </article>
  )
}
```

`apps/web/components/sandbox/design/SpecimenState.tsx`:

```tsx
import type { ReactNode } from "react"

/** A captioned state inside a Specimen ("disabled", "invalid", a variant name…). */
export function SpecimenState({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1.5">
      <span className="font-mono text-[11px] text-muted-foreground">{label}</span>
      {children}
    </div>
  )
}
```

- [ ] **Step 3: Fixture providers**

`apps/web/components/sandbox/design/FixtureProviders.tsx`:

```tsx
"use client"

import { useMemo, useState, type ReactNode } from "react"
import { AuthContext, type AuthContextValue } from "@/lib/data/auth-context"
import { DataContext, type DataContextValue } from "@/lib/data/provider-context"
import { DESIGN_ACCOUNT, DESIGN_PROFILE, designStore } from "@/lib/seed/design-fixtures"

const noop = async () => {}

// Every write resolves without effect: the design page must never reach Supabase.
const FIXTURE_AUTH: AuthContextValue = {
  user: DESIGN_ACCOUNT,
  profile: DESIGN_PROFILE,
  isAuthenticated: true,
  isLoading: false,
  isProfileLoading: false,
  login: noop,
  register: noop,
  signInWithApple: noop,
  signInWithGoogle: noop,
  logout: noop,
  updateProfile: async () => DESIGN_PROFILE,
  setHandle: async (handle) => handle,
  updatePassword: noop,
  deleteAccount: noop,
}

/**
 * Shadows the root AuthProvider and DataProvider for everything the design
 * page renders. `provider: null` makes the data hooks read the fixture store;
 * their provider-backed reads (achievements, ripple, draft count) fall back to
 * their empty values.
 */
export function FixtureProviders({ children }: { children: ReactNode }) {
  const [store, setStore] = useState(() => designStore(new Date()))
  const data = useMemo<DataContextValue>(
    () => ({ provider: null, store, setStore, loading: false, error: null, refreshStore: () => {} }),
    [store],
  )
  return (
    <AuthContext.Provider value={FIXTURE_AUTH}>
      <DataContext.Provider value={data}>{children}</DataContext.Provider>
    </AuthContext.Provider>
  )
}
```

- [ ] **Step 4: Toolbar**

`apps/web/components/sandbox/design/DesignToolbar.tsx`:

```tsx
"use client"

import { Button } from "@/components/ui/button"
import { COLOR_WORLDS } from "@/lib/config/color-worlds"
import { tuningProgress } from "@/lib/design/specimens"
import type { DesignContrast, DesignMode, DesignTheme, DesignThemeName } from "@/lib/theme/design-theme"

type Option<T extends string> = { value: T; label: string }

const THEME_OPTIONS: Option<DesignThemeName>[] = [
  { value: "current", label: "Current" },
  { value: "m3", label: "M3" },
]
const MODE_OPTIONS: Option<DesignMode>[] = [
  { value: "light", label: "Light" },
  { value: "dark", label: "Dark" },
]
const CONTRAST_OPTIONS: Option<DesignContrast>[] = [
  { value: "standard", label: "Standard" },
  { value: "medium", label: "Medium" },
  { value: "high", label: "High" },
]
const SECTIONS = [
  { id: "foundations", label: "Foundations" },
  { id: "primitives", label: "Primitives" },
  { id: "components", label: "Components" },
  { id: "views", label: "Views" },
]

function Segmented<T extends string>({
  label,
  options,
  value,
  onChange,
}: {
  label: string
  options: Option<T>[]
  value: T
  onChange: (next: T) => void
}) {
  return (
    <div role="group" aria-label={label} className="flex items-center gap-1">
      <span className="mr-1 text-xs text-muted-foreground">{label}</span>
      {options.map((option) => (
        <Button
          key={option.value}
          size="xs"
          variant={option.value === value ? "default" : "outline"}
          aria-pressed={option.value === value}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </Button>
      ))}
    </div>
  )
}

type DesignToolbarProps = {
  theme: DesignTheme
  onChange: (patch: Partial<DesignTheme>) => void
}

export function DesignToolbar({ theme, onChange }: DesignToolbarProps) {
  const { tuned, total } = tuningProgress()
  return (
    <header className="sticky top-0 z-40 border-b border-border bg-background/90 backdrop-blur">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-5 gap-y-2 px-4 py-3">
        <h1 className="font-heading text-lg">Design</h1>
        <Segmented label="Theme" options={THEME_OPTIONS} value={theme.theme} onChange={(v) => onChange({ theme: v })} />
        {theme.theme === "current" ? (
          <label className="flex items-center gap-2 text-xs text-muted-foreground">
            World
            <select
              className="h-6 rounded-md border border-border bg-card px-1 text-xs text-foreground"
              value={theme.world}
              onChange={(e) => {
                const world = COLOR_WORLDS.find((w) => w.id === e.target.value)
                if (world) onChange({ world: world.id })
              }}
            >
              {COLOR_WORLDS.map((w) => (
                <option key={w.id} value={w.id}>
                  {w.label}
                </option>
              ))}
            </select>
          </label>
        ) : (
          <Segmented
            label="Contrast"
            options={CONTRAST_OPTIONS}
            value={theme.contrast}
            onChange={(v) => onChange({ contrast: v })}
          />
        )}
        <Segmented label="Mode" options={MODE_OPTIONS} value={theme.mode} onChange={(v) => onChange({ mode: v })} />
        <span className="text-xs text-muted-foreground">
          {tuned}/{total} tuned · <kbd>T</kbd> theme · <kbd>D</kbd> mode
        </span>
        <nav aria-label="Sections" className="flex gap-3 text-xs">
          {SECTIONS.map((s) => (
            <a key={s.id} href={`#${s.id}`} className="underline-offset-4 hover:underline">
              {s.label}
            </a>
          ))}
        </nav>
      </div>
    </header>
  )
}
```

- [ ] **Step 5: Screen and route**

`apps/web/components/sandbox/design/DesignScreen.tsx` (first version; each section task adds its section to `<main>`):

```tsx
"use client"

import { useEffect, type MouseEvent } from "react"
import { primeEmotionPalettes } from "@/lib/data/useEmotionPalettes"
import { SANDBOX_PALETTES } from "@/lib/seed/sandbox-palettes"
import { DesignToolbar } from "./DesignToolbar"
import { FixtureProviders } from "./FixtureProviders"
import { useDesignTheme } from "./useDesignTheme"

// Seed the palette cache at module scope, before any consumer mounts (the
// /sandbox/path precedent): pebbles render tinted without a Supabase call.
primeEmotionPalettes(SANDBOX_PALETTES)

function isTyping(target: EventTarget | null): boolean {
  return (
    target instanceof HTMLElement &&
    (target.isContentEditable || ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName))
  )
}

// Specimens contain real links (cards, tiles, headers). Capture-phase
// preventDefault stops the browser; stopPropagation stops next/link's handler.
// React events follow the React tree, so this covers portaled overlays too.
function blockLinkNavigation(e: MouseEvent) {
  if (e.target instanceof Element && e.target.closest("a[href]")) {
    e.preventDefault()
    e.stopPropagation()
  }
}

/**
 * /sandbox/design (#986): every web component, rendered from fixtures, under a
 * Current ↔ M3 × light/dark × contrast switcher. Unauthenticated,
 * network-free, never writes the viewer's preferences.
 */
export function DesignScreen() {
  const { theme, themeKey, setTheme } = useDesignTheme()

  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      if (e.metaKey || e.ctrlKey || e.altKey || isTyping(e.target)) return
      const key = e.key.toLowerCase()
      if (key === "t") setTheme({ theme: theme.theme === "m3" ? "current" : "m3" })
      if (key === "d") setTheme({ mode: theme.mode === "dark" ? "light" : "dark" })
    }
    window.addEventListener("keydown", onKeyDown)
    return () => window.removeEventListener("keydown", onKeyDown)
  }, [theme, setTheme])

  return (
    <div className="min-h-dvh bg-background text-foreground">
      <DesignToolbar theme={theme} onChange={setTheme} />
      <FixtureProviders>
        <main
          data-theme-key={themeKey}
          onClickCapture={blockLinkNavigation}
          className="mx-auto flex max-w-6xl flex-col gap-20 px-4 py-10"
        />
      </FixtureProviders>
    </div>
  )
}
```

`apps/web/app/sandbox/design/page.tsx`:

```tsx
import { Suspense } from "react"
import type { Metadata } from "next"
import { DesignScreen } from "@/components/sandbox/design/DesignScreen"

export const metadata: Metadata = { title: "Design" }

// DesignScreen reads its theme from the URL (useSearchParams); Next 16 needs a
// Suspense boundary around that for a prerendered route, or the build fails.
export default function DesignPage() {
  return (
    <Suspense fallback={null}>
      <DesignScreen />
    </Suspense>
  )
}
```

(`noindex` comes from `app/sandbox/layout.tsx`.)

- [ ] **Step 6: Lint, typecheck, build**

Run:
```bash
npm run lint --workspace=apps/web
npm run build --workspace=apps/web
```
Expected: green, with `/sandbox/design` in the build's route list.

- [ ] **Step 7: Verify the switcher and the race in the browser**

Run `npm run dev --workspace=apps/web` and open `http://localhost:3000/sandbox/design`. With devtools:
1. `document.documentElement.className` contains `m3` and no color-world class, and the page background is `#FFF8F7`.
2. `T` flips to Current (no `m3`), and `D` flips `dark`. The URL updates each time. Both keys are ignored while focus is in the World `<select>`.
3. **The race:** set your real color world to Moss Pool and your real theme to dark (Settings → Appearance), then open `/sandbox/design?theme=m3&mode=light` with a **hard reload**. `<html>` must end with `m3` and without `moss-pool` / `dark`. If it doesn't, the observer isn't re-applying; debug that before moving on.
4. **Restore:** navigate client-side from the design page to `/path` (browser back, or run `window.history.back()` after arriving from `/path`). `<html>` goes back to `moss-pool dark`, and `localStorage.getItem("pbbls-color-world")` is still `"moss-pool"`.
5. Put your real preferences back afterwards.

- [ ] **Step 8: Commit**

```bash
git add apps/web/components/sandbox/design apps/web/app/sandbox/design
git commit -m "feat(web): add the design page shell and theme switcher"
```

### Task 2.6: Foundations

**Files:**
- Create: `apps/web/components/sandbox/design/foundations/ColorSwatch.tsx`, `M3Roles.tsx`, `BridgeTable.tsx`, `TypeScale.tsx`, `ShapeScale.tsx`, `FoundationsSection.tsx`
- Modify: `apps/web/components/sandbox/design/DesignScreen.tsx`

- [ ] **Step 1: ColorSwatch**

```tsx
"use client"

import { useEffect, useState } from "react"
import { contrastRatio, toHex, type Rgb } from "@/lib/theme/contrast"
import { cn } from "@/lib/utils"

/**
 * Resolves a custom property to sRGB. getComputedStyle hands back whatever
 * syntax the token was written in (hex, oklch…), so paint it on one canvas
 * pixel and read that back.
 */
function resolveVar(name: string): Rgb | null {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  const ctx = value ? document.createElement("canvas").getContext("2d") : null
  if (!ctx) return null
  ctx.fillStyle = value
  ctx.fillRect(0, 0, 1, 1)
  const [r, g, b] = ctx.getImageData(0, 0, 1, 1).data
  return [r, g, b]
}

type ColorSwatchProps = {
  label: string
  /** Custom property for the fill, e.g. "--m3-primary". */
  background: string
  /** Custom property for the text drawn on it; enables the contrast check. */
  foreground?: string
  /** Changes whenever the theme does, so the swatch re-reads its values. */
  themeKey: string
}

export function ColorSwatch({ label, background, foreground, themeKey }: ColorSwatchProps) {
  const [resolved, setResolved] = useState<{ bg: Rgb | null; fg: Rgb | null } | null>(null)

  useEffect(() => {
    // One frame late on purpose: the page applies its <html> classes in its own
    // effect, which runs after this child's effect.
    const frame = requestAnimationFrame(() =>
      setResolved({ bg: resolveVar(background), fg: foreground ? resolveVar(foreground) : null }),
    )
    return () => cancelAnimationFrame(frame)
  }, [background, foreground, themeKey])

  const ratio = resolved?.bg && resolved.fg ? contrastRatio(resolved.bg, resolved.fg) : null

  return (
    <figure className="flex w-44 flex-col overflow-hidden rounded-lg border border-border text-xs">
      <div
        className="flex h-16 items-end p-2"
        style={{ background: `var(${background})`, color: foreground ? `var(${foreground})` : undefined }}
      >
        {foreground && <span className="font-medium">Aa {foreground.replace(/^--(m3-)?/, "")}</span>}
      </div>
      <figcaption className="flex flex-col gap-0.5 bg-card p-2 text-card-foreground">
        <span className="font-mono">{label}</span>
        <span className="font-mono text-muted-foreground">{resolved?.bg ? toHex(resolved.bg) : "—"}</span>
        {ratio !== null && (
          <span className={cn("font-mono", ratio < 4.5 ? "text-destructive" : "text-muted-foreground")}>
            {ratio.toFixed(2)}:1{ratio < 4.5 ? " · below AA" : ""}
          </span>
        )}
      </figcaption>
    </figure>
  )
}
```

- [ ] **Step 2: M3Roles**

```tsx
import { ColorSwatch } from "./ColorSwatch"

type Pair = readonly [background: string, foreground?: string]

const GROUPS: { title: string; pairs: Pair[] }[] = [
  { title: "Primary", pairs: [["primary", "on-primary"], ["primary-container", "on-primary-container"], ["inverse-primary"]] },
  { title: "Secondary", pairs: [["secondary", "on-secondary"], ["secondary-container", "on-secondary-container"]] },
  { title: "Tertiary", pairs: [["tertiary", "on-tertiary"], ["tertiary-container", "on-tertiary-container"]] },
  { title: "Error (amber)", pairs: [["error", "on-error"], ["error-container", "on-error-container"]] },
  {
    title: "Surface",
    pairs: [
      ["surface", "on-surface"],
      ["surface-variant", "on-surface-variant"],
      ["surface-dim", "on-surface"],
      ["surface-bright", "on-surface"],
      ["inverse-surface", "inverse-on-surface"],
    ],
  },
  {
    title: "Surface containers",
    pairs: [
      ["surface-container-lowest", "on-surface"],
      ["surface-container-low", "on-surface"],
      ["surface-container", "on-surface"],
      ["surface-container-high", "on-surface"],
      ["surface-container-highest", "on-surface"],
    ],
  },
  { title: "Outline", pairs: [["outline"], ["outline-variant"], ["scrim"]] },
  {
    title: "Fixed",
    pairs: [
      ["primary-fixed", "on-primary-fixed"],
      ["primary-fixed-dim", "on-primary-fixed-variant"],
      ["secondary-fixed", "on-secondary-fixed"],
      ["secondary-fixed-dim", "on-secondary-fixed-variant"],
      ["tertiary-fixed", "on-tertiary-fixed"],
      ["tertiary-fixed-dim", "on-tertiary-fixed-variant"],
    ],
  },
]

export function M3Roles({ themeKey }: { themeKey: string }) {
  return (
    <div className="flex flex-col gap-6">
      <h3 className="font-heading text-xl">M3 roles</h3>
      {GROUPS.map((group) => (
        <div key={group.title} className="flex flex-col gap-2">
          <h4 className="text-sm font-medium">{group.title}</h4>
          <div className="flex flex-wrap gap-3">
            {group.pairs.map(([bg, fg]) => (
              <ColorSwatch
                key={bg}
                label={bg}
                background={`--m3-${bg}`}
                foreground={fg ? `--m3-${fg}` : undefined}
                themeKey={themeKey}
              />
            ))}
          </div>
        </div>
      ))}
    </div>
  )
}
```

- [ ] **Step 3: BridgeTable**

```tsx
import { M3_BRIDGE } from "@/lib/theme/m3-bridge"

function Chip({ color }: { color: string }) {
  return <span aria-hidden className="size-4 shrink-0 rounded-sm border border-border" style={{ background: color }} />
}

export function BridgeTable() {
  return (
    <div className="flex flex-col gap-3">
      <h3 className="font-heading text-xl">Bridge</h3>
      <p className="max-w-2xl text-sm text-muted-foreground">
        Each app token and the M3 role it points at under <code>.m3</code> (globals.css). A tuned component
        skips the bridge and reads the role directly.
      </p>
      <table className="w-full max-w-xl text-sm">
        <thead>
          <tr className="text-left text-xs text-muted-foreground">
            <th className="py-1 font-medium">App token</th>
            <th className="py-1 font-medium">M3 role</th>
          </tr>
        </thead>
        <tbody>
          {M3_BRIDGE.map(([token, role]) => (
            <tr key={token} className="border-t border-border">
              <td className="py-1.5">
                <span className="inline-flex items-center gap-2">
                  <Chip color={`var(--${token})`} />
                  <code>--{token}</code>
                </span>
              </td>
              <td className="py-1.5">
                <span className="inline-flex items-center gap-2">
                  <Chip color={`var(--m3-${role})`} />
                  <code>{role}</code>
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
```

- [ ] **Step 4: TypeScale and ShapeScale**

Tailwind only generates classes it finds written out in full, so every class below is a literal string.

`TypeScale.tsx`:

```tsx
const SAMPLE = "Lunch on the wall by the canal"

const M3_STYLES = [
  { label: "display-large", className: "m3-type-display-large" },
  { label: "display-medium", className: "m3-type-display-medium" },
  { label: "display-small", className: "m3-type-display-small" },
  { label: "headline-large", className: "m3-type-headline-large" },
  { label: "headline-medium", className: "m3-type-headline-medium" },
  { label: "headline-small", className: "m3-type-headline-small" },
  { label: "title-large", className: "m3-type-title-large" },
  { label: "title-medium", className: "m3-type-title-medium" },
  { label: "title-small", className: "m3-type-title-small" },
  { label: "body-large", className: "m3-type-body-large" },
  { label: "body-medium", className: "m3-type-body-medium" },
  { label: "body-small", className: "m3-type-body-small" },
  { label: "label-large", className: "m3-type-label-large" },
  { label: "label-medium", className: "m3-type-label-medium" },
  { label: "label-small", className: "m3-type-label-small" },
]

const APP_FACES = [
  { label: "font-sans (app body)", className: "font-sans text-base" },
  { label: "font-heading (Ysabeau)", className: "font-heading text-2xl" },
  { label: "font-script (Reenie Beanie)", className: "font-script text-3xl" },
  { label: "font-hand (Caveat)", className: "font-hand text-2xl" },
]

function Row({ label, className }: { label: string; className: string }) {
  return (
    <div className="flex flex-col gap-0.5 border-t border-border py-2">
      <span className="font-mono text-[11px] text-muted-foreground">{label}</span>
      <p className={className}>{SAMPLE}</p>
    </div>
  )
}

export function TypeScale() {
  return (
    <div className="grid gap-8 md:grid-cols-2">
      <div className="flex flex-col">
        <h3 className="mb-2 font-heading text-xl">M3 type scale</h3>
        {M3_STYLES.map((s) => (
          <Row key={s.label} {...s} />
        ))}
      </div>
      <div className="flex flex-col">
        <h3 className="mb-2 font-heading text-xl">App faces</h3>
        {APP_FACES.map((s) => (
          <Row key={s.label} {...s} />
        ))}
      </div>
    </div>
  )
}
```

`ShapeScale.tsx`:

```tsx
import { cn } from "@/lib/utils"

const M3_SHAPES = [
  { label: "none · 0", className: "rounded-m3-none" },
  { label: "extra-small · 4", className: "rounded-m3-extra-small" },
  { label: "small · 8", className: "rounded-m3-small" },
  { label: "medium · 12", className: "rounded-m3-medium" },
  { label: "large · 16", className: "rounded-m3-large" },
  { label: "large-increased · 20", className: "rounded-m3-large-increased" },
  { label: "extra-large · 28", className: "rounded-m3-extra-large" },
  { label: "extra-large-increased · 32", className: "rounded-m3-extra-large-increased" },
  { label: "extra-extra-large · 48", className: "rounded-m3-extra-extra-large" },
  { label: "full", className: "rounded-m3-full" },
]

// Derived from --radius, which .m3 moves to 12px: this row shows what the
// bridge does to every untuned rounded-* in the app.
const APP_RADII = [
  { label: "rounded-sm", className: "rounded-sm" },
  { label: "rounded-md", className: "rounded-md" },
  { label: "rounded-lg", className: "rounded-lg" },
  { label: "rounded-xl", className: "rounded-xl" },
  { label: "rounded-2xl", className: "rounded-2xl" },
  { label: "rounded-3xl", className: "rounded-3xl" },
  { label: "rounded-4xl", className: "rounded-4xl" },
]

function Tiles({ title, shapes }: { title: string; shapes: { label: string; className: string }[] }) {
  return (
    <div className="flex flex-col gap-2">
      <h3 className="font-heading text-xl">{title}</h3>
      <div className="flex flex-wrap gap-4">
        {shapes.map((s) => (
          <figure key={s.label} className="flex w-24 flex-col items-center gap-1">
            <div className={cn("size-20 border border-primary bg-primary/15", s.className)} />
            <figcaption className="text-center font-mono text-[11px] text-muted-foreground">{s.label}</figcaption>
          </figure>
        ))}
      </div>
    </div>
  )
}

export function ShapeScale() {
  return (
    <div className="flex flex-col gap-8">
      <Tiles title="M3 shapes" shapes={M3_SHAPES} />
      <Tiles title="App radii (from --radius)" shapes={APP_RADII} />
    </div>
  )
}
```

- [ ] **Step 5: FoundationsSection, wired into the screen**

```tsx
import type { DesignTheme } from "@/lib/theme/design-theme"
import { DesignSection } from "../DesignSection"
import { BridgeTable } from "./BridgeTable"
import { ColorSwatch } from "./ColorSwatch"
import { M3Roles } from "./M3Roles"
import { ShapeScale } from "./ShapeScale"
import { TypeScale } from "./TypeScale"

const APP_TOKENS: readonly (readonly [background: string, foreground?: string])[] = [
  ["background", "foreground"],
  ["surface", "foreground"],
  ["surface-alt", "foreground"],
  ["card", "card-foreground"],
  ["popover", "popover-foreground"],
  ["primary", "primary-foreground"],
  ["secondary", "secondary-foreground"],
  ["muted", "muted-foreground"],
  ["accent", "accent-foreground"],
  ["destructive", "destructive-foreground"],
  ["border"],
  ["input"],
  ["ring"],
]

type FoundationsSectionProps = { theme: DesignTheme; themeKey: string }

export function FoundationsSection({ theme, themeKey }: FoundationsSectionProps) {
  return (
    <DesignSection
      id="foundations"
      title="Foundations"
      description="Live values read from the page's computed styles, so what you see is what is applied. Pairs show their text contrast; anything under 4.5:1 is flagged."
    >
      <div className="flex flex-col gap-3">
        <h3 className="font-heading text-xl">App tokens</h3>
        <p className="max-w-2xl text-sm text-muted-foreground">
          What every component reads today. Under M3 they come from the bridge.
        </p>
        <div className="flex flex-wrap gap-3">
          {APP_TOKENS.map(([bg, fg]) => (
            <ColorSwatch
              key={bg}
              label={bg}
              background={`--${bg}`}
              foreground={fg ? `--${fg}` : undefined}
              themeKey={themeKey}
            />
          ))}
        </div>
      </div>
      {theme.theme === "m3" ? (
        <>
          <M3Roles themeKey={themeKey} />
          <BridgeTable />
        </>
      ) : (
        <p className="text-sm text-muted-foreground">
          Switch to M3 (press <kbd>T</kbd>) to see the role palette and the bridge.
        </p>
      )}
      <TypeScale />
      <ShapeScale />
    </DesignSection>
  )
}
```

In `DesignScreen.tsx`, add `import { FoundationsSection } from "./foundations/FoundationsSection"` and turn the self-closing `<main … />` into:

```tsx
        <main
          data-theme-key={themeKey}
          onClickCapture={blockLinkNavigation}
          className="mx-auto flex max-w-6xl flex-col gap-20 px-4 py-10"
        >
          <FoundationsSection theme={theme} themeKey={themeKey} />
        </main>
```

- [ ] **Step 6: Verify**

Run `npm run lint --workspace=apps/web`, then open `/sandbox/design` in the browser:
- Under M3 light standard, the `primary` swatch reads `#8E4955` and `error` reads `#7F560F`. Switching contrast to High changes both to the high-contrast values in `app/m3-theme.css`.
- Under Current, the app tokens show Blush Quartz (`primary` `#C07A7A`), and the M3 block is replaced by the hint.
- The type rows render Ysabeau for display/headline/title and Inclusive Sans for body/label.

- [ ] **Step 7: Commit**

```bash
git add apps/web/components/sandbox/design
git commit -m "feat(web): show colour, type and shape foundations on the design page"
```

### Task 2.7: Primitives

**Files:**
- Create: `apps/web/components/sandbox/design/primitives/OverlaySpecimens.tsx`, `PrimitivesSection.tsx`
- Modify: `apps/web/components/sandbox/design/DesignScreen.tsx`

API notes for this codebase (shadcn base-nova on `@base-ui/react`):
- Triggers take `render={<Button … />}`. They don't take `asChild`.
- `DialogClose`, `SheetClose`, `AlertDialogCancel` and `AlertDialogAction` take Button's `variant`/`size` props directly.
- `DropdownMenuItem` has no `variant`.

- [ ] **Step 1: OverlaySpecimens**

```tsx
"use client"

import { useState } from "react"
import { toast } from "sonner"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet"
import { ConfirmDialog } from "@/components/ui/ConfirmDialog"
import { PickerSheet } from "@/components/ui/PickerSheet"
import { SelectableItem } from "@/components/ui/SelectableItem"
import { SANDBOX_SOULS } from "@/lib/seed/sandbox-pebbles"
import { Specimen } from "../Specimen"

/** Primitives that portal to <body>: each behind a trigger, themed through <html>. */
export function OverlaySpecimens() {
  const [sort, setSort] = useState("newest")
  const [soulId, setSoulId] = useState(SANDBOX_SOULS[0].id)

  return (
    <>
      <Specimen id="dialog">
        <Dialog>
          <DialogTrigger render={<Button variant="outline" />}>Open dialog</DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Rename collection</DialogTitle>
              <DialogDescription>Dialogs sit on the popover surface, over the scrim.</DialogDescription>
            </DialogHeader>
            <DialogFooter>
              <DialogClose>Cancel</DialogClose>
              <DialogClose variant="default">Save</DialogClose>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </Specimen>

      <Specimen id="alert-dialog">
        <AlertDialog>
          <AlertDialogTrigger render={<Button variant="outline" />}>Open alert dialog</AlertDialogTrigger>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>Discard this pebble?</AlertDialogTitle>
              <AlertDialogDescription>Alert dialogs ask before something can’t be undone.</AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>Keep</AlertDialogCancel>
              <AlertDialogAction>Discard</AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      </Specimen>

      <Specimen id="confirm-dialog">
        <ConfirmDialog
          trigger={<Button variant="destructive">Delete collection</Button>}
          title="Delete this collection?"
          description="The pebbles stay on your path."
          confirmLabel="Delete"
          onConfirm={() => {}}
        />
        <ConfirmDialog
          trigger={<Button variant="outline">Leave without saving</Button>}
          title="Leave without saving?"
          description="Your changes to this pebble will be lost."
          variant="default"
          confirmLabel="Leave"
          onConfirm={() => {}}
        />
      </Specimen>

      <Specimen id="sheet">
        <Sheet>
          <SheetTrigger render={<Button variant="outline" />}>Open sheet</SheetTrigger>
          <SheetContent>
            <SheetHeader>
              <SheetTitle>Sheet title</SheetTitle>
            </SheetHeader>
            <p className="text-sm text-muted-foreground">A bottom sheet on mobile, a side panel from md up.</p>
          </SheetContent>
        </Sheet>
      </Specimen>

      <Specimen id="picker-sheet">
        <PickerSheet
          title="Pick a soul"
          closeLabel="Close"
          trigger={<SheetTrigger render={<Button variant="outline" />}>Open picker sheet</SheetTrigger>}
          footer={<Button className="w-full">Done</Button>}
        >
          <div role="radiogroup" aria-label="Souls" className="flex flex-col gap-1">
            {SANDBOX_SOULS.map((soul) => (
              <SelectableItem
                key={soul.id}
                role="radio"
                selected={soul.id === soulId}
                onSelect={() => setSoulId(soul.id)}
                showCheck
              >
                {soul.name}
              </SelectableItem>
            ))}
          </div>
        </PickerSheet>
      </Specimen>

      <Specimen id="popover">
        <Popover>
          <PopoverTrigger render={<Button variant="outline" />}>Open popover</PopoverTrigger>
          <PopoverContent className="w-64 text-sm">Popovers use the popover surface.</PopoverContent>
        </Popover>
      </Specimen>

      <Specimen id="dropdown-menu">
        <DropdownMenu>
          <DropdownMenuTrigger render={<Button variant="outline" />}>Open menu</DropdownMenuTrigger>
          <DropdownMenuContent align="start">
            <DropdownMenuItem>Edit</DropdownMenuItem>
            <DropdownMenuItem>Duplicate</DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuRadioGroup value={sort} onValueChange={(value) => setSort(String(value))}>
              <DropdownMenuRadioItem value="newest">Newest first</DropdownMenuRadioItem>
              <DropdownMenuRadioItem value="oldest">Oldest first</DropdownMenuRadioItem>
            </DropdownMenuRadioGroup>
          </DropdownMenuContent>
        </DropdownMenu>
      </Specimen>

      <Specimen id="toast">
        <Button variant="outline" onClick={() => toast("Pebble saved")}>
          Default
        </Button>
        <Button variant="outline" onClick={() => toast.success("Pebble saved")}>
          Success
        </Button>
        <Button variant="outline" onClick={() => toast.error("Couldn’t save the pebble")}>
          Error
        </Button>
        <Button variant="outline" onClick={() => toast.info("Drafts sync when you’re back online")}>
          Info
        </Button>
      </Specimen>
    </>
  )
}
```

- [ ] **Step 2: PrimitivesSection**

```tsx
"use client"

import { useState } from "react"
import { Plus } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Calendar } from "@/components/ui/calendar"
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import { EmotionBadge } from "@/components/ui/EmotionBadge"
import { SearchableList } from "@/components/ui/SearchableList"
import { SectionLabel } from "@/components/ui/SectionLabel"
import { SelectableItem } from "@/components/ui/SelectableItem"
import { TagList } from "@/components/ui/TagList"
import { EMOTIONS } from "@/lib/config/emotions"
import { SANDBOX_SOULS } from "@/lib/seed/sandbox-pebbles"
import { DesignSection } from "../DesignSection"
import { Specimen } from "../Specimen"
import { SpecimenState } from "../SpecimenState"
import { OverlaySpecimens } from "./OverlaySpecimens"

const BUTTON_VARIANTS = ["default", "outline", "secondary", "ghost", "destructive", "link"] as const
const BUTTON_SIZES = ["xs", "sm", "default", "lg"] as const
const ICON_SIZES = ["icon-xs", "icon-sm", "icon", "icon-lg"] as const
const BADGE_VARIANTS = ["default", "secondary", "destructive", "outline", "ghost", "link"] as const

export function PrimitivesSection() {
  const [day, setDay] = useState<Date | undefined>(() => new Date())
  const [query, setQuery] = useState("")
  const [selected, setSelected] = useState(SANDBOX_SOULS[1].id)
  const matches = SANDBOX_SOULS.filter((s) => s.name.toLowerCase().includes(query.toLowerCase()))

  return (
    <DesignSection
      id="primitives"
      title="Primitives"
      description="Every file in components/ui, in the states that change its colours. Overlays open from their trigger."
    >
      <Specimen id="button">
        <div className="flex flex-col gap-4">
          {BUTTON_VARIANTS.map((variant) => (
            <SpecimenState key={variant} label={variant}>
              <div className="flex flex-wrap items-center gap-2">
                {BUTTON_SIZES.map((size) => (
                  <Button key={size} variant={variant} size={size}>
                    {size}
                  </Button>
                ))}
                {ICON_SIZES.map((size) => (
                  <Button key={size} variant={variant} size={size} aria-label={`${variant} ${size}`}>
                    <Plus />
                  </Button>
                ))}
                <Button variant={variant}>
                  <Plus data-icon="inline-start" />
                  With icon
                </Button>
                <Button variant={variant} disabled>
                  Disabled
                </Button>
              </div>
            </SpecimenState>
          ))}
        </div>
      </Specimen>

      <Specimen id="badge">
        {BADGE_VARIANTS.map((variant) => (
          <Badge key={variant} variant={variant}>
            {variant}
          </Badge>
        ))}
      </Specimen>

      <Specimen id="card">
        <Card className="w-80">
          <CardHeader>
            <CardTitle>Card title</CardTitle>
            <CardDescription>Supporting description text.</CardDescription>
            <CardAction>
              <Button size="sm" variant="ghost">
                Action
              </Button>
            </CardAction>
          </CardHeader>
          <CardContent className="text-sm">Body content sits on the card surface.</CardContent>
          <CardFooter>
            <Button size="sm">Primary</Button>
          </CardFooter>
        </Card>
      </Specimen>

      <Specimen id="checkbox">
        <SpecimenState label="off">
          <Checkbox aria-label="Off" />
        </SpecimenState>
        <SpecimenState label="on">
          <Checkbox aria-label="On" defaultChecked />
        </SpecimenState>
        <SpecimenState label="disabled">
          <Checkbox aria-label="Disabled" disabled />
        </SpecimenState>
        <SpecimenState label="disabled · on">
          <Checkbox aria-label="Disabled on" disabled defaultChecked />
        </SpecimenState>
      </Specimen>

      <Specimen id="input">
        <SpecimenState label="empty">
          <Input className="w-56" placeholder="Name this pebble" />
        </SpecimenState>
        <SpecimenState label="filled">
          <Input className="w-56" defaultValue="Lunch by the canal" />
        </SpecimenState>
        <SpecimenState label="invalid">
          <Input className="w-56" defaultValue="not-an-email" aria-invalid />
        </SpecimenState>
        <SpecimenState label="disabled">
          <Input className="w-56" defaultValue="Can’t touch this" disabled />
        </SpecimenState>
      </Specimen>

      <Specimen id="calendar">
        <Calendar mode="single" selected={day} onSelect={setDay} />
      </Specimen>

      <OverlaySpecimens />

      <Specimen id="emotion-badge">
        {EMOTIONS.slice(0, 6).map((emotion) => (
          <EmotionBadge key={emotion.id} emotion={emotion} />
        ))}
        {EMOTIONS.slice(0, 3).map((emotion) => (
          <EmotionBadge key={`${emotion.id}-md`} emotion={emotion} size="md" />
        ))}
      </Specimen>

      <Specimen id="searchable-list">
        <div className="w-72">
          <SearchableList
            query={query}
            onQueryChange={setQuery}
            placeholder="Search souls"
            isEmpty={matches.length === 0}
            emptyMessage="No soul matches"
          >
            {matches.map((soul) => (
              <SelectableItem key={soul.id} selected={false} onSelect={() => setQuery(soul.name)}>
                {soul.name}
              </SelectableItem>
            ))}
          </SearchableList>
        </div>
      </Specimen>

      <Specimen id="section-label">
        <SectionLabel>Section label</SectionLabel>
      </Specimen>

      <Specimen id="selectable-item">
        <div role="radiogroup" aria-label="Selectable items" className="flex w-72 flex-col gap-1">
          {SANDBOX_SOULS.slice(0, 3).map((soul, i) => (
            <SelectableItem
              key={soul.id}
              role="radio"
              selected={soul.id === selected}
              onSelect={() => setSelected(soul.id)}
              showCheck
              muted={i === 2}
            >
              {soul.name}
              {i === 2 ? " (muted)" : ""}
            </SelectableItem>
          ))}
        </div>
      </Specimen>

      <Specimen id="tag-list">
        <TagList items={SANDBOX_SOULS.map((s) => ({ id: s.id, name: s.name }))} />
      </Specimen>
    </DesignSection>
  )
}
```

If TypeScript rejects `<Calendar mode="single" selected={day} onSelect={setDay} />`, copy the props from an existing `Calendar` call in `components/record/InlineDatePicker.tsx` or `DatePickerDialog.tsx`.

- [ ] **Step 3: Wire into the screen**

In `DesignScreen.tsx`, add `import { PrimitivesSection } from "./primitives/PrimitivesSection"` and put `<PrimitivesSection />` after `<FoundationsSection … />`.

- [ ] **Step 4: Verify**

Run `npm run lint --workspace=apps/web` and `npm run test --workspace=apps/web`. In the browser, open each overlay under M3 light and M3 dark. Each must render on the M3 surface (popover = surface-container), which proves the `<html>` scoping reaches portals. Fire each toast.

- [ ] **Step 5: Commit**

```bash
git add apps/web/components/sandbox/design
git commit -m "feat(web): render every ui primitive on the design page"
```

### Task 2.8: Feature components

**Files:**
- Create: `apps/web/components/sandbox/design/ComponentsSection.tsx`
- Modify: `apps/web/components/sandbox/design/DesignScreen.tsx`

- [ ] **Step 1: ComponentsSection**

```tsx
"use client"

import { Flame, Gem, Leaf, Settings, Sparkles, UserRound } from "lucide-react"
import { Button } from "@/components/ui/button"
import { PageHeader } from "@/components/layout/PageHeader"
import { EmptyState } from "@/components/layout/EmptyState"
import { PebbleCard } from "@/components/path/PebbleCard"
import { PathPebbleRow } from "@/components/path/PathPebbleRow"
import { PathEmptyState } from "@/components/path/PathEmptyState"
import { GamificationBlock } from "@/components/path/GamificationBlock"
import { PebbleVisual } from "@/components/pebble/PebbleVisual"
import { IntensityDots, PositivenessIndicator } from "@/components/pebble/PebbleIndicators"
import { SoulCard } from "@/components/souls/SoulCard"
import { SoulsEmptyState } from "@/components/souls/SoulsEmptyState"
import { CollectionCard } from "@/components/collections/CollectionCard"
import { ModeBadge } from "@/components/collections/ModeBadge"
import { ProfileBanner } from "@/components/profile/ProfileBanner"
import { StatsCard } from "@/components/profile/StatsCard"
import { DataTile } from "@/components/profile/DataTile"
import { ShortcutTile } from "@/components/profile/ShortcutTile"
import { SectionCard } from "@/components/profile/SectionCard"
import { LabCard } from "@/components/profile/LabCard"
import { SettingsGroup } from "@/components/settings/SettingsGroup"
import { SettingsRow } from "@/components/settings/SettingsRow"
import { EMOTIONS } from "@/lib/config/emotions"
import { useCollections } from "@/lib/data/useCollections"
import { usePebbles } from "@/lib/data/usePebbles"
import { DESIGN_ASSIDUITY, DESIGN_PROFILE, DESIGN_RIPPLE } from "@/lib/seed/design-fixtures"
import { SANDBOX_MARK_MAP, SANDBOX_SOULS } from "@/lib/seed/sandbox-pebbles"
import { DesignSection } from "./DesignSection"
import { Specimen } from "./Specimen"

function markOf(id: string | undefined | null) {
  return id ? SANDBOX_MARK_MAP.get(id) : undefined
}

function soulNames(ids: string[]): string[] {
  return SANDBOX_SOULS.filter((s) => ids.includes(s.id)).map((s) => s.name)
}

export function ComponentsSection() {
  const { pebbles } = usePebbles()
  const { collections } = useCollections()

  return (
    <DesignSection
      id="components"
      title="Components"
      description="The most-used feature components, fed by the fixture store. Links are inert on this page."
    >
      <Specimen id="pebble-card">
        {pebbles.slice(0, 2).map((pebble) => (
          <div key={pebble.id} className="w-80">
            <PebbleCard
              pebble={pebble}
              emotion={EMOTIONS.find((e) => e.id === pebble.emotion_id)}
              mark={markOf(pebble.mark_id)}
              soulNames={soulNames(pebble.soul_ids)}
            />
          </div>
        ))}
      </Specimen>

      <Specimen id="path-pebble-row">
        <div className="flex w-full max-w-md flex-col">
          {pebbles.slice(0, 3).map((pebble, i) => (
            <PathPebbleRow key={pebble.id} pebble={pebble} mark={markOf(pebble.mark_id)} positionIndex={i} />
          ))}
        </div>
      </Specimen>

      <Specimen id="path-empty-state">
        <div className="w-full max-w-md">
          <PathEmptyState />
        </div>
      </Specimen>

      <Specimen id="gamification-block">
        <GamificationBlock
          icon={Flame}
          label="Bounce"
          value={3}
          dialogTitle="Bounce"
          dialogDescription="Days in a row with at least one pebble."
        />
        <GamificationBlock
          icon={Gem}
          label="Karma"
          value={42}
          dialogTitle="Karma"
          dialogDescription="Earned by recording and caring for pebbles."
        />
      </Specimen>

      <Specimen id="pebble-visual">
        {pebbles.slice(0, 4).map((pebble) => (
          <div key={pebble.id} className="size-24">
            <PebbleVisual pebble={pebble} mark={markOf(pebble.mark_id)} />
          </div>
        ))}
      </Specimen>

      <Specimen id="pebble-indicators">
        {([1, 2, 3] as const).map((intensity) => (
          <IntensityDots key={intensity} intensity={intensity} />
        ))}
        {[-1, 0, 1].map((value) => (
          <PositivenessIndicator key={value} value={value} />
        ))}
      </Specimen>

      <Specimen id="soul-card">
        {SANDBOX_SOULS.slice(0, 3).map((soul) => (
          <div key={soul.id} className="w-60">
            <SoulCard
              soul={soul}
              mark={markOf(soul.glyph_id)}
              pebbleCount={pebbles.filter((p) => p.soul_ids.includes(soul.id)).length}
            />
          </div>
        ))}
      </Specimen>

      <Specimen id="souls-empty-state">
        <div className="w-full max-w-md">
          <SoulsEmptyState />
        </div>
      </Specimen>

      <Specimen id="collection-card">
        {collections.map((collection) => (
          <div key={collection.id} className="w-72">
            <CollectionCard collection={collection} />
          </div>
        ))}
      </Specimen>

      <Specimen id="mode-badge">
        <ModeBadge mode="stack" />
        <ModeBadge mode="pack" />
        <ModeBadge mode="track" />
      </Specimen>

      <Specimen id="profile-banner">
        <div className="w-full max-w-md">
          <ProfileBanner
            displayName={DESIGN_PROFILE.display_name}
            memberSince="Nov 2, 2025"
            glyph={markOf(DESIGN_PROFILE.glyph_id) ?? null}
          />
        </div>
      </Specimen>

      <Specimen id="stats-card">
        <div className="w-full max-w-md">
          <StatsCard
            ripple={DESIGN_RIPPLE}
            assiduity={DESIGN_ASSIDUITY}
            daysPracticed={19}
            pebbles={pebbles.length}
            karma={42}
          />
        </div>
      </Specimen>

      <Specimen id="data-tile">
        <DataTile value={128} icon={Sparkles} label="Pebbles" />
        <DataTile value={null} icon={Leaf} label="Loading" />
      </Specimen>

      <Specimen id="shortcut-tile">
        <ShortcutTile href="/souls" icon={UserRound} label="Souls" />
        <ShortcutTile href="/settings" icon={Settings} label="Settings" />
      </Specimen>

      <Specimen id="section-card">
        <SectionCard className="w-80">
          <p className="text-sm">Content in a section card.</p>
        </SectionCard>
      </Specimen>

      <Specimen id="lab-card">
        <div className="w-full max-w-md">
          <LabCard />
        </div>
      </Specimen>

      <Specimen id="settings-row">
        <div className="w-full max-w-md">
          <SettingsGroup aria-label="Example settings group">
            <SettingsRow icon={UserRound} href="/settings">
              Linked row
            </SettingsRow>
            <SettingsRow icon={Leaf} trailing={<span className="text-sm text-muted-foreground">On</span>}>
              Row with a trailing value
            </SettingsRow>
            <SettingsRow onClick={() => {}}>Plain row</SettingsRow>
          </SettingsGroup>
        </div>
      </Specimen>

      <Specimen id="page-header">
        <div className="w-full max-w-md">
          <PageHeader title="Page title" backHref="/profile" rightSlot={<Button size="sm">Save</Button>} />
        </div>
      </Specimen>

      <Specimen id="empty-state">
        <div className="w-full max-w-md">
          <EmptyState
            title="Nothing here yet"
            description="Empty states say what will appear here and offer the next step."
            action={<Button>Do the thing</Button>}
          />
        </div>
      </Specimen>
    </DesignSection>
  )
}
```

- [ ] **Step 2: Wire into the screen**

In `DesignScreen.tsx`, add `import { ComponentsSection } from "./ComponentsSection"` and put `<ComponentsSection />` after `<PrimitivesSection />`.

- [ ] **Step 3: Verify**

Run `npm run lint --workspace=apps/web`. In the browser:
- Pebbles are tinted (the palettes were primed).
- Clicking a SoulCard or CollectionCard does **not** navigate.
- The network tab shows no Supabase requests from these specimens. If PebbleVisual requests `v_emotions_with_palette`, the priming missed, and that's a bug to fix, not to accept.

- [ ] **Step 4: Commit**

```bash
git add apps/web/components/sandbox/design
git commit -m "feat(web): render the main feature components on the design page"
```

### Task 2.9: Views

**Files:**
- Create: `apps/web/components/sandbox/design/views/DeviceFrame.tsx`, `PathView.tsx`, `PebbleDetailView.tsx`, `ProfileView.tsx`, `SettingsView.tsx`, `ViewsSection.tsx`
- Modify: `apps/web/components/sandbox/design/DesignScreen.tsx`

- [ ] **Step 1: DeviceFrame**

```tsx
"use client"

import { useState, type ReactNode } from "react"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"

export function DeviceFrame({ children }: { children: ReactNode }) {
  const [fill, setFill] = useState(false)
  return (
    <div className="flex w-full flex-col gap-2">
      <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
        <Button size="xs" variant={fill ? "outline" : "default"} aria-pressed={!fill} onClick={() => setFill(false)}>
          Phone
        </Button>
        <Button size="xs" variant={fill ? "default" : "outline"} aria-pressed={fill} onClick={() => setFill(true)}>
          Fill
        </Button>
        <span>Breakpoints follow the browser window, not the frame: narrow the window for the mobile layout.</span>
      </div>
      {/* translateZ(0) makes the frame the containing block for position:fixed
          descendants (bottom docks, sticky bars), so a view's fixed chrome stays
          inside its frame instead of covering the design page. */}
      <div
        className={cn(
          "h-[760px] overflow-y-auto rounded-3xl border border-border bg-background [transform:translateZ(0)]",
          fill ? "w-full" : "w-[390px] max-w-full",
        )}
      >
        {children}
      </div>
    </div>
  )
}
```

- [ ] **Step 2: The four views**

`PathView.tsx`:

```tsx
"use client"

import { PathScreen } from "@/components/path/PathScreen"
import { usePebbles } from "@/lib/data/usePebbles"
import { useSouls } from "@/lib/data/useSouls"

/** app/path/page.tsx, fed by the fixture store. */
export function PathView() {
  const { pebbles } = usePebbles()
  const { souls } = useSouls()
  return <PathScreen pebbles={pebbles} souls={souls} loading={false} />
}
```

`PebbleDetailView.tsx`:

```tsx
"use client"

import { PageLayout } from "@/components/layout/PageLayout"
import { PebbleDetail } from "@/components/pebble/PebbleDetail"
import { useCollections } from "@/lib/data/useCollections"
import { usePebbles } from "@/lib/data/usePebbles"
import { useSouls } from "@/lib/data/useSouls"
import { useUsableGlyphs } from "@/lib/data/useUsableGlyphs"

/** app/pebble/[id]/page.tsx for the newest fixture pebble. Every write is a no-op. */
export function PebbleDetailView() {
  const { pebbles } = usePebbles()
  const { souls } = useSouls()
  const { collections } = useCollections()
  const { glyphs: marks } = useUsableGlyphs()
  const pebble = pebbles[0]
  if (!pebble) return null

  return (
    <PageLayout>
      <section>
        <PebbleDetail
          pebble={pebble}
          souls={souls}
          collections={collections}
          marks={marks}
          mark={marks.find((m) => m.id === pebble.mark_id)}
          onUpdatePebble={async () => pebble}
          onUploadSnap={async () => ({ id: "design-snap", storage_path: "", sort_order: 0 })}
          onAddSoul={async () => {}}
        />
      </section>
    </PageLayout>
  )
}
```

`ProfileView.tsx`:

```tsx
"use client"

import Link from "next/link"
import { Settings } from "lucide-react"
import { useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"
import { PageHeader } from "@/components/layout/PageHeader"
import { PageLayout } from "@/components/layout/PageLayout"
import { AchievementsShelf } from "@/components/profile/AchievementsShelf"
import { CollectionsCard } from "@/components/profile/CollectionsCard"
import { LabCard } from "@/components/profile/LabCard"
import { LogoutButton } from "@/components/profile/LogoutButton"
import { ProfileBanner } from "@/components/profile/ProfileBanner"
import { ShortcutsRow } from "@/components/profile/ShortcutsRow"
import { StatsCard } from "@/components/profile/StatsCard"
import { usePebbles } from "@/lib/data/usePebbles"
import { useUsableGlyphs } from "@/lib/data/useUsableGlyphs"
import { useFormatDate } from "@/lib/i18n"
import {
  DESIGN_ACCOUNT,
  DESIGN_ASSIDUITY,
  DESIGN_PROFILE,
  DESIGN_RIPPLE,
} from "@/lib/seed/design-fixtures"

/** app/profile/page.tsx's tree, with fixture values where the page reads the network. */
export function ProfileView() {
  const t = useTranslations("profile")
  const formatDate = useFormatDate()
  const { glyphs } = useUsableGlyphs()
  const { pebbles } = usePebbles()
  const glyph = glyphs.find((g) => g.id === DESIGN_PROFILE.glyph_id) ?? null

  return (
    <PageLayout>
      <section>
        <PageHeader
          title={t("title")}
          backHref="/path"
          rightSlot={
            <Button variant="outline" size="icon" aria-label={t("settingsAria")} render={<Link href="/settings" />}>
              <Settings />
            </Button>
          }
        />
        <div className="flex flex-col gap-6">
          <ProfileBanner
            displayName={DESIGN_PROFILE.display_name}
            memberSince={formatDate(DESIGN_ACCOUNT.created_at, { dateStyle: "medium" })}
            glyph={glyph}
          />
          <ShortcutsRow />
          <StatsCard
            ripple={DESIGN_RIPPLE}
            assiduity={DESIGN_ASSIDUITY}
            daysPracticed={19}
            pebbles={pebbles.length}
            karma={42}
          />
          <AchievementsShelf />
          <CollectionsCard />
          <LabCard />
          <LogoutButton onLogout={async () => {}} />
        </div>
      </section>
    </PageLayout>
  )
}
```

`SettingsView.tsx`:

```tsx
"use client"

import { useState } from "react"
import { useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"
import { PageHeader } from "@/components/layout/PageHeader"
import { PageLayout } from "@/components/layout/PageLayout"
import { ConsentSection } from "@/components/settings/ConsentSection"
import { DeleteAccountSection } from "@/components/settings/DeleteAccountSection"
import { GlyphHeader } from "@/components/settings/GlyphHeader"
import { InformationsSection } from "@/components/settings/InformationsSection"
import { LegalSection } from "@/components/settings/LegalSection"
import { PasswordSection } from "@/components/settings/PasswordSection"
import { ProvidersSection } from "@/components/settings/ProvidersSection"
import { PublicProfileSection } from "@/components/settings/PublicProfileSection"
import { useUsableGlyphs } from "@/lib/data/useUsableGlyphs"
import { DESIGN_ACCOUNT, DESIGN_CONSENT, DESIGN_PROFILE } from "@/lib/seed/design-fixtures"

/**
 * app/settings/page.tsx's tree with local, throwaway state. AppearanceSection
 * is left out on purpose: its controls write the viewer's real theme, locale
 * and color world.
 */
export function SettingsView() {
  const t = useTranslations("settings")
  const { glyphs } = useUsableGlyphs()
  const [glyphId, setGlyphId] = useState<string | null>(DESIGN_PROFILE.glyph_id)
  const [name, setName] = useState(DESIGN_PROFILE.display_name)
  const [handle, setHandle] = useState(DESIGN_PROFILE.handle ?? "")
  const [isPublic, setIsPublic] = useState(DESIGN_PROFILE.public_profile)
  const [password, setPassword] = useState("")
  const glyph = glyphs.find((g) => g.id === glyphId) ?? null

  return (
    <PageLayout>
      <section>
        <PageHeader
          title={t("title")}
          backHref="/profile"
          rightSlot={
            <Button size="sm" disabled>
              {t("save")}
            </Button>
          }
        />
        <div className="flex flex-col gap-6">
          <GlyphHeader glyph={glyph} glyphs={glyphs} selectedGlyphId={glyphId} onSelect={setGlyphId} />
          <InformationsSection name={name} onNameChange={setName} email={DESIGN_ACCOUNT.email} />
          <PublicProfileSection
            handle={handle}
            onHandleChange={setHandle}
            handleError={null}
            isPublic={isPublic}
            onPublicChange={setIsPublic}
            savedHandle={DESIGN_PROFILE.handle}
            savedPublic={DESIGN_PROFILE.public_profile}
          />
          <ProvidersSection providers={DESIGN_ACCOUNT.providers ?? []} />
          <PasswordSection value={password} onChange={setPassword} />
          <LegalSection />
          <ConsentSection consent={DESIGN_CONSENT} onGrant={async () => {}} onWithdrawn={() => {}} />
          <DeleteAccountSection onDeleted={() => {}} />
        </div>
      </section>
    </PageLayout>
  )
}
```

- [ ] **Step 3: ViewsSection, wired into the screen**

```tsx
import { DesignSection } from "../DesignSection"
import { Specimen } from "../Specimen"
import { DeviceFrame } from "./DeviceFrame"
import { PathView } from "./PathView"
import { PebbleDetailView } from "./PebbleDetailView"
import { ProfileView } from "./ProfileView"
import { SettingsView } from "./SettingsView"

export function ViewsSection() {
  return (
    <DesignSection
      id="views"
      title="Views"
      description="Whole screens composed the way their app/ pages compose them, fed by the fixture store. Writes do nothing, links are inert."
    >
      <Specimen
        id="view-path"
        note="Tapping a pebble opens the peek; its emotion and domain tiles share the Pebble detail limitation below."
      >
        <DeviceFrame>
          <PathView />
        </DeviceFrame>
      </Specimen>
      <Specimen
        id="view-pebble"
        note="The emotion and domain tiles read reference views from Supabase that cannot be seeded, so here they show their generic fallback."
      >
        <DeviceFrame>
          <PebbleDetailView />
        </DeviceFrame>
      </Specimen>
      <Specimen
        id="view-profile"
        note="The achievements shelf reads through provider methods, not the store, so it shows its empty state."
      >
        <DeviceFrame>
          <ProfileView />
        </DeviceFrame>
      </Specimen>
      <Specimen
        id="view-settings"
        note="Appearance is left out: its controls would change your real theme, locale and color world."
      >
        <DeviceFrame>
          <SettingsView />
        </DeviceFrame>
      </Specimen>
    </DesignSection>
  )
}
```

In `DesignScreen.tsx`, add `import { ViewsSection } from "./views/ViewsSection"` and put `<ViewsSection />` after `<ComponentsSection />`.

- [ ] **Step 4: Verify**

Run:
```bash
npm run lint --workspace=apps/web
npm run test --workspace=apps/web
npm run build --workspace=apps/web
```
Expected: green.

In the browser, check each view:
- It renders inside its frame. The Path bottom dock stays inside the frame, not over the page.
- The Path view opens on a filled week.
- Nothing navigates away.
- There are no uncaught errors. The two known `console.error`s from the pebble detail's emotion/domain tiles are expected; list them in the PR.

- [ ] **Step 5: Commit**

```bash
git add apps/web/components/sandbox/design
git commit -m "feat(web): render path, pebble, profile and settings views on the design page"
```

### Task 2.10: Controller only: full verification, Arkaik, Part 2 PR

- [ ] **Step 1: Full web gate:** `npm run lint`, `npm run test` and `npm run build`, each with `--workspace=apps/web`.
- [ ] **Step 2: Browser matrix:** use claude-in-chrome to open `/sandbox/design` at every combination and screenshot Foundations plus one overlay each time:
  - Current × 5 worlds × 2 modes
  - M3 × 3 contrasts × 2 modes

  Then repeat the race and restore checks from Task 2.5 Step 7. Attach the M3 light/dark standard and Current Blush Quartz light screenshots to the PR.
- [ ] **Step 3: Arkaik:** check the hosted map for a `/sandbox/path` view node (arkaik-mcp `list_nodes`).
  - If there is one, add `/sandbox/design` beside it with the same edges.
  - If there isn't, sandbox pages are off the map, so do nothing.
  - If the arkaik-mcp tools aren't available, say so. Never touch `docs/arkaik/`.
- [ ] **Step 4: Push the stack and open the Part 2 PR** (see the `gh-stack` skill):
  - **Title:** `feat(web): add a design page to tune the material 3 theme`.
  - **Body:** starts with `Resolves #986`. It lists the key files, the known fixture limitations and the screenshots. The limitations are: the pebble detail's emotion/domain tiles, the achievements empty state, Appearance left out, and breakpoints following the window.
  - **Labels:** `feat`, `web`, `ui`, `no-lab-note`.
  - **Milestone:** as confirmed with the maintainer.
