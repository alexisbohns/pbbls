"use client"

import { Suspense, useEffect, useState } from "react"
import { useRouter, useSearchParams } from "next/navigation"
import { Loader2 } from "lucide-react"
import { QuickPebbleEditor } from "@/components/path/QuickPebbleEditor"
import { RecordFlow } from "@/components/record/flow/RecordFlow"
import { useDataProvider } from "@/lib/data/provider-context"
import type { PebbleDraftPayload } from "@/lib/data/data-provider"

/**
 * `?draft=<id>` resumes a server draft (M47). Reading search params requires a
 * Suspense boundary in the App Router, hence the split.
 *
 * `?composer=form` opens the single-screen composer instead of the flow. Two
 * composers is a cost accepted deliberately and temporarily: the flow is an
 * experiment in interaction model, and the honest way to evaluate it is to be
 * able to fall back without a redeploy. A query parameter was chosen over a
 * setting for the same reason iOS chose a long-press — it adds no chrome, no
 * persisted state and no localized string, and it deletes in one line when the
 * experiment resolves.
 */
function RecordEditor() {
  const router = useRouter()
  const { provider } = useDataProvider()
  const searchParams = useSearchParams()
  const draftId = searchParams.get("draft") ?? undefined
  const wantsForm = searchParams.get("composer") === "form"

  const [payload, setPayload] = useState<PebbleDraftPayload | undefined>(undefined)

  // Derived, not stored: a `loadingDraft` state variable had to be cleared from
  // inside the effect for every early return, and the branch where `provider` is
  // still null had no such return — so the spinner ran forever.
  const loadingDraft = draftId !== undefined && provider !== null && payload === undefined

  useEffect(() => {
    if (!draftId || !provider) return
    let cancelled = false
    void (async () => {
      await Promise.resolve()
      if (cancelled) return
      try {
        const draft = await provider.getPebbleDraft(draftId)
        if (cancelled) return
        // A draft deleted from another tab/device just opens a blank composer;
        // there is nothing useful to say about it. `{}` also resolves the
        // spinner, which `undefined` would not.
        setPayload(draft?.payload ?? {})
      } catch (err) {
        if (cancelled) return
        console.error("[record] failed to load draft", err)
        setPayload({})
      }
    })()
    return () => {
      cancelled = true
    }
  }, [draftId, provider])

  if (loadingDraft) {
    return (
      <div className="flex justify-center py-12" aria-live="polite">
        <Loader2 className="size-5 animate-spin text-muted-foreground" aria-hidden />
      </div>
    )
  }

  if (wantsForm) {
    return (
      <QuickPebbleEditor
        draftId={draftId}
        initialPayload={payload}
        onPebbleCreated={() => router.push("/path")}
        onDraftSaved={() => router.push("/drafts")}
      />
    )
  }

  return (
    <RecordFlow
      draftId={draftId}
      initialPayload={payload}
      onExit={() => router.push("/path")}
      onDraftSaved={() => router.push("/drafts")}
    />
  )
}

export default function RecordPage() {
  return (
    <Suspense fallback={null}>
      <RecordEditor />
    </Suspense>
  )
}
