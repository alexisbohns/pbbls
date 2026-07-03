"use client"

import Link from "next/link"
import { useTranslations } from "next-intl"
import { ChevronRight, FlaskConical } from "lucide-react"
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
          <FlaskConical className="size-[18px] shrink-0 text-primary" aria-hidden />
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
