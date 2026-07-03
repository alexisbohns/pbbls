"use client"

import Link from "next/link"
import { CirclePlus } from "lucide-react"
import { useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"
import { EmptyState } from "@/components/layout/EmptyState"

export function PathEmptyState() {
  const t = useTranslations("path.empty.currentWeek")
  return (
    <EmptyState
      title={t("title")}
      description={t("description")}
      action={
        <Button render={<Link href="/record" />}>
          <CirclePlus className="size-4" data-icon="inline-start" />
          {t("cta")}
        </Button>
      }
    />
  )
}
