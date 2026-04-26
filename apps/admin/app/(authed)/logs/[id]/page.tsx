import Link from "next/link"
import { notFound } from "next/navigation"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb"
import { LogForm } from "../_components/LogForm"
import { DeleteLogButton } from "../_components/DeleteLogButton"
import { updateLog } from "../actions"

type Params = Promise<{ id: string }>

export default async function EditLogPage({ params }: { params: Params }) {
  const { id } = await params
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.from("logs").select("*").eq("id", id).single()

  if (error || !data) {
    if (error) console.error("[logs/[id]] select failed:", error.message)
    notFound()
  }

  const log = data
  const updateAction = updateLog.bind(null, id)

  return (
    <section className="space-y-6">
      <Breadcrumb>
        <BreadcrumbList>
          <BreadcrumbItem>
            {/* Base UI BreadcrumbLink uses render prop instead of asChild */}
            <BreadcrumbLink render={<Link href="/logs" />}>Logs</BreadcrumbLink>
          </BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem>
            <BreadcrumbPage>{log.title_en}</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>
      <header>
        <h1 className="text-2xl font-semibold">Edit log</h1>
        <p className="text-muted-foreground text-sm">
          {log.published ? "Published" : "Draft"} · {log.species} · {log.platform}
        </p>
      </header>
      <LogForm
        log={log}
        action={updateAction}
        submitLabel={log.published ? "Save changes" : "Save draft"}
        extraActions={<DeleteLogButton id={id} title={log.title_en} />}
      />
    </section>
  )
}
