import { describe, expect, it } from "vitest"
import { composerDefaults, type ComposerState } from "@/components/record/draft-payload"
import {
  clampName,
  firstGap,
  hasAnswer,
  initialFlowState,
  isAnswered,
  NAME_LIMIT,
  optionalButtonIsSkip,
  recordFlowReducer,
  type RecordFlowAction,
  type RecordFlowState,
} from "./flow"
import {
  COUNTED_STEPS,
  dotIndex,
  isOptionalStep,
  nextStep,
  previousStep,
  RECORD_STEPS,
  type RecordStep,
} from "./steps"

function run(state: RecordFlowState, ...actions: RecordFlowAction[]): RecordFlowState {
  return actions.reduce(recordFlowReducer, state)
}

/** A draft with every mandatory answer filled in. */
function answered(overrides: Partial<ComposerState> = {}): ComposerState {
  return {
    ...composerDefaults(),
    name: "A walk by the river",
    emotionId: "emotion-1",
    domainIds: ["domain-1"],
    ...overrides,
  }
}

describe("record steps", () => {
  it("keeps the eleven steps in the order the flow depends on", () => {
    expect(RECORD_STEPS).toEqual([
      "photo", "when", "name", "valence", "emotion", "domain",
      "souls", "collection", "glyph", "privacy", "success",
    ])
  })

  it("puts photo before when and valence before emotion", () => {
    // The two sequencing dependencies a single-screen form cannot have: EXIF
    // seeds the moment, and the valence cell orders the emotion categories.
    expect(RECORD_STEPS.indexOf("photo")).toBeLessThan(RECORD_STEPS.indexOf("when"))
    expect(RECORD_STEPS.indexOf("valence")).toBeLessThan(RECORD_STEPS.indexOf("emotion"))
  })

  it("marks exactly the four skippable steps optional", () => {
    const optional = RECORD_STEPS.filter(isOptionalStep)
    expect(optional).toEqual(["photo", "souls", "collection", "glyph"])
  })

  it("counts ten dots and leaves success uncounted", () => {
    expect(COUNTED_STEPS).toHaveLength(10)
    expect(COUNTED_STEPS).not.toContain("success")
    expect(dotIndex("success")).toBeNull()
    expect(dotIndex("photo")).toBe(0)
    expect(dotIndex("privacy")).toBe(9)
  })

  it("has no step before photo and none after success", () => {
    expect(previousStep("photo")).toBeNull()
    expect(nextStep("success")).toBeNull()
  })
})

describe("forward gating", () => {
  it("refuses to leave a mandatory step that has no answer", () => {
    const blocked = run(initialFlowState(), { type: "goTo", step: "name" }, { type: "advance" })
    expect(blocked.step).toBe("name")
  })

  it("leaves a mandatory step once it is answered", () => {
    const moved = run(
      initialFlowState(),
      { type: "goTo", step: "name" },
      { type: "setName", value: "Coffee with Sam" },
      { type: "advance" },
    )
    expect(moved.step).toBe("valence")
  })

  it("lets an empty optional step be skipped", () => {
    // Photo is where the flow opens, and skipping it is the whole point of it
    // being step 0 rather than a requirement.
    const skipped = run(initialFlowState(), { type: "advance" })
    expect(skipped.step).toBe("when")
  })

  it("treats when, valence and privacy as pre-answered", () => {
    const state = initialFlowState()
    for (const step of ["when", "valence", "privacy"] as RecordStep[]) {
      expect(hasAnswer(step, state)).toBe(true)
    }
  })

  it("never advances past the terminal step", () => {
    const done = run(initialFlowState(answered()), { type: "published" }, { type: "advance" })
    expect(done.step).toBe("success")
  })
})

describe("back", () => {
  it("preserves the answers of the steps it walks back through", () => {
    const state = run(
      initialFlowState(),
      { type: "goTo", step: "name" },
      { type: "setName", value: "First snow" },
      { type: "advance" },
      { type: "advance" },
      { type: "selectEmotion", id: "emotion-7" },
      { type: "back" },
      { type: "back" },
    )
    expect(state.step).toBe("valence")
    expect(state.draft.name).toBe("First snow")
    expect(state.draft.emotionId).toBe("emotion-7")
  })

  it("stops at the first step and is unavailable on success", () => {
    expect(run(initialFlowState(), { type: "back" }).step).toBe("photo")
    const published = run(initialFlowState(answered()), { type: "published" }, { type: "back" })
    expect(published.step).toBe("success")
  })
})

