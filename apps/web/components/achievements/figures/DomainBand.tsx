import { bandItems } from "@/lib/achievements/runs"
import { BAND_DOT_DROP, BAND_HEIGHT, BAND_WIDTH, shards } from "@/lib/achievements/shards"
import type { AchievementsDomains } from "@/lib/types"

/**
 * The domains' mended band (#1308, D11; the boards' `shardStrip`): the domains
 * entered in the order they came, inked, then those not yet, dotted, spaced by
 * both lists together; a dot under each new one. Decorative: its card's label
 * carries the count and the coverage. It is laid out on the board's 300 × 40
 * and scales as one, so its seams never change with the room; `width` only
 * caps the size it renders at. iOS `DomainBand`.
 */
export function DomainBand({ domains, width = BAND_WIDTH }: { domains: AchievementsDomains; width?: number }) {
  const items = bandItems(domains)
  const pieces = shards(items.length, BAND_WIDTH, BAND_HEIGHT)
  return (
    <svg
      aria-hidden
      focusable="false"
      viewBox={`0 0 ${BAND_WIDTH} ${BAND_HEIGHT + 10}`}
      className="block h-auto w-full overflow-visible"
      style={{ maxWidth: width }}
    >
      {items.map((item, i) =>
        item.on ? (
          <path
            key={i}
            d={pieces[i].d}
            fill="color-mix(in oklab, var(--spine-ink) 8%, var(--spine-stock))"
            stroke="var(--spine-ink)"
            strokeWidth={1.3}
            strokeLinejoin="round"
          />
        ) : (
          <path key={i} d={pieces[i].d} fill="none" stroke="var(--spine-ink-2)" strokeWidth={1} strokeDasharray="1.2 2.2" strokeLinecap="round" />
        ),
      )}
      {items.map((item, i) =>
        item.isNew ? (
          <circle
            key={`new-${i}`}
            cx={pieces[i].centre}
            cy={BAND_HEIGHT + BAND_DOT_DROP}
            r={2.4}
            fill="var(--spine-ink)"
            stroke="var(--spine-stock)"
            strokeWidth={1.6}
            paintOrder="stroke"
          />
        ) : null,
      )}
    </svg>
  )
}
