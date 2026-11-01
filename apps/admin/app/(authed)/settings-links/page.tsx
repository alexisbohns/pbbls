import Link from "next/link"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import { Button } from "@/components/ui/button"
import type { SettingsLinkRow } from "@/lib/settings-links/types"
import { SettingsLinkTable } from "./_components/SettingsLinkTable"

export default async function SettingsLinksPage() {
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase
    .from("settings_links")
    .select("*")
    .order("section")
    .order("sort_order")

  if (error) {
    console.error("[settings-links] select failed:", error.message)
  }

  const links: SettingsLinkRow[] = data ?? []

  return (
    <section className="space-y-6">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Settings links</h1>
          <p className="text-muted-foreground text-sm">
            The links every app shows in Settings, by section. Changes reach the apps on their next Settings visit.
          </p>
        </div>
        <Button render={<Link href="/settings-links/new" />}>New link</Button>
      </header>
      {error ? (
        <p role="alert" className="text-destructive text-sm">
          Couldn&rsquo;t load the links: {error.message}
        </p>
      ) : (
        <SettingsLinkTable links={links} />
      )}
    </section>
  )
}
