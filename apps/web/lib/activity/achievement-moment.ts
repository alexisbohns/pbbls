import type { AchievementUnlockResult } from "@/lib/data/data-provider"
import type { Achievement } from "@/lib/types"

export type AchievementMoment = {
  results: AchievementUnlockResult[]
  catalog: Achievement[]
}

type Listener = (moment: AchievementMoment) => void

/**
 * The unlock moment's fire channel (D13). A module-level emitter rather than a
 * context because the six fire sites are plain functions called from data
 * hooks, not components — the same shape Sonner uses: `toast()` is a module
 * call and `<Toaster/>` is the mounted host. `<AchievementMomentHost/>` in the
 * root layout is the only subscriber.
 *
 * Deliberately fire-only: nothing here awaits or reads back, so a mutation
 * path never blocks on the celebration.
 */
const listeners = new Set<Listener>()

export function presentAchievementMoment(moment: AchievementMoment): void {
  if (moment.results.length === 0) return
  for (const listener of listeners) listener(moment)
}

export function subscribeAchievementMoment(listener: Listener): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}
