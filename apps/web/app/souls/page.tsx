"use client"

import { useMemo } from "react"
import { useTranslations } from "next-intl"
import { useSouls } from "@/lib/data/useSouls"
import { usePebbles } from "@/lib/data/usePebbles"
import { useMarks } from "@/lib/data/useMarks"
import { AddSoulForm } from "@/components/souls/AddSoulForm"
import { SoulList } from "@/components/souls/SoulList"
import { SoulsEmptyState } from "@/components/souls/SoulsEmptyState"
import { PageLayout } from "@/components/layout/PageLayout"
import { PageHeader } from "@/components/layout/PageHeader"

export default function SoulsPage() {
  const { souls, loading: soulsLoading, addSoul } = useSouls()
  const { pebbles, loading: pebblesLoading } = usePebbles()
  const { marks } = useMarks()
  const t = useTranslations("souls")

  const loading = soulsLoading || pebblesLoading

  const pebbleCounts = useMemo(() => {
    const counts = new Map<string, number>()
    for (const pebble of pebbles) {
      for (const soulId of pebble.soul_ids) {
        counts.set(soulId, (counts.get(soulId) ?? 0) + 1)
      }
    }
    return counts
  }, [pebbles])

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
