"use client"

import Link from "next/link"
import { FileText } from "lucide-react"
import { useTranslations } from "next-intl"
import { usePebbleDraftCount } from "@/lib/data/usePebbleDraftCount"

/**
 * Path entry point to /drafts, with a count. Renders nothing when there are no
 * drafts: an always-visible empty affordance would just be chrome, and quick
 * capture is reached from the composer, not from here.
 */
export function DraftsEntry() {
  const t = useTranslations("drafts")
  const count = usePebbleDraftCount()

  if (count === 0) return null

  return (
    <Link
      href="/drafts"
      className="inline-flex items-center gap-1.5 rounded-md border border-dashed border-muted-foreground/30 px-2.5 py-1 text-xs font-medium text-muted-foreground transition-colors hover:border-muted-foreground/50 hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
    >
      <FileText className="size-3.5" aria-hidden />
      {t("entry", { count })}
    </Link>
  )
}
