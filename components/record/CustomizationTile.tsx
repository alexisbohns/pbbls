import type { LucideIcon } from "lucide-react"
import { cn } from "@/lib/utils"

type CustomizationTileProps = {
  icon: LucideIcon
  filled: boolean
  onClick: () => void
  ariaLabel: string
  children?: React.ReactNode
}

export function CustomizationTile({
  icon: Icon,
  filled,
  onClick,
  ariaLabel,
  children,
}: CustomizationTileProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={ariaLabel}
      className={cn(
        "relative flex aspect-square items-center justify-center rounded-xl transition-all duration-100 outline-none focus-visible:ring-2 focus-visible:ring-ring active:scale-95 overflow-hidden",
        filled
          ? "border border-border bg-muted/50"
          : "border border-dashed border-muted-foreground/30 hover:border-muted-foreground/50 hover:bg-muted/30",
      )}
    >
      {filled && children ? (
        children
      ) : (
        <Icon
          className="size-5 text-muted-foreground/50"
          aria-hidden
        />
      )}
    </button>
  )
}
