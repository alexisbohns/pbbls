"use client"

import Link from "next/link"
import { useTranslations } from "next-intl"

/** The fresh-week invitation, set on the paper like the rest of the Path (#1052). */
export function PathEmptyState() {
  const t = useTranslations("path.empty.currentWeek")
  return (
    <div className="flex flex-col items-center px-8 py-16 text-center font-spine text-(--spine-ink)">
      <h2 className="text-[28px] leading-tight font-light tracking-[-.02em]">{t("title")}</h2>
      <p className="mt-2 max-w-[28ch] text-base font-[380] text-(--spine-ink-2) italic">{t("description")}</p>
      <Link
        href="/record"
        className="mt-5 inline-block py-2 font-spine-sc text-xs font-semibold tracking-[.18em] lowercase underline decoration-1 underline-offset-[5px] hover:text-(--spine-ink-2)"
      >
        {t("cta")}
      </Link>
    </div>
  )
}
