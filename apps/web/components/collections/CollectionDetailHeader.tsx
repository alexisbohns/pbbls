"use client"

import { DrawDotted } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { useTranslations } from "next-intl"
import type { Collection } from "@/lib/types"
import { ModeBadge } from "@/components/collections/ModeBadge"
import { CollectionFormDialog } from "@/components/collections/CollectionFormDialog"
import { Button } from "@/components/ui/button"

type CollectionDetailHeaderProps = {
  collection: Collection
  pebbleCount: number
  onEdit: (data: { name: string; mode?: Collection["mode"]; glyph_id: string | null }) => Promise<void>
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
    <div className="mb-6 flex flex-wrap items-center gap-2">
      {/* The name is the detail's title and the edit is its top bar action (#1384, two-area spec D6). */}
      <ModeBadge mode={collection.mode} />
      <span className="m3-type-body-large text-m3-on-surface-variant">{t("pebbleCount", { count: pebbleCount })}</span>
      <CollectionFormDialog
        trigger={
          <Button variant="ghost" size="icon" className="ms-auto size-11 rounded-full text-m3-on-surface-variant" aria-label={tDetail("editAria")}>
            <DrawDotted className={PBBLS_ICON_SIZE.toolbar} />
          </Button>
        }
        title={tForm("editTitle")}
        submitLabel={tForm("submitSave")}
        initialName={collection.name}
        initialMode={collection.mode}
        initialGlyphId={collection.glyph_id}
        onSubmit={onEdit}
      />
    </div>
  )
}
