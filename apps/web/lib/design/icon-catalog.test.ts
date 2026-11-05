import { describe, expect, it } from "vitest"
import { ICON_ROWS, componentName, lucideBaseName, replacementsFor } from "./icon-catalog"

describe("ICON_ROWS", () => {
  // The icons page draws every row: a row the generator emits must resolve.
  it("finds the generated component of every candidate row and twin", () => {
    for (const row of ICON_ROWS.filter((r) => r.status !== "exploring")) {
      expect(row.Icon, row.name).not.toBeNull()
      for (const twin of row.twins) expect(twin.Icon, `${row.name}.${twin.variant}`).not.toBeNull()
    }
  })
})

describe("componentName", () => {
  it("follows the generator's naming", () => {
    expect(componentName("check-circle-dot")).toBe("CheckCircleDot")
    expect(componentName("path", "lit-1")).toBe("PathLit1")
    expect(componentName("home", "active")).toBe("HomeActive")
  })
})

describe("lucide names", () => {
  it("strips the alias spellings", () => {
    expect(lucideBaseName("Trash2Icon")).toBe("Trash2")
    expect(lucideBaseName("LucideTrash2")).toBe("Trash2")
    expect(lucideBaseName("Icon")).toBe("Icon")
  })

  it("finds the drawing that replaces a Lucide icon", () => {
    expect(replacementsFor("CircleCheckBig").map((r) => r.name)).toContain("check-circle")
  })
})
