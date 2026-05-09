"use client"

import { useTranslations } from "next-intl"
import { EmptyState } from "@/components/layout/EmptyState"

export function SoulsEmptyState() {
  const t = useTranslations("souls.empty")
  return <EmptyState title={t("title")} description={t("description")} />
}
