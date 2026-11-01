"use client"

import { useLookupMaps } from "@/lib/data/useLookupMaps"
import { useMarks } from "@/lib/data/useMarks"
import { usePathFocus, usePathReading } from "@/lib/hooks/usePathFocus"
import { PathBottomDock } from "@/components/path/PathBottomDock"
import { SpineWeek } from "@/components/path/spine/SpineWeek"

/**
 * The Path's verso drawn on its own (#1384, two-area spec D4): the focused
 * week's opening, persons and contents, in the left area at the reading
 * column's width while the recto runs in the right area. It follows the day
 * the recto is reading, and its contents press stones on the recto through
 * the shared presses. The bounce and karma pill sits at its foot.
 */
export function PathVerso() {
  const focus = usePathFocus()
  const { readingDay } = usePathReading()
  const { marks } = useMarks()
  const { soulMap, markMap } = useLookupMaps(focus.souls, marks)
  const entry = focus.focusedEntry
  const index = entry ? focus.entries.indexOf(entry) : -1
  const previous = focus.entries[index - 1]
  const next = focus.entries[index + 1]

  return (
    <div className="spine-paper relative min-h-full pb-24 text-(--spine-ink)">
      {entry && (
        <SpineWeek
          only="verso"
          entry={entry}
          today={focus.today}
          soulMap={soulMap}
          markMap={markMap}
          readingDay={readingDay}
          onOpen={focus.openMoment}
          onPreviousWeek={previous ? () => focus.focusOn(previous.weekStart) : undefined}
          onNextWeek={next ? () => focus.focusOn(next.weekStart) : undefined}
        />
      )}
      <PathBottomDock />
    </div>
  )
}
