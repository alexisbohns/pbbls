"use client"

import { useId, type CSSProperties } from "react"
import { getOutline } from "@/lib/config/pebble-outlines"
import type { Polarity, Size } from "@/lib/config/pebble-geometry"
import {
  LIP_PX,
  SHADOW_FAR_PX,
  SHADOW_NEAR_PX,
  canvasTransform,
  carveTransform,
  glyphSide,
  glyphTransform,
  ringGeometry,
  unitsPerPx,
} from "@/lib/config/stone-geometry"
import { STONE_CARVINGS, type StoneCarving } from "@/lib/config/stone-carvings"
import {
  BLANK_PALETTE,
  BODY_RAMP,
  EDGE_SHADE,
  FOSSIL_TONES,
  GLINT_STRENGTH,
  GLYPH_TONES,
  SLOT_COLOR,
  VEIN_TONES,
  stonePaletteVars,
  type Paint,
  type StonePalette,
} from "@/lib/config/stone-tones"
import type { PebbleGlyph } from "@/lib/types"
import { glintGeometry, stoneRecipe, type Dot, type Ellipse } from "@/lib/utils/spine/stone-recipe"
import {
  STONE_GLINT_SHEEN_ID,
  STONE_GLINT_SWEEP_ID,
  STONE_GLOW_ID,
  STONE_SOFT_ID,
  stoneBlurId,
  stoneBodyId,
  stoneClipId,
  stoneRingId,
} from "@/lib/utils/spine/stone-ids"
import { cn } from "@/lib/utils"
import { StoneGlyph } from "./StoneGlyph"

type LitStoneProps = {
  size: Size
  polarity: Polarity
  /**
   * The emotion's palette. Null is a moment with no emotion yet (the moments
   * split): a blank river pebble in grey, uncarved, whatever its polarity.
   */
  palette: StonePalette | null
  glyph: PebbleGlyph | null
  /** `hashUUID(pebble.id)`: the same pebble always draws the same stone. */
  seed: number
  className?: string
}

const WIDTH_CLASS: Record<Size, string> = {
  small: "w-[34px] spread:w-[40px]",
  medium: "w-[44px] spread:w-[52px]",
  large: "w-[54px] spread:w-[58px]",
}

/**
 * The stone on the margin line (#1052): the final mock's SVG render over the
 * iOS geometry (spec D3, D5). The rings and the stone are sibling SVGs over
 * one box, so a press can dip the stone's HTML wrapper (`data-stone-press`,
 * compositor-only) and swell the rings (`data-stone-rings`) without
 * re-rasterising the filtered stone. The glint (`data-stone-sheen`,
 * `-sweep`, `-spark`) rests invisible until a press animates it. Nothing here
 * animates on its own.
 *
 * Needs `StoneSprite` once on the page. Decorative: the pressable wrapper
 * carries the label.
 */
