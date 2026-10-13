import type { Polarity, Size } from "@/lib/config/pebble-geometry"
import { SEED_CANVAS, STONE_WIDTH } from "@/lib/config/stone-geometry"
import type { Slot } from "@/lib/config/stone-tones"

// The seeded detail of one lit stone (#1052), in canvas units: the final
// mock's construction, with positions drawn from the pebble's own seed so the
// same pebble always draws the same stone and no two gems sparkle alike.
//
// - gem (highlight): five facets around a pentagon plus its face, ten glitter
//   dots, two sparkles at the light;
// - river pebble (neutral): fourteen grain specks, mostly lit, and a soft
//   diffuse light (matte, unlike the gem's shine);
// - lava (lowlight): nine pits and a faint highlight.

export type Dot = { cx: number; cy: number; r: number; opacity: number; slot: Slot }

export type Facet = {
  points: [number, number][]
  slot: Slot
  opacity: number
  /** The hairline from the face corner out along the facet's edge. */
  edge: [[number, number], [number, number]] | null
}

export type Ellipse = { cx: number; cy: number; rx: number; ry: number; rotate: number; opacity: number }

export type StoneRecipe =
  | { material: "gem"; facets: Facet[]; dots: Dot[]; sparkles: string[] }
  | { material: "pebble"; dots: Dot[]; light: Ellipse }
  | { material: "lava"; dots: Dot[]; light: Ellipse }

export type GlintGeometry = {
  sheen: Ellipse
  /** The sweep band before it travels; it crosses `travel` units to the right. */
  sweep: { x: number; y: number; width: number; height: number; rotate: number; cx: number; cy: number }
  travel: number
  spark: string
}

// The mock's face, as fractions of the canvas; the outer facet corners are the
// face corners pushed five times their distance from its centre.
const FACE: [number, number][] = [
  [0.31, 0.21],
  [0.59, 0.16],
  [0.75, 0.37],
  [0.53, 0.53],
  [0.27, 0.42],
]
const FACET_SHADE: { slot: Slot; opacity: number }[] = [
  { slot: "white", opacity: 0.2 },
  { slot: "white", opacity: 0.07 },
  { slot: "dark", opacity: 0.16 },
  { slot: "dark", opacity: 0.26 },
  { slot: "white", opacity: 0.1 },
]
/** How far a face corner may wander, as a fraction of the canvas. */
const FACE_JITTER = 0.025

type DotRule = {
  count: number
  /** Radius and opacity ranges; radius in px on the phone stone. */
  r: [number, number]
  opacity: [number, number]
  /** The box the dots fall in, as fractions of the canvas. */
  x: [number, number]
  y: [number, number]
  slot: (rand: () => number) => Slot
}

const DOTS: Record<Polarity, DotRule> = {
  highlight: { count: 10, r: [0.36, 0.87], opacity: [0.4, 0.94], x: [0.17, 0.84], y: [0.18, 0.82], slot: () => "light" },
  neutral: {
    count: 14,
    r: [0.27, 0.58],
    opacity: [0.25, 0.54],
    x: [0.12, 0.88],
    y: [0.19, 0.87],
    slot: (rand) => (rand() < 0.7 ? "light" : "shaded"),
  },
  lowlight: { count: 9, r: [0.4, 0.99], opacity: [0.27, 0.6], x: [0.18, 0.82], y: [0.19, 0.78], slot: () => "dark" },
}

/** mulberry32: a small, fast, well-mixed 32-bit PRNG. */
function random(seed: number): () => number {
  let a = seed >>> 0
  return () => {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

const between = (rand: () => number, [lo, hi]: [number, number]) => lo + rand() * (hi - lo)
const r2 = (n: number) => Math.round(n * 100) / 100

/** A four-point star, its arms bowed in to `0.16` of the radius. */
export function starPath(cx: number, cy: number, r: number): string {
  const k = r * 0.16
  const p = (x: number, y: number) => `${r2(x)} ${r2(y)}`
  return (
    `M${p(cx, cy - r)}Q${p(cx + k, cy - k)} ${p(cx + r, cy)}Q${p(cx + k, cy + k)} ${p(cx, cy + r)}` +
    `Q${p(cx - k, cy + k)} ${p(cx - r, cy)}Q${p(cx - k, cy - k)} ${p(cx, cy - r)}Z`
  )
}

export function stoneRecipe(size: Size, polarity: Polarity, seed: number): StoneRecipe {
  const { width: W, height: H } = SEED_CANVAS[size]
  const unitsPerPx = W / STONE_WIDTH[size].phone
  const rand = random(seed)

  const rule = DOTS[polarity]
  const dots: Dot[] = Array.from({ length: rule.count }, () => ({
    cx: r2(between(rand, rule.x) * W),
    cy: r2(between(rand, rule.y) * H),
    r: r2(between(rand, rule.r) * unitsPerPx),
    opacity: r2(between(rand, rule.opacity)),
    slot: rule.slot(rand),
  }))

  if (polarity === "neutral") {
    return {
      material: "pebble",
      dots,
      // Matte: a broad, dim wash on the lit side rather than a specular spot.
      light: { cx: 0.38 * W, cy: 0.3 * H, rx: 0.34 * W, ry: 0.2 * H, rotate: -16, opacity: 0.16 },
    }
  }
  if (polarity === "lowlight") {
    return {
      material: "lava",
      dots,
      light: { cx: 0.34 * W, cy: 0.24 * H, rx: 0.26 * W, ry: 0.15 * H, rotate: 0, opacity: 0.07 },
    }
  }

  const face = FACE.map(([x, y]): [number, number] => [
    r2((x + (rand() * 2 - 1) * FACE_JITTER) * W),
    r2((y + (rand() * 2 - 1) * FACE_JITTER) * H),
  ])
  const cx = face.reduce((sum, [x]) => sum + x, 0) / face.length
  const cy = face.reduce((sum, [, y]) => sum + y, 0) / face.length
  const outer = face.map(([x, y]): [number, number] => [r2(6 * x - 5 * cx), r2(6 * y - 5 * cy)])
  const facets: Facet[] = face.map((corner, i) => {
    const next = (i + 1) % face.length
    return {
      points: [corner, face[next], outer[next], outer[i]],
      ...FACET_SHADE[i],
      edge: [corner, outer[i]],
    }
  })
  facets.push({ points: face, slot: "white", opacity: 0.14, edge: null })

  return {
    material: "gem",
    facets,
    dots,
    // The two sparkles sit where the light lands; their size is the mock's, in px.
    sparkles: [
      starPath(0.33 * W, 0.23 * H, 2.65 * unitsPerPx),
      starPath(0.63 * W, 0.3 * H, 1.55 * unitsPerPx),
    ],
  }
}

/** The glint a press runs across the stone: fixed per size, in canvas units. */
export function glintGeometry(size: Size): GlintGeometry {
  const { width: W, height: H } = SEED_CANVAS[size]
  return {
    sheen: { cx: 0.38 * W, cy: 0.3 * H, rx: 0.3 * W, ry: 0.14 * H, rotate: -24, opacity: 0.9 },
    sweep: { x: -0.55 * W, y: -0.4 * H, width: 0.38 * W, height: 1.8 * H, rotate: 22, cx: W / 2, cy: H / 2 },
    travel: 1.6 * W,
    spark: starPath(0.33 * W, 0.25 * H, 0.085 * W),
  }
}
