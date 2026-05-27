"use client"

import { useState } from "react"
import { Plus } from "lucide-react"
import { useTranslations } from "next-intl"
import type { Mark, Soul } from "@/lib/types"
import { SoulGlyphThumbnail } from "@/components/souls/SoulGlyphThumbnail"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import { cn } from "@/lib/utils"

type PebbleEditSoulsGridProps = {
  soulIds: string[]
  souls: Soul[]
  marks: Mark[]
  onAddRequest: () => void
  onRemove: (id: string) => void
  className?: string
}

// Souls grid in Edit mode: the existing selection plus a dashed `Add` tile.
// Tapping an existing soul opens a confirm dialog with Remove; tapping `Add`
// delegates to the parent (which opens `SoulsSheet`). Mirrors the iOS souls
// row in `PebbleFormView`.
export function PebbleEditSoulsGrid({
  soulIds,
  souls,
  marks,
  onAddRequest,
  onRemove,
  className,
}: PebbleEditSoulsGridProps) {
  const t = useTranslations("pebble.edit")
  const [confirmingId, setConfirmingId] = useState<string | null>(null)

  const matchedSouls = soulIds
    .map((id) => souls.find((s) => s.id === id))
    .filter((s): s is Soul => s !== undefined)

  const soulPendingRemoval =
    confirmingId !== null ? souls.find((s) => s.id === confirmingId) ?? null : null

  return (
    <>
      <ul className={cn("grid grid-cols-2 gap-3", className)} role="list">
        {matchedSouls.map((soul) => {
          const mark = marks.find((m) => m.id === soul.glyph_id)
          return (
            <li key={soul.id}>
              <button
                type="button"
                onClick={() => setConfirmingId(soul.id)}
                aria-label={t("removeSoulTitle")}
                className="flex w-full items-center gap-3 rounded-2xl border border-border/60 px-3 py-2.5 text-left transition-colors hover:bg-surface active:scale-[0.98] outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-muted/60">
                  <SoulGlyphThumbnail
                    mark={mark}
                    className="size-7 text-foreground"
                  />
                </span>
                <span className="line-clamp-1 text-sm font-medium text-foreground">
                  {soul.name}
                </span>
              </button>
            </li>
          )
        })}
        <li>
          <button
            type="button"
            onClick={onAddRequest}
            aria-label={t("addSoulAria")}
            className="flex w-full items-center gap-3 rounded-2xl border-2 border-dashed border-muted-foreground/30 px-3 py-2.5 text-left text-muted-foreground transition-colors hover:border-muted-foreground/60 hover:text-foreground active:scale-[0.98] outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <span className="grid size-10 shrink-0 place-items-center">
              <Plus className="size-5" aria-hidden />
            </span>
            <span className="text-xs font-medium uppercase tracking-[0.1em]">
              {t("addSoul")}
            </span>
          </button>
        </li>
      </ul>
      <AlertDialog
        open={confirmingId !== null}
        onOpenChange={(open) => {
          if (!open) setConfirmingId(null)
        }}
      >
        <AlertDialogContent size="sm">
          <AlertDialogHeader>
            <AlertDialogTitle>{t("removeSoulTitle")}</AlertDialogTitle>
            <AlertDialogDescription>
              {soulPendingRemoval
                ? t("removeSoulDescription", { name: soulPendingRemoval.name })
                : ""}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t("removeSoulCancel")}</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              onClick={() => {
                if (confirmingId) onRemove(confirmingId)
                setConfirmingId(null)
              }}
            >
              {t("removeSoulConfirm")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
