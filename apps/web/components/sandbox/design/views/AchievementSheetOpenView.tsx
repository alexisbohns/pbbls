"use client"

import { AchievementSheetView } from "@/components/achievements/sheets/AchievementSheetView"
import type { SheetKey } from "@/lib/achievements/sheets"
import { sheetHref } from "@/lib/achievements/sheets"
import { useSheetNames } from "@/lib/i18n/useSheetNames"
import { DESIGN_ACHIEVEMENT_PALETTES, DESIGN_ACHIEVEMENTS } from "@/lib/seed/design-achievements"
import { YouView } from "./YouView"

const noop = () => {}

/**
 * You with an achievements sheet open as a nested detail (#1384, two-area spec
 * D5), from the canonical design fixture. Its rows, back arrow and ready marks
 * do nothing here.
 */
export function AchievementSheetOpenView({ sheet }: { sheet: SheetKey }) {
  const names = useSheetNames()
  return (
    <YouView
      at={sheetHref(sheet)}
      detail={
        <AchievementSheetView
          sheet={sheet}
          page={DESIGN_ACHIEVEMENTS}
          loading={false}
          palettes={DESIGN_ACHIEVEMENT_PALETTES}
          names={names}
          pulling={null}
          onPull={noop}
          onOpen={noop}
          onBack={noop}
        />
      }
    />
  )
}
