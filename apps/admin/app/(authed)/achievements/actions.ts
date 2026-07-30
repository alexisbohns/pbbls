"use server"

import { revalidatePath } from "next/cache"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import { achievementErrorMessage } from "@/lib/achievements/types"
import type { GlyphStroke } from "@/lib/pebblestore/types"

export type ActionResult = { error: string } | undefined
export type CreateResult = { error: string } | { id: string }

const LIST_PATH = "/achievements"

export async function createAchievement(input: {
  slug: string
  family: string
  threshold: number | null
  emotionId: string | null
  domainId: string | null
  titleEn: string
  titleFr: string
  descriptionEn: string
  descriptionFr: string
  karmaReward: number
  sortOrder: number
}): Promise<CreateResult> {
  const supabase = await createServerSupabaseClient()
  // The per-family arguments default to null in SQL, so an omitted key is how a
  // family that has no threshold (or no reference row) says so.
  const { data, error } = await supabase.rpc("admin_create_achievement", {
    p_slug: input.slug,
    p_family: input.family,
    p_title_en: input.titleEn,
    p_title_fr: input.titleFr,
    p_karma_reward: input.karmaReward,
    p_sort_order: input.sortOrder,
    p_threshold: input.threshold ?? undefined,
    p_emotion_id: input.emotionId ?? undefined,
    p_domain_id: input.domainId ?? undefined,
    p_description_en: input.descriptionEn,
    p_description_fr: input.descriptionFr,
  })
  if (error) {
    console.error("[achievements] createAchievement failed:", error.message)
    return { error: achievementErrorMessage(error.message) }
  }
  revalidatePath(LIST_PATH)
  return { id: data as unknown as string }
}

export async function updateAchievement(input: {
  id: string
  titleEn: string
  titleFr: string
  descriptionEn: string
  descriptionFr: string
  karmaReward: number
  sortOrder: number
  isActive: boolean
}): Promise<ActionResult> {
  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.rpc("admin_update_achievement", {
    p_achievement_id: input.id,
    p_title_en: input.titleEn,
    p_title_fr: input.titleFr,
    p_karma_reward: input.karmaReward,
    p_description_en: input.descriptionEn,
    p_description_fr: input.descriptionFr,
    p_sort_order: input.sortOrder,
    p_is_active: input.isActive,
  })
  if (error) {
    console.error("[achievements] updateAchievement failed:", error.message)
    return { error: achievementErrorMessage(error.message) }
  }
  revalidatePath(LIST_PATH)
  revalidatePath(`${LIST_PATH}/${input.id}`)
  return undefined
}

export async function setAchievementGlyph(input: {
  id: string
  strokes: GlyphStroke[]
  viewBox: string
}): Promise<ActionResult> {
  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.rpc("admin_set_achievement_glyph", {
    p_achievement_id: input.id,
    p_strokes: input.strokes as unknown as never, // jsonb
    p_view_box: input.viewBox,
  })
  if (error) {
    console.error("[achievements] setAchievementGlyph failed:", error.message)
    return { error: achievementErrorMessage(error.message) }
  }
  revalidatePath(LIST_PATH)
  revalidatePath(`${LIST_PATH}/${input.id}`)
  return undefined
}

export async function deleteAchievement(id: string): Promise<ActionResult> {
  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.rpc("admin_delete_achievement", {
    p_achievement_id: id,
  })
  if (error) {
    console.error("[achievements] deleteAchievement failed:", error.message)
    return { error: achievementErrorMessage(error.message) }
  }
  revalidatePath(LIST_PATH)
  return undefined
}
