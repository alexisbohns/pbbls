import Link from "next/link"
import { Plus } from "lucide-react"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { GlyphPreview } from "@/components/pebblestore/GlyphPreview"
import {
  ACHIEVEMENT_FAMILIES,
  familyLabel,
  type AdminAchievement,
} from "@/lib/achievements/types"

/**
 * The achievements library. Rows are grouped by family in catalog `sort_order`
 * — the same order the apps render — so the ladder reads here the way users
 * see it. Inactive (retired) badges stay listed: the admin has to see what it
 * took out of circulation.
 */
export default async function AchievementsPage({
  searchParams,
}: {
  searchParams: Promise<{ family?: string }>
}) {
  const { family } = await searchParams
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.rpc("admin_list_achievements")

  if (error) {
    console.error("[achievements] admin_list_achievements failed:", error.message)
  }
  const all: AdminAchievement[] = (data ?? []) as AdminAchievement[]
  const rows = family ? all.filter((a) => a.family === family) : all

  // Families in catalog order, keeping only the ones that have rows.
  const families = ACHIEVEMENT_FAMILIES.filter((f) => rows.some((a) => a.family === f))

  return (
    <section className="space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Achievements</h1>
          <p className="text-sm text-muted-foreground">
            Edit a badge&rsquo;s copy, karma, order and visual, or add a new tier to an
            existing family. Karma edits apply to future unlocks only — nobody is
            paid again or clawed back.
          </p>
        </div>
        <Button size="sm" render={<Link href="/achievements/new" />}>
          <Plus aria-hidden />
          New tier
        </Button>
      </header>

      <nav className="flex flex-wrap gap-2">
        <Button
          size="sm"
          variant={family ? "outline" : "default"}
          render={<Link href="/achievements" />}
        >
          All ({all.length})
        </Button>
        {ACHIEVEMENT_FAMILIES.map((f) => {
          const count = all.filter((a) => a.family === f).length
          if (count === 0) return null
          return (
            <Button
              key={f}
              size="sm"
              variant={family === f ? "default" : "outline"}
              render={<Link href={`?family=${f}`} />}
            >
              {familyLabel(f)} ({count})
            </Button>
          )
        })}
      </nav>

      {error ? (
        <p className="text-sm text-destructive">
          Could not load achievements. Check the server console.
        </p>
      ) : (
        <div className="space-y-8">
          {families.map((f) => (
            <div key={f} className="space-y-3">
              <h2 className="text-sm font-semibold text-muted-foreground">
                {familyLabel(f)}
              </h2>
              <ul className="grid gap-3 sm:grid-cols-2">
                {rows
                  .filter((a) => a.family === f)
                  .map((a) => (
                    <li key={a.id}>
                      <Link
                        href={`/achievements/${a.id}`}
                        className="flex items-center gap-4 rounded-lg border p-4 transition-colors hover:bg-muted/50"
                      >
                        <span className="grid size-14 shrink-0 place-items-center rounded-md border bg-card text-foreground">
                          {a.strokes && a.strokes.length > 0 && a.view_box ? (
                            <GlyphPreview
                              strokes={a.strokes}
                              viewBox={a.view_box}
                              className="size-10"
                            />
                          ) : (
                            <span className="text-xs text-muted-foreground">—</span>
                          )}
                        </span>
                        <span className="min-w-0 flex-1">
                          <span className="flex items-center gap-2">
                            <span className="truncate font-medium">
                              {a.title_en ?? a.slug}
                            </span>
                            {a.is_active ? null : (
                              <Badge variant="outline">Retired</Badge>
                            )}
                          </span>
                          <span className="block truncate text-sm text-muted-foreground">
                            {a.threshold !== null ? `Threshold ${a.threshold} · ` : ""}
                            {a.karma_reward > 0
                              ? `${a.karma_reward} karma`
                              : "No karma"}
                            {" · "}
                            {a.unlock_count} unlocked
                          </span>
                          <span className="block text-xs text-muted-foreground/70">
                            {a.slug}
                          </span>
                        </span>
                      </Link>
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
