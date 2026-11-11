import type { ComponentType } from "react"
import { Collection, DOTTED, KarmaDot, PebbleGlyph, PebbleGlowMd, PebbleRound, People, StonesAlignedBottom, StonesStair, StonesTrack, type Form } from "@pbbls/icons"
import type { GuideIllustration } from "@/lib/types"

/** A guide's chip icon by its `illustration` (#1384, spec D14). */
export const GUIDE_ICONS: Record<GuideIllustration, ComponentType<{ className?: string; form?: Form }>> = {
  pebbles: PebbleRound,
  stones: StonesAlignedBottom,
  emotions: StonesTrack,
  valence: StonesStair,
  domains: PebbleGlowMd,
  collections: Collection,
  souls: People,
  glyphs: PebbleGlyph,
  karma: KarmaDot,
}

/** The form a guide's chip icon draws, where it is not the regular one. */
export const GUIDE_ICON_FORMS: Partial<Record<GuideIllustration, Form>> = {
  glyphs: DOTTED,
}
