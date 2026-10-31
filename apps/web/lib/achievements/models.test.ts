import { describe, expect, it } from "vitest"
import { designPage, designPalettes } from "./__fixtures__/design"
import { countingLadder, domainsLadder, emotionsLadder, explorerLadder, yearLadder } from "./ladders"
import { facets } from "./rosace"
import { bandItems, familyRuns } from "./runs"
import { SIX_FIRST_SEALS, pebbleSeal } from "./seals"
import { skylineStages } from "./skyline"
import { discMonth } from "./year-disc"
import { wheelModel, type WheelMark } from "./wheel"
import type { AchievementsDomains, AchievementsEmotions, AchievementsExplorer, EmotionSeat } from "@/lib/types"

// The mapping from the payload to the figures, on the canonical design fixture
// every surface draws, then on payloads the board never drew (spec §4).

const page = designPage()
const palettes = designPalettes()

function sections() {
  const { explorer, emotions, domains, pebbles, glyphsCarved, glyphSales, years } = page
  if (!explorer || !emotions || !domains || !pebbles || !glyphsCarved || !glyphSales) throw new Error("fixture lost a section")
  return { explorer, emotions, domains, pebbles, glyphsCarved, glyphSales, year: years[0] }
}
const s = sections()

const kinds = (marks: WheelMark[]) => marks.map((m) => m.kind)

describe("the explorer wheel, on the design fixture", () => {
  const w = wheelModel(s.explorer, s.emotions, s.domains)

  it("darkens the tracks the server lists as done", () => {
    expect(w.tracks).toEqual({ first_five: true, six_firsts: true, twenty_five: true, domains: false })
  })

  it("prints the first pebble at the centre in its own seal and emotion", () => {
    expect(w.centre).toMatchObject({ kind: "seal", width: 25, seal: { size: "medium", outline: "highlight", print: "solid" } })
    expect(w.centre.kind === "seal" && w.centre.tone).toEqual({ by: "emotion", emotionId: "f1b09d24-3c2e-414b-a751-fc91da51fcdb" })
  })

  it("draws the first five as their tasks' marks, every 72°", () => {
    expect(w.firstFive.map((m) => (m.kind === "first-five" ? m.task : m.kind))).toEqual([
      "first-moment",
      "first-pebble",
      "first-collection",
      "first-soul",
      "first-glyph",
    ])
    expect(w.firstFive[1].x).toBeCloseTo(44 * Math.sin((72 * Math.PI) / 180))
  })

  it("seats the six firsts by index, the intensities a solid print on the neutral outline", () => {
    expect(w.sixFirsts.map((m) => (m.kind === "seal" ? [m.seal.size, m.seal.outline, m.seal.print, m.width] : m.kind))).toEqual([
      ["small", "neutral", "solid", 9],
      ["medium", "neutral", "solid", 12],
      ["large", "neutral", "solid", 14],
      ["medium", "lowlight", "wash", 12.5],
      ["medium", "neutral", "tint", 12.5],
      ["medium", "highlight", "solid", 12.5],
    ])
  })

  it("inks the 25 pebbles the server counts", () => {
    expect(kinds(w.twentyFive).filter((k) => k === "seal")).toHaveLength(25)
  })

  it("seats the domains in the order they came, then the not yet, spaced by both lists", () => {
    expect(kinds(w.domains)).toEqual([...Array<string>(14).fill("seal"), ...Array<string>(4).fill("ghost")])
    // The newest entered (served first) is the last to have come: its new mark is on seat 13.
    expect(w.domains.map((m) => m.isNew).indexOf(true)).toBe(13)
    expect(w.domains.every((m) => m.kind !== "seal" || (m.seal.outline === "neutral" && m.seal.print === "solid"))).toBe(true)
  })

  it("prints each met emotion with its crossing pebble's seal, in its family", () => {
    expect(kinds(w.emotions).filter((k) => k === "seal")).toHaveLength(23)
    const sad = w.emotions[29]
    expect(sad).toMatchObject({ kind: "seal", isNew: true, seal: { size: "large", outline: "lowlight", print: "wash" } })
    expect(sad.width).toBeCloseTo(7.4 + 3 * 1.6)
    expect(sad.kind === "seal" && sad.tone).toEqual({ by: "family", family: "sadness" })
    expect(w.families.map((f) => f.family)).toEqual(["joy", "pride", "anger", "fear", "shame", "sadness", "peace"])
    expect(w.families[0].arc).toBe("M-61.3 -119.2A134 134 0 0 1 61.3 -119.2")
  })
})

