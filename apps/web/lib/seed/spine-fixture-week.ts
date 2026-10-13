import type { Mark, Pebble, PebbleCover, Soul } from "@/lib/types"
import { emotionId } from "./sandbox-palettes"

// ---------------------------------------------------------------------------
// The Spine's fixture week (#1052): the design brief's week of Monday 5 to
// Sunday 11 October 2026, verbatim, so the design page can be read against the
// final mock. To it, as the #720 lesson asks (a fixture must take every branch
// the product takes):
// - Friday is left empty, its one pebble moved to Saturday morning: a *still* day;
// - Bruno's Sunday pebble has no glyph: an uncarved stone;
// - a fourteen-person picnic on Thursday: the people line's overflow;
// - an untitled photo on Saturday: a moment from the split with no name.
//
// Times are Paris local (summer time). Photos are the committed
// `public/sandbox/` files, signed URL and thumb alike, so the card's ratio probe
// runs for real.
// ---------------------------------------------------------------------------

const STAMP = "2026-10-01T00:00:00Z"

/** The brief's six glyphs, 200 × 200, engine stroke width. */
export const SPINE_FIXTURE_MARKS: Mark[] = [
  {
    id: "glyph-identity",
    name: "identity",
    viewBox: "0 0 200 200",
    strokes: [
      { d: "M48.67,134.33 Q62.53,106.07 70.265,93.8 Q78,81.53 83.6,80.6 Q89.2,79.67 98.66499999999999,86.2 Q108.13,92.73 113.865,93.4 Q119.6,94.07 129.2,79.27 Q138.8,64.47 155.865,47.67 Q172.93,30.87 180.66500000000002,54.47 Q188.4,78.07 200,96.735 L211.6,115.4", width: 6 },
      { d: "M152.67,64.2 Q153.47,72.2 155.87,76.6 Q158.27,81 159.335,81 Q160.4,81 163.465,75.4 Q166.53,69.8 172,73.13499999999999 Q177.47,76.47 180.4,76.6 L183.33,76.73", width: 6 },
      { d: "M72.13,41 Q81.47,42.33 84,39.8 L86.53,37.27", width: 6 },
      { d: "M36.67,69.53 Q47.6,70.33 50.135000000000005,72.33 Q52.67,74.33 60.535000000000004,69.66499999999999 L68.4,65", width: 6 },
      { d: "M105.47,123.67 L107.07,152.47", width: 6 },
      { d: "M101.2,129 Q107.33,133 109.86500000000001,131.135 L112.4,129.27", width: 6 },
      { d: "M95.6,135.67 Q104.67,140.47 106.67,140.47 Q108.67,140.47 111.47,138.33499999999998 L114.27,136.2", width: 6 },
      { d: "M94,145.27 Q91.6,145.53 98.13499999999999,149.4 Q104.67,153.27 110.935,150.335 L117.2,147.4", width: 6 },
      { d: "M161.2,128.47 L161.47,154.6", width: 6 },
      { d: "M157.47,132.73 Q162.8,138.07 164.4,134.6 L166,131.13", width: 6 },
      { d: "M152.13,140.73 Q156.93,145 160.265,144.735 Q163.6,144.47 165.735,142.6 L167.87,140.73", width: 6 },
      { d: "M149.2,151.4 Q156.67,152.2 160.26999999999998,154.2 Q163.87,156.2 169.2,152.865 L174.53,149.53", width: 6 },
    ],
    created_at: STAMP,
    updated_at: STAMP,
  },
  {
    id: "glyph-travel",
    name: "travel",
    viewBox: "0 0 200 200",
    strokes: [
      { d: "M48.67,134.33 Q62.53,106.07 70.265,93.8 Q78,81.53 83.6,80.6 Q89.2,79.67 98.66499999999999,86.2 Q108.13,92.73 113.865,93.4 Q119.6,94.07 129.2,79.27 Q138.8,64.47 155.865,47.67 Q172.93,30.87 180.66500000000002,54.47 Q188.4,78.07 200,96.735 L211.6,115.4", width: 6 },
      { d: "M152.67,64.2 Q153.47,72.2 155.87,76.6 Q158.27,81 159.335,81 Q160.4,81 163.465,75.4 Q166.53,69.8 172,73.13499999999999 Q177.47,76.47 180.4,76.6 L183.33,76.73", width: 6 },
      { d: "M72.13,41 Q81.47,42.33 84,39.8 L86.53,37.27", width: 6 },
      { d: "M36.67,69.53 Q47.6,70.33 50.135000000000005,72.33 Q52.67,74.33 60.535000000000004,69.66499999999999 L68.4,65", width: 6 },
      { d: "M105.47,123.67 L107.07,152.47", width: 6 },
      { d: "M101.2,129 Q107.33,133 109.86500000000001,131.135 L112.4,129.27", width: 6 },
      { d: "M95.6,135.67 Q104.67,140.47 106.67,140.47 Q108.67,140.47 111.47,138.33499999999998 L114.27,136.2", width: 6 },
      { d: "M94,145.27 Q91.6,145.53 98.13499999999999,149.4 Q104.67,153.27 110.935,150.335 L117.2,147.4", width: 6 },
      { d: "M161.2,128.47 L161.47,154.6", width: 6 },
      { d: "M157.47,132.73 Q162.8,138.07 164.4,134.6 L166,131.13", width: 6 },
      { d: "M152.13,140.73 Q156.93,145 160.265,144.735 Q163.6,144.47 165.735,142.6 L167.87,140.73", width: 6 },
      { d: "M149.2,151.4 Q156.67,152.2 160.26999999999998,154.2 Q163.87,156.2 169.2,152.865 L174.53,149.53", width: 6 },
    ],
    created_at: STAMP,
    updated_at: STAMP,
  },
  {
    id: "glyph-friends",
    name: "friends",
    viewBox: "0 0 200 200",
    strokes: [
      { d: "M15.44,87.11 Q29.89,100.44 42.335,106 Q54.78,111.56 68.33500000000001,111.225 Q81.89,110.89 100.78,103.66499999999999 Q119.67,96.44 128,94.775 Q136.33,93.11 150.885,93.11 Q165.44,93.11 169.32999999999998,94.66499999999999 Q173.22,96.22 177.11,100.11 L181,104", width: 6 },
      { d: "M54.11,164.22 Q54.33,96.22 57.665,87.22 Q61,78.22 65.11,77.33 Q69.22,76.44 74.66499999999999,94 Q80.11,111.56 84.11,138.89 L88.11,166.22", width: 6 },
      { d: "M119.22,158.67 Q121,87.78 124,75.78 Q127,63.78 130.33499999999998,64.78 Q133.67,65.78 137.225,72.225 Q140.78,78.67 144.445,102.89 Q148.11,127.11 148.775,141.11 L149.44,155.11", width: 6 },
      { d: "M59,56.22 Q69.44,56 69.66499999999999,60.22 Q69.89,64.44 66.555,65.555 Q63.22,66.67 62.445,60.89 Q61.67,55.11 67.33500000000001,44.775 L73,34.44", width: 6 },
      { d: "M136.33,41.78 Q127.89,46.22 126.33500000000001,49 Q124.78,51.78 130.445,52.225 Q136.11,52.67 137.11,48.78 Q138.11,44.89 130.445,39 Q122.78,33.11 118.445,32.555 L114.11,32", width: 6 },
    ],
    created_at: STAMP,
    updated_at: STAMP,
  },
  {
    id: "glyph-family",
    name: "family",
    viewBox: "0 0 200 200",
    strokes: [
      { d: "M42.78,151.33 Q71.44,143.33 94,140.775 Q116.56,138.22 130.335,138.555 Q144.11,138.89 158.11,144 L172.11,149.11", width: 6 },
      { d: "M58.11,129.78 L64.56,143.56", width: 6 },
      { d: "M88.11,139.78 Q83.44,119.11 84.44,111.555 Q85.44,104 89,103.445 Q92.56,102.89 95.225,104.225 Q97.89,105.56 102,111.445 L106.11,117.33", width: 6 },
      { d: "M93.44,104.22 Q85.44,101.33 83.88499999999999,95.66499999999999 Q82.33,90 88.66499999999999,88.78 Q95,87.56 96.33500000000001,93.67 Q97.67,99.78 92.555,101.225 L87.44,102.67", width: 6 },
      { d: "M148.11,136.89 Q143.22,112.89 139.11,107.445 Q135,102 131.78,101.11 Q128.56,100.22 128.56,95.22 Q128.56,90.22 121.33500000000001,88.775 Q114.11,87.33 113.22,92.445 Q112.33,97.56 118.66499999999999,98.225 Q125,98.89 122.445,106.445 L119.89,114", width: 6 },
      { d: "M107.67,138.22 Q105.44,135.56 104.775,129.67000000000002 Q104.11,123.78 107.22,123.78 Q110.33,123.78 110.66499999999999,129.33499999999998 L111,134.89", width: 6 },
      { d: "M126.78,135.56 Q124.78,132.44 124.67,123.66499999999999 Q124.56,114.89 128.115,114.33500000000001 Q131.67,113.78 132.89,117.11 Q134.11,120.44 133.22000000000003,129.22 L132.33,138", width: 6 },
      { d: "M49.67,66.67 Q61.89,69.11 65.11,64.89 L68.33,60.67", width: 6 },
      { d: "M127.22,38.22 Q132.33,42.89 133.775,42.89 Q135.22,42.89 137.22,40.555 L139.22,38.22", width: 6 },
    ],
    created_at: STAMP,
    updated_at: STAMP,
  },
  {
    id: "glyph-hobbies",
    name: "hobbies",
    viewBox: "0 0 200 200",
    strokes: [
      { d: "M80.3,79.73 Q72.23,80.22 70.03,84.00999999999999 Q67.83,87.8 67.955,90.36500000000001 Q68.08,92.93 71.99000000000001,92.93 Q75.9,92.93 78.95500000000001,91.1 Q82.01,89.27 83.355,86.82499999999999 Q84.7,84.38 84.21000000000001,79.125 Q83.72,73.87 78.1,61.400000000000006 Q72.48,48.93 72.48,44.775 Q72.48,40.62 75.045,38.91 Q77.61,37.2 84.33500000000001,36.71 Q91.06,36.22 95.33500000000001,40.620000000000005 Q99.61,45.02 100.345,51.255 Q101.08,57.49 94.47999999999999,57.245000000000005 Q87.88,57 87.755,54.435 Q87.63,51.87 90.445,50.644999999999996 L93.26,49.42", width: 4 },
      { d: "M168.3,122.76 Q158.77,120.8 157.18,126.78999999999999 Q155.59,132.78 160.965,133.025 Q166.34,133.27 169.4,130.58 Q172.46,127.89 172.46,123.97999999999999 Q172.46,120.07 166.47,106.99 Q160.48,93.91 165.61,93.545 Q170.74,93.18 181.255,86.45500000000001 Q191.77,79.73 195.315,88.53 Q198.86,97.33 198.98000000000002,103.685 Q199.1,110.04 197.265,111.875 Q195.43,113.71 191.275,114.32 Q187.12,114.93 186.755,111.265 Q186.39,107.6 189.935,105.4 L193.48,103.2", width: 4 },
      { d: "M130.17,82.18 Q123.32,81.44 122.22,86.82 Q121.12,92.2 126.86500000000001,92.69 Q132.61,93.18 137.745,76.555 Q142.88,59.93 147.155,57.730000000000004 Q151.43,55.53 151.8,63.355000000000004 L152.17,71.18", width: 4 },
      { d: "M104.74,130.09 Q97.66,131.56 97.9,138.525 Q98.14,145.49 102.53999999999999,145.49 Q106.94,145.49 109.50999999999999,139.5 Q112.08,133.51 111.83500000000001,127.155 Q111.59,120.8 107.92500000000001,110.78 Q104.26,100.76 108.045,101.49000000000001 Q111.83,102.22 115.375,108.945 L118.92,115.67", width: 4 },
      { d: "M64.66,130.09 Q56.1,131.31 55,134 Q53.9,136.69 57.32,136.69 Q60.74,136.69 61.11,132.78 Q61.48,128.87 52.31,113.1 Q43.14,97.33 47.42,97.33 Q51.7,97.33 55.735,101.245 L59.77,105.16", width: 4 },
    ],
    created_at: STAMP,
    updated_at: STAMP,
  },
  {
    id: "glyph-partner",
    name: "partner",
    viewBox: "0 0 200 200",
    strokes: [
      { d: "M111.83,155.51 Q82.26,140.6 73.09,132.29 Q63.92,123.98 58.91,111.635 Q53.9,99.29 54.144999999999996,87.68 Q54.39,76.07 60.99,69.835 Q67.59,63.6 77.49000000000001,60.91 Q87.39,58.22 94.35499999999999,58.345 Q101.32,58.47 106.945,60.915 Q112.57,63.36 116.6,66.78 Q120.63,70.2 126.255,86.09 Q131.88,101.98 132.49,114.2 Q133.1,126.42 129.68,134.365 Q126.26,142.31 123.08000000000001,145.365 Q119.9,148.42 118.065,148.42 Q116.23,148.42 115.375,145.61 Q114.52,142.8 114.03,131.19 Q113.54,119.58 120.755,97.455 Q127.97,75.33 130.78,71.42 Q133.59,67.51 141.29000000000002,61.400000000000006 Q148.99,55.29 152.29000000000002,53.825 Q155.59,52.36 161.09,52.6 Q166.59,52.84 171.35500000000002,57.120000000000005 Q176.12,61.4 179.79000000000002,70.81 Q183.46,80.22 183.57999999999998,90.36500000000001 Q183.7,100.51 181.99,105.275 Q180.28,110.04 176,117.255 Q171.72,124.47 163.655,132.655 Q155.59,140.84 148.865,145 Q142.14,149.16 129.555,152.45999999999998 L116.97,155.76", width: 4 },
      { d: "M105.72,48.44 Q97.17,50.4 87.515,49.91 Q77.86,49.42 74.435,44.29 Q71.01,39.16 70.89,31.945 Q70.77,24.73 76.515,22.045 Q82.26,19.36 89.96000000000001,18.869999999999997 Q97.66,18.38 98.39,30.11 Q99.12,41.84 91.545,42.94 Q83.97,44.04 82.99000000000001,41.474999999999994 Q82.01,38.91 82.745,34.51 L83.48,30.11", width: 4 },
      { d: "M149.72,28.89 Q133.83,32.8 129.8,36.345 Q125.77,39.89 125.28,41.845 Q124.79,43.8 140.19,44.165 Q155.59,44.53 166.59,38.175 Q177.59,31.82 167.445,31.82 Q157.3,31.82 149.11,35.245000000000005 L140.92,38.67", width: 4 },
      { d: "M31.66,74.11 L43.63,74.36", width: 4 },
      { d: "M41.19,55.29 L47.79,64.09", width: 4 },
    ],
    created_at: STAMP,
    updated_at: STAMP,
  },
]

