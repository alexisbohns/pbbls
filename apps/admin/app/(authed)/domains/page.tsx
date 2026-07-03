import Link from "next/link"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import { GlyphPreview } from "@/components/pebblestore/GlyphPreview"
import type { AdminDomain } from "@/lib/domains/types"

export default async function DomainsPage() {
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.rpc("admin_list_domains")

  if (error) {
    console.error("[domains] admin_list_domains failed:", error.message)
  }
  const domains: AdminDomain[] = (data ?? []) as AdminDomain[]

  return (
    <section className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold">Domains</h1>
        <p className="text-sm text-muted-foreground">
          Edit a domain&rsquo;s name, description, and glyph. Names/descriptions are
          also localized in the app catalogs — edits here update the fallback.
        </p>
      </header>

      {error ? (
        <p className="text-sm text-destructive">
          Could not load domains. Check the server console.
        </p>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2">
          {domains.map((d) => (
            <li key={d.id}>
              <Link
                href={`/domains/${d.id}`}
                className="flex items-center gap-4 rounded-lg border p-4 transition-colors hover:bg-muted/50"
              >
                <span className="grid size-14 shrink-0 place-items-center rounded-md border bg-card text-foreground">
                  {d.strokes && d.strokes.length > 0 && d.view_box ? (
                    <GlyphPreview
                      strokes={d.strokes}
                      viewBox={d.view_box}
                      className="size-10"
                    />
                  ) : (
                    <span className="text-xs text-muted-foreground">—</span>
                  )}
                </span>
                <span className="min-w-0">
                  <span className="block font-medium">{d.name}</span>
                  <span className="block truncate text-sm text-muted-foreground">
                    {d.label || "No description"}
                  </span>
                  <span className="block text-xs text-muted-foreground/70">{d.slug}</span>
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
