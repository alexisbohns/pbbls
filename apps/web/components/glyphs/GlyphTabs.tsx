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
    <nav className="mb-6 flex border-b border-m3-outline-variant" role="tablist">
      {TABS.map((tab) => (
        <button
          key={tab}
          role="tab"
          aria-selected={active === tab}
          onClick={() => select(tab)}
          // M3 secondary tabs (#1384, two-area spec D6): the label, a 2 px
          // indicator under the active one.
          className={`m3-type-title-small -mb-px min-h-12 flex-1 border-b-2 px-3 outline-none transition-colors focus-visible:bg-m3-surface-container-high motion-reduce:transition-none ${
            active === tab
              ? "border-m3-primary text-m3-on-surface"
              : "border-transparent text-m3-on-surface-variant hover:bg-m3-surface-container-low hover:text-m3-on-surface"
          }`}
        >
          {t(tab)}
        </button>
      ))}
    </nav>
  )
}
