"use client"

import { Suspense, useEffect, useState } from "react"
import { useRouter, useSearchParams } from "next/navigation"
import Link from "next/link"
import { ArrowLeft, Loader2 } from "lucide-react"
import { useTranslations } from "next-intl"
import { QuickPebbleEditor } from "@/components/path/QuickPebbleEditor"
import { useDataProvider } from "@/lib/data/provider-context"
import type { PebbleDraftPayload } from "@/lib/data/data-provider"

/**
 * `?draft=<id>` resumes a server draft (M47). Reading search params requires a
 * Suspense boundary in the App Router, hence the split.
 */
function RecordEditor() {
  const router = useRouter()
  const { provider } = useDataProvider()
  const draftId = useSearchParams().get("draft") ?? undefined

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

  return (
    <QuickPebbleEditor
      draftId={draftId}
      initialPayload={payload}
      onPebbleCreated={() => router.push("/path")}
      onDraftSaved={() => router.push("/drafts")}
    />
  )
}

export default function RecordPage() {
  const t = useTranslations("record")

  return (
    <section className="mx-auto w-full max-w-lg">
      <header className="mb-4 flex items-center gap-2">
        <Link
          href="/path"
          aria-label={t("back")}
          className="-ml-2 inline-flex size-9 items-center justify-center rounded-lg text-muted-foreground transition-colors hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <ArrowLeft className="size-5" aria-hidden />
        </Link>
        <h1 className="font-heading text-lg font-semibold">{t("srTitle")}</h1>
      </header>
      <Suspense fallback={null}>
        <RecordEditor />
      </Suspense>
    </section>
  )
}
