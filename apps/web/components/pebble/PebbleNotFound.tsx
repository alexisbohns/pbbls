"use client"

import { useTranslations } from "next-intl"
import { NotFoundCard } from "@/components/layout/NotFoundCard"

type PebbleNotFoundProps = {
  /** Replaces the default description, e.g. "This moment no longer exists." */
  description?: string
}

export function PebbleNotFound({ description }: PebbleNotFoundProps = {}) {
  const t = useTranslations("pebble")
  return (
    <NotFoundCard
      title={t("notFoundTitle")}
      description={description ?? t("notFoundDescription")}
      href="/path"
      linkText={t("back")}
    />
  )
}
