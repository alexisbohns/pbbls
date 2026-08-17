"use client"

import { Lock, Users, Globe } from "lucide-react"
import type { LucideIcon } from "lucide-react"
import { useTranslations } from "next-intl"
import type { Visibility } from "@/lib/types"
import { SelectableItem } from "@/components/ui/SelectableItem"

// Order = disclosure order: each step down shares with more people.
export const VISIBILITY_GRADES: { value: Visibility; icon: LucideIcon }[] = [
  { value: "secret", icon: Lock },
  { value: "private", icon: Users },
  { value: "public", icon: Globe },
]

export function visibilityIcon(value: Visibility): LucideIcon {
  return (VISIBILITY_GRADES.find((g) => g.value === value) ?? VISIBILITY_GRADES[0]).icon
}

type VisibilityMenuProps = {
  value: Visibility
  onChange: (value: Visibility) => void
}

/**
 * The three-grade option list (M51), shared by the composer chip
 * (VisibilityPicker) and the detail badge popover (PebbleDetail) so the two
 * pickers can never drift. Render inside a PopoverContent.
 */
export function VisibilityMenu({ value, onChange }: VisibilityMenuProps) {
  const t = useTranslations("record.visibility")
  return (
    <>
      {VISIBILITY_GRADES.map(({ value: grade, icon: Icon }) => (
        <SelectableItem
          key={grade}
          selected={value === grade}
          onSelect={() => onChange(grade)}
        >
          <Icon className="size-4 shrink-0" />
          {t(grade)}
        </SelectableItem>
      ))}
    </>
  )
}
