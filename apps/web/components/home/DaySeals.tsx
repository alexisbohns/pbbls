import { FlatSeal } from "@/components/achievements/figures/FlatSeal"
import { pebbleSeal } from "@/lib/achievements/seals"
import type { StonePalette } from "@/lib/config/stone-tones"
import { getOutline } from "@/lib/config/pebble-outlines"
import { sealWidth } from "@/lib/home/month-columns"
import type { MonthWrapPebble } from "@/lib/types"

const GAP = 2

/**
 * A day's pebbles in the month grid (#1384, spec D10): up to three of the
 * Spine's flat seals side by side, sized by intensity from the 15 px pip and
 * inked by emotion. Needs `StoneSprite` on the page (the app shell mounts it once).
 */
export function DaySeals({
  pebbles,
  paletteFor,
}: {
  pebbles: readonly MonthWrapPebble[]
  paletteFor: (emotionId: string | null) => StonePalette | null
}) {
  const sized = pebbles.map((p) => {
    const seal = pebbleSeal(p)
    const width = sealWidth(p.intensity)
    const outline = getOutline(seal.size, seal.outline)
    return { p, seal, width, height: (outline.height * width) / outline.width }
  })
  // Each seal's centre: the widths before it, plus the gaps.
  const placed = sized.map((s, k) => ({
    ...s,
    cx: sized.slice(0, k).reduce((sum, prev) => sum + prev.width + GAP, 0) + s.width / 2,
  }))
  const total = placed.reduce((sum, s) => sum + s.width, 0) + GAP * Math.max(0, placed.length - 1)
  const height = Math.max(...placed.map((s) => s.height), 1)
  return (
    <svg aria-hidden="true" width={total} height={height} viewBox={`0 0 ${total} ${height}`} className="overflow-visible">
      {placed.map((s, k) => (
        <FlatSeal key={k} seal={s.seal} x={s.cx} y={height / 2} width={s.width} palette={paletteFor(s.p.emotionId)} />
      ))}
    </svg>
  )
}
