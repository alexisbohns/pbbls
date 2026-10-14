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
  /**
   * An outline drawn at full opacity around the silhouette (the blank stone,
   * R4). Off by default: the fill-only backdrop every pebble sits on.
   */
  stroke?: string
  className?: string
}

/**
 * Renders the per-(size × polarity) pebble silhouette behind the composed
 * pebble artwork. Fill-only unless `stroke` is set. The web port of iOS
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
  stroke,
  className,
}: PebbleOutlineBackdropProps) {
  const { path, width, height, fillRule } = getOutline(size, polarity)
  // Petroglyph wobble (#555): dev-only closed-contour displacement of the
  // silhouette. `fillRule` is untouched, so the evenodd `large-lowlight` hole
  // still carves. Falls back to the flat path on parse failure.
  const wobbled = WOBBLE_ENABLED ? wobbleBackdrop(path, width, height) : null
  // Stroked, the alpha moves from the whole svg onto the fill so the outline
  // stays opaque; unstroked, the backdrop renders exactly as it always has.
  const stroked = stroke !== undefined
  return (
    <svg
      aria-hidden
      viewBox={`0 0 ${width} ${height}`}
      preserveAspectRatio="xMidYMid meet"
      overflow={stroked ? "visible" : undefined}
      className={cn("size-full", className)}
      style={stroked ? undefined : { opacity: fillOpacity }}
    >
      <path
        d={wobbled ?? path}
        fill={fillColor}
        fillRule={fillRule}
        {...(stroked && {
          fillOpacity,
          stroke,
          strokeWidth: 1.5,
          strokeLinejoin: "round" as const,
          vectorEffect: "non-scaling-stroke",
        })}
      />
    </svg>
  )
}
