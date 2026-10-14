import { describe, expect, it } from "vitest"
import { MomentWriteError, momentErrorKey } from "./moment-write-error"
import { IOS_ERR_MOMENT_EMPTY, IOS_ERR_NEEDS_DOMAIN } from "./__fixtures__/moments-captured"

// PostgREST error bodies: the two captured ones verbatim, the rest shaped from
// the RPC bodies in 20261012090100 (errcode, message, detail).
const keyOf = (fn: "save_moment" | "complete_pebble", body: string) =>
  momentErrorKey(new MomentWriteError(fn, JSON.parse(body)))

describe("momentErrorKey", () => {
  it("maps the captured refusals", () => {
    expect(keyOf("save_moment", IOS_ERR_MOMENT_EMPTY)).toBe("momentEmpty")
    expect(keyOf("complete_pebble", IOS_ERR_NEEDS_DOMAIN)).toBe("pebbleNeedsDomain")
  })

  it("maps an ownership refusal whatever its code", () => {
    expect(
      keyOf("complete_pebble", `{"code":"42501","details":"aaaaaaaa-0000-4000-8000-000000000001","hint":null,"message":"soul_not_owned"}`),
    ).toBe("soulNotOwned")
  })

  it("maps a missing moment, and any other P0002, to momentNotFound", () => {
    expect(keyOf("complete_pebble", `{"code":"P0002","details":null,"hint":null,"message":"moment_not_found"}`)).toBe("momentNotFound")
    expect(keyOf("save_moment", `{"code":"P0002","details":null,"hint":null,"message":"snap_not_found"}`)).toBe("momentNotFound")
  })

  it("maps a client bug to the generic copy", () => {
    expect(keyOf("save_moment", `{"code":"22023","details":"snaps must be an array","hint":null,"message":"invalid_payload"}`)).toBe("generic")
  })

  it("maps the photo quota", () => {
    expect(keyOf("save_moment", `{"code":"P0001","details":null,"hint":null,"message":"media_quota_exceeded"}`)).toBe("mediaQuotaExceeded")
  })

  it("maps anything that is not a refusal to the generic copy", () => {
    expect(momentErrorKey(new Error("Failed to fetch"))).toBe("generic")
    expect(momentErrorKey(new MomentWriteError("save_moment", null))).toBe("generic")
  })

  it("keeps the code and names the function", () => {
    const err = new MomentWriteError("save_moment", JSON.parse(IOS_ERR_MOMENT_EMPTY))
    expect(err.code).toBe("P0001")
    expect(err.message).toBe("save_moment failed: moment_empty")
  })
})
