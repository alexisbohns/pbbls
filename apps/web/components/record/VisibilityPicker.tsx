import { Lock, Globe } from "lucide-react"
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
  return (
    <Popover>
      <PopoverTrigger
        className="inline-flex items-center gap-1.5 rounded-md border border-border bg-background px-2.5 py-1 text-xs font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
        aria-label={`Visibility: ${value}`}
      >
        {value === "private" ? (
          <Lock className="size-3.5" aria-hidden />
        ) : (
          <Globe className="size-3.5" aria-hidden />
        )}
        {value === "private" ? "Private" : "Public"}
      </PopoverTrigger>
      <PopoverContent align="start" className="min-w-[140px]">
        <SelectableItem
          selected={value === "private"}
          onSelect={() => onChange("private")}
        >
          <Lock className="size-4 shrink-0" />
          Private
        </SelectableItem>
        <SelectableItem
          selected={value === "public"}
          onSelect={() => onChange("public")}
        >
          <Globe className="size-4 shrink-0" />
          Public
        </SelectableItem>
      </PopoverContent>
    </Popover>
  )
}
