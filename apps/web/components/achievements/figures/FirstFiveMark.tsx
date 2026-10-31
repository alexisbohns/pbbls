import { FIRST_FIVE_MARKS } from "@/lib/achievements/seals"
import type { FirstFiveTask } from "@/lib/types"
import { FlatSeal } from "./FlatSeal"

const SOUL = ["M30,110 Q65,55 100,110 Q135,165 170,110"]
const GLYPH = ["M100,40 Q160,40 160,100 Q160,160 100,160 Q40,160 40,100 Q40,65 95,70", "M100,100 L100,101"]

/** A mark drawn in a 200-unit box, `side` wide on (x, y), at the glyph's 1.14-unit carve. */
function Drawn({ x, y, side, paths }: { x: number; y: number; side: number; paths: string[] }) {
  return (
    <g transform={`translate(${(x - side / 2).toFixed(2)} ${(y - side / 2).toFixed(2)}) scale(${(side / 200).toFixed(4)})`}>
      {paths.map((d) => (
        <path key={d} d={d} fill="none" stroke="var(--spine-ink)" strokeWidth={(6 * 200 * 0.19) / side} strokeLinecap="round" strokeLinejoin="round" />
      ))}
    </g>
  )
}

/**
 * One of the first five drawn as the part its task makes (#1308, the boards'
 * `FIVE_MARKS`, as the Home checklist pictures them): a blank seal, the seal
 * lit, three seals together, a soul's mark, a glyph in a dashed circle.
 * `scale` grows it for a sheet's row. iOS `drawFirstFiveMark`.
 */
export function FirstFiveMark({ task, x, y, scale = 1 }: { task: FirstFiveTask; x: number; y: number; scale?: number }) {
  const spec = FIRST_FIVE_MARKS[task]
  if (spec.kind === "seals") {
    return (
      <>
        {spec.seals.map(({ dx, dy, width, ...seal }, i) => (
          <FlatSeal key={i} seal={seal} x={x + dx * scale} y={y + dy * scale} width={width * scale} />
        ))}
      </>
    )
  }
  if (spec.kind === "soul") return <Drawn x={x} y={y} side={14 * scale} paths={SOUL} />
  return (
    <>
      <circle cx={x} cy={y} r={7.2 * scale} fill="none" stroke="var(--spine-ink-2)" strokeWidth={1} strokeDasharray="1.6 1.6" />
      <Drawn x={x} y={y} side={9.5 * scale} paths={GLYPH} />
    </>
  )
}
