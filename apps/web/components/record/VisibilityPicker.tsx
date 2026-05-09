"use client"

import { Lock, Globe } from "lucide-react"
import { useTranslations } from "next-intl"
import { SelectableItem } from "@/components/ui/SelectableItem"
import {
  Popover,
  PopoverTrigger,
  PopoverContent,
} from "@/components/ui/popover"

type VisibilityPickerProps = {
  value: "private" | "public"
  onChange: (value: "private" | "public") => void
}

export function VisibilityPicker({ value, onChange }: VisibilityPickerProps) {
  const t = useTranslations("record.visibility")

  return (
    <Popover>
      <PopoverTrigger
        className="inline-flex items-center gap-1.5 rounded-md border border-border bg-background px-2.5 py-1 text-xs font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
        aria-label={value === "private" ? t("ariaPrivate") : t("ariaPublic")}
      >
        {value === "private" ? (
          <Lock className="size-3.5" aria-hidden />
        ) : (
          <Globe className="size-3.5" aria-hidden />
        )}
        {t(value)}
      </PopoverTrigger>
      <PopoverContent align="start" className="min-w-[140px]">
        <SelectableItem
          selected={value === "private"}
          onSelect={() => onChange("private")}
        >
          <Lock className="size-4 shrink-0" />
          {t("private")}
        </SelectableItem>
        <SelectableItem
          selected={value === "public"}
          onSelect={() => onChange("public")}
        >
          <Globe className="size-4 shrink-0" />
          {t("public")}
        </SelectableItem>
      </PopoverContent>
    </Popover>
  )
}
