"use client"

import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import {
  CalendarDays,
  Check,
  Fingerprint,
  Image,
  Users,
} from "lucide-react"
import { motion, useReducedMotion } from "framer-motion"
import { usePebbles } from "@/lib/data/usePebbles"
import { usePebblesCount } from "@/lib/data/usePebblesCount"
import { useBounce } from "@/lib/data/useBounce"
import { todayLocal } from "@/lib/data/bounce-levels"
import { useSouls } from "@/lib/data/useSouls"
import { useCollections } from "@/lib/data/useCollections"
import { useMarks } from "@/lib/data/useMarks"
import { compressImage } from "@/lib/utils/image-compress"
import { Button } from "@/components/ui/button"
import { ValenceIntensityGrid } from "@/components/record/ValenceIntensityGrid"
import { CustomizationTile } from "@/components/record/CustomizationTile"
import { DomainPopover } from "@/components/record/DomainPopover"
import { EmotionPopover } from "@/components/record/EmotionPopover"
import { CollectionPopover } from "@/components/record/CollectionPopover"
import { VisibilityPicker } from "@/components/record/VisibilityPicker"
import { DatePickerDialog } from "@/components/record/DatePickerDialog"
import { GlyphPickerDialog } from "@/components/record/GlyphPickerDialog"
import { SoulsSheet } from "@/components/record/SoulsSheet"
import { GlyphPreview } from "@/components/glyphs/GlyphPreview"
import { cn } from "@/lib/utils"

type Intensity = 1 | 2 | 3
type Valence = -1 | 0 | 1

type QuickPebbleEditorProps = {
  onPebbleCreated?: (pebbleId: string) => void
}

function isNow(dateStr: string): boolean {
  const diff = Math.abs(Date.now() - new Date(dateStr).getTime())
  return diff < 60_000
}

