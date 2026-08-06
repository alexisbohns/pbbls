import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { allFailedError, settledOr } from "./settled-feed"

const fulfilled = <T>(value: T): PromiseSettledResult<T> => ({ status: "fulfilled", value })
const rejected = (reason: unknown): PromiseSettledResult<never> => ({ status: "rejected", reason })

beforeEach(() => {
  vi.spyOn(console, "error").mockImplementation(() => {})
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe("settledOr", () => {
  it("returns the value when the feed settled", () => {
    expect(settledOr(fulfilled([1, 2, 3]), [], "test")).toEqual([1, 2, 3])
  })

  it("returns the fallback when the feed rejected", () => {
    expect(settledOr(rejected(new Error("boom")), [], "test")).toEqual([])
  })

  it("logs the rejection with its scope rather than swallowing it silently", () => {
    const reason = new Error("boom")
    settledOr(rejected(reason), [], "useLabFeed:changelog")
    expect(console.error).toHaveBeenCalledWith("[useLabFeed:changelog] failed to load", reason)
  })

  it("never throws — a failing feed must not take its callers down", () => {
    expect(() => settledOr(rejected("a bare string"), new Set<string>(), "test")).not.toThrow()
  })
})

describe("allFailedError", () => {
  const boom = new Error("boom")

  it("is null when every content feed succeeded", () => {
    expect(allFailedError([fulfilled([]), fulfilled([]), fulfilled([]), fulfilled([])], "fallback")).toBeNull()
  })

  // The whole point of #700: three sections loading is not a page-level error.
  it("is null when even one content feed succeeded", () => {
    expect(allFailedError([rejected(boom), rejected(boom), rejected(boom), fulfilled([])], "fallback")).toBeNull()
  })

  it("returns an error only when every content feed failed", () => {
    expect(allFailedError([rejected(boom), rejected(boom)], "fallback")).toBe(boom)
  })

  it("wraps a non-Error rejection in an Error carrying the fallback message", () => {
    const result = allFailedError([rejected("just a string")], "Failed to load Lab")
    expect(result).toBeInstanceOf(Error)
    expect(result?.message).toBe("Failed to load Lab")
  })

  it("surfaces the first rejection, so the logged cause matches what the user hit", () => {
    const first = new Error("first")
    expect(allFailedError([rejected(first), rejected(new Error("second"))], "fallback")).toBe(first)
  })

  it("is null for an empty list — no content feeds means nothing failed", () => {
    expect(allFailedError([], "fallback")).toBeNull()
  })
})
