import type { ComponentType } from "react"
import { useTranslations } from "next-intl"
import { CalendarDate, KarmaDot, PebbleRound } from "@pbbls/icons"
import { SplitStone } from "@/components/stones/SplitStone"
import { SURFACE_CONTAINER_FILL } from "@/components/home/home-fills"
import { formatNumber } from "@/lib/i18n/format"
import { cn } from "@/lib/utils"

type CounterKey = "days" | "pebbles" | "karma"

const COUNTERS: readonly { key: CounterKey; icon: ComponentType<{ className?: string }> }[] = [
  { key: "days", icon: CalendarDate },
  { key: "pebbles", icon: PebbleRound },
  { key: "karma", icon: KarmaDot },
]

// The figure keeps its 36 px while its piece has room for four digits, and
// shrinks with the piece below that (a 320 px window, a larger text size), so
// "1 240" never runs past its stone. 100cqi is the piece's content width. In
// the same squeeze the label drops under its icon rather than overflowing.
const FIT = "[font-size:min(2.25rem,calc(100cqi/2.4))]!"

type YouCountersProps = {
  /** Each value is served; null while it loads ("—"). */
  values: Record<CounterKey, number | null>
  locale: string
  /** The days could not be read: one quiet line and a retry under the stone (D23). */
  failed?: boolean
  onRetry?: () => void
}

/**
 * You's counters (#1384 D16): one rock cut in three, right under the header,
 * without a title. Each piece is a term and its value, read in that order
 * (label, then figure) while the figure shows first.
 */
export function YouCounters({ values, locale, failed = false, onRetry }: YouCountersProps) {
  const t = useTranslations("you")
  return (
    <section aria-label={t("counters.label")}>
      <SplitStone
        n={3}
        height={88}
        seed="days|pebbles|karma"
        fill={SURFACE_CONTAINER_FILL}
        cells={COUNTERS.map(({ key, icon: Icon }, i) => {
          const value = values[key]
          return (
            <dl key={key} className={cn("@container flex h-full flex-col justify-center py-3 pr-2.5", i === 0 ? "pl-4" : "pl-5")}>
              <dt className="pb-type-row-line flex min-w-0 flex-wrap items-center gap-x-1.5 text-m3-on-surface-variant">
                <Icon className="size-[1.125rem] shrink-0 text-m3-primary" />
                <span className="min-w-0">{t(`counters.${key}`)}</span>
              </dt>
              <dd className={cn("pb-type-counter order-first mb-1.5 whitespace-nowrap text-m3-on-surface", FIT)}>
                {value === null ? "—" : formatNumber(value, locale)}
              </dd>
            </dl>
          )
        })}
      />
      {failed && (
        <p className="pb-type-row-line mt-3 px-1 text-m3-on-surface-variant italic">
          {t("error")}{" "}
          <button type="button" onClick={onRetry} className="font-semibold text-m3-primary not-italic underline-offset-2 hover:underline">
            {t("retry")}
          </button>
        </p>
      )}
    </section>
  )
}
