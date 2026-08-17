"use client"

import { useTranslations } from "next-intl"
import type { Visibility } from "@/lib/types"
import {
  VISIBILITY_GRADES,
  VisibilityMenu,
} from "@/components/record/VisibilityMenu"
import {
  Popover,
  PopoverTrigger,
  PopoverContent,
} from "@/components/ui/popover"

type VisibilityPickerProps = {
  value: Visibility
  onChange: (value: Visibility) => void
}

export function VisibilityPicker({ value, onChange }: VisibilityPickerProps) {
  const t = useTranslations("record.visibility")
  const CurrentIcon = (
    VISIBILITY_GRADES.find((g) => g.value === value) ?? VISIBILITY_GRADES[0]
  ).icon

  return (
    <Popover>
      <PopoverTrigger
        className="inline-flex items-center gap-1.5 rounded-md border border-border bg-background px-2.5 py-1 text-xs font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
        aria-label={t("aria", { grade: t(value) })}
      >
        <CurrentIcon className="size-3.5" aria-hidden />
        {t(value)}
      </PopoverTrigger>
      <PopoverContent align="start" className="min-w-[140px]">
        <VisibilityMenu value={value} onChange={onChange} />
      </PopoverContent>
    </Popover>
  )
}
