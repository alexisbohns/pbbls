import { getOutline } from "@/lib/config/pebble-outlines"
import type { Size, Polarity } from "@/lib/config/pebble-geometry"
import { WOBBLE_ENABLED, wobbleBackdrop } from "@/lib/wobble"
import { cn } from "@/lib/utils"

type PebbleOutlineBackdropProps = {
  size: Size
  polarity: Polarity
  // 6-digit `#RRGGBB`; the alpha rides separately on `fillOpacity`.
  fillColor: string
  fillOpacity: number
  className?: string
}

/**
 * Renders the per-(size × polarity) pebble silhouette behind the composed
 * pebble artwork. Fill-only, no stroke. The web port of iOS
 * `PebbleOutlineBackdropView`: the silhouette path is real JSX (no
 * `dangerouslySetInnerHTML`, no `#FF00FF` sentinel-swap) so `fill` is a prop.
 *
 * `preserveAspectRatio="xMidYMid meet"` centers and fits the silhouette in the
 * proposed box, like SVGView's `.aspectRatio(.fit)`. Accessibility-hidden — the
 * parent pebble already carries the label.
 */
export function PebbleOutlineBackdrop({
  size,
  polarity,
  fillColor,
  fillOpacity,
  className,
}: PebbleOutlineBackdropProps) {
  const { path, width, height, fillRule } = getOutline(size, polarity)
  // Petroglyph wobble (#555): dev-only closed-contour displacement of the
  // silhouette. `fillRule` is untouched, so the evenodd `large-lowlight` hole
  // still carves. Falls back to the flat path on parse failure.
  const wobbled = WOBBLE_ENABLED ? wobbleBackdrop(path, width, height) : null
  return (
    <svg
      aria-hidden
      viewBox={`0 0 ${width} ${height}`}
      preserveAspectRatio="xMidYMid meet"
      className={cn("size-full", className)}
      style={{ opacity: fillOpacity }}
    >
      <path d={wobbled ?? path} fill={fillColor} fillRule={fillRule} />
    </svg>
  )
}
