"use client"

import Link from "next/link"
import { ChevronLeft } from "lucide-react"
import { useTranslations } from "next-intl"
import { LabLogList } from "@/components/lab/LabLogList"
import { PageLayout } from "@/components/layout/PageLayout"

export default function LabBacklogPage() {
  const t = useTranslations("lab")
  const tSections = useTranslations("lab.sections")
  return (
    <PageLayout>
      <section>
        <nav className="mb-6">
          <Link
            href="/lab"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ChevronLeft className="size-4" aria-hidden />
            {t("back")}
          </Link>
        </nav>
        <h1 className="mb-6 text-2xl font-semibold">{tSections("backlog")}</h1>
        <LabLogList mode="backlog" />
      </section>
    </PageLayout>
  )
}
