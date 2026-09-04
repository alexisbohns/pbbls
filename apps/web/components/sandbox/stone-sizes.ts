import type { StoneSize } from "@/components/path/PathStone"

export type { StoneSize }

/** The toolbar's stone-scale options. Only the sandbox varies this — the shipped
 *  wall picks one and keeps it. */
export const STONE_SIZES: { key: StoneSize; label: string; note: string }[] = [
  { key: "sm", label: "Small stone", note: "Discreet. The card reads as a photo first, with the pebble as a marker." },
  { key: "md", label: "Medium stone", note: "Balanced. The pebble is a peer of the picture rather than a badge on it." },
  { key: "lg", label: "Large stone", note: "Dominant. The pebble is the subject and the picture supports it." },
]

/** A small pebble's card gets a stone one step down. */
export const STEP_DOWN: Record<StoneSize, StoneSize> = { sm: "sm", md: "sm", lg: "md" }