describe("selection", () => {
  it("commits and advances on a tile pick", () => {
    const state = run(
      initialFlowState(),
      { type: "goTo", step: "emotion" },
      { type: "selectEmotion", id: "emotion-3" },
    )
    expect(state.draft.emotionId).toBe("emotion-3")
    expect(state.step).toBe("domain")
  })

  it("holds at most one domain while keeping the plural payload key", () => {
    const state = run(
      initialFlowState(),
      { type: "goTo", step: "domain" },
      { type: "selectDomain", id: "domain-1" },
      { type: "goTo", step: "domain" },
      { type: "selectDomain", id: "domain-2" },
    )
    expect(state.draft.domainIds).toEqual(["domain-2"])
  })

  it("does not advance on valence, so the nine cells stay readable", () => {
    const state = run(
      initialFlowState(),
      { type: "goTo", step: "valence" },
      { type: "selectValence", intensity: 3, valence: 1 },
    )
    expect(state.step).toBe("valence")
    expect(state.draft.intensity).toBe(3)
    expect(state.draft.valence).toBe(1)
  })

  it("does not publish on a privacy tap", () => {
    const state = run(
      initialFlowState(),
      { type: "goTo", step: "privacy" },
      { type: "selectVisibility", value: "public" },
    )
    expect(state.step).toBe("privacy")
    expect(state.draft.visibility).toBe("public")
  })

  it("toggles souls and collections without advancing", () => {
    const state = run(
      initialFlowState(),
      { type: "goTo", step: "souls" },
      { type: "toggleSoul", id: "soul-a" },
      { type: "toggleSoul", id: "soul-b" },
      { type: "toggleSoul", id: "soul-a" },
      { type: "goTo", step: "collection" },
      { type: "toggleCollection", id: "coll-a" },
      { type: "toggleCollection", id: "coll-b" },
    )
    expect(state.draft.soulIds).toEqual(["soul-b"])
    // Multi-select is a web capability the flow must not drop.
    expect(state.draft.collectionIds).toEqual(["coll-a", "coll-b"])
    expect(state.step).toBe("collection")
  })
})

describe("the optional button", () => {
  it("reads Skip while empty and Done once filled", () => {
    const empty = run(initialFlowState(), { type: "goTo", step: "souls" })
    expect(optionalButtonIsSkip(empty)).toBe(true)
    const filled = run(empty, { type: "toggleSoul", id: "soul-a" })
    expect(optionalButtonIsSkip(filled)).toBe(false)
    expect(isAnswered(filled)).toBe(true)
  })

  it("counts a photo as answered while it is still uploading", () => {
    // `pendingSnap` only exists once the upload lands; the button must read
    // Done from the moment the user picks.
    const picked = run(initialFlowState(), { type: "setPhoto", snap: undefined, attached: true })
    expect(hasAnswer("photo", picked)).toBe(true)
  })
})

describe("the name clamp", () => {
  it("clamps at the limit as the user types", () => {
    const long = "x".repeat(NAME_LIMIT + 20)
    const state = run(initialFlowState(), { type: "setName", value: long })
    expect(state.draft.name).toHaveLength(NAME_LIMIT)
    expect(clampName(long)).toHaveLength(NAME_LIMIT)
  })

  it("leaves a name under the limit alone", () => {
    const state = run(initialFlowState(), { type: "setName", value: "Short" })
    expect(state.draft.name).toBe("Short")
  })

  it("produces a fresh state for an over-limit keystroke, so the field re-renders", () => {
    // This is the whole mechanism behind the clamp being honest on screen. The
    // name step is a controlled field, so an over-limit keystroke is only
    // rejected visually if React re-renders and rewrites the DOM value. Adding
    // a "same value, skip the update" guard here would bail the reducer out,
    // leave the typed overflow sitting in the field, and trim it silently at
    // publish — which is exactly the bug iOS hit.
    const atLimit = run(initialFlowState(), { type: "setName", value: "x".repeat(NAME_LIMIT) })
    const overflowed = recordFlowReducer(atLimit, {
      type: "setName",
      value: `${"x".repeat(NAME_LIMIT)}y`,
    })
    expect(overflowed.draft.name).toBe(atLimit.draft.name)
    expect(overflowed).not.toBe(atLimit)
    expect(overflowed.draft).not.toBe(atLimit.draft)
  })
})

describe("the photo's capture date", () => {
  it("seeds the moment and flags where it came from", () => {
    const state = run(initialFlowState(), {
      type: "applyCaptureDate",
      value: "2026-08-01T09:30:00.000Z",
    })
    expect(state.draft.happenedAt).toBe("2026-08-01T09:30:00.000Z")
    expect(state.seededFromPhoto).toBe(true)
  })

  it("leaves the moment at now for a photo with no readable date", () => {
    const before = initialFlowState()
    const state = run(before, { type: "applyCaptureDate", value: null })
    expect(state.draft.happenedAt).toBe(before.draft.happenedAt)
    expect(state.seededFromPhoto).toBe(false)
  })

  it("stops claiming credit once the user picks a moment by hand", () => {
    const state = run(
      initialFlowState(),
      { type: "applyCaptureDate", value: "2026-08-01T09:30:00.000Z" },
      { type: "setHappenedAt", value: "2026-08-02T18:00:00.000Z" },
    )
    expect(state.seededFromPhoto).toBe(false)
  })
})

describe("resuming a draft", () => {
  it("lands on the first unanswered mandatory step", () => {
    const state = run(initialFlowState(), {
      type: "hydrate",
      draft: answered({ emotionId: "", domainIds: [] }),
    })
    expect(state.step).toBe("emotion")
  })

  it("does not treat a skipped optional step as a gap", () => {
    // A draft with every mandatory answer but no photo, souls, collection or
    // glyph is complete: re-asking would undo the user's decision to skip.
    const state = run(initialFlowState(), { type: "hydrate", draft: answered() })
    expect(state.step).toBe("privacy")
    expect(firstGap(state)).toBe("privacy")
  })

  it("lands on name for a draft that holds only a photo", () => {
    const state = run(initialFlowState(), {
      type: "hydrate",
      draft: composerDefaults(),
    })
    expect(state.step).toBe("name")
  })
})
