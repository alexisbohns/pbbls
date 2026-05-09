"use client"

import { useTranslations } from "next-intl"
import { LabFeed } from "@/components/lab/LabFeed"
import { PageLayout } from "@/components/layout/PageLayout"

export default function LabPage() {
  const t = useTranslations("lab")
  return (
    <PageLayout>
      <section>
        <div className="mb-6 flex items-center justify-between">
          <h1 className="text-2xl font-semibold">{t("title")}</h1>
        </div>
        <LabFeed />
      </section>
    </PageLayout>
  )
}
