import type { CSSProperties, ReactNode } from "react"
import { stonePaletteVars, type StonePalette } from "@/lib/config/stone-tones"

/**
 * Sets an emotion family's palette on an SVG group (#1308): inside it,
 * `var(--spine-spot)` is the family's hue (primary on Paper, secondary at
 * Night) and `var(--spine-deep)` its deep ink, and `--p --s --sh` its tones.
 * Without a palette the children draw as they are, in ink.
 */
export function HueGroup({ palette, children }: { palette: StonePalette | null; children: ReactNode }) {
  if (!palette) return <g>{children}</g>
  return (
    <g className="spine-spot" style={stonePaletteVars(palette) as CSSProperties}>
      {children}
    </g>
  )
}
