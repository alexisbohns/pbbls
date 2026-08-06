/**
 * Helpers for feeds that settle independently.
 *
 * The Lab loads five things at once. Four are content (announcements,
 * changelog, initiatives, backlog); the fifth, the reactions lookup, is
 * enrichment. Under a single `Promise.all` + `try/catch`, any one rejection set
 * the page's error state and `LabFeed` rendered nothing but `feedError` —
 * including the sections that had loaded fine. #439 is a worked example: one
 * missing column blanked the entire web Lab.
 *
 * iOS got per-feed isolation in #420 and Android inherited it in #596; this is
 * the same contract for web. A fullscreen error is reserved for the case where
 * every content feed failed, and the reactions lookup never counts toward it.
 *
 * Deliberately dependency-free so it is unit-testable: the hook it serves
 * imports React and the Supabase client, and the node-environment Vitest setup
 * resolves neither.
 */

/**
 * A feed's value when it settled, or `fallback` when it rejected. Failures are
 * logged and swallowed — the web analog of iOS `LabView.logFetchFailure`, where
 * a failing feed is an isolated, recorded event rather than a page-level one.
 */
export function settledOr<T>(result: PromiseSettledResult<T>, fallback: T, scope: string): T {
  if (result.status === "fulfilled") return result.value
  console.error(`[${scope}] failed to load`, result.reason)
  return fallback
}

/**
 * The error to surface when EVERY content feed failed, and `null` when even one
 * succeeded — a page showing three of its four sections is still a page.
 *
 * Only content results belong here. Passing the reactions lookup in would let a
 * lost upvote state blank content that loaded perfectly well, which is the bug
 * this module exists to prevent.
 */
export function allFailedError(results: PromiseSettledResult<unknown>[], message: string): Error | null {
  if (results.length === 0) return null
  if (!results.every((result) => result.status === "rejected")) return null
  const first = results[0]
  const reason = first.status === "rejected" ? first.reason : undefined
  return reason instanceof Error ? reason : new Error(message)
}
