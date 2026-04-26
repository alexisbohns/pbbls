import Link from "next/link"
import { Plus } from "lucide-react"
import { buttonVariants } from "@/components/ui/button"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import { isLogSpecies, isLogStatus } from "@/lib/logs/options"
import type { LogRow } from "@/lib/logs/types"
import { LogsTable } from "./_components/LogsTable"
import { LogsFilters } from "./_components/LogsFilters"

type SearchParams = Promise<{ species?: string; status?: string; published?: string }>

export default async function LogsPage({ searchParams }: { searchParams: SearchParams }) {
  const params = await searchParams
  const supabase = await createServerSupabaseClient()

  let query = supabase
    .from("logs")
    .select("*")
    .order("updated_at", { ascending: false })

  if (isLogSpecies(params.species)) query = query.eq("species", params.species)
  if (isLogStatus(params.status)) query = query.eq("status", params.status)
  if (params.published === "true") query = query.eq("published", true)
  if (params.published === "false") query = query.eq("published", false)

  const { data, error } = await query

  if (error) {
    console.error("[logs/page] select failed:", error.message)
  }

  const logs: LogRow[] = data ?? []

  return (
    <section className="space-y-6">
      <header className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Logs</h1>
        <Link href="/logs/new" className={buttonVariants()}>
          <Plus className="size-4" aria-hidden />
          New log
        </Link>
      </header>
      <LogsFilters />
      <LogsTable logs={logs} />
    </section>
  )
}
