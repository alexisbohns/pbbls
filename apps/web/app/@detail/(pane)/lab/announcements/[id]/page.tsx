"use client"

import { use } from "react"
import { useTranslations } from "next-intl"
import { AnnouncementDetail } from "@/components/lab/AnnouncementDetail"
import { DetailTopBar } from "@/components/layout/DetailTopBar"

/** An announcement, nested under the Lab in the detail area (#1384, two-area spec D2). */
export default function AnnouncementPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = use(params)
  const t = useTranslations("lab")

  return (
    <>
      <DetailTopBar title={t("title")} backHref="/lab" />
      <div className="mx-auto w-full max-w-2xl px-4 pt-2 pb-12">
        <AnnouncementDetail id={id} />
      </div>
    </>
  )
}
