"use client"

import { usePebbles } from "@/lib/data/usePebbles"
import { useSouls } from "@/lib/data/useSouls"
import { PebbleTimeline } from "@/components/path/PebbleTimeline"
import { PathEmptyState } from "@/components/path/PathEmptyState"
import { GamificationBlocks } from "@/components/path/GamificationBlocks"

export default function PathPage() {
  const { pebbles, loading: pebblesLoading } = usePebbles()
  const { souls, loading: soulsLoading } = useSouls()

  const loading = pebblesLoading || soulsLoading

  return (
    <section>
      <h1 className="mb-6 font-heading text-2xl font-semibold">Path</h1>

      <GamificationBlocks />

      {loading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : pebbles.length === 0 ? (
        <PathEmptyState />
      ) : (
        <PebbleTimeline pebbles={pebbles} souls={souls} />
      )}
    </section>
  )
}
