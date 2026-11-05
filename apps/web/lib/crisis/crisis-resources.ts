import raw from "./crisis-resources.json"
import type { Locale } from "@/lib/i18n"

export type Localized = { en: string; fr: string }
export type CrisisLine = { label: Localized; number: string; tel: string; note?: Localized }
export type CrisisCountry = { code: string; name: Localized; lines: CrisisLine[] }
export type CrisisResources = {
  version: number
  verified_on: string
  worldwide: { label: Localized; url: string }
  countries: CrisisCountry[]
}

export const crisisResources: CrisisResources = raw

/** The country to open on: the device region when listed, otherwise FR (the product's home market), never "none". */
export function defaultCountry(
  region: string | undefined,
  resources: CrisisResources = crisisResources,
): CrisisCountry {
  const found = region
    ? resources.countries.find((c) => c.code === region.toUpperCase())
    : undefined
  return found ?? resources.countries.find((c) => c.code === "FR") ?? resources.countries[0]
}

export function pick(text: Localized, locale: Locale): string {
  return locale === "fr" ? text.fr : text.en
}
