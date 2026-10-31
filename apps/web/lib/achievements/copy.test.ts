import { createTranslator } from "next-intl"
import { describe, expect, it } from "vitest"
import en from "@/lib/i18n/messages/en.json"
import fr from "@/lib/i18n/messages/fr.json"
import { designPage } from "./__fixtures__/design"
import { achievementsWords, type Translate } from "./copy"
import type { CountingLadder, YearNextStep } from "@/lib/types"

// The page's words against iOS's own tests (AchievementsCopyTests,
// AchievementsA11yTests), on the canonical design fixture: word for word.

function words(locale: "en" | "fr") {
  const t = createTranslator({ locale, messages: locale === "en" ? en : fr, namespace: "achievements.page" })
  return achievementsWords(t as unknown as Translate, locale)
}
const EN = words("en")
const FR = words("fr")
const page = designPage()
const n = (v: number) => new Intl.NumberFormat("fr").format(v)

function sections() {
  const { explorer, emotions, domains, pebbles, glyphsCarved, glyphSales, years } = page
  if (!explorer || !emotions || !domains || !pebbles || !glyphsCarved || !glyphSales) throw new Error("fixture lost a section")
  return { explorer, emotions, domains, pebbles, glyphsCarved, glyphSales, year: years[0] }
}
const s = sections()

const ladder = (count: number, levels: number[], next: { threshold: number; progress: number } | null): CountingLadder => ({
  count,
  levels: levels.map((threshold) => ({ threshold, on: null, pebble: null, isNew: false })),
  next: next ? { ...next, left: next.threshold - count } : null,
  ladder: [1, 10, 25, 50, 100, 250, 500, 1000],
  thenEvery: 500,
  since: null,
})
const step = (over: Partial<YearNextStep>): YearNextStep => ({ month: 11, needs: "moments", daysLeft: 12, reachable: true, ...over })

describe("the header and the cards", () => {
  it("says since a full date, then days, singular on the first", () => {
    expect(EN.since("2026-03-01")).toBe("since Sunday, March 1, 2026")
    expect(FR.since("2026-03-01")).toBe("depuis le dimanche 1 mars 2026")
    expect([EN.daysUnit(249), EN.daysUnit(1), FR.daysUnit(249), FR.daysUnit(1)]).toEqual(["days", "day", "jours", "jour"])
  })

  it("titles the cards and hints at them", () => {
    const cards = ["explorer", "emotions", "domains", "pebbles", "year"] as const
    expect(cards.map((c) => EN.cardTitle(c))).toEqual(["Explorer", "Emotions", "Domains", "Pebbles", "The year"])
    expect(cards.map((c) => FR.cardTitle(c))).toEqual(["Explorateur", "Émotions", "Domaines", "Galets", "L'année"])
    expect(EN.sinceHint("2026-03-02")).toBe("since March 2")
    expect(FR.sinceHint("2026-03-02")).toBe("depuis le 2 mars")
    expect(FR.domainsHint()).toBe("dans l'ordre d'arrivée")
    expect(EN.yearHint(s.year)).toBe("in progress")
    expect(EN.yearHint({ ...s.year, current: false })).toBeNull()
  })

  it("says what the coverage means, and nothing for an unknown tier", () => {
    expect(EN.coverage("most")).toBe("Your pebbles reach most parts of your life. Your insights have plenty to read.")
    expect(FR.coverage("most")).toBe("Tes galets touchent la plupart des parts de ta vie. Tes aperçus ont de quoi lire.")
    expect(EN.coverage(null)).toBeNull()
  })
})

describe("the pebbles line", () => {
  it("counts the stones the server lists and names the one that rises", () => {
    expect(EN.pebblesLine(s.pebbles)).toBe("137 pebbles. Five stones stand; the sixth rises toward 250.")
    expect(FR.pebblesLine(s.pebbles)).toBe("137 galets. Cinq pierres se dressent ; la sixième s'élève vers 250.")
    const one = ladder(1, [1], { threshold: 10, progress: 0 })
    expect(EN.pebblesLine(one)).toBe("1 pebble. One stone stands; the second rises toward 10.")
    expect(FR.pebblesLine(one)).toBe("1 galet. Une pierre se dresse ; la deuxième s'élève vers 10.")
    const none = ladder(0, [], { threshold: 1, progress: 0 })
    expect(EN.pebblesLine(none)).toBe("0 pebbles. No stone stands yet; the first rises toward 1.")
    expect(FR.pebblesLine(none)).toBe("0 galet. Aucune pierre ne se dresse encore ; la première s'élève vers 1.")
    const all = ladder(1137, [1, 10, 25, 50, 100, 250, 500, 1000], { threshold: 1500, progress: 0.274 })
    expect(EN.pebblesLine(all)).toBe("1,137 pebbles. Every stone stands; the next mark is 1,500.")
    expect(FR.pebblesLine(all)).toBe(`${n(1137)} galets. Toutes les pierres se dressent ; le prochain cap est ${n(1500)}.`)
    expect(EN.stones(ladder(10234, [], null))).toBe("Every stone stands.")
  })
})

