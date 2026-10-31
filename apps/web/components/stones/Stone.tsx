"use client"

import type { ReactNode } from "react"
import { useElementSize } from "@/lib/hooks/useElementSize"
import { cachedStone, stoneKey } from "@/lib/stones/stone-cache"
import { stonePath } from "@/lib/stones/stone-path"
import { cn } from "@/lib/utils"
import type { StoneFill } from "./stone-fill"

type StoneProps = {
  seed: string
  fill: StoneFill
  corner?: number
  bow?: number
  className?: string
  children?: ReactNode
}

/**
 * One stone behind its children (#1384, spec D7), sized by its box. Until the
 * box is measured it paints the same fill with a plain rounded radius, so
 * nothing shifts and the server render is never empty.
 */
export function Stone({ seed, fill, corner, bow, className, children }: StoneProps) {
  const [ref, size] = useElementSize<HTMLDivElement>()
  const path =
    size && size.width > 0 && size.height > 0
      ? cachedStone("stone", stoneKey(seed, size.width, size.height, [corner, bow]), () =>
          stonePath(size.width, size.height, seed, { corner, bow }),
        )
      : null

  return (
    <div ref={ref} className={cn("relative isolate", !path && cn("rounded-[22px]", fill.bg), className)}>
      {path && size && (
        <svg
          aria-hidden="true"
          className="pointer-events-none absolute inset-0 -z-10 overflow-visible"
          width={size.width}
          height={size.height}
          viewBox={`0 0 ${size.width} ${size.height}`}
        >
          <path d={path} className={fill.fill} />
        </svg>
      )}
      {children}
    </div>
  )
}
