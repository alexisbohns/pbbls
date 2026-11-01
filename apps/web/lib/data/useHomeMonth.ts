"use client"

import { useAuth } from "@/lib/data/auth-context"
import { useKeptRead } from "@/lib/data/useKeptRead"
import { currentMonthStart, readerTimeZone } from "@/lib/home/timezone"
import type { HomeMonth } from "@/lib/types"

const kept = new Map<string, HomeMonth | null>()

/** The current month as Home's grid reads it: `path_month_wrap` for this month in the reader's zone (#1384 D10). */
export function useHomeMonth() {
  const { user } = useAuth()
  const tz = readerTimeZone()
  const month = currentMonthStart(tz)
  return useKeptRead(kept, user ? `${user.id}|${month}|${tz}` : null, (p) => p.getHomeMonth(month, tz), "path_month_wrap")
}
