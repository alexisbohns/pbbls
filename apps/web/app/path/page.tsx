"use client"

import { useState, useRef, useEffect, useCallback } from "react"
import { usePebbles } from "@/lib/data/usePebbles"
import { useSouls } from "@/lib/data/useSouls"
import { PebbleTimeline } from "@/components/path/PebbleTimeline"
import { PathEmptyState } from "@/components/path/PathEmptyState"
import { PathProfileCard } from "@/components/path/PathProfileCard"
import { PageLayout } from "@/components/layout/PageLayout"
import { QuickPebbleEditor } from "@/components/path/QuickPebbleEditor"
import { PebblePeek } from "@/components/path/PebblePeek"

export default function PathPage() {
  const { pebbles, loading: pebblesLoading } = usePebbles()
  const { souls, loading: soulsLoading } = useSouls()
  const [selectedPebbleId, setSelectedPebbleId] = useState<string | null>(null)
  const scrollTargetRef = useRef<string | null>(null)

  const loading = pebblesLoading || soulsLoading

  const handlePebbleCreated = useCallback((id: string) => {
    scrollTargetRef.current = id
    setSelectedPebbleId(id)
  }, [])

  // Scroll to the newly created pebble once it appears in the DOM
  useEffect(() => {
    const targetId = scrollTargetRef.current
    if (!targetId) return

    const el = document.getElementById(`pebble-${targetId}`)
    if (el) {
      el.scrollIntoView({ behavior: "smooth", block: "center" })
      scrollTargetRef.current = null
    }
  }, [pebbles])

  return (
    <PageLayout sidebar={<PathProfileCard />}>
      <QuickPebbleEditor onPebbleCreated={handlePebbleCreated} />

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

      <PebblePeek
        pebbleId={selectedPebbleId}
        onClose={() => setSelectedPebbleId(null)}
      />
    </PageLayout>
  )
}
