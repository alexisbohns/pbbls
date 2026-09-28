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
