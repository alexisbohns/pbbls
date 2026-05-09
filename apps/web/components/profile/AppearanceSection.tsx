"use client"

import { Globe, Sun } from "lucide-react"
import { useTranslations } from "next-intl"
import { ThemeToggle } from "@/components/layout/ThemeToggle"
import { LocaleSwitcher } from "@/components/profile/LocaleSwitcher"

export function AppearanceSection() {
  const t = useTranslations("profile")

  return (
    <div className="divide-y divide-border rounded-xl border border-border">
      <div className="flex items-center gap-3 px-4 py-3">
        <Sun className="size-5 text-muted-foreground" aria-hidden />
        <span className="flex-1 text-sm font-medium">{t("appearance")}</span>
        <ThemeToggle />
      </div>
      <div className="flex items-center gap-3 px-4 py-3">
        <Globe className="size-5 text-muted-foreground" aria-hidden />
        <span className="flex-1 text-sm font-medium">{t("language")}</span>
        <LocaleSwitcher />
      </div>
    </div>
  )
}
