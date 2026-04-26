import { Suspense } from "react"
import Link from "next/link"
import { Plus } from "lucide-react"
import { buttonVariants } from "@/components/ui/button"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import type { LogRow } from "@/lib/logs/types"
import { FeatureSection } from "../_components/FeatureSection"
import { FeatureSectionSkeleton } from "../_components/FeatureSectionSkeleton"
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
        <Link href="/logs/new?species=announcement" className={buttonVariants()}>
          <Plus className="size-4" aria-hidden />
          New log
        </Link>
      </header>
      <div className="space-y-8">
        <FeatureSection title="Drafts" logs={drafts} emptyLabel="No drafts." />
        <Suspense fallback={<FeatureSectionSkeleton title="Published" />}>
          <AnnouncementsPublishedSection />
        </Suspense>
      </div>
    </section>
  )
}
