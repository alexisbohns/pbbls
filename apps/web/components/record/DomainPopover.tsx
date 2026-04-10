import { Compass } from "lucide-react"
import { DOMAINS } from "@/lib/config/domains"
import { SelectableItem } from "@/components/ui/SelectableItem"
import {
  Popover,
  PopoverTrigger,
  PopoverContent,
} from "@/components/ui/popover"
import { cn } from "@/lib/utils"

type DomainPopoverProps = {
  value: string[]
  onChange: (ids: string[]) => void
}

export function DomainPopover({ value, onChange }: DomainPopoverProps) {
  const selectedDomains = DOMAINS.filter((d) => value.includes(d.id))

  const toggle = (id: string) => {
    onChange(
      value.includes(id) ? value.filter((d) => d !== id) : [...value, id],
    )
  }

  return (
    <Popover>
      <PopoverTrigger
        className={cn(
          "inline-flex items-center gap-1.5 rounded-md px-2.5 py-1 text-xs font-medium transition-colors",
          value.length > 0
            ? "border border-border bg-background text-foreground"
            : "border border-dashed border-muted-foreground/30 text-muted-foreground hover:border-muted-foreground/50",
        )}
        aria-label={value.length > 0 ? `Domains: ${selectedDomains.map((d) => d.name).join(", ")}` : "Pick domains"}
      >
        <Compass className="size-3.5" aria-hidden />
        {selectedDomains.length > 0
          ? selectedDomains.map((d) => d.name).join(", ")
          : "Domain"}
      </PopoverTrigger>
      <PopoverContent align="start" className="min-w-[180px]">
        {DOMAINS.map((domain) => (
          <SelectableItem
            key={domain.id}
            selected={value.includes(domain.id)}
            onSelect={() => toggle(domain.id)}
          >
            <span className="flex flex-col items-start">
              <span>{domain.name}</span>
              <span className="text-xs text-muted-foreground">{domain.label}</span>
            </span>
          </SelectableItem>
        ))}
      </PopoverContent>
    </Popover>
  )
}
