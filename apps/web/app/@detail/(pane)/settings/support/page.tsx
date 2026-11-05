"use client"

import { useTranslations } from "next-intl"
import { DetailTopBar } from "@/components/layout/DetailTopBar"
import { CrisisResourcesList } from "@/components/support/CrisisResourcesList"

/** Crisis resources (#1459): reachable signed out or in, from Settings and the quiet success step. */
export default function SupportPage() {
  const t = useTranslations("settings.support")
  return (
    <>
      <DetailTopBar title={t("title")} backHref="/settings" />
      <section className="mx-auto w-full max-w-2xl px-4 pt-2 pb-12">
        <div className="flex flex-col gap-4">
          <p className="m3-type-body-large text-m3-on-surface">{t("intro")}</p>
          <CrisisResourcesList />
        </div>
      </section>
    </>
  )
}
