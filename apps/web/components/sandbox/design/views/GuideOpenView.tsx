"use client"

import { GuideArticle } from "@/components/home/GuideArticle"
import { renderGuideMarkdown } from "@/lib/home/guide-markdown"
import { DESIGN_GUIDES } from "@/lib/seed/design-home"
import { HomeView } from "./HomeView"

const BODY = renderGuideMarkdown(
  "A pebble's stone has one of nine shapes, set by how strongly the moment weighed and how it felt.\n\n- **Weight** sets its size.\n- **Light** sets its finish.",
)

/** Home with a guide open in the detail area (#1384, two-area spec D2), from the fixture guides. */
export function GuideOpenView() {
  return <HomeView at="/guides/stones" detail={<GuideArticle guide={DESIGN_GUIDES[0]} html={BODY} />} />
}
