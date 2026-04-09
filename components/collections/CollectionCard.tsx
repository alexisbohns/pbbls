import Link from "next/link"
import type { Collection } from "@/lib/types"
import { ModeBadge } from "@/components/collections/ModeBadge"
import { pluralize } from "@/lib/utils/formatters"

type CollectionCardProps = {
  collection: Collection
}

export function CollectionCard({ collection }: CollectionCardProps) {
  const count = collection.pebble_ids.length

  return (
    <article>
      <Link
        href={`/collections/${collection.id}`}
        className="block rounded-lg border border-border px-4 py-3 transition-all duration-100 hover:bg-muted/50 active:scale-[0.98] focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
      >
        <h3 className="text-sm font-medium">{collection.name}</h3>

        <div className="mt-1.5 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
          <ModeBadge mode={collection.mode} />
          <span>{pluralize(count, "pebble")}</span>
        </div>
      </Link>
    </article>
  )
}
