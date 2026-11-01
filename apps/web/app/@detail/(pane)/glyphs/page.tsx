"use client"

import { Suspense } from "react"
import Link from "next/link"
import { useSearchParams } from "next/navigation"
import { useTranslations } from "next-intl"
import { useMarks } from "@/lib/data/useMarks"
import { GlyphList } from "@/components/glyphs/GlyphList"
import { GlyphsEmptyState } from "@/components/glyphs/GlyphsEmptyState"
import { GlyphTabs, type GlyphTab } from "@/components/glyphs/GlyphTabs"
import { MarketGlyphs } from "@/components/glyphs/MarketGlyphs"
import { FavouriteGlyphs } from "@/components/glyphs/FavouriteGlyphs"
import { DetailTopBar } from "@/components/layout/DetailTopBar"
import { Button } from "@/components/ui/button"

function GlyphsView() {
  const params = useSearchParams()
  const tab = (params.get("tab") as GlyphTab) ?? "mine"
  const t = useTranslations("glyphs")
  const { marks, loading } = useMarks()

  return (
    <>
      <DetailTopBar
        title={t("title")}
        actions={
          <Button variant="ghost" nativeButton={false} render={<Link href="/carve" />} className="m3-type-label-large h-10 rounded-full px-3 text-m3-primary">
            {t("carveNew")}
          </Button>
        }
      />
      <section className="mx-auto w-full max-w-2xl px-4 pt-2 pb-12">
      <GlyphTabs active={tab} />

      {tab === "market" ? (
        <MarketGlyphs />
      ) : tab === "favourites" ? (
        <FavouriteGlyphs />
      ) : loading ? (
        <p className="text-sm text-muted-foreground">{t("loading")}</p>
      ) : marks.length === 0 ? (
        <GlyphsEmptyState />
      ) : (
        <GlyphList marks={marks} />
      )}
      </section>
    </>
  )
}

/** Glyphs, in the detail area beside You (#1384, two-area spec D2, D6). */
export default function GlyphsPage() {
  return (
    <Suspense>
      <GlyphsView />
    </Suspense>
  )
}
