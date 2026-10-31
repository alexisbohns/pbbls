import { createTranslator } from "next-intl"
import { describe, expect, it } from "vitest"
import en from "@/lib/i18n/messages/en.json"
import fr from "@/lib/i18n/messages/fr.json"
import { designNames, designPage } from "./__fixtures__/design"
import { achievementsWords, type Translate } from "./copy"
import { domainSections, earlierYears, emotionSections, explorerSections, ladderSection, milestonesNote, yearRows } from "./sheet-rows"
import { NO_SHEETS, closeSheet, isShown, needsPrune, pushSheet, sameSheet, sheetsReducer, shownStack, type SheetKey, type SheetsState } from "./sheets"
import type { AchievementsPage } from "@/lib/types"

// The sheets against iOS's own tests (AchievementsSheetRowsTests,
// AchievementSheetRoutingTests), on the canonical design fixture.

function words(locale: "en" | "fr") {
  const messages = locale === "en" ? en : fr
  const t = createTranslator({ locale, messages, namespace: "achievements.page" })
  const root = createTranslator({ locale, messages })
  return achievementsWords(t as unknown as Translate, locale, root as unknown as Translate)
}
const EN = words("en")
const FR = words("fr")
const page = designPage()
const names = designNames()

function sections() {
  const { explorer, emotions, domains, pebbles, glyphsCarved, glyphSales, years } = page
  if (!explorer || !emotions || !domains || !pebbles || !glyphsCarved || !glyphSales) throw new Error("fixture lost a section")
  return { explorer, emotions, domains, pebbles, glyphsCarved, glyphSales, year: years[0] }
}
const s = sections()

const explorer: SheetKey = { card: "explorer" }
const domains: SheetKey = { card: "domains" }

describe("the sheet stack", () => {
  it("pushes on top, brings back one already open, and pops from a depth", () => {
    expect(pushSheet([explorer], domains)).toEqual([explorer, domains])
    expect(pushSheet([explorer, domains], explorer)).toEqual([explorer])
    expect(pushSheet([{ card: "year", year: 2026 }], { card: "year", year: 2025 })).toHaveLength(2)
    expect(closeSheet([explorer, domains], 1)).toEqual([explorer])
    expect(sameSheet({ card: "year", year: 2026 }, { card: "year", year: 2025 })).toBe(false)
  })

  it("closes a sheet whose section left the payload, with everything above it", () => {
    const gone: AchievementsPage = { ...page, domains: null }
    expect(isShown(domains, gone)).toBe(false)
    expect(shownStack([explorer, domains, { card: "emotions" }], gone)).toEqual([explorer])
    expect(shownStack([{ card: "year", year: 2025 }], page)).toEqual([])
    expect(shownStack([explorer], null)).toEqual([])
  })
})

describe("the Explorer sheet", () => {
  const sec = explorerSections(s.explorer, s.emotions, s.domains, EN)

  it("lists the first five, the six firsts, the 25th, then the rows to Domains and Emotions", () => {
    expect(sec.map((x) => x.id)).toEqual(["first-five", "six-firsts", "twenty-five", "domains-emotions"])
    const [five, six, twentyFive, outer] = sec
    expect(five.note).toBe("five of five")
    expect(five.rows.map((r) => r.name)).toEqual(["First moment", "First pebble", "First collection", "First soul", "First glyph"])
    expect(five.rows[0]).toMatchObject({ sub: "Untitled", opens: { kind: "moment", momentId: "a0000000-0000-4000-8000-000000000000" } })
    expect(five.rows[1]).toMatchObject({ sub: "Mara moves in", date: "Mar 2", label: "First pebble, Mara moves in, Mar 2" })
    expect(five.rows[2]).toMatchObject({ sub: "Elio, year one", date: "Apr 10", opens: undefined, mark: { kind: "firstFive", task: "first-collection" } })
    expect(six.rows.map((r) => r.name)).toEqual(["Small", "Medium", "Large", "Lowlight", "Neutral", "Highlight"])
    expect(six.rows[3]).toMatchObject({ sub: "Gym, finally", date: "Mar 8" })
    expect([twentyFive.note, twentyFive.rows[0]?.name]).toEqual(["twenty-five of twenty-five", "The 25th"])
    expect(outer.rows.map((r) => [r.opens, r.sub, r.mark.kind])).toEqual([
      [{ kind: "sheet", sheet: { card: "domains" } }, "14 of 18", "domain"],
      [{ kind: "sheet", sheet: { card: "emotions" } }, "23 of 38", "rosace"],
    ])
  })

  it("says the 25th is still to come, and names what it cannot as a first", () => {
    const waiting = explorerSections({ ...s.explorer, twentyFive: { count: 0, done: false, on: null, pebble: null, isNew: false } }, null, null, EN)
    expect(waiting[2]).toMatchObject({ note: "zero of twenty-five", rows: [], quiet: "The 25th pebble is still to come." })
    const unknown = explorerSections(
      { ...s.explorer, firstFive: [{ task: null, done: true, on: "2026-03-02", subject: null, isNew: false }], sixFirsts: [{ kind: null, done: true, on: null, pebble: null, isNew: false }], twentyFive: null },
      null,
      null,
      EN,
    )
    expect(unknown.map((x) => x.id)).toEqual(["first-five", "six-firsts"])
    expect(unknown[0].rows[0]).toMatchObject({ name: "A first", mark: { kind: "ghost" }, date: "Mar 2" })
    expect(unknown[1].rows[0].name).toBe("A first")
    const todo = explorerSections({ ...s.explorer, sixFirsts: [{ kind: "small", done: false, on: null, pebble: null, isNew: false }] }, null, null, EN)
    expect(todo[1].rows[0].label).toBe("Small, not yet")
  })

  it("speaks French", () => {
    const fr = explorerSections(s.explorer, s.emotions, s.domains, FR)
    expect(fr[0].title).toBe("Les cinq premières fois")
    expect(fr[0].note).toBe("cinq sur cinq")
    expect(fr[1].rows.map((r) => r.name)).toEqual(["Petit", "Moyen", "Grand", "Creux", "Neutre", "Temps fort"])
    expect(fr[0].rows[0].sub).toBe("Sans titre")
  })
})