const soul = (id: string, name: string, glyph: string): Soul => ({
  id,
  name,
  glyph_id: `glyph-${glyph}`,
  created_at: STAMP,
  updated_at: STAMP,
})

const NEIGHBOURS: Soul[] = [
  soul("soul-ana", "Ana", "friends"),
  soul("soul-bea", "Béa", "hobbies"),
  soul("soul-cyril", "Cyril", "identity"),
  soul("soul-dora", "Dora", "travel"),
  soul("soul-ezra", "Ezra", "family"),
  soul("soul-felix", "Félix", "partner"),
  soul("soul-gaby", "Gaby", "friends"),
  soul("soul-hugo", "Hugo", "hobbies"),
  soul("soul-ines", "Inès", "travel"),
]

export const SPINE_FIXTURE_SOULS: Soul[] = [
  soul("soul-mara", "Mara", "partner"),
  soul("soul-elio", "Elio", "family"),
  soul("soul-nadia", "Nadia", "friends"),
  soul("soul-jules", "Jules", "hobbies"),
  soul("soul-bruno", "Bruno", "travel"),
  ...NEIGHBOURS,
]

const MARKS = new Map(SPINE_FIXTURE_MARKS.map((m) => [m.id, m]))

function cover(file: string): PebbleCover {
  const url = `/sandbox/${file}`
  return { snapId: `snap-${file}`, url, thumbUrl: url }
}

