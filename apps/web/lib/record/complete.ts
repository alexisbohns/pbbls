import type { CompleteDraft } from "@/lib/data/moment-payloads"
import type { Intensity, Positiveness, Visibility } from "@/lib/types"
import { nextIn, previousIn, COMPLETE_STEPS, type CompleteStep } from "./steps"

/**
 * The Complete flow's state machine (#1050): the answers that turn a moment
 * into the caller's pebble, the step the user is on, and every interaction
 * that changes either.
 *
 * Ported from `CompleteFlowModel` (iOS). Pure, like the record flow's reducer,
 * so gating, back-preserves-answers, the Skip / Done label and resume-to-first-
 * gap are tested without rendering. Web has no haptics (PD-W16).
 *
 * `CompleteDraft` is defined beside the payload builder that consumes it and
 * re-exported here, where the flow owns it.
 */
export type { CompleteDraft }

export type CompleteFlowState = { draft: CompleteDraft; step: CompleteStep }

export type CompleteFlowAction =
  | { type: "advance" }
  | { type: "back" }
  | { type: "goTo"; step: CompleteStep }
  | { type: "selectValence"; intensity: Intensity; positiveness: Positiveness }
  | { type: "selectEmotion"; id: string }
  | { type: "selectDomain"; id: string }
  | { type: "toggleSoul"; id: string }
  | { type: "selectCollection"; id: string | null }
  | { type: "selectVisibility"; value: Visibility }
  | { type: "hydrate"; draft: CompleteDraft }
  | { type: "completed" }

/**
 * Nothing chosen yet. The valence seeds neutral-medium so the fan has a stone
 * under the finger from the first frame, but the seed is not an answer until a
 * pick or Continue confirms it (iOS `isValenceConfirmed`).
 */
export function completeDefaults(): CompleteDraft {
  return {
    intensity: 2,
    positiveness: 0,
    valenceConfirmed: false,
    emotionId: null,
    domainIds: [],
    soulIds: [],
    collectionIds: [],
    visibility: "secret",
  }
}

export function initialCompleteState(draft?: CompleteDraft): CompleteFlowState {
  return { draft: draft ?? completeDefaults(), step: "valence" }
}

/** Steps the user may pass without answering. */
export function isCompleteOptionalStep(step: CompleteStep): boolean {
  return step === "souls" || step === "collection"
}

/**
 * Whether a step has been answered. Exhaustive with no `default`: this is the
 * one place that says what "answered" means.
 */
export function hasCompleteAnswer(step: CompleteStep, draft: CompleteDraft): boolean {
  switch (step) {
    case "privacy":
    case "success":
      return true
    case "valence":
      return draft.valenceConfirmed
    case "emotion":
      return draft.emotionId !== null
    case "domain":
      return draft.domainIds.length > 0
    case "souls":
      return draft.soulIds.length > 0
    case "collection":
      return draft.collectionIds.length > 0
  }
}

/** Skip while the optional step is empty, Done once it holds something. */
export function optionalButtonIsSkip(state: CompleteFlowState): boolean {
  return !hasCompleteAnswer(state.step, state.draft)
}

/** What `complete_pebble` needs: a confirmed valence, an emotion, exactly one domain. */
export function isDraftComplete(draft: CompleteDraft): boolean {
  return draft.valenceConfirmed && draft.emotionId !== null && draft.domainIds.length === 1
}

/**
 * Where a restored draft lands: the first mandatory step it has not answered,
 * else privacy. Optional steps never count as gaps — skipping one is an answer.
 */
export function firstGap(state: CompleteFlowState): CompleteStep {
  for (const candidate of COMPLETE_STEPS) {
    if (candidate === "success" || candidate === "privacy" || isCompleteOptionalStep(candidate)) continue
    if (!hasCompleteAnswer(candidate, state.draft)) return candidate
  }
  return "privacy"
}

/** The next step, never `success`: only a completed write reaches it. */
function forward(step: CompleteStep): CompleteStep {
  const next = nextIn(COMPLETE_STEPS, step)
  return next === null || next === "success" ? step : next
}

/** A tile pick: commit the value and move on in one gesture. */
function commitAndAdvance(state: CompleteFlowState, draft: CompleteDraft): CompleteFlowState {
  return { draft, step: forward(state.step) }
}

export function completeFlowReducer(
  state: CompleteFlowState,
  action: CompleteFlowAction,
): CompleteFlowState {
  switch (action.type) {
    case "advance": {
      if (state.step === "success" || state.step === "privacy") return state
      // Continue on valence confirms the seed the user is looking at.
      if (state.step === "valence") {
        return { draft: { ...state.draft, valenceConfirmed: true }, step: forward(state.step) }
      }
      // A blocked advance is silent: the only unanswered steps with no button
      // are the tile steps, where the pick is the advance.
      if (!isCompleteOptionalStep(state.step) && !hasCompleteAnswer(state.step, state.draft)) return state
      return { ...state, step: forward(state.step) }
    }

    case "back": {
      // The pebble exists once on success; walking back would offer a second write.
      if (state.step === "success") return state
      const previous = previousIn(COMPLETE_STEPS, state.step)
      return previous ? { ...state, step: previous } : state
    }

    case "goTo":
      return { ...state, step: action.step }

    // Commits in place (iOS #728): the fan is a comparison, and Continue advances.
    case "selectValence":
      return {
        ...state,
        draft: {
          ...state.draft,
          intensity: action.intensity,
          positiveness: action.positiveness,
          valenceConfirmed: true,
        },
      }

    case "selectEmotion":
      return commitAndAdvance(state, { ...state.draft, emotionId: action.id })

    // Exactly one domain (R13); the key stays plural for the payload.
    case "selectDomain":
      return commitAndAdvance(state, { ...state.draft, domainIds: [action.id] })

    // Multi-select: a toggle never advances, the Skip / Done button does.
    case "toggleSoul": {
      const soulIds = state.draft.soulIds.includes(action.id)
        ? state.draft.soulIds.filter((id) => id !== action.id)
        : [...state.draft.soulIds, action.id]
      return { ...state, draft: { ...state.draft, soulIds } }
    }

    // Zero or one collection (R13, PD-W11): a pick commits and advances, and
    // `null` is the "None" row.
    case "selectCollection":
      return commitAndAdvance(state, {
        ...state.draft,
        collectionIds: action.id === null ? [] : [action.id],
      })

    // Selects without advancing: the step's action is the write.
    case "selectVisibility":
      return { ...state, draft: { ...state.draft, visibility: action.value } }

    case "hydrate": {
      const hydrated: CompleteFlowState = { ...state, draft: action.draft }
      return { ...hydrated, step: firstGap(hydrated) }
    }

    case "completed":
      return { ...state, step: "success" }
  }
}
