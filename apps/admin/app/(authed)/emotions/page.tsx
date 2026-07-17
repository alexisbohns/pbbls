import { createServerSupabaseClient } from "@/lib/supabase/server"
import type { AdminEmotion, AdminEmotionCategory } from "@/lib/emotions/types"
import { EmotionsManager } from "./_components/EmotionsManager"

export default async function EmotionsPage() {
  const supabase = await createServerSupabaseClient()

  const [categoriesRes, emotionsRes] = await Promise.all([
    supabase.rpc("admin_list_emotion_categories"),
    supabase.rpc("admin_list_emotions"),
  ])

  if (categoriesRes.error) {
    console.error("[emotions] admin_list_emotion_categories failed:", categoriesRes.error.message)
  }
  if (emotionsRes.error) {
    console.error("[emotions] admin_list_emotions failed:", emotionsRes.error.message)
  }

  const categories = (categoriesRes.data ?? []) as AdminEmotionCategory[]
  const emotions = (emotionsRes.data ?? []) as AdminEmotion[]
  const loadError = Boolean(categoriesRes.error || emotionsRes.error)

  return (
    <section className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold">Emotions</h1>
        <p className="text-sm text-muted-foreground">
          Manage each emotion category&rsquo;s colour palette and each emotion&rsquo;s emoji. Changes
          apply to every client (web, iOS, Android) reading the shared palette.
        </p>
      </header>

      {loadError ? (
        <p className="text-sm text-destructive">
          Could not load emotions. Check the server console.
        </p>
      ) : (
        <EmotionsManager categories={categories} emotions={emotions} />
      )}
    </section>
  )
}
