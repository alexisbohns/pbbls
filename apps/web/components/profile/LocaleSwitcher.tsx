"use client"

import { Check, Globe } from "lucide-react"
import { useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import {
  LOCALE_LABELS,
  SUPPORTED_LOCALES,
  useLocale,
  type Locale,
} from "@/lib/i18n"

export function LocaleSwitcher() {
  const t = useTranslations("locale")
  const { locale, setLocale } = useLocale()

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <Button variant="ghost" size="sm" aria-label={t("switcher")}>
            <Globe className="size-4" />
            {LOCALE_LABELS[locale]}
          </Button>
        }
      />
      <DropdownMenuContent align="end">
        {SUPPORTED_LOCALES.map((value) => (
          <DropdownMenuItem
            key={value}
            onClick={() => setLocale(value as Locale)}
          >
            <Check
              className={
                "size-4 " +
                (value === locale ? "opacity-100" : "opacity-0")
              }
              aria-hidden
            />
            {LOCALE_LABELS[value]}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
