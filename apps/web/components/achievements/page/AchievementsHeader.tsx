"use client"

import { useAchievementsWords } from "@/lib/i18n/useAchievementsWords"

/**
 * The page's header (#1308, D1–D2): since the account began, and the days
 * kept, which is calendar age (the server's `days_kept`), never a streak. It
 * hides when the server sent no count. iOS `AchievementsHeader`.
 */
export function AchievementsHeader({ since, daysKept }: { since: string | null; daysKept: number | null }) {
  const words = useAchievementsWords()
  if (daysKept === null) return null
  return (
    <header className="px-1.5 pt-1.5 pb-5">
      <p className="sr-only">{words.a11y.header(daysKept, since)}</p>
      <div aria-hidden className="flex flex-col gap-1">
        {since ? <span className="font-spine-sc text-[11.5px] font-semibold tracking-[.2em] text-spine-ink-2">{words.since(since)}</span> : null}
        <span className="flex items-baseline gap-3.5">
          <span className="font-spine-office text-[78px] leading-[.84] font-[230] tracking-[-.025em] tabular-nums">{words.number(daysKept)}</span>
          <span className="font-spine-sc text-[15px] font-[560] tracking-[.16em] text-spine-ink-2">{words.daysUnit(daysKept)}</span>
        </span>
      </div>
    </header>
  )
}
