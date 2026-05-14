"use client"

import { useEffect, useRef } from "react"
import { useTranslations } from "next-intl"
import { WeekRollCairn } from "@/components/path/WeekRollCairn"
import {
  isoWeekKey,
  type WeekRollEntry,
} from "@/lib/utils/week-roll-entries"

type WeekRollProps = {
  entries: WeekRollEntry[]
  focused: Date
  onFocus: (weekStart: Date) => void
}

function opacityForDistance(d: number): number {
  if (d === 0) return 1
  if (d === 1) return 0.5
  if (d === 2) return 0.25
  return 0
}

export function WeekRoll({ entries, focused, onFocus }: WeekRollProps) {
  const t = useTranslations("path")
  const focusedIso = isoWeekKey(focused)
  const focusedIndex = entries.findIndex((e) => e.weekStartIso === focusedIso)
  const scrollRef = useRef<HTMLDivElement>(null)
  const isFirstRunRef = useRef(true)

  useEffect(() => {
    const container = scrollRef.current
    if (!container) return
    const el = container.querySelector<HTMLElement>(`[data-week="${focusedIso}"]`)
    if (!el) return
    el.scrollIntoView({
      behavior: isFirstRunRef.current ? "instant" : "smooth",
      inline: "center",
      block: "nearest",
    })
    isFirstRunRef.current = false
  }, [focusedIso])

  return (
    <div
      ref={scrollRef}
      aria-label={t("weekRoll.label")}
      className="overflow-x-auto [&::-webkit-scrollbar]:hidden [-ms-overflow-style:none] [scrollbar-width:none]"
    >
      <ul className="flex items-center gap-3 px-[50%]">
        {entries.map((entry, i) => (
          <WeekRollCairn
            key={entry.weekStartIso}
            entry={entry}
            isFocused={i === focusedIndex}
            opacity={opacityForDistance(Math.abs(i - focusedIndex))}
            onClick={() => onFocus(entry.weekStart)}
          />
        ))}
      </ul>
    </div>
  )
}
