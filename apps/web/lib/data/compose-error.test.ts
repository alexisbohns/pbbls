import { describe, expect, it } from "vitest"

import { hasPublishMinimum, composerDefaults } from "@/components/record/draft-payload"
import {
  ComposeError,
  composeErrorSlug,
  publishErrorMessageKey,
  readEdgeErrorBody,
} from "./compose-error"

/**
 * A pebble needs a domain since the moments split (#1046, D2). The composers
 * gate on it, and when the server refuses anyway the slug reaches the user as
 * words, not as "try again".
 */

describe("readEdgeErrorBody", () => {
  it("reads the slug compose-pebble answers with, verbatim", () => {
    // The edge function's 400 body: `{ error: rpcError.message }`.
    const response = new Response(JSON.stringify({ error: "pebble_needs_domain" }), {
      status: 400,
      headers: { "Content-Type": "application/json" },
    })
    return expect(readEdgeErrorBody(response)).resolves.toBe("pebble_needs_domain")
  })

  it("answers null for a body it cannot read", async () => {
    expect(await readEdgeErrorBody(new Response("relay error", { status: 502 }))).toBeNull()
    expect(await readEdgeErrorBody(new Response(JSON.stringify({ error: "" }), { status: 400 }))).toBeNull()
    expect(await readEdgeErrorBody(undefined)).toBeNull()
  })
})

describe("publishErrorMessageKey", () => {
  it("explains a missing domain and keeps the generic message for the rest", () => {
    expect(publishErrorMessageKey(new ComposeError("compose-pebble", "pebble_needs_domain"))).toBe("needsDomain")
    expect(publishErrorMessageKey(new ComposeError("compose-pebble", "moment_empty"))).toBe("publishFailed")
    expect(publishErrorMessageKey(new Error("pebble_needs_domain"))).toBe("publishFailed")
    expect(composeErrorSlug("nope")).toBeNull()
  })
})

describe("hasPublishMinimum", () => {
  const ready = { ...composerDefaults(), name: "Swim", emotionId: "joy" }

  it("requires a domain on top of a name and an emotion", () => {
    expect(hasPublishMinimum(ready)).toBe(false)
    expect(hasPublishMinimum({ ...ready, domainIds: ["d1"] })).toBe(true)
  })

  it("still requires a name and an emotion", () => {
    expect(hasPublishMinimum({ ...ready, name: "  ", domainIds: ["d1"] })).toBe(false)
    expect(hasPublishMinimum({ ...ready, emotionId: "", domainIds: ["d1"] })).toBe(false)
  })
})
