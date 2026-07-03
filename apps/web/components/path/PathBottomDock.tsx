"use client"

import Link from "next/link"
import { useTranslations } from "next-intl"
import { PathBottomBar } from "@/components/path/PathBottomBar"

export function PathBottomDock() {
  const t = useTranslations("path")

  return (
    <div className="sticky inset-x-0 bottom-0 bg-gradient-to-t from-white to-transparent dark:from-background">
      <div className="px-4">
        <Link
          href="/record"
          className="block w-full rounded-2xl bg-surface py-3 text-center font-heading text-[17px] font-bold text-primary transition-colors hover:bg-muted dark:bg-accent dark:text-primary"
        >
          {t("newPebble")}
        </Link>
      </div>
      <PathBottomBar />
    </div>
  )
}
