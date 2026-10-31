import { useId } from "react"
import { SKYLINE_VIEWS, STAGES, contour, skylineStages, stageClip } from "@/lib/achievements/skyline"
import type { CountingLadder } from "@/lib/types"

type PebbleSkylineProps = {
  stack: CountingLadder
  /** `wide`: the pebbles card and sheet; `narrow`: a glyph stack's line. */
  view?: keyof typeof SKYLINE_VIEWS
  /** The board's width: the skyline takes the width it is given, up to this. */
  width?: number
}

/**
 * One counting stack's skyline (#1308, D3; the boards' `skyline`): a stage per
 * ladder step, in ink when the server lists it reached, the server's next one
 * filled from the ground by its progress; the contour in ink once every stage
 * stands. Decorative: its card's label carries the count and the milestones.
 * iOS `PebbleSkyline`.
 */
export function PebbleSkyline({ stack, view = "wide", width = view === "wide" ? 300 : 103 }: PebbleSkylineProps) {
  const v = SKYLINE_VIEWS[view]
  const clipBase = useId()
  const { stages, standing } = skylineStages(stack)
  return (
    <svg
      aria-hidden
      focusable="false"
      viewBox={`${v.x} ${v.y} ${v.width} ${v.height}`}
      className="block h-auto w-full overflow-visible"
      style={{ maxWidth: width }}
    >
      {stages.map((s) =>
        s.reached ? (
          <path key={s.index} d={STAGES[s.index]} fill="var(--spine-ink)" />
        ) : s.fill > 0 ? (
          <g key={s.index}>
            <clipPath id={`${clipBase}-${s.index}`}>
              <rect {...stageClip(s.index, s.fill)} />
            </clipPath>
            <path d={STAGES[s.index]} fill="var(--spine-ink)" clipPath={`url(#${clipBase}-${s.index})`} />
          </g>
        ) : null,
      )}
      <path
        d={contour(stages.length, v.end)}
        fill="none"
        stroke={standing ? "var(--spine-ink)" : "var(--spine-ink-2)"}
        strokeWidth={1}
        vectorEffect="non-scaling-stroke"
        strokeLinejoin="round"
        strokeLinecap="round"
      />
    </svg>
  )
}
