"use client"

import Link from "next/link"
import { useTranslations } from "next-intl"
import { ChevronRight } from "lucide-react"
import type { Collection } from "@/lib/types"
import { useCollections } from "@/lib/data/useCollections"
import { SectionCard } from "@/components/profile/SectionCard"
import { SectionLabel } from "@/components/ui/SectionLabel"
import { CollectionTile } from "@/components/profile/CollectionTile"
import { CollectionFormDialog } from "@/components/collections/CollectionFormDialog"

/**
 * The Profile "Collections" card — web port of the iOS `ProfileCollectionsCard`.
 * Header row navigates to the full list; a horizontal scroller shows each
 * collection tile (whole tile → detail), or a dashed create tile when empty.
 */
export function CollectionsCard() {
  const t = useTranslations("profile")
  const tForm = useTranslations("collections.form")
  const { collections, addCollection } = useCollections()

  const handleCreate = async ({
    name,
    mode,
  }: {
    name: string
    mode?: Collection["mode"]
  }) => {
    await addCollection({ name, mode, pebble_ids: [] })
  }

  return (
    <SectionCard>
      <Link
        href="/collections"
        className="flex items-center gap-2 transition-opacity hover:opacity-80"
      >
        <SectionLabel className="flex-1">{t("collectionsTitle")}</SectionLabel>
        <ChevronRight className="size-4 text-muted-foreground" aria-hidden />
      </Link>

      <div className="-mx-4 overflow-x-auto px-4">
        <ul className="flex w-max items-stretch gap-2.5">
          {collections.length === 0 ? (
            <li>
              <CollectionFormDialog
                trigger={
                  <button type="button" className="block h-full text-left">
                    <CollectionTile variant="empty" />
                  </button>
                }
                title={tForm("newTitle")}
                submitLabel={tForm("submitCreate")}
                onSubmit={handleCreate}
              />
            </li>
          ) : (
            collections.map((collection) => (
              <li key={collection.id}>
                <Link href={`/collections/${collection.id}`} className="block h-full">
                  <CollectionTile
                    variant="filled"
                    name={collection.name}
                    count={collection.pebble_ids.length}
                  />
                </Link>
              </li>
            ))
          )}
        </ul>
      </div>
    </SectionCard>
  )
}
