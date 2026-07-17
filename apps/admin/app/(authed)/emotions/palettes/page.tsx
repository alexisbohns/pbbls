import { createServerSupabaseClient } from "@/lib/supabase/server"
import type { AdminEmotionCategory } from "@/lib/emotions/types"
import { PaletteEditor } from "../_components/PaletteEditor"

export default async function PalettesPage() {
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.rpc("admin_list_emotion_categories")

  if (error) {
    console.error("[emotions] admin_list_emotion_categories failed:", error.message)
  }
  const categories = (data ?? []) as AdminEmotionCategory[]

  return (
    <section className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold">Palettes</h1>
        <p className="text-sm text-muted-foreground">
          Edit each emotion category&rsquo;s colour palette — primary, secondary, light, shaded and
          dark. Changes apply to every client (web, iOS, Android) reading the shared palette.
        </p>
      </header>

      {error ? (
        <p className="text-sm text-destructive">
          Could not load palettes. Check the server console.
        </p>
      ) : categories.length === 0 ? (
        <p className="text-sm text-muted-foreground">No emotion categories yet.</p>
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          {categories.map((c) => (
            <PaletteEditor key={c.id} category={c} />
          ))}
        </div>
      )}
    </section>
  )
}
