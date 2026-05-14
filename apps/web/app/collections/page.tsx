"use client"

import { useCallback } from "react"
import { Plus } from "lucide-react"
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
    async (data: { name: string; mode?: Collection["mode"] }) => {
      await addCollection({ name: data.name, mode: data.mode, pebble_ids: [] })
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
                  <Plus data-icon="inline-start" />
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
