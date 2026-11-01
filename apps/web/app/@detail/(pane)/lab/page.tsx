"use client"

import { useTranslations } from "next-intl"
import { LabFeed } from "@/components/lab/LabFeed"
import { PageLayout } from "@/components/layout/PageLayout"
import { PageHeader } from "@/components/layout/PageHeader"

export default function LabPage() {
  const t = useTranslations("lab")
  return (
    <PageLayout>
      <section>
        <PageHeader title={t("title")} />
        <LabFeed />
      </section>
    </PageLayout>
  )
}
