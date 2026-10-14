"use client"

import { useCallback, useEffect, useMemo, useReducer, useRef, useState } from "react"
import { useRouter } from "next/navigation"
import { useTranslations } from "next-intl"
import { useMoments } from "@/lib/data/useMoments"
import { captureSavePayload } from "@/lib/data/moment-payloads"
import { momentErrorKey } from "@/lib/data/moment-write-error"
import { useCapturePhoto } from "@/lib/hooks/useCapturePhoto"
import { useRetiredComposerDraftCleanup } from "@/lib/hooks/useRetiredComposerDraftCleanup"
import { useRecordFlowHistory } from "@/lib/hooks/useRecordFlowHistory"
import {
  CAPTURE_STEPS,
  canSave,
  captureReducer,
  hasAnythingToKeep,
  initialCaptureState,
  photoButtonIsSkip,
  type CaptureStep,
} from "@/lib/record/capture"
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import { Button } from "@/components/ui/button"
import { RecordFlowChrome } from "@/components/record/flow/RecordFlowChrome"
import {
  RecordStepScaffold,
  type RecordStepAction,
  type RecordStepSecondaryAction,
} from "@/components/record/flow/RecordStepScaffold"
import { CaptureStepContent } from "./CaptureStepContent"
import { useCaptureStepCopy } from "./capture-step-copy"

/** Which button started the save in flight, so only that one spins. */
type Saving = "save" | "complete" | null

/**
 * Capture (#1050): photo · when · name → one `save_moment`, the way to record
 * on web. Ported from iOS `CaptureFlowView`.
 *
 * "Save moment" lands on the Path at the new moment's week (R14); "Make it a
 * pebble now" saves, then replaces this page with Complete for the moment
 * (PD-W5), so Back never returns to an already-saved capture. ✕ asks only when
 * there is something to keep (R2). A failed save keeps the photo for the retry.
 *
 * Orchestration only: the order, the clamp and the two gates live in the pure
 * reducer (`lib/record/capture.ts`), the photo in `useCapturePhoto`.
 */
