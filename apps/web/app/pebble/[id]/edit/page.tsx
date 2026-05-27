"use client"

import { use } from "react"
import { useTranslations } from "next-intl"
import { usePebble } from "@/lib/data/usePebble"
import { useSouls } from "@/lib/data/useSouls"
import { useCollections } from "@/lib/data/useCollections"
import { useMarks } from "@/lib/data/useMarks"
import { PebbleEdit } from "@/components/pebble/PebbleEdit"
import { PebbleNotFound } from "@/components/pebble/PebbleNotFound"
import { PageLayout } from "@/components/layout/PageLayout"

export default function PebbleEditPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = use(params)
  const t = useTranslations("pebble")
  const {
    pebble,
    loading: pebbleLoading,
    updatePebble,
    uploadSnap,
    deletePebbleMedia,
  } = usePebble(id)
  const { souls, loading: soulsLoading, addSoul } = useSouls()
  const { collections, loading: collectionsLoading } = useCollections()
  const { marks, loading: marksLoading } = useMarks()

  const loading = pebbleLoading || soulsLoading || collectionsLoading || marksLoading
  const mark = pebble ? marks.find((m) => m.id === pebble.mark_id) : undefined

  return (
    <PageLayout>
      <section>
        {loading ? (
          <p className="text-sm text-muted-foreground">{t("loading")}</p>
        ) : pebble ? (
          <PebbleEdit
            pebble={pebble}
            souls={souls}
            collections={collections}
            marks={marks}
            mark={mark}
            onUpdatePebble={(input) => updatePebble(input)}
            onUploadSnap={uploadSnap}
            onDeletePebbleMedia={deletePebbleMedia}
            onAddSoul={(name) => addSoul({ name })}
          />
        ) : (
          <PebbleNotFound />
        )}
      </section>
    </PageLayout>
  )
}
