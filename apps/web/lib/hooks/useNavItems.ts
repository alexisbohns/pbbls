"use client"

import { useTranslations } from "next-intl"
import { NAV_ITEMS, type NavItem } from "@/lib/config/navigation"

export type LocalizedNavItem = NavItem & { label: string }

/**
 * Returns the static nav items annotated with locale-aware labels.
 * Keep the static config import in sync with `messages.*.json` `nav.*`.
 */
export function useNavItems(): readonly LocalizedNavItem[] {
  const t = useTranslations("nav")
  return NAV_ITEMS.map((item) => ({ ...item, label: t(item.key) }))
}
