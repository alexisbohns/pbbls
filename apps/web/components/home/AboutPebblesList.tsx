import Link from "next/link"
import { ChevronRight } from "lucide-react"
import { useTranslations } from "next-intl"
import { GUIDE_ICONS } from "@/lib/config/guides"
import type { Guide } from "@/lib/types"
import { IconChip } from "./IconChip"

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
              <Link
                href={`/guides/${guide.slug}`}
                className="flex items-center gap-3.5 rounded-xl px-1 py-2.5 outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                <IconChip seed={`chip-${i}`} icon={guide.illustration ? GUIDE_ICONS[guide.illustration] : null} />
                <span className="min-w-0 flex-1">
                  <span className="pb-type-row-title block text-m3-on-surface">{guide.title}</span>
                  <span className="pb-type-row-line mt-0.5 block text-m3-on-surface-variant">{guide.summary}</span>
                </span>
                <ChevronRight aria-hidden="true" className="size-[18px] shrink-0 text-m3-outline" />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
