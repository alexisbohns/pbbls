import { Layers } from "lucide-react"
import type { Collection } from "@/lib/types"
import { SelectableItem } from "@/components/ui/SelectableItem"
import {
  Popover,
  PopoverTrigger,
  PopoverContent,
} from "@/components/ui/popover"
import { cn } from "@/lib/utils"

type CollectionPopoverProps = {
  value: string[]
  onChange: (ids: string[]) => void
  collections: Collection[]
  variant?: "tile" | "chip"
}

export function CollectionPopover({ value, onChange, collections, variant = "tile" }: CollectionPopoverProps) {
  const selectedNames = collections
    .filter((c) => value.includes(c.id))
    .map((c) => c.name)

  const toggle = (id: string) => {
    onChange(
      value.includes(id) ? value.filter((c) => c !== id) : [...value, id],
    )
  }

  const trigger =
    variant === "chip" ? (
      <PopoverTrigger
        className={cn(
          "inline-flex items-center gap-1.5 rounded-md px-2.5 py-1 text-xs font-medium transition-colors",
          value.length > 0
            ? "border border-border bg-background text-foreground"
            : "border border-dashed border-muted-foreground/30 text-muted-foreground hover:border-muted-foreground/50",
        )}
        aria-label={value.length > 0 ? `Collections: ${selectedNames.join(", ")}` : "Pick collections"}
      >
        <Layers className="size-3.5" aria-hidden />
        {selectedNames.length > 0 ? selectedNames.join(", ") : "Collection"}
      </PopoverTrigger>
    ) : (
      <PopoverTrigger
        className={cn(
          "relative flex aspect-square items-center justify-center rounded-xl transition-all duration-100 outline-none focus-visible:ring-2 focus-visible:ring-ring active:scale-95 overflow-hidden",
          value.length > 0
            ? "border border-border bg-muted/50"
            : "border border-dashed border-muted-foreground/30 hover:border-muted-foreground/50 hover:bg-muted/30",
        )}
        aria-label={value.length > 0 ? `${value.length} collection(s) selected` : "Add to collection"}
      >
        {value.length > 0 ? (
          <span className="text-xs font-medium text-muted-foreground">
            {value.length}
          </span>
        ) : (
          <Layers className="size-5 text-muted-foreground/50" aria-hidden />
        )}
      </PopoverTrigger>
    )

  return (
    <Popover>
      {trigger}
      <PopoverContent align="start" className="min-w-[180px]">
        {collections.length === 0 ? (
          <p className="px-2 py-4 text-center text-sm text-muted-foreground">
            No collections yet
          </p>
        ) : (
          collections.map((coll) => (
            <SelectableItem
              key={coll.id}
              selected={value.includes(coll.id)}
              onSelect={() => toggle(coll.id)}
            >
              {coll.name}
            </SelectableItem>
          ))
        )}
      </PopoverContent>
    </Popover>
  )
}
