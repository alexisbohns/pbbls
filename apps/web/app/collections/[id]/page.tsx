"use client"

import { use, useMemo, useCallback } from "react"
import Link from "next/link"
import { useTranslations } from "next-intl"
import type { Collection } from "@/lib/types"
import { useCollection } from "@/lib/data/useCollection"
import { useCollections } from "@/lib/data/useCollections"
import { useMoments } from "@/lib/data/useMoments"
import { momentsInCollection } from "@/lib/data/moment-lists"
import { useSouls } from "@/lib/data/useSouls"
import { CollectionDetailHeader } from "@/components/collections/CollectionDetailHeader"
import { CollectionPebbleList } from "@/components/collections/CollectionPebbleList"
import { CollectionNotFound } from "@/components/collections/CollectionNotFound"
import { PageLayout } from "@/components/layout/PageLayout"

export default function CollectionDetailPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = use(params)
  const t = useTranslations("collections")
  const tDetail = useTranslations("collections.detail")
  const { collection, loading: collectionLoading } = useCollection(id)
  const { updateCollection } = useCollections()
  const { moments, loading: momentsLoading } = useMoments()
  const { souls, loading: soulsLoading } = useSouls()

  const loading = collectionLoading || momentsLoading || soulsLoading

  const collectionMoments = useMemo(
    () => (collection ? momentsInCollection(moments, collection) : []),
    [collection, moments],
  )

  const handleEdit = useCallback(
    async (data: { name: string; mode?: Collection["mode"]; glyph_id: string | null }) => {
      await updateCollection(id, data)
    },
    [id, updateCollection],
  )

  const handleRemovePebble = useCallback(
    async (pebbleId: string) => {
      if (!collection) return
      await updateCollection(id, {
        pebble_ids: collection.pebble_ids.filter((pid) => pid !== pebbleId),
      })
    },
    [id, collection, updateCollection],
  )

  return (
    <PageLayout>
      <section>
      <nav className="mb-6">
        <Link
          href="/collections"
          className="text-sm text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        >
          {tDetail("back")}
        </Link>
      </nav>

      {loading ? (
        <p className="text-sm text-muted-foreground">{t("loading")}</p>
      ) : collection ? (
        <>
          <CollectionDetailHeader
            collection={collection}
            pebbleCount={collectionMoments.length}
            onEdit={handleEdit}
          />
          {collectionMoments.length === 0 ? (
            <p className="py-10 text-center text-sm text-muted-foreground">
              {tDetail("noPebbles")}
            </p>
          ) : (
            <CollectionPebbleList
              moments={collectionMoments}
              souls={souls}
              onRemove={handleRemovePebble}
            />
          )}
        </>
      ) : (
        <CollectionNotFound />
      )}
      </section>
    </PageLayout>
  )
}
