"use client"

import { CirclePlus } from "lucide-react"
import { useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"
import { EmptyState } from "@/components/layout/EmptyState"

type PathEmptyStateProps = {
  onCarve?: () => void
}

export function PathEmptyState({ onCarve }: PathEmptyStateProps) {
  const t = useTranslations("path.empty.currentWeek")
  return (
    <EmptyState
      title={t("title")}
      description={t("description")}
      action={
        <Button onClick={onCarve}>
          <CirclePlus className="size-4" data-icon="inline-start" />
          {t("cta")}
        </Button>
      }
    />
  )
}
