import type { Pebble, Soul, Collection, Mark } from "@/lib/types"
import type { UpdatePebbleInput, UpdateCollectionInput } from "@/lib/data/data-provider"
import { EMOTIONS, DOMAINS, CARD_TYPES } from "@/lib/config"
import { IntensityDots, PositivenessIndicator } from "@/components/pebble/PebbleIndicators"
import { DetailSection } from "@/components/pebble/DetailSection"
import { GlyphPreview } from "@/components/glyphs/GlyphPreview"
import { SoulSheet } from "@/components/pebble/SoulSheet"
import { DomainSheet } from "@/components/pebble/DomainSheet"
import { CollectionSheet } from "@/components/pebble/CollectionSheet"
import { GlyphSheet } from "@/components/pebble/GlyphSheet"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Plus } from "lucide-react"
import { dateTimeFormatter } from "@/lib/utils/formatters"

type PebbleDetailProps = {
  pebble: Pebble
  souls: Soul[]
  collections: Collection[]
  allCollections: Collection[]
  marks: Mark[]
  mark: Mark | undefined
  onUpdatePebble: (input: UpdatePebbleInput) => Promise<Pebble>
  onUpdateCollection: (id: string, input: UpdateCollectionInput) => Promise<Collection>
}

export function PebbleDetail({
  pebble,
  souls,
  collections,
  allCollections,
  marks,
  mark,
  onUpdatePebble,
  onUpdateCollection,
}: PebbleDetailProps) {
  const emotion = EMOTIONS.find((e) => e.id === pebble.emotion_id)
  const domains = DOMAINS.filter((d) => pebble.domain_ids.includes(d.id))
  const matchedSouls = pebble.soul_ids
    .map((id) => souls.find((s) => s.id === id))
    .filter((s): s is Soul => s !== undefined)

  const linkedCollectionIds = collections.map((c) => c.id)
  const formattedDate = dateTimeFormatter.format(new Date(pebble.happened_at))

  return (
    <article>
      {/* Header */}
      <header>
        <h1 className="text-2xl font-semibold">{pebble.name}</h1>

        <div className="mt-2 flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
          {emotion ? (
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
          ) : (
            <Button
              variant="ghost"
              size="xs"
              aria-label="Add emotion"
              disabled
            >
              <Plus data-icon="inline-start" />
              Emotion
            </Button>
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

      {/* Glyph */}
      <DetailSection
        id="glyph"
        title="Glyph"
        addTrigger={
          <GlyphSheet
            marks={marks}
            selectedMarkId={pebble.mark_id}
            onSave={(markId) => void onUpdatePebble({ mark_id: markId })}
          />
        }
      >
        {mark && (
          <GlyphPreview
            mark={mark}
            className="mt-2 h-20 w-20"
          />
        )}
      </DetailSection>

      {/* Instants */}
      {pebble.instants.length > 0 && (
        <DetailSection id="instants" title="Instants">
          <ul className="mt-2 grid grid-cols-3 gap-2" role="list" aria-label="Photo instants">
            {pebble.instants.map((uri, i) => (
              <li key={i}>
                <img
                  src={uri}
                  alt={`Instant ${i + 1}`}
                  className="aspect-square w-full rounded-lg object-cover"
                />
              </li>
            ))}
          </ul>
        </DetailSection>
      )}

      {/* Souls */}
      <DetailSection
        id="souls"
        title="Souls"
        addTrigger={
          <SoulSheet
            value={pebble.soul_ids}
            onSave={(ids) => void onUpdatePebble({ soul_ids: ids })}
          />
        }
      >
        {matchedSouls.length > 0 && (
          <ul className="mt-2 flex flex-wrap gap-2" role="list">
            {matchedSouls.map((soul) => (
              <li key={soul.id}>
                <Badge variant="outline">{soul.name}</Badge>
              </li>
            ))}
          </ul>
        )}
      </DetailSection>

      {/* Collections */}
      <DetailSection
        id="collections"
        title="Collections"
        addTrigger={
          <CollectionSheet
            pebbleId={pebble.id}
            allCollections={allCollections}
            linkedIds={linkedCollectionIds}
            onUpdateCollection={onUpdateCollection}
          />
        }
      >
        {collections.length > 0 && (
          <ul className="mt-2 flex flex-wrap gap-2" role="list">
            {collections.map((collection) => (
              <li key={collection.id}>
                <Badge variant="outline">{collection.name}</Badge>
              </li>
            ))}
          </ul>
        )}
      </DetailSection>

      {/* Domains */}
      <DetailSection
        id="domains"
        title="Domains"
        addTrigger={
          <DomainSheet
            value={pebble.domain_ids}
            onSave={(ids) => void onUpdatePebble({ domain_ids: ids })}
          />
        }
      >
        {domains.length > 0 && (
          <ul className="mt-2 flex flex-wrap gap-2" role="list">
            {domains.map((domain) => (
              <li key={domain.id}>
                <Badge variant="outline">{domain.name}</Badge>
              </li>
            ))}
          </ul>
        )}
      </DetailSection>

      {/* Cards */}
      <DetailSection id="cards" title="Cards">
        {pebble.cards.length > 0 && (
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
        )}
      </DetailSection>
    </article>
  )
}
