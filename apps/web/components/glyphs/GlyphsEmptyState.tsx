"use client"

import Link from "next/link"
import { useTranslations } from "next-intl"
import { EmptyState } from "@/components/layout/EmptyState"

export function GlyphsEmptyState() {
  const t = useTranslations("glyphs.empty")
  return (
    <EmptyState
      title={t("title")}
      description={t("description")}
      action={
        <Link
          href="/carve"
          className="text-sm font-medium text-primary underline underline-offset-4 hover:text-primary/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        >
          {t("cta")}
        </Link>
      }
    />
  )
}
