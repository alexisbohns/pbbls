"use client"

import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { Loader2 } from "lucide-react"
import { useTranslations } from "next-intl"
import type { Pebble, Soul } from "@/lib/types"
import { WeekRoll } from "@/components/path/WeekRoll"
import { WeekHeader } from "@/components/path/WeekHeader"
import { WeekPager } from "@/components/path/WeekPager"
import { PathBottomDock } from "@/components/path/PathBottomDock"
import { PebblePeek } from "@/components/path/PebblePeek"
import {
  buildWeekRollEntries,
  isoWeekKey,
  isoWeekStart,
  weekIndex,
  type WeekRollEntry,
} from "@/lib/utils/week-roll-entries"

type PathScreenProps = {
  pebbles: Pebble[]
  souls: Soul[]
  loading: boolean
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

export function PathScreen({ pebbles, souls, loading }: PathScreenProps) {
  const t = useTranslations("path")
  // today is frozen at mount; the failure mode (user leaves the tab open past
  // midnight) is acceptable — navigation back to /path remounts the component.
  const today = useMemo(() => new Date(), [])
  const entries = useMemo(() => buildWeekRollEntries(pebbles, today), [pebbles, today])

  // Source of truth is the ISO key (e.g. "2026-W19"). Resolving to a real
  // entry at render time lets us fall back gracefully when a focused week
  // disappears (e.g. last pebble of a past, non-current week deleted) —
  // no setState-during-render, no useEffect setState ping-pong.
  const [focusedKey, setFocusedKey] = useState<string>(() => isoWeekKey(today))
  const [selectedPebbleId, setSelectedPebbleId] = useState<string | null>(null)
  const [editorExpanded, setEditorExpanded] = useState(false)
  const scrollTargetRef = useRef<string | null>(null)

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

  const handlePebbleCreated = useCallback((pebble: Pebble) => {
    // If the new pebble landed in a different week than the focused one,
    // jump focus to that week so the user sees their just-created row.
    const pebbleWeek = isoWeekKey(new Date(pebble.happened_at))
    if (pebbleWeek !== focusedKey) setFocusedKey(pebbleWeek)
    scrollTargetRef.current = pebble.id
    setSelectedPebbleId(pebble.id)
  }, [focusedKey])

  const handleCarvePebble = useCallback(() => setEditorExpanded(true), [])

  const handleClosePeek = useCallback(() => setSelectedPebbleId(null), [])

  const handlePrev = useCallback(() => {
    const idx = weekIndex(entries, focusedWeekStart)
    const target = entries[idx - 1]
    if (target) setFocusedKey(target.weekStartIso)
  }, [entries, focusedWeekStart])

  const handleNext = useCallback(() => {
    const idx = weekIndex(entries, focusedWeekStart)
    const target = entries[idx + 1]
    if (target) setFocusedKey(target.weekStartIso)
  }, [entries, focusedWeekStart])

  if (loading && pebbles.length === 0) {
    return (
      <div className="flex min-h-[60dvh] items-center justify-center">
        <Loader2 className="size-6 animate-spin text-muted-foreground" aria-label={t("loading")} />
      </div>
    )
  }

  return (
    <div className="mx-auto flex h-[100dvh] max-w-md flex-col">
      <div className="px-4 pt-4">
        <WeekRoll
          entries={entries}
          focused={focusedWeekStart}
          onFocus={setFocusedFromDate}
        />
      </div>
      <div className="px-4 pt-3">
        <WeekHeader
          entries={entries}
          focused={focusedWeekStart}
          today={today}
          onPrev={handlePrev}
          onNext={handleNext}
        />
      </div>
      <div className="min-h-0 flex-1 pt-3">
        <WeekPager
          entries={entries}
          focused={focusedWeekStart}
          souls={souls}
          onFocusChange={setFocusedFromDate}
          onSelectPebble={setSelectedPebbleId}
          onCarvePebble={handleCarvePebble}
          scrollTargetRef={scrollTargetRef}
        />
      </div>
      <PathBottomDock
        editorExpanded={editorExpanded}
        onEditorExpandedChange={setEditorExpanded}
        onPebbleCreated={handlePebbleCreated}
      />
      <PebblePeek
        pebbleId={selectedPebbleId}
        onClose={handleClosePeek}
      />
    </div>
  )
}
