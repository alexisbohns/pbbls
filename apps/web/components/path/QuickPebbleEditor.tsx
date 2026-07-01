"use client"

import { useCallback, useEffect, useRef, useState } from "react"
import {
  CalendarDays,
  Check,
  Fingerprint,
  Image,
  Users,
} from "lucide-react"
import { AnimatePresence, motion, useReducedMotion } from "framer-motion"
import { useTranslations } from "next-intl"
import type { Pebble, PebbleSnap } from "@/lib/types"
import { useFormatDate } from "@/lib/i18n"
import { usePebbles } from "@/lib/data/usePebbles"
import { useSouls } from "@/lib/data/useSouls"
import { useCollections } from "@/lib/data/useCollections"
import { useUsableGlyphs } from "@/lib/data/useUsableGlyphs"
import { Button } from "@/components/ui/button"
import { ValenceIntensityGrid } from "@/components/record/ValenceIntensityGrid"
import { CustomizationTile } from "@/components/record/CustomizationTile"
import { DomainPopover } from "@/components/record/DomainPopover"
import {
  EmotionPickerSheet,
  useSelectedEmotionDisplay,
} from "@/components/record/EmotionPicker"
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
  expanded?: boolean
  onExpandedChange?: (next: boolean) => void
  onPebbleCreated?: (pebble: Pebble) => void
}

function isNow(dateStr: string): boolean {
  const diff = Math.abs(Date.now() - new Date(dateStr).getTime())
  return diff < 60_000
}

