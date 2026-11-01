"use client"

import { ListItem } from "@/components/m3/ListItem"
import { useTranslations } from "next-intl"
import { ArrowDownLeft, ArrowUpRight } from "lucide-react"
import type { KarmaEvent } from "@/lib/types"
import { signedAmount, direction, reasonKey } from "@/lib/utils/wallet-format"

/** One karma movement as an M3 list item (#1384, two-area spec D6); the amount set in Ysabeau's lining figures. */
export function WalletHistoryItem({ event }: { event: KarmaEvent }) {
  const t = useTranslations("wallet")
  const dir = direction(event.delta)
  const Icon = dir === "in" ? ArrowDownLeft : ArrowUpRight
  const date = new Date(event.created_at).toLocaleDateString()

  return (
    <ListItem
      leading={<Icon className="size-5" aria-hidden />}
      headline={t(`reason.${reasonKey(event.reason)}`)}
      supporting={date}
      trailing={<span className="pb-type-figure-small not-italic text-m3-on-surface">{signedAmount(event.delta)}</span>}
    />
  )
}
