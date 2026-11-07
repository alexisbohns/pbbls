"use client"

import { useSearchParams } from "next/navigation"
import { useTranslations } from "next-intl"
import { DocsHeader } from "@/components/docs/DocsHeader"
import { DocsNav } from "@/components/docs/DocsNav"
import { DocsShell } from "@/components/docs/DocsShell"
import type { DocsPageMeta } from "@/lib/docs/types"

type DocsIndexProps = {
  pages: DocsPageMeta[]
}

export function DocsIndex({ pages }: DocsIndexProps) {
  const searchParams = useSearchParams()
  const isEmbed = searchParams.get("embed") === "true"
  const t = useTranslations("docs")
  const brand = useTranslations("brand")

  const content = (
    <>
      <DocsHeader kicker={brand("name")} title={t("title")} lead={t("indexLead")} />
      <DocsNav pages={pages} />
    </>
  )

  if (isEmbed) {
    return <div className="mx-auto max-w-prose px-4 py-6">{content}</div>
  }

  return (
    <DocsShell pages={pages} title={t("title")}>
      {content}
    </DocsShell>
  )
}
