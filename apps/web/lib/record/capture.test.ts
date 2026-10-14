import { describe, expect, it } from "vitest"
import type { PebbleSnap } from "@/lib/types"
import {
  CAPTURE_STEPS,
  canSave,
  captureReducer,
  hasAnythingToKeep,
  initialCaptureState,
  photoButtonIsSkip,
  type CaptureAction,
  type CaptureState,
} from "./capture"
import { NAME_LIMIT } from "./name"

const NOW = new Date("2026-10-14T09:30:12.345Z")

const SNAP: PebbleSnap = {
  id: "5f0c1d2e-3a4b-4c5d-8e6f-7a8b9c0d1e2f",
  storage_path: "907e7d76-00d3-4e53-82a3-4849bf0a9614/5f0c1d2e-3a4b-4c5d-8e6f-7a8b9c0d1e2f",
  sort_order: 0,
}

function run(state: CaptureState, ...actions: CaptureAction[]): CaptureState {
  return actions.reduce(captureReducer, state)
}

const initial = () => initialCaptureState(NOW)

describe("capture steps", () => {
  it("runs photo, when, name and nothing after", () => {
    expect(CAPTURE_STEPS).toEqual(["photo", "when", "name"])
    const a = run(initial(), { type: "advance" })
    expect(a.step).toBe("when")
    const b = run(a, { type: "advance" })
    expect(b.step).toBe("name")
    expect(run(b, { type: "advance" }).step).toBe("name")
  })

  it("advances past the photo step without a photo", () => {
    expect(run(initial(), { type: "advance" }).step).toBe("when")
  })

  it("does nothing on back from the photo step", () => {
    const state = initial()
    expect(run(state, { type: "back" })).toBe(state)
  })

  it("keeps the answers when going back", () => {
    const state = run(
      initial(),
      { type: "goTo", step: "name" },
      { type: "setName", value: "Picnic" },
      { type: "back" },
    )
    expect(state.step).toBe("when")
    expect(state.name).toBe("Picnic")
  })

  it("starts on the photo step, at now, with nothing attached", () => {
    expect(initial()).toEqual({
      step: "photo",
      name: "",
      happenedAt: NOW.toISOString(),
      hasEditedDate: false,
      seededFromPhoto: false,
      photo: "none",
      snap: undefined,
    })
  })
})

describe("name", () => {
  it("clamps at the name limit", () => {
    const long = "x".repeat(NAME_LIMIT + 5)
    const state = run(initial(), { type: "setName", value: long })
    expect(state.name).toHaveLength(40)
    expect(NAME_LIMIT).toBe(40)
  })
})

describe("when", () => {
  const exif = "2026-10-12T17:04:00.000Z"

  it("takes the photo's capture date before any manual edit", () => {
    const state = run(initial(), { type: "applyCaptureDate", value: exif })
    expect(state.happenedAt).toBe(exif)
    expect(state.seededFromPhoto).toBe(true)
  })

  it("ignores the photo's capture date once the user set the date", () => {
    const manual = "2026-10-10T08:00:00.000Z"
    const state = run(
      initial(),
      { type: "setHappenedAt", value: manual },
      { type: "applyCaptureDate", value: exif },
    )
    expect(state.happenedAt).toBe(manual)
    expect(state.hasEditedDate).toBe(true)
    expect(state.seededFromPhoto).toBe(false)
  })

  it("keeps now for a photo without a readable date", () => {
    const state = run(initial(), { type: "applyCaptureDate", value: null })
    expect(state.happenedAt).toBe(NOW.toISOString())
    expect(state.seededFromPhoto).toBe(false)
  })

  it("keeps the photo's date but drops the credit when the photo is removed", () => {
    const state = run(
      initial(),
      { type: "photo", state: "uploading" },
      { type: "applyCaptureDate", value: exif },
      { type: "photo", state: "uploaded", snap: SNAP },
      { type: "photo", state: "none" },
    )
    expect(state.happenedAt).toBe(exif)
    expect(state.seededFromPhoto).toBe(false)
  })

  it("keeps the previous photo's date but drops the credit on a re-pick without one", () => {
    const state = run(
      initial(),
      { type: "applyCaptureDate", value: exif },
      { type: "photo", state: "uploading" },
      { type: "applyCaptureDate", value: null },
    )
    expect(state.happenedAt).toBe(exif)
    expect(state.seededFromPhoto).toBe(false)
  })

  it("stops crediting the photo once the user edits the date", () => {
    const state = run(
      initial(),
      { type: "applyCaptureDate", value: exif },
      { type: "setHappenedAt", value: "2026-10-11T08:00:00.000Z" },
    )
    expect(state.seededFromPhoto).toBe(false)
  })
})

describe("photo", () => {
  it("holds a snap only once uploaded", () => {
    const uploaded = run(initial(), { type: "photo", state: "uploaded", snap: SNAP })
    expect(uploaded.snap).toEqual(SNAP)
    const repicked = run(uploaded, { type: "photo", state: "uploading" })
    expect(repicked.snap).toBeUndefined()
    const removed = run(uploaded, { type: "photo", state: "none" })
    expect(removed.snap).toBeUndefined()
  })

  it("says Skip until a photo is picked, then Done", () => {
    expect(photoButtonIsSkip(initial())).toBe(true)
    expect(photoButtonIsSkip(run(initial(), { type: "photo", state: "uploading" }))).toBe(false)
  })
})

describe("canSave", () => {
  const named = () => run(initial(), { type: "setName", value: "Picnic" })

  it("is false for a blank name and no photo", () => {
    expect(canSave(initial(), false)).toBe(false)
    expect(canSave(run(initial(), { type: "setName", value: "   " }), false)).toBe(false)
  })

  it("is true for a name and no photo", () => {
    expect(canSave(named(), false)).toBe(true)
  })

  it("is true for a photo alone only once it is uploaded", () => {
    expect(canSave(run(initial(), { type: "photo", state: "uploaded", snap: SNAP }), false)).toBe(true)
  })

  it("is false while the photo is picking, uploading or failed, even with a name", () => {
    for (const photo of ["picking", "uploading", "failed"] as const) {
      expect(canSave(run(initial(), { type: "photo", state: photo }), false)).toBe(false)
      expect(canSave(run(named(), { type: "photo", state: photo }), false)).toBe(false)
    }
  })

  it("is false while saving", () => {
    expect(canSave(named(), true)).toBe(false)
  })
})

describe("hasAnythingToKeep", () => {
  it("is false initially", () => {
    expect(hasAnythingToKeep(initial())).toBe(false)
  })

  it("is false for a date edit or a blank name alone", () => {
    expect(
      hasAnythingToKeep(
        run(
          initial(),
          { type: "setHappenedAt", value: "2026-10-10T08:00:00.000Z" },
          { type: "setName", value: "  " },
        ),
      ),
    ).toBe(false)
  })

  it("is true with only a picked photo, whatever its upload is doing", () => {
    for (const photo of ["picking", "uploading", "failed"] as const) {
      expect(hasAnythingToKeep(run(initial(), { type: "photo", state: photo }))).toBe(true)
    }
    expect(hasAnythingToKeep(run(initial(), { type: "photo", state: "uploaded", snap: SNAP }))).toBe(true)
  })

  it("is true with a name", () => {
    expect(hasAnythingToKeep(run(initial(), { type: "setName", value: "Picnic" }))).toBe(true)
  })
})
