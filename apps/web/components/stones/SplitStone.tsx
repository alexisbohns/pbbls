"use client"

import type { ReactNode } from "react"
import { useElementSize } from "@/lib/hooks/useElementSize"
import { cachedStone, stoneKey } from "@/lib/stones/stone-cache"
import type { StoneOptions } from "@/lib/stones/stone-outline"
import { DEFAULT_SPLIT_SEED, splitStone } from "@/lib/stones/stone-split"
import { cn } from "@/lib/utils"
import type { StoneFill } from "./stone-fill"

type SplitStoneProps = {
  n: number
  /** The rock's height in px. */
  height: number
  seed?: string
  options?: StoneOptions
  /** One fill for every piece, or one per piece. */
  fill: StoneFill | readonly StoneFill[]
  /** One slot per piece; may be empty for a purely decorative rock. */
  cells?: readonly ReactNode[]
  className?: string
}

function isFillList(fill: StoneFill | readonly StoneFill[]): fill is readonly StoneFill[] {
  return Array.isArray(fill)
}

/**
 * One rock cut into `n` pieces, each piece a grid cell (#1384, spec D7). The
 * cells are equal columns separated by the gutter, which is exactly where
 * `splitStone` puts the pieces, so each cell's content sits on its own piece.
 * The cells render before the rock is measured (each on a plain rounded fill),
 * so nothing shifts when it is drawn.
 */
export function SplitStone({ n, height, seed = DEFAULT_SPLIT_SEED, options, fill, cells, className }: SplitStoneProps) {
  const [ref, size] = useElementSize<HTMLDivElement>()
  const list = isFillList(fill) ? fill : [fill]
  const fills = Array.from({ length: n }, (_, k) => list[Math.min(k, list.length - 1)])
  const gutter = options?.gutter ?? 8
  const pieces =
    size && size.width > 0
      ? cachedStone("split", stoneKey(seed, size.width, height, [n, options]), () =>
          splitStone(size.width, height, n, seed, options),
        )
      : null
  const drawn = pieces !== null && pieces.every((d) => d !== "")

  return (
    <div
      ref={ref}
      className={cn("relative isolate grid", className)}
      style={{ height, gridTemplateColumns: `repeat(${n}, minmax(0, 1fr))`, columnGap: gutter }}
    >
      {drawn && size && (
        <svg
          aria-hidden="true"
          className="pointer-events-none absolute inset-0 -z-10 overflow-visible"
          width={size.width}
          height={height}
          viewBox={`0 0 ${size.width} ${height}`}
        >
          {pieces.map((d, k) => (
            <path key={k} d={d} className={fills[k].fill} />
          ))}
        </svg>
      )}
      {fills.map((f, k) => (
        <div
          key={k}
          className={cn("relative min-w-0", !drawn && cn(f.bg, height > 24 ? "rounded-[22px]" : "rounded-full"))}
        >
          {cells?.[k]}
        </div>
      ))}
    </div>
  )
}
