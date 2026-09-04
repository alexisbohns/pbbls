import type { Mark, Pebble, Soul } from "@/lib/types"
import { GLYPH_VIEWBOX } from "@/lib/config/glyphs"
import { emotionId } from "./sandbox-palettes"

// ---------------------------------------------------------------------------
// Fixture content for /sandbox/path.
//
// Self-contained on purpose: no Supabase, no auth, no Storage. Every pebble
// carries `render_svg: null` so PebbleVisual falls through to the client engine
// — already the documented path for unauthenticated previews like the landing
// page — and pictures are local files under `public/sandbox/`.
// ---------------------------------------------------------------------------

// Real photographs rather than generated gradients: a smooth gradient never tests
// how the stone and the hand-written name read over a busy, high-contrast picture,
// which is most of what there is to judge here.
//
// Sourced from Pexels (free to use, no attribution required) and committed as
// fixtures so the sandbox stays network-free. Kept in a mix of aspect ratios —
// 16:9, 2:3, 3:2 and 1:1 — because the picture well takes its height from its own
// width and every ratio lands differently in the card.
const SNAPS = {
  rooftop: "/sandbox/rooftop-group.jpg", // 16:9 — group selfie, blown-out sky
  seawall: "/sandbox/seawall-portrait.jpg", // 2:3 — portrait, busy background
  fountain: "/sandbox/fountain.jpg", // 3:2 — bright sky, high contrast
  forest: "/sandbox/forest-crew.jpg", // 3:2 — dark, low contrast
  march: "/sandbox/flag-march.jpg", // 1:1 — mid-tone, strong colour
} as const

// Four hand-drawn marks so soul avatars are visually distinguishable. Stroke
// width is 6 in glyph space, per the canonical glyph model.
const stroke = (d: string) => ({ d, width: 6 })

export const SANDBOX_MARKS: Mark[] = [
  {
    id: "mark-wave",
    name: "Wave",
    strokes: [stroke("M30 120 Q 70 60 100 120 T 170 120")],
    viewBox: GLYPH_VIEWBOX,
    created_at: "2026-01-01T00:00:00Z",
    updated_at: "2026-01-01T00:00:00Z",
  },
  {
    id: "mark-arrow",
    name: "Arrow",
    strokes: [stroke("M40 160 L 160 40"), stroke("M110 40 L 160 40 L 160 90")],
    viewBox: GLYPH_VIEWBOX,
    created_at: "2026-01-01T00:00:00Z",
    updated_at: "2026-01-01T00:00:00Z",
  },
  {
    id: "mark-spiral",
    name: "Spiral",
    strokes: [stroke("M100 100 m -8 0 a 8 8 0 1 1 16 0 a 24 24 0 1 1 -40 0 a 44 44 0 1 1 76 20")],
    viewBox: GLYPH_VIEWBOX,
    created_at: "2026-01-01T00:00:00Z",
    updated_at: "2026-01-01T00:00:00Z",
  },
  {
    id: "mark-cross",
    name: "Cross",
    strokes: [stroke("M55 55 L 145 145"), stroke("M145 55 L 55 145")],
    viewBox: GLYPH_VIEWBOX,
    created_at: "2026-01-01T00:00:00Z",
    updated_at: "2026-01-01T00:00:00Z",
  },
]

export const SANDBOX_MARK_MAP: Map<string, Mark> = new Map(
  SANDBOX_MARKS.map((mark) => [mark.id, mark]),
)

export const SANDBOX_SOULS: Soul[] = [
  { id: "soul-mia", name: "Mia", glyph_id: "mark-wave", created_at: "", updated_at: "" },
  { id: "soul-lucas", name: "Lucas", glyph_id: "mark-arrow", created_at: "", updated_at: "" },
  { id: "soul-papa", name: "Papa", glyph_id: "mark-spiral", created_at: "", updated_at: "" },
  { id: "soul-luna", name: "Luna", glyph_id: "mark-cross", created_at: "", updated_at: "" },
  { id: "soul-noor", name: "Noor", glyph_id: "mark-wave", created_at: "", updated_at: "" },
]

export const SANDBOX_SOUL_MAP: Map<string, Soul> = new Map(
  SANDBOX_SOULS.map((soul) => [soul.id, soul]),
)

type Spec = {
  name: string
  intensity: 1 | 2 | 3
  emotion: string
  positiveness?: -1 | 0 | 1
  snap?: string
  souls?: string[]
  /** Minutes past 08:00 on the fixture day. Keeps the week chronological. */
  at?: number
}

