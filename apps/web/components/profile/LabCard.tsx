"use client"

import Link from "next/link"
import { useTranslations } from "next-intl"
import { ChevronRight } from "lucide-react"
import { Sparkle } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { cn } from "@/lib/utils"
import { SectionCard } from "@/components/profile/SectionCard"

/**
 * The Profile "Lab" card — web port of the iOS `ProfileLabCard`. The whole card
 * links to the Lab feed.
 */
export function LabCard() {
  const t = useTranslations("profile.lab")
  return (
    <Link href="/lab" className="block transition-opacity hover:opacity-90">
      <SectionCard>
        <div className="flex items-center gap-3">
          <Sparkle className={cn(PBBLS_ICON_SIZE.row, "shrink-0 text-primary")} />
          <div className="flex min-w-0 flex-1 flex-col gap-1">
            <span className="text-base font-semibold text-foreground">{t("title")}</span>
            <span className="text-sm text-muted-foreground">{t("subtitle")}</span>
          </div>
          <ChevronRight className="size-4 shrink-0 text-muted-foreground" aria-hidden />
        </div>
      </SectionCard>
    </Link>
  )
}
