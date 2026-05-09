"use client"

import { Pencil } from "lucide-react"
import { useTranslations } from "next-intl"
import type { Collection } from "@/lib/types"
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
  const t = useTranslations("collections")
  const tForm = useTranslations("collections.form")
  const tDetail = useTranslations("collections.detail")

  return (
    <header className="mb-6">
      <div className="flex items-center gap-2">
        <h1 className="text-2xl font-semibold">{collection.name}</h1>
        <CollectionFormDialog
          trigger={
            <Button
              variant="ghost"
              size="icon-xs"
              aria-label={tDetail("editAria")}
            >
              <Pencil />
            </Button>
          }
          title={tForm("editTitle")}
          submitLabel={tForm("submitSave")}
          initialName={collection.name}
          initialMode={collection.mode}
          onSubmit={onEdit}
        />
      </div>

      <div className="mt-2 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
        <ModeBadge mode={collection.mode} />
        <span>{t("pebbleCount", { count: pebbleCount })}</span>
      </div>
    </header>
  )
}
