"use client"

import { useState } from "react"
import { usePebbles } from "@/lib/data/usePebbles"
import { useSouls } from "@/lib/data/useSouls"
import { PebbleTimeline } from "@/components/path/PebbleTimeline"
import { PathEmptyState } from "@/components/path/PathEmptyState"
import { PathLayout } from "@/components/path/PathLayout"
import { QuickPebbleEditor } from "@/components/path/QuickPebbleEditor"
import { PebbleSheet } from "@/components/path/PebbleSheet"

export default function PathPage() {
  const { pebbles, loading: pebblesLoading } = usePebbles()
  const { souls, loading: soulsLoading } = useSouls()
  const [selectedPebbleId, setSelectedPebbleId] = useState<string | null>(null)

  const loading = pebblesLoading || soulsLoading

  return (
    <PathLayout>
      <QuickPebbleEditor />

      {loading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : pebbles.length === 0 ? (
        <PathEmptyState />
      ) : (
        <PebbleTimeline
          pebbles={pebbles}
          souls={souls}
          onSelectPebble={setSelectedPebbleId}
        />
      )}

      <PebbleSheet
        pebbleId={selectedPebbleId}
        onClose={() => setSelectedPebbleId(null)}
      />
    </PathLayout>
  )
}
