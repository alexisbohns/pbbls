import type { Pebble, Soul } from "@/lib/types"
import { EMOTIONS, DOMAINS, CARD_TYPES } from "@/lib/config"
import { IntensityDots, PositivenessIndicator } from "@/components/pebble/PebbleIndicators"
import { Badge } from "@/components/ui/badge"
import { dateTimeFormatter } from "@/lib/utils/formatters"

type PebbleDetailProps = {
  pebble: Pebble
  souls: Soul[]
}

export function PebbleDetail({ pebble, souls }: PebbleDetailProps) {
  const emotion = EMOTIONS.find((e) => e.id === pebble.emotion_id)
  const domains = DOMAINS.filter((d) => pebble.domain_ids.includes(d.id))
  const matchedSouls = pebble.soul_ids
    .map((id) => souls.find((s) => s.id === id))
    .filter((s): s is Soul => s !== undefined)

  const formattedDate = dateTimeFormatter.format(new Date(pebble.happened_at))

  return (
    <article>
      {/* Header */}
      <header>
        <h1 className="text-2xl font-semibold">{pebble.name}</h1>

        <div className="mt-2 flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
          {emotion && (
            <span
              className="rounded-full px-2.5 py-0.5 text-sm font-medium"
              style={{
                backgroundColor: `${emotion.color}20`,
                color: emotion.color,
              }}
              aria-label={`Emotion: ${emotion.name}`}
            >
              {emotion.name}
            </span>
          )}

          <IntensityDots intensity={pebble.intensity} />
          <PositivenessIndicator value={pebble.positiveness} />

          <time dateTime={pebble.happened_at}>{formattedDate}</time>
        </div>
      </header>

      {/* Description */}
      {pebble.description && (
        <p className="mt-4 text-sm text-foreground">{pebble.description}</p>
      )}

      {/* Souls */}
      {matchedSouls.length > 0 && (
        <section className="mt-6" aria-labelledby="souls-heading">
          <h2 id="souls-heading" className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
            Souls
          </h2>
          <ul className="mt-2 flex flex-wrap gap-2" role="list">
            {matchedSouls.map((soul) => (
              <li key={soul.id}>
                <Badge variant="outline">{soul.name}</Badge>
              </li>
            ))}
          </ul>
        </section>
      )}

      {/* Domains */}
      {domains.length > 0 && (
        <section className="mt-6" aria-labelledby="domains-heading">
          <h2 id="domains-heading" className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
            Domains
          </h2>
          <ul className="mt-2 flex flex-wrap gap-2" role="list">
            {domains.map((domain) => (
              <li key={domain.id}>
                <Badge variant="outline">{domain.name}</Badge>
              </li>
            ))}
          </ul>
        </section>
      )}

      {/* Cards */}
      {pebble.cards.length > 0 && (
        <section className="mt-6" aria-labelledby="cards-heading">
          <h2 id="cards-heading" className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
            Cards
          </h2>
          <ol className="mt-3 space-y-4" role="list">
            {pebble.cards.map((card, index) => {
              const cardType = CARD_TYPES.find((c) => c.id === card.species_id)
              return (
                <li
                  key={`${card.species_id}-${index}`}
                  className="rounded-lg border border-border px-4 py-3"
                >
                  <h3 className="text-xs font-medium text-muted-foreground">
                    {cardType?.prompt ?? card.species_id}
                  </h3>
                  <p className="mt-1 text-sm whitespace-pre-wrap">{card.value}</p>
                </li>
              )
            })}
          </ol>
        </section>
      )}
    </article>
  )
}
