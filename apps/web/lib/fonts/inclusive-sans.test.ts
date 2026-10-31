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

describe("Inclusive Sans wiring", () => {
  it("is loaded by next/font/local from both files, not preloaded", () => {
    const fonts = readFileSync(path.join(webRoot, "app/fonts.ts"), "utf8")
    for (const [name] of FILES) expect(fonts).toContain(`./fonts/inclusive-sans/${name}`)
    expect(fonts.match(/weight: "300 700"/g)).toHaveLength(2)
    expect(fonts).toContain("preload: false")
    expect(fonts).toContain('variable: "--font-inclusive-sans"')
  })

  it("is the face --m3-font-plain names", () => {
    const css = readFileSync(path.join(webRoot, "app/globals.css"), "utf8")
    expect(css).toContain("var(--font-inclusive-sans)")
    expect(css).not.toContain('"Inclusive Sans Variable"')
  })
})
