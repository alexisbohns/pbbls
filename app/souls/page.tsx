"use client"

import { useMemo } from "react"
import { useSouls } from "@/lib/data/useSouls"
import { usePebbles } from "@/lib/data/usePebbles"
import { AddSoulForm } from "@/components/souls/AddSoulForm"
import { SoulList } from "@/components/souls/SoulList"
import { SoulsEmptyState } from "@/components/souls/SoulsEmptyState"
import { PageLayout } from "@/components/layout/PageLayout"
import { PathProfileCard } from "@/components/path/PathProfileCard"

export default function SoulsPage() {
  const { souls, loading: soulsLoading, addSoul, removeSoul } = useSouls()
  const { pebbles, loading: pebblesLoading } = usePebbles()

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

  const handleAdd = async (name: string) => {
    await addSoul({ name })
  }

  return (
    <PageLayout sidebar={<PathProfileCard />}>
      <section>
      <h1 className="mb-6 text-2xl font-semibold">Souls</h1>

      <AddSoulForm onAdd={handleAdd} />

      {loading ? (
        <p className="text-sm text-muted-foreground">Loading\u2026</p>
      ) : souls.length === 0 ? (
        <SoulsEmptyState />
      ) : (
        <SoulList
          souls={souls}
          pebbleCounts={pebbleCounts}
          onDelete={removeSoul}
        />
      )}
      </section>
    </PageLayout>
  )
}
