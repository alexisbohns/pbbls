import { readFileSync } from "node:fs"
import { join } from "node:path"
import { describe, it, expect } from "vitest"
import { getAllLocaleContent, getDocsManifest } from "./load-docs"

// The Play Data safety form as exported from Play Console, kept in the repo so a
// change to what the app collects updates the form, this page and the privacy
// policy together (CLAUDE.md, "Store privacy declarations").
const CSV_PATH = join(__dirname, "../../../../docs/compliance/play-data-safety.csv")

type Row = { question: string; response: string; value: string }

function parseCsv(text: string): string[][] {
  const rows: string[][] = []
  let row: string[] = []
  let field = ""
  let quoted = false
  for (let i = 0; i < text.length; i++) {
    const c = text[i]
    if (quoted) {
      if (c === '"' && text[i + 1] === '"') {
        field += '"'
        i++
      } else if (c === '"') quoted = false
      else field += c
    } else if (c === '"') quoted = true
    else if (c === ",") {
      row.push(field)
      field = ""
    } else if (c === "\n" || c === "\r") {
      if (c === "\r" && text[i + 1] === "\n") i++
      row.push(field)
      rows.push(row)
      row = []
      field = ""
    } else field += c
  }
  if (field !== "" || row.length > 0) {
    row.push(field)
    rows.push(row)
  }
  return rows
}

function readForm(): Row[] {
  const [, ...rows] = parseCsv(readFileSync(CSV_PATH, "utf8"))
  return rows.map(([question = "", response = "", value = ""]) => ({ question, response, value }))
}

type Label = { en: string; fr: string }

// What the page calls each data type Play asks about. Every type in the form is
// either collected or listed as not collected, so a new declaration fails here
// until the page says it.
const COLLECTED: Record<string, Label> = {
  PSL_NAME: { en: "Your name", fr: "Votre nom" },
  PSL_EMAIL: { en: "Your email address", fr: "Votre adresse e-mail" },
  PSL_USER_ACCOUNT: { en: "Your account ID", fr: "Votre identifiant de compte" },
  PSL_PHOTOS: { en: "Photos you add", fr: "Les photos que vous ajoutez" },
  PSL_HEALTH: { en: "How you feel", fr: "Ce que vous ressentez" },
  PSL_USER_GENERATED_CONTENT: { en: "What you write and make", fr: "Ce que vous écrivez et créez" },
  PSL_USER_INTERACTION: { en: "Your activity", fr: "Votre activité" },
  PSL_OTHER_APP_ACTIVITY: { en: "Your onboarding answers", fr: "Vos réponses à l'accueil" },
}

const NOT_COLLECTED: { types: string[]; label: Label }[] = [
  { types: ["PSL_APPROX_LOCATION", "PSL_PRECISE_LOCATION"], label: { en: "Your location", fr: "Votre position" } },
  { types: ["PSL_CONTACTS"], label: { en: "Your phone's contacts", fr: "Les contacts de votre téléphone" } },
  { types: ["PSL_EMAILS", "PSL_SMS_CALL_LOG", "PSL_OTHER_MESSAGES"], label: { en: "Your emails, texts or calls", fr: "Vos e-mails, SMS ou appels" } },
  { types: ["PSL_VIDEOS"], label: { en: "Videos", fr: "Vidéos" } },
  { types: ["PSL_AUDIO", "PSL_MUSIC", "PSL_OTHER_AUDIO"], label: { en: "Voice or audio recordings", fr: "Enregistrements vocaux ou audio" } },
  { types: ["PSL_FILES_AND_DOCS", "PSL_CALENDAR"], label: { en: "Your files or calendar", fr: "Vos fichiers ou votre agenda" } },
  {
    types: ["PSL_CREDIT_DEBIT_BANK_ACCOUNT_NUMBER", "PSL_PURCHASE_HISTORY", "PSL_CREDIT_SCORE", "PSL_OTHER"],
    label: { en: "Payment or financial information", fr: "Vos informations de paiement ou financières" },
  },
  { types: ["PSL_DEVICE_ID"], label: { en: "Device or advertising identifiers", fr: "Identifiants d'appareil ou publicitaires" } },
  {
    types: ["PSL_CRASH_LOGS", "PSL_PERFORMANCE_DIAGNOSTICS", "PSL_OTHER_PERFORMANCE"],
    label: { en: "Crash reports or diagnostics", fr: "Rapports de plantage ou diagnostics" },
  },
  {
    types: ["PSL_WEB_BROWSING_HISTORY", "PSL_IN_APP_SEARCH_HISTORY", "PSL_APPS_ON_DEVICE"],
    label: { en: "Your browsing history, searches or other apps", fr: "Votre historique de navigation, vos recherches ou vos autres applications" },
  },
  {
    types: ["PSL_ADDRESS", "PSL_PHONE", "PSL_RACE_ETHNICITY", "PSL_POLITICAL_RELIGIOUS", "PSL_SEXUAL_ORIENTATION_GENDER_IDENTITY", "PSL_OTHER_PERSONAL"],
    label: { en: "Your address, phone number or other personal details", fr: "Votre adresse, votre numéro de téléphone ou d'autres informations personnelles" },
  },
  { types: ["PSL_FITNESS"], label: { en: "Fitness data", fr: "Vos données d'activité physique" } },
]

