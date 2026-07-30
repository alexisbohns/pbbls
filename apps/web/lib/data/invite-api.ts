import type { SupabaseClient } from "@supabase/supabase-js"
import type { ConnectionPeer, MarkStroke, PeerGlyph } from "@/lib/types"

// Anon-callable invite preview (M49, design D4). Deliberately NOT on
// `DataProvider`: the `/invite/[token]` page must render "X invites you to
// connect" for signed-out visitors, so this follows the `logs-api.ts` shape —
// a plain function taking whichever SupabaseClient the caller has (the server
// client for the SSR page, the browser client elsewhere).

export type ConnectionInvitePreview =
  | { status: "valid"; inviter: ConnectionPeer }
  | { status: "expired" }
  | { status: "not_found" }

/**
 * Narrow the RPC peer projection `{display_name, glyph: {strokes, view_box} |
 * null}` (jsonb, untyped to PostgREST) into `ConnectionPeer`. Shared with
 * `SupabaseProvider` — preview, accept and get_connections all return the same
 * projection shape, and the snake→camel / view_box→viewBox mapping must not
 * drift between them. A malformed glyph degrades to `null` (text-only render)
 * rather than crashing the page.
 */
export function toConnectionPeer(raw: unknown): ConnectionPeer {
  const obj =
    raw !== null && typeof raw === "object" && !Array.isArray(raw)
      ? (raw as Record<string, unknown>)
      : {}
  const glyphRaw = obj.glyph
  const glyphObj =
    glyphRaw !== null && typeof glyphRaw === "object" && !Array.isArray(glyphRaw)
      ? (glyphRaw as Record<string, unknown>)
      : null
  const glyph: PeerGlyph | null =
    glyphObj && Array.isArray(glyphObj.strokes) && typeof glyphObj.view_box === "string"
      ? { strokes: glyphObj.strokes as MarkStroke[], viewBox: glyphObj.view_box }
      : null
  return {
    displayName: (obj.display_name as string) ?? "",
    glyph,
  }
}

/**
 * Preview who is behind an invite token. The RPC never raises — it returns
 * `{status}` shapes (expired and revoked share one outward "expired" shape
 * with no inviter data) — so a throw here means transport/config failure,
 * not a dead token.
 */
export async function previewConnectionInvite(
  supabase: SupabaseClient,
  token: string,
): Promise<ConnectionInvitePreview> {
  const { data, error } = await supabase.rpc("preview_connection_invite", {
    p_token: token,
  })
  if (error) throw new Error(`preview_connection_invite failed: ${error.message}`)
  const obj =
    data !== null && typeof data === "object" && !Array.isArray(data)
      ? (data as Record<string, unknown>)
      : {}
  if (obj.status === "valid") {
    return { status: "valid", inviter: toConnectionPeer(obj.inviter) }
  }
  if (obj.status === "expired") return { status: "expired" }
  if (obj.status !== "not_found") {
    // Unknown shape from a future server — log it, degrade to the dark state.
    console.error("[invite-api] unexpected preview shape", data)
  }
  return { status: "not_found" }
}
