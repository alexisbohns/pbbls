"use client"

import { motion, useReducedMotion, type PanInfo } from "framer-motion"
import { type MutableRefObject } from "react"
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
  onCarvePebble: () => void
  scrollTargetRef: MutableRefObject<string | null>
}

export function WeekPager({
  entries,
  focused,
  souls,
  onFocusChange,
  onSelectPebble,
  onCarvePebble,
  scrollTargetRef,
}: WeekPagerProps) {
  const focusedIndex = weekIndex(entries, focused)
  const prefersReducedMotion = useReducedMotion()
  const isTouch = useMediaQuery("(pointer: coarse)")

  const handleDragEnd = (_: unknown, info: PanInfo) => {
    const width = typeof window !== "undefined" ? window.innerWidth : 0
    const threshold = width * 0.3
    if (info.offset.x < -threshold || info.velocity.x < -200) {
      const next = entries[focusedIndex + 1]
      if (next) onFocusChange(next.weekStart)
    } else if (info.offset.x > threshold || info.velocity.x > 200) {
      const prev = entries[focusedIndex - 1]
      if (prev) onFocusChange(prev.weekStart)
    }
  }

  return (
    <div className="overflow-hidden">
      <motion.div
        className="flex w-full"
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
          <div key={entry.weekStartIso} className="w-full shrink-0">
            {Math.abs(i - focusedIndex) <= 1 ? (
              <WeekPath
                entry={entry}
                souls={souls}
                isFocused={i === focusedIndex}
                onSelectPebble={onSelectPebble}
                onCarvePebble={onCarvePebble}
                scrollTargetRef={scrollTargetRef}
              />
            ) : null}
          </div>
        ))}
      </motion.div>
    </div>
  )
}
