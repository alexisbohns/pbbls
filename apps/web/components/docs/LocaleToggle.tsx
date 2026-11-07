"use client"

import { Check } from "lucide-react"
import { useTranslations } from "next-intl"
import { useDocsLocale } from "@/lib/hooks/useDocsLocale"
import { cn } from "@/lib/utils"
import type { DocsLocale } from "@/lib/docs/types"

const LOCALE_LABELS: Record<DocsLocale, string> = {
  en: "English",
  fr: "Français",
}

/**
 * The language switch as an M3 connected button group: separate buttons 2 px
 * apart, the group's outer corners round and its inner corners tight. The
 * selected one turns into a full pill in primary container, with a check; the others
 * sit on surface container, the soft ground of the navigation's chips.
 */
export function LocaleToggle({ className }: { className?: string }) {
  const { locale, setLocale, locales } = useDocsLocale()
  const t = useTranslations("docs")
  const last = locales.length - 1

  return (
    <div role="group" aria-label={t("languageAria")} className={cn("flex h-10 gap-0.5", className)}>
      {locales.map((l, i) => {
        const selected = l === locale
        return (
          <button
            key={l}
            type="button"
            lang={l}
            aria-pressed={selected}
            onClick={() => setLocale(l)}
            className={cn(
              "m3-type-label-large flex flex-1 cursor-pointer items-center justify-center gap-2 px-4 outline-none",
              "transition-[border-radius,background-color,color] duration-200 ease-out",
              "focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-m3-surface",
              selected
                ? "rounded-[20px] bg-m3-primary-container text-m3-on-primary-container"
                : cn(
                    // Round on the group's outer ends (half the 40 px height), a
                    // softer 14 px where buttons meet. Not `rounded-*-full`: its
                    // infinite radius makes the browser scale every corner of
                    // the box down to nothing, the 14 px ones included.
                    "bg-m3-surface-container text-m3-on-surface hover:bg-m3-surface-container-high",
                    i === 0 ? "rounded-l-[20px]" : "rounded-l-[14px]",
                    i === last ? "rounded-r-[20px]" : "rounded-r-[14px]",
                  ),
            )}
          >
            {selected && <Check aria-hidden className="size-[18px]" />}
            {LOCALE_LABELS[l]}
          </button>
        )
      })}
    </div>
  )
}
