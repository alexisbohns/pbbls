import { createServerSupabaseClient } from "@/lib/supabase/server"
import type { ContentReport, ReportStatus } from "./types"

export async function listContentReports(status?: ReportStatus): Promise<ContentReport[]> {
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.rpc("admin_list_content_reports", {
    p_status: status ?? undefined,
  })
  if (error) {
    console.error("[moderation] listContentReports failed:", error.message)
    throw new Error(error.message)
  }
  return (data ?? []) as unknown as ContentReport[]
}
