import { polar, r1, type Segment } from "@/lib/achievements/geometry"
import type { YearMonth } from "@/lib/types"

// The docked year disc (#1308, spec D12; the boards' g/112 and
// `yearDiscDocked`, treatment B): a stone 220 units across on (120, 120), a
// spoke a month at 30° × (month − 1) from twelve, notches on the inner disc
// (50) and the rim (80). A month's counter docks at the radius its served
// level names (30, 50, 80), and its served progress runs along the spoke.
// Only a month the server says is upcoming or quiet is muted; a kept month
// wears its family's hue, or ink when the family is absent or unresolved; a
// rim month adds the hairline ring, a hued spoke and a hued numeral. iOS
// `YearDiscGeometry` and `YearDisc`, Android the same.

export const DISC_BOX = { x: 10, y: 10, width: 220, height: 220 } as const
export const DISC_CENTRE = 120
/** Level → radius: the centre, the inner disc, the rim. */
export const DISC_RADII = [30, 50, 80] as const
const SPOKE = [14, 88] as const
const NUMERAL_RADIUS = 96
export const NUMERALS = ["I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII"] as const
/** A counter's seal width. */
export const COUNTER_WIDTH = 12.5

/** The stone's edge (the boards' g/112 outline). */
export const STONE_EDGE =
  "M225.1,120.0 L223.0,136.3 L217.4,151.6 L211.9,166.8 L205.0,181.7 L192.4,192.4 L181.0,203.9 L167.6,213.5 L151.9,218.2 L136.1,221.4 L120.0,225.2 L103.6,223.5 L87.6,219.6 L72.9,212.5 L58.7,204.4 L46.3,193.7 L34.7,182.0 L27.8,167.0 L20.4,152.4 L17.4,136.3 L15.6,120.0 L16.7,103.6 L20.4,87.6 L27.0,72.6 L36.8,59.6 L45.4,45.4 L58.2,35.0 L73.4,28.6 L88.0,21.5 L103.9,18.3 L120.0,14.9 L136.0,18.8 L152.0,21.7 L167.3,27.1 L180.7,36.5 L192.8,47.2 L203.3,59.5 L213.5,72.4 L220.4,87.4 L222.5,103.8 Z"

/** The stone's grain, the boards' forty dots verbatim (`YEARBASE`): x, y, r. */
export const STONE_GRAIN: readonly (readonly [number, number, number])[] = [
  [217.6, 135.5, 0.7], [25.8, 134.9, 0.8], [105.0, 25.2, 0.6], [37.7, 179.8, 0.9], [25.9, 134.9, 0.8],
  [189.4, 50.6, 0.8], [209.8, 74.2, 0.7], [89.4, 25.9, 0.9], [52.0, 52.0, 0.8], [25.7, 105.1, 0.8],
  [217.5, 135.4, 0.8], [197.8, 63.5, 0.8], [75.7, 207.0, 0.9], [150.1, 27.3, 0.7], [25.1, 135.0, 0.6],
  [135.0, 214.5, 0.5], [89.0, 24.5, 0.9], [75.3, 32.2, 0.5], [25.7, 134.9, 0.6], [63.8, 42.6, 0.7],
  [176.8, 41.8, 0.5], [120.0, 18.6, 0.8], [105.1, 26.1, 0.6], [197.9, 176.6, 0.5], [120.0, 23.9, 0.9],
  [164.2, 206.7, 0.5], [42.2, 176.5, 0.9], [178.3, 200.2, 0.7], [89.6, 213.6, 0.7], [204.2, 162.9, 0.5],
  [207.2, 75.6, 0.8], [176.1, 42.8, 0.8], [120.0, 219.1, 0.5], [219.4, 104.3, 0.7], [90.6, 29.7, 0.6],
  [90.5, 210.8, 0.9], [216.3, 88.7, 0.6], [120.0, 220.0, 0.5], [135.4, 22.6, 0.7], [20.7, 135.7, 0.5],
]

