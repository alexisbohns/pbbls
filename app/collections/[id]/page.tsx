"use client"

import { use, useMemo, useCallback } from "react"
import Link from "next/link"
import type { Pebble, Collection } from "@/lib/types"
import { useCollection } from "@/lib/data/useCollection"
import { useCollections } from "@/lib/data/useCollections"
import { usePebbles } from "@/lib/data/usePebbles"
import { useSouls } from "@/lib/data/useSouls"
import { CollectionDetailHeader } from "@/components/collections/CollectionDetailHeader"
import { CollectionPebbleList } from "@/components/collections/CollectionPebbleList"
import { CollectionNotFound } from "@/components/collections/CollectionNotFound"

export default function CollectionDetailPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = use(params)
  const { collection, loading: collectionLoading } = useCollection(id)
  const { updateCollection } = useCollections()
  const { pebbles, loading: pebblesLoading } = usePebbles()
  const { souls, loading: soulsLoading } = useSouls()

  const loading = collectionLoading || pebblesLoading || soulsLoading

  const resolvedPebbles = useMemo(() => {
    if (!collection) return []
    const pebbleMap = new Map(pebbles.map((p) => [p.id, p]))
    return collection.pebble_ids
      .map((pid) => pebbleMap.get(pid))
      .filter((p): p is Pebble => p != null)
  }, [collection, pebbles])

  const handleEdit = useCallback(
    async (data: { name: string; mode?: Collection["mode"] }) => {
      await updateCollection(id, data)
    },
    [id, updateCollection],
  )

  const handleRemovePebble = useCallback(
    async (pebbleId: string) => {
      if (!collection) return
      await updateCollection(id, {
        pebble_ids: collection.pebble_ids.filter((pid) => pid !== pebbleId),
      })
    },
    [id, collection, updateCollection],
  )

  return (
    <section>
      <nav className="mb-6">
        <Link
          href="/collections"
          className="text-sm text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        >
          &larr; Back to Collections
        </Link>
      </nav>

      {loading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : collection ? (
        <>
          <CollectionDetailHeader
            collection={collection}
            pebbleCount={resolvedPebbles.length}
            onEdit={handleEdit}
          />
          {resolvedPebbles.length === 0 ? (
            <p className="py-10 text-center text-sm text-muted-foreground">
              No pebbles in this collection yet.
            </p>
          ) : (
            <CollectionPebbleList
              pebbles={resolvedPebbles}
              souls={souls}
              onRemove={handleRemovePebble}
            />
          )}
        </>
      ) : (
        <CollectionNotFound />
      )}
    </section>
  )
}
