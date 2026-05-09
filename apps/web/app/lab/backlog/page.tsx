"use client"

import Link from "next/link"
import { ChevronLeft } from "lucide-react"
import { LabLogList } from "@/components/lab/LabLogList"
import { PageLayout } from "@/components/layout/PageLayout"

export default function LabBacklogPage() {
  return (
    <PageLayout>
      <section>
        <nav className="mb-6">
          <Link
            href="/lab"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ChevronLeft className="size-4" aria-hidden />
            Back to Lab
          </Link>
        </nav>
        <h1 className="mb-6 text-2xl font-semibold">Backlog</h1>
        <LabLogList mode="backlog" />
      </section>
    </PageLayout>
  )
}