describe("the Emotions sheet", () => {
  it("lists the server's families in its order, met by the day met, then the rest", () => {
    const sec = emotionSections(s.emotions, names, EN)
    expect(sec).toHaveLength(7)
    const joy = sec[0]
    expect([joy.family, joy.note, joy.title]).toEqual(["joy", "5 of 6", "Joy"])
    expect(joy.rows.map((r) => r.id)).toEqual(["seat-3", "seat-4", "seat-1", "seat-2", "seat-0"])
    expect(joy.notYet).toHaveLength(1)
    expect(sec[5].rows.find((r) => r.id === "seat-29")?.isNew).toBe(true)
  })

  it("keeps what it cannot name under a fallback", () => {
    const sec = emotionSections(
      { ...s.emotions, seats: [{ seat: 0, emotionId: null, family: "awe", met: true, on: null, pebble: null, isNew: false }, { seat: 1, emotionId: null, family: "awe", met: false, on: null, pebble: null, isNew: false }], families: [{ family: "awe", met: [0], notYet: [1] }] },
      names,
      EN,
    )
    expect(sec[0]).toMatchObject({ title: "Other emotions", notYet: ["An emotion"] })
    expect(sec[0].rows[0]).toMatchObject({ name: "An emotion", opens: undefined, mark: { kind: "pebble", intensity: 2, positiveness: 0 } })
  })
})

describe("the Domains sheet", () => {
  it("lists the register newest first, numbered by arrival, then not yet", () => {
    const sec = domainSections(s.domains, names, EN)
    expect(sec[0].rows).toHaveLength(14)
    expect(sec[0].note).toBe("newest first")
    expect(sec[0].rows[0]).toMatchObject({ number: "No. 14", isNew: true, date: "Oct 1" })
    expect(sec[0].rows[13].number).toBe("No. 01")
    expect(sec[1].notYet).toHaveLength(4)
    expect(domainSections({ ...s.domains, entered: [{ domainId: "nope", on: null, pebble: null, isNew: false }], notYet: [] }, names, EN)[0].rows[0].name).toBe("A domain")
  })
})

describe("the Pebbles sheet", () => {
  it("lists the levels, the next with what is left, the steps ahead, then the tail", () => {
    expect(milestonesNote(s.pebbles, EN)).toBe("five of eight")
    const sec = ladderSection(s.pebbles, "milestones", "Milestones", undefined, EN)
    expect(sec.rows.map((r) => r.name)).toEqual(["1", "10", "25", "50", "100", "250", "500", "1,000"])
    expect(sec.rows.map((r) => r.style)).toEqual(["number", "number", "number", "number", "number", "next", "ghostNumber", "ghostNumber"])
    expect(sec.rows[0]).toMatchObject({ sub: "Mara moves in", date: "Mar 2" })
    expect(sec.rows[5]).toMatchObject({ next: "113 to go", mark: { kind: "stage", index: 5, fill: 0.247, done: false } })
    expect([sec.rows[5].date, sec.rows[5].opens]).toEqual([undefined, undefined])
    expect(sec.trailing).toBe("then every 500")
    expect(sec.rows.slice(5).map((r) => r.label)).toEqual(["250, 113 to go, not yet", "500, not yet", "1,000, not yet"])
    expect(ladderSection(s.pebbles, "milestones", "Jalons", undefined, FR).rows[5].label).toBe("250, encore 113, pas encore")
    const carved = ladderSection(s.glyphsCarved, "carved", "Glyphs carved", "12", EN)
    expect(carved.rows.map((r) => r.name)).toEqual(["10", "25", "50", "75", "100"])
    expect(carved.rows[0]).toMatchObject({ sub: undefined, date: "Sep 21", opens: undefined })
    expect(carved.trailing).toBeUndefined()
    const sales = ladderSection(s.glyphSales, "sales", "Glyph sales", "3", EN, "The buyer is never named.")
    expect([sales.trailing, sales.closing]).toEqual([undefined, "The buyer is never named."])
    const empty = ladderSection({ ...s.glyphSales, ladder: [], next: null, levels: [{ threshold: 5, on: null, pebble: null, isNew: false }] }, "e", "E", undefined, EN)
    expect(empty.rows[0].mark).toEqual({ kind: "stage", index: -1, fill: 1, done: true })
  })
})

