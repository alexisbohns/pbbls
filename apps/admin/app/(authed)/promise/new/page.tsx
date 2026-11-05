import Link from "next/link"
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb"
import { PromiseLetterForm } from "../_components/PromiseLetterForm"
import { createPromiseLetter } from "../actions"

export default function NewPromiseLetterPage() {
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
            <BreadcrumbPage>New letter</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>
      <header>
        <h1 className="text-2xl font-semibold">New letter</h1>
        <p className="text-muted-foreground text-sm">
          The letter every new account reads at the first onboarding step.
        </p>
      </header>
      <PromiseLetterForm letter={null} action={createPromiseLetter} submitLabel="Save" />
    </section>
  )
}
