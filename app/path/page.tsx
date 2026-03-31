"use client"

import { usePebbles } from "@/lib/data/usePebbles"
import { useSouls } from "@/lib/data/useSouls"
import { PebbleTimeline } from "@/components/path/PebbleTimeline"
import { PathEmptyState } from "@/components/path/PathEmptyState"
import { PebblesCounter } from "@/components/path/PebblesCounter"

export default function PathPage() {
  const { pebbles, loading: pebblesLoading } = usePebbles()
  const { souls, loading: soulsLoading } = useSouls()

  const loading = pebblesLoading || soulsLoading

  return (
    <section>
      <h1 className="mb-6 text-2xl font-semibold">Path</h1>

      <PebblesCounter />

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
