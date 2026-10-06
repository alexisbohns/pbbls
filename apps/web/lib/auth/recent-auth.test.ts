import { describe, it, expect } from "vitest"
import {
  isReauthRequired,
  isRecentSignIn,
  newestAmrStamp,
  reauthMethod,
  REAUTH_REQUIRED,
} from "./recent-auth"

const NOW_MS = Date.parse("2026-10-06T12:00:00Z")
const NOW_S = NOW_MS / 1000

const b64url = (s: string) =>
  Buffer.from(s, "utf8").toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "")

const jwt = (payload: string) => `${b64url('{"alg":"HS256","typ":"JWT"}')}.${b64url(payload)}.sig`

/**
 * Real GoTrue payloads. The password `amr` array, `[{"method":"password",
 * "timestamp":1790539740}]`, was captured verbatim from a real sign-up token
 * by `verify-recent-auth.ts` against the linked project on 2026-09-27 (the same
 * capture Android's RecentAuthTest pins). Only the timestamp is substituted so
 * the test controls the clock. The `oauth` variant is GoTrue's own shape with
 * `method` = `oauth`.
 */
const realPassword = (at: number) =>
  `{"aud":"authenticated","role":"authenticated","aal":"aal1","amr":[{"method":"password","timestamp":${at}}],"is_anonymous":false}`
const realOauth = (at: number) =>
  `{"aud":"authenticated","role":"authenticated","aal":"aal1","amr":[{"method":"oauth","timestamp":${at}}],"is_anonymous":false}`

const tokenAt = (secondsAgo: number, payload = realPassword) => jwt(payload(NOW_S - secondsAgo))

describe("isRecentSignIn", () => {
  it("accepts the verbatim captured token shape", () => {
    const captured = jwt(
      '{"aud":"authenticated","role":"authenticated","aal":"aal1","amr":[{"method":"password","timestamp":1790539740}],"is_anonymous":false}',
    )
    expect(newestAmrStamp(captured)).toBe(1790539740)
    expect(isRecentSignIn(captured, 1790539740 * 1000 + 60_000)).toBe(true)
  })

  it("treats a password or oauth sign-in a minute ago as fresh", () => {
    expect(isRecentSignIn(tokenAt(60), NOW_MS)).toBe(true)
    expect(isRecentSignIn(tokenAt(60, realOauth), NOW_MS)).toBe(true)
  })

  it("is fresh exactly at the window edge and stale past it", () => {
    expect(isRecentSignIn(tokenAt(600), NOW_MS)).toBe(true)
    expect(isRecentSignIn(tokenAt(660), NOW_MS)).toBe(false)
  })

  it("counts any fresh stamp in a mixed amr", () => {
    const token = jwt(
      `{"amr":[{"method":"password","timestamp":${NOW_S - 7200}},{"method":"oauth","timestamp":${NOW_S - 30}}]}`,
    )
    expect(isRecentSignIn(token, NOW_MS)).toBe(true)
  })

  it("never treats missing, empty or malformed amr as fresh", () => {
    for (const payload of [
      "{}",
      '{"amr":[]}',
      '{"amr":null}',
      `{"amr":{"method":"password","timestamp":${NOW_S}}}`,
      '{"amr":[{"method":"password"}]}',
      `{"amr":[{"method":"password","timestamp":"${NOW_S}"}]}`,
      '{"amr":[1,"x",null,{"timestamp":{"nested":1}}]}',
      // RFC 8176 string form: GoTrue can emit it, and it carries no time.
      '{"amr":["pwd"]}',
    ]) {
      expect(isRecentSignIn(jwt(payload), NOW_MS), payload).toBe(false)
    }
  })

  it("never treats a non-JWT as fresh", () => {
    for (const token of [null, undefined, "", "token", "a.b", "a.%%%.c", `a.${b64url("not json")}.c`, `a.${b64url("[1]")}.c`]) {
      expect(isRecentSignIn(token, NOW_MS), String(token)).toBe(false)
    }
  })
})

describe("reauthMethod", () => {
  it("asks for a password whenever an email identity exists", () => {
    expect(reauthMethod(["email"])).toBe("password")
    expect(reauthMethod(["google", "email"])).toBe("password")
    expect(reauthMethod([])).toBe("password")
  })

  it("re-runs the linked provider for provider-only accounts", () => {
    expect(reauthMethod(["google"])).toBe("google")
    expect(reauthMethod(["apple"])).toBe("apple")
    expect(reauthMethod(["apple", "google"])).toBe("google")
  })
})

describe("isReauthRequired", () => {
  it("recognizes the stable code and nothing else", () => {
    expect(isReauthRequired(new Error(REAUTH_REQUIRED))).toBe(true)
    expect(isReauthRequired(new Error("handle_taken"))).toBe(false)
    expect(isReauthRequired(new Error("profile update timed out after 10000ms"))).toBe(false)
    expect(isReauthRequired(REAUTH_REQUIRED)).toBe(false)
    expect(isReauthRequired(null)).toBe(false)
  })
})
