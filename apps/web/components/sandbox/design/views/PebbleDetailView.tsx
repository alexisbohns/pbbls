"use client"

import { PageLayout } from "@/components/layout/PageLayout"
import { PebbleDetail } from "@/components/pebble/PebbleDetail"
import { useCollections } from "@/lib/data/useCollections"
import { usePebbles } from "@/lib/data/usePebbles"
import { useSouls } from "@/lib/data/useSouls"
import { useUsableGlyphs } from "@/lib/data/useUsableGlyphs"

/** app/pebble/[id]/page.tsx for the newest fixture pebble. Every write is a no-op. */
export function PebbleDetailView() {
  const { pebbles } = usePebbles()
  const { souls } = useSouls()
  const { collections } = useCollections()
  const { glyphs: marks } = useUsableGlyphs()
  const pebble = pebbles[0]
  if (!pebble) return null

  return (
    <PageLayout>
      <section>
        <PebbleDetail
          pebble={pebble}
          souls={souls}
          collections={collections}
          marks={marks}
          mark={marks.find((m) => m.id === pebble.mark_id)}
          onUpdatePebble={async () => pebble}
          onUploadSnap={async () => ({ id: "design-snap", storage_path: "", sort_order: 0 })}
          onAddSoul={async () => {}}
        />
      </section>
    </PageLayout>
  )
}