export function LitStone({ size, polarity: cut, palette, glyph, seed, className }: LitStoneProps) {
  const gradientId = useId()
  const blank = palette === null
  const polarity: Polarity = blank ? "neutral" : cut
  const outline = getOutline(size, polarity)
  const body = `#${stoneBodyId(size, polarity)}`
  const raked = `#${stoneRingId(size, polarity)}`
  const clip = `url(#${stoneClipId(size, polarity)})`
  const upx = unitsPerPx(size)
  const canvas = canvasTransform(size, polarity)
  const carve = carveTransform(size, polarity)
  const carving = STONE_CARVINGS[size][polarity]
  const recipe = stoneRecipe(size, polarity, seed)
  const glint = glintGeometry(size)
  const { rings, halo } = ringGeometry(size, polarity)
  const viewBox = `0 0 ${outline.width} ${outline.height}`
  const { lip, ink } = GLYPH_TONES[polarity]

  const style = {
    ...stonePaletteVars(palette ?? BLANK_PALETTE),
    aspectRatio: `${outline.width} / ${outline.height}`,
  } as CSSProperties

  return (
    <span className={cn("relative block", WIDTH_CLASS[size], className)} style={style} data-polarity={polarity}>
      <svg
        aria-hidden
        focusable="false"
        viewBox={viewBox}
        className="absolute inset-0 size-full overflow-visible"
        data-stone-rings=""
        style={{ transformOrigin: "center" }}
      >
        <use href={raked} transform={halo} fill="var(--spine-halo)" />
        {rings.map((matrix) => (
          <use
            key={matrix}
            href={raked}
            transform={matrix}
            fill="none"
            stroke="var(--spine-ring)"
            strokeWidth="1px"
            className="transition-[stroke] duration-200"
            data-stone-ring=""
          />
        ))}
      </svg>
      <span className="absolute inset-0" data-stone-press="" style={{ transformOrigin: "center" }}>
        <svg
          aria-hidden
          focusable="false"
          viewBox={viewBox}
          className="block size-full overflow-visible"
          style={{ filter: polarity === "lowlight" ? "var(--spine-lava-filter)" : "var(--spine-stone-filter)" }}
        >
          <defs>
            <radialGradient id={gradientId} cx=".38" cy=".3" r=".9" fx=".34" fy=".24">
              {BODY_RAMP[polarity].map((stop) => (
                <stop key={stop.offset} offset={stop.offset} style={{ stopColor: stop.color }} />
              ))}
            </radialGradient>
          </defs>

          <use
            href={body}
            transform={`translate(${SHADOW_FAR_PX.x * upx} ${SHADOW_FAR_PX.y * upx})`}
            filter={`url(#${stoneBlurId("far", size)})`}
            style={{ fill: "rgb(var(--spine-shadow-rgb))", opacity: "var(--spine-shadow-a)" }}
          />
          <use
            href={body}
            transform={`translate(${SHADOW_NEAR_PX.x * upx} ${SHADOW_NEAR_PX.y * upx})`}
            filter={`url(#${stoneBlurId("near", size)})`}
            style={{ fill: "rgb(var(--spine-shadow-rgb))", opacity: "calc(var(--spine-shadow-a) * 1.35)" }}
          />

          <use href={body} fill={`url(#${gradientId})`} />
          <g clipPath={clip}>
            {recipe.material === "gem" && (
              <g transform={canvas}>
                {recipe.facets.map((facet, i) => (
                  <g key={i}>
                    <polygon
                      points={facet.points.flat().join(" ")}
                      fill={SLOT_COLOR[facet.slot]}
                      opacity={facet.opacity}
                      {...(facet.edge
                        ? {}
                        : { stroke: "var(--l)", strokeOpacity: 0.5, strokeWidth: 0.8, vectorEffect: "non-scaling-stroke" })}
                    />
                    {facet.edge && (
                      <path
                        d={`M${facet.edge[0].join(" ")}L${facet.edge[1].join(" ")}`}
                        stroke="var(--l)"
                        strokeOpacity={0.3}
                        strokeWidth={0.8}
                        vectorEffect="non-scaling-stroke"
                      />
                    )}
                  </g>
                ))}
              </g>
            )}

            {recipe.material === "lava" && <Dots dots={recipe.dots} transform={canvas} />}

            {!blank && (
              <g transform={carve}>
                {recipe.material === "lava" && <Fossil carving={carving.fossil} paints={FOSSIL_TONES[polarity]} />}
                {carving.veins.map((vein, i) =>
                  VEIN_TONES[polarity].map((paint, k) => (
                    <path
                      key={`${i}-${k}`}
                      d={vein.d}
                      fill="none"
                      stroke={paint.color}
                      strokeWidth={paint.width}
                      strokeOpacity={paint.opacity}
                      strokeLinecap={polarity === "lowlight" ? "round" : undefined}
                      transform={offset(paint)}
                      filter={paint.glow ? `url(#${STONE_GLOW_ID})` : undefined}
                    />
                  )),
                )}
                {recipe.material !== "lava" && <Fossil carving={carving.fossil} paints={FOSSIL_TONES[polarity]} />}
              </g>
            )}

            {recipe.material !== "lava" && <Dots dots={recipe.dots} transform={canvas} />}
            {recipe.material === "gem" && (
              <g transform={canvas}>
                {recipe.sparkles.map((d) => (
                  <path key={d} d={d} fill="#fff" opacity={0.92} />
                ))}
              </g>
            )}
            {recipe.material !== "gem" && <SoftLight light={recipe.light} transform={canvas} />}

            <use
              href={body}
              fill="none"
              stroke={EDGE_SHADE[polarity].color}
              strokeWidth={`${EDGE_SHADE[polarity].width ?? 5}px`}
              strokeOpacity={EDGE_SHADE[polarity].opacity}
              filter={`url(#${stoneBlurId("ao", size)})`}
            />

            {glyph && !blank && (
              <>
                <g transform={`translate(${LIP_PX.x * upx} ${LIP_PX.y * upx})`}>
                  <g transform={carve}>
                    <g transform={glyphTransform(size, polarity, glyphSide(glyph.viewBox))}>
                      <StoneGlyph strokes={glyph.strokes} stroke={lip.color} strokeOpacity={lip.opacity} />
                    </g>
                  </g>
                </g>
                <g transform={carve}>
                  <g transform={glyphTransform(size, polarity, glyphSide(glyph.viewBox))}>
                    <StoneGlyph strokes={glyph.strokes} stroke={ink.color} strokeOpacity={ink.opacity} />
                  </g>
                </g>
              </>
            )}
          </g>

          <g clipPath={clip} pointerEvents="none" opacity={GLINT_STRENGTH[polarity]}>
            <g transform={canvas}>
              <ellipse
                cx={glint.sheen.cx}
                cy={glint.sheen.cy}
                rx={glint.sheen.rx}
                ry={glint.sheen.ry}
                transform={`rotate(${glint.sheen.rotate} ${glint.sheen.cx} ${glint.sheen.cy})`}
                fill={`url(#${STONE_GLINT_SHEEN_ID})`}
                opacity={0}
                data-stone-sheen=""
              />
              <g transform={`rotate(${glint.sweep.rotate} ${glint.sweep.cx} ${glint.sweep.cy})`}>
                <rect
                  x={glint.sweep.x}
                  y={glint.sweep.y}
                  width={glint.sweep.width}
                  height={glint.sweep.height}
                  fill={`url(#${STONE_GLINT_SWEEP_ID})`}
                  data-stone-sweep={glint.travel}
                />
              </g>
              <path
                d={glint.spark}
                fill="#fff"
                opacity={0}
                style={{ transform: "scale(0)", transformBox: "fill-box", transformOrigin: "center" }}
                data-stone-spark=""
              />
            </g>
          </g>
        </svg>
      </span>
    </span>
  )
}

