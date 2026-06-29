"use client"

import { useTranslations } from "next-intl"
import { ArrowDownLeft, ArrowUpRight } from "lucide-react"
import type { KarmaEvent } from "@/lib/types"
import { signedAmount, direction, reasonKey } from "@/lib/utils/wallet-format"

export function WalletHistoryItem({ event }: { event: KarmaEvent }) {
  const t = useTranslations("wallet")
  const dir = direction(event.delta)
  const Icon = dir === "in" ? ArrowDownLeft : ArrowUpRight
  const date = new Date(event.created_at).toLocaleDateString()

  return (
    <li className="flex items-center justify-between gap-3 py-3">
      <div className="flex items-center gap-3">
        <Icon className="size-4 text-muted-foreground" aria-hidden />
        <div>
          <p className="text-sm font-medium">{t(`reason.${reasonKey(event.reason)}`)}</p>
          <p className="text-xs text-muted-foreground">{date}</p>
        </div>
      </div>
      <span className="text-sm font-semibold tabular-nums">{signedAmount(event.delta)}</span>
    </li>
  )
}
