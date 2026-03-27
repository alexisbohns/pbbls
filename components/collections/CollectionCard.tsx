import Link from "next/link"
import type { Collection } from "@/lib/types"
import { ModeBadge } from "@/components/collections/ModeBadge"

type CollectionCardProps = {
  collection: Collection
}

export function CollectionCard({ collection }: CollectionCardProps) {
  const count = collection.pebble_ids.length

  return (
    <article>
      <Link
        href={`/collections/${collection.id}`}
        className="block rounded-lg border border-border px-4 py-3 transition-colors hover:bg-muted/50 focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
      >
        <h3 className="text-sm font-medium">{collection.name}</h3>

        <div className="mt-1.5 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
          <ModeBadge mode={collection.mode} />
          <span>
            {count} {count === 1 ? "pebble" : "pebbles"}
          </span>
        </div>
      </Link>
    </article>
  )
}
