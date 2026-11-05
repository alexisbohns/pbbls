"use client"

import { useEffect, useState } from "react"
import { Phone, Globe } from "lucide-react"
import { useTranslations } from "next-intl"
import { SettingsGroup } from "@/components/settings/SettingsGroup"
import { SettingsRow } from "@/components/settings/SettingsRow"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { crisisResources, defaultCountry, pick } from "@/lib/crisis/crisis-resources"
import { useFormatDate } from "@/lib/i18n/format"
import { useLocale } from "@/lib/i18n"

/** The country picker and the lines for it, in the normal text colour (no alarm). */
export function CrisisResourcesList() {
  const t = useTranslations("settings.support")
  const { locale } = useLocale()
  const formatDate = useFormatDate()
  // SSR-safe: the first render is the default (FR), then the device region.
  const [code, setCode] = useState(() => defaultCountry(undefined).code)

  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    setCode(defaultCountry(navigator.language.split("-")[1]).code)
  }, [])
  /* eslint-enable react-hooks/set-state-in-effect */

  const country = crisisResources.countries.find((c) => c.code === code) ?? defaultCountry(code)
  const items = crisisResources.countries.map((c) => ({ value: c.code, label: pick(c.name, locale) }))
  const { worldwide } = crisisResources

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-2">
        <label htmlFor="crisis-country" className="m3-type-label-large text-m3-on-surface-variant">
          {t("country")}
        </label>
        <Select items={items} value={country.code} onValueChange={(v) => v && setCode(v)}>
          <SelectTrigger id="crisis-country" className="w-full">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {items.map((item) => (
              <SelectItem key={item.value} value={item.value}>
                {item.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <SettingsGroup aria-label={pick(country.name, locale)}>
        {country.lines.map((line) => (
          <SettingsRow key={line.tel} icon={Phone} href={`tel:${line.tel}`} external>
            <span className="flex flex-col">
              <span className="m3-type-body-large text-m3-on-surface">{pick(line.label, locale)}</span>
              <span className="m3-type-title-medium text-m3-primary">{line.number}</span>
              {line.note ? (
                <span className="m3-type-body-medium text-m3-on-surface-variant">
                  {pick(line.note, locale)}
                </span>
              ) : null}
            </span>
          </SettingsRow>
        ))}
      </SettingsGroup>

      {worldwide.url.startsWith("https://") ? (
        <SettingsGroup>
          <SettingsRow icon={Globe} href={worldwide.url} external>
            {pick(worldwide.label, locale)}
          </SettingsRow>
        </SettingsGroup>
      ) : null}

      <p className="m3-type-body-small text-m3-on-surface-variant">
        {t("verified", { date: formatDate(`${crisisResources.verified_on}T12:00:00`, { dateStyle: "long" }) })}
      </p>
    </div>
  )
}
