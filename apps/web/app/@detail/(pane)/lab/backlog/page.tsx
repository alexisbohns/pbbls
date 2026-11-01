"use client"

import { useTranslations } from "next-intl"
import { LabLogList } from "@/components/lab/LabLogList"
import { DetailTopBar } from "@/components/layout/DetailTopBar"

export default function LabBacklogPage() {
  const tSections = useTranslations("lab.sections")
  return (
    <>
      <DetailTopBar title={tSections("backlog")} backHref="/lab" />
      <div className="mx-auto w-full max-w-2xl px-4 pt-2 pb-12">
        <LabLogList mode="backlog" />
      </div>
    </>
  )
}
