"use server"

import { revalidatePath } from "next/cache"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import { domainErrorMessage } from "@/lib/domains/types"
import type { GlyphStroke } from "@/lib/pebblestore/types"

export type ActionResult = { error: string } | undefined

const LIST_PATH = "/domains"

export async function updateDomain(input: {
  id: string
  name: string
  label: string
}): Promise<ActionResult> {
  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.rpc("admin_update_domain", {
    p_domain_id: input.id,
    p_name: input.name,
    p_label: input.label,
  })
  if (error) {
    console.error("[domains] updateDomain failed:", error.message)
    return { error: domainErrorMessage(error.message) }
  }
  revalidatePath(LIST_PATH)
  revalidatePath(`${LIST_PATH}/${input.id}`)
  return undefined
}

export async function setDomainGlyph(input: {
  id: string
  strokes: GlyphStroke[]
  viewBox: string
}): Promise<ActionResult> {
  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.rpc("admin_set_domain_glyph", {
    p_domain_id: input.id,
    p_strokes: input.strokes as unknown as never, // jsonb
    p_view_box: input.viewBox,
  })
  if (error) {
    console.error("[domains] setDomainGlyph failed:", error.message)
    return { error: domainErrorMessage(error.message) }
  }
  revalidatePath(LIST_PATH)
  revalidatePath(`${LIST_PATH}/${input.id}`)
  return undefined
}
