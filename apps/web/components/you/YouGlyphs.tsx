import { useTranslations } from "next-intl"
import { DrawDotted, Petroglyph, Pouch } from "@pbbls/icons"
import { LinkRow } from "@/components/home/LinkRow"

/**
 * You's Glyphs (#1384 D17): three rows to the glyph tabs. The two counts are
 * served by get_profile_engagement (#1386), never counted here; a null count
 * (not served) keeps the row's line without a number.
 */
export function YouGlyphs({ carved, owned }: { carved: number | null; owned: number | null }) {
  const t = useTranslations("you.glyphs")
  const titleId = "you-glyphs-title"
  return (
    <section aria-labelledby={titleId}>
      <h2 id={titleId} className="pb-type-section mb-2 px-1 text-m3-on-surface">
        {t("title")}
      </h2>
      <ul>
        <li>
          <LinkRow
            href="/glyphs?tab=mine"
            seed="chip-0"
            icon={DrawDotted}
            title={t("mine")}
            line={carved === null ? t("mineLineNoCount") : t("mineLine", { count: carved })}
          />
        </li>
        <li>
          <LinkRow
            href="/glyphs?tab=favourites"
            seed="chip-1"
            icon={Pouch}
            title={t("owned")}
            line={owned === null ? t("ownedLineNoCount") : t("ownedLine", { count: owned })}
          />
        </li>
        <li>
          <LinkRow href="/glyphs?tab=market" seed="chip-2" icon={Petroglyph} title={t("community")} line={t("communityLine")} />
        </li>
      </ul>
    </section>
  )
}
