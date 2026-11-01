import { useTranslations } from "next-intl"
import { PebbleDottedGlyph } from "@pbbls/icons"
import { GlyphPreview } from "@/components/glyphs/GlyphPreview"
import { Pentagon } from "@/components/stones/Pentagon"
import { formatDate } from "@/lib/i18n/format"
import type { Mark } from "@/lib/types"
import { RipplesLine } from "./RipplesLine"
import { SettingsButton } from "./SettingsButton"

type YouHeaderProps = {
  name: string
  /** When the account was made (ISO), shown as "Member since March 2026". */
  memberSince: string
  /** The profile glyph, or null for the dotted placeholder. */
  glyph: Mark | null
  rippleLevel: number
  activeToday: boolean
  locale: string
}

/**
 * You's header (#1384 D15): Settings at the top right, the glyph on the
 * pentagon, the name in the hand as the page's heading (it wraps, never
 * truncates), the member-since line and the Ripples line.
 */
export function YouHeader({ name, memberSince, glyph, rippleLevel, activeToday, locale }: YouHeaderProps) {
  const t = useTranslations("you")
  const since = formatDate(memberSince, locale, { month: "long", year: "numeric" })
  return (
    <header className="flex flex-col">
      <div className="-mb-[18px] flex justify-end">
        <SettingsButton />
      </div>
      <div className="flex flex-col items-center text-center">
        <Pentagon>
          {glyph && glyph.strokes.length > 0 ? (
            <GlyphPreview mark={glyph} className="size-[3.625rem]" strokeClassName="text-m3-on-primary-container" />
          ) : (
            <PebbleDottedGlyph className="size-[3.625rem] text-m3-on-primary-container" />
          )}
        </Pentagon>
        <h1 className="pb-type-hand mt-3.5 max-w-full text-m3-on-surface [overflow-wrap:anywhere]">{name}</h1>
        <p className="pb-type-since mt-1.5 text-m3-on-surface-variant">{t("memberSince", { date: since })}</p>
        <RipplesLine level={rippleLevel} activeToday={activeToday} className="mt-3" />
      </div>
    </header>
  )
}
