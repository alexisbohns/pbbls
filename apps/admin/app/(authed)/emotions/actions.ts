"use server"

import { revalidatePath } from "next/cache"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import { emotionErrorMessage } from "@/lib/emotions/types"

export type ActionResult = { error: string } | undefined

const PALETTES_PATH = "/emotions/palettes"
const EMOJIS_PATH = "/emotions/emojis"

export async function updateEmotionPalette(input: {
  id: string
  primary: string
  secondary: string
  light: string
  shaded: string
  dark: string
}): Promise<ActionResult> {
  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.rpc("admin_update_emotion_palette", {
    p_category_id: input.id,
    p_primary: input.primary,
    p_secondary: input.secondary,
    p_light: input.light,
    p_shaded: input.shaded,
    p_dark: input.dark,
  })
  if (error) {
    console.error("[emotions] updateEmotionPalette failed:", error.message)
    return { error: emotionErrorMessage(error.message) }
  }
  revalidatePath(PALETTES_PATH)
  return undefined
}

export async function updateEmotionEmoji(input: {
  id: string
  emoji: string
}): Promise<ActionResult> {
  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.rpc("admin_update_emotion_emoji", {
    p_emotion_id: input.id,
    p_emoji: input.emoji,
  })
  if (error) {
    console.error("[emotions] updateEmotionEmoji failed:", error.message)
    return { error: emotionErrorMessage(error.message) }
  }
  revalidatePath(EMOJIS_PATH)
  return undefined
}
