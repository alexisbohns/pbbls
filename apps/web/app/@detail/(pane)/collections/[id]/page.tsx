"use client"

import { use, useMemo, useCallback } from "react"
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
import { DetailTopBar } from "@/components/layout/DetailTopBar"

/** A collection, in the detail area beside Collections (#1384, two-area spec D2, D6). */
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
  const { loading: soulsLoading } = useSouls()

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
    <>
      <DetailTopBar title={collection?.name ?? ""} />
      <div className="mx-auto w-full max-w-2xl px-4 pt-2 pb-12">
        {loading ? (
          <p className="m3-type-body-medium text-m3-on-surface-variant">{t("loading")}</p>
        ) : collection ? (
          <>
            <CollectionDetailHeader collection={collection} pebbleCount={collectionMoments.length} onEdit={handleEdit} />
            {collectionMoments.length === 0 ? (
              <p className="m3-type-body-medium py-10 text-center text-m3-on-surface-variant">{tDetail("noPebbles")}</p>
            ) : (
              <CollectionPebbleList moments={collectionMoments} onRemove={handleRemovePebble} />
            )}
          </>
        ) : (
          <CollectionNotFound />
        )}
      </div>
    </>
  )
}