type Spec = {
  n: number
  name: string
  at: string
  intensity: Pebble["intensity"]
  positiveness: Pebble["positiveness"]
  emotion: string
  souls: string[]
  glyph: string | null
  photo?: string
}

function pebble(spec: Spec): Pebble {
  const mark = spec.glyph ? MARKS.get(`glyph-${spec.glyph}`) : undefined
  const happened = `2026-10-${spec.at}:00+02:00`
  const media = spec.photo ? cover(spec.photo) : null
  return {
    id: `spine-fixture-${String(spec.n).padStart(2, "0")}`,
    name: spec.name,
    happened_at: happened,
    intensity: spec.intensity,
    positiveness: spec.positiveness,
    visibility: "private",
    emotion_id: emotionId(spec.emotion),
    soul_ids: spec.souls.map((s) => `soul-${s}`),
    domain_ids: [],
    collection_ids: [],
    mark_id: mark?.id,
    instants: media?.url ? [media.url] : [],
    snaps: media ? [{ id: media.snapId, storage_path: `fixture/${media.snapId}`, sort_order: 0 }] : [],
    cards: [],
    glyph: mark ? { id: mark.id, name: mark.name, strokes: mark.strokes, viewBox: mark.viewBox } : null,
    cover: media,
    created_at: happened,
    updated_at: happened,
  }
}

