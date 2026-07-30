import Link from "next/link"
import { notFound } from "next/navigation"
import { ChevronLeft } from "lucide-react"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import type { AdminAchievement } from "@/lib/achievements/types"
import { AchievementEditor } from "./_components/AchievementEditor"

export default async function AchievementEditorPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = await params
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.rpc("admin_list_achievements")

  if (error) {
    console.error("[achievements/[id]] admin_list_achievements failed:", error.message)
  }
  const achievement = ((data ?? []) as AdminAchievement[]).find((a) => a.id === id)
  if (!achievement) notFound()

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <Link
        href="/achievements"
        className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
      >
        <ChevronLeft className="size-4" aria-hidden />
        All achievements
      </Link>
      <AchievementEditor achievement={achievement} />
    </div>
  )
}
