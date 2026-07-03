import type { ReactNode } from "react"
import { cn } from "@/lib/utils"

type SectionCardProps = {
  children: ReactNode
  className?: string
}

/**
 * Card chrome for Profile-screen sections (Stats, Collections, Lab) — the web
 * analog of the iOS `.profileCard()` modifier: clear background, 1pt border,
 * `rounded-2xl` (≈17pt) corners, and even padding. Defaults to a leading
 * vertical stack with a comfortable gap; override via `className`.
 */
export function SectionCard({ children, className }: SectionCardProps) {
  return (
    <div
      className={cn(
        "flex flex-col gap-4 rounded-2xl border border-border p-4",
        className,
      )}
    >
      {children}
    </div>
  )
}