describe("the year's next step", () => {
  it("says what it needs, how many days are left, or that it can't be reached", () => {
    expect(EN.nextStep(step({}))).toBe("Twelve more days with a moment, one in each full week, bring November to the inner disc.")
    expect(FR.nextStep(step({}))).toBe(
      "Douze jours de plus avec un moment, un dans chaque semaine complète : novembre rejoindra le disque intérieur.",
    )
    const one = step({ month: 4, needs: "pebbles", daysLeft: 1 })
    expect(EN.nextStep(one)).toBe("One more day with a pebble, and one in each full week, bring April to the rim.")
    expect(FR.nextStep(one)).toBe("Un jour de plus avec un galet, et un dans chaque semaine complète : avril atteindra le bord.")
    expect(EN.nextStep(step({ month: 4, needs: "pebbles", daysLeft: 0 }))).toBe("Keep a pebble in every full week and April reaches the rim.")
    expect(EN.nextStep(step({ reachable: false }))).toBe("November can't reach the inner disc this month.")
    expect(FR.nextStep(step({ reachable: false }))).toBe("Novembre n'atteindra pas le disque intérieur ce mois-ci.")
    expect(FR.nextStep(step({ month: 4, needs: "pebbles", reachable: false }))).toBe("Avril n'atteindra pas le bord ce mois-ci.")
    expect(EN.nextStep(step({ needs: null }))).toBeNull()
    expect(EN.monthInSentence(13)).toBe("13")
  })
})

describe("the ceremony", () => {
  it("names its stack as the catalog does", () => {
    expect(EN.ceremonyTitle("emotions")).toBe("Every emotion")
    expect(EN.ceremonyBody("emotions")).toBe("Your pebbles have carried every feeling on the wheel.")
    expect(FR.ceremonyTitle("six_firsts")).toBe("Les six premières fois")
    expect(EN.ready()).toBe("ready")
    expect(FR.ready()).toBe("prêt")
    expect(EN.ceremonyError()).toBe("Couldn't open the ceremony. Please try again.")
    expect(FR.ceremonyError()).toBe("Impossible d'ouvrir la cérémonie. Réessaie.")
  })
})

describe("what the page reads aloud", () => {
  it("reads the header", () => {
    expect(EN.a11y.header(249, "2026-03-01")).toBe("249 days, since Sunday, March 1, 2026")
    expect(FR.a11y.header(249, "2026-03-01")).toBe("249 jours, depuis le dimanche 1 mars 2026")
    expect(EN.a11y.header(1, null)).toBe("1 day")
  })

  it("reads Explorer's rings, done from rings_done, then what is new", () => {
    expect(EN.a11y.explorer(s.explorer, s.emotions, s.domains)).toBe(
      "Explorer: the first five, done; the six firsts, done; 25 pebbles, done; domains, 14 of 18; emotions, 23 of 38. 3 new.",
    )
    expect(FR.a11y.explorer(s.explorer, s.emotions, s.domains)).toBe(
      "Explorateur : les cinq premières fois, c'est fait ; les six premières fois, c'est fait ; 25 galets, c'est fait ; domaines, 14 sur 18 ; émotions, 23 sur 38. 3 nouveautés.",
    )
    expect(EN.a11y.explorer({ ...s.explorer, ringsDone: [], twentyFive: null }, null, null)).toBe(
      "Explorer: the first five, 5 of 5; the six firsts, 6 of 6.",
    )
  })

  it("reads the emotions, the domains and the pebbles", () => {
    expect(EN.a11y.emotions(s.emotions)).toBe("Emotions, 23 of 38. 2 new.")
    expect(FR.a11y.emotions(s.emotions)).toBe("Émotions, 23 sur 38. 2 nouveautés.")
    expect(EN.a11y.domains(s.domains)).toBe("Domains, 14 of 18. Your pebbles reach most parts of your life. Your insights have plenty to read. 1 new.")
    expect(EN.a11y.pebbles(s.pebbles, s.glyphsCarved, s.glyphSales)).toBe(
      "Pebbles. 137 pebbles. Five stones stand; the sixth rises toward 250. Glyphs carved: 12. Glyph sales: 3.",
    )
    expect(EN.a11y.pebbles(s.pebbles, null, null)).toBe("Pebbles. 137 pebbles. Five stones stand; the sixth rises toward 250.")
  })

  it("reads the year as the server's lists, then the next step", () => {
    expect(EN.a11y.year(s.year)).toBe(
      "The year 2026. On the rim: August. On the inner disc: March, April, May, June, July, September, and October. " +
        "On their way: November. Twelve more days with a moment, one in each full week, bring November to the inner disc.",
    )
    expect(FR.a11y.year(s.year)).toBe(
      "L'année 2026. Au bord : août. Sur le disque intérieur : mars, avril, mai, juin, juillet, septembre et octobre. " +
        "En chemin : novembre. Douze jours de plus avec un moment, un dans chaque semaine complète : novembre rejoindra le disque intérieur.",
    )
    const empty = { ...s.year, months: s.year.months.map((m) => ({ ...m, level: 0 as const, progress: 0 })), nextStep: null }
    expect(EN.a11y.disc(empty)).toBe("The year 2026. On the rim: none. On the inner disc: none. On their way: none.")
  })

  it("reads a ready mark as its ceremony being ready", () => {
    expect(EN.a11y.ready("six_firsts")).toBe("The six firsts: the ceremony is ready.")
    expect(FR.a11y.ready("emotions")).toBe("Toutes les émotions : la cérémonie est prête.")
  })
})
