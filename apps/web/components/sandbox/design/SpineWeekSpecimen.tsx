"use client"

import { useMemo, useState } from "react"
import { SpineWeek } from "@/components/path/spine/SpineWeek"
import { Button } from "@/components/ui/button"
import {
  SPINE_FIXTURE_MARKS,
  SPINE_FIXTURE_PEBBLES,
  SPINE_FIXTURE_SOULS,
  SPINE_FIXTURE_TODAY,
} from "@/lib/seed/spine-fixture-week"
import { buildWeekRollEntries } from "@/lib/utils/week-roll-entries"
import { Specimen } from "./Specimen"

const SOUL_MAP = new Map(SPINE_FIXTURE_SOULS.map((s) => [s.id, s]))
const MARK_MAP = new Map(SPINE_FIXTURE_MARKS.map((m) => [m.id, m]))

/** The fixture read a week later, so week 41 is behind us and has an ending. */
const A_WEEK_LATER = new Date(2026, 9, 19, 9, 0)

/**
 * One week of the Spine over the brief's fixture week (#1052), through the
 * pipeline the Path will use: `buildWeekRollEntries` → `SpineWeek`. Read it as
 * the current week (it asks for a pebble) or a week later (it ends).
 */
export function SpineWeekSpecimen() {
  const [current, setCurrent] = useState(false)
  const [said, setSaid] = useState<string | null>(null)
  const today = current ? SPINE_FIXTURE_TODAY : A_WEEK_LATER

  const entry = useMemo(() => {
    const found = buildWeekRollEntries(SPINE_FIXTURE_PEBBLES, today).find((e) => e.weekStartIso === "2026-W41")
    if (!found) throw new Error("[spine fixture] week 41 is missing")
    return found
  }, [today])

  const open = (id: string) => setSaid(`Opens: ${SPINE_FIXTURE_PEBBLES.find((p) => p.id === id)?.name || "(untitled)"}`)

  return (
    <Specimen
      id="spine-week"
      note="The brief's week of 5–11 October, plus a still Friday, an uncarved stone, a fourteen-person picnic and an untitled photo. The persons and the contents follow the day being read; a contents link ripples its stone without opening it. Phone geometry; the spread comes with Part 5."
      className="block p-0"
    >
      <div className="flex gap-2 p-3">
        <Button size="sm" variant={current ? "outline" : "default"} onClick={() => setCurrent(false)}>
          A past week
        </Button>
        <Button size="sm" variant={current ? "default" : "outline"} onClick={() => setCurrent(true)}>
          The current week
        </Button>
      </div>
      <div className="spine-paper w-full max-w-[740px] overflow-hidden rounded-xl">
        <SpineWeek
          entry={entry}
          today={today}
          soulMap={SOUL_MAP}
          markMap={MARK_MAP}
          onOpen={open}
          onPreviousWeek={() => setSaid("Turns back to week 40")}
          onNextWeek={() => setSaid("Turns to week 42")}
        />
      </div>
      <p aria-live="polite" className="p-3 text-sm text-muted-foreground">
        {said ?? "Nothing opened yet."}
      </p>
    </Specimen>
  )
}
