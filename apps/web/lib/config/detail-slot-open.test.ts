import { describe, expect, it } from "vitest"
import type { FlightRouterState } from "next/dist/shared/lib/app-router-types"
import { computeSelectedLayoutSegment, getSelectedLayoutSegmentPath } from "next/dist/shared/lib/segment"
import { isDetailOpen } from "./app-routes"

// Whether a detail is open is read from the `@detail` slot's segments in the
// router tree. The trees below are shaped as Next builds them for this app's
// slot, and the segments come from Next's own code (what
// `useSelectedLayoutSegments("detail")` returns), so a Next upgrade that
// changes the reading fails here and not on a phone.
const PAGE: FlightRouterState = ["__PAGE__", {}]

function rootWith(detail: FlightRouterState): FlightRouterState {
  return ["", { children: ["(you)", { children: ["profile", { children: PAGE }] }], detail }]
}

const pane = (...route: FlightRouterState[0][]): FlightRouterState =>
  ["(pane)", { children: route.reduceRight<FlightRouterState>((child, segment) => [segment, { children: child }], PAGE) }]

const OPEN: Record<string, FlightRouterState> = {
  "/settings": pane("settings"),
  "/achievements/year/2026": pane("achievements", "year", ["year", "2026", "d", null]),
  "/souls/abc": pane("souls", ["id", "abc", "d", null]),
  "/pebble/abc (intercepted)": pane("(.)pebble", ["id", "abc", "d", null]),
}

const CLOSED: Record<string, FlightRouterState> = {
  "/ (the slot's null root page)": PAGE,
  "/profile (the null catch-all)": [["rest", "profile", "c", null], { children: PAGE }],
  "a hard load with no match (the null default)": ["__DEFAULT__", {}],
}

const slotSegments = (tree: FlightRouterState) => getSelectedLayoutSegmentPath(tree, "detail")

describe("isDetailOpen", () => {
  it("is open on every detail, whose page sits in the slot's (pane) group", () => {
    for (const [url, slot] of Object.entries(OPEN)) expect(isDetailOpen(slotSegments(rootWith(slot))), url).toBe(true)
  })

  it("is closed on the slot's null pages", () => {
    for (const [url, slot] of Object.entries(CLOSED)) expect(isDetailOpen(slotSegments(rootWith(slot))), url).toBe(false)
  })

  it("is closed outside the app router", () => {
    expect(isDetailOpen(null)).toBe(false)
  })

  it("cannot be read from useSelectedLayoutSegment, which gives a slot's last segment, never (pane)", () => {
    // The bug this pins: `useSelectedLayoutSegment("detail") === "(pane)"` was
    // never true, so below 1024 px no detail ever opened.
    for (const [url, slot] of Object.entries(OPEN)) {
      expect(computeSelectedLayoutSegment(slotSegments(rootWith(slot)), "detail"), url).not.toBe("(pane)")
    }
  })
})
