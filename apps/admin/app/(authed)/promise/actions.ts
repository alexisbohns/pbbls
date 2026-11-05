"use server"

import { redirect } from "next/navigation"
import { revalidatePath } from "next/cache"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import type { PromiseLetterInsert } from "@/lib/promise-letters/types"

export type PromiseLetterFormResult = { error: string } | undefined

type ParsedFields = { ok: true; values: PromiseLetterInsert } | { ok: false; error: string }

function readFields(formData: FormData): ParsedFields {
  const body_md_en = formData.get("body_md_en")?.toString().trim() ?? ""
  const body_md_fr = formData.get("body_md_fr")?.toString().trim() || null
  const signed_by_en = formData.get("signed_by_en")?.toString().trim() || "The Pebbles team"
  const signed_by_fr = formData.get("signed_by_fr")?.toString().trim() || null
  const published = formData.get("published") === "on"
  if (!body_md_en) return { ok: false, error: "The letter (EN) is required." }
  return { ok: true, values: { body_md_en, body_md_fr, signed_by_en, signed_by_fr, published } }
}

export async function createPromiseLetter(_state: PromiseLetterFormResult, formData: FormData): Promise<PromiseLetterFormResult> {
  const parsed = readFields(formData)
  if (!parsed.ok) return { error: parsed.error }
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.from("promise_letters").insert(parsed.values).select("id").single()
  if (error) {
    console.error("[promise/createPromiseLetter] insert failed:", error.message)
    return { error: "Could not save the letter." }
  }
  revalidatePath("/promise")
  redirect(`/promise/${data.id}`)
}

export async function updatePromiseLetter(id: string, _state: PromiseLetterFormResult, formData: FormData): Promise<PromiseLetterFormResult> {
  const parsed = readFields(formData)
  if (!parsed.ok) return { error: parsed.error }
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.from("promise_letters").update(parsed.values).eq("id", id).select("id")
  if (error) {
    console.error("[promise/updatePromiseLetter] update failed:", error.message)
    return { error: "Could not save the letter." }
  }
  // RLS filters rows without raising, so a refused write comes back as zero rows.
  if (data.length === 0) {
    console.error("[promise/updatePromiseLetter] update touched no row:", id)
    return { error: "Could not save the letter." }
  }
  revalidatePath("/promise")
  revalidatePath(`/promise/${id}`)
  return undefined
}

export async function deletePromiseLetter(id: string): Promise<void> {
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.from("promise_letters").delete().eq("id", id).select("id")
  if (error) {
    console.error("[promise/deletePromiseLetter] delete failed:", error.message)
    throw new Error("Could not delete the letter.")
  }
  // RLS filters rows without raising, so a refused delete comes back as zero rows.
  if (data.length === 0) {
    console.error("[promise/deletePromiseLetter] delete touched no row:", id)
    throw new Error("Could not delete the letter.")
  }
  revalidatePath("/promise")
  redirect("/promise")
}