describe("the explorer wheel, on payloads the board never drew", () => {
  const explorer = (over: Partial<AchievementsExplorer> = {}): AchievementsExplorer => ({
    firstPebble: null,
    firstFive: [],
    sixFirsts: [],
    twentyFive: null,
    ringsDone: [],
    completion: null,
    ...over,
  })
  const seat = (k: number, over: Partial<EmotionSeat> = {}): EmotionSeat => ({
    seat: k,
    emotionId: null,
    family: "joy",
    met: false,
    on: null,
    pebble: null,
    isNew: false,
    ...over,
  })
  const emotions = (seats: EmotionSeat[]): AchievementsEmotions => ({ count: 0, of: seats.length, seats, families: [], since: null, completion: null })

  it("draws a ghost at the centre before the first pebble, and 25 ghosts without the 25", () => {
    const w = wheelModel(explorer(), null, null)
    expect(w.centre).toMatchObject({ kind: "ghost", r: 4 })
    expect(kinds(w.twentyFive)).toEqual(Array<string>(25).fill("ghost"))
    expect(w.domains).toEqual([])
    expect(w.emotions).toEqual([])
  })

  it("keeps unknown tasks and kinds as ghosts, their new marks as served, spread by the served count", () => {
    const w = wheelModel(
      explorer({
        firstFive: Array.from({ length: 6 }, (_, i) => ({ task: null, done: true, on: null, subject: null, isNew: i === 5 })),
        sixFirsts: [{ kind: null, done: true, on: null, pebble: null, isNew: true }],
      }),
      null,
      null,
    )
    expect(kinds(w.firstFive)).toEqual(Array<string>(6).fill("ghost"))
    expect(w.firstFive[5].isNew).toBe(true)
    expect(w.firstFive[1].x).toBeCloseTo(44 * Math.sin((60 * Math.PI) / 180))
    expect(w.sixFirsts[0]).toMatchObject({ kind: "ghost", isNew: true })
  })

  it("prints a met seat whose pebble is gone as the even medium stone, and seats 37 by index", () => {
    const w = wheelModel(explorer(), emotions(Array.from({ length: 37 }, (_, k) => seat(k, { met: k === 0, family: k < 6 ? "awe" : "joy" }))), null)
    expect(w.emotions).toHaveLength(37)
    expect(w.emotions[0]).toMatchObject({ kind: "seal", seal: { size: "medium", outline: "neutral", print: "tint" }, tone: { by: "family", family: "awe" } })
    expect(w.families.map((f) => f.family)).toEqual(["awe", "joy"])
  })

  it("spaces 17 domains round their ring", () => {
    const domains: AchievementsDomains = {
      count: 10,
      of: 17,
      coverage: "half",
      entered: Array.from({ length: 10 }, (_, i) => ({ domainId: `d${i}`, on: null, pebble: null, isNew: i === 0 })),
      notYet: Array.from({ length: 7 }, (_, i) => `n${i}`),
      completion: null,
    }
    const w = wheelModel(explorer(), null, domains)
    expect(w.domains).toHaveLength(17)
    expect(w.domains[9].isNew).toBe(true)
    expect(bandItems(domains)[9]).toEqual({ domainId: "d0", on: true, isNew: true })
  })
})

describe("the rosace's facets", () => {
  it("tones each met seat by its place in its family, and bridges two met neighbours of one family", () => {
    const f = facets(s.emotions.seats)
    expect(f.slice(0, 6).map((x) => x.place)).toEqual([0, 0.2, 0.4, 0.6, 0.8, 1])
    expect(f.slice(6, 9).map((x) => x.place)).toEqual([0, 0.5, 1])
    // Pride is whole: both stars inside it are lit; the last one does not reach anger.
    expect(f.slice(6, 9).map((x) => x.bridged)).toEqual([true, true, false])
    expect(f[7].nextPlace).toBe(1)
  })

  it("runs a family-less seat with its null neighbours", () => {
    expect(familyRuns([{ family: null }, { family: null }, { family: "joy" }])).toEqual([
      { family: null, start: 0, length: 2 },
      { family: "joy", start: 2, length: 1 },
    ])
  })
})

