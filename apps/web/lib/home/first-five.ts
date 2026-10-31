import type { FirstFiveTask } from "@/lib/types"

/**
 * Home's first five (#1384, spec D12; iOS `HomeChecklist`): five one-shot
 * achievements in Home's order, and the claimed blue pebble that ends the list.
 * A pure projection of the catalog and the unlocks; no client counts anything.
 */
export const FIRST_FIVE_ORDER: readonly FirstFiveTask[] = [
  "first-moment",
  "first-pebble",
  "first-collection",
  "first-soul",
  "first-glyph",
]

export const BLUE_PEBBLE = "blue-pebble"

export type FirstFiveState = "unavailable" | "inProgress" | "claimable" | "done"

export type FirstFive = {
  state: FirstFiveState
  done: Record<FirstFiveTask, boolean>
  count: number
}

/**
 * `catalogSlugs`: the catalog's active slugs. Unavailable when one of the six
 * rows is missing (an unlock is permanent, so a slug the user holds still
 * counts); done once the blue pebble is unlocked, whatever else (a data repair
 * may leave it alone); claimable when all five are and it is not.
 */
export function firstFiveState(catalogSlugs: ReadonlySet<string>, unlockedSlugs: ReadonlySet<string>): FirstFive {
  const done: Record<FirstFiveTask, boolean> = {
    "first-moment": unlockedSlugs.has("first-moment"),
    "first-pebble": unlockedSlugs.has("first-pebble"),
    "first-collection": unlockedSlugs.has("first-collection"),
    "first-soul": unlockedSlugs.has("first-soul"),
    "first-glyph": unlockedSlugs.has("first-glyph"),
  }
  const count = FIRST_FIVE_ORDER.filter((t) => done[t]).length
  const available = [...FIRST_FIVE_ORDER, BLUE_PEBBLE].every((slug) => catalogSlugs.has(slug) || unlockedSlugs.has(slug))
  const state: FirstFiveState = !available
    ? "unavailable"
    : unlockedSlugs.has(BLUE_PEBBLE)
      ? "done"
      : count === FIRST_FIVE_ORDER.length
        ? "claimable"
        : "inProgress"
  return { state, done, count }
}
