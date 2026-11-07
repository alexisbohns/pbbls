import { readFileSync } from "node:fs"
import { join } from "node:path"
import { describe, it, expect } from "vitest"
import { getAllLocaleContent, getDocsManifest } from "./load-docs"

// The Play Data safety form as exported from Play Console, kept in the repo so a
// change to what the app collects updates the form, this page and the privacy
// policy together (CLAUDE.md, "Store privacy declarations").
const CSV_PATH = join(__dirname, "../../../../docs/compliance/play-data-safety.csv")
// The iOS privacy manifest, which the App Store privacy labels mirror.
const MANIFEST_PATH = join(__dirname, "../../../ios/Pebbles/Resources/PrivacyInfo.xcprivacy")

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

// Play's data types and purposes in Apple's vocabulary. A type or purpose Play
// declares that is missing here fails the test below: map it, then declare it in
// the manifest.
const APPLE_TYPE: Record<string, string> = {
  PSL_NAME: "Name",
  PSL_EMAIL: "EmailAddress",
  PSL_USER_ACCOUNT: "UserID",
  PSL_PHOTOS: "PhotosorVideos",
  PSL_VIDEOS: "PhotosorVideos",
  PSL_AUDIO: "AudioData",
  PSL_HEALTH: "Health",
  PSL_FITNESS: "Fitness",
  PSL_USER_INTERACTION: "ProductInteraction",
  PSL_USER_GENERATED_CONTENT: "OtherUserContent",
  PSL_OTHER_APP_ACTIVITY: "OtherDataTypes",
  PSL_CRASH_LOGS: "CrashData",
  PSL_PERFORMANCE_DIAGNOSTICS: "PerformanceData",
  PSL_APPROX_LOCATION: "CoarseLocation",
  PSL_PRECISE_LOCATION: "PreciseLocation",
  PSL_CONTACTS: "Contacts",
}

const APPLE_PURPOSE: Record<string, string> = {
  PSL_APP_FUNCTIONALITY: "AppFunctionality",
  PSL_ACCOUNT_MANAGEMENT: "AppFunctionality",
  PSL_ANALYTICS: "Analytics",
  PSL_PERSONALIZATION: "ProductPersonalization",
  PSL_ADVERTISING: "ThirdPartyAdvertising",
  PSL_DEVELOPER_COMMUNICATIONS: "DeveloperAdvertising",
}

type AppleEntry = { type: string; linked: boolean; tracking: boolean; purposes: string[] }

function readManifest(): { tracking: boolean; entries: AppleEntry[] } {
  const xml = readFileSync(MANIFEST_PATH, "utf8").replace(/<!--[\s\S]*?-->/g, "")
  const start = xml.indexOf("<key>NSPrivacyCollectedDataTypes</key>")
  const body = xml.slice(start)
  const entries = [...body.matchAll(/<dict>([\s\S]*?)<\/dict>/g)].map(([, dict = ""]) => ({
    type: dict.match(/<string>NSPrivacyCollectedDataType(\w+)<\/string>/)?.[1] ?? "",
    linked: /<key>NSPrivacyCollectedDataTypeLinked<\/key>\s*<true\/>/.test(dict),
    tracking: /<key>NSPrivacyCollectedDataTypeTracking<\/key>\s*<true\/>/.test(dict),
    purposes: [...dict.matchAll(/<string>NSPrivacyCollectedDataTypePurpose(\w+)<\/string>/g)].map(([, p = ""]) => p),
  }))
  return { tracking: /<key>NSPrivacyTracking<\/key>\s*<true\/>/.test(xml), entries }
}

describe("the App Store privacy labels (iOS privacy manifest)", () => {
  const form = readForm()
  const declared = form.filter((row) => row.question.startsWith("PSL_DATA_TYPES_") && row.value === "true").map((row) => row.response)
  const manifest = readManifest()

  it("declares no tracking, and every type as linked to the person", () => {
    expect(manifest.tracking).toBe(false)
    for (const entry of manifest.entries) {
      expect(entry.tracking, `${entry.type} tracking`).toBe(false)
      expect(entry.linked, `${entry.type} linked`).toBe(true)
    }
  })

  it("declares the same data types as the Play form", () => {
    for (const type of declared) expect(APPLE_TYPE[type], `${type} has no Apple data type in APPLE_TYPE`).toBeDefined()
    const types = manifest.entries.map((entry) => entry.type)
    expect(new Set(types).size).toBe(types.length)
    expect(new Set(types)).toEqual(new Set(declared.map((type) => APPLE_TYPE[type])))
  })

  it("gives each type the purposes the Play form gives it", () => {
    const expected = new Map<string, Set<string>>()
    for (const type of declared) {
      const purposes = form
        .filter((row) => row.question === `PSL_DATA_USAGE_RESPONSES:${type}:DATA_USAGE_COLLECTION_PURPOSE` && row.value === "true")
        .map((row) => row.response)
      for (const purpose of purposes) expect(APPLE_PURPOSE[purpose], `${purpose} has no Apple purpose in APPLE_PURPOSE`).toBeDefined()
      const apple = APPLE_TYPE[type] ?? ""
      const set = expected.get(apple) ?? new Set<string>()
      for (const purpose of purposes) set.add(APPLE_PURPOSE[purpose] ?? "")
      expected.set(apple, set)
    }
    for (const entry of manifest.entries) expect(new Set(entry.purposes), entry.type).toEqual(expected.get(entry.type))
  })
})
