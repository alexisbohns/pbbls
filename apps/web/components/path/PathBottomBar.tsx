"use client"

import Link from "next/link"
import { CircleUser, CirclePile, Sparkle } from "lucide-react"
import { useTranslations } from "next-intl"
import { useAuth } from "@/lib/data/auth-context"
import { useBounce } from "@/lib/data/useBounce"
import { useKarma } from "@/lib/data/useKarma"
import { useUsableGlyphs } from "@/lib/data/useUsableGlyphs"
import { GlyphPreview } from "@/components/glyphs/GlyphPreview"

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
    <span className="flex items-center gap-1.5">
      <Icon className="size-4 text-primary" aria-hidden />
      <span className="text-sm font-semibold text-foreground dark:text-primary">{value}</span>
      <span className="text-xs text-muted-foreground">{label}</span>
    </span>
  )
}

export function PathBottomBar() {
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
      className="flex items-center justify-between gap-3 px-4 py-3 pb-[calc(0.75rem+var(--safe-area-bottom))]"
    >
      <Link
        href="/profile"
        aria-label={t("bottomBar.profileAria")}
        className="inline-flex size-10 items-center justify-center text-primary"
      >
        {glyph && glyph.strokes.length > 0 ? (
          <GlyphPreview mark={glyph} className="size-7" />
        ) : (
          <CircleUser className="size-7" />
        )}
      </Link>
      <Link
        href="/wallet"
        aria-label={t("bottomBar.walletAria")}
        className="flex items-center gap-4"
      >
        <Stat icon={CirclePile} value={bounceLoading ? "—" : bounce} label={t("stats.bounce")} />
        <Stat icon={Sparkle} value={karmaLoading ? "—" : karma} label={t("stats.karma")} />
      </Link>
    </nav>
  )
}
