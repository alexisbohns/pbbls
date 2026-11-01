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
import { PageLayout } from "@/components/layout/PageLayout"
import { PageHeader } from "@/components/layout/PageHeader"

function GlyphsView() {
  const params = useSearchParams()
  const tab = (params.get("tab") as GlyphTab) ?? "mine"
  const t = useTranslations("glyphs")
  const { marks, loading } = useMarks()

  return (
    <section>
      <PageHeader
        title={t("title")}
        rightSlot={
          <Link
            href="/carve"
            className="text-sm font-medium text-primary underline underline-offset-4 hover:text-primary/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          >
            {t("carveNew")}
          </Link>
        }
      />
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
  )
}

export default function GlyphsPage() {
  return (
    <PageLayout>
      <Suspense>
        <GlyphsView />
      </Suspense>
    </PageLayout>
  )
}
