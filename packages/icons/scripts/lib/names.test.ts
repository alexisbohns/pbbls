import { describe, expect, it } from "vitest"
import { camelName, constantName, pascalName } from "./names"

describe("names", () => {
  it.each([
    ["check-circle", "CheckCircle", "checkCircle", "CHECK_CIRCLE"],
    ["home", "Home", "home", "HOME"],
    ["pebble-glyph-open", "PebbleGlyphOpen", "pebbleGlyphOpen", "PEBBLE_GLYPH_OPEN"],
  ])("%s", (name, pascal, camel, constant) => {
    expect(pascalName(name)).toBe(pascal)
    expect(camelName(name)).toBe(camel)
    expect(constantName(name)).toBe(constant)
  })
})
