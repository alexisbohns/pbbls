"use client"

import Link from "next/link"
import { CirclePile } from "lucide-react"
import { KarmaDot } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { useTranslations } from "next-intl"
import { useBounce } from "@/lib/data/useBounce"
import { useKarma } from "@/lib/data/useKarma"
import { cn } from "@/lib/utils"

function Stat({
  icon: Icon,
  value,
  label,
}: {
  icon: React.ComponentType<{ className?: string }>
  value: number | string
  label: string
}) {
  return (
    <span className="flex items-baseline gap-1.5">
      <Icon className={cn(PBBLS_ICON_SIZE.inline, "self-center text-(--spine-ink-2)")} />
      <span className="font-spine text-[15px] font-semibold text-(--spine-ink) [font-variant-numeric:lining-nums_tabular-nums]">
        {value}
      </span>
      <span className="font-spine-sc text-[11px] font-semibold tracking-[.14em] text-(--spine-ink-2)">{label}</span>
    </span>
  )
}

/**
 * Bounce and karma on a translucent pill of card stock (#1052), a link to the
 * wallet. The tabs (Home and You among them) live in the app navigation since
 * #1384, so the pill is no longer a navigation of its own.
 */
export function PathBottomBar({ className }: { className?: string }) {
  const t = useTranslations("path")
  const { bounce, loading: bounceLoading } = useBounce()
  const { karma, loading: karmaLoading } = useKarma()

  return (
    <Link
      href="/wallet"
      aria-label={t("bottomBar.walletAria")}
      className={cn(
        "flex min-h-11 items-center gap-4 rounded-full border border-(--spine-rule) bg-[color-mix(in_oklab,var(--spine-stock)_88%,transparent)] px-4 backdrop-blur-[8px]",
        className,
      )}
    >
      <Stat icon={CirclePile} value={bounceLoading ? "—" : bounce} label={t("stats.bounce")} />
      <Stat icon={KarmaDot} value={karmaLoading ? "—" : karma} label={t("stats.karma")} />
    </Link>
  )
}
