import type { SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

/**
 * Reads the karma delta a create/enrich RPC just recorded for a pebble.
 *
 * `create_pebble` writes one `pebble_created` event; `update_pebble` writes a
 * `pebble_enriched` event only when karma changed. We take the most recent row
 * matching (ref_id, reason) so a re-enrich reads its own delta. Returns null
 * when no matching row exists (e.g. an enrich that changed no karma) — the
 * client treats null as "nothing to celebrate".
 *
 * Best-effort: the karma flash is delight-only, so any read error resolves to
 * null rather than failing the whole create/enrich response.
 */
export async function readKarmaDelta(
  client: SupabaseClient,
  refId: string,
  reason: "pebble_created" | "pebble_enriched",
): Promise<number | null> {
  const { data, error } = await client
    .from("karma_events")
    .select("delta")
    .eq("ref_id", refId)
    .eq("reason", reason)
    .order("created_at", { ascending: false })
    .limit(1)
    .maybeSingle();

  if (error) {
    console.error("readKarmaDelta failed:", error);
    return null;
  }
  return data?.delta ?? null;
}
