"use client"

import Link from "next/link"
import { FileText, Trash2 } from "lucide-react"
import { useTranslations } from "next-intl"
import type { PebbleDraftRecord } from "@/lib/data/data-provider"
import { useSelectedEmotionDisplay } from "@/components/record/EmotionPicker"
import { ConfirmDialog } from "@/components/ui/ConfirmDialog"
import { Button } from "@/components/ui/button"
import { useFormatRelativeTime } from "@/lib/i18n"

type DraftRowProps = {
  draft: PebbleDraftRecord
  onDelete: (id: string) => void
}

export function DraftRow({ draft, onDelete }: DraftRowProps) {
  const t = useTranslations("drafts")
  const emotion = useSelectedEmotionDisplay(draft.payload.emotion_id)
  const formatRelative = useFormatRelativeTime()
  const savedAgo = formatRelative(draft.updated_at)

  return (
    <li className="flex items-center gap-3 rounded-xl border border-muted px-3 py-2.5 dark:border-accent">
      <Link
        href={`/record?draft=${draft.id}`}
        className="flex min-w-0 flex-1 items-center gap-3 rounded-lg outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        {/* A draft has no render_svg — there is nothing carved yet — so it gets
            a placeholder chip rather than a pebble visual. */}
        <span
          aria-hidden
          className="flex size-9 shrink-0 items-center justify-center rounded-lg border border-dashed border-muted-foreground/30 text-muted-foreground"
        >
          {emotion ? <span className="text-base">{emotion.emoji}</span> : <FileText className="size-4" />}
        </span>
        <span className="min-w-0 flex-1">
          <span
            className={
              draft.payload.name
                ? "block truncate font-medium text-foreground"
                : "block truncate font-medium italic text-muted-foreground"
            }
          >
            {draft.payload.name || t("untitled")}
          </span>
          <span className="block truncate text-xs text-muted-foreground">
            {t("savedAgo", { ago: savedAgo })}
          </span>
        </span>
      </Link>
      <ConfirmDialog
        trigger={
          <Button variant="ghost" size="icon" aria-label={t("deleteAria")}>
            <Trash2 className="size-4 text-muted-foreground" aria-hidden />
          </Button>
        }
        title={t("deleteTitle")}
        description={t("deleteDescription")}
        confirmLabel={t("deleteConfirm")}
        cancelLabel={t("deleteCancel")}
        onConfirm={() => onDelete(draft.id)}
      />
    </li>
  )
}
