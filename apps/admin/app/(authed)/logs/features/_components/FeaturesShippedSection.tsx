import { createServerSupabaseClient } from "@/lib/supabase/server"
import type { PlatformFilter as PlatformFilterValue } from "@/lib/logs/options"
import type { LogRow } from "@/lib/logs/types"
import { LogSection } from "../../_components/LogSection"

export async function FeaturesShippedSection({
  platform,
}: {
  platform: PlatformFilterValue | undefined
}) {
  const supabase = await createServerSupabaseClient()

  let query = supabase
    .from("logs")
    .select("*")
    .eq("species", "feature")
    .eq("status", "shipped")
    .order("updated_at", { ascending: false })

  if (platform) {
    query = query.in("platform", [platform, "all"])
  }

  const { data, error } = await query

  if (error) {
    console.error("[logs/features/shipped] select failed:", error.message)
  }

  const logs: LogRow[] = data ?? []

  return <LogSection title="Shipped" logs={logs} emptyLabel="No shipped features." />
}
