import { ChartCardSkeleton } from "@/components/analytics/ChartCardSkeleton"
import { KpiStripSkeleton } from "@/components/analytics/KpiStripSkeleton"

export default function AnalyticsLoading() {
  return (
    <div className="space-y-6">
      <KpiStripSkeleton />
      <ChartCardSkeleton />
    </div>
  )
}