/** How a month is toned: muted (no colour), or kept, in its family's hue (`family`, ink when it does not resolve). */
export type MonthTone = { muted: true } | { muted: false; family: string | null }

export type DiscMonth = {
  month: number
  tone: MonthTone
  spoke: Segment & { width: number; hued: boolean }
  /** The inner disc's notch, then the rim's, each with its weight. */
  notches: [Segment & { width: number }, Segment & { width: number }]
  /** The goal on the inner disc, in ink, for a month begun at the centre. */
  goal: Segment | null
  /** The way travelled: the whole climb for a rim month, else the progress. `hued` takes the month's tone. */
  climb: (Segment & { hued: boolean }) | null
  counter: { x: number; y: number; ring: boolean; print: "solid" | "blank"; dashed: boolean }
  numeral: { x: number; y: number; text: string; size: number; emphasized: boolean }
}

function point(r: number, deg: number): [number, number] {
  const [x, y] = polar(r, deg)
  return [r1(x + DISC_CENTRE), r1(y + DISC_CENTRE)]
}

function segment(p: [number, number], q: [number, number]): Segment {
  return { x1: p[0], y1: p[1], x2: q[0], y2: q[1] }
}

/** The notch across a spoke at `r`, 6 long. */
function notch(r: number, deg: number): Segment {
  const [x, y] = polar(r, deg)
  const rad = (deg * Math.PI) / 180
  const [dx, dy] = [3 * Math.cos(rad), 3 * Math.sin(rad)]
  return segment([r1(x + DISC_CENTRE - dx), r1(y + DISC_CENTRE - dy)], [r1(x + DISC_CENTRE + dx), r1(y + DISC_CENTRE + dy)])
}

/** A month's place and marks on the disc, read off its served state, family, level and progress. */
export function discMonth(m: YearMonth): DiscMonth {
  const deg = (m.month - 1) * 30
  const muted = m.state !== "kept"
  const tone: MonthTone = muted ? { muted: true } : { muted: false, family: m.family }
  const level = m.level
  const rim = level === 2 && !muted
  let climb: DiscMonth["climb"] = null
  if (level === 2) climb = { ...segment(point(DISC_RADII[1] + 6, deg), point(DISC_RADII[2] - 7, deg)), hued: true }
  else if (m.progress > 0) {
    const from = DISC_RADII[level]
    const gap = DISC_RADII[level + 1] - from - 12
    // Toward the inner disc in ink (moments have no colour); from it on, in the month's tone.
    climb = { ...segment(point(from + 6, deg), point(from + 6 + m.progress * gap, deg)), hued: level === 1 }
  }
  const [cx, cy] = point(DISC_RADII[level], deg)
  const [nx, ny] = polar(NUMERAL_RADIUS, deg)
  const text = NUMERALS[m.month - 1] ?? String(m.month)
  return {
    month: m.month,
    tone,
    spoke: { ...segment(point(SPOKE[0], deg), point(SPOKE[1], deg)), width: rim ? 1 : muted ? 0.4 : 0.7, hued: rim },
    notches: [
      { ...notch(DISC_RADII[1], deg), width: muted ? 0.6 : 1 },
      { ...notch(DISC_RADII[2], deg), width: muted || level < 2 ? 0.6 : 1 },
    ],
    goal: level === 0 && m.progress > 0 && !muted ? segment(point(DISC_RADII[1], deg + 4.6), point(DISC_RADII[1], deg - 4.6)) : null,
    climb,
    counter: { x: cx, y: cy, ring: !muted && level === 2, print: muted ? "blank" : "solid", dashed: muted && m.state === "upcoming" },
    // The baseline 2.5 below the point, centred on it (the boards' numerals).
    numeral: { x: r1(nx + DISC_CENTRE), y: r1(ny + DISC_CENTRE + 2.5), text, size: text.length >= 3 ? 6 : 7, emphasized: rim },
  }
}
