"use client"

import { useCallback } from "react"
import { PlusCircle } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { useTranslations } from "next-intl"
import { useCollections } from "@/lib/data/useCollections"
import { CollectionList } from "@/components/collections/CollectionList"
import { CollectionsEmptyState } from "@/components/collections/CollectionsEmptyState"
import { CollectionFormDialog } from "@/components/collections/CollectionFormDialog"
import { Button } from "@/components/ui/button"
import { PageLayout } from "@/components/layout/PageLayout"
import { PageHeader } from "@/components/layout/PageHeader"
import type { Collection } from "@/lib/types"

export default function CollectionsPage() {
  const { collections, loading, addCollection } = useCollections()
  const t = useTranslations("collections")
  const tForm = useTranslations("collections.form")

  const handleCreate = useCallback(
    async (data: { name: string; mode?: Collection["mode"]; glyph_id: string | null }) => {
      await addCollection({ name: data.name, mode: data.mode, glyph_id: data.glyph_id, pebble_ids: [] })
    },
    [addCollection],
  )

  return (
    <PageLayout>
      <section>
        <PageHeader
          title={t("title")}
          rightSlot={
            <CollectionFormDialog
              trigger={
                <Button variant="outline" size="sm">
                  <PlusCircle data-icon="inline-start" className={PBBLS_ICON_SIZE.inline} />
                  {t("newCta")}
                </Button>
              }
              title={tForm("newTitle")}
              submitLabel={tForm("submitCreate")}
              onSubmit={handleCreate}
            />
          }
        />

        {loading ? (
          <p className="text-sm text-muted-foreground">{t("loading")}</p>
        ) : collections.length === 0 ? (
          <CollectionsEmptyState />
        ) : (
          <CollectionList collections={collections} />
        )}
      </section>
    </PageLayout>
  )
}
