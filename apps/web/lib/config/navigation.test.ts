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
