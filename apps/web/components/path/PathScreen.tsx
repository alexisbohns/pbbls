"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import { useRouter } from "next/navigation"
import { Loader2 } from "lucide-react"
import { useTranslations } from "next-intl"
import type { Moment, Soul } from "@/lib/types"
import { StoneSprite } from "@/components/pebble/stone/StoneSprite"
import { WeekRoll } from "@/components/path/WeekRoll"
import { WeekPager } from "@/components/path/WeekPager"
import { PathBottomDock } from "@/components/path/PathBottomDock"
import { PebblePeek } from "@/components/path/PebblePeek"
import { cn } from "@/lib/utils"
import {
  buildWeekRollEntries,
  isoWeekKey,
  isoWeekNumber,
  isoWeekStart,
  nextFocusStep,
  type WeekRollEntry,
} from "@/lib/utils/week-roll-entries"

type PathScreenProps = {
  /** Bare and completed (#1050): a bare moment is a blank stone on the path. */
  moments: Moment[]
  souls: Soul[]
  loading: boolean
  /**
   * `?focus=<momentId>` (R14): open on the week of that moment once the store
   * holds it, then drop the parameter. Null or absent: today's week.
   */
  focusMomentId?: string | null
}

/**
 * Pick the entry whose `weekStart` is closest to `preferred`. On tie,
 * prefer the earlier week. Caller guarantees `entries.length > 0`.
 */
function closestEntry(entries: WeekRollEntry[], preferred: Date): WeekRollEntry {
  return entries.reduce((best, e) => {
    const dBest = Math.abs(best.weekStart.getTime() - preferred.getTime())
    const dE = Math.abs(e.weekStart.getTime() - preferred.getTime())
    if (dE < dBest) return e
    if (dE === dBest && e.weekStart.getTime() < best.weekStart.getTime()) return e
    return best
  }, entries[0])
}

export function PathScreen({ moments, souls, loading, focusMomentId = null }: PathScreenProps) {
  const t = useTranslations("path")
  const router = useRouter()
  // today is frozen at mount; the failure mode (user leaves the tab open past
  // midnight) is acceptable — navigation back to /path remounts the component.
  const today = useMemo(() => new Date(), [])
  const entries = useMemo(() => buildWeekRollEntries(moments, today), [moments, today])

  // Source of truth is the ISO key (e.g. "2026-W19"). Resolving to a real
  // entry at render time lets us fall back gracefully when a focused week
  // disappears (e.g. last moment of a past, non-current week deleted) —
  // no setState-during-render, no useEffect setState ping-pong.
  const [focusedKey, setFocusedKey] = useState<string>(() => isoWeekKey(today))
  // The read page in the peek, by MOMENT id (#1050).
  const [selectedMomentId, setSelectedMomentId] = useState<string | null>(null)

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

  const focusedEntry = entries.find((e) => e.weekStartIso === focusedKey)
    ?? (entries.length > 0 ? closestEntry(entries, isoWeekStart(today)) : undefined)
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
      const idx = entries.findIndex((entry) => entry.weekStartIso === resolvedFocusedKey)
      const nextIdx = e.key === "ArrowLeft" ? idx - 1 : idx + 1
      const target = entries[nextIdx]
      if (target) {
        e.preventDefault()
        setFocusedKey(target.weekStartIso)
      }
    }
    window.addEventListener("keydown", onKey)
    return () => window.removeEventListener("keydown", onKey)
  }, [entries, resolvedFocusedKey])

  const setFocusedFromDate = useCallback((date: Date) => {
    setFocusedKey(isoWeekKey(date))
  }, [])

  const handleClosePeek = useCallback(() => setSelectedMomentId(null), [])

  if (loading && moments.length === 0) {
    return (
      <div className="flex min-h-[60dvh] items-center justify-center">
        <Loader2 className="size-6 animate-spin text-muted-foreground" aria-label={t("loading")} />
      </div>
    )
  }

  return (
    <div
      className={cn(
        "spine-paper relative flex h-full w-full flex-col overflow-x-hidden pt-[var(--safe-area-top)] text-(--spine-ink)",
        // From 900 px the roll and the dock sit over the verso; the week
        // reserves their room (SpineWeek reads these).
        "spread:[--spine-verso-top:46px] spread:[--spine-verso-foot:calc(64px+var(--safe-area-bottom))]",
      )}
    >
      <WeekRoll
        entries={entries}
        focused={focusedWeekStart}
        onFocus={setFocusedFromDate}
        className="shrink-0 spread:spine-paper spread:absolute spread:top-(--safe-area-top) spread:left-(--spine-verso-x) spread:z-20 spread:w-(--spine-verso-w) spread:pr-12 spread:pl-9"
      />
      <p aria-live="polite" className="sr-only">
        {t("weekLabel", { week: focusedEntry?.isoWeek ?? isoWeekNumber(focusedWeekStart) })}
      </p>
      <div className="min-h-0 flex-1">
        <WeekPager
          entries={entries}
          focused={focusedWeekStart}
          today={today}
          souls={souls}
          onFocusChange={setFocusedFromDate}
          onOpen={setSelectedMomentId}
        />
      </div>
      {/* The stones' shared shapes and filters, once for every week the pager holds. */}
      <StoneSprite />
      <PathBottomDock className="spread:right-auto spread:left-(--spine-verso-x) spread:w-(--spine-verso-w)" />
      <PebblePeek
        momentId={selectedMomentId}
        onClose={handleClosePeek}
      />
    </div>
  )
}
