"use client"

import LabPage from "@/app/@detail/(pane)/lab/page"
import { YouView } from "./YouView"

/** You with The Lab open in the detail area (#1384, two-area spec D2). It reads the network where the page does, so it may show its empty or loading state here. */
export function LabOpenView() {
  return <YouView at="/lab" detail={<LabPage />} />
}
