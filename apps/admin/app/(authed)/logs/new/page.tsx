import Link from "next/link"
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb"
import { isLogSpecies } from "@/lib/logs/options"
import { LogForm } from "../_components/LogForm"
import { createLog } from "../actions"

type SearchParams = Promise<{ species?: string }>

export default async function NewLogPage({ searchParams }: { searchParams: SearchParams }) {
  const params = await searchParams
  const initialSpecies = isLogSpecies(params.species) ? params.species : undefined
  const breadcrumbHref = initialSpecies === "announcement" ? "/logs/announcements" : "/logs/features"
  const breadcrumbLabel = initialSpecies === "announcement" ? "Announcements" : "Features"

  return (
    <section className="space-y-6">
      <Breadcrumb>
        <BreadcrumbList>
          <BreadcrumbItem>
            {/* Base UI BreadcrumbLink uses render prop instead of asChild */}
            <BreadcrumbLink render={<Link href={breadcrumbHref} />}>{breadcrumbLabel}</BreadcrumbLink>
          </BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem>
            <BreadcrumbPage>New log</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>
      <header>
        <h1 className="text-2xl font-semibold">New log</h1>
        <p className="text-muted-foreground text-sm">Create a changelog entry or announcement.</p>
      </header>
      <LogForm log={null} action={createLog} submitLabel="Save draft" initialSpecies={initialSpecies} />
    </section>
  )
}
