"use server"

import { revalidatePath } from "next/cache"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import type { ReportTargetKind } from "@/lib/moderation/types"

export type ActionResult = { error: string } | undefined

const QUEUE_PATH = "/moderation/reports"

/** Map the SQL error contract to English admin copy. */
function messageFor(code: string): string {
  switch (code) {
    case "not_admin":
      return "You are not authorized to perform this action."
    case "not_found":
      return "That report no longer exists."
    case "invalid_state":
      return "That report has already been resolved."
    case "invalid_outcome":
      return "A report can only be actioned or dismissed."
    case "invalid_kind":
      return "Glyphs are taken down by delisting, not by hiding."
    default:
      return "Something went wrong. Check the server console for details."
  }
}

/**
 * Close a report. `actioned` also performs the takedown, in the same
 * transaction as the verdict — so "the status says actioned" and "the content
 * is down" cannot drift apart. Since #833 the takedown is reversible: a pebble
 * or profile is hidden rather than having its own settings overwritten.
 */
export async function resolveReport(
  reportId: string,
  outcome: "actioned" | "dismissed",
  note?: string,
): Promise<ActionResult> {
  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.rpc("resolve_content_report", {
    p_report_id: reportId,
    p_outcome: outcome,
    p_note: note?.trim() ? note.trim() : undefined,
  })
  if (error) {
    console.error("[moderation] resolveReport failed:", error.message)
    return { error: messageFor(error.message) }
  }
  revalidatePath(QUEUE_PATH)
  return undefined
}

/**
 * Hide or un-hide content directly, independent of any report — the appeal
 * path. Un-hiding is how a wrongly-removed pebble or profile comes back.
 */
export async function setContentHidden(
  targetKind: ReportTargetKind,
  targetId: string,
  hidden: boolean,
  note?: string,
): Promise<ActionResult> {
  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.rpc("admin_set_content_hidden", {
    p_target_kind: targetKind,
    p_target_id: targetId,
    p_hidden: hidden,
    p_note: note?.trim() ? note.trim() : undefined,
  })
  if (error) {
    console.error("[moderation] setContentHidden failed:", error.message)
    return { error: messageFor(error.message) }
  }
  revalidatePath(QUEUE_PATH)
  return undefined
}

/**
 * Free an impersonating handle back to the pool. DESTRUCTIVE and separate from
 * the default takedown on purpose: once released, anyone can claim the name.
 * The right tool for impersonation, the wrong one for everything else.
 */
export async function releaseHandle(userId: string, note?: string): Promise<ActionResult> {
  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.rpc("admin_release_handle", {
    p_user_id: userId,
    p_note: note?.trim() ? note.trim() : undefined,
  })
  if (error) {
    console.error("[moderation] releaseHandle failed:", error.message)
    return { error: messageFor(error.message) }
  }
  revalidatePath(QUEUE_PATH)
  return undefined
}
