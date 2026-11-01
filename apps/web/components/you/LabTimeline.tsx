import Link from "next/link"
import { ChevronRight } from "lucide-react"
import { useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"
import { dayMonth } from "@/lib/utils/day-month"
import { logSummary, logTitle } from "@/lib/utils/log-localized"
import { cn } from "@/lib/utils"
import type { Log } from "@/lib/types"

/**
 * The three latest shipped features on You (#1384 D19): a date, a dot on a
 * hairline (the newest filled), the title and its line; then "All updates" to
 * the full changelog.
 */
export function LabTimeline({ logs, locale }: { logs: readonly Log[]; locale: string }) {
  const t = useTranslations("you.lab")
  return (
    <div>
      <ol>
        {logs.map((log, i) => {
          const when = log.released_at ?? log.published_at ?? log.created_at
          const last = i === logs.length - 1
          return (
            <li key={log.id} className="flex items-stretch gap-3">
              <time
                dateTime={when}
                className="pb-type-kicker w-[2.875rem] shrink-0 pt-px text-right font-medium text-m3-on-surface-variant not-italic"
              >
                {dayMonth(when, locale, "short")}
              </time>
              <span aria-hidden="true" className="flex w-[9px] shrink-0 flex-col items-center pt-1.5">
                <span
                  className={cn(
                    "size-[9px] shrink-0 rounded-full",
                    i === 0 ? "bg-m3-primary" : "border-[1.5px] border-m3-outline bg-background",
                  )}
                />
                {!last && <span className="mt-1 w-px flex-1 bg-m3-rule" />}
              </span>
              <span className={cn("min-w-0 flex-1", !last && "pb-3.5")}>
                <span className="pb-type-row-title block text-m3-on-surface">{logTitle(log, locale)}</span>
                <span className="pb-type-row-line line-clamp-1 text-m3-on-surface-variant">{logSummary(log, locale)}</span>
              </span>
            </li>
          )
        })}
      </ol>
      <Button
        variant="outline"
        nativeButton={false}
        render={<Link href="/lab/changelog" />}
        className="pb-type-label mt-3.5 ml-[calc(2.875rem+0.75rem)] h-auto gap-1 rounded-full border-m3-outline-variant bg-transparent py-2 pr-3 pl-4 text-m3-primary"
      >
        {t("allUpdates")}
        <ChevronRight aria-hidden="true" className="size-4" />
      </Button>
    </div>
  )
}
