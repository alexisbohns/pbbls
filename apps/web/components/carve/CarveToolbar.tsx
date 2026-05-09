"use client"

import { Undo2, Trash2, Pen, Check } from "lucide-react"
import { useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"

type CarveToolbarProps = {
  strokeCount: number
  strokeWidth: number
  onUndo: () => void
  onClear: () => void
  onWidthChange: (width: number) => void
  onSave: () => void
  saving: boolean
  showSave?: boolean
}

const WIDTH_OPTIONS: ReadonlyArray<{ value: number; key: "thin" | "medium" | "thick" }> = [
  { value: 2, key: "thin" },
  { value: 4, key: "medium" },
  { value: 7, key: "thick" },
]

export function CarveToolbar({
  strokeCount,
  strokeWidth,
  onUndo,
  onClear,
  onWidthChange,
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

      <div className="mx-1 h-6 w-px bg-border" role="separator" />

      <div
        role="radiogroup"
        aria-label={t("widthLabel")}
        className="flex items-center gap-1"
      >
        {WIDTH_OPTIONS.map((opt) => (
          <Button
            key={opt.value}
            variant={strokeWidth === opt.value ? "secondary" : "ghost"}
            size="sm"
            role="radio"
            aria-checked={strokeWidth === opt.value}
            aria-label={t("widthAria", { label: t(opt.key) })}
            onClick={() => onWidthChange(opt.value)}
            className="h-8 px-2"
          >
            <Pen className="mr-1 h-3 w-3" />
            <span
              className="inline-block rounded-full bg-foreground"
              style={{ width: opt.value + 2, height: opt.value + 2 }}
              aria-hidden="true"
            />
          </Button>
        ))}
      </div>

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
