import { useTranslations } from "next-intl"
import type { Log } from "@/lib/types"
import { CommunityStone } from "./CommunityStone"
import { LabAnnouncementCard } from "./LabAnnouncementCard"
import { LabTimeline } from "./LabTimeline"

type YouLabViewProps = {
  announcement: Log | null
  changelog: readonly Log[]
  locale: string
  /** Both reads failed: one quiet line and a retry; the community still shows (D23). */
  failed?: boolean
  onRetry?: () => void
}

/**
 * You's Lab (#1384 D19): the latest announcement, the three latest shipped
 * features, the community. Each read stands alone: a missing half leaves the
 * rest, and the community always shows.
 */
export function YouLabView({ announcement, changelog, locale, failed = false, onRetry }: YouLabViewProps) {
  const t = useTranslations("you")
  const titleId = "you-lab-title"
  return (
    <section aria-labelledby={titleId}>
      <h2 id={titleId} className="pb-type-section mb-[18px] px-1 text-m3-on-surface">
        {t("lab.title")}
      </h2>
      {announcement && (
        <div className="mb-[22px]">
          <LabAnnouncementCard log={announcement} locale={locale} />
        </div>
      )}
      {changelog.length > 0 && (
        <div className="mb-6">
          <LabTimeline logs={changelog} locale={locale} />
        </div>
      )}
      {failed && (
        <p className="pb-type-row-line mb-6 px-1 text-m3-on-surface-variant italic">
          {t("lab.error")}{" "}
          <button type="button" onClick={onRetry} className="font-semibold text-m3-primary not-italic underline-offset-2 hover:underline">
            {t("retry")}
          </button>
        </p>
      )}
      <CommunityStone />
    </section>
  )
}