/** Times run forward across a scenario so the layout order is a real timeline. */
function build(specs: Spec[], scenario: string): Pebble[] {
  return specs.map((spec, i) => {
    const minutes = spec.at ?? i * 97
    const when = new Date(Date.UTC(2026, 7, 17, 8, 0, 0))
    when.setUTCMinutes(when.getUTCMinutes() + minutes)
    const iso = when.toISOString().replace(/\.\d{3}Z$/, "Z")
    return {
      id: `${scenario}-${i}`,
      name: spec.name,
      happened_at: iso,
      intensity: spec.intensity,
      positiveness: spec.positiveness ?? 0,
      visibility: "private",
      emotion_id: emotionId(spec.emotion),
      soul_ids: spec.souls ?? [],
      domain_ids: [],
      collection_ids: [],
      mark_id: SANDBOX_MARKS[i % SANDBOX_MARKS.length].id,
      instants: spec.snap ? [spec.snap] : [],
      snaps: [],
      cards: [],
      render_svg: null,
      render_version: null,
      created_at: iso,
      updated_at: iso,
    }
  })
}

export type SandboxScenario = {
  key: string
  label: string
  /** What this scenario is meant to expose — shown under the toolbar. */
  note: string
  pebbles: Pebble[]
}

export const SANDBOX_SCENARIOS: SandboxScenario[] = [
  {
    key: "mixed",
    label: "Mixed ladder",
    note: "small ×2, medium ×2, small, large, medium ×3 — the full progressive ladder in one week.",
    pebbles: build(
      [
        { name: "Coffee before anyone woke up", intensity: 1, emotion: "calm", positiveness: 1 },
        { name: "Inbox already ugly", intensity: 1, emotion: "annoyed", positiveness: -1 },
        { name: "Lunch on the wall by the canal", intensity: 2, emotion: "content", positiveness: 1, snap: SNAPS.rooftop, souls: ["soul-mia"] },
        { name: "Lucas landed the job", intensity: 2, emotion: "proud", positiveness: 1, souls: ["soul-lucas"] },
        { name: "Forgot to call back", intensity: 1, emotion: "guilty", positiveness: -1 },
        { name: "The whole family on the terrace, finally", intensity: 3, emotion: "grateful", positiveness: 1, snap: SNAPS.fountain, souls: ["soul-mia", "soul-papa", "soul-luna"] },
        { name: "Rain on the way home", intensity: 2, emotion: "peaceful", positiveness: 1, snap: SNAPS.forest },
        { name: "Luna asleep on the keyboard", intensity: 2, emotion: "content", positiveness: 1, snap: SNAPS.march, souls: ["soul-luna"] },
        { name: "Long call with Papa", intensity: 2, emotion: "grateful", positiveness: 1, souls: ["soul-papa"] },
      ],
      "mixed",
    ),
  },
  {
    key: "oddMediums",
    label: "Odd medium runs",
    note: "runs of 1, 3 and 5 mediums, split by smalls — the centering rule for an odd trailing card.",
    pebbles: build(
      [
        { name: "One on its own", intensity: 2, emotion: "hopeful", positiveness: 1, snap: SNAPS.forest },
        { name: "Quick note", intensity: 1, emotion: "calm" },
        { name: "Three, first", intensity: 2, emotion: "joyful", positiveness: 1, snap: SNAPS.fountain },
        { name: "Three, second", intensity: 2, emotion: "satisfied", positiveness: 1 },
        { name: "Three, third", intensity: 2, emotion: "relieved", positiveness: 1, snap: SNAPS.seawall },
        { name: "Another quick note", intensity: 1, emotion: "worried", positiveness: -1 },
        { name: "Five, first", intensity: 2, emotion: "content", positiveness: 1, snap: SNAPS.rooftop },
        { name: "Five, second", intensity: 2, emotion: "grateful", positiveness: 1 },
        { name: "Five, third", intensity: 2, emotion: "peaceful", positiveness: 1, snap: SNAPS.rooftop },
        { name: "Five, fourth", intensity: 2, emotion: "hopeful", positiveness: 1 },
        { name: "Five, fifth", intensity: 2, emotion: "proud", positiveness: 1, snap: SNAPS.seawall },
      ],
      "odd",
    ),
  },
  {
    key: "allMedium",
    label: "Pure grid",
    note: "eight mediums, nothing else — the 2-col grid with no interruptions.",
    pebbles: build(
      [
        { name: "Market at eight", intensity: 2, emotion: "content", positiveness: 1, snap: SNAPS.fountain },
        { name: "Bread still warm", intensity: 2, emotion: "grateful", positiveness: 1, snap: SNAPS.seawall, souls: ["soul-mia"] },
        { name: "Long swim", intensity: 2, emotion: "peaceful", positiveness: 1, snap: SNAPS.forest },
        { name: "That email again", intensity: 2, emotion: "frustrated", positiveness: -1 },
        { name: "Noor called out of nowhere", intensity: 2, emotion: "surprised", souls: ["soul-noor"] },
        { name: "Sunset from the roof", intensity: 2, emotion: "joyful", positiveness: 1, snap: SNAPS.fountain },
        { name: "Couldn't sleep", intensity: 2, emotion: "anxious", positiveness: -1, snap: SNAPS.forest },
        { name: "Finished the book", intensity: 2, emotion: "satisfied", positiveness: 1, snap: SNAPS.rooftop },
      ],
      "grid",
    ),
  },
  {
    key: "noPhotos",
    label: "No pictures",
    note: "every size, zero pictures — the case the glyph variants exist to answer.",
    pebbles: build(
      [
        { name: "Woke up before the alarm", intensity: 1, emotion: "calm", positiveness: 1 },
        { name: "Said the thing I'd been avoiding", intensity: 2, emotion: "brave", positiveness: 1, souls: ["soul-mia"] },
        { name: "It landed better than I feared", intensity: 2, emotion: "relieved", positiveness: 1 },
        { name: "Walked the long way back", intensity: 1, emotion: "peaceful", positiveness: 1 },
        { name: "A whole evening with nothing to prove", intensity: 3, emotion: "content", positiveness: 1, souls: ["soul-lucas", "soul-luna"] },
        { name: "One more, unpaired", intensity: 2, emotion: "hopeful", positiveness: 1 },
      ],
      "nophoto",
    ),
  },
  {
    key: "photosOnly",
    label: "All pictures",
    note: "every card carries a picture, in mixed aspect ratios — 16:9, 2:3, 3:2 and square.",
    pebbles: build(
      [
        { name: "First light", intensity: 2, emotion: "peaceful", positiveness: 1, snap: SNAPS.forest },
        { name: "The canal", intensity: 2, emotion: "content", positiveness: 1, snap: SNAPS.rooftop },
        { name: "Her hands in the dough", intensity: 2, emotion: "grateful", positiveness: 1, snap: SNAPS.seawall, souls: ["soul-mia"] },
        { name: "Green everywhere", intensity: 2, emotion: "joyful", positiveness: 1, snap: SNAPS.forest },
        { name: "Everyone, at the table", intensity: 3, emotion: "joyful", positiveness: 1, snap: SNAPS.fountain, souls: ["soul-mia", "soul-papa"] },
        { name: "Last light", intensity: 2, emotion: "satisfied", positiveness: 1, snap: SNAPS.march },
      ],
      "photos",
    ),
  },
  {
    key: "manySouls",
    label: "Soul overflow",
    note: "0, 1, 2 and 5 souls on otherwise identical cards — same picture throughout, so the avatar stack is the only variable.",
    pebbles: build(
      [
        { name: "Alone", intensity: 2, emotion: "calm", positiveness: 1, snap: SNAPS.march, souls: [] },
        { name: "Just Mia", intensity: 2, emotion: "content", positiveness: 1, snap: SNAPS.march, souls: ["soul-mia"] },
        { name: "Mia and Lucas", intensity: 2, emotion: "joyful", positiveness: 1, snap: SNAPS.march, souls: ["soul-mia", "soul-lucas"] },
        { name: "Everyone at once", intensity: 2, emotion: "grateful", positiveness: 1, snap: SNAPS.march, souls: ["soul-mia", "soul-lucas", "soul-papa", "soul-luna", "soul-noor"] },
        { name: "All five, no picture", intensity: 2, emotion: "proud", positiveness: 1, souls: ["soul-mia", "soul-lucas", "soul-papa", "soul-luna", "soul-noor"] },
        { name: "All five, full width", intensity: 3, emotion: "joyful", positiveness: 1, snap: SNAPS.fountain, souls: ["soul-mia", "soul-lucas", "soul-papa", "soul-luna", "soul-noor"] },
      ],
      "souls",
    ),
  },
  {
    key: "longTitles",
    label: "Long titles",
    note: "title wrapping in Caveat, at half width, full width and in a compact row.",
    pebbles: build(
      [
        { name: "A short one", intensity: 2, emotion: "calm", positiveness: 1, snap: SNAPS.rooftop },
        { name: "The afternoon where absolutely nothing happened and that turned out to be the entire point of it", intensity: 2, emotion: "peaceful", positiveness: 1 },
        { name: "Supercalifragilisticexpialidocious", intensity: 2, emotion: "surprised", snap: SNAPS.seawall },
        { name: "Another perfectly ordinary Tuesday that I would like very much to remember in ten years", intensity: 2, emotion: "content", positiveness: 1, snap: SNAPS.rooftop },
        { name: "A full-width card carrying a title long enough to need two lines even at this width, which is the point", intensity: 3, emotion: "grateful", positiveness: 1, snap: SNAPS.fountain, souls: ["soul-papa"] },
        { name: "And a compact row with a name long enough that it has to truncate somewhere around here", intensity: 1, emotion: "annoyed", positiveness: -1 },
      ],
      "titles",
    ),
  },
]
