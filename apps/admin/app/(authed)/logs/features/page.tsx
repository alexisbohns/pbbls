import { Suspense } from "react"
import Link from "next/link"
import { Plus } from "lucide-react"
import { buttonVariants } from "@/components/ui/button"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import { isPlatformFilter } from "@/lib/logs/options"
import type { LogRow, LogStatus } from "@/lib/logs/types"
import { LogSection } from "../_components/LogSection"
import { LogSectionSkeleton } from "../_components/LogSectionSkeleton"
import { PlatformFilter } from "./_components/PlatformFilter"
import { FeaturesShippedSection } from "./_components/FeaturesShippedSection"

type SearchParams = Promise<{ platform?: string }>

const ACTIVE_STATUSES = ["in_progress", "planned", "backlog"] as const satisfies readonly LogStatus[]

export default async function FeaturesPage({ searchParams }: { searchParams: SearchParams }) {
  const params = await searchParams
  const platform = isPlatformFilter(params.platform) ? params.platform : undefined

  const supabase = await createServerSupabaseClient()

  let query = supabase
    .from("logs")
    .select("*")
    .eq("species", "feature")
    .in("status", ACTIVE_STATUSES)
    .order("updated_at", { ascending: false })

  if (platform) {
    query = query.in("platform", [platform, "all"])
  }

  const { data, error } = await query

  if (error) {
    console.error("[logs/features/active] select failed:", error.message)
  }

  const active: LogRow[] = data ?? []
  const grouped: Record<(typeof ACTIVE_STATUSES)[number], LogRow[]> = {
    in_progress: [],
    planned: [],
    backlog: [],
  }
  for (const log of active) {
    if (log.status === "in_progress" || log.status === "planned" || log.status === "backlog") {
      grouped[log.status].push(log)
    }
  }

  return (
    <section className="space-y-6">
      <header className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Features</h1>
        <Link href="/logs/new?species=feature" className={buttonVariants()}>
          <Plus className="size-4" aria-hidden />
          New log
        </Link>
      </header>
      <PlatformFilter />
      <div className="space-y-8">
        <LogSection
          title="In progress"
          logs={grouped.in_progress}
          emptyLabel="No features in progress."
        />
        <LogSection title="Planned" logs={grouped.planned} emptyLabel="No planned features." />
        <LogSection title="Backlog" logs={grouped.backlog} emptyLabel="No backlog features." />
        <Suspense fallback={<LogSectionSkeleton title="Shipped" />}>
          <FeaturesShippedSection platform={platform} />
        </Suspense>
      </div>
    </section>
  )
}
