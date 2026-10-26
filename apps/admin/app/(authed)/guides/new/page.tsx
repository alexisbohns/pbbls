import Link from "next/link"
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb"
import { GuideForm } from "../_components/GuideForm"
import { createGuide } from "../actions"

export default function NewGuidePage() {
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
            <BreadcrumbPage>New guide</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>
      <header>
        <h1 className="text-2xl font-semibold">New guide</h1>
        <p className="text-muted-foreground text-sm">
          A short reading for the Home tab&apos;s About section.
        </p>
      </header>
      <GuideForm guide={null} action={createGuide} submitLabel="Save" />
    </section>
  )
}
