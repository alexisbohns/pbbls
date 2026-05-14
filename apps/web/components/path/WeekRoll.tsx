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
    // Defer so the flex layout and Rive canvas sizing have settled before we
    // measure. Without this, the first run sees zero-width Rive canvases and
    // the scroll math snaps to the wrong column.
    const handle = window.requestAnimationFrame(() => {
      const el = container.querySelector<HTMLElement>(`[data-week="${focusedIso}"]`)
      if (!el) return
      const elRect = el.getBoundingClientRect()
      const containerRect = container.getBoundingClientRect()
      const elementLeftInContainer =
        elRect.left - containerRect.left + container.scrollLeft
      const desiredLeftInContainer =
        (container.clientWidth - elRect.width) / 2
      const targetScroll = elementLeftInContainer - desiredLeftInContainer
      container.scrollTo({
        left: targetScroll,
        behavior: isFirstRunRef.current ? "instant" : "smooth",
      })
      isFirstRunRef.current = false
    })
    return () => window.cancelAnimationFrame(handle)
  }, [focusedIso])

  return (
    <div
      ref={scrollRef}
      aria-label={t("weekRoll.label")}
      className="overflow-x-auto [&::-webkit-scrollbar]:hidden [-ms-overflow-style:none] [scrollbar-width:none]"
    >
      {/* paddingInline 50% lets scrollIntoView center the first or last item;
          arbitrary % via Tailwind sometimes drops the rule, so inline. */}
      <ul
        className="flex items-center gap-3"
        style={{ paddingInline: "50%" }}
      >
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
