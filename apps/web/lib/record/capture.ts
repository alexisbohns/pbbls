import { momentDisplayName } from "@/lib/data/moment-row"
import type { PebbleSnap } from "@/lib/types"
import { clampName } from "./name"
import { nextIn, previousIn } from "./steps"

/**
 * The Capture flow's state machine (#1050): photo · when · name, saved as a
 * moment through one `save_moment`. Ported from iOS `CaptureFlowModel`.
 *
 * Pure, so the order, the name clamp, the EXIF seeding rule and the two
 * questions the container keeps asking — "may this be saved?" and "is there
 * anything to lose?" — are tested without rendering. Capture has no autosave
 * (PD-W7) and no haptics (PD-W16).
 */

/**
 * `photo` before `when`, so the date step arrives seeded from the photo's EXIF.
 * No terminal step: a save leaves the flow. Must stay module-level (the
 * history hook stores indexes into it).
 */
export const CAPTURE_STEPS = ["photo", "when", "name"] as const

export type CaptureStep = (typeof CAPTURE_STEPS)[number]

/**
 * Where the attached photo is. `picking` is a pick being read before its
 * upload starts; `uploaded` is the only state that carries a snap.
 */
export type CapturePhoto = "none" | "picking" | "uploading" | "uploaded" | "failed"

export type CaptureState = {
  step: CaptureStep
  name: string
  /** ISO string; whole-second truncation happens in the payload builder. */
  happenedAt: string
  /** Set by the user's own edit on the when step: a photo's date never overrides it. */
  hasEditedDate: boolean
  /** True while the date on screen is the photo's. */
  seededFromPhoto: boolean
  photo: CapturePhoto
  snap: PebbleSnap | undefined
}

export type CaptureAction =
  | { type: "advance" }
  | { type: "back" }
  | { type: "goTo"; step: CaptureStep }
  | { type: "setName"; value: string }
  | { type: "setHappenedAt"; value: string }
  | { type: "applyCaptureDate"; value: string | null }
  | { type: "photo"; state: Exclude<CapturePhoto, "uploaded"> }
  | { type: "photo"; state: "uploaded"; snap: PebbleSnap }

export function initialCaptureState(now: Date = new Date()): CaptureState {
  return {
    step: "photo",
    name: "",
    happenedAt: now.toISOString(),
    hasEditedDate: false,
    seededFromPhoto: false,
    photo: "none",
    snap: undefined,
  }
}

/**
 * D3: a moment needs a name or a photo, and the photo must have landed: a
 * pick still being read or uploaded, or a failed upload, blocks the save
 * rather than silently saving without it.
 */
export function canSave(state: CaptureState, saving: boolean): boolean {
  if (saving) return false
  if (state.photo === "picking" || state.photo === "uploading" || state.photo === "failed") {
    return false
  }
  return momentDisplayName(state.name) !== null || state.photo === "uploaded"
}

/**
 * R2: whether ✕ has to ask. A picked photo counts from the moment it is
 * picked, whatever its upload is doing; a date alone does not count.
 */
export function hasAnythingToKeep(state: CaptureState): boolean {
  return momentDisplayName(state.name) !== null || state.photo !== "none"
}

/** `Skip` on the photo step until a photo is picked, then `Done`. */
export function photoButtonIsSkip(state: CaptureState): boolean {
  return state.photo === "none"
}

export function captureReducer(state: CaptureState, action: CaptureAction): CaptureState {
  switch (action.type) {
    // No gate: every step may be left (the photo is optional, the date is
    // seeded, and the name step's buttons are the save).
    case "advance": {
      const next = nextIn(CAPTURE_STEPS, state.step)
      return next ? { ...state, step: next } : state
    }

    case "back": {
      const previous = previousIn(CAPTURE_STEPS, state.step)
      return previous ? { ...state, step: previous } : state
    }

    case "goTo":
      return { ...state, step: action.step }

    case "setName":
      return { ...state, name: clampName(action.value) }

    case "setHappenedAt":
      return { ...state, happenedAt: action.value, hasEditedDate: true, seededFromPhoto: false }

    case "applyCaptureDate":
      // No readable capture date keeps "now"; a date the user chose wins.
      if (!action.value || state.hasEditedDate) return state
      return { ...state, happenedAt: action.value, seededFromPhoto: true }

    case "photo":
      return {
        ...state,
        photo: action.state,
        snap: action.state === "uploaded" ? action.snap : undefined,
      }
  }
}
