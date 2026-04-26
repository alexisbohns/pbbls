"use server"

import { redirect } from "next/navigation"
import { revalidatePath } from "next/cache"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import { isLogSpecies, isLogStatus } from "@/lib/logs/options"
import type { LogInsert, LogPlatform } from "@/lib/logs/types"

const PLATFORM_VALUES: ReadonlyArray<LogPlatform> = ["web", "ios", "android", "all"]
function isLogPlatform(value: string | null): value is LogPlatform {
  return value !== null && (PLATFORM_VALUES as readonly string[]).includes(value)
}

export type LogFormResult = { error: string } | undefined

function readLogFields(formData: FormData): { values: LogInsert; error?: string } {
  const species = formData.get("species")?.toString()
  const platform = formData.get("platform")?.toString() ?? null
  const status = formData.get("status")?.toString()
  const title_en = formData.get("title_en")?.toString().trim() ?? ""
  const title_fr = formData.get("title_fr")?.toString().trim() || null
  const summary_en = formData.get("summary_en")?.toString().trim() ?? ""
  const summary_fr = formData.get("summary_fr")?.toString().trim() || null
  const body_md_en = formData.get("body_md_en")?.toString().trim() || null
  const body_md_fr = formData.get("body_md_fr")?.toString().trim() || null
  const external_url = formData.get("external_url")?.toString().trim() || null
  const cover_image_path = formData.get("cover_image_path")?.toString().trim() || null
  const published = formData.get("published") === "on"

  if (!isLogSpecies(species)) return { error: "Species is required.", values: {} as LogInsert }
  if (!isLogPlatform(platform)) return { error: "Platform is required.", values: {} as LogInsert }
  if (!isLogStatus(status)) return { error: "Status is required.", values: {} as LogInsert }
  if (!title_en) return { error: "Title (EN) is required.", values: {} as LogInsert }
  if (!summary_en) return { error: "Summary (EN) is required.", values: {} as LogInsert }

  return {
    values: {
      species,
      platform,
      status,
      title_en,
      title_fr,
      summary_en,
      summary_fr,
      body_md_en,
      body_md_fr,
      external_url,
      cover_image_path,
      published,
      published_at: published ? new Date().toISOString() : null,
    },
  }
}

export async function createLog(_: LogFormResult, formData: FormData): Promise<LogFormResult> {
  const parsed = readLogFields(formData)
  if (parsed.error) return { error: parsed.error }

  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase
    .from("logs")
    .insert(parsed.values)
    .select("id")
    .single()

  if (error || !data) {
    console.error("[logs/createLog] insert failed:", error?.message)
    return { error: "Could not create the log. Check the server console for details." }
  }

  revalidatePath("/logs")
  redirect(`/logs/${data.id}`)
}

export async function updateLog(
  id: string,
  _: LogFormResult,
  formData: FormData,
): Promise<LogFormResult> {
  const parsed = readLogFields(formData)
  if (parsed.error) return { error: parsed.error }

  const supabase = await createServerSupabaseClient()

  // Preserve published_at if the row was already published and stays published.
  // If newly published: stamp now(). If unpublished: clear it.
  let published_at: string | null = parsed.values.published_at ?? null
  if (parsed.values.published) {
    const { data: existing, error: readError } = await supabase
      .from("logs")
      .select("published, published_at")
      .eq("id", id)
      .single()
    if (readError) {
      console.error("[logs/updateLog] read existing failed:", readError.message)
      return { error: "Could not load the existing log to update." }
    }
    if (existing.published && existing.published_at) {
      published_at = existing.published_at
    } else {
      published_at = new Date().toISOString()
    }
  } else {
    published_at = null
  }

  const { error } = await supabase
    .from("logs")
    .update({ ...parsed.values, published_at })
    .eq("id", id)

  if (error) {
    console.error("[logs/updateLog] update failed:", error.message)
    return { error: "Could not save changes. Check the server console for details." }
  }

  revalidatePath("/logs")
  revalidatePath(`/logs/${id}`)
  return undefined
}

export async function deleteLog(id: string): Promise<void> {
  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.from("logs").delete().eq("id", id)

  if (error) {
    console.error("[logs/deleteLog] delete failed:", error.message)
    throw new Error("Could not delete the log.")
  }

  revalidatePath("/logs")
  redirect("/logs")
}
