"use client"

import { use, useCallback } from "react"
import { useRouter } from "next/navigation"
import { useTranslations } from "next-intl"
import { usePebble } from "@/lib/data/usePebble"
import { useSouls } from "@/lib/data/useSouls"
import { useCollections } from "@/lib/data/useCollections"
import { useMarks } from "@/lib/data/useMarks"
import { PebbleDetail } from "@/components/pebble/PebbleDetail"
import { PebbleNotFound } from "@/components/pebble/PebbleNotFound"
import { PageLayout } from "@/components/layout/PageLayout"

export default function PebbleDetailPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = use(params)
  const router = useRouter()
  const t = useTranslations("pebble")
  const { pebble, loading: pebbleLoading, updatePebble, uploadSnap } = usePebble(id)
  const { souls, loading: soulsLoading, addSoul } = useSouls()
  const { collections, loading: collectionsLoading } = useCollections()
  const { marks, loading: marksLoading } = useMarks()

  const loading = pebbleLoading || soulsLoading || collectionsLoading || marksLoading

  const mark = pebble ? marks.find((m) => m.id === pebble.mark_id) : undefined

  const handleAddSoul = useCallback(
    async (name: string) => {
      const soul = await addSoul({ name })
      if (pebble) {
        await updatePebble({ soul_ids: [...pebble.soul_ids, soul.id] })
      }
    },
    [addSoul, updatePebble, pebble],
  )

  return (
    <PageLayout>
      <section>
        {loading ? (
          <p className="text-sm text-muted-foreground">{t("loading")}</p>
        ) : pebble ? (
          <PebbleDetail
            pebble={pebble}
            souls={souls}
            collections={collections}
            marks={marks}
            mark={mark}
            onUpdatePebble={updatePebble}
            onUploadSnap={uploadSnap}
            onAddSoul={handleAddSoul}
            onClose={() => router.back()}
          />
        ) : (
          <PebbleNotFound />
        )}
      </section>
    </PageLayout>
  )
}
