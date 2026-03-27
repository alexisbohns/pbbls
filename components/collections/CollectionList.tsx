import type { Collection } from "@/lib/types"
import { CollectionCard } from "@/components/collections/CollectionCard"

type CollectionListProps = {
  collections: Collection[]
}

export function CollectionList({ collections }: CollectionListProps) {
  return (
    <ul className="flex flex-col gap-2">
      {collections.map((collection) => (
        <li key={collection.id}>
          <CollectionCard collection={collection} />
        </li>
      ))}
    </ul>
  )
}
