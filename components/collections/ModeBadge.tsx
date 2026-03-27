import type { Collection } from "@/lib/types"

const MODE_META: Record<
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
    <span
      className="rounded-full border border-border px-2 py-0.5 text-xs font-medium"
      aria-label={`Mode: ${label}`}
    >
      <span aria-hidden="true">{emoji}</span> {label}
    </span>
  )
}