const NOT_COLLECTED_HEADING = { en: "What we don't collect", fr: "Ce que nous ne collectons pas" }

describe("the Data safety page", () => {
  const form = readForm()
  const typeRows = form.filter((row) => row.question.startsWith("PSL_DATA_TYPES_"))
  const declared = new Set(typeRows.filter((row) => row.value === "true").map((row) => row.response))
  const pages = getAllLocaleContent("data-safety")

  it("is listed in the manifest", () => {
    expect(getDocsManifest().pages.map((page) => page.slug)).toContain("data-safety")
  })

  it("names every data type the form asks about, collected or not", () => {
    const covered = [...Object.keys(COLLECTED), ...NOT_COLLECTED.flatMap((group) => group.types)]
    expect(new Set(covered).size).toBe(covered.length)
    expect(new Set(covered)).toEqual(new Set(typeRows.map((row) => row.response)))
  })

  it("lists what the form declares as collected, and only that", () => {
    expect(declared).toEqual(new Set(Object.keys(COLLECTED)))
    for (const group of NOT_COLLECTED) {
      for (const type of group.types) expect(declared.has(type), `${type} is declared but the page says it is not collected`).toBe(false)
    }
  })

  it("puts each label in the right section, in both languages", () => {
    for (const locale of ["en", "fr"] as const) {
      const [collected, notCollected] = pages[locale].html.split(NOT_COLLECTED_HEADING[locale])
      expect(notCollected, `${locale}: the "not collected" heading`).toBeDefined()
      for (const label of Object.values(COLLECTED)) expect(collected).toContain(label[locale])
      for (const { label } of NOT_COLLECTED) expect(notCollected).toContain(label[locale])
    }
  })

  it("says nothing is shared, matching the form", () => {
    const shared = form.filter((row) => row.response === "PSL_DATA_USAGE_ONLY_SHARED" && row.value === "true")
    expect(shared).toEqual([])
  })

  it("points to the deletion pages the form gives Play", () => {
    const slugs = getDocsManifest().pages.map((page) => page.slug)
    for (const question of ["PSL_ACCOUNT_DELETION_URL", "PSL_DATA_DELETION_URL"]) {
      const url = form.find((row) => row.question === question)?.value ?? ""
      const match = url.match(/^https:\/\/www\.pbbls\.app\/docs\/([a-z-]+)$/)
      expect(match, `${question}: ${url}`).not.toBeNull()
      const slug = match?.[1] ?? ""
      expect(slugs).toContain(slug)
      for (const page of Object.values(pages)) expect(page.html).toContain(`href="/docs/${slug}"`)
    }
  })
})
