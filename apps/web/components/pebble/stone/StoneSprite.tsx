import { getOutline } from "@/lib/config/pebble-outlines"
import type { Polarity, Size } from "@/lib/config/pebble-geometry"
import { BLUR, SEED_CANVAS, canvasScale } from "@/lib/config/stone-geometry"
import { WOBBLE_ENABLED, wobbleBackdrop } from "@/lib/wobble"
import {
  STONE_GLINT_SHEEN_ID,
  STONE_GLINT_SWEEP_ID,
  STONE_GLOW_ID,
  STONE_GRAIN_ID,
  STONE_SOFT_ID,
  stoneBlurId,
  stoneBodyId,
  stoneClipId,
  stoneHatchId,
  stoneRingId,
} from "@/lib/utils/spine/stone-ids"

const SIZES: Size[] = ["small", "medium", "large"]
const POLARITIES: Polarity[] = ["lowlight", "neutral", "highlight"]

type Body = { size: Size; polarity: Polarity; d: string; flat: string; fillRule: "evenodd" | "nonzero" }

// Wobbled once per process: the nine silhouettes are the same on every page.
// Only lava is cut rough; the river pebble and the gem keep their soft edge.
let bodies: Body[] | null = null

function stoneBodies(): Body[] {
  bodies ??= SIZES.flatMap((size) =>
    POLARITIES.map((polarity) => {
      const outline = getOutline(size, polarity)
      const wobbled = WOBBLE_ENABLED && polarity === "lowlight" ? wobbleBackdrop(outline.path, outline.width, outline.height) : null
      return { size, polarity, d: wobbled ?? outline.path, flat: outline.path, fillRule: outline.fillRule }
    }),
  )
  return bodies
}

/** 45° hatching across the canvas, 30 units apart: the engraved seal's shading. */
function hatch(size: Size): string {
  const { width, height } = SEED_CANVAS[size]
  const lines: string[] = []
  for (let x = -(height + 20); x <= width; x += 30) lines.push(`M${x} -20L${x + height + 40} ${height + 20}`)
  return lines.join("")
}

/**
 * The shared defs every lit stone and seal on the page draws from (#1052),
 * rendered once per page: the nine silhouettes (lava wobbled) and their clips, the
 * colourless blurs, the glint's paints and the seal's hatching. Anything that
 * carries a colour lives on the stone itself, because a paint server resolves
 * `var(--p)` in its own tree, not the referencing one's.
 */
export function StoneSprite() {
  return (
    <svg aria-hidden focusable="false" className="pointer-events-none absolute size-0 overflow-hidden">
      <defs>
        {stoneBodies().map(({ size, polarity, d, flat, fillRule }) => (
          <g key={`${size}-${polarity}`}>
            {/* No fill here, so each <use> paints the body its own way. The
                stroke is non-scaling here because vector-effect does not
                inherit through <use>: every stroke on the body (rings, rim,
                edge shade) is in screen px. */}
            <path
              id={stoneBodyId(size, polarity)}
              d={d}
              fillRule={fillRule}
              clipRule={fillRule}
              vectorEffect="non-scaling-stroke"
            />
            {/* Scaled up to 1.6×, the wobble would read as a crinkle, not a rake. */}
            <path id={stoneRingId(size, polarity)} d={flat} fillRule={fillRule} vectorEffect="non-scaling-stroke" />
            <clipPath id={stoneClipId(size, polarity)}>
              <use href={`#${stoneBodyId(size, polarity)}`} />
            </clipPath>
          </g>
        ))}
        {SIZES.map((size) => (
          <g key={size}>
            {/* Shadows and the edge shade are drawn in outline units: the
                mock's blurs, scaled from its canvas into this box. */}
            {(["near", "far", "ao"] as const).map((name) => (
              <filter key={name} id={stoneBlurId(name, size)} x="-60%" y="-60%" width="220%" height="220%">
                <feGaussianBlur stdDeviation={Math.round(BLUR[name] * canvasScale(size) * 100) / 100} />
              </filter>
            ))}
            <path id={stoneHatchId(size)} d={hatch(size)} vectorEffect="non-scaling-stroke" />
          </g>
        ))}
        {/* Drawn inside the canvas and carving groups, so the mock's own units. */}
        <filter id={STONE_SOFT_ID} x="-60%" y="-60%" width="220%" height="220%">
          <feGaussianBlur stdDeviation={BLUR.soft} />
        </filter>
        <filter id={STONE_GLOW_ID} x="-60%" y="-60%" width="220%" height="220%">
          <feGaussianBlur stdDeviation={BLUR.glow} />
        </filter>
        {/* Lava's pitted surface, drawn in screen px: a dark speck where the
            noise's red runs high, a pale one where its green does. Colourless
            so the stone's own body shows through between them. */}
        <filter id={STONE_GRAIN_ID} x="0" y="0" width="100%" height="100%" colorInterpolationFilters="sRGB">
          <feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves="2" seed="7" result="noise" />
          <feColorMatrix
            in="noise"
            values="0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  6 0 0 0 -3.5"
            result="dark"
          />
          <feColorMatrix
            in="noise"
            values="0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  0 4 0 0 -2.75"
            result="light"
          />
          <feMerge>
            <feMergeNode in="dark" />
            <feMergeNode in="light" />
          </feMerge>
        </filter>
        <linearGradient id={STONE_GLINT_SWEEP_ID} x1="0" y1="0" x2="1" y2="0">
          <stop offset="0" stopColor="#fff" stopOpacity="0" />
          <stop offset="0.5" stopColor="#fff" stopOpacity="0.72" />
          <stop offset="1" stopColor="#fff" stopOpacity="0" />
        </linearGradient>
        <radialGradient id={STONE_GLINT_SHEEN_ID}>
          <stop offset="0" stopColor="#fff" stopOpacity="0.75" />
          <stop offset="1" stopColor="#fff" stopOpacity="0" />
        </radialGradient>
      </defs>
    </svg>
  )
}
