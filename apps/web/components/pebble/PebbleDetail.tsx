"use client"

import { useState, useCallback, useRef } from "react"
import { Lock, SquarePen, X } from "lucide-react"
import { useTranslations } from "next-intl"
import type { Pebble, PebbleSnap, Soul, Collection, Mark } from "@/lib/types"
import type { UpdatePebbleInput } from "@/lib/data/data-provider"
import { useFormatPeekDate } from "@/lib/i18n"
import { useEmotionPalettes } from "@/lib/data/useEmotionPalettes"
import { PebbleVisual } from "@/components/pebble/PebbleVisual"
import {
  EmotionTile,
  DomainTile,
  CollectionTile,
} from "@/components/pebble/PebbleDetailTiles"
import { PebbleDetailSoulsGrid } from "@/components/pebble/PebbleDetailSoulsGrid"
import { PebbleDetailAddToolbar } from "@/components/pebble/PebbleDetailAddToolbar"
import { Sheet } from "@/components/ui/sheet"
import { ValencePickerBody } from "@/components/record/ValenceIntensityGrid"
import { SoulsSheet } from "@/components/record/SoulsSheet"
import { cn } from "@/lib/utils"

type PebbleDetailProps = {
  pebble: Pebble
  souls: Soul[]
  collections: Collection[]
  marks: Mark[]
  mark: Mark | undefined
  onUpdatePebble: (input: UpdatePebbleInput) => Promise<Pebble>
  onUploadSnap: (file: File) => Promise<PebbleSnap>
  onAddSoul: (name: string) => Promise<void>
  onClose?: () => void
  onEdit?: () => void
}

