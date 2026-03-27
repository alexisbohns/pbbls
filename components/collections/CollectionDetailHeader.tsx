import type { Collection } from "@/lib/types"
import { ModeBadge } from "@/components/collections/ModeBadge"

type CollectionDetailHeaderProps = {
  collection: Collection
  pebbleCount: number
}

export function CollectionDetailHeader({
  collection,
  pebbleCount,
}: CollectionDetailHeaderProps) {
  return (
    <header className="mb-6">
      <h1 className="text-2xl font-semibold">{collection.name}</h1>

      <div className="mt-2 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
        <ModeBadge mode={collection.mode} />
        <span>
          {pebbleCount} {pebbleCount === 1 ? "pebble" : "pebbles"}
        </span>
      </div>
    </header>
  )
}
