import Link from "next/link"
import { useTranslations } from "next-intl"
import { Card } from "@/components/ui/card"
import { SplitStone } from "@/components/stones/SplitStone"
import { PRIMARY_CONTAINER_FILL } from "@/components/home/home-fills"
import { dayMonth } from "@/lib/utils/day-month"
import { labAssetUrl } from "@/lib/utils/lab-asset-url"
import { logSummary, logTitle } from "@/lib/utils/log-localized"
import type { Log } from "@/lib/types"

/**
 * The Lab's latest announcement on You (#1384 D19): a card to its page, under
 * its cover, or under a drawn band (a stone cut in three on a wash of the
 * accent surface) when it has none.
 */
export function LabAnnouncementCard({ log, locale }: { log: Log; locale: string }) {
  const t = useTranslations("you.lab")
  const cover = labAssetUrl(log.cover_image_path)
  const date = dayMonth(log.published_at ?? log.created_at, locale)
  return (
    <Link
      href={`/lab/announcements/${log.id}`}
      className="block rounded-[18px] outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background"
    >
      <Card className="pb-card gap-0 py-0">
        {cover ? (
          // eslint-disable-next-line @next/next/no-img-element -- public Supabase storage URL, next/image domain config not wired
          <img src={cover} alt="" className="h-24 w-full bg-m3-surface-container object-cover" loading="lazy" />
        ) : (
          <div
            aria-hidden="true"
            className="flex justify-center bg-[color-mix(in_srgb,var(--m3-primary-container)_35%,var(--m3-stock))] px-4 py-[22px]"
          >
            <SplitStone
              n={3}
              height={64}
              options={{ corner: 22, gutter: 6, fillet: 14 }}
              fill={PRIMARY_CONTAINER_FILL}
              className="w-full max-w-[286px]"
            />
          </div>
        )}
        <div className="flex flex-col gap-1 px-[18px] pt-3.5 pb-[18px]">
          <p className="pb-type-kicker text-m3-on-surface-variant">{t("kicker", { date })}</p>
          <h3 className="pb-type-card-title text-m3-on-surface">{logTitle(log, locale)}</h3>
          <p className="pb-type-row-line line-clamp-2 text-m3-on-surface-variant">{logSummary(log, locale)}</p>
        </div>
      </Card>
    </Link>
  )
}
