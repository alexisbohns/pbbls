"use client"

import { Undo2, Trash2, Check } from "lucide-react"
import { useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"

type CarveToolbarProps = {
  strokeCount: number
  onUndo: () => void
  onClear: () => void
  onSave: () => void
  saving: boolean
  showSave?: boolean
}

export function CarveToolbar({
  strokeCount,
  onUndo,
  onClear,
  onSave,
  saving,
  showSave = true,
}: CarveToolbarProps) {
  const t = useTranslations("carve.toolbar")

  return (
    <nav
      className="flex flex-wrap items-center gap-2"
      aria-label={t("ariaLabel")}
    >
      <Button
        variant="ghost"
        size="icon"
        onClick={onUndo}
        disabled={strokeCount === 0}
        aria-label={t("undo")}
      >
        <Undo2 className="h-4 w-4" />
      </Button>

      <Button
        variant="ghost"
        size="icon"
        onClick={onClear}
        disabled={strokeCount === 0}
        aria-label={t("clear")}
      >
        <Trash2 className="h-4 w-4" />
      </Button>

      {showSave && (
        <>
          <div className="flex-1" />

          <Button
            onClick={onSave}
            disabled={strokeCount === 0 || saving}
            aria-label={t("saveAria")}
            className="h-11 px-4 md:h-8 md:px-2.5"
          >
            <Check className="mr-1.5 h-4 w-4" />
            {saving ? t("saving") : t("save")}
          </Button>
        </>
      )}
    </nav>
  )
}
