"use client"

import { ArrowUpCircle } from "lucide-react"
import { useTranslations } from "next-intl"
import { cn } from "@/lib/utils"

type ReactionButtonProps = {
  count: number
  isReacted: boolean
  onToggle: () => void
  disabled?: boolean
}

// Tap target that toggles the viewer's upvote on a backlog item.
// Purely visual — the parent owns the state and performs the write.
// Mirrors apps/ios/Pebbles/Features/Lab/Components/ReactionButton.swift.
export function ReactionButton({ count, isReacted, onToggle, disabled }: ReactionButtonProps) {
  const t = useTranslations("lab.reaction")
  return (
    <button
      type="button"
      onClick={onToggle}
      disabled={disabled}
      aria-label={isReacted ? t("removeUpvoteAria") : t("upvoteAria")}
      aria-pressed={isReacted}
      className={cn(
        "inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium transition-colors",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
        "disabled:opacity-50 disabled:cursor-not-allowed",
        isReacted
          ? "text-primary"
          : "text-muted-foreground hover:text-foreground",
      )}
    >
      <ArrowUpCircle
        className={cn("size-4", isReacted && "fill-primary/15")}
        aria-hidden
      />
      <span className="tabular-nums">{count}</span>
    </button>
  )
}
