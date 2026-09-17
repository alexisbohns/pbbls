import type { ContentReport } from "@/lib/moderation/types"
import { ReportCard } from "./ReportCard"

export function ReportQueue({ reports }: { reports: ContentReport[] }) {
  if (reports.length === 0) {
    return (
      <p className="rounded-lg border border-dashed p-8 text-center text-sm text-muted-foreground">
        No reports in this state.
      </p>
    )
  }
  return (
    <div className="space-y-4">
      {reports.map((r) => (
        <ReportCard key={r.id} report={r} />
      ))}
    </div>
  )
}
