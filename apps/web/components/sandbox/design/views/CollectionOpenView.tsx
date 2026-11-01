"use client"

import CollectionDetailPage from "@/app/@detail/(pane)/collections/[id]/page"
import { CollectionsView } from "./CollectionsView"

const PARAMS = Promise.resolve({ id: "design-col-stack" })

/** Collections with one open in the detail area (#1384, two-area spec D2), from the fixture store. */
export function CollectionOpenView() {
  return <CollectionsView at="/collections/design-col-stack" detail={<CollectionDetailPage params={PARAMS} />} />
}
