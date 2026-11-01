"use server"

import { redirect } from "next/navigation"
import { revalidatePath } from "next/cache"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import { KEY_PATTERN, isAllowedLinkUrl, isSettingsLinkSection } from "@/lib/settings-links/options"
import type { SettingsLinkInsert } from "@/lib/settings-links/types"

export type SettingsLinkFormResult = { error: string } | undefined

function readLinkFields(formData: FormData): { values: SettingsLinkInsert; error?: string } {
  const key = formData.get("key")?.toString().trim() ?? ""
  const sectionRaw = formData.get("section")?.toString()
  const sortRaw = formData.get("sort_order")?.toString().trim() ?? ""
  const sort_order = sortRaw === "" ? 0 : Number(sortRaw)
  const title_en = formData.get("title_en")?.toString().trim() ?? ""
  const title_fr = formData.get("title_fr")?.toString().trim() || null
  const url = formData.get("url")?.toString().trim() ?? ""
  const published = formData.get("published") === "on"

  const empty = {} as SettingsLinkInsert
  if (!KEY_PATTERN.test(key)) return { error: "Key is lowercase words joined by hyphens.", values: empty }
  if (!isSettingsLinkSection(sectionRaw)) return { error: "Pick a section.", values: empty }
  if (!Number.isInteger(sort_order) || sort_order < 0 || sort_order > 32767) return { error: "Sort order is a whole number from 0 to 32767.", values: empty }
  if (!title_en) return { error: "Title (EN) is required.", values: empty }
  if (!isAllowedLinkUrl(url)) return { error: "The URL starts with https:// or mailto:.", values: empty }

  return { values: { key, section: sectionRaw, sort_order, title_en, title_fr, url, published } }
}

export async function createSettingsLink(_state: SettingsLinkFormResult, formData: FormData): Promise<SettingsLinkFormResult> {
  const parsed = readLinkFields(formData)
  if (parsed.error) return { error: parsed.error }
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.from("settings_links").insert(parsed.values).select("id").single()
  if (error) {
    console.error("[settings-links/createSettingsLink] insert failed:", error.message)
    return { error: error.code === "23505" ? "A link with that key already exists." : "Could not save the link." }
  }
  revalidatePath("/settings-links")
  redirect(`/settings-links/${data.id}`)
}

export async function updateSettingsLink(id: string, _state: SettingsLinkFormResult, formData: FormData): Promise<SettingsLinkFormResult> {
  const parsed = readLinkFields(formData)
  if (parsed.error) return { error: parsed.error }
  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.from("settings_links").update(parsed.values).eq("id", id)
  if (error) {
    console.error("[settings-links/updateSettingsLink] update failed:", error.message)
    return { error: error.code === "23505" ? "A link with that key already exists." : "Could not save the link." }
  }
  revalidatePath("/settings-links")
  revalidatePath(`/settings-links/${id}`)
  return undefined
}

export async function deleteSettingsLink(id: string): Promise<void> {
  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.from("settings_links").delete().eq("id", id)
  if (error) {
    console.error("[settings-links/deleteSettingsLink] delete failed:", error.message)
    throw new Error("Could not delete the link.")
  }
  revalidatePath("/settings-links")
  redirect("/settings-links")
}
