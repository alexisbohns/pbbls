"use client"

import { useTranslations } from "next-intl"
import { NotFoundCard } from "@/components/layout/NotFoundCard"

export function GlyphNotFound() {
  const t = useTranslations("glyphs.detail")
  return (
    <NotFoundCard
      title={t("notFoundTitle")}
      description={t("notFoundDescription")}
      href="/glyphs"
      linkText={t("linkText")}
    />
  )
}
