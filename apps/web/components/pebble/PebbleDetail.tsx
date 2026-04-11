"use client"

import { useState, useCallback, useRef } from "react"
import type { Pebble, Soul, Collection, Mark } from "@/lib/types"
import type { UpdatePebbleInput, UpdateCollectionInput } from "@/lib/data/data-provider"
import { CARD_TYPES } from "@/lib/config"
import { PebbleVisual } from "@/components/pebble/PebbleVisual"
import { PeekTagChips } from "@/components/pebble/PeekTagChips"
import { PeekBottomBar } from "@/components/pebble/PeekBottomBar"
import { ValenceIntensityGrid } from "@/components/record/ValenceIntensityGrid"
import { GlyphPickerDialog } from "@/components/record/GlyphPickerDialog"
import { DatePickerDialog } from "@/components/record/DatePickerDialog"
import { SoulsSheet } from "@/components/record/SoulsSheet"
import { formatPeekDate } from "@/lib/utils/formatters"
import { compressImage } from "@/lib/utils/image-compress"

type PebbleDetailProps = {
  pebble: Pebble
  souls: Soul[]
  collections: Collection[]
  allCollections: Collection[]
  marks: Mark[]
  mark: Mark | undefined
  onUpdatePebble: (input: UpdatePebbleInput) => Promise<Pebble>
  onUpdateCollection: (id: string, input: UpdateCollectionInput) => Promise<Collection>
  onAddSoul: (name: string) => Promise<void>
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
  onAddSoul,
}: PebbleDetailProps) {
  const [glyphOpen, setGlyphOpen] = useState(false)
  const [dateOpen, setDateOpen] = useState(false)
  const [soulsOpen, setSoulsOpen] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const formattedDate = formatPeekDate(new Date(pebble.happened_at))

  const handleGlyphSave = useCallback(
    (markId: string | undefined) => {
      void onUpdatePebble({ mark_id: markId })
      setGlyphOpen(false)
    },
    [onUpdatePebble],
  )

  const handleDateSave = useCallback(
    (date: Date) => {
      void onUpdatePebble({ happened_at: date.toISOString() })
      setDateOpen(false)
    },
    [onUpdatePebble],
  )

  const handleSoulToggle = useCallback(
    (id: string) => {
      const newIds = pebble.soul_ids.includes(id)
        ? pebble.soul_ids.filter((sid) => sid !== id)
        : [...pebble.soul_ids, id]
      void onUpdatePebble({ soul_ids: newIds })
    },
    [pebble.soul_ids, onUpdatePebble],
  )

  const handleFileChange = useCallback(
    async (e: React.ChangeEvent<HTMLInputElement>) => {
      const files = e.target.files
      if (!files || files.length === 0) return
      const compressed = await compressImage(files[0])
      void onUpdatePebble({ instants: [compressed] })
    },
    [onUpdatePebble],
  )

  return (
    <article>
      {/* Clickable pebble visual → glyph picker */}
      <button
        type="button"
        onClick={() => setGlyphOpen(true)}
        className="mx-auto mb-4 block cursor-pointer"
        aria-label="Change glyph"
      >
        <PebbleVisual
          pebble={pebble}
          mark={mark}
          tier="detail"
          className="size-[160px]"
        />
      </button>

      {/* Title */}
      <h1 className="text-2xl font-heading font-semibold text-muted-foreground text-center">
        {pebble.name}
      </h1>

      {/* Clickable date → date picker */}
      <button
        type="button"
        onClick={() => setDateOpen(true)}
        className="mx-auto mt-2 block cursor-pointer text-xs uppercase tracking-wider text-muted-foreground transition-colors hover:text-foreground"
      >
        <time dateTime={pebble.happened_at}>{formattedDate}</time>
      </button>

      {/* Intensity / Valence grid */}
      <div className="mt-2 flex justify-center">
        <ValenceIntensityGrid
          intensity={pebble.intensity}
          valence={pebble.positiveness}
          onIntensityChange={(v) => void onUpdatePebble({ intensity: v })}
          onValenceChange={(v) => void onUpdatePebble({ positiveness: v })}
        />
      </div>

      {/* Tag chips: emotion, domain, collection */}
      <div className="mt-4">
        <PeekTagChips
          pebble={pebble}
          collections={collections}
          allCollections={allCollections}
          onUpdatePebble={onUpdatePebble}
          onUpdateCollection={onUpdateCollection}
        />
      </div>

      {/* Single photo */}
      {pebble.instants[0] && (
        /* eslint-disable-next-line @next/next/no-img-element -- base64 data URL, next/image optimization not applicable */
        <img
          src={pebble.instants[0]}
          alt="Pebble photo"
          className="mt-4 w-full rounded-xl object-cover"
        />
      )}

      {/* Description */}
      {pebble.description && (
        <p className="mt-4 text-sm text-foreground">{pebble.description}</p>
      )}

      {/* Cards */}
      {pebble.cards.length > 0 && (
        <ol className="mt-4 space-y-3" role="list">
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

      {/* Bottom bar */}
      <PeekBottomBar
        pebble={pebble}
        souls={souls}
        onOpenSoulsSheet={() => setSoulsOpen(true)}
        onTriggerPhotoUpload={() => fileInputRef.current?.click()}
      />

      {/* Hidden file input for photo upload */}
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={(e) => void handleFileChange(e)}
      />

      {/* Controlled dialogs */}
      <GlyphPickerDialog
        open={glyphOpen}
        onOpenChange={setGlyphOpen}
        marks={marks}
        selectedMarkId={pebble.mark_id}
        onSave={handleGlyphSave}
      />
      <DatePickerDialog
        open={dateOpen}
        onOpenChange={setDateOpen}
        initialDate={new Date(pebble.happened_at)}
        onSave={handleDateSave}
      />
      <SoulsSheet
        open={soulsOpen}
        onOpenChange={setSoulsOpen}
        selectedIds={pebble.soul_ids}
        onToggle={handleSoulToggle}
        souls={souls}
        onAddSoul={onAddSoul}
      />
    </article>
  )
}