export function QuickPebbleEditor({ onPebbleCreated }: QuickPebbleEditorProps) {
  const { addPebble } = usePebbles()
  const { souls, addSoul } = useSouls()
  const { collections, updateCollection } = useCollections()
  const { marks } = useMarks()
  const { pebblesCount, loading: countLoading } = usePebblesCount()
  const { bounceWindow, loading: bounceLoading } = useBounce()
  const prefersReducedMotion = useReducedMotion()

  // Collapse state
  const [expanded, setExpanded] = useState(false)
  const sectionRef = useRef<HTMLElement>(null)
  const titleInputRef = useRef<HTMLTextAreaElement>(null)
  const hasAutoExpanded = useRef(false)

  // Form state
  const [name, setName] = useState("")
  const [description, setDescription] = useState("")
  const [happenedAt, setHappenedAt] = useState(() => new Date().toISOString())
  const [intensity, setIntensity] = useState<Intensity>(2)
  const [valence, setValence] = useState<Valence>(0)
  const [emotionId, setEmotionId] = useState("")
  const [domainIds, setDomainIds] = useState<string[]>([])
  const [soulIds, setSoulIds] = useState<string[]>([])
  const [markId, setMarkId] = useState<string | undefined>(undefined)
  const [collectionIds, setCollectionIds] = useState<string[]>([])
  const [instant, setInstant] = useState<string | undefined>(undefined)
  const [visibility, setVisibility] = useState<"private" | "public">("private")
  const [saving, setSaving] = useState(false)

  // Dialog/sheet state
  const [dateOpen, setDateOpen] = useState(false)
  const [glyphOpen, setGlyphOpen] = useState(false)
  const [soulsOpen, setSoulsOpen] = useState(false)

  // File input ref
  const fileInputRef = useRef<HTMLInputElement>(null)

  // Auto-expand: new users (<5 pebbles) or no pebble created today
  const shouldAutoExpand = useMemo(() => {
    if (countLoading || bounceLoading) return false
    if (pebblesCount < 5) return true
    return !bounceWindow.includes(todayLocal())
  }, [pebblesCount, countLoading, bounceWindow, bounceLoading])

  useEffect(() => {
    if (hasAutoExpanded.current) return
    if (countLoading || bounceLoading) return
    hasAutoExpanded.current = true
    if (shouldAutoExpand) {
      setExpanded(true)
      requestAnimationFrame(() => titleInputRef.current?.focus())
    }
  }, [shouldAutoExpand, countLoading, bounceLoading])

  const selectedMark = marks.find((m) => m.id === markId)

  const resetForm = useCallback(() => {
    setName("")
    setDescription("")
    setHappenedAt(new Date().toISOString())
    setIntensity(2)
    setValence(0)
    setEmotionId("")
    setDomainIds([])
    setSoulIds([])
    setMarkId(undefined)
    setCollectionIds([])
    setInstant(undefined)
    setVisibility("private")
  }, [])

  const handleSubmit = useCallback(async () => {
    if (!name.trim() || saving) return
    setSaving(true)

    try {
      const finalHappenedAt = isNow(happenedAt) ? new Date().toISOString() : happenedAt

      const input = {
        name: name.trim(),
        description: description.trim(),
        happened_at: finalHappenedAt,
        intensity,
        positiveness: valence,
        visibility,
        emotion_id: emotionId || "serenity",
        soul_ids: soulIds,
        domain_ids: domainIds,
        mark_id: markId,
        instants: instant ? [instant] : [],
        cards: [],
      }

      const pebble = await addPebble(input)

      // Add pebble to selected collections
      for (const collId of collectionIds) {
        const coll = collections.find((c) => c.id === collId)
        if (coll) {
          await updateCollection(collId, {
            pebble_ids: [...coll.pebble_ids, pebble.id],
          })
        }
      }

      resetForm()
      setExpanded(false)
      titleInputRef.current?.blur()
      onPebbleCreated?.(pebble.id)
    } finally {
      setSaving(false)
    }
  }, [
    name, description, happenedAt, intensity, valence, visibility,
    emotionId, soulIds, domainIds, markId, collectionIds, instant,
    saving, addPebble, collections, updateCollection, resetForm, onPebbleCreated,
  ])

  const toggleSoul = useCallback((id: string) => {
    setSoulIds((prev) =>
      prev.includes(id) ? prev.filter((s) => s !== id) : [...prev, id],
    )
  }, [])

  const handleAddSoul = useCallback(async (soulName: string) => {
    const soul = await addSoul({ name: soulName })
    setSoulIds((prev) => [...prev, soul.id])
  }, [addSoul])

  const handleFileChange = useCallback(async (files: FileList | null) => {
    if (!files || files.length === 0) return
    const compressed = await compressImage(files[0])
    setInstant(compressed)
  }, [])

  // Focus tracking — expand on focus, collapse on blur when empty
  const handleFocusCapture = useCallback(() => {
    if (!expanded) setExpanded(true)
  }, [expanded])

  const handleBlurCapture = useCallback(() => {
    // rAF lets the browser settle focus after portal transitions
    requestAnimationFrame(() => {
      const active = document.activeElement
      if (active && sectionRef.current?.contains(active)) return
      if (active instanceof HTMLElement) {
        const inPortal = active.closest(
          '[data-slot="popover-content"], [data-slot="popover-positioner"], [data-slot="dialog-content"], [data-slot="sheet-content"], [role="dialog"]',
        )
        if (inPortal) return
      }
      if (!name.trim()) setExpanded(false)
    })
  }, [name])

  // Collapse/expand animation
  const collapsibleVariants = {
    expanded: { height: "auto", opacity: 1 },
    collapsed: { height: 0, opacity: 0 },
  }
  const collapsibleTransition = prefersReducedMotion
    ? { duration: 0 }
    : { height: { duration: 0.25, ease: "easeInOut" }, opacity: { duration: 0.15 } }

  const dateLabel = isNow(happenedAt)
    ? "Now"
    : new Intl.DateTimeFormat(undefined, {
        month: "short",
        day: "numeric",
        hour: "numeric",
        minute: "2-digit",
      }).format(new Date(happenedAt))

  return (
    <section
      ref={sectionRef}
      className={cn("rounded-xl border bg-card transition-[padding] duration-200", expanded ? "p-4" : "px-4 py-3")}
      aria-label="Pebble editor"
      onFocusCapture={handleFocusCapture}
      onBlurCapture={handleBlurCapture}
    >
      {/* Collapsible: header row above title */}
      <motion.div
        initial={false}
        animate={expanded ? "expanded" : "collapsed"}
        variants={collapsibleVariants}
        transition={collapsibleTransition}
        style={{ overflow: "hidden" }}
        aria-hidden={!expanded}
      >
        {/* Header: date + intensity/valence grid */}
        <div className="mb-3 flex items-center justify-between">
          <button
            type="button"
            onClick={() => {
              setDateOpen(true)
            }}
            className="inline-flex items-center gap-1.5 rounded-md border border-border bg-background px-2.5 py-1 text-xs font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
          >
            <CalendarDays className="size-3.5" aria-hidden />
            {dateLabel}
          </button>

          <ValenceIntensityGrid
            intensity={intensity}
            valence={valence}
            onIntensityChange={setIntensity}
            onValenceChange={setValence}
          />
        </div>
      </motion.div>

      {/* Title input — always visible */}
      <textarea
        ref={titleInputRef}
        value={name}
        onChange={(e) => setName(e.target.value)}
        placeholder="What happened?"
        className={cn(
          "w-full resize-none border-none bg-transparent font-heading text-xl font-semibold text-foreground outline-none field-sizing-content placeholder:text-muted-foreground/50",
          expanded && "mb-2",
        )}
        rows={1}
        onKeyDown={(e) => {
          if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault()
            void handleSubmit()
          }
        }}
      />

      {/* Collapsible: content below title */}
      <motion.div
        initial={false}
        animate={expanded ? "expanded" : "collapsed"}
        variants={collapsibleVariants}
        transition={collapsibleTransition}
        style={{ overflow: "hidden" }}
        aria-hidden={!expanded}
      >
      {/* Qualification pills: domain + emotion */}
      <div className="mb-3 flex items-center gap-2">
        <DomainPopover value={domainIds} onChange={setDomainIds} />
        <EmotionPopover value={emotionId} onChange={setEmotionId} />
      </div>

      {/* Description textarea */}
      <textarea
        value={description}
        onChange={(e) => setDescription(e.target.value)}
        placeholder="What was that awesome?"
        className="mb-4 w-full resize-none border-none bg-transparent text-sm text-foreground outline-none field-sizing-content placeholder:text-muted-foreground/50"
        rows={1}
      />

      {/* Customization tiles: glyph, collection, souls, photo */}
      <div className="mb-4 grid grid-cols-4 gap-2">
        {/* Glyph tile */}
        <CustomizationTile
          icon={Fingerprint}
          filled={!!selectedMark}
          onClick={() => setGlyphOpen(true)}
          ariaLabel={selectedMark ? "Change glyph" : "Add glyph"}
        >
          {selectedMark && (
            <GlyphPreview mark={selectedMark} className="size-full p-2" />
          )}
        </CustomizationTile>

        {/* Collection tile */}
        <CollectionPopover
          value={collectionIds}
          onChange={setCollectionIds}
          collections={collections}
        />

        {/* Souls tile */}
        <CustomizationTile
          icon={Users}
          filled={soulIds.length > 0}
          onClick={() => setSoulsOpen(true)}
          ariaLabel={soulIds.length > 0 ? `${soulIds.length} soul(s) selected` : "Add souls"}
        >
          {soulIds.length > 0 && (
            <span className="text-xs font-medium text-muted-foreground">
              {soulIds.length}
            </span>
          )}
        </CustomizationTile>

        {/* Photo tile */}
        <CustomizationTile
          icon={Image}
          filled={!!instant}
          onClick={() => {
            if (instant) {
              setInstant(undefined)
            } else {
              fileInputRef.current?.click()
            }
          }}
          ariaLabel={instant ? "Remove photo" : "Add photo"}
        >
          {instant && (
            /* eslint-disable-next-line @next/next/no-img-element -- base64 data URL, next/image optimization not applicable */
            <img
              src={instant}
              alt="Uploaded photo"
              className="size-full object-cover"
            />
          )}
        </CustomizationTile>
      </div>

      {/* Hidden file input for photo upload */}
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        className="hidden"
        aria-hidden="true"
        tabIndex={-1}
        onChange={(e) => {
          void handleFileChange(e.target.files)
          e.target.value = ""
        }}
      />

      {/* Footer: privacy picker + save button */}
      <div className="flex items-center justify-between">
        <VisibilityPicker value={visibility} onChange={setVisibility} />

        <Button
          variant="default"
          size="icon"
          disabled={!name.trim() || saving}
          onClick={() => void handleSubmit()}
          aria-label="Save pebble"
          className="size-9 rounded-full"
        >
          <Check className="size-5" aria-hidden />
        </Button>
      </div>
      </motion.div>

      {/* Date picker dialog */}
      <DatePickerDialog
        open={dateOpen}
        onOpenChange={setDateOpen}
        initialDate={new Date(happenedAt)}
        onSave={(date) => setHappenedAt(date.toISOString())}
      />

      {/* Glyph picker dialog */}
      <GlyphPickerDialog
        open={glyphOpen}
        onOpenChange={setGlyphOpen}
        marks={marks}
        selectedMarkId={markId}
        onSave={setMarkId}
      />

      {/* Souls picker sheet */}
      <SoulsSheet
        open={soulsOpen}
        onOpenChange={setSoulsOpen}
        selectedIds={soulIds}
        onToggle={toggleSoul}
        souls={souls}
        onAddSoul={handleAddSoul}
      />
    </section>
  )
}
