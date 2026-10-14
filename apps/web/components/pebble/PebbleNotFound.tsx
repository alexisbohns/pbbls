"use client"

import { useTranslations } from "next-intl"
import { NotFoundCard } from "@/components/layout/NotFoundCard"

type PebbleNotFoundProps = {
  /** Replaces the default description, e.g. "This moment no longer exists." */
  description?: string
}

/** Not found on a moment route (#1050): the read page, its edits, Complete. */
export function PebbleNotFound({ description }: PebbleNotFoundProps = {}) {
  const t = useTranslations("pebble")
  const tMoment = useTranslations("moment")
  return (
    <NotFoundCard
      title={tMoment("notFoundTitle")}
      description={description ?? tMoment("errors.momentNotFound")}
      href="/path"
      linkText={t("back")}
    />
  )
}
