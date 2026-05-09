"use client"

import { useTranslations } from "next-intl"
import { NotFoundCard } from "@/components/layout/NotFoundCard"

export function CollectionNotFound() {
  const t = useTranslations("collections.detail")
  return (
    <NotFoundCard
      title={t("notFoundTitle")}
      description={t("notFoundDescription")}
      href="/collections"
      linkText={t("linkText")}
    />
  )
}
