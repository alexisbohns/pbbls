import type { Collection } from "@/lib/types"
import { Badge } from "@/components/ui/badge"

export const MODE_META: Record<
  NonNullable<Collection["mode"]>,
  { emoji: string; label: string }
> = {
  stack: { emoji: "🎯", label: "Stack" },
  pack: { emoji: "📦", label: "Pack" },
  track: { emoji: "🔄", label: "Track" },
}

export function ModeBadge({ mode }: { mode: Collection["mode"] }) {
  if (!mode) return null

  const { emoji, label } = MODE_META[mode]

  return (
    <Badge variant="outline" aria-label={`Mode: ${label}`}>
      <span aria-hidden="true">{emoji}</span> {label}
    </Badge>
  )
}
