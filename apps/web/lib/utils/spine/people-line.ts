import type { Locale } from "@/lib/i18n/locales"
import { cardinalWords } from "@/lib/i18n/number-words"

/**
 * Who a pebble was with, as one line (#1052, spec §Many people):
 *
 *   0 → no line · 1 → A · 2 → A & B · 3 → A, B & C
 *   n ≥ 4 → A, B "& {n − 2 in words} others", up to five glyph marks of the
 *           others, then +{n − 2 − 5} when positive
 *
 * e.g. at 14: "with MARA, ELIO & twelve others [5 marks] +7". Souls are ordered
 * as the persons of the week are, so the most present people are the named ones.
 */
export type PeopleLine = {
  /** Soul ids set by name. Empty means the pebble is solitary: no line at all. */
  named: string[]
  /** How many souls are folded into "& N others" (0 when everyone is named). */
  others: number
  /** `others` in words, e.g. "twelve"; "" when 0. */
  othersWords: string
  /** Soul ids of the others drawn as glyph marks, at most five. */
  marks: string[]
  /** Others left after the marks, shown as "+N"; 0 means no "+". */
  overflow: number
}

export const PEOPLE_LINE_MARKS = 5

/** Named in full up to this many souls. */
const NAMED_IN_FULL = 3

/**
 * @param soulIds the pebble's souls.
 * @param order soul ids in persons-of-the-week order; ids missing from it
 *   keep their relative order after the ranked ones.
 */
export function peopleLine(
  soulIds: readonly string[],
  order: readonly string[],
  locale: Locale,
): PeopleLine {
  const rank = new Map(order.map((id, index) => [id, index]))
  const ranked = [...new Set(soulIds)]
    .map((id, index) => ({ id, index }))
    .sort(
      (a, b) =>
        (rank.get(a.id) ?? Number.MAX_SAFE_INTEGER) - (rank.get(b.id) ?? Number.MAX_SAFE_INTEGER) ||
        a.index - b.index,
    )
    .map((s) => s.id)

  if (ranked.length <= NAMED_IN_FULL) {
    return { named: ranked, others: 0, othersWords: "", marks: [], overflow: 0 }
  }

  const named = ranked.slice(0, 2)
  const rest = ranked.slice(2)
  const marks = rest.slice(0, PEOPLE_LINE_MARKS)
  return {
    named,
    others: rest.length,
    // "others" is "autres" in French, the same for either gender above one.
    othersWords: cardinalWords(rest.length, locale),
    marks,
    overflow: rest.length - marks.length,
  }
}
