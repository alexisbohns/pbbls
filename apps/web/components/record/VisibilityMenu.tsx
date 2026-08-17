"use client"

import { Lock, Users, Globe } from "lucide-react"
import type { LucideIcon } from "lucide-react"
import { useTranslations } from "next-intl"
import type { Visibility } from "@/lib/types"
import { SelectableItem } from "@/components/ui/SelectableItem"

export const VISIBILITY_ICONS: Record<Visibility, LucideIcon> = {
  secret: Lock,
  private: Users,
  public: Globe,
}

// Order = disclosure order: each step down shares with more people.
const VISIBILITY_ORDER: Visibility[] = ["secret", "private", "public"]

export const VISIBILITY_GRADES: { value: Visibility; icon: LucideIcon }[] =
  VISIBILITY_ORDER.map((value) => ({ value, icon: VISIBILITY_ICONS[value] }))

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
