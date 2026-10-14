"use client"

import { useCallback, useState } from "react"
import { useRouter } from "next/navigation"
import { useTranslations } from "next-intl"
import type { Moment } from "@/lib/types"
import { useMoments } from "@/lib/data/useMoments"
import { detachedPaths, snapsAfterEdit } from "@/lib/data/moment-edit"
import { editMomentSavePayload } from "@/lib/data/moment-payloads"
import { momentErrorKey } from "@/lib/data/moment-write-error"
import { useMomentEditDraft } from "@/lib/hooks/useMomentEditDraft"
import { PebbleEditToolbar } from "@/components/pebble/PebbleEditToolbar"
import { PebbleEditTitle } from "@/components/pebble/PebbleEditTitle"
import { PebbleEditDate } from "@/components/pebble/PebbleEditDate"
import { PebbleEditDescription } from "@/components/pebble/PebbleEditDescription"
import { PebbleEditPicture, useSnapStaging } from "@/components/pebble/PebbleEditPicture"
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

type MomentEditProps = {
  moment: Moment
}

/**
 * Edit moment (R5): when, name, description and photo, saved through one
 * `save_moment`. Ported from iOS `EditMomentSheet`.
 *
 * The photo follows R6: removing it only detaches it in the form; a new pick
 * uploads at once; Save sends the whole snap set (`[]` when removed) so the
 * server checks "a name or a photo" in the same write, then the detached files
 * go best-effort. Cancel and a failed save keep the saved photo; Discard
 * removes only a new upload's files.
 */
export function MomentEdit({ moment }: MomentEditProps) {
  const t = useTranslations("pebble.edit")
  const tMoment = useTranslations("moment.editMoment")
  const tErrors = useTranslations("moment.errors")
  const router = useRouter()
  const { saveMoment, uploadSnap, removeSnapFiles } = useMoments()

  const form = useMomentEditDraft(moment)
  const { draft, originalSnaps } = form

  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [confirmDiscardOpen, setConfirmDiscardOpen] = useState(false)

  const snap = useSnapStaging({
    originalSnap: originalSnaps[0] ?? null,
    originalInstantUrl: form.originalInstantUrl,
    draftSnap: draft.snap,
    setDraftSnap: form.setSnap,
    uploadSnap,
    discardFiles: removeSnapFiles,
  })

  const readPage = `/pebble/${moment.id}`
  const canSave = form.isDirty && form.hasContent && !saving && !snap.uploading && !snap.failed

  const handleCancel = useCallback(() => {
    if (saving) return
    if (!form.isDirty && !snap.uploading) {
      router.replace(readPage)
      return
    }
    setConfirmDiscardOpen(true)
  }, [saving, form.isDirty, snap.uploading, router, readPage])

  const handleConfirmDiscard = useCallback(async () => {
    setConfirmDiscardOpen(false)
    await snap.cleanup()
    router.replace(readPage)
  }, [snap, router, readPage])

  const handleSave = useCallback(async () => {
    if (!canSave) return
    const snaps = snapsAfterEdit(originalSnaps, draft.snap)
    const payload = editMomentSavePayload(moment.id, {
      name: draft.name,
      description: draft.description,
      happenedAt: draft.happenedAt,
      snaps,
    })
    setSaving(true)
    setSaveError(null)
    try {
      await saveMoment(payload)
    } catch (err) {
      // R6: a failed save keeps every file, the new upload included, for a retry.
      console.error("[moment-edit] save_moment failed", err)
      setSaveError(tErrors(momentErrorKey(err)))
      setSaving(false)
      return
    }
    // The rows are gone; now the files, best-effort and without holding the form.
    void removeSnapFiles(detachedPaths(originalSnaps, snaps))
    router.replace(readPage)
  }, [canSave, originalSnaps, draft, moment.id, saveMoment, removeSnapFiles, router, readPage, tErrors])

  return (
    <article className="mx-auto max-w-md pb-12">
      <PebbleEditToolbar
        title={tMoment("title")}
        isSaving={saving}
        canSave={canSave}
        onCancel={handleCancel}
        onSave={() => void handleSave()}
      />

      <section className="mt-4 flex justify-center">
        <PebbleEditPicture
          displayUrl={snap.displayUrl}
          uploading={snap.uploading}
          failed={snap.failed}
          onPick={(file) => void snap.pickFile(file)}
          onRemove={() => void snap.remove()}
          className="pl-[25px]"
        />
      </section>

      <PebbleEditTitle value={draft.name} onChange={form.setName} className="mt-4" />

      <PebbleEditDate value={draft.happenedAt} onChange={form.setHappenedAt} className="mt-2" />

      <PebbleEditDescription value={draft.description} onChange={form.setDescription} className="mt-6" />

      {!form.hasContent && (
        <p className="mt-6 text-center text-sm text-muted-foreground">{tMoment("needsContent")}</p>
      )}

      {saveError && (
        <p
          role="alert"
          className="mt-6 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
        >
          {saveError}
        </p>
      )}

      <AlertDialog open={confirmDiscardOpen} onOpenChange={setConfirmDiscardOpen}>
        <AlertDialogContent size="sm">
          <AlertDialogHeader>
            <AlertDialogTitle>{t("discardTitle")}</AlertDialogTitle>
            <AlertDialogDescription>{t("discardDescription")}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t("discardKeep")}</AlertDialogCancel>
            <AlertDialogAction variant="destructive" onClick={() => void handleConfirmDiscard()}>
              {t("discardConfirm")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </article>
  )
}
