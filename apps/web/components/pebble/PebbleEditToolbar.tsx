"use client"

import { Loader2 } from "lucide-react"
import { useTranslations } from "next-intl"
import { cn } from "@/lib/utils"

type PebbleEditToolbarProps = {
  isSaving: boolean
  canSave: boolean
  onCancel: () => void
  onSave: () => void
}

// Cancel · EDITION · Save — sticky top bar for the Edit screen.
// Save is disabled when pristine, saving, or while a picture upload hasn't
// resolved (`canSave` carries the combined gate from the parent).
export function PebbleEditToolbar({
  isSaving,
  canSave,
  onCancel,
  onSave,
}: PebbleEditToolbarProps) {
  const t = useTranslations("pebble.edit")
  return (
    <header className="grid grid-cols-3 items-center py-2">
      <div className="justify-self-start">
        <button
          type="button"
          onClick={onCancel}
          disabled={isSaving}
          aria-label={t("cancelAria")}
          className={cn(
            "rounded-md px-2 py-1 text-base text-muted-foreground transition-colors",
            "hover:text-foreground active:scale-95 outline-none focus-visible:ring-2 focus-visible:ring-ring",
            "disabled:opacity-50 disabled:pointer-events-none",
          )}
        >
          {t("cancel")}
        </button>
      </div>
      <p className="justify-self-center text-xs font-medium uppercase tracking-[0.1em] text-muted-foreground">
        {isSaving ? t("saving") : t("toolbarTitle")}
      </p>
      <div className="justify-self-end">
        <button
          type="button"
          onClick={onSave}
          disabled={!canSave}
          aria-label={t("saveAria")}
          aria-busy={isSaving}
          className={cn(
            "inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-base transition-colors",
            "active:scale-95 outline-none focus-visible:ring-2 focus-visible:ring-ring",
            canSave
              ? "text-foreground hover:text-primary"
              : "text-muted-foreground/60 pointer-events-none",
          )}
        >
          {isSaving && <Loader2 className="size-4 animate-spin" aria-hidden />}
          {t("save")}
        </button>
      </div>
    </header>
  )
}
