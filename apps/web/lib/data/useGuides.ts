"use client"

import { useAuth } from "@/lib/data/auth-context"
import { useKeptRead } from "@/lib/data/useKeptRead"
import type { Guide } from "@/lib/types"

const kept = new Map<string, Guide[]>()

/** The published guides in the reader's language (#1384 D14), for About Pebbles and the guide page. */
export function useGuides(locale: "en" | "fr") {
  const { user } = useAuth()
  return useKeptRead(kept, user ? `${user.id}|${locale}` : null, (p) => p.getGuides(locale), "guides")
}
