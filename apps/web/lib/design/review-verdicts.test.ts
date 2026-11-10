import { describe, expect, it } from "vitest"
import { VERDICTS, VERDICT_KEYS, isVerdict, needsWork, nextVerdict, parseRecord, verdictLabel } from "./useReviewVerdicts"

describe("nextVerdict", () => {
  it("cycles unset → ok → … → fix → unset", () => {
    expect(nextVerdict(undefined)).toBe("ok")
    expect(nextVerdict("ok")).toBe("progress")
    expect(nextVerdict("drop-twin")).toBe("incorrect")
    expect(nextVerdict("fix")).toBeUndefined()
  })
})

describe("parseRecord", () => {
  const isWord = (v: unknown): v is string => typeof v === "string" && v !== ""
  it("keeps the entries the guard accepts and drops the rest", () => {
    expect(parseRecord('{"moon-cloud":"tag the moon as focus","home":"","x":3}', isWord)).toEqual({ "moon-cloud": "tag the moon as focus" })
  })
  it("reads nothing from empty, broken or non-object storage", () => {
    expect(parseRecord(null, isWord)).toEqual({})
    expect(parseRecord("{not json", isWord)).toEqual({})
    expect(parseRecord('"a string"', isWord)).toEqual({})
  })
})

describe("verdicts", () => {
  it("has one key per verdict, Space for ok", () => {
    expect(VERDICTS).toEqual(["ok", "progress", "solid", "mark", "drop-twin", "incorrect", "fix"])
    expect(VERDICT_KEYS).toEqual({ " ": "ok", p: "progress", s: "solid", m: "mark", d: "drop-twin", i: "incorrect", f: "fix" })
    expect(new Set(Object.values(VERDICT_KEYS)).size).toBe(VERDICTS.length)
  })

  it("keeps the old stored verdicts valid and refuses unknown ones", () => {
    for (const v of ["ok", "fix", "drop-twin", "progress", "solid", "mark", "incorrect"]) expect(isVerdict(v)).toBe(true)
    expect(isVerdict("half")).toBe(false)
    expect(parseRecord('{"a":"solid","b":"nope"}', isVerdict)).toEqual({ a: "solid" })
  })

  it("names each verdict for the tile and counts everything but ok as work", () => {
    expect(verdictLabel("progress")).toBe("needs progress")
    expect(verdictLabel("drop-twin")).toBe("drop twin")
    expect(needsWork("ok")).toBe(false)
    expect(needsWork(undefined)).toBe(false)
    expect(needsWork("incorrect")).toBe(true)
  })
})
