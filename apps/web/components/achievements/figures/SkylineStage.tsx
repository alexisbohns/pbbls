import { useId } from "react"
import { GROUND, STAGES, stageAlone, stageClip, stageIndex } from "@/lib/achievements/skyline"

/**
 * One stage of the skyline alone, a register row's mark (#1308, the boards'
 * `stage`): solid when `done` (a level the server lists), otherwise its contour
 * with the fill it has (the server's progress), on its own ground line. Stages
 * past the eighth reuse the last. Decorative. Android `SkylineStage`.
 */
export function SkylineStage({ stage, fill, done, scale = 1 }: { stage: number; fill: number; done: boolean; scale?: number }) {
  const clipId = useId()
  const k = stageIndex(stage)
  const { transform, baseline } = stageAlone(k, scale)
  return (
    <svg aria-hidden focusable="false" viewBox="-14 -14 28 28" width={28} height={28} className="block overflow-visible">
      <g transform={transform}>
        {done ? (
          <path d={STAGES[k]} fill="var(--spine-ink)" />
        ) : (
          <>
            {fill > 0 && (
              <>
                <clipPath id={clipId}>
                  <rect {...stageClip(k, fill)} />
                </clipPath>
                <path d={STAGES[k]} fill="var(--spine-ink)" clipPath={`url(#${clipId})`} />
              </>
            )}
            <path d={STAGES[k]} fill="none" stroke="var(--spine-ink-2)" strokeWidth={1} vectorEffect="non-scaling-stroke" strokeLinejoin="round" />
          </>
        )}
        <line x1={baseline[0]} x2={baseline[1]} y1={GROUND} y2={GROUND} stroke="var(--spine-ink-2)" strokeWidth={1} vectorEffect="non-scaling-stroke" />
      </g>
    </svg>
  )
}
