import type { SupabaseClient } from "@supabase/supabase-js"
import type { Log, LogPlatform, LogSpecies, LogStatus } from "@/lib/types"

// Read/write helpers for `public.logs` and `public.log_reactions`.
// All reads go through `v_logs_with_counts` so every row carries its
// reaction count. Reactions are single-table writes — no RPC needed.
//
// Mirrors apps/ios/Pebbles/Features/Lab/Services/LogsService.swift.

type LogRow = {
  id: string | null
  species: string | null
  platform: string | null
  status: string | null
  title_en: string | null
  title_fr: string | null
  summary_en: string | null
  summary_fr: string | null
  body_md_en: string | null
  body_md_fr: string | null
  cover_image_path: string | null
  external_url: string | null
  published: boolean | null
  published_at: string | null
  created_at: string | null
  reaction_count: number | null
}

function rowToLog(row: LogRow): Log | null {
  if (!row.id || !row.species || !row.platform || !row.status || !row.created_at) {
    return null
  }
  if (row.title_en == null || row.summary_en == null) return null
  return {
    id: row.id,
    species: row.species as LogSpecies,
    platform: row.platform as LogPlatform,
    status: row.status as LogStatus,
    title_en: row.title_en,
    title_fr: row.title_fr,
    summary_en: row.summary_en,
    summary_fr: row.summary_fr,
    body_md_en: row.body_md_en,
    body_md_fr: row.body_md_fr,
    cover_image_path: row.cover_image_path,
    external_url: row.external_url,
    published: row.published ?? false,
    published_at: row.published_at,
    created_at: row.created_at,
    reaction_count: row.reaction_count ?? 0,
  }
}

function rowsToLogs(rows: LogRow[] | null): Log[] {
  return (rows ?? []).map(rowToLog).filter((l): l is Log => l != null)
}

export async function fetchAnnouncements(
  supabase: SupabaseClient,
  options?: { limit?: number },
): Promise<Log[]> {
  let query = supabase
    .from("v_logs_with_counts")
    .select("*")
    .eq("species", "announcement")
    .eq("published", true)
    .order("published_at", { ascending: false })
  if (options?.limit) query = query.limit(options.limit)
  const { data, error } = await query
  if (error) throw new Error(error.message)
  return rowsToLogs(data as LogRow[] | null)
}

export async function fetchChangelog(
  supabase: SupabaseClient,
  options?: { limit?: number },
): Promise<Log[]> {
  let query = supabase
    .from("v_logs_with_counts")
    .select("*")
    .eq("species", "feature")
    .eq("status", "shipped")
    .eq("published", true)
    .order("published_at", { ascending: false })
  if (options?.limit) query = query.limit(options.limit)
  const { data, error } = await query
  if (error) throw new Error(error.message)
  return rowsToLogs(data as LogRow[] | null)
}

export async function fetchInitiatives(supabase: SupabaseClient): Promise<Log[]> {
  const { data, error } = await supabase
    .from("v_logs_with_counts")
    .select("*")
    .eq("species", "feature")
    .eq("status", "in_progress")
    .eq("published", true)
    .order("published_at", { ascending: false })
  if (error) throw new Error(error.message)
  return rowsToLogs(data as LogRow[] | null)
}

export async function fetchBacklog(
  supabase: SupabaseClient,
  options?: { limit?: number },
): Promise<Log[]> {
  let query = supabase
    .from("v_logs_with_counts")
    .select("*")
    .eq("species", "feature")
    .eq("status", "backlog")
    .eq("published", true)
    .order("reaction_count", { ascending: false })
    .order("created_at", { ascending: false })
  if (options?.limit) query = query.limit(options.limit)
  const { data, error } = await query
  if (error) throw new Error(error.message)
  return rowsToLogs(data as LogRow[] | null)
}

export async function fetchAnnouncement(
  supabase: SupabaseClient,
  id: string,
): Promise<Log | null> {
  const { data, error } = await supabase
    .from("v_logs_with_counts")
    .select("*")
    .eq("id", id)
    .maybeSingle()
  if (error) throw new Error(error.message)
  if (!data) return null
  return rowToLog(data as LogRow)
}

export async function fetchMyReactions(
  supabase: SupabaseClient,
  userId: string | null,
): Promise<Set<string>> {
  if (!userId) return new Set()
  const { data, error } = await supabase
    .from("log_reactions")
    .select("log_id")
    .eq("user_id", userId)
  if (error) throw new Error(error.message)
  return new Set((data ?? []).map((row) => (row as { log_id: string }).log_id))
}

export async function react(
  supabase: SupabaseClient,
  userId: string,
  logId: string,
): Promise<void> {
  const { error } = await supabase
    .from("log_reactions")
    .insert({ log_id: logId, user_id: userId })
  if (error) throw new Error(error.message)
}

export async function unreact(
  supabase: SupabaseClient,
  userId: string,
  logId: string,
): Promise<void> {
  const { error } = await supabase
    .from("log_reactions")
    .delete()
    .eq("log_id", logId)
    .eq("user_id", userId)
  if (error) throw new Error(error.message)
}