export function CaptureFlow() {
  const t = useTranslations("record.flow")
  const tCapture = useTranslations("record.capture")
  const tPhoto = useTranslations("record.flow.photo")
  const tErrors = useTranslations("moment.errors")
  const router = useRouter()

  const { saveMoment, uploadSnap, removeSnapFiles } = useMoments()

  const [state, dispatch] = useReducer(captureReducer, undefined, () => initialCaptureState())
  const [saving, setSaving] = useState<Saving>(null)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [closeConfirmOpen, setCloseConfirmOpen] = useState(false)

  // A save outlives the page when the user leaves mid-flight (browser Back
  // past the flow, a tab link): its resolution must not then yank them off
  // wherever they went.
  const mountedRef = useRef(true)
  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
    }
  }, [])

  useRetiredComposerDraftCleanup()

  const photo = useCapturePhoto({
    uploadSnap,
    removeSnapFiles,
    onPhoto: dispatch,
    onCaptureDate: (value) => dispatch({ type: "applyCaptureDate", value }),
    locked: saving !== null,
  })

  const goTo = useCallback((step: CaptureStep) => dispatch({ type: "goTo", step }), [])
  const { goBack } = useRecordFlowHistory(CAPTURE_STEPS, state.step, goTo, {
    locked: saving !== null,
  })

  const savable = canSave(state, saving !== null)

  /** Non-null while the attached photo blocks saving. */
  const blockedMessage = useMemo(() => {
    if (state.photo === "picking" || state.photo === "uploading") return tPhoto("blockedUploading")
    if (state.photo === "failed") return tPhoto("blockedFailed")
    return null
  }, [state.photo, tPhoto])

  // ---------------------------------------------------------------------------
  // Saving
  // ---------------------------------------------------------------------------

  const save = useCallback(
    async (then: "path" | "complete") => {
      if (!canSave(state, saving !== null)) return
      setSaving(then === "complete" ? "complete" : "save")
      setSaveError(null)
      let momentId: string
      try {
        momentId = await saveMoment(
          captureSavePayload({ name: state.name, happenedAt: state.happenedAt, snap: state.snap }),
        )
      } catch (err) {
        // The upload stays for the retry. A save can start from the close
        // dialog on any step, and its error lives on the name step.
        console.error("[capture] save_moment failed", err)
        dispatch({ type: "goTo", step: "name" })
        setSaveError(tErrors(momentErrorKey(err)))
        setSaving(null)
        return
      }
      // The photo is the moment's now. `saving` stays set while the page
      // leaves, so nothing can be pressed twice.
      photo.commit()
      if (!mountedRef.current) return
      const id = momentId.toLowerCase()
      if (then === "complete") router.replace(`/record/complete/${id}?from=capture`)
      else router.push(`/path?focus=${id}`)
    },
    [state, saving, saveMoment, photo, router, tErrors],
  )

  // ---------------------------------------------------------------------------
  // Leaving
  // ---------------------------------------------------------------------------

  /** ✕ asks only when there is something to keep (R2), and never mid-save. */
  const handleClose = useCallback(() => {
    if (saving !== null) return
    if (!hasAnythingToKeep(state)) {
      router.push("/path")
      return
    }
    setCloseConfirmOpen(true)
  }, [saving, state, router])

  /** The only path that deletes the uploaded photo's files on purpose. */
  const handleDiscard = useCallback(() => {
    setCloseConfirmOpen(false)
    photo.discard()
    router.push("/path")
  }, [photo, router])

  const handleSaveFromDialog = useCallback(() => {
    setCloseConfirmOpen(false)
    void save("path")
  }, [save])

  // ---------------------------------------------------------------------------
  // Render
  // ---------------------------------------------------------------------------

  const copy = useCaptureStepCopy(state.step)

  const action = ((): RecordStepAction => {
    switch (state.step) {
      case "photo":
        return {
          kind: "text",
          label: photoButtonIsSkip(state) ? t("skip") : t("done"),
          onPress: () => dispatch({ type: "advance" }),
        }
      case "when":
        return {
          kind: "primary",
          label: t("continue"),
          enabled: true,
          onPress: () => dispatch({ type: "advance" }),
        }
      case "name":
        return {
          kind: "primary",
          label: tCapture("save"),
          enabled: savable,
          loading: saving === "save",
          onPress: () => void save("path"),
        }
    }
  })()

  const secondary: RecordStepSecondaryAction | undefined =
    state.step === "name"
      ? {
          label: tCapture("makePebbleNow"),
          enabled: savable,
          loading: saving === "complete",
          onPress: () => void save("complete"),
        }
      : undefined

  return (
    <>
      <section
        aria-label={tCapture("aria")}
        className="mx-auto flex min-h-[calc(100dvh-4rem-var(--safe-area-top)-var(--safe-area-bottom))] w-full max-w-lg flex-col gap-4"
      >
        <RecordFlowChrome
          steps={CAPTURE_STEPS}
          counted={CAPTURE_STEPS}
          step={state.step}
          onBack={goBack}
          onClose={handleClose}
          backDisabled={saving !== null}
        />

        <RecordStepScaffold
          title={copy.title}
          subtitle={copy.subtitle}
          action={action}
          secondary={secondary}
        >
          <CaptureStepContent
            state={state}
            previewUrl={photo.previewUrl}
            blockedMessage={blockedMessage}
            saveError={saveError}
            onPickPhoto={photo.pick}
            onRetryPhoto={photo.retry}
            onRemovePhoto={photo.remove}
            onSetHappenedAt={(value) => dispatch({ type: "setHappenedAt", value })}
            onSetName={(value) => dispatch({ type: "setName", value })}
            onSubmitName={() => void save("path")}
          />
        </RecordStepScaffold>
      </section>

      <AlertDialog open={closeConfirmOpen} onOpenChange={setCloseConfirmOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{tCapture("closeTitle")}</AlertDialogTitle>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{tCapture("keepGoing")}</AlertDialogCancel>
            <Button variant="destructive" onClick={handleDiscard}>
              {t("discard")}
            </Button>
            {savable && <Button onClick={handleSaveFromDialog}>{tCapture("save")}</Button>}
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
