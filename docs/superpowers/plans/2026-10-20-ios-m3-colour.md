# iOS M3 Colour (Stack A) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** iOS draws every interface colour from the Android M3 scheme: it is generated into asset-catalog colorsets and read through `Color.pebbles.<role>`. Errors are orange. Red survives only in system dialogs. A Swift test fails the build on any colour literal in feature code.

**Architecture:** A pure TypeScript module in `apps/web/lib/theme/ios-colorsets.ts` reuses the web's `parseColorSchemes`. It renders four-appearance colorsets (light, dark, and High Contrast for each) plus a generated Swift entry point, `apps/ios/Shared/Theme/PebblesColors.swift`. Two drift gates keep the committed files equal to `ColorSchemes.kt`: Vitest on every PR, and a regenerate-and-diff step in `ios.yml`. On the Swift side, `PebblesColorsTests` proves UIKit honours the appearance keys. `ThemeLiteralsTests` is a per-file ratchet whose `debt` table shrinks part by part until A3b deletes it, along with `Palettes.swift` and the 11 legacy colorsets.

**Tech Stack:** TypeScript + Vitest + tsx (web workspace), SwiftUI / UIKit, iOS 17, Swift Testing, XcodeGen, SwiftLint, GitHub Actions, `gh stack`.

**Spec (the contract):** `docs/superpowers/specs/2026-10-20-ios-m3-colour-design.md`. It holds the D1–D9 decisions, the role-mapping table (§4), the literal test (§7) and the decision-log draft (§11).

---

## Before you start

- This plan lives on the `docs/1161-ios-new-path-specs` branch (or on `main` once that docs PR is merged). Keep a copy open. Task 1 switches you to `main`.
- `apps/ios/Config/Secrets.xcconfig` is git-ignored and must exist before `xcodegen generate`. If you work in a worktree, copy it from the main checkout (memory: worktrees lack gitignored config).
- **Trusted builds.** After any `xcodegen generate`, Xcode may serve stale objects. Before every iOS build or test whose result you rely on, run `rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*`.
- iOS verification commands (from the repo root):
  - `npm run lint --workspace=@pbbls/ios` runs `swiftlint` in `apps/ios`.
  - `npm run test --workspace=@pbbls/ios` runs `xcodegen generate && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17'`.
  - One suite: `cd apps/ios && xcodegen generate && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' -only-testing:PebblesTests/<SuiteStruct> -quiet`.
- Web verification commands:
  - `npm run lint --workspace=apps/web`
  - `npm run test --workspace=apps/web -- lib/theme/ios-colorsets.test.ts`
- Every PR in the stack carries the labels `feat`, `ui`, `ios` and `no-lab-note`, and the milestone `M64 · New Path`. Arkaik coverage replaces the Lab Note, per standing rule. Never write a Kritik `F-…` id in a PR body.
- Do not add `Co-Authored-By` lines from this plan. The executing session appends its own attribution.

## File map

| File | Part | Responsibility |
|---|---|---|
| `apps/web/lib/theme/ios-colorsets.ts` (new) | A1 | Pure renderer: schemes + pins → `Map<path relative to apps/ios, contents>` |
| `apps/web/lib/theme/ios-colorsets.test.ts` (new) | A1 | Unit tests + drift gate against the committed iOS tree |
| `apps/web/scripts/generate-ios-colors.ts` (new) | A1 | Thin shell: read Kotlin, render, clear `M3/`, write |
| `apps/web/package.json`, `apps/ios/package.json` | A1 | `generate:ios-colors` / `generate:colors` scripts |
| `apps/ios/Pebbles/Resources/Assets.xcassets/{AccentColor.colorset,M3/}` | A1 (generated) | App catalog roles + global tint |
| `apps/ios/PebblesWidget/Assets.xcassets/M3/` | A1 (generated) | Widget copy, byte for byte |
| `apps/ios/Shared/Theme/PebblesColors.swift` | A1 (generated) | `PebblesColorRole`, `Color.pebbles`, `UIColor.pebbles(_:)` |
| `apps/ios/project.yml` | A1 | Asset-symbol extensions off on both targets |
| `apps/ios/PebblesWidget/KarmaActivityWidget.swift` | A1 | `Color.pebbles.primary` |
| `apps/ios/PebblesTests/PebblesColorsTests.swift` (new) | A1 | Four trait collections resolve to the Kotlin hexes |
| `apps/ios/PebblesTests/ThemeLiteralsTests.swift` (new) | A1→A3b | Literal ratchet (allowlist + shrinking debt) |
| `apps/ios/Pebbles/Theme/ColorTokensPreview.swift` | A1, A3b | Roles grid. The old-token section goes in A3b |
| `.github/workflows/ios.yml` | A1 | Path filter + colour drift step |
| `apps/ios/CLAUDE.md` | A1 | One convention line |
| `apps/ios/Pebbles/Components/Menus/PebblesMenu.swift` (new, branch B only) | A2 | Shared hand-built menu (D9) |
| `docs/decisions/log.md` | A2 | §11 entry |
| `apps/ios/Scripts/verify-color-contrast.mjs` | A3a | Repoint to `M3/googleCapsuleInk` |
| `apps/ios/Pebbles/Features/Path/Stone/StonePalette.swift` | A3b | `brand` as sRGB literals (D5) |
| `apps/ios/Pebbles/Theme/Color+Rive.swift` | A3b | Contrast-aware resolution (D6) |
| `apps/ios/Pebbles/Theme/Palettes.swift` + 11 colorsets | A3b | Deleted |

---

## Part A1 — `feat/921-ios-m3-colour-generator`

PR title: `feat(ios): generate the m3 colour scheme from the android export`. Body starts `Part of #921`.

### Task 1: Create the stack from an updated main and pick up the Arkaik status

**Files:** none

- [ ] **Step 1: Update main and create the stack**

```bash
cd /Users/alexis/code/pbbls
git status --porcelain            # must be empty; stash or commit elsewhere first
git fetch origin
git checkout main
git merge --ff-only origin/main
git config rerere.enabled true
git config remote.pushDefault origin
gh stack init feat/921-ios-m3-colour-generator
git branch --show-current         # expect: feat/921-ios-m3-colour-generator
```

- [ ] **Step 2: Arkaik pickup (spec §10)**

Load the arkaik-mcp tools (`ToolSearch` with `select:mcp__arkaik-mcp__list_nodes,mcp__arkaik-mcp__get_node,mcp__arkaik-mcp__update_node`). `ARKAIK_TOKEN` must be exported. List the acceptance nodes and look for one that covers the appearance, theme or brand scheme on iOS. If one exists, move it to `development` with `update_node`. `update_node` replaces metadata wholesale, so read the node first and send its `gherkin` and values back unchanged. If none applies, nothing moves, now or in later parts. Note the outcome for the A1 PR body. **If the arkaik-mcp tools are not available, say so and stop.** Never edit `docs/arkaik/bundle.json`.

### Task 2: `fromKebab` and `parsePinnedColors`

**Files:**
- Create: `apps/web/lib/theme/ios-colorsets.ts`
- Create: `apps/web/lib/theme/ios-colorsets.test.ts`

- [ ] **Step 1: Write the failing tests**

`apps/web/lib/theme/ios-colorsets.test.ts`:

```ts
import { describe, expect, it } from "vitest"
import { fromKebab, parsePinnedColors } from "./ios-colorsets"

describe("fromKebab", () => {
  it("camel-cases a kebab role, inverting m3-schemes' toKebab", () => {
    expect(fromKebab("surface-container-low")).toBe("surfaceContainerLow")
    expect(fromKebab("on-sand-container")).toBe("onSandContainer")
    expect(fromKebab("primary")).toBe("primary")
  })
})

describe("parsePinnedColors", () => {
  it("reads the Google capsule ink", () => {
    expect(parsePinnedColors("internal val GoogleCapsuleInk = Color(0xFF4A3639)\n")).toEqual({
      googleCapsuleInk: "#4A3639",
    })
  })

  it("fails when the pin is missing", () => {
    expect(() => parsePinnedColors("internal val Other = Color(0xFF4A3639)\n")).toThrow(/GoogleCapsuleInk/)
  })

  it("fails when the pin is translucent", () => {
    expect(() => parsePinnedColors("internal val GoogleCapsuleInk = Color(0x804A3639)\n")).toThrow(/opaque/)
  })
})
```

- [ ] **Step 2: Run it and watch it fail**

Run: `npm run test --workspace=apps/web -- lib/theme/ios-colorsets.test.ts`
Expected: FAIL. The cause is `Failed to resolve import "./ios-colorsets"` (or "does not provide an export named").

- [ ] **Step 3: Write the minimal module**

`apps/web/lib/theme/ios-colorsets.ts`:

```ts
/**
 * iOS draws the M3 scheme Android exports (#921). This module turns the parsed
 * `ColorSchemes.kt` into the asset-catalog colorsets and the generated Swift
 * entry point `Color.pebbles`. It is pure string-in / files-out like
 * m3-schemes.ts, so the drift test runs it in Vitest's node env.
 * Spec: docs/superpowers/specs/2026-10-20-ios-m3-colour-design.md (D1–D3, §2.3–2.5).
 */

export interface IosPins {
  googleCapsuleInk: string
}

/** `surface-container-low` → `surfaceContainerLow`: the inverse of m3-schemes' `toKebab`. */
export function fromKebab(role: string): string {
  return role.replace(/-([a-z])/g, (_, c: string) => c.toUpperCase())
}

const GOOGLE_CAPSULE_INK = /internal val GoogleCapsuleInk = Color\(0x([0-9A-Fa-f]{2})([0-9A-Fa-f]{6})\)/

/** The one hand-pinned value iOS needs (§2.5). Lives here so m3-schemes.ts stays untouched. */
export function parsePinnedColors(kotlin: string): IosPins {
  const match = kotlin.match(GOOGLE_CAPSULE_INK)
  if (!match) throw new Error("ColorSchemes.kt has no GoogleCapsuleInk pin")
  const [, alpha, rgb] = match
  if (alpha.toUpperCase() !== "FF") {
    throw new Error(`GoogleCapsuleInk has alpha 0x${alpha}; the pinned ink must be opaque`)
  }
  return { googleCapsuleInk: `#${rgb.toUpperCase()}` }
}
```

- [ ] **Step 4: Run it and watch it pass**

Run: `npm run test --workspace=apps/web -- lib/theme/ios-colorsets.test.ts`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add apps/web/lib/theme/ios-colorsets.ts apps/web/lib/theme/ios-colorsets.test.ts
git commit -m "feat(ios): parse the pinned google ink and invert kebab roles"
```

### Task 3: `renderColorset` in Xcode's own formatting

**Files:**
- Modify: `apps/web/lib/theme/ios-colorsets.ts`
- Modify: `apps/web/lib/theme/ios-colorsets.test.ts`

- [ ] **Step 1: Write the failing tests**

Add `renderColorset` to the import line, then append:

