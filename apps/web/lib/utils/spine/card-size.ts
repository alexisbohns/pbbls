/**
 * Deck-card sizing for the Spine (#1052, spec §Card sizing). The card is sized
 * to its photo's own ratio and never crops it.
 *
 * The chrome (index, rail, mat, title, souls strip) is treated as a fixed
 * 123 px vertical and 95 px horizontal, so with `cap` the card's height cap:
 *
 *   W = min(column, (cap − 123) × ratio + 95)      H = 123 + (W − 95) / ratio
 *
 * The component writes the same formula in CSS
 * (`min(100%, calc((var(--cap) - 123px) * var(--ar) + 95px))`), so no JS runs
 * per card once `--ar` is known; this module is the tested mirror of it, plus
 * the 180 px floor (a title needs that width, even if the card then exceeds its
 * cap). Until a photo's ratio is known the card is square (D14).
 */

export type Intensity = 1 | 2 | 3

/** Fixed chrome around the photo, in px. */
export const CARD_CHROME = { vertical: 123, horizontal: 95 } as const

/** Below this width a title no longer fits. */
export const CARD_MIN_WIDTH = 180

/** The ratio a card holds while its photo's real ratio is unknown (D14). */
export const CARD_PENDING_RATIO = 1

const CAP: Record<Intensity, { phone: number; wide: number }> = {
  1: { phone: 330, wide: 340 },
  2: { phone: 370, wide: 400 },
  3: { phone: 400, wide: 460 },
}

/** The painted bottom edge's thickness, by intensity. */
const EDGE: Record<Intensity, number> = { 1: 2, 2: 3.5, 3: 5.5 }

/** The card's height cap in px. `wide` is the ≥ 900 px spread. */
export function cardCap(intensity: Intensity, wide: boolean): number {
  return wide ? CAP[intensity].wide : CAP[intensity].phone
}

export function cardEdge(intensity: Intensity): number {
  return EDGE[intensity]
}

export type CardSize = { width: number; height: number; cap: number }

/**
 * The card's width and approximate height for a photo `ratio` (width / height)
 * in a content column `column` px wide. The height is exact only when the card
 * unit has reached its 4.4 px ceiling (card ≥ 308 px); narrower cards drift by
 * up to about 10 px, as in the mock.
 */
export function cardSize({
  intensity,
  ratio,
  column,
  wide,
}: {
  intensity: Intensity
  ratio: number
  column: number
  wide: boolean
}): CardSize {
  const cap = cardCap(intensity, wide)
  const safeRatio = Number.isFinite(ratio) && ratio > 0 ? ratio : CARD_PENDING_RATIO
  const fitted = Math.min(column, (cap - CARD_CHROME.vertical) * safeRatio + CARD_CHROME.horizontal)
  const width = Math.max(fitted, Math.min(CARD_MIN_WIDTH, column))
  const height = CARD_CHROME.vertical + (width - CARD_CHROME.horizontal) / safeRatio
  return { width, height, cap }
}
