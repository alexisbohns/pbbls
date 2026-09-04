import { describe, expect, it } from "vitest"
import { hasDisallowedEmailChar, normalizeEmailInput } from "./email-input"

// Parity note: these cases mirror the iOS `AuthViewLogicTests` /
// Android `AuthLogicTest` expectations for the same helpers. The three surfaces
// must agree on what a valid address is — that agreement, not the strip itself,
// is the point of the rule (`docs/decisions/log.md`, 2026-05-26).
describe("normalizeEmailInput", () => {
  // The character is removed; the tag after it is NOT. "alex+spam@gmail.com"
  // becomes "alexspam@gmail.com", not "alex@gmail.com" — which is what defeats
  // aliasing, since the result no longer routes to the original mailbox. iOS
  // (`removeAll { $0 == "+" }`) and Android (`filter { it != '+' }`) do exactly
  // this, and web matching them character-for-character is the whole point.
  it("strips the banned '+' anywhere in the address, keeping the rest", () => {
    expect(normalizeEmailInput("alex+spam@gmail.com")).toBe("alexspam@gmail.com")
    expect(normalizeEmailInput("+leading@gmail.com")).toBe("leading@gmail.com")
    expect(normalizeEmailInput("trailing@gmail.com+")).toBe("trailing@gmail.com")
  })

  it("strips every occurrence, not just the first", () => {
    expect(normalizeEmailInput("a+b+c@gmail.com")).toBe("abc@gmail.com")
  })

  it("lowercases, matching iOS and Android", () => {
    expect(normalizeEmailInput("Alex.Bohns@Gmail.COM")).toBe("alex.bohns@gmail.com")
  })

  it("leaves a clean address untouched", () => {
    expect(normalizeEmailInput("alex@gmail.com")).toBe("alex@gmail.com")
  })

  it("handles the empty string", () => {
    expect(normalizeEmailInput("")).toBe("")
  })

  it("is idempotent — re-normalizing a normalized value is a no-op", () => {
    const once = normalizeEmailInput("Alex+Spam@Gmail.com")
    expect(normalizeEmailInput(once)).toBe(once)
  })
})

describe("hasDisallowedEmailChar", () => {
  it("detects the banned character before it is stripped", () => {
    expect(hasDisallowedEmailChar("alex+spam@gmail.com")).toBe(true)
  })

  it("is false for a clean address", () => {
    expect(hasDisallowedEmailChar("alex@gmail.com")).toBe(false)
  })

  it("is false once the value has been normalized — why callers must check first", () => {
    expect(hasDisallowedEmailChar(normalizeEmailInput("alex+spam@gmail.com"))).toBe(false)
  })
})
