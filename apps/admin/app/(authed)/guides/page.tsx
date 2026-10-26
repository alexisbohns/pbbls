import Link from "next/link"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import { Button } from "@/components/ui/button"
import type { GuideRow } from "@/lib/guides/types"
import { GuideTable } from "./_components/GuideTable"

export default async function GuidesPage() {
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase
    .from("guides")
    .select("*")
    .order("sort_order")
    .order("slug")

  if (error) {
    console.error("[guides] select failed:", error.message)
  }

  const guides: GuideRow[] = data ?? []

  return (
    <section className="space-y-6">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Guides</h1>
          <p className="text-muted-foreground text-sm">
            The About Pebbles readings on the Home tab, in sort order.
          </p>
        </div>
        <Button render={<Link href="/guides/new" />}>New guide</Button>
      </header>
      <GuideTable guides={guides} />
    </section>
  )
}
