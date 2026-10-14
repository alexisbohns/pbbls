"use client"

import { use } from "react"
import { CompleteFlow } from "@/components/record/complete/CompleteFlow"

/**
 * Complete for one moment (#1050). Under `/record` for the immersive chrome,
 * the auth gate and the store gating, with no layout edits. `?from=capture`
 * marks a flow chained from Capture, which changes where ✕ lands.
 */
export default function CompletePage({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>
  searchParams: Promise<{ from?: string | string[] }>
}) {
  const { id } = use(params)
  const { from } = use(searchParams)
  return <CompleteFlow momentId={id.toLowerCase()} from={from === "capture" ? "capture" : null} />
}
