"use client"

import { useTranslations } from "next-intl"
import { EmptyState } from "@/components/layout/EmptyState"

export function CollectionsEmptyState() {
  const t = useTranslations("collections.empty")
  return <EmptyState title={t("title")} description={t("description")} />
}
