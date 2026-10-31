"use client"

import { PebbleDottedGlyph } from "@pbbls/icons"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { cn } from "@/lib/utils"
import { useTranslations } from "next-intl"
import type { Mark } from "@/lib/types"
import { GlyphPreview } from "@/components/glyphs/GlyphPreview"

type ProfileBannerProps = {
  displayName: string
  /** Pre-formatted member-since date (e.g. "Apr 12, 2026"). */
  memberSince: string
  /** The user's profile glyph, or null to show the carve placeholder. */
  glyph: Mark | null
}

/**
 * Centered glyph + display name + "member since" — web port of the iOS
 * `ProfileBanner`. The name renders in the Reenie Beanie script face per the
 * current Figma design.
 */
export function ProfileBanner({ displayName, memberSince, glyph }: ProfileBannerProps) {
  const t = useTranslations("profile")

  return (
    <div className="flex flex-col items-center gap-8">
      {glyph && glyph.strokes.length > 0 ? (
        <GlyphPreview mark={glyph} className="size-24" />
      ) : (
        <div className="flex size-24 items-center justify-center rounded-2xl border border-dashed border-border">
          <PebbleDottedGlyph className={cn(PBBLS_ICON_SIZE.hero, "text-muted-foreground")} />
        </div>
      )}

      <div className="flex flex-col items-center gap-1 text-center">
        <h2 className="font-script text-[41px] leading-none tracking-[-0.05em] text-foreground">
          {displayName}
        </h2>
        <span className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
          {t("memberSinceValue", { date: memberSince })}
        </span>
      </div>
    </div>
  )
}
