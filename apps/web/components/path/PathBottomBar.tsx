"use client"

import Link from "next/link"
import { CircleUser, CirclePile, Sparkle } from "lucide-react"
import { useTranslations } from "next-intl"
import { useAuth } from "@/lib/data/auth-context"
import { useBounce } from "@/lib/data/useBounce"
import { useKarma } from "@/lib/data/useKarma"
import { useUsableGlyphs } from "@/lib/data/useUsableGlyphs"
import { GlyphPreview } from "@/components/glyphs/GlyphPreview"
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
      <Icon className="size-3.5 self-center text-(--spine-ink-2)" aria-hidden />
      <span className="font-spine text-[15px] font-semibold text-(--spine-ink) [font-variant-numeric:lining-nums_tabular-nums]">
        {value}
      </span>
      <span className="font-spine-sc text-[11px] font-semibold tracking-[.14em] text-(--spine-ink-2)">{label}</span>
    </span>
  )
}

/** Profile, bounce and karma on a translucent pill of card stock (#1052). */
export function PathBottomBar({ className }: { className?: string }) {
  const t = useTranslations("path")
  const { profile } = useAuth()
  const { glyphs } = useUsableGlyphs()
  const { bounce, loading: bounceLoading } = useBounce()
  const { karma, loading: karmaLoading } = useKarma()

  const glyph = profile?.glyph_id
    ? glyphs.find((g) => g.id === profile.glyph_id) ?? null
    : null

  return (
    <nav
      aria-label={t("bottomBar.label")}
      className={cn(
        "flex items-center gap-3 rounded-full border border-(--spine-rule) bg-[color-mix(in_oklab,var(--spine-stock)_88%,transparent)] py-0.5 pr-4 pl-1 backdrop-blur-[8px]",
        className,
      )}
    >
      <Link
        href="/profile"
        aria-label={t("bottomBar.profileAria")}
        className="inline-flex size-10 items-center justify-center rounded-full text-(--spine-ink)"
      >
        {glyph && glyph.strokes.length > 0 ? (
          <GlyphPreview mark={glyph} className="size-6" strokeClassName="text-(--spine-ink)" />
        ) : (
          <CircleUser className="size-6" />
        )}
      </Link>
      <Link
        href="/wallet"
        aria-label={t("bottomBar.walletAria")}
        className="flex min-h-10 items-center gap-4"
      >
        <Stat icon={CirclePile} value={bounceLoading ? "—" : bounce} label={t("stats.bounce")} />
        <Stat icon={Sparkle} value={karmaLoading ? "—" : karma} label={t("stats.karma")} />
      </Link>
    </nav>
  )
}
