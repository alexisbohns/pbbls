"use client"

import { useRouter } from "next/navigation"
import { useTranslations } from "next-intl"
import { Trophy } from "lucide-react"
import { toast } from "sonner"
import { useAchievementCopy } from "@/lib/i18n"
import type { AchievementUnlockResult } from "@/lib/data/data-provider"
import type { Achievement } from "@/lib/types"

type AchievementUnlockPillProps = {
  toastId: string | number
  results: AchievementUnlockResult[]
  catalog: Achievement[]
}

export function AchievementUnlockPill({
  toastId,
  results,
  catalog,
}: AchievementUnlockPillProps) {
  const router = useRouter()
  const t = useTranslations("activity")
  const resolveCopy = useAchievementCopy()

  const handleTap = () => {
    toast.dismiss(toastId)
    router.push("/achievements")
  }

  // One pill per response (D8): a lone unlock is announced by its localized
  // title; several collapse into a count phrase. The catalog lookup can only
  // miss if the check raced a catalog change — the count phrase covers it.
  const single =
    results.length === 1 ? catalog.find((a) => a.slug === results[0].slug) : undefined
  const headline = single
    ? resolveCopy(single).title
    : t("achievementsUnlocked", { count: results.length })

  // Total karma the unlocks actually paid — reported by the RPC from the
  // ledger, so the pill can never show a number the wallet doesn't hold.
  const totalKarma = results.reduce((sum, r) => sum + r.karmaGranted, 0)

  // Full spoken sentence: "Achievement unlocked: First pebble. Earned 5 karma.
  // View achievements."
  const label = [
    single
      ? t("srAchievementUnlocked", { title: headline })
      : t("srAchievementsUnlocked", { count: results.length }),
    totalKarma > 0 ? t("srAchievementKarma", { amount: totalKarma }) : null,
    `${t("viewAchievements")}.`,
  ]
    .filter((part): part is string => part !== null)
    .join(" ")

  return (
    <button
      type="button"
      onClick={handleTap}
      aria-label={label}
      className="flex items-center gap-2 rounded-full bg-neutral-900 px-4 py-2 text-sm font-semibold text-white shadow-lg ring-1 ring-white/10 transition active:scale-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-300 focus-visible:ring-offset-2 dark:bg-neutral-800 motion-reduce:transition-none motion-reduce:active:scale-100"
    >
      <Trophy aria-hidden className="size-4 text-amber-300" />
      <span aria-hidden>{headline}</span>
      {totalKarma > 0 && (
        <span aria-hidden className="text-amber-300">
          {t("amount", { amount: totalKarma })}
        </span>
      )}
    </button>
  )
}
