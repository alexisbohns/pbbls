"use client"

import { motion, useReducedMotion } from "framer-motion"
import { useLookupMaps } from "@/lib/data/useLookupMaps"
import { useMarks } from "@/lib/data/useMarks"
import type { Soul } from "@/lib/types"
import { PathPebbleRow } from "@/components/path/PathPebbleRow"
import { PathEmptyState } from "@/components/path/PathEmptyState"
import type { WeekRollEntry } from "@/lib/utils/week-roll-entries"

type WeekPathProps = {
  entry: WeekRollEntry
  souls: Soul[]
  isFocused: boolean
  onSelectPebble: (id: string) => void
}

export function WeekPath({
  entry,
  souls,
  isFocused,
  onSelectPebble,
}: WeekPathProps) {
  const prefersReducedMotion = useReducedMotion()
  const { marks } = useMarks()
  const { markMap } = useLookupMaps(souls, marks)

  // Cascade key is derived from props: it changes when `isFocused` flips
  // to true (false→true transition forces a remount and plays the
  // stagger) or when the focused week's pebble count changes (so newly
  // created rows trigger a fresh cascade). When not focused the key is
  // stable on `weekStartIso` so we don't remount on unrelated re-renders.
  const cascadeKey = isFocused
    ? `${entry.weekStartIso}-focused-${entry.pebbles.length}`
    : `${entry.weekStartIso}-unfocused`

  if (entry.pebbles.length === 0) {
    return <PathEmptyState />
  }

  return (
    <motion.ol
      key={cascadeKey}
      className="flex h-full flex-col gap-1 overflow-y-auto p-4 pb-16"
      style={{
        maskImage: "linear-gradient(to bottom, black 0%, black 90%, transparent 100%)",
        WebkitMaskImage: "linear-gradient(to bottom, black 0%, black 90%, transparent 100%)",
      }}
      initial={isFocused ? "hidden" : "visible"}
      animate="visible"
      variants={{
        visible: { transition: { staggerChildren: prefersReducedMotion ? 0 : 0.08 } },
      }}
    >
      {entry.pebbles.map((pebble, i) => (
        <motion.li
          key={pebble.id}
          id={`pebble-${pebble.id}`}
          variants={{
            hidden: { opacity: 0, y: -4 },
            visible: {
              opacity: 1,
              y: 0,
              transition: { duration: prefersReducedMotion ? 0 : 0.25, ease: "easeOut" },
            },
          }}
        >
          <PathPebbleRow
            pebble={pebble}
            mark={pebble.mark_id ? markMap.get(pebble.mark_id) : undefined}
            positionIndex={i}
            onSelect={onSelectPebble}
          />
        </motion.li>
      ))}
    </motion.ol>
  )
}
