import Link from "next/link"
import { ChevronLeft } from "lucide-react"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import type { AdminAchievement } from "@/lib/achievements/types"
import type { AdminDomain } from "@/lib/domains/types"
import type { AdminEmotion } from "@/lib/emotions/types"
import { NewAchievementForm } from "./_components/NewAchievementForm"

export default async function NewAchievementPage() {
  const supabase = await createServerSupabaseClient()
  const [catalog, emotions, domains] = await Promise.all([
    supabase.rpc("admin_list_achievements"),
    supabase.rpc("admin_list_emotions"),
    supabase.rpc("admin_list_domains"),
  ])

  for (const [label, res] of [
    ["admin_list_achievements", catalog],
    ["admin_list_emotions", emotions],
    ["admin_list_domains", domains],
  ] as const) {
    if (res.error) console.error(`[achievements/new] ${label} failed:`, res.error.message)
  }

  const rows = (catalog.data ?? []) as AdminAchievement[]

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <Link
        href="/achievements"
        className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
      >
        <ChevronLeft className="size-4" aria-hidden />
        All achievements
      </Link>
      <NewAchievementForm
        existing={rows.map((a) => ({
          slug: a.slug,
          family: a.family,
          threshold: a.threshold,
          sortOrder: a.sort_order,
        }))}
        emotions={((emotions.data ?? []) as AdminEmotion[]).map((e) => ({
          id: e.id,
          slug: e.slug,
          name: e.name,
        }))}
        domains={((domains.data ?? []) as AdminDomain[]).map((d) => ({
          id: d.id,
          slug: d.slug,
          name: d.name,
        }))}
      />
    </div>
  )
}
