"use client"

import { useRouter, useSearchParams } from "next/navigation"
import { useTranslations } from "next-intl"

export type GlyphTab = "mine" | "favourites" | "market"
const TABS: GlyphTab[] = ["mine", "favourites", "market"]

export function GlyphTabs({ active }: { active: GlyphTab }) {
  const router = useRouter()
  const params = useSearchParams()
  const t = useTranslations("glyphs.tabs")

  const select = (tab: GlyphTab) => {
    const next = new URLSearchParams(params)
    next.set("tab", tab)
    router.replace(`/glyphs?${next.toString()}`)
  }

  return (
    <nav className="mb-6 flex gap-1 rounded-lg bg-muted p-1" role="tablist">
      {TABS.map((tab) => (
        <button
          key={tab}
          role="tab"
          aria-selected={active === tab}
          onClick={() => select(tab)}
          className={`flex-1 rounded-md px-3 py-1.5 text-sm font-medium transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${
            active === tab
              ? "bg-background text-foreground shadow-sm"
              : "text-muted-foreground hover:text-foreground"
          }`}
        >
          {t(tab)}
        </button>
      ))}
    </nav>
  )
}
