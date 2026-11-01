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
import { SettingsLinkForm } from "../_components/SettingsLinkForm"
import { DeleteSettingsLinkButton } from "../_components/DeleteSettingsLinkButton"
import { updateSettingsLink } from "../actions"

type Params = Promise<{ id: string }>

export default async function EditSettingsLinkPage({ params }: { params: Params }) {
  const { id } = await params
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.from("settings_links").select("*").eq("id", id).single()

  if (error || !data) {
    if (error) console.error("[settings-links/[id]] select failed:", error.message)
    notFound()
  }

  const link = data

  return (
    <section className="space-y-6">
      <Breadcrumb>
        <BreadcrumbList>
          <BreadcrumbItem>
            {/* Base UI BreadcrumbLink uses render prop instead of asChild */}
            <BreadcrumbLink render={<Link href="/settings-links" />}>Settings links</BreadcrumbLink>
          </BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem>
            <BreadcrumbPage>{link.title_en}</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>
      <header>
        <h1 className="text-2xl font-semibold">{link.title_en}</h1>
        <p className="text-muted-foreground text-sm">
          {link.published ? "Published" : "Draft"} · {link.key}
        </p>
      </header>
      <SettingsLinkForm
        link={link}
        action={updateSettingsLink.bind(null, id)}
        submitLabel="Save changes"
        extraActions={<DeleteSettingsLinkButton id={id} title={link.title_en} />}
      />
    </section>
  )
}