export function QuickPebbleEditor({
  expanded: expandedProp,
  onExpandedChange,
  onPebbleCreated,
}: QuickPebbleEditorProps) {
  const { addPebble, uploadSnap } = usePebbles()
  const { souls, addSoul } = useSouls()
  const { collections } = useCollections()
  const { glyphs: marks } = useUsableGlyphs()
  const prefersReducedMotion = useReducedMotion()
  const t = useTranslations("record")
  const tPath = useTranslations("path")
  const tGlyph = useTranslations("record.glyph")
  const tPhoto = useTranslations("record.photo")
  const tSouls = useTranslations("record.souls")
  const tEmotion = useTranslations("record.emotion")
  const formatDate = useFormatDate()

  // Collapse state — controlled-or-uncontrolled
  const [expandedInternal, setExpandedInternal] = useState(false)
  const isControlled = expandedProp !== undefined
  const expanded = isControlled ? expandedProp : expandedInternal
  const setExpanded = useCallback(
    (next: boolean) => {
      if (!isControlled) setExpandedInternal(next)
      onExpandedChange?.(next)
    },
    [isControlled, onExpandedChange],
  )
  const sectionRef = useRef<HTMLElement>(null)
  const titleInputRef = useRef<HTMLTextAreaElement>(null)

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
  // Snap upload state: `pendingSnap` is the uploaded descriptor we'll send in
  // the create payload; `snapPreview` is an object URL for in-form preview
  // (revoked on replace/unmount). Re-picking a photo leaves the previous
  // snap's storage files as orphans — same as iOS CreatePebbleSheet when the
  // sheet is dismissed without saving; the server-side sweep handles them.
  const [pendingSnap, setPendingSnap] = useState<PebbleSnap | undefined>(undefined)
  const [snapPreview, setSnapPreview] = useState<string | undefined>(undefined)
  const [snapUploading, setSnapUploading] = useState(false)
  const [visibility, setVisibility] = useState<"private" | "public">("private")
  const [saving, setSaving] = useState(false)

  // Dialog/sheet state
  const [dateOpen, setDateOpen] = useState(false)
  const [glyphOpen, setGlyphOpen] = useState(false)
  const [soulsOpen, setSoulsOpen] = useState(false)
  const [emotionPickerOpen, setEmotionPickerOpen] = useState(false)

  // File input ref
  const fileInputRef = useRef<HTMLInputElement>(null)

  const selectedMark = marks.find((m) => m.id === markId)
  const selectedEmotion = useSelectedEmotionDisplay(emotionId || undefined)

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
    setPendingSnap(undefined)
    setSnapPreview((prev) => {
      if (prev) URL.revokeObjectURL(prev)
      return undefined
    })
    setVisibility("private")
  }, [])

  // Revoke any outstanding object URL on unmount.
  useEffect(() => {
    return () => {
      if (snapPreview) URL.revokeObjectURL(snapPreview)
    }
  }, [snapPreview])

  const handleSubmit = useCallback(async () => {
    if (!name.trim() || saving || snapUploading) return
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
        collection_ids: collectionIds,
        mark_id: markId,
        snaps: pendingSnap ? [pendingSnap] : [],
        cards: [],
      }

      const pebble = await addPebble(input)

      resetForm()
      setExpanded(false)
      titleInputRef.current?.blur()
      onPebbleCreated?.(pebble)
    } finally {
      setSaving(false)
    }
  }, [
    name, description, happenedAt, intensity, valence, visibility,
    emotionId, soulIds, domainIds, markId, collectionIds, pendingSnap,
    saving, snapUploading, addPebble, resetForm, onPebbleCreated, setExpanded,
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
    const file = files[0]
    // Show a local preview immediately, then upload to storage and store the
    // returned snap descriptor for the create payload.
    setSnapPreview((prev) => {
      if (prev) URL.revokeObjectURL(prev)
      return URL.createObjectURL(file)
    })
    setSnapUploading(true)
    try {
      const snap = await uploadSnap(file)
      setPendingSnap(snap)
    } catch (err) {
      console.error("[quick-pebble-editor] snap upload failed", err)
      setSnapPreview((prev) => {
        if (prev) URL.revokeObjectURL(prev)
        return undefined
      })
      setPendingSnap(undefined)
    } finally {
      setSnapUploading(false)
    }
  }, [uploadSnap])

  const clearSnap = useCallback(() => {
    setSnapPreview((prev) => {
      if (prev) URL.revokeObjectURL(prev)
      return undefined
    })
    setPendingSnap(undefined)
  }, [])

  // Focus tracking — expand on focus, collapse on blur when empty
  const handleFocusCapture = useCallback(() => {
    if (!expanded) setExpanded(true)
  }, [expanded, setExpanded])

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
  }, [name, setExpanded])

  const dateLabel = isNow(happenedAt)
    ? t("now")
    : formatDate(happenedAt, {
        month: "short",
        day: "numeric",
        hour: "numeric",
        minute: "2-digit",
      })

  return (
    <>
      {/* Collapsed trigger — hidden while the overlay is open.
          Light mode: brand-light bg with brand accent label.
          Dark mode: brand foreground (near-white) bg with brand accent label. */}
      {!expanded && (
        <button
          type="button"
          onClick={() => setExpanded(true)}
          className="block w-full rounded-2xl bg-surface py-3 text-center font-heading text-[17px] font-bold text-primary transition-colors hover:bg-muted dark:bg-accent dark:text-primary"
        >
          {tPath("newPebble")}
        </button>
      )}

      {/* Expanded overlay */}
      <AnimatePresence>
        {expanded && (
          <>
            <motion.div
              key="backdrop"
              className="fixed inset-0 z-30 bg-background/60 backdrop-blur-sm"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: prefersReducedMotion ? 0 : 0.15 }}
              onClick={() => {
                if (!name.trim()) setExpanded(false)
              }}
            />
            <motion.section
              key="overlay"
              ref={sectionRef}
              aria-label={t("editorAria")}
              className="fixed inset-x-0 bottom-0 z-40 max-h-[min(72vh,640px)] overflow-y-auto rounded-t-2xl border-t bg-card p-4 pb-[calc(1rem+var(--safe-area-bottom))]"
              initial={{ y: "100%" }}
              animate={{ y: 0 }}
              exit={{ y: "100%" }}
              transition={{ duration: prefersReducedMotion ? 0 : 0.25, ease: "easeOut" }}
              onFocusCapture={handleFocusCapture}
              onBlurCapture={handleBlurCapture}
            >
              {/* Header: date + intensity/valence grid */}
              <div className="mb-3 flex items-center justify-between">
                <button
                  type="button"
                  onClick={() => setDateOpen(true)}
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

              {/* Title input */}
              <textarea
                ref={titleInputRef}
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder={t("namePlaceholder")}
                className="mb-2 w-full resize-none border-none bg-transparent font-heading text-xl font-semibold text-foreground outline-none field-sizing-content placeholder:text-muted-foreground/50"
                rows={1}
                onKeyDown={(e) => {
                  if (e.key === "Enter" && !e.shiftKey) {
                    e.preventDefault()
                    void handleSubmit()
                  }
                }}
              />

              {/* Qualification pills */}
              <div className="mb-3 flex items-center gap-2">
                <DomainPopover value={domainIds} onChange={setDomainIds} />
                <button
                  type="button"
                  onClick={() => setEmotionPickerOpen(true)}
                  aria-label={
                    selectedEmotion
                      ? tEmotion("selectedAria", { name: selectedEmotion.name })
                      : tEmotion("pickAria")
                  }
                  className={cn(
                    "inline-flex items-center gap-1.5 rounded-md px-2.5 py-1 text-xs font-medium transition-colors",
                    selectedEmotion
                      ? "border border-border bg-background text-foreground"
                      : "border border-dashed border-muted-foreground/30 text-muted-foreground hover:border-muted-foreground/50",
                  )}
                >
                  {selectedEmotion ? (
                    <>
                      <span aria-hidden>{selectedEmotion.emoji}</span>
                      {selectedEmotion.name}
                    </>
                  ) : (
                    tEmotion("label")
                  )}
                </button>
              </div>

              {/* Description */}
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder={t("descriptionPlaceholder")}
                className="mb-4 w-full resize-none border-none bg-transparent text-sm text-foreground outline-none field-sizing-content placeholder:text-muted-foreground/50"
                rows={1}
              />

              {/* Customization tiles */}
              <div className="mb-4 grid grid-cols-4 gap-2">
                <CustomizationTile
                  icon={Fingerprint}
                  filled={!!selectedMark}
                  onClick={() => setGlyphOpen(true)}
                  ariaLabel={selectedMark ? tGlyph("changeAria") : tGlyph("addAria")}
                >
                  {selectedMark && <GlyphPreview mark={selectedMark} className="size-full p-2" />}
                </CustomizationTile>
                <CollectionPopover
                  value={collectionIds}
                  onChange={setCollectionIds}
                  collections={collections}
                />
                <CustomizationTile
                  icon={Users}
                  filled={soulIds.length > 0}
                  onClick={() => setSoulsOpen(true)}
                  ariaLabel={soulIds.length > 0 ? tSouls("selectedAria", { count: soulIds.length }) : tSouls("addAria")}
                >
                  {soulIds.length > 0 && (
                    <span className="text-xs font-medium text-muted-foreground">{soulIds.length}</span>
                  )}
                </CustomizationTile>
                <CustomizationTile
                  icon={Image}
                  filled={!!snapPreview}
                  onClick={() => {
                    if (snapPreview) {
                      clearSnap()
                    } else {
                      fileInputRef.current?.click()
                    }
                  }}
                  ariaLabel={snapPreview ? tPhoto("removeAria") : tPhoto("addAria")}
                >
                  {snapPreview && (
                    /* eslint-disable-next-line @next/next/no-img-element -- object URL */
                    <img src={snapPreview} alt={tPhoto("alt")} className="size-full object-cover" />
                  )}
                </CustomizationTile>
              </div>

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

              {/* Footer */}
              <div className="flex items-center justify-between">
                <VisibilityPicker value={visibility} onChange={setVisibility} />
                <Button
                  variant="default"
                  size="icon"
                  disabled={!name.trim() || saving || snapUploading}
                  onClick={() => void handleSubmit()}
                  aria-label={t("save")}
                  className="size-9 rounded-full"
                >
                  <Check className="size-5" aria-hidden />
                </Button>
              </div>
            </motion.section>
          </>
        )}
      </AnimatePresence>

      {/* Dialogs / sheets (unchanged) */}
      <DatePickerDialog
        open={dateOpen}
        onOpenChange={setDateOpen}
        initialDate={new Date(happenedAt)}
        onSave={(date) => setHappenedAt(date.toISOString())}
      />
      <GlyphPickerDialog
        open={glyphOpen}
        onOpenChange={setGlyphOpen}
        marks={marks}
        selectedMarkId={markId}
        onSave={setMarkId}
      />
      <SoulsSheet
        open={soulsOpen}
        onOpenChange={setSoulsOpen}
        selectedIds={soulIds}
        onToggle={toggleSoul}
        souls={souls}
        onAddSoul={handleAddSoul}
      />
      <EmotionPickerSheet
        open={emotionPickerOpen}
        onOpenChange={setEmotionPickerOpen}
        value={emotionId || undefined}
        intensity={intensity}
        valence={valence}
        onChange={(id) => setEmotionId(id ?? "")}
      />
    </>
  )
}
