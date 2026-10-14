"use client"

import { useEffect, useState } from "react"
import { motion, useReducedMotion, type PanInfo } from "framer-motion"
import { SpineWeek } from "@/components/path/spine/SpineWeek"
import { useLookupMaps } from "@/lib/data/useLookupMaps"
import { useMarks } from "@/lib/data/useMarks"
import { useMediaQuery } from "@/lib/hooks/useMediaQuery"
import type { Soul } from "@/lib/types"
import {
  weekIndex,
  type WeekRollEntry,
} from "@/lib/utils/week-roll-entries"

type WeekPagerProps = {
  entries: WeekRollEntry[]
  focused: Date
  /** Frozen at the Path's mount: which week is current, which days are still to come. */
  today: Date
  souls: Soul[]
  onFocusChange: (weekStart: Date) => void
  /** Opens the read page, with the MOMENT id (#1050). */
  onOpen: (momentId: string) => void
}

export function WeekPager({
  entries,
  focused,
  today,
  souls,
  onFocusChange,
  onOpen,
}: WeekPagerProps) {
  const focusedIndex = weekIndex(entries, focused)
  const { marks } = useMarks()
  const { soulMap, markMap } = useLookupMaps(souls, marks)
  // The neighbours (±1, kept for paging) render once the focused week has
  // painted and the main thread is idle (spec §Performance).
  const [neighbours, setNeighbours] = useState(false)
  useEffect(() => {
    if (typeof window.requestIdleCallback === "function") {
      const handle = window.requestIdleCallback(() => setNeighbours(true), { timeout: 1500 })
      return () => window.cancelIdleCallback(handle)
    }
    // Safari has no requestIdleCallback.
    const timer = setTimeout(() => setNeighbours(true), 300)
    return () => clearTimeout(timer)
  }, [])
  const prefersReducedMotion = useReducedMotion()
  const isTouch = useMediaQuery("(pointer: coarse)")

  const handleDragEnd = (_: unknown, info: PanInfo) => {
    // Ignore the gesture entirely when the user moved more vertically than
    // horizontally — that's a scroll, not a swipe. Even with
    // dragDirectionLock, Framer can briefly register horizontal pan during a
    // diagonal scroll; this guard rejects anything that's not clearly
    // sideways intent.
    if (Math.abs(info.offset.y) >= Math.abs(info.offset.x)) return

    const width = typeof window !== "undefined" ? window.innerWidth : 0
    const offsetThreshold = width * 0.4
    const velocityThreshold = 600
    const goNext =
      info.offset.x < -offsetThreshold || info.velocity.x < -velocityThreshold
    const goPrev =
      info.offset.x > offsetThreshold || info.velocity.x > velocityThreshold

    if (goNext) {
      const next = entries[focusedIndex + 1]
      if (next) onFocusChange(next.weekStart)
    } else if (goPrev) {
      const prev = entries[focusedIndex - 1]
      if (prev) onFocusChange(prev.weekStart)
    }
  }

  return (
    <div className="h-full w-full overflow-hidden">
      <motion.div
        className="flex h-full w-full"
        animate={{ x: `-${focusedIndex * 100}%` }}
        transition={{
          duration: prefersReducedMotion ? 0 : 0.3,
          ease: [0.32, 0.72, 0, 1],
        }}
        drag={isTouch ? "x" : false}
        dragDirectionLock
        dragConstraints={{ left: 0, right: 0 }}
        dragElastic={0.2}
        onDragEnd={handleDragEnd}
      >
        {entries.map((entry, i) => {
          const distance = Math.abs(i - focusedIndex)
          const previous = entries[i - 1]
          const next = entries[i + 1]
          return (
            // Each week is its own vertical scroll: the spread's verso sticks
            // to the top of it, as tall as the page.
            <div
              key={entry.weekStartIso}
              className="h-full w-full shrink-0 overflow-x-hidden overflow-y-auto overscroll-y-contain [container-type:size] [--spine-page-h:100cqh]"
            >
              {distance === 0 || (distance === 1 && neighbours) ? (
                <SpineWeek
                  entry={entry}
                  today={today}
                  soulMap={soulMap}
                  markMap={markMap}
                  onOpen={onOpen}
                  onPreviousWeek={previous ? () => onFocusChange(previous.weekStart) : undefined}
                  onNextWeek={next ? () => onFocusChange(next.weekStart) : undefined}
                />
              ) : null}
            </div>
          )
        })}
      </motion.div>
    </div>
  )
}
