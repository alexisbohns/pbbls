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
import { PromiseLetterForm } from "../_components/PromiseLetterForm"
import { DeletePromiseLetterButton } from "../_components/DeletePromiseLetterButton"
import { updatePromiseLetter } from "../actions"

type Params = Promise<{ id: string }>

export default async function EditPromiseLetterPage({ params }: { params: Params }) {
  const { id } = await params
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.from("promise_letters").select("*").eq("id", id).single()

  if (error || !data) {
    if (error) console.error("[promise/[id]] select failed:", error.message)
    notFound()
  }

  const letter = data
  const openingLine = letter.body_md_en.split("\n")[0].slice(0, 80)

  return (
    <section className="space-y-6">
      <Breadcrumb>
        <BreadcrumbList>
          <BreadcrumbItem>
            {/* Base UI BreadcrumbLink uses render prop instead of asChild */}
            <BreadcrumbLink render={<Link href="/promise" />}>Promise</BreadcrumbLink>
          </BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem>
            <BreadcrumbPage>{openingLine}</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>
      <header>
        <h1 className="text-2xl font-semibold">{openingLine}</h1>
        <p className="text-muted-foreground text-sm">
          {letter.published ? "Published" : "Draft"} · {letter.signed_by_en}
        </p>
      </header>
      <PromiseLetterForm
        letter={letter}
        action={updatePromiseLetter.bind(null, id)}
        submitLabel="Save changes"
        extraActions={<DeletePromiseLetterButton id={id} />}
      />
    </section>
  )
}
