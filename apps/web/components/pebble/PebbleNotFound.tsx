"use client"

import { useTranslations } from "next-intl"
import { NotFoundCard } from "@/components/layout/NotFoundCard"

export function PebbleNotFound() {
  const t = useTranslations("pebble")
  return (
    <NotFoundCard
      title={t("notFoundTitle")}
      description={t("notFoundDescription")}
      href="/path"
      linkText={t("back")}
    />
  )
}
