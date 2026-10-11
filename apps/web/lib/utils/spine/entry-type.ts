/**
 * The type ranks of a pebble's name on the Spine (#1052, spec §No-photo entry,
 * §Contents, §Card anatomy). Intensity sets the size, valence sets the cut, so
 * neither ever rests on colour alone.
 */
import type { Intensity } from "./card-size"

export type Valence = -1 | 0 | 1

export type NameType = {
  /** px */
  fontSize: number
  fontWeight: number
  italic: boolean
  /** em */
  letterSpacing: number
}

export type EntryType = NameType & {
  lineHeight: number
  /** The stone's centre, in px from the entry's top: the name's first line. */
  anchor: number
}

const ENTRY_SIZE: Record<Intensity, { phone: number; wide: number }> = {
  1: { phone: 19, wide: 21 },
  2: { phone: 24, wide: 27 },
  3: { phone: 31, wide: 35 },
}

const ENTRY_WEIGHT: Record<Valence, number> = { 1: 600, 0: 460, [-1]: 340 }

/** A no-photo entry's name: no box, set as type. */
export function entryType(intensity: Intensity, valence: Valence, wide: boolean): EntryType {
  const fontSize = wide ? ENTRY_SIZE[intensity].wide : ENTRY_SIZE[intensity].phone
  const lowlight = valence === -1
  return {
    fontSize,
    fontWeight: ENTRY_WEIGHT[valence],
    italic: lowlight,
    letterSpacing: lowlight ? -0.004 : -0.012,
    lineHeight: 1.12,
    anchor: fontSize * 0.6,
  }
}

const CONTENTS_WEIGHT: Record<Intensity, number> = { 1: 400, 2: 470, 3: 620 }

/** A contents row's name: weight by intensity, lowlights in a light italic. */
export function contentsNameType(intensity: Intensity, valence: Valence, wide: boolean): NameType {
  const lowlight = valence === -1
  return {
    fontSize: wide ? 15.5 : 16.5,
    fontWeight: lowlight ? 350 : CONTENTS_WEIGHT[intensity],
    italic: lowlight,
    letterSpacing: 0,
  }
}

const CARD_TITLE_WEIGHT: Record<Valence, number> = { 1: 600, 0: 480, [-1]: 360 }

/**
 * A card's title cut by valence. Its size is in card units (`4.4u`), which only
 * the card's container query knows, so it is not returned here.
 */
export function cardTitleCut(valence: Valence): Pick<NameType, "fontWeight" | "italic"> {
  return { fontWeight: CARD_TITLE_WEIGHT[valence], italic: valence === -1 }
}