describe("the year sheet", () => {
  it("rows the served months with their counts uncapped, done from the server, then the earlier years", () => {
    const rows = yearRows(s.year, EN)
    expect(rows.map((r) => r.month.month)).toEqual([11, 10, 9, 8, 7, 6, 5, 4, 3])
    const [november] = rows
    expect(november.name).toBe("November")
    expect(november.moments).toEqual({ value: "2", target: null, fill: 2 / 14, done: false, hued: false })
    expect(november.pebbles).toMatchObject({ value: "2", hued: true })
    expect(november.weeks).toEqual({ value: "—", target: null, fill: null, done: false, hued: false })
    const august = rows.find((r) => r.month.month === 8)
    expect(august?.moments).toEqual({ value: "19", target: null, fill: 1, done: true, hued: false })
    expect(august?.weeks).toEqual({ value: "4", target: "/4", fill: 1, done: true, hued: false })
    expect(november.label).toBe("November. Days with a moment: 2. With a pebble: 2. No full week yet.")
    expect(august?.label).toMatch(/^August\. Days with a moment: 19, reached\./)
    expect(earlierYears(s.year, page.years, EN)).toMatchObject({ rows: [], quiet: "None yet. 2026 is your first year here." })
    const past = { ...s.year, year: 2025, current: false }
    expect(earlierYears(s.year, [s.year, past], EN).rows.map((r) => r.opens)).toEqual([{ kind: "sheet", sheet: { card: "year", year: 2025 } }])
    expect(earlierYears(past, [s.year, past], EN).quiet).toBe("None yet. 2025 is your first year here.")
  })
})

describe("the sheets read aloud", () => {
  it("say what is new, and what is not yet as one", () => {
    expect(EN.sheet.row(["Joy", null, "Mar 2"], true)).toBe("Joy, Mar 2, new since you last looked")
    expect(FR.sheet.notYet(["Anxieux", "Amer"])).toBe("Pas encore : Anxieux, Amer")
  })
})

describe("the stack's state", () => {
  const open = (state: SheetsState, key: SheetKey) => sheetsReducer(state, { type: "open", key })

  it("opens on top, and closes with everything above it, drawn until each close has played", () => {
    let st = open(open(NO_SHEETS, explorer), domains)
    expect(st.opened.map((e) => e.key)).toEqual([explorer, domains])
    st = sheetsReducer(st, { type: "close", depth: 0 })
    expect(st.opened).toEqual([])
    expect(st.rendered).toHaveLength(2)
    st = sheetsReducer(st, { type: "closed", open: st.rendered[1].open })
    expect(st.rendered).toHaveLength(1)
    st = sheetsReducer(st, { type: "closed", open: st.rendered[0].open })
    expect(st.rendered).toEqual([])
  })

  it("closes only the top on its own dismissal, and ignores a close-played for a sheet still open", () => {
    let st = open(open(NO_SHEETS, explorer), domains)
    st = sheetsReducer(st, { type: "close", depth: 1 })
    expect(st.opened.map((e) => e.key)).toEqual([explorer])
    expect(sheetsReducer(st, { type: "closed", open: st.opened[0].open })).toBe(st)
  })

  it("marks a card seen once per open, and again on a new open", () => {
    let st = open(NO_SHEETS, explorer)
    const first = st.opened[0].open
    st = sheetsReducer(sheetsReducer(st, { type: "seen", open: first }), { type: "seen", open: first })
    expect(st.seen).toEqual([first])
    st = sheetsReducer(st, { type: "close", depth: 0 })
    st = open(st, explorer)
    expect(st.opened[0].open).not.toBe(first)
    expect(st.seen.includes(st.opened[0].open)).toBe(false)
  })

  it("prunes a sheet whose section left the payload, so it never comes back by itself", () => {
    let st = open(open(open(NO_SHEETS, explorer), domains), { card: "emotions" })
    const gone: AchievementsPage = { ...page, domains: null }
    expect(needsPrune(st, gone)).toBe(true)
    st = sheetsReducer(st, { type: "prune", page: gone })
    expect(st.opened.map((e) => e.key)).toEqual([explorer])
    expect(needsPrune(st, page)).toBe(false)
    expect(sheetsReducer(st, { type: "prune", page })).toBe(st)
  })

  it("holds a pebble until the sheets have closed", () => {
    let st = sheetsReducer(open(NO_SHEETS, explorer), { type: "openMoment", momentId: "m1" })
    expect([st.opened, st.pendingMoment]).toEqual([[], "m1"])
    st = sheetsReducer(st, { type: "movedOn" })
    expect(st.pendingMoment).toBeNull()
  })
})
