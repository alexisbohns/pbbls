"use client"

import GlyphsPage from "@/app/@detail/(pane)/glyphs/page"
import { YouView } from "./YouView"

/** You with Glyphs open in the detail area (#1384, two-area spec D2). It reads the network where the page does, so it may show its empty or loading state here. */
export function GlyphsOpenView() {
  return <YouView at="/glyphs" detail={<GlyphsPage />} />
}
