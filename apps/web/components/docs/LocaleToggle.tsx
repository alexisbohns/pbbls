"use client"

import { useDocsLocale } from "@/lib/hooks/useDocsLocale"
import { Button } from "@/components/ui/button"
import type { DocsLocale } from "@/lib/docs/types"

const LOCALE_LABELS: Record<DocsLocale, string> = {
  en: "EN",
  fr: "FR",
}

export function LocaleToggle() {
  const { locale, setLocale, locales } = useDocsLocale()

  return (
    <div role="group" aria-label="Language" className="flex gap-1">
      {locales.map((l) => (
        <Button
          key={l}
          variant={l === locale ? "secondary" : "ghost"}
          size="xs"
          aria-pressed={l === locale}
          onClick={() => setLocale(l)}
        >
          {LOCALE_LABELS[l]}
        </Button>
      ))}
    </div>
  )
}
