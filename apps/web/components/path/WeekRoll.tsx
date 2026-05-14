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

export function WeekRoll({ entries, focused, onFocus }: WeekRollProps) {
  const t = useTranslations("path")
  const focusedIso = isoWeekKey(focused)
  const scrollRef = useRef<HTMLDivElement>(null)
  const isFirstRunRef = useRef(true)

  useEffect(() => {
    const container = scrollRef.current
    if (!container) return
    const ul = container.firstElementChild as HTMLElement | null

    const center = () => {
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
    }

    // First pass after layout settles.
    const handle = window.requestAnimationFrame(center)

    // The Rive canvases load asynchronously and can resize the row well after
    // mount; re-center whenever the inner list resizes.
    const ro = ul ? new ResizeObserver(center) : null
    if (ul && ro) ro.observe(ul)

    return () => {
      window.cancelAnimationFrame(handle)
      ro?.disconnect()
    }
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
        {entries.map((entry) => (
          <WeekRollCairn
            key={entry.weekStartIso}
            entry={entry}
            isFocused={entry.weekStartIso === focusedIso}
            onClick={() => onFocus(entry.weekStart)}
          />
        ))}
      </ul>
    </div>
  )
}
