"use client"

import { useTranslations } from "next-intl"
import { CalendarDate, KarmaDot, PebbleRound } from "@pbbls/icons"
import type { RippleSummary } from "@/lib/types"
import { SectionCard } from "@/components/profile/SectionCard"
import { SectionLabel } from "@/components/ui/SectionLabel"
import { RipplesRow } from "@/components/profile/RipplesRow"
import { DataTile } from "@/components/profile/DataTile"

type StatsCardProps = {
  ripple: RippleSummary
  assiduity: boolean[]
  daysPracticed: number | null
  pebbles: number | null
  karma: number | null
}

/**
 * The Profile "Stats" card — web port of the iOS `ProfileStatsCard`. Card
 * chrome around a ripples row, a divider, and the Days/Pebbles/Karma counters.
 */
export function StatsCard({
  ripple,
  assiduity,
  daysPracticed,
  pebbles,
  karma,
}: StatsCardProps) {
  const t = useTranslations("profile.stats")

  return (
    <SectionCard>
      <SectionLabel>{t("title")}</SectionLabel>
      <RipplesRow ripple={ripple} assiduity={assiduity} />
      <div className="h-px bg-border" />
      <div className="flex items-start">
        <DataTile value={daysPracticed} icon={CalendarDate} label={t("days")} />
        <DataTile value={pebbles} icon={PebbleRound} label={t("pebbles")} />
        <DataTile value={karma} icon={KarmaDot} label={t("karma")} />
      </div>
    </SectionCard>
  )
}
