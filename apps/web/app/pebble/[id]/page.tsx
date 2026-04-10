"use client"

import { use, useCallback } from "react"
import Link from "next/link"
import { usePebble } from "@/lib/data/usePebble"
import { useSouls } from "@/lib/data/useSouls"
import { useCollections } from "@/lib/data/useCollections"
import { useMarks } from "@/lib/data/useMarks"
import { PebbleDetail } from "@/components/pebble/PebbleDetail"
import { PebbleNotFound } from "@/components/pebble/PebbleNotFound"
import { PageLayout } from "@/components/layout/PageLayout"

export default function PebbleDetailPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = use(params)
  const { pebble, loading: pebbleLoading, updatePebble } = usePebble(id)
  const { souls, loading: soulsLoading, addSoul } = useSouls()
  const { collections, loading: collectionsLoading, updateCollection } = useCollections()
  const { marks, loading: marksLoading } = useMarks()

  const loading = pebbleLoading || soulsLoading || collectionsLoading || marksLoading

  const matchedCollections = collections.filter((c) =>
    c.pebble_ids.includes(id),
  )
  const mark = pebble ? marks.find((m) => m.id === pebble.mark_id) : undefined

  const handleAddSoul = useCallback(
    async (name: string) => {
      const soul = await addSoul({ name })
      if (pebble) {
        await updatePebble({ soul_ids: [...pebble.soul_ids, soul.id] })
      }
    },
    [addSoul, updatePebble, pebble],
  )

  return (
    <PageLayout>
      <section>
      <nav className="mb-6">
        <Link
          href="/path"
          className="text-sm text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        >
          &larr; Back to Path
        </Link>
      </nav>

      {loading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : pebble ? (
        <PebbleDetail
          pebble={pebble}
          souls={souls}
          collections={matchedCollections}
          allCollections={collections}
          marks={marks}
          mark={mark}
          onUpdatePebble={updatePebble}
          onUpdateCollection={updateCollection}
          onAddSoul={handleAddSoul}
        />
      ) : (
        <PebbleNotFound />
      )}
      </section>
    </PageLayout>
  )
}
