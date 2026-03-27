"use client"

import { useCollections } from "@/lib/data/useCollections"
import { CollectionList } from "@/components/collections/CollectionList"
import { CollectionsEmptyState } from "@/components/collections/CollectionsEmptyState"

export default function CollectionsPage() {
  const { collections, loading } = useCollections()

  return (
    <section>
      <h1 className="mb-6 text-2xl font-semibold">Collections</h1>

      {loading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : collections.length === 0 ? (
        <CollectionsEmptyState />
      ) : (
        <CollectionList collections={collections} />
      )}
    </section>
  )
}
