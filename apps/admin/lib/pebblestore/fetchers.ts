import { createServerSupabaseClient } from "@/lib/supabase/server"
import type { AdminSubmission, PebbleShape, SubmissionStatus } from "./types"

export async function listSubmissions(status?: SubmissionStatus): Promise<AdminSubmission[]> {
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.rpc("admin_list_glyph_submissions", {
    p_status: status ?? undefined,
  })
  if (error) {
    console.error("[pebblestore] listSubmissions failed:", error.message)
    throw new Error(error.message)
  }
  return (data ?? []) as unknown as AdminSubmission[]
}

export async function listShapes(): Promise<PebbleShape[]> {
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase
    .from("pebble_shapes")
    .select("id, slug, name, path, view_box")
    .order("name")
  if (error) {
    console.error("[pebblestore] listShapes failed:", error.message)
    throw new Error(error.message)
  }
  return data ?? []
}
