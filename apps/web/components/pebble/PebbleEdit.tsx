"use client"

import { useCallback, useState } from "react"
import { useRouter } from "next/navigation"
import { useTranslations } from "next-intl"
import type { Pebble, PebbleSnap, Soul, Collection, Mark } from "@/lib/types"
import { usePebbleDraft } from "@/lib/hooks/usePebbleDraft"
import type { UpdatePebbleInput } from "@/lib/data/data-provider"
import { PebbleFramed } from "@/components/pebble/PebbleFramed"
import {
  EmotionTile,
  DomainTile,
  CollectionTile,
} from "@/components/pebble/PebbleDetailTiles"
import { PebbleEditToolbar } from "@/components/pebble/PebbleEditToolbar"
import { PebbleEditTitle } from "@/components/pebble/PebbleEditTitle"
import { PebbleEditDescription } from "@/components/pebble/PebbleEditDescription"
import { PebbleEditDate } from "@/components/pebble/PebbleEditDate"
import {
  PebbleEditPicture,
  useSnapStaging,
} from "@/components/pebble/PebbleEditPicture"
import { PebbleEditSoulsGrid } from "@/components/pebble/PebbleEditSoulsGrid"
import { SheetTrigger } from "@/components/ui/sheet"
import { PickerSheet } from "@/components/ui/PickerSheet"
import { ValenceGrid } from "@/components/record/ValenceIntensityGrid"
import { EmotionPickerSheet } from "@/components/record/EmotionPicker"
import { SoulsSheet } from "@/components/record/SoulsSheet"
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

type PebbleEditProps = {
  pebble: Pebble
  souls: Soul[]
  collections: Collection[]
  marks: Mark[]
  mark: Mark | undefined
  onUpdatePebble: (input: UpdatePebbleInput) => Promise<Pebble>
  onUploadSnap: (file: File) => Promise<PebbleSnap>
  onDeletePebbleMedia: (snapId: string) => Promise<void>
  onAddSoul: (name: string) => Promise<Soul>
}

