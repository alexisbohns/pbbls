"use client"

import { useRouter } from "next/navigation"
import { useTranslations } from "next-intl"
import { Sparkle } from "lucide-react"
import { toast } from "sonner"
import type { KarmaReason } from "@/lib/types"

type KarmaActivityPillProps = {
  toastId: string | number
  amount: number
  reason: KarmaReason
}

export function KarmaActivityPill({ toastId, amount, reason }: KarmaActivityPillProps) {
  const router = useRouter()
  const t = useTranslations("activity")
  const tReason = useTranslations("wallet.reason")

  const handleTap = () => {
    toast.dismiss(toastId)
    router.push("/wallet")
  }

  // Full spoken sentence: "Earned 5 karma — Pebble created. View wallet."
  const label = `${t("srEarned", { amount, reason: tReason(reason) })} ${t("viewWallet")}.`

  return (
    <button
      type="button"
      onClick={handleTap}
      aria-label={label}
      className="flex items-center gap-2 rounded-full bg-neutral-900 px-4 py-2 text-sm font-semibold text-white shadow-lg ring-1 ring-white/10 transition active:scale-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-300 focus-visible:ring-offset-2 dark:bg-neutral-800 motion-reduce:transition-none motion-reduce:active:scale-100"
    >
      <Sparkle aria-hidden className="size-4 text-amber-300" />
      <span aria-hidden>{t("amount", { amount })}</span>
    </button>
  )
}
