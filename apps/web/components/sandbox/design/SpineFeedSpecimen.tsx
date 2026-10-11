"use client"

import { useMemo, useState } from "react"
import { SpineFeed } from "@/components/path/spine/SpineFeed"
import { useLocale } from "@/lib/i18n"
import {
  SPINE_FIXTURE_MARKS,
  SPINE_FIXTURE_PEBBLES,
  SPINE_FIXTURE_SOULS,
  SPINE_FIXTURE_TODAY,
} from "@/lib/seed/spine-fixture-week"
import { personsOfWeek } from "@/lib/utils/spine/persons-of-week"
import { spineWeek } from "@/lib/utils/spine/spine-week"
import { buildWeekRollEntries } from "@/lib/utils/week-roll-entries"
import { Specimen } from "./Specimen"

const SOUL_MAP = new Map(SPINE_FIXTURE_SOULS.map((s) => [s.id, s]))
const MARK_MAP = new Map(SPINE_FIXTURE_MARKS.map((m) => [m.id, m]))

/**
 * The Spine's feed over the brief's fixture week (#1052), through the same
 * pipeline the Path will use: `buildWeekRollEntries` → `spineWeek`, people
 * ordered by `personsOfWeek`. Press a stone, a card or an entry.
 */
export function SpineFeedSpecimen() {
  const { locale } = useLocale()
  const [opened, setOpened] = useState<string | null>(null)

  const { week, order } = useMemo(() => {
    const entry = buildWeekRollEntries(SPINE_FIXTURE_PEBBLES, SPINE_FIXTURE_TODAY).find(
      (e) => e.weekStartIso === "2026-W41",
    )
    if (!entry) throw new Error("[spine fixture] week 41 is missing")
    const built = spineWeek(entry, SPINE_FIXTURE_TODAY)
    const persons = personsOfWeek(entry.pebbles, built.weekStart, SOUL_MAP, locale)
    return { week: built, order: persons.map((p) => p.soul.id) }
  }, [locale])

  const openedName = SPINE_FIXTURE_PEBBLES.find((p) => p.id === opened)?.name

  return (
    <Specimen
      id="spine-feed"
      note="The brief's week of 5–11 October, plus a still Friday, an uncarved stone, a fourteen-person picnic and an untitled photo. Cards start square and settle to their photo once its ratio is probed. Phone geometry below 900 px, the spread's above."
      className="block p-0"
    >
      <div className="spine-paper w-full max-w-[740px] overflow-hidden rounded-xl">
        <SpineFeed week={week} order={order} soulMap={SOUL_MAP} markMap={MARK_MAP} onOpen={setOpened} />
      </div>
      <p aria-live="polite" className="p-3 text-sm text-muted-foreground">
        {opened ? `Opens: ${openedName || "(untitled)"}` : "Nothing opened yet."}
      </p>
    </Specimen>
  )
}
