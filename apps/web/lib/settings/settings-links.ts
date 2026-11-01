import seed from "./__fixtures__/settings-links.json"

export const SETTINGS_LINK_SECTIONS = ["legal", "about", "support"] as const
export type SettingsLinkSection = (typeof SETTINGS_LINK_SECTIONS)[number]
export type SettingsLink = { key: string; section: SettingsLinkSection; title: string; url: string; external: boolean }
export type SettingsLinkGroup = { section: SettingsLinkSection; links: SettingsLink[] }

/** The seed rows, verbatim: shown before the first fetch and whenever it fails (spec §3). */
export const DEFAULT_SETTINGS_LINKS: unknown = seed

function isRecord(v: unknown): v is Record<string, unknown> {
  return typeof v === "object" && v !== null && !Array.isArray(v)
}
function text(v: unknown): string | null {
  return typeof v === "string" && v.trim() !== "" ? v : null
}

/** `settings_links` rows → links in the reader's language. A row this build cannot show is dropped. */
export function decodeSettingsLinks(rows: unknown, locale: "en" | "fr"): SettingsLink[] {
  if (!Array.isArray(rows)) return []
  return rows.flatMap((row) => {
    if (!isRecord(row)) return []
    const key = text(row.key)
    const section = SETTINGS_LINK_SECTIONS.find((s) => s === row.section)
    const titleEn = text(row.title_en)
    const url = text(row.url)
    if (!key || !section || !titleEn || !url || !/^(https:\/\/|mailto:)/.test(url)) {
      console.warn("[settings] link dropped", row)
      return []
    }
    const title = (locale === "fr" ? text(row.title_fr) : null) ?? titleEn
    return [{ key, section, title, url, external: url.startsWith("https://") }]
  })
}

/** Sections in the fixed order, each keeping the rows' order; empty sections are omitted. */
export function groupSettingsLinks(links: SettingsLink[]): SettingsLinkGroup[] {
  return SETTINGS_LINK_SECTIONS.map((section) => ({ section, links: links.filter((l) => l.section === section) }))
    .filter((g) => g.links.length > 0)
}
