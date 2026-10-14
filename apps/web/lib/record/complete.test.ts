import { describe, expect, it } from "vitest"
import {
  completeDefaults,
  completeFlowReducer,
  firstGap,
  initialCompleteState,
  isDraftComplete,
  optionalButtonIsSkip,
  type CompleteDraft,
  type CompleteFlowAction,
  type CompleteFlowState,
} from "./complete"
import { COMPLETE_COUNTED_STEPS, COMPLETE_STEPS, type CompleteStep } from "./steps"

function run(state: CompleteFlowState, ...actions: CompleteFlowAction[]): CompleteFlowState {
  return actions.reduce(completeFlowReducer, state)
}

function at(step: CompleteStep, overrides: Partial<CompleteDraft> = {}): CompleteFlowState {
  return { draft: { ...completeDefaults(), ...overrides }, step }
}

/** Every mandatory answer given. */
function answered(overrides: Partial<CompleteDraft> = {}): CompleteDraft {
  return {
    ...completeDefaults(),
    valenceConfirmed: true,
    emotionId: "emotion-1",
    domainIds: ["domain-1"],
    ...overrides,
  }
}

describe("complete steps", () => {
  it("starts at valence, ends on success, and has no glyph step", () => {
    expect(COMPLETE_STEPS).toEqual([
      "valence", "emotion", "domain", "souls", "collection", "privacy", "success",
    ])
    expect(COMPLETE_COUNTED_STEPS).toHaveLength(6)
  })
})

describe("completeDefaults", () => {
  it("seeds neutral-medium, unconfirmed, secret, with nothing picked", () => {
    expect(completeDefaults()).toEqual({
      intensity: 2,
      positiveness: 0,
      valenceConfirmed: false,
      emotionId: null,
      domainIds: [],
      soulIds: [],
      collectionIds: [],
      visibility: "secret",
    })
    expect(initialCompleteState().step).toBe("valence")
  })
})

describe("completeFlowReducer", () => {
  it("a valence pick commits in place and confirms, without advancing", () => {
    const next = run(at("valence"), { type: "selectValence", intensity: 3, positiveness: -1 })
    expect(next.step).toBe("valence")
    expect(next.draft).toMatchObject({ intensity: 3, positiveness: -1, valenceConfirmed: true })
  })

  it("Continue on valence confirms the seed and advances", () => {
    const next = run(at("valence"), { type: "advance" })
    expect(next.step).toBe("emotion")
    expect(next.draft).toMatchObject({ intensity: 2, positiveness: 0, valenceConfirmed: true })
  })

  it("an emotion pick commits and advances", () => {
    const next = run(at("emotion"), { type: "selectEmotion", id: "e1" })
    expect(next.step).toBe("domain")
    expect(next.draft.emotionId).toBe("e1")
  })

  it("a domain pick holds exactly one domain and advances", () => {
    const next = run(at("domain", { domainIds: ["old-1", "old-2"] }), { type: "selectDomain", id: "d1" })
    expect(next.step).toBe("souls")
    expect(next.draft.domainIds).toEqual(["d1"])
  })

  it("a soul toggle keeps the step, and toggles off on a second tap", () => {
    const once = run(at("souls"), { type: "toggleSoul", id: "s1" })
    expect(once.step).toBe("souls")
    expect(once.draft.soulIds).toEqual(["s1"])
    expect(run(once, { type: "toggleSoul", id: "s1" }).draft.soulIds).toEqual([])
  })

  it("a collection pick holds one and advances; null selects none", () => {
    const picked = run(at("collection"), { type: "selectCollection", id: "c1" })
    expect(picked.step).toBe("privacy")
    expect(picked.draft.collectionIds).toEqual(["c1"])

    const none = run(at("collection", { collectionIds: ["c1"] }), { type: "selectCollection", id: null })
    expect(none.step).toBe("privacy")
    expect(none.draft.collectionIds).toEqual([])
  })

  it("a privacy pick keeps the step, and advance from privacy never reaches success", () => {
    const next = run(at("privacy", answered()), { type: "selectVisibility", value: "public" })
    expect(next.step).toBe("privacy")
    expect(next.draft.visibility).toBe("public")
    expect(run(next, { type: "advance" }).step).toBe("privacy")
  })

  it("optional steps advance unanswered, with Skip then Done", () => {
    expect(optionalButtonIsSkip(at("souls"))).toBe(true)
    expect(optionalButtonIsSkip(at("souls", { soulIds: ["s1"] }))).toBe(false)
    expect(run(at("souls"), { type: "advance" }).step).toBe("collection")
    expect(run(at("collection"), { type: "advance" }).step).toBe("privacy")
  })

  it("does not advance past an unanswered tile step", () => {
    expect(run(at("emotion"), { type: "advance" }).step).toBe("emotion")
    expect(run(at("domain"), { type: "advance" }).step).toBe("domain")
  })

  it("back from valence is a no-op, and back keeps answers", () => {
    expect(run(at("valence"), { type: "back" }).step).toBe("valence")
    const back = run(at("domain", { emotionId: "e1" }), { type: "back" })
    expect(back.step).toBe("emotion")
    expect(back.draft.emotionId).toBe("e1")
  })

  it("hydrate of a draft with an emotion but no domain lands on domain", () => {
    const next = run(at("valence"), {
      type: "hydrate",
      draft: { ...completeDefaults(), valenceConfirmed: true, emotionId: "e1" },
    })
    expect(next.step).toBe("domain")
    expect(next.draft.emotionId).toBe("e1")
  })

  it("hydrate of an unconfirmed valence lands on valence", () => {
    const next = run(at("valence"), { type: "hydrate", draft: { ...completeDefaults(), soulIds: ["s1"] } })
    expect(next.step).toBe("valence")
  })

  it("hydrate of a complete draft lands on privacy, never on an optional step", () => {
    expect(run(at("valence"), { type: "hydrate", draft: answered() }).step).toBe("privacy")
  })

  it("completed goes to success, after which back and advance are no-ops", () => {
    const done = run(at("privacy", answered()), { type: "completed" })
    expect(done.step).toBe("success")
    expect(run(done, { type: "back" }).step).toBe("success")
    expect(run(done, { type: "advance" }).step).toBe("success")
  })
})

describe("isDraftComplete", () => {
  it("needs a confirmed valence, an emotion and exactly one domain", () => {
    expect(isDraftComplete(answered())).toBe(true)
    expect(isDraftComplete(answered({ valenceConfirmed: false }))).toBe(false)
    expect(isDraftComplete(answered({ emotionId: null }))).toBe(false)
    expect(isDraftComplete(answered({ domainIds: [] }))).toBe(false)
    expect(isDraftComplete(answered({ domainIds: ["d1", "d2"] }))).toBe(false)
  })

  it("does not need souls or a collection", () => {
    expect(isDraftComplete(answered({ soulIds: [], collectionIds: [] }))).toBe(true)
  })
})

describe("firstGap", () => {
  it("walks valence, emotion, domain, else privacy", () => {
    expect(firstGap(at("privacy"))).toBe("valence")
    expect(firstGap(at("privacy", { valenceConfirmed: true }))).toBe("emotion")
    expect(firstGap(at("privacy", { valenceConfirmed: true, emotionId: "e1" }))).toBe("domain")
    expect(firstGap(at("valence", answered()))).toBe("privacy")
  })
})
