import { useTranslations } from "next-intl"
import { GUIDE_ICONS } from "@/lib/config/guides"
import type { Guide } from "@/lib/types"
import { LinkRow } from "./LinkRow"

/**
 * About Pebbles (#1384, spec D14): the head, then one row per published guide
 * to its page. Nothing under the head when there is nothing to read.
 */
export function AboutPebblesList({ guides }: { guides: readonly Guide[] }) {
  const t = useTranslations("home.about")
  const titleId = "home-about-title"
  return (
    <section aria-labelledby={titleId}>
      <h2 id={titleId} className="pb-type-section mb-2 px-1 text-m3-on-surface">
        {t("title")}
      </h2>
      {guides.length > 0 && (
        <ul>
          {guides.map((guide, i) => (
            <li key={guide.slug}>
              <LinkRow
                href={`/guides/${guide.slug}`}
                seed={`chip-${i}`}
                icon={guide.illustration ? GUIDE_ICONS[guide.illustration] : null}
                title={guide.title}
                line={guide.summary}
              />
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
