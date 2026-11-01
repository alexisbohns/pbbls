"use client"

import { use } from "react"
import { ConnectionDetail } from "@/components/connections/ConnectionDetail"

/** A connection, nested under Connections in the detail area (#1384, two-area spec D2). */
export default function ConnectionDetailPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = use(params)

  return <ConnectionDetail connectionId={id} />
}
