"use client"

import {
  averageHex,
  INK_HEXES,
  MESH_POINTS,
  SELECTED_HEXES,
  WASH_HEXES,
  type ValenceMeshIds,
} from "@/lib/valence/stone-style"

/**
 * The three highlight gradients, as SVG paints the stones can fill with.
 *
 * iOS draws them with `MeshGradient`, which has no web equivalent. What
 * reproduces it is the shape a mesh gradient already is: a colour sample at
 * each control point, bleeding into its neighbours. Each sample becomes a
 * radial gradient fading to nothing, laid over a flat average so the gaps
 * between them never show the page through, and the whole thing is wrapped in a
 * `<pattern>` in object-bounding-box units — so it stretches with whatever stone
 * fills with it, exactly as the mesh does.
 *
 * Rendered once per picker instance, with instance-scoped ids: two pickers on
 * one page (the record step and an open sheet) must not fight over `#…`.
 */
export function ValenceMeshDefs({ ids }: { ids: ValenceMeshIds }) {
  return (
    <svg aria-hidden focusable="false" className="pointer-events-none absolute size-0">
      <defs>
        <Mesh id={ids.wash} hexes={WASH_HEXES} />
        <Mesh id={ids.ink} hexes={INK_HEXES} />
        <Mesh id={ids.selected} hexes={SELECTED_HEXES} />
      </defs>
    </svg>
  )
}

/**
 * Sample radius, in bounding-box units. The control points sit ~0.33 apart, so
 * this overlaps every sample with its neighbours — which is what turns sixteen
 * discs into one continuous wash rather than a polka dot.
 */
const SAMPLE_RADIUS = 0.5

function Mesh({ id, hexes }: { id: string; hexes: readonly string[] }) {
  return (
    <>
      {MESH_POINTS.map((_, i) => (
        <radialGradient key={i} id={`${id}-${i}`}>
          <stop offset="0%" stopColor={hexes[i]} stopOpacity={1} />
          <stop offset="100%" stopColor={hexes[i]} stopOpacity={0} />
        </radialGradient>
      ))}
      <pattern id={id} width="1" height="1" patternContentUnits="objectBoundingBox">
        <rect width="1" height="1" fill={averageHex(hexes)} />
        {MESH_POINTS.map(([x, y], i) => (
          <ellipse key={i} cx={x} cy={y} rx={SAMPLE_RADIUS} ry={SAMPLE_RADIUS} fill={`url(#${id}-${i})`} />
        ))}
      </pattern>
    </>
  )
}
