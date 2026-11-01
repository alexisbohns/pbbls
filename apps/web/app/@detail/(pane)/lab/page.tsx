"use client"

import { useTranslations } from "next-intl"
import { LabFeed } from "@/components/lab/LabFeed"
import { DetailTopBar } from "@/components/layout/DetailTopBar"

/** The Lab, in the detail area beside You (#1384, two-area spec D2, D6). */
export default function LabPage() {
  const t = useTranslations("lab")
  return (
    <>
      <DetailTopBar title={t("title")} />
      <div className="mx-auto w-full max-w-2xl px-4 pt-2 pb-12">
        <LabFeed />
      </div>
    </>
  )
}
