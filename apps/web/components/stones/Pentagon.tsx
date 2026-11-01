import type { ReactNode } from "react"
import { PENTAGON_BOX, PENTAGON_PATH } from "@/lib/stones/pentagon"
import { cn } from "@/lib/utils"

/**
 * The avatar's squircle pentagon (#1384, spec D7): a fixed 128 × 122 box
 * filled primary-container, its children centred and nudged 8 px down to sit
 * in the shape's optical centre.
 */
export function Pentagon({ className, children }: { className?: string; children?: ReactNode }) {
  const { width, height } = PENTAGON_BOX
  return (
    <div
      className={cn("relative isolate grid place-items-center text-m3-on-primary-container", className)}
      style={{ width, height }}
    >
      <svg
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 -z-10 overflow-visible"
        width={width}
        height={height}
        viewBox={`0 0 ${width} ${height}`}
      >
        <path d={PENTAGON_PATH} className="fill-m3-primary-container" />
      </svg>
      <div className="mt-2 grid place-items-center">{children}</div>
    </div>
  )
}
