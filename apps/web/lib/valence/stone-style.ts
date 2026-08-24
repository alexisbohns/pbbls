// Backdrop wash and ink for one polarity of valence stone, plus the warm
// gradient the highlight stones and the highlight headline word wear. Port of
// `apps/ios/Pebbles/Features/Path/Valence/ValenceStoneStyle.swift`; the hexes
// and the mesh control points are copied from it verbatim.
//
// Mirrors the roles `EmotionPalette.pebbleFrameColors` hands a real pebble: the
// backdrop is a soft silhouette *behind* the artwork, and the ink is what the
// artwork's lines are drawn in. Highlight is the interesting one: backdrop and
// ink are the *same* gradient at two intensities, so the lines read as the
// vivid edge of a soft wash rather than as a separate colour.
//
// Web has no `MeshGradient`. The mesh is reproduced two ways from one set of
// samples — as an SVG `<pattern>` of overlapping radial gradients for the
// stones (`ValenceMeshDefs`), and as layered CSS `radial-gradient`s behind
// `background-clip: text` for the headline word. Both read the arrays below, so
// the word and its stone stay the same gradient.

import type { Polarity } from "@/lib/config/pebble-geometry"

/** An SVG fill: a paint string plus the opacity it is laid on at. */
export type Fill = { paint: string; opacity?: number }

export type StonePaint = {
  /** Fills the silhouette behind the artwork. Never stroked — the outline a
   *  stone reads as belongs to the ink, not to the backdrop. */
  backdrop: Fill
  /**
   * Set only when the two colour schemes need different materials, which is
   * highlight's resting wash and nothing else. The stone draws both and lets
   * the `.dark` cascade pick, rather than reading the theme in JS — the same
   * rule `PathStone` follows, and for the same reason (a JS-read theme desyncs
   * between the server and client render).
   */
  backdropDark?: Fill
  /** Tints the pebble artwork drawn inside the backdrop. */
  ink: Fill
}

/** The three `<pattern>` ids one picker instance owns. */
export type ValenceMeshIds = { wash: string; ink: string; selected: string }

/**
 * Selection inverts the two roles: the wash becomes the solid and the ink goes
 * pale, so the chosen stone reads as filled in rather than merely less faded
 * than its neighbours. It is the same treatment
 * `EmotionPalette.pebbleFrameColors(forIntensity: 3)` gives a hero pebble on
 * the Path — a `light` stroke over an opaque `primary` fill.
 */
export function stonePaint(
  polarity: Polarity,
  isSelected: boolean,
  mesh: ValenceMeshIds,
): StonePaint {
  if (isSelected) {
    switch (polarity) {
      case "lowlight":
        return { backdrop: { paint: "var(--muted-foreground)" }, ink: { paint: "var(--background)" } }
      case "neutral":
        return { backdrop: { paint: "var(--primary)" }, ink: { paint: "var(--primary-foreground)" } }
      case "highlight":
        // White rather than the pale accent, and against a wash taken to full
        // strength: on the resting peach the artwork had almost nothing to push
        // against.
        return { backdrop: { paint: `url(#${mesh.selected})` }, ink: { paint: "#FFFFFF" } }
    }
  }
  switch (polarity) {
    case "lowlight":
      return { backdrop: { paint: "var(--muted)" }, ink: { paint: "var(--muted-foreground)" } }
    case "neutral":
      // iOS's `AccentSurface` is `AccentPrimary` at 0.10; web carries the alpha
      // on `fill-opacity` rather than baking a second token.
      return {
        backdrop: { paint: "var(--primary)", opacity: 0.1 },
        ink: { paint: "var(--primary)" },
      }
    case "highlight":
      return {
        // Light mode keeps the sampled gradient at low opacity: over a light
        // page it stays the pastel it was sampled from.
        backdrop: { paint: `url(#${mesh.wash})`, opacity: 0.35 },
        // Dark mode cannot — the same gradient over black goes muddy and
        // opaque, and the stone ends up looking nothing like its neighbours,
        // which wear flat 10%-alpha surfaces. So it joins that convention
        // instead of fighting it, with the Joy emotion category's own
        // `surface_color` at the same 10%: a warm gold that keeps highlight
        // distinct from neutral's rose.
        backdropDark: { paint: JOY_SURFACE, opacity: 0.1 },
        ink: { paint: `url(#${mesh.ink})` },
      }
  }
}

/** Joy's `surface_color`, copied rather than read from the palette service for
 *  the same reason iOS copies it: the picker draws before the palettes load. */
const JOY_SURFACE = "#A15C08"

