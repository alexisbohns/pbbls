"use client"

import { useTranslations } from "next-intl"
import type { CompletedMoment, Emotion } from "@/lib/types"
import { useFormatTime } from "@/lib/i18n"
import { PebbleFramed } from "@/components/pebble/PebbleFramed"
import { MomentName } from "@/components/pebble/MomentName"
import { momentDisplayName, stoneLookOf } from "@/lib/data/moment-row"
import { IntensityDots, PositivenessIndicator } from "@/components/pebble/PebbleIndicators"
import { EmotionBadge } from "@/components/ui/EmotionBadge"

type PebbleCardProps = {
  moment: CompletedMoment
  emotion: Emotion | undefined
  soulNames: string[]
  /** Called with the MOMENT id, the read page's route key (R9). */
  onSelect?: (momentId: string) => void
}

function MetadataRow({
  emotion,
  moment,
}: {
  emotion: Emotion | undefined
  moment: CompletedMoment
}) {
  return (
    <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
      {emotion && <EmotionBadge emotion={emotion} />}
      <IntensityDots intensity={moment.pebble.intensity} size="xs" />
      <PositivenessIndicator value={moment.pebble.positiveness} size="xs" />
    </div>
  )
}

/** A completed moment as a list row (soul and collection detail, PD-W19). */
export function PebbleCard({ moment, emotion, soulNames, onSelect }: PebbleCardProps) {
  const t = useTranslations("pebble")
  const tMoment = useTranslations("moment")
  const formatTime = useFormatTime()
  const time = formatTime(moment.happened_at)
  const isLarge = moment.pebble.intensity === 3
  const firstInstant = moment.instants[0] ?? null
  const label = momentDisplayName(moment.name) ?? tMoment("untitled")
  const look = stoneLookOf(moment)

  return (
    <article>
      <button
        type="button"
        onClick={() => onSelect?.(moment.id)}
        className={
          isLarge
            ? "flex w-full flex-col items-center gap-3 rounded-xl px-4 py-7 text-left transition-all duration-100 bg-card hover:bg-card/50 border active:scale-[0.98] focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
            : "flex w-full items-center gap-3 rounded-xl p-3 text-left transition-all duration-100 bg-card hover:bg-card/50 border active:scale-[0.98] focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
        }
        aria-label={t("cardAria", { name: label, time })}
      >
        {isLarge ? (
          <>
            <PebbleFramed look={look} className="size-16 shrink-0" />

            <MomentName
              as="h3"
              name={moment.name}
              className="text-center font-heading text-base font-semibold"
            />

            <time
              dateTime={moment.happened_at}
              className="text-xs text-muted-foreground"
            >
              {time}
            </time>

            {firstInstant && (
              /* eslint-disable-next-line @next/next/no-img-element -- signed storage URL, next/image optimization not applicable */
              <img
                src={firstInstant}
                alt={t("instantAlt", { name: label })}
                className="max-h-40 w-full rounded-lg object-cover"
              />
            )}

            <MetadataRow emotion={emotion} moment={moment} />

            {soulNames.length > 0 && (
              <span className="text-xs text-muted-foreground">
                <span className="sr-only">{t("withPrefix")}</span>
                {soulNames.join(", ")}
              </span>
            )}
          </>
        ) : (
          <>
            <PebbleFramed look={look} className="size-10 shrink-0" />

            <div className="min-w-0 flex-1">
              <MomentName as="h3" name={moment.name} className="font-heading text-sm font-medium" />

              <div className="mt-1.5 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                {emotion && <EmotionBadge emotion={emotion} />}
                <IntensityDots intensity={moment.pebble.intensity} size="xs" />
                <PositivenessIndicator value={moment.pebble.positiveness} size="xs" />

                <time dateTime={moment.happened_at}>{time}</time>

                {soulNames.length > 0 && (
                  <span>
                    <span className="sr-only">{t("withPrefix")}</span>
                    {soulNames.join(", ")}
                  </span>
                )}
              </div>
            </div>

            {firstInstant && (
              /* eslint-disable-next-line @next/next/no-img-element -- signed storage URL, next/image optimization not applicable */
              <img
                src={firstInstant}
                alt={t("instantAlt", { name: label })}
                className="size-8 shrink-0 rounded-md object-cover"
              />
            )}
          </>
        )}
      </button>
    </article>
  )
}
