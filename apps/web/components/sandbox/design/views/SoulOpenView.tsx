"use client"

import SoulDetailPage from "@/app/@detail/(pane)/souls/[id]/page"
import { PeopleView } from "./PeopleView"

const PARAMS = Promise.resolve({ id: "soul-mia" })

/** People with a soul open in the detail area (#1384, two-area spec D2), from the fixture store. */
export function SoulOpenView() {
  return <PeopleView at="/souls/soul-mia" detail={<SoulDetailPage params={PARAMS} />} />
}
