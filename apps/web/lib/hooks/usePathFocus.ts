"use client"

import { createContext, useContext } from "react"
import type { Soul } from "@/lib/types"
import type { WeekRollEntry } from "@/lib/utils/week-roll-entries"

/**
 * The Path's shared state (#1384, two-area spec D4): the focused week, the
 * moment open in the peek and (apart) the day being read on the line. One provider
 * (`PathFocusProvider`) holds it, so the Path's two halves (the verso with the
 * opening, persons and contents; the recto with the line) can sit in
 * different places and stay on one week.
 */
export type PathFocus = {
  /** Frozen at the provider's mount (which week is current, which days are still to come). */
  today: Date
  entries: WeekRollEntry[]
  souls: Soul[]
  /** The store is still loading and holds no moment yet. */
  waiting: boolean
  focusedEntry: WeekRollEntry | undefined
  focusedWeekStart: Date
  focusOn: (date: Date) => void
  /** The moment open in the peek (by MOMENT id, #1050), or null. */
  openMomentId: string | null
  openMoment: (momentId: string) => void
  closeMoment: () => void
}

/**
 * The day crossing the reading line on the recto, published for a verso drawn
 * elsewhere (its persons and contents follow it). Its own context, so a day
 * crossing re-renders the verso, never the pager.
 */
export type PathReading = {
  readingDay: number | undefined
  setReadingDay: (day: number | undefined) => void
}

export const PathFocusContext = createContext<PathFocus | null>(null)
export const PathReadingContext = createContext<PathReading | null>(null)

export function usePathFocus(): PathFocus {
  const focus = useContext(PathFocusContext)
  if (!focus) throw new Error("usePathFocus must be used inside PathFocusProvider")
  return focus
}

export function usePathReading(): PathReading {
  const reading = useContext(PathReadingContext)
  if (!reading) throw new Error("usePathReading must be used inside PathFocusProvider")
  return reading
}
