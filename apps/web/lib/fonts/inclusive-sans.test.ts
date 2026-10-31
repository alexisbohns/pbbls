import { readFileSync } from "node:fs"
import path from "node:path"
import { fileURLToPath } from "node:url"
import { describe, expect, it } from "vitest"

// Inclusive Sans ships from the maintainer's own files (#1384, spec D5): the same
// bytes iOS bundles, so the two surfaces share a checksum.
const webRoot = fileURLToPath(new URL("../..", import.meta.url))
const fontDir = path.join(webRoot, "app/fonts/inclusive-sans")

const FILES = [
  ["InclusiveSans-VariableFont_wght.ttf", 112064],
  ["InclusiveSans-Italic-VariableFont_wght.ttf", 114308],
] as const

describe("Inclusive Sans files", () => {
  for (const [name, bytes] of FILES) {
    it(`${name} is the brief's file`, () => {
      const data = readFileSync(path.join(fontDir, name))
      expect(data.byteLength).toBe(bytes)
      // A TrueType outline font starts with the sfnt version 0x00010000.
      expect(data.readUInt32BE(0)).toBe(0x00010000)
    })
  }

  it("carries its licence", () => {
    expect(readFileSync(path.join(fontDir, "OFL.txt"), "utf8")).toContain("SIL OPEN FONT LICENSE")
  })
})
