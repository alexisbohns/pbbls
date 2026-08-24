"use client"

import { useCallback, useEffect, useRef } from "react"
import { stepIndex, RECORD_STEPS, type RecordStep } from "@/lib/record/steps"

/** Marker on `history.state` identifying an entry this flow pushed. */
const STEP_KEY = "pbblsRecordStep"

/**
 * Mirrors the flow's current step into browser history, one entry per step
 * reached, so the Back button walks the flow instead of leaving the composer.
 *
 * iOS gets this free from the navigation stack. On the web, Back leaving the
 * composer mid-flow would be the single worst regression in this port: ten
 * screens of answers discarded by the one gesture every user reaches for.
 *
 * The rule is one-directional so the two never fight: React state is the source
 * of truth, forward moves push an entry, and every backward move — the in-flow
 * chevron included, via `goBack()` — goes through `history.back()` and lands
 * here as a `popstate`. Entries carry no URL change: a `?step=` in the address
 * bar would be stale the moment the page is reloaded, since the draft itself
 * lives in the autosave snapshot rather than in the URL.
 */
export function useRecordFlowHistory(
  step: RecordStep,
  onPopTo: (step: RecordStep) => void,
) {
  const syncedRef = useRef<number | null>(null)

  useEffect(() => {
    const onPopState = (event: PopStateEvent) => {
      const state = event.state as Record<string, unknown> | null
      const index = state?.[STEP_KEY]
      // No marker means we popped past the flow's first entry, onto whatever
      // preceded /record. That is a route change and Next's router owns it.
      if (typeof index !== "number") return
      const popped = RECORD_STEPS[index]
      if (!popped) return
      syncedRef.current = index
      onPopTo(popped)
    }
    window.addEventListener("popstate", onPopState)
    return () => window.removeEventListener("popstate", onPopState)
  }, [onPopTo])

  useEffect(() => {
    const index = stepIndex(step)

    // Claim the entry the flow opened on, so popping back to it is recognised
    // as step 0 rather than as leaving. Spread the existing state: Next's
    // router keeps its own tree there and clobbering it breaks navigation.
    if (syncedRef.current === null) {
      window.history.replaceState(
        { ...window.history.state, [STEP_KEY]: index },
        "",
      )
      syncedRef.current = index
      return
    }

    // One entry per step reached, so Back is one step back — never a jump over
    // steps the user answered on the way in (which is what a resumed draft
    // landing deep in the flow would otherwise produce).
    for (let next = syncedRef.current + 1; next <= index; next++) {
      window.history.pushState({ ...window.history.state, [STEP_KEY]: next }, "")
    }
    syncedRef.current = index
  }, [step])

  /**
   * The in-flow back gesture. Routed through history rather than straight into
   * the reducer so the entry stack and the step never disagree — otherwise the
   * chevron would leave an orphan entry and the next Back would appear to do
   * nothing.
   */
  const goBack = useCallback(() => {
    window.history.back()
  }, [])

  return { goBack }
}
