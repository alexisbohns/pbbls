import { createServerSupabaseClient } from "@/lib/supabase/server"
import type { LogRow } from "@/lib/logs/types"
import { FeatureSection } from "../../_components/FeatureSection"

export async function AnnouncementsPublishedSection() {
  const supabase = await createServerSupabaseClient()

  const { data, error } = await supabase
    .from("logs")
    .select("*")
    .eq("species", "announcement")
    .eq("published", true)
    .order("updated_at", { ascending: false })

  if (error) {
    console.error("[logs/announcements/published] select failed:", error.message)
  }

  const logs: LogRow[] = data ?? []

  return <FeatureSection title="Published" logs={logs} emptyLabel="No published announcements." />
}
