import { Suspense } from "react"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import type { LogRow } from "@/lib/logs/types"
import { LogSection } from "../_components/LogSection"
import { LogSectionSkeleton } from "../_components/LogSectionSkeleton"
import { NewLogButton } from "../_components/NewLogButton"
import { AnnouncementsPublishedSection } from "./_components/AnnouncementsPublishedSection"

export default async function AnnouncementsPage() {
  const supabase = await createServerSupabaseClient()

  const { data, error } = await supabase
    .from("logs")
    .select("*")
    .eq("species", "announcement")
    .eq("published", false)
    .order("updated_at", { ascending: false })

  if (error) {
    console.error("[logs/announcements/drafts] select failed:", error.message)
  }

  const drafts: LogRow[] = data ?? []

  return (
    <section className="space-y-6">
      <header className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Announcements</h1>
        <NewLogButton species="announcement" />
      </header>
      <div className="space-y-8">
        <LogSection title="Drafts" logs={drafts} emptyLabel="No drafts." variant="drafts" />
        <Suspense fallback={<LogSectionSkeleton title="Published" />}>
          <AnnouncementsPublishedSection />
        </Suspense>
      </div>
    </section>
  )
}
