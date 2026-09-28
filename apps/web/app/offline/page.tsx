"use client"

import { useTranslations } from "next-intl"
import { buttonVariants } from "@/components/ui/button"
import { PageLayout } from "@/components/layout/PageLayout"

export default function OfflinePage() {
  const t = useTranslations("offline")
  return (
    <PageLayout>
      <section className="flex flex-col items-center justify-center gap-4 py-20 text-center">
        <h1 className="text-2xl font-semibold">{t("title")}</h1>
        <p className="text-sm text-muted-foreground">{t("description")}</p>
        {/* A plain <a>, not next/link: offline, the page must reload from the network. */}
        <a href="/path" className={buttonVariants({ variant: "outline" })}>
          {t("cta")}
        </a>
      </section>
    </PageLayout>
  )
}
