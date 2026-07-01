"use server"

import { redirect } from "next/navigation"
import { revalidatePath } from "next/cache"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import type { GlyphStroke } from "@/lib/pebblestore/types"

export type ActionResult = { error: string } | undefined

const QUEUE_PATH = "/pebblestore/glyphs"

/** Map the SQL error contract (§3) to English admin copy. */
function messageFor(code: string): string {
  switch (code) {
    case "not_admin":
      return "You are not authorized to perform this action."
    case "not_found":
      return "That submission no longer exists."
    case "invalid_state":
      return "That submission is not in a state where this action is allowed."
    case "missing_note":
      return "A rejection reason is required."
    case "bad_price":
      return "Price must be a positive number of karma."
    case "empty_glyph":
      return "This glyph has no usable strokes to publish."
    case "user_not_found":
      return "No account matches that user."
    default:
      return "Something went wrong. Check the server console for details."
  }
}

export async function approveGlyph(submissionId: string, price?: number): Promise<ActionResult> {
  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.rpc("approve_glyph", {
    p_submission_id: submissionId,
    p_price: price ?? undefined,
  })
  if (error) {
    console.error("[pebblestore] approveGlyph failed:", error.message)
    return { error: messageFor(error.message) }
  }
  revalidatePath(QUEUE_PATH)
  return undefined
}

export async function rejectGlyph(submissionId: string, note: string): Promise<ActionResult> {
  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.rpc("reject_glyph", {
    p_submission_id: submissionId,
    p_note: note,
  })
  if (error) {
    console.error("[pebblestore] rejectGlyph failed:", error.message)
    return { error: messageFor(error.message) }
  }
  revalidatePath(QUEUE_PATH)
  return undefined
}

export async function setGlyphPrice(submissionId: string, price: number): Promise<ActionResult> {
  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.rpc("set_glyph_price", {
    p_submission_id: submissionId,
    p_price: price,
  })
  if (error) {
    console.error("[pebblestore] setGlyphPrice failed:", error.message)
    return { error: messageFor(error.message) }
  }
  revalidatePath(QUEUE_PATH)
  return undefined
}

export async function publishGlyph(input: {
  name: string
  strokes: GlyphStroke[]
  viewBox: string
  price: number
}): Promise<ActionResult> {
  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.rpc("publish_admin_glyph", {
    p_name: input.name,
    p_strokes: input.strokes as unknown as never, // jsonb
    p_view_box: input.viewBox,
    p_price: input.price,
  })
  if (error) {
    console.error("[pebblestore] publishGlyph failed:", error.message)
    return { error: messageFor(error.message) }
  }
  revalidatePath(QUEUE_PATH)
  redirect(`${QUEUE_PATH}?status=approved`)
}

/** Delist (or relist) an approved glyph — leaves the market, owners keep it. */
export async function setGlyphListed(submissionId: string, listed: boolean): Promise<ActionResult> {
  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.rpc("set_glyph_listed", {
    p_submission_id: submissionId,
    p_listed: listed,
  })
  if (error) {
    console.error("[pebblestore] setGlyphListed failed:", error.message)
    return { error: messageFor(error.message) }
  }
  revalidatePath(QUEUE_PATH)
  return undefined
}

/** Hard-delete a glyph (cascades to its submission + entitlements). Destructive. */
export async function deleteGlyph(glyphId: string): Promise<ActionResult> {
  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.rpc("admin_delete_glyph", { p_glyph_id: glyphId })
  if (error) {
    console.error("[pebblestore] deleteGlyph failed:", error.message)
    return { error: messageFor(error.message) }
  }
  revalidatePath(QUEUE_PATH)
  return undefined
}

export type FoundUser = { id: string; email: string | null }

/** Resolve a user by email for attribution. Returns null when no match. */
export async function findUser(
  email: string,
): Promise<{ user: FoundUser | null } | { error: string }> {
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.rpc("admin_find_user", { p_email: email })
  if (error) {
    console.error("[pebblestore] findUser failed:", error.message)
    return { error: messageFor(error.message) }
  }
  return { user: (data as FoundUser | null) ?? null }
}

/** Attribute a glyph to a user (transfer ownership → they earn payouts). */
export async function attributeGlyph(glyphId: string, userId: string): Promise<ActionResult> {
  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.rpc("admin_attribute_glyph", {
    p_glyph_id: glyphId,
    p_user_id: userId,
  })
  if (error) {
    console.error("[pebblestore] attributeGlyph failed:", error.message)
    return { error: messageFor(error.message) }
  }
  revalidatePath(QUEUE_PATH)
  return undefined
}
