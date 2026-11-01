import { AchievementSheetDetail } from "@/components/achievements/sheets/AchievementSheetDetail"
import { namedSheet } from "@/lib/achievements/sheets"

/**
 * An achievements sheet (Explorer, Emotions, Domains, Pebbles) as a nested
 * detail under Achievements (#1384, two-area spec D5): `/achievements/explorer`
 * and its siblings. An unknown name says it is not there.
 */
export default async function AchievementSheetPage({ params }: { params: Promise<{ sheet: string }> }) {
  const { sheet } = await params
  return <AchievementSheetDetail sheet={namedSheet(sheet)} />
}
