import type { AchievementPalettes } from "@/lib/achievements/palette"
import { COUNTER_WIDTH, DISC_BOX, DISC_CENTRE, STONE_EDGE, STONE_GRAIN, discMonth, type DiscMonth } from "@/lib/achievements/year-disc"
import type { AchievementYear } from "@/lib/types"
import { FlatSeal } from "./FlatSeal"
import { HueGroup } from "./HueGroup"

const COUNTER = { size: "medium", outline: "neutral" } as const

/** A month's hue: its family's (`--spine-spot`), or ink when it is absent or unresolved. */
function hueOf(month: DiscMonth, palettes: AchievementPalettes) {
  const palette = month.tone.muted ? null : palettes.family(month.tone.family)
  return { palette, hue: palette ? "var(--spine-spot)" : "var(--spine-ink)" }
}

/**
 * The docked year disc (#1308, D12; the boards' `yearDiscDocked`, treatment
 * B): the stone and its grain, two inner rings, a spoke and two notches a
 * month, then each month's counter at its served level, its climb and, for a
 * month begun at the centre, its goal. A kept month wears its family's hue, or
 * ink; a rim month adds the hairline ring, a hued spoke and a hued numeral;
 * only a month the server says is upcoming or quiet is muted. Decorative: its
 * card's label reads the months at each place. iOS `YearDisc`.
 */
export function YearDisc({ year, palettes, size = 280 }: { year: AchievementYear; palettes: AchievementPalettes; size?: number }) {
  const months = year.months.map(discMonth)
  return (
    <svg
      aria-hidden
      focusable="false"
      viewBox={`${DISC_BOX.x} ${DISC_BOX.y} ${DISC_BOX.width} ${DISC_BOX.height}`}
      className="block h-auto w-full overflow-visible"
      style={{ maxWidth: size }}
    >
      <path d={STONE_EDGE} fill="var(--spine-stock)" stroke="var(--spine-ink-2)" strokeOpacity={0.85} strokeWidth={1.2} strokeLinejoin="round" />
      {STONE_GRAIN.map(([cx, cy, r], i) => (
        <circle key={i} cx={cx} cy={cy} r={r} fill="var(--spine-ink-3)" />
      ))}
      <circle cx={DISC_CENTRE} cy={DISC_CENTRE} r={52} fill="none" stroke="var(--spine-ink-2)" strokeOpacity={0.5} strokeWidth={1} />
      <circle cx={DISC_CENTRE} cy={DISC_CENTRE} r={48.5} fill="none" stroke="var(--spine-ink-2)" strokeOpacity={0.45} strokeWidth={0.5} />
      {months.map((m) => {
        const { palette, hue } = hueOf(m, palettes)
        return (
          <HueGroup key={`base-${m.month}`} palette={palette}>
            <line {...lineOf(m.spoke)} stroke={m.spoke.hued ? hue : "var(--spine-ink-2)"} strokeWidth={m.spoke.width} />
            {m.notches.map((n, i) => (
              <line key={i} {...lineOf(n)} stroke="var(--spine-ink-2)" strokeWidth={n.width} />
            ))}
          </HueGroup>
        )
      })}
      {months.map((m) => {
        const { palette, hue } = hueOf(m, palettes)
        return (
          <HueGroup key={`month-${m.month}`} palette={palette}>
            {m.goal && <line {...lineOf(m.goal)} stroke="var(--spine-ink)" strokeWidth={1.3} strokeLinecap="round" />}
            {m.climb && (
              <line {...lineOf(m.climb)} stroke={m.climb.hued && !m.tone.muted ? hue : "var(--spine-ink)"} strokeWidth={1.8} strokeLinecap="round" />
            )}
            {m.counter.ring && (
              <circle cx={m.counter.x} cy={m.counter.y} r={COUNTER_WIDTH * 0.78} fill="none" stroke="var(--spine-ink)" strokeWidth={0.8} />
            )}
            {m.counter.print === "solid" ? (
              <FlatSeal seal={{ ...COUNTER, print: "solid" }} x={m.counter.x} y={m.counter.y} width={COUNTER_WIDTH} palette={palette} />
            ) : (
              <FlatSeal seal={{ ...COUNTER, print: "blank" }} x={m.counter.x} y={m.counter.y} width={COUNTER_WIDTH} ink="ink2" dashed={m.counter.dashed} />
            )}
          </HueGroup>
        )
      })}
      <path d="M116,120 L124,120 M120,116 L120,124" stroke="var(--spine-ink-2)" strokeWidth={1.1} />
      {months.map((m) => {
        const { palette, hue } = hueOf(m, palettes)
        return (
          <HueGroup key={`numeral-${m.month}`} palette={palette}>
            <text
              x={m.numeral.x}
              y={m.numeral.y}
              textAnchor="middle"
              fontSize={m.numeral.size}
              fontWeight={m.numeral.emphasized ? 600 : 400}
              fill={m.numeral.emphasized ? hue : "var(--spine-ink-2)"}
              className="font-spine"
            >
              {m.numeral.text}
            </text>
          </HueGroup>
        )
      })}
    </svg>
  )
}

function lineOf(s: { x1: number; y1: number; x2: number; y2: number }) {
  return { x1: s.x1, y1: s.y1, x2: s.x2, y2: s.y2 }
}
