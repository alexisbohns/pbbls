"use client"

import { use } from "react"
import { PageLayout } from "@/components/layout/PageLayout"
import { ConnectionDetail } from "@/components/connections/ConnectionDetail"

export default function ConnectionDetailPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = use(params)

  return (
    <PageLayout>
      <ConnectionDetail connectionId={id} />
    </PageLayout>
  )
}
