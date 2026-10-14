"use client"

import { use, useEffect } from "react"
import { useRouter } from "next/navigation"
import { useTranslations } from "next-intl"
import { isCompleted } from "@/lib/data/moment-row"
import { useMomentRoute } from "@/lib/data/useMomentRoute"
import { useUsableGlyphs } from "@/lib/data/useUsableGlyphs"
import { PebbleEnrich } from "@/components/pebble/PebbleEnrich"
import { PebbleNotFound } from "@/components/pebble/PebbleNotFound"
import { PageLayout } from "@/components/layout/PageLayout"

/**
 * Edit pebble (#1050, R5). The id is a moment id; a pebble id redirects. A
 * bare moment has no pebble to edit, so it goes to the Complete flow.
 */
export default function PebbleEnrichPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = use(params)
  const router = useRouter()
  const t = useTranslations("pebble")
  const tErrors = useTranslations("moment.errors")
  const { moment, loading, redirecting } = useMomentRoute(id, "/edit-pebble")
  const { glyphs: marks, loading: marksLoading } = useUsableGlyphs()

  const bareId = moment && !isCompleted(moment) ? moment.id : undefined
  useEffect(() => {
    if (bareId) router.replace(`/record/complete/${bareId}`)
  }, [bareId, router])

  return (
    <PageLayout>
      <section>
        {loading || marksLoading || redirecting || bareId ? (
          <p className="text-sm text-muted-foreground">{t("loading")}</p>
        ) : moment && isCompleted(moment) ? (
          <PebbleEnrich moment={moment} marks={marks} />
        ) : (
          <PebbleNotFound description={tErrors("momentNotFound")} />
        )}
      </section>
    </PageLayout>
  )
}
