"use client"

import type { RecordFormData } from "@/components/record/types"
import { useSouls } from "@/lib/data/useSouls"
import { EMOTIONS, DOMAINS, CARD_TYPES } from "@/lib/config"
import { IntensityDots, PositivenessIndicator } from "@/components/pebble/PebbleIndicators"
import { Badge } from "@/components/ui/badge"
import { shortDateTimeFormatter } from "@/lib/utils/formatters"
import type { Soul } from "@/lib/types"

type ReviewSummaryProps = {
  data: RecordFormData
}

export function ReviewSummary({ data }: ReviewSummaryProps) {
  const { souls } = useSouls()

  const emotion = EMOTIONS.find((e) => e.id === data.emotion_id)
  const domains = DOMAINS.filter((d) => data.domain_ids.includes(d.id))
  const matchedSouls = data.soul_ids
    .map((id) => souls.find((s) => s.id === id))
    .filter((s): s is Soul => s !== undefined)

  const filledCards = data.cards.filter((c) => c.value.trim() !== "")
  const formattedDate = shortDateTimeFormatter.format(new Date(data.happened_at))

  return (
    <section aria-labelledby="review-heading" className="space-y-3">
      <h2
        id="review-heading"
        className="text-sm font-medium"
      >
        Review
      </h2>

      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
        {/* Name */}
        {data.name.trim() !== "" && (
          <>
            <dt className="text-muted-foreground">Name</dt>
            <dd>{data.name}</dd>
          </>
        )}

        {/* Description */}
        {data.description.trim() !== "" && (
          <>
            <dt className="text-muted-foreground">Description</dt>
            <dd>{data.description}</dd>
          </>
        )}

        {/* Date */}
        <dt className="text-muted-foreground">When</dt>
        <dd>
          <time dateTime={data.happened_at}>{formattedDate}</time>
        </dd>

        {/* Emotion */}
        {emotion && (
          <>
            <dt className="text-muted-foreground">Emotion</dt>
            <dd>
              <span
                className="inline-block rounded-full px-2 py-0.5 text-xs font-medium"
                style={{
                  backgroundColor: `${emotion.color}20`,
                  color: emotion.color,
                }}
              >
                {emotion.name}
              </span>
            </dd>
          </>
        )}

        {/* Intensity & Positiveness */}
        <dt className="text-muted-foreground">Intensity</dt>
        <dd className="flex items-center gap-3">
          <IntensityDots intensity={data.intensity} />
          <PositivenessIndicator value={data.positiveness} />
        </dd>

        {/* Souls */}
        {matchedSouls.length > 0 && (
          <>
            <dt className="text-muted-foreground">Souls</dt>
            <dd className="flex flex-wrap gap-1.5">
              {matchedSouls.map((soul) => (
                <Badge key={soul.id} variant="outline">{soul.name}</Badge>
              ))}
            </dd>
          </>
        )}

        {/* Domains */}
        {domains.length > 0 && (
          <>
            <dt className="text-muted-foreground">Domains</dt>
            <dd className="flex flex-wrap gap-1.5">
              {domains.map((domain) => (
                <Badge key={domain.id} variant="outline">{domain.name}</Badge>
              ))}
            </dd>
          </>
        )}

        {/* Cards */}
        <dt className="text-muted-foreground">Cards</dt>
        <dd>
          {filledCards.length} of {CARD_TYPES.length} filled
        </dd>
      </dl>
    </section>
  )
}
