"use server"

import { redirect } from "next/navigation"
import { revalidatePath } from "next/cache"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import { SLUG_PATTERN, isGuideIllustration } from "@/lib/guides/options"
import type { GuideInsert } from "@/lib/guides/types"

export type GuideFormResult = { error: string } | undefined


function readGuideFields(formData: FormData): { values: GuideInsert; error?: string } {
  const slug = formData.get("slug")?.toString().trim() ?? ""
  const sortRaw = formData.get("sort_order")?.toString().trim() ?? ""
  const sort_order = sortRaw === "" ? 0 : Number(sortRaw)
  const illustrationRaw = formData.get("illustration")?.toString() || undefined
  const illustration = isGuideIllustration(illustrationRaw) ? illustrationRaw : null
  const title_en = formData.get("title_en")?.toString().trim() ?? ""
  const title_fr = formData.get("title_fr")?.toString().trim() || null
  const summary_en = formData.get("summary_en")?.toString().trim() ?? ""
  const summary_fr = formData.get("summary_fr")?.toString().trim() || null
  const body_md_en = formData.get("body_md_en")?.toString().trim() ?? ""
  const body_md_fr = formData.get("body_md_fr")?.toString().trim() || null
  const published = formData.get("published") === "on"

  const empty = {} as GuideInsert
  if (!SLUG_PATTERN.test(slug)) return { error: "Slug is lowercase words joined by hyphens.", values: empty }
  if (!Number.isInteger(sort_order) || sort_order < 0 || sort_order > 32767) return { error: "Sort order is a whole number from 0 to 32767.", values: empty }
  if (!title_en) return { error: "Title (EN) is required.", values: empty }
  if (!summary_en) return { error: "Summary (EN) is required.", values: empty }
  if (!body_md_en) return { error: "Body (EN) is required.", values: empty }

  return { values: { slug, sort_order, illustration, title_en, title_fr, summary_en, summary_fr, body_md_en, body_md_fr, published } }
}

export async function createGuide(_state: GuideFormResult, formData: FormData): Promise<GuideFormResult> {
  const parsed = readGuideFields(formData)
  if (parsed.error) return { error: parsed.error }
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.from("guides").insert(parsed.values).select("id").single()
  if (error) {
    console.error("[guides/createGuide] insert failed:", error.message)
    return { error: error.code === "23505" ? "A guide with that slug already exists." : "Could not save the guide." }
  }
  revalidatePath("/guides")
  redirect(`/guides/${data.id}`)
}

export async function updateGuide(id: string, _state: GuideFormResult, formData: FormData): Promise<GuideFormResult> {
  const parsed = readGuideFields(formData)
  if (parsed.error) return { error: parsed.error }
  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.from("guides").update(parsed.values).eq("id", id)
  if (error) {
    console.error("[guides/updateGuide] update failed:", error.message)
    return { error: error.code === "23505" ? "A guide with that slug already exists." : "Could not save the guide." }
  }
  revalidatePath("/guides")
  revalidatePath(`/guides/${id}`)
  return undefined
}

export async function deleteGuide(id: string): Promise<void> {
  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.from("guides").delete().eq("id", id)
  if (error) {
    console.error("[guides/deleteGuide] delete failed:", error.message)
    throw new Error("Could not delete the guide.")
  }
  revalidatePath("/guides")
  redirect("/guides")
}
