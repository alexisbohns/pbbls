import { AchievementSheetDetail } from "@/components/achievements/sheets/AchievementSheetDetail"
import { yearSheet } from "@/lib/achievements/sheets"

/** A year's sheet as a nested detail under Achievements (#1384, two-area spec D5): `/achievements/year/2026`. */
export default async function AchievementYearPage({ params }: { params: Promise<{ year: string }> }) {
  const { year } = await params
  return <AchievementSheetDetail sheet={yearSheet(year)} />
}
