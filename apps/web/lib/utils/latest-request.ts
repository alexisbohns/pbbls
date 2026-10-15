/**
 * Tracks which of several overlapping async requests is the latest, so a
 * result that lands after a newer request started (or after the work was
 * abandoned) can be told apart and discarded instead of applied.
 *
 * `begin()` hands out a token for a new request and makes every earlier token
 * stale; `invalidate()` makes every token handed out so far stale without
 * starting a new one (a Remove, a Discard, an unmount).
 */
export type LatestRequest = {
  begin: () => number
  isLatest: (token: number) => boolean
  invalidate: () => void
}

export function createLatestRequest(): LatestRequest {
  let current = 0
  return {
    begin: () => ++current,
    isLatest: (token) => token === current,
    invalidate: () => {
      current += 1
    },
  }
}
