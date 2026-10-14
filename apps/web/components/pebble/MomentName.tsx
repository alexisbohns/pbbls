"use client"

import type { ElementType } from "react"
import { useTranslations } from "next-intl"
import { momentDisplayName } from "@/lib/data/moment-row"
import { cn } from "@/lib/utils"

type MomentNameProps = {
  name: string | null
  className?: string
  /** The element to render, `span` by default (`h1` on the read page). */
  as?: ElementType
}

/**
 * A moment's name, or "Untitled" in italic secondary text when it has none
 * (R3). The name is user-authored and never localized.
 */
export function MomentName({ name, className, as: Tag = "span" }: MomentNameProps) {
  const t = useTranslations("moment")
  const display = momentDisplayName(name)
  if (display) return <Tag className={className}>{display}</Tag>
  return <Tag className={cn(className, "italic text-muted-foreground")}>{t("untitled")}</Tag>
}
