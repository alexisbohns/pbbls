import { arcPath, polar, type Segment } from "@/lib/achievements/geometry"
import { bandItems, familyRuns } from "@/lib/achievements/runs"
import {
  DOMAIN_SEAL,
  FIRST_FIVE_MARK_WIDTH,
  GHOST_WIDTH,
  NEUTRAL_MEDIUM,
  SIX_FIRST_SEALS,
  TWENTY_FIVE_SEAL,
  pebbleSeal,
  seatWidth,
  type PlacedSeal,
} from "@/lib/achievements/seals"
import type { AchievementsDomains, AchievementsEmotions, AchievementsExplorer, ExplorerRing, FirstFiveTask } from "@/lib/types"

// The Explorer wheel (#1308, spec D6; the boards' `wheel`), centre out: the
// user's first pebble, the first five, the six firsts, 25 pebbles, the domains
// in the order they came, the emotions on the rosace's seats. A seat the
// payload marks done or met draws its mark, any other a ghost; a seat the
// server flags new gets its mark whatever else it is; a ring listed in
// `rings_done` gets the darker track. Seats are placed by their index in the
// served list and spread by the served count. iOS `ExplorerWheel`.

export const WHEEL_SIDE = 300
export const WHEEL_RADII: Record<ExplorerRing, number> = {
  first_five: 44,
  six_firsts: 68,
  twenty_five: 90,
  domains: 112,
  emotions: 134,
}
/** The ring of 25 seats: the "25 pebbles" milestone (D6). */
export const TWENTY_FIVE_SEATS = 25

const step = (count: number) => 360 / Math.max(count, 1)

/** The first five round their ring, the first at twelve: every 72° for five. */
export function firstFiveAngle(index: number, count: number): number {
  return index * step(count)
}

/** `count` seats round a ring, the first half a step past twelve (the six firsts, the 25, the domains). */
export function seatAngle(index: number, count: number): number {
  return (index + 0.5) * step(count)
}

/** The emotions' seats, as the rosace seats them: seat 2.5 at twelve. */
export function emotionAngle(seat: number, count: number): number {
  return (seat - 2.5) * step(count)
}

/** What colours a seal: ink, a family's palette, or an emotion's. */
export type SealTone = { by: "ink" } | { by: "family"; family: string | null } | { by: "emotion"; emotionId: string | null }

type Place = { x: number; y: number; isNew: boolean; width: number }

export type WheelMark =
  | (Place & { kind: "ghost"; r: number })
  | (Place & { kind: "seal"; seal: PlacedSeal; tone: SealTone })
  | (Place & { kind: "first-five"; task: FirstFiveTask })

/** The rings the wheel draws a track for. */
export type TrackRing = Exclude<ExplorerRing, "emotions">

export type WheelFamily = { family: string | null; arc: string; tick: Segment }

export type WheelModel = {
  /** The four drawn tracks (the emotions ring has none: its family arcs stand for it). */
  tracks: Record<TrackRing, boolean>
  families: WheelFamily[]
  emotions: WheelMark[]
  domains: WheelMark[]
  twentyFive: WheelMark[]
  sixFirsts: WheelMark[]
  firstFive: WheelMark[]
  centre: WheelMark
}

function at(r: number, deg: number): { x: number; y: number } {
  const [x, y] = polar(r, deg)
  return { x, y }
}

function ghost(r: number, deg: number, isNew: boolean, radius = 3.2): WheelMark {
  return { ...at(r, deg), kind: "ghost", r: radius, isNew, width: GHOST_WIDTH }
}

function seal(r: number, deg: number, placed: PlacedSeal, tone: SealTone, isNew: boolean): WheelMark {
  return { ...at(r, deg), kind: "seal", seal: placed, tone, isNew, width: placed.width }
}

export function wheelModel(
  explorer: AchievementsExplorer,
  emotions: AchievementsEmotions | null,
  domains: AchievementsDomains | null,
): WheelModel {
  const seats = emotions?.seats ?? []
  const band = domains ? bandItems(domains) : []
  const lit = explorer.twentyFive?.count ?? 0
  const first = explorer.firstPebble
  return {
    tracks: {
      first_five: explorer.ringsDone.includes("first_five"),
      six_firsts: explorer.ringsDone.includes("six_firsts"),
      twenty_five: explorer.ringsDone.includes("twenty_five"),
      domains: explorer.ringsDone.includes("domains"),
    },
    families: familyRuns(seats).map((run) => {
      const half = step(seats.length) / 2
      const from = emotionAngle(run.start, seats.length) - half
      const to = emotionAngle(run.start + run.length - 1, seats.length) + half
      const [x1, y1] = polar(WHEEL_RADII.emotions + 6, from)
      const [x2, y2] = polar(WHEEL_RADII.emotions + 11, from)
      return { family: run.family, arc: arcPath(WHEEL_RADII.emotions, from + 1.2, to - 1.2), tick: { x1, y1, x2, y2 } }
    }),
    emotions: seats.map((s, k) => {
      const deg = emotionAngle(k, seats.length)
      if (!s.met) return ghost(WHEEL_RADII.emotions, deg, s.isNew)
      // A met seat whose pebble is gone prints as the even medium stone.
      const valence = s.pebble ?? NEUTRAL_MEDIUM
      return seal(WHEEL_RADII.emotions, deg, { ...pebbleSeal(valence), width: seatWidth(valence.intensity) }, { by: "family", family: s.family }, s.isNew)
    }),
    domains: band.map((d, i) => {
      const deg = seatAngle(i, band.length)
      return d.on ? seal(WHEEL_RADII.domains, deg, DOMAIN_SEAL, { by: "ink" }, d.isNew) : ghost(WHEEL_RADII.domains, deg, d.isNew)
    }),
    // Always drawn: without the server's count, 25 ghosts.
    twentyFive: Array.from({ length: TWENTY_FIVE_SEATS }, (_, i) => {
      const deg = seatAngle(i, TWENTY_FIVE_SEATS)
      return i < lit ? seal(WHEEL_RADII.twenty_five, deg, TWENTY_FIVE_SEAL, { by: "ink" }, false) : ghost(WHEEL_RADII.twenty_five, deg, false, 2.6)
    }),
    sixFirsts: explorer.sixFirsts.map((s, i) => {
      const deg = seatAngle(i, explorer.sixFirsts.length)
      return s.done && s.kind ? seal(WHEEL_RADII.six_firsts, deg, SIX_FIRST_SEALS[s.kind], { by: "ink" }, s.isNew) : ghost(WHEEL_RADII.six_firsts, deg, s.isNew)
    }),
    firstFive: explorer.firstFive.map((f, i) => {
      const deg = firstFiveAngle(i, explorer.firstFive.length)
      if (!f.done || !f.task) return ghost(WHEEL_RADII.first_five, deg, f.isNew)
      return { ...at(WHEEL_RADII.first_five, deg), kind: "first-five", task: f.task, isNew: f.isNew, width: FIRST_FIVE_MARK_WIDTH }
    }),
    centre: first
      ? { x: 0, y: 0, kind: "seal", seal: { ...pebbleSeal(first), width: 25 }, tone: { by: "emotion", emotionId: first.emotionId }, isNew: false, width: 25 }
      : { x: 0, y: 0, kind: "ghost", r: 4, isNew: false, width: GHOST_WIDTH },
  }
}
