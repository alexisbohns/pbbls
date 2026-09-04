"use client"

import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import {
  CalendarDays,
  Check,
  Fingerprint,
  Image,
  Users,
} from "lucide-react"
import { useTranslations } from "next-intl"
import type { Pebble, PebbleSnap, Visibility } from "@/lib/types"
import { useFormatDate } from "@/lib/i18n"
import { usePebbles } from "@/lib/data/usePebbles"
import { useSouls } from "@/lib/data/useSouls"
import { useCollections } from "@/lib/data/useCollections"
import { useUsableGlyphs } from "@/lib/data/useUsableGlyphs"
import { useDataProvider } from "@/lib/data/provider-context"
import { useComposerDrafts } from "@/lib/hooks/useComposerDrafts"
import {
  applyDraftPayload,
  type ComposerState,
} from "@/components/record/draft-payload"
import type { PebbleDraftPayload } from "@/lib/data/data-provider"
import { Button } from "@/components/ui/button"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import { ValenceIntensityGrid } from "@/components/record/ValenceIntensityGrid"
import { CustomizationTile } from "@/components/record/CustomizationTile"
import { DomainSheet } from "@/components/record/DomainSheet"
import {
  EmotionPickerSheet,
  useSelectedEmotionDisplay,
} from "@/components/record/EmotionPicker"
import { CollectionSheet } from "@/components/record/CollectionSheet"
import { VisibilityPicker } from "@/components/record/VisibilityPicker"
import { DatePickerDialog } from "@/components/record/DatePickerDialog"
import { GlyphPickerDialog } from "@/components/record/GlyphPickerDialog"
import { SoulsSheet } from "@/components/record/SoulsSheet"
import { GlyphPreview } from "@/components/glyphs/GlyphPreview"
import { cn } from "@/lib/utils"

type Intensity = 1 | 2 | 3
type Valence = -1 | 0 | 1

type QuickPebbleEditorProps = {
  onPebbleCreated?: (pebble: Pebble) => void
  /**
   * Resume a server draft (M47). `draftId` is deleted once the pebble
   * publishes; `initialPayload` hydrates the form. Both come from /drafts.
   */
  draftId?: string
  initialPayload?: PebbleDraftPayload
  /** Fired after an intentional "save as draft" (the route navigates away). */
  onDraftSaved?: () => void
}

function isNow(dateStr: string): boolean {
  const diff = Math.abs(Date.now() - new Date(dateStr).getTime())
  return diff < 60_000
}

