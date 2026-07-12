"use client"

import { useState } from "react"
import { useTranslations } from "next-intl"
import { useDataProvider } from "@/lib/data/provider-context"
import { GlyphPickerGrid } from "@/components/glyphs/GlyphPickerGrid"
import { GlyphMarketPickerList } from "@/components/glyphs/GlyphMarketPickerList"

type GlyphPickerTab = "mine" | "owned" | "community"
const TABS: GlyphPickerTab[] = ["mine", "owned", "community"]

type GlyphPickerTabsProps = {
  selectedMarkId: string | undefined
  /** Select a glyph (Mine/Owned tap, or a just-bought Community glyph). */
  onSelect: (id: string | undefined) => void
}

/**
 * Tabbed body of the glyph picker, harmonized with the /glyphs store:
 * Mine (authored) · Owned (entitled) are directly pickable; Community glyphs
 * are bought inline and auto-selected. Local tab state only — unlike the
 * store's GlyphTabs this is not URL-driven.
 */
export function GlyphPickerTabs({ selectedMarkId, onSelect }: GlyphPickerTabsProps) {
  const t = useTranslations("record.glyph")
  const { store } = useDataProvider()
  const [tab, setTab] = useState<GlyphPickerTab>("mine")

  return (
    <div>
      <nav className="mb-4 flex gap-1 rounded-lg bg-muted p-1" role="tablist" aria-label={t("title")}>
        {TABS.map((tb) => (
          <button
            key={tb}
            type="button"
            role="tab"
            aria-selected={tab === tb}
            onClick={() => setTab(tb)}
            className={`flex-1 rounded-md px-3 py-1.5 text-sm font-medium transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${
              tab === tb
                ? "bg-background text-foreground shadow-sm"
                : "text-muted-foreground hover:text-foreground"
            }`}
          >
            {t(`tabs.${tb}`)}
          </button>
        ))}
      </nav>

      {tab === "mine" && (
        <GlyphPickerGrid
          marks={store.marks}
          selectedMarkId={selectedMarkId}
          onSelect={onSelect}
          emptyMessage={t("emptyMine")}
        />
      )}
      {tab === "owned" && (
        <GlyphPickerGrid
          marks={store.entitledMarks}
          selectedMarkId={selectedMarkId}
          onSelect={onSelect}
          emptyMessage={t("emptyOwned")}
        />
      )}
      {tab === "community" && <GlyphMarketPickerList onBought={onSelect} />}
    </div>
  )
}
