import type {
  AchievementYear,
  AchievementsDomains,
  AchievementsEmotions,
  AchievementsExplorer,
  CountingLadder,
  ExplorerRing,
} from "@/lib/types"

// Each card's margin ladder (#1308, the boards' `ladder`, `fracLadder`). A
// bar's fill is the server's progress, or a served count over its served
// whole (geometry); its done style is always a served fact: a ring listed in
// `rings_done`, a family with nothing served as not yet, `coverage == all`, a
// listed level, a month on the rim. A fill that reaches 1 never shows done on
// its own. Android `CardLadders`.

/** One bar: how far it is filled, and whether it wears the done style (solid ink). */
export type LadderBar = { fill: number; done: boolean }

const TWENTY_FIVE = 25

function fraction(n: number, of: number): number {
  return of <= 0 ? 0 : Math.min(1, Math.max(0, n / of))
}

export function explorerLadder(
  explorer: AchievementsExplorer,
  emotions: AchievementsEmotions | null,
  domains: AchievementsDomains | null,
): LadderBar[] {
  const ring = (r: ExplorerRing, fill: number): LadderBar => {
    const done = explorer.ringsDone.includes(r)
    return { fill: done ? 1 : fill, done }
  }
  return [
    ring("first_five", fraction(explorer.firstFive.filter((f) => f.done).length, explorer.firstFive.length)),
    ring("six_firsts", fraction(explorer.sixFirsts.filter((s) => s.done).length, explorer.sixFirsts.length)),
    ring("twenty_five", fraction(explorer.twentyFive?.count ?? 0, TWENTY_FIVE)),
    ring("domains", fraction(domains?.count ?? 0, domains?.of ?? 0)),
    ring("emotions", fraction(emotions?.count ?? 0, emotions?.of ?? 0)),
  ]
}

/** One bar per served family, in its order; done when the server lists nothing not yet met in it. */
export function emotionsLadder(emotions: AchievementsEmotions): LadderBar[] {
  return emotions.families.map((f) => ({
    fill: fraction(f.met.length, f.met.length + f.notYet.length),
    done: f.met.length > 0 && f.notYet.length === 0,
  }))
}

/** The domains' one tall bar: done when the server's coverage is `all`. */
export function domainsLadder(domains: AchievementsDomains): LadderBar {
  return { fill: fraction(domains.count, domains.of), done: domains.coverage === "all" }
}

/** One bar per ladder step: a listed level is done, the server's next fills by its progress. */
export function countingLadder(stack: CountingLadder): LadderBar[] {
  const reached = new Set(stack.levels.map((l) => l.threshold))
  return stack.ladder.map((t) => {
    if (reached.has(t)) return { fill: 1, done: true }
    if (stack.next?.threshold === t) return { fill: stack.next.progress, done: false }
    return { fill: 0, done: false }
  })
}

/** Each month at `(level + progress) / 2`: the inner disc half way, the rim the top and done. */
export function yearLadder(year: AchievementYear): LadderBar[] {
  return year.months.map((m) => ({ fill: (m.level + (m.level >= 2 ? 0 : m.progress)) / 2, done: m.level >= 2 }))
}