export function QuickPebbleEditor({
  onPebbleCreated,
  draftId,
  initialPayload,
  onDraftSaved,
}: QuickPebbleEditorProps) {
  const { addPebble, uploadSnap } = usePebbles()
  const { souls, addSoul } = useSouls()
  const { collections } = useCollections()
  const { glyphs: marks } = useUsableGlyphs()
  const { provider, loading: storeLoading } = useDataProvider()
  const t = useTranslations("record")
  const tDraft = useTranslations("record.draft")
  const tGlyph = useTranslations("record.glyph")
  const tPhoto = useTranslations("record.photo")
  const tSouls = useTranslations("record.souls")
  const tEmotion = useTranslations("record.emotion")
  const formatDate = useFormatDate()

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
  const [visibility, setVisibility] = useState<Visibility>("secret")
  const [saving, setSaving] = useState(false)
  // Publish failures were silent before M47 (no catch at all), which made
  // "delete the draft once it published" unimplementable. Surfaced inline, per
  // the PebbleEdit precedent.
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [savingDraft, setSavingDraft] = useState(false)

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

  // -------------------------------------------------------------------------
  // Drafts (M47). `state` is the single projection of the flat form state; the
  // server-draft path and the localStorage snapshot both derive from it.
  // -------------------------------------------------------------------------

  const state = useMemo<ComposerState>(
    () => ({
      name,
      description,
      happenedAt,
      intensity,
      valence,
      emotionId,
      domainIds,
      soulIds,
      markId,
      collectionIds,
      visibility,
      pendingSnap,
    }),
    [
      name, description, happenedAt, intensity, valence, emotionId,
      domainIds, soulIds, markId, collectionIds, visibility, pendingSnap,
    ],
  )

  const applyPayload = useCallback(
    (payload: PebbleDraftPayload) => {
      // Sanitize against what the user actually still owns (D7): a soul or
      // glyph deleted behind the draft's back costs a chip, not an opaque FK
      // error at publish time.
      const next = applyDraftPayload(payload, {
        soulIds: new Set(souls.map((s) => s.id)),
        collectionIds: new Set(collections.map((c) => c.id)),
        markIds: new Set(marks.map((m) => m.id)),
      })
      setName(next.name)
      setDescription(next.description)
      setHappenedAt(next.happenedAt)
      setIntensity(next.intensity)
      setValence(next.valence)
      setEmotionId(next.emotionId)
      setDomainIds(next.domainIds)
      setSoulIds(next.soulIds)
      setMarkId(next.markId)
      setCollectionIds(next.collectionIds)
      setVisibility(next.visibility)
      setPendingSnap(next.pendingSnap)
    },
    [souls, collections, marks],
  )

  // A restored draft's photo is already in Storage but has no `blob:` preview —
  // mint a signed URL so the photo tile shows it (mirrors the edit screen).
  useEffect(() => {
    if (!provider || !pendingSnap || snapPreview) return
    let cancelled = false
    void (async () => {
      const url = await provider.getDraftSnapUrl(pendingSnap.storage_path)
      if (!cancelled && url) setSnapPreview(url)
    })()
    return () => {
      cancelled = true
    }
  }, [provider, pendingSnap, snapPreview])

  const {
    isEmpty,
    restorePromptOpen,
    setRestorePromptOpen,
    restore: handleRestore,
    discardSnapshot: handleDiscardSnapshot,
    saveAsDraft,
    consumeAfterPublish,
  } = useComposerDrafts({
    draftId,
    initialPayload,
    state,
    applyPayload,
    ready: !storeLoading,
  })

  /** Intentional "save as draft" — ungated, unlike publishing (D5). */
  const handleSaveDraft = useCallback(async () => {
    if (isEmpty || savingDraft || saving || snapUploading) return
    setSavingDraft(true)
    setSubmitError(null)
    if (await saveAsDraft()) {
      resetForm()
      titleInputRef.current?.blur()
      onDraftSaved?.()
    } else {
      setSubmitError(tDraft("saveFailed"))
    }
    setSavingDraft(false)
  }, [
    isEmpty, savingDraft, saving, snapUploading,
    saveAsDraft, resetForm, onDraftSaved, tDraft,
  ])

  const handleSubmit = useCallback(async () => {
    if (!name.trim() || !emotionId || saving || snapUploading) return
    setSaving(true)
    setSubmitError(null)

    try {
      // A resumed draft keeps its captured timestamp (D6): only a genuinely
      // "now" value gets re-stamped at submit.
      const finalHappenedAt = isNow(happenedAt) ? new Date().toISOString() : happenedAt

      const input = {
        name: name.trim(),
        description: description.trim(),
        happened_at: finalHappenedAt,
        intensity,
        positiveness: valence,
        visibility,
        emotion_id: emotionId,
        soul_ids: soulIds,
        domain_ids: domainIds,
        collection_ids: collectionIds,
        mark_id: markId,
        snaps: pendingSnap ? [pendingSnap] : [],
        cards: [],
      }

      const pebble = await addPebble(input)

      // The pebble exists (`addPebble` resolves on soft-success too, keyed off
      // pebble_id), so the draft has served its purpose.
      await consumeAfterPublish()

      resetForm()
      titleInputRef.current?.blur()
      onPebbleCreated?.(pebble)
    } catch (err) {
      // Before M47 this was swallowed entirely: no catch, no toast, form left
      // as-is with no explanation.
      console.error("[quick-pebble-editor] create failed", err)
      setSubmitError(tDraft("publishFailed"))
    } finally {
      setSaving(false)
    }
  }, [
    name, description, happenedAt, intensity, valence, visibility,
    emotionId, soulIds, domainIds, markId, collectionIds, pendingSnap,
    saving, snapUploading, addPebble, resetForm, onPebbleCreated,
    consumeAfterPublish, tDraft,
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
      <section aria-label={t("editorAria")}>
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
          <DomainSheet value={domainIds} onChange={setDomainIds} />
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
          <CollectionSheet
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

        {submitError && (
          <p
            role="alert"
            className="mb-3 rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive"
          >
            {submitError}
          </p>
        )}

        {/* Footer */}
        <div className="flex items-center justify-between">
          <VisibilityPicker value={visibility} onChange={setVisibility} />
          <div className="flex items-center gap-2">
            {/* Quick capture: ungated, unlike publishing (D5). "Just a name" —
                or just a glyph — is a valid draft. */}
            <Button
              variant="ghost"
              size="sm"
              disabled={isEmpty || saving || savingDraft || snapUploading}
              onClick={() => void handleSaveDraft()}
              className="text-muted-foreground"
            >
              {tDraft("save")}
            </Button>
            <Button
              variant="default"
              size="icon"
              disabled={!name.trim() || !emotionId || saving || snapUploading}
              onClick={() => void handleSubmit()}
              aria-label={t("save")}
              className="size-9 rounded-full"
            >
              <Check className="size-5" aria-hidden />
            </Button>
          </div>
        </div>
      </section>

      {/* Crash-insurance restore. Only offered when not already resuming a
          server draft, and only once per mount. */}
      <AlertDialog open={restorePromptOpen} onOpenChange={setRestorePromptOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{tDraft("restoreTitle")}</AlertDialogTitle>
            <AlertDialogDescription>{tDraft("restoreDescription")}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={handleDiscardSnapshot}>
              {tDraft("restoreDiscard")}
            </AlertDialogCancel>
            <AlertDialogAction onClick={handleRestore}>
              {tDraft("restoreConfirm")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

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
        selectedMarkId={markId}
        onSave={setMarkId}
      />
      <SoulsSheet
        open={soulsOpen}
        onOpenChange={setSoulsOpen}
        selectedIds={soulIds}
        onToggle={toggleSoul}
        souls={souls}
        marks={marks}
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
