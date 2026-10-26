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
import { GuideForm } from "../_components/GuideForm"
import { DeleteGuideButton } from "../_components/DeleteGuideButton"
import { updateGuide } from "../actions"

type Params = Promise<{ id: string }>

export default async function EditGuidePage({ params }: { params: Params }) {
  const { id } = await params
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.from("guides").select("*").eq("id", id).single()

  if (error || !data) {
    if (error) console.error("[guides/[id]] select failed:", error.message)
    notFound()
  }

  const guide = data

  return (
    <section className="space-y-6">
      <Breadcrumb>
        <BreadcrumbList>
          <BreadcrumbItem>
            {/* Base UI BreadcrumbLink uses render prop instead of asChild */}
            <BreadcrumbLink render={<Link href="/guides" />}>Guides</BreadcrumbLink>
          </BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem>
            <BreadcrumbPage>{guide.title_en}</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>
      <header>
        <h1 className="text-2xl font-semibold">{guide.title_en}</h1>
        <p className="text-muted-foreground text-sm">
          {guide.published ? "Published" : "Draft"} · {guide.slug}
        </p>
      </header>
      <GuideForm
        guide={guide}
        action={updateGuide.bind(null, id)}
        submitLabel="Save changes"
        extraActions={<DeleteGuideButton id={id} title={guide.title_en} />}
      />
    </section>
  )
}