export function PebbleDetail({
  pebble,
  souls,
  collections,
  marks,
  mark,
  onUpdatePebble,
  onUploadSnap,
  onAddSoul,
  onClose,
  onEdit,
}: PebbleDetailProps) {
  const [valenceOpen, setValenceOpen] = useState(false)
  const [soulsOpen, setSoulsOpen] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const t = useTranslations("pebble")
  const tVisibility = useTranslations("record.visibility")
  const formatPeekDate = useFormatPeekDate()
  const formattedDate = formatPeekDate(pebble.happened_at)
  const { paletteByEmotionId } = useEmotionPalettes()
  const palette = paletteByEmotionId.get(pebble.emotion_id)

  const matchedSouls = pebble.soul_ids
    .map((id) => souls.find((s) => s.id === id))
    .filter((s): s is Soul => s !== undefined)
  const snap = pebble.instants[0]
  const hasSouls = matchedSouls.length > 0
  const hasCollection = pebble.collection_ids.length > 0
  const hasPicture = !!snap

  const handleEmotionChange = useCallback(
    (id: string) => {
      void onUpdatePebble({ emotion_id: id })
    },
    [onUpdatePebble],
  )

  const handleDomainChange = useCallback(
    (ids: string[]) => {
      void onUpdatePebble({ domain_ids: ids })
    },
    [onUpdatePebble],
  )

  const handleCollectionChange = useCallback(
    (newIds: string[]) => {
      void onUpdatePebble({ collection_ids: newIds })
    },
    [onUpdatePebble],
  )

  const handleAddCollection = useCallback(
    (id: string) => {
      void onUpdatePebble({ collection_ids: [...pebble.collection_ids, id] })
    },
    [onUpdatePebble, pebble.collection_ids],
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
      try {
        const uploaded = await onUploadSnap(files[0])
        await onUpdatePebble({ snaps: [uploaded] })
      } catch (err) {
        console.error("[pebble-detail] snap upload failed", err)
      } finally {
        // Reset so picking the same file twice still fires onChange.
        e.target.value = ""
      }
    },
    [onUploadSnap, onUpdatePebble],
  )

  // Emotion palette drives the boxed pebble overlay when a snap is shown.
  // Secondary as background; pebble stroke is forced to `light_color` via the
  // PebbleVisual override so the pebble reads against the tinted box in dark
  // mode (where the default stroke is `secondary_color` — same hue as the
  // box).
  const overlayBackground = palette?.secondary_color
  const overlayStroke = palette?.light_color

  return (
    <article>
      {/* Top bar: static privacy indicator + edit/close buttons */}
      <header className="flex items-center justify-between">
        <span
          aria-label={
            pebble.visibility === "private"
              ? tVisibility("ariaPrivate")
              : tVisibility("ariaPublic")
          }
          className={cn(
            "grid size-10 place-items-center rounded-full bg-surface text-muted-foreground",
            pebble.visibility !== "private" && "opacity-0",
          )}
        >
          <Lock className="size-4" aria-hidden />
        </span>
        <div className="flex items-center gap-2">
          {onEdit && (
            <button
              type="button"
              onClick={onEdit}
              aria-label={t("editAria")}
              className="grid size-10 place-items-center rounded-full bg-surface text-muted-foreground transition-colors hover:bg-primary hover:text-surface active:scale-95 outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <SquarePen className="size-4" aria-hidden />
            </button>
          )}
          {onClose ? (
            <button
              type="button"
              onClick={onClose}
              aria-label={t("peek.close")}
              className="grid size-10 place-items-center rounded-full bg-surface text-muted-foreground transition-colors hover:bg-primary hover:text-surface active:scale-95 outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <X className="size-4" aria-hidden />
            </button>
          ) : (
            <span className="size-10" aria-hidden />
          )}
        </div>
      </header>

      {/* Pebble visual (and snap if present) — opens valence/intensity sheet */}
      <Sheet open={valenceOpen} onOpenChange={setValenceOpen}>
        <button
          type="button"
          onClick={() => setValenceOpen(true)}
          className="mx-auto mt-2 mb-4 block cursor-pointer outline-none focus-visible:ring-2 focus-visible:ring-ring rounded-2xl"
          aria-label={t("editIntensityAria")}
        >
          {hasPicture ? (
            <div className="relative mx-auto w-[240px]">
              {/* eslint-disable-next-line @next/next/no-img-element -- base64 data URL, next/image optimization not applicable */}
              <img
                src={snap}
                alt={t("photoAlt")}
                className="aspect-square w-full rounded-2xl object-cover scale-90 -rotate-4"
              />
              <span
                className="absolute -right-4 -top-4 grid size-[100px] place-items-center rounded-2xl shadow-md rotate-7"
                style={overlayBackground ? { backgroundColor: overlayBackground } : undefined}
              >
                <PebbleVisual
                  pebble={pebble}
                  mark={mark}
                  tier="detail"
                  className="size-[72px]"
                  strokeOverride={overlayStroke}
                />
              </span>
            </div>
          ) : (
            <PebbleVisual
              pebble={pebble}
              mark={mark}
              tier="detail"
              className="mx-auto size-[100px]"
            />
          )}
        </button>
        {valenceOpen && (
          <ValencePickerBody
            intensity={pebble.intensity}
            valence={pebble.positiveness}
            onSelect={(size, polarity) => {
              void onUpdatePebble({ intensity: size, positiveness: polarity })
              setValenceOpen(false)
            }}
          />
        )}
      </Sheet>

      {/* Title */}
      <h1 className="text-center font-heading text-2xl font-semibold text-foreground">
        {pebble.name}
      </h1>

      {/* Date */}
      <p className="mt-2 text-center text-xs font-medium uppercase tracking-wider text-muted-foreground">
        <time dateTime={pebble.happened_at}>{formattedDate}</time>
      </p>

      {/* Tile row: emotion + domain + collection (if any) */}
      <div
        className={cn(
          "mt-6 grid gap-3",
          hasCollection ? "grid-cols-3" : "grid-cols-2",
        )}
      >
        <EmotionTile value={pebble.emotion_id} onChange={handleEmotionChange} />
        <DomainTile value={pebble.domain_ids} onChange={handleDomainChange} />
        {hasCollection && (
          <CollectionTile
            value={pebble.collection_ids}
            onChange={handleCollectionChange}
            collections={collections}
          />
        )}
      </div>

      {/* Description */}
      {pebble.description && (
        <p className="mt-6 whitespace-pre-wrap text-base text-foreground leading-[1.4]">
          {pebble.description}
        </p>
      )}

      {/* Souls grid */}
      {hasSouls && (
        <div className="mt-6">
          <PebbleDetailSoulsGrid
            souls={matchedSouls}
            marks={marks}
            onOpenSoulsSheet={() => setSoulsOpen(true)}
          />
        </div>
      )}

      {/* Add toolbar — only renders if something is missing */}
      <div className="mt-6">
        <PebbleDetailAddToolbar
          showSoul={!hasSouls}
          showStack={!hasCollection}
          showPicture={!hasPicture}
          collections={collections}
          onAddCollection={handleAddCollection}
          onOpenSoulsSheet={() => setSoulsOpen(true)}
          onTriggerPhotoUpload={() => fileInputRef.current?.click()}
        />
      </div>

      {/* Hidden file input for photo upload */}
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={(e) => void handleFileChange(e)}
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
