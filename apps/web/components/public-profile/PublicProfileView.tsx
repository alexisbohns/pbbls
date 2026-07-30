"use client"

import Link from "next/link"
import { CalendarDays, Fingerprint, Shell, Waves } from "lucide-react"
import { useTranslations } from "next-intl"
import type { PublicProfile } from "@/lib/types"
import { useFormatDate } from "@/lib/i18n"
import { GlyphPreview } from "@/components/glyphs/GlyphPreview"
import { RippleBadge } from "@/components/profile/ripples/RippleBadge"
import { AssiduityGrid } from "@/components/profile/AssiduityGrid"
import { DataTile } from "@/components/profile/DataTile"
import { SectionCard } from "@/components/profile/SectionCard"

type PublicProfileViewProps = {
  profile: PublicProfile
}

/**
 * The /u/[handle] public page body (M50). Anonymous-friendly: renders only the
 * `get_public_profile` projection — banner, evolutive rings, 28-day grid and
 * counters — plus a "made with Pebbles" CTA back to the landing page. Reuses
 * the owner-profile primitives (RippleBadge, AssiduityGrid, DataTile) so the
 * public page and the owner's profile stay visually in step.
 */
export function PublicProfileView({ profile }: PublicProfileViewProps) {
  const t = useTranslations("publicProfile")
  const formatDate = useFormatDate()

  // Not RipplesRow: that component renders "N more pebbles to level X" from
  // `pebbles28d`, a raw count the public projection deliberately withholds.
  // The badge and grid primitives are shared; the copy around them is not.
  //
  // The projection also omits active_today (a presence signal), so the badge
  // always renders the neutral tone.
  const rippleLevel = Math.min(Math.max(profile.ripple_level, 0), 6)
  const bounceLevel = Math.min(Math.max(profile.bounce_level, 0), 7)

  return (
    <div className="mx-auto flex min-h-full w-full max-w-md flex-col px-4 pt-10 md:px-6 md:pt-14">
      <section className="flex flex-col gap-8">
        <div className="flex flex-col items-center gap-8">
          {profile.glyph && profile.glyph.strokes.length > 0 ? (
            <GlyphPreview
              mark={{
                id: "public-profile-glyph",
                strokes: profile.glyph.strokes,
                viewBox: profile.glyph.view_box,
                created_at: "",
                updated_at: "",
              }}
              className="size-24"
            />
          ) : (
            <div className="flex size-24 items-center justify-center rounded-2xl border border-dashed border-border">
              <Fingerprint className="size-8 text-muted-foreground" aria-hidden />
            </div>
          )}

          <div className="flex flex-col items-center gap-1 text-center">
            <h1 className="font-script text-[41px] leading-none tracking-[-0.05em] text-foreground">
              {profile.display_name}
            </h1>
            <span className="text-sm font-medium text-muted-foreground">
              @{profile.handle}
            </span>
            <span className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
              {t("memberSince", {
                date: formatDate(profile.member_since, { dateStyle: "medium" }),
              })}
            </span>
          </div>
        </div>

        <SectionCard>
          <div className="flex items-center gap-4">
            <RippleBadge level={rippleLevel} activeToday={false} />
            <div className="flex min-w-0 flex-1 flex-col gap-1">
              <span className="text-sm font-medium text-foreground">
                {t("rippleLevel", { level: rippleLevel })}
              </span>
              <span className="flex items-center gap-1 text-sm text-muted-foreground">
                <Waves className="size-3.5 text-primary" aria-hidden />
                {t("bounceLevel", { level: bounceLevel })}
              </span>
            </div>
            <AssiduityGrid data={profile.assiduity} />
          </div>
          <div className="h-px bg-border" />
          <div className="flex items-start">
            <DataTile
              value={profile.days_practiced}
              icon={CalendarDays}
              label={t("days")}
            />
            <DataTile
              value={profile.pebbles_count}
              icon={Shell}
              label={t("pebbles")}
            />
          </div>
        </SectionCard>
      </section>

      <footer className="mt-auto flex justify-center py-10">
        <Link
          href="/"
          className="text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
        >
          {t("madeWith")}
        </Link>
      </footer>
    </div>
  )
}
