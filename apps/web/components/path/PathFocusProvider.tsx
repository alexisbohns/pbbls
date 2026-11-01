"use client"

import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react"
import { useRouter } from "next/navigation"
import type { Moment, Soul } from "@/lib/types"
import { PathFocusContext, PathReadingContext, type PathFocus } from "@/lib/hooks/usePathFocus"
import { SpinePressContext, useSpinePresses } from "@/lib/hooks/useSpinePresses"
import { focusedEntryOf, steppedKey, weekParamKey } from "@/lib/path/path-focus"
import { buildWeekRollEntries, isoWeekKey, isoWeekStart, nextFocusStep } from "@/lib/utils/week-roll-entries"

type PathFocusProviderProps = {
  /** Bare and completed (#1050): a bare moment is a blank stone on the path. */
  moments: Moment[]
  souls: Soul[]
  loading: boolean
  /**
   * `?focus=<momentId>` (R14): open on the week of that moment once the store
   * holds it, then drop the parameter. Null or absent: today's week.
   */
  focusMomentId?: string | null
  /**
   * `?week=` (#1384, spec D9): a Monday at UTC midnight. Opens on the closest
   * week the Path has once the store settles, then drops the parameter, the
   * way `focusMomentId` is consumed. `focusMomentId` wins when both are given.
   */
  focusWeek?: Date | null
  children: ReactNode
}

/**
 * The Path's focus, shared by its two halves (#1384, two-area spec D4). It
 * holds what `PathScreen` held: the focused week (by ISO key, resolved at
 * render so a vanished week falls back to the closest one), the one-time
 * `?focus=` and `?week=`, the arrow keys, the peek's moment, and the stones'
 * presses, so a contents row on one half presses its stone on the other.
 */
export function PathFocusProvider({
  moments,
  souls,
  loading,
  focusMomentId = null,
  focusWeek = null,
  children,
}: PathFocusProviderProps) {
  const router = useRouter()
  // today is frozen at mount; the failure mode (user leaves the tab open past
  // midnight) is acceptable — navigation back to /path remounts the provider.
  const today = useMemo(() => new Date(), [])
  const entries = useMemo(() => buildWeekRollEntries(moments, today), [moments, today])
  const [focusedKey, setFocusedKey] = useState<string>(() => isoWeekKey(today))
  const [openMomentId, setOpenMomentId] = useState<string | null>(null)
  const [readingDay, setReadingDay] = useState<number | undefined>(undefined)
  const presses = useSpinePresses()

  // R14: a `?focus=` moment pages to its week once, as soon as the store holds
  // it (a capture's reload may land after the first render). Adjusted during
  // render rather than in an effect, as the focused key above is. Once loading
  // settles the focus is consumed even if its moment never arrived, so a later
  // refresh cannot page the Path mid-browse. The URL is cleaned in the effect
  // below, which re-arms the focus for the next `?focus=`.
  const [appliedFocus, setAppliedFocus] = useState<string | null>(null)
  const focusStep = nextFocusStep(moments, focusMomentId, appliedFocus, loading)
  if (focusStep !== null) {
    setAppliedFocus(focusStep.consumed)
    if (focusStep.key !== null) setFocusedKey(focusStep.key)
  }
  useEffect(() => {
    if (focusMomentId !== null && focusMomentId === appliedFocus) router.replace("/path")
  }, [focusMomentId, appliedFocus, router])

  // #1384 D9: a `?week=` Monday pages to the closest week once the store has
  // settled, adjusted during render like the focus above; the URL is cleaned
  // below, which re-arms it for the next `?week=`.
  const weekParam = focusWeek ? focusWeek.toISOString().slice(0, 10) : null
  const [appliedWeek, setAppliedWeek] = useState<string | null>(null)
  if (weekParam === null && appliedWeek !== null) setAppliedWeek(null)
  if (weekParam !== null && weekParam !== appliedWeek && focusMomentId === null && !loading) {
    setAppliedWeek(weekParam)
    const key = focusWeek ? weekParamKey(focusWeek, entries) : null
    if (key !== null) setFocusedKey(key)
  }
  useEffect(() => {
    if (weekParam !== null && weekParam === appliedWeek) router.replace("/path")
  }, [weekParam, appliedWeek, router])

  const focusedEntry = focusedEntryOf(entries, focusedKey, today)
  const focusedWeekStart = focusedEntry?.weekStart ?? isoWeekStart(today)
  const resolvedFocusedKey = focusedEntry?.weekStartIso ?? focusedKey

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== "ArrowLeft" && e.key !== "ArrowRight") return
      const active = document.activeElement
      if (active instanceof HTMLElement) {
        const tag = active.tagName.toLowerCase()
        if (tag === "input" || tag === "textarea" || active.isContentEditable) return
      }
      const target = steppedKey(entries, resolvedFocusedKey, e.key === "ArrowLeft" ? -1 : 1)
      if (target !== null) {
        e.preventDefault()
        setFocusedKey(target)
      }
    }
    window.addEventListener("keydown", onKey)
    return () => window.removeEventListener("keydown", onKey)
  }, [entries, resolvedFocusedKey])

  const focusOn = useCallback((date: Date) => setFocusedKey(isoWeekKey(date)), [])
  const openMoment = useCallback((momentId: string) => setOpenMomentId(momentId), [])
  const closeMoment = useCallback(() => setOpenMomentId(null), [])

  const value = useMemo(
    (): PathFocus => ({
      today,
      entries,
      souls,
      waiting: loading && moments.length === 0,
      focusedEntry,
      focusedWeekStart,
      focusOn,
      openMomentId,
      openMoment,
      closeMoment,
    }),
    [today, entries, souls, loading, moments.length, focusedEntry, focusedWeekStart, focusOn, openMomentId, openMoment, closeMoment],
  )
  const reading = useMemo(() => ({ readingDay, setReadingDay }), [readingDay])

  return (
    <PathFocusContext value={value}>
      <PathReadingContext value={reading}>
        <SpinePressContext value={presses}>{children}</SpinePressContext>
      </PathReadingContext>
    </PathFocusContext>
  )
}
