"use client"

import { use, useCallback, useState } from "react"
import { useRouter } from "next/navigation"
import { useTranslations } from "next-intl"
import { useMomentRoute } from "@/lib/data/useMomentRoute"
import { useSouls } from "@/lib/data/useSouls"
import { useCollections } from "@/lib/data/useCollections"
import { useUsableGlyphs } from "@/lib/data/useUsableGlyphs"
import { MomentRead } from "@/components/pebble/MomentRead"
import { PebbleNotFound } from "@/components/pebble/PebbleNotFound"
import { DetailTopBar } from "@/components/layout/DetailTopBar"
import { useDetailPane } from "@/lib/hooks/useDetailPane"

/**
 * A moment's read page (#1050), a detail over the whole right area from
 * 1024 px and the overlay below (#1384, two-area spec D5). The id is a MOMENT
 * id; a link minted with a pebble id redirects to its moment's page. Deleting
 * it closes the detail.
 */
export default function MomentReadPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = use(params)
  const router = useRouter()
  const t = useTranslations("pebble")
  const tMoment = useTranslations("moment")
  const { moment, loading: momentLoading, redirecting } = useMomentRoute(id)
  const { souls, loading: soulsLoading } = useSouls()
  const { collections, loading: collectionsLoading } = useCollections()
  const { glyphs: marks, loading: marksLoading } = useUsableGlyphs()
  // Set once the moment is deleted, so the pane leaves without flashing "not found".
  const [leaving, setLeaving] = useState(false)
  const { close } = useDetailPane()

  const loading = momentLoading || soulsLoading || collectionsLoading || marksLoading

  const handleDeleted = useCallback(() => {
    setLeaving(true)
    close()
  }, [close])

  if (moment && !loading && !redirecting && !leaving) {
    return (
      <MomentRead
        moment={moment}
        souls={souls}
        collections={collections}
        marks={marks}
        onNavigate={(href) => router.push(href)}
        onDeleted={handleDeleted}
      />
    )
  }

  return (
    <>
      <DetailTopBar title={loading || redirecting || leaving ? t("loading") : tMoment("notFoundTitle")} />
      <div className="mx-auto w-full max-w-2xl px-4 pt-2 pb-12">
        {loading || redirecting || leaving ? (
          <p className="m3-type-body-medium text-m3-on-surface-variant">{t("loading")}</p>
        ) : (
          <PebbleNotFound />
        )}
      </div>
    </>
  )
}
