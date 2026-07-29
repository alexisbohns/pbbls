"use client"

import { useCallback } from "react"
import { Loader2 } from "lucide-react"
import { useTranslations } from "next-intl"
import { usePebbleDrafts } from "@/lib/data/usePebbleDrafts"
import { DraftRow } from "@/components/drafts/DraftRow"

/**
 * Drafts get their own surface rather than a section inside /path (D4): the
 * timeline is a happened_at-ordered history of real pebbles, and its week
 * grouping, ripple, bounce and stats all assume exactly that.
 */
export function DraftsList() {
  const t = useTranslations("drafts")
  const { drafts, loading, error, removeDraft } = usePebbleDrafts()

  const handleDelete = useCallback(
    (id: string) => {
      void removeDraft(id).catch((err: unknown) => {
        console.error("[drafts] delete failed", err)
      })
    },
    [removeDraft],
  )

  if (loading) {
    return (
      <div className="flex justify-center py-12" aria-live="polite">
        <Loader2 className="size-5 animate-spin text-muted-foreground" aria-label={t("loading")} />
      </div>
    )
  }

  if (error) {
    return (
      <p role="alert" className="py-12 text-center text-sm text-destructive">
        {t("loadError")}
      </p>
    )
  }

  if (drafts.length === 0) {
    return <p className="py-12 text-center text-sm text-muted-foreground">{t("empty")}</p>
  }

  return (
    <ul className="flex flex-col gap-2">
      {drafts.map((draft) => (
        <DraftRow key={draft.id} draft={draft} onDelete={handleDelete} />
      ))}
    </ul>
  )
}