```ts
describe("renderColorset", () => {
  // Byte-for-byte copy of today's SystemBackground.colorset/Contents.json, as
  // Xcode wrote it: opening a generated catalog in Xcode must not churn it.
  it("writes a two-appearance colorset exactly the way Xcode does", () => {
    expect(
      renderColorset({ light: { hex: "#FFFFFF", alpha: 1 }, dark: { hex: "#171012", alpha: 1 } }),
    ).toBe(`{
  "colors" : [
    {
      "color" : {
        "color-space" : "srgb",
        "components" : {
          "alpha" : "1.000",
          "blue" : "0xFF",
          "green" : "0xFF",
          "red" : "0xFF"
        }
      },
      "idiom" : "universal"
    },
    {
      "appearances" : [
        {
          "appearance" : "luminosity",
          "value" : "dark"
        }
      ],
      "color" : {
        "color-space" : "srgb",
        "components" : {
          "alpha" : "1.000",
          "blue" : "0x12",
          "green" : "0x10",
          "red" : "0x17"
        }
      },
      "idiom" : "universal"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
`)
  })

  it("orders the four appearances as D2 and keys High Contrast with contrast/high", () => {
    const json: { colors: { appearances?: unknown }[] } = JSON.parse(
      renderColorset({
        light: { hex: "#000001", alpha: 1 },
        dark: { hex: "#000002", alpha: 1 },
        lightHigh: { hex: "#000003", alpha: 1 },
        darkHigh: { hex: "#000004", alpha: 1 },
      }),
    )
    expect(json.colors.map((c) => c.appearances ?? null)).toEqual([
      null,
      [{ appearance: "luminosity", value: "dark" }],
      [{ appearance: "contrast", value: "high" }],
      [
        { appearance: "luminosity", value: "dark" },
        { appearance: "contrast", value: "high" },
      ],
    ])
  })

  it("writes alpha with three decimals", () => {
    expect(renderColorset({ light: { hex: "#1D1B16", alpha: 0.14 } })).toContain(`"alpha" : "0.140"`)
  })

  it("rejects a hex that is not #RRGGBB", () => {
    expect(() => renderColorset({ light: { hex: "#abc", alpha: 1 } })).toThrow(/RRGGBB/)
  })
})
```

- [ ] **Step 2: Run it and watch it fail**

Run: `npm run test --workspace=apps/web -- lib/theme/ios-colorsets.test.ts`
Expected: FAIL with `renderColorset is not a function` (or a missing-export error).

- [ ] **Step 3: Implement**

Append to `apps/web/lib/theme/ios-colorsets.ts`:

```ts
export type Appearance = "light" | "dark" | "lightHigh" | "darkHigh"

export interface Tone {
  hex: string
  alpha: number
}

/** D2: entry order in every colorset. */
export const APPEARANCES: readonly Appearance[] = ["light", "dark", "lightHigh", "darkHigh"]

interface AppearanceKey {
  appearance: "luminosity" | "contrast"
  value: "dark" | "high"
}

/**
 * D2: the keys actool honours, checked by compiling a test catalog with Xcode
 * 26.3 and reading the .car with `assetutil --info`. A misspelt key compiles
 * silently and falls back to light, which is why PebblesColorsTests resolves
 * real trait collections.
 */
const APPEARANCE_KEYS: Record<Appearance, AppearanceKey[] | null> = {
  light: null,
  dark: [{ appearance: "luminosity", value: "dark" }],
  lightHigh: [{ appearance: "contrast", value: "high" }],
  darkHigh: [
    { appearance: "luminosity", value: "dark" },
    { appearance: "contrast", value: "high" },
  ],
}

const XCODE_INFO = { author: "xcode", version: 1 }

/** JSON the way Xcode writes it: two-space indent, `"key" : value`, trailing newline. */
export function xcodeJson(value: unknown): string {
  return `${JSON.stringify(value, null, 2).replace(/"([^"]+)": /g, '"$1" : ')}\n`
}

function components({ hex, alpha }: Tone) {
  if (!/^#[0-9A-F]{6}$/.test(hex)) throw new Error(`${hex} is not an uppercase #RRGGBB`)
  // Key order is Xcode's (alphabetical), so the file does not churn on open.
  return {
    alpha: alpha.toFixed(3),
    blue: `0x${hex.slice(5, 7)}`,
    green: `0x${hex.slice(3, 5)}`,
    red: `0x${hex.slice(1, 3)}`,
  }
}

/** One `Contents.json`. A tone given only for `light` is a single-appearance colorset (§2.5). */
export function renderColorset(tones: Partial<Record<Appearance, Tone>>): string {
  const colors = APPEARANCES.flatMap((appearance) => {
    const tone = tones[appearance]
    if (!tone) return []
    const color = { "color-space": "srgb", components: components(tone) }
    const keys = APPEARANCE_KEYS[appearance]
    return [keys ? { appearances: keys, color, idiom: "universal" } : { color, idiom: "universal" }]
  })
  return xcodeJson({ colors, info: XCODE_INFO })
}
```

- [ ] **Step 4: Run it and watch it pass**

Run: `npm run test --workspace=apps/web -- lib/theme/ios-colorsets.test.ts`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add apps/web/lib/theme/ios-colorsets.ts apps/web/lib/theme/ios-colorsets.test.ts
git commit -m "feat(ios): render colorsets in xcode's own json formatting"
```

### Task 4: `renderIosColorsets`, with the scheme roles, the nine Spine tokens, the pin, `AccentColor` and both catalogs

**Files:**
- Modify: `apps/web/lib/theme/ios-colorsets.ts`
- Modify: `apps/web/lib/theme/ios-colorsets.test.ts`

- [ ] **Step 1: Write the failing tests**

Extend the imports:

```ts
import type { M3Scheme } from "./m3-schemes"
import {
  APP_CATALOG,
  M3_FOLDER,
  WIDGET_CATALOG,
  fromKebab,
  parsePinnedColors,
  renderColorset,
  renderIosColorsets,
} from "./ios-colorsets"
```

Append:

```ts
// The renderer reads only these four schemes. Each carries the five roles the
// Spine tokens derive from. Hexes are the real ColorSchemes.kt values.
function scheme(
  name: string,
  mode: "light" | "dark",
  contrast: "standard" | "high",
  [primary, onSurface, onSurfaceVariant, low, lowest]: [string, string, string, string, string],
): M3Scheme {
  return {
    name,
    mode,
    contrast,
    roles: [
      ["primary", primary],
      ["on-surface", onSurface],
      ["on-surface-variant", onSurfaceVariant],
      ["surface-container-low", low],
      ["surface-container-lowest", lowest],
    ],
  }
}
const FIXTURE: M3Scheme[] = [
  scheme("LightScheme", "light", "standard", ["#8E4955", "#1D1B16", "#4C4639", "#F9F3EA", "#FFFFFF"]),
  scheme("LightHighContrastScheme", "light", "high", ["#501824", "#000000", "#000000", "#F6F0E7", "#FFFFFF"]),
  scheme("DarkScheme", "dark", "standard", ["#FFB2BC", "#E7E2D9", "#CFC6B4", "#1B1B1E", "#0E0E10"]),
  scheme("DarkHighContrastScheme", "dark", "high", ["#FFEBED", "#FFFFFF", "#FFFFFF", "#1F1F22", "#000000"]),
]
const PINS = { googleCapsuleInk: "#4A3639" }

interface ColorsetEntry {
  appearances?: { appearance: string; value: string }[]
  color: { components: { alpha: string; red: string; green: string; blue: string } }
}
interface ColorsetJson {
  colors: ColorsetEntry[]
}
function colorset(files: Map<string, string>, role: string, catalog = APP_CATALOG): ColorsetJson {
  const text = files.get(`${catalog}/${M3_FOLDER}/${role}.colorset/Contents.json`)
  if (text === undefined) throw new Error(`no colorset for ${role} in ${catalog}`)
  const parsed: ColorsetJson = JSON.parse(text)
  return parsed
}
const hexes = (json: ColorsetJson) =>
  json.colors.map(({ color: { components: c } }) => `#${c.red.slice(2)}${c.green.slice(2)}${c.blue.slice(2)}`)
const alphas = (json: ColorsetJson) => json.colors.map(({ color: { components: c } }) => c.alpha)

describe("renderIosColorsets", () => {
  const files = renderIosColorsets(FIXTURE, PINS)

  it("maps each scheme role's four entries to light, dark, light HC, dark HC", () => {
    expect(hexes(colorset(files, "primary"))).toEqual(["#8E4955", "#FFB2BC", "#501824", "#FFEBED"])
    expect(colorset(files, "surfaceContainerLowest").colors).toHaveLength(4)
  })

  it("regenerates AccentColor as primary, so the global tint is the role", () => {
    expect(files.get(`${APP_CATALOG}/AccentColor.colorset/Contents.json`)).toBe(
      files.get(`${APP_CATALOG}/${M3_FOLDER}/primary.colorset/Contents.json`),
    )
  })

  it("pins googleCapsuleInk to one entry with no appearances", () => {
    const ink = colorset(files, "googleCapsuleInk")
    expect(ink.colors).toHaveLength(1)
    expect(ink.colors[0].appearances).toBeUndefined()
    expect(hexes(ink)).toEqual(["#4A3639"])
  })

  it("blends rule from onSurface at 0.14 Paper and 0.12 Night", () => {
    const rule = colorset(files, "rule")
    expect(hexes(rule)).toEqual(["#1D1B16", "#E7E2D9", "#000000", "#FFFFFF"])
    expect(alphas(rule)).toEqual(["0.140", "0.120", "0.140", "0.120"])
  })

  it("reads stock from surfaceContainerLowest Paper and surfaceContainerLow Night", () => {
    expect(hexes(colorset(files, "stock"))).toEqual(["#FFFFFF", "#1B1B1E", "#FFFFFF", "#1F1F22"])
  })

  it("takes ring from onSurfaceVariant Paper and onSurface Night", () => {
    const ring = colorset(files, "ring")
    expect(hexes(ring)).toEqual(["#4C4639", "#E7E2D9", "#000000", "#FFFFFF"])
    expect(alphas(ring)).toEqual(["0.300", "0.170", "0.300", "0.170"])
  })

  it("emits all nine Spine tokens", () => {
    for (const token of ["halo", "stock", "rule", "cardRule", "line", "seam", "teeth", "ring", "ripple"]) {
      expect(colorset(files, token).colors, token).toHaveLength(4)
    }
  })

  it("marks the M3 folder as a namespace in both catalogs", () => {
    for (const catalog of [APP_CATALOG, WIDGET_CATALOG]) {
      expect(files.get(`${catalog}/${M3_FOLDER}/Contents.json`)).toBe(`{
  "info" : {
    "author" : "xcode",
    "version" : 1
  },
  "properties" : {
    "provides-namespace" : true
  }
}
`)
    }
  })

  it("gives the widget the app's M3 folder byte for byte", () => {
    const appPrefix = `${APP_CATALOG}/${M3_FOLDER}/`
    const appFiles = [...files.keys()].filter((k) => k.startsWith(appPrefix))
    expect(appFiles.length).toBeGreaterThan(0)
    for (const key of appFiles) {
      const widgetKey = `${WIDGET_CATALOG}/${M3_FOLDER}/${key.slice(appPrefix.length)}`
      expect(files.get(widgetKey), widgetKey).toBe(files.get(key))
    }
  })

  it("fails when a Spine token collides with a scheme role", () => {
    const colliding = FIXTURE.map((s) => ({ ...s, roles: [...s.roles, ["halo", "#000000"] as [string, string]] }))
    expect(() => renderIosColorsets(colliding, PINS)).toThrow(/halo/)
  })

  it("fails when one of the four schemes is missing", () => {
    expect(() => renderIosColorsets(FIXTURE.slice(1), PINS)).toThrow(/LightScheme/)
  })
})
```

The `as [string, string]` in the collision test keeps the tuple type. Inferred, it would widen to `string[]`. It is the only assertion in the file.

- [ ] **Step 2: Run it and watch it fail**

Run: `npm run test --workspace=apps/web -- lib/theme/ios-colorsets.test.ts`
Expected: FAIL with a missing export for `renderIosColorsets` / `APP_CATALOG`.

- [ ] **Step 3: Implement**

Add at the top of `ios-colorsets.ts`, under the doc comment:

```ts
import type { M3Scheme } from "./m3-schemes"

/** `apps/ios`, relative to the `apps/web` workspace root. */
export const IOS_ROOT_FROM_WEB = "../ios"
/** Paths below are relative to `apps/ios` (§2.3). */
export const APP_CATALOG = "Pebbles/Resources/Assets.xcassets"
export const WIDGET_CATALOG = "PebblesWidget/Assets.xcassets"
export const M3_FOLDER = "M3"
export const SWIFT_ENTRY_POINT = "Shared/Theme/PebblesColors.swift"
```

Append:

```ts
const SCHEME_FOR: Record<Appearance, string> = {
  light: "LightScheme",
  dark: "DarkScheme",
  lightHigh: "LightHighContrastScheme",
  darkHigh: "DarkHighContrastScheme",
}

type Source = readonly [role: string, alpha: number]

interface SpineToken {
  name: string
  /** Light and light high contrast. */
  paper: Source
  /** Dark and dark high contrast. */
  night: Source
}

/**
 * §2.4: the nine scheme-only Spine tokens, from #1118's "Spine token → M3 role"
 * table (docs/superpowers/specs/2026-10-18-android-spine-brand-scheme-design.md,
 * §The rule set), mirroring Android's `SpineColors.from(scheme, night)`. A blend
 * with an emotion palette is never here: stack D computes it in SpineColors.swift.
 */
export const SPINE_TOKENS: readonly SpineToken[] = [
  { name: "halo", paper: ["surfaceContainerLow", 1], night: ["surfaceContainerLow", 1] },
  { name: "stock", paper: ["surfaceContainerLowest", 1], night: ["surfaceContainerLow", 1] },
  { name: "rule", paper: ["onSurface", 0.14], night: ["onSurface", 0.12] },
  { name: "cardRule", paper: ["onSurface", 0.22], night: ["onSurface", 0.18] },
  { name: "line", paper: ["onSurface", 0.34], night: ["onSurface", 0.3] },
  { name: "seam", paper: ["onSurface", 0.3], night: ["onSurface", 0.24] },
  { name: "teeth", paper: ["onSurface", 0.2], night: ["onSurface", 0.14] },
  { name: "ring", paper: ["onSurfaceVariant", 0.3], night: ["onSurface", 0.17] },
  { name: "ripple", paper: ["onSurface", 0.55], night: ["onSurface", 0.5] },
]

function perAppearance<T>(make: (appearance: Appearance) => T): Record<Appearance, T> {
  return { light: make("light"), dark: make("dark"), lightHigh: make("lightHigh"), darkHigh: make("darkHigh") }
}

/** Every file the generator owns, keyed by its path relative to `apps/ios`. */
export function renderIosColorsets(schemes: readonly M3Scheme[], pins: IosPins): Map<string, string> {
  const rolesByAppearance = perAppearance((appearance) => {
    const name = SCHEME_FOR[appearance]
    const found = schemes.find((s) => s.name === name)
    if (!found) throw new Error(`no ${name} to render the iOS ${appearance} appearance from`)
    return new Map(found.roles.map(([kebab, hex]) => [fromKebab(kebab), hex]))
  })
  const hexFor = (appearance: Appearance, role: string): string => {
    const hex = rolesByAppearance[appearance].get(role)
    if (hex === undefined) throw new Error(`${SCHEME_FOR[appearance]} has no ${role}`)
    return hex
  }

  // Insertion order is the Swift enum order: scheme roles in Kotlin source
  // order (sand appended by parseColorSchemes), then Spine, then the pin.
  const tones = new Map<string, Partial<Record<Appearance, Tone>>>()
  for (const role of rolesByAppearance.light.keys()) {
    tones.set(role, perAppearance((appearance) => ({ hex: hexFor(appearance, role), alpha: 1 })))
  }
  for (const token of SPINE_TOKENS) {
    if (tones.has(token.name)) throw new Error(`Spine token ${token.name} collides with a scheme role`)
    tones.set(
      token.name,
      perAppearance((appearance) => {
        const [role, alpha] = appearance === "light" || appearance === "lightHigh" ? token.paper : token.night
        return { hex: hexFor(appearance, role), alpha }
      }),
    )
  }
  if (tones.has("googleCapsuleInk")) throw new Error("googleCapsuleInk collides with a scheme role")
  tones.set("googleCapsuleInk", { light: { hex: pins.googleCapsuleInk, alpha: 1 } })

  const primary = tones.get("primary")
  if (!primary) throw new Error("the schemes have no primary role to regenerate AccentColor from")

  const files = new Map<string, string>()
  files.set(`${APP_CATALOG}/AccentColor.colorset/Contents.json`, renderColorset(primary))
  const namespace = xcodeJson({ info: XCODE_INFO, properties: { "provides-namespace": true } })
  for (const catalog of [APP_CATALOG, WIDGET_CATALOG]) {
    files.set(`${catalog}/${M3_FOLDER}/Contents.json`, namespace)
    for (const [role, tone] of tones) {
      files.set(`${catalog}/${M3_FOLDER}/${role}.colorset/Contents.json`, renderColorset(tone))
    }
  }
  return files
}
```

- [ ] **Step 4: Run it and watch it pass**

Run: `npm run test --workspace=apps/web -- lib/theme/ios-colorsets.test.ts`
Expected: PASS (19 tests).

- [ ] **Step 5: Commit**

```bash
git add apps/web/lib/theme/ios-colorsets.ts apps/web/lib/theme/ios-colorsets.test.ts
git commit -m "feat(ios): render scheme roles, spine tokens and the pinned ink as colorsets"
```

### Task 5: The generated Swift entry point

**Files:**
- Modify: `apps/web/lib/theme/ios-colorsets.ts`
- Modify: `apps/web/lib/theme/ios-colorsets.test.ts`

- [ ] **Step 1: Write the failing tests**

Add `SWIFT_ENTRY_POINT` and `renderPebblesColorsSwift` to the `./ios-colorsets` import, then append:

```ts
describe("renderPebblesColorsSwift", () => {
  const swift = renderPebblesColorsSwift(["primary", "onPrimary"])

  it("opens with the do-not-edit header and ends with one newline", () => {
    expect(swift.startsWith("// GENERATED by `npm run generate:ios-colors --workspace=@pbbls/web`")).toBe(true)
    expect(swift.endsWith("}\n")).toBe(true)
    expect(swift.endsWith("}\n\n")).toBe(false)
  })

  it("declares one case and one property per role", () => {
    expect(swift).toContain("enum PebblesColorRole: String, CaseIterable, Sendable {\n    case primary\n    case onPrimary\n")
    expect(swift).toContain("    var onPrimary: Color { Color(PebblesColorRole.onPrimary.assetName) }\n")
    expect(swift).toContain('    var assetName: String { "M3/\\(rawValue)" }')
  })

  it("exposes Color.pebbles and the UIKit bridge", () => {
    expect(swift).toContain("    static let pebbles = PebblesColors()")
    expect(swift).toContain("    static func pebbles(_ role: PebblesColorRole) -> UIColor {")
    expect(swift).toContain('preconditionFailure("missing colorset \\(role.assetName)")')
  })
})

describe("renderIosColorsets' Swift file", () => {
  it("lists scheme roles, then the Spine tokens, then the pin", () => {
    const swift = renderIosColorsets(FIXTURE, PINS).get(SWIFT_ENTRY_POINT) ?? ""
    const cases = [...swift.matchAll(/^ {4}case (\w+)$/gm)].map(([, c]) => c)
    expect(cases).toEqual([
      "primary", "onSurface", "onSurfaceVariant", "surfaceContainerLow", "surfaceContainerLowest",
      "halo", "stock", "rule", "cardRule", "line", "seam", "teeth", "ring", "ripple",
      "googleCapsuleInk",
    ])
  })
})
```

- [ ] **Step 2: Run it and watch it fail**

Run: `npm run test --workspace=apps/web -- lib/theme/ios-colorsets.test.ts`
Expected: FAIL with a missing export for `renderPebblesColorsSwift`.

- [ ] **Step 3: Implement**

Append to `ios-colorsets.ts`:

```ts
const SWIFT_HEADER = `// GENERATED by \`npm run generate:ios-colors --workspace=@pbbls/web\` from
// apps/android/.../core/designsystem/ColorSchemes.kt. Do not edit: change the
// Android export and regenerate. apps/web/lib/theme/ios-colorsets.test.ts fails
// when this file and the Kotlin disagree.
`

/** D3: `Shared/Theme/PebblesColors.swift`, compiled into the app and the widget. */
export function renderPebblesColorsSwift(roles: readonly string[]): string {
  const cases = roles.map((role) => `    case ${role}`).join("\n")
  const properties = roles
    .map((role) => `    var ${role}: Color { Color(PebblesColorRole.${role}.assetName) }`)
    .join("\n")
  return `${SWIFT_HEADER}import SwiftUI
import UIKit

/// Every colour iOS paints chrome with: the M3 scheme roles in Kotlin source
/// order, the sand custom colour, the Spine tokens and the pinned Google ink.
enum PebblesColorRole: String, CaseIterable, Sendable {
${cases}

    /// The colorset's name in the \`M3\` namespace of both asset catalogs.
    var assetName: String { "M3/\\(rawValue)" }
}

/// \`Color.pebbles.<role>\`: one property per \`PebblesColorRole\`.
struct PebblesColors: Sendable {
${properties}
}

extension Color {
    /// The only way feature code names a colour. Roles, never hexes.
    static let pebbles = PebblesColors()
}

extension UIColor {
    /// UIKit appearance proxies and Rive. Same roles, same assets.
    static func pebbles(_ role: PebblesColorRole) -> UIColor {
        guard let color = UIColor(named: role.assetName) else {
            preconditionFailure("missing colorset \\(role.assetName)")
        }
        return color
    }
}
`
}
```

In `renderIosColorsets`, add this line just before `return files`:

```ts
  files.set(SWIFT_ENTRY_POINT, renderPebblesColorsSwift([...tones.keys()]))
```

- [ ] **Step 4: Run it and watch it pass**

Run: `npm run test --workspace=apps/web -- lib/theme/ios-colorsets.test.ts`
Expected: PASS (23 tests).

- [ ] **Step 5: Commit**

```bash
git add apps/web/lib/theme/ios-colorsets.ts apps/web/lib/theme/ios-colorsets.test.ts
git commit -m "feat(ios): render the color.pebbles swift entry point"
```

### Task 6: The generator script, the npm scripts and the first generation

**Files:**
- Create: `apps/web/scripts/generate-ios-colors.ts`
- Modify: `apps/web/package.json` (scripts block, after `"generate:m3"`)
- Modify: `apps/ios/package.json` (scripts block)
- Generated: `apps/ios/Pebbles/Resources/Assets.xcassets/AccentColor.colorset/Contents.json`, `apps/ios/Pebbles/Resources/Assets.xcassets/M3/**`, `apps/ios/PebblesWidget/Assets.xcassets/M3/**`, `apps/ios/Shared/Theme/PebblesColors.swift`

- [ ] **Step 1: Write the script**

`apps/web/scripts/generate-ios-colors.ts`:

```ts
/**
 * Writes the iOS colorsets and Shared/Theme/PebblesColors.swift from Android's
 * ColorSchemes.kt (#921). Run from the web workspace:
 * `npm run generate:ios-colors --workspace=@pbbls/web`
 * (or `npm run generate:colors --workspace=@pbbls/ios`).
 */
import { mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs"
import path from "node:path"
import {
  APP_CATALOG,
  IOS_ROOT_FROM_WEB,
  M3_FOLDER,
  WIDGET_CATALOG,
  parsePinnedColors,
  renderIosColorsets,
} from "../lib/theme/ios-colorsets"
import { ANDROID_SCHEMES_FROM_WEB, parseColorSchemes } from "../lib/theme/m3-schemes"

const webRoot = process.cwd()
const iosRoot = path.resolve(webRoot, IOS_ROOT_FROM_WEB)

try {
  const kotlin = readFileSync(path.resolve(webRoot, ANDROID_SCHEMES_FROM_WEB), "utf8")
  const files = renderIosColorsets(parseColorSchemes(kotlin), parsePinnedColors(kotlin))
  // The M3 folders are wholly generated. Clearing them first means a role
  // removed upstream leaves no stale colorset behind.
  for (const catalog of [APP_CATALOG, WIDGET_CATALOG]) {
    rmSync(path.join(iosRoot, catalog, M3_FOLDER), { recursive: true, force: true })
  }
  for (const [relative, contents] of files) {
    const target = path.join(iosRoot, relative)
    mkdirSync(path.dirname(target), { recursive: true })
    writeFileSync(target, contents)
  }
  console.log(`[generate:ios-colors] wrote ${files.size} files under apps/ios`)
} catch (err) {
  console.error("[generate:ios-colors] failed:", err instanceof Error ? err.message : err)
  process.exit(1)
}
```

- [ ] **Step 2: Add the npm scripts**

In `apps/web/package.json`, after `"generate:m3": "tsx scripts/generate-m3-theme.ts",` add:

```json
    "generate:ios-colors": "tsx scripts/generate-ios-colors.ts",
```

In `apps/ios/package.json`, after `"generate": "xcodegen generate",` add:

```json
    "generate:colors": "npm run generate:ios-colors --workspace=@pbbls/web",
```

- [ ] **Step 3: Generate**

Run: `npm run generate:colors --workspace=@pbbls/ios`
Expected: `[generate:ios-colors] wrote 128 files under apps/ios`. That is 1 AccentColor + 2 × (1 namespace + 62 colorsets) + 1 Swift file. If the count differs, recount the roles before going on: 52 parsed (48 M3 + 4 sand) + 9 Spine + 1 pin = 62.

- [ ] **Step 4: Inspect**

```bash
git status --short apps/ios
head -12 apps/ios/Shared/Theme/PebblesColors.swift
cat apps/ios/Pebbles/Resources/Assets.xcassets/AccentColor.colorset/Contents.json | head -14
ls apps/ios/Pebbles/Resources/Assets.xcassets/M3 | wc -l     # expect 63 (62 colorsets + Contents.json)
```

Expected: `AccentColor` now starts with `"blue" : "0x55", "green" : "0x49", "red" : "0x8E"` (`#8E4955`), and the old legacy colorsets are untouched.

- [ ] **Step 5: Commit**

```bash
git add apps/web/scripts/generate-ios-colors.ts apps/web/package.json apps/ios/package.json \
  apps/ios/Pebbles/Resources/Assets.xcassets/AccentColor.colorset \
  apps/ios/Pebbles/Resources/Assets.xcassets/M3 \
  apps/ios/PebblesWidget/Assets.xcassets/M3 \
  apps/ios/Shared/Theme/PebblesColors.swift
git commit -m "feat(ios): generate the m3 colorsets and color.pebbles from colorschemes.kt"
```

### Task 7: The Vitest drift gate on the real Kotlin and the committed tree

**Files:**
- Modify: `apps/web/lib/theme/ios-colorsets.test.ts`

- [ ] **Step 1: Write the tests**

Add these imports at the top. Replace the Task 4 line `import type { M3Scheme } from "./m3-schemes"` with the merged `./m3-schemes` import below, so the module is imported once:

```ts
import { readdirSync, readFileSync } from "node:fs"
import path from "node:path"
import { fileURLToPath } from "node:url"
import { ANDROID_SCHEMES_FROM_WEB, type M3Scheme, parseColorSchemes, toKebab } from "./m3-schemes"
```

Also add `IOS_ROOT_FROM_WEB` to the `./ios-colorsets` import, next to the names already there. Then append:

```ts
// The drift gate (§8): the committed iOS files must be exactly what the
// generator writes from the committed Kotlin. Runs in web.yml on every PR.
describe("committed iOS colours", () => {
  const webRoot = fileURLToPath(new URL("../..", import.meta.url))
  const iosRoot = path.join(webRoot, IOS_ROOT_FROM_WEB)
  const kotlin = readFileSync(path.join(webRoot, ANDROID_SCHEMES_FROM_WEB), "utf8")
  const files = renderIosColorsets(parseColorSchemes(kotlin), parsePinnedColors(kotlin))

  it("matches the generator output (run `npm run generate:colors --workspace=@pbbls/ios`)", () => {
    for (const [relative, contents] of files) {
      expect(readFileSync(path.join(iosRoot, relative), "utf8"), relative).toBe(contents)
    }
  })

  it("holds no colorset in M3/ that the generator does not write", () => {
    for (const catalog of [APP_CATALOG, WIDGET_CATALOG]) {
      const onDisk = readdirSync(path.join(iosRoot, catalog, M3_FOLDER)).filter((n) => n.endsWith(".colorset"))
      const prefix = `${catalog}/${M3_FOLDER}/`
      const expected = [...files.keys()]
        .filter((k) => k.startsWith(prefix) && k.endsWith(".colorset/Contents.json"))
        .map((k) => k.slice(prefix.length, -"/Contents.json".length))
      expect(onDisk.sort()).toEqual(expected.sort())
    }
  })

  it("renders 62 roles: 48 M3 + 4 sand + 9 Spine + the pin", () => {
    const prefix = `${APP_CATALOG}/${M3_FOLDER}/`
    expect([...files.keys()].filter((k) => k.startsWith(prefix) && k.endsWith(".colorset/Contents.json"))).toHaveLength(62)
  })

  it("carries the real primary, error and surface hexes in D2 order", () => {
    expect(hexes(colorset(files, "primary"))).toEqual(["#8E4955", "#FFB2BC", "#501824", "#FFEBED"])
    expect(hexes(colorset(files, "error"))).toEqual(["#9B4500", "#FFB68E", "#4D1E00", "#FFECE4"])
    expect(hexes(colorset(files, "surface"))).toEqual(["#FEF9EF", "#131315", "#FEF9EF", "#131315"])
  })

  it("round-trips every Kotlin role name through toKebab and fromKebab", () => {
    const lightBlock = kotlin.slice(
      kotlin.indexOf("internal val LightScheme"),
      kotlin.indexOf("internal val LightMediumContrastScheme"),
    )
    const camel = [...lightBlock.matchAll(/(\w+) = Color\(0x/g)].map(([, role]) => role)
    expect(camel.length).toBe(48)
    for (const role of camel) expect(fromKebab(toKebab(role))).toBe(role)
    for (const role of ["sand", "onSand", "sandContainer", "onSandContainer"]) {
      expect(fromKebab(toKebab(role))).toBe(role)
    }
  })
})
```

- [ ] **Step 2: Run them**

Run: `npm run test --workspace=apps/web -- lib/theme/ios-colorsets.test.ts`
Expected: PASS (28 tests).

- [ ] **Step 3: Prove the gate bites**

```bash
printf ' ' >> apps/ios/Pebbles/Resources/Assets.xcassets/M3/surface.colorset/Contents.json
npm run test --workspace=apps/web -- lib/theme/ios-colorsets.test.ts   # expect FAIL naming M3/surface.colorset
git checkout -- apps/ios/Pebbles/Resources/Assets.xcassets/M3/surface.colorset/Contents.json
```

- [ ] **Step 4: Lint the web workspace and run its full suite**

Run: `npm run lint --workspace=apps/web && npm run test --workspace=apps/web`
Expected: no ESLint errors, and the whole Vitest suite passes.

- [ ] **Step 5: Commit**

```bash
git add apps/web/lib/theme/ios-colorsets.test.ts
git commit -m "test(ios): fail vitest when the committed ios colours drift from the kotlin"
```

### Task 8: Turn asset-symbol extensions off, move the widget to `Color.pebbles.primary`, delete its `AccentPrimary`

**Files:**
- Modify: `apps/ios/project.yml` (the `Pebbles` and `PebblesWidget` targets' `settings.base`)
- Modify: `apps/ios/PebblesWidget/KarmaActivityWidget.swift:13,22,31,35`
- Delete: `apps/ios/PebblesWidget/Assets.xcassets/AccentPrimary.colorset/`

- [ ] **Step 1: Confirm nothing uses generated asset symbols**

Run: `grep -rnE 'Color\(\.[a-z]|Image\(\.[a-z]|UIImage\(resource:|ColorResource|ImageResource' apps/ios --include='*.swift'`
Expected: the only hits are `Color(.sRGB, …)` literals in `Features/Path/Stone/StonePalette.swift`. Those are colour-space initialisers, not resource lookups.

- [ ] **Step 2: Edit `project.yml`**

Under `targets.Pebbles.settings.base`, after `ASSETCATALOG_COMPILER_APPICON_NAME: AppIcon`, add:

```yaml
        # Off: Xcode would otherwise generate Color statics named after every
        # colorset (M3/primary → Color.primary), colliding with SwiftUI's own
        # members and opening a second, ungoverned entry point beside
        # Color.pebbles (docs/superpowers/specs/2026-10-20-ios-m3-colour-design.md §2.3).
        ASSETCATALOG_COMPILER_GENERATE_SWIFT_ASSET_SYMBOL_EXTENSIONS: NO
```

Under `targets.PebblesWidget.settings.base`, after `TARGETED_DEVICE_FAMILY: "1"`, add:

```yaml
        ASSETCATALOG_COMPILER_GENERATE_SWIFT_ASSET_SYMBOL_EXTENSIONS: NO
```

- [ ] **Step 3: Move the widget**

In `apps/ios/PebblesWidget/KarmaActivityWidget.swift`, replace all four `Color("AccentPrimary")` with `Color.pebbles.primary`:

```bash
perl -pi -e 's/Color\("AccentPrimary"\)/Color.pebbles.primary/g' apps/ios/PebblesWidget/KarmaActivityWidget.swift
grep -c 'Color.pebbles.primary' apps/ios/PebblesWidget/KarmaActivityWidget.swift   # expect 4
git rm -r apps/ios/PebblesWidget/Assets.xcassets/AccentPrimary.colorset
```

- [ ] **Step 4: Regenerate the project and build cleanly**

```bash
cd apps/ios
xcodegen generate
rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*
xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' build 2>&1 | tee /tmp/pebbles-a1-build.log | tail -3
grep -iE 'asset symbol|GeneratedAssetSymbols|ambiguous use of .primary' /tmp/pebbles-a1-build.log   # expect no output
```

Expected: `** BUILD SUCCEEDED **`, and the grep prints nothing. The build compiles the widget extension too, because the app embeds it.

- [ ] **Step 5: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/project.yml apps/ios/PebblesWidget/KarmaActivityWidget.swift apps/ios/PebblesWidget/Assets.xcassets
git commit -m "feat(ios): widget reads color.pebbles; asset symbol extensions off"
```

### Task 9: `PebblesColorsTests`, proof that UIKit honours the four appearances

**Files:**
- Create: `apps/ios/PebblesTests/PebblesColorsTests.swift`

- [ ] **Step 1: Write the test**

```swift
import SwiftUI
import Testing
import UIKit
@testable import Pebbles

/// The asset catalog's appearance keys are only proven by resolving real trait
/// collections: a misspelt key compiles silently and falls back to light
/// (spec 2026-10-20-ios-m3-colour §13). Expected hexes are ColorSchemes.kt at
/// the commit that generated the colorsets.
@Suite("PebblesColors")
struct PebblesColorsTests {

    enum Look: String, CaseIterable, Sendable {
        case light, dark, lightHigh, darkHigh

        var traits: UITraitCollection {
            UITraitCollection { traits in
                traits.userInterfaceStyle = (self == .dark || self == .darkHigh) ? .dark : .light
                traits.accessibilityContrast = (self == .lightHigh || self == .darkHigh) ? .high : .normal
            }
        }
    }

    private func hex(_ color: UIColor) -> String {
        var red: CGFloat = 0, green: CGFloat = 0, blue: CGFloat = 0, alpha: CGFloat = 0
        color.getRed(&red, green: &green, blue: &blue, alpha: &alpha)
        return String(
            format: "#%02X%02X%02X",
            Int((red * 255).rounded()), Int((green * 255).rounded()), Int((blue * 255).rounded())
        )
    }

    @Test("every role has a colorset in the app bundle")
    func everyRoleResolves() {
        for role in PebblesColorRole.allCases {
            #expect(UIColor(named: role.assetName, in: .main, compatibleWith: nil) != nil, "\(role.assetName)")
        }
    }

    @Test("roles resolve to the Kotlin hexes under the four trait collections", arguments: [
        (PebblesColorRole.surface, ["#FEF9EF", "#131315", "#FEF9EF", "#131315"]),
        (PebblesColorRole.error, ["#9B4500", "#FFB68E", "#4D1E00", "#FFECE4"]),
        (PebblesColorRole.primary, ["#8E4955", "#FFB2BC", "#501824", "#FFEBED"])
    ])
    func resolvesPerAppearance(role: PebblesColorRole, expected: [String]) {
        let got = Look.allCases.map { hex(UIColor.pebbles(role).resolvedColor(with: $0.traits)) }
        #expect(got == expected, "\(role.rawValue): \(got)")
    }

    @Test("Color.pebbles and UIColor.pebbles name the same asset")
    func swiftUIAndUIKitAgree() {
        for look in Look.allCases {
            let viaSwiftUI = UIColor(Color.pebbles.error).resolvedColor(with: look.traits)
            #expect(hex(viaSwiftUI) == hex(UIColor.pebbles(.error).resolvedColor(with: look.traits)), "\(look)")
        }
    }

    @Test("rule is onSurface at 0.14 Paper and 0.12 Night")
    func ruleAlpha() {
        var alphas: [Double] = []
        for look in Look.allCases {
            var red: CGFloat = 0, green: CGFloat = 0, blue: CGFloat = 0, alpha: CGFloat = 0
            UIColor.pebbles(.rule).resolvedColor(with: look.traits).getRed(&red, green: &green, blue: &blue, alpha: &alpha)
            alphas.append((Double(alpha) * 100).rounded() / 100)
        }
        #expect(alphas == [0.14, 0.12, 0.14, 0.12])
    }

    @Test("googleCapsuleInk ignores appearance and contrast")
    func googleInkIsPinned() {
        let resolved = Set(Look.allCases.map { hex(UIColor.pebbles(.googleCapsuleInk).resolvedColor(with: $0.traits)) })
        #expect(resolved == ["#4A3639"])
    }
}
```

- [ ] **Step 2: Run it**

```bash
cd apps/ios && xcodegen generate
rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*
xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' -only-testing:PebblesTests/PebblesColorsTests -quiet
```

Expected: PASS, 5 tests (7 test cases with the arguments).

- [ ] **Step 3: Prove the test catches a misspelt key**

```bash
cd /Users/alexis/code/pbbls
perl -pi -e 's/"contrast"/"contrst"/' apps/ios/Pebbles/Resources/Assets.xcassets/M3/primary.colorset/Contents.json
cd apps/ios && rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*
xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' -only-testing:PebblesTests/PebblesColorsTests -quiet
# expect FAIL on primary: lightHigh resolves to #8E4955 instead of #501824
cd /Users/alexis/code/pbbls && git checkout -- apps/ios/Pebbles/Resources/Assets.xcassets/M3/primary.colorset/Contents.json
```

- [ ] **Step 4: Commit**

```bash
git add apps/ios/PebblesTests/PebblesColorsTests.swift
git commit -m "test(ios): resolve m3 roles under light, dark and both high contrast traits"
```

### Task 10: `ThemeLiteralsTests` with the allowlist and the A1 debt baseline

**Files:**
- Create: `apps/ios/PebblesTests/ThemeLiteralsTests.swift`

- [ ] **Step 1: Write the rule tests and the ratchet**

```swift
import Foundation
import Testing

/// D8 (spec 2026-10-20-ios-m3-colour §7), the iOS twin of Android's
/// ThemeLiteralsTest. Feature code names a colour only as
/// `Color.pebbles.<role>`. The simulator shares the host filesystem, so the test
/// reads the sources from `#filePath` (precedent: StoneLab/StoneLabPalettesTests).
///
/// A file's count is its number of offending *lines* (one line can break two
/// rules: `UIColor(red:` also matches `Color(red`). The ratchet fails when a
/// count rises ("new literal") and when one falls without its `debt` entry
/// being lowered ("lower the debt").
@Suite("Theme literals")
struct ThemeLiteralsTests {

    struct Rule {
        let regex: NSRegularExpression
        let reason: String

        // NSRegularExpression, not a Swift Regex literal: the first rule needs
        // a lookbehind.
        init(_ pattern: String, _ reason: String) {
            guard let regex = try? NSRegularExpression(pattern: pattern) else {
                preconditionFailure("invalid pattern \(pattern)")
            }
            self.regex = regex
            self.reason = reason
        }
    }

    static let rules: [Rule] = [
        Rule(#"(?<![\w.])(Color)?\.(red|orange|yellow|green|mint|teal|cyan|blue|indigo|purple|pink|brown|gray|white|black)\b"#,
             "a system or absolute colour: use a Color.pebbles role"),
        Rule(#"Color\.(primary|secondary|accentColor)\b"#,
             "a system label colour: use onSurface / onSurfaceVariant / primary"),
        Rule(#"Color\(\s*(red|white|hue|\.sRGB|\.displayP3)"#, "a literal colour"),
        Rule(#"Color\(hex:\s*""#, "a hex literal (Color(hex: variable) on server data is fine)"),
        Rule(#"Color\(""#, "a named asset: only the generated PebblesColors.swift names assets"),
        Rule(#"UIColor\((red|white|hue|named|displayP3Red):"#, "a UIKit literal: use UIColor.pebbles(_:)"),
        Rule(#"UIColor\.(system\w+|label|secondaryLabel|tertiaryLabel|white|black|red|clear)\b"#,
             "a UIKit system colour: use UIColor.pebbles(_:)"),
        Rule(#"role:\s*\.destructive"#, "a destructive role outside a system .alert / .confirmationDialog")
    ]

    static let scannedFolders = ["Features", "Components"]

    /// Not scanned (spec §7).
    static let exemptPrefixes = [
        "Features/Path/Stone/",
        "Features/StoneLab/",
        "Features/Path/Models/EmotionPalette.swift",
        "Components/Buttons/AppleSignInButton.swift",
        "Components/Buttons/GoogleSignInButton.swift"
    ]

    /// Permanent: hits that are not chrome colours.
    static let allowlist: [String: (count: Int, reason: String)] = [
        "Features/Path/Valence/ValenceStoneStyle.swift": (1, "joySurface, Joy's emotion surface (data)"),
        "Features/Path/Read/PebbleSnapFrame.swift": (1, "preview fixture image drawn above #Preview"),
        "Features/Path/Components/WeekPathView.swift": (2, "fade mask stops (alpha only)"),
        "Features/Path/Render/PebbleAnimatedRenderView.swift": (1, "stroke-reveal mask"),
        "Features/Welcome/HandcraftedLogoView.swift": (1, "stroke-reveal mask"),
        "Features/Connections/InviteSheet.swift": (1, "QR quiet zone must be white for scanners"),
        "Features/Capture/CaptureFlowView.swift": (1, "role: .destructive in a system dialog"),
        "Features/Path/MomentDeleteDialog.swift": (1, "role: .destructive in a system dialog"),
        "Features/Profile/Sheets/SettingsSheet.swift": (1, "role: .destructive in a system dialog"),
        "Features/Profile/Lists/SoulsListView.swift": (1, "role: .destructive in a system dialog"),
        "Features/Connections/ConnectionsListView.swift": (2, "role: .destructive in a system dialog"),
        "Features/Profile/Lists/CollectionsListView.swift": (1, "role: .destructive in a system dialog"),
        "Features/Glyph/Views/GlyphCarveSheet.swift": (1, "role: .destructive in a system dialog")
    ]

    /// Shrinks to nothing by the end of the stack (A2: .red and owned
    /// destructive roles; A3a / A3b: the folders they migrate). Never raise it.
    static let debt: [String: Int] = [
        "Components/Buttons/PebblesPrimaryButtonStyle.swift": 2,
        "Components/Checkboxes/PebblesCheckbox.swift": 1,
        "Components/Inputs/PebblesTextInput.swift": 1,
        "Components/PebbleRow.swift": 1,
        "Features/Auth/AuthView.swift": 2,
        "Features/Capture/CaptureFlowView.swift": 1,
        "Features/Complete/Steps/CompletePrivacyStep.swift": 1,
        "Features/Connections/ConnectionsListView.swift": 1,
        "Features/Connections/InviteAcceptSheet.swift": 1,
        "Features/Connections/InviteSheet.swift": 1,
        "Features/Glyph/Views/GlyphCanvasView.swift": 1,
        "Features/Glyph/Views/GlyphCarveSheet.swift": 2,
        "Features/Glyph/Views/GlyphDetailDrawer.swift": 2,
        "Features/Glyph/Views/GlyphPickerContent.swift": 1,
        "Features/Glyph/Views/GlyphTabBar.swift": 2,
        "Features/Glyph/Views/GlyphsListView.swift": 1,
        "Features/Glyph/Views/SlideToConfirm.swift": 1,
        "Features/Karma/AchievementMomentView.swift": 1,
        "Features/Karma/KarmaEarnedCapsule.swift": 2,
        "Features/Lab/Components/FeaturedCommunityCard.swift": 1,
        "Features/Onboarding/OnboardingView.swift": 3,
        "Features/Path/Components/PathPebbleRow.swift": 3,
        "Features/Path/Components/WeekPathView.swift": 1,
        "Features/Path/EditMomentSheet.swift": 1,
        "Features/Path/PebbleDetailSheet.swift": 1,
        "Features/Path/PebbleFormView.swift": 1,
        "Features/Path/Valence/ValenceStoneStyle.swift": 1,
        "Features/PebbleMedia/AttachedPhotoView.swift": 4,
        "Features/PebbleMedia/ExistingSnapRow.swift": 3,
        "Features/PebbleMedia/SnapImageView.swift": 1,
        "Features/Profile/Components/CollectionGlyphSection.swift": 1,
        "Features/Profile/Lists/CollectionsListView.swift": 1,
        "Features/Profile/Lists/SoulsListView.swift": 1,
        "Features/Profile/Sheets/CreateCollectionSheet.swift": 1,
        "Features/Profile/Sheets/CreateSoulSheet.swift": 1,
        "Features/Profile/Sheets/EditCollectionSheet.swift": 1,
        "Features/Profile/Sheets/EditSoulSheet.swift": 1,
        "Features/Profile/Sheets/SettingsSheet.swift": 4,
        "Features/Record/RecordStepScaffold.swift": 1,
        "Features/Record/Steps/RecordPhotoStep.swift": 3,
        "Features/Welcome/WelcomeView.swift": 2
    ]

    static let pebblesRoot: URL = {
        var url = URL(fileURLWithPath: #filePath)
        while url.lastPathComponent != "apps" { url.deleteLastPathComponent() }
        return url.appendingPathComponent("ios/Pebbles")
    }()

    /// Offending lines of one source. Text after `//` is ignored, and so is
    /// everything from the first `#Preview` on (preview fixtures fake server data).
    static func offendingLines(in source: String) -> [(line: Int, reason: String)] {
        var hits: [(line: Int, reason: String)] = []
        for (index, rawLine) in source.components(separatedBy: "\n").enumerated() {
            if rawLine.trimmingCharacters(in: .whitespaces).hasPrefix("#Preview") { break }
            let code = rawLine.components(separatedBy: "//")[0]
            let range = NSRange(code.startIndex..., in: code)
            if let rule = rules.first(where: { $0.regex.firstMatch(in: code, range: range) != nil }) {
                hits.append((index + 1, rule.reason))
            }
        }
        return hits
    }

    static func scan() throws -> [String: [(line: Int, reason: String)]] {
        var result: [String: [(line: Int, reason: String)]] = [:]
        let rootDepth = pebblesRoot.pathComponents.count
        for folder in scannedFolders {
            let base = pebblesRoot.appendingPathComponent(folder)
            guard let walker = FileManager.default.enumerator(at: base, includingPropertiesForKeys: nil) else {
                continue
            }
            for case let url as URL in walker where url.pathExtension == "swift" {
                let relative = url.pathComponents.dropFirst(rootDepth).joined(separator: "/")
                if exemptPrefixes.contains(where: { relative.hasPrefix($0) }) { continue }
                let hits = try offendingLines(in: String(contentsOf: url, encoding: .utf8))
                if !hits.isEmpty { result[relative] = hits }
            }
        }
        return result
    }

    // MARK: - Rules

    @Test("each rule catches its target and spares the allowed spellings", arguments: [
        (".foregroundStyle(.red)", true),
        ("return SIMD3<Float>(resolved.red, resolved.green, resolved.blue)", false),
        (".foregroundStyle(.secondary)", false),
        (".fill(Color.secondary.opacity(0.2))", true),
        (".background(Color.white)", true),
        (".shadow(color: .black.opacity(0.22), radius: 14, y: 4)", true),
        ("Color(hex: palette.primaryHex)", false),
        ("Color(hex: \"#A15C081A\") ?? .clear", true),
        ("Color(\"SystemMuted\")", true),
        ("UIColor(red: 0.42, green: 0.48, blue: 0.56, alpha: 1).setFill()", true),
        ("Color(.sRGB, red: 1, green: 1, blue: 1)", true),
        ("UIColor(named: \"AccentColor\")", true),
        ("let muted = UIColor.systemGray5", true),
        ("Button(role: .destructive) {", true),
        (".foregroundStyle(Color.pebbles.error)", false),
        ("proxy.backgroundColor = UIColor.pebbles(.surfaceContainerHighest)", false),
        (".tint(.primary) // was Color.white", false)
    ])
    func rule(line: String, offends: Bool) {
        #expect(Self.offendingLines(in: line).isEmpty != offends, "\(line)")
    }

    @Test("nothing from the first #Preview on is scanned")
    func previewIsExempt() {
        let source = "let ink = Color.pebbles.onSurface\n#Preview {\n    Text(\"x\").foregroundStyle(.red)\n}\n"
        #expect(Self.offendingLines(in: source).isEmpty)
    }

    // MARK: - Ratchet

    @Test("feature code names colours only through Color.pebbles")
    func ratchet() throws {
        let found = try Self.scan()
        var allowed = Self.debt
        for (path, entry) in Self.allowlist { allowed[path, default: 0] += entry.count }

        var problems: [String] = []
        for path in Set(found.keys).union(allowed.keys).sorted() {
            let hits = found[path] ?? []
            let limit = allowed[path] ?? 0
            if hits.count > limit {
                let lines = hits.map { "  \(path):\($0.line) — \($0.reason)" }.joined(separator: "\n")
                problems.append("\(path): \(hits.count) offending lines, \(limit) allowed — new literal.\n\(lines)")
            } else if hits.count < limit {
                problems.append("\(path): \(hits.count) offending lines, \(limit) allowed — lower the debt.")
            }
        }
        let current = found.keys.sorted().map { "\"\($0)\": \(found[$0]?.count ?? 0)," }.joined(separator: "\n")
        #expect(problems.isEmpty, "\(problems.joined(separator: "\n"))\n\nCurrent counts (allowlist + debt):\n\(current)")
    }
}
```

- [ ] **Step 2: Run it**

```bash
cd apps/ios && xcodegen generate
rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*
xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' -only-testing:PebblesTests/ThemeLiteralsTests -quiet
```

Expected: PASS. The table above was computed on 2026-10-20 from `main` at `d53be9cf` plus this stack. If main moved and the ratchet fails, use the "Current counts" block in the failure message: set `debt[path]` to the current count minus the path's allowlist count. Do not move a hit into the allowlist unless the spec's §7 allowlist names it.

- [ ] **Step 3: Prove the ratchet turns both ways**

1. Add `.foregroundStyle(.blue)` on a new line inside the `body` of `apps/ios/Pebbles/Features/Path/VisibilityChip.swift`, run the suite, and expect "new literal". Revert the line.
2. Lower `"Features/Welcome/WelcomeView.swift": 2` to `3`, run the suite, and expect "lower the debt". Revert.

- [ ] **Step 4: Lint**

Run: `npm run lint --workspace=@pbbls/ios`
Expected: no error-severity violations. `large_tuple` is a warning at 2+ members. The `(count:, reason:)` and `(line:, reason:)` tuples have exactly 2 and sit at the threshold, which is not over it.

- [ ] **Step 5: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/PebblesTests/ThemeLiteralsTests.swift
git commit -m "test(ios): ratchet colour literals in features and components"
```

### Task 11: `ColorTokensPreview` gains the roles grid

**Files:**
- Modify: `apps/ios/Pebbles/Theme/ColorTokensPreview.swift`

- [ ] **Step 1: Add the roles grid above the existing `#Preview`s and keep the old `TokensGrid`**

Insert after `import SwiftUI`:

```swift
import UIKit

/// A role resolved for an explicit trait collection. SwiftUI previews cannot
/// turn on Increase Contrast (`colorSchemeContrast` is read-only in the
/// environment), so the swatch resolves the UIColor itself.
private struct RoleSwatch: View {
    let role: PebblesColorRole
    let traits: UITraitCollection

    private func resolved(_ role: PebblesColorRole) -> Color {
        Color(uiColor: UIColor.pebbles(role).resolvedColor(with: traits))
    }

    var body: some View {
        VStack(spacing: 6) {
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .fill(resolved(role))
                .frame(height: 56)
                .overlay(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .strokeBorder(resolved(.outlineVariant))
                )
            Text(verbatim: role.rawValue)
                .font(.caption2.monospaced())
                .foregroundStyle(resolved(.onSurface))
        }
    }
}

private struct RolesGrid: View {
    let title: String
    let traits: UITraitCollection
    private let columns = [GridItem(.adaptive(minimum: 110), spacing: 12)]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                Text(verbatim: title)
                    .font(.headline)
                    .foregroundStyle(Color(uiColor: UIColor.pebbles(.onSurface).resolvedColor(with: traits)))
                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(PebblesColorRole.allCases, id: \.self) { role in
                        RoleSwatch(role: role, traits: traits)
                    }
                }
            }
            .padding()
        }
        .background(Color(uiColor: UIColor.pebbles(.surface).resolvedColor(with: traits)))
    }
}

private func previewTraits(_ style: UIUserInterfaceStyle, _ contrast: UIAccessibilityContrast) -> UITraitCollection {
    UITraitCollection { traits in
        traits.userInterfaceStyle = style
        traits.accessibilityContrast = contrast
    }
}
```

In the existing `Swatch`, change `.strokeBorder(Color.primary.opacity(0.08))` to `.strokeBorder(Color.pebbles.outlineVariant)` and `.foregroundStyle(.primary)` to `.foregroundStyle(Color.pebbles.onSurface)`.

Append after the existing two previews:

```swift
#Preview("Roles — Light") {
    RolesGrid(title: "Light", traits: previewTraits(.light, .normal))
}

#Preview("Roles — Dark") {
    RolesGrid(title: "Dark", traits: previewTraits(.dark, .normal))
}

#Preview("Roles — Light, High Contrast") {
    RolesGrid(title: "Light · High Contrast", traits: previewTraits(.light, .high))
}

#Preview("Roles — Dark, High Contrast") {
    RolesGrid(title: "Dark · High Contrast", traits: previewTraits(.dark, .high))
}
```

- [ ] **Step 2: Build and eyeball**

```bash
cd apps/ios && rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*
xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' build -quiet
```

Expected: `BUILD SUCCEEDED`. Open the file in Xcode's canvas and check all four "Roles" previews by eye. `primary` must differ between light and light HC (`#8E4955` vs `#501824`).

- [ ] **Step 3: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/Pebbles/Theme/ColorTokensPreview.swift
git commit -m "feat(ios): preview every m3 role in light, dark and high contrast"
```

### Task 12: CI — path filter and the colour-drift step in `ios.yml`

**Files:**
- Modify: `.github/workflows/ios.yml` (both `paths:` lists, the `verify` job)

- [ ] **Step 1: Extend both path filters**

In `on.pull_request.paths` and `on.push.paths`, after `- 'packages/supabase/**'`, add:

```yaml
      # The iOS colours are generated from the Android palette by a web script,
      # so a palette change that touches no apps/ios file must still run the
      # drift check below (web.yml catches it too, via Vitest).
      - 'apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/ColorSchemes.kt'
      - 'apps/web/lib/theme/**'
      - 'apps/web/scripts/generate-ios-colors.ts'
```

- [ ] **Step 2: Rewrite the head of the `verify` job**

Replace `name: privacy manifests · colour contrast` with `name: privacy manifests · colour contrast · colour drift`. Replace the `Set up Node` step and add the two steps after it, before `Verify privacy manifests`:

```yaml
      - name: Set up Node
        uses: actions/setup-node@v7
        with:
          node-version: '24'
          cache: npm

      - name: Install dependencies
        run: npm ci

      # The colorsets and Shared/Theme/PebblesColors.swift are generated from
      # ColorSchemes.kt. Regenerating must leave apps/ios untouched. `git status`
      # rather than `git diff`, which would miss a new, uncommitted colorset.
      - name: Check colour drift
        run: |
          npm run generate:ios-colors --workspace=@pbbls/web
          if [ -n "$(git status --porcelain -- apps/ios)" ]; then
            echo "apps/ios colours differ from ColorSchemes.kt. Run 'npm run generate:colors --workspace=@pbbls/ios' and commit:"
            git status --porcelain -- apps/ios
            exit 1
          fi
```

- [ ] **Step 3: Check it locally**

```bash
npm run generate:ios-colors --workspace=@pbbls/web && test -z "$(git status --porcelain -- apps/ios)" && echo "no drift"
```

Expected: `no drift`.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/ios.yml
git commit -m "chore(ios): run the colour drift check in ci"
```

### Task 13: The convention line in `apps/ios/CLAUDE.md`

**Files:**
- Modify: `apps/ios/CLAUDE.md` (Swift conventions list, after the `.task { ... }` bullet)

- [ ] **Step 1: Add the bullet**

```markdown
- **Colours are generated.** `npm run generate:colors` writes `Assets.xcassets/M3/` and `Shared/Theme/PebblesColors.swift` from Android's `ColorSchemes.kt`. Feature code uses `Color.pebbles.<role>` only, and `ThemeLiteralsTests` fails on anything else.
```

- [ ] **Step 2: Commit**

```bash
git add apps/ios/CLAUDE.md
git commit -m "docs(ios): colours are generated, feature code uses color.pebbles"
```

### Task 14: Verify A1, check on device, open the PR

**Files:** none

- [ ] **Step 1: Full gates**

```bash
cd /Users/alexis/code/pbbls
npm run lint --workspace=apps/web
npm run test --workspace=apps/web
npm run lint --workspace=@pbbls/ios
rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*
npm run test --workspace=@pbbls/ios
```

Expected: every command green. The iOS suite includes `PebblesColorsTests` and `ThemeLiteralsTests`.

- [ ] **Step 2: Device checks (spec §13)**

Install on the maintainer's iPhone (memory: iOS device deploy recipe). Check two things:
1. The karma Live Activity sparkle on the Lock Screen and in the Dynamic Island resolves to `primary`.
2. Take before/after screenshots of one `.alert` / `.confirmationDialog` (Settings → Delete account) and one form (the record flow name step). These show the global tint moving from `#C07A7A` to `#8E4955` / `#FFB2BC`. Apart from that tint, nothing on screen should change.

- [ ] **Step 3: Push and open the PR**

```bash
git fetch origin && git log --oneline origin/main..main   # expect nothing: main has no local-only commits
gh stack push
gh stack submit --auto
gh pr edit feat/921-ios-m3-colour-generator \
  --title "feat(ios): generate the m3 colour scheme from the android export" \
  --add-label feat --add-label ui --add-label ios --add-label no-lab-note \
  --milestone "M64 · New Path" \
  --body-file <a temp file outside the repo with the body below>
```

Body: open with `Part of #921`. Then list the key files from the file map (A1 rows). Then the **new patterns to accept** (spec §12): a Swift entry point generated by a web script, and the debt ratchet. Then the AccentColor before/after shots, the Live Activity check, the Arkaik outcome from Task 1, and the trailing session attribution.

---

## Part A2 — `feat/921-ios-orange-error`

PR title: `feat(ios): errors are orange, destructive controls take the error role`. Body starts `Closes #921`.

### Task 15: Add the branch to the stack

- [ ] **Step 1**

```bash
cd /Users/alexis/code/pbbls
git checkout feat/921-ios-m3-colour-generator
gh stack add feat/921-ios-orange-error
git branch --show-current   # expect: feat/921-ios-orange-error
```

### Task 16: The 21 error inks take `Color.pebbles.error`

**Files (all under `apps/ios/Pebbles/Features/`, re-grepped 2026-10-20):**

| Site | Site |
|---|---|
| `Capture/CaptureFlowView.swift:191` | `Path/PebbleFormView.swift:140` |
| `Record/Steps/RecordPhotoStep.swift:103` | `Welcome/WelcomeView.swift:161` |
| `Auth/AuthView.swift:56` | `Connections/InviteAcceptSheet.swift:80` |
| `Auth/AuthView.swift:92` | `Profile/Sheets/CreateSoulSheet.swift:44` |
| `PebbleMedia/AttachedPhotoView.swift:58` | `Profile/Sheets/EditSoulSheet.swift:56` |
| `Complete/Steps/CompletePrivacyStep.swift:18` | `Profile/Sheets/SettingsSheet.swift:137` |
| `Path/EditMomentSheet.swift:234` | `Profile/Sheets/SettingsSheet.swift:391` |
| `Profile/Sheets/EditCollectionSheet.swift:70` | `Profile/Sheets/SettingsSheet.swift:467` |
| `Profile/Sheets/CreateCollectionSheet.swift:53` | `Glyph/Views/GlyphCarveSheet.swift:90` |
| `Glyph/Views/GlyphDetailDrawer.swift:65` | `Glyph/Views/GlyphDetailDrawer.swift:70` |
| `Glyph/Views/GlyphsListView.swift:85` | |

- Modify: `apps/ios/PebblesTests/ThemeLiteralsTests.swift` (`debt`)

- [ ] **Step 1: Lower the debt first (the test goes red)**

In `ThemeLiteralsTests.debt`, delete the entries for `Features/Auth/AuthView.swift`, `Features/Capture/CaptureFlowView.swift`, `Features/Complete/Steps/CompletePrivacyStep.swift`, `Features/Connections/InviteAcceptSheet.swift`, `Features/Glyph/Views/GlyphDetailDrawer.swift`, `Features/Glyph/Views/GlyphsListView.swift`, `Features/Path/EditMomentSheet.swift`, `Features/Path/PebbleFormView.swift`, `Features/Profile/Sheets/CreateCollectionSheet.swift`, `Features/Profile/Sheets/CreateSoulSheet.swift`, `Features/Profile/Sheets/EditCollectionSheet.swift` and `Features/Profile/Sheets/EditSoulSheet.swift`. Then set:

```swift
        "Features/Glyph/Views/GlyphCarveSheet.swift": 1,
        "Features/PebbleMedia/AttachedPhotoView.swift": 3,
        "Features/Profile/Sheets/SettingsSheet.swift": 1,
        "Features/Record/Steps/RecordPhotoStep.swift": 2,
        "Features/Welcome/WelcomeView.swift": 1,
```

Run: `cd apps/ios && xcodegen generate && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' -only-testing:PebblesTests/ThemeLiteralsTests -quiet`
Expected: FAIL with "new literal" on each of the 17 files.

- [ ] **Step 2: Replace the ink**

```bash
cd /Users/alexis/code/pbbls/apps/ios/Pebbles/Features
perl -pi -e 's/\.foregroundStyle\(\.red\)/.foregroundStyle(Color.pebbles.error)/g' \
  Capture/CaptureFlowView.swift Record/Steps/RecordPhotoStep.swift Auth/AuthView.swift \
  PebbleMedia/AttachedPhotoView.swift Complete/Steps/CompletePrivacyStep.swift Path/EditMomentSheet.swift \
  Path/PebbleFormView.swift Welcome/WelcomeView.swift Connections/InviteAcceptSheet.swift \
  Profile/Sheets/CreateSoulSheet.swift Profile/Sheets/EditSoulSheet.swift Profile/Sheets/SettingsSheet.swift \
  Profile/Sheets/EditCollectionSheet.swift Profile/Sheets/CreateCollectionSheet.swift \
  Glyph/Views/GlyphCarveSheet.swift Glyph/Views/GlyphDetailDrawer.swift Glyph/Views/GlyphsListView.swift
perl -pi -e 's{\.foregroundStyle\(Color\.pebbles\.error\) // #921}{.foregroundStyle(Color.pebbles.error)}' \
  Capture/CaptureFlowView.swift Complete/Steps/CompletePrivacyStep.swift Path/EditMomentSheet.swift Path/PebbleFormView.swift
cd /Users/alexis/code/pbbls
grep -rn '\.red\b' apps/ios/Pebbles/Features | grep -v 'Features/Path/Stone/\|Features/StoneLab/'   # expect no output
grep -rc 'Color.pebbles.error' apps/ios/Pebbles/Features | awk -F: '{s+=$2} END {print s}'          # expect 21
```

- [ ] **Step 3: Run the ratchet**

Run the same `-only-testing:PebblesTests/ThemeLiteralsTests` command.
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features apps/ios/PebblesTests/ThemeLiteralsTests.swift
git commit -m "feat(ios): error text takes the orange error role"
```

### Task 17: Owned destructive buttons and the swipe action drop `role: .destructive`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Record/Steps/RecordPhotoStep.swift:124,140`
- Modify: `apps/ios/Pebbles/Features/PebbleMedia/ExistingSnapRow.swift:76`
- Modify: `apps/ios/Pebbles/Features/PebbleMedia/AttachedPhotoView.swift:82`
- Modify: `apps/ios/Pebbles/Features/Connections/InviteSheet.swift:84`
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift:462`
- Modify: `apps/ios/Pebbles/Features/Profile/Components/CollectionGlyphSection.swift:36`
- Modify: `apps/ios/Pebbles/Features/Glyph/Views/GlyphCarveSheet.swift:107-114`
- Modify: `apps/ios/Pebbles/Features/Profile/Lists/CollectionsListView.swift:102` (swipe)
- Modify: `apps/ios/PebblesTests/ThemeLiteralsTests.swift` (`debt`)

- [ ] **Step 1: Lower the debt (red)**

In `debt`, delete `Features/Record/Steps/RecordPhotoStep.swift`, `Features/Connections/InviteSheet.swift`, `Features/Profile/Sheets/SettingsSheet.swift`, `Features/Profile/Components/CollectionGlyphSection.swift`, `Features/Glyph/Views/GlyphCarveSheet.swift` and `Features/Profile/Lists/CollectionsListView.swift`, and set:

```swift
        "Features/PebbleMedia/AttachedPhotoView.swift": 2,
        "Features/PebbleMedia/ExistingSnapRow.swift": 2,
```

Run the ratchet. Expected: FAIL ("new literal") on those 8 files.

- [ ] **Step 2: Edit each site**

`RecordPhotoStep.swift:124-126`:

```swift
        Button(action: onRemove) {
            Image(systemName: "xmark.circle.fill")
                .foregroundStyle(Color.pebbles.error)
        }
```

`RecordPhotoStep.swift:140`:

```swift
            Button("Remove", action: onRemove)
                .pebblesFont(.subhead)
                .foregroundStyle(Color.pebbles.error)
```

`ExistingSnapRow.swift:76-78` and `AttachedPhotoView.swift:82-84` (identical shape):

```swift
            Button(action: onRemove) {
                Image(systemName: "xmark.circle.fill")
                    .foregroundStyle(Color.pebbles.error)
            }
```

`InviteSheet.swift:84`: change `Button(role: .destructive) {` to `Button {`, and after `.disabled(isRotating)` add `.foregroundStyle(Color.pebbles.error)`.

`SettingsSheet.swift:462`: change `Button(role: .destructive) {` to `Button {`. Line 467 already reads `.foregroundStyle(Color.pebbles.error)` from Task 16.

`CollectionGlyphSection.swift:36`:

```swift
                Button("Remove glyph") { glyph = nil }
                    .foregroundStyle(Color.pebbles.error)
                    .pebblesListRow(position: .bottom)
```

`GlyphCarveSheet.swift:107-114`. The Clear capsule is a destructive control, so its ground moves to the error container now rather than in A3b:

```swift
                Button {
                    strokes.removeAll()
                } label: {
                    Label("Clear", systemImage: "trash")
                        .foregroundStyle(Color.pebbles.error)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(Capsule().fill(Color.pebbles.errorContainer))
                }
```

`CollectionsListView.swift:102-106`, the swipe action. Dropping the role also stops the row animating away before the confirmation dialog:

```swift
                        Button {
                            pendingDeletion = collection
                        } label: {
                            Label("Delete", systemImage: "trash")
                        }
                        .tint(Color.pebbles.error)
```

- [ ] **Step 3: Check the role count and run the ratchet**

```bash
grep -rn 'role: *\.destructive' apps/ios/Pebbles | wc -l    # expect 13 (8 dialogs + 5 menus still to do)
```

Run the ratchet. Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features apps/ios/PebblesTests/ThemeLiteralsTests.swift
git commit -m "feat(ios): owned destructive buttons take the error role, not system red"
```

### Task 18: D9 device trial — is `.tint` honoured on a `Menu` / `.contextMenu` item?

**Files (trial edits, kept only on outcome A):**
- `apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift:66-70` (Menu)
- `apps/ios/Pebbles/Features/Path/Components/PathPebbleRow.swift:76-80` (context menu)

- [ ] **Step 1: Apply the trial**

`PebbleDetailSheet.swift:66-70`:

```swift
                                Button {
                                    if let detail { pendingDeletion = MomentDeleteTarget(detail: detail) }
                                } label: {
                                    Label("Delete", systemImage: "trash")
                                }
                                .tint(Color.pebbles.error)
```

`PathPebbleRow.swift:76-80`:

```swift
        .contextMenu {
            Button(action: onDelete) {
                Label("Delete", systemImage: "trash")
            }
            .tint(Color.pebbles.error)
        }
```

- [ ] **Step 2: Build, install and look**

Use a clean build (`rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*`). Run on a physical iPhone on iOS 26, and on iOS 17: a physical device if one is available, otherwise the iOS 17.x simulator runtime, recorded as such. On each, in light and dark mode, check two places:
1. The pebble card's ⋮ menu (open a pebble, then tap the ellipsis).
2. A long press on a Path row.

Record per OS: is "Delete" (title and icon) drawn in the orange `error` role (`#9B4500` light / `#FFB68E` dark), or in plain menu ink?

- [ ] **Step 3: Decide**

- **Outcome A — honoured on both OS versions, in both menus:** keep the trial edits and go to Task 19A.
- **Outcome B — ignored on either OS or in either menu:** revert the trial with `git checkout -- apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift apps/ios/Pebbles/Features/Path/Components/PathPebbleRow.swift` and go to Task 19B.1.

Plain menu ink is not an outcome, and neither is restoring `role: .destructive`. Note the result (OS versions, devices or simulator, what was seen) for the decision-log entry in Task 20.

### Task 19A (outcome A only): every Delete menu item takes the tint

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift:66` (trial edit kept)
- Modify: `apps/ios/Pebbles/Features/Path/Components/PathPebbleRow.swift:77` (trial edit kept)
- Modify: `apps/ios/Pebbles/Components/PebbleRow.swift:36`
- Modify: `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift:105`
- Modify: `apps/ios/Pebbles/Features/Connections/ConnectionsListView.swift:93`
- Modify: `apps/ios/PebblesTests/ThemeLiteralsTests.swift` (`debt`)

- [ ] **Step 1: Lower the debt (red)**

Delete the `debt` entries for `Components/PebbleRow.swift`, `Features/Path/PebbleDetailSheet.swift`, `Features/Profile/Lists/SoulsListView.swift` and `Features/Connections/ConnectionsListView.swift`. Set `"Features/Path/Components/PathPebbleRow.swift": 2`. Run the ratchet. Expected: FAIL on `PebbleRow`, `SoulsListView` and `ConnectionsListView`.

- [ ] **Step 2: Edit the three remaining menus**

`Components/PebbleRow.swift:35-39`:

```swift
        .contextMenu {
            Button(action: onDelete) {
                Label("Delete", systemImage: "trash")
            }
            .tint(Color.pebbles.error)
        }
```

`SoulsListView.swift:104-110`:

```swift
                        .contextMenu {
                            Button {
                                pendingDeletion = item
                            } label: {
                                Label("Delete", systemImage: "trash")
                            }
                            .tint(Color.pebbles.error)
                        }
```

`ConnectionsListView.swift:92-98`:

```swift
                        .contextMenu {
                            Button {
                                pendingRemoval = row
                            } label: {
                                Label("Remove", systemImage: "person.badge.minus")
                            }
                            .tint(Color.pebbles.error)
                        }
```

- [ ] **Step 3: Verify**

```bash
grep -rn 'role: *\.destructive' apps/ios/Pebbles   # expect exactly the 8 dialog sites:
# Capture/CaptureFlowView.swift:112, Path/MomentDeleteDialog.swift:94, Profile/Sheets/SettingsSheet.swift:192,
# Profile/Lists/SoulsListView.swift:47, Connections/ConnectionsListView.swift:44 and :47,
# Profile/Lists/CollectionsListView.swift:47, Glyph/Views/GlyphCarveSheet.swift:54
```

Run the ratchet. Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles apps/ios/PebblesTests/ThemeLiteralsTests.swift
git commit -m "feat(ios): delete menu items take the error tint"
```

Skip to Task 20.

### Task 19B.1 (outcome B only): `PebblesMenu`, the one shared hand-built menu

**Files:**
- Create: `apps/ios/Pebbles/Components/Menus/PebblesMenu.swift`

- [ ] **Step 1: Write the component**

```swift
import SwiftUI

/// One row of a `PebblesMenu`.
struct PebblesMenuItem: Identifiable {
    let id = UUID()
    let title: LocalizedStringResource
    let systemImage: String
    let isDestructive: Bool
    let action: () -> Void

    init(
        title: LocalizedStringResource,
        systemImage: String,
        isDestructive: Bool = false,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.systemImage = systemImage
        self.isDestructive = isDestructive
        self.action = action
    }
}

/// The menu every orange Delete lives in (D9, decision log 2026-10-20, #921).
/// iOS draws `Menu` and `.contextMenu` items through UIMenu, which ignored a
/// SwiftUI tint in the A2 device trial, so the error role needs a surface of
/// its own. This is a popover kept as a popover on iPhone. That gives
/// outside-tap dismissal and the system's focus return to the trigger. The
/// list adds the VoiceOver modal trait, moves focus to its first item and
/// dismisses on the escape gesture. No call site rolls its own menu.
struct PebblesMenu<Trigger: View>: View {
    let items: [PebblesMenuItem]
    @ViewBuilder let trigger: () -> Trigger

    @State private var isPresented = false

    var body: some View {
        Button { isPresented = true } label: { trigger() }
            .popover(isPresented: $isPresented) {
                PebblesMenuList(items: items) { isPresented = false }
            }
    }
}

extension View {
    /// `.contextMenu` for rows whose actions include an orange Delete. A long
    /// press opens the same list, and VoiceOver gets the items as actions.
    func pebblesContextMenu(_ items: [PebblesMenuItem]) -> some View {
        modifier(PebblesContextMenu(items: items))
    }
}

private struct PebblesContextMenu: ViewModifier {
    let items: [PebblesMenuItem]
    @State private var isPresented = false

    func body(content: Content) -> some View {
        content
            .simultaneousGesture(LongPressGesture(minimumDuration: 0.5).onEnded { _ in isPresented = true })
            .accessibilityActions {
                ForEach(items) { item in
                    Button(action: item.action) { Text(item.title) }
                }
            }
            .popover(isPresented: $isPresented) {
                PebblesMenuList(items: items) { isPresented = false }
            }
    }
}

private struct PebblesMenuList: View {
    let items: [PebblesMenuItem]
    let dismiss: () -> Void

    @AccessibilityFocusState private var focusedItem: UUID?

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(items) { item in
                Button {
                    dismiss()
                    item.action()
                } label: {
                    Label { Text(item.title) } icon: { Image(systemName: item.systemImage) }
                        .labelStyle(.titleAndIcon)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, Spacing.lg)
                        .padding(.vertical, Spacing.md)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .foregroundStyle(item.isDestructive ? Color.pebbles.error : Color.pebbles.onSurface)
                .accessibilityFocused($focusedItem, equals: item.id)
                if item.id != items.last?.id {
                    Rectangle().fill(Color.pebbles.rule).frame(height: 1)
                }
            }
        }
        .frame(minWidth: 220)
        .background(Color.pebbles.surfaceContainer)
        .presentationCompactAdaptation(.popover)
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
        .accessibilityAction(.escape, dismiss)
        .onAppear { focusedItem = items.first?.id }
    }
}
```

- [ ] **Step 2: Build and lint**

```bash
cd apps/ios && xcodegen generate && rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*
xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' build -quiet
cd /Users/alexis/code/pbbls && npm run lint --workspace=@pbbls/ios
```

Expected: build succeeds and there are no lint errors. `Components/Menus/` is scanned by `ThemeLiteralsTests` and holds no literal.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Components/Menus/PebblesMenu.swift
git commit -m "feat(ios): add the shared pebbles menu for orange delete actions"
```

### Task 19B.2 (outcome B only): every menu with a Delete adopts `PebblesMenu`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift:57-75`
- Modify: `apps/ios/Pebbles/Features/Path/Components/PathPebbleRow.swift:76-80`
- Modify: `apps/ios/Pebbles/Components/PebbleRow.swift:35-39`
- Modify: `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift:104-110`
- Modify: `apps/ios/Pebbles/Features/Connections/ConnectionsListView.swift:92-98`
- Modify: `apps/ios/PebblesTests/ThemeLiteralsTests.swift` (`debt`)

- [ ] **Step 1: Lower the debt (red)**

Delete the `debt` entries for `Components/PebbleRow.swift`, `Features/Path/PebbleDetailSheet.swift`, `Features/Profile/Lists/SoulsListView.swift` and `Features/Connections/ConnectionsListView.swift`, and set `"Features/Path/Components/PathPebbleRow.swift": 2`. Run the ratchet. Expected: FAIL on those five files.

- [ ] **Step 2: Replace the card's ⋮ menu**

In `PebbleDetailSheet.swift`, replace the whole `Menu { … } label: { Image(systemName: "ellipsis.circle") }` block (lines 57-73) with:

```swift
                            PebblesMenu(items: menuItems) {
                                Image(systemName: "ellipsis.circle")
                            }
```

Keep the `.disabled(detail == nil || isDeleting || isLoading)` and `.accessibilityLabel(Text("More"))` modifiers that follow it. Add this computed property to the view, next to its other private members:

```swift
    private var menuItems: [PebblesMenuItem] {
        var items = [PebblesMenuItem(title: "Edit moment", systemImage: "pencil") { isPresentingEditMoment = true }]
        if detail?.pebble != nil {
            items.append(PebblesMenuItem(title: "Edit pebble", systemImage: "circle.hexagongrid") {
                isPresentingEditPebble = true
            })
        }
        items.append(PebblesMenuItem(title: "Delete", systemImage: "trash", isDestructive: true) {
            if let detail { pendingDeletion = MomentDeleteTarget(detail: detail) }
        })
        return items
    }
```

- [ ] **Step 3: Replace the four context menus**

`PathPebbleRow.swift` and `Components/PebbleRow.swift`: replace the `.contextMenu { … }` block with:

```swift
        .pebblesContextMenu([
            PebblesMenuItem(title: "Delete", systemImage: "trash", isDestructive: true, action: onDelete)
        ])
```

`SoulsListView.swift`:

```swift
                        .pebblesContextMenu([
                            PebblesMenuItem(title: "Delete", systemImage: "trash", isDestructive: true) {
                                pendingDeletion = item
                            }
                        ])
```

`ConnectionsListView.swift`:

```swift
                        .pebblesContextMenu([
                            PebblesMenuItem(title: "Remove", systemImage: "person.badge.minus", isDestructive: true) {
                                pendingRemoval = row
                            }
                        ])
```

- [ ] **Step 4: Verify**

```bash
grep -rn '\.contextMenu\|^\s*Menu {' apps/ios/Pebbles/Features apps/ios/Pebbles/Components   # no menu that holds a Delete remains
grep -rn 'role: *\.destructive' apps/ios/Pebbles | wc -l                                       # expect 8 (the dialog sites)
```

Run the ratchet and then `npm run test --workspace=@pbbls/ios` after `rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*`. Expected: PASS. Open `Localizable.xcstrings` in Xcode and confirm "Edit moment", "Edit pebble", "Delete" and "Remove" are not `New` or `Stale`. They keep their existing keys.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles apps/ios/PebblesTests/ThemeLiteralsTests.swift
git commit -m "feat(ios): delete menus use the shared pebbles menu"
```

### Task 19B.3 (outcome B only): VoiceOver and dismissal check on device

- [ ] **Step 1: Check, on device, for the card ⋮ menu and one context menu (Path row long press)**

1. A short tap on a Path row still opens the pebble, and a long press opens the menu without also opening the pebble. If the tap also fires after the long press, replace `.simultaneousGesture(...)` with `.onLongPressGesture(minimumDuration: 0.5) { isPresented = true }` in `PebblesContextMenu` and re-check.
2. A tap outside the menu dismisses it.
3. With VoiceOver on, focus lands on the first item when the menu opens and stays inside it (modal). The two-finger scrub (escape) dismisses it. Focus then returns to the trigger.
4. VoiceOver's actions rotor on a Path row offers "Delete".

Fix any failure in `PebblesMenu.swift` only, then commit `fix(ios): <what> in the pebbles menu`.

### Task 20: The decision-log entry

**Files:**
- Modify: `docs/decisions/log.md` (append at the end)

- [ ] **Step 1: Append the §11 entry**

Append the spec's §11 block verbatim, with these changes:
- Date the heading with the day A2 merges (`## <YYYY-MM-DD> — iOS adopts the brand Material 3 scheme; errors are orange, system dialogs keep their red (#921)`).
- Under the "Destructive menus" bullet, replace "A2 records here which one the device trial gave." with the outcome sentence that applies:
  - Outcome A: `The A2 device trial (iOS <x> on <device>, iOS <y> on <device or simulator>) showed the tint honoured on Menu and .contextMenu items, so every Delete menu stays a system menu with .tint(Color.pebbles.error).`
  - Outcome B: `The A2 device trial (iOS <x> on <device>, iOS <y> on <device or simulator>) showed UIMenu ignoring the tint, so every menu with an orange Delete uses PebblesMenu (Components/Menus/PebblesMenu.swift), the card's ⋮ menu included.`
- In **Refs**, add `apps/ios/Pebbles/Components/Menus/PebblesMenu.swift` on outcome B.

- [ ] **Step 2: Commit**

```bash
git add docs/decisions/log.md
git commit -m "docs(ios): record the m3 scheme, the orange error and the dialog exception"
```

### Task 21: Verify A2, device pass, open the PR

- [ ] **Step 1: Gates**

```bash
npm run lint --workspace=@pbbls/ios
rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*
npm run test --workspace=@pbbls/ios
grep -rn '\.red\b' apps/ios/Pebbles/Features | grep -v 'Features/Path/Stone/\|Features/StoneLab/'   # expect no output
grep -rn 'role: *\.destructive' apps/ios/Pebbles                                                    # expect the 8 dialog sites only
```

- [ ] **Step 2: Device pass (spec §9 A2)**

Use light and dark, with Increase Contrast on and off. Check:
- One error text per feature: auth (wrong password), soul (create with an empty name if validated), collection (save failure if reachable), glyph (`GlyphsListView` load error, simulated offline) and settings (handle error).
- Every owned destructive control in Task 17, plus the menus from 19A/19B.
- Every system dialog of §6.3, which is still red: Capture discard, moment delete, delete account, soul delete, connection remove ×2, collection delete, glyph discard.

With VoiceOver, a dialog's destructive button is announced as destructive, and the in-app delete buttons as plain buttons.

- [ ] **Step 3: Push and open the PR**

```bash
git fetch origin && git log --oneline origin/main..main   # expect nothing
gh stack push
gh stack submit --auto
gh pr edit feat/921-ios-orange-error \
  --title "feat(ios): errors are orange, destructive controls take the error role" \
  --add-label feat --add-label ui --add-label ios --add-label no-lab-note \
  --milestone "M64 · New Path" \
  --body-file <temp file>
```

Body: open with `Closes #921`. Then list the touched files and the D9 outcome. On outcome B, name the new pattern to accept (`PebblesMenu`, spec §12). Note that the decision-log entry is included. End with the session attribution.

---

## Part A3a — `feat/921-ios-m3-roles-shell`

PR title: `feat(ios): shell, shared components and capture flows speak m3 roles`. Body starts `Part of #921`.

**How every A3 file is migrated.** Each A3 task below runs the same two passes:

1. **Mechanical pass.** A perl substitution for the tokens that have one meaning in the §4 table, run only on the task's files:

```bash
perl -pi -e '
  s/(?<!\w)(?:Color)?\.system\.foreground\b/Color.pebbles.onSurface/g;
  s/(?<!\w)(?:Color)?\.system\.secondary\b/Color.pebbles.onSurfaceVariant/g;
  s/(?<!\w)(?:Color)?\.system\.onLight\b/Color.pebbles.googleCapsuleInk/g;
  s/(?<!\w)(?:Color)?\.accent\.primary\b/Color.pebbles.primary/g;
  s/(?<!\w)(?:Color)?\.accent\.surface\b/Color.pebbles.primaryContainer/g;
  s/(?<!\w)(?:Color)?\.accent\.secondary\b/Color.pebbles.secondaryContainer/g;
' <files>
```

2. **By-use pass.** Every remaining `system.background`, `system.muted`, `accent.light` and `accent.shaded`, and every literal, is mapped by what it is used as, through the task's table. The tables were classified on 2026-10-20. Also check the content drawn on a `primaryContainer` / `secondaryContainer` fill (former `accent.surface` / `accent.secondary`): text and icons there take the on-pair (`onPrimaryContainer` / `onSecondaryContainer`). Sites below the file's first `#Preview` are fixtures and take `surface`. Finally, rewrite doc comments in the touched files that still name an old token (`system.muted`, `accent.surface`, …) to name the role.

After each task: `grep -nE '(Color)?\.(system|accent)\.' <files>` prints nothing, and the ratchet passes with the task's debt entries deleted.

### Task 22: Add the branch to the stack

- [ ] **Step 1**

```bash
git checkout feat/921-ios-orange-error
gh stack add feat/921-ios-m3-roles-shell
```

### Task 23: `Theme/` speaks roles

**Files:**
- Modify: `apps/ios/Pebbles/Theme/PebblesScreen.swift`
- Modify: `apps/ios/Pebbles/Theme/PebblesList.swift:37,71,81,84,94,108`
- Modify: `apps/ios/Pebbles/Theme/SurfaceTile.swift:27,34,41`
- Modify: `apps/ios/Pebbles/Theme/PebblesToolbarTitle.swift:22,37,45`
- Modify: `apps/ios/Pebbles/Theme/PebbleToolbarButton.swift:25,40`
- Modify: `apps/ios/Pebbles/Theme/ProfileCard.swift:21`

- [ ] **Step 1: `PebblesScreen.swift` (spec §4)**

```swift
private struct PebblesLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        Label {
            configuration.title
        } icon: {
            configuration.icon
                .foregroundStyle(Color.pebbles.primary)
        }
    }
}

private struct PebblesScreen: ViewModifier {
    func body(content: Content) -> some View {
        content
            .tint(Color.pebbles.primary)
            .foregroundStyle(Color.pebbles.onSurfaceVariant)
            .labelStyle(PebblesLabelStyle())
            .scrollContentBackground(.hidden)
            .background(Color.pebbles.surface)
            .toolbarBackground(Color.pebbles.surface, for: .navigationBar)
    }
}
```

In its doc comment, change "Background is always `system.background`" to "Background is always the `surface` role".

- [ ] **Step 2: The other five files, by use**

| Site | Used as | → |
|---|---|---|
| `PebblesList.swift:37` `.listRowSeparatorTint` | separator | `Color.pebbles.rule` |
| `PebblesList.swift:71,81,84,94` `.stroke` | card outline | `Color.pebbles.rule` |
| `PebblesList.swift:108` | section header ink-2 | `Color.pebbles.onSurfaceVariant` |
| `SurfaceTile.swift:27` | icon: muted placeholder / default on the wash | `muted ? Color.pebbles.outline : (iconTint ?? Color.pebbles.onPrimaryContainer)` |
| `SurfaceTile.swift:34` | label: muted placeholder / default on the wash | `muted ? Color.pebbles.outline : (labelColor ?? Color.pebbles.onPrimaryContainer)` |
| `SurfaceTile.swift:41` | tinted wash | `backgroundColor ?? Color.pebbles.primaryContainer` |
| `PebblesToolbarTitle.swift:22,37` | ink-2 | `Color.pebbles.onSurfaceVariant` |
| `PebblesToolbarTitle.swift:45` (preview) | ground | `Color.pebbles.surface` |
| `PebbleToolbarButton.swift:25` `.tint` | ink-2 | `Color.pebbles.onSurfaceVariant` |
| `PebbleToolbarButton.swift:40` (preview) | ground | `Color.pebbles.surface` |
| `ProfileCard.swift:21` | card outline | `Color.pebbles.rule` |

- [ ] **Step 3: Build, then commit**

```bash
grep -nE '(Color)?\.(system|accent)\.' apps/ios/Pebbles/Theme/{PebblesScreen,PebblesList,SurfaceTile,PebblesToolbarTitle,PebbleToolbarButton,ProfileCard}.swift   # expect nothing
cd apps/ios && rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-* && xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' build -quiet
cd /Users/alexis/code/pbbls
git add apps/ios/Pebbles/Theme
git commit -m "feat(ios): theme modifiers speak m3 roles"
```

### Task 24: UIKit bridges — `PebblesApp`, `OnboardingView`, `RootView`

**Files:**
- Modify: `apps/ios/Pebbles/PebblesApp.swift:60-95`
- Modify: `apps/ios/Pebbles/Features/Onboarding/OnboardingView.swift:24-25,57`
- Modify: `apps/ios/Pebbles/RootView.swift:110`
- Modify: `apps/ios/PebblesTests/ThemeLiteralsTests.swift` (`debt`)

- [ ] **Step 1: Lower the debt (red)**

Delete `"Features/Onboarding/OnboardingView.swift": 3` from `debt`. Run the ratchet. Expected: FAIL on that file.

- [ ] **Step 2: `PebblesApp.swift`**

Replace the body of `configureSegmentedControlAppearance()`:

```swift
    private static func configureSegmentedControlAppearance() {
        let proxy = UISegmentedControl.appearance()
        proxy.backgroundColor = UIColor.pebbles(.surfaceContainerHighest)
        proxy.selectedSegmentTintColor = UIColor.pebbles(.secondaryContainer)

        proxy.setTitleTextAttributes([
            .foregroundColor: UIColor.pebbles(.onSecondaryContainer),
            .font: UIFont.systemFont(ofSize: UIFont.systemFontSize, weight: .medium)
        ], for: .selected)

        proxy.setTitleTextAttributes([
            .foregroundColor: UIColor.pebbles(.onSurfaceVariant),
            .font: UIFont.systemFont(ofSize: UIFont.systemFontSize, weight: .regular)
        ], for: .normal)
    }
```

In `configureNavigationBarAppearance()`, replace `guard let foreground = UIColor(named: "SystemForeground") else { return }` with `let foreground = UIColor.pebbles(.onSurface)`. Change its doc comment to "picks up the `onSurface` role instead of SwiftUI's default `UIColor.label`".

- [ ] **Step 3: `OnboardingView.swift`**

```swift
        UIPageControl.appearance().currentPageIndicatorTintColor = UIColor.pebbles(.primary)
        UIPageControl.appearance().pageIndicatorTintColor = UIColor.pebbles(.outlineVariant)
```

Line 57 is a label on a `.borderedProminent` (primary) fill: `.foregroundStyle(.white)` becomes `.foregroundStyle(Color.pebbles.onPrimary)`.

- [ ] **Step 4: `RootView.swift:110`**

`.background(Color.system.background.ignoresSafeArea())` becomes `.background(Color.pebbles.surface.ignoresSafeArea())`.

- [ ] **Step 5: Ratchet, build, commit**

Run the ratchet. Expected: PASS.

```bash
git add apps/ios/Pebbles/PebblesApp.swift apps/ios/Pebbles/Features/Onboarding/OnboardingView.swift apps/ios/Pebbles/RootView.swift apps/ios/PebblesTests/ThemeLiteralsTests.swift
git commit -m "feat(ios): uikit appearance proxies read m3 roles"
```

### Task 25: `Components/`, the Google contrast test and the contrast script

**Files:**
- Modify: `apps/ios/Pebbles/Components/Auth/LegalDisclaimerText.swift`, `apps/ios/Pebbles/Components/Auth/PebblesAuthSwitcher.swift`
- Modify: `apps/ios/Pebbles/Components/Buttons/AppleSignInButton.swift`, `apps/ios/Pebbles/Components/Buttons/GoogleSignInButton.swift`, `apps/ios/Pebbles/Components/Buttons/PebblesPrimaryButtonStyle.swift`
- Modify: `apps/ios/Pebbles/Components/Checkboxes/PebblesCheckbox.swift`, `apps/ios/Pebbles/Components/Inputs/PebblesTextInput.swift`
- Modify: `apps/ios/PebblesTests/GoogleSignInButtonContrastTests.swift:44-51`
- Modify: `apps/ios/Scripts/verify-color-contrast.mjs`, plus the comment above `Verify colour contrast` in `.github/workflows/ios.yml`
- Modify: `apps/ios/PebblesTests/ThemeLiteralsTests.swift` (`debt`)

- [ ] **Step 1: Update the Google test first (red)**

Replace `onLightIsAppearanceIndependent` in `GoogleSignInButtonContrastTests.swift`:

```swift
    @Test("googleCapsuleInk resolves identically in all four appearances")
    func capsuleInkIsAppearanceIndependent() {
        let looks: [(UIUserInterfaceStyle, UIAccessibilityContrast)] = [(.light, .normal), (.dark, .normal), (.light, .high), (.dark, .high)]
        let resolved = looks.map { style, contrast in
            UIColor.pebbles(.googleCapsuleInk).resolvedColor(with: UITraitCollection { traits in
                traits.userInterfaceStyle = style
                traits.accessibilityContrast = contrast
            })
        }
        #expect(Set(resolved).count == 1)
    }
```

This test passes as soon as it is written. `M3/googleCapsuleInk` exists since A1 and holds the same `#4A3639` as `SystemOnLight`, so the repoint is not visible by value. `labelIsLegible` keeps proving the capsule's contrast once `labelColor` moves in Step 2.

Delete the debt entries `Components/Buttons/PebblesPrimaryButtonStyle.swift`, `Components/Checkboxes/PebblesCheckbox.swift` and `Components/Inputs/PebblesTextInput.swift`. Run `-only-testing:PebblesTests/GoogleSignInButtonContrastTests -only-testing:PebblesTests/ThemeLiteralsTests`. Expected: the Google suite passes, and the ratchet FAILs with "new literal" on the three files.

- [ ] **Step 2: Mechanical pass**

```bash
cd /Users/alexis/code/pbbls/apps/ios/Pebbles/Components
perl -pi -e '
  s/(?<!\w)(?:Color)?\.system\.foreground\b/Color.pebbles.onSurface/g;
  s/(?<!\w)(?:Color)?\.system\.secondary\b/Color.pebbles.onSurfaceVariant/g;
  s/(?<!\w)(?:Color)?\.system\.onLight\b/Color.pebbles.googleCapsuleInk/g;
  s/(?<!\w)(?:Color)?\.accent\.primary\b/Color.pebbles.primary/g;
  s/(?<!\w)(?:Color)?\.accent\.surface\b/Color.pebbles.primaryContainer/g;
  s/(?<!\w)(?:Color)?\.accent\.secondary\b/Color.pebbles.secondaryContainer/g;
' Auth/LegalDisclaimerText.swift Auth/PebblesAuthSwitcher.swift Buttons/AppleSignInButton.swift \
  Buttons/GoogleSignInButton.swift Buttons/PebblesPrimaryButtonStyle.swift Checkboxes/PebblesCheckbox.swift \
  Inputs/PebblesTextInput.swift
cd /Users/alexis/code/pbbls
```

- [ ] **Step 3: By-use pass**

| Site | Used as | → |
|---|---|---|
| `GoogleSignInButton.swift:25` capsule stroke | control boundary | `Color.pebbles.outline` |
| `GoogleSignInButton.swift:32` `surface = Color.white` | pinned by Google's guidelines (file exempt) | unchanged |
| `GoogleSignInButton.swift:42`, `AppleSignInButton.swift:29`, `PebblesAuthSwitcher.swift:23`, `LegalDisclaimerText.swift:36` (previews) | ground | `Color.pebbles.surface` |
| `AppleSignInButton.swift:17,21` white on black | Apple's guidelines (file exempt) | unchanged |
| `PebblesPrimaryButtonStyle.swift:17` `ProgressView().tint(.white)` | ink on a primary fill | `.tint(Color.pebbles.onPrimary)` |
| `PebblesPrimaryButtonStyle.swift:21` | enabled label / disabled label | `isEnabled ? Color.pebbles.onPrimary : Color.pebbles.onSurface.opacity(0.38)` |
| `PebblesPrimaryButtonStyle.swift:31` disabled fill | fill | `isEnabled ? Color.clear : Color.pebbles.surfaceContainerHighest` |
| `PebblesCheckbox.swift:38` `Color.white` (unchecked fill) | stock | `isChecked ? Color.pebbles.primary : Color.pebbles.stock` |
| `PebblesCheckbox.swift:40` unchecked stroke | control boundary | `isChecked ? Color.pebbles.primary : Color.pebbles.outline` |
| `PebblesCheckbox.swift:44` checkmark / unchecked ink | ink on the primary fill / ink-2 | `isChecked ? Color.pebbles.onPrimary : Color.pebbles.onSurfaceVariant` |
| `PebblesCheckbox.swift:65,77` (previews) | ground | `Color.pebbles.surface` |
| `PebblesTextInput.swift:42` `Color.white` | text-field ground (stock) | `Color.pebbles.stock` |
| `PebblesTextInput.swift:46` stroke | control boundary | `Color.pebbles.outline` |
| `PebblesTextInput.swift:64,71,78` (previews) | ground | `Color.pebbles.surface` |

- [ ] **Step 4: Repoint `verify-color-contrast.mjs`**

In `apps/ios/Scripts/verify-color-contrast.mjs`:
- Change the colorset path to `"Pebbles/Resources/Assets.xcassets/M3/googleCapsuleInk.colorset/Contents.json"`.
- Replace every `SystemOnLight` in messages and the header comment with `googleCapsuleInk`.
- Replace `(see SystemPalette.onLight)` with `(see GoogleCapsuleInk in ColorSchemes.kt)`.

In `.github/workflows/ios.yml`, change the comment `# SystemOnLight stays a single-appearance colorset…` to `# M3/googleCapsuleInk stays a single-appearance colorset…`.

```bash
npm run verify:contrast --workspace=@pbbls/ios   # expect: googleCapsuleInk on white = 11.2…:1 (AA ok)
```

- [ ] **Step 5: Tests, then commit**

```bash
grep -rnE '(Color)?\.(system|accent)\.' apps/ios/Pebbles/Components   # expect nothing
cd apps/ios && rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*
xcodegen generate && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' -only-testing:PebblesTests/GoogleSignInButtonContrastTests -only-testing:PebblesTests/ThemeLiteralsTests -quiet
cd /Users/alexis/code/pbbls
git add apps/ios/Pebbles/Components apps/ios/PebblesTests apps/ios/Scripts/verify-color-contrast.mjs .github/workflows/ios.yml
git commit -m "feat(ios): shared components speak m3 roles; contrast check reads googlecapsuleink"
```

### Task 26: `Features/Shared/`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Shared/SoulItem.swift:88-95`
- Modify: `apps/ios/Pebbles/Features/Shared/Ripples/RippleStrokeColor.swift:25-27`
- Modify: `apps/ios/Pebbles/Features/Shared/Ripples/RippleBadge.swift`

- [ ] **Step 1: Mechanical pass** on the three files, using the perl block from the A3 header (run from `apps/ios/Pebbles/Features/Shared`).

- [ ] **Step 2: By-use pass**

| Site | → |
|---|---|
| `SoulItem.swift:94` `colorScheme == .dark ? Color.accent.shaded : Color.accent.secondary` | `Color.pebbles.secondaryContainer`. Delete `@Environment(\.colorScheme)` if nothing else reads it, and update the comment above to "Count icon uses the secondaryContainer role." |
| `SoulItem.swift:89` `nameColor` | `.selected ? Color.pebbles.primary : Color.pebbles.onSurfaceVariant` (mechanical) |
| `RippleStrokeColor.swift:25` `.default: return .system.muted` | `Color.pebbles.outline` (a de-emphasised mark, ink-3) |
| `RippleStrokeColor.swift:26,27` | `Color.pebbles.primary`, `Color.pebbles.onSurfaceVariant` (mechanical) |
| `RippleBadge.swift:82` (preview) | `Color.pebbles.surface` |

- [ ] **Step 3: Tests and commit**

Run `-only-testing:PebblesTests/RippleStrokeColorTests`. Expected: PASS. The test compares tones, not colours.

```bash
git add apps/ios/Pebbles/Features/Shared
git commit -m "feat(ios): shared feature views speak m3 roles"
```

### Task 27: `Welcome/`, `Auth/`, `Onboarding/`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Welcome/HandcraftedLogoView.swift`, `WelcomeCarousel.swift`, `WelcomeSlideView.swift`, `WelcomeView.swift` (in `apps/ios/Pebbles/Features/Welcome/`)
- Modify: `apps/ios/Pebbles/Features/Onboarding/OnboardingPageView.swift`
- `Features/Auth/` has no old token left after A2 (verify with grep).
- Modify: `apps/ios/PebblesTests/ThemeLiteralsTests.swift` (`debt`)

- [ ] **Step 1: Lower the debt (red):** delete `"Features/Welcome/WelcomeView.swift": 1`. Run the ratchet. Expected: FAIL on `WelcomeView`.

- [ ] **Step 2: Mechanical pass** on the five files.

- [ ] **Step 3: By-use pass**

| Site | → |
|---|---|
| `WelcomeView.swift:54` `Color.system.background.ignoresSafeArea()` | `Color.pebbles.surface.ignoresSafeArea()` |
| `WelcomeView.swift:124` `.foregroundStyle(.white)` on `.borderedProminent` | `Color.pebbles.onPrimary` |
| `WelcomeCarousel.swift:50` inactive page dot | `idx == currentIndex ? Color.pebbles.primary : Color.pebbles.outlineVariant` (same rule as the page control) |
| `HandcraftedLogoView.swift:246` `Color.white` mask stroke | allowlisted, unchanged |
| `HandcraftedLogoView.swift:259,266,273` (previews) | `Color.pebbles.surface` |
| `OnboardingPageView.swift:15,49` `accent.surface` wash → `primaryContainer` (mechanical) | any text or icon drawn on it takes `onPrimaryContainer` |

- [ ] **Step 4: Ratchet, then commit**

```bash
grep -rnE '(Color)?\.(system|accent)\.' apps/ios/Pebbles/Features/{Welcome,Auth,Onboarding}   # expect nothing
git add apps/ios/Pebbles/Features/Welcome apps/ios/Pebbles/Features/Onboarding apps/ios/PebblesTests/ThemeLiteralsTests.swift
git commit -m "feat(ios): welcome, auth and onboarding speak m3 roles"
```

### Task 28: `Record/`, `Capture/`, `Complete/`, `PebbleMedia/`

`PebbleMedia/` is not named in spec §10. It holds no old token, only literals: photo placeholders and the upload check. It is migrated here as part of the capture flows it serves, so that A3b's debt only names its own folders. Say so in the PR body.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Record/RecordFlowChrome.swift`, `RecordStepScaffold.swift`, `Steps/RecordNameStep.swift`, `Steps/RecordPhotoStep.swift`, `Steps/RecordWhenStep.swift` (under `apps/ios/Pebbles/Features/Record/`)
- Modify: `apps/ios/Pebbles/Features/Capture/CaptureFlowView.swift`
- Modify: `apps/ios/Pebbles/Features/Complete/CompleteFlowView.swift`, `Steps/CompleteCollectionStep.swift`, `Steps/CompletePrivacyStep.swift`, `Steps/CompleteSuccessStep.swift` (under `apps/ios/Pebbles/Features/Complete/`)
- Modify: `apps/ios/Pebbles/Features/PebbleMedia/AttachedPhotoView.swift:36,53`, `ExistingSnapRow.swift:33,67`, `SnapImageView.swift:56`
- Modify: `apps/ios/PebblesTests/ThemeLiteralsTests.swift` (`debt`)

- [ ] **Step 1: Lower the debt (red):** delete `Features/Record/RecordStepScaffold.swift`, `Features/PebbleMedia/AttachedPhotoView.swift`, `Features/PebbleMedia/ExistingSnapRow.swift` and `Features/PebbleMedia/SnapImageView.swift`. Run the ratchet. Expected: FAIL on the four files.

- [ ] **Step 2: Mechanical pass** on the ten Record/Capture/Complete files.

- [ ] **Step 3: By-use pass**

| Site | → |
|---|---|
| `CaptureFlowView.swift:85`, `CompleteFlowView.swift:44` `.background(Color.system.background)` | `Color.pebbles.surface` |
| `RecordFlowChrome.swift:63` inactive step dot | `Color.pebbles.outlineVariant` (page-dot rule) |
| `RecordStepScaffold.swift:123` `.shadow(color: .black.opacity(0.22)…)` | `Color.pebbles.scrim.opacity(0.22)` |
| `RecordPhotoStep.swift:73` `accent.surface` (mechanical → `primaryContainer`) | content on it takes `onPrimaryContainer` |
| `CompleteCollectionStep.swift:46`, `CompletePrivacyStep.swift:52` unselected chip `Color.system.muted` | `Color.pebbles.surfaceContainerHighest`. The selected wash stays `Color.pebbles.primary.opacity(0.12)` |
| `AttachedPhotoView.swift:36`, `ExistingSnapRow.swift:67` `Color.secondary.opacity(0.2)` | `Color.pebbles.surfaceContainerHighest` |
| `SnapImageView.swift:56` `Color.secondary.opacity(0.1)` | `Color.pebbles.surfaceContainerHighest` |
| `AttachedPhotoView.swift:53`, `ExistingSnapRow.swift:33` `.foregroundStyle(.green)` | `Color.pebbles.tertiary` (Android: `PebblesSuccess` → `tertiary`) |

- [ ] **Step 4: Ratchet, then commit**

```bash
grep -rnE '(Color)?\.(system|accent)\.' apps/ios/Pebbles/Features/{Record,Capture,Complete,PebbleMedia}   # expect nothing
git add apps/ios/Pebbles/Features/{Record,Capture,Complete,PebbleMedia} apps/ios/PebblesTests/ThemeLiteralsTests.swift
git commit -m "feat(ios): record, capture, complete and media views speak m3 roles"
```

### Task 29: `Karma/`, `Connections/`, `Lab/`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Karma/AchievementMomentView.swift:15,65,81`, `KarmaEarnedCapsule.swift:77,79,82,143`
- Modify: `apps/ios/Pebbles/Features/Connections/ConnectionsListView.swift`, `InviteAcceptSheet.swift`, `InviteSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Lab/Components/AnnouncementRow.swift:23,25,27`, `FeaturedCommunityCard.swift:30`, `LogTimeline.swift:81,88`, `ReactionButton.swift`, `LabView.swift:106`, `Views/AnnouncementDetailView.swift:25,27`
- Modify: `apps/ios/PebblesTests/ThemeLiteralsTests.swift` (`debt`)

- [ ] **Step 1: Lower the debt (red):** delete `Features/Karma/AchievementMomentView.swift`, `Features/Karma/KarmaEarnedCapsule.swift` and `Features/Lab/Components/FeaturedCommunityCard.swift`. After this, `debt` holds only A3b paths (Step 5 checks). Run the ratchet. Expected: FAIL on the three files.

- [ ] **Step 2: Mechanical pass** on the eleven files.

- [ ] **Step 3: By-use pass**

| Site | → |
|---|---|
| `AchievementMomentView.swift:15` `Color.black.opacity(0.55)` | `Color.pebbles.scrim.opacity(0.55)` |
| `AchievementMomentView.swift:65` muted text | `Color.pebbles.outline` |
| `AchievementMomentView.swift:81` card fill | `Color.pebbles.stock` |
| `KarmaEarnedCapsule.swift:77` capsule fill | `Color.pebbles.stock` |
| `KarmaEarnedCapsule.swift:79` `.white.opacity(0.22)` glass rim | `Color.pebbles.rule` |
| `KarmaEarnedCapsule.swift:82` `.black.opacity(0.16)` shadow | `Color.pebbles.scrim.opacity(0.16)` |
| `KarmaEarnedCapsule.swift:143` (preview) `[.indigo, .purple, .orange]` | `[Color.pebbles.primary, Color.pebbles.tertiary, Color.pebbles.sand]` |
| `InviteSheet.swift:117` QR `Color.white` | allowlisted, unchanged |
| `FeaturedCommunityCard.swift:30` `.white` on `.borderedProminent` | `Color.pebbles.onPrimary` |
| `AnnouncementRow.swift:23,25,27`, `AnnouncementDetailView.swift:25,27` `Color.system.muted.opacity(0.3)` placeholder | `Color.pebbles.surfaceContainerHighest` (drop the opacity) |
| `LogTimeline.swift:81,88` timeline line | `Color.pebbles.rule` |
| `LabView.swift:106` `.listRowSeparatorTint` | `Color.pebbles.rule` |

- [ ] **Step 4: Ratchet, then commit**

```bash
grep -rnE '(Color)?\.(system|accent)\.' apps/ios/Pebbles/Features/{Karma,Connections,Lab}   # expect nothing
git add apps/ios/Pebbles/Features/{Karma,Connections,Lab} apps/ios/PebblesTests/ThemeLiteralsTests.swift
git commit -m "feat(ios): karma, connections and lab speak m3 roles"
```

- [ ] **Step 5: Check the remaining debt** in `ThemeLiteralsTests.debt`. It must be exactly:

```swift
        "Features/Glyph/Views/GlyphCanvasView.swift": 1,
        "Features/Glyph/Views/GlyphPickerContent.swift": 1,
        "Features/Glyph/Views/GlyphTabBar.swift": 2,
        "Features/Glyph/Views/SlideToConfirm.swift": 1,
        "Features/Path/Components/PathPebbleRow.swift": 2,
        "Features/Path/Components/WeekPathView.swift": 1,
        "Features/Path/Valence/ValenceStoneStyle.swift": 1
```

### Task 30: Verify A3a, simulator pass, open the PR

- [ ] **Step 1: Gates**

```bash
npm run lint --workspace=@pbbls/ios
npm run verify:contrast --workspace=@pbbls/ios
rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*
npm run test --workspace=@pbbls/ios
grep -rnE '(Color)?\.(system|accent)\.' apps/ios/Pebbles/Theme/{PebblesScreen,PebblesList,SurfaceTile,PebblesToolbarTitle,PebbleToolbarButton,ProfileCard}.swift \
  apps/ios/Pebbles/PebblesApp.swift apps/ios/Pebbles/RootView.swift apps/ios/Pebbles/Components \
  apps/ios/Pebbles/Features/{Shared,Welcome,Auth,Onboarding,Record,Capture,Complete,PebbleMedia,Karma,Connections,Lab}   # expect nothing
```

- [ ] **Step 2: Simulator pass (spec §9)**

Walk every folder touched: Welcome, sign-in, onboarding, record flow, capture, complete, karma moment, connections, invites, Lab, and the segmented auth switcher. Do it in light, dark, and Increase Contrast (Settings → Accessibility → Display & Text Size). Compare the neutrals with the hosted Spine-on-Material mock (memory: reference_spine_on_material_mock). Fix any unreadable pair by moving it to its on-role.

- [ ] **Step 3: Push and open the PR**

```bash
git fetch origin && git log --oneline origin/main..main   # expect nothing
gh stack push
gh stack submit --auto
gh pr edit feat/921-ios-m3-roles-shell \
  --title "feat(ios): shell, shared components and capture flows speak m3 roles" \
  --add-label feat --add-label ui --add-label ios --add-label no-lab-note \
  --milestone "M64 · New Path" --body-file <temp file>
```

Body: open with `Part of #921`. Then the folders migrated, the `PebbleMedia` note from Task 28, the `verify:contrast` repoint, and the simulator-pass screenshots. End with the session attribution.

---

## Part A3b — `feat/921-ios-m3-roles-path`

PR title: `feat(ios): path, profile and glyphs speak m3 roles; the old palette goes`. Body starts `Part of #921`.

The mechanical pass and by-use rules are the same as in the A3a header. The perl block is repeated in each task below.

### Task 31: Add the branch to the stack

- [ ] **Step 1**

```bash
git checkout feat/921-ios-m3-roles-shell
gh stack add feat/921-ios-m3-roles-path
git log --oneline origin/main -- apps/ios/Pebbles/Features/Path/Valence/ValenceStoneStyle.swift | head -3   # did stack B3 already delete the dead members?
```

### Task 32: `StonePalette.brand` keeps the old ramp as stone data (D5)

**Files:**
- Create: `apps/ios/PebblesTests/Stone/StonePaletteBrandTests.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Stone/StonePalette.swift:20-30`

- [ ] **Step 1: Write the characterisation test (it must pass before and after)**

```swift
import SwiftUI
import Testing
@testable import Pebbles

/// D5: the brand stone keeps the #456 accent ramp verbatim, in both appearances,
/// because the lit-stone shader is tuned to it and a stone never follows the theme.
@Suite("StonePalette.brand")
struct StonePaletteBrandTests {

    private func hex(_ rgb: SIMD3<Float>) -> String {
        String(format: "#%02X%02X%02X", Int((rgb.x * 255).rounded()), Int((rgb.y * 255).rounded()), Int((rgb.z * 255).rounded()))
    }

    @Test("every slot keeps the old accent value in light and dark", arguments: [
        (StonePalette.Slot.dark, "#341B1B"),
        (StonePalette.Slot.shaded, "#8C4949"),
        (StonePalette.Slot.primary, "#C07A7A"),
        (StonePalette.Slot.secondary, "#EAD3D3"),
        (StonePalette.Slot.light, "#FAF4F4"),
        (StonePalette.Slot.surface, "#C07A7A")
    ])
    func ramp(slot: StonePalette.Slot, expected: String) {
        #expect(hex(StonePalette.brand.rgb(slot, scheme: .light)) == expected)
        #expect(hex(StonePalette.brand.rgb(slot, scheme: .dark)) == expected)
    }

    @Test("surface is the primary at 10% alpha")
    func surfaceAlpha() {
        let resolved = StonePalette.brand.surface.resolve(in: EnvironmentValues())
        #expect(abs(resolved.opacity - 0.1) < 0.005)
    }
}
```

Run `-only-testing:PebblesTests/StonePaletteBrandTests`. Expected: PASS against today's asset-backed `brand`. That is the baseline.

- [ ] **Step 2: Replace `brand` with literals**

```swift
    /// The brand stone (D5, spec 2026-10-20-ios-m3-colour): the #456 accent ramp
    /// kept verbatim as stone data, not scheme roles. The lit-stone shader (#975)
    /// is tuned to a body ramp, and a stone never changes with the theme. `dark`
    /// (#341B1B) is the darkest tone and `shaded` (#8C4949) the mid one, the
    /// `emotion_categories` convention.
    static let brand = StonePalette(
        primary: Color(.sRGB, red: 192 / 255, green: 122 / 255, blue: 122 / 255),
        secondary: Color(.sRGB, red: 234 / 255, green: 211 / 255, blue: 211 / 255),
        light: Color(.sRGB, red: 250 / 255, green: 244 / 255, blue: 244 / 255),
        surface: Color(.sRGB, red: 192 / 255, green: 122 / 255, blue: 122 / 255, opacity: 0.1),
        dark: Color(.sRGB, red: 52 / 255, green: 27 / 255, blue: 27 / 255),
        shaded: Color(.sRGB, red: 140 / 255, green: 73 / 255, blue: 73 / 255)
    )
```

Also change the type's doc comment "The picker builds one from the brand accent" to "The picker uses `brand`".

- [ ] **Step 3: Run it again**

Run the same test, after `rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*`. Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/PebblesTests/Stone/StonePaletteBrandTests.swift apps/ios/Pebbles/Features/Path/Stone/StonePalette.swift
git commit -m "feat(ios): the brand stone keeps its ramp as stone data"
```

### Task 33: Rive's bound colour follows High Contrast (D6)

**Files:**
- Create: `apps/ios/PebblesTests/ColorRiveResolutionTests.swift`
- Modify: `apps/ios/Pebbles/Theme/Color+Rive.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Components/WeekRollCairnCell.swift:20,43,50,61-63,99`

- [ ] **Step 1: Write the failing test**

```swift
import SwiftUI
import Testing
import UIKit
@testable import Pebbles

@Suite("Color+Rive")
struct ColorRiveResolutionTests {

    private func hex(_ color: UIColor) -> String {
        var red: CGFloat = 0, green: CGFloat = 0, blue: CGFloat = 0, alpha: CGFloat = 0
        color.getRed(&red, green: &green, blue: &blue, alpha: &alpha)
        return String(format: "#%02X%02X%02X", Int((red * 255).rounded()), Int((green * 255).rounded()), Int((blue * 255).rounded()))
    }

    @Test("the bound colour resolves against appearance and Increase Contrast", arguments: [
        (ColorScheme.light, ColorSchemeContrast.standard, "#8E4955"),
        (ColorScheme.dark, ColorSchemeContrast.standard, "#FFB2BC"),
        (ColorScheme.light, ColorSchemeContrast.increased, "#501824"),
        (ColorScheme.dark, ColorSchemeContrast.increased, "#FFEBED")
    ])
    func resolves(scheme: ColorScheme, contrast: ColorSchemeContrast, expected: String) {
        #expect(hex(Color.pebbles.primary.resolvedForRive(in: scheme, contrast: contrast)) == expected)
    }
}
```

Run `-only-testing:PebblesTests/ColorRiveResolutionTests`. Expected: FAIL to compile (`value of type 'Color' has no member 'resolvedForRive'`).

- [ ] **Step 2: Implement in `Color+Rive.swift`**

Replace the function with:

```swift
    /// Resolves this SwiftUI `Color` against the appearance *and* Increase
    /// Contrast, then applies its RGBA to a Rive Data Binding colour property.
    /// Read `colorScheme` and `colorSchemeContrast` from the environment in the
    /// calling view, and re-apply when either changes, so a bound role follows
    /// High Contrast like every other role (D6, spec 2026-10-20-ios-m3-colour).
    /// Colours baked into the .riv artwork are not touched.
    ///
    /// **SDK gotcha (RiveRuntime 6.19.2):** the header for
    /// `RiveDataBindingViewModelInstance.ColorProperty.set(red:green:blue:alpha:)`
    /// documents the channel range as 0–255, but the implementation in
    /// `RiveDataBindingViewModelInstanceProperty.mm` clamps each channel
    /// with `fmax(0, fmin(value, 1.0))` and then multiplies by 255
    /// internally. Passing 0–255 makes every channel saturate to 1.0 → 255
    /// (white). We must pass the 0–1 normalized values returned by
    /// `UIColor.getRed(_:green:blue:alpha:)` directly.
    func applyToRiveColorProperty(
        _ property: RiveDataBindingViewModel.Instance.ColorProperty,
        in colorScheme: ColorScheme,
        contrast: ColorSchemeContrast
    ) {
        let uiColor = resolvedForRive(in: colorScheme, contrast: contrast)
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        uiColor.getRed(&r, green: &g, blue: &b, alpha: &a)
        // The SDK clamps these to 0–1 — pass UIColor's native 0–1 range
        // (NOT 0–255 as the SDK header claims).
        property.set(red: r, green: g, blue: b, alpha: a)
    }

    /// The UIColor Rive receives, resolved through UIKit so asset-catalog
    /// colours honour both traits.
    func resolvedForRive(in colorScheme: ColorScheme, contrast: ColorSchemeContrast) -> UIColor {
        let traits = UITraitCollection { traits in
            traits.userInterfaceStyle = colorScheme == .dark ? .dark : .light
            traits.accessibilityContrast = contrast == .increased ? .high : .normal
        }
        return UIColor(self).resolvedColor(with: traits)
    }
```

- [ ] **Step 3: Update `WeekRollCairnCell`**

- After line 20, add `@Environment(\.colorSchemeContrast) private var colorSchemeContrast`.
- After `.onChange(of: colorScheme) { _, _ in applyViewModel() }`, add `.onChange(of: colorSchemeContrast) { _, _ in applyViewModel() }`.
- Line 99 becomes `strokeColor.applyToRiveColorProperty(prop, in: colorScheme, contrast: colorSchemeContrast)`.
- `strokeColor` becomes `isFocused ? SwiftUI.Color.pebbles.primary : SwiftUI.Color.pebbles.onSurfaceVariant`. Update its comment: "focused → primary, unfocused → onSurfaceVariant (spec §4, D6)".
- Line 43 becomes `.foregroundStyle(isFocused ? Color.pebbles.primary : Color.pebbles.onSurfaceVariant)`.
- Change the doc comment on `applyViewModel()` to "Pushes the current `isFocused` / `colorScheme` / `colorSchemeContrast` state into Rive."

- [ ] **Step 4: Run, then commit**

Run `-only-testing:PebblesTests/ColorRiveResolutionTests`. Expected: PASS (4 cases).

```bash
git add apps/ios/PebblesTests/ColorRiveResolutionTests.swift apps/ios/Pebbles/Theme/Color+Rive.swift apps/ios/Pebbles/Features/Path/Components/WeekRollCairnCell.swift
git commit -m "feat(ios): rive's bound stroke follows the m3 role and high contrast"
```

### Task 34: `ValenceStoneStyle` keeps only `headlineInk`, on roles

The m3-colour spec §4 lists the dead members, and §10 says "`inkHexes` is B3's". The later foundations spec §B3.1 settles it: every member except `headlineInk` is dead, `inkHexes` included, and whichever of A3b or B3 lands first deletes them all. This task follows §B3.1. If Task 31's `git log` shows B3 already did the deletion, only Step 2's three role reads remain to do.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Valence/ValenceStoneStyle.swift` (whole file)
- Modify: `apps/ios/PebblesTests/ThemeLiteralsTests.swift` (`allowlist`, `debt`)

- [ ] **Step 1: Confirm the only caller**

Run: `grep -rn "ValenceStoneStyle" apps/ios --include='*.swift' | grep -v 'Valence/ValenceStoneStyle.swift'`
Expected: one line, `ValenceHeadlineView.swift:19: … ValenceStoneStyle.headlineInk(for: valence.polarity)`.

- [ ] **Step 2: Lower the ratchet (red), then rewrite the file**

In `ThemeLiteralsTests`, delete the `allowlist` entry and the `debt` entry for `Features/Path/Valence/ValenceStoneStyle.swift`. Run the ratchet. Expected: FAIL ("new literal", 2 lines: `Color.white` and `Color(hex: "`).

Replace the whole file with:

```swift
import SwiftUI

/// Ink for the headline word naming the picked valence. The stones are lit
/// brand stone since #974, so the word takes a role near the tone its stone
/// wears: lowlight `onSurface` (at headline size the near-black stone tone
/// reads as disabled), neutral `onPrimaryContainer` (the mid tone), highlight
/// `primary`. The washes, meshes and selection styles that lived here had no
/// caller since #975 and were deleted (spec 2026-10-20-ios-new-path-foundations
/// §B3.1).
struct ValenceStoneStyle {
    static func headlineInk(for polarity: ValencePolarity) -> AnyShapeStyle {
        switch polarity {
        case .lowlight:  return AnyShapeStyle(Color.pebbles.onSurface)
        case .neutral:   return AnyShapeStyle(Color.pebbles.onPrimaryContainer)
        case .highlight: return AnyShapeStyle(Color.pebbles.primary)
        }
    }
}
```

- [ ] **Step 3: Ratchet, build, commit**

Run the ratchet. Expected: PASS. Build to confirm nothing else referenced the deleted members.

```bash
git add apps/ios/Pebbles/Features/Path/Valence/ValenceStoneStyle.swift apps/ios/PebblesTests/ThemeLiteralsTests.swift
git commit -m "feat(ios): valence headline ink speaks m3 roles; dead stone styles go"
```

### Task 35: The rest of `Features/Path/`

**Files** (all under `apps/ios/Pebbles/Features/Path/`):
- `Components/`: `NewPebbleButton.swift`, `PathBottomBar.swift`, `PathPebbleRow.swift`, `WeekHeaderView.swift`, `WeekPathView.swift`
- `DomainPickerContent.swift`, `EditMomentSheet.swift`, `EmotionPickerContent.swift`, `MomentNameText.swift`, `PathView.swift`, `PebbleFormView.swift`, `SoulPickerContent.swift`, `SoulPill.swift`, `VisibilityChip.swift`
- `Read/`: `PebbleMetaPill.swift`, `PebblePillFlow.swift`, `PebblePrivacyBadge.swift`, `PebbleReadBanner.swift`, `PebbleReadTitle.swift`, `PebbleReadView.swift`, `PebbleSnapFrame.swift`
- `Render/PebbleAnimatedRenderView.swift`, `Stone/StoneView.swift`, `Valence/ValenceHeadlineView.swift`, `Valence/ValenceRollView.swift`
- `Models/EmotionPalette.swift:16-17` (doc comment only)
- `apps/ios/Pebbles/Services/EmotionPaletteService.swift:12` (doc comment only)
- `apps/ios/PebblesTests/ThemeLiteralsTests.swift` (`debt`)

- [ ] **Step 1: Lower the debt (red):** delete `Features/Path/Components/PathPebbleRow.swift` and `Features/Path/Components/WeekPathView.swift` from `debt`. `WeekPathView`'s 2 allowlisted mask stops stay. Run the ratchet. Expected: FAIL on both files.

- [ ] **Step 2: Mechanical pass**

```bash
cd /Users/alexis/code/pbbls/apps/ios/Pebbles/Features/Path
perl -pi -e '
  s/(?<!\w)(?:Color)?\.system\.foreground\b/Color.pebbles.onSurface/g;
  s/(?<!\w)(?:Color)?\.system\.secondary\b/Color.pebbles.onSurfaceVariant/g;
  s/(?<!\w)(?:Color)?\.system\.onLight\b/Color.pebbles.googleCapsuleInk/g;
  s/(?<!\w)(?:Color)?\.accent\.primary\b/Color.pebbles.primary/g;
  s/(?<!\w)(?:Color)?\.accent\.surface\b/Color.pebbles.primaryContainer/g;
  s/(?<!\w)(?:Color)?\.accent\.secondary\b/Color.pebbles.secondaryContainer/g;
' Components/NewPebbleButton.swift Components/PathBottomBar.swift Components/PathPebbleRow.swift \
  Components/WeekHeaderView.swift Components/WeekPathView.swift DomainPickerContent.swift EditMomentSheet.swift \
  EmotionPickerContent.swift MomentNameText.swift PathView.swift PebbleFormView.swift SoulPickerContent.swift \
  SoulPill.swift VisibilityChip.swift Read/PebbleMetaPill.swift Read/PebblePillFlow.swift \
  Read/PebblePrivacyBadge.swift Read/PebbleReadBanner.swift Read/PebbleReadTitle.swift Read/PebbleReadView.swift \
  Read/PebbleSnapFrame.swift Render/PebbleAnimatedRenderView.swift Stone/StoneView.swift \
  Valence/ValenceHeadlineView.swift Valence/ValenceRollView.swift
cd /Users/alexis/code/pbbls
```

- [ ] **Step 3: By-use pass**

| Site | → |
|---|---|
| `PathBottomBar.swift:17` `colorScheme == .dark ? … : …` | `Color.pebbles.primary`. Drop `@Environment(\.colorScheme)` if nothing else reads it. Stack C deletes this file later, and whichever lands second rebases. |
| `NewPebbleButton.swift:16` `fill` | `Color.pebbles.surface`. Drop `@Environment(\.colorScheme)` if unused (same Stack C note). |
| `NewPebbleButton.swift:26` `.fill(Color.system.muted)` | `Color.pebbles.surfaceContainerHighest` |
| `WeekHeaderView.swift:37` `strokeColor` (rounded-rect outline) | `Color.pebbles.rule` |
| `WeekPathView.swift:90` `.foregroundStyle(.white)` on the `primary` "plus" fill | `Color.pebbles.onPrimary` |
| `WeekPathView.swift:55,56` mask stops | allowlisted, unchanged |
| `PathPebbleRow.swift:107` `Color.white` photo border | `Color.pebbles.stock` |
| `PathPebbleRow.swift:109` `Color.black.opacity(0.18)` shadow | `Color.pebbles.scrim.opacity(0.18)` |
| `PathPebbleRow.swift:168` `palette.light` / `palette.primary` | emotion palette, unchanged |
| `PathView.swift:119` `Color.system.background` | `Color.pebbles.surface` |
| `PebbleReadView.swift:74` `pageColors?.background ?? Color.system.background` | `pageColors?.background ?? Color.pebbles.surface` |
| `PebblePrivacyBadge.swift:44` capsule outline | `Color.pebbles.rule` |
| `PebbleMetaPill.swift` `accent.secondary` (mechanical → `secondaryContainer`) | its text/icon takes `onSecondaryContainer` |
| `PebbleSnapFrame.swift:76` `UIColor(red:…)` preview fixture | allowlisted, unchanged |
| Previews: `DomainPickerContent:87`, `PebblePillFlow:103`, `PebblePrivacyBadge:70`, `PebbleReadBanner:88,101,117`, `PebbleReadTitle:60`, `PebbleSnapFrame:99,111,123`, `PebbleMetaPill:164`, `PebbleAnimatedRenderView:250`, `StoneView:196` | `Color.pebbles.surface` |
| `EmotionPalette.swift:16-17` doc | "fall back to `Color.pebbles.primary`" (drop `primaryHex`) |
| `EmotionPaletteService.swift:12` doc | same wording |

- [ ] **Step 4: Tests, then commit**

Run `-only-testing:PebblesTests/ThemeLiteralsTests -only-testing:PebblesTests/PathPebbleRowNameColorTests -only-testing:PebblesTests/PebblePageColorsTests`. Expected: PASS.

```bash
grep -rnE '(Color)?\.(system|accent)\.' apps/ios/Pebbles/Features/Path apps/ios/Pebbles/Services   # expect nothing
git add apps/ios/Pebbles/Features/Path apps/ios/Pebbles/Services/EmotionPaletteService.swift apps/ios/PebblesTests/ThemeLiteralsTests.swift
git commit -m "feat(ios): path views speak m3 roles"
```

### Task 36: `Features/Profile/`

**Files** (under `apps/ios/Pebbles/Features/Profile/`): `AchievementsView.swift`, `Components/AssiduityGrid.swift`, `Components/CollectionGlyphSection.swift`, `Components/DataTile.swift`, `Components/ProfileAchievementsCard.swift`, `Components/ProfileCollectionCard.swift`, `Components/ProfileCollectionsCard.swift`, `Components/ProfileLabCard.swift`, `Components/ProfileLogoutButton.swift`, `Components/ProfileStatsCard.swift`, `Components/RipplesRow.swift`, `Sheets/SettingsSheet.swift`

- [ ] **Step 1: Mechanical pass**

```bash
cd /Users/alexis/code/pbbls/apps/ios/Pebbles/Features/Profile
perl -pi -e '
  s/(?<!\w)(?:Color)?\.system\.foreground\b/Color.pebbles.onSurface/g;
  s/(?<!\w)(?:Color)?\.system\.secondary\b/Color.pebbles.onSurfaceVariant/g;
  s/(?<!\w)(?:Color)?\.system\.onLight\b/Color.pebbles.googleCapsuleInk/g;
  s/(?<!\w)(?:Color)?\.accent\.primary\b/Color.pebbles.primary/g;
  s/(?<!\w)(?:Color)?\.accent\.surface\b/Color.pebbles.primaryContainer/g;
  s/(?<!\w)(?:Color)?\.accent\.secondary\b/Color.pebbles.secondaryContainer/g;
' AchievementsView.swift Components/AssiduityGrid.swift Components/CollectionGlyphSection.swift \
  Components/DataTile.swift Components/ProfileAchievementsCard.swift Components/ProfileCollectionCard.swift \
  Components/ProfileCollectionsCard.swift Components/ProfileLabCard.swift Components/ProfileLogoutButton.swift \
  Components/ProfileStatsCard.swift Components/RipplesRow.swift Sheets/SettingsSheet.swift
cd /Users/alexis/code/pbbls
```

- [ ] **Step 2: By-use pass**

| Site | → |
|---|---|
| `AchievementsView.swift:133` locked icon | `isUnlocked ? Color.pebbles.primary : Color.pebbles.outline` |
| `AchievementsView.swift:138,161` muted text | `Color.pebbles.outline` |
| `ProfileStatsCard.swift:18` `Divider().overlay` | `Color.pebbles.rule` |
| `ProfileAchievementsCard.swift:53`, `ProfileCollectionsCard.swift:69`, `ProfileLabCard.swift:23` muted icon/text | `Color.pebbles.outline` |
| `ProfileCollectionCard.swift:85` card outline | `Color.pebbles.rule` |
| `ProfileCollectionCard.swift:89` dashed empty placeholder | `Color.pebbles.outline` |
| `ProfileCollectionCard.swift:45,48` wash (mechanical → `primaryContainer`) + its icon | icon → `Color.pebbles.onPrimaryContainer` |
| `ProfileLogoutButton.swift:10,15` label on the wash | label → `Color.pebbles.onPrimaryContainer` |
| `AssiduityGrid.swift:37` inactive cell | `active ? Color.pebbles.primary : Color.pebbles.surfaceContainerHighest` |
| `AssiduityGrid.swift:52` (preview) | `Color.pebbles.surface` |

- [ ] **Step 3: Commit**

```bash
grep -rnE '(Color)?\.(system|accent)\.' apps/ios/Pebbles/Features/Profile   # expect nothing
git add apps/ios/Pebbles/Features/Profile
git commit -m "feat(ios): profile views speak m3 roles"
```

### Task 37: `Features/Glyph/`

**Files** (under `apps/ios/Pebbles/Features/Glyph/Views/`): `GlyphBanner.swift`, `GlyphCanvasView.swift:69`, `GlyphCarveSheet.swift`, `GlyphDetailDrawer.swift`, `GlyphPickerContent.swift:144,175`, `GlyphTabBar.swift:32,37,72,74,77`, `GlyphView.swift:52,70,74`, `GlyphsListView.swift:138`, `SlideToConfirm.swift:38-42,78`
- Modify: `apps/ios/PebblesTests/ThemeLiteralsTests.swift` (`debt`)

- [ ] **Step 1: Lower the debt (red):** delete the four `Features/Glyph/...` entries. `debt` is now empty: leave it as `[:]`. Run the ratchet. Expected: FAIL on the four Glyph files.

- [ ] **Step 2: Mechanical pass**

```bash
cd /Users/alexis/code/pbbls/apps/ios/Pebbles/Features/Glyph/Views
perl -pi -e '
  s/(?<!\w)(?:Color)?\.system\.foreground\b/Color.pebbles.onSurface/g;
  s/(?<!\w)(?:Color)?\.system\.secondary\b/Color.pebbles.onSurfaceVariant/g;
  s/(?<!\w)(?:Color)?\.system\.onLight\b/Color.pebbles.googleCapsuleInk/g;
  s/(?<!\w)(?:Color)?\.accent\.primary\b/Color.pebbles.primary/g;
  s/(?<!\w)(?:Color)?\.accent\.surface\b/Color.pebbles.primaryContainer/g;
  s/(?<!\w)(?:Color)?\.accent\.secondary\b/Color.pebbles.secondaryContainer/g;
' GlyphBanner.swift GlyphCanvasView.swift GlyphCarveSheet.swift GlyphDetailDrawer.swift GlyphPickerContent.swift \
  GlyphTabBar.swift GlyphView.swift GlyphsListView.swift SlideToConfirm.swift
cd /Users/alexis/code/pbbls
```

- [ ] **Step 3: By-use pass**

| Site | → |
|---|---|
| `GlyphCanvasView.swift:69` `Color.white` canvas | `Color.pebbles.stock` |
| `GlyphPickerContent.swift:144` `Color.secondary.opacity(0.06)` tile ground | `Color.pebbles.surfaceContainerHighest` |
| `GlyphPickerContent.swift:175` muted | `Color.pebbles.outline` |
| `GlyphTabBar.swift:37` selected capsule (mechanical → `primaryContainer`) | the selected tab ink at :32 takes `selection == tab ? Color.pebbles.onPrimaryContainer : Color.pebbles.onSurfaceVariant` |
| `GlyphTabBar.swift:72` capsule fill | `Color.pebbles.stock` |
| `GlyphTabBar.swift:74` `.white.opacity(0.22)` glass rim | `Color.pebbles.rule` |
| `GlyphTabBar.swift:77` `.black.opacity(0.16)` | `Color.pebbles.scrim.opacity(0.16)` |
| `GlyphDetailDrawer.swift:118,127` label knock-out ground | `Color.pebbles.surface` (matches the sheet ground) |
| `GlyphDetailDrawer.swift:143,147` muted | `Color.pebbles.outline` |
| `GlyphDetailDrawer.swift` `accent.secondary` (mechanical → `secondaryContainer`) | its content takes `onSecondaryContainer` |
| `GlyphCarveSheet.swift:67` prompt | `Color.pebbles.outline` |
| `GlyphCarveSheet.swift:85` canvas stroke | `Color.pebbles.outline` (control boundary) |
| `GlyphCarveSheet.swift:99,102` the non-destructive capsule button (mechanical) | label → `Color.pebbles.onPrimaryContainer` on the `primaryContainer` capsule |
| `GlyphView.swift:52` dashed carve placeholder, `:70`, `:74` thumbnail stroke | `Color.pebbles.outline` |
| `GlyphsListView.swift:138` muted | `Color.pebbles.outline` |
| `SlideToConfirm.swift:38` track `accent.surface` | `Color.pebbles.surfaceContainerHighest`. The spec table maps both the track and the progress to `primaryContainer`, which would make the progress invisible. The progress keeps the container role and the track takes the fill role. Say so in the PR. |
| `SlideToConfirm.swift:42` progress `accent.light` | `Color.pebbles.primaryContainer` |
| `SlideToConfirm.swift:78` `.white` on the `primary` thumb | `Color.pebbles.onPrimary` |

- [ ] **Step 4: Ratchet, then commit**

```bash
grep -rnE '(Color)?\.(system|accent)\.' apps/ios/Pebbles/Features/Glyph   # expect nothing
git add apps/ios/Pebbles/Features/Glyph apps/ios/PebblesTests/ThemeLiteralsTests.swift
git commit -m "feat(ios): glyph views speak m3 roles"
```

### Task 38: Delete the old palette and the `debt` table

**Files:**
- Delete: `apps/ios/Pebbles/Theme/Palettes.swift`
- Delete: `apps/ios/Pebbles/Resources/Assets.xcassets/{SystemBackground,SystemForeground,SystemMuted,SystemOnLight,SystemSecondary,AccentDark,AccentLight,AccentPrimary,AccentSecondary,AccentShaded,AccentSurface}.colorset`
- Modify: `apps/ios/Pebbles/Theme/ColorTokensPreview.swift` (remove `Swatch`, `TokensGrid`, the two "Tokens —" previews)
- Modify: `apps/ios/PebblesTests/ThemeLiteralsTests.swift` (remove `debt`)

- [ ] **Step 1: Confirm nothing reads an old token**

Run: `grep -rn "Color\.system\|Color\.accent\|\.system\.\|\.accent\." apps/ios/Pebbles apps/ios/PebblesTests apps/ios/PebblesWidget | grep -v 'Theme/Palettes.swift\|Theme/ColorTokensPreview.swift'`
Expected: no output.

- [ ] **Step 2: Delete**

```bash
git rm apps/ios/Pebbles/Theme/Palettes.swift
for c in SystemBackground SystemForeground SystemMuted SystemOnLight SystemSecondary AccentDark AccentLight AccentPrimary AccentSecondary AccentShaded AccentSurface; do
  git rm -r "apps/ios/Pebbles/Resources/Assets.xcassets/$c.colorset"
done
```

In `ColorTokensPreview.swift`, delete `private struct Swatch`, `private struct TokensGrid` and the `#Preview("Tokens — Light")` / `#Preview("Tokens — Dark")` blocks, keeping the roles grid from Task 11.

- [ ] **Step 3: Remove the ratchet's `debt`**

In `ThemeLiteralsTests.swift`, delete the `debt` property and its doc comment. In `ratchet()`, replace `var allowed = Self.debt` with `var allowed: [String: Int] = [:]`. In the suite's doc comment, replace the "ratchet" paragraph with: "A file's count is its number of offending lines; it must equal its `allowlist` entry exactly, so a stale entry fails too."

- [ ] **Step 4: Gates**

```bash
grep -rn "Color\.system\|Color\.accent\|\.system\.\|\.accent\." apps/ios/Pebbles   # expect nothing (spec §9)
ls apps/ios/Pebbles/Resources/Assets.xcassets | grep -E '^(System|Accent(Dark|Light|Primary|Secondary|Shaded|Surface))'   # expect nothing
npm run generate:colors --workspace=@pbbls/ios && test -z "$(git status --porcelain -- apps/ios)" && echo "no drift"
cd apps/ios && xcodegen generate && rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*
cd /Users/alexis/code/pbbls && npm run lint --workspace=@pbbls/ios && npm run test --workspace=@pbbls/ios
npm run verify:contrast --workspace=@pbbls/ios
npm run test --workspace=apps/web -- lib/theme/ios-colorsets.test.ts
```

Expected: every command green. The Vitest renderColorset fixture inlines its expected string, so deleting `SystemBackground.colorset` does not affect it.

- [ ] **Step 5: Commit**

```bash
git add -A apps/ios/Pebbles/Theme apps/ios/Pebbles/Resources/Assets.xcassets apps/ios/PebblesTests/ThemeLiteralsTests.swift
git commit -m "feat(ios): delete the old palette; the literal ratchet holds no debt"
```

### Task 39: Verify A3b, simulator pass, open the PR

- [ ] **Step 1: Simulator pass (spec §9)**

Walk the Path (rows, week roll and Rive cairn focus, the new-pebble button, the read view with and without an emotion palette, the privacy badge), the valence picker headline, Profile (cards, achievements, assiduity grid, settings), and Glyphs (list, carve sheet, detail drawer, slide-to-confirm). Do it in light, dark, and Increase Contrast. Turn Increase Contrast on while the week roll is visible: the cairn stroke must re-apply. Compare the neutrals with the hosted Spine-on-Material mock.

- [ ] **Step 2: Push and open the PR**

```bash
git fetch origin && git log --oneline origin/main..main   # expect nothing
gh stack push
gh stack submit --auto
gh pr edit feat/921-ios-m3-roles-path \
  --title "feat(ios): path, profile and glyphs speak m3 roles; the old palette goes" \
  --add-label feat --add-label ui --add-label ios --add-label no-lab-note \
  --milestone "M64 · New Path" --body-file <temp file>
```

Body: open with `Part of #921`. Then D5 (the brand stone), D6 (Rive and contrast), the `ValenceStoneStyle` deletion (and its §B3.1 ownership note), the `SlideToConfirm` track deviation, the deletions, and the simulator-pass screenshots. End with the session attribution.

---

## Self-review notes (kept for the executor)

- **Spec coverage.**
  - D1: Tasks 2–6.
  - D2: Tasks 3–4, proven in Task 9.
  - §2.3: Tasks 4, 6, 8.
  - §2.4: Task 4.
  - §2.5: Tasks 2, 4, 25.
  - D3: Task 5.
  - D4: Tasks 23–29 and 34–37.
  - D5: Task 32.
  - D6: Task 33.
  - §5: Task 38.
  - D7: Tasks 16, 17, 19.
  - D8: Task 10, with the debt shrinking in Tasks 16, 17, 19, 24, 25, 27, 28, 29, 34, 35, 37 and 38.
  - D9: Tasks 18, 19A, 19B.1–19B.3.
  - §8: Tasks 7, 12.
  - §9: Tasks 7, 9, 14, 21, 30, 39.
  - §10: part headers and Tasks 1, 14, 21, 30, 39.
  - §11: Task 20.
  - §12: the PR bodies in Tasks 14 and 21.
- **Deviations to surface in the PRs.**
  1. `PebbleMedia/` is migrated in A3a. The spec's folder lists omit it.
  2. The `SlideToConfirm` track takes `surfaceContainerHighest`, not `primaryContainer`.
  3. `ValenceStoneStyle` follows foundations §B3.1 (delete `inkHexes` too) over the m3-colour spec's "`inkHexes` is B3's".
  4. The ratchet counts offending lines, not matches, so that `PebbleSnapFrame`'s single fixture line equals its allowlist count of 1.
- **Re-grep before trusting line numbers.** Every `path:line` above was re-grepped on 2026-10-20 at `d53be9cf`. If main has moved, `grep -n` the quoted code before editing.
