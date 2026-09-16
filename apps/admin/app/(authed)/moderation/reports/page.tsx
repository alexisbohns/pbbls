import Link from "next/link"
import { Button } from "@/components/ui/button"
import { ErrorBlock } from "@/components/analytics/ErrorBlock"
import { listContentReports } from "@/lib/moderation/fetchers"
import type { ContentReport, ReportStatus } from "@/lib/moderation/types"
import { ReportQueue } from "./_components/ReportQueue"

const STATUSES: ReportStatus[] = ["open", "actioned", "dismissed"]

function isStatus(v: string | undefined): v is ReportStatus {
  return v === "open" || v === "actioned" || v === "dismissed"
}

export default async function ContentReportsPage({
  searchParams,
}: {
  searchParams: Promise<{ status?: string }>
}) {
  const { status } = await searchParams
  const active: ReportStatus = isStatus(status) ? status : "open"

  // Oldest-first is the RPC's own ordering (FIFO review) — the queue does not
  // re-sort, so the person who waited longest is answered first.
  //
  // Caught rather than left to throw: the RPC refuses a non-admin with
  // not_admin and anon with a permission error, and a moderation queue that
  // answers either with a 500 tells the operator nothing about why.
  let reports: ContentReport[]
  try {
    reports = await listContentReports(active)
  } catch (err) {
    return (
      <ErrorBlock
        label="Failed to load content reports"
        message={err instanceof Error ? err.message : String(err)}
      />
    )
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-lg font-semibold">Content reports</h1>
        <p className="text-sm text-muted-foreground">
          What people flagged on public pebbles, public profiles and Market glyphs.
        </p>
      </div>

      <nav className="flex gap-2">
        {STATUSES.map((s) => (
          <Button
            key={s}
            size="sm"
            variant={s === active ? "default" : "outline"}
            render={<Link href={`/moderation/reports?status=${s}`} />}
          >
            {s[0].toUpperCase() + s.slice(1)}
          </Button>
        ))}
      </nav>

      <ReportQueue reports={reports} />
    </div>
  )
}
