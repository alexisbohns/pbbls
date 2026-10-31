import type { ComponentType } from "react"
import { Collection, KarmaDot, PebbleDottedGlyph, PebbleGlowMd, PebbleRound, People, StonesAlignedBottom, StonesStair, StonesTrack } from "@pbbls/icons"
import type { GuideIllustration } from "@/lib/types"

/** A guide's chip icon by its `illustration` (#1384, spec D14). */
export const GUIDE_ICONS: Record<GuideIllustration, ComponentType<{ className?: string }>> = {
  pebbles: PebbleRound,
  stones: StonesAlignedBottom,
  emotions: StonesTrack,
  valence: StonesStair,
  domains: PebbleGlowMd,
  collections: Collection,
  souls: People,
  glyphs: PebbleDottedGlyph,
  karma: KarmaDot,
}