// ── The highlight gradient ──────────────────────────────────────────
//
// Sampled from the reference gradient at each of the mesh's own control points
// (patch-averaged, so no single noisy pixel decides a corner). Rose at the top
// left, blush across the top right, gold rising from the bottom left.
//
// Every sample lands between hue 3° and 41° — the whole thing is warm. That is
// what makes the ink below possible: a gradient this narrow can be saturated
// without becoming a rainbow, which a full-hue-wheel one cannot. Three
// references were tried before this held; see the spec's revision 5 before
// re-litigating a colour.

export const MESH_POINTS: readonly (readonly [number, number])[] = [
  [0.0, 0.0], [0.3, 0.0], [0.7, 0.0], [1.0, 0.0],
  [0.0, 0.3], [0.2, 0.4], [0.7, 0.2], [1.0, 0.3],
  [0.0, 0.7], [0.3, 0.8], [0.7, 0.6], [1.0, 0.7],
  [0.0, 1.0], [0.3, 1.0], [0.7, 1.0], [1.0, 1.0],
]

/** The resting wash. */
export const WASH_HEXES: readonly string[] = [
  "#E7928B", "#FBA78F", "#FED6C9", "#FDC0B5",
  "#EAA68F", "#F5B592", "#FDC9B6", "#FCAA9F",
  "#EFC094", "#F8CF97", "#FBB493", "#F3968C",
  "#F1CC95", "#FADB9A", "#F1B192", "#E6908B",
]

/**
 * The same gradient as ink: each sample keeps its hue and takes a fixed
 * saturation and lightness (HSL 0.90 / 0.52), which runs gold through orange to
 * coral. The wash is far too light to draw the artwork with — a stone inked in
 * it disappears against the page — so the wash fills and this twin draws.
 */
export const INK_HEXES: readonly string[] = [
  "#F32716", "#F34716", "#F34C16", "#F33816",
  "#F34E16", "#F36416", "#F35116", "#F33016",
  "#F38116", "#F39616", "#F35C16", "#F32C16",
  "#F39A16", "#F3AC16", "#F35E16", "#F32316",
]

/**
 * The wash taken up to full strength for the selected stone: the same hues at
 * HSL 0.92 / 0.58. The resting wash is too pale to carry a white outline, and
 * selection is exactly where the stone needs to shout.
 */
export const SELECTED_HEXES: readonly string[] = [
  "#F64031", "#F65D31", "#F66231", "#F64F31",
  "#F66331", "#F67731", "#F66631", "#F64931",
  "#F69131", "#F6A331", "#F67031", "#F64531",
  "#F6A731", "#F6B731", "#F67231", "#F63C31",
]

/**
 * Flat colour laid under the radial samples so the gaps between them never show
 * the page through. Averaged rather than picked so it cannot bias a corner.
 */
export function averageHex(hexes: readonly string[]): string {
  const total = hexes.reduce(
    (sum, hex) => {
      const n = parseInt(hex.slice(1), 16)
      return [sum[0] + ((n >> 16) & 0xff), sum[1] + ((n >> 8) & 0xff), sum[2] + (n & 0xff)]
    },
    [0, 0, 0],
  )
  const channel = (value: number) =>
    Math.round(value / hexes.length)
      .toString(16)
      .padStart(2, "0")
  return `#${channel(total[0])}${channel(total[1])}${channel(total[2])}`
}

/**
 * The mesh as a CSS `background-image`, for the one place that paints text
 * rather than a shape. Each sample becomes a radial stop fading to nothing, in
 * the same positions the SVG pattern uses, over the averaged base.
 */
export function meshBackgroundImage(hexes: readonly string[]): string {
  const layers = MESH_POINTS.map(
    ([x, y], i) =>
      `radial-gradient(circle at ${x * 100}% ${y * 100}%, ${hexes[i]} 0%, transparent 62%)`,
  )
  const base = averageHex(hexes)
  return [...layers, `linear-gradient(${base}, ${base})`].join(", ")
}

/**
 * Fill for the headline word naming the picked valence. Highlight carries the
 * same gradient its stone does, so the word and the stone read as one thing.
 * Lowlight goes darker than its stone ink: at headline size a grey word looks
 * disabled rather than quiet.
 */
export function headlineInk(polarity: Polarity): { color?: string; backgroundImage?: string } {
  switch (polarity) {
    case "lowlight":
      return { color: "var(--foreground)" }
    case "neutral":
      return { color: "var(--primary)" }
    case "highlight":
      return { backgroundImage: meshBackgroundImage(INK_HEXES) }
  }
}
