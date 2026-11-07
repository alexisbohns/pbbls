import type { ComponentType } from "react"
import { AbstractKey, AbstractSign, AbstractUmbrella, Broom, Hand, Scale, Sparkles, Trash } from "@pbbls/icons"
import type { DocsIcon } from "@/lib/docs/types"

/** A docs page's chip icon in the navigation, by its manifest `icon`. */
export const DOCS_ICONS: Record<DocsIcon, ComponentType<{ className?: string }>> = {
  sign: AbstractSign,
  scale: Scale,
  key: AbstractKey,
  sparkles: Sparkles,
  hand: Hand,
  trash: Trash,
  broom: Broom,
  umbrella: AbstractUmbrella,
}
