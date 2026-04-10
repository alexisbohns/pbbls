import type { ReactNode } from "react"
import { Check } from "lucide-react"
import { cn } from "@/lib/utils"

type SelectableItemProps = {
  selected: boolean
  onSelect: () => void
  children: ReactNode
  role?: "option" | "radio" | "menuitemradio"
  className?: string
}

export function SelectableItem({
  selected,
  onSelect,
  children,
  role,
  className,
}: SelectableItemProps) {
  const ariaProps = role === "option"
    ? { role: "option" as const, "aria-selected": selected }
    : role === "radio"
      ? { role: "radio" as const, "aria-checked": selected }
      : role === "menuitemradio"
        ? { role: "menuitemradio" as const, "aria-checked": selected }
        : { "aria-pressed": selected }

  return (
    <button
      type="button"
      onClick={onSelect}
      className={cn(
        "flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-sm outline-none transition-colors hover:bg-muted",
        selected && "font-medium",
        className,
      )}
      {...ariaProps}
    >
      <span className="flex size-4 shrink-0 items-center justify-center">
        {selected && <Check className="size-4" />}
      </span>
      {children}
    </button>
  )
}
