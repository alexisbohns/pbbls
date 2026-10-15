"use client"

import { useMemo } from "react"
import { useTranslations } from "next-intl"
import { useSouls } from "@/lib/data/useSouls"
import { useMoments } from "@/lib/data/useMoments"
import { soulPebbleCounts } from "@/lib/data/moment-lists"
import { useUsableGlyphs } from "@/lib/data/useUsableGlyphs"
import { AddSoulForm } from "@/components/souls/AddSoulForm"
import { SoulList } from "@/components/souls/SoulList"
import { SoulsEmptyState } from "@/components/souls/SoulsEmptyState"
import { PageLayout } from "@/components/layout/PageLayout"
import { PageHeader } from "@/components/layout/PageHeader"

export default function SoulsPage() {
  const { souls, loading: soulsLoading, addSoul } = useSouls()
  const { moments, loading: momentsLoading } = useMoments()
  const { glyphs: marks } = useUsableGlyphs()
  const t = useTranslations("souls")

  const loading = soulsLoading || momentsLoading

  const pebbleCounts = useMemo(() => soulPebbleCounts(moments), [moments])

  const handleAdd = async (input: { name: string; glyph_id: string }) => {
    await addSoul(input)
  }

  return (
    <PageLayout>
      <section>
        <PageHeader title={t("title")} />

        <AddSoulForm marks={marks} onAdd={handleAdd} />

        {loading ? (
          <p className="text-sm text-muted-foreground">{t("loading")}</p>
        ) : souls.length === 0 ? (
          <SoulsEmptyState />
        ) : (
          <SoulList
            souls={souls}
            marks={marks}
            pebbleCounts={pebbleCounts}
          />
        )}
      </section>
    </PageLayout>
  )
}
