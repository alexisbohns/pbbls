"use client"

import { useMemo } from "react"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import type { AdminEmotion, AdminEmotionCategory } from "@/lib/emotions/types"
import { PaletteEditor } from "./PaletteEditor"
import { EmojiRow } from "./EmojiRow"

export function EmotionsManager({
  categories,
  emotions,
}: {
  categories: AdminEmotionCategory[]
  emotions: AdminEmotion[]
}) {
  // Group emotions under their category for the Emojis tab, preserving the
  // RPC's (category name, emotion name) ordering.
  const grouped = useMemo(() => {
    const byCategory = new Map<
      string,
      { category: Pick<AdminEmotion, "category_id" | "category_name" | "category_primary_color">; items: AdminEmotion[] }
    >()
    for (const e of emotions) {
      const bucket = byCategory.get(e.category_id)
      if (bucket) {
        bucket.items.push(e)
      } else {
        byCategory.set(e.category_id, {
          category: {
            category_id: e.category_id,
            category_name: e.category_name,
            category_primary_color: e.category_primary_color,
          },
          items: [e],
        })
      }
    }
    return [...byCategory.values()]
  }, [emotions])

  return (
    <Tabs defaultValue="palettes" className="gap-6">
      <TabsList>
        <TabsTrigger value="palettes">Palettes</TabsTrigger>
        <TabsTrigger value="emojis">Emojis</TabsTrigger>
      </TabsList>

      <TabsContent value="palettes">
        {categories.length === 0 ? (
          <p className="text-sm text-muted-foreground">No emotion categories yet.</p>
        ) : (
          <div className="grid gap-4 lg:grid-cols-2">
            {categories.map((c) => (
              <PaletteEditor key={c.id} category={c} />
            ))}
          </div>
        )}
      </TabsContent>

      <TabsContent value="emojis">
        {grouped.length === 0 ? (
          <p className="text-sm text-muted-foreground">No emotions yet.</p>
        ) : (
          <div className="space-y-8">
            {grouped.map(({ category, items }) => (
              <div key={category.category_id} className="space-y-3">
                <h2 className="flex items-center gap-2 text-sm font-semibold">
                  <span
                    aria-hidden
                    className="size-3 rounded-full border"
                    style={{ backgroundColor: category.category_primary_color }}
                  />
                  {category.category_name}
                </h2>
                <ul className="grid gap-2 sm:grid-cols-2">
                  {items.map((e) => (
                    <li key={e.id}>
                      <EmojiRow emotion={e} />
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        )}
      </TabsContent>
    </Tabs>
  )
}
