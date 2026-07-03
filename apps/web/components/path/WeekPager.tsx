"use client"

import { motion, useReducedMotion, type PanInfo } from "framer-motion"
import { useMediaQuery } from "@/lib/hooks/useMediaQuery"
import type { Soul } from "@/lib/types"
import { WeekPath } from "@/components/path/WeekPath"
import {
  weekIndex,
  type WeekRollEntry,
} from "@/lib/utils/week-roll-entries"

type WeekPagerProps = {
  entries: WeekRollEntry[]
  focused: Date
  souls: Soul[]
  onFocusChange: (weekStart: Date) => void
  onSelectPebble: (id: string) => void
}

export function WeekPager({
  entries,
  focused,
  souls,
  onFocusChange,
  onSelectPebble,
}: WeekPagerProps) {
  const focusedIndex = weekIndex(entries, focused)
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
        {entries.map((entry, i) => (
          <div key={entry.weekStartIso} className="h-full w-full shrink-0">
            {Math.abs(i - focusedIndex) <= 1 ? (
              <WeekPath
                entry={entry}
                souls={souls}
                isFocused={i === focusedIndex}
                onSelectPebble={onSelectPebble}
              />
            ) : null}
          </div>
        ))}
      </motion.div>
    </div>
  )
}
