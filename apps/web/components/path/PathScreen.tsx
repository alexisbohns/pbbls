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
  isoWeekStart,
  weekIndex,
} from "@/lib/utils/week-roll-entries"

type PathScreenProps = {
  pebbles: Pebble[]
  souls: Soul[]
  loading: boolean
}

export function PathScreen({ pebbles, souls, loading }: PathScreenProps) {
  const t = useTranslations("path")
  const today = useMemo(() => new Date(), [])
  const entries = useMemo(() => buildWeekRollEntries(pebbles, today), [pebbles, today])

  const [focusedWeekStart, setFocusedWeekStart] = useState<Date>(() => isoWeekStart(today))
  const [selectedPebbleId, setSelectedPebbleId] = useState<string | null>(null)
  const [editorExpanded, setEditorExpanded] = useState(false)
  const scrollTargetRef = useRef<string | null>(null)

  // If entries change and the focused week is no longer in them, fall back to
  // the closest remaining entry. Computed during render (not in an effect) so
  // we satisfy the React 19 set-state-in-effect rule.
  if (entries.length > 0 && weekIndex(entries, focusedWeekStart) < 0) {
    const target = entries.reduce((best, e) => {
      if (!best) return e
      const dBest = Math.abs(best.weekStart.getTime() - focusedWeekStart.getTime())
      const dE = Math.abs(e.weekStart.getTime() - focusedWeekStart.getTime())
      if (dE < dBest) return e
      if (dE === dBest && e.weekStart.getTime() < best.weekStart.getTime()) return e
      return best
    }, entries[0])
    setFocusedWeekStart(target.weekStart)
  }

  // Keyboard nav: ←/→ when no input is focused.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== "ArrowLeft" && e.key !== "ArrowRight") return
      const active = document.activeElement
      if (active instanceof HTMLElement) {
        const tag = active.tagName.toLowerCase()
        if (tag === "input" || tag === "textarea" || active.isContentEditable) return
      }
      const idx = weekIndex(entries, focusedWeekStart)
      const nextIdx = e.key === "ArrowLeft" ? idx - 1 : idx + 1
      const target = entries[nextIdx]
      if (target) {
        e.preventDefault()
        setFocusedWeekStart(target.weekStart)
      }
    }
    window.addEventListener("keydown", onKey)
    return () => window.removeEventListener("keydown", onKey)
  }, [entries, focusedWeekStart])

  const handlePebbleCreated = useCallback((id: string) => {
    scrollTargetRef.current = id
    setSelectedPebbleId(id)
  }, [])

  const handleCarvePebble = useCallback(() => setEditorExpanded(true), [])

  const handlePrev = useCallback(() => {
    const idx = weekIndex(entries, focusedWeekStart)
    const target = entries[idx - 1]
    if (target) setFocusedWeekStart(target.weekStart)
  }, [entries, focusedWeekStart])

  const handleNext = useCallback(() => {
    const idx = weekIndex(entries, focusedWeekStart)
    const target = entries[idx + 1]
    if (target) setFocusedWeekStart(target.weekStart)
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
          onFocus={setFocusedWeekStart}
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
          onFocusChange={setFocusedWeekStart}
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
        onClose={() => setSelectedPebbleId(null)}
      />
    </div>
  )
}