describe("the skyline", () => {
  it("stands the listed levels and raises the next by the served progress", () => {
    const p = skylineStages(s.pebbles)
    expect(p.stages.map((x) => x.fill)).toEqual([1, 1, 1, 1, 1, 0.247, 0, 0])
    expect(p.standing).toBe(false)
    expect(skylineStages(s.glyphsCarved).stages.map((x) => [x.reached, x.fill])).toEqual([
      [true, 1],
      [false, 0.133],
      [false, 0],
      [false, 0],
      [false, 0],
    ])
  })

  it("stands only on listed levels, never on a progress that reaches 1", () => {
    const top = skylineStages({ ...s.glyphSales, levels: [], next: { threshold: 1, left: 0, progress: 1 } })
    expect(top.stages[0]).toMatchObject({ reached: false, fill: 1 })
    expect(top.standing).toBe(false)
    const all = skylineStages({ ...s.glyphSales, levels: [1, 5, 10, 25, 50].map((t) => ({ threshold: t, on: null, pebble: null, isNew: false })), next: null })
    expect(all.standing).toBe(true)
  })
})

describe("the margin ladders", () => {
  it("fills the explorer's rings, done only where the server says so", () => {
    expect(explorerLadder(s.explorer, s.emotions, s.domains)).toEqual([
      { fill: 1, done: true },
      { fill: 1, done: true },
      { fill: 1, done: true },
      { fill: 14 / 18, done: false },
      { fill: 23 / 38, done: false },
    ])
    const counted = explorerLadder({ ...s.explorer, ringsDone: [] }, s.emotions, s.domains)
    expect(counted[0]).toEqual({ fill: 1, done: false })
  })

  it("bars each served family, done when nothing is left in it", () => {
    expect(emotionsLadder(s.emotions).map((b) => b.done)).toEqual([false, true, false, false, false, false, false])
    expect(emotionsLadder(s.emotions)[0].fill).toBeCloseTo(5 / 6)
  })

  it("bars the domains by count, done only on coverage all", () => {
    expect(domainsLadder(s.domains)).toEqual({ fill: 14 / 18, done: false })
    expect(domainsLadder({ ...s.domains, count: 18, coverage: "most" }).done).toBe(false)
    expect(domainsLadder({ ...s.domains, coverage: "all" }).done).toBe(true)
  })

  it("bars a counting stack by its listed levels and its next", () => {
    expect(countingLadder(s.pebbles).map((b) => [b.fill, b.done])).toEqual([
      [1, true],
      [1, true],
      [1, true],
      [1, true],
      [1, true],
      [0.247, false],
      [0, false],
      [0, false],
    ])
  })

  it("bars each month by its level and progress, done on the rim", () => {
    const bars = yearLadder(s.year)
    expect(bars[7]).toEqual({ fill: 1, done: true })
    expect(bars[2].fill).toBeCloseTo((1 + 0.857) / 2)
    expect(bars[11]).toEqual({ fill: 0, done: false })
  })
})

describe("the year disc, on the design fixture", () => {
  const months = s.year.months.map(discMonth)

  it("mutes the quiet and the upcoming months, and tones the kept ones by family", () => {
    expect(months.map((m) => (m.tone.muted ? "muted" : m.tone.family))).toEqual([
      "muted",
      "muted",
      "peace",
      "joy",
      "joy",
      "joy",
      "joy",
      "joy",
      "peace",
      "joy",
      "peace",
      "muted",
    ])
  })

  it("rings August on the rim, and dots December's counter", () => {
    expect(months[7].counter).toMatchObject({ ring: true, print: "solid" })
    expect(months.filter((m) => m.counter.ring).map((m) => m.month)).toEqual([8])
    expect(months[11].counter).toMatchObject({ print: "blank", dashed: true })
    expect(months[0].counter).toMatchObject({ print: "blank", dashed: false })
  })

  it("resolves every family the fixture names", () => {
    for (const m of months) if (!m.tone.muted) expect(palettes.family(m.tone.family)).not.toBeNull()
    expect(palettes.family("awe")).toBeNull()
  })
})

describe("seals", () => {
  it("print a pebble as the Spine does: solid bright, tinted even, washed heavy", () => {
    expect(pebbleSeal({ intensity: 3, positiveness: 1 })).toEqual({ size: "large", outline: "highlight", print: "solid" })
    expect(pebbleSeal({ intensity: 1, positiveness: 0 })).toEqual({ size: "small", outline: "neutral", print: "tint" })
    expect(pebbleSeal({ intensity: 2, positiveness: -1 })).toEqual({ size: "medium", outline: "lowlight", print: "wash" })
    expect(Object.values(SIX_FIRST_SEALS).map((x) => x.width)).toEqual([9, 12, 14, 12.5, 12.5, 12.5])
  })
})
