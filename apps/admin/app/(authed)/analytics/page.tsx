import { Suspense } from "react"
import { ActiveUsersChartCard } from "@/components/analytics/ActiveUsersChartCard"
import { ChartCardSkeleton } from "@/components/analytics/ChartCardSkeleton"
import { KpiStrip } from "@/components/analytics/KpiStrip"
import { KpiStripSkeleton } from "@/components/analytics/KpiStripSkeleton"
import { PebbleEnrichmentCard } from "@/components/analytics/PebbleEnrichmentCard"
import { PebbleVolumeChartCard } from "@/components/analytics/PebbleVolumeChartCard"
import { RetentionHeatmapCard } from "@/components/analytics/RetentionHeatmapCard"
import { TimeRangeTabs } from "@/components/analytics/TimeRangeTabs"
import { isTimeRange, type TimeRange } from "@/lib/analytics/types"

type SearchParams = Promise<{ range?: string }>

export default async function AnalyticsPage({
  searchParams,
}: {
  searchParams: SearchParams
}) {
  const params = await searchParams
  const range: TimeRange = isTimeRange(params.range) ? params.range : "30d"

  return (
    <section className="space-y-6">
      <header className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Analytics</h1>
        <TimeRangeTabs />
      </header>

      <Suspense fallback={<KpiStripSkeleton />}>
        <KpiStrip range={range} />
      </Suspense>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
        <div className="lg:col-span-8">
          <Suspense fallback={<ChartCardSkeleton />}>
            <ActiveUsersChartCard range={range} />
          </Suspense>
        </div>
        <div className="lg:col-span-4">
          <Suspense fallback={<ChartCardSkeleton />}>
            <RetentionHeatmapCard />
          </Suspense>
        </div>
        <div className="lg:col-span-8">
          <Suspense fallback={<ChartCardSkeleton />}>
            <PebbleVolumeChartCard range={range} />
          </Suspense>
        </div>
        <div className="lg:col-span-4">
          <Suspense fallback={<ChartCardSkeleton />}>
            <PebbleEnrichmentCard />
          </Suspense>
        </div>
      </div>
    </section>
  )
}
