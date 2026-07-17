import { createServerSupabaseClient } from "@/lib/supabase/server"
import type { AdminEmotion } from "@/lib/emotions/types"
import { EmojiRow } from "../_components/EmojiRow"

/** Group emotions under their category, preserving the RPC's ordering. */
function groupByCategory(emotions: AdminEmotion[]) {
  const byCategory = new Map<string, { name: string; primary: string; items: AdminEmotion[] }>()
  for (const e of emotions) {
    const bucket = byCategory.get(e.category_id)
    if (bucket) {
      bucket.items.push(e)
    } else {
      byCategory.set(e.category_id, {
        name: e.category_name,
        primary: e.category_primary_color,
        items: [e],
      })
    }
  }
  return [...byCategory.entries()].map(([id, g]) => ({ id, ...g }))
}

export default async function EmojisPage() {
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.rpc("admin_list_emotions")

  if (error) {
    console.error("[emotions] admin_list_emotions failed:", error.message)
  }
  const groups = groupByCategory((data ?? []) as AdminEmotion[])

  return (
    <section className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold">Emojis</h1>
        <p className="text-sm text-muted-foreground">
          Edit the emoji mapped to each emotion. Emotions are grouped by category.
        </p>
      </header>

      {error ? (
        <p className="text-sm text-destructive">
          Could not load emotions. Check the server console.
        </p>
      ) : groups.length === 0 ? (
        <p className="text-sm text-muted-foreground">No emotions yet.</p>
      ) : (
        <div className="space-y-8">
          {groups.map((g) => (
            <div key={g.id} className="space-y-3">
              <h2 className="flex items-center gap-2 text-sm font-semibold">
                <span
                  aria-hidden
                  className="size-3 rounded-full border"
                  style={{ backgroundColor: g.primary }}
                />
                {g.name}
              </h2>
              <ul className="grid gap-2 sm:grid-cols-2">
                {g.items.map((e) => (
                  <li key={e.id}>
                    <EmojiRow emotion={e} />
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}
