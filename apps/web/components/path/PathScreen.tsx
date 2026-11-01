"use client"

import { Loader2 } from "lucide-react"
import { useTranslations } from "next-intl"
import type { Moment, Soul } from "@/lib/types"
import { StoneSprite } from "@/components/pebble/stone/StoneSprite"
import { WeekRoll } from "@/components/path/WeekRoll"
import { WeekPager } from "@/components/path/WeekPager"
import { PathBottomDock } from "@/components/path/PathBottomDock"
import { PathFocusProvider } from "@/components/path/PathFocusProvider"
import { PebblePeek } from "@/components/path/PebblePeek"
import { usePathFocus } from "@/lib/hooks/usePathFocus"
import { cn } from "@/lib/utils"
import { isoWeekNumber } from "@/lib/utils/week-roll-entries"

type PathScreenProps = {
  /** Bare and completed (#1050): a bare moment is a blank stone on the path. */
  moments: Moment[]
  souls: Soul[]
  loading: boolean
  /** `?focus=<momentId>` (R14), consumed once by the focus provider. */
  focusMomentId?: string | null
  /** `?week=` (#1384, spec D9), consumed once by the focus provider. */
  focusWeek?: Date | null
}

/**
 * The whole Path in one place (#1052): the roll, the week pager with each
 * week's verso and recto (side by side as a spread from 900 px), the dock and
 * the peek. Its focus lives in `PathFocusProvider`, which the two-area layout
 * shares between a verso and a recto drawn apart (#1384, two-area spec D4).
 */
export function PathScreen({ moments, souls, loading, focusMomentId = null, focusWeek = null }: PathScreenProps) {
  return (
    <PathFocusProvider moments={moments} souls={souls} loading={loading} focusMomentId={focusMomentId} focusWeek={focusWeek}>
      <PathBook />
    </PathFocusProvider>
  )
}

function PathBook() {
  const t = useTranslations("path")
  const focus = usePathFocus()

  if (focus.waiting) {
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
        entries={focus.entries}
        focused={focus.focusedWeekStart}
        onFocus={focus.focusOn}
        className="shrink-0 spread:spine-paper spread:absolute spread:top-(--safe-area-top) spread:left-(--spine-verso-x) spread:z-20 spread:w-(--spine-verso-w) spread:pr-12 spread:pl-9"
      />
      <p aria-live="polite" className="sr-only">
        {t("weekLabel", { week: focus.focusedEntry?.isoWeek ?? isoWeekNumber(focus.focusedWeekStart) })}
      </p>
      <div className="min-h-0 flex-1">
        <WeekPager
          entries={focus.entries}
          focused={focus.focusedWeekStart}
          today={focus.today}
          souls={focus.souls}
          onFocusChange={focus.focusOn}
          onOpen={focus.openMoment}
        />
      </div>
      {/* The stones' shared shapes and filters, once for every week the pager holds. */}
      <StoneSprite />
      <PathBottomDock className="spread:right-auto spread:left-(--spine-verso-x) spread:w-(--spine-verso-w)" />
      <PebblePeek momentId={focus.openMomentId} onClose={focus.closeMoment} />
    </div>
  )
}
