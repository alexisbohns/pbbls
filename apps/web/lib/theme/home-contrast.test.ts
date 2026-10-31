import { readFileSync } from "node:fs"
import path from "node:path"
import { fileURLToPath } from "node:url"
import { describe, expect, it } from "vitest"
import { contrastRatio, type Rgb } from "./contrast"
import { ANDROID_SCHEMES_FROM_WEB, parseColorSchemes } from "./m3-schemes"

// The text pairs Home and You set (#1384, spec §Accessibility), checked against
// the real Android export in Paper and Night: every one must reach WCAG AA.
const webRoot = fileURLToPath(new URL("../..", import.meta.url))
const schemes = parseColorSchemes(readFileSync(path.join(webRoot, ANDROID_SCHEMES_FROM_WEB), "utf8"))

function rgb(hex: string): Rgb {
  const channel = (i: number) => parseInt(hex.slice(i, i + 2), 16)
  return [channel(1), channel(3), channel(5)]
}

const SHARED: [ink: string, ground: string][] = [
  ["on-surface", "surface"],
  ["on-surface-variant", "surface"],
  ["on-surface-variant", "surface-container"],
  ["primary", "surface"],
  ["on-primary-container", "primary-container"],
  ["on-tertiary-container", "tertiary-container"],
  ["on-surface", "surface-container"],
]

const CASES = {
  LightScheme: [...SHARED, ["on-surface", "surface-container-lowest"]],
  DarkScheme: [...SHARED, ["on-surface", "surface-container-low"]],
} satisfies Record<string, [string, string][]>

describe("Home and You text contrast", () => {
  for (const [name, pairs] of Object.entries(CASES)) {
    const scheme = schemes.find((s) => s.name === name)
    const role = (r: string) => {
      const hex = scheme?.roles.find(([k]) => k === r)?.[1]
      if (!hex) throw new Error(`${name} has no ${r}`)
      return rgb(hex)
    }
    for (const [ink, ground] of pairs) {
      it(`${name}: ${ink} on ${ground} is at least 4.5:1`, () => {
        expect(contrastRatio(role(ink), role(ground))).toBeGreaterThanOrEqual(4.5)
      })
    }
  }
})
