import { describe, it, expect } from "vitest"
import { activeConsent, type ConsentRow } from "./consent"

const row = (over: Partial<ConsentRow>): ConsentRow => ({
  id: "r1",
  kind: "health_data",
  document_version: "1.1.0",
  source: "web_register",
  granted_at: "2026-09-11T10:00:00Z",
  withdrawn_at: null,
  superseded_at: null,
  ...over,
})

describe("activeConsent", () => {
  it("returns the live row for the kind", () => {
    expect(activeConsent([row({})], "health_data")?.id).toBe("r1")
  })

  it("returns null when the only row was withdrawn", () => {
    expect(activeConsent([row({ withdrawn_at: "2026-09-12T10:00:00Z" })], "health_data")).toBeNull()
  })

  it("returns null when the only row was superseded", () => {
    expect(activeConsent([row({ superseded_at: "2026-09-12T10:00:00Z" })], "health_data")).toBeNull()
  })

  it("ignores rows of another kind", () => {
    expect(activeConsent([row({ kind: "public_profile" })], "health_data")).toBeNull()
  })

  it("picks the live row out of a full history", () => {
    const history = [
      row({ id: "old", superseded_at: "2026-09-12T10:00:00Z", document_version: "1.0.0" }),
      row({ id: "gone", withdrawn_at: "2026-09-13T10:00:00Z" }),
      row({ id: "live", document_version: "1.2.0" }),
    ]
    expect(activeConsent(history, "health_data")?.id).toBe("live")
  })

  it("returns null for an empty ledger", () => {
    expect(activeConsent([], "health_data")).toBeNull()
  })
})
