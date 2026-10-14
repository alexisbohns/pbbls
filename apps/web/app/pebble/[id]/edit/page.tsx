"use client"

import { use } from "react"
import { useTranslations } from "next-intl"
import { useMomentRoute } from "@/lib/data/useMomentRoute"
import { MomentEdit } from "@/components/pebble/MomentEdit"
import { PebbleNotFound } from "@/components/pebble/PebbleNotFound"
import { PageLayout } from "@/components/layout/PageLayout"

/** Edit moment (#1050, R5). The id is a moment id; a pebble id redirects. */
export default function MomentEditPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = use(params)
  const t = useTranslations("pebble")
  const tErrors = useTranslations("moment.errors")
  const { moment, loading, redirecting } = useMomentRoute(id, "/edit")

  return (
    <PageLayout>
      <section>
        {loading || redirecting ? (
          <p className="text-sm text-muted-foreground">{t("loading")}</p>
        ) : moment ? (
          <MomentEdit moment={moment} />
        ) : (
          <PebbleNotFound description={tErrors("momentNotFound")} />
        )}
      </section>
    </PageLayout>
  )
}
