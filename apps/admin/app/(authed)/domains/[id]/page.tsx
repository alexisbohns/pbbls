import Link from "next/link"
import { notFound } from "next/navigation"
import { ChevronLeft } from "lucide-react"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import type { AdminDomain } from "@/lib/domains/types"
import { DomainEditor } from "./_components/DomainEditor"

export default async function DomainEditorPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = await params
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.rpc("admin_list_domains")

  if (error) {
    console.error("[domains/[id]] admin_list_domains failed:", error.message)
  }
  const domain = ((data ?? []) as AdminDomain[]).find((d) => d.id === id)
  if (!domain) notFound()

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <Link
        href="/domains"
        className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
      >
        <ChevronLeft className="size-4" aria-hidden />
        All domains
      </Link>
      <DomainEditor domain={domain} />
    </div>
  )
}
