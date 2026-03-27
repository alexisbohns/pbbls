"use client"

import type { RecordFormData } from "@/components/record/RecordStepper"
import { useSouls } from "@/lib/data/useSouls"
import {
  EMOTIONS,
  DOMAINS,
  CARD_TYPES,
  POSITIVENESS_SIGNS,
  POSITIVENESS_LABELS,
} from "@/lib/config"
import type { Soul } from "@/lib/types"

type ReviewSummaryProps = {
  data: RecordFormData
}

const dateTimeFormatter = new Intl.DateTimeFormat("en-US", {
  weekday: "short",
  year: "numeric",
  month: "short",
  day: "numeric",
  hour: "numeric",
  minute: "numeric",
})

export function ReviewSummary({ data }: ReviewSummaryProps) {
  const { souls } = useSouls()

  const emotion = EMOTIONS.find((e) => e.id === data.emotion_id)
  const domains = DOMAINS.filter((d) => data.domain_ids.includes(d.id))
  const matchedSouls = data.soul_ids
    .map((id) => souls.find((s) => s.id === id))
    .filter((s): s is Soul => s !== undefined)

  const filledCards = data.cards.filter((c) => c.value.trim() !== "")
  const formattedDate = dateTimeFormatter.format(new Date(data.happened_at))

  const intensityFilled = "●".repeat(data.intensity)
  const intensityEmpty = "○".repeat(3 - data.intensity)
  const positSign = POSITIVENESS_SIGNS[data.positiveness] ?? "~"
  const positLabel = POSITIVENESS_LABELS[data.positiveness] ?? "Neutral"

  return (
    <section aria-labelledby="review-heading" className="space-y-3">
      <h2
        id="review-heading"
        className="text-sm font-medium"
      >
        Review
      </h2>

      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
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
          <abbr
            title={`Intensity: ${data.intensity} of 3`}
            className="tracking-wide no-underline"
          >
            {intensityFilled}
            {intensityEmpty}
          </abbr>
          <abbr
            title={`Positiveness: ${positLabel}`}
            className="font-medium no-underline"
          >
            {positSign}
          </abbr>
        </dd>

        {/* Souls */}
        {matchedSouls.length > 0 && (
          <>
            <dt className="text-muted-foreground">Souls</dt>
            <dd className="flex flex-wrap gap-1.5">
              {matchedSouls.map((soul) => (
                <span
                  key={soul.id}
                  className="rounded-full border border-border px-2 py-0.5 text-xs font-medium"
                >
                  {soul.name}
                </span>
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
                <span
                  key={domain.id}
                  className="rounded-full border border-border px-2 py-0.5 text-xs font-medium"
                >
                  {domain.name}
                </span>
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
