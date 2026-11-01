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
import { PageLayout } from "@/components/layout/PageLayout"

/**
 * A moment's read page (#1050). The id is a MOMENT id; a link minted with a
 * pebble id redirects to its moment's page.
 */
export default function MomentReadPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = use(params)
  const router = useRouter()
  const t = useTranslations("pebble")
  const { moment, loading: momentLoading, redirecting } = useMomentRoute(id)
  const { souls, loading: soulsLoading } = useSouls()
  const { collections, loading: collectionsLoading } = useCollections()
  const { glyphs: marks, loading: marksLoading } = useUsableGlyphs()
  // Set once the moment is deleted, so the page leaves without flashing "not found".
  const [leaving, setLeaving] = useState(false)

  const loading = momentLoading || soulsLoading || collectionsLoading || marksLoading

  const handleDeleted = useCallback(() => {
    setLeaving(true)
    router.replace("/path")
  }, [router])

  return (
    <PageLayout>
      <section>
        {loading || redirecting || leaving ? (
          <p className="text-sm text-muted-foreground">{t("loading")}</p>
        ) : moment ? (
          <MomentRead
            moment={moment}
            souls={souls}
            collections={collections}
            marks={marks}
            onNavigate={(href) => router.push(href)}
            onClose={() => router.back()}
            onDeleted={handleDeleted}
          />
        ) : (
          <PebbleNotFound />
        )}
      </section>
    </PageLayout>
  )
}
