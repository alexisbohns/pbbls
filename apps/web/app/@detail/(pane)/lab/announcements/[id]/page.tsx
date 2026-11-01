"use client"

import { use } from "react"
import Link from "next/link"
import { ChevronLeft } from "lucide-react"
import { useTranslations } from "next-intl"
import { AnnouncementDetail } from "@/components/lab/AnnouncementDetail"
import { PageLayout } from "@/components/layout/PageLayout"

export default function AnnouncementPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = use(params)
  const t = useTranslations("lab")

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
        <AnnouncementDetail id={id} />
      </section>
    </PageLayout>
  )
}
