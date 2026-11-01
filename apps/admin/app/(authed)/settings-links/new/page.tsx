import Link from "next/link"
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb"
import { SettingsLinkForm } from "../_components/SettingsLinkForm"
import { createSettingsLink } from "../actions"

export default function NewSettingsLinkPage() {
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
            <BreadcrumbPage>New link</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>
      <header>
        <h1 className="text-2xl font-semibold">New link</h1>
        <p className="text-muted-foreground text-sm">
          A row in the Settings screen of every app.
        </p>
      </header>
      <SettingsLinkForm link={null} action={createSettingsLink} submitLabel="Save" />
    </section>
  )
}
