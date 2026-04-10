"use client"

import { Sun } from "lucide-react"
import { ThemeToggle } from "@/components/layout/ThemeToggle"

export function AppearanceSection() {
  return (
    <div className="divide-y divide-border rounded-xl border border-border">
      <div className="flex items-center gap-3 px-4 py-3">
        <Sun className="size-5 text-muted-foreground" aria-hidden />
        <span className="flex-1 text-sm font-medium">Appearance</span>
        <ThemeToggle />
      </div>
    </div>
  )
}
