"use client"

import { Lock, Users, Globe } from "lucide-react"
import type { LucideIcon } from "lucide-react"
import { useTranslations } from "next-intl"
import type { Visibility } from "@/lib/types"
import { SelectableItem } from "@/components/ui/SelectableItem"
import {
  Popover,
  PopoverTrigger,
  PopoverContent,
} from "@/components/ui/popover"

type VisibilityPickerProps = {
  value: Visibility
  onChange: (value: Visibility) => void
}

// Order = disclosure order: each step down shares with more people.
const GRADES: { value: Visibility; icon: LucideIcon }[] = [
  { value: "secret", icon: Lock },
  { value: "private", icon: Users },
  { value: "public", icon: Globe },
]

export function VisibilityPicker({ value, onChange }: VisibilityPickerProps) {
  const t = useTranslations("record.visibility")
  const current = GRADES.find((g) => g.value === value) ?? GRADES[0]
  const CurrentIcon = current.icon

  return (
    <Popover>
      <PopoverTrigger
        className="inline-flex items-center gap-1.5 rounded-md border border-border bg-background px-2.5 py-1 text-xs font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
        aria-label={t("aria", { grade: t(current.value) })}
      >
        <CurrentIcon className="size-3.5" aria-hidden />
        {t(current.value)}
      </PopoverTrigger>
      <PopoverContent align="start" className="min-w-[140px]">
        {GRADES.map(({ value: grade, icon: Icon }) => (
          <SelectableItem
            key={grade}
            selected={value === grade}
            onSelect={() => onChange(grade)}
          >
            <Icon className="size-4 shrink-0" />
            {t(grade)}
          </SelectableItem>
        ))}
      </PopoverContent>
    </Popover>
  )
}
