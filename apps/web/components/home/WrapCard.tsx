import Link from "next/link"
import { useTranslations } from "next-intl"
import { Card } from "@/components/ui/card"
import { daysInMonth, firstWeekOf, monthName, monthYear } from "@/lib/home/month-labels"
import type { HomeWrap } from "@/lib/types"
import { WrapRing } from "./WrapRing"

const CARD = "pb-card w-[152px] gap-0 p-[18px_16px_18px_18px] text-m3-on-surface"
const FOCUS = "block rounded-[18px] outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background"

/**
 * A finished month (#1384, spec D13). Revealed: its ring and count, a link to
 * the Path at the month's first week. Folded (never unfolded): its name and a
 * turned corner, a button that asks to unfold it.
 */
export function WrapCard({ wrap, locale, onUnfold }: { wrap: HomeWrap; locale: string; onUnfold: (wrap: HomeWrap) => void }) {
  const t = useTranslations("home.wraps")
  const month = monthName(wrap.month, locale)
  const capitalised = month.charAt(0).toLocaleUpperCase(locale) + month.slice(1)
  const label = `${capitalised} ${monthYear(wrap.month)}`

  if (wrap.revealedAt === null) {
    return (
      <button type="button" onClick={() => onUnfold(wrap)} aria-label={t("cardAriaFolded", { month: label, count: wrap.count })} className={`${FOCUS} text-left`}>
        <Card className={`${CARD} relative min-h-[214px] justify-end`}>
          <span aria-hidden="true" className="absolute top-0 right-0 size-[14px] rounded-bl-[6px] bg-m3-surface-container-high [clip-path:polygon(0_0,100%_100%,0_100%)]" />
          <span className="pb-type-card-month">{capitalised}</span>
          <span className="pb-type-annotation mt-1.5 text-m3-on-surface-variant">{t("folded")}</span>
        </Card>
      </button>
    )
  }

  return (
    <Link href={`/path?week=${firstWeekOf(wrap.month)}`} aria-label={t("cardAria", { month: label, count: wrap.count })} className={FOCUS}>
      <Card className={CARD}>
        <div aria-hidden="true" className="relative mb-3.5 size-[116px]">
          <WrapRing daysInMonth={daysInMonth(wrap.month)} days={wrap.days} />
          <span className="pb-type-counter absolute inset-0 grid place-items-center text-[2.125rem]">{wrap.count}</span>
        </div>
        <span aria-hidden="true" className="pb-type-card-month">
          {capitalised}
        </span>
        <span aria-hidden="true" className="pb-type-kicker mt-0.5 text-m3-on-surface-variant">
          {monthYear(wrap.month)}
        </span>
      </Card>
    </Link>
  )
}
