import {
  composerDefaults,
  type ComposerState,
} from "@/components/record/draft-payload"
import type { PebbleSnap, Visibility } from "@/lib/types"
import {
  isOptionalStep,
  nextStep,
  previousStep,
  RECORD_STEPS,
  type RecordStep,
} from "./steps"

/**
 * The record flow's state machine — the draft under construction, the step the
 * user is on, and every interaction that changes either.
 *
 * Ported from `RecordFlowModel` (iOS, M58). Kept as a pure reducer rather than
 * spread across the step components for the same reason iOS kept it in one
 * object: it makes the interesting behavior — gating, back-preserves-answers,
 * skip-only-on-optional-steps, resume-to-first-gap, the name clamp — testable
 * with no rendering at all. Web drops the haptic half of that rationale (see
 * `RecordFlow`), and keeps the testable half.
 *
 * The draft is a `ComposerState`, the same projection the single-screen
 * composer uses, so `buildDraftPayload` / `applyDraftPayload` — and with them
 * the whole M47 draft contract — are shared rather than reimplemented.
 */

/**
 * Longest name the flow accepts. Front-end only: neither `pebbles.name` nor the
 * create payload constrains length, and nothing server-side enforces it.
 */
export const NAME_LIMIT = 40

export type RecordFlowState = {
  draft: ComposerState
  step: RecordStep
  /**
   * Mirrored from the container's photo state rather than derived from
   * `draft.pendingSnap`: a picked photo counts as answered while it is still
   * uploading, and the descriptor only exists once the upload lands.
   */
  hasPhoto: boolean
  /** True when `happenedAt` came from the photo's EXIF rather than from now. */
  seededFromPhoto: boolean
}

export function initialFlowState(draft?: ComposerState): RecordFlowState {
  return {
    draft: draft ?? composerDefaults(),
    step: "photo",
    hasPhoto: draft?.pendingSnap !== undefined,
    seededFromPhoto: false,
  }
}

export type RecordFlowAction =
  | { type: "advance" }
  | { type: "back" }
  | { type: "goTo"; step: RecordStep }
  | { type: "setName"; value: string }
  | { type: "setHappenedAt"; value: string }
  | { type: "applyCaptureDate"; value: string | null }
  | { type: "setPhoto"; snap: PebbleSnap | undefined; attached: boolean }
  | { type: "selectValence"; intensity: 1 | 2 | 3; valence: -1 | 0 | 1 }
  | { type: "selectEmotion"; id: string }
  | { type: "selectDomain"; id: string }
  | { type: "toggleSoul"; id: string }
  | { type: "toggleCollection"; id: string }
  | { type: "selectGlyph"; id: string | undefined }
  | { type: "selectVisibility"; value: Visibility }
  | { type: "hydrate"; draft: ComposerState }
  | { type: "published" }

/**
 * Whether a given step has been answered. Drives both the forward gate and the
 * optional steps' Skip / Done button label.
 *
 * Exhaustive with no `default`, deliberately: this is the single place that says
 * what "answered" means, and a `default` would silently treat a newly added step
 * as already answered.
 */
export function hasAnswer(step: RecordStep, state: RecordFlowState): boolean {
  switch (step) {
    // Nothing for the user to supply: `when` arrives seeded from the photo's
    // EXIF or from now, `valence` from the grid's centre cell (web's valence has
    // no empty state), `privacy` from 'secret', and `success` is terminal.
    case "when":
    case "valence":
    case "privacy":
    case "success":
      return true
    case "photo":
      return state.hasPhoto
    case "name":
      return state.draft.name.trim().length > 0
    case "emotion":
      return state.draft.emotionId !== ""
    case "domain":
      return state.draft.domainIds.length > 0
    case "souls":
      return state.draft.soulIds.length > 0
    case "collection":
      return state.draft.collectionIds.length > 0
    case "glyph":
      return state.draft.markId !== undefined
  }
}

/**
 * Whether the current step may be left. Optional steps are always satisfied:
 * passing one is the user saying "not this one", not an error.
 */
export function isAnswered(state: RecordFlowState): boolean {
  return isOptionalStep(state.step) || hasAnswer(state.step, state)
}

/** Skip while the optional step is empty, Done once it holds something. */
export function optionalButtonIsSkip(state: RecordFlowState): boolean {
  return !hasAnswer(state.step, state)
}

/**
 * The first mandatory step this draft has not answered — where a resumed draft
 * lands.
 *
 * Optional steps never count as gaps: skipping one is a legitimate answer, and
 * re-asking would silently undo the user's decision.
 */
