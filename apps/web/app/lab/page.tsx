"use client"

import { LabFeed } from "@/components/lab/LabFeed"
import { PageLayout } from "@/components/layout/PageLayout"

export default function LabPage() {
  return (
    <PageLayout>
      <section>
        <div className="mb-6 flex items-center justify-between">
          <h1 className="text-2xl font-semibold">Lab</h1>
        </div>
        <LabFeed />
      </section>
    </PageLayout>
  )
}
