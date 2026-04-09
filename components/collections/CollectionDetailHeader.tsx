import { Pencil } from "lucide-react"
import type { Collection } from "@/lib/types"
import { pluralize } from "@/lib/utils/formatters"
import { ModeBadge } from "@/components/collections/ModeBadge"
import { CollectionFormDialog } from "@/components/collections/CollectionFormDialog"
import { Button } from "@/components/ui/button"

type CollectionDetailHeaderProps = {
  collection: Collection
  pebbleCount: number
  onEdit: (data: { name: string; mode?: Collection["mode"] }) => void
}

export function CollectionDetailHeader({
  collection,
  pebbleCount,
  onEdit,
}: CollectionDetailHeaderProps) {
  return (
    <header className="mb-6">
      <div className="flex items-center gap-2">
        <h1 className="text-2xl font-semibold">{collection.name}</h1>
        <CollectionFormDialog
          trigger={
            <Button
              variant="ghost"
              size="icon-xs"
              aria-label="Edit collection"
            >
              <Pencil />
            </Button>
          }
          title="Edit collection"
          submitLabel="Save"
          initialName={collection.name}
          initialMode={collection.mode}
          onSubmit={onEdit}
        />
      </div>

      <div className="mt-2 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
        <ModeBadge mode={collection.mode} />
        <span>{pluralize(pebbleCount, "pebble")}</span>
      </div>
    </header>
  )
}
