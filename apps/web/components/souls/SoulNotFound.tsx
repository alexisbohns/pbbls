"use client"

import { useTranslations } from "next-intl"
import { NotFoundCard } from "@/components/layout/NotFoundCard"

export function SoulNotFound() {
  const t = useTranslations("souls.detail")
  return (
    <NotFoundCard
      title={t("notFoundTitle")}
      description={t("notFoundDescription")}
      href="/souls"
      linkText={t("linkText")}
    />
  )
}
