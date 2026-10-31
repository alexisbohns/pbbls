import { CAIRN_BOX, EMPTY_CAIRN, cairnStones, cairnWidths, type CairnDay } from "@/lib/home/cairn"

// The pebble outline the cairns and the seals in the mock press flat (17.6 × 14.8 at 3.3, 4.5).
const PEBBLE = "M11.8 4.6 C17.2 4.5 21 7.4 20.9 11.6 C20.8 16.2 17 19.2 11.7 19.3 C7 19.3 3.3 16.4 3.3 12 C3.3 7.5 6.8 4.7 11.8 4.6 Z"

/**
 * A week's cairn under its column (#1384, spec D10): its stones from the
 * served [moments, intensity] pairs, or one dotted stone for a week without a
 * pebble. Decorative: the column's link names the week.
 */
export function CairnMark({ cairn }: { cairn: readonly CairnDay[] | null }) {
  const widths = cairn ? cairnWidths(cairn) : []
  const stones = widths.length > 0 ? cairnStones(widths) : EMPTY_CAIRN
  return (
    <svg aria-hidden="true" width={CAIRN_BOX.width} height={CAIRN_BOX.height} viewBox={`0 0 ${CAIRN_BOX.width} ${CAIRN_BOX.height}`}>
      {stones.map((s, k) => (
        <path
          key={k}
          d={PEBBLE}
          transform={`translate(${(s.x - (3.3 * s.w) / 17.6).toFixed(2)} ${(s.y - (4.5 * s.h) / 14.8).toFixed(2)}) scale(${(s.w / 17.6).toFixed(3)} ${(s.h / 14.8).toFixed(3)})`}
          vectorEffect="non-scaling-stroke"
          className={s.dotted ? "fill-none stroke-m3-outline" : "fill-m3-on-surface-variant"}
          strokeWidth={s.dotted ? 1 : undefined}
          strokeDasharray={s.dotted ? "2 2" : undefined}
        />
      ))}
    </svg>
  )
}
