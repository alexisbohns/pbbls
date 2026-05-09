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
import { PathProfileCard } from "@/components/path/PathProfileCard"
import type { Collection } from "@/lib/types"
import { BackPath } from "@/components/ui/BackPath"

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
    <PageLayout sidebar={<><BackPath /><PathProfileCard /></>}>
      <section>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-semibold">{t("title")}</h1>
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
      </div>

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