// Pebble edit screen. Holds the draft, drives the toolbar state machine,
// orchestrates the glyph picker chain (Valence → Emotion), the Souls sheet,
// and the picture upload coordinator. Commits via a single
// `compose-pebble-update` call on Save.
export function PebbleEdit({
  pebble,
  souls,
  collections,
  marks,
  mark,
  onUpdatePebble,
  onUploadSnap,
  onDeletePebbleMedia,
  onAddSoul,
}: PebbleEditProps) {
  const t = useTranslations("pebble.edit")
  const tPebble = useTranslations("pebble")
  const tValencePicker = useTranslations("record.valencePicker")
  const router = useRouter()

  const { draft, setField, setFields, isDirty, buildPayload } =
    usePebbleDraft(pebble)

  const [valenceOpen, setValenceOpen] = useState(false)
  const [emotionOpen, setEmotionOpen] = useState(false)
  const [soulsOpen, setSoulsOpen] = useState(false)
  const [confirmDiscardOpen, setConfirmDiscardOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState(false)

  const originalSnap = pebble.snaps[0] ?? null
  const originalInstantUrl = pebble.instants[0] ?? null

  const snap = useSnapStaging({
    originalSnap,
    originalInstantUrl,
    draftSnap: draft.snap,
    setDraftSnap: (next) => setField("snap", next),
    uploadSnap: onUploadSnap,
    deletePebbleMedia: onDeletePebbleMedia,
  })

  const canSave = isDirty && !saving && !snap.uploading && !snap.failed

  const handleCancel = useCallback(() => {
    if (saving) return
    if (!isDirty && !snap.uploading) {
      router.replace(`/pebble/${pebble.id}`)
      return
    }
    setConfirmDiscardOpen(true)
  }, [saving, isDirty, snap.uploading, router, pebble.id])

  const handleConfirmDiscard = useCallback(async () => {
    setConfirmDiscardOpen(false)
    await snap.cleanup()
    router.replace(`/pebble/${pebble.id}`)
  }, [snap, router, pebble.id])

  const handleSave = useCallback(async () => {
    if (!canSave) return
    const payload = buildPayload()
    setSaving(true)
    setSaveError(false)
    try {
      await onUpdatePebble(payload)
      // Successful save: the staged snap is now committed to the pebble row,
      // so it's no longer "uncommitted" — skip cleanup and navigate away.
      router.replace(`/pebble/${pebble.id}`)
    } catch (err) {
      console.error("[pebble-edit] save failed", err)
      setSaveError(true)
      setSaving(false)
      // Keep the draft and the staged snap intact so the user can retry.
    }
  }, [canSave, buildPayload, onUpdatePebble, router, pebble.id])

  const handleSoulToggle = useCallback(
    (id: string) => {
      const next = draft.soul_ids.includes(id)
        ? draft.soul_ids.filter((sid) => sid !== id)
        : [...draft.soul_ids, id]
      setField("soul_ids", next)
    },
    [draft.soul_ids, setField],
  )

  const handleAddSoul = useCallback(
    async (name: string) => {
      const soul = await onAddSoul(name)
      setField("soul_ids", [...draft.soul_ids, soul.id])
    },
    [onAddSoul, draft.soul_ids, setField],
  )

  // Glyph picker chain: tap the pebble visual → open the Valence picker;
  // on select, close it and open the Emotion picker prefilled with the new
  // valence/intensity; on emotion-pick, write everything into the draft in
  // one dispatch.
  const handleValenceSelect = (
    intensity: Pebble["intensity"],
    positiveness: Pebble["positiveness"],
  ) => {
    setFields({ intensity, positiveness })
    setValenceOpen(false)
    setEmotionOpen(true)
  }

  const handleEmotionPick = (id: string | undefined) => {
    if (id) setField("emotion_id", id)
    setEmotionOpen(false)
  }

  // Build a synthetic pebble for the visual so it reflects staged changes
  // (intensity/positiveness/emotion/glyph) before save.
  const draftPebble: Pebble = {
    ...pebble,
    intensity: draft.intensity,
    positiveness: draft.positiveness,
    emotion_id: draft.emotion_id,
    mark_id: draft.mark_id ?? undefined,
  }
  const draftMark = draft.mark_id
    ? marks.find((m) => m.id === draft.mark_id)
    : mark

  return (
    <article className="mx-auto max-w-md pb-12">
      <PebbleEditToolbar
        isSaving={saving}
        canSave={canSave}
        onCancel={handleCancel}
        onSave={handleSave}
      />

      {/* Hero: picture slot (left) + pebble visual (right) */}
      <section className="mt-4 flex items-start justify-center gap-3">
        <PebbleEditPicture
          displayUrl={snap.displayUrl}
          uploading={snap.uploading}
          failed={snap.failed}
          onPick={(file) => void snap.pickFile(file)}
          onRemove={() => void snap.remove()}
        />
        <PickerSheet
          open={valenceOpen}
          onOpenChange={setValenceOpen}
          title={tValencePicker("title")}
          closeLabel={tValencePicker("close")}
          trigger={
            <SheetTrigger
              aria-label={tPebble("editIntensityAria")}
              className="mt-4 cursor-pointer rounded-2xl outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <PebbleFramed
                pebble={draftPebble}
                mark={draftMark}
                tier="detail"
                className="size-24 rotate-[7.52deg]"
              />
            </SheetTrigger>
          }
        >
          {valenceOpen && (
            <ValenceGrid
              intensity={draft.intensity}
              valence={draft.positiveness}
              onSelect={handleValenceSelect}
            />
          )}
        </PickerSheet>
      </section>

      <PebbleEditTitle
        value={draft.name}
        onChange={(next) => setField("name", next)}
        className="mt-4"
      />

      <PebbleEditDate
        value={draft.happened_at}
        onChange={(next) => setField("happened_at", next)}
        className="mt-2"
      />

      <div className="mt-6 flex flex-wrap gap-3">
        <EmotionTile
          editing
          value={draft.emotion_id}
          onChange={(id) => setField("emotion_id", id)}
          className="min-w-[100px] flex-1"
        />
        <DomainTile
          editing
          value={draft.domain_ids}
          onChange={(ids) => setField("domain_ids", ids)}
          className="min-w-[100px] flex-1"
        />
        <CollectionTile
          editing
          value={draft.collection_ids}
          onChange={(ids) => setField("collection_ids", ids)}
          collections={collections}
          className="min-w-[100px] flex-1"
        />
      </div>

      <PebbleEditDescription
        value={draft.description}
        onChange={(next) => setField("description", next)}
        className="mt-6"
      />

      <PebbleEditSoulsGrid
        soulIds={draft.soul_ids}
        souls={souls}
        marks={marks}
        onAddRequest={() => setSoulsOpen(true)}
        onRemove={(id) =>
          setField(
            "soul_ids",
            draft.soul_ids.filter((sid) => sid !== id),
          )
        }
        className="mt-6"
      />

      {saveError && (
        <p
          role="alert"
          className="mt-6 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
        >
          {t("errorToast")}
        </p>
      )}

      <EmotionPickerSheet
        open={emotionOpen}
        onOpenChange={setEmotionOpen}
        value={draft.emotion_id || undefined}
        intensity={draft.intensity}
        valence={draft.positiveness}
        onChange={handleEmotionPick}
      />

      <SoulsSheet
        open={soulsOpen}
        onOpenChange={setSoulsOpen}
        selectedIds={draft.soul_ids}
        onToggle={handleSoulToggle}
        souls={souls}
        marks={marks}
        onAddSoul={async (name) => {
          await handleAddSoul(name)
        }}
      />

      <AlertDialog
        open={confirmDiscardOpen}
        onOpenChange={setConfirmDiscardOpen}
      >
        <AlertDialogContent size="sm">
          <AlertDialogHeader>
            <AlertDialogTitle>{t("discardTitle")}</AlertDialogTitle>
            <AlertDialogDescription>
              {t("discardDescription")}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t("discardKeep")}</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              onClick={() => void handleConfirmDiscard()}
            >
              {t("discardConfirm")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </article>
  )
}
