"use client"

import { Suspense } from "react"
import { useRouter, useSearchParams } from "next/navigation"
import { QuickPebbleEditor } from "@/components/path/QuickPebbleEditor"
import { RecordFlow } from "@/components/record/flow/RecordFlow"

/**
 * `?composer=form` opens the single-screen composer instead of the flow. Two
 * composers is a cost accepted deliberately and temporarily: the flow is an
 * experiment in interaction model, and the honest way to evaluate it is to be
 * able to fall back without a redeploy. A query parameter was chosen over a
 * setting for the same reason iOS chose a long-press — it adds no chrome, no
 * persisted state and no localized string, and it deletes in one line when the
 * experiment resolves. Reading search params requires a Suspense boundary in
 * the App Router, hence the split.
 *
 * There is no `?draft=` any more: server drafts were retired by the moments
 * split (#1046, D7).
 */
function RecordEditor() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const wantsForm = searchParams.get("composer") === "form"

  if (wantsForm) {
    return <QuickPebbleEditor onPebbleCreated={() => router.push("/path")} />
  }

  return <RecordFlow onExit={() => router.push("/path")} />
}

export default function RecordPage() {
  return (
    <Suspense fallback={null}>
      <RecordEditor />
    </Suspense>
  )
}
