"use client"

import { useCallback } from "react"
import { Plus } from "lucide-react"
import { useCollections } from "@/lib/data/useCollections"
import { CollectionList } from "@/components/collections/CollectionList"
import { CollectionsEmptyState } from "@/components/collections/CollectionsEmptyState"
import { CollectionFormDialog } from "@/components/collections/CollectionFormDialog"
import { Button } from "@/components/ui/button"
import type { Collection } from "@/lib/types"

export default function CollectionsPage() {
  const { collections, loading, addCollection } = useCollections()

  const handleCreate = useCallback(
    async (data: { name: string; mode?: Collection["mode"] }) => {
      await addCollection({ name: data.name, mode: data.mode, pebble_ids: [] })
    },
    [addCollection],
  )

  return (
    <section>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Collections</h1>
        <CollectionFormDialog
          trigger={
            <Button variant="outline" size="sm">
              <Plus data-icon="inline-start" />
              New
            </Button>
          }
          title="New collection"
          submitLabel="Create"
          onSubmit={handleCreate}
        />
      </div>

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
