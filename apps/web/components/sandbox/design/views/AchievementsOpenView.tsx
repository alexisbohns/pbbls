"use client"

import AchievementsPage from "@/app/@detail/(pane)/achievements/page"
import { YouView } from "./YouView"

/** You with Achievements open in the detail area (#1384, two-area spec D2). It reads the network where the page does, so it may show its empty or loading state here. */
export function AchievementsOpenView() {
  return <YouView at="/achievements" detail={<AchievementsPage />} />
}
