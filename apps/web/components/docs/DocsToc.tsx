"use client"

import { useTranslations } from "next-intl"
import { scrollToSection } from "@/lib/docs/scroll-to-section"
import { cn } from "@/lib/utils"
import type { DocsTocItem } from "@/lib/docs/types"

type DocsTocProps = {
  items: readonly DocsTocItem[]
  active: string | null
}

/** "01", "02"… */
export function tocNumber(index: number): string {
  return String(index + 1).padStart(2, "0")
}

/**
 * The page's own contents, beside it on wide screens: numbered, ruled rows.
 * It stays quiet (half opacity) until the pointer or focus comes to it.
 */
export function DocsToc({ items, active }: DocsTocProps) {
  const t = useTranslations("docs")
  if (items.length === 0) return null

  return (
    <nav aria-label={t("tocAria")} className="opacity-50 transition-opacity duration-300 hover:opacity-100 focus-within:opacity-100">
      <p className="mb-3 font-spine-sc text-xs font-semibold tracking-[.2em] text-m3-on-surface-variant uppercase">
        {t("onThisPage")}
      </p>
      <ol>
        {items.map((item, i) => {
          const current = item.id === active
          return (
            <li key={item.id}>
              <a
                href={`#${item.id}`}
                onClick={(event) => scrollToSection(event, item.id)}
                aria-current={current ? "location" : undefined}
                className={cn(
                  "flex gap-4 py-2 text-sm leading-snug outline-none focus-visible:ring-2 focus-visible:ring-ring",
                  current ? "text-m3-on-surface" : "text-m3-on-surface-variant hover:text-m3-on-surface",
                )}
              >
                <span className={cn("shrink-0 font-mono tabular-nums", current ? "text-m3-primary" : "text-m3-outline")}>
                  {tocNumber(i)}
                </span>
                <span className={cn(current && "font-medium")}>{item.label}</span>
              </a>
            </li>
          )
        })}
      </ol>
    </nav>
  )
}
