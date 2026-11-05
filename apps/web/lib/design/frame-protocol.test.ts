import { describe, expect, it } from "vitest"
import { fitScale, DESIGN_DEVICES, FRAME_GAP } from "./devices"
import { frameHref, frameMessage, parseFrameMessage, parseFrameTarget } from "./frame-protocol"

describe("frameHref / parseFrameTarget", () => {
  it("round-trips a view with the theme", () => {
    const href = frameHref({ view: "view-home" }, "theme=m3&mode=dark")
    const params = new URL(href, "https://x.test").searchParams
    expect(parseFrameTarget(params)).toEqual({ view: "view-home" })
    expect(params.get("theme")).toBe("m3")
    expect(params.get("mode")).toBe("dark")
  })

  it("round-trips a set of specimens", () => {
    const href = frameHref({ specimens: ["button", "badge"] }, "theme=current")
    expect(parseFrameTarget(new URL(href, "https://x.test").searchParams)).toEqual({ specimens: ["button", "badge"] })
  })

  it("has no target without params", () => {
    expect(parseFrameTarget(new URLSearchParams(""))).toBeNull()
    expect(parseFrameTarget(new URLSearchParams("specimens="))).toBeNull()
  })
})

describe("parseFrameMessage", () => {
  it("accepts its own messages", () => {
    expect(parseFrameMessage(frameMessage({ type: "height", height: 640 }))).toEqual(frameMessage({ type: "height", height: 640 }))
    expect(parseFrameMessage(frameMessage({ type: "theme", themeKey: "a=b" }))).toEqual(frameMessage({ type: "theme", themeKey: "a=b" }))
    expect(parseFrameMessage(frameMessage({ type: "ready" }))).toEqual(frameMessage({ type: "ready" }))
  })

  it("ignores anything else posted to the window", () => {
    expect(parseFrameMessage("hello")).toBeNull()
    expect(parseFrameMessage({ type: "height", height: 1 })).toBeNull()
    expect(parseFrameMessage({ source: "pbbls-design", type: "height", height: Number.NaN })).toBeNull()
  })
})

describe("fitScale", () => {
  it("lays a phone and a desktop side by side at one scale", () => {
    const available = 1000
    const scale = fitScale(DESIGN_DEVICES, available)
    const used = DESIGN_DEVICES.reduce((sum, d) => sum + d.width * scale, 0) + FRAME_GAP
    expect(used).toBeCloseTo(available)
  })

  it("never enlarges past the real size", () => {
    expect(fitScale(DESIGN_DEVICES, 10_000)).toBe(1)
  })
})