const NEIGHBOUR_IDS = NEIGHBOURS.map((s) => s.id.replace("soul-", ""))

const SPECS: Spec[] = [
  { n: 1, name: "Flag march with Nadia", at: "05T10:15", intensity: 3, positiveness: 1, emotion: "proud", souls: ["nadia"], glyph: "friends", photo: "flag-march.jpg" },
  { n: 2, name: "Elio lost his first tooth", at: "05T19:40", intensity: 2, positiveness: 1, emotion: "joyful", souls: ["elio", "mara"], glyph: "family" },
  { n: 3, name: "Rent went up again", at: "06T08:02", intensity: 2, positiveness: -1, emotion: "angry", souls: [], glyph: "identity" },
  { n: 4, name: "Forest walk, all of us", at: "06T16:30", intensity: 3, positiveness: 1, emotion: "peaceful", souls: ["mara", "elio", "bruno"], glyph: "travel", photo: "forest-crew.jpg" },
  { n: 5, name: "Couldn’t sleep", at: "07T03:10", intensity: 1, positiveness: -1, emotion: "scared", souls: [], glyph: "identity" },
  { n: 6, name: "Coffee by the fountain", at: "07T12:05", intensity: 1, positiveness: 0, emotion: "peaceful", souls: ["jules"], glyph: "friends", photo: "fountain.jpg" },
  { n: 7, name: "Rooftop birthday", at: "08T21:00", intensity: 3, positiveness: 1, emotion: "joyful", souls: ["nadia", "jules", "mara"], glyph: "hobbies", photo: "rooftop-group.jpg" },
  { n: 12, name: "Picnic on the square, the whole street", at: "08T13:10", intensity: 2, positiveness: 1, emotion: "joyful", souls: ["mara", "elio", "nadia", "jules", "bruno", ...NEIGHBOUR_IDS], glyph: "friends" },
  { n: 8, name: "Said no to the extra project", at: "10T09:20", intensity: 2, positiveness: 0, emotion: "proud", souls: [], glyph: "identity" },
  { n: 9, name: "Mara on the seawall", at: "10T17:45", intensity: 2, positiveness: 1, emotion: "joyful", souls: ["mara"], glyph: "partner", photo: "seawall-portrait.jpg" },
  { n: 13, name: "", at: "10T19:05", intensity: 1, positiveness: 0, emotion: "peaceful", souls: ["mara"], glyph: "partner", photo: "fountain.jpg" },
  { n: 10, name: "Grandma’s call went quiet", at: "11T14:00", intensity: 2, positiveness: -1, emotion: "sad", souls: [], glyph: "family" },
  { n: 11, name: "Bruno asleep on my feet", at: "11T22:30", intensity: 1, positiveness: 0, emotion: "peaceful", souls: ["bruno"], glyph: null },
]

export const SPINE_FIXTURE_PEBBLES: Pebble[] = SPECS.map(pebble)

/** A Sunday evening of the fixture week: every day of it is in the past but today. */
export const SPINE_FIXTURE_TODAY = new Date(2026, 9, 11, 23, 0)
