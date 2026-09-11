import { describe, it, expect } from "vitest"
import { canSubmitRegistration, type ConsentChecks } from "./registration-gate"

const all: ConsentChecks = { terms: true, privacy: true, healthData: true }

describe("canSubmitRegistration", () => {
  it("allows submission only when all three are accepted", () => {
    expect(canSubmitRegistration(all)).toBe(true)
  })

  it("blocks when any single box is unticked", () => {
    expect(canSubmitRegistration({ ...all, terms: false })).toBe(false)
    expect(canSubmitRegistration({ ...all, privacy: false })).toBe(false)
    expect(canSubmitRegistration({ ...all, healthData: false })).toBe(false)
  })

  it("blocks when nothing is accepted", () => {
    expect(canSubmitRegistration({ terms: false, privacy: false, healthData: false })).toBe(false)
  })
})