export function firstGap(state: RecordFlowState): RecordStep {
  for (const candidate of RECORD_STEPS) {
    if (candidate === "success" || isOptionalStep(candidate)) continue
    if (!hasAnswer(candidate, state)) return candidate
  }
  return "privacy"
}

export function clampName(raw: string): string {
  return raw.slice(0, NAME_LIMIT)
}

/** A tile pick: commit the value and move on in one gesture. */
function commitAndAdvance(
  state: RecordFlowState,
  draft: ComposerState,
): RecordFlowState {
  const next = nextStep(state.step)
  return { ...state, draft, step: next ?? state.step }
}

export function recordFlowReducer(
  state: RecordFlowState,
  action: RecordFlowAction,
): RecordFlowState {
  switch (action.type) {
    case "advance": {
      if (state.step === "success") return state
      // A blocked advance is silent on web: the primary button is already
      // disabled, so the only way here is a keyboard submit on an unanswered
      // step, where nothing happening is the correct answer.
      if (!isAnswered(state)) return state
      const next = nextStep(state.step)
      return next ? { ...state, step: next } : state
    }

    case "back": {
      if (state.step === "success") return state
      const previous = previousStep(state.step)
      // Answers are untouched, so a step re-entered by going back shows what
      // the user already chose.
      return previous ? { ...state, step: previous } : state
    }

    case "goTo":
      return { ...state, step: action.step }

    case "setName":
      return {
        ...state,
        draft: { ...state.draft, name: clampName(action.value) },
      }

    case "setHappenedAt":
      // A hand-picked moment overrides the EXIF seed, so the "from your photo"
      // note stops claiming credit for a date the user chose.
      return {
        ...state,
        draft: { ...state.draft, happenedAt: action.value },
        seededFromPhoto: false,
      }

    case "applyCaptureDate":
      // No-op for a photo without a readable capture date, so `happenedAt`
      // stays at its default of now.
      if (!action.value) return state
      return {
        ...state,
        draft: { ...state.draft, happenedAt: action.value },
        seededFromPhoto: true,
      }

    case "setPhoto":
      return {
        ...state,
        draft: { ...state.draft, pendingSnap: action.snap },
        hasPhoto: action.attached,
      }

    // Valence commits in place instead of advancing (iOS #728): the grid is a
    // comparison of nine cells, and a tap that leaves the screen denies the
    // user the look at what they just chose next to the eight they did not.
    // The step's Continue button does the advancing.
    case "selectValence":
      return {
        ...state,
        draft: {
          ...state.draft,
          intensity: action.intensity,
          valence: action.valence,
        },
      }

    case "selectEmotion":
      return commitAndAdvance(state, { ...state.draft, emotionId: action.id })

    // The payload key stays plural so `domain_ids` is unchanged, but the flow
    // holds at most one — same contract as `DomainSheet`.
    case "selectDomain":
      return commitAndAdvance(state, { ...state.draft, domainIds: [action.id] })

    // Souls are multi-select, so a toggle never advances — the step's
    // Skip / Done button does that.
    case "toggleSoul": {
      const soulIds = state.draft.soulIds.includes(action.id)
        ? state.draft.soulIds.filter((id) => id !== action.id)
        : [...state.draft.soulIds, action.id]
      return { ...state, draft: { ...state.draft, soulIds } }
    }

    // Multi-select on web, unlike iOS's single collection: the web composer has
    // always allowed a pebble in several collections, and making the step
    // single-select to keep every step uniform would be a real capability loss.
    case "toggleCollection": {
      const collectionIds = state.draft.collectionIds.includes(action.id)
        ? state.draft.collectionIds.filter((id) => id !== action.id)
        : [...state.draft.collectionIds, action.id]
      return { ...state, draft: { ...state.draft, collectionIds } }
    }

    case "selectGlyph":
      return commitAndAdvance(state, { ...state.draft, markId: action.id })

    // Privacy selects without advancing: Publish is the step's action, and
    // silently publishing on a grade tap would be a trap.
    case "selectVisibility":
      return {
        ...state,
        draft: { ...state.draft, visibility: action.value },
      }

    case "hydrate": {
      const hydrated: RecordFlowState = {
        ...state,
        draft: action.draft,
        hasPhoto: action.draft.pendingSnap !== undefined,
      }
      return { ...hydrated, step: firstGap(hydrated) }
    }

    case "published":
      return { ...state, step: "success" }
  }
}
