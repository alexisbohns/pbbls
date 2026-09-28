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
