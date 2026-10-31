import { POLARITY_BY_VALENCE, SIZE_BY_INTENSITY, type Polarity, type Size } from "@/lib/config/pebble-geometry"
import type { CrossingPebble, FirstFiveTask, StoneKind } from "@/lib/types"

// The flat seal (#1308, the boards' `sealAt`): the Spine's print of a stone,
// placed in a figure. Its outline is kept apart from its print, because the
// boards cut some prints on another outline: the six firsts' three intensities,
// the domains' ring and the year's counters are a solid print on the neutral
// outline. iOS `FlatSeal`, Android `InkSeal`.

/** Solid fills the outline; tint lays the ink over the stock inside a rim; wash adds a hatch; blank is the stock inside a rim. */
export type SealPrint = "solid" | "tint" | "wash" | "blank"

export type SealSpec = { size: Size; outline: Polarity; print: SealPrint }

/** The width a seal prints at, in a figure's units. */
export type PlacedSeal = SealSpec & { width: number }

const PRINT_BY_POLARITY: Record<Polarity, SealPrint> = { highlight: "solid", neutral: "tint", lowlight: "wash" }

/** A pebble as the Spine prints it: its size, its valence's outline and print. */
export function pebbleSeal(p: Pick<CrossingPebble, "intensity" | "positiveness">): SealSpec {
  const outline = POLARITY_BY_VALENCE[p.positiveness]
  return { size: SIZE_BY_INTENSITY[p.intensity], outline, print: PRINT_BY_POLARITY[outline] }
}

/** The even medium stone: a met seat whose pebble is gone still prints. */
export const NEUTRAL_MEDIUM = { intensity: 2, positiveness: 0 } as const

/** A met emotion on the Explorer wheel: 7.4 plus 1.6 per step of intensity. */
export function seatWidth(intensity: number): number {
  return 7.4 + intensity * 1.6
}

/** The six firsts (D5): three sizes of the solid seal on the neutral outline, then the three prints at medium. */
export const SIX_FIRST_SEALS: Record<StoneKind, PlacedSeal> = {
  small: { size: "small", outline: "neutral", print: "solid", width: 9 },
  medium: { size: "medium", outline: "neutral", print: "solid", width: 12 },
  large: { size: "large", outline: "neutral", print: "solid", width: 14 },
  lowlight: { size: "medium", outline: "lowlight", print: "wash", width: 12.5 },
  neutral: { size: "medium", outline: "neutral", print: "tint", width: 12.5 },
  highlight: { size: "medium", outline: "highlight", print: "solid", width: 12.5 },
}

/** A domain entered, on the wheel's domains ring: a small solid seal on the neutral outline. */
export const DOMAIN_SEAL: PlacedSeal = { size: "small", outline: "neutral", print: "solid", width: 9 }

/** One of the 25 pebbles: a small solid highlight. */
export const TWENTY_FIVE_SEAL: PlacedSeal = { size: "small", outline: "highlight", print: "solid", width: 6.2 }

/** A first-five task's mark (the boards' `FIVE_MARKS`): seals at offsets, or a drawn mark. */
export type FirstFiveMarkSpec =
  | { kind: "seals"; seals: (PlacedSeal & { dx: number; dy: number })[] }
  | { kind: "soul" }
  | { kind: "glyph" }

export const FIRST_FIVE_MARKS: Record<FirstFiveTask, FirstFiveMarkSpec> = {
  "first-moment": { kind: "seals", seals: [{ size: "medium", outline: "neutral", print: "blank", width: 10, dx: 0, dy: 0 }] },
  "first-pebble": { kind: "seals", seals: [{ size: "medium", outline: "highlight", print: "solid", width: 10, dx: 0, dy: 0 }] },
  "first-collection": {
    kind: "seals",
    seals: [
      { size: "small", outline: "highlight", print: "solid", width: 6, dx: -4.6, dy: 2.4 },
      { size: "medium", outline: "neutral", print: "tint", width: 6.6, dx: 0, dy: -3 },
      { size: "small", outline: "lowlight", print: "wash", width: 6, dx: 4.6, dy: 2.4 },
    ],
  },
  "first-soul": { kind: "soul" },
  "first-glyph": { kind: "glyph" },
}

/** A first-five mark's width, for the new mark round it. */
export const FIRST_FIVE_MARK_WIDTH = 10

/** A ghost's diameter, for a new mark round a seat not yet filled. */
export const GHOST_WIDTH = 6.4