function offset(paint: Paint): string | undefined {
  return paint.dx || paint.dy ? `translate(${paint.dx ?? 0} ${paint.dy ?? 0})` : undefined
}

function Dots({ dots, transform }: { dots: Dot[]; transform: string }) {
  return (
    <g transform={transform}>
      {dots.map((dot, i) => (
        <circle key={i} cx={dot.cx} cy={dot.cy} r={dot.r} fill={SLOT_COLOR[dot.slot]} opacity={dot.opacity} />
      ))}
    </g>
  )
}

function Fossil({ carving, paints }: { carving: StoneCarving["fossil"]; paints: Paint[] }) {
  if (!carving) return null
  return paints.map((paint, i) => (
    <path
      key={i}
      d={carving.d}
      fillRule={carving.fillRule}
      fill={paint.color}
      opacity={paint.opacity}
      transform={offset(paint)}
    />
  ))
}

function SoftLight({ light, transform }: { light: Ellipse; transform: string }) {
  return (
    <g transform={transform}>
      <ellipse
        cx={light.cx}
        cy={light.cy}
        rx={light.rx}
        ry={light.ry}
        fill="#fff"
        opacity={light.opacity}
        filter={`url(#${STONE_SOFT_ID})`}
        transform={light.rotate ? `rotate(${light.rotate} ${light.cx} ${light.cy})` : undefined}
      />
    </g>
  )
}
